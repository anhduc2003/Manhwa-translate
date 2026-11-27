package com.manhwatranslate.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.manhwatranslate.core.model.TranslationProviderType

/**
 * API keys live in an EncryptedSharedPreferences file backed by the Android Keystore, kept
 * apart from the plain settings so they never end up in a backup or a JSON export.
 */
class SecretsStore(context: Context) {

    private val preferences: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as SharedPreferences
    }.getOrElse {
        context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    fun apiKey(provider: TranslationProviderType): String =
        preferences.getString(keyOf(provider), "").orEmpty()

    fun setApiKey(provider: TranslationProviderType, value: String) {
        preferences.edit().putString(keyOf(provider), value.trim()).apply()
    }

    fun hasKey(provider: TranslationProviderType): Boolean = apiKey(provider).isNotBlank()

    private fun keyOf(provider: TranslationProviderType): String = "key_${provider.name.lowercase()}"

    private companion object {
        const val FILE_NAME = "manhwa_secrets"
        const val FALLBACK_FILE = "manhwa_secrets_fallback"
    }
}
