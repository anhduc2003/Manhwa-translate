package com.manhwatranslate.core.erase

/** Measurements of the original lettering, used to pick a matching font. */
data class InkMetrics(
    val coverage: Float,
    val strokeWidthPx: Float,
    val slant: Float,
    val inkCount: Int,
) {
    companion object {
        val Empty = InkMetrics(0f, 0f, 0f, 0)
    }
}

/**
 * Measures stroke weight and slant of the raw letter pixels. Horizontal run lengths approximate
 * stroke width for the vertical stems that dominate printed lettering; the drift of each row's
 * ink centre line reveals an italic face.
 */
object InkAnalyzer {

    fun analyze(
        mask: ByteArray,
        width: Int,
        height: Int,
        rowCount: Int = height,
    ): InkMetrics {
        if (width <= 0 || height <= 0) return InkMetrics.Empty
        var inkCount = 0
        var runTotal = 0
        var runSamples = 0
        val centroids = FloatArray(height) { Float.NaN }

        for (y in 0 until height) {
            var rowSum = 0
            var rowCount = 0
            var run = 0
            for (x in 0 until width) {
                val ink = mask[y * width + x].toInt() != 0
                if (ink) {
                    inkCount++
                    run++
                    rowSum += x
                    rowCount++
                } else if (run > 0) {
                    if (run >= 2 && run < width * 0.7f) {
                        runTotal += run
                        runSamples++
                    }
                    run = 0
                }
            }
            if (run > 0 && run >= 2 && run < width * 0.7f) {
                runTotal += run
                runSamples++
            }
            if (rowCount > 0) centroids[y] = rowSum.toFloat() / rowCount
        }

        val strokeWidth = if (runSamples == 0) 0f else runTotal.toFloat() / runSamples.toFloat()
        return InkMetrics(
            coverage = inkCount.toFloat() / (width.toFloat() * height.toFloat()),
            strokeWidthPx = strokeWidth,
            slant = slope(centroids),
            inkCount = inkCount,
        )
    }

    private fun slope(values: FloatArray): Float {
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (index in values.indices) {
            if (values[index].isNaN()) continue
            sumX += index.toFloat()
            sumY += values[index]
            count++
        }
        if (count < 4) return 0f
        val meanX = sumX / count
        val meanY = sumY / count
        var numerator = 0f
        var denominator = 0f
        for (index in values.indices) {
            if (values[index].isNaN()) continue
            val dx = index - meanX
            numerator += dx * (values[index] - meanY)
            denominator += dx * dx
        }
        if (denominator <= 0f) return 0f
        return numerator / denominator
    }
}
