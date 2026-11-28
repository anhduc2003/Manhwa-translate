package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeatmapRegionsTest {

    private fun prob(blocks: List<RectI>): FloatArray {
        val values = FloatArray(HeatmapRegions.INPUT * HeatmapRegions.INPUT)
        for (block in blocks) {
            for (y in block.top until block.bottom) {
                for (x in block.left until block.right) {
                    values[y * HeatmapRegions.INPUT + x] = 1f
                }
            }
        }
        return values
    }

    @Test
    fun `tiles cover a narrow page edge to edge`() {
        val tiles = HeatmapRegions.tiles(720, 15000)
        assertTrue(tiles.isNotEmpty())
        assertEquals(0, tiles.minOf { it.left })
        assertEquals(720, tiles.maxOf { it.right })
        assertEquals(0, tiles.minOf { it.top })
        assertEquals(15000, tiles.maxOf { it.bottom })
        tiles.forEach {
            assertEquals(HeatmapRegions.TILE, it.width)
            assertEquals(HeatmapRegions.TILE, it.height)
        }
    }

    @Test
    fun `neighbouring tiles overlap so nothing sits on a seam`() {
        val tiles = HeatmapRegions.tiles(720, 15000).filter { it.top == 0 }
        assertTrue(tiles.size >= 2)
        for (index in 1 until tiles.size) {
            assertTrue(tiles[index].left < tiles[index - 1].right)
        }
    }

    @Test
    fun `a page smaller than a tile is one tile`() {
        val tiles = HeatmapRegions.tiles(200, 300)
        assertEquals(1, tiles.size)
        assertEquals(RectI(0, 0, HeatmapRegions.TILE, HeatmapRegions.TILE), tiles.first())
    }

    @Test
    fun `input is the tile averaged down to the model size`() {
        val page = ArgbImage.filled(HeatmapRegions.TILE, HeatmapRegions.TILE, Colors.opaque(255, 40, 8))
        val out = FloatArray(3 * HeatmapRegions.INPUT * HeatmapRegions.INPUT)
        HeatmapRegions.writeInput(page, RectI(0, 0, HeatmapRegions.TILE, HeatmapRegions.TILE), out)
        val plane = HeatmapRegions.INPUT * HeatmapRegions.INPUT
        for (index in 0 until plane) {
            assertEquals(1f, out[index], 1e-4f)
            assertEquals(40f / 255f, out[plane + index], 1e-4f)
            assertEquals(8f / 255f, out[2 * plane + index], 1e-4f)
        }
    }

    @Test
    fun `proposals are boxes around runs of lettering and skip specks`() {
        val boxes = HeatmapRegions.proposals(
            prob(listOf(RectI(10, 20, 40, 30), RectI(120, 150, 170, 180), RectI(90, 90, 92, 92))),
            threshold = 0.5f,
            minArea = 200,
        )
        assertEquals(2, boxes.size)
        assertTrue(boxes.contains(RectI(20, 40, 80, 60)))
        assertTrue(boxes.contains(RectI(240, 300, 340, 360)))
    }

    @Test
    fun `logits become probabilities`() {
        val prob = HeatmapRegions.probabilities(floatArrayOf(-40f, 0f, 2f, 40f))
        assertEquals(0f, prob[0], 1e-4f)
        assertEquals(0.5f, prob[1], 1e-6f)
        assertEquals(0.8808f, prob[2], 1e-4f)
        assertEquals(1f, prob[3], 1e-4f)
    }

    @Test
    fun `boxes that touch are joined`() {
        val merged = HeatmapRegions.mergeOverlapping(
            listOf(RectI(0, 0, 100, 40), RectI(100, 0, 200, 40), RectI(400, 400, 500, 440)),
        )
        assertEquals(listOf(RectI(0, 0, 200, 40), RectI(400, 400, 500, 440)), merged)
    }

    @Test
    fun `proposals the OCR boxes already cover are dropped`() {
        val known = listOf(RectI(0, 0, 100, 50))
        val covered = RectI(5, 5, 95, 45)
        val halfOutside = RectI(60, 20, 200, 60)
        val elsewhere = RectI(300, 300, 400, 340)
        val fresh = HeatmapRegions.uncovered(listOf(covered, halfOutside, elsewhere), known, 0.5f)
        assertEquals(listOf(halfOutside, elsewhere), fresh)
    }
}
