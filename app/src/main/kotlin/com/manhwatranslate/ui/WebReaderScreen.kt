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
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
import com.manhwatranslate.ui.components.IconTile
import com.manhwatranslate.web.AdBlocker
import com.manhwatranslate.web.PageImage
import com.manhwatranslate.web.ReaderStyle
import com.manhwatranslate.web.WebImageBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.SwitchDefaults
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp

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
    var pageLoadTick by remember { mutableIntStateOf(0) }
    // A manual clear stops the automatic pass for this page; the next load or press starts it again.
    var autoPaused by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    // The reading page stays in view: the sheet opens at its half height and expands on a drag,
    // instead of covering the whole chapter the moment it is opened.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
        autoPaused = false
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

    /**
     * Automatic translation. A chapter page loads its strips lazily, so one scan finds a handful
     * of them and the rest arrive as the reader scrolls; the loop keeps asking for new images and
     * queues whatever appeared, which is what turns the page over strip by strip instead of
     * leaving most of the chapter in the original language.
     */
    LaunchedEffect(state.clearSignal) {
        if (state.clearSignal > 0) autoPaused = true
    }
    LaunchedEffect(pageLoadTick) {
        if (pageLoadTick > 0) autoPaused = false
    }
    LaunchedEffect(pageLoadTick, state.autoTranslate, autoPaused) {
        if (pageLoadTick == 0 || autoPaused || !state.autoTranslate) return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        // Let the page settle: the first strips and the site's own scripts land just after
        // onPageFinished, and scanning before that finds the wrong images.
        delay(AUTO_START_PAUSE_MS)
        injectReaderScripts(view, view.url.orEmpty(), viewModel, blocker, darkPage)
        var quietRounds = 0
        while (isActive) {
            val found = scanImages(view)
            if (found.isEmpty()) {
                quietRounds++
            } else {
                quietRounds = 0
                viewModel.translatePageImages(found, view.url.orEmpty())
            }
            // A settled page is checked less often, but never stops: the next strip the reader
            // scrolls to still needs translating.
            delay(if (quietRounds > QUIET_SCANS_BEFORE_SLOWING) SLOW_RESCAN_MS else RESCAN_MS)
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
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(
                                    com.manhwatranslate.ui.theme.LocalAccents.current.glowStart,
                                    com.manhwatranslate.ui.theme.LocalAccents.current.glowEnd,
                                ),
                            ),
                        ),
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                            ),
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
                                pageLoadTick += 1
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
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { SheetHandle() },
        ) {
            WebSettingsSheet(
                state = state,
                onClose = { settingsOpen = false },
                onToggleAds = { viewModel.toggleAdBlock() },
                onToggleSource = { viewModel.toggleBlockerSource(it) },
                onToggleAuto = { viewModel.setAutoTranslate(it) },
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
    state.totalImages > 0 && state.autoTranslate ->
        "Đã dịch ${state.totalImages} ảnh · tự dò thêm khi cuộn"
    state.totalImages > 0 && state.timing.isNotBlank() ->
        "Đã dịch xong · ${state.timing}"
    state.totalImages > 0 -> "Đã dịch xong · vuốt để đọc"
    pageLoaded && state.autoTranslate -> "Sắp tự dịch ảnh trong trang"
    pageLoaded -> "Dịch mọi ảnh trong trang"
    else -> "Dán link rồi bấm Mở"
}

/**
 * How long the reader waits after a page reports itself loaded before it starts translating, and
 * how often it looks for strips that appeared afterwards.
 */
private const val AUTO_START_PAUSE_MS = 1_500L
private const val RESCAN_MS = 2_000L
private const val QUIET_SCANS_BEFORE_SLOWING = 10
private const val SLOW_RESCAN_MS = 6_000L

/** Asks the page for the strips it has not handed over yet. */
private suspend fun scanImages(view: WebView): List<PageImage> = suspendCancellableCoroutine { continuation ->
    view.evaluateJavascript("window.__mtScan ? window.__mtScan() : '[]'") { raw ->
        continuation.resume(parseScannedImages(raw))
    }
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
            IconTile(icon = Icons.Outlined.Public, size = 64.dp)
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

// --- Bảng màu Dark Purple chuẩn Material 3 cho bảng cài đặt trình đọc ---
private object ReaderSettingsPalette {
    val Background = Color(0xFF0E0C14)
    val CardSurface = Color(0xFF181523)
    val CardBorder = Color(0xFF2C2540)
    val Divider = Color(0x18FFFFFF)
    val Primary = Color(0xFFA864FF)
    val PrimaryContainer = Color(0x29A864FF)
    val TextPrimary = Color(0xFFF3EFFA)
    val TextSecondary = Color(0xFF9C94AD)
    val Destructive = Color(0xFFFF6B81)
    val DestructiveContainer = Color(0x22FF6B81)
}

@Composable
private fun WebSettingsSheet(
    state: WebReaderUiState,
    onClose: () -> Unit,
    onToggleAds: () -> Unit,
    onToggleSource: (String) -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onToggleReader: (Boolean) -> Unit,
    onToggleDesktop: (Boolean) -> Unit,
    onToggleKeepAwake: (Boolean) -> Unit,
    onClearTranslations: () -> Unit,
    onClearCookies: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    val palette = ReaderSettingsPalette
    val filterSources = AdBlocker.SOURCES
    val activeFilterCount = remember(state.blockerSources) {
        filterSources.count { it.id in state.blockerSources }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(palette.Background)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Tiêu đề thay cho TopAppBar vì đây là ModalBottomSheet
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.PrimaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = palette.Primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = "Cài đặt trình đọc",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Đóng",
                    tint = palette.TextPrimary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        // 1. NHÓM HIỂN THỊ & TRẢI NGHIỆM
        ReaderSettingsSectionHeader(title = "Hiển thị & Trải nghiệm")
        ReaderSettingsGroupCard {
            ReaderSettingsSwitchRow(
                icon = Icons.Rounded.Translate,
                title = "Tự động dịch",
                subtitle = "Bắt đầu sau khi trang tải xong, dò thêm ảnh mới",
                checked = state.autoTranslate,
                onCheckedChange = onToggleAuto,
            )
            ReaderSettingsInsetDivider()
            ReaderSettingsSwitchRow(
                icon = Icons.Rounded.AutoStories,
                title = "Chế độ đọc",
                subtitle = "Ảnh vừa khung, nền và chữ theo ứng dụng",
                checked = state.readerMode,
                onCheckedChange = onToggleReader,
            )
            ReaderSettingsInsetDivider()
            ReaderSettingsSwitchRow(
                icon = Icons.Rounded.Computer,
                title = "Giao diện máy tính",
                subtitle = "Giả lập trình duyệt desktop",
                checked = state.desktopMode,
                onCheckedChange = onToggleDesktop,
            )
            ReaderSettingsInsetDivider()
            ReaderSettingsSwitchRow(
                icon = Icons.Rounded.WbSunny,
                title = "Giữ màn hình sáng",
                subtitle = "Không tự động tắt màn hình khi đang đọc",
                checked = state.keepAwake,
                onCheckedChange = onToggleKeepAwake,
            )
        }

        // 2. NHÓM CHẶN QUẢNG CÁO & NGUỒN BỘ LỌC (gom logic & tự thu gọn)
        ReaderSettingsSectionHeader(
            title = "Chặn quảng cáo & Bộ lọc",
            badgeText = if (state.adBlockEnabled) {
                "$activeFilterCount/${filterSources.size} đang bật"
            } else {
                "Đang tắt"
            },
            badgeActive = state.adBlockEnabled,
        )
        ReaderSettingsGroupCard {
            ReaderSettingsSwitchRow(
                icon = Icons.Rounded.Shield,
                title = "Chặn quảng cáo",
                subtitle = "Lọc tên miền quảng cáo và trình theo dõi",
                checked = state.adBlockEnabled,
                onCheckedChange = { onToggleAds() },
            )

            AnimatedVisibility(
                visible = state.adBlockEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.16f)),
                ) {
                    HorizontalDivider(color = palette.Divider, thickness = 1.dp)
                    filterSources.forEachIndexed { index, source ->
                        ReaderFilterSourceRow(
                            name = source.label,
                            domainCount = if (state.blockerReady) {
                                state.blockerCounts[source.id] ?: 0
                            } else {
                                null
                            },
                            hint = source.hint,
                            checked = source.id in state.blockerSources,
                            onCheckedChange = { checked -> onToggleSource(source.id) },
                        )
                        if (index < filterSources.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 20.dp, end = 16.dp),
                                color = palette.Divider,
                                thickness = 0.8.dp,
                            )
                        }
                    }
                }
            }
        }

        // 3. NHÓM TÁC VỤ TRANG & DỮ LIỆU
        ReaderSettingsSectionHeader(title = "Tác vụ & Dữ liệu trang")
        ReaderSettingsGroupCard {
            ReaderSettingsActionRow(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                title = "Mở bằng trình duyệt ngoài",
                subtitle = "Chuyển trang hiện tại sang Chrome hoặc trình duyệt mặc định",
                isDestructive = false,
                onClick = onOpenExternal,
            )
            ReaderSettingsInsetDivider()
            ReaderSettingsActionRow(
                icon = Icons.Rounded.DeleteSweep,
                title = "Xoá bản dịch trên trang",
                subtitle = "Khôi phục lại nội dung ngôn ngữ gốc",
                isDestructive = true,
                onClick = onClearTranslations,
            )
            ReaderSettingsInsetDivider()
            ReaderSettingsActionRow(
                icon = Icons.Rounded.CleaningServices,
                title = "Xoá cookie của trang",
                subtitle = "Đăng xuất và làm mới dữ liệu lưu trữ cục bộ",
                isDestructive = true,
                onClick = onClearCookies,
            )
        }
    }
}

