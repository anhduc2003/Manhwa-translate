package com.manhwatranslate.core.erase

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextEraserTest {

    private val builder = TextMaskBuilder()

    private fun bubblePage(): ArgbImage {
        val image = ArgbImage.filled(200, 120, Colors.opaque(250, 250, 250))
        for (y in 40 until 76) {
            for (x in 30 until 170) {
                image.set(x, y, Colors.opaque(12, 12, 12))
            }
        }
        return image
    }

    private fun region(box: RectI = RectI(29, 39, 171, 77)) = TextRegion(
        id = 0,
        box = box,
        lines = listOf(TextLine(box.expanded(-1), "block")),
        text = "block",
    )

    @Test
    fun `solid mode removes every dark pixel inside the bubble`() {
        val image = bubblePage()
        val mask = requireNotNull(builder.build(image, region()))
        val stats = TextEraser(EraseOptions(mode = EraseMode.SOLID)).erase(image, mask)
        assertTrue(stats.pixelsErased > 0)
        for (y in 40 until 76) {
            for (x in 30 until 170) {
                assertTrue(Colors.luminance(image.get(x, y)) > 200f, "pixel ($x,$y) still dark")
            }
        }
    }

    @Test
    fun `auto mode chooses solid for flat bubbles`() {
        val image = bubblePage()
        val mask = requireNotNull(builder.build(image, region()))
        val stats = TextEraser(EraseOptions(mode = EraseMode.AUTO)).erase(image, mask)
        assertEquals(EraseMode.SOLID, stats.mode)
    }

    @Test
    fun `auto mode chooses diffusion for textured backgrounds`() {
        val random = java.util.Random(11)
        val image = ArgbImage(200, 120, IntArray(200 * 120) { _ ->
            val shade = 90 + random.nextInt(90)
            Colors.opaque(shade, shade, shade)
        })
        val mask = requireNotNull(builder.build(image, region()))
        val stats = TextEraser(EraseOptions(mode = EraseMode.AUTO)).erase(image, mask)
        assertEquals(EraseMode.DIFFUSION, stats.mode)
    }

    @Test
    fun `diffusion keeps the gradient running through the erased letters`() {
        val image = ArgbImage(240, 120, IntArray(240 * 120) { index ->
            val x = index % 240
            val shade = 100 + x * 60 / 240
            Colors.opaque(shade, shade, shade)
        })
        for (y in 40 until 76) {
            for (x in 60 until 180) {
                image.set(x, y, Colors.opaque(20, 20, 20))
            }
        }
        val textBox = RectI(58, 39, 182, 77)
        val mask = requireNotNull(builder.build(image, region(textBox)))
        TextEraser(EraseOptions(mode = EraseMode.DIFFUSION)).erase(image, mask)

        val left = Colors.red(image.get(80, 58))
        val right = Colors.red(image.get(160, 58))
        val expectedLeft = 100 + 80 * 60 / 240
        val expectedRight = 100 + 160 * 60 / 240
        assertTrue(right > left + 15, "gradient lost inside the fill: $left -> $right")
        assertTrue(kotlin.math.abs(left - expectedLeft) < 20, "left fill $left vs $expectedLeft")
        assertTrue(kotlin.math.abs(right - expectedRight) < 20, "right fill $right vs $expectedRight")
    }

    @Test
    fun `erasing outside the mask never happens`() {
        val image = bubblePage()
        val mask = requireNotNull(builder.build(image, region()))
        val untouched = Colors.opaque(0, 0, 0)
        image.set(2, 2, untouched)
        TextEraser(EraseOptions(mode = EraseMode.SOLID)).erase(image, mask)
        assertEquals(untouched, image.get(2, 2))
    }
}
