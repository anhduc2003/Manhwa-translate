package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage

/** A horizontal slice of a page handed to the OCR engine. */
data class ImageTile(
    val index: Int,
    val rect: RectI,
    val image: ArgbImage,
)

/**
 * Webtoon episodes are often 800x20000 pixels. OCR engines are slow and memory hungry on
 * inputs that tall, so pages are scanned in overlapping horizontal slices and the results
 * are stitched back together afterwards.
 */
object ImageTiler {
    const val DEFAULT_MAX_TILE_HEIGHT = 1800
    const val DEFAULT_OVERLAP = 96

    fun split(
        image: ArgbImage,
        maxTileHeight: Int = DEFAULT_MAX_TILE_HEIGHT,
        overlap: Int = DEFAULT_OVERLAP,
    ): List<ImageTile> {
        require(maxTileHeight > 0) { "maxTileHeight must be positive" }
        if (image.height <= maxTileHeight) {
            return listOf(ImageTile(0, RectI(0, 0, image.width, image.height), image))
        }
        val safeOverlap = overlap.coerceIn(0, maxTileHeight / 2)
        val step = maxTileHeight - safeOverlap
        val tiles = ArrayList<ImageTile>()
        var y = 0
        var index = 0
        while (y < image.height) {
            val bottom = minOf(y + maxTileHeight, image.height)
            val rect = RectI(0, y, image.width, bottom)
            tiles += ImageTile(index++, rect, image.crop(rect))
            if (bottom >= image.height) break
            y += step
        }
        return tiles
    }
}
