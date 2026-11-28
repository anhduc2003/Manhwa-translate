package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SfxFilterTest {

    private fun bubblePage(): Pair<ArgbImage, TextRegion> {
        val image = ArgbImage.filled(1000, 600, Colors.opaque(245, 245, 245))
        for (y in 200 until 500) {
            for (x in 200 until 800) image.set(x, y, Colors.opaque(255, 255, 255))
        }
        for (y in 330 until 366) {
            for (x in 300 until 700) image.set(x, y, Colors.opaque(20, 20, 20))
        }
        val box = RectI(298, 328, 702, 368)
        return image to TextRegion(
            id = 0,
            box = box,
            lines = listOf(TextLine(box, "I never thought it would end")),
            text = "I never thought it would end",
        )
    }

    private fun artworkPage(): Pair<ArgbImage, TextRegion> {
        val random = Random(5)
        val image = ArgbImage(1000, 600, IntArray(1000 * 600) { _ ->
            val shade = 40 + random.nextInt(140)
            Colors.opaque(shade, shade, shade)
        })
        val box = RectI(300, 220, 700, 320)
        return image to TextRegion(
            id = 1,
            box = box,
            lines = listOf(TextLine(box, "CRASH")),
            text = "CRASH!",
        )
    }

    @Test
    fun `dialogue inside a white bubble is kept`() {
        val (image, region) = bubblePage()
        val analysis = SfxFilter.analyze(image, region, image.width, SfxSensitivity.MEDIUM)
        assertFalse(analysis.isSoundEffect, "score ${analysis.score} ${analysis.reasons}")
    }

    @Test
    fun `big display letters on busy artwork are treated as a sound effect`() {
        val (image, region) = artworkPage()
        val analysis = SfxFilter.analyze(image, region, image.width, SfxSensitivity.MEDIUM)
        assertTrue(analysis.isSoundEffect, "score ${analysis.score} ${analysis.reasons}")
    }

    @Test
    fun `a two word line on artwork is dialogue, not a sound effect`() {
        // High contrast artwork: the rule for a sound effect is a busy surround, and real splash
        // pages are exactly this busy around a shout balloon.
        val image = ArgbImage(1000, 600, IntArray(1000 * 600) { index ->
            val x = index % 1000
            val y = index / 1000
            if ((x / 6 + y / 6) % 2 == 0) Colors.opaque(10, 10, 10) else Colors.opaque(250, 250, 250)
        })
        // The letters sit on busy artwork, which is how a spiky shout balloon reads from outside
        // the box; two words with a sentence shape are still dialogue.
        val box = RectI(280, 200, 720, 340)
        val region = TextRegion(
            id = 4,
            box = box,
            lines = listOf(
                TextLine(RectI(box.left, 200, box.right, 268), "THEN"),
                TextLine(RectI(box.left, 272, box.right, 340), "FASTER..."),
            ),
            text = "THEN FASTER...",
        )

        val analysis = SfxFilter.analyze(image, region, image.width, SfxSensitivity.MEDIUM)

        assertFalse(analysis.isSoundEffect, "score ${analysis.score} ${analysis.reasons}")
    }

    @Test
    fun `turning the filter off never skips anything`() {
        val (image, region) = artworkPage()
        val analysis = SfxFilter.analyze(image, region, image.width, SfxSensitivity.OFF)
        assertFalse(analysis.isSoundEffect)
    }

    @Test
    fun `low sensitivity is stricter than high`() {
        val random = Random(9)
        val image = ArgbImage(1000, 400, IntArray(1000 * 400) { _ ->
            val shade = 120 + random.nextInt(30)
            Colors.opaque(shade, shade, shade)
        })
        val box = RectI(200, 150, 800, 220)
        val region = TextRegion(
            id = 2,
            box = box,
            lines = listOf(TextLine(box, "BOOM")),
            text = "BOOM",
        )
        val low = SfxFilter.analyze(image, region, image.width, SfxSensitivity.LOW)
        val high = SfxFilter.analyze(image, region, image.width, SfxSensitivity.HIGH)
        assertTrue(high.score >= low.score)
        assertTrue(!low.isSoundEffect || high.isSoundEffect)
    }

    @Test
    fun `a large shout inside a bubble is still dialogue`() {
        val image = ArgbImage.filled(1000, 800, Colors.opaque(240, 240, 240))
        for (y in 150 until 650) for (x in 150 until 850) image.set(x, y, Colors.opaque(255, 255, 255))
        for (y in 350 until 420) for (x in 300 until 700) image.set(x, y, Colors.opaque(15, 15, 15))
        val box = RectI(298, 348, 702, 422)
        val region = TextRegion(
            id = 3,
            box = box,
            lines = listOf(TextLine(box, "STOP RIGHT THERE!")),
            text = "STOP RIGHT THERE!",
        )
        val analysis = SfxFilter.analyze(image, region, image.width, SfxSensitivity.MEDIUM)
        assertFalse(analysis.isSoundEffect, "score ${analysis.score} ${analysis.reasons}")
    }

    @Test
    fun `a sentence hand lettered on artwork is dialogue, not a sound effect`() {
        val image = ArgbImage.filled(900, 700, Colors.opaque(150, 170, 200))
        val random = java.util.Random(3)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val shade = 110 + random.nextInt(90)
                image.set(x, y, Colors.opaque(shade, shade, shade))
            }
        }
        val box = RectI(120, 200, 520, 260)
        val region = TextRegion(
            id = 4,
            box = box,
            lines = listOf(TextLine(box, "Your drunk habit isn't nice.")),
            text = "Your drunk habit isn't nice.",
        )
        val analysis = SfxFilter.analyze(image, region, image.width, SfxSensitivity.MEDIUM)
        assertFalse(analysis.isSoundEffect, "score ${analysis.score} ${analysis.reasons}")
    }
}
