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
    private fun page(degrees: Double, centerY: Double = height / 2.0): ArgbImage {
        val image = ArgbImage.filled(width, height, Colors.opaque(255, 255, 255))
        val radians = Math.toRadians(degrees)
        val cx = width / 2.0
        val cy = centerY
        // Five separate strokes with gaps between them, the way a line of glyphs is drawn: one
        // unbroken bar would read as a panel border rather than as lettering.
        for (glyph in 0 until 5) {
            val first = -60 + glyph * 26
            for (step in first until first + 16) {
                val x = cx + step * 1.4 * cos(radians)
                val y = cy + step * 1.4 * sin(radians)
                for (thick in -4..4) {
                    val px = (x - thick * sin(radians)).toInt()
                    val py = (y + thick * cos(radians)).toInt()
                    if (px in 0 until width && py in 0 until height) image.set(px, py, Colors.opaque(15, 15, 15))
                }
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

    /** Behaves like the Android recognizer: boxes come back offset by the tile's own position. */
    private object OffsetAddingRecognizer : TileRecognizer {
        override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> {
            val image = tile.image
            var minY = Int.MAX_VALUE
            var maxY = -1
            var minX = Int.MAX_VALUE
            var maxX = -1
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (Colors.luminance(image.get(x, y)) < 120f) {
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                    }
                }
            }
            if (maxY < 0 || (maxY - minY) > 40 || (maxX - minX) < 100) return emptyList()
            val box = RectI(
                minX + tile.rect.left,
                minY + tile.rect.top,
                maxX + tile.rect.left,
                maxY + tile.rect.top,
            )
            return listOf(
                TextRegion(0, box, listOf(TextLine(box, "HELLO")), "HELLO"),
            )
        }
    }

    @Test
    fun `a region found in a turned tile lands on the page, not beside it`() {
        val compositor = CapturingCompositor()
        val pipeline = TranslationPipeline(
            recognizer = OffsetAddingRecognizer,
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

        val result = runBlocking {
            pipeline.process(
                page(degrees = 20.0, centerY = 200.0),
                PipelineOptions(
                    script = SourceScript.LATIN,
                    rotationAngles = listOf(24f),
                    maxTileHeight = 180,
                    sfxSensitivity = com.manhwatranslate.core.scan.SfxSensitivity.OFF,
                ),
            )
        }

        assertTrue(result.regions.isNotEmpty(), "the turned pass should have found the line")
        val region = result.regions.first()
        // The line sits at the middle of the second tile; a box that forgot the tile's own offset
        // lands a tile-height above it, which is how the phantom boxes appeared beside the page.
        assertTrue(
            kotlin.math.abs(region.box.centerY - 200) < 25,
            "region landed at ${region.box}, the line is at y=200",
        )
        assertTrue(
            kotlin.math.abs(region.box.centerX - width / 2) < 25,
            "region landed at ${region.box}, the line is at x=${width / 2}",
        )
    }

    private class CapturingCompositor : PageCompositor {
        val items = ArrayList<RenderItem>()
        override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage {
            this.items += items
            return page
        }
    }

    private fun pipeline(
        compositor: PageCompositor,
        recognizer: TileRecognizer = LevelRecognizer,
    ) = TranslationPipeline(
        recognizer = recognizer,
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
                PipelineOptions(
                    script = SourceScript.LATIN,
                    rotationAngles = listOf(-24f, 24f),
                    sfxSensitivity = com.manhwatranslate.core.scan.SfxSensitivity.OFF,
                ),
            )
        }

        assertEquals(1, result.regions.size, "the slanted line should be found by a turned pass")
        val rotation = result.regions.first().rotationDegrees
        assertTrue(abs(abs(rotation) - 24f) < 1f, "rotation reported as $rotation")
        // The line is drawn down to the right; the translation has to follow it that way, not
        // tilt back up as it did when the levelling angle was reported with a flipped sign.
        assertTrue(rotation > 0f, "a line going down to the right was reported as $rotation")
        assertEquals(1, compositor.items.size)
        assertEquals(rotation, compositor.items.first().rotationDegrees)
    }

    @Test
    fun `auto slant finds the line at its own angle and draws it back along the slant`() {
        val compositor = CapturingCompositor()
        val result = runBlocking {
            pipeline(compositor).process(
                page(degrees = 20.0),
                PipelineOptions(
                    script = SourceScript.LATIN,
                    autoSlant = true,
                    maxTileHeight = 180,
                    sfxSensitivity = com.manhwatranslate.core.scan.SfxSensitivity.OFF,
                ),
            )
        }

        assertEquals(1, result.regions.size, "the slanted line should be found without a fixed angle")
        val region = result.regions.first()
        // The angle the line is drawn at, not its mirror image: the translation has to run along
        // the original line, which goes down to the right for a positive angle.
        assertTrue(abs(region.rotationDegrees - 20f) < 4f, "rotation reported as ${region.rotationDegrees}")
        assertTrue(
            region.uprightWidthPx > region.uprightHeightPx,
            "a turned line is wider than it is tall, got ${region.uprightWidthPx}x${region.uprightHeightPx}",
        )
        assertEquals(1, compositor.items.size)
        assertEquals(region.rotationDegrees, compositor.items.first().rotationDegrees)
        assertTrue(
            compositor.items.first().layout.box.centerX == region.box.centerX &&
                compositor.items.first().layout.box.centerY == region.box.centerY,
            "the block must be laid out around the line it replaces",
        )
    }

    @Test
    fun `auto slant invents nothing on level lettering`() {
        val result = runBlocking {
            pipeline(
                compositor = CapturingCompositor(),
                recognizer = OffsetAddingRecognizer,
            ).process(
                page(degrees = 0.0),
                PipelineOptions(
                    script = SourceScript.LATIN,
                    autoSlant = true,
                    maxTileHeight = 180,
                    sfxSensitivity = com.manhwatranslate.core.scan.SfxSensitivity.OFF,
                ),
            )
        }

        assertEquals(1, result.regions.size)
        assertEquals(0f, result.regions.first().rotationDegrees, 0.01f)
    }

    @Test
    fun `the translation of a turned line is laid out in its upright box`() {
        val slanted = TextRegion(
            id = 0,
            box = RectI(40, 60, 340, 220),
            lines = listOf(TextLine(RectI(40, 60, 340, 220), "HELLO")),
            text = "HELLO",
            rotationDegrees = 20f,
            uprightWidthPx = 300,
            uprightHeightPx = 34,
        )
        val pipeline = pipeline(CapturingCompositor())
        val layout = pipeline.layoutFor(slanted, "XIN CHAO", PipelineOptions(script = SourceScript.LATIN))

        assertEquals(300, layout.box.width, "the layout must use the line's own width")
        assertEquals(34, layout.box.height, "the layout must use the line's own height")
        assertEquals(190, layout.box.centerX)
        assertEquals(140, layout.box.centerY)
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
