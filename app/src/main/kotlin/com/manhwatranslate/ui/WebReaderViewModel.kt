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
    val blockerSources: Set<String> = emptySet(),
    val blockerCounts: Map<String, Int> = emptyMap(),
    val blockerReady: Boolean = false,
    val readerMode: Boolean = true,
    val desktopMode: Boolean = false,
    val keepAwake: Boolean = true,
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

    val adBlocker: com.manhwatranslate.web.AdBlocker get() = container.adBlocker

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
        viewModelScope.launch {
            container.settingsRepository.settings.collect { value ->
                settings = value
                container.adBlocker.setEnabledSources(value.webBlockerSources)
                _state.value = _state.value.copy(
                    adBlockEnabled = value.webBlockAds,
                    blockerSources = value.webBlockerSources,
                    readerMode = value.webReaderMode,
                    desktopMode = value.webDesktopMode,
                    keepAwake = value.webKeepAwake,
                )
            }
        }
        viewModelScope.launch {
            container.adBlocker.prepare()
            _state.value = _state.value.copy(
                blockerReady = container.adBlocker.isReady,
                blockerCounts = container.adBlocker.sourceCounts(),
            )
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
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(webBlockAds = enabled) }
        }
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

    fun toggleBlockerSource(id: String) {
        val current = _state.value.blockerSources
        val next = if (id in current) current - id else current + id
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(webBlockerSources = next) }
        }
        container.adBlocker.setEnabledSources(next)
        _state.value = _state.value.copy(blockerSources = next)
    }

    fun setReaderMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(webReaderMode = enabled) } }
        _state.value = _state.value.copy(readerMode = enabled)
    }

    fun setDesktopMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(webDesktopMode = enabled) } }
        _state.value = _state.value.copy(desktopMode = enabled)
    }

    fun setKeepAwake(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(webKeepAwake = enabled) } }
        _state.value = _state.value.copy(keepAwake = enabled)
    }

    fun clearCookies() {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        showMessage("Đã xoá cookie của trang")
    }

    fun clearTranslated() {
        clearTranslatedPages()
        showMessage("Đã xoá bản dịch đã lưu trong phiên này")
    }

    fun showMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
