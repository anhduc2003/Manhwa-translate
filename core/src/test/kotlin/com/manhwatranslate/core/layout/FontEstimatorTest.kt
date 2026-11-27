package com.manhwatranslate.core.layout

import com.manhwatranslate.core.erase.InkMetrics
import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FontEstimatorTest {

    private fun region(text: String, box: RectI, lines: Int = 1) = TextRegion(
        id = 0,
        box = box,
        lines = List(lines) { index ->
            TextLine(
                RectI(box.left, box.top + index * (box.height / lines), box.right, box.top + (index + 1) * (box.height / lines)),
                text,
            )
        },
        text = text,
    )

    @Test
    fun `thick strokes select a black weight`() {
        val result = FontEstimator.estimate(
            region = region("Hello", RectI(0, 0, 300, 40)),
            metrics = InkMetrics(coverage = 0.4f, strokeWidthPx = 14f, slant = 0f, inkCount = 500),
            preferredFamily = null,
            maxSizePx = 96f,
            minSizePx = 11f,
        )
        assertEquals(FontWeightLevel.BLACK, result.font.weight)
        assertTrue(result.strokeRatio > 0.3f)
    }

    @Test
    fun `thin strokes select a regular weight`() {
        val result = FontEstimator.estimate(
            region = region("Hello there", RectI(0, 0, 300, 40)),
            metrics = InkMetrics(coverage = 0.2f, strokeWidthPx = 2.5f, slant = 0f, inkCount = 300),
            preferredFamily = null,
            maxSizePx = 96f,
            minSizePx = 11f,
        )
        assertEquals(FontWeightLevel.REGULAR, result.font.weight)
        assertFalse(result.font.italic)
    }

    @Test
    fun `slant produces an italic font`() {
        val result = FontEstimator.estimate(
            region = region("Narration line", RectI(0, 0, 300, 40)),
            metrics = InkMetrics(coverage = 0.25f, strokeWidthPx = 5f, slant = 0.4f, inkCount = 300),
            preferredFamily = null,
            maxSizePx = 96f,
            minSizePx = 11f,
        )
        assertTrue(result.font.italic)
    }

    @Test
    fun `short all caps picks a condensed face`() {
        val result = FontEstimator.estimate(
            region = region("BOOM", RectI(0, 0, 300, 60)),
            metrics = InkMetrics(coverage = 0.3f, strokeWidthPx = 8f, slant = 0f, inkCount = 300),
            preferredFamily = null,
            maxSizePx = 96f,
            minSizePx = 11f,
        )
        assertEquals(FontFamilyKind.CONDENSED, result.font.family)
    }

    @Test
    fun `a pinned family wins over the heuristic`() {
        val result = FontEstimator.estimate(
            region = region("BOOM", RectI(0, 0, 300, 60)),
            metrics = InkMetrics(coverage = 0.3f, strokeWidthPx = 8f, slant = 0f, inkCount = 300),
            preferredFamily = FontFamilyKind.SERIF,
            maxSizePx = 96f,
            minSizePx = 11f,
        )
        assertEquals(FontFamilyKind.SERIF, result.font.family)
    }

    @Test
    fun `estimated size follows the original line height`() {
        val result = FontEstimator.estimate(
            region = region("Line one\nLine two", RectI(0, 0, 300, 80), lines = 2),
            metrics = InkMetrics(coverage = 0.2f, strokeWidthPx = 4f, slant = 0f, inkCount = 200),
            preferredFamily = null,
            maxSizePx = 96f,
            minSizePx = 11f,
        )
        assertTrue(result.estimatedTextSizePx in 25f..40f, "size ${result.estimatedTextSizePx}")
    }
}
