package com.manhwatranslate.core.erase

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextRegion

/**
 * Turns a detected text region into a pixel-accurate erase mask.
 *
 * The mask is built from two signals, both restricted to the detected line boxes:
 * a global distance to the estimated bubble colour (catches crisp lettering) and a local
 * contrast term against a 5x5 mean (catches low-contrast lettering and background gradients).
 * Closing then fills glyph interiors, and a final dilation covers anti-aliased edges.
 */
class TextMaskBuilder(
    val options: Options = Options(),
) {
    data class Options(
        val padding: Int = 2,
        val linePadding: Int = 3,
        val globalTolerance: Float = 42f,
        val localTolerance: Float = 22f,
        val closingRadius: Int = 2,
        val dilation: Int = 2,
        val minCoverage: Float = 0.004f,
    )

    fun build(image: ArgbImage, region: TextRegion): TextMask? {
        val bounds = region.box.expanded(options.padding).clamped(image.width, image.height) ?: return null
        val crop = image.crop(bounds)
        val width = crop.width
        val height = crop.height
        val pixels = crop.pixels

        val roi = buildRoi(region, bounds, width, height)
        val background = estimateBackground(pixels, roi, width, height)

        val mask = ByteArray(width * height)
        val localMeans = localMeanLuminance(pixels, width, height)
        val tolerance = options.globalTolerance
        val localTolerance = options.localTolerance
        var marked = 0
        for (index in mask.indices) {
            if (roi[index].toInt() == 0) continue
            val pixel = pixels[index]
            val globalDistance = Colors.distanceTo(background.red, background.green, background.blue, pixel)
            val localContrast = kotlin.math.abs(Colors.luminance(pixel) - localMeans[index])
            val isText = globalDistance > tolerance || localContrast > localTolerance
            if (isText) {
                mask[index] = 1
                marked++
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
        } else {
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
            backgroundArgb = Colors.pack(0xFF, background.red, background.green, background.blue),
            backgroundContrast = background.stdDev,
            fillRectangle = fillRectangle,
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

    private data class Background(val red: Int, val green: Int, val blue: Int, val stdDev: Float)

    private fun estimateBackground(
        pixels: IntArray,
        roi: ByteArray,
        width: Int,
        height: Int,
    ): Background {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (index in pixels.indices) {
            if (roi[index].toInt() != 0) continue
            val pixel = pixels[index]
            r += Colors.red(pixel)
            g += Colors.green(pixel)
            b += Colors.blue(pixel)
            count++
        }
        if (count < 32) {
            return estimateFromBorder(pixels, width, height)
        }
        val meanR = (r / count).toInt()
        val meanG = (g / count).toInt()
        val meanB = (b / count).toInt()
        var variance = 0.0
        for (index in pixels.indices) {
            if (roi[index].toInt() != 0) continue
            val luminance = Colors.luminance(pixels[index])
            val reference = Colors.luminance(meanR, meanG, meanB)
            val diff = luminance - reference
            variance += (diff * diff).toDouble()
        }
        return Background(meanR, meanG, meanB, kotlin.math.sqrt(variance / count).toFloat())
    }

    private fun estimateFromBorder(pixels: IntArray, width: Int, height: Int): Background {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        val band = maxOf(1, minOf(width, height) / 12)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val onBorder = x < band || y < band || x >= width - band || y >= height - band
                if (!onBorder) continue
                val pixel = pixels[y * width + x]
                r += Colors.red(pixel)
                g += Colors.green(pixel)
                b += Colors.blue(pixel)
                count++
            }
        }
        if (count == 0) return Background(255, 255, 255, 0f)
        val meanR = (r / count).toInt()
        val meanG = (g / count).toInt()
        val meanB = (b / count).toInt()
        var variance = 0.0
        val reference = Colors.luminance(meanR, meanG, meanB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val onBorder = x < band || y < band || x >= width - band || y >= height - band
                if (!onBorder) continue
                val diff = Colors.luminance(pixels[y * width + x]) - reference
                variance += (diff * diff).toDouble()
            }
        }
        return Background(
            meanR,
            meanG,
            meanB,
            kotlin.math.sqrt(variance / count.coerceAtLeast(1)).toFloat(),
        )
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
