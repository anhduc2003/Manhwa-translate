package com.manhwatranslate.translate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the model download is doing, shared by every screen that shows it. */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState

    data class Running(val url: String, val progress: DownloadProgress) : ModelDownloadState

    data class Finished(val model: ModelDownload) : ModelDownloadState

    data class Failed(val url: String, val message: String) : ModelDownloadState
}

/**
 * Owns the model download for the whole app rather than for one screen.
 *
 * The transfer outlives the settings screen, so the state has to as well: a download started
 * there keeps running after the user walks away, and coming back has to find the real progress
 * instead of an untouched partial file and an invented bar. The finished model is written into
 * the settings from here, so a download that ends while no screen is watching is still saved.
 */
class ModelDownloadManager(
    private val scope: CoroutineScope,
    private val download: suspend (url: String, onProgress: (DownloadProgress) -> Unit) -> ModelDownload,
    private val persist: suspend (ModelDownload) -> Unit,
    private val discard: (url: String) -> Unit = {},
) {

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    /**
     * Starts [url] unless a transfer is already in flight. A link that is not a .gguf model is
     * refused before any bytes are spent, and shows up as [ModelDownloadState.Failed].
     */
    fun start(url: String) {
        val clean = url.trim()
        val problem = ModelDownloader.urlProblem(clean)
        if (problem != null) {
            // Whatever was downloaded for this link is not a model either, so it should not sit
            // on the phone taking up space.
            discard(clean)
            _state.value = ModelDownloadState.Failed(clean, problem)
            return
        }
        if (running) return
        _state.value = ModelDownloadState.Running(clean, DownloadProgress())
        job = scope.launch {
            runCatching {
                download(clean) { progress ->
                    _state.value = ModelDownloadState.Running(clean, progress)
                }
            }.onSuccess { model ->
                runCatching { persist(model) }
                _state.value = ModelDownloadState.Finished(model)
            }.onFailure { error ->
                _state.value = ModelDownloadState.Failed(clean, error.message ?: "Tải mô hình thất bại")
            }
        }
    }

    /** Drops a finished or failed result once its message has been shown. */
    fun consume() {
        if (_state.value !is ModelDownloadState.Running) _state.value = ModelDownloadState.Idle
    }
}
