package com.manhwatranslate.core.scan

import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion

/**
 * Cuts a region that actually holds two bubbles stacked on top of each other.
 *
 * OCR engines group nearby lettering into one region, so a close pair of bubbles arrives as a
 * single region carrying both bubbles' lines. The region then gets one long translation drawn
 * across both outlines, which reads as nonsense and spills over the artwork.
 *
 * Two signals separate the pair. Inside one bubble the lines are packed tightly, so the gap
 * between two bubbles stands out as several times the gap between lines; and where the two
 * bubbles touch, the band between their lines crosses the upper bubble's edge. A region is cut
 * at every line break where either signal fires.
 */
object RegionSplitter {

    data class Options(
        /** Darker than this counts as ink, so a bubble edge reads as a stroke. */
        val outlineLuminance: Float = 120f,
        /** Share of the lines' shared width a stroke must cover to count as a bubble edge. */
        val outlineCoverage: Float = 0.5f,
        /** Bubble paper is this light on average; lettering on dark artwork is never split. */
        val paperLuminance: Float = 150f,
        /** A gap wider than this share of the line height starts a new bubble without a stroke. */
        val maxGapRatio: Float = 0.85f,
        /** Gaps narrower than this many pixels never split, whatever the line height. */
        val minGapPx: Int = 10,
        /** Trimmed from both lines so their own glyphs never read as an outline. */
        val bandInset: Int = 2,
    )

    fun split(
        image: ArgbImage,
        regions: List<TextRegion>,
        options: Options = Options(),
    ): List<TextRegion> = regions.flatMap { splitRegion(image, it, options) }

    private fun splitRegion(image: ArgbImage, region: TextRegion, options: Options): List<TextRegion> {
        val lines = region.lines.sortedBy { it.box.top }
        if (lines.size < 2) return listOf(region)
        if (!linesFitRegion(region, lines)) return listOf(region)
        val threshold = maxOf(options.minGapPx, (medianHeight(lines) * options.maxGapRatio).toInt())
        val groups = ArrayList<MutableList<TextLine>>()
        var current = arrayListOf(lines.first())
        for (index in 1 until lines.size) {
            val upper = lines[index - 1]
            val lower = lines[index]
            val gap = lower.box.top - upper.box.bottom
            if (gap > threshold || crossesOutline(image, upper, lower, options)) {
                groups += current
                current = arrayListOf(lower)
            } else {
                current += lower
            }
        }
        groups += current
        if (groups.size == 1) return listOf(region)
        return groups.map { group ->
            val box = group.map { it.box }.reduce { left, right -> left.union(right) }
            region.copy(
                box = box,
                lines = group,
                text = group.joinToString(" ") { it.text }.trim(),
            )
        }
    }

    /**
     * A region whose lines sit outside its own box has boxes measured against another origin,
     * so its pixel coordinates say nothing about the image and it is left alone.
     */
    private fun linesFitRegion(region: TextRegion, lines: List<TextLine>): Boolean {
        val union = lines.map { it.box }.reduce { left, right -> left.union(right) }
        val slack = 4
        return union.left >= region.box.left - slack &&
            union.top >= region.box.top - slack &&
            union.right <= region.box.right + slack &&
            union.bottom <= region.box.bottom + slack
    }

    /** True when the band between two lines carries a stroke wide enough to be a bubble edge. */
    private fun crossesOutline(
        image: ArgbImage,
        upper: TextLine,
        lower: TextLine,
        options: Options,
    ): Boolean {
        val left = maxOf(upper.box.left, lower.box.left).coerceIn(0, image.width)
        val right = minOf(upper.box.right, lower.box.right).coerceIn(left, image.width)
        val span = right - left
        if (span < 8) return false
        val top = (upper.box.bottom + options.bandInset).coerceIn(0, image.height)
        val bottom = (lower.box.top - options.bandInset).coerceIn(top, image.height)
        if (bottom - top < 2) return false
        var total = 0.0
        var rows = 0
        for (y in top until bottom) {
            for (x in left until right) total += Colors.luminance(image.get(x, y))
            rows++
        }
        if (total / (rows * span) < options.paperLuminance) return false
        val needed = span * options.outlineCoverage
        for (y in top until bottom) {
            var dark = 0
            for (x in left until right) {
                if (Colors.luminance(image.get(x, y)) < options.outlineLuminance) dark++
            }
            if (dark >= needed) return true
        }
        return false
    }

    private fun medianHeight(lines: List<TextLine>): Int {
        val heights = lines.map { it.box.height }.sorted()
        return heights[heights.size / 2].coerceAtLeast(1)
    }
}
