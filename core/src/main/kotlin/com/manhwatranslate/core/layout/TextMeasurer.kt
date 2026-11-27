package com.manhwatranslate.core.layout

/**
 * Platform text metrics. Android implements this with [android.graphics.Paint], the JVM demo
 * harness with `java.awt.FontMetrics`, and unit tests with a deterministic fake.
 */
interface TextMeasurer {
    fun measureWidth(text: String, sizePx: Float, bold: Boolean): Float

    fun lineHeight(sizePx: Float, bold: Boolean): Float

    /** How many leading characters of [text] fit into [maxWidth] at the given size (at least 1). */
    fun breakIndex(text: String, sizePx: Float, bold: Boolean, maxWidth: Float): Int
}
