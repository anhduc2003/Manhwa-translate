package com.manhwatranslate.core.pipeline

import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.erase.EraseOptions
import com.manhwatranslate.core.erase.TextEraser
import com.manhwatranslate.core.erase.TextMask
import com.manhwatranslate.core.erase.TextMaskBuilder
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
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
    /**
     * Tallest slice processed at once. Webtoon strips reach tens of thousands of pixels, more than
     * a phone can hold as one bitmap; the pipeline works through [maxBandHeight] slices instead and
     * cuts them at blank rows so no letter is sliced in half. 0 disables slicing.
     */
    val maxBandHeight: Int = 6000,
    /** How far around the ideal cut the search for a blank row may wander. */
    val bandSearchPx: Int = 320,
    /** Context kept on each side of a slice so a block near the seam is read whole. */
    val bandMarginPx: Int = 320,
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
    private val aggressiveMaskOptions = TextMaskBuilder.Options(
        globalTolerance = 32f,
        inkAffinity = 1.25f,
        closingRadius = 3,
        dilation = 4,
    )

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
        val bands = planBands(page, options)
        if (bands.size > 1) return processBands(page, bands, options, onProgress)
        return processBand(
            page = page,
            bandTop = 0,
            bandBottom = page.height,
            contextTop = 0,
            contextBottom = page.height,
            options = options,
            onProgress = onProgress,
            progressBase = 0f,
            progressSpan = 1f,
        )
    }

    /**
     * Cuts the strip into slices at the quietest rows around the ideal boundary: a row of flat
     * page (a gutter between panels) never holds lettering, so nothing is split across a seam.
     */
    private fun planBands(page: ArgbImage, options: PipelineOptions): List<IntRange> {
        val limit = options.maxBandHeight
        if (limit <= 0 || page.height <= limit) return listOf(0 until page.height)
        val count = (page.height + limit - 1) / limit
        val bounds = ArrayList<Int>()
        bounds += 0
        for (index in 1 until count) {
            val ideal = (page.height.toLong() * index / count).toInt()
            bounds += quietestRow(page, ideal, options.bandSearchPx.coerceAtLeast(0))
        }
        bounds += page.height
        val result = ArrayList<IntRange>()
        for (index in 0 until bounds.size - 1) {
            val top = bounds[index]
            val bottom = bounds[index + 1]
            if (bottom > top) result += top until bottom
        }
        return result.ifEmpty { listOf(0 until page.height) }
    }

    private fun quietestRow(page: ArgbImage, ideal: Int, search: Int): Int {
        val from = (ideal - search).coerceAtLeast(1)
        val to = (ideal + search).coerceAtMost(page.height - 1)
        if (to <= from) return ideal.coerceIn(1, page.height - 1)
        var bestRow = ideal
        var bestSpread = Float.MAX_VALUE
        for (y in from until to) {
            var sum = 0.0
            var sumSquares = 0.0
            var count = 0
            var x = 0
            while (x < page.width) {
                val value = Colors.luminance(page.get(x, y)).toDouble()
                sum += value
                sumSquares += value * value
                count++
                x += 4
            }
            if (count == 0) continue
            val mean = sum / count
            val variance = (sumSquares / count - mean * mean).toDouble()
            val spread = (kotlin.math.sqrt(if (variance > 0) variance else 0.0) + (255.0 - mean) * 0.6).toFloat()
            if (spread < bestSpread) {
                bestSpread = spread
                bestRow = y
            }
        }
        return bestRow.coerceIn(1, page.height - 1)
    }

    private suspend fun processBands(
        page: ArgbImage,
        bands: List<IntRange>,
        options: PipelineOptions,
        onProgress: (PipelineProgress) -> Unit,
    ): PageProcessingResult {
        val started = System.currentTimeMillis()
        val cleaned = ArgbImage(page.width, page.height, IntArray(page.width * page.height))
        val rendered = ArgbImage(page.width, page.height, IntArray(page.width * page.height))
        val regions = ArrayList<TextRegion>()
        val translations = ArrayList<RegionTranslation>()
        val skipped = ArrayList<TextRegion>()
        val warnings = ArrayList<String>()

        bands.forEachIndexed { index, band ->
            val base = index.toFloat() / bands.size
            val span = 1f / bands.size
            val margin = options.bandMarginPx.coerceAtLeast(0)
            val contextTop = (band.first - margin).coerceAtLeast(0)
            val contextBottom = (band.last + 1 + margin).coerceAtMost(page.height)
            val result = processBand(
                page = page,
                bandTop = band.first,
                bandBottom = band.last + 1,
                contextTop = contextTop,
                contextBottom = contextBottom,
                options = options,
                onProgress = onProgress,
                progressBase = base,
                progressSpan = span,
            )
            val coreTop = band.first - contextTop
            val coreBottom = band.last + 1 - contextTop
            val coreBox = com.manhwatranslate.core.geometry.RectI(0, coreTop, page.width, coreBottom)
            cleaned.blit(result.cleanedImage.crop(coreBox), 0, band.first)
            rendered.blit(result.image.crop(coreBox), 0, band.first)
            val offset = contextTop
            val idOffset = regions.size
            regions += result.regions.map { region ->
                region.copy(id = region.id + idOffset, box = region.box.translated(0, offset), lines = region.lines.map { it.copy(box = it.box.translated(0, offset)) })
            }
            translations += result.translations.map { it.copy(regionId = it.regionId + idOffset) }
            skipped += result.skipped.map { it.copy(box = it.box.translated(0, offset)) }
            warnings += result.warnings
        }

        if (regions.isEmpty() && warnings.none { it.contains("Không tìm thấy") }) {
            warnings += "Không tìm thấy vùng chữ nào trên trang này"
        }
        onProgress(PipelineProgress.of(PipelineStage.DONE, 1f, "Hoàn tất"))
        return PageProcessingResult(
            image = rendered,
            cleanedImage = cleaned,
            regions = regions,
            translations = translations,
            skipped = skipped,
            warnings = warnings.distinct(),
            elapsedMillis = System.currentTimeMillis() - started,
        )
    }

    private suspend fun processBand(
        page: ArgbImage,
        bandTop: Int,
        bandBottom: Int,
        contextTop: Int,
        contextBottom: Int,
        options: PipelineOptions,
        onProgress: (PipelineProgress) -> Unit,
        progressBase: Float,
        progressSpan: Float,
    ): PageProcessingResult {
        val slice = if (contextTop == 0 && contextBottom == page.height) {
            page
        } else {
            page.crop(com.manhwatranslate.core.geometry.RectI(0, contextTop, page.width, contextBottom))
        }
        val coreTop = bandTop - contextTop
        val coreBottom = bandBottom - contextTop
        val scoped: (PipelineProgress) -> Unit = { progress ->
            onProgress(
                PipelineProgress(
                    stage = progress.stage,
                    fraction = progressBase + progress.fraction * progressSpan,
                    message = progress.message,
                ),
            )
        }
        scoped(PipelineProgress.of(PipelineStage.PREPARING, 0.02f, "Chuẩn bị trang"))
        return processSlice(slice, options, scoped, coreTop, coreBottom, contextTop)
    }

    private suspend fun processSlice(
        page: ArgbImage,
        options: PipelineOptions,
        onProgress: (PipelineProgress) -> Unit,
        coreTop: Int = 0,
        coreBottom: Int = Int.MAX_VALUE,
        pageOffsetY: Int = 0,
    ): PageProcessingResult {
        val started = System.currentTimeMillis()
        val warnings = ArrayList<String>()
        onProgress(PipelineProgress.of(PipelineStage.PREPARING, 0.02f, "Chuẩn bị trang"))

        val detected = detect(page, options, warnings, onProgress, coreTop, coreBottom, pageOffsetY)
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
        coreTop: Int = 0,
        coreBottom: Int = Int.MAX_VALUE,
        pageOffsetY: Int = 0,
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
                val turned = if (pageOffsetY == 0) {
                    tile
                } else {
                    ImageTile(tile.index, tile.rect.translated(0, pageOffsetY), tile.image)
                }
                val found = recognizer.recognize(turned, options.script)
                raw += if (pageOffsetY == 0) found else found.map { it.translated(0, -pageOffsetY) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                warnings += "Nhận diện chữ thất bại ở khối ${index + 1}: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        val refined = RegionMerger.refine(raw, mergerOptions)
        val grouped = RegionGrouper.group(page, refined, grouperOptions)
            .filter { region ->
                val center = (region.box.top + region.box.bottom) / 2
                center >= coreTop && center < coreBottom
            }
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
            if (mask != null) {
                eraser.erase(working, mask)
                if (residualInkRatio(working, mask) > 0.06f) {
                    val retry = maskBuilder.build(page, region, aggressiveMaskOptions)
                    if (retry != null) eraser.erase(working, retry)
                }
            }
        }
        return PreparedPage(measured, skipped, working)
    }

    /**
     * Share of the box still standing out from the reconstructed paper after the fill. A box that
     * is mostly clear reads as erased; a high share means the mask missed part of the lettering,
     * so it is built again with a looser tolerance and a wider dilation.
     */
    private fun residualInkRatio(image: ArgbImage, mask: TextMask): Float {
        val surface = mask.surface ?: return 0f
        var total = 0
        var leftover = 0
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                val pixel = image.get(mask.left + x, mask.top + y)
                val paper = surface.argbAt(x, y)
                total++
                val paperDistance = Colors.distance(pixel, paper)
                if (paperDistance > 70f && Colors.distance(pixel, mask.inkArgb) < paperDistance) leftover++
            }
        }
        if (total == 0) return 0f
        return leftover.toFloat() / total.toFloat()
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
