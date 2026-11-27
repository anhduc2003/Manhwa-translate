package com.manhwatranslate.render

import android.graphics.Bitmap
import android.util.Base64
import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.FloatBuffer

/**
 * Fills a bubble's lettering back in with MI-GAN, the inpainting model Picsart released under
 * the MIT licence (see `assets/inpaint/LICENSE-MI-GAN.txt`). A translation drawn over a flat
 * colour patch reads as a sticker; a translation drawn over the model's reconstruction reads as
 * the page's own artwork, which is what a professionally lettered chapter looks like.
 *
 * The model takes a 256x256 RGB tile plus a hole mask and returns the reconstructed tile. Each
 * patch is small, so a chapter never needs a page-sized buffer.
 */
class MiGanInpainter(private val assets: android.content.res.AssetManager) {

    companion object {
        private const val MODEL = "inpaint/migan-quality-fp16.onnx"
        private const val TILE = 256

        /** Hole is the text box run inwards by this share, so glyph edges are covered. */
        private const val ERODE = 0.06f
    }

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val session: OrtSession? by lazy {
        runCatching {
            assets.open(MODEL).use { stream ->
                val bytes = stream.readBytes()
                environment.createSession(bytes, OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                })
            }
        }.getOrNull()
    }

    val available: Boolean get() = session != null

    /**
     * The bubble with its lettering reconstructed, encoded as a small image the reader can use as
     * the background of the translated text. Returns null when the model is unavailable.
     */
    data class Patch(val url: String, val luminance: Float)

    suspend fun patch(page: ArgbImage, area: RectI): Patch? = withContext(Dispatchers.Default) {
        val model = session ?: return@withContext null
        val cover = area.clamped(page.width, page.height) ?: return@withContext null
        val side = (maxOf(cover.width, cover.height) * 1.5f).toInt().coerceAtLeast(TILE / 2)
        val left = (cover.centerX - side / 2).coerceIn(0, maxOf(0, page.width - side))
        val top = (cover.centerY - side / 2).coerceIn(0, maxOf(0, page.height - side))
        val right = minOf(page.width, left + side)
        val bottom = minOf(page.height, top + side)
        if (right - left < 8 || bottom - top < 8) return@withContext null

        val input = FloatArray(4 * TILE * TILE)
        val inner = cover.shrunk(ERODE)
        for (y in 0 until TILE) {
            val sourceY = top + (y * (bottom - top)) / TILE
            for (x in 0 until TILE) {
                val sourceX = left + (x * (right - left)) / TILE
                val pixel = page.get(sourceX, sourceY)
                val index = y * TILE + x
                input[index] = Colors.red(pixel) / 127.5f - 1f
                input[TILE * TILE + index] = Colors.green(pixel) / 127.5f - 1f
                input[2 * TILE * TILE + index] = Colors.blue(pixel) / 127.5f - 1f
                val hole = sourceX in inner.left until inner.right &&
                    sourceY in inner.top until inner.bottom
                input[3 * TILE * TILE + index] = if (hole) 1f else 0f
            }
        }

        val result = runCatching {
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input),
                longArrayOf(1, 4, TILE.toLong(), TILE.toLong()),
            ).use { tensor ->
                model.run(mapOf(model.inputNames.first() to tensor)).use { output ->
                    val values = (output.get(0) as OnnxTensor).floatBuffer
                    val pixels = IntArray(TILE * TILE)
                    for (index in 0 until TILE * TILE) {
                        val r = ((values.get(index) + 1f) * 127.5f).toInt().coerceIn(0, 255)
                        val g = ((values.get(TILE * TILE + index) + 1f) * 127.5f).toInt().coerceIn(0, 255)
                        val b = ((values.get(2 * TILE * TILE + index) + 1f) * 127.5f).toInt().coerceIn(0, 255)
                        pixels[index] = Colors.opaque(r, g, b)
                    }
                    pixels
                }
            }
        }.getOrNull() ?: return@withContext null

        encodeCover(result, left, top, right, bottom, cover)
    }

    /** Cuts the reconstructed region that sits under the text box and encodes it for the page. */
    private fun encodeCover(
        tile: IntArray,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cover: RectI,
    ): Patch? {
        val width = cover.width
        val height = cover.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        var luminance = 0.0
        val spanX = (right - left).coerceAtLeast(1)
        val spanY = (bottom - top).coerceAtLeast(1)
        for (y in 0 until height) {
            val tileY = ((cover.top + y - top) * TILE) / spanY
            for (x in 0 until width) {
                val tileX = ((cover.left + x - left) * TILE) / spanX
                val index = tileY.coerceIn(0, TILE - 1) * TILE + tileX.coerceIn(0, TILE - 1)
                pixels[y * width + x] = tile[index]
                luminance += Colors.luminance(tile[index])
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, out)
            bitmap.recycle()
            out.toByteArray()
        }
        if (bytes.isEmpty()) return null
        val average = (luminance / (width * height)).toFloat()
        return Patch(
            url = "data:image/webp;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP),
            luminance = average,
        )
    }

    private fun RectI.shrunk(share: Float): RectI {
        val dx = (width * share).toInt()
        val dy = (height * share).toInt()
        val shrunk = RectI(left + dx, top + dy, right - dx, bottom - dy)
        return if (shrunk.width >= 4 && shrunk.height >= 4) shrunk else this
    }

    fun close() {
        runCatching { session?.close() }
    }
}
