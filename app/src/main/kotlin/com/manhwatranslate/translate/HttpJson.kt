package com.manhwatranslate.translate

import com.manhwatranslate.core.translate.TranslationFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * A translation backend failed. [transient] marks the failures worth retrying: a timeout or a
 * connection reset may pass on the next try, while a bad key, a spent quota or a 404 will fail
 * exactly the same way and should go straight to the backup provider.
 */
class TranslationException(
    message: String,
    cause: Throwable? = null,
    transient: Boolean = false,
) : TranslationFailure(message, cause, transient)

internal object HttpJson {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = withContext(Dispatchers.IO) {
        val raw = postRaw(url, body, headers)
        runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw TranslationException("Phản hồi không phải JSON hợp lệ") }
    }

    /** Raw body, for services whose answer is a bare list. */
    suspend fun postRaw(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMedia))
        headers.forEach { (key, value) -> builder.header(key, value) }
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (error: Exception) {
            throw TranslationException(
                "Không kết nối được tới máy chủ dịch: ${error.message}",
                error,
                transient = true,
            )
        }
        response.use { result ->
            val raw = result.body?.string().orEmpty()
            if (!result.isSuccessful) {
                throw TranslationException(
                    messageFor(result.code, raw),
                    transient = transientFor(result.code),
                )
            }
            raw
        }
    }

    private fun transientFor(code: Int): Boolean = code == 429 || code in 500..599

    private fun messageFor(code: Int, raw: String): String {
        val detail = runCatching {
            val element = json.parseToJsonElement(raw).jsonObject
            element["error"]?.let { error ->
                (error as? JsonObject)?.get("message")?.toString()?.trim('"')
            } ?: element["message"]?.toString()?.trim('"')
        }.getOrNull()
        return when (code) {
            401, 403 -> "API key không hợp lệ hoặc không đủ quyền (HTTP $code)" +
                (if (detail.isNullOrBlank()) "" else ": $detail")
            429 -> "Đã vượt hạn mức gọi API, thử lại sau ít phút (HTTP 429)"
            in 500..599 -> "Máy chủ dịch đang lỗi (HTTP $code)"
            else -> "Lỗi dịch HTTP $code${if (detail.isNullOrBlank()) "" else ": $detail"}"
        }
    }
}
