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

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "manhwa_user")

/** Người dùng đã đăng nhập qua Google, chỉ lưu thông tin hiển thị (không phải token). */
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val idToken: String = "",
) {
    val isSignedIn: Boolean get() = userId.isNotBlank()
}

/**
 * Lưu phiên đăng nhập Google trong DataStore thường. Token ID chỉ giữ tạm cho phiên hiện tại để
 * gửi lên server khi cần, còn thông tin hồ sơ (tên, email, ảnh) lưu lâu dài để hiển thị.
 */
class UserSessionStore(private val context: Context) {

    val user: Flow<UserProfile> = context.userDataStore.data.map { prefs ->
        UserProfile(
            userId = prefs[KEY_USER_ID].orEmpty(),
            displayName = prefs[KEY_DISPLAY_NAME].orEmpty(),
            email = prefs[KEY_EMAIL].orEmpty(),
            photoUrl = prefs[KEY_PHOTO_URL].orEmpty(),
            idToken = prefs[KEY_ID_TOKEN].orEmpty(),
        )
    }

    suspend fun current(): UserProfile = user.first()

    suspend fun save(profile: UserProfile) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_USER_ID] = profile.userId
            prefs[KEY_DISPLAY_NAME] = profile.displayName
            prefs[KEY_EMAIL] = profile.email
            prefs[KEY_PHOTO_URL] = profile.photoUrl
            if (profile.idToken.isNotBlank()) prefs[KEY_ID_TOKEN] = profile.idToken
        }
    }

    suspend fun clear() {
        context.userDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_PHOTO_URL = stringPreferencesKey("photo_url")
        val KEY_ID_TOKEN = stringPreferencesKey("id_token")
    }
}
