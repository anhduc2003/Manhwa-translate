package com.manhwatranslate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "manhwa_settings")

class SettingsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        preferences[SETTINGS_KEY]?.let { raw ->
            runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrNull()
        } ?: AppSettings()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[SETTINGS_KEY]?.let { raw ->
                runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrNull()
            } ?: AppSettings()
            preferences[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    suspend fun current(): AppSettings = settings.first()

    private companion object {
        val SETTINGS_KEY = stringPreferencesKey("app_settings_v1")
    }
}
