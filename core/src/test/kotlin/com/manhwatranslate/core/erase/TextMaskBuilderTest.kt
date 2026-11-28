package com.manhwatranslate.core.erase

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextMaskBuilderTest {

    private val builder = TextMaskBuilder()

    private val textBlock = RectI(120, 90, 180, 110)
    private val regionBox = RectI(110, 80, 190, 120)

    private fun page(): ArgbImage {
        val image = ArgbImage.filled(300, 200, Colors.opaque(255, 255, 255))
        for (y in textBlock.top until textBlock.bottom) {
            for (x in textBlock.left until textBlock.right) {
                image.set(x, y, Colors.opaque(16, 16, 16))
            }
        }
        return image
    }

    private fun region(box: RectI = regionBox, text: String = "hello") = TextRegion(
        id = 0,
        box = box,
        lines = listOf(TextLine(textBlock, text)),
        text = text,
    )

    @Test
    fun `mask covers the lettering`() {
        val mask = requireNotNull(builder.build(page(), region()))
        val insideText = mask.at(textBlock.centerX - mask.left, textBlock.centerY - mask.top)
        assertTrue(insideText, "letters must be masked")
    }

    @Test
    fun `a stroke that reaches past the box is erased with the rest`() {
        val image = page()
        // A tail of a letter that was drawn outside the box OCR reported: a stray Y or P left
        // standing next to the translation is more obvious than the whole original sentence.
        // Connected to the lettering inside the box, so it is the same stroke continuing out.
        for (y in 92 until 108) {
            for (x in 100 until 126) image.set(x, y, Colors.opaque(16, 16, 16))
        }

        val mask = requireNotNull(builder.build(image, region()))

        // Just outside the detected box on the left, still inside the erase margin.
        val outside = mask.at(101 - mask.left, 100 - mask.top)
        assertTrue(outside, "a stroke 4px outside the box must be erased with the rest")
    }

    @Test
    fun `artwork in the margin is not swallowed by the mask`() {
        val image = page()
        // A red band just outside the detected box, the way bubble shading sits next to lettering.
        for (y in 86 until 114) {
            for (x in 96 until 104) image.set(x, y, Colors.opaque(200, 40, 40))
        }

        val mask = requireNotNull(builder.build(image, region()))

        val band = mask.at(100 - mask.left, 100 - mask.top)
        assertTrue(!band, "artwork beside the box must not be erased")
    }

    @Test
    fun `bubble background around the letters is kept`() {
        val mask = requireNotNull(builder.build(page(), region()))
        val nearCorner = mask.at(2, 2)
        val nearRight = mask.at(mask.width - 3, 2)
        assertTrue(!nearCorner, "background near the crop corner must be kept")
        assertTrue(!nearRight, "background near the crop corner must be kept")
        assertTrue(mask.coverage() in 0.05f..0.95f, "coverage ${mask.coverage()}")
    }

    @Test
    fun `mask keeps the bubble colour as background estimate`() {
        val mask = requireNotNull(builder.build(page(), region()))
        assertEquals(255, Colors.red(mask.backgroundArgb))
        assertEquals(255, Colors.green(mask.backgroundArgb))
        assertEquals(255, Colors.blue(mask.backgroundArgb))
        assertTrue(mask.backgroundContrast < 1f)
    }

    @Test
    fun `light text on a dark bubble is masked too`() {
        val image = ArgbImage.filled(300, 200, Colors.opaque(20, 20, 24))
        for (y in textBlock.top until textBlock.bottom) {
            for (x in textBlock.left until textBlock.right) {
                image.set(x, y, Colors.opaque(245, 245, 245))
            }
        }
        val mask = requireNotNull(builder.build(image, region()))
        assertTrue(mask.at(textBlock.centerX - mask.left, textBlock.centerY - mask.top))
        assertTrue(Colors.luminance(mask.backgroundArgb) < 60f)
    }

    @Test
    fun `nothing to erase on a blank bubble returns no mask`() {
        val image = ArgbImage.filled(300, 200, Colors.opaque(255, 255, 255))
        assertNull(builder.build(image, region(text = "")))
    }

    @Test
    fun `mask reaches a little past the box but not across the page`() {
        val mask = requireNotNull(builder.build(page(), region()))

        // Room for strokes drawn outside the detected box, bounded so a bad box cannot erase the page.
        val margin = (regionBox.height * builder.options.eraseMarginFactor).toInt()
        assertEquals(regionBox.left - 2 - margin, mask.left)
        assertEquals(regionBox.top - 2 - margin, mask.top)
        assertEquals(regionBox.width + 4 + 2 * margin, mask.width)
        assertEquals(regionBox.height + 4 + 2 * margin, mask.height)
    }
}
