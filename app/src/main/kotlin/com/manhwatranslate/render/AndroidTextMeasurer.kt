package com.manhwatranslate.render

import android.graphics.Paint
import android.graphics.Typeface
import com.manhwatranslate.core.layout.TextMeasurer

class AndroidTextMeasurer : TextMeasurer {

    private val paints = HashMap<Long, Paint>()

    private fun paint(sizePx: Float, bold: Boolean): Paint {
        val key = (sizePx.toBits().toLong() shl 1) or if (bold) 1L else 0L
        return paints.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sizePx
                typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
                isSubpixelText = true
            }
        }
    }

    override fun measureWidth(text: String, sizePx: Float, bold: Boolean): Float =
        paint(sizePx, bold).measureText(text)

    override fun lineHeight(sizePx: Float, bold: Boolean): Float {
        val metrics = paint(sizePx, bold).fontMetrics
        return metrics.descent - metrics.ascent + metrics.leading
    }

    override fun breakIndex(text: String, sizePx: Float, bold: Boolean, maxWidth: Float): Int {
        if (text.isEmpty() || maxWidth <= 0f) return 1
        val count = paint(sizePx, bold).breakText(text, true, maxWidth, null)
        return count.coerceIn(1, text.length)
    }
}
