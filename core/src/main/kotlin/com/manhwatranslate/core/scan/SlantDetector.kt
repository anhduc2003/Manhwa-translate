package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A stretch of lettering that lies along a slant, with the angle it is drawn at. */
data class SlantedLettering(
    /** Tight box around the ink, in the coordinates of the image it was found in. */
    val box: RectI,
    /** Degrees clockwise off level, which is also the angle the translation has to be drawn at. */
    val angleDegrees: Float,
    val inkPixels: Int,
)

/**
 * Finds lettering drawn along a slant without being told which angles to try.
 *
 * A recognizer that expects a level baseline returns nothing for a line that runs uphill, so the
 * page used to be read again at two fixed turns. A line at any other angle stayed invisible.
 * Here the ink itself is measured first: strokes stand out from the paper around them, the strokes
 * of one line are grouped together, and the turn that piles their rows into the sharpest peaks is
 * the angle the line is drawn at. Each candidate is then read once, levelled, at its own angle.
 */
object SlantDetector {

    /** Share of the local shade range a pixel has to sit within of either end to count as a stroke. */
    private const val INK_SHARE = 0.30f

    /**
     * Above this many pixels a tile is measured at half size. The angle survives it (a stroke is
     * still several pixels wide) and the pass costs a quarter of the work, which matters on a
     * phone handling a webtoon strip tile by tile.
     */
    private const val FULL_RESOLUTION_PIXELS = 420_000L

    data class Options(
        /** Slants shallower than this are left to the level pass. */
        val minAngleDegrees: Float = 8f,
        val maxAngleDegrees: Float = 46f,
        /** Least ink a candidate may hold. */
        val minInkPixels: Int = 45,
        /** Shortest candidate along its own line, so a stray mark is not offered as dialogue. */
        val minLengthPx: Int = 40,
        val minThicknessPx: Float = 4f,
        /** A candidate thicker than this is artwork, not a line of lettering. */
        val maxThicknessPx: Float = 280f,
        /** Least spread of shades a stroke has to stand out by. */
        val inkThreshold: Int = 26,
        /** Radius over which the darkest and lightest shade around a pixel is measured. */
        val paperRadius: Int = 3,
        /** Most candidates offered, the longest line first. */
        val maxCandidates: Int = 4,
        /** A line of lettering is several glyphs; one long stroke is a panel border or speed line. */
        val minStrokes: Int = 2,
        /** Least share of empty columns between the glyphs of a line, at its own angle. */
        val minGapRatio: Float = 0.08f,
        val coarseStepDegrees: Float = 3f,
        val fineStepDegrees: Float = 0.5f,
        /** Most ink points used to score an angle; the rest are sampled. */
        val maxPoints: Int = 4000,
    )

    fun detect(
        image: ArgbImage,
        skipBoxes: List<RectI> = emptyList(),
        options: Options = Options(),
    ): List<SlantedLettering> {
        if (image.width < options.minLengthPx + 2 || image.height < 12) return emptyList()
        val factor = if (image.width.toLong() * image.height > FULL_RESOLUTION_PIXELS) 2 else 1
        val working = if (factor == 1) image else downscale(image, factor)
        val scaled = if (factor == 1) {
            options
        } else {
            options.copy(
                minInkPixels = (options.minInkPixels / 4).coerceAtLeast(6),
                minLengthPx = (options.minLengthPx / 2).coerceAtLeast(12),
                minThicknessPx = (options.minThicknessPx / 2f).coerceAtLeast(2f),
                maxThicknessPx = options.maxThicknessPx / 2f,
            )
        }
        val scaledSkips = skipBoxes.map {
            RectI(it.left / factor, it.top / factor, it.right / factor, it.bottom / factor)
        }
        val ink = inkMask(working, scaled)
        for (box in scaledSkips) clear(ink, working.width, working.height, box.expanded(3))

        val components = componentsOf(ink, working.width, working.height)
        if (components.isEmpty()) return emptyList()

        val found = ArrayList<SlantedLettering>()
        for (cluster in cluster(components)) {
            if (cluster.ink < scaled.minInkPixels) continue
            if (cluster.box.width < scaled.minLengthPx) continue
            if (scaledSkips.any { it.expanded(2).contains(cluster.box) }) continue
            val angle = angleOf(ink, working.width, cluster.box, cluster.strokes, scaled) ?: continue
            if (abs(angle) < scaled.minAngleDegrees) continue
            val box = if (factor == 1) {
                cluster.box
            } else {
                RectI(
                    (cluster.box.left * factor).coerceAtMost(image.width - 1),
                    (cluster.box.top * factor).coerceAtMost(image.height - 1),
                    (cluster.box.right * factor).coerceAtMost(image.width),
                    (cluster.box.bottom * factor).coerceAtMost(image.height),
                )
            }
            found += SlantedLettering(box, angle, cluster.ink * factor * factor)
        }
        return found.sortedByDescending { it.inkPixels }.take(options.maxCandidates)
    }

