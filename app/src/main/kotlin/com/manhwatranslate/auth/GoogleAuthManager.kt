package com.manhwatranslate.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.manhwatranslate.data.UserProfile
import com.manhwatranslate.data.UserSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Điều phối đăng nhập Google cho ứng dụng. Giữ một GoogleSignInClient dùng chung, lưu hồ sơ
 * người dùng vào UserSessionStore và phát trạng thái đăng nhập cho Compose.
 */
class GoogleAuthManager(
    private val context: Context,
    private val sessionStore: UserSessionStore,
    private val scope: CoroutineScope,
    private val webClientId: String,
) {

    private val googleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .apply {
                if (webClientId.isNotBlank() && webClientId != "YOUR_GOOGLE_WEB_CLIENT_ID") {
                    requestIdToken(webClientId)
                }
            }
            .requestEmail()
            .build(),
    )

    private val _user = MutableStateFlow(UserProfile())
    val user: StateFlow<UserProfile> = _user.asStateFlow()

    /** Khởi tạo trạng thái từ bộ lưu và dữ liệu đăng nhập đã có. */
    suspend fun load() {
        val stored = sessionStore.current()
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val profile = if (account != null) stored.copy(
            userId = account.id.orEmpty().ifBlank { stored.userId },
            displayName = account.displayName.orEmpty().ifBlank { stored.displayName },
            email = account.email.orEmpty().ifBlank { stored.email },
            photoUrl = account.photoUrl?.toString().orEmpty().ifBlank { stored.photoUrl },
        ) else stored
        _user.value = profile
        if (account != null) sessionStore.save(profile)
    }

    fun signInIntent(): Intent = googleSignInClient.signInIntent

    /** Xử lý kết quả từ ActivityResultLauncher (IntentSenderRequest / sign-in intent). */
    suspend fun handleResult(data: Intent?): Boolean {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val profile = UserProfile(
                userId = account.id.orEmpty(),
                displayName = account.displayName.orEmpty(),
                email = account.email.orEmpty(),
                photoUrl = account.photoUrl?.toString().orEmpty(),
                idToken = account.idToken.orEmpty(),
            )
            _user.value = profile
            sessionStore.save(profile)
            true
        } catch (e: ApiException) {
            _user.value = sessionStore.current()
            false
        } catch (e: Exception) {
            _user.value = sessionStore.current()
            false
        }
    }

    fun signOut(onDone: () -> Unit = {}) {
        googleSignInClient.signOut()
            .addOnCompleteListener {
                scope.launch {
                    sessionStore.clear()
                    _user.value = UserProfile()
                    onDone()
                }
            }
    }

    fun revokeAccess(onDone: () -> Unit = {}) {
        googleSignInClient.revokeAccess()
            .addOnCompleteListener {
                scope.launch {
                    sessionStore.clear()
                    _user.value = UserProfile()
                    onDone()
                }
            }
    }
}
