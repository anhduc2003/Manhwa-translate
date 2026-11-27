package com.manhwatranslate.core.erase

import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors

enum class EraseMode {
    /** Pick SOLID for flat bubbles, DIFFUSION for textured or gradient backgrounds. */
    AUTO,
    /** Fill letters with the bubble's estimated background colour, then feather the seam. */
    SOLID,
    /** Grow the surrounding colours into the letters (nearest-neighbour diffusion). */
    DIFFUSION,
}

data class EraseOptions(
    val mode: EraseMode = EraseMode.AUTO,
    val solidContrastThreshold: Float = 14f,
    val diffusionPasses: Int = 2,
)

data class EraseStats(
    val mode: EraseMode,
    val pixelsErased: Int,
)

/**
 * Removes masked letter pixels by reconstructing the background underneath them.
 */
class TextEraser(
    private val options: EraseOptions = EraseOptions(),
) {

    fun erase(target: ArgbImage, mask: TextMask): EraseStats {
        val mode = resolveMode(mask)
        val pixels = target.pixels
        val width = mask.width
        val height = mask.height
        var erased = 0

        when (mode) {
            EraseMode.SOLID -> {
                val background = mask.backgroundArgb
                for (index in mask.data.indices) {
                    if (mask.data[index].toInt() == 0) continue
                    pixels[(mask.top + index / width) * target.width + mask.left + index % width] = background
                    erased++
                }
            }
            EraseMode.DIFFUSION -> {
                erased = diffuse(target, mask)
            }
            EraseMode.AUTO -> Unit
        }

        smoothInsideMask(target, mask, passes = maxOf(1, options.diffusionPasses))
        return EraseStats(mode, erased)
    }

    private fun resolveMode(mask: TextMask): EraseMode = when (options.mode) {
        EraseMode.AUTO -> if (mask.backgroundContrast <= options.solidContrastThreshold) {
            EraseMode.SOLID
        } else {
            EraseMode.DIFFUSION
        }
        else -> options.mode
    }

    /** Multi-source BFS that grows the nearest unmasked colour into every masked pixel. */
    private fun diffuse(target: ArgbImage, mask: TextMask): Int {
        val width = mask.width
        val height = mask.height
        val pageWidth = target.width
        val pixels = target.pixels
        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        var head = 0
        var tail = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (mask.data[index].toInt() != 0) continue
                visited[index] = true
                if (hasMaskedNeighbour(mask, x, y, width, height)) {
                    queue[tail++] = index
                }
            }
        }

        var filled = 0
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            val sourceColor = pixels[(mask.top + y) * pageWidth + mask.left + x]
            for (dy in -1..1) {
                val ny = y + dy
                if (ny < 0 || ny >= height) continue
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    if (nx < 0 || nx >= width) continue
                    val nIndex = ny * width + nx
                    if (visited[nIndex]) continue
                    if (mask.data[nIndex].toInt() == 0) {
                        visited[nIndex] = true
                        continue
                    }
                    visited[nIndex] = true
                    pixels[(mask.top + ny) * pageWidth + mask.left + nx] = sourceColor
                    queue[tail++] = nIndex
                    filled++
                }
            }
        }

        if (filled == 0) {
            val background = mask.backgroundArgb
            for (index in mask.data.indices) {
                if (mask.data[index].toInt() == 0) continue
                pixels[(mask.top + index / width) * pageWidth + mask.left + index % width] = background
                filled++
            }
        }
        return filled
    }

    private fun hasMaskedNeighbour(mask: TextMask, x: Int, y: Int, width: Int, height: Int): Boolean {
        for (dy in -1..1) {
            val ny = y + dy
            if (ny < 0 || ny >= height) continue
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                if (nx < 0 || nx >= width) continue
                if (mask.data[ny * width + nx].toInt() != 0) return true
            }
        }
        return false
    }

    /**
     * Averages only the ring of masked pixels next to real background, so the fill fuses with
     * the bubble edge while interior colours keep the surrounding gradients intact.
     */
    private fun smoothInsideMask(target: ArgbImage, mask: TextMask, passes: Int) {
        val width = mask.width
        val height = mask.height
        val pageWidth = target.width
        val pixels = target.pixels
        val interior = mask.data.copyOf()
        Morphology.erode(interior, width, height, 2)
        val band = BooleanArray(width * height) { index ->
            mask.data[index].toInt() != 0 && interior[index].toInt() == 0
        }
        val scratch = IntArray(width * height)
        repeat(passes) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    if (!band[index]) continue
                    var a = 0
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    for (dy in -1..1) {
                        val ny = (y + dy).coerceIn(0, height - 1)
                        for (dx in -1..1) {
                            val nx = (x + dx).coerceIn(0, width - 1)
                            val pixel = pixels[(mask.top + ny) * pageWidth + mask.left + nx]
                            a += Colors.alpha(pixel)
                            r += Colors.red(pixel)
                            g += Colors.green(pixel)
                            b += Colors.blue(pixel)
                            count++
                        }
                    }
                    scratch[index] = Colors.pack(a / count, r / count, g / count, b / count)
                }
            }
            for (index in band.indices) {
                if (!band[index]) continue
                pixels[(mask.top + index / width) * pageWidth + mask.left + index % width] = scratch[index]
            }
        }
    }
}
