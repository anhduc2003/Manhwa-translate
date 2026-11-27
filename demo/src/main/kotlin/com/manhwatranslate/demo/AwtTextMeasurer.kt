package com.manhwatranslate.demo

import com.manhwatranslate.core.layout.FontFamilyKind
import com.manhwatranslate.core.layout.FontSpec
import com.manhwatranslate.core.layout.FontWeightLevel
import com.manhwatranslate.core.layout.TextMeasurer
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics
import java.awt.font.TextLayout

class AwtTextMeasurer : TextMeasurer {
    private val context = FontRenderContext(null, true, true)
    private val cache = HashMap<Long, Font>()

    fun font(sizePx: Float, spec: FontSpec): Font {
        val family = when (spec.family) {
            FontFamilyKind.SERIF -> Font.SERIF
            FontFamilyKind.MONOSPACE -> Font.MONOSPACED
            FontFamilyKind.CONDENSED -> Font.SANS_SERIF
            FontFamilyKind.CUSTOM -> Font.SANS_SERIF
            FontFamilyKind.SANS -> Font.SANS_SERIF
        }
        val style = when {
            spec.weight == FontWeightLevel.REGULAR || spec.weight == FontWeightLevel.THIN -> Font.PLAIN
            else -> Font.BOLD
        } or if (spec.italic) Font.ITALIC else Font.PLAIN
        val key = (sizePx.toBits().toLong() shl 8) or
            (family.hashCode().toLong() and 0xFF) or
            (style.toLong() shl 4)
        return cache.getOrPut(key) { Font(family, style, 12).deriveFont(sizePx) }
    }

    fun font(sizePx: Float, bold: Boolean): Font =
        font(sizePx, FontSpec(weight = if (bold) FontWeightLevel.BOLD else FontWeightLevel.REGULAR))

    override fun measureWidth(text: String, sizePx: Float, font: FontSpec): Float {
        if (text.isEmpty()) return 0f
        return TextLayout(text, font(sizePx, font), context).bounds.width.toFloat()
    }

    override fun lineHeight(sizePx: Float, font: FontSpec): Float {
        val metrics: LineMetrics = font(sizePx, font).getLineMetrics("Ag", context)
        return metrics.ascent + metrics.descent + metrics.leading
    }

    override fun breakIndex(text: String, sizePx: Float, font: FontSpec, maxWidth: Float): Int {
        if (text.isEmpty() || maxWidth <= 0f) return 1
        var low = 1
        var high = text.length
        var best = 1
        val awtFont = font(sizePx, font)
        while (low <= high) {
            val mid = (low + high) / 2
            val width = TextLayout(text.substring(0, mid), awtFont, context).bounds.width.toFloat()
            if (width <= maxWidth) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }
}
