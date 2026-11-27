package com.manhwatranslate.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.manhwatranslate.web.WebImageBridge
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * A reader for manhwa sites: a fixed address bar at the top, the page below it, and a settings
 * sheet for the ad blocker, the reader layout and the page translation.
 */
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
    val blocker = viewModel.adBlocker
    var webView by remember { mutableStateOf<WebView?>(null) }
    var addressBar by rememberSaveable { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    LaunchedEffect(state.desktopMode) {
        webView?.let { view ->
            view.settings.userAgentString = if (state.desktopMode) DESKTOP_AGENT else MOBILE_AGENT
            view.reload()
        }
    }

    KeepScreenOn(state.keepAwake)

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    fun open(input: String) {
        val url = normalizeUrl(input) ?: return
        addressBar = url
        focus.clearFocus()
        webView?.loadUrl(url)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Đọc truyện trên web", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = when {
                                state.translating -> "Đang dịch ${state.translatedCount}/${state.totalImages} ảnh"
                                state.totalImages > 0 -> "Đã dịch ${state.translatedCount}/${state.totalImages} ảnh"
                                state.adBlockEnabled -> "Chặn quảng cáo: bật"
                                else -> "Chặn quảng cáo: tắt"
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
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại")
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Cài đặt web")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
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
                        Text(
                            text = if (state.totalImages > 0) {
                                "Chạm vào một khung chữ trên ảnh để sửa bản dịch"
                            } else {
                                "Dán link truyện ở ô phía trên rồi bấm Mở"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                val view = webView ?: return@Button
                                view.evaluateJavascript(
                                    "window.__mtScanImages ? window.__mtScanImages() : -1",
                                ) { result ->
                                    val total = result?.trim('"')?.toIntOrNull() ?: -1
                                    if (total < 0) {
                                        view.evaluateJavascript(blocker.imageBridgeScript, null)
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
            AddressBar(
                value = addressBar,
                onValueChange = { addressBar = it },
                onOpen = { open(addressBar) },
                onPaste = {
                    val clip = context.getSystemService(ClipboardManager::class.java)
                    val pasted = clip?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        ?.trim()
                    if (!pasted.isNullOrBlank()) {
                        addressBar = pasted
                        open(pasted)
                    } else {
                        viewModel.showMessage("Bộ nhớ tạm không có link nào")
                    }
                },
            )
            if (state.loading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
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
                            settings.loadsImagesAutomatically = true
                            settings.blockNetworkImage = false
                            settings.userAgentString = MOBILE_AGENT
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
                                    val current = viewModel.state.value
                                    if (current.adBlockEnabled && blocker.shouldBlock(url.toString())) {
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
                                    injectReaderScripts(view, url.orEmpty(), viewModel, blocker)
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

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }, sheetState = sheetState) {
            WebSettingsSheet(
                state = state,
                onToggleAds = { viewModel.toggleAdBlock() },
                onToggleSource = { viewModel.toggleBlockerSource(it) },
                onToggleReader = { viewModel.setReaderMode(it) },
                onToggleDesktop = { viewModel.setDesktopMode(it) },
                onToggleKeepAwake = { viewModel.setKeepAwake(it) },
                onClearCookies = { viewModel.clearCookies() },
                onClearTranslations = { viewModel.clearTranslated() },
                onOpenExternal = {
                    val url = addressBar.ifBlank { webView?.url.orEmpty() }
                    if (url.isNotBlank()) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                    settingsOpen = false
                },
            )
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

@Composable
private fun AddressBar(
    value: String,
    onValueChange: (String) -> Unit,
    onOpen: () -> Unit,
    onPaste: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Dán link truyện (https://…)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onOpen() }),
                trailingIcon = {
                    IconButton(onClick = onPaste) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "Dán link")
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onOpen, shape = RoundedCornerShape(16.dp)) {
                Text("Mở")
            }
        }
    }
}

@Composable
private fun WebSettingsSheet(
    state: WebReaderUiState,
    onToggleAds: () -> Unit,
    onToggleSource: (String) -> Unit,
    onToggleReader: (Boolean) -> Unit,
    onToggleDesktop: (Boolean) -> Unit,
    onToggleKeepAwake: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onClearTranslations: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text("Cài đặt trình đọc", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        SettingSwitch("Chặn quảng cáo", "Tắt cả bộ lọc và script dọn trang", state.adBlockEnabled) { onToggleAds() }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Bộ lọc quảng cáo", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (state.blockerReady) {
                "Đang dùng ${state.blockerSources.size} bộ lọc, tổng " +
                    "${state.blockerCounts.filterKeys { it in state.blockerSources }.values.sum()} tên miền"
            } else {
                "Đang nạp bộ lọc…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        com.manhwatranslate.web.AdBlocker.SOURCES.forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSource(source.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(source.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${source.hint} · ${state.blockerCounts[source.id] ?: 0} tên miền",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = source.id in state.blockerSources,
                    onCheckedChange = { onToggleSource(source.id) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SettingSwitch("Chế độ đọc", "Ảnh rộng hết khung, ẩn cột và popup thừa", state.readerMode, onToggleReader)
        SettingSwitch("Chế độ máy tính", "Dùng user-agent desktop cho trang chỉ hỗ trợ web", state.desktopMode, onToggleDesktop)
        SettingSwitch("Giữ màn hình sáng", "Không tắt màn hình khi đang đọc", state.keepAwake, onToggleKeepAwake)

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mở bằng trình duyệt ngoài")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClearTranslations, modifier = Modifier.fillMaxWidth()) {
            Text("Xoá bản dịch đã lưu trong phiên này")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClearCookies, modifier = Modifier.fillMaxWidth()) {
            Text("Xoá cookie của trang")
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val activity = context as? Activity
        if (enabled) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (enabled) activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun injectReaderScripts(
    view: WebView,
    url: String,
    viewModel: WebReaderViewModel,
    blocker: com.manhwatranslate.web.AdBlocker,
) {
    val state = viewModel.state.value
    val css = JSONObject.quote(blocker.cssFor(url))
    view.evaluateJavascript(
        "(function(){var s=document.getElementById('mt-guard');" +
            "if(!s){s=document.createElement('style');s.id='mt-guard';document.documentElement.appendChild(s);}" +
            "s.textContent=$css;})()",
        null,
    )
    if (state.adBlockEnabled) view.evaluateJavascript(blocker.pageScript, null)
    if (state.readerMode) {
        view.evaluateJavascript(
            "(function(){var s=document.getElementById('mt-reader');" +
                "if(!s){s=document.createElement('style');s.id='mt-reader';document.documentElement.appendChild(s);}" +
                "s.textContent='img,canvas,video{max-width:100%!important;height:auto!important;}" +
                "body{overflow-x:hidden!important;}' + " +
                "'aside,[class*=\"sidebar\"],[class*=\"related\"],[class*=\"comment\"]{display:none!important;}'" +
                ";})()",
            null,
        )
    }
    view.evaluateJavascript(blocker.imageBridgeScript, null)
}

private const val MOBILE_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

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
