package com.manhwatranslate.core.image

import com.manhwatranslate.core.geometry.RectI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One turn of a page, with the mapping that puts a box found in the turned copy back on the
 * original.
 *
 * Lettering drawn along a slanted line is invisible to an OCR pass that expects a level line, so
 * the page is turned by a few angles and read again; the boxes that come back are turned back to
 * the page, and the angle travels with them so the translation can be drawn along the same line.
 */
class ImageRotation private constructor(
    val degrees: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val width: Int,
    val height: Int,
) {
    private val radians = Math.toRadians(degrees.toDouble())
    private val cos = cos(radians)
    private val sin = sin(radians)
    private val sourceCenterX = sourceWidth / 2.0
    private val sourceCenterY = sourceHeight / 2.0
    private val turnedCenterX = width / 2.0
    private val turnedCenterY = height / 2.0

    /** A point of the turned copy, in the coordinates of the original page. */
    fun toSource(x: Double, y: Double): Pair<Double, Double> {
        val dx = x - turnedCenterX
        val dy = y - turnedCenterY
        return (cos * dx - sin * dy + sourceCenterX) to (sin * dx + cos * dy + sourceCenterY)
    }

    /** A box of the turned copy, as the box that contains the same corners on the page. */
    fun toSource(box: RectI): RectI {
        val corners = listOf(
            toSource(box.left.toDouble(), box.top.toDouble()),
            toSource(box.right.toDouble(), box.top.toDouble()),
            toSource(box.left.toDouble(), box.bottom.toDouble()),
            toSource(box.right.toDouble(), box.bottom.toDouble()),
        )
        val left = corners.minOf { it.first }.toInt()
        val top = corners.minOf { it.second }.toInt()
        val right = corners.maxOf { it.first }.toInt()
        val bottom = corners.maxOf { it.second }.toInt()
        return RectI(left, top, maxOf(right, left + 1), maxOf(bottom, top + 1))
    }

    companion object {
        /** The turn of [image] by [degrees] clockwise, canvas grown to hold it. */
        fun of(image: ArgbImage, degrees: Float): ImageRotation {
            val radians = Math.toRadians(degrees.toDouble())
            val cos = cos(radians)
            val sin = sin(radians)
            val width = kotlin.math.ceil(
                kotlin.math.abs(image.width * cos) + kotlin.math.abs(image.height * sin),
            ).toInt().coerceAtLeast(1)
            val height = kotlin.math.ceil(
                kotlin.math.abs(image.width * sin) + kotlin.math.abs(image.height * cos),
            ).toInt().coerceAtLeast(1)
            return ImageRotation(degrees, image.width, image.height, width, height)
        }
    }
}

/** [this] picture turned by [rotation], the way [ImageRotation] maps it back. */
fun ArgbImage.rotated(rotation: ImageRotation): ArgbImage {
    val out = IntArray(rotation.width * rotation.height)
    for (y in 0 until rotation.height) {
        for (x in 0 until rotation.width) {
            val (sx, sy) = rotation.toSource(x.toDouble(), y.toDouble())
            // Clamped rather than left blank: the corners the turn adds must carry the colour of
            // the page edge, or the recognizer sees a black frame that is not on the page at all.
            val sourceX = sx.toInt().coerceIn(0, width - 1)
            val sourceY = sy.toInt().coerceIn(0, height - 1)
            out[y * rotation.width + x] = pixels[sourceY * width + sourceX]
        }
    }
    return ArgbImage(rotation.width, rotation.height, out)
}
