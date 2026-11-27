package com.manhwatranslate.core.erase

/** Separable binary morphology on row-major byte masks (1 = set). */
object Morphology {

    fun dilate(mask: ByteArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        horizontalMax(mask, width, height, radius)
        verticalMax(mask, width, height, radius)
    }

    fun erode(mask: ByteArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        horizontalMin(mask, width, height, radius)
        verticalMin(mask, width, height, radius)
    }

    /** Dilation followed by erosion: fills gaps inside letters and closes broken strokes. */
    fun close(mask: ByteArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        dilate(mask, width, height, radius)
        erode(mask, width, height, radius)
    }

    fun countSet(mask: ByteArray): Int {
        var count = 0
        for (value in mask) if (value.toInt() != 0) count++
        return count
    }

    private fun horizontalMax(mask: ByteArray, width: Int, height: Int, radius: Int) {
        val row = ByteArray(width)
        for (y in 0 until height) {
            val offset = y * width
            java.lang.System.arraycopy(mask, offset, row, 0, width)
            for (x in 0 until width) {
                var value = 0
                val from = maxOf(0, x - radius)
                val to = minOf(width - 1, x + radius)
                var i = from
                while (i <= to) {
                    if (row[i].toInt() != 0) {
                        value = 1
                        break
                    }
                    i++
                }
                mask[offset + x] = value.toByte()
            }
        }
    }

    private fun verticalMax(mask: ByteArray, width: Int, height: Int, radius: Int) {
        val column = ByteArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) column[y] = mask[y * width + x]
            for (y in 0 until height) {
                var value = 0
                val from = maxOf(0, y - radius)
                val to = minOf(height - 1, y + radius)
                var i = from
                while (i <= to) {
                    if (column[i].toInt() != 0) {
                        value = 1
                        break
                    }
                    i++
                }
                mask[y * width + x] = value.toByte()
            }
        }
    }

    private fun horizontalMin(mask: ByteArray, width: Int, height: Int, radius: Int) {
        val row = ByteArray(width)
        for (y in 0 until height) {
            val offset = y * width
            java.lang.System.arraycopy(mask, offset, row, 0, width)
            for (x in 0 until width) {
                var value = 1
                val from = maxOf(0, x - radius)
                val to = minOf(width - 1, x + radius)
                var i = from
                while (i <= to) {
                    if (row[i].toInt() == 0) {
                        value = 0
                        break
                    }
                    i++
                }
                mask[offset + x] = value.toByte()
            }
        }
    }

    private fun verticalMin(mask: ByteArray, width: Int, height: Int, radius: Int) {
        val column = ByteArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) column[y] = mask[y * width + x]
            for (y in 0 until height) {
                var value = 1
                val from = maxOf(0, y - radius)
                val to = minOf(height - 1, y + radius)
                var i = from
                while (i <= to) {
                    if (column[i].toInt() == 0) {
                        value = 0
                        break
                    }
                    i++
                }
                mask[y * width + x] = value.toByte()
            }
        }
    }
}
