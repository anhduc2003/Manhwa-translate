package com.manhwatranslate.core.pipeline

import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.erase.EraseOptions
import com.manhwatranslate.core.erase.TextEraser
import com.manhwatranslate.core.erase.TextMask
import com.manhwatranslate.core.erase.TextMaskBuilder
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.layout.FontEstimator
import com.manhwatranslate.core.layout.FontFamilyKind
import com.manhwatranslate.core.layout.FontMode
import com.manhwatranslate.core.layout.FontSpec
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
import com.manhwatranslate.core.scan.RegionGrouper
import com.manhwatranslate.core.scan.RegionMerger
import com.manhwatranslate.core.scan.SfxFilter
import com.manhwatranslate.core.scan.SfxSensitivity
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
    val sfxSensitivity: SfxSensitivity = SfxSensitivity.MEDIUM,
    val matchBackgroundColor: Boolean = true,
)

/**
 * The whole manhwa page flow: scan for text, drop sound effects, erase the lettering, translate
 * it, draw the translation back into the same bubbles with a font matched to the original.
 */
class TranslationPipeline(
    private val recognizer: TileRecognizer,
    private val translationService: TranslationService,
    private val measurer: TextMeasurer,
    private val compositor: PageCompositor,
    private val maskBuilder: TextMaskBuilder = TextMaskBuilder(),
    private val mergerOptions: RegionMerger.Options = RegionMerger.Options(),
    private val grouperOptions: RegionGrouper.Options = RegionGrouper.Options(),
) {
    private val layoutEngine = TextLayoutEngine(measurer)

    private data class PreparedPage(
        val regions: List<TextRegion>,
        val skipped: List<TextRegion>,
        val cleaned: ArgbImage,
    )

    suspend fun process(
        page: ArgbImage,
        options: PipelineOptions,
        onProgress: (PipelineProgress) -> Unit = {},
    ): PageProcessingResult {
        val started = System.currentTimeMillis()
        val warnings = ArrayList<String>()
        onProgress(PipelineProgress.of(PipelineStage.PREPARING, 0.02f, "Chuẩn bị trang"))

        val detected = detect(page, options, warnings, onProgress)
        onProgress(PipelineProgress.of(PipelineStage.ERASING, 0.5f, "Xoá chữ gốc"))
        val prepared = prepare(page, detected, options, warnings)

        if (prepared.regions.isEmpty()) {
            warnings += if (prepared.skipped.isEmpty()) {
                "Không tìm thấy vùng chữ nào trên trang này"
            } else {
                "Chỉ tìm thấy hiệu ứng âm thanh, không có lời thoại để dịch"
            }
            onProgress(PipelineProgress.of(PipelineStage.DONE, 1f, "Hoàn tất"))
            return PageProcessingResult(
                image = prepared.cleaned,
                cleanedImage = prepared.cleaned,
                regions = emptyList(),
                translations = emptyList(),
                skipped = prepared.skipped,
                warnings = warnings,
                elapsedMillis = System.currentTimeMillis() - started,
            )
        }

        onProgress(
            PipelineProgress.of(
                PipelineStage.TRANSLATING,
                0.62f,
                "Đang dịch ${prepared.regions.size} vùng chữ",
            ),
        )
        val batch = translationService.translate(
            regions = prepared.regions,
            script = options.script,
            target = options.target,
            minLetterCount = options.minLetterCount,
            onProgress = { fraction ->
                onProgress(
                    PipelineProgress.of(
                        PipelineStage.TRANSLATING,
                        0.62f + 0.18f * fraction,
                        "Đang dịch ${prepared.regions.size} vùng chữ",
                    ),
                )
            },
        )
        warnings += batch.warnings

        onProgress(PipelineProgress.of(PipelineStage.RENDERING, 0.84f, "Vẽ bản dịch"))
        val rendered = compose(prepared.cleaned, prepared.regions, batch.translations, options) { count ->
            onProgress(PipelineProgress.of(PipelineStage.RENDERING, 0.92f, "Vẽ $count khối chữ"))
        }
        onProgress(PipelineProgress.of(PipelineStage.DONE, 1f, "Hoàn tất"))

        return PageProcessingResult(
            image = rendered,
            cleanedImage = prepared.cleaned,
            regions = prepared.regions,
            translations = batch.translations,
            skipped = prepared.skipped,
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
    ): ArgbImage = compose(cleaned, regions, translations, options) {}

    fun layoutFor(
        region: TextRegion,
        text: String,
        options: PipelineOptions,
    ): com.manhwatranslate.core.layout.TextLayout {
        val style = options.render
        val targetBox = expandedBox(region.box, options.boxExpansion)
        val font = fontFor(region, style)
        val originalSize = if (options.render.autoSizeFromOriginal) {
            (region.box.height.toFloat() / region.lines.size.coerceAtLeast(1)) * 1.25f
        } else {
            Float.MAX_VALUE
        }
        val maxSize = minOf(style.maxTextSizePx, maxOf(style.minTextSizePx, minOf(targetBox.height.toFloat(), originalSize))) *
            style.textSizeScale.coerceAtLeast(0.2f)
        val layoutStyle = LayoutStyle(
            minTextSizePx = style.minTextSizePx * style.textSizeScale.coerceAtLeast(0.2f),
            maxTextSizePx = maxSize,
            lineSpacing = style.lineSpacing,
            maxLines = style.maxLines,
            font = font,
            strokeWidthFactor = if (style.strokeWidthFactor > 0f) style.strokeWidthFactor else 0f,
            preferredLines = region.lines.size,
        )
        return layoutEngine.layout(text, targetBox, layoutStyle)
    }

    private fun fontFor(region: TextRegion, style: RenderStyle): FontSpec {
        val estimated = region.font
        val family = when (style.fontMode) {
            FontMode.AUTO -> estimated.family
            FontMode.SANS -> FontFamilyKind.SANS
            FontMode.SERIF -> FontFamilyKind.SERIF
            FontMode.CONDENSED -> FontFamilyKind.CONDENSED
            FontMode.MONOSPACE -> FontFamilyKind.MONOSPACE
            FontMode.CUSTOM -> FontFamilyKind.CUSTOM
        }
        return FontSpec(
            family = family,
            weight = style.fontWeightOverride ?: estimated.weight,
            italic = estimated.italic,
        )
    }

    private fun expandedBox(
        box: com.manhwatranslate.core.geometry.RectI,
        factor: Float,
    ): com.manhwatranslate.core.geometry.RectI {
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
        val grouped = RegionGrouper.group(page, refined, grouperOptions)
        onProgress(
            PipelineProgress.of(
                PipelineStage.DETECTING,
                0.47f,
                "Tìm thấy ${grouped.size} vùng chữ",
            ),
        )
        return grouped
    }

    private fun prepare(
        page: ArgbImage,
        detected: List<TextRegion>,
        options: PipelineOptions,
        warnings: MutableList<String>,
    ): PreparedPage {
        val skipped = ArrayList<TextRegion>()
        val kept = ArrayList<TextRegion>()
        for (region in detected) {
            val analysis = SfxFilter.analyze(page, region, page.width, options.sfxSensitivity)
            if (analysis.isSoundEffect) skipped += region else kept += region
        }
        if (skipped.isNotEmpty()) {
            warnings += "Bỏ qua ${skipped.size} vùng hiệu ứng âm thanh"
        }

        val working = page.copy()
        val eraser = TextEraser(
            EraseOptions(mode = options.eraseMode, matchBackground = options.matchBackgroundColor),
        )
        val measured = ArrayList<TextRegion>(kept.size)
        for (region in kept) {
            val mask = maskBuilder.build(page, region)
            val font = if (mask != null) {
                estimateFont(region, mask, options)
            } else {
                region.font
            }
            val updated = region.copy(font = font)
            measured += updated
            if (mask != null) eraser.erase(working, mask)
        }
        return PreparedPage(measured, skipped, working)
    }

    private fun estimateFont(region: TextRegion, mask: TextMask, options: PipelineOptions): FontSpec {
        if (mask.metrics.inkCount == 0) return region.font
        val preferred = when (options.render.fontMode) {
            FontMode.AUTO -> null
            FontMode.SANS -> FontFamilyKind.SANS
            FontMode.SERIF -> FontFamilyKind.SERIF
            FontMode.CONDENSED -> FontFamilyKind.CONDENSED
            FontMode.MONOSPACE -> FontFamilyKind.MONOSPACE
            FontMode.CUSTOM -> FontFamilyKind.CUSTOM
        }
        val result = FontEstimator.estimate(
            region = region,
            metrics = mask.metrics,
            preferredFamily = preferred,
            maxSizePx = options.render.maxTextSizePx,
            minSizePx = options.render.minTextSizePx,
        )
        return result.font.copy(
            weight = options.render.fontWeightOverride ?: result.font.weight,
        )
    }

    private fun compose(
        cleaned: ArgbImage,
        regions: List<TextRegion>,
        translations: List<RegionTranslation>,
        options: PipelineOptions,
        onPlanned: (Int) -> Unit,
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
            items += RenderItem(layoutFor(region, text, options), options.render)
        }
        onPlanned(items.size)
        return compositor.compose(cleaned.copy(), items)
    }
}
