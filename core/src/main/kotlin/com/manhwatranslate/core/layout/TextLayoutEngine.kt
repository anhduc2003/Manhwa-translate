package com.manhwatranslate.core.layout

import com.manhwatranslate.core.geometry.RectI

data class LayoutStyle(
    val minTextSizePx: Float = 11f,
    val maxTextSizePx: Float = 96f,
    val paddingFactor: Float = 0.05f,
    val minPaddingPx: Float = 2f,
    val lineSpacing: Float = 1.08f,
    val maxLines: Int = 8,
    val bold: Boolean = true,
    val minTextSizeRatio: Float = 0.55f,
)

/**
 * A ready-to-draw block of translated text: chosen font size, wrapped lines and the
 * vertical position of the block inside the original text box.
 */
data class TextLayout(
    val lines: List<String>,
    val textSizePx: Float,
    val lineHeightPx: Float,
    val blockHeightPx: Float,
    val box: RectI,
    val padding: Float,
    val bold: Boolean,
    val fits: Boolean,
) {
    val availableWidth: Float get() = (box.width - 2 * padding).coerceAtLeast(1f)

    fun baselineFor(lineIndex: Int): Float {
        val blockTop = box.top + (box.height - blockHeightPx) / 2f
        val firstBaseline = blockTop + textSizePx * 0.82f
        return firstBaseline + lineIndex * lineHeightPx
    }

    fun lineLeft(line: String, measurer: TextMeasurer): Float {
        val width = measurer.measureWidth(line, textSizePx, bold)
        return box.left + (box.width - width) / 2f
    }
}

/**
 * Fits translated text back into the space the original text occupied.
 *
 * The engine searches for the largest font size at which the wrapped text still fits the
 * region, then wraps greedily at word boundaries (any character for CJK), falling back to a
 * hard character break when a single word is wider than the box.
 */
class TextLayoutEngine(private val measurer: TextMeasurer) {

    fun layout(text: String, box: RectI, style: LayoutStyle = LayoutStyle()): TextLayout {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        val padding = maxOf(style.minPaddingPx, minOf(box.width, box.height) * style.paddingFactor)
        val availableWidth = (box.width - 2 * padding).coerceAtLeast(4f)
        val availableHeight = (box.height - 2 * padding).coerceAtLeast(4f)

        if (normalized.isEmpty()) {
            return TextLayout(
                lines = emptyList(),
                textSizePx = style.minTextSizePx,
                lineHeightPx = measurer.lineHeight(style.minTextSizePx, style.bold),
                blockHeightPx = 0f,
                box = box,
                padding = padding,
                bold = style.bold,
                fits = true,
            )
        }

        val hardFloor = maxOf(4f, style.minTextSizePx * style.minTextSizeRatio)
        var size = style.maxTextSizePx
        var lines = wrap(normalized, size, availableWidth)
        while (size > hardFloor) {
            val lineHeight = measurer.lineHeight(size, style.bold)
            val height = lineHeight * style.lineSpacing * lines.size
            if (lines.size <= style.maxLines && height <= availableHeight) break
            size -= 1f
            lines = wrap(normalized, size, availableWidth)
        }
        val lineHeight = measurer.lineHeight(size, style.bold)
        val blockHeight = lineHeight * style.lineSpacing * lines.size
        val fits = lines.size <= style.maxLines && blockHeight <= availableHeight * 1.02f
        return TextLayout(
            lines = lines,
            textSizePx = size,
            lineHeightPx = lineHeight * style.lineSpacing,
            blockHeightPx = blockHeight,
            box = box,
            padding = padding,
            bold = style.bold,
            fits = fits,
        )
    }

    fun wrap(text: String, sizePx: Float, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        val hardLines = text.split('\n')
        val result = ArrayList<String>()
        for (hardLine in hardLines) {
            result += wrapParagraph(hardLine, sizePx, maxWidth)
        }
        return result
    }

    private fun wrapParagraph(paragraph: String, sizePx: Float, maxWidth: Float): List<String> {
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) return emptyList()
        val tokens = tokenize(trimmed)
        val lines = ArrayList<String>()
        var current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                lines += current.toString().trim()
                current = StringBuilder()
            }
        }

        for (token in tokens) {
            val needsSpace = !token.cjk && current.isNotEmpty() && !current.lastIsCjkChar()
            val candidate = if (needsSpace) "$current ${token.text}" else "$current${token.text}"
            if (measurer.measureWidth(candidate, sizePx, bold = true) <= maxWidth) {
                current = StringBuilder(candidate)
                continue
            }
            if (current.isEmpty()) {
                if (measurer.measureWidth(token.text, sizePx, bold = true) <= maxWidth) {
                    current = StringBuilder(token.text)
                } else {
                    lines += splitLongToken(token.text, sizePx, maxWidth)
                }
            } else {
                flush()
                if (measurer.measureWidth(token.text, sizePx, bold = true) <= maxWidth) {
                    current = StringBuilder(token.text)
                } else {
                    lines += splitLongToken(token.text, sizePx, maxWidth)
                }
            }
        }
        flush()
        if (lines.isEmpty()) lines += trimmed
        return lines
    }

    private fun splitLongToken(token: String, sizePx: Float, maxWidth: Float): List<String> {
        val pieces = ArrayList<String>()
        var remaining = token
        var guard = 0
        while (remaining.isNotEmpty() && guard++ < 200) {
            val fit = measurer.breakIndex(remaining, sizePx, bold = true, maxWidth = maxWidth)
            val take = fit.coerceIn(1, remaining.length)
            pieces += remaining.substring(0, take)
            remaining = remaining.substring(take)
        }
        return pieces
    }

    private data class Token(val text: String, val cjk: Boolean)

    private fun tokenize(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        val word = StringBuilder()
        fun flushWord() {
            if (word.isNotEmpty()) {
                tokens += Token(word.toString(), cjk = false)
                word.clear()
            }
        }
        for (char in text) {
            when {
                char.isWhitespace() -> flushWord()
                char.isCjkChar() -> {
                    flushWord()
                    tokens += Token(char.toString(), cjk = true)
                }
                else -> word.append(char)
            }
        }
        flushWord()
        return tokens
    }

    private fun Char.isCjkChar(): Boolean {
        val code = code
        return (code in 0x3040..0x30FF) ||
            (code in 0x3400..0x4DBF) ||
            (code in 0x4E00..0x9FFF) ||
            (code in 0xAC00..0xD7AF) ||
            (code in 0xF900..0xFAFF) ||
            (code in 0x20000..0x2FA1F)
    }

    private fun StringBuilder.last(): Char = if (isEmpty()) ' ' else this[length - 1]

    private fun StringBuilder.lastOrNullChar(): Char? = if (isEmpty()) null else this[length - 1]

    private fun StringBuilder.lastIsCjkChar(): Boolean = lastOrNullChar()?.isCjkChar() ?: false
}
