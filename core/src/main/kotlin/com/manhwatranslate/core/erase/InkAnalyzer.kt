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
 * Measures stroke weight and slant of the raw letter pixels.
 *
 * The weight comes from a chamfer distance transform: for every ink pixel the distance to the
 * nearest background pixel is known, and twice the mean of those distances is the stroke
 * thickness. Counting horizontal runs instead, as this used to, mixes stems with the long bars of
 * E, T and the crossbars, so every ordinary line of dialogue measured as extra black.
 *
 * The slant is taken per text line and then taken as the median, because a single fit across a
 * multi-line bubble averages lines that lean opposite ways into no slant at all.
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
        for (value in mask) if (value.toInt() != 0) inkCount++
        if (inkCount == 0) return InkMetrics.Empty

        val distance = distanceTransform(mask, width, height)
        var total = 0f
        for (index in mask.indices) if (mask[index].toInt() != 0) total += distance[index]
        val strokeWidth = 2f * total / inkCount

        return InkMetrics(
            coverage = inkCount.toFloat() / (width.toFloat() * height.toFloat()),
            strokeWidthPx = strokeWidth,
            slant = medianLineSlant(mask, width, height),
            inkCount = inkCount,
        )
    }

    /** Distance from every ink pixel to the nearest background pixel, 3-4 chamfer. */
    private fun distanceTransform(mask: ByteArray, width: Int, height: Int): FloatArray {
        val diagonal = 1.41421356f
        val far = 1e6f
        val distance = FloatArray(width * height) { if (mask[it].toInt() == 0) 0f else far }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (distance[index] == 0f) continue
                var best = distance[index]
                if (x > 0) best = minOf(best, distance[index - 1] + 1f)
                if (y > 0) {
                    best = minOf(best, distance[index - width] + 1f)
                    if (x > 0) best = minOf(best, distance[index - width - 1] + diagonal)
                    if (x < width - 1) best = minOf(best, distance[index - width + 1] + diagonal)
                }
                distance[index] = best
            }
        }
        for (y in height - 1 downTo 0) {
            for (x in width - 1 downTo 0) {
                val index = y * width + x
                if (distance[index] == 0f) continue
                var best = distance[index]
                if (x < width - 1) best = minOf(best, distance[index + 1] + 1f)
                if (y < height - 1) {
                    best = minOf(best, distance[index + width] + 1f)
                    if (x < width - 1) best = minOf(best, distance[index + width + 1] + diagonal)
                    if (x > 0) best = minOf(best, distance[index + width - 1] + diagonal)
                }
                distance[index] = best
            }
        }
        return distance
    }

    /**
     * Median slant of the lettering, measured line by line.
     *
     * Each line is sheared by a series of angles and the shear that makes the ink columns pile up
     * into the sharpest peaks is the slant. Tracking the centre of the ink instead, as this used
     * to, follows the shape of the alphabet rather than the pen: the top of a line holds only the
     * tall letters, so the centre drifts even in perfectly upright text.
     */
    private fun medianLineSlant(mask: ByteArray, width: Int, height: Int): Float {
        val slopes = ArrayList<Float>(4)
        var y = 0
        while (y < height) {
            if (!rowHasInk(mask, width, y)) { y++; continue }
            val start = y
            while (y < height && rowHasInk(mask, width, y)) y++
            val rows = y - start
            if (rows < 8) continue
            lineSlant(mask, width, start, rows)?.let { slopes += it }
        }
        if (slopes.isEmpty()) return 0f
        slopes.sort()
        return slopes[slopes.size / 2]
    }

    /** The shear, in pixels of horizontal shift per row, that sharpens the line the most. */
    private fun lineSlant(mask: ByteArray, width: Int, start: Int, rows: Int): Float? {
        var best = 0f
        var bestScore = -1f
        val margin = Math.round(0.6f * rows).toInt() + 1
        var shear = -0.6f
        while (shear <= 0.61f) {
            val profile = IntArray(width + 2 * margin)
            var total = 0L
            for (row in 0 until rows) {
                val shift = Math.round(shear * row)
                val offset = (start + row) * width
                for (x in 0 until width) {
                    if (mask[offset + x].toInt() == 0) continue
                    val target = x - shift + margin
                    profile[target]++
                    total++
                }
            }
            if (total > 0) {
                var squares = 0.0
                for (value in profile) squares += (value.toDouble() * value.toDouble())
                val score = (squares / (total.toDouble() * total.toDouble())).toFloat()
                if (score > bestScore) {
                    bestScore = score
                    best = shear
                }
            }
            shear += 0.1f
        }
        return best
    }

    private fun rowHasInk(mask: ByteArray, width: Int, y: Int): Boolean {
        val offset = y * width
        for (x in 0 until width) if (mask[offset + x].toInt() != 0) return true
        return false
    }
}
