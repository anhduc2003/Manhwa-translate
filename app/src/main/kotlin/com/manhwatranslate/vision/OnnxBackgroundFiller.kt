package com.manhwatranslate.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.manhwatranslate.core.erase.BackgroundFiller
import com.manhwatranslate.core.erase.TextMask
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * The trained background fill, run through ONNX Runtime on the phone.
 *
 * `FillNet` takes the region with the lettering zeroed plus the mask, and returns the rebuilt pixels.
 * The graph is fully convolutional, so the crop goes in at whatever size the region is; it pools
 * twice, which is why the crop is padded up to a multiple of four first.
 */
class OnnxBackgroundFiller(context: Context) : BackgroundFiller, Closeable {

    private val assets = context.applicationContext.assets

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val sessionLazy = lazy {
        val model = assets.open(MODEL_ASSET).use { it.readBytes() }
        environment.createSession(model, OrtSession.SessionOptions())
    }

    private val session: OrtSession get() = sessionLazy.value

    private val inputName: String by lazy { session.inputNames.first() }

    override fun fill(crop: ArgbImage, mask: TextMask): ArgbImage? {
        val width = crop.width
        val height = crop.height
        val paddedWidth = width + (-width).mod(PADDING)
        val paddedHeight = height + (-height).mod(PADDING)
        val plane = paddedWidth * paddedHeight
        val input = FloatArray(INPUT_CHANNELS * plane)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val marked = x >= mask.left && y >= mask.top && x < mask.right && y < mask.bottom &&
                    mask.at(x - mask.left, y - mask.top)
                val pixel = crop.pixels[y * width + x]
                val index = y * paddedWidth + x
                if (!marked) {
                    input[index] = ((pixel shr 16) and 0xFF) / 255f
                    input[plane + index] = ((pixel shr 8) and 0xFF) / 255f
                    input[2 * plane + index] = (pixel and 0xFF) / 255f
                }
                input[3 * plane + index] = if (marked) 1f else 0f
            }
        }
        // Three constant planes carry the paper colour, so the network never has to guess which
        // colour the hole should be; they match what the trainer fed it.
        val paper = mask.backgroundArgb
        java.util.Arrays.fill(input, 4 * plane, 5 * plane, ((paper shr 16) and 0xFF) / 255f)
        java.util.Arrays.fill(input, 5 * plane, 6 * plane, ((paper shr 8) and 0xFF) / 255f)
        java.util.Arrays.fill(input, 6 * plane, 7 * plane, (paper and 0xFF) / 255f)

        val shape = longArrayOf(1, INPUT_CHANNELS.toLong(), paddedHeight.toLong(), paddedWidth.toLong())
        val output = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val values = (result[0] as? OnnxTensor) ?: return null
                val floats = FloatArray(3 * plane)
                values.floatBuffer.get(floats)
                floats
            }
        }
        val rebuilt = ArgbImage(crop.width, crop.height, crop.pixels.copyOf())
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (!mask.at(x, y)) continue
                val index = (mask.top + y) * paddedWidth + mask.left + x
                rebuilt.set(
                    mask.left + x,
                    mask.top + y,
                    Colors.opaque(
                        channel(output[index]),
                        channel(output[plane + index]),
                        channel(output[2 * plane + index]),
                    ),
                )
            }
        }
        return rebuilt
    }

    private fun channel(value: Float): Int = (value * 255f).toInt().coerceIn(0, 255)

    /** Releases the native session; the shared [OrtEnvironment] stays alive for the process. */
    override fun close() {
        if (sessionLazy.isInitialized()) runCatching { sessionLazy.value.close() }
    }

    companion object {
        /** Shipped in `app/src/main/assets`; exported by `tools/ink/export_onnx.py`. */
        const val MODEL_ASSET = "fillnet.onnx"

        /** The graph pools twice, so both sides have to divide by four. */
        const val PADDING = 4

        /** RGB with the hole zeroed, the mask, then the paper colour on three planes. */
        const val INPUT_CHANNELS = 7
    }
}
