package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegionSplitterTest {

    private val paper = Colors.opaque(252, 252, 252)
    private val ink = Colors.opaque(20, 20, 20)

    private fun line(left: Int, top: Int, right: Int, bottom: Int, text: String) =
        TextLine(RectI(left, top, right, bottom), text)

    private fun region(vararg lines: TextLine): TextRegion {
        val box = lines.map { it.box }.reduce { left, right -> left.union(right) }
        return TextRegion(
            id = 0,
            box = box,
            lines = lines.toList(),
            text = lines.joinToString(" ") { it.text },
        )
    }

    private fun page(width: Int = 1080, height: Int = 2050): ArgbImage =
        ArgbImage.filled(width, height, paper)

    /** Draws a bubble outline: a horizontal stroke across [left]..[right] at [y]. */
    private fun ArgbImage.outline(left: Int, right: Int, y: Int, thickness: Int = 3) {
        for (row in y until y + thickness) {
            for (x in left until right) set(x, row, ink)
        }
    }

    /**
     * The page the user reported: a big bubble with four lines and a small bubble just below it,
     * read as one six-line block. The two bubbles sit 156px apart on a page whose lines are 52px
     * tall, while the lines inside each bubble are 5-8px apart.
     */
    @Test
    fun `the reported double bubble is cut into two regions`() {
        val double = region(
            line(381, 856, 563, 910, "I CAN'T"),
            line(236, 915, 712, 970, "HAVE MY STUDENT"),
            line(254, 976, 697, 1027, "GETTING HIMSELF"),
            line(286, 1034, 658, 1085, "INTO TROUBLE."),
            line(572, 1241, 822, 1293, "DON'T YOU"),
            line(573, 1301, 825, 1350, "THINK SO?"),
        )
        val split = RegionSplitter.split(page(), listOf(double))
        assertEquals(2, split.size)
        assertEquals("I CAN'T HAVE MY STUDENT GETTING HIMSELF INTO TROUBLE.", split[0].text)
        assertEquals("DON'T YOU THINK SO?", split[1].text)
        assertEquals(RectI(236, 856, 712, 1085), split[0].box)
        assertEquals(RectI(572, 1241, 825, 1350), split[1].box)
    }

    /** Two bubbles that touch, so the gap alone cannot separate them: the edge in the band does. */
    @Test
    fun `bubbles that touch are cut at the edge between them`() {
        val image = page()
        image.outline(210, 720, 300)
        val double = region(
            line(210, 120, 700, 168, "I CAN'T"),
            line(210, 178, 706, 226, "HAVE MY STUDENT"),
            line(254, 236, 700, 284, "GETTING HIMSELF"),
            line(287, 292, 660, 300, "INTO TROUBLE."),
            line(572, 320, 820, 368, "DON'T YOU"),
            line(572, 378, 820, 426, "THINK SO?"),
        )
        val split = RegionSplitter.split(image, listOf(double))
        assertEquals(2, split.size)
        assertEquals("I CAN'T HAVE MY STUDENT GETTING HIMSELF INTO TROUBLE.", split[0].text)
        assertEquals("DON'T YOU THINK SO?", split[1].text)
    }

    @Test
    fun `lines of a single bubble stay together`() {
        val single = region(
            line(96, 120, 404, 152, "TOI DA KHONG"),
            line(96, 158, 404, 190, "NGHI BUOC"),
            line(96, 196, 396, 228, "BUOC DI"),
        )
        val split = RegionSplitter.split(page(), listOf(single))
        assertEquals(1, split.size)
        assertEquals(3, split.first().lines.size)
    }

    @Test
    fun `a region with one line is left alone`() {
        val one = region(line(96, 120, 404, 160, "MOT DONG"))
        assertEquals(1, RegionSplitter.split(page(), listOf(one)).size)
    }

    @Test
    fun `a wide gap starts a new bubble even without an outline`() {
        val wide = region(
            line(96, 120, 404, 160, "MOT"),
            line(96, 340, 404, 380, "HAI"),
        )
        val split = RegionSplitter.split(page(), listOf(wide))
        assertEquals(2, split.size)
        assertEquals(listOf("MOT", "HAI"), split.map { it.text })
    }

    @Test
    fun `lettering on dark artwork is not split`() {
        val image = ArgbImage.filled(1080, 2050, Colors.opaque(18, 18, 22))
        val caption = region(
            line(96, 120, 404, 160, "ON THAT DAY"),
            line(96, 172, 404, 212, "A THOUGHT"),
        )
        assertEquals(1, RegionSplitter.split(image, listOf(caption)).size)
    }

    @Test
    fun `three stacked bubbles are cut into three regions`() {
        val image = page()
        image.outline(96, 404, 196)
        image.outline(96, 404, 272)
        val triple = region(
            line(96, 120, 404, 196, "MOT"),
            line(96, 208, 404, 272, "HAI"),
            line(96, 284, 404, 348, "BA"),
        )
        val split = RegionSplitter.split(image, listOf(triple))
        assertEquals(3, split.size)
        assertTrue(split.all { it.lines.size == 1 })
        assertEquals(listOf("MOT", "HAI", "BA"), split.map { it.text })
    }

    /** Lines whose boxes sit outside the region's box cannot be trusted against the image. */
    @Test
    fun `lines that do not fit their region box are left alone`() {
        val lines = arrayOf(
            line(96, 120, 404, 160, "MOT"),
            line(96, 340, 404, 380, "HAI"),
        )
        val mismatched = TextRegion(
            id = 0,
            box = RectI(0, 0, 200, 200),
            lines = lines.toList(),
            text = "MOT HAI",
        )
        assertEquals(1, RegionSplitter.split(page(), listOf(mismatched)).size)
    }
}
