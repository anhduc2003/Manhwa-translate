package com.manhwatranslate.core.layout

/** Deterministic monospace metrics so layout maths can be asserted exactly. */
class FakeMonospaceMeasurer(
    private val widthFactor: Float = 0.55f,
    private val heightFactor: Float = 1.2f,
) : TextMeasurer {

    override fun measureWidth(text: String, sizePx: Float, font: FontSpec): Float =
        text.length * sizePx * widthFactor * weightFactor(font)

    override fun lineHeight(sizePx: Float, font: FontSpec): Float = sizePx * heightFactor

    override fun breakIndex(text: String, sizePx: Float, font: FontSpec, maxWidth: Float): Int {
        if (maxWidth <= 0f) return 1
        val perChar = sizePx * widthFactor * weightFactor(font)
        return (maxWidth / perChar).toInt().coerceIn(1, text.length)
    }

    private fun weightFactor(font: FontSpec): Float = when (font.weight) {
        FontWeightLevel.THIN -> 0.92f
        FontWeightLevel.REGULAR -> 0.95f
        FontWeightLevel.MEDIUM -> 1f
        FontWeightLevel.BOLD -> 1.05f
        FontWeightLevel.BLACK -> 1.12f
    }
}
