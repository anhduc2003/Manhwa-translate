package com.manhwatranslate.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun readKeys(): Set<TranslationProviderType> =
        TranslationProviderType.entries.filter { container.secretsStore.hasKey(it) }.toSet()
}
