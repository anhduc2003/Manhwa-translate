package com.manhwatranslate.translate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Result of a model download, ready to be pointed at by [LocalLlmTranslationProvider]. */
data class ModelDownload(
    val path: String,
    val name: String,
    val bytes: Long,
)

/** Progress of a running model download, in 0..1. */
data class DownloadProgress(
    val fraction: Float = 0f,
    val bytes: Long = 0L,
    val total: Long = 0L,
)

/**
 * Streams a .gguf file from the network into app storage, reporting progress so the settings
 * screen can show how much of the model has arrived. The file is written to a temp name first and
 * only moved into place once it is complete, so an interrupted download never leaves a half
 * model behind that the loader would try to open.
 */
class ModelDownloader(
    private val context: Context,
    private val client: OkHttpClient,
) {

    private val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Downloads [url] into app storage. [onProgress] is called from a background thread; the
     * caller is responsible for marshalling it to the UI thread if needed.
     */
    suspend fun download(
        url: String,
        onProgress: (DownloadProgress) -> Unit,
    ): ModelDownload = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Máy chủ trả lỗi HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Không có dữ liệu tải về")
            val total = body.contentLength().coerceAtLeast(0L)
            val name = fileNameFrom(url, response.header("Content-Disposition"))
            val temp = File(modelsDir, ".$name.part")
            val final = File(modelsDir, name)
            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(
                            DownloadProgress(
                                fraction = if (total > 0) downloaded.toFloat() / total else 0f,
                                bytes = downloaded,
                                total = total,
                            ),
                        )
                    }
                }
            }
            if (total > 0 && temp.length() < total) {
                temp.delete()
                throw IOException("Tải mô hình không đầy đủ (${temp.length()}/$total byte)")
            }
            if (final.exists()) final.delete()
            if (!temp.renameTo(final)) {
                temp.delete()
                throw IOException("Không lưu được mô hình vào bộ nhớ")
            }
            ModelDownload(final.absolutePath, name, final.length())
        }
    }

    /** A model name that stays on the local filesystem no matter what the server calls the file. */
    private fun fileNameFrom(url: String, disposition: String?): String {
        val fromDisposition = disposition
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("filename=") }
            ?.substringAfter('=')
            ?.trim('"')
        val raw = fromDisposition ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "model.gguf" }
        val safe = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
        return if (safe.endsWith(".gguf")) safe else "$safe.gguf"
    }
}
