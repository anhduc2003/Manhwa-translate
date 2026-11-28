package com.manhwatranslate.core.pipeline

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.layout.FakeMonospaceMeasurer
import com.manhwatranslate.core.model.PageProcessingResult
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.render.PageCompositor
import com.manhwatranslate.core.render.RenderItem
import com.manhwatranslate.core.scan.ImageTile
import com.manhwatranslate.core.scan.SfxSensitivity
import com.manhwatranslate.core.scan.TextDetector
import com.manhwatranslate.core.translate.TranslationProvider
import com.manhwatranslate.core.translate.TranslationService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextDetectorPipelineTest {

    private val bubble = RectI(300, 250, 700, 320)

    /** The small credit line the OCR pass on the whole page does not report. */
    private val credit = RectI(100, 500, 300, 540)

    private fun page(): ArgbImage {
        val image = ArgbImage.filled(900, 600, Colors.opaque(252, 252, 252))
        for (y in bubble.top until bubble.bottom) {
            for (x in bubble.left until bubble.right) {
                if ((x - bubble.left) / 8 % 2 == 0) image.set(x, y, Colors.opaque(20, 20, 20))
            }
        }
        for (y in credit.top until credit.bottom) {
            for (x in credit.left until credit.right) {
                if ((x - credit.left) / 4 % 2 == 0) image.set(x, y, Colors.opaque(30, 30, 30))
            }
        }
        return image
    }

    /** Reads the bubble on a page-sized tile and the credit only when the tile is a crop of it. */
    private fun recognizer() = object : TileRecognizer {
        override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> {
            val coversCredit = tile.rect.width <= credit.width + 40 && tile.rect.height <= credit.height + 40
            if (!coversCredit) {
                return listOf(
                    TextRegion(
                        id = 0,
                        box = bubble,
                        lines = listOf(
                            TextLine(RectI(bubble.left, bubble.top, bubble.right, bubble.top + 35), "HELLO THERE"),
                            TextLine(RectI(bubble.left, bubble.top + 35, bubble.right, bubble.bottom), "OLD FRIEND"),
                        ),
                        text = "HELLO THERE OLD FRIEND",
                    ),
                )
            }
            return listOf(
                TextRegion(
                    id = 0,
                    box = RectI(4, 4, credit.width - 4, credit.height - 4),
                    lines = listOf(TextLine(RectI(4, 4, credit.width - 4, credit.height - 4), "ARTIST PYEONGHYUNG")),
                    text = "ARTIST PYEONGHYUNG",
                ),
            )
        }
    }

    private fun pipeline(detector: TextDetector?) = TranslationPipeline(
        recognizer = recognizer(),
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
        textDetector = detector,
    )

    private val detector = object : TextDetector {
        override fun detect(image: ArgbImage): List<RectI> = listOf(credit)
    }

    private fun run(detector: TextDetector?, missed: Boolean): PageProcessingResult = runBlocking {
        pipeline(detector).process(
            page(),
            PipelineOptions(
                script = SourceScript.LATIN,
                sfxSensitivity = SfxSensitivity.LOW,
                detectMissedText = missed,
            ),
        )
    }

    @Test
    fun `lettering the recognizer skipped is read when the detector is on`() {
        val result = run(detector, missed = true)
        val texts = result.regions.map { it.text }
        assertTrue(texts.any { it.contains("ARTIST PYEONGHYUNG") }, "expected the credit line, got $texts")
        assertEquals(2, result.regions.size)
        val found = result.regions.first { it.text.contains("ARTIST PYEONGHYUNG") }
        assertTrue(found.box.left in credit.left - 20..credit.right, "credit box landed at ${found.box}")
        assertTrue(found.box.top in credit.top - 20..credit.bottom, "credit box landed at ${found.box}")
    }

    @Test
    fun `nothing extra is read when the setting is off`() {
        val result = run(detector, missed = false)
        assertEquals(1, result.regions.size)
        assertTrue(result.regions.none { it.text.contains("ARTIST PYEONGHYUNG") })
    }

    @Test
    fun `a detector that throws leaves the page alone`() {
        val broken = object : TextDetector {
            override fun detect(image: ArgbImage): List<RectI> = throw IllegalStateException("no model")
        }
        val result = run(broken, missed = true)
        assertEquals(1, result.regions.size)
        assertTrue(result.warnings.any { it.contains("no model") }, "warnings were ${result.warnings}")
    }

    @Test
    fun `a proposal the OCR already reported is not read twice`() {
        val overlapping = object : TextDetector {
            override fun detect(image: ArgbImage): List<RectI> = listOf(bubble)
        }
        val result = run(overlapping, missed = true)
        assertEquals(1, result.regions.size)
    }
}
