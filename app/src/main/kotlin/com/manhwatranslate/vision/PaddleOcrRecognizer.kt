package com.manhwatranslate.vision

import android.graphics.Bitmap
import android.util.Base64
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.pipeline.TileRecognizer
import com.manhwatranslate.core.scan.ImageTile
import com.manhwatranslate.core.scan.PaddleOcrParser
import com.manhwatranslate.translate.HttpJson
import com.manhwatranslate.translate.TranslationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

/**
 * Talks to a self-hosted PaddleOCR Hub Serving endpoint (`paddleocr serving` or the Docker
 * image). Each tile is sent as a base64 JPEG; the answer is turned into regions in page space.
 * PaddleOCR keeps its own detector tuned for scene text, which reads lettering on artwork that
 * the on-device model tends to miss.
 */
class PaddleOcrRecognizer(
    private val serverUrl: String,
    private val token: String = "",
) : TileRecognizer {

    override suspend fun recognize(tile: ImageTile, script: SourceScript): List<TextRegion> {
        if (serverUrl.isBlank()) throw TranslationException("Chưa nhập địa chỉ server PaddleOCR")
        if (serverUrl.contains("/api/v2/ocr/jobs", ignoreCase = true)) {
            throw TranslationException(
                "Địa chỉ này là API job của PaddleOCR (tạo job rồi chờ kết quả), " +
                    "app chưa đọc được định dạng đó. Hãy nhập server Hub Serving " +
                    "(.../predict/ocr_system) hoặc chọn ML Kit.",
            )
        }
        val payload = withContext(Dispatchers.Default) { encode(tile.image.toBitmap()) }
        val response = HttpJson.postRaw(
            url = endpoint(),
            body = payload,
            headers = buildMap {
                put("Content-Type", "application/json")
                if (token.isNotBlank()) put("Authorization", "Bearer ${token.trim()}")
            },
        )
        return PaddleOcrParser.parse(response, tile.rect.left, tile.rect.top)
    }

    private fun endpoint(): String {
        val base = serverUrl.trim().trimEnd('/')
        return when {
            base.endsWith("/ocr_system") || base.contains("/predict/") -> base
            base.endsWith("/predict") -> "$base/ocr_system"
            else -> "$base/predict/ocr_system"
        }
    }

    private fun encode(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        bitmap.recycle()
        return buildJsonObject {
            put(
                "images",
                buildJsonArray {
                    add(JsonPrimitive(Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)))
                },
            )
        }.toString()
    }

    companion object {
        /** Sends a blank tile so the user can tell a wrong address from a wrong image. */
        suspend fun probe(serverUrl: String): String {
            val recognizer = PaddleOcrRecognizer(serverUrl)
            val blank = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xFFFFFFFF.toInt())
            }
            val tile = ImageTile(
                index = 0,
                rect = com.manhwatranslate.core.geometry.RectI(0, 0, blank.width, blank.height),
                image = com.manhwatranslate.core.image.ArgbImage(
                    blank.width,
                    blank.height,
                    IntArray(blank.width * blank.height) { 0xFFFFFFFF.toInt() },
                ),
            )
            blank.recycle()
            val regions = recognizer.recognize(tile, SourceScript.AUTO)
            return "Server trả lời OK (${regions.size} dòng chữ trên ảnh thử)"
        }
    }
}
