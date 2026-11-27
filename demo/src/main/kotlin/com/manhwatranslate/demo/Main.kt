package com.manhwatranslate.demo

import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.pipeline.PipelineOptions
import com.manhwatranslate.core.pipeline.TranslationPipeline
import com.manhwatranslate.core.render.RenderStyle
import com.manhwatranslate.core.translate.TranslationService
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO

/**
 * Runs the full pipeline on a synthetic manhwa page and writes proof images to ./out.
 * This is how the pixel algorithms are verified without an Android device.
 */
fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val outputDir = File(args.firstOrNull() ?: "out").apply { mkdirs() }

    val measurer = AwtTextMeasurer()
    val page = SyntheticPage(measurer).render()
    ImageIO.write(page.image, "png", File(outputDir, "01-source.png"))

    val source = page.image.toArgbImage()
    val pipeline = TranslationPipeline(
        recognizer = GroundTruthRecognizer(page.texts),
        translationService = TranslationService(PhrasebookTranslator(Phrasebook.ENGLISH_TO_VIETNAMESE)),
        measurer = measurer,
        compositor = Java2DCompositor(measurer),
    )

    val options = PipelineOptions(
        script = SourceScript.AUTO,
        target = TranslationLanguage.VIETNAMESE,
        eraseMode = EraseMode.AUTO,
        render = RenderStyle(),
        maxTileHeight = 600,
    )

    val started = System.currentTimeMillis()
    val result = runBlocking {
        pipeline.process(source, options) { progress ->
            println("[${progress.stage}] ${(progress.fraction * 100).toInt()}% ${progress.message}")
        }
    }
    val elapsed = System.currentTimeMillis() - started

    ImageIO.write(result.image.toBufferedImage(), "png", File(outputDir, "02-translated.png"))
    ImageIO.write(result.cleanedImage.toBufferedImage(), "png", File(outputDir, "03-cleaned.png"))

    val bubble = RectI(70, 150, 520, 430)
    writePng(source, bubble, "04-bubble-before.png", outputDir)
    writePng(result.cleanedImage, bubble, "05-bubble-erased.png", outputDir)
    writePng(result.image, bubble, "06-bubble-after.png", outputDir)

    val sfx = RectI(560, 470, 940, 660)
    writePng(source, sfx, "07-sfx-before.png", outputDir)
    writePng(result.cleanedImage, sfx, "08-sfx-erased.png", outputDir)
    writePng(result.image, sfx, "09-sfx-after.png", outputDir)

    println()
    println("Vùng chữ: ${result.regions.size}")
    result.regions.forEach { region ->
        val translation = result.translations.firstOrNull { it.regionId == region.id }
        println("  #${region.id} ${region.box}  \"${region.text}\" -> \"${translation?.translatedText}\"")
    }
    println()

    println("Thời gian: ${elapsed}ms, pipeline báo ${result.elapsedMillis}ms")
    if (result.warnings.isNotEmpty()) {
        println("Cảnh báo:")
        result.warnings.forEach { println("  - $it") }
    }

    println()
    println("Mực còn sót trong từng khung chữ:")
    var beforeTotal = 0
    var afterTotal = 0
    for (region in result.regions) {
        val before = inkPixels(source, region.box)
        val after = inkPixels(result.cleanedImage, region.box)
        beforeTotal += before
        afterTotal += after
        val percent = if (before == 0) 0f else after * 100f / before
        println("  #${region.id} ${region.box} mực ${before} -> ${after} (${"%.1f".format(percent)}%)")
    }
    println("Tổng mực trong khung chữ: $beforeTotal -> $afterTotal")
    println("Ảnh đã ghi vào ${outputDir.absolutePath}")
}

private fun writePng(image: ArgbImage, rect: RectI, name: String, dir: File) {
    val crop = image.crop(rect)
    ImageIO.write(crop.toBufferedImage(), "png", File(dir, name))
}

private fun inkPixels(image: ArgbImage, rect: RectI): Int {
    val clamped = rect.clamped(image.width, image.height) ?: return 0
    val values = ArrayList<Float>()
    for (y in clamped.top until clamped.bottom) {
        for (x in clamped.left until clamped.right) {
            values.add(Colors.luminance(image.get(x, y)))
        }
    }
    if (values.isEmpty()) return 0
    values.sort()
    val background = values[values.size / 2]
    return values.count { kotlin.math.abs(it - background) > 60f }
}
