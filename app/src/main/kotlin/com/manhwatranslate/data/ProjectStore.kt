package com.manhwatranslate.data

import android.content.Context
import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.TextRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class SavedPage(
    val id: String,
    val groupId: String,
    val pageIndex: Int,
    val pageCount: Int,
    val createdAt: Long,
    val title: String,
    val width: Int,
    val height: Int,
    val regions: List<TextRegion>,
    val translations: List<RegionTranslation>,
    val targetLanguage: String,
    val providerName: String,
    val durationMs: Long,
    val warnings: List<String> = emptyList(),
    val skippedCount: Int = 0,
    val edited: Boolean = false,
) {
    val fileNameSource: String get() = "source.jpg"
    val fileNameCleaned: String get() = "cleaned.png"
    val fileNameThumb: String get() = "thumb.jpg"
}

/**
 * Every processed page lives in its own folder: the source image, the erased plate the text is
 * redrawn on, a thumbnail, and the page record with all region boxes and translations.
 */
class ProjectStore(private val context: Context, private val imageStore: ImageStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val root: File by lazy {
        File(context.filesDir, "pages").apply { mkdirs() }
    }

    private val indexFile: File by lazy { File(root, "index.json") }

    private val _pages = MutableStateFlow(loadIndex())
    val pages: StateFlow<List<SavedPage>> = _pages.asStateFlow()

    suspend fun save(
        source: com.manhwatranslate.core.image.ArgbImage,
        cleaned: com.manhwatranslate.core.image.ArgbImage,
        regions: List<TextRegion>,
        translations: List<RegionTranslation>,
        settings: AppSettings,
        title: String,
        groupId: String,
        pageIndex: Int,
        pageCount: Int,
        durationMs: Long,
        warnings: List<String>,
        skippedCount: Int = 0,
    ): SavedPage = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dir = directoryOf(id)
        dir.mkdirs()

        val page = SavedPage(
            id = id,
            groupId = groupId,
            pageIndex = pageIndex,
            pageCount = pageCount,
            createdAt = System.currentTimeMillis(),
            title = title,
            width = cleaned.width,
            height = cleaned.height,
            regions = regions,
            translations = translations,
            targetLanguage = settings.target.code,
            providerName = settings.provider.label,
            durationMs = durationMs,
            warnings = warnings,
            skippedCount = skippedCount,
        )

        imageStore.encodeJpeg(source, File(dir, page.fileNameSource), quality = 92)
        imageStore.encodePng(cleaned, File(dir, page.fileNameCleaned))
        writeThumbnail(cleaned, File(dir, page.fileNameThumb))
        writeRecord(page)

        val updated = listOf(page) + _pages.value.filterNot { it.id == id }
        persistIndex(updated)
        _pages.value = updated
        pruneOldPages(updated)
        page
    }

    suspend fun updatePage(page: SavedPage, cleaned: com.manhwatranslate.core.image.ArgbImage) =
        withContext(Dispatchers.IO) {
            val dir = directoryOf(page.id)
            imageStore.encodePng(cleaned, File(dir, page.fileNameCleaned))
            writeThumbnail(cleaned, File(dir, page.fileNameThumb))
            writeRecord(page)
            val updated = _pages.value.map { if (it.id == page.id) page else it }
            persistIndex(updated)
            _pages.value = updated
        }

    fun load(id: String): SavedPage? = _pages.value.firstOrNull { it.id == id }

    fun groupOf(groupId: String): List<SavedPage> =
        _pages.value.filter { it.groupId == groupId }.sortedBy { it.pageIndex }

    fun loadImage(page: SavedPage, kind: PageImage): com.manhwatranslate.core.image.ArgbImage {
        val file = when (kind) {
            PageImage.SOURCE -> File(directoryOf(page.id), page.fileNameSource)
            PageImage.CLEANED -> File(directoryOf(page.id), page.fileNameCleaned)
            PageImage.THUMBNAIL -> File(directoryOf(page.id), page.fileNameThumb)
        }
        return imageStore.decodeFile(file)
    }

    fun thumbnailFile(page: SavedPage): File = File(directoryOf(page.id), page.fileNameThumb)

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        directoryOf(id).deleteRecursively()
        val updated = _pages.value.filterNot { it.id == id }
        persistIndex(updated)
        _pages.value = updated
    }

    suspend fun deleteGroup(groupId: String) = withContext(Dispatchers.IO) {
        _pages.value.filter { it.groupId == groupId }.forEach { page ->
            directoryOf(page.id).deleteRecursively()
        }
        val updated = _pages.value.filterNot { it.groupId == groupId }
        persistIndex(updated)
        _pages.value = updated
    }

    private fun directoryOf(id: String): File = File(root, id)

    private fun writeRecord(page: SavedPage) {
        val dir = directoryOf(page.id)
        File(dir, "page.json").writeText(json.encodeToString(SavedPage.serializer(), page))
    }

    private fun writeThumbnail(image: com.manhwatranslate.core.image.ArgbImage, file: File) {
        val maxWidth = 360
        if (image.width <= maxWidth) {
            imageStore.encodeJpeg(image, file, quality = 80)
            return
        }
        val targetHeight = (image.height.toLong() * maxWidth / image.width).toInt().coerceAtLeast(1)
        val scaled = com.manhwatranslate.core.image.ArgbImage.filled(maxWidth, targetHeight, 0xFF000000.toInt())
        for (y in 0 until targetHeight) {
            val sourceY = (y.toLong() * image.height / targetHeight).toInt().coerceIn(0, image.height - 1)
            for (x in 0 until maxWidth) {
                val sourceX = (x.toLong() * image.width / maxWidth).toInt().coerceIn(0, image.width - 1)
                scaled.set(x, y, image.get(sourceX, sourceY))
            }
        }
        imageStore.encodeJpeg(scaled, file, quality = 80)
    }

    private fun loadIndex(): List<SavedPage> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(SavedPage.serializer()),
                indexFile.readText(),
            )
        }.getOrDefault(emptyList())
    }

    private fun persistIndex(pages: List<SavedPage>) {
        runCatching {
            indexFile.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(SavedPage.serializer()),
                    pages,
                ),
            )
        }
    }

    private fun pruneOldPages(pages: List<SavedPage>) {
        val limit = 120
        if (pages.size <= limit) return
        val removed = pages.drop(limit)
        removed.forEach { directoryOf(it.id).deleteRecursively() }
        val kept = pages.take(limit)
        persistIndex(kept)
        _pages.value = kept
    }
}

enum class PageImage { SOURCE, CLEANED, THUMBNAIL }
