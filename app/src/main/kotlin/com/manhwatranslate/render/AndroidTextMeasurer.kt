package com.manhwatranslate.render

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import com.manhwatranslate.core.layout.FontFamilyKind
import com.manhwatranslate.core.layout.FontSpec
import com.manhwatranslate.core.layout.FontWeightLevel
import com.manhwatranslate.core.layout.TextMeasurer

class AndroidTextMeasurer : TextMeasurer {

    private val paints = HashMap<Long, Paint>()
    private val typefaces = HashMap<String, Typeface>()

    /** A font the user imported, used when the style asks for [FontFamilyKind.CUSTOM]. */
    var customTypeface: Typeface? = null
        private set

    fun setCustomTypeface(typeface: Typeface?) {
        customTypeface = typeface
        typefaces.clear()
        paints.clear()
    }

    fun typefaceFor(font: FontSpec): Typeface {
        val key = "${font.family}-${font.weight}-${font.italic}"
        return typefaces.getOrPut(key) {
            val family = when (font.family) {
                FontFamilyKind.SERIF -> "serif"
                FontFamilyKind.CONDENSED -> "sans-serif-condensed"
                FontFamilyKind.MONOSPACE -> "monospace"
                FontFamilyKind.CUSTOM -> return@getOrPut customTypeface ?: Typeface.SANS_SERIF
                FontFamilyKind.SANS -> "sans-serif"
            }
            val style = if (font.italic) Typeface.ITALIC else Typeface.NORMAL
            val base = Typeface.create(family, style)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(base, font.weight.androidWeight, font.italic)
            } else {
                val bold = font.weight.androidWeight >= FontWeightLevel.BOLD.androidWeight
                Typeface.create(base, if (bold) style or Typeface.BOLD else style)
            }
        }
    }

    fun newPaint(sizePx: Float, font: FontSpec): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = sizePx
        typeface = typefaceFor(font)
        isSubpixelText = true
    }

    private fun paint(sizePx: Float, font: FontSpec): Paint {
        val key = (sizePx.toBits().toLong() shl 12) or
            (font.family.ordinal.toLong() shl 4) or
            (font.weight.ordinal.toLong() shl 1) or
            (if (font.italic) 1L else 0L)
        return paints.getOrPut(key) { newPaint(sizePx, font) }
    }

    override fun measureWidth(text: String, sizePx: Float, font: FontSpec): Float =
        paint(sizePx, font).measureText(text)

    override fun lineHeight(sizePx: Float, font: FontSpec): Float {
        val metrics = paint(sizePx, font).fontMetrics
        return metrics.descent - metrics.ascent + metrics.leading
    }

    override fun breakIndex(text: String, sizePx: Float, font: FontSpec, maxWidth: Float): Int {
        if (text.isEmpty() || maxWidth <= 0f) return 1
        val count = paint(sizePx, font).breakText(text, true, maxWidth, null)
        return count.coerceIn(1, text.length)
    }
}
