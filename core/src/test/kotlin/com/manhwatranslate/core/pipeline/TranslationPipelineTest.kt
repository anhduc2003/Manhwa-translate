package com.manhwatranslate.core.pipeline

import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.layout.FakeMonospaceMeasurer
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
import kotlin.math.sqrt

class TranslationPipelineTest {

    private val box = RectI(300, 250, 700, 320)

    /** Grainy bubble with letter-like bars, so surface fill and diffusion differ. */
    private fun grainyBubble(): ArgbImage {
        val random = java.util.Random(7)
        val image = ArgbImage(900, 600, IntArray(900 * 600) {
            val shade = 130 + random.nextInt(50)
            Colors.opaque(shade, shade, shade)
        })
        for (y in box.top until box.bottom) {
            for (x in box.left until box.right) {
                if ((x - box.left) / 8 % 2 == 0) image.set(x, y, Colors.opaque(15, 15, 15))
            }
        }
        return image
    }

    private fun pipeline(): TranslationPipeline = TranslationPipeline(
        recognizer = object : TileRecognizer {
            override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> = listOf(
                TextRegion(
                    id = 0,
                    box = box,
                    lines = listOf(
                        TextLine(RectI(box.left, box.top, box.right, box.top + 35), "HELLO THERE"),
                        TextLine(RectI(box.left, box.top + 35, box.right, box.bottom), "OLD FRIEND"),
                    ),
                    text = "HELLO THERE OLD FRIEND",
                ),
            )
        },
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
        measurer = FakeMonospaceMeasurer(),
        compositor = object : PageCompositor {
            override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage = page
        },
    )

    @Test
    fun `erase mode from options reaches the eraser`() {
        val solid = runPipeline(EraseMode.SOLID)
        val diffusion = runPipeline(EraseMode.DIFFUSION)
        val solidSpread = interiorSpread(solid)
        val diffusionSpread = interiorSpread(diffusion)
        println("SOLID spread=$solidSpread DIFFUSION spread=$diffusionSpread")
        assertTrue(solidSpread < 12f, "surface fill should leave a smooth patch, spread was $solidSpread")
        assertTrue(
            diffusionSpread > solidSpread * 1.5f,
            "diffusion should carry the grain in, spreads were $solidSpread and $diffusionSpread",
        )
    }

    @Test
    fun `the drawn size follows the size measured on the page`() {
        val pipeline = pipeline()
        val options = PipelineOptions(script = SourceScript.LATIN)
        val loud = TextRegion(
            id = 0,
            box = box,
            lines = listOf(TextLine(box, "HELLO")),
            text = "HELLO",
            originalTextSizePx = 46f,
        )
        val whispered = loud.copy(id = 1, originalTextSizePx = 13f)

        val loudLayout = pipeline.layoutFor(loud, "XIN CHAO", options)
        val whisperedLayout = pipeline.layoutFor(whispered, "XIN CHAO", options)

        assertTrue(
            loudLayout.textSizePx > whisperedLayout.textSizePx * 1.5f,
            "a 46px original drew at ${loudLayout.textSizePx} and a 13px one at ${whisperedLayout.textSizePx}",
        )
    }

    private fun interiorSpread(image: ArgbImage): Float {
        val inner = box.expanded(-4)
        val values = ArrayList<Float>()
        for (y in inner.top until inner.bottom) {
            for (x in inner.left until inner.right) values.add(Colors.luminance(image.get(x, y)))
        }
        val mean = values.average().toFloat()
        val variance = values.sumOf { val d = it - mean; (d * d).toDouble() } / values.size
        return sqrt(variance).toFloat()
    }

    private fun runPipeline(mode: EraseMode): ArgbImage = runBlocking {
        pipeline().process(
            grainyBubble(),
            PipelineOptions(script = SourceScript.LATIN, eraseMode = mode),
        ).cleanedImage
    }

