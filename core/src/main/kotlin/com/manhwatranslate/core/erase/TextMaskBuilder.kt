package com.manhwatranslate.core.erase

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextRegion

/**
 * Turns a detected text region into a pixel-accurate erase mask.
 *
 * Three colours are estimated before anything is erased: the paper colour (the dominant colour
 * of the area around the letters), the letter colour (the pixels furthest from that paper), and
 * a quadratic surface through the paper pixels so gradients and shading are modelled. A pixel is
 * lettering when it is far from the surface *and* closer to the letter colour than to the paper,
 * which keeps artwork of a different colour inside the box from being erased.
 */
class TextMaskBuilder(
    val options: Options = Options(),
) {
    data class Options(
        val padding: Int = 2,
        val contextPadding: Int = 16,
        val linePadding: Int = 3,
        val globalTolerance: Float = 40f,
        val inkAffinity: Float = 0.85f,
        val strongDistanceFactor: Float = 2.5f,
        val localTolerance: Float = 24f,
        val closingRadius: Int = 2,
        val dilation: Int = 2,
        val minCoverage: Float = 0.004f,
        val fitSurface: Boolean = true,
    )

    fun build(image: ArgbImage, region: TextRegion): TextMask? {
        val bounds = region.box.expanded(options.padding).clamped(image.width, image.height) ?: return null
        val context = region.box.expanded(options.contextPadding).clamped(image.width, image.height) ?: bounds
        val crop = image.crop(bounds)
        val width = crop.width
        val height = crop.height
        val pixels = crop.pixels

        val roi = buildRoi(region, bounds, width, height)
        val paper = dominantRingColor(image, context)
        val ink = estimateInkColor(pixels, roi, paper, options.globalTolerance)
        val samples = buildSampleMask(pixels, paper, ink)
        val surface = if (options.fitSurface) BackgroundSurface.fit(pixels, width, height, samples) else null
        val backgroundAt = { x: Int, y: Int ->
            if (surface != null) {
                Colors.pack(
                    0xFF,
                    surface.redAt(x, y).toInt().coerceIn(0, 255),
                    surface.greenAt(x, y).toInt().coerceIn(0, 255),
                    surface.blueAt(x, y).toInt().coerceIn(0, 255),
                )
            } else {
                paper
            }
        }

        val localMeans = localMeanLuminance(pixels, width, height)
        val mask = ByteArray(width * height)
        var marked = 0

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val index = rowOffset + x
                if (roi[index].toInt() == 0) continue
                val pixel = pixels[index]
                val background = backgroundAt(x, y)
                val backgroundDistance = Colors.distance(pixel, background)
                if (backgroundDistance <= options.globalTolerance) {
                    val localContrast = kotlin.math.abs(Colors.luminance(pixel) - localMeans[index])
                    if (localContrast > options.localTolerance * 1.6f && backgroundDistance > 14f) {
                        mask[index] = 1
                        marked++
                    }
                    continue
                }
                val inkDistance = Colors.distance(pixel, ink)
                val closerToInk = inkDistance < backgroundDistance * options.inkAffinity
                val stronglyDifferent = backgroundDistance > options.globalTolerance * options.strongDistanceFactor
                if (closerToInk || stronglyDifferent) {
                    mask[index] = 1
                    marked++
                }
            }
        }

        val coverage = marked.toFloat() / mask.size.toFloat()
        var fillRectangle = false
        if (coverage < options.minCoverage) {
            if (roiHasArea(roi) && region.letterCount > 0) {
                for (index in roi.indices) if (roi[index].toInt() != 0) mask[index] = 1
                fillRectangle = true
            } else {
                return null
            }
        }

        val metrics = InkAnalyzer.analyze(mask, width, height)
        if (!fillRectangle) {
            Morphology.close(mask, width, height, options.closingRadius)
            Morphology.dilate(mask, width, height, options.dilation)
        }
        if (Morphology.countSet(mask) == 0) return null

        return TextMask(
            left = bounds.left,
            top = bounds.top,
            width = width,
            height = height,
            data = mask,
            backgroundArgb = surface?.argbAt(width / 2, height / 2) ?: paper,
            backgroundContrast = paperTexture(pixels, samples, width, height),
            fillRectangle = fillRectangle,
            inkArgb = ink,
            surface = surface,
            metrics = metrics,
        )
    }

    private fun roiHasArea(roi: ByteArray): Boolean {
        for (value in roi) if (value.toInt() != 0) return true
        return false
    }

    private fun buildRoi(region: TextRegion, bounds: RectI, width: Int, height: Int): ByteArray {
        val roi = ByteArray(width * height)
        val lineRects = region.lineBoxes
        if (lineRects.isEmpty()) {
            java.util.Arrays.fill(roi, 1)
            return roi
        }
        for (line in lineRects) {
            val rect = line
                .expanded(options.linePadding)
                .intersection(bounds)
                ?.translated(-bounds.left, -bounds.top)
                ?: continue
            for (y in rect.top until rect.bottom) {
                val offset = y * width
                for (x in rect.left until rect.right) roi[offset + x] = 1
            }
        }
        if (!roiHasArea(roi)) java.util.Arrays.fill(roi, 1)
        return roi
    }

    /**
     * Pixels the background surface may be fitted through: the ones closer to the paper colour
     * than to the letter colour, so lettering and artwork of another colour are left out.
     */
    private fun buildSampleMask(pixels: IntArray, paper: Int, ink: Int): ByteArray {
        val samples = ByteArray(pixels.size)
        if (Colors.distance(paper, ink) < 30f) {
            java.util.Arrays.fill(samples, 1)
            return samples
        }
        var kept = 0
        for (index in pixels.indices) {
            val pixel = pixels[index]
            if (Colors.distance(pixel, paper) <= Colors.distance(pixel, ink)) kept++ else samples[index] = 1
        }
        if (kept < pixels.size / 20) {
            for (index in pixels.indices) {
                samples[index] = if (Colors.distance(pixels[index], paper) <= 60f) 0 else 1
            }
        }
        return samples
    }

    /**
     * Colour of the paper around the letters, taken from the rim of the context box. The rim is
     * almost always bubble, even when the box itself is half lettering, which a plain mode over
     * the whole area can get wrong.
     */
    private fun dominantRingColor(image: ArgbImage, rect: RectI): Int {
        val band = maxOf(2, minOf(rect.width, rect.height) / 5)
        val counts = HashMap<Int, Int>()
        var total = 0
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                val onRing = x < rect.left + band || x >= rect.right - band ||
                    y < rect.top + band || y >= rect.bottom - band
                if (!onRing) continue
                val key = quantize(image.get(x, y))
                counts[key] = (counts[key] ?: 0) + 1
                total++
            }
        }
        if (total == 0) return Colors.opaque(255, 255, 255)
        var bestKey = 0
        var bestCount = -1
        for ((key, count) in counts) {
            if (count > bestCount) {
                bestCount = count
                bestKey = key
            }
        }
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                val onRing = x < rect.left + band || x >= rect.right - band ||
                    y < rect.top + band || y >= rect.bottom - band
                if (!onRing) continue
                val pixel = image.get(x, y)
                if (quantize(pixel) != bestKey) continue
                r += Colors.red(pixel)
                g += Colors.green(pixel)
                b += Colors.blue(pixel)
                n++
            }
        }
        if (n == 0) return Colors.opaque(255, 255, 255)
        return Colors.opaque((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun quantize(argb: Int): Int {
        val r = Colors.red(argb) shr 4
        val g = Colors.green(argb) shr 4
        val b = Colors.blue(argb) shr 4
        return (r shl 8) or (g shl 4) or b
    }

    /**
     * Letters are the most common colour among the pixels of the line boxes that sit clearly
     * away from the paper. A mode outvotes the long tail a background gradient leaves behind,
     * which a median would drag into the middle of the gradient.
     */
    private fun estimateInkColor(
        pixels: IntArray,
        roi: ByteArray,
        paper: Int,
        tolerance: Float,
    ): Int {
        val counts = HashMap<Int, Int>()
        for (index in pixels.indices) {
            if (roi[index].toInt() == 0) continue
            val pixel = pixels[index]
            if (Colors.distance(pixel, paper) < tolerance * 1.2f) continue
            val key = quantize(pixel)
            counts[key] = (counts[key] ?: 0) + 1
        }
        if (counts.isEmpty()) {
            return if (Colors.isDark(paper)) Colors.opaque(255, 255, 255) else Colors.opaque(0, 0, 0)
        }
        var bestKey = 0
        var bestCount = -1
        for ((key, count) in counts) {
            if (count > bestCount) {
                bestCount = count
                bestKey = key
            }
        }
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (index in pixels.indices) {
            if (roi[index].toInt() == 0) continue
            val pixel = pixels[index]
            if (quantize(pixel) != bestKey) continue
            r += Colors.red(pixel)
            g += Colors.green(pixel)
            b += Colors.blue(pixel)
            n++
        }
        if (n == 0) return Colors.opaque(0, 0, 0)
        return Colors.opaque((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /**
     * How busy the paper is from one pixel to the next. A flat bubble scores near zero and is
     * refilled from the surface; grainy, shaded or painted ones score high and are diffused from
     * their neighbours so the fill keeps the texture instead of laying a smooth patch on it.
     */
    private fun paperTexture(
        pixels: IntArray,
        samples: ByteArray,
        width: Int,
        height: Int,
    ): Float {
        var sum = 0.0
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (samples[index].toInt() != 0) continue
                val here = Colors.luminance(pixels[index])
                if (x + 1 < width && samples[index + 1].toInt() == 0) {
                    sum += kotlin.math.abs(here - Colors.luminance(pixels[index + 1])).toDouble()
                    count++
                }
                if (y + 1 < height && samples[index + width].toInt() == 0) {
                    sum += kotlin.math.abs(here - Colors.luminance(pixels[index + width])).toDouble()
                    count++
                }
            }
        }
        if (count == 0) return 0f
        return (sum / count).toFloat()
    }

    /** Separable 5x5 box mean of luminance, clamped at the borders. */
    private fun localMeanLuminance(pixels: IntArray, width: Int, height: Int): FloatArray {
        val luminance = FloatArray(pixels.size)
        for (index in pixels.indices) luminance[index] = Colors.luminance(pixels[index])
        val horizontal = FloatArray(pixels.size)
        val radius = 2
        for (y in 0 until height) {
            val rowOffset = y * width
            var sum = 0f
            for (x in 0..minOf(radius, width - 1)) sum += luminance[rowOffset + x]
            var count = minOf(radius, width - 1) + 1
            for (x in 0 until width) {
                horizontal[rowOffset + x] = sum / count
                val addIndex = x + radius + 1
                if (addIndex < width) {
                    sum += luminance[rowOffset + addIndex]
                    count++
                }
                val removeIndex = x - radius
                if (removeIndex >= 0) {
                    sum -= luminance[rowOffset + removeIndex]
                    count--
                }
            }
        }
        val result = FloatArray(pixels.size)
        for (x in 0 until width) {
            var sum = 0f
            for (y in 0..minOf(radius, height - 1)) sum += horizontal[y * width + x]
            var count = minOf(radius, height - 1) + 1
            for (y in 0 until height) {
                result[y * width + x] = sum / count
                val addIndex = y + radius + 1
                if (addIndex < height) {
                    sum += horizontal[addIndex * width + x]
                    count++
                }
                val removeIndex = y - radius
                if (removeIndex >= 0) {
                    sum -= horizontal[removeIndex * width + x]
                    count--
                }
            }
        }
        return result
    }
}
