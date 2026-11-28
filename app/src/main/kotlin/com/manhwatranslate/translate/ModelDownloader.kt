package com.manhwatranslate.translate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    companion object {
        /**
         * Downloads run on their own client because a model is hundreds of megabytes to a few
         * gigabytes: the shared translation client would hit its 45s read timeout on a slow
         * connection long before the file arrived. This one only bounds the connect/read stalls
         * between bytes, not the whole transfer.
         */
        fun client(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // unlimited: a 4 GB file can take a while
            .build()

        /**
         * Streams [input] into [target] while reporting progress. Kept in the companion so the
         * write-and-progress logic can be verified without an Android device.
         */
        fun streamTo(
            target: File,
            input: java.io.InputStream,
            total: Long,
            onProgress: (DownloadProgress) -> Unit,
        ) {
            target.outputStream().use { output ->
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

        /** A model name that stays on the local filesystem no matter what the server calls it. */
        fun fileNameFrom(url: String, disposition: String?): String {
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

        /**
         * Streams [url] into [targetDir] with [client]. The target directory is injected so the
         * network path can be exercised in a JVM test without an Android Context.
         */
        suspend fun downloadTo(
            client: OkHttpClient,
            targetDir: File,
            url: String,
            onProgress: (DownloadProgress) -> Unit,
        ): ModelDownload = withContext(Dispatchers.IO) {
            targetDir.mkdirs()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Máy chủ trả lỗi HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Không có dữ liệu tải về")
                val total = body.contentLength().coerceAtLeast(0L)
                val name = fileNameFrom(url, response.header("Content-Disposition"))
                val temp = File(targetDir, ".$name.part")
                val final = File(targetDir, name)
                body.byteStream().use { input ->
                    streamTo(temp, input, total, onProgress)
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
    }

    /**
     * Downloads [url] into app storage. [onProgress] is called from a background thread; the
     * caller is responsible for marshalling it to the UI thread if needed.
     */
    suspend fun download(
        url: String,
        onProgress: (DownloadProgress) -> Unit,
    ): ModelDownload = downloadTo(client, modelsDir, url, onProgress)
}
