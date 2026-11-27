package com.manhwatranslate.core.erase

import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackgroundSurfaceTest {

    @Test
    fun `fits a horizontal gradient`() {
        val width = 120
        val height = 80
        val image = ArgbImage(width, height, IntArray(width * height) { index ->
            val x = index % width
            val shade = 100 + x * 120 / width
            Colors.opaque(shade, shade, shade)
        })
        val used = ByteArray(width * height)
        val surface = requireNotNull(BackgroundSurface.fit(image.pixels, width, height, used))
        val left = surface.redAt(10, 40)
        val right = surface.redAt(110, 40)
        assertTrue(right - left > 80f, "gradient not modelled: $left -> $right")
        assertEquals(Colors.red(image.get(60, 40)).toFloat(), surface.redAt(60, 40), 4f)
    }

    @Test
    fun `fits a radial shading`() {
        val width = 100
        val height = 100
        val image = ArgbImage(width, height, IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val distanceSquared = ((x - 50) * (x - 50) + (y - 50) * (y - 50)).toDouble()
            val shade = (240 - distanceSquared * 0.02).toInt().coerceIn(0, 255)
            Colors.opaque(shade, shade, shade)
        })
        val used = ByteArray(width * height)
        val surface = requireNotNull(BackgroundSurface.fit(image.pixels, width, height, used))
        assertEquals(Colors.red(image.get(50, 50)).toFloat(), surface.redAt(50, 50), 6f)
        assertEquals(Colors.red(image.get(10, 50)).toFloat(), surface.redAt(10, 50), 6f)
    }

    @Test
    fun `returns null when there is nothing to fit`() {
        val pixels = IntArray(40 * 40) { Colors.opaque(255, 255, 255) }
        val used = ByteArray(40 * 40) { 1 }
        assertNull(BackgroundSurface.fit(pixels, 40, 40, used))
    }

    @Test
    fun `ignores pixels marked as letters`() {
        val width = 60
        val height = 40
        val image = ArgbImage(width, height, IntArray(width * height) { index ->
            val x = index % width
            val inText = x in 20..40
            Colors.opaque(if (inText) 0 else 250, if (inText) 0 else 250, if (inText) 0 else 250)
        })
        val used = ByteArray(width * height) { index ->
            val x = index % width
            if (x in 20..40) 1 else 0
        }
        val surface = requireNotNull(BackgroundSurface.fit(image.pixels, width, height, used, sampleStep = 1))
        assertEquals(250f, surface.redAt(30, 20), 2f)
    }
}

class InkAnalyzerTest {

    @Test
    fun `measures stroke width from horizontal runs`() {
        val width = 80
        val height = 20
        val mask = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val inStroke = x % 12 < 3
                if (inStroke) mask[y * width + x] = 1
            }
        }
        val metrics = InkAnalyzer.analyze(mask, width, height)
        assertEquals(3f, metrics.strokeWidthPx, 0.6f)
        assertTrue(metrics.coverage > 0.2f)
    }

    @Test
    fun `detects a slanted face`() {
        val width = 120
        val height = 40
        val mask = ByteArray(width * height)
        for (y in 0 until height) {
            val start = 20 + (y * 0.3f).toInt()
            for (x in start until minOf(start + 6, width)) {
                mask[y * width + x] = 1
            }
        }
        val metrics = InkAnalyzer.analyze(mask, width, height)
        assertTrue(metrics.slant > 0.2f, "slant ${metrics.slant}")
    }

    @Test
    fun `upright text has no slant`() {
        val width = 60
        val height = 30
        val mask = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 20 until 30) mask[y * width + x] = 1
        }
        val metrics = InkAnalyzer.analyze(mask, width, height)
        assertTrue(kotlin.math.abs(metrics.slant) < 0.05f)
    }
}
