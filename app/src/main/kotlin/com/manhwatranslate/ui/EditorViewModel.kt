package com.manhwatranslate.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.model.TranslationStatus
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.data.PageImage
import com.manhwatranslate.data.SavedPage
import com.manhwatranslate.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val loading: Boolean = true,
    val page: SavedPage? = null,
    val groupPages: List<SavedPage> = emptyList(),
    val image: ImageBitmap? = null,
    val imageVersion: Int = 0,
    val showOriginal: Boolean = false,
    val selectedRegionId: Int? = null,
    val editingText: String = "",
    val busy: Boolean = false,
    val busyLabel: String = "",
    val message: String? = null,
)

sealed interface EditorEvent {
    data class Share(val intent: Intent) : EditorEvent
    data class Message(val text: String) : EditorEvent
    data object Closed : EditorEvent
}

class EditorViewModel(
    application: Application,
    private val container: AppContainer,
    private val pageId: String,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    private var settings: AppSettings = AppSettings()
    private var cleaned: ArgbImage? = null
    private var source: ArgbImage? = null
    private var composed: ArgbImage? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            settings = container.settingsRepository.current()
            val page = withContext(Dispatchers.IO) { container.projectStore.load(pageId) }
            if (page == null) {
                _state.value = _state.value.copy(loading = false, message = "Không tìm thấy trang")
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) {
                runCatching { container.projectStore.loadImage(page, PageImage.CLEANED) }
            }
            loaded.onSuccess { cleanedImage ->
                cleaned = cleanedImage
                val result = composeResult(page, cleanedImage)
                composed = result
                _state.value = _state.value.copy(
                    loading = false,
                    page = page,
                    groupPages = container.projectStore.groupOf(page.groupId),
                    image = result.toImageBitmap(),
                    imageVersion = _state.value.imageVersion + 1,
                    showOriginal = false,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    message = "Không mở được ảnh: ${error.message}",
                )
            }
        }
    }

    private fun composeResult(page: SavedPage, cleanedImage: ArgbImage): ArgbImage =
        container.engine.recompose(
            cleaned = cleanedImage,
            regions = page.regions,
            translations = page.translations,
            settings = settings,
        )

    private suspend fun ArgbImage.toImageBitmap(): ImageBitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.asImageBitmap()
    }

    fun toggleOriginal() {
        val current = _state.value
        val page = current.page ?: return
        val next = !current.showOriginal
        _state.value = current.copy(
            showOriginal = next,
            imageVersion = current.imageVersion + 1,
        )
        viewModelScope.launch {
            val bitmap = if (next) sourceImage(page)?.toImageBitmap() else composed?.toImageBitmap()
            _state.value = _state.value.copy(image = bitmap)
        }
    }

    private suspend fun sourceImage(page: SavedPage): ArgbImage? {
        source?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { container.projectStore.loadImage(page, PageImage.SOURCE) }.getOrNull()
        }?.also { source = it }
    }

    fun selectRegion(regionId: Int?) {
        val page = _state.value.page ?: return
        val translation = page.translations.firstOrNull { it.regionId == regionId }
        _state.value = _state.value.copy(
            selectedRegionId = regionId,
            editingText = translation?.translatedText.orEmpty(),
        )
    }

    fun updateEditingText(text: String) {
        _state.value = _state.value.copy(editingText = text)
    }

    fun applyEdit() {
        val page = _state.value.page ?: return
        val regionId = _state.value.selectedRegionId ?: return
        val text = _state.value.editingText
        val updated = page.translations.map { translation ->
            if (translation.regionId == regionId) {
                translation.copy(translatedText = text, status = TranslationStatus.EDITED)
            } else {
                translation
            }
        }
        val updatedPage = page.copy(translations = updated, edited = true)
        renderAndPersist(updatedPage, label = "Đang vẽ lại")
    }

    fun retranslateSelected() {
        val page = _state.value.page ?: return
        val regionId = _state.value.selectedRegionId ?: return
        val region = page.regions.firstOrNull { it.id == regionId } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = "Đang dịch lại")
            try {
                val provider = container.providerFor(settings)
                val result = provider.translate(
                    texts = listOf(region.text),
                    sourceLanguage = if (region.script != com.manhwatranslate.core.model.SourceScript.AUTO) {
                        region.script
                    } else {
                        settings.sourceScript
                    },
                    target = settings.target,
                ).firstOrNull().orEmpty()
                if (result.isBlank()) {
                    _state.value = _state.value.copy(busy = false, message = "Không nhận được bản dịch mới")
                    return@launch
                }
                val updated = page.translations.map { translation ->
                    if (translation.regionId == regionId) {
                        translation.copy(translatedText = result, status = TranslationStatus.EDITED)
                    } else {
                        translation
                    }
                }
                _state.value = _state.value.copy(editingText = result)
                renderAndPersist(page.copy(translations = updated, edited = true), label = "Đang vẽ lại")
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = error.message ?: "Dịch lại thất bại",
                )
            }
        }
    }

    private fun renderAndPersist(page: SavedPage, label: String) {
        val cleanedImage = cleaned ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = label)
            val composedImage = withContext(Dispatchers.Default) {
                composeResult(page, cleanedImage)
            }
            composed = composedImage
            withContext(Dispatchers.IO) {
                container.projectStore.updatePage(page, composedImage)
            }
            val display = if (_state.value.showOriginal) {
                sourceImage(page)?.toImageBitmap()
            } else {
                composedImage.toImageBitmap()
            }
            _state.value = _state.value.copy(
                busy = false,
                page = page,
                image = display,
                imageVersion = _state.value.imageVersion + 1,
                message = null,
            )
        }
    }

    fun saveToGallery() {
        val page = _state.value.page ?: return
        val image = composed ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = "Đang lưu ảnh")
            try {
                val name = "manhwa-${page.pageIndex + 1}-${System.currentTimeMillis()}.png"
                container.imageStore.exportToGallery(image, name)
                _state.value = _state.value.copy(busy = false, message = "Đã lưu vào thư viện ảnh")
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "Không lưu được ảnh: ${error.message}",
                )
            }
        }
    }

    fun share() {
        val page = _state.value.page ?: return
        val image = composed ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = "Đang chuẩn bị chia sẻ")
            try {
                val uri = container.imageStore.exportToCache(image, "manhwa-${page.pageIndex + 1}.png")
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _state.value = _state.value.copy(busy = false)
                _events.tryEmit(EditorEvent.Share(Intent.createChooser(intent, "Chia sẻ trang đã dịch")))
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "Không chia sẻ được: ${error.message}",
                )
            }
        }
    }

    fun deletePage() {
        viewModelScope.launch {
            container.projectStore.delete(pageId)
            _events.tryEmit(EditorEvent.Closed)
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
