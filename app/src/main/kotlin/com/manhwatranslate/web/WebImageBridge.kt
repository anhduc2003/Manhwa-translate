package com.manhwatranslate.web

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.CookieManager
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** One page image the reader found and asked to translate. */
data class PageImage(
    val index: Int,
    val src: String,
    val width: Int,
    val height: Int,
)

/**
 * Downloads the chapter images the reader page points at, translates them and serves the
 * results back to the WebView through a local host the request interceptor owns.
 *
 * The images are fetched natively instead of read from the page's canvas: a canvas holding a
 * cross-origin image is tainted, so `toDataURL` throws and nothing would ever be translated.
 * Fetching here also keeps the site's cookies and the page's Referer, which the hotlink
 * protection most manhwa sites use depends on.
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
        private const val MAX_CACHED_PAGES = 32
        private const val MAX_IMAGE_PIXELS = 12_000_000
        private const val MAX_PARALLEL = 2
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, ByteArray>()
    private val order = ArrayDeque<String>()
    private val orderLock = Any()
    private val pending = AtomicInteger(0)
    private val completed = AtomicInteger(0)
    private val permit = Semaphore(MAX_PARALLEL)

    @Volatile
    private var pageUrl: String = ""

    /** The page the images belong to; its URL becomes the Referer the sites check. */
    fun setPageUrl(url: String) {
        pageUrl = url
    }

    /** Queues every image for translation; progress arrives through [onProgress]. */
    fun translate(images: List<PageImage>) {
        if (images.isEmpty()) return
        pending.addAndGet(images.size)
        onProgress(completed.get(), pending.get())
        images.forEach(::queue)
    }

    private fun queue(image: PageImage) {
        scope.launch {
            permit.withPermit {
                try {
                    val bytes = download(image.src) ?: throw IOException("Máy chủ ảnh từ chối tải")
                    val budget = minOf(settingsProvider().maxPagePixels, MAX_IMAGE_PIXELS)
                    val page = decode(bytes, budget) ?: throw IOException("Ảnh không đọc được")
                    val result = withContext(Dispatchers.Default) {
                        engine.processPage(page, settingsProvider()) { }
                    }
                    val token = "${image.index}-${System.currentTimeMillis()}.png"
                    val png = encodePng(result.image) ?: throw IOException("Không mã hoá được ảnh")
                    cache[token] = png
                    remember(token)
                    withContext(Dispatchers.Main) { onTranslated(image.index, token) }
                } catch (error: OutOfMemoryError) {
                    onFailed(image.index, "Ảnh quá lớn so với bộ nhớ máy")
                } catch (error: Exception) {
                    onFailed(image.index, error.message ?: "Lỗi dịch ảnh")
                } finally {
                    completed.incrementAndGet()
                    onProgress(completed.get(), pending.get())
                }
            }
        }
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
                cache.remove(order.removeFirst())
            }
        }
    }

    private suspend fun download(url: String): ByteArray? {
        val cookie = withContext(Dispatchers.Main) {
            runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
                if (pageUrl.isNotBlank()) builder.header("Referer", pageUrl)
                cookie?.let { builder.header("Cookie", it) }
                client.newCall(builder.build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            }.getOrNull()
        }
    }

    private suspend fun decode(bytes: ByteArray, maxPixels: Int): ArgbImage? =
        withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth.toLong() * bounds.outHeight.toLong() /
                (sample.toLong() * sample) > maxPixels
            ) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@withContext null
            val scaled = scaleToBudget(bitmap, maxPixels)
            imageStore.fromBitmap(scaled).also { scaled.recycle() }
        }

    private fun scaleToBudget(bitmap: Bitmap, maxPixels: Int): Bitmap {
        val pixels = bitmap.width.toLong() * bitmap.height.toLong()
        if (pixels <= maxPixels) return bitmap
        val factor = kotlin.math.sqrt(maxPixels.toDouble() / pixels.toDouble()).toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).toInt().coerceAtLeast(1),
            (bitmap.height * factor).toInt().coerceAtLeast(1),
            true,
        ).also { bitmap.recycle() }
    }

    private suspend fun encodePng(image: ArgbImage): ByteArray? = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = imageStore.toBitmap(image)
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }.getOrNull()
    }
}
