package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextRegion

/**
 * Puts the pieces of one speech bubble back together.
 *
 * OCR often returns a bubble as several blocks: a short line above a longer one, a name tag
 * inside a caption, a line of hand lettering next to the lettering on the artwork. Translating
 * those pieces one by one gives the same sentence several font sizes and loses the context, so
 * neighbouring blocks that sit in the same quiet area of the page are merged into the region
 * they belong to before anything is erased or translated.
 */
object RegionGrouper {

    data class Options(
        /** Largest vertical gap between two blocks of the same bubble, as a share of their line height. */
        val maxGapRatio: Float = 0.75f,
        /** Share of the narrower block's width that must overlap horizontally. */
        val minHorizontalOverlap: Float = 0.45f,
        /** Two blocks never join when the result would be taller than this many of their heights. */
        val maxHeightRatio: Float = 4.5f,
        /** Luminance spread of the band between two blocks still counted as quiet bubble paper. */
        val quietBandSpread: Float = 24f,
    )

    fun group(
        image: ArgbImage,
        regions: List<TextRegion>,
        options: Options = Options(),
    ): List<TextRegion> {
        val working = ArrayList(regions)
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in working.indices) {
                for (j in i + 1 until working.size) {
                    if (!canJoin(image, working[i], working[j], options)) continue
                    val joined = join(working[i], working[j])
                    working.removeAt(j)
                    working[i] = joined
                    merged = true
                    break@outer
                }
            }
        }
        return working.sortedBy { it.box.top }.mapIndexed { index, region -> region.copy(id = index) }
    }

    private fun canJoin(image: ArgbImage, a: TextRegion, b: TextRegion, options: Options): Boolean {
        val upper = if (a.box.top <= b.box.top) a else b
        val lower = if (upper === a) b else a
        val lineHeight = maxOf(upper.box.height, lower.box.height).coerceAtLeast(1)
        val gap = lower.box.top - upper.box.bottom
        if (gap > lineHeight * options.maxGapRatio) return false
        if (lower.box.top < upper.box.bottom - lineHeight) return false
        if (mergedHeight(upper.box, lower.box) > lineHeight * options.maxHeightRatio) return false
        val narrower = minOf(upper.box.width, lower.box.width).coerceAtLeast(1)
        if (upper.box.horizontalOverlap(lower.box) < narrower * options.minHorizontalOverlap) return false
        return isQuiet(image, upper.box, lower.box, options.quietBandSpread)
    }

    /** The band between two blocks has to look like bubble paper, not like artwork in between. */
    private fun isQuiet(image: ArgbImage, upper: RectI, lower: RectI, limit: Float): Boolean {
        val top = upper.bottom.coerceAtMost(lower.top)
        val bottom = lower.top.coerceAtLeast(top + 1)
        val left = maxOf(upper.left, lower.left)
        val right = minOf(upper.right, lower.right)
        if (bottom <= top || right <= left) return true
        val sampleTop = top.coerceIn(0, image.height - 1)
        val sampleBottom = bottom.coerceIn(sampleTop + 1, image.height)
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        for (y in sampleTop until sampleBottom) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(image.width)) {
                val luminance = Colors.luminance(image.get(x, y)).toDouble()
                sum += luminance
                sumSquares += luminance * luminance
                count++
            }
        }
        if (count < 8) return true
        val mean = sum / count
        val variance = sumSquares / count - mean * mean
        if (variance <= 0.0) return true
        return kotlin.math.sqrt(variance) <= limit
    }

    private fun mergedHeight(first: RectI, second: RectI): Int =
        maxOf(first.bottom, second.bottom) - minOf(first.top, second.top)

    private fun join(first: TextRegion, second: TextRegion): TextRegion {
        val lines = (first.lines + second.lines).sortedWith(compareBy({ it.box.top }, { it.box.left }))
        return first.copy(
            box = first.box.union(second.box),
            lines = lines,
            text = listOf(first.text, second.text).filter { it.isNotBlank() }.joinToString(" "),
            confidence = minOf(first.confidence ?: 1f, second.confidence ?: 1f),
            font = if (first.box.height >= second.box.height) first.font else second.font,
        )
    }
}
