package com.manhwatranslate.core.erase

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors

enum class EraseMode {
    /** Pick SOLID for flat bubbles, DIFFUSION for textured or gradient backgrounds. */
    AUTO,
    /** Paint the fitted background surface over the letters, then match the seam colours. */
    SOLID,
    /** Grow the surrounding colours into the letters (nearest-neighbour diffusion). */
    DIFFUSION,
    /** Rebuild the hidden background with the trained fill model, when one is available. */
    MODEL,
}

data class EraseOptions(
    val mode: EraseMode = EraseMode.AUTO,
    val solidContrastThreshold: Float = 6f,
    val diffusionPasses: Int = 3,
    val matchBackground: Boolean = true,
    /** After the fill, keeps going over ink the mask missed right next to it. */
    val removeResidualInk: Boolean = true,
    /** Applies the seam colour correction on textured fills too, not just flat ones. */
    val seamCorrectDiffusion: Boolean = true,
)

data class EraseStats(
    val mode: EraseMode,
    val pixelsErased: Int,
    val usedSurface: Boolean = false,
)

/**
 * Removes masked letter pixels by reconstructing the background underneath them.
 */
class TextEraser(
    private val options: EraseOptions = EraseOptions(),
    /** Trained fill; without it [EraseMode.MODEL] and [EraseMode.AUTO] use the surface fit. */
    private val filler: BackgroundFiller? = null,
) {

    fun erase(target: ArgbImage, mask: TextMask): EraseStats {
        val mode = resolveMode(mask)
        var erased = 0
        val surface = mask.surface

        when (mode) {
            EraseMode.SOLID -> {
                erased = fillWithSurface(target, mask, surface)
                if (options.matchBackground && surface != null) applySeamCorrection(target, mask, surface)
                if (options.removeResidualInk) erased += eraseResidualInk(target, mask)
            }
            EraseMode.DIFFUSION -> {
                erased = diffuse(target, mask)
                smoothInsideMask(target, mask, passes = maxOf(1, options.diffusionPasses))
                if (options.matchBackground && options.seamCorrectDiffusion && mask.surface != null) {
                    applySeamCorrection(target, mask, mask.surface)
                }
                if (options.removeResidualInk) erased += eraseResidualInk(target, mask)
            }
            EraseMode.MODEL -> {
                erased = fillWithModel(target, mask)
                if (erased == 0) {
                    erased = fillWithSurface(target, mask, surface)
                    if (options.matchBackground && surface != null) applySeamCorrection(target, mask, surface)
                    if (options.removeResidualInk) erased += eraseResidualInk(target, mask)
                }
            }
            EraseMode.AUTO -> Unit
        }

        return EraseStats(
            mode,
            erased,
            usedSurface = surface != null && (mode == EraseMode.SOLID || mode == EraseMode.MODEL),
        )
    }

    private companion object {
        /** Real surroundings handed to the model around the region, so it can continue gradients. */
        const val MODEL_CONTEXT = 24
    }

    private fun resolveMode(mask: TextMask): EraseMode = when (options.mode) {
        EraseMode.AUTO -> when {
            filler != null -> EraseMode.MODEL
            mask.backgroundContrast <= options.solidContrastThreshold -> EraseMode.SOLID
            else -> EraseMode.DIFFUSION
        }
        else -> options.mode
    }

    /** Paints the fitted surface over the letters; the fallback when the model cannot run. */
    private fun fillWithSurface(target: ArgbImage, mask: TextMask, surface: BackgroundSurface?): Int {
        val pixels = target.pixels
        val width = mask.width
        var erased = 0
        for (index in mask.data.indices) {
            if (mask.data[index].toInt() == 0) continue
            val x = index % width
            val y = index / width
            pixels[(mask.top + y) * target.width + mask.left + x] = surface?.argbAt(x, y) ?: mask.backgroundArgb
            erased++
        }
        return erased
    }

    /**
     * Hands the region and a margin of real surroundings to the model and keeps only the pixels the
     * mask marks, so artwork the mask never covered stays exactly as it was.
     */
    private fun fillWithModel(target: ArgbImage, mask: TextMask): Int {
        val model = filler ?: return 0
        val context = MODEL_CONTEXT
        val rect = RectI(
            mask.left - context,
            mask.top - context,
            mask.right + context,
            mask.bottom + context,
        ).clamped(target.width, target.height) ?: return 0
        val crop = target.crop(rect)
        val local = TextMask(
            left = mask.left - rect.left,
            top = mask.top - rect.top,
            width = mask.width,
            height = mask.height,
            data = mask.data,
            backgroundArgb = mask.backgroundArgb,
            backgroundContrast = mask.backgroundContrast,
            inkArgb = mask.inkArgb,
            surface = mask.surface,
            metrics = mask.metrics,
        )
        val rebuilt = try {
            model.fill(crop, local)
        } catch (error: Throwable) {
            null
        } ?: return 0
        if (rebuilt.width != crop.width || rebuilt.height != crop.height) return 0
        var erased = 0
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (!mask.at(x, y)) continue
                target.set(mask.left + x, mask.top + y, rebuilt.get(local.left + x, local.top + y))
                erased++
            }
        }
        return erased
    }

    /**
     * The fitted surface is smooth, real paper is not. The difference between the two along the
     * rim of the letters is pushed back into the patch with a falloff, so the fill matches the
     * grain and colour drift around it instead of sitting on top as a flat stain.
     */
    private fun applySeamCorrection(target: ArgbImage, mask: TextMask, surface: BackgroundSurface) {
        val width = mask.width
        val height = mask.height
        val pageWidth = target.width
        val pixels = target.pixels
        val size = width * height
        val deltaR = FloatArray(size)
        val deltaG = FloatArray(size)
        val deltaB = FloatArray(size)
        val scratchR = FloatArray(size)
        val scratchG = FloatArray(size)
        val scratchB = FloatArray(size)

        var seeded = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (mask.data[index].toInt() != 0) continue
                if (!nearMask(mask, x, y, width, height)) continue
                val pixel = pixels[(mask.top + y) * pageWidth + mask.left + x]
                deltaR[index] = Colors.red(pixel) - surface.redAt(x, y)
                deltaG[index] = Colors.green(pixel) - surface.greenAt(x, y)
                deltaB[index] = Colors.blue(pixel) - surface.blueAt(x, y)
                seeded = true
            }
        }
        if (!seeded) return

        val decay = 0.92f
        repeat(10) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    if (mask.data[index].toInt() == 0) continue
                    var sumR = deltaR[index]
                    var sumG = deltaG[index]
                    var sumB = deltaB[index]
                    var count = 1
                    if (x > 0) {
                        sumR += deltaR[index - 1]; sumG += deltaG[index - 1]; sumB += deltaB[index - 1]; count++
                    }
                    if (x + 1 < width) {
                        sumR += deltaR[index + 1]; sumG += deltaG[index + 1]; sumB += deltaB[index + 1]; count++
                    }
                    if (y > 0) {
                        sumR += deltaR[index - width]; sumG += deltaG[index - width]; sumB += deltaB[index - width]; count++
                    }
                    if (y + 1 < height) {
                        sumR += deltaR[index + width]; sumG += deltaG[index + width]; sumB += deltaB[index + width]; count++
                    }
                    val divisor = count.toFloat()
                    scratchR[index] = (sumR / divisor) * decay
                    scratchG[index] = (sumG / divisor) * decay
                    scratchB[index] = (sumB / divisor) * decay
                }
            }
            for (index in 0 until size) {
                if (mask.data[index].toInt() == 0) continue
                deltaR[index] = scratchR[index]
                deltaG[index] = scratchG[index]
                deltaB[index] = scratchB[index]
            }
        }

        for (index in 0 until size) {
            if (mask.data[index].toInt() == 0) continue
            val x = index % width
            val y = index / width
            val pageIndex = (mask.top + y) * pageWidth + mask.left + x
            val pixel = pixels[pageIndex]
            val red = (Colors.red(pixel) + deltaR[index]).toInt().coerceIn(0, 255)
            val green = (Colors.green(pixel) + deltaG[index]).toInt().coerceIn(0, 255)
            val blue = (Colors.blue(pixel) + deltaB[index]).toInt().coerceIn(0, 255)
            pixels[pageIndex] = Colors.pack(Colors.alpha(pixel), red, green, blue)
        }
    }

    /**
     * Anti-aliased rims and letters whose box was drawn a little short leave a grey ghost behind.
     * Anything still much closer to the letter colour than to the reconstructed paper, and close
     * to what was just erased, gets the paper colour too; artwork of another colour is untouched.
     */
    private fun eraseResidualInk(target: ArgbImage, mask: TextMask): Int {
        val surface = mask.surface ?: return 0
        val width = mask.width
        val height = mask.height
        val pageWidth = target.width
        val pixels = target.pixels
        val quietPaper = mask.backgroundContrast < 6f
        var painted = 0
        repeat(2) { pass ->
            var changed = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (mask.data[y * width + x].toInt() != 0) continue
                    if (!quietPaper && !nearMask(mask, x, y, width, height)) continue
                    val pageIndex = (mask.top + y) * pageWidth + mask.left + x
                    val pixel = pixels[pageIndex]
                    val paper = surface.argbAt(x, y)
                    val paperDistance = Colors.distance(pixel, paper)
                    if (paperDistance <= 20f) continue
                    if (Colors.distance(pixel, mask.inkArgb) >= paperDistance) continue
                    pixels[pageIndex] = paper
                    changed++
                }
            }
            painted += changed
            if (changed == 0 || pass == 1) return painted
        }
        return painted
    }

    private fun nearMask(mask: TextMask, x: Int, y: Int, width: Int, height: Int): Boolean {
        for (dy in -2..2) {
            val ny = y + dy
            if (ny < 0 || ny >= height) continue
            for (dx in -2..2) {
                val nx = x + dx
                if (nx < 0 || nx >= width) continue
                if (mask.data[ny * width + nx].toInt() != 0) return true
            }
        }
        return false
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
            for (index in mask.data.indices) {
                if (mask.data[index].toInt() == 0) continue
                val x = index % width
                val y = index / width
                pixels[(mask.top + y) * pageWidth + mask.left + x] =
                    mask.surface?.argbAt(x, y) ?: mask.backgroundArgb
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
        if (band.none { it }) return
        val scratch = IntArray(width * height)
        // Also smooth the deep interior on textured fills so a patch does not carry a flat
        // colour in the middle of a shaded or grainy bubble; the extra passes blend it with the
        // surrounding texture instead of leaving a visible stain.
        val core = BooleanArray(width * height) { index ->
            mask.data[index].toInt() != 0 && interior[index].toInt() != 0
        }
        repeat(passes) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    if (!band[index] && !core[index]) continue
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
                if (!band[index] && !core[index]) continue
                pixels[(mask.top + index / width) * pageWidth + mask.left + index % width] = scratch[index]
            }
        }
    }
}
