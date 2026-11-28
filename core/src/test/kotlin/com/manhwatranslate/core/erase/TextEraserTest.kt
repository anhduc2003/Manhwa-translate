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

    /** Stands in for the trained fill: paints the masked pixels its own colour, leaves the rest alone. */
    private fun filler(paint: Int = Colors.opaque(232, 226, 240)) = object : BackgroundFiller {
        override fun fill(crop: ArgbImage, mask: TextMask): ArgbImage {
            val out = crop.copy()
            for (y in 0 until mask.height) {
                for (x in 0 until mask.width) {
                    if (mask.at(x, y)) out.set(mask.left + x, mask.top + y, paint)
                }
            }
            return out
        }
    }

    @Test
    fun `model mode repaints only the pixels the mask marks`() {
        val image = bubblePage()
        val mask = requireNotNull(builder.build(image, region()))
        val outside = image.get(5, 5)
        val stats = TextEraser(EraseOptions(mode = EraseMode.MODEL), filler()).erase(image, mask)
        assertEquals(EraseMode.MODEL, stats.mode)
        assertTrue(stats.pixelsErased > 0)
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (!mask.at(x, y)) continue
                val pixel = image.get(mask.left + x, mask.top + y)
                assertTrue(
                    Colors.luminance(pixel) > 200f,
                    "masked pixel (${mask.left + x},${mask.top + y}) was not repainted",
                )
            }
        }
        assertEquals(outside, image.get(5, 5), "pixels outside the mask must not move")
    }

    @Test
    fun `a fill that paints the wrong colour is slid back to the background`() {
        val image = bubblePage()
        val mask = requireNotNull(builder.build(image, region()))
        val wrong = object : BackgroundFiller {
            override fun fill(crop: ArgbImage, mask: TextMask): ArgbImage {
                val out = crop.copy()
                for (y in 0 until mask.height) {
                    for (x in 0 until mask.width) {
                        if (mask.at(x, y)) out.set(mask.left + x, mask.top + y, Colors.opaque(90, 60, 120))
                    }
                }
                return out
            }
        }
        TextEraser(EraseOptions(mode = EraseMode.MODEL), wrong).erase(image, mask)
        for (y in 40 until 76) {
            for (x in 30 until 170) {
                val pixel = image.get(x, y)
                assertTrue(
                    Colors.luminance(pixel) > 200f,
                    "pixel ($x,$y) kept the model's colour ${Colors.red(pixel)},${Colors.green(pixel)},${Colors.blue(pixel)}",
                )
            }
        }
    }

    @Test
    fun `a fill that is the right texture but the wrong brightness is slid back into place`() {
        val image = ArgbImage.filled(200, 120, Colors.opaque(60, 90, 120))
        for (y in 40 until 76) {
            for (x in 30 until 170) {
                image.set(x, y, Colors.opaque(250, 250, 250))
            }
        }
        val mask = requireNotNull(builder.build(image, region()))
        val bright = object : BackgroundFiller {
            override fun fill(crop: ArgbImage, mask: TextMask): ArgbImage {
                val out = crop.copy()
                for (y in 0 until mask.height) {
                    for (x in 0 until mask.width) {
                        if (mask.at(x, y)) out.set(mask.left + x, mask.top + y, Colors.opaque(96, 126, 156))
                    }
                }
                return out
            }
        }
        TextEraser(EraseOptions(mode = EraseMode.MODEL), bright).erase(image, mask)
        var red = 0
        var green = 0
        var blue = 0
        var count = 0
        for (y in 40 until 76) {
            for (x in 30 until 170) {
                val pixel = image.get(x, y)
                red += Colors.red(pixel)
                green += Colors.green(pixel)
                blue += Colors.blue(pixel)
                count++
            }
        }
        assertEquals(60.0, red.toDouble() / count, 6.0)
        assertEquals(90.0, green.toDouble() / count, 6.0)
        assertEquals(120.0, blue.toDouble() / count, 6.0)
    }

    @Test
    fun `auto mode prefers the model when one is available`() {
        val image = bubblePage()
        val mask = requireNotNull(builder.build(image, region()))
        val stats = TextEraser(EraseOptions(mode = EraseMode.AUTO), filler()).erase(image, mask)
        assertEquals(EraseMode.MODEL, stats.mode)
    }

    @Test
    fun `a filler that cannot run falls back to the surface fill`() {
        val image = bubblePage()
        val mask = requireNotNull(builder.build(image, region()))
        val silent = object : BackgroundFiller {
            override fun fill(crop: ArgbImage, mask: TextMask): ArgbImage? = null
        }
        val stats = TextEraser(EraseOptions(mode = EraseMode.MODEL), silent).erase(image, mask)
        assertTrue(stats.pixelsErased > 0)
        for (y in 40 until 76) {
            for (x in 30 until 170) {
                assertTrue(Colors.luminance(image.get(x, y)) > 200f, "pixel ($x,$y) still dark")
            }
        }
    }

    @Test
    fun `a filler that throws falls back too`() {
        val image = bubblePage()
        val mask = requireNotNull(builder.build(image, region()))
        val broken = object : BackgroundFiller {
            override fun fill(crop: ArgbImage, mask: TextMask): ArgbImage? =
                throw IllegalStateException("no model")
        }
        val stats = TextEraser(EraseOptions(mode = EraseMode.MODEL), broken).erase(image, mask)
        assertTrue(stats.pixelsErased > 0)
        assertTrue(Colors.luminance(image.get(100, 58)) > 200f)
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
