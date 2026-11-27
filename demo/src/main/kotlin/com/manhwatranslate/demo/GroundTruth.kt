package com.manhwatranslate.demo

import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.translate.TranslationProvider
import com.manhwatranslate.core.scan.ImageTile

/** Pretends to be the OCR engine: returns the boxes the synthetic page drew. */
class GroundTruthRecognizer(
    private val texts: List<SyntheticPage.GroundTruthText>,
) : com.manhwatranslate.core.pipeline.TileRecognizer {

    override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> {
        kotlinx.coroutines.delay(2)
        val tileRect = tile.rect
        return texts.withIndex()
            .filter { (_, truth) ->
                tileRect.contains(truth.box.centerX, truth.box.centerY)
            }
            .map { (index, truth) ->
                TextRegion(
                    id = index,
                    box = truth.box,
                    lines = truth.lines,
                    text = truth.text,
                    script = sourceScriptOf(truth.text),
                    confidence = 0.98f,
                )
            }
    }

    private fun sourceScriptOf(text: String): SourceScript = when {
        text.any { it.code in 0xAC00..0xD7AF } -> SourceScript.KOREAN
        text.any { it.code in 0x4E00..0x9FFF } -> SourceScript.CHINESE
        text.any { it.code in 0x3040..0x30FF } -> SourceScript.JAPANESE
        else -> SourceScript.LATIN
    }
}

/** A tiny offline phrasebook standing in for a paid translation API. */
class PhrasebookTranslator(
    private val phrasebook: Map<String, String>,
) : TranslationProvider {

    override val id: String = "phrasebook"
    override val displayName: String = "Phrasebook"
    override val recommendedBatchSize: Int = 12

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> = texts.map { text ->
        phrasebook[text] ?: "[${target.code.uppercase()}] $text"
    }
}

object Phrasebook {
    val ENGLISH_TO_VIETNAMESE: Map<String, String> = mapOf(
        "Chapter 12" to "Chương 12",
        "I never thought it would end like this." to
            "Tôi chưa từng nghĩ mọi chuyện lại kết thúc như thế này.",
        "Give me the sword and I'll let you live." to
            "Đưa thanh kiếm đây, ta sẽ để ngươi sống.",
        "CRASH!" to "RẦM!",
        "The gate has been closed for a hundred years. No one has come back alive." to
            "Cánh cổng này đã đóng suốt trăm năm. Chưa một ai quay về còn sống cả.",
        "You think you can run from me?" to
            "Ngươi nghĩ mình có thể chạy thoát khỏi ta sao?",
        "Psst… over here." to "Suỵt… ở đây này.",
        "The sword you are holding belongs to the fallen king. Every soldier in this city is looking for it." to
            "Thanh kiếm ngươi đang cầm là của vị vua đã mất. Mọi binh lính trong thành phố này đều đang tìm nó.",
    )

    fun translatedFor(region: TextRegion): RegionTranslation = RegionTranslation(
        regionId = region.id,
        sourceText = region.text,
        translatedText = ENGLISH_TO_VIETNAMESE[region.text] ?: region.text,
    )
}
