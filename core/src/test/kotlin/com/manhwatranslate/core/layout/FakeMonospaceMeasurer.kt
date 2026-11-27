package com.manhwatranslate.core.layout

/** Deterministic monospace metrics so layout maths can be asserted exactly. */
class FakeMonospaceMeasurer(
    private val widthFactor: Float = 0.55f,
    private val heightFactor: Float = 1.2f,
) : TextMeasurer {

    override fun measureWidth(text: String, sizePx: Float, bold: Boolean): Float =
        text.length * sizePx * widthFactor

    override fun lineHeight(sizePx: Float, bold: Boolean): Float = sizePx * heightFactor

    override fun breakIndex(text: String, sizePx: Float, bold: Boolean, maxWidth: Float): Int {
        if (maxWidth <= 0f) return 1
        val perChar = sizePx * widthFactor
        return (maxWidth / perChar).toInt().coerceIn(1, text.length)
    }
}
