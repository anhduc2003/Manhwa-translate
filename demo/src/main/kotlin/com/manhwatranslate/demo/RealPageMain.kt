package com.manhwatranslate.demo

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.pipeline.PipelineOptions
import com.manhwatranslate.core.pipeline.TileRecognizer
import com.manhwatranslate.core.pipeline.TranslationPipeline
import com.manhwatranslate.core.render.RenderStyle
import com.manhwatranslate.core.scan.ImageTile
import com.manhwatranslate.core.scan.SfxSensitivity
import com.manhwatranslate.core.translate.TranslationProvider
import com.manhwatranslate.core.translate.TranslationService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.imageio.ImageIO

/**
 * Runs the real pipeline over a real manhwa page whose text boxes come from an external OCR
 * run, so the erase, layout and render stages are measured on artwork rather than on the
 * synthetic page.
 */
fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    if (args.size < 3) {
        println("usage: realpage <image> <ocr.json> <outDir>")
        return
    }
    val imageFile = File(args[0])
    val ocrFile = File(args[1])
    val outDir = File(args[2]).apply { mkdirs() }

    val source = ImageIO.read(imageFile).toArgbImage()
    val document = Json { ignoreUnknownKeys = true }.decodeFromString(OcrDocument.serializer(), ocrFile.readText())
    val regions = groupLines(document.boxes, source.width)

    println("=== ${imageFile.name} ${source.width}x${source.height} ===")
    println("Dòng OCR: ${document.boxes.size}, vùng chữ: ${regions.size}")

    val measurer = AwtTextMeasurer()
    val pipeline = TranslationPipeline(
        recognizer = FixedRegions(regions),
        translationService = TranslationService(PseudoTranslator()),
        measurer = measurer,
        compositor = Java2DCompositor(measurer),
    )
    val options = PipelineOptions(
        target = TranslationLanguage.VIETNAMESE,
        render = RenderStyle(),
        maxTileHeight = 1800,
        sfxSensitivity = SFX_LEVEL,
    )
    val started = System.currentTimeMillis()
    val result = runBlocking { pipeline.process(source, options) { } }
    val elapsed = System.currentTimeMillis() - started

    val prefix = imageFile.nameWithoutExtension
    ImageIO.write(result.image.toBufferedImage(), "png", File(outDir, "$prefix-translated.png"))
    ImageIO.write(source.toBufferedImage(), "png", File(outDir, "$prefix-source.png"))
    ImageIO.write(result.cleanedImage.toBufferedImage(), "png", File(outDir, "$prefix-erased.png"))

    val stats = result.regions.mapNotNull { region ->
        val box = region.box.expanded(-1).clamped(source.width, source.height) ?: return@mapNotNull null
        val before = luminanceStd(source, box)
        val after = luminanceStd(result.cleanedImage, box)
        before to after
    }
    val medianBefore = stats.map { it.first }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
    val medianAfter = stats.map { it.second }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
    val collapsed = stats.count { it.second < it.first * 0.5f }
    val outside = changedOutside(source, result.cleanedImage, result.regions)
    val inkPerRegion = result.regions.map { region ->
        val box = region.box.clamped(source.width, source.height) ?: return@map 0 to 0
        inkPixels(source, box) to inkPixels(result.cleanedImage, box)
    }
    val inkBefore = inkPerRegion.sumOf { it.first }
    val inkAfter = inkPerRegion.sumOf { it.second }
    println("Vùng xử lý: ${result.regions.size}, bỏ qua SFX: ${result.skipped.size}")
    println("Độ lệch chuẩn trong khung chữ: trước ${"%.1f".format(medianBefore)} -> sau ${"%.1f".format(medianAfter)}")
    println("Số vùng có nền phẳng hơn 50%% sau khi xoá: $collapsed/${stats.size}")
    println("Pixel bị đổi ngoài mọi khung chữ: $outside")
    println("Mực chữ trong các khung (pixel lệch nền > 60): $inkBefore -> $inkAfter")
    println("Số pixel mực còn sót trên mỗi khung, cao nhất: " +
        inkPerRegion.maxByOrNull { it.second }?.let { "trước ${it.first} -> sau ${it.second}" })
    val overflows = result.regions.count { region ->
        val translation = result.translations.firstOrNull { it.regionId == region.id } ?: return@count false
        val layout = pipeline.layoutFor(region, translation.translatedText, options)
        val widest = layout.lines.maxOfOrNull { measurer.measureWidth(it, layout.textSizePx, layout.font) } ?: 0f
        val stroke = layout.textSizePx * options.render.strokeWidthFactor * 2f
        widest + stroke > layout.box.width || layout.blockHeightPx > layout.box.height
    }
    println("Vùng có chữ tràn khỏi khung: $overflows/${result.regions.size}")
    println("Thời gian: ${elapsed}ms")
    println()
    println("Vùng chưa phẳng hẳn:")
    stats.forEachIndexed { index, (before, after) ->
        if (after >= before * 0.5f) {
            val region = result.regions.getOrNull(index)
            println("  #$index std ${"%.1f".format(before)} -> ${"%.1f".format(after)} box=${region?.box} \"${region?.text?.take(50)}\"" )
        }
    }
    if (result.skipped.isNotEmpty()) {
        println("Vùng bị bỏ qua:")
        result.skipped.take(12).forEach { region ->
            val analysis = com.manhwatranslate.core.scan.SfxFilter.analyze(source, region, source.width, SFX_LEVEL)
            println("  ${region.box} \"${region.text.take(40)}\" score=${analysis.score} ${analysis.reasons.joinToString("; ")}")
        }
    }
    if (result.warnings.isNotEmpty()) {
        println("Cảnh báo: ${result.warnings.take(3)}")
    }
    println("Ảnh: ${outDir.absolutePath}/$prefix-*.png")
    println()
}

