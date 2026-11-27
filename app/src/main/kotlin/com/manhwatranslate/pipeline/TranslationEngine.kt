package com.manhwatranslate.pipeline

import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.model.PageProcessingResult
import com.manhwatranslate.core.model.PipelineProgress
import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.pipeline.PipelineOptions
import com.manhwatranslate.core.pipeline.TileRecognizer
import com.manhwatranslate.core.pipeline.TranslationPipeline
import com.manhwatranslate.core.render.PageCompositor
import com.manhwatranslate.core.scan.ImageTiler
import com.manhwatranslate.core.translate.TranslationProvider
import com.manhwatranslate.core.translate.TranslationService
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.render.AndroidTextMeasurer

/**
 * Wires the platform pieces (ML Kit OCR, a translation backend, Android text drawing) into the
 * core pipeline.
 */
class TranslationEngine(
    private val recognizerResolver: (AppSettings) -> TileRecognizer,
    private val measurer: AndroidTextMeasurer,
    private val compositor: PageCompositor,
    private val providerResolver: (AppSettings) -> TranslationProvider,
    private val backupResolver: (AppSettings) -> TranslationProvider? = { null },
    private val backupRecognizer: TileRecognizer? = null,
) {

    suspend fun processPage(
        page: ArgbImage,
        settings: AppSettings,
        onProgress: (PipelineProgress) -> Unit = {},
    ): PageProcessingResult {
        val pipeline = TranslationPipeline(
            recognizer = recognizerResolver(settings),
            translationService = TranslationService(
                providerResolver(settings),
                backup = backupResolver(settings),
            ),
            measurer = measurer,
            compositor = compositor,
            backupRecognizer = backupRecognizer,
        )
        val options = settings.toPipelineOptions().copy(
            maxTileHeight = tileHeightFor(page),
        )
        return pipeline.process(page, options, onProgress)
    }

    /** Finds the dialogue and translates it without drawing on the page. */
    suspend fun analyzePage(
        page: ArgbImage,
        settings: AppSettings,
        onProgress: (PipelineProgress) -> Unit = {},
    ): List<com.manhwatranslate.core.pipeline.OverlayBox> {
        val pipeline = pipeline(settings)
        val options = settings.toPipelineOptions().copy(maxTileHeight = tileHeightFor(page))
        return pipeline.analyze(page, options, onProgress)
    }

    /** Re-translates the same bubbles with the backend the user picked. */
    suspend fun refineOverlay(
        boxes: List<com.manhwatranslate.core.pipeline.OverlayBox>,
        settings: AppSettings,
    ): List<com.manhwatranslate.core.pipeline.OverlayBox> {
        if (boxes.isEmpty()) return boxes
        val service = TranslationService(
            providerResolver(settings),
            backup = backupResolver(settings),
        )
        val regions = boxes.mapIndexed { index, box ->
            TextRegion(id = index, box = box.box, text = box.source)
        }
        val batch = service.translate(
            regions = regions,
            script = settings.toPipelineOptions().script,
            target = settings.target,
        )
        val byId = batch.translations.associateBy { it.regionId }
        return boxes.mapIndexed { index, box ->
            val better = byId[index]?.translatedText?.takeIf { it.isNotBlank() && it != box.source }
            if (better == null) box else box.copy(text = better)
        }
    }

    private fun pipeline(settings: AppSettings): TranslationPipeline = TranslationPipeline(
        recognizer = recognizerResolver(settings),
        translationService = TranslationService(
            providerResolver(settings),
            backup = backupResolver(settings),
        ),
        measurer = measurer,
        compositor = compositor,
        backupRecognizer = backupRecognizer,
    )

    /**
     * Re-translates a finished page with the backend the user picked and draws it back over the
     * same erased artwork, which is how a page shows a readable translation immediately and a
     * better one as soon as the cloud model answers.
     */
    suspend fun refineTranslation(
        result: PageProcessingResult,
        settings: AppSettings,
    ): PageProcessingResult {
        if (result.regions.isEmpty()) return result
        val service = TranslationService(
            providerResolver(settings),
            backup = backupResolver(settings),
        )
        val options = settings.toPipelineOptions()
        val batch = service.translate(
            regions = result.regions,
            script = options.script,
            target = options.target,
            minLetterCount = options.minLetterCount,
        )
        val image = recompose(result.cleanedImage, result.regions, batch.translations, settings)
        return result.copy(
            image = image,
            translations = batch.translations,
            warnings = (result.warnings + batch.warnings).distinct(),
        )
    }

    fun recompose(
        cleaned: ArgbImage,
        regions: List<TextRegion>,
        translations: List<RegionTranslation>,
        settings: AppSettings,
        options: PipelineOptions = settings.toPipelineOptions(),
    ): ArgbImage {
        val pipeline = TranslationPipeline(
            recognizer = recognizerResolver(settings),
            translationService = TranslationService(
                providerResolver(settings),
                backup = backupResolver(settings),
            ),
            measurer = measurer,
            compositor = compositor,
            backupRecognizer = backupRecognizer,
        )
        return pipeline.recompose(cleaned, regions, translations, options)
    }

    private fun tileHeightFor(page: ArgbImage): Int =
        minOf(ImageTiler.DEFAULT_MAX_TILE_HEIGHT, maxOf(600, page.height / 2))
}
