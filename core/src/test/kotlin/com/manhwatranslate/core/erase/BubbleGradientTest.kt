package com.manhwatranslate.core.erase

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot

/**
 * The patch under erased lettering has to match the bubble it sits in. A manhwa bubble usually
 * carries a soft gradient, and a text box is mostly ink, so the only place the background can be
 * read from is the ring around the box.
 */
class BubbleGradientTest {

    private val builder = TextMaskBuilder()
    private val width = 220
    private val height = 140

    private fun shadeAt(x: Int, y: Int): Int {
        val centreX = width * 0.3f
        val centreY = height * 0.35f
        val maxDistance = hypot(width.toFloat(), height.toFloat()) * 0.75f
        val distance = hypot(x - centreX, y - centreY) / maxDistance
        return (250f - 34f * distance).coerceIn(0f, 255f).toInt()
    }

    private fun backgroundAt(x: Int, y: Int): Int {
        val shade = shadeAt(x, y)
        return Colors.opaque(shade, shade, (shade + 6).coerceIn(0, 255))
    }

    /** A white bubble with a radial highlight, the way shaded speech bubbles are drawn. */
    private fun gradientBubble(): ArgbImage {
        val image = ArgbImage(width, height, IntArray(width * height))
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.set(x, y, backgroundAt(x, y))
            }
        }
        return image
    }

    /** A box that is almost all ink: four thick bars with thin gaps, like a full bubble. */
    private fun fillWithLettering(image: ArgbImage, box: RectI) {
        for (row in 0 until 4) {
            val bandHeight = (box.height - 8) / 4
            val top = box.top + 4 + row * bandHeight
            val bottom = top + bandHeight - 3
            for (y in top until bottom) {
                for (x in box.left + 3 until box.right - 3) {
                    image.set(x, y, Colors.opaque(14, 14, 16))
                }
            }
        }
    }

    private fun region(box: RectI) = TextRegion(
        id = 0,
        box = box,
        lines = listOf(TextLine(box, "dòng một"), TextLine(box, "dòng hai")),
        text = "dòng một dòng hai",
    )

    @Test
    fun `the patch keeps the bubble gradient instead of flattening it`() {
        val box = RectI(60, 40, 190, 100)
        val page = gradientBubble()
        fillWithLettering(page, box)

        val mask = requireNotNull(builder.build(page, region(box)))
        TextEraser(EraseOptions(mode = EraseMode.SOLID)).erase(page, mask)

        var totalError = 0.0
        var samples = 0
        for (y in box.top + 2 until box.bottom - 2) {
            for (x in box.left + 2 until box.right - 2) {
                totalError += Colors.distance(page.get(x, y), backgroundAt(x, y))
                samples++
            }
        }
        val meanError = totalError / samples

        assertTrue(
            meanError < 6.0,
            "the erased patch drifts from the bubble gradient by $meanError levels",
        )
    }

    @Test
    fun `a small box with almost no background inside still matches the bubble`() {
        // A tight caption box: the letters fill it edge to edge, so the only background visible
        // inside the box is a one pixel rim.
        val box = RectI(90, 60, 140, 78)
        val page = gradientBubble()
        for (y in box.top + 1 until box.bottom - 1) {
            for (x in box.left + 1 until box.right - 1) {
                page.set(x, y, Colors.opaque(14, 14, 16))
            }
        }

        val mask = requireNotNull(builder.build(page, region(box)))
        TextEraser(EraseOptions(mode = EraseMode.SOLID)).erase(page, mask)

        var totalError = 0.0
        var samples = 0
        for (y in box.top + 1 until box.bottom - 1) {
            for (x in box.left + 1 until box.right - 1) {
                totalError += Colors.distance(page.get(x, y), backgroundAt(x, y))
                samples++
            }
        }
        val meanError = totalError / samples

        assertTrue(
            meanError < 8.0,
            "a tight caption box is filled with a colour $meanError levels off the bubble",
        )
    }

    @Test
    fun `a box the mask barely understood is not repainted wholesale`() {
        // OCR sometimes hands back a box that is mostly clean paper with a couple of thin strokes.
        // Filling the whole box there leaves a rectangle of estimated colour on a bubble whose
        // gradient the estimate cannot follow, which reads as a visible patch.
        val box = RectI(70, 30, 170, 125)
        val page = gradientBubble()
        for (x in box.left + 20 until box.left + 30) {
            page.set(x, box.top + 20, Colors.opaque(12, 12, 12))
            page.set(x, box.top + 21, Colors.opaque(12, 12, 12))
        }

        val mask = requireNotNull(builder.build(page, region(box)))
        TextEraser(EraseOptions(mode = EraseMode.SOLID)).erase(page, mask)

        val untouched = Colors.distance(page.get(box.right - 4, box.bottom - 4), backgroundAt(box.right - 4, box.bottom - 4))
        assertTrue(untouched == 0f, "paper far from any letter was repainted ($untouched levels off)")
    }

    @Test
    fun `no dark lettering is left behind`() {
        val box = RectI(60, 40, 190, 100)
        val page = gradientBubble()
        fillWithLettering(page, box)

        val mask = requireNotNull(builder.build(page, region(box)))
        TextEraser(EraseOptions(mode = EraseMode.SOLID)).erase(page, mask)

        for (y in box.top + 2 until box.bottom - 2) {
            for (x in box.left + 2 until box.right - 2) {
                assertTrue(
                    Colors.luminance(page.get(x, y)) > 150f,
                    "pixel ($x,$y) still carries ink",
                )
            }
        }
    }
}
