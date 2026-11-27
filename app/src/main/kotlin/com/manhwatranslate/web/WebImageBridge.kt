package com.manhwatranslate.web

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.model.PipelineProgress
import com.manhwatranslate.core.model.PipelineStage
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
import java.util.concurrent.atomic.AtomicLong

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
    private val onFinished: (summary: String, seconds: Int) -> Unit = { _, _ -> },
) {

    companion object {
        const val LOCAL_HOST = "manhwa-translate.local"
        private const val MAX_CACHED_PAGES = 32
        private const val MAX_IMAGE_PIXELS = 12_000_000

        /**
         * Pages the pipeline works on at once. It holds three page-sized buffers per image, so
         * two at a time is what fits in a phone's heap. Downloads are cheap by comparison and run
         * ahead of it, so an image rarely waits for its bytes once a slot frees up.
         */
        private const val MAX_PARALLEL = 2
        private const val MAX_DOWNLOADS = 4
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
    private val countGate = Semaphore(MAX_PARALLEL)
    private val downloadGate = Semaphore(MAX_DOWNLOADS)

    private val startedAt = AtomicLong(0)
    private val detectNanos = AtomicLong(0)
    private val translateNanos = AtomicLong(0)

    @Volatile
    private var pageUrl: String = ""

    /** The page the images belong to; its URL becomes the Referer the sites check. */
    fun setPageUrl(url: String) {
        pageUrl = url
    }

    /** Whole seconds the current batch has been running, for the reader's status line. */
    fun elapsedSeconds(): Int {
        val start = startedAt.get()
        if (start == 0L) return 0
        return ((System.currentTimeMillis() - start) / 1000L).toInt()
    }

    /**
     * Where a finished batch spent its time. Reading a page costs far more in on-device OCR than
     * in the translation round trips, so the reader shows the split instead of guessing.
     */
    fun summary(): String {
        val device = detectNanos.get() / 1_000_000L
        val translate = translateNanos.get() / 1_000_000L
        val rest = (startedAt.get().takeIf { it > 0 }?.let {
            System.currentTimeMillis() - it
        } ?: 0L) - device - translate
        return "xử lý ảnh ${seconds(device)} · dịch ${seconds(translate)} · " +
            "còn lại ${seconds(rest.coerceAtLeast(0))}"
    }

    private fun seconds(millis: Long): String =
        if (millis < 1_000L) "${millis}ms" else
            String.format(java.util.Locale.US, "%.1fs", millis / 1000f)

    /** Queues every image for translation; progress arrives through [onProgress]. */
    fun translate(images: List<PageImage>) {
        if (images.isEmpty()) return
        startedAt.set(System.currentTimeMillis())
        detectNanos.set(0)
        translateNanos.set(0)
        pending.addAndGet(images.size)
        onProgress(completed.get(), pending.get())
        images.forEach(::queue)
    }

    private fun queue(image: PageImage) {
        scope.launch {
            try {
                val bytes = download(image.src) ?: throw IOException("Máy chủ ảnh từ chối tải")
                val budget = minOf(settingsProvider().maxPagePixels, MAX_IMAGE_PIXELS)
                val plan = plan(bytes, budget) ?: throw IOException("Ảnh không đọc được")
                countGate.acquire()
                try {
                    val page = decode(bytes, plan) ?: throw IOException("Ảnh không đọc được")
                    val clock = StageClock()
                    val result = withContext(Dispatchers.Default) {
                        engine.processPage(page, settingsProvider()) { progress ->
                            clock.onProgress(progress)
                        }
                    }
                    clock.finish()
                    detectNanos.addAndGet(clock.deviceNanos)
                    translateNanos.addAndGet(clock.translateNanos)
                    val token = "${image.index}-${System.currentTimeMillis()}.jpg"
                    val jpeg = encodeJpeg(result.image) ?: throw IOException("Không mã hoá được ảnh")
                    cache[token] = jpeg
                    remember(token)
                    withContext(Dispatchers.Main) { onTranslated(image.index, token) }
                } finally {
                    countGate.release()
                }
            } catch (error: OutOfMemoryError) {
                onFailed(image.index, "Ảnh quá lớn so với bộ nhớ máy")
            } catch (error: Exception) {
                onFailed(image.index, error.message ?: "Lỗi dịch ảnh")
            } finally {
                val done = completed.incrementAndGet()
                onProgress(done, pending.get())
                if (done >= pending.get()) onFinished(summary(), elapsedSeconds())
            }
        }
    }

    fun serve(token: String): ByteArray? = cache[token]

    fun clear() {
        cache.clear()
        synchronized(orderLock) { order.clear() }
        pending.set(0)
        completed.set(0)
        startedAt.set(0)
        detectNanos.set(0)
        translateNanos.set(0)
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
            downloadGate.withPermit {
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
    }

    private suspend fun decode(bytes: ByteArray, plan: Plan): ArgbImage? =
        withContext(Dispatchers.Default) {
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = plan.sample },
            ) ?: return@withContext null
            val scaled = scaleToBudget(bitmap, plan.pixels)
            imageStore.fromBitmap(scaled).also { scaled.recycle() }
        }

    private data class Plan(val sample: Int, val pixels: Int)

    /** Reads the image size without decoding it, so a page can be admitted to the pixel budget. */
    private fun plan(bytes: ByteArray, maxPixels: Int): Plan? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight.toLong() /
            (sample.toLong() * sample) > maxPixels
        ) {
            sample *= 2
        }
        return Plan(sample, (bounds.outWidth / sample) * (bounds.outHeight / sample))
    }

    /** Splits one image's processing time into on-device work and the translation round trips. */
    private class StageClock {
        var deviceNanos = 0L
        var translateNanos = 0L
        private var stage: PipelineStage? = null
        private var since = System.nanoTime()

        fun onProgress(progress: PipelineProgress) {
            if (progress.stage == stage) return
            val now = System.nanoTime()
            val elapsed = now - since
            when (stage) {
                PipelineStage.TRANSLATING -> translateNanos += elapsed
                null -> Unit
                else -> deviceNanos += elapsed
            }
            stage = progress.stage
            since = now
        }

        fun finish() {
            onProgress(PipelineProgress.of(PipelineStage.DONE, 1f, ""))
        }
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

    /**
     * JPEG rather than PNG: a lossless encode of a 6-megapixel page costs a second or more on a
     * phone and holds the bytes for a whole chapter in memory, while text at quality 95 stays
     * crisp. The reader shows the result in a WebView, so nothing needs the lossless form.
     */
    private suspend fun encodeJpeg(image: ArgbImage): ByteArray? = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = imageStore.toBitmap(image)
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }.getOrNull()
    }
}
