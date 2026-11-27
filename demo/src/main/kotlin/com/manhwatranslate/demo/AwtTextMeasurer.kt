package com.manhwatranslate.demo

import com.manhwatranslate.core.layout.TextMeasurer
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics
import java.awt.font.TextLayout

class AwtTextMeasurer : TextMeasurer {
    private val context = FontRenderContext(null, true, true)
    private val cache = HashMap<Long, Font>()

    fun font(sizePx: Float, bold: Boolean): Font =
        cache.getOrPut((sizePx.toBits().toLong() shl 1) or if (bold) 1L else 0L) {
            Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 12).deriveFont(sizePx)
        }

    override fun measureWidth(text: String, sizePx: Float, bold: Boolean): Float {
        if (text.isEmpty()) return 0f
        return TextLayout(text, font(sizePx, bold), context).bounds.width.toFloat()
    }

    override fun lineHeight(sizePx: Float, bold: Boolean): Float {
        val metrics: LineMetrics = font(sizePx, bold).getLineMetrics("Ag", context)
        return metrics.ascent + metrics.descent + metrics.leading
    }

    override fun breakIndex(text: String, sizePx: Float, bold: Boolean, maxWidth: Float): Int {
        if (text.isEmpty() || maxWidth <= 0f) return 1
        var low = 1
        var high = text.length
        var best = 1
        while (low <= high) {
            val mid = (low + high) / 2
            val width = TextLayout(text.substring(0, mid), font(sizePx, bold), context).bounds.width.toFloat()
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