private val SFX_LEVEL = SfxSensitivity.MEDIUM

/** Pixels that stand out from the box's own background, so letters count on light and dark bubbles alike. */
private fun inkPixels(image: ArgbImage, box: RectI): Int {
    val values = ArrayList<Float>()
    for (y in box.top until box.bottom) {
        for (x in box.left until box.right) values.add(Colors.luminance(image.get(x, y)))
    }
    if (values.isEmpty()) return 0
    values.sort()
    val background = values[values.size / 2]
    return values.count { kotlin.math.abs(it - background) > 60f }
}

private fun luminanceStd(image: ArgbImage, box: RectI): Float {
    var sum = 0.0
    var sumSquares = 0.0
    var count = 0
    for (y in box.top until box.bottom) {
        for (x in box.left until box.right) {
            val value = Colors.luminance(image.get(x, y)).toDouble()
            sum += value
            sumSquares += value * value
            count++
        }
    }
    if (count == 0) return 0f
    val mean = sum / count
    val variance = sumSquares / count - mean * mean
    return if (variance <= 0.0) 0f else kotlin.math.sqrt(variance).toFloat()
}

/** Pixels the erase stage touched that are not inside any detected text box. */
private fun changedOutside(source: ArgbImage, cleaned: ArgbImage, regions: List<TextRegion>): Long {
    val mask = Array(regions.size) { regions[it].box.expanded(3) }
    var changed = 0L
    for (y in 0 until source.height step 2) {
        for (x in 0 until source.width step 2) {
            if (mask.any { it.contains(x, y) }) continue
            if (Colors.distance(source.get(x, y), cleaned.get(x, y)) > 18f) changed++
        }
    }
    return changed
}

@Serializable
private data class OcrBox(
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int,
    val text: String,
    val score: Float = 0f,
)

@Serializable
private data class OcrDocument(
    val width: Int,
    val height: Int,
    val boxes: List<OcrBox>,
)

private fun groupLines(boxes: List<OcrBox>, pageWidth: Int): List<TextRegion> {
    val sorted = boxes.filter { it.text.isNotBlank() }.sortedBy { it.y0 }
    val groups = ArrayList<MutableList<OcrBox>>()
    for (box in sorted) {
        var placed = false
        for (group in groups) {
            val last = group.last()
            val height = (last.y1 - last.y0).coerceAtLeast(1)
            val gap = box.y0 - last.y1
            val overlap = minOf(box.x1, last.x1) - maxOf(box.x0, last.x0)
            val minWidth = minOf(box.x1 - box.x0, last.x1 - last.x0).coerceAtLeast(1)
            val sameBand = kotlin.math.abs(box.x0 - last.x0) < minWidth * 0.6f
            if (gap in -height / 2..(height * 0.9f).toInt() && overlap > minWidth * 0.45f && sameBand) {
                group += box
                placed = true
                break
            }
        }
        if (!placed) groups += mutableListOf(box)
    }
    return groups.mapIndexed { index, group ->
        val lines = group.map { box ->
            TextLine(RectI(box.x0, box.y0, box.x1, box.y1), box.text)
        }.sortedBy { it.box.top }
        val box = lines.map { it.box }.reduce { acc, rect -> acc.union(rect) }.expanded(1)
        TextRegion(
            id = index,
            box = box,
            lines = lines,
            text = lines.joinToString(" ") { it.text },
            script = SourceScript.LATIN,
        )
    }.filter { it.letterCount > 0 }
}

/** Hands the pipeline the pre-computed regions for each tile, like an OCR would. */
private class FixedRegions(private val regions: List<TextRegion>) : TileRecognizer {
    override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> {
        kotlinx.coroutines.delay(1)
        return regions.filter { tile.rect.contains(it.box.centerX, it.box.centerY) }
    }
}

/** Length-matched fake translation so the layout is exercised with realistic proportions. */
private class PseudoTranslator : TranslationProvider {
    override val id: String = "pseudo"
    override val displayName: String = "Pseudo"
    override val recommendedBatchSize: Int = 24

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> = texts.map { pseudo(it) }

    private fun pseudo(text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return text
        val builder = StringBuilder()
        words.forEachIndexed { index, word ->
            if (index > 0) builder.append(' ')
            builder.append(vietnameseLike(word))
        }
        return builder.toString()
    }

    private fun vietnameseLike(word: String): String {
        val syllables = listOf("tôi", "không", "ngươi", "một", "cái", "chuyện", "này", "đi", "rồi",
            "là", "của", "như", "thế", "sẽ", "được", "trong", "mà", "đã", "có", "khi", "rất", "vẫn")
        val cleaned = word.filter { it.isLetter() }
        if (cleaned.isEmpty()) return word
        val target = cleaned.length
        val candidates = syllables.filter { it.length <= maxOf(2, target + 1) }
        val pick = candidates[(cleaned.hashCode().let { if (it < 0) -it else it }) % candidates.size.coerceAtLeast(1)]
        return if (cleaned.first().isUpperCase()) pick.replaceFirstChar { it.uppercase() } else pick
    }
}
