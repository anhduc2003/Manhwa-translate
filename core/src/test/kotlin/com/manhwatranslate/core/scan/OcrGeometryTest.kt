package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OcrGeometryTest {

    @Test
    fun `a scaled box is divided before the tile offset is added`() {
        val measured = RectI(100, 200, 300, 260)
        val page = OcrGeometry.toPage(measured, scale = 2f, offsetX = 0, offsetY = 5000)
        assertEquals(RectI(50, 5100, 150, 5130), page)
    }

    @Test
    fun `a single band at native size keeps its coordinates`() {
        val measured = RectI(40, 60, 200, 100)
        assertEquals(measured, OcrGeometry.toPage(measured, 1f, 0, 0))
    }

    @Test
    fun `a zero scale is treated as native size`() {
        assertEquals(RectI(1, 2, 3, 4), OcrGeometry.toPage(RectI(1, 2, 3, 4), 0f, 0, 0))
    }
}
