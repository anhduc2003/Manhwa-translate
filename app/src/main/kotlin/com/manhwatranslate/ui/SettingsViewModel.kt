package com.manhwatranslate.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhwatranslate.core.layout.FontMode
import com.manhwatranslate.core.model.TranslationProviderType
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.di.AppContainer
import com.manhwatranslate.translate.DownloadProgress
import com.manhwatranslate.translate.FreeProviders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val configuredKeys: Set<TranslationProviderType> = emptySet(),
    val message: String? = null,
    val downloadingModel: Boolean = false,
    val downloadProgress: DownloadProgress = DownloadProgress(),
    /** Bytes of the model URL already on disk from an earlier, interrupted attempt. */
    val partialModelBytes: Long = 0L,
)

class SettingsViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _state.value = _state.value.copy(
                    settings = settings,
                    configuredKeys = readKeys(),
                    partialModelBytes = container.modelDownloader.partialBytes(settings.localLlmModelUrl),
                )
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun saveApiKey(provider: TranslationProviderType, key: String) {
        container.secretsStore.setApiKey(provider, key)
        _state.value = _state.value.copy(
            configuredKeys = readKeys(),
            message = if (key.isBlank()) "Đã xoá API key" else "Đã lưu API key",
        )
    }

    fun apiKey(provider: TranslationProviderType): String = container.secretsStore.apiKey(provider)

    /**
     * Sends one short sentence through whatever provider is selected so a bad key, a wrong
     * endpoint or a mismatch between key and provider shows up as the server's own message
     * instead of a failed page.
     */
    fun testApiKey() {
        val settings = _state.value.settings
        viewModelScope.launch {
            _state.value = _state.value.copy(message = "Đang kiểm tra khoá…")
            val outcome = runCatching {
                container.providerFor(settings).translate(
                    texts = listOf("Good morning"),
                    sourceLanguage = com.manhwatranslate.core.model.SourceScript.LATIN,
                    target = com.manhwatranslate.core.model.TranslationLanguage.VIETNAMESE,
                )
            }
            _state.value = _state.value.copy(
                message = outcome.fold(
                    onSuccess = { lines ->
                        "Khoá hoạt động: \"Good morning\" → \"${lines.firstOrNull().orEmpty()}\""
                    },
                    onFailure = { error -> error.message ?: error.javaClass.simpleName },
                ),
            )
        }
    }

    /**
     * Asks every keyless source one short sentence, so a blocked or rate-limited endpoint shows
     * up here instead of as a page that came back in Korean.
     */
    fun testFreeSources() {
        val settings = _state.value.settings
        viewModelScope.launch {
            _state.value = _state.value.copy(message = "Đang kiểm tra nguồn miễn phí…")
            val results = FreeProviders.all(settings.libreTranslateUrl).map { provider ->
                val outcome = runCatching {
                    provider.translate(
                        texts = listOf("Good morning"),
                        sourceLanguage = com.manhwatranslate.core.model.SourceScript.LATIN,
                        target = com.manhwatranslate.core.model.TranslationLanguage.VIETNAMESE,
                    )
                }
                val status = outcome.fold(
                    onSuccess = { lines -> "OK: ${lines.firstOrNull().orEmpty()}" },
                    onFailure = { error -> error.message ?: error.javaClass.simpleName },
                )
                "${provider.displayName} — $status"
            }
            _state.value = _state.value.copy(message = results.joinToString(" · "))
        }
    }

    fun testOcrServer() {
        val settings = _state.value.settings
        viewModelScope.launch {
            _state.value = _state.value.copy(message = "Đang hỏi server PaddleOCR…")
            val outcome = runCatching {
                com.manhwatranslate.vision.PaddleOcrRecognizer.probe(settings.paddleOcrUrl)
            }
            _state.value = _state.value.copy(
                message = outcome.getOrElse { error -> error.message ?: error.javaClass.simpleName },
            )
        }
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            runCatching { container.fontStore.import(uri) }
                .onSuccess { name ->
                    container.measurer.setCustomTypeface(container.fontStore.loadTypeface())
                    container.settingsRepository.update {
                        it.copy(customFontName = name, fontMode = FontMode.CUSTOM)
                    }
                    _state.value = _state.value.copy(message = "Đã tải font: $name")
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        message = error.message ?: "Không đọc được tệp font",
                    )
                }
        }
    }

    fun clearFont() {
        container.fontStore.clear()
        container.measurer.setCustomTypeface(null)
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(customFontName = "", fontMode = FontMode.AUTO)
            }
        }
        _state.value = _state.value.copy(message = "Đã xoá font tuỳ chỉnh")
    }

    /**
     * Copies the chosen .gguf model into app storage and remembers its path, so a page can be
     * translated offline without the user keeping the source file around.
     */
    fun importLlmModel(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val name = resolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "model.gguf"
                val safeName = name.substringAfterLast('/').ifBlank { "model.gguf" }
                val file = java.io.File(getApplication<Application>().filesDir, "models/$safeName")
                file.parentFile?.mkdirs()
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Không mở được tệp mô hình")
                file.absolutePath to safeName
            }.onSuccess { (path, name) ->
                container.settingsRepository.update {
                    it.copy(localLlmModelPath = path, localLlmModelName = name)
                }
                _state.value = _state.value.copy(message = "Đã nạp mô hình: $name")
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    message = error.message ?: "Không đọc được tệp mô hình",
                )
            }
        }
    }

    /**
     * Downloads a .gguf model straight from [SettingsUiState.settings]'s URL and remembers it as
     * the active offline model. Progress is published on the main state flow so the settings
     * screen can show the bar; an error is surfaced as a message instead of crashing a page.
     */
    fun downloadLlmModel() {
        val url = _state.value.settings.localLlmModelUrl.trim()
        if (url.isBlank()) {
            _state.value = _state.value.copy(message = "Nhập URL của mô hình .gguf trước")
            return
        }
        if (_state.value.downloadingModel) return
        container.appScope.launch {
            _state.value = _state.value.copy(downloadingModel = true, downloadProgress = DownloadProgress())
            runCatching {
                container.modelDownloader.download(url) { progress ->
                    _state.value = _state.value.copy(downloadProgress = progress)
                }
            }.onSuccess { model ->
                container.settingsRepository.update {
                    it.copy(
                        localLlmModelPath = model.path,
                        localLlmModelName = model.name,
                    )
                }
                _state.value = _state.value.copy(
                    downloadingModel = false,
                    partialModelBytes = 0L,
                    message = "Đã tải mô hình: ${model.name} (${formatMegabytes(model.bytes)})",
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    downloadingModel = false,
                    partialModelBytes = container.modelDownloader
                        .partialBytes(_state.value.settings.localLlmModelUrl),
                    message = error.message ?: "Tải mô hình thất bại",
                )
            }
        }
    }

    private fun formatMegabytes(bytes: Long): String =
        String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun readKeys(): Set<TranslationProviderType> =
        TranslationProviderType.entries.filter { container.secretsStore.hasKey(it) }.toSet()
}
