package com.manhwatranslate.core.layout

import com.manhwatranslate.core.geometry.RectI

data class LayoutStyle(
    val minTextSizePx: Float = 11f,
    val maxTextSizePx: Float = 96f,
    val paddingFactor: Float = 0.05f,
    val minPaddingPx: Float = 2f,
    val lineSpacing: Float = 1.08f,
    val maxLines: Int = 8,
    val minTextSizeRatio: Float = 0.55f,
    val font: FontSpec = FontSpec.Default,
    /** Outline drawn around the letters, reserved on both sides so nothing spills out of the box. */
    val strokeWidthFactor: Float = 0f,
    /** Line count of the original lettering; the translation aims for the same shape. */
    val preferredLines: Int = 0,
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
    val font: FontSpec,
    val fits: Boolean,
) {
    val availableWidth: Float get() = (box.width - 2 * padding).coerceAtLeast(1f)

    fun baselineFor(lineIndex: Int): Float {
        val blockTop = box.top + (box.height - blockHeightPx) / 2f
        val firstBaseline = blockTop + textSizePx * 0.82f
        return firstBaseline + lineIndex * lineHeightPx
    }

    /**
     * Extra scale the block needs so it cannot leave [box]. The search already shrinks the font,
     * but a box that is too small for the translation at any readable size, or a text size the
     * reader raised, can still overflow; compositors apply this so a line never spills onto the
     * artwork next to the bubble.
     */
    fun scaleToFit(maxLineWidthPx: Float): Float {
        if (fits) return 1f
        val widthScale = if (maxLineWidthPx > 0f) box.width / maxLineWidthPx else 1f
        val heightScale = if (blockHeightPx > 0f) box.height / blockHeightPx else 1f
        return minOf(1f, widthScale, heightScale).coerceIn(0.05f, 1f)
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
        val font = style.font

        if (normalized.isEmpty()) {
            return TextLayout(
                lines = emptyList(),
                textSizePx = style.minTextSizePx,
                lineHeightPx = measurer.lineHeight(style.minTextSizePx, font),
                blockHeightPx = 0f,
                box = box,
                padding = padding,
                font = font,
                fits = true,
            )
        }

        val hardFloor = maxOf(4f, style.minTextSizePx * style.minTextSizeRatio)
        val absoluteFloor = 6f
        val shapeLimit = if (style.preferredLines > 0) {
            minOf(style.maxLines, style.preferredLines + 1)
        } else {
            style.maxLines
        }

        var size = style.maxTextSizePx
        var lines = wrap(normalized, size, lineWidth(size, availableWidth, style), font)
        var fits = false

        while (true) {
            val height = measurer.lineHeight(size, font) * style.lineSpacing * lines.size
            if (lines.size <= shapeLimit && height <= availableHeight) {
                fits = true
                break
            }
            if (size <= hardFloor) break
            size -= 1f
            lines = wrap(normalized, size, lineWidth(size, availableWidth, style), font)
        }

        if (!fits) {
            size = hardFloor
            while (true) {
                lines = wrap(normalized, size, lineWidth(size, availableWidth, style), font)
                val height = measurer.lineHeight(size, font) * style.lineSpacing * lines.size
                if ((lines.size <= style.maxLines && height <= availableHeight) || size <= absoluteFloor) {
                    fits = lines.size <= style.maxLines && height <= availableHeight
                    break
                }
                size -= 1f
            }
        }

        val lineHeight = measurer.lineHeight(size, font)
        val blockHeight = lineHeight * style.lineSpacing * lines.size
        return TextLayout(
            lines = lines,
            textSizePx = size,
            lineHeightPx = lineHeight * style.lineSpacing,
            blockHeightPx = blockHeight,
            box = box,
            padding = padding,
            font = font,
            fits = fits,
        )
    }

    /** Wrapping width for one size, leaving room for the outline that will be drawn later. */
    private fun lineWidth(sizePx: Float, availableWidth: Float, style: LayoutStyle): Float =
        (availableWidth - sizePx * style.strokeWidthFactor * 2f).coerceAtLeast(4f)

    fun wrap(text: String, sizePx: Float, maxWidth: Float, font: FontSpec = FontSpec.Default): List<String> {
        if (text.isEmpty()) return emptyList()
        val hardLines = text.split('\n')
        val result = ArrayList<String>()
        for (hardLine in hardLines) {
            result += wrapParagraph(hardLine, sizePx, maxWidth, font)
        }
        return result
    }

    private fun wrapParagraph(paragraph: String, sizePx: Float, maxWidth: Float, font: FontSpec): List<String> {
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
            if (measurer.measureWidth(candidate, sizePx, font) <= maxWidth) {
                current = StringBuilder(candidate)
                continue
            }
            if (current.isEmpty()) {
                if (measurer.measureWidth(token.text, sizePx, font) <= maxWidth) {
                    current = StringBuilder(token.text)
                } else {
                    lines += splitLongToken(token.text, sizePx, maxWidth, font)
                }
            } else {
                flush()
                if (measurer.measureWidth(token.text, sizePx, font) <= maxWidth) {
                    current = StringBuilder(token.text)
                } else {
                    lines += splitLongToken(token.text, sizePx, maxWidth, font)
                }
            }
        }
        flush()
        if (lines.isEmpty()) lines += trimmed
        return lines
    }

    private fun splitLongToken(token: String, sizePx: Float, maxWidth: Float, font: FontSpec): List<String> {
        val pieces = ArrayList<String>()
        var remaining = token
        var guard = 0
        while (remaining.isNotEmpty() && guard++ < 200) {
            val fit = measurer.breakIndex(remaining, sizePx, font, maxWidth)
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

    private fun StringBuilder.lastIsCjkChar(): Boolean {
        if (isEmpty()) return false
        return this[length - 1].isCjkChar()
    }
}