@Composable
private fun ReaderSettingsSectionHeader(
    title: String,
    badgeText: String? = null,
    badgeActive: Boolean = false,
) {
    val palette = ReaderSettingsPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            ),
            color = palette.TextSecondary,
        )
        if (badgeText != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (badgeActive) palette.PrimaryContainer else Color.White.copy(alpha = 0.06f),
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (badgeActive) palette.Primary else palette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = ReaderSettingsPalette
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = palette.CardSurface,
        border = BorderStroke(1.dp, palette.CardBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun ReaderSettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = ReaderSettingsPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (checked) palette.PrimaryContainer
                    else Color.White.copy(alpha = 0.05f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) palette.Primary else palette.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
                color = palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                    color = palette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        ReaderSettingsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ReaderFilterSourceRow(
    name: String,
    domainCount: Int?,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = ReaderSettingsPalette
    val formattedCount = remember(domainCount) {
        domainCount?.let {
            NumberFormat.getNumberInstance(Locale("vi", "VN")).format(it)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chấm chỉ báo trạng thái bộ lọc
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (checked) palette.Primary
                    else palette.TextSecondary.copy(alpha = 0.35f),
                ),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
                color = palette.TextPrimary,
            )
            Text(
                text = if (formattedCount != null) "$formattedCount tên miền" else hint,
                style = MaterialTheme.typography.bodySmall,
                color = palette.TextSecondary,
            )
        }

        ReaderSettingsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ReaderSettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean,
    onClick: () -> Unit,
) {
    val palette = ReaderSettingsPalette
    val accentColor = if (isDestructive) palette.Destructive else palette.Primary
    val containerColor = if (isDestructive) palette.DestructiveContainer else palette.PrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
                color = if (isDestructive) palette.Destructive else palette.TextPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                    color = palette.TextSecondary,
                )
            }
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = palette.TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ReaderSettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = ReaderSettingsPalette.Primary,
            uncheckedThumbColor = Color(0xFF9C94AD),
            uncheckedTrackColor = Color(0xFF252036),
            uncheckedBorderColor = Color(0xFF473E63),
        ),
    )
}

@Composable
private fun ReaderSettingsInsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color = ReaderSettingsPalette.Divider,
        thickness = 0.8.dp,
    )
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)),
    )
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
