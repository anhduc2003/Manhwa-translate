package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegionGrouperTest {

    private fun whitePage(): ArgbImage = ArgbImage.filled(400, 600, Colors.opaque(252, 252, 252))

    private fun block(box: RectI, text: String, id: Int = 0) = TextRegion(
        id = id,
        box = box,
        lines = listOf(TextLine(box, text)),
        text = text,
    )

    @Test
    fun `two blocks of one bubble become a single region`() {
        val image = whitePage()
        val upper = block(RectI(80, 100, 300, 130), "TOI DA KHONG")
        val lower = block(RectI(60, 138, 320, 168), "NGHI RANG BUOC BUOC")
        val grouped = RegionGrouper.group(image, listOf(upper, lower))
        assertEquals(1, grouped.size)
        assertEquals(RectI(60, 100, 320, 168), grouped.first().box)
        assertEquals("TOI DA KHONG NGHI RANG BUOC BUOC", grouped.first().text)
        assertEquals(2, grouped.first().lines.size)
    }

    @Test
    fun `blocks separated by artwork stay apart`() {
        val image = whitePage()
        for (y in 168 until 200) {
            for (x in 0 until image.width) {
                val shade = if ((x + y) % 12 < 6) 20 else 240
                image.set(x, y, Colors.opaque(shade, shade, shade))
            }
        }
        val upper = block(RectI(80, 100, 300, 130), "TOI DA KHONG")
        val lower = block(RectI(60, 205, 320, 235), "NGHI RANG BUOC BUOC")
        val grouped = RegionGrouper.group(image, listOf(upper, lower))
        assertEquals(2, grouped.size)
    }

    @Test
    fun `side by side bubbles are not merged`() {
        val image = whitePage()
        val left = block(RectI(40, 200, 180, 260), "TRAI")
        val right = block(RectI(220, 200, 360, 260), "PHAI")
        val grouped = RegionGrouper.group(image, listOf(left, right))
        assertEquals(2, grouped.size)
    }

    @Test
    fun `a chain of lines in one bubble collapses to one region`() {
        val image = whitePage()
        val texts = listOf("MOT", "HAI", "BA", "BON")
        val regions = texts.mapIndexed { index, text ->
            val top = 100 + index * 40
            block(RectI(100, top, 300, top + 28), text, index)
        }
        val grouped = RegionGrouper.group(image, regions)
        assertTrue(grouped.size <= 2, "expected the lines to join, got ${grouped.size}")
        assertEquals("MOT HAI BA BON", grouped.first().text)
    }
}
