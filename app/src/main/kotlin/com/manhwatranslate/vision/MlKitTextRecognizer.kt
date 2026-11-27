package com.manhwatranslate.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.pipeline.TileRecognizer
import com.manhwatranslate.core.scan.ImageTile
import com.manhwatranslate.core.scan.OcrGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * ML Kit on-device OCR. One recognizer per writing system; in auto mode every model runs and
 * the results are merged, keeping whichever model read a given bubble most plausibly.
 */
class MlKitTextRecognizer : TileRecognizer {

    private val latin: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val korean: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    private val chinese: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val japanese: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> {
        val scale = recognizeScale(tile.image.width, tile.image.height)
        val bitmap = tile.image.toBitmap().let { original ->
            if (scale <= 1.01f) {
                original
            } else {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(),
                    true,
                ).also { original.recycle() }
            }
        }
        return try {
            val models = if (script.isAuto) listOf(SourceScript.LATIN, SourceScript.KOREAN, SourceScript.CHINESE, SourceScript.JAPANESE)
            else listOf(script)
            val perModel = coroutineScope {
                models.map { model ->
                    async(Dispatchers.Default) {
                        runCatching { recognizeWith(model, bitmap, tile) }
                            .getOrDefault(emptyList<TextRegion>())
                    }
                }.awaitAll()
            }
            val mapped = perModel.map { model -> model.map { it.movedToPage(scale, tile) } }
            if (models.size == 1) mapped.first() else merge(mapped)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Narrow webtoon pages carry lettering that ML Kit reads better a little larger, so tiles are
     * upscaled before recognition and the boxes are scaled back afterwards.
     */
    private fun recognizeScale(tileWidth: Int, tileHeight: Int): Float {
        val byWidth = if (tileWidth >= 1400) 1f else (1400f / tileWidth.toFloat()).coerceIn(1f, 2f)
        val pixels = (tileWidth.toFloat() * tileHeight.toFloat()).coerceAtLeast(1f)
        val byMemory = kotlin.math.sqrt(RECOGNIZE_PIXEL_BUDGET / pixels).coerceIn(1f, 2f)
        return minOf(byWidth, byMemory)
    }

    private companion object {
        /** Upscaling helps small lettering but a tile must still fit comfortably in memory. */
        const val RECOGNIZE_PIXEL_BUDGET = 4_000_000f
    }

    private fun TextRegion.movedToPage(scale: Float, tile: ImageTile): TextRegion {
        fun toPage(box: RectI) = OcrGeometry.toPage(box, scale, tile.rect.left, tile.rect.top)
        return copy(box = toPage(box), lines = lines.map { it.copy(box = toPage(it.box)) })
    }

    private suspend fun recognizeWith(
        script: SourceScript,
        bitmap: Bitmap,
        tile: ImageTile,
    ): List<TextRegion> {
        val client = recognizerFor(script)
        val result = client.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
        val regions = ArrayList<TextRegion>()
        for ((index, block) in result.textBlocks.withIndex()) {
            if (block.text.isBlank()) continue
            val lines = block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                TextLine(box = RectI(box.left, box.top, box.right, box.bottom), text = line.text)
            }.filter { it.text.isNotBlank() }
            if (lines.isEmpty()) continue
            val box = block.boundingBox?.let {
                RectI(it.left, it.top, it.right, it.bottom)
            } ?: lines.map { it.box }.reduce { acc, rect -> acc.union(rect) }
            regions += TextRegion(
                id = index,
                box = box,
                lines = lines,
                text = block.text,
                script = script,
                confidence = null,
            )
        }
        return regions
    }

    /** Keeps the most plausible reading for each bubble across the four OCR models. */
    private fun merge(perModel: List<List<TextRegion>>): List<TextRegion> {
        val scored = perModel.flatten()
            .map { region -> region to plausibility(region.text, region.script) }
            .filter { it.second > 0.15f }
            .sortedByDescending { it.second }

        val accepted = ArrayList<TextRegion>()
        for ((region, _) in scored) {
            val overlaps = accepted.any { existing ->
                existing.box.iou(region.box) > 0.35f ||
                    existing.box.coverageBy(region.box) > 0.7f ||
                    region.box.coverageBy(existing.box) > 0.7f
            }
            if (!overlaps) accepted += region
        }
        return accepted
    }

    private fun plausibility(text: String, script: SourceScript): Float {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return 0f
        var matching = 0
        for (char in text) {
            if (!char.isLetter()) continue
            val code = char.code
            val matches = when (script) {
                SourceScript.KOREAN -> code in 0xAC00..0xD7AF || code in 0x1100..0x11FF
                SourceScript.CHINESE -> code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF
                SourceScript.JAPANESE -> code in 0x3040..0x30FF || code in 0x4E00..0x9FFF
                SourceScript.LATIN -> code < 0x250
                SourceScript.AUTO -> true
            }
            if (matches) matching++
        }
        return matching.toFloat() / letters
    }

    private fun recognizerFor(script: SourceScript): TextRecognizer = when (script) {
        SourceScript.KOREAN -> korean
        SourceScript.CHINESE -> chinese
        SourceScript.JAPANESE -> japanese
        else -> latin
    }

    fun close() {
        listOf(latin, korean, chinese, japanese).forEach { runCatching { it.close() } }
    }
}

fun com.manhwatranslate.core.image.ArgbImage.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
