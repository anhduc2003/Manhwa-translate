package com.manhwatranslate.core.pipeline

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.render.PageCompositor
import com.manhwatranslate.core.render.RenderItem
import com.manhwatranslate.core.scan.ImageTile
import com.manhwatranslate.core.translate.TranslationProvider
import com.manhwatranslate.core.translate.TranslationService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A lettering line drawn along a slant. The recognizer here behaves like the real ones: it only
 * reads a line that is level, so the slanted original produces nothing until the page is turned.
 */
class RotatedLetteringTest {

    private val width = 420
    private val height = 300

    /** A bar of [length] by [thickness] pixels, drawn [degrees] off level around the centre. */
    private fun page(degrees: Double): ArgbImage {
        val image = ArgbImage.filled(width, height, Colors.opaque(255, 255, 255))
        val radians = Math.toRadians(degrees)
        val cx = width / 2.0
        val cy = height / 2.0
        for (step in -60..60) {
            val x = cx + step * 1.4 * cos(radians)
            val y = cy + step * 1.4 * sin(radians)
            for (thick in -4..4) {
                val px = (x - thick * sin(radians)).toInt()
                val py = (y + thick * cos(radians)).toInt()
                if (px in 0 until width && py in 0 until height) image.set(px, py, Colors.opaque(15, 15, 15))
            }
        }
        return image
    }

    /**
     * Reads a level line only, the way an OCR pass that expects a straight baseline behaves: a
     * wide, flat band of ink is read, a slanted one is not.
     */
    private object LevelRecognizer : TileRecognizer {
        override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> {
            val image = tile.image
            var minX = Int.MAX_VALUE
            var maxX = -1
            var minY = Int.MAX_VALUE
            var maxY = -1
            var dark = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (Colors.luminance(image.get(x, y)) < 120f) {
                        dark++
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (dark < 300) return emptyList()
            val box = RectI(minX - 4, minY - 6, maxX + 4, maxY + 6)
            val flat = (maxY - minY) <= 40 && (maxX - minX) >= 100
            if (!flat) return emptyList()
            return listOf(
                TextRegion(
                    id = 0,
                    box = box,
                    lines = listOf(TextLine(box, "HELLO")),
                    text = "HELLO",
                ),
            )
        }
    }

    private class CapturingCompositor : PageCompositor {
        val items = ArrayList<RenderItem>()
        override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage {
            this.items += items
            return page
        }
    }

    private fun pipeline(compositor: PageCompositor) = TranslationPipeline(
        recognizer = LevelRecognizer,
        translationService = TranslationService(
            object : TranslationProvider {
                override val id = "test"
                override val displayName = "Test"
                override suspend fun translate(
                    texts: List<String>,
                    sourceLanguage: SourceScript,
                    target: TranslationLanguage,
                ): List<String> = texts.map { "XIN CHAO" }
            },
        ),
        measurer = com.manhwatranslate.core.layout.FakeMonospaceMeasurer(),
        compositor = compositor,
    )

    @Test
    fun `lettering drawn along a slant is translated and drawn along the same slant`() {
        val compositor = CapturingCompositor()
        val result = runBlocking {
            pipeline(compositor).process(
                page(degrees = 20.0),
                PipelineOptions(script = SourceScript.LATIN, rotationAngles = listOf(-24f, 24f)),
            )
        }

        assertEquals(1, result.regions.size, "the slanted line should be found by a turned pass")
        val rotation = result.regions.first().rotationDegrees
        assertTrue(abs(abs(rotation) - 24f) < 1f, "rotation reported as $rotation")
        assertEquals(1, compositor.items.size)
        assertEquals(rotation, compositor.items.first().rotationDegrees)
    }

    @Test
    fun `turning the retry off leaves the slanted line alone`() {
        val compositor = CapturingCompositor()
        val result = runBlocking {
            pipeline(compositor).process(
                page(degrees = 20.0),
                PipelineOptions(script = SourceScript.LATIN, rotationAngles = emptyList()),
            )
        }

        assertTrue(result.regions.isEmpty(), "a level-only pass must not invent a region")
        assertTrue(compositor.items.isEmpty())
    }
}
