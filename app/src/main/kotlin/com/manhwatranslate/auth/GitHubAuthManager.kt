package com.manhwatranslate.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.manhwatranslate.data.UserProfile
import com.manhwatranslate.data.UserSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Đăng nhập GitHub bằng OAuth Device Flow — chạy hoàn toàn trên thiết bị, không cần client secret
 * hay redirect URI. Trình duyệt mở trang xác nhận mã; ứng dụng poll để lấy access token rồi gọi
 * /user để lấy hồ sơ.
 */
class GitHubAuthManager(
    private val context: Context,
    private val sessionStore: UserSessionStore,
    private val scope: CoroutineScope,
    private val clientId: String,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()
    private val _user = MutableStateFlow(UserProfile())
    val user: StateFlow<UserProfile> = _user.asStateFlow()

    /** Có thể đăng nhập hay không (cần client ID hợp lệ). */
    val isConfigured: Boolean get() = clientId.isNotBlank() && clientId != "YOUR_GITHUB_CLIENT_ID"

    suspend fun load() {
        _user.value = sessionStore.current()
    }

    /** Bắt đầu luồng device flow: lấy device code, mở trình duyệt, poll lấy token. */
    fun startSignIn(onStatus: (String) -> Unit = {}) {
        if (!isConfigured) {
            onStatus("Chưa cấu hình Client ID GitHub. Thêm vào strings.xml rồi build lại.")
            return
        }
        scope.launch {
            try {
                onStatus("Đang tạo phiên đăng nhập…")
                val device = requestDeviceCode()
                val verifyUri = "https://github.com/login/device"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(verifyUri))
                runCatching { context.startActivity(browserIntent) }
                onStatus("Mở trình duyệt, nhập mã ${device.user_code}")

                var token = ""
                var attempts = 0
                while (token.isBlank() && attempts < device.expires_in / device.interval + 5) {
                    delay(device.interval * 1000L)
                    token = pollAccessToken(device.device_code)
                    attempts++
                }
                if (token.isBlank()) {
                    onStatus("Đăng nhập hết hạn hoặc bị huỷ.")
                    return@launch
                }
                onStatus("Đang tải hồ sơ…")
                val profile = fetchProfile(token)
                sessionStore.save(profile)
                _user.value = profile
                onStatus("")
            } catch (e: Exception) {
                onStatus("Không đăng nhập được: ${e.message ?: "lỗi mạng"}")
            }
        }
    }

    fun signOut(onDone: () -> Unit = {}) {
        scope.launch {
            sessionStore.clear()
            _user.value = UserProfile()
            onDone()
        }
    }

    private suspend fun requestDeviceCode(): DeviceCodeResponse = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "read:user user:email")
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .post(body)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            json.decodeFromString<DeviceCodeResponse>(text)
        }
    }

    private suspend fun pollAccessToken(deviceCode: String): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .post(body)
            .header("Accept", "application/json")
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<TokenResponse>(text)
                parsed.access_token.orEmpty()
            }
        }.getOrDefault("")
    }

    private suspend fun fetchProfile(token: String): UserProfile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val me = json.decodeFromString<GitHubUser>(text)
            UserProfile(
                userId = me.id.toString(),
                displayName = me.name ?: me.login,
                email = me.email.orEmpty(),
                photoUrl = me.avatar_url,
                username = me.login,
            )
        }
    }
}

@Serializable
private data class DeviceCodeResponse(
    val device_code: String,
    val user_code: String,
    val verification_uri: String = "",
    val expires_in: Int = 900,
    val interval: Int = 5,
)

@Serializable
private data class TokenResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val error: String? = null,
)

@Serializable
private data class GitHubUser(
    val id: Long = 0,
    val login: String = "",
    val name: String? = null,
    val email: String? = null,
    val avatar_url: String = "",
)
