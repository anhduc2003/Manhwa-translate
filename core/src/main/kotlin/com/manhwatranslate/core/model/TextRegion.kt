package com.manhwatranslate.core.model

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.layout.FontSpec
import kotlinx.serialization.Serializable

/** A single recognized text line inside a region. */
@Serializable
data class TextLine(
    val box: RectI,
    val text: String,
)

/** A detected block of text (a speech bubble, a caption box, a sound effect). */
@Serializable
data class TextRegion(
    val id: Int,
    val box: RectI,
    val lines: List<TextLine> = emptyList(),
    val text: String = lines.joinToString(" ") { it.text }.trim(),
    val script: SourceScript = SourceScript.AUTO,
    val confidence: Float? = null,
    val font: FontSpec = FontSpec.Default,
) {
    val lineBoxes: List<RectI> get() = lines.map { it.box }
    val isBlank: Boolean get() = text.isBlank()
    val letterCount: Int get() = text.count { it.isLetterOrDigit() }

    fun withId(newId: Int): TextRegion = copy(id = newId)

    fun translated(dx: Int, dy: Int): TextRegion = copy(
        box = box.translated(dx, dy),
        lines = lines.map { it.copy(box = it.box.translated(dx, dy)) },
    )
}

@Serializable
data class RegionTranslation(
    val regionId: Int,
    val sourceText: String,
    val translatedText: String,
    val status: TranslationStatus = TranslationStatus.DONE,
    val note: String? = null,
) {
    val isEmpty: Boolean get() = translatedText.isBlank()
}

@Serializable
enum class TranslationStatus { DONE, SKIPPED, FAILED, EDITED }

/** Result of running detection + erase + translate + render over one page. */
data class PageProcessingResult(
    val image: com.manhwatranslate.core.image.ArgbImage,
    val cleanedImage: com.manhwatranslate.core.image.ArgbImage,
    val regions: List<TextRegion>,
    val translations: List<RegionTranslation>,
    val skipped: List<TextRegion> = emptyList(),
    val warnings: List<String> = emptyList(),
    val elapsedMillis: Long = 0L,
)

enum class PipelineStage {
    PREPARING,
    DETECTING,
    ERASING,
    TRANSLATING,
    RENDERING,
    DONE,
    FAILED,
}

data class PipelineProgress(
    val stage: PipelineStage,
    val fraction: Float,
    val message: String,
) {
    companion object {
        fun of(stage: PipelineStage, fraction: Float, message: String = ""): PipelineProgress =
            PipelineProgress(stage, fraction.coerceIn(0f, 1f), message)
    }
}
