package com.manhwatranslate.core.pipeline

import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.erase.TextEraser
import com.manhwatranslate.core.erase.TextMaskBuilder
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.layout.LayoutStyle
import com.manhwatranslate.core.layout.TextLayoutEngine
import com.manhwatranslate.core.layout.TextMeasurer
import com.manhwatranslate.core.model.PageProcessingResult
import com.manhwatranslate.core.model.PipelineProgress
import com.manhwatranslate.core.model.PipelineStage
import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.render.PageCompositor
import com.manhwatranslate.core.render.RenderItem
import com.manhwatranslate.core.render.RenderStyle
import com.manhwatranslate.core.scan.ImageTiler
import com.manhwatranslate.core.scan.ImageTile
import com.manhwatranslate.core.scan.RegionMerger
import com.manhwatranslate.core.translate.TranslationService
import kotlinx.coroutines.CancellationException

/** OCR backend operating on one tile of the page. */
interface TileRecognizer {
    suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion>
}

data class PipelineOptions(
    val script: SourceScript = SourceScript.AUTO,
    val target: TranslationLanguage = TranslationLanguage.VIETNAMESE,
    val eraseMode: EraseMode = EraseMode.AUTO,
    val render: RenderStyle = RenderStyle(),
    val minLetterCount: Int = 1,
    val maxTileHeight: Int = ImageTiler.DEFAULT_MAX_TILE_HEIGHT,
    val tileOverlap: Int = ImageTiler.DEFAULT_OVERLAP,
    val keepOriginalWhenFailed: Boolean = true,
    /** Lets translated text use a slightly larger box than the original letters occupied. */
    val boxExpansion: Float = 1.0f,
)

/**
 * The whole manhwa page flow: scan for text, erase it, translate it, draw the translation
 * back into the same bubbles.
 */