    /**
     * Strokes against the paper they sit on. A pixel counts as ink when it sits at one end of the
     * local range of shades, so a flat margin scores nothing, lettering scores along its glyphs,
     * and the paper just outside a stroke stays out — a mean would smear every stroke into a halo
     * and close the gaps between glyphs, which the angle measurement needs to see.
     */
    private fun inkMask(image: ArgbImage, options: Options): ByteArray {
        val width = image.width
        val height = image.height
        val luminance = IntArray(width * height) { Colors.luminance(image.pixels[it]).toInt() }
        val radius = options.paperRadius.coerceAtLeast(1)
        val horizontalMin = IntArray(luminance.size)
        val horizontalMax = IntArray(luminance.size)
        horizontal(luminance, width, height, radius, horizontalMin, horizontalMax, minimum = true)
        horizontal(luminance, width, height, radius, horizontalMin, horizontalMax, minimum = false)
        val minima = IntArray(luminance.size)
        val maxima = IntArray(luminance.size)
        vertical(horizontalMin, horizontalMax, width, height, radius, minima, maxima, minimum = true)
        vertical(horizontalMin, horizontalMax, width, height, radius, minima, maxima, minimum = false)

        // The paper around a stroke, averaged over a wider window than the extrema are taken, so
        // "is this pixel at the end of its local range" and "is it off the paper at all" have to
        // agree before a pixel counts as a stroke.
        val meanRadius = options.paperRadius + 4
        val stride = width + 1
        val integral = IntArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0
            val rowOffset = (y + 1) * stride
            val sourceOffset = y * width
            for (x in 0 until width) {
                rowSum += luminance[sourceOffset + x]
                integral[rowOffset + x + 1] = integral[rowOffset - stride + x + 1] + rowSum
            }
        }
        val margin = maxOf(14, options.inkThreshold)
        val mask = ByteArray(luminance.size)
        for (y in 0 until height) {
            val top = (y - meanRadius).coerceAtLeast(0)
            val bottom = (y + meanRadius + 1).coerceAtMost(height)
            for (x in 0 until width) {
                val left = (x - meanRadius).coerceAtLeast(0)
                val right = (x + meanRadius + 1).coerceAtMost(width)
                val sum = integral[bottom * stride + right] -
                    integral[top * stride + right] -
                    integral[bottom * stride + left] +
                    integral[top * stride + left]
                val index = y * width + x
                val paper = sum / ((bottom - top) * (right - left))
                val sample = luminance[index]
                val low = minima[index]
                val high = maxima[index]
                val range = high - low
                if (range < options.inkThreshold) continue
                val fromLow = sample - low
                val fromHigh = high - sample
                // The paper is the commoner shade of the window, so the end of the range it sits
                // on is the paper end: only the other end carries strokes. Without this, paper
                // pixels between two dark glyphs read as light ink and flood the gaps the angle
                // measurement needs.
                val dark = if (paper - low >= high - paper) {
                    fromLow <= range * INK_SHARE && sample <= paper - margin
                } else {
                    false
                }
                val light = if (paper - low < high - paper) {
                    fromHigh <= range * INK_SHARE && sample >= paper + margin
                } else {
                    false
                }
                if (dark || light) mask[index] = 1
            }
        }
        return mask
    }

    private fun horizontal(
        source: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        minima: IntArray,
        maxima: IntArray,
        minimum: Boolean,
    ) {
        val target = if (minimum) minima else maxima
        val deque = IntArray(width)
        for (y in 0 until height) {
            val offset = y * width
            var head = 0
            var tail = 0
            var next = 0
            for (x in 0 until width) {
                val right = minOf(x + radius, width - 1)
                while (next <= right) {
                    val value = source[offset + next]
                    if (minimum) {
                        while (tail > head && source[offset + deque[tail - 1]] >= value) tail--
                    } else {
                        while (tail > head && source[offset + deque[tail - 1]] <= value) tail--
                    }
                    deque[tail++] = next
                    next++
                }
                while (deque[head] < x - radius) head++
                target[offset + x] = source[offset + deque[head]]
            }
        }
    }

    private fun vertical(
        sourceMin: IntArray,
        sourceMax: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        minima: IntArray,
        maxima: IntArray,
        minimum: Boolean,
    ) {
        val source = if (minimum) sourceMin else sourceMax
        val target = if (minimum) minima else maxima
        val deque = IntArray(height)
        for (x in 0 until width) {
            var head = 0
            var tail = 0
            var next = 0
            for (y in 0 until height) {
                val bottom = minOf(y + radius, height - 1)
                while (next <= bottom) {
                    val value = source[next * width + x]
                    if (minimum) {
                        while (tail > head && source[deque[tail - 1] * width + x] >= value) tail--
                    } else {
                        while (tail > head && source[deque[tail - 1] * width + x] <= value) tail--
                    }
                    deque[tail++] = next
                    next++
                }
                while (deque[head] < y - radius) head++
                target[y * width + x] = source[deque[head] * width + x]
            }
        }
    }

    /** Half-size copy, so a full tile is measured over a quarter of the pixels. */
    private fun downscale(image: ArgbImage, factor: Int): ArgbImage {
        val width = image.width / factor
        val height = image.height / factor
        val out = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0 until factor) {
                    val rowOffset = (y * factor + dy) * image.width
                    for (dx in 0 until factor) {
                        val pixel = image.pixels[rowOffset + x * factor + dx]
                        a += Colors.alpha(pixel)
                        r += Colors.red(pixel)
                        g += Colors.green(pixel)
                        b += Colors.blue(pixel)
                    }
                }
                val count = factor * factor
                out[y * width + x] = Colors.pack(a / count, r / count, g / count, b / count)
            }
        }
        return ArgbImage(width, height, out)
    }

    private fun clear(ink: ByteArray, width: Int, height: Int, box: RectI) {
        val rect = box.clamped(width, height) ?: return
        for (y in rect.top until rect.bottom) {
            val offset = y * width
            for (x in rect.left until rect.right) ink[offset + x] = 0
        }
    }

    private class Component(val box: RectI, val count: Int)

    private fun componentsOf(ink: ByteArray, width: Int, height: Int): List<Component> {
        val visited = ByteArray(ink.size)
        val stack = IntArray(ink.size)
        val found = ArrayList<Component>()
        for (start in ink.indices) {
            if (ink[start].toInt() == 0 || visited[start].toInt() != 0) continue
            var head = 0
            var tail = 0
            stack[tail++] = start
            visited[start] = 1
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            var count = 0
            while (head < tail) {
                val index = stack[head++]
                val x = index % width
                val y = index / width
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    val rowOffset = ny * width
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val neighbour = rowOffset + nx
                        if (ink[neighbour].toInt() == 0 || visited[neighbour].toInt() != 0) continue
                        visited[neighbour] = 1
                        if (tail < stack.size) stack[tail++] = neighbour
                    }
                }
            }
            if (count < 3) continue
            found += Component(RectI(minX, minY, maxX + 1, maxY + 1), count)
        }
        return found
    }

    private class Cluster(var box: RectI, var ink: Int, var strokes: Int)

    /** Strokes of one line sit close together; strokes of different panels do not. */
    private fun cluster(components: List<Component>): List<Cluster> {
        val parent = IntArray(components.size) { it }
        fun root(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }

        val cell = 24
        val buckets = HashMap<Long, MutableList<Int>>()
        for (index in components.indices) {
            val box = components[index].box
            val gap = gapFor(box)
            for (cellY in (box.top - gap) / cell..(box.bottom + gap) / cell) {
                for (cellX in (box.left - gap) / cell..(box.right + gap) / cell) {
                    val key = key(cellX, cellY)
                    val neighbours = buckets[key] ?: continue
                    for (other in neighbours) {
                        val otherBox = components[other].box
                        if (!box.expanded(minOf(gap, gapFor(otherBox))).intersects(otherBox)) continue
                        val a = root(other)
                        val b = root(index)
                        if (a != b) parent[b] = a
                    }
                }
            }
            for (cellY in box.top / cell..box.bottom / cell) {
                for (cellX in box.left / cell..box.right / cell) {
                    buckets.getOrPut(key(cellX, cellY)) { ArrayList() } += index
                }
            }
        }

        val byRoot = LinkedHashMap<Int, Cluster>()
        for (index in components.indices) {
            val component = components[index]
            val existing = byRoot[root(index)]
            byRoot[root(index)] = if (existing == null) {
                Cluster(component.box, component.count, strokes = 1)
            } else {
                existing.box = existing.box.union(component.box)
                existing.ink += component.count
                existing.strokes++
                existing
            }
        }
        return byRoot.values.toList()
    }

    private fun key(x: Int, y: Int): Long = (y.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)

    private fun gapFor(box: RectI): Int = (box.height * 0.9f).toInt().coerceIn(3, 18)

    /**
     * The angle the cluster is drawn at, or null when it is not a line of lettering. Measured by
     * levelling the ink: at the angle the line runs along, its strokes collapse onto few rows.
     */
    private fun angleOf(
        ink: ByteArray,
        width: Int,
        box: RectI,
        strokes: Int,
        options: Options,
    ): Float? {
        val left = box.left
        val top = box.top
        val clusterWidth = box.width
        val clusterHeight = box.height
        var total = 0
        for (y in top until box.bottom) {
            val offset = y * width
            for (x in left until box.right) if (ink[offset + x].toInt() != 0) total++
        }
        if (total < options.minInkPixels) return null
        val budget = minOf(options.maxPoints, total)
        val stride = ceil(total.toFloat() / budget).toInt().coerceAtLeast(1)
        val xs = IntArray(budget)
        val ys = IntArray(budget)
        var count = 0
        var seen = 0
        for (y in top until box.bottom) {
            val offset = y * width
            for (x in left until box.right) {
                if (ink[offset + x].toInt() == 0) continue
                if (seen++ % stride != 0) continue
                if (count >= budget) break
                xs[count] = x - left
                ys[count] = y - top
                count++
            }
            if (count >= budget) break
        }
        if (count < 12) return null
        val diagonal = sqrt((clusterWidth * clusterWidth + clusterHeight * clusterHeight).toDouble())
        val rows = IntArray(diagonal.toInt() + 4)
        val offset = rows.size / 2
        val centre = box.centerX - left
        val middle = box.centerY - top
        val coarse = pickAngle(
            xs, ys, count, rows, offset, centre, middle,
            -options.maxAngleDegrees, options.maxAngleDegrees, options.coarseStepDegrees,
        )
        val refined = pickAngle(
            xs, ys, count, rows, offset, centre, middle,
            coarse - 3f, coarse + 3f, options.fineStepDegrees,
        )

        val radians = Math.toRadians(-refined.toDouble())
        val cos = cos(radians)
        val sin = sin(radians)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (index in 0 until count) {
            val x = (xs[index] - centre).toFloat()
            val y = (ys[index] - middle).toFloat()
            val turnedX = (x * cos - y * sin).toFloat()
            val turnedY = (x * sin + y * cos).toFloat()
            if (turnedX < minX) minX = turnedX
            if (turnedX > maxX) maxX = turnedX
            if (turnedY < minY) minY = turnedY
            if (turnedY > maxY) maxY = turnedY
        }
        val length = maxX - minX
        val thickness = maxY - minY
        if (length < options.minLengthPx) return null
        if (thickness < options.minThicknessPx || thickness > options.maxThicknessPx) return null
        if (strokes < options.minStrokes) {
            // One connected stroke is a border or a speed line unless the ink leaves gaps along
            // the line, which only lettering (or a dashed line) does.
            val bin = maxOf(1, stride)
            val columns = BooleanArray((length / bin).toInt() + 2)
            for (index in 0 until count) {
                val x = (xs[index] - centre).toFloat()
                val y = (ys[index] - middle).toFloat()
                val column = ((x * cos - y * sin) - minX) / bin
                columns[column.toInt().coerceIn(0, columns.size - 1)] = true
            }
            val empty = columns.count { !it }
            if (empty.toFloat() / columns.size.toFloat() < options.minGapRatio) return null
        }
        // Lettering runs along its line; a square or round blob of artwork is not a line.
        if (thickness > length * 0.85f) return null
        return refined
    }

    /**
     * The turn, in degrees clockwise, that pushes the rows of ink into the sharpest peaks. Rows
     * are scored by the sum of their squared counts, which peaks exactly when the strokes of a
     * line all land on the same rows.
     */
    private fun pickAngle(
        xs: IntArray,
        ys: IntArray,
        count: Int,
        rows: IntArray,
        offset: Int,
        centre: Int,
        middle: Int,
        from: Float,
        to: Float,
        step: Float,
    ): Float {
        var best = from
        var bestScore = -1.0
        var angle = from
        while (angle <= to + 0.001f) {
            java.util.Arrays.fill(rows, 0)
            val radians = Math.toRadians(-angle.toDouble())
            val cos = cos(radians)
            val sin = sin(radians)
            for (index in 0 until count) {
                val x = (xs[index] - centre).toFloat()
                val y = (ys[index] - middle).toFloat()
                val row = offset + (x * sin + y * cos).toInt()
                if (row in rows.indices) rows[row]++
            }
            var squares = 0.0
            for (value in rows) squares += value.toDouble() * value.toDouble()
            val score = squares / (count.toDouble() * count.toDouble())
            if (score > bestScore) {
                bestScore = score
                best = angle
            }
            angle += step
        }
        return best
    }
}
