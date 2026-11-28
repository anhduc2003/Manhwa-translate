package com.manhwatranslate.core.pipeline

import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.erase.EraseOptions
import com.manhwatranslate.core.erase.TextEraser
import com.manhwatranslate.core.erase.TextMask
import com.manhwatranslate.core.erase.TextMaskBuilder
import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.layout.FontEstimator
import com.manhwatranslate.core.layout.FontFamilyKind
import com.manhwatranslate.core.layout.FontMode
import com.manhwatranslate.core.layout.FontTracking
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
import com.manhwatranslate.core.image.ImageRotation
import com.manhwatranslate.core.image.rotated
import com.manhwatranslate.core.scan.ImageTiler
import com.manhwatranslate.core.scan.ImageTile
import com.manhwatranslate.core.scan.RegionGrouper
import com.manhwatranslate.core.scan.RegionMerger
import com.manhwatranslate.core.scan.RegionSplitter
import com.manhwatranslate.core.scan.SfxFilter
import com.manhwatranslate.core.scan.SfxSensitivity
import com.manhwatranslate.core.scan.SlantDetector
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
    /**
     * Reads lettering drawn along a slant at whatever angle it is drawn at: the tilt is measured
     * on the ink itself and only the lines that are actually slanted are read again, levelled.
     */
    val autoSlant: Boolean = false,
    /**
     * Fixed angles, in degrees, that the page is turned by to read lettering drawn along a slant.
     * Each extra angle costs one more OCR pass over every tile. [autoSlant] reads a slanted line
     * at its own angle without this list; keeping it is only useful for a page whose slants are
     * known in advance.
     */
    val rotationAngles: List<Float> = emptyList(),
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
 * How much larger than the measured original a translation may be drawn. A little room keeps a
 * short translation from looking squeezed, while still letting a whisper stay a whisper.
 */
