package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A line of lettering drawn along a slant, the way a shouted aside in a manhwa panel is drawn.
 * Each case here was a line a level-only pass could not see at all.
 */
class SlantDetectorTest {

    private val width = 460
    private val height = 320

    /**
     * Draws [letters] glyph-like boxes in a row, the row itself turned by [degrees] clockwise,
     * with every box upright in the row's own frame so the result reads like a turned line.
     */
    private fun page(
        degrees: Double,
        letters: Int = 5,
        centreX: Double = width / 2.0,
        centreY: Double = height / 2.0,
        letterHeight: Int = 26,
        letterWidth: Int = 18,
        gap: Int = 6,
    ): ArgbImage {
        val image = ArgbImage.filled(width, height, Colors.opaque(252, 252, 252))
        val radians = Math.toRadians(degrees)
        val cos = cos(radians)
        val sin = sin(radians)
        val total = letters * letterWidth + (letters - 1) * gap
        for (letter in 0 until letters) {
            val start = -total / 2.0 + letter * (letterWidth + gap)
            for (row in 0 until letterHeight) {
                for (column in 0 until letterWidth) {
                    // Hollow boxes read as glyph outlines rather than as one solid bar.
                    val onEdge = row < 3 || row >= letterHeight - 3 ||
                        column < 3 || column >= letterWidth - 3 ||
                        row in letterHeight / 2 - 1..letterHeight / 2 + 1
                    if (!onEdge) continue
                    val x = start + column
                    val y = -letterHeight / 2.0 + row
                    val px = (centreX + x * cos - y * sin).toInt()
                    val py = (centreY + x * sin + y * cos).toInt()
                    if (px in 0 until width && py in 0 until height) image.set(px, py, Colors.opaque(18, 18, 18))
                }
            }
        }
        return image
    }

    @Test
    fun `lettering drawn uphill is found and measured`() {
        val line = SlantDetector.detect(page(degrees = 28.0)).firstOrNull()

        assertTrue(line != null, "a line drawn at 28 degrees should be found")
        assertEquals(28f, line!!.angleDegrees, 3f, "measured ${line.angleDegrees} degrees")
        assertTrue(line.inkPixels > 200, "only ${line.inkPixels} ink pixels")
    }

    @Test
    fun `lettering drawn downhill is found with the opposite sign`() {
        val line = SlantDetector.detect(page(degrees = -22.0)).firstOrNull()

        assertTrue(line != null, "a line drawn at -22 degrees should be found")
        assertEquals(-22f, line!!.angleDegrees, 3f, "measured ${line.angleDegrees} degrees")
    }

    @Test
    fun `a level line is left to the level pass`() {
        val found = SlantDetector.detect(page(degrees = 0.0))

        assertTrue(found.isEmpty(), "a level line must not be offered as slanted: $found")
    }

    @Test
    fun `lettering already read is not offered again`() {
        val page = page(degrees = 30.0)
        val known = RectI(90, 100, 380, 240)

        val found = SlantDetector.detect(page, skipBoxes = listOf(known))

        assertTrue(found.isEmpty(), "a line inside a known box was offered again: $found")
    }

    @Test
    fun `light lettering on a dark bubble is found too`() {
        val image = ArgbImage.filled(width, height, Colors.opaque(24, 24, 28))
        val radians = Math.toRadians(24.0)
        val cos = cos(radians)
        val sin = sin(radians)
        for (glyph in 0 until 5) {
            for (row in 0 until 24) {
                for (column in 0 until 14) {
                    val onEdge = row < 3 || row >= 21 || column < 3 || column >= 11 ||
                        row in 10..12
                    if (!onEdge) continue
                    val x = -50.0 + glyph * 22 + column
                    val y = -12.0 + row
                    val px = (width / 2.0 + x * cos - y * sin).toInt()
                    val py = (height / 2.0 + x * sin + y * cos).toInt()
                    if (px in 0 until width && py in 0 until height) image.set(px, py, Colors.opaque(244, 244, 244))
                }
            }
        }

        val line = SlantDetector.detect(image).firstOrNull()

        assertTrue(line != null, "light lettering on dark artwork should be found")
        assertEquals(24f, line!!.angleDegrees, 3f, "measured ${line.angleDegrees} degrees")
    }

    @Test
    fun `a solid diagonal border is not offered as lettering`() {
        val image = ArgbImage.filled(width, height, Colors.opaque(252, 252, 252))
        val radians = Math.toRadians(30.0)
        for (step in -140..140) {
            val x = width / 2.0 + step * cos(radians)
            val y = height / 2.0 + step * sin(radians)
            for (thick in -4..4) {
                val px = (x - thick * sin(radians)).toInt()
                val py = (y + thick * cos(radians)).toInt()
                if (px in 0 until width && py in 0 until height) image.set(px, py, Colors.opaque(20, 20, 20))
            }
        }

        assertTrue(SlantDetector.detect(image).isEmpty(), "one unbroken stroke is a border, not a word")
    }

    @Test
    fun `a round blob of artwork is not a line of lettering`() {
        val image = ArgbImage.filled(width, height, Colors.opaque(252, 252, 252))
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = (x - width / 2.0) / 70.0
                val dy = (y - height / 2.0) / 70.0
                if (dx * dx + dy * dy <= 1.0) image.set(x, y, Colors.opaque(20, 20, 20))
            }
        }

        val found = SlantDetector.detect(image)

        assertTrue(found.isEmpty(), "a circle is not a line: $found")
    }

    @Test
    fun `the reported box sits on the lettering`() {
        val page = page(degrees = 25.0)
        val line = SlantDetector.detect(page).firstOrNull()

        assertTrue(line != null)
        val centre = line!!.box
        assertTrue(abs(centre.centerX - width / 2) < 30, "box centre x at ${centre.centerX}")
        assertTrue(abs(centre.centerY - height / 2) < 30, "box centre y at ${centre.centerY}")
    }
}
