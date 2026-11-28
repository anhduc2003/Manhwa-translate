package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage

/**
 * The arithmetic around a text-heatmap model, kept apart from the runtime that evaluates it so it
 * can be tested on the JVM: which tiles cover a page, how a tile is packed into the model's input,
 * and how the probabilities it returns become boxes.
 *
 * The page-wide heat map is never materialised. A 720x6000 slice would cost 17 MB as floats, which a
 * phone cannot spare next to the page itself, so each tile's boxes are translated into page
 * coordinates and boxes that overlap are joined afterwards.
 */
object HeatmapRegions {
    /** Side of the square the model was trained on. */
    const val TILE = 384

    /** Distance between neighbouring tiles; half a tile, so no bubble sits on a seam. */
    const val STRIDE = 192

    /** Side of the model's input, [TILE] shrunk by [SCALE]. */
    const val INPUT = 192

    /** How much smaller the input is than the tile it covers. */
    const val SCALE = TILE / INPUT

    /** Proposals smaller than this many pixels are noise, not lettering. */
    const val MIN_AREA = 1200

    /**
     * Tiles covering a [width]x[height] page. The last tile in each direction is pulled back to the
     * edge so the page is covered exactly at its borders instead of being padded.
     */
    fun tiles(width: Int, height: Int): List<RectI> {
        if (width <= 0 || height <= 0) return emptyList()
        val xs = offsets(width)
        val ys = offsets(height)
        val result = ArrayList<RectI>(xs.size * ys.size)
        for (top in ys) {
            for (left in xs) {
                result += RectI(left, top, left + TILE, top + TILE)
            }
        }
        return result
    }

    private fun offsets(length: Int): List<Int> {
        if (length <= TILE) return listOf(0)
        val result = ArrayList<Int>()
        var position = 0
        while (position + TILE < length) {
            result += position
            position += STRIDE
        }
        result += length - TILE
        return result
    }

    /**
     * Packs [tile] into the model's input: [INPUT]x[INPUT] RGB in 0..1, channel first, each value the
     * average of the [SCALE]x[SCALE] block it stands for.
     */
    fun writeInput(image: ArgbImage, tile: RectI, out: FloatArray) {
        require(out.size == 3 * INPUT * INPUT) { "Expected ${3 * INPUT * INPUT} floats, got ${out.size}" }
        val plane = INPUT * INPUT
        val samples = (SCALE * SCALE).toFloat() * 255f
        for (y in 0 until INPUT) {
            val sourceTop = tile.top + y * SCALE
            for (x in 0 until INPUT) {
                val sourceLeft = tile.left + x * SCALE
                var red = 0
                var green = 0
                var blue = 0
                for (dy in 0 until SCALE) {
                    val row = (sourceTop + dy).coerceIn(0, image.height - 1) * image.width
                    for (dx in 0 until SCALE) {
                        val column = (sourceLeft + dx).coerceIn(0, image.width - 1)
                        val pixel = image.pixels[row + column]
                        red += (pixel shr 16) and 0xFF
                        green += (pixel shr 8) and 0xFF
                        blue += pixel and 0xFF
                    }
                }
                val index = y * INPUT + x
                out[index] = red / samples
                out[plane + index] = green / samples
                out[2 * plane + index] = blue / samples
            }
        }
    }

    /** Turns the model's raw logits into 0..1 probabilities. */
    fun probabilities(logits: FloatArray): FloatArray =
        FloatArray(logits.size) { index -> 1f / (1f + kotlin.math.exp(-logits[index])) }

    /**
     * Boxes around every connected run of pixels in [prob] (one tile's output) at or above
     * [threshold], in the tile's own pixel coordinates, skipping runs under [minArea].
     */
    fun proposals(prob: FloatArray, threshold: Float, minArea: Int = MIN_AREA): List<RectI> {
        require(prob.size == INPUT * INPUT) { "Expected ${INPUT * INPUT} values, got ${prob.size}" }
        val seen = BooleanArray(INPUT * INPUT)
        val stack = IntArray(INPUT * INPUT)
        val result = ArrayList<RectI>()
        for (start in prob.indices) {
            if (seen[start] || prob[start] < threshold) continue
            var top = Int.MAX_VALUE
            var left = Int.MAX_VALUE
            var bottom = Int.MIN_VALUE
            var right = Int.MIN_VALUE
            var area = 0
            var size = 0
            stack[size++] = start
            seen[start] = true
            while (size > 0) {
                val index = stack[--size]
                val x = index % INPUT
                val y = index / INPUT
                area++
                if (y < top) top = y
                if (y > bottom) bottom = y
                if (x < left) left = x
                if (x > right) right = x
                if (x > 0) size = push(prob, seen, stack, size, index - 1, threshold)
                if (x + 1 < INPUT) size = push(prob, seen, stack, size, index + 1, threshold)
                if (y > 0) size = push(prob, seen, stack, size, index - INPUT, threshold)
                if (y + 1 < INPUT) size = push(prob, seen, stack, size, index + INPUT, threshold)
            }
            if (area * SCALE * SCALE >= minArea) {
                result += RectI(left * SCALE, top * SCALE, (right + 1) * SCALE, (bottom + 1) * SCALE)
            }
        }
        return result
    }

    private fun push(
        prob: FloatArray,
        seen: BooleanArray,
        stack: IntArray,
        size: Int,
        index: Int,
        threshold: Float,
    ): Int {
        if (seen[index] || prob[index] < threshold) return size
        seen[index] = true
        stack[size] = index
        return size + 1
    }

    /**
     * Joins boxes that touch. Neighbouring tiles overlap by half a tile, so the two halves of one
     * bubble come back as two boxes that share area and merge into the bubble the OCR should read.
     */
    fun mergeOverlapping(boxes: List<RectI>): List<RectI> {
        val merged = ArrayList<RectI>(boxes.size)
        for (box in boxes) {
            var current = box
            var index = merged.size - 1
            while (index >= 0) {
                val other = merged[index]
                if (current.left <= other.right && other.left <= current.right &&
                    current.top <= other.bottom && other.top <= current.bottom
                ) {
                    current = current.union(other)
                    merged.removeAt(index)
                }
                index--
            }
            merged += current
        }
        return merged
    }

    /** Keeps the proposals [known] does not already cover by at least [minCoverage]. */
    fun uncovered(
        proposals: List<RectI>,
        known: List<RectI>,
        minCoverage: Float = 0.5f,
    ): List<RectI> = proposals.filter { proposal ->
        val area = proposal.area.toFloat()
        area > 0f && known.none { box -> (proposal.intersection(box)?.area ?: 0L) / area >= minCoverage }
    }
}
