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
        val bitmap = tile.image.toBitmap()
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
            if (models.size == 1) perModel.first() else merge(perModel)
        } finally {
            bitmap.recycle()
        }
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
                TextLine(
                    box = RectI(box.left, box.top, box.right, box.bottom).translated(tile.rect.left, tile.rect.top),
                    text = line.text,
                )
            }.filter { it.text.isNotBlank() }
            if (lines.isEmpty()) continue
            val box = block.boundingBox?.let {
                RectI(it.left, it.top, it.right, it.bottom).translated(tile.rect.left, tile.rect.top)
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
