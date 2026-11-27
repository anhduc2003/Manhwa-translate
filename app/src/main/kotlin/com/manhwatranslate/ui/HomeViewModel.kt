package com.manhwatranslate.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhwatranslate.core.model.PipelineProgress
import com.manhwatranslate.core.model.PipelineStage
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.data.SavedPage
import com.manhwatranslate.di.AppContainer
import com.manhwatranslate.translate.TranslationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ProcessingState(
    val title: String,
    val pageIndex: Int,
    val pageCount: Int,
    val stage: String,
    val progress: Float,
)

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val pages: List<SavedPage> = emptyList(),
    val processing: ProcessingState? = null,
)

sealed interface HomeEvent {
    data class OpenPage(val pageId: String) : HomeEvent
    data class Message(val text: String) : HomeEvent
}

class HomeViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collectLatest { settings ->
                _state.value = _state.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            container.projectStore.pages.collectLatest { pages ->
                _state.value = _state.value.copy(pages = pages)
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun translate(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (job?.isActive == true) {
            _events.tryEmit(HomeEvent.Message("Đang dịch trang khác, vui lòng đợi"))
            return
        }
        job = viewModelScope.launch {
            val settings = container.settingsRepository.current()
            if (settings.provider.requiresApiKey && container.secretsStore.apiKey(settings.provider).isBlank()) {
                _events.tryEmit(
                    HomeEvent.Message("Chưa nhập API key cho ${settings.provider.label}. Vào Cài đặt để thêm hoặc chọn dịch trên máy."),
                )
                return@launch
            }
            val groupId = UUID.randomUUID().toString()
            var firstPageId: String? = null
            val total = uris.size
            for ((index, uri) in uris.withIndex()) {
                val title = displayName(uri)
                updateProgress(
                    ProcessingState(title, index + 1, total, "Đang đọc ảnh", 0f + index.toFloat() / total),
                )
                try {
                    val source = container.imageStore.import(uri, settings.maxPagePixels)
                    val result = container.engine.processPage(source, settings) { progress ->
                        updateProgress(progress.toProcessing(title, index, total))
                    }
                    val page = container.projectStore.save(
                        source = source,
                        cleaned = result.cleanedImage,
                        regions = result.regions,
                        translations = result.translations,
                        settings = settings,
                        title = title,
                        groupId = groupId,
                        pageIndex = index,
                        pageCount = total,
                        durationMs = result.elapsedMillis,
                        warnings = result.warnings,
                        skippedCount = result.skipped.size,
                    )
                    if (firstPageId == null) firstPageId = page.id
                    val summary = buildSummary(result.regions.size, result.translations, result.warnings, result.skipped.size)
                    if (index == total - 1) _events.tryEmit(HomeEvent.Message(summary))
                } catch (error: OutOfMemoryError) {
                    _events.tryEmit(
                        HomeEvent.Message("Ảnh quá lớn cho bộ nhớ máy. Hãy giảm chất lượng ảnh trong Cài đặt."),
                    )
                    break
                } catch (error: TranslationException) {
                    _events.tryEmit(HomeEvent.Message(error.message ?: "Dịch thất bại"))
                } catch (error: Exception) {
                    _events.tryEmit(HomeEvent.Message("Lỗi xử lý ảnh: ${error.message ?: error.javaClass.simpleName}"))
                }
            }
            _state.value = _state.value.copy(processing = null)
            firstPageId?.let { _events.tryEmit(HomeEvent.OpenPage(it)) }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(processing = null)
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            container.projectStore.delete(pageId)
            _events.tryEmit(HomeEvent.Message("Đã xoá trang"))
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            container.projectStore.deleteGroup(groupId)
            _events.tryEmit(HomeEvent.Message("Đã xoá nhóm trang"))
        }
    }

    private fun updateProgress(processing: ProcessingState) {
        _state.value = _state.value.copy(processing = processing)
    }

    private fun PipelineProgress.toProcessing(title: String, index: Int, total: Int) = ProcessingState(
        title = title,
        pageIndex = index + 1,
        pageCount = total,
        stage = message.ifBlank { stageLabel(stage) },
        progress = (index + fraction) / total.toFloat(),
    )

    private fun stageLabel(stage: PipelineStage): String = when (stage) {
        PipelineStage.PREPARING -> "Chuẩn bị"
        PipelineStage.DETECTING -> "Nhận diện chữ"
        PipelineStage.ERASING -> "Xoá chữ gốc"
        PipelineStage.TRANSLATING -> "Đang dịch"
        PipelineStage.RENDERING -> "Vẽ bản dịch"
        PipelineStage.DONE -> "Hoàn tất"
        PipelineStage.FAILED -> "Lỗi"
    }

    private fun buildSummary(
        regionCount: Int,
        translations: List<com.manhwatranslate.core.model.RegionTranslation>,
        warnings: List<String>,
        skippedCount: Int,
    ): String {
        val failed = translations.count { it.status == com.manhwatranslate.core.model.TranslationStatus.FAILED }
        val suffix = if (skippedCount > 0) " · bỏ qua $skippedCount vùng SFX" else ""
        return when {
            regionCount == 0 -> "Không tìm thấy lời thoại trên ảnh$suffix"
            failed > 0 -> "Đã dịch $regionCount vùng, $failed vùng giữ nguyên do lỗi dịch$suffix"
            warnings.isNotEmpty() && warnings.first().startsWith("Bỏ qua") -> "Đã dịch $regionCount vùng$suffix"
            warnings.isNotEmpty() -> warnings.first()
            else -> "Đã dịch $regionCount vùng chữ$suffix"
        }
    }

    private suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: "Manhwa"
    }
}