    @Test
    fun `a very tall page is processed in bands without losing a region`() {
        val pageHeight = 4000
        val pageWidth = 900
        val image = ArgbImage.filled(pageWidth, pageHeight, Colors.opaque(250, 250, 250))
        val boxes = (0 until 8).map { index ->
            val top = 260 + index * 460
            RectI(120, top, 700, top + 40)
        }
        for (box in boxes) {
            for (y in box.top until box.bottom) {
                for (x in box.left until box.right) image.set(x, y, Colors.opaque(15, 15, 15))
            }
        }
        val pipeline = TranslationPipeline(
            recognizer = DarkBlocks,
            translationService = TranslationService(
                object : TranslationProvider {
                    override val id = "test"
                    override val displayName = "Test"
                    override suspend fun translate(
                        texts: List<String>,
                        sourceLanguage: SourceScript,
                        target: TranslationLanguage,
                    ): List<String> = texts.map { "DONG $it" }
                },
            ),
            measurer = FakeMonospaceMeasurer(),
            compositor = object : PageCompositor {
                override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage = page
            },
        )
        val result = runBlocking {
            pipeline.process(
                image,
                PipelineOptions(
                    script = SourceScript.LATIN,
                    maxTileHeight = 700,
                    maxBandHeight = 900,
                ),
            )
        }
        assertEquals(pageHeight, result.image.height)
        assertEquals(boxes.size, result.regions.size, "every block should survive the band seams")
        assertEquals(boxes.size, result.translations.size)
        result.regions.forEachIndexed { index, region ->
            assertEquals(boxes[index].top, region.box.top, "region $index moved")
            assertTrue(
                Colors.luminance(result.cleanedImage.get(region.box.left + 4, region.box.top + 4)) > 150f,
                "region $index was not erased",
            )
        }
    }

    /**
     * A banded page must reach the backend in one call. A long webtoon strip is cut into several
     * bands, and one round trip per band is what made cloud translation feel slow.
     */
    @Test
    fun `a banded page is translated in a single backend call`() {
        val pageHeight = 4000
        val pageWidth = 900
        val image = ArgbImage.filled(pageWidth, pageHeight, Colors.opaque(250, 250, 250))
        val boxes = (0 until 8).map { index ->
            val top = 260 + index * 460
            RectI(120, top, 700, top + 40)
        }
        for (box in boxes) {
            for (y in box.top until box.bottom) {
                for (x in box.left until box.right) image.set(x, y, Colors.opaque(15, 15, 15))
            }
        }
        var calls = 0
        val pipeline = TranslationPipeline(
            recognizer = DarkBlocks,
            translationService = TranslationService(
                object : TranslationProvider {
                    override val id = "test"
                    override val displayName = "Test"
                    override suspend fun translate(
                        texts: List<String>,
                        sourceLanguage: SourceScript,
                        target: TranslationLanguage,
                    ): List<String> {
                        calls++
                        return texts.map { "DONG $it" }
                    }
                },
            ),
            measurer = FakeMonospaceMeasurer(),
            compositor = object : PageCompositor {
                override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage = page
            },
        )
        val result = runBlocking {
            pipeline.process(
                image,
                PipelineOptions(
                    script = SourceScript.LATIN,
                    maxTileHeight = 700,
                    maxBandHeight = 900,
                ),
            )
        }
        assertEquals(1, calls, "the whole page should reach the backend in one call")
        assertEquals(boxes.size, result.regions.size)
        assertEquals(boxes.size, result.translations.size)
        assertTrue(result.translations.all { it.translatedText.startsWith("DONG ") })
    }

    @Test
    fun `a broken recognizer falls back to the backup`() {
        val page = ArgbImage.filled(600, 600, Colors.opaque(250, 250, 250))
        val box = RectI(120, 100, 320, 120)
        for (y in box.top until box.bottom) {
            for (x in box.left until box.right) page.set(x, y, Colors.opaque(20, 20, 20))
        }
        val broken = object : TileRecognizer {
            override suspend fun recognize(
                tile: ImageTile,
                script: SourceScript,
            ): List<TextRegion> = throw IllegalStateException("Lỗi dịch HTTP 404")
        }
        val working = object : TileRecognizer {
            override suspend fun recognize(
                tile: ImageTile,
                script: SourceScript,
            ): List<TextRegion> = listOf(
                TextRegion(
                    id = 0,
                    box = box,
                    lines = listOf(TextLine(box, "HELLO THERE MY FRIEND")),
                    text = "HELLO THERE MY FRIEND",
                ),
            )
        }
        val pipeline = TranslationPipeline(
            recognizer = broken,
            translationService = TranslationService(
                object : TranslationProvider {
                    override val id = "test"
                    override val displayName = "Test"
                    override suspend fun translate(
                        texts: List<String>,
                        sourceLanguage: SourceScript,
                        target: TranslationLanguage,
                    ): List<String> = texts.map { "DONG $it" }
                },
            ),
            measurer = FakeMonospaceMeasurer(),
            compositor = object : PageCompositor {
                override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage = page
            },
            backupRecognizer = working,
        )
        val result = runBlocking {
            pipeline.process(page, PipelineOptions(script = SourceScript.LATIN, maxTileHeight = 1800))
        }
        assertEquals(
            1,
            result.regions.size,
            "backup failed: warnings=${result.warnings} skipped=${result.skipped.size}",
        )
        assertTrue(result.warnings.any { it.contains("chuyển sang đọc chữ trên máy") })
    }

