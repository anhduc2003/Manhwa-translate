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
    fun `a coloured mark in the bubble keeps its colour after the letters are erased`() {
        val image = bubblePage()
        for (y in 44 until 72) {
            for (x in 60 until 88) {
                val dx = (x - 74) / 14.0
                val dy = (y - 58) / 14.0
                if (dx * dx + dy * dy <= 1.0) image.set(x, y, Colors.opaque(206, 44, 60))
            }
        }
        val textBox = RectI(29, 39, 171, 77)
        val mask = requireNotNull(builder.build(image, region(textBox)))
        TextEraser(EraseOptions()).erase(image, mask)

        var red = 0
        for (y in 46 until 70) {
            for (x in 62 until 86) {
                val pixel = image.get(x, y)
                if (Colors.red(pixel) > 160 && Colors.green(pixel) < 110 && Colors.blue(pixel) < 120) red++
            }
        }
        assertTrue(red > 150, "the red mark was painted over as if it were lettering ($red px left)")
        assertTrue(Colors.luminance(image.get(160, 58)) > 200f, "the lettering beside it must still go")
    }

    @Test
    fun `the patch leaves no coloured specks on a gradient bubble`() {
        val width = 280
        val height = 160
        val image = ArgbImage(width, height, IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val shade = (246 - x * 18 / width - y * 10 / height).coerceIn(0, 255)
            Colors.opaque(shade, (shade - 6).coerceAtLeast(0), (shade - 18).coerceAtLeast(0))
        })
        for (y in 50 until 110) {
            for (x in 50 until 240) {
                if (y % 24 < 14 && (x / 9) % 2 == 0) image.set(x, y, Colors.opaque(20, 20, 20))
            }
        }
        val textBox = RectI(46, 46, 244, 114)
        val mask = requireNotNull(builder.build(image, region(textBox)))
        TextEraser(EraseOptions()).erase(image, mask)

        var worstChroma = 0f
        for (y in textBox.top + 4 until textBox.bottom - 4) {
            for (x in textBox.left + 4 until textBox.right - 4) {
                val pixel = image.get(x, y)
                val shade = (246 - x * 18 / width - y * 10 / height).toFloat()
                // How far the patch drifts from the walk of the bubble, per channel pair.
                val redShift = Colors.red(pixel) - shade
                val blueShift = Colors.blue(pixel) - (shade - 18)
                val chroma = kotlin.math.abs(redShift - blueShift)
                if (chroma > worstChroma) worstChroma = chroma
            }
        }
        assertTrue(worstChroma < 18f, "the fill left a colour drift of $worstChroma levels")
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
