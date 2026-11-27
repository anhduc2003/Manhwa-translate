package com.manhwatranslate.core.erase

/**
 * Binary mask over a rectangle of the page. Values are 1 where pixels belong to letters
 * (and therefore must be erased) and 0 elsewhere.
 */
class TextMask(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val data: ByteArray,
    val backgroundArgb: Int,
    val backgroundContrast: Float,
    val fillRectangle: Boolean = false,
    val inkArgb: Int = 0,
    val surface: BackgroundSurface? = null,
    val metrics: InkMetrics = InkMetrics.Empty,
) {
    init {
        require(data.size == width * height) { "Mask size mismatch" }
    }

    val right: Int get() = left + width
    val bottom: Int get() = top + height

    fun at(x: Int, y: Int): Boolean = data[y * width + x].toInt() != 0

    fun markedCount(): Int = data.count { it.toInt() != 0 }

    fun coverage(): Float = if (data.isEmpty()) 0f else markedCount().toFloat() / data.size.toFloat()
}
