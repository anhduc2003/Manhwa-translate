package com.manhwatranslate.core.image

import com.manhwatranslate.core.geometry.RectI

/**
 * Mutable ARGB_8888 image buffer. This is the pixel format shared with Android's
 * [android.graphics.Bitmap] and with `java.awt.image.BufferedImage` in the demo harness,
 * so the same algorithms run unchanged on device and on the JVM.
 */
class ArgbImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "Image must be non-empty, got ${width}x$height" }
        require(pixels.size == width * height) {
            "Pixel buffer size ${pixels.size} does not match ${width}x$height"
        }
    }

    fun get(x: Int, y: Int): Int = pixels[y * width + x]

    fun set(x: Int, y: Int, argb: Int) {
        pixels[y * width + x] = argb
    }

    fun crop(rect: RectI): ArgbImage {
        val clamped = requireNotNull(rect.clamped(width, height)) { "Crop rect $rect outside image" }
        val out = IntArray(clamped.width * clamped.height)
        for (row in 0 until clamped.height) {
            val srcOffset = (clamped.top + row) * width + clamped.left
            val dstOffset = row * clamped.width
            java.lang.System.arraycopy(pixels, srcOffset, out, dstOffset, clamped.width)
        }
        return ArgbImage(clamped.width, clamped.height, out)
    }

    fun copy(): ArgbImage = ArgbImage(width, height, pixels.copyOf())

    fun fill(argb: Int) {
        java.util.Arrays.fill(pixels, argb)
    }

    companion object {
        fun filled(width: Int, height: Int, argb: Int): ArgbImage =
            ArgbImage(width, height, IntArray(width * height) { argb })
    }
}
