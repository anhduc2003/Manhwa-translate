package com.manhwatranslate.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.scan.HeatmapRegions
import com.manhwatranslate.core.scan.TextDetector
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * The trained text detector, run through ONNX Runtime on the phone.
 *
 * The model is a 117k-parameter U-Net that marks lettering on a 384x384 tile seen at half size; it
 * costs about 9 ms a tile on a desktop CPU. Everything around the model — tiling, packing a tile,
 * turning probabilities into boxes — lives in [HeatmapRegions] so it is covered by JVM tests, and
 * this class only owns the runtime and the model file.
 */
class OnnxTextDetector(
    context: Context,
    private val threshold: Float = DEFAULT_THRESHOLD,
) : TextDetector, Closeable {

    private val assets = context.applicationContext.assets

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val sessionLazy = lazy {
        val model = assets.open(MODEL_ASSET).use { it.readBytes() }
        environment.createSession(model, OrtSession.SessionOptions())
    }

    private val session: OrtSession get() = sessionLazy.value

    private val inputName: String by lazy { session.inputNames.first() }

    private val input = FloatArray(3 * HeatmapRegions.INPUT * HeatmapRegions.INPUT)

    override fun detect(image: ArgbImage): List<RectI> {
        val tiles = HeatmapRegions.tiles(image.width, image.height)
        if (tiles.isEmpty()) return emptyList()
        val proposals = ArrayList<RectI>(tiles.size)
        for (tile in tiles) {
            HeatmapRegions.writeInput(image, tile, input)
            val probability = run() ?: continue
            for (box in HeatmapRegions.proposals(probability, threshold)) {
                proposals += box.translated(tile.left, tile.top)
            }
        }
        return HeatmapRegions.mergeOverlapping(proposals)
    }

    private fun run(): FloatArray? {
        val shape = longArrayOf(1, 3, HeatmapRegions.INPUT.toLong(), HeatmapRegions.INPUT.toLong())
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as? OnnxTensor ?: return null
                val logits = FloatArray(HeatmapRegions.INPUT * HeatmapRegions.INPUT)
                output.floatBuffer.get(logits)
                return HeatmapRegions.probabilities(logits)
            }
        }
    }

    /** Releases the native session; the shared [OrtEnvironment] stays alive for the process. */
    override fun close() {
        if (sessionLazy.isInitialized()) runCatching { sessionLazy.value.close() }
    }

    companion object {
        /** Shipped in `app/src/main/assets`; exported by `tools/train/export_onnx.py`. */
        const val MODEL_ASSET = "textnet.onnx"

        /**
         * Above this the model is confident enough to be worth a second OCR pass. Measured on 10
         * held-out pages: 0.5 finds 87% of text lines with 42% precision, 0.75 finds 82% with 56%.
         */
        const val DEFAULT_THRESHOLD = 0.75f
    }
}
