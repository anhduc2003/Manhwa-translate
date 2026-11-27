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
}
