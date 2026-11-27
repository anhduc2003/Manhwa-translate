package com.manhwatranslate.web

import android.util.Base64
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.data.ImageStore
import com.manhwatranslate.pipeline.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bridge the reader page calls into. Images arrive as data URLs from the page itself (so the
 * site's cookies still apply), get translated page by page, and are served back to the WebView
 * from [serve] under a local host the request interceptor owns.
 */
class WebImageBridge(
    private val scope: CoroutineScope,
    private val imageStore: ImageStore,
    private val engine: TranslationEngine,
    private val settingsProvider: () -> AppSettings,
    private val onTranslated: (index: Int, token: String) -> Unit,
    private val onFailed: (index: Int, reason: String) -> Unit,
    private val onProgress: (done: Int, total: Int) -> Unit,
) {

    companion object {
        const val LOCAL_HOST = "manhwa-translate.local"
        private const val MAX_CACHED_PAGES = 24
        private const val MAX_IMAGE_PIXELS = 6_000_000
    }

    private val cache = ConcurrentHashMap<String, ByteArray>()
    private val order = ArrayDeque<String>()
    private val orderLock = Any()
    private val pending = AtomicInteger(0)
    private val completed = AtomicInteger(0)
    private val permit = Semaphore(2)

    @android.webkit.JavascriptInterface
    fun submit(index: Int, dataUrl: String, width: Int, height: Int) {
        val settings = settingsProvider()
        pending.incrementAndGet()
        onProgress(completed.get(), pending.get())
        scope.launch {
            permit.withPermit {
                try {
                    val image = decodeDataUrl(dataUrl, settings.maxPagePixels)
                    if (image == null) {
                        onFailed(index, "Ảnh không đọc được")
                        return@withPermit
                    }
                    val result = engine.processPage(image, settings) { }
                    val token = "$index-${System.currentTimeMillis()}.png"
                    val bytes = encodePng(result.image)
                    if (bytes != null) {
                        cache[token] = bytes
                        remember(token)
                        onTranslated(index, token)
                    } else {
                        onFailed(index, "Không mã hoá được ảnh đã dịch")
                    }
                } catch (error: OutOfMemoryError) {
                    onFailed(index, "Ảnh quá lớn cho bộ nhớ máy")
                } catch (error: Exception) {
                    onFailed(index, error.message ?: "Lỗi dịch ảnh")
                } finally {
                    completed.incrementAndGet()
                    onProgress(completed.get(), pending.get())
                }
            }
        }
    }

    @android.webkit.JavascriptInterface
    fun failed(index: Int, reason: String) {
        onFailed(index, reason)
    }

    @android.webkit.JavascriptInterface
    fun report(message: String) {
        onFailed(-1, message)
    }

    fun serve(token: String): ByteArray? = cache[token]

    fun clear() {
        cache.clear()
        synchronized(orderLock) { order.clear() }
        pending.set(0)
        completed.set(0)
    }

    fun cachedCount(): Int = cache.size

    private fun remember(token: String) {
        synchronized(orderLock) {
            order.addLast(token)
            while (order.size > MAX_CACHED_PAGES) {
                val oldest = order.removeFirstOrNull() ?: break
                cache.remove(oldest)
            }
        }
    }

    private suspend fun decodeDataUrl(dataUrl: String, maxPixels: Int): ArgbImage? =
        withContext(Dispatchers.Default) {
            val comma = dataUrl.indexOf(',')
            if (comma < 0) return@withContext null
            val payload = dataUrl.substring(comma + 1)
            val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return@withContext null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth > 0 && bounds.outHeight > 0 &&
                bounds.outWidth.toLong() * bounds.outHeight.toLong() / (sample.toLong() * sample) > maxPixels
            ) {
                sample *= 2
            }
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = runCatching {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }.getOrNull() ?: return@withContext null
            val pixels = bitmap.width.toLong() * bitmap.height.toLong()
            val scaled = if (pixels > maxPixels) {
                val factor = kotlin.math.sqrt(maxPixels.toDouble() / pixels.toDouble()).toFloat()
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * factor).toInt().coerceAtLeast(1),
                    (bitmap.height * factor).toInt().coerceAtLeast(1),
                    true,
                ).also { if (it != bitmap) bitmap.recycle() }
            } else {
                bitmap
            }
            val image = imageStore.fromBitmap(scaled)
            scaled.recycle()
            image
        }

    private suspend fun encodePng(image: ArgbImage): ByteArray? = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = imageStore.toBitmap(image)
            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            stream.toByteArray()
        }.getOrNull()
    }
}
