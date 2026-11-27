package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegionMergerTest {

    private fun region(id: Int, box: RectI, text: String = "hello world") = TextRegion(
        id = id,
        box = box,
        lines = listOf(TextLine(box, text)),
        text = text,
    )

    @Test
    fun `duplicate detection from overlapping tiles collapses into one region`() {
        val a = region(0, RectI(100, 100, 300, 160))
        val b = region(1, RectI(104, 103, 298, 158))
        val refined = RegionMerger.refine(listOf(a, b))
        assertEquals(1, refined.size)
        assertEquals(RectI(100, 100, 300, 160), refined.first().box)
    }

    @Test
    fun `a paragraph cut at a tile boundary is stitched back together`() {
        val top = region(0, RectI(120, 100, 320, 140), "The gate has been")
        val bottom = region(1, RectI(120, 143, 300, 186), "closed for years.")
        val refined = RegionMerger.refine(listOf(top, bottom))
        assertEquals(1, refined.size)
        assertEquals(2, refined.first().lines.size)
        assertTrue(refined.first().text.contains("gate"))
        assertTrue(refined.first().text.contains("closed"))
    }

    @Test
    fun `two bubbles side by side are kept apart`() {
        val left = region(0, RectI(40, 100, 260, 220))
        val right = region(1, RectI(300, 100, 520, 220))
        assertEquals(2, RegionMerger.refine(listOf(left, right)).size)
    }

    @Test
    fun `regions are ordered top to bottom then left to right`() {
        val bottom = region(0, RectI(30, 500, 200, 560))
        val topRight = region(1, RectI(400, 100, 600, 160))
        val topLeft = region(2, RectI(30, 104, 200, 164))
        val refined = RegionMerger.refine(listOf(bottom, topRight, topLeft))
        assertEquals(0, refined[0].id)
        assertTrue(refined[0].box.top < 200)
        assertTrue(refined[0].box.left < 200)
        assertEquals(2, refined[2].id)
        assertTrue(refined[2].box.top > 400)
    }

    @Test
    fun `tiny or letter-less detections are dropped`() {
        val noise = region(0, RectI(10, 10, 14, 14), "|")
        val punctuation = region(1, RectI(100, 100, 160, 130), "!!!")
        val real = region(2, RectI(200, 200, 400, 260), "Hello")
        val refined = RegionMerger.refine(listOf(noise, punctuation, real))
        assertEquals(1, refined.size)
        assertEquals("Hello", refined.first().text)
    }

    @Test
    fun `ids are renumbered after refinement`() {
        val regions = listOf(
            region(7, RectI(10, 10, 200, 60)),
            region(9, RectI(10, 200, 200, 260)),
        )
        val refined = RegionMerger.refine(regions)
        assertEquals(listOf(0, 1), refined.map { it.id })
    }
}
