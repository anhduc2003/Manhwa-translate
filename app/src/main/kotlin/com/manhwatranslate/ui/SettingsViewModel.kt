package com.manhwatranslate.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhwatranslate.core.layout.FontMode
import com.manhwatranslate.core.model.TranslationProviderType
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val configuredKeys: Set<TranslationProviderType> = emptySet(),
    val message: String? = null,
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
                _state.value = _state.value.copy(settings = settings, configuredKeys = readKeys())
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

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun readKeys(): Set<TranslationProviderType> =
        TranslationProviderType.entries.filter { container.secretsStore.hasKey(it) }.toSet()
}