    @Test
    fun `a page the first pass misses is scanned again with smaller tiles`() {
        val page = ArgbImage.filled(600, 2000, Colors.opaque(250, 250, 250))
        val band = RectI(120, 100, 320, 120)
        for (y in band.top until band.bottom) {
            for (x in band.left until band.right) page.set(x, y, Colors.opaque(20, 20, 20))
        }
        val pipeline = TranslationPipeline(
            recognizer = object : TileRecognizer {
                override suspend fun recognize(
                    tile: ImageTile,
                    script: SourceScript,
                ): List<TextRegion> = if (tile.image.height in 700..1000) {
                    listOf(
                        TextRegion(
                            id = 0,
                            box = band,
                            lines = listOf(TextLine(band, "SMALL PRINT")),
                            text = "SMALL PRINT",
                        ),
                    )
                } else {
                    emptyList()
                }
            },
            translationService = TranslationService(
                object : TranslationProvider {
                    override val id = "test"
                    override val displayName = "Test"
                    override suspend fun translate(
                        texts: List<String>,
                        sourceLanguage: SourceScript,
                        target: TranslationLanguage,
                    ): List<String> = texts.map { "DONG $it" }
                },
            ),
            measurer = FakeMonospaceMeasurer(),
            compositor = object : PageCompositor {
                override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage = page
            },
        )
        val result = runBlocking {
            pipeline.process(
                page,
                PipelineOptions(script = SourceScript.LATIN, maxTileHeight = 1800),
            )
        }
        assertEquals(
            1,
            result.regions.size,
            "retry should find it; warnings=${result.warnings} skipped=${result.skipped.size}",
        )
    }

    @Test
    fun `a flat tile is not sent to the recognizer`() {
        val seen = ArrayList<Int>()
        val pipeline = TranslationPipeline(
            recognizer = object : TileRecognizer {
                override suspend fun recognize(
                    tile: ImageTile,
                    script: SourceScript,
                ): List<TextRegion> {
                    seen += tile.rect.top
                    return emptyList()
                }
            },
            translationService = TranslationService(
                object : TranslationProvider {
                    override val id = "test"
                    override val displayName = "Test"
                    override suspend fun translate(
                        texts: List<String>,
                        sourceLanguage: SourceScript,
                        target: TranslationLanguage,
                    ): List<String> = texts
                },
            ),
            measurer = FakeMonospaceMeasurer(),
            compositor = object : PageCompositor {
                override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage = page
            },
        )
        val page = ArgbImage.filled(600, 3000, Colors.opaque(250, 250, 250))
        runBlocking {
            pipeline.process(
                page,
                PipelineOptions(script = SourceScript.LATIN, maxTileHeight = 700),
            )
        }
        assertTrue(seen.isEmpty(), "an empty margin has no lettering to recognize")
    }

    /** Finds the dark blocks in whatever slice it is handed, like a real recognizer would. */
    private object DarkBlocks : TileRecognizer {        override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> {
            val found = ArrayList<TextRegion>()
            var currentTop = -1
            var currentBottom = -1
            var left = Int.MAX_VALUE
            var right = -1
            for (y in 0 until tile.image.height) {
                var darkRow = false
                for (x in 0 until tile.image.width) {
                    if (Colors.luminance(tile.image.get(x, y)) < 100f) {
                        darkRow = true
                        left = minOf(left, x)
                        right = maxOf(right, x + 1)
                    }
                }
                if (darkRow) {
                    if (currentTop < 0) currentTop = y
                    currentBottom = y + 1
                } else if (currentTop >= 0) {
                    found += blockOf(left, currentTop, right, currentBottom, found.size)
                    currentTop = -1
                    left = Int.MAX_VALUE
                    right = -1
                }
            }
            if (currentTop >= 0) found += blockOf(left, currentTop, right, currentBottom, found.size)
            return found.map { it.copy(box = it.box.translated(tile.rect.left, tile.rect.top)) }
        }

        private fun blockOf(left: Int, top: Int, right: Int, bottom: Int, index: Int): TextRegion {
            val box = RectI(left, top, right, bottom)
            return TextRegion(
                id = index,
                box = box,
                lines = listOf(TextLine(box, "TEXT BLOCK $index")),
                text = "TEXT BLOCK $index",
            )
        }
    }
}
