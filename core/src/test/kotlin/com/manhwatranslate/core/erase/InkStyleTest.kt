package com.manhwatranslate.core.erase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The measurements a translated page inherits its lettering style from. Each case here was a
 * way the old run-length/centre-of-ink pair lied: it read ordinary dialogue as extra black and
 * upright text as slanted, so every bubble came out in the same heavy italic face.
 */
class InkStyleTest {

    private fun mask(width: Int, height: Int): ByteArray = ByteArray(width * height)

    private fun fill(data: ByteArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in y0 until y1) for (x in x0 until x1) data[y * width + x] = 1
    }

    @Test
    fun `a long crossbar is a thin stroke, not a fat one`() {
        // Two rows of 60px bars stacked 12px apart: a letter like E or T read as ink.
        val width = 80
        val height = 30
        val data = mask(width, height)
        fill(data, width, 5, 6, 65, 9)
        fill(data, width, 5, 18, 65, 21)

        val metrics = InkAnalyzer.analyze(data, width, height)

        // The bars are 3px thick however long they are.
        assertTrue(
            metrics.strokeWidthPx in 2.0f..5.0f,
            "a 3px bar measured ${metrics.strokeWidthPx}px",
        )
    }

    @Test
    fun `upright letters with tall ones on the left are not italic`() {
        val width = 120
        val height = 40
        val data = mask(width, height)
        // A tall letter on the left, short letters to its right: the ink centre moves right as
        // the rows go down even though nothing is slanted.
        fill(data, width, 8, 5, 12, 35)
        fill(data, width, 30, 18, 34, 35)
        fill(data, width, 50, 18, 54, 35)
        fill(data, width, 70, 18, 74, 35)

        val metrics = InkAnalyzer.analyze(data, width, height)

        assertEquals(0f, metrics.slant, 0.1f, "upright stems read as ${metrics.slant}")
    }

    @Test
    fun `stems leaning to the right are italic`() {
        val width = 120
        val height = 40
        val data = mask(width, height)
        // Four stems of 3px, each row shifted half a pixel right: about a 12 degree slant.
        for (row in 0 until height) {
            val shift = Math.round(row * 0.5f)
            for (stem in intArrayOf(10, 30, 50, 70)) {
                val start = stem + shift
                for (x in start until start + 3) if (x in 0 until width) data[row * width + x] = 1
            }
        }

        val metrics = InkAnalyzer.analyze(data, width, height)

        assertTrue(metrics.slant > 0.25f, "a 0.5px-per-row slant read as ${metrics.slant}")
    }

    @Test
    fun `an empty mask measures nothing`() {
        val metrics = InkAnalyzer.analyze(ByteArray(40 * 20), 40, 20)

        assertEquals(InkMetrics.Empty, metrics)
    }
}
