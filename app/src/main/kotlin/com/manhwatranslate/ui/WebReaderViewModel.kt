package com.manhwatranslate.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.di.AppContainer
import com.manhwatranslate.web.WebImageBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WebReaderUiState(
    val url: String = "",
    val loading: Boolean = false,
    val progress: Int = 0,
    val adBlockEnabled: Boolean = true,
    val translating: Boolean = false,
    val translatedCount: Int = 0,
    val totalImages: Int = 0,
    val message: String? = null,
    val clearSignal: Int = 0,
)

class WebReaderViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(WebReaderUiState())
    val state: StateFlow<WebReaderUiState> = _state.asStateFlow()

    private var settings: AppSettings = AppSettings()

    /** Set by the screen so the bridge can push a translated page back into the WebView. */
    var onApplyImage: ((index: Int, url: String) -> Unit)? = null

    val bridge: WebImageBridge = WebImageBridge(
        scope = viewModelScope,
        imageStore = container.imageStore,
        engine = container.engine,
        settingsProvider = { settings },
        onTranslated = { index, token ->
            val url = "https://${WebImageBridge.LOCAL_HOST}/page/$token"
            _state.value = _state.value.copy(translatedCount = _state.value.translatedCount + 1)
            onApplyImage?.invoke(index, url)
        },
        onFailed = { index, reason ->
            if (index < 0) {
                _state.value = _state.value.copy(message = reason)
            }
        },
        onProgress = { done, total ->
            _state.value = _state.value.copy(
                translatedCount = done,
                totalImages = maxOf(total, _state.value.totalImages),
                translating = total > 0 && done < total,
            )
        },
    )

    init {
        viewModelScope.launch { settings = container.settingsRepository.current() }
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings = it }
        }
    }

    fun onUrlChanged(url: String) {
        _state.value = _state.value.copy(url = url)
    }

    fun onLoadStarted() {
        _state.value = _state.value.copy(loading = true, progress = 0, translatedCount = 0, totalImages = 0)
    }

    fun onProgress(progress: Int) {
        _state.value = _state.value.copy(progress = progress)
    }

    fun onLoadFinished() {
        _state.value = _state.value.copy(loading = false)
    }

    fun onScanStarted(total: Int) {
        _state.value = _state.value.copy(
            translating = total > 0,
            totalImages = total,
            translatedCount = 0,
            message = if (total == 0) "Không tìm thấy ảnh truyện nào trên trang" else null,
        )
    }

    fun toggleAdBlock() {
        val enabled = !_state.value.adBlockEnabled
        _state.value = _state.value.copy(adBlockEnabled = enabled, clearSignal = _state.value.clearSignal + 1)
    }

    fun clearTranslatedPages() {
        bridge.clear()
        _state.value = _state.value.copy(
            translatedCount = 0,
            totalImages = 0,
            translating = false,
            clearSignal = _state.value.clearSignal + 1,
        )
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
