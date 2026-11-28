package com.manhwatranslate.core.scan

import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.model.TextRegion

enum class SfxSensitivity(val threshold: Int) {
    OFF(Int.MAX_VALUE),
    LOW(5),
    MEDIUM(4),
    HIGH(3),
}

data class SfxAnalysis(
    val isSoundEffect: Boolean,
    val score: Int,
    val reasons: List<String> = emptyList(),
)

/**
 * Tells dialogue apart from sound effects ("CRASH!", "BAM") so scanslation does not erase and
 * translate artwork lettering. Dialogue lives in a bubble: small letters on a quiet, uniform
 * surround. Sound effects are big display type drawn straight onto the artwork, so the area
 * around them is busy and their letters are large relative to the page.
 */
object SfxFilter {

    fun analyze(
        image: ArgbImage,
        region: TextRegion,
        pageWidth: Int,
        sensitivity: SfxSensitivity = SfxSensitivity.MEDIUM,
    ): SfxAnalysis {
        if (sensitivity == SfxSensitivity.OFF) return SfxAnalysis(false, 0)
        val reasons = ArrayList<String>()
        var score = 0

        val lineCount = region.lines.size.coerceAtLeast(1)
        val charHeight = region.box.height.toFloat() / lineCount
        val relativeHeight = charHeight / pageWidth.toFloat()
        if (relativeHeight > 0.055f) {
            score += 2
            reasons += "chữ quá lớn so với trang"
        } else if (relativeHeight > 0.04f) {
            score += 1
            reasons += "chữ lớn"
        }

        val surround = surroundVariance(image, region)
        when {
            surround > 52f -> {
                score += 2
                reasons += "vùng quanh là hình vẽ, không phải bong bóng"
            }
            surround > 26f -> {
                score += 1
                reasons += "nền quanh chữ có hình"
            }
            surround < 8f -> {
                score -= 2
                reasons += "nền quanh chữ trơn đều (bong bóng)"
            }
        }

        val letters = region.text.count { it.isLetter() }
        if (letters <= 5) {
            score += 1
            reasons += "quá ít ký tự"
        }
        val aspect = region.box.width.toFloat() / region.box.height.toFloat().coerceAtLeast(1f)
        if (aspect > 5f && region.lines.size == 1) {
            score += 1
            reasons += "một dòng kéo dài"
        }
        if (region.lines.size == 1 && !region.text.contains(' ') && letters <= 10) {
            score += 1
            reasons += "một từ đơn"
        }

        // A sound effect is one shout: "BAM", "CRASH". Two words with a sentence shape are
        // dialogue even when the letters are large and the bubble is drawn on the artwork, which
        // is exactly what a spiky speech balloon looks like from the outside.
        val words = region.text.split(' ').count { word -> word.any { it.isLetter() } }
        if (words >= 2 && letters >= 8) {
            reasons += "câu thoại, không phải hiệu ứng"
            return SfxAnalysis(false, 0, reasons)
        }
        if (words >= 3 && letters >= 12) {
            reasons += "câu thoại, không phải hiệu ứng"
            return SfxAnalysis(false, 0, reasons)
        }
        return SfxAnalysis(score >= sensitivity.threshold, score, reasons)
    }

    /** Standard deviation of luminance in a band just outside the text box. */
    private fun surroundVariance(image: ArgbImage, region: TextRegion): Float {
        val band = (region.box.width / 40).coerceIn(4, 10)
        val outer = region.box.expanded(band).clamped(image.width, image.height) ?: return 0f
        val inner = region.box.expanded(-1)
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        val step = 2
        for (y in outer.top until outer.bottom step step) {
            for (x in outer.left until outer.right step step) {
                if (inner.width > 0 && inner.height > 0 && inner.contains(x, y)) continue
                val luminance = Colors.luminance(image.get(x, y)).toDouble()
                sum += luminance
                sumSquares += luminance * luminance
                count++
            }
        }
        if (count < 24) return 0f
        val mean = sum / count
        val variance = sumSquares / count - mean * mean
        return if (variance <= 0.0) 0f else kotlin.math.sqrt(variance).toFloat()
    }
}
