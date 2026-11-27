package com.manhwatranslate.core.erase

import com.manhwatranslate.core.image.Colors

/**
 * A quadratic colour surface fitted through the bubble around the letters. One plane is not
 * enough for manhwa bubbles, which often carry a soft radial or diagonal gradient; the extra
 * cross and squared terms track those, and the fitted value is what the erased letters get
 * painted with so the patch matches the surrounding colour instead of a flat average.
 */
class BackgroundSurface(
    private val coefficients: FloatArray,
    private val width: Int,
    private val height: Int,
    private val minRed: Float,
    private val maxRed: Float,
    private val minGreen: Float,
    private val maxGreen: Float,
    private val minBlue: Float,
    private val maxBlue: Float,
) {
    fun redAt(x: Int, y: Int): Float = channelAt(x, y, 0).coerceIn(minRed, maxRed)

    fun greenAt(x: Int, y: Int): Float = channelAt(x, y, 1).coerceIn(minGreen, maxGreen)

    fun blueAt(x: Int, y: Int): Float = channelAt(x, y, 2).coerceIn(minBlue, maxBlue)

    fun argbAt(x: Int, y: Int): Int = Colors.opaque(
        redAt(x, y).toInt(),
        greenAt(x, y).toInt(),
        blueAt(x, y).toInt(),
    )

    private fun channelAt(x: Int, y: Int, channel: Int): Float {
        val nx = if (width > 1) (x.toFloat() / (width - 1)) * 2f - 1f else 0f
        val ny = if (height > 1) (y.toFloat() / (height - 1)) * 2f - 1f else 0f
        val offset = channel * BASIS_SIZE
        return coefficients[offset] +
            coefficients[offset + 1] * nx +
            coefficients[offset + 2] * ny +
            coefficients[offset + 3] * nx * nx +
            coefficients[offset + 4] * nx * ny +
            coefficients[offset + 5] * ny * ny
    }

    companion object {
        const val BASIS_SIZE = 6

        private fun basis(nx: Float, ny: Float, out: FloatArray) {
            out[0] = 1f
            out[1] = nx
            out[2] = ny
            out[3] = nx * nx
            out[4] = nx * ny
            out[5] = ny * ny
        }

        /**
         * Fits through pixels where [used] is 0. Returns null when there are too few samples or
         * the system is degenerate, so callers can fall back to a plain average.
         */
        fun fit(
            pixels: IntArray,
            width: Int,
            height: Int,
            used: ByteArray,
            sampleStep: Int = 1,
        ): BackgroundSurface? {
            if (width < 3 || height < 3) return null
            val normal = Array(BASIS_SIZE) { FloatArray(BASIS_SIZE) }
            val rhs = Array(3) { FloatArray(BASIS_SIZE) }
            val basis = FloatArray(BASIS_SIZE)
            var samples = 0
            val step = sampleStep.coerceAtLeast(1)

            for (y in 0 until height step step) {
                val ny = (y.toFloat() / (height - 1)) * 2f - 1f
                for (x in 0 until width step step) {
                    val index = y * width + x
                    if (used[index].toInt() != 0) continue
                    val nx = (x.toFloat() / (width - 1)) * 2f - 1f
                    basis(nx, ny, basis)
                    for (row in 0 until BASIS_SIZE) {
                        val b = basis[row]
                        for (column in 0 until BASIS_SIZE) {
                            normal[row][column] += b * basis[column]
                        }
                    }
                    val pixel = pixels[index]
                    rhs[0][0] += basis[0] * Colors.red(pixel)
                    rhs[0][1] += basis[1] * Colors.red(pixel)
                    rhs[0][2] += basis[2] * Colors.red(pixel)
                    rhs[0][3] += basis[3] * Colors.red(pixel)
                    rhs[0][4] += basis[4] * Colors.red(pixel)
                    rhs[0][5] += basis[5] * Colors.red(pixel)
                    rhs[1][0] += basis[0] * Colors.green(pixel)
                    rhs[1][1] += basis[1] * Colors.green(pixel)
                    rhs[1][2] += basis[2] * Colors.green(pixel)
                    rhs[1][3] += basis[3] * Colors.green(pixel)
                    rhs[1][4] += basis[4] * Colors.green(pixel)
                    rhs[1][5] += basis[5] * Colors.green(pixel)
                    rhs[2][0] += basis[0] * Colors.blue(pixel)
                    rhs[2][1] += basis[1] * Colors.blue(pixel)
                    rhs[2][2] += basis[2] * Colors.blue(pixel)
                    rhs[2][3] += basis[3] * Colors.blue(pixel)
                    rhs[2][4] += basis[4] * Colors.blue(pixel)
                    rhs[2][5] += basis[5] * Colors.blue(pixel)
                    samples++
                }
            }
            if (samples < 24) return null

            for (index in 0 until BASIS_SIZE) normal[index][index] += 1e-3f
            val solution = solve(normal, rhs) ?: return null

            var minRed = Float.MAX_VALUE
            var maxRed = -Float.MAX_VALUE
            var minGreen = Float.MAX_VALUE
            var maxGreen = -Float.MAX_VALUE
            var minBlue = Float.MAX_VALUE
            var maxBlue = -Float.MAX_VALUE
            for (y in 0 until height step step) {
                val ny = (y.toFloat() / (height - 1)) * 2f - 1f
                for (x in 0 until width step step) {
                    val index = y * width + x
                    if (used[index].toInt() != 0) continue
                    val pixel = pixels[index]
                    val red = Colors.red(pixel).toFloat()
                    val green = Colors.green(pixel).toFloat()
                    val blue = Colors.blue(pixel).toFloat()
                    if (red < minRed) minRed = red
                    if (red > maxRed) maxRed = red
                    if (green < minGreen) minGreen = green
                    if (green > maxGreen) maxGreen = green
                    if (blue < minBlue) minBlue = blue
                    if (blue > maxBlue) maxBlue = blue
                }
            }
            return BackgroundSurface(
                coefficients = solution,
                width = width,
                height = height,
                minRed = minRed,
                maxRed = maxRed,
                minGreen = minGreen,
                maxGreen = maxGreen,
                minBlue = minBlue,
                maxBlue = maxBlue,
            )
        }

        /** Solves A·x = B for every right-hand side column using Gaussian elimination. */
        private fun solve(matrix: Array<FloatArray>, right: Array<FloatArray>): FloatArray? {
            val size = BASIS_SIZE
            val a = Array(size) { row -> matrix[row].copyOf() }
            val b = Array(right.size) { channel -> right[channel].copyOf() }

            for (column in 0 until size) {
                var pivot = column
                for (row in column + 1 until size) {
                    if (kotlin.math.abs(a[row][column]) > kotlin.math.abs(a[pivot][column])) pivot = row
                }
                if (kotlin.math.abs(a[pivot][column]) < 1e-6f) return null
                if (pivot != column) {
                    val temp = a[pivot]
                    a[pivot] = a[column]
                    a[column] = temp
                    for (channel in b.indices) {
                        val value = b[channel][pivot]
                        b[channel][pivot] = b[channel][column]
                        b[channel][column] = value
                    }
                }
                val diagonal = a[column][column]
                for (row in column + 1 until size) {
                    val factor = a[row][column] / diagonal
                    if (factor == 0f) continue
                    for (k in column until size) a[row][k] -= factor * a[column][k]
                    for (channel in b.indices) b[channel][row] -= factor * b[channel][column]
                }
            }

            val result = FloatArray(size * 3)
            for (channel in b.indices) {
                for (row in size - 1 downTo 0) {
                    var sum = b[channel][row]
                    for (k in row + 1 until size) sum -= a[row][k] * result[channel * size + k]
                    result[channel * size + row] = sum / a[row][row]
                }
            }
            return result
        }
    }
}