private const val ORIGINAL_SIZE_ALLOWANCE = 1.15f

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
    private val splitterOptions: RegionSplitter.Options = RegionSplitter.Options(),
    /**
     * Reader used when [recognizer] throws. A self-hosted OCR server that is down or speaks a
     * different protocol would otherwise turn every page into "no dialogue found".
     */
    private val backupRecognizer: TileRecognizer? = null,
) {
    private val layoutEngine = TextLayoutEngine(measurer)
    private var activeRecognizer: TileRecognizer = recognizer
    private val aggressiveMaskOptions = TextMaskBuilder.Options(
        globalTolerance = 32f,
        inkResidualLimit = 72f,
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

    /**
     * One band's work, kept in the slice's own coordinates so the render can paste straight back
     * onto the page. [regions] carry page-wide ids so the single translation call can address
     * every band's regions at once.
     */
    private data class BandWork(
        val band: IntRange,
        val contextTop: Int,
        val contextBottom: Int,
        val regions: List<TextRegion>,
        val skipped: List<TextRegion>,
    )

    /**
     * Detects and erases band by band, then translates the whole page in one call and renders the
     * bands back. Translating per band would cost one backend round trip per band on a long
     * webtoon strip, which dominates the page's time when the translation runs on a cloud API.
     */
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
        val skipped = ArrayList<TextRegion>()
        val warnings = ArrayList<String>()
        val works = ArrayList<BandWork>()
        var nextId = 0

        bands.forEachIndexed { index, band ->
            val base = index.toFloat() / bands.size
            val span = 1f / bands.size
            val margin = options.bandMarginPx.coerceAtLeast(0)
            val contextTop = (band.first - margin).coerceAtLeast(0)
            val contextBottom = (band.last + 1 + margin).coerceAtMost(page.height)
            val coreTop = band.first - contextTop
            val coreBottom = band.last + 1 - contextTop
            val slice = page.crop(RectI(0, contextTop, page.width, contextBottom))
            val scoped: (PipelineProgress) -> Unit = { progress ->
                onProgress(
                    PipelineProgress(
                        stage = progress.stage,
                        fraction = base + progress.fraction * span,
                        message = progress.message,
                    ),
                )
            }
            scoped(PipelineProgress.of(PipelineStage.PREPARING, 0.02f, "Chuẩn bị trang"))
            val detected = detect(slice, options, warnings, scoped, coreTop, coreBottom, contextTop)
            scoped(PipelineProgress.of(PipelineStage.ERASING, 0.5f, "Xoá chữ gốc"))
            val prepared = prepare(slice, detected, options, warnings)
            cleaned.blit(prepared.cleaned.crop(RectI(0, coreTop, page.width, coreBottom)), 0, band.first)
            val numbered = prepared.regions.map { it.copy(id = nextId++) }
            works += BandWork(band, contextTop, contextBottom, numbered, prepared.skipped)
            regions += numbered.map { it.translated(0, contextTop) }
            skipped += prepared.skipped.map { it.translated(0, contextTop) }
        }

        if (regions.isEmpty()) {
            val recognizerFailed = warnings.any { it.startsWith("Nhận diện chữ thất bại") }
            when {
                recognizerFailed && skipped.isEmpty() -> Unit
                skipped.isEmpty() -> warnings += "Không tìm thấy vùng chữ nào trên trang này"
                else -> warnings += "Chỉ tìm thấy hiệu ứng âm thanh trên trang này"
            }
            onProgress(PipelineProgress.of(PipelineStage.DONE, 1f, "Hoàn tất"))
            return PageProcessingResult(
                image = cleaned,
                cleanedImage = cleaned,
                regions = emptyList(),
                translations = emptyList(),
                skipped = skipped,
                warnings = warnings.distinct(),
                elapsedMillis = System.currentTimeMillis() - started,
            )
        }

        onProgress(
            PipelineProgress.of(
                PipelineStage.TRANSLATING,
                0.62f,
                "Đang dịch ${regions.size} vùng chữ",
            ),
        )
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
        val byId = batch.translations.associateBy { it.regionId }

        onProgress(PipelineProgress.of(PipelineStage.RENDERING, 0.84f, "Vẽ bản dịch"))
        var drawn = 0
        for (work in works) {
            val coreTop = work.band.first - work.contextTop
            val coreBottom = work.band.last + 1 - work.contextTop
            val coreBox = RectI(0, coreTop, page.width, coreBottom)
            if (work.regions.isEmpty()) {
                rendered.blit(cleaned.crop(coreBox), 0, work.band.first)
                continue
            }
            val bandCleaned = cleaned.crop(RectI(0, work.contextTop, page.width, work.contextBottom))
            val bandTranslations = work.regions.mapNotNull { byId[it.id] }
            val bandImage = compose(bandCleaned, work.regions, bandTranslations, options) { drawn += it }
            rendered.blit(bandImage.crop(coreBox), 0, work.band.first)
        }
        onProgress(PipelineProgress.of(PipelineStage.RENDERING, 0.92f, "Vẽ $drawn khối chữ"))
        onProgress(PipelineProgress.of(PipelineStage.DONE, 1f, "Hoàn tất"))

        return PageProcessingResult(
            image = rendered,
            cleanedImage = cleaned,
            regions = regions,
            translations = batch.translations,
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
            val recognizerFailed = warnings.any { it.startsWith("Nhận diện chữ thất bại") }
            when {
                recognizerFailed && prepared.skipped.isEmpty() -> Unit
                prepared.skipped.isEmpty() -> warnings += "Không tìm thấy vùng chữ nào trên trang này"
                else -> warnings += "Chỉ tìm thấy hiệu ứng âm thanh, không có lời thoại để dịch"
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
        // A turned line is laid out in its own upright box, not in the larger rectangle that
        // contains it on the page, or the translation would wrap wider than the line it replaces.
        val targetBox = expandedBox(region.uprightBox(), options.boxExpansion)
        val font = fontFor(region, style)
        // The size the original lettering was measured at, not the size that fills the bubble:
        // a whispered line should stay small and a shout should stay large, exactly as on the page.
        val measuredSize = region.originalTextSizePx.takeIf { it > 0f }
        val originalSize = if (options.render.autoSizeFromOriginal) {
            (measuredSize ?: (region.box.height.toFloat() / region.lines.size.coerceAtLeast(1)) * 0.78f) *
                ORIGINAL_SIZE_ALLOWANCE
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
        val font = FontSpec(
            family = family,
            weight = style.fontWeightOverride ?: estimated.weight,
            italic = estimated.italic,
        )
        return font.copy(tracking = FontTracking.em(measurer, font))
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

    /**
     * Runs the recognizer over the page in tiles. A second pass with shorter tiles gives the
     * recognizer more pixels per letter, which is what reads the small lettering a first pass
     * misses; it only runs when the first pass found nothing at all.
     */
    private suspend fun scanTiles(
        page: ArgbImage,
        tileHeight: Int,
        options: PipelineOptions,
        warnings: MutableList<String>,
        onProgress: (PipelineProgress) -> Unit,
        pageOffsetY: Int,
    ): List<TextRegion> {
        val tiles = ImageTiler.split(page, tileHeight, options.tileOverlap)
        val raw = ArrayList<TextRegion>()
        tiles.forEachIndexed { index, tile ->
            onProgress(
                PipelineProgress.of(
                    PipelineStage.DETECTING,
                    0.05f + 0.42f * (index.toFloat() / tiles.size.toFloat()),
                    "Nhận diện chữ (${index + 1}/${tiles.size})",
                ),
            )
            if (isFlat(tile.image)) return@forEachIndexed
            val turned = if (pageOffsetY == 0) {
                tile
            } else {
                ImageTile(tile.index, tile.rect.translated(0, pageOffsetY), tile.image)
            }
            val found = try {
                activeRecognizer.recognize(turned, options.script)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                retryWithBackup(error, turned, options.script, warnings, index)
            }
            raw += if (pageOffsetY == 0) found else found.map { it.translated(0, -pageOffsetY) }
            raw += readTilted(tile, options, pageOffsetY)
            raw += readSlanted(tile, found, options, pageOffsetY)
        }
        return dedupe(raw)
    }

    /**
     * Finds the lettering the level pass could not see because it runs uphill or downhill, and
     * reads each line once, levelled at the angle the detector measured for it. Only the slanted
     * lines are recognised again, so the cost is one small OCR pass per slanted line rather than
     * a whole extra pass over the tile.
     */
    private suspend fun readSlanted(
        tile: ImageTile,
        alreadyRead: List<TextRegion>,
        options: PipelineOptions,
        pageOffsetY: Int,
    ): List<TextRegion> {
        if (!options.autoSlant) return emptyList()
        val skip = alreadyRead.map { it.box.translated(-tile.rect.left, -tile.rect.top) }
        val candidates = try {
            SlantDetector.detect(tile.image, skip)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            emptyList()
        }
        if (candidates.isEmpty()) return emptyList()
        val found = ArrayList<TextRegion>()
        for (candidate in candidates) {
            val pad = maxOf(10, candidate.box.height / 2)
            val bounds = candidate.box.expanded(pad).clamped(tile.image.width, tile.image.height) ?: continue
            if (bounds.width < 24 || bounds.height < 16) continue
            val crop = tile.image.crop(bounds)
            val rotation = ImageRotation.of(crop, candidate.angleDegrees)
            // Anchored at zero, as in the fixed-angle pass: the recognizer adds the tile's own
            // offset to every box, and these coordinates are still in the levelled copy.
            val turned = ImageTile(
                tile.index,
                RectI(0, 0, rotation.width, rotation.height),
                crop.rotated(rotation),
            )
            val regions = try {
                activeRecognizer.recognize(turned, options.script)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                emptyList()
            }
            for (region in regions) {
                val box = rotation.toSource(region.box)
                if (box.width < 8 || box.height < 8) continue
                val lines = region.lines.map { it.copy(box = rotation.toSource(it.box)) }
                val mapped = region.copy(
                    box = box,
                    lines = lines,
                    rotationDegrees = candidate.angleDegrees,
                    uprightWidthPx = region.box.width,
                    uprightHeightPx = region.box.height,
                ).translated(tile.rect.left + bounds.left, tile.rect.top + bounds.top)
                found += if (pageOffsetY == 0) mapped else mapped.translated(0, -pageOffsetY)
            }
        }
        return found
    }

    /**
     * Reads the tile again turned by each configured angle, and maps what comes back onto the
     * page with the angle it was found at. A slanted bubble produces nothing at zero degrees, so
     * without this pass it is neither erased nor translated.
     */
    private suspend fun readTilted(
        tile: ImageTile,
        options: PipelineOptions,
        pageOffsetY: Int,
    ): List<TextRegion> {
        if (options.rotationAngles.isEmpty()) return emptyList()
        val found = ArrayList<TextRegion>()
        for (angle in options.rotationAngles) {
            val rotation = ImageRotation.of(tile.image, angle)
            // The recognizer adds a tile's own offset to every box it returns, and a turned copy
            // is not laid out at the tile's offset. Handing it a tile anchored at zero keeps that
            // arithmetic honest: the boxes stay in turned-image coordinates until they are mapped.
            val turned = ImageTile(
                tile.index,
                com.manhwatranslate.core.geometry.RectI(0, 0, rotation.width, rotation.height),
                tile.image.rotated(rotation),
            )
            val regions = try {
                activeRecognizer.recognize(turned, options.script)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                emptyList()
            }
            for (region in regions) {
                val box = rotation.toSource(region.box)
                if (box.width < 8 || box.height < 8) continue
                val lines = region.lines.map { it.copy(box = rotation.toSource(it.box)) }
                val mapped = region.copy(
                    box = box,
                    lines = lines,
                    // The turn that levelled the line is the angle it was drawn at, so drawing the
                    // translation by that angle puts it back along the original line.
                    rotationDegrees = angle,
                    uprightWidthPx = region.box.width,
                    uprightHeightPx = region.box.height,
                )
                    .translated(tile.rect.left, tile.rect.top)
                found += if (pageOffsetY == 0) mapped else mapped.translated(0, -pageOffsetY)
            }
        }
        return found
    }

    /**
     * Drops a region another pass already reported in the same place. A level pass sometimes
     * catches a letter or two of a slanted line; when the pass that read the line levelled finds
     * more of it, that fuller reading replaces the fragment instead of being thrown away.
     */
    private fun dedupe(regions: List<TextRegion>): List<TextRegion> {
        val kept = ArrayList<TextRegion>(regions.size)
        for (region in regions) {
            var duplicate = false
            for (index in kept.indices) {
                val other = kept[index]
                if (overlaps(other.box, region.box) <= 0.45f) continue
                duplicate = true
                if (region.letterCount > other.letterCount) kept[index] = region
                break
            }
            if (!duplicate) kept += region
        }
        return kept
    }

    private fun overlaps(a: com.manhwatranslate.core.geometry.RectI, b: com.manhwatranslate.core.geometry.RectI): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val intersection = (right - left).toFloat() * (bottom - top).toFloat()
        val smallest = minOf(a.width.toFloat() * a.height.toFloat(), b.width.toFloat() * b.height.toFloat())
        return if (smallest <= 0f) 0f else intersection / smallest
    }

    private suspend fun retryWithBackup(
        error: Throwable,
        tile: ImageTile,
        script: SourceScript,
        warnings: MutableList<String>,
        index: Int,
    ): List<TextRegion> {
        val reason = error.message ?: error.javaClass.simpleName
        val backup = backupRecognizer
        if (backup == null || activeRecognizer === backup) {
            warnings += "Nhận diện chữ thất bại ở khối ${index + 1}: $reason"
            return emptyList()
        }
        activeRecognizer = backup
        warnings += "Máy đọc chữ bên ngoài lỗi ($reason), đã chuyển sang đọc chữ trên máy"
        return try {
            backup.recognize(tile, script)
        } catch (fallbackError: Throwable) {
            if (fallbackError is CancellationException) throw fallbackError
            warnings += "Nhận diện chữ thất bại ở khối ${index + 1}: " +
                (fallbackError.message ?: fallbackError.javaClass.simpleName)
            emptyList()
        }
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
        var raw = scanTiles(page, options.maxTileHeight, options, warnings, onProgress, pageOffsetY)
        if (raw.isEmpty() && options.maxTileHeight > MIN_RETRY_TILE_HEIGHT) {
            onProgress(
                PipelineProgress.of(PipelineStage.DETECTING, 0.3f, "Thử lại với khung nhỏ hơn"),
            )
            raw = scanTiles(
                page = page,
                tileHeight = maxOf(MIN_RETRY_TILE_HEIGHT, options.maxTileHeight / 2),
                options = options,
                warnings = warnings,
                onProgress = onProgress,
                pageOffsetY = pageOffsetY,
            )
        }
        val refined = RegionMerger.refine(raw, mergerOptions)
        val grouped = RegionSplitter.split(page, RegionGrouper.group(page, refined, grouperOptions), splitterOptions)
            .filter { region ->
                val center = (region.box.top + region.box.bottom) / 2
                center >= coreTop && center < coreBottom
            }
            .mapIndexed { index, region -> region.copy(id = index) }
        onProgress(
            PipelineProgress.of(
                PipelineStage.DETECTING,
                0.47f,
                "Tìm thấy ${grouped.size} vùng chữ",
            ),
        )
        return grouped
    }

    /**
     * A tile of one flat colour is a gutter or an empty margin between panels, and running four
     * OCR models over it finds nothing. Sampled at a stride small enough that a glyph stroke
     * still lands on a sample, so a tile with lettering on it is never mistaken for flat.
     */
    private companion object {
        /** Luminance spread under which a tile holds one flat colour rather than artwork. */
        const val FLAT_SHADE_SPAN = 20

        /** Shortest tile the retry pass may use, so the recognizer always gains resolution. */
        const val MIN_RETRY_TILE_HEIGHT = 400
    }

    private fun isFlat(image: ArgbImage): Boolean {
        var low = 255
        var high = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val shade = Colors.luminance(image.get(x, y)).toInt()
                if (shade < low) low = shade
                if (shade > high) high = shade
                if (high - low > FLAT_SHADE_SPAN) return false
                x += 3
            }
            y += 3
        }
        return true
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
            val lettering = mask?.let { estimateLettering(region, it, options) }
            val updated = region.copy(
                font = lettering?.font ?: region.font,
                originalTextSizePx = lettering?.sizePx ?: region.originalTextSizePx,
            )
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

    /** Lettering measured off the artwork: the face to draw with and the size it was drawn at. */
    private data class EstimatedLettering(val font: FontSpec, val sizePx: Float)

    private fun estimateLettering(
        region: TextRegion,
        mask: TextMask,
        options: PipelineOptions,
    ): EstimatedLettering? {
        if (mask.metrics.inkCount == 0) return null
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
        val font = result.font
            .copy(weight = options.render.fontWeightOverride ?: result.font.weight)
            .let { it.copy(tracking = FontTracking.em(measurer, it)) }
        return EstimatedLettering(font = font, sizePx = result.estimatedTextSizePx)
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
            items += RenderItem(layoutFor(region, text, options), options.render, region.rotationDegrees)
        }
        onPlanned(items.size)
        return compositor.compose(cleaned.copy(), items)
    }
}
