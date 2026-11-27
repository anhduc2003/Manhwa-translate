package com.manhwatranslate.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwatranslate.web.AdBlocker
import com.manhwatranslate.web.WebImageBridge
import org.json.JSONObject
import java.io.ByteArrayInputStream

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebReaderScreen(
    viewModel: WebReaderViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var addressBar by rememberSaveable { mutableStateOf("") }
    var showAddressField by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.onApplyImage = { index, url ->
            webView?.evaluateJavascript("window.__mtApply($index, ${JSONObject.quote(url)})", null)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Đọc truyện trên web", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (state.totalImages > 0) {
                                "Đã dịch ${state.translatedCount}/${state.totalImages} ảnh"
                            } else {
                                "Chặn quảng cáo, cookie và link ẩn"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddressField = !showAddressField }) {
                        Icon(Icons.Outlined.Language, contentDescription = "Địa chỉ")
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại")
                    }
                    IconButton(onClick = { viewModel.toggleAdBlock() }) {
                        Icon(
                            if (state.adBlockEnabled) Icons.Outlined.Block else Icons.Outlined.CheckCircle,
                            contentDescription = "Chặn quảng cáo",
                            tint = if (state.adBlockEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column(Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (state.translating) {
                        LinearProgressIndicator(
                            progress = {
                                if (state.totalImages == 0) 0f
                                else state.translatedCount.toFloat() / state.totalImages.toFloat()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (state.adBlockEnabled) "Chặn quảng cáo: bật" else "Chặn quảng cáo: tắt",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.translating) {
                                Text(
                                    text = "Đang dịch ${state.translatedCount}/${state.totalImages} ảnh",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val view = webView ?: return@Button
                                view.evaluateJavascript(
                                    "window.__mtScanImages ? window.__mtScanImages() : -1",
                                ) { result ->
                                    val total = result?.trim('"')?.toIntOrNull() ?: -1
                                    if (total < 0) {
                                        view.evaluateJavascript(AdBlocker.imageBridgeScript, null)
                                        view.postDelayed({
                                            view.evaluateJavascript(
                                                "window.__mtScanImages ? window.__mtScanImages() : 0",
                                            ) { second ->
                                                viewModel.onScanStarted(second?.trim('"')?.toIntOrNull() ?: 0)
                                            }
                                        }, 250)
                                    } else {
                                        viewModel.onScanStarted(total)
                                    }
                                }
                            },
                            enabled = !state.translating,
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Dịch ảnh trên trang")
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showAddressField) {
                OutlinedTextField(
                    value = addressBar,
                    onValueChange = { addressBar = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = { Text("Dán địa chỉ trang truyện (https://…)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val url = normalizeUrl(addressBar)
                        if (url != null) {
                            addressBar = url
                            focus.clearFocus()
                            webView?.loadUrl(url)
                        }
                    }),
                    trailingIcon = {
                        TextButton(onClick = {
                            val url = normalizeUrl(addressBar)
                            if (url != null) {
                                addressBar = url
                                focus.clearFocus()
                                webView?.loadUrl(url)
                            }
                        }) { Text("Đi") }
                    },
                )
            }
            if (state.loading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.mediaPlaybackRequiresUserGesture = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setBackgroundColor(0xFFFFFFFF.toInt())

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? {
                                    val url = request.url
                                    if (url.host == WebImageBridge.LOCAL_HOST) {
                                        val token = url.lastPathSegment ?: return emptyResponse()
                                        val bytes = viewModel.bridge.serve(token) ?: return emptyResponse()
                                        return WebResourceResponse(
                                            "image/png",
                                            null,
                                            ByteArrayInputStream(bytes),
                                        )
                                    }
                                    if (viewModel.state.value.adBlockEnabled && AdBlocker.shouldBlock(url.toString())) {
                                        return blockedResponse()
                                    }
                                    return null
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    viewModel.onLoadStarted()
                                    addressBar = url.orEmpty()
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    viewModel.onLoadFinished()
                                    injectProtection(view)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val target = request.url.toString()
                                    if (!target.startsWith("http")) return true
                                    return false
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    viewModel.onProgress(newProgress)
                                }

                                override fun onCreateWindow(
                                    view: WebView,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message,
                                ): Boolean = false
                            }

                            addJavascriptInterface(viewModel.bridge, "AndroidImages")
                            webView = this
                        }
                    },
                    update = { view -> webView = view },
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onApplyImage = null
            webView?.let { view ->
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }
            webView = null
        }
    }
}

private fun injectProtection(view: WebView) {
    val css = JSONObject.quote(AdBlocker.pageCss)
    view.evaluateJavascript(
        "(function(){var s=document.getElementById('mt-guard');" +
            "if(!s){s=document.createElement('style');s.id='mt-guard';document.documentElement.appendChild(s);}" +
            "s.textContent=$css;})()",
        null,
    )
    view.evaluateJavascript(AdBlocker.pageScript, null)
    view.evaluateJavascript(AdBlocker.imageBridgeScript, null)
}

private fun blockedResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

private fun emptyResponse(): WebResourceResponse =
    WebResourceResponse("image/png", null, ByteArrayInputStream(ByteArray(0)))

private fun normalizeUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> null
    }
}
