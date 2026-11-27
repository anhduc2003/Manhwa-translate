package com.manhwatranslate.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwatranslate.ui.components.AppTopBar
import com.manhwatranslate.ui.components.GroupCard
import com.manhwatranslate.ui.components.NavRow
import com.manhwatranslate.ui.components.RowDivider
import com.manhwatranslate.ui.components.SectionTitle
import com.manhwatranslate.ui.components.ToggleRow
import com.manhwatranslate.web.AdBlocker
import com.manhwatranslate.web.PageImage
import com.manhwatranslate.web.ReaderStyle
import com.manhwatranslate.web.WebImageBridge
import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A reader for manhwa sites: an always-visible address bar in the top bar, the page underneath,
 * a settings sheet for the ad blocker and the reader layout, and a button that translates every
 * chapter image in place.
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
    val focusManager = LocalFocusManager.current
    val darkPage = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val blocker = viewModel.adBlocker
    var webView by remember { mutableStateOf<WebView?>(null) }
    var addressBar by rememberSaveable { mutableStateOf("") }
    var pageLoaded by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.onApplyImage = { index, url ->
            webView?.evaluateJavascript(
                "window.__mtApply && window.__mtApply($index, ${JSONObject.quote(url)})",
                null,
            )
        }
        viewModel.onApplyFailed = { index ->
            webView?.evaluateJavascript("window.__mtFail && window.__mtFail($index)", null)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { text ->
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = if (state.details.isEmpty()) null else "Chi tiết",
                withDismissAction = state.details.isEmpty(),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) detailsOpen = true
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(darkPage, state.readerMode) {
        webView?.let { view ->
            injectReaderScripts(view, view.url.orEmpty(), viewModel, blocker, darkPage)
        }
    }

    LaunchedEffect(state.clearSignal) {
        if (state.clearSignal > 0) {
            webView?.evaluateJavascript("window.__mtReset && window.__mtReset()", null)
        }
    }

    var appliedDesktopMode by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.desktopMode) {
        val previous = appliedDesktopMode
        appliedDesktopMode = state.desktopMode
        if (previous != null && previous != state.desktopMode) {
            webView?.let { view ->
                view.settings.userAgentString = userAgentFor(state.desktopMode)
                view.reload()
            }
        }
    }

    KeepScreenOn(state.keepAwake)

    fun open(input: String) {
        val url = normalizeUrl(input)
        if (url == null) {
            viewModel.showMessage("Link không hợp lệ")
            return
        }
        addressBar = url
        pageLoaded = false
        focusManager.clearFocus()
        webView?.loadUrl(url)
    }

    fun pasteAndOpen() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
        if (text.isNullOrBlank()) {
            viewModel.showMessage("Bộ nhớ tạm không có link")
        } else {
            open(text)
        }
    }

    fun translatePage() {
        val view = webView
        if (view == null) {
            viewModel.showMessage("Chưa có trang nào để dịch")
            return
        }
        view.evaluateJavascript("typeof window.__mtScan === 'function'") { present ->
            if (present?.trim('"') == "true") {
                view.evaluateJavascript("window.__mtScan()") { raw ->
                    viewModel.translatePageImages(parseScannedImages(raw), view.url.orEmpty())
                }
            } else {
                injectReaderScripts(view, view.url.orEmpty(), viewModel, blocker, darkPage)
                view.postDelayed({
                    view.evaluateJavascript("window.__mtScan ? window.__mtScan() : '[]'") { raw ->
                        viewModel.translatePageImages(parseScannedImages(raw), view.url.orEmpty())
                    }
                }, 300)
            }
        }
    }

    if (detailsOpen) {
        AlertDialog(
            onDismissRequest = { detailsOpen = false },
            title = { Text("Chi tiết dịch") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.details.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsOpen = false }) { Text("Đóng") }
            },
        )
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                AppTopBar(
                    title = "Đọc trên web",
                    subtitle = subtitle(state),
                    onBack = onBack,
                    actions = {
                        if (state.details.isNotEmpty()) {
                            IconButton(onClick = { detailsOpen = true }) {
                                Icon(Icons.Outlined.Info, contentDescription = "Chi tiết dịch")
                            }
                        }
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại")
                        }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Cài đặt")
                        }
                    },
                )
                AddressBar(
                    value = addressBar,
                    onValueChange = { addressBar = it },
                    onOpen = { open(addressBar) },
                    onPaste = { pasteAndOpen() },
                )
            }
        },
        bottomBar = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                )
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                    AnimatedVisibility(
                        visible = state.translating,
                        enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
                    ) {
                        Column {
                            val progress by animateFloatAsState(
                                targetValue = if (state.totalImages == 0) 0f
                                else state.translatedCount.toFloat() / state.totalImages,
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                label = "translateProgress",
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = hint(state, pageLoaded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { translatePage() },
                            enabled = !state.translating,
                            shape = CircleShape,
                        ) {
                            AnimatedContent(
                                targetState = state.translating,
                                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                                label = "translateButton",
                            ) { running ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (running) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.AutoFixHigh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (running) "Đang dịch ${state.translatedCount}/${state.totalImages}"
                                        else "Dịch ảnh",
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedVisibility(
                visible = state.loading,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(250)),
            ) {
                val loadProgress by animateFloatAsState(
                    targetValue = state.progress / 100f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "loadProgress",
                )
                LinearProgressIndicator(
                    progress = { loadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { view ->
                    WebView(view).apply {
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
                        settings.userAgentString = userAgentFor(viewModel.state.value.desktopMode)
                        setBackgroundColor(pageBackground(darkPage))

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                val url = request.url
                                if (url.host == WebImageBridge.LOCAL_HOST) {
                                    val token = url.lastPathSegment
                                    val body = token?.let { viewModel.bridge.serve(it) }
                                    return if (body == null) {
                                        emptyResponse()
                                    } else {
                                        WebResourceResponse("image/jpeg", null, ByteArrayInputStream(body))
                                    }
                                }
                                val current = viewModel.state.value
                                if (current.adBlockEnabled && blocker.shouldBlock(url.toString())) {
                                    return blockedResponse()
                                }
                                return null
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                pageLoaded = false
                                addressBar = url.orEmpty()
                                viewModel.onLoadStarted()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                pageLoaded = true
                                viewModel.onLoadFinished()
                                viewModel.onPageUrl(url.orEmpty())
                                injectReaderScripts(view, url.orEmpty(), viewModel, blocker, darkPage)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = !request.url.toString().startsWith("http")
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                viewModel.onProgress(newProgress)
                            }
                        }

                        webView = this
                    }
                },
                update = { view ->
                    webView = view
                    view.setBackgroundColor(pageBackground(darkPage))
                },
            )
            AnimatedVisibility(
                visible = !pageLoaded && !state.loading,
                enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 12 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 12 },
            ) {
                EmptyReader(
                    onPaste = { pasteAndOpen() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = sheetState,
        ) {
            WebSettingsSheet(
                state = state,
                onToggleAds = { viewModel.toggleAdBlock() },
                onToggleSource = { viewModel.toggleBlockerSource(it) },
                onToggleReader = { viewModel.setReaderMode(it) },
                onToggleDesktop = { viewModel.setDesktopMode(it) },
                onToggleKeepAwake = { viewModel.setKeepAwake(it) },
                onClearTranslations = { viewModel.clearTranslated() },
                onClearCookies = { viewModel.clearCookies() },
                onOpenExternal = {
                    val target = webView?.url ?: addressBar
                    if (target.isNotBlank()) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                    }
                    settingsOpen = false
                },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onApplyImage = null
            viewModel.onApplyFailed = null
            webView?.let {
                it.loadUrl("about:blank")
                it.destroy()
            }
            webView = null
        }
    }
}

private fun subtitle(state: WebReaderUiState): String = when {
    state.translating -> "Đang dịch ${state.translatedCount}/${state.totalImages} ảnh"
    state.totalImages > 0 && state.elapsedSeconds > 0 ->
        "Đã dịch ${state.translatedCount} ảnh · ${state.elapsedSeconds}s"
    state.totalImages > 0 -> "Đã dịch ${state.translatedCount} ảnh"
    state.adBlockEnabled -> "Chặn quảng cáo: bật"
    else -> "Chặn quảng cáo: tắt"
}

private fun hint(state: WebReaderUiState, pageLoaded: Boolean): String = when {
    state.translating -> "Giữ màn hình mở trong lúc dịch"
    state.totalImages > 0 && state.timing.isNotBlank() ->
        "Đã dịch xong · ${state.timing}"
    state.totalImages > 0 -> "Đã dịch xong · vuốt để đọc"
    pageLoaded -> "Dịch mọi ảnh trong trang"
    else -> "Dán link rồi bấm Mở"
}

private fun parseScannedImages(raw: String?): List<PageImage> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    return runCatching {
        val value = JSONTokener(raw).nextValue()
        val array = when (value) {
            is JSONArray -> value
            is String -> JSONArray(value)
            else -> return emptyList()
        }
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val src = item.optString("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PageImage(
                index = item.optInt("i", index),
                src = src,
                width = item.optInt("w", 0),
                height = item.optInt("h", 0),
            )
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun EmptyReader(onPaste: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text("Chưa mở trang nào", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Dán link chương truyện để đọc và dịch ảnh ngay trên trang.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onPaste, shape = CircleShape) {
                Icon(
                    Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Dán link và mở")
            }
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
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Dán link truyện", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = CircleShape,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onPaste) {
                        Icon(
                            Icons.Outlined.ContentPaste,
                            contentDescription = "Dán link",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onOpen() }),
            )
            Button(
                onClick = onOpen,
                shape = CircleShape,
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Text("Mở", fontWeight = FontWeight.SemiBold)
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
    onClearTranslations: () -> Unit,
    onClearCookies: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Trình đọc", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))

        GroupCard {
            ToggleRow(
                title = "Chặn quảng cáo",
                checked = state.adBlockEnabled,
                onCheckedChange = { onToggleAds() },
            )
            RowDivider()
            ToggleRow(
                title = "Chế độ đọc",
                subtitle = "Ảnh vừa khung, nền và chữ theo ứng dụng",
                checked = state.readerMode,
                onCheckedChange = onToggleReader,
            )
            RowDivider()
            ToggleRow(
                title = "Máy tính",
                subtitle = "Giả lập trình duyệt desktop",
                checked = state.desktopMode,
                onCheckedChange = onToggleDesktop,
            )
            RowDivider()
            ToggleRow(
                title = "Giữ màn hình sáng",
                checked = state.keepAwake,
                onCheckedChange = onToggleKeepAwake,
            )
        }

        SectionTitle("Nguồn bộ lọc", modifier = Modifier.padding(top = 4.dp))
        GroupCard {
            AdBlocker.SOURCES.forEachIndexed { index, source ->
                if (index > 0) RowDivider()
                ToggleRow(
                    title = source.label,
                    subtitle = if (state.blockerReady) {
                        "${state.blockerCounts[source.id] ?: 0} tên miền"
                    } else {
                        source.hint
                    },
                    checked = source.id in state.blockerSources,
                    onCheckedChange = { onToggleSource(source.id) },
                )
            }
        }

        SectionTitle("Dữ liệu", modifier = Modifier.padding(top = 4.dp))
        GroupCard {
            NavRow(
                icon = Icons.Outlined.LayersClear,
                title = "Xoá bản dịch trên trang",
                showChevron = false,
                onClick = onClearTranslations,
            )
            RowDivider()
            NavRow(
                icon = Icons.Outlined.CleaningServices,
                title = "Xoá cookie của trang",
                showChevron = false,
                onClick = onClearCookies,
            )
            RowDivider()
            NavRow(
                icon = Icons.Outlined.OpenInNew,
                title = "Mở bằng trình duyệt ngoài",
                showChevron = false,
                onClick = onOpenExternal,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val activity = context as? Activity
        if (enabled) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun pageBackground(dark: Boolean): Int = if (dark) 0xFF0D0A0D.toInt() else 0xFFFAF6F3.toInt()

private fun injectReaderScripts(
    view: WebView,
    url: String,
    viewModel: WebReaderViewModel,
    blocker: AdBlocker,
    dark: Boolean,
) {
    val state = viewModel.state.value
    view.evaluateJavascript(blocker.imageBridgeScript, null)
    if (state.adBlockEnabled) {
        val css = JSONObject.quote(blocker.cssFor(url))
        view.evaluateJavascript(
            "(function(){var s=document.getElementById('mt-guard');" +
                "if(!s){s=document.createElement('style');s.id='mt-guard';" +
                "document.documentElement.appendChild(s);}s.textContent=$css;})()",
            null,
        )
        view.evaluateJavascript(blocker.pageScript, null)
    }
    if (state.readerMode) {
        val readerCss = JSONObject.quote(ReaderStyle.css(dark))
        view.evaluateJavascript(
            "(function(){var s=document.getElementById('mt-reader');" +
                "if(!s){s=document.createElement('style');s.id='mt-reader';" +
                "document.documentElement.appendChild(s);}" +
                "s.textContent=$readerCss;})()",
            null,
        )
    } else {
        view.evaluateJavascript(
            "(function(){var s=document.getElementById('mt-reader');" +
                "if(s){s.remove();}})()",
            null,
        )
    }
}

private fun userAgentFor(desktop: Boolean): String =
    if (desktop) DESKTOP_AGENT else MOBILE_AGENT

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
    val withScheme = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> return null
    }
    return withScheme.takeIf { runCatching { Uri.parse(it).host }.getOrNull() != null }
}
