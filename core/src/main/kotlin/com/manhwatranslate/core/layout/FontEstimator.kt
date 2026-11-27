package com.manhwatranslate.core.layout

import com.manhwatranslate.core.erase.InkMetrics
import com.manhwatranslate.core.model.TextRegion

/**
 * Picks the closest available font for the original lettering: weight from measured stroke
 * thickness, italic from the slant of the ink, condensed for short all-caps display lines.
 * The user can pin a family, in which case only weight and slant follow the artwork.
 */
object FontEstimator {

    data class Result(
        val font: FontSpec,
        val strokeRatio: Float,
        val estimatedTextSizePx: Float,
    )

    fun estimate(
        region: TextRegion,
        metrics: InkMetrics,
        preferredFamily: FontFamilyKind?,
        maxSizePx: Float,
        minSizePx: Float,
    ): Result {
        val lineCount = region.lines.size.coerceAtLeast(1)
        val lineHeight = region.box.height.toFloat() / lineCount
        val ratio = if (lineHeight <= 0f) 0f else metrics.strokeWidthPx / lineHeight
        val weight = when {
            ratio <= 0f -> FontWeightLevel.BOLD
            ratio < 0.075f -> FontWeightLevel.REGULAR
            ratio < 0.115f -> FontWeightLevel.MEDIUM
            ratio < 0.165f -> FontWeightLevel.BOLD
            else -> FontWeightLevel.BLACK
        }
        val italic = kotlin.math.abs(metrics.slant) > 0.12f
        val family = preferredFamily ?: autoFamily(region)
        val size = (lineHeight * 0.78f).coerceIn(minSizePx, maxSizePx)
        return Result(
            font = FontSpec(family = family, weight = weight, italic = italic),
            strokeRatio = ratio,
            estimatedTextSizePx = size,
        )
    }

    private fun autoFamily(region: TextRegion): FontFamilyKind {
        val letters = region.text.filter { it.isLetter() }
        val allCaps = letters.isNotEmpty() && letters.all { it.isUpperCase() }
        return when {
            allCaps && letters.length <= 24 -> FontFamilyKind.CONDENSED
            else -> FontFamilyKind.SANS
        }
    }
}