class TranslationPipeline(
    private val recognizer: TileRecognizer,
    private val translationService: TranslationService,
    private val measurer: TextMeasurer,
    private val compositor: PageCompositor,
    private val maskBuilder: TextMaskBuilder = TextMaskBuilder(),
    private val eraser: TextEraser = TextEraser(),
    private val mergerOptions: RegionMerger.Options = RegionMerger.Options(),
) {
    private val layoutEngine = TextLayoutEngine(measurer)

    suspend fun process(
        page: ArgbImage,
        options: PipelineOptions,
        onProgress: (PipelineProgress) -> Unit = {},
    ): PageProcessingResult {
        val started = System.currentTimeMillis()
        val warnings = ArrayList<String>()
        onProgress(PipelineProgress.of(PipelineStage.PREPARING, 0.02f, "Chuẩn bị trang"))

        val regions = detect(page, options, warnings, onProgress)

        onProgress(PipelineProgress.of(PipelineStage.ERASING, 0.5f, "Xoá chữ gốc"))
        val cleaned = erase(page, regions, options)

        if (regions.isEmpty()) {
            warnings += "Không tìm thấy vùng chữ nào trên trang này"
            onProgress(PipelineProgress.of(PipelineStage.DONE, 1f, "Hoàn tất"))
            return PageProcessingResult(
                image = page.copy(),
                cleanedImage = cleaned,
                regions = regions,
                translations = emptyList(),
                warnings = warnings,
                elapsedMillis = System.currentTimeMillis() - started,
            )
        }

        onProgress(PipelineProgress.of(PipelineStage.TRANSLATING, 0.62f, "Đang dịch ${regions.size} vùng chữ"))
        val batch = translationService.translate(
            regions = regions,
            script = options.script,
            target = options.target,
            minLetterCount = options.minLetterCount,
            onProgress = { fraction ->
                onProgress(
                    PipelineProgress.of(
                        PipelineStage.TRANSLATING,
                        0.62f + 0.18f * fraction,
                        "Đang dịch ${regions.size} vùng chữ",
                    ),
                )
            },
        )
        warnings += batch.warnings

        onProgress(PipelineProgress.of(PipelineStage.RENDERING, 0.84f, "Vẽ bản dịch"))
        val rendered = finalImageFrom(cleaned, regions, batch.translations, options, onProgress)
        onProgress(PipelineProgress.of(PipelineStage.DONE, 1f, "Hoàn tất"))

        return PageProcessingResult(
            image = rendered,
            cleanedImage = cleaned,
            regions = regions,
            translations = batch.translations,
            warnings = warnings,
            elapsedMillis = System.currentTimeMillis() - started,
        )
    }

    /** Re-draws every region's current translation onto the already-erased page. */
    fun recompose(
        cleaned: ArgbImage,
        regions: List<TextRegion>,
        translations: List<RegionTranslation>,
        options: PipelineOptions,
    ): ArgbImage = finalImageFrom(cleaned, regions, translations, options) {}

    fun layoutFor(region: TextRegion, text: String, options: PipelineOptions): com.manhwatranslate.core.layout.TextLayout {
        val style = options.render
        val targetBox = expandedBox(region.box, options.boxExpansion)
        val maxSize = minOf(style.maxTextSizePx, maxOf(style.minTextSizePx, targetBox.height.toFloat())) *
            style.textSizeScale.coerceAtLeast(0.2f)
        val layoutStyle = LayoutStyle(
            minTextSizePx = style.minTextSizePx * style.textSizeScale.coerceAtLeast(0.2f),
            maxTextSizePx = maxSize,
            lineSpacing = style.lineSpacing,
            maxLines = style.maxLines,
            bold = style.bold,
        )
        return layoutEngine.layout(text, targetBox, layoutStyle)
    }

    private fun expandedBox(box: com.manhwatranslate.core.geometry.RectI, factor: Float): com.manhwatranslate.core.geometry.RectI {
        if (factor <= 1.001f) return box
        val newWidth = (box.width * factor).toInt().coerceAtLeast(box.width)
        val newHeight = (box.height * factor).toInt().coerceAtLeast(box.height)
        val left = box.centerX - newWidth / 2
        val top = box.centerY - newHeight / 2
        return com.manhwatranslate.core.geometry.RectI(left, top, left + newWidth, top + newHeight)
    }

    private suspend fun detect(
        page: ArgbImage,
        options: PipelineOptions,
        warnings: MutableList<String>,
        onProgress: (PipelineProgress) -> Unit,
    ): List<TextRegion> {
        val tiles = ImageTiler.split(page, options.maxTileHeight, options.tileOverlap)
        val raw = ArrayList<TextRegion>()
        tiles.forEachIndexed { index, tile ->
            onProgress(
                PipelineProgress.of(
                    PipelineStage.DETECTING,
                    0.05f + 0.42f * (index.toFloat() / tiles.size.toFloat()),
                    "Nhận diện chữ (${index + 1}/${tiles.size})",
                ),
            )
            try {
                raw += recognizer.recognize(tile, options.script)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                warnings += "Nhận diện chữ thất bại ở khối ${index + 1}: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        val refined = RegionMerger.refine(raw, mergerOptions)
        onProgress(
            PipelineProgress.of(
                PipelineStage.DETECTING,
                0.47f,
                "Tìm thấy ${refined.size} vùng chữ",
            ),
        )
        return refined
    }

    private fun erase(
        page: ArgbImage,
        regions: List<TextRegion>,
        options: PipelineOptions,
    ): ArgbImage {
        val working = page.copy()
        for (region in regions) {
            val mask = maskBuilder.build(page, region) ?: continue
            eraser.erase(working, mask)
        }
        return working
    }

    private fun finalImageFrom(
        cleaned: ArgbImage,
        regions: List<TextRegion>,
        translations: List<RegionTranslation>,
        options: PipelineOptions,
        onProgress: (PipelineProgress) -> Unit,
    ): ArgbImage {
        val byId = translations.associateBy { it.regionId }
        val items = ArrayList<RenderItem>(regions.size)
        for (region in regions) {
            val translation = byId[region.id]
            val text = when {
                translation == null -> if (options.keepOriginalWhenFailed) region.text else ""
                translation.status == com.manhwatranslate.core.model.TranslationStatus.SKIPPED &&
                    !options.keepOriginalWhenFailed -> ""
                translation.status == com.manhwatranslate.core.model.TranslationStatus.FAILED &&
                    !options.keepOriginalWhenFailed -> ""
                else -> translation.translatedText
            }
            if (text.isBlank()) continue
            val layout = layoutFor(region, text, options)
            items += RenderItem(layout, options.render)
        }
        onProgress(
            PipelineProgress.of(
                PipelineStage.RENDERING,
                0.92f,
                "Vẽ ${items.size} khối chữ",
            ),
        )
        return compositor.compose(cleaned.copy(), items)
    }
}
