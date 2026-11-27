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
import com.manhwatranslate.core.translate.mergeTranslations
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
        val translations = mergeTranslations(result.translations, batch.translations)
        val image = recompose(result.cleanedImage, result.regions, translations, settings)
        return result.copy(
            image = image,
            translations = translations,
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
