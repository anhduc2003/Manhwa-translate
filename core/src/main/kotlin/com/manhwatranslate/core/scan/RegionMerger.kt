package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion

/**
 * Cleans up raw detector output: the same bubble found twice in overlapping tiles, or a
 * paragraph cut in half at a tile boundary, must end up as exactly one region.
 */
object RegionMerger {

    data class Options(
        val iouThreshold: Float = 0.30f,
        val containmentThreshold: Float = 0.70f,
        val maxSplitGapPx: Int = 6,
        val minLetterCount: Int = 1,
        val minBoxSizePx: Int = 6,
        val mergeAdjacentSplits: Boolean = true,
    )

    fun refine(raw: List<TextRegion>, options: Options = Options()): List<TextRegion> {
        val cleaned = raw
            .filter { !it.isBlank }
            .filter { it.box.width >= options.minBoxSizePx && it.box.height >= options.minBoxSizePx }
            .filter { it.letterCount >= options.minLetterCount }
        val deduped = mergeOverlaps(cleaned, options)
        val stitched = if (options.mergeAdjacentSplits) mergeSplits(deduped, options) else deduped
        return sortReadingOrder(stitched).mapIndexed { index, region -> region.copy(id = index) }
    }

    private fun mergeOverlaps(regions: List<TextRegion>, options: Options): List<TextRegion> {
        val result = ArrayList<TextRegion>()
        for (region in regions) {
            var current = region
            var index = 0
            while (index < result.size) {
                val existing = result[index]
                val iou = existing.box.iou(current.box)
                val contained = existing.box.coverageBy(current.box) >= options.containmentThreshold ||
                    current.box.coverageBy(existing.box) >= options.containmentThreshold
                val sameText = existing.text.isNotBlank() && existing.text == current.text
                // A slanted line and a level one are two different pieces of lettering even when
                // their boxes overlap; joining them would level the translation of the slanted one.
                val sameTilt = kotlin.math.abs(existing.rotationDegrees - current.rotationDegrees) <= 3f
                if (sameTilt && (iou >= options.iouThreshold || contained ||
                        (sameText && existing.box.iou(current.box) > 0.15f))
                ) {
                    current = merge(existing, current)
                    result.removeAt(index)
                } else {
                    index++
                }
            }
            result += current
        }
        return result
    }

    private fun mergeSplits(regions: List<TextRegion>, options: Options): List<TextRegion> {
        val sorted = regions.sortedBy { it.box.top }
        val result = ArrayList<TextRegion>()
        for (region in sorted) {
            val split = result.firstOrNull { existing -> isSplit(existing, region, options) }
            if (split != null) {
                result.remove(split)
                result += merge(split, region)
            } else {
                result += region
            }
        }
        return result
    }

    private fun isSplit(a: TextRegion, b: TextRegion, options: Options): Boolean {
        if (kotlin.math.abs(a.rotationDegrees - b.rotationDegrees) > 3f) return false
        val gap = a.box.verticalGap(b.box)
        if (gap > options.maxSplitGapPx) return false
        val overlapWidth = a.box.horizontalOverlap(b.box)
        val minWidth = minOf(a.box.width, b.box.width)
        if (minWidth <= 0) return false
        val horizontalOverlapRatio = overlapWidth.toFloat() / minWidth.toFloat()
        if (horizontalOverlapRatio < 0.75f) return false
        val leftAligned = kotlin.math.abs(a.box.left - b.box.left) <= 4
        val centered = kotlin.math.abs(a.box.centerX - b.box.centerX) <= maxOf(3, minWidth / 8)
        return leftAligned || centered
    }

    private fun merge(a: TextRegion, b: TextRegion): TextRegion {
        val box = a.box.union(b.box)
        val lines = dedupeLines(a.lines + b.lines)
        val text = if (lines.isNotEmpty()) {
            lines.joinToString(" ") { it.text }.trim()
        } else {
            listOf(a.text, b.text).filter { it.isNotBlank() }.joinToString(" ").trim()
        }
        val confidence = listOfNotNull(a.confidence, b.confidence).average().takeIf { !it.isNaN() }
        return a.copy(
            box = box,
            lines = lines,
            text = text,
            script = if (a.script == com.manhwatranslate.core.model.SourceScript.AUTO) b.script else a.script,
            confidence = confidence?.toFloat(),
            font = if (a.box.height >= b.box.height) a.font else b.font,
            uprightWidthPx = maxOf(a.uprightWidthPx, b.uprightWidthPx),
            uprightHeightPx = maxOf(a.uprightHeightPx, b.uprightHeightPx),
        )
    }

    fun dedupeLines(lines: List<TextLine>): List<TextLine> {
        val kept = ArrayList<TextLine>()
        for (line in lines.sortedBy { it.box.top }) {
            val duplicate = kept.any { existing ->
                existing.box.iou(line.box) > 0.5f || (existing.text == line.text && existing.box.intersects(line.box))
            }
            if (!duplicate && line.text.isNotBlank()) kept += line
        }
        return kept.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }

    /** Manhwa pages read top to bottom; side-by-side bubbles read left to right. */
    fun sortReadingOrder(regions: List<TextRegion>): List<TextRegion> {
        if (regions.isEmpty()) return regions
        val sorted = regions.sortedWith(
            compareBy<TextRegion> { it.box.top / 24 }.thenBy { it.box.left },
        )
        return sorted
    }

    fun assignIds(regions: List<TextRegion>): List<TextRegion> =
        regions.mapIndexed { index, region -> region.withId(index) }
}
