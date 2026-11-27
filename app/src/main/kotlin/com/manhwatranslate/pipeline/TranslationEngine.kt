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
) {

    suspend fun processPage(
        page: ArgbImage,
        settings: AppSettings,
        onProgress: (PipelineProgress) -> Unit = {},
    ): PageProcessingResult {
        val pipeline = TranslationPipeline(
            recognizer = recognizerResolver(settings),
            translationService = TranslationService(providerResolver(settings)),
            measurer = measurer,
            compositor = compositor,
        )
        val options = settings.toPipelineOptions().copy(
            maxTileHeight = tileHeightFor(page),
        )
        return pipeline.process(page, options, onProgress)
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
            translationService = TranslationService(providerResolver(settings)),
            measurer = measurer,
            compositor = compositor,
        )
        return pipeline.recompose(cleaned, regions, translations, options)
    }

    private fun tileHeightFor(page: ArgbImage): Int =
        minOf(ImageTiler.DEFAULT_MAX_TILE_HEIGHT, maxOf(600, page.height / 2))
}
