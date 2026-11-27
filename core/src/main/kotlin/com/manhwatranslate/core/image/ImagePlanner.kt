package com.manhwatranslate.core.image

/**
 * How to decode a page: [sample] is the power-of-two subsampling the decoder applies,
 * [width] x [height] is the size that leaves it with, and [pixels] is the pixel count the
 * sampled bitmap is then smoothly scaled down to (never above the budget).
 */
data class ImagePlan(val sample: Int, val pixels: Int, val width: Int, val height: Int)

/**
 * Subsampling throws detail away in factors of four, so a page only samples as far as keeps
 * the transient decode within twice the pixel budget; the remainder is a single smooth scale
 * to the budget, which keeps far more detail than sampling down to it would.
 */
fun planImage(sourceWidth: Int, sourceHeight: Int, maxPixels: Int): ImagePlan {
    require(sourceWidth > 0 && sourceHeight > 0) { "Image must be non-empty, got ${sourceWidth}x$sourceHeight" }
    require(maxPixels > 0) { "Pixel budget must be positive, got $maxPixels" }
    val ceiling = maxPixels.toLong() * 2
    var sample = 1
    var width = sourceWidth
    var height = sourceHeight
    while (width.toLong() * height > ceiling) {
        sample *= 2
        width = (sourceWidth - 1) / sample + 1
        height = (sourceHeight - 1) / sample + 1
    }
    return ImagePlan(
        sample = sample,
        pixels = minOf(width.toLong() * height, maxPixels.toLong()).toInt(),
        width = width,
        height = height,
    )
}
