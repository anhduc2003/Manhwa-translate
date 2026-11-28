package com.manhwatranslate.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.manhwatranslate.core.model.TranslationProviderType
import com.manhwatranslate.core.model.TranslationStatus
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.data.UserProfile
import com.manhwatranslate.data.SavedPage
import com.manhwatranslate.ui.components.AccentPill
import com.manhwatranslate.ui.components.AppLogo
import com.manhwatranslate.ui.components.CustomManhwaHeroCard
import com.manhwatranslate.ui.components.HeroStyleConfig
import com.manhwatranslate.ui.components.GroupCard
import com.manhwatranslate.ui.components.IconTile
import com.manhwatranslate.ui.components.ManhwaBottomNavBar
import com.manhwatranslate.ui.components.SectionTitle
import com.manhwatranslate.ui.components.StatusBadge
import com.manhwatranslate.ui.theme.LocalAccents
import com.manhwatranslate.ui.theme.Motion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HistoryBatch(
    val groupId: String,
    val coverPage: SavedPage,
    val totalPages: Int,
    val latestTimestamp: Long,
    val totalRegions: Int,
    val isCompleted: Boolean,
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWebReader: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> if (uris.isNotEmpty()) viewModel.translate(uris) }

    var pendingCapture by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCapture
        pendingCapture = null
        if (success && uri != null) viewModel.translate(listOf(uri))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenPage -> onOpenPage(event.pageId)
                is HomeEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    HomeContent(
        state = state,
        snackbar = snackbar,
        onPickImages = {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onCapture = {
            val file = File(context.cacheDir, "capture").apply { mkdirs() }
                .resolve("shot-${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            pendingCapture = uri
            takePicture.launch(uri)
        },
        onOpenSettings = onOpenSettings,
        onOpenWebReader = onOpenWebReader,
        onOpenPage = onOpenPage,
        onDeletePage = { viewModel.deletePage(it) },
        onDeleteGroup = { viewModel.deleteGroup(it) },
        onCancelProcessing = { viewModel.cancel() },
        onSignIn = onSignIn,
        onSignOut = onSignOut,
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onPickImages: () -> Unit,
    onCapture: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWebReader: () -> Unit,
    onOpenPage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onDeleteGroup: (String) -> Unit = {},
    onCancelProcessing: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    // 0 = Trang chủ, 2 = Thư viện (Tab 1 mở Đọc web, Tab 3 mở Cài đặt)
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    val historyBatches = remember(state.pages) {
        state.pages
            .groupBy { it.groupId }
            .map { (groupId, groupPages) ->
                val sorted = groupPages.sortedBy { it.pageIndex }
                val cover = sorted.first()
                val hasFailed = groupPages.any { p ->
                    p.translations.any { it.status == TranslationStatus.FAILED }
                }
                HistoryBatch(
                    groupId = groupId,
                    coverPage = cover,
                    totalPages = groupPages.size,
                    latestTimestamp = groupPages.maxOf { it.createdAt },
                    totalRegions = groupPages.sumOf { it.regions.size },
                    isCompleted = !hasFailed && groupPages.any { it.regions.isNotEmpty() },
                )
            }
            .sortedByDescending { it.latestTimestamp }
    }
    val pageRows = remember(state.pages) { state.pages.chunked(2) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            HomeHeader(
                title = if (activeTab == 2) "Thư viện" else "Manhwa Translate",
                user = state.user,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                onOpenWebReader = onOpenWebReader,
            )
        },
        bottomBar = {
            ManhwaBottomNavBar(
                selectedIndex = activeTab,
                onSelect = { index ->
                    when (index) {
                        0 -> activeTab = 0
                        1 -> onOpenWebReader()
                        2 -> activeTab = 2
                        3 -> onOpenSettings()
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (activeTab == 0) {
                item {
                    val latestThumb = remember(state.pages) {
                        state.pages.firstOrNull()?.let { page ->
                            File(context.filesDir, "pages/${page.id}/${page.fileNameThumb}")
                        }
                    }
                    CustomManhwaHeroCard(
                        onPickImages = onPickImages,
                        onCapture = onCapture,
                        artModel = latestThumb,
                        style = HeroStyleConfig(
                            cornerRadius = 28.dp,
                            gradientColors = listOf(
                                Color(0xFF331D60),
                                Color(0xFF8B2464),
                                Color(0xFFE87070),
                            ),
                            borderColors = listOf(
                                Color(0x998C5CE6),
                                Color(0x88E04F8E),
                                Color(0xCCFF9B85),
                            ),
                            artAlpha = 0.42f,
                            monochromeArt = true,
                            artWidthFraction = 0.65f,
                        ),
                    )
                }

                item {
                    ConfigRow(settings = state.settings, onOpenSettings = onOpenSettings)
                }

                item {
                    SectionTitle(
                        text = "Lịch sử dịch",
                        trailing = if (state.pages.isEmpty()) null else "${state.pages.size} trang",
                        modifier = Modifier.animateItem(),
                    )
                }

                if (historyBatches.isEmpty()) {
                    item {
                        EmptyLibrary(
                            onPickImages = onPickImages,
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    items(historyBatches, key = { it.groupId }) { batch ->
                        HistoryBatchCard(
                            batch = batch,
                            onOpen = { onOpenPage(batch.coverPage.id) },
                            onDelete = { onDeleteGroup(batch.groupId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            } else {
                item {
                    SectionTitle(
                        text = "Tất cả trang đã dịch",
                        trailing = if (state.pages.isEmpty()) null else "${state.pages.size} trang",
                    )
                }
                if (state.pages.isEmpty()) {
                    item { EmptyLibrary(onPickImages = onPickImages) }
                } else {
                    items(pageRows.size) { rowIndex ->
                        val rowPages = pageRows[rowIndex]
                        Row(
                            modifier = Modifier.animateItem(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            rowPages.forEach { page ->
                                PageCard(
                                    page = page,
                                    onOpen = { onOpenPage(page.id) },
                                    onDelete = { onDeletePage(page.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowPages.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    state.processing?.let { processing ->
        ProcessingDialog(processing, onCancel = onCancelProcessing)
    }
}

@Composable
private fun HomeHeader(
    title: String,
    user: UserProfile,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenWebReader: () -> Unit,
) {
    val accents = LocalAccents.current
    var showAccount by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (user.isSignedIn) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(accents.glowStart, accents.glowEnd)),
                    shape = CircleShape,
                )
                .clickable { if (user.isSignedIn) showAccount = true else onSignIn() },
            contentAlignment = Alignment.Center,
        ) {
            if (user.isSignedIn && user.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = "Tài khoản ${user.displayName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = if (user.isSignedIn) "Tài khoản" else "Đăng nhập GitHub",
                    modifier = Modifier.size(20.dp),
                    tint = if (user.isSignedIn) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(accents.glowStart, accents.glowEnd)),
                    shape = CircleShape,
                )
                .clickable(onClick = onOpenWebReader),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = "Đọc web nhanh",
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }

    if (showAccount) {
        AlertDialog(
            onDismissRequest = { showAccount = false },
            title = { Text("Tài khoản GitHub", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.displayName.ifBlank { user.username.ifBlank { "Người dùng GitHub" } }, fontWeight = FontWeight.Medium)
                    if (user.username.isNotBlank()) {
                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (user.email.isNotBlank()) {
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Đăng nhập GitHub giúp quản lý tài khoản và đồng bộ dữ liệu giữa các thiết bị.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccount = false }) { Text("Đóng") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAccount = false
                    onSignOut()
                }) { Text("Đăng xuất", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun ConfigRow(settings: AppSettings, onOpenSettings: () -> Unit) {
    GroupCard(glowBorder = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon = Icons.Outlined.SwapHoriz)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Ngôn ngữ và bộ dịch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = settings.sourceScript.shortLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(15.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = settings.target.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            AccentPill(text = settings.provider.shortLabel(), maxWidth = 112.dp)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HistoryBatchCard(
    batch: HistoryBatch,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val page = batch.coverPage
    val accents = LocalAccents.current
    val displayTitle = page.title.substringBeforeLast('.').ifBlank { "Trang đã dịch" }

    GroupCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = File(
                        LocalContext.current.filesDir,
                        "pages/${page.id}/${page.fileNameThumb}",
                    ),
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (accents.imageScrim.alpha > 0f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(accents.imageScrim),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatRelativeTime(batch.latestTimestamp)} • ${batch.totalPages} trang",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (batch.isCompleted) {
                    Spacer(Modifier.height(2.dp))
                    StatusBadge(text = "Đã hoàn thành", positive = true)
                } else {
                    Text(
                        text = "${batch.totalRegions} vùng thoại · ${page.providerName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Xoá",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(if (batch.totalPages > 1) "Xoá nhóm ${batch.totalPages} trang?" else "Xoá trang này?")
            },
            text = { Text("Ảnh đã dịch và dữ liệu nhận diện sẽ bị xoá khỏi máy.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Xoá") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Huỷ") }
            },
        )
    }
}

@Composable
private fun EmptyLibrary(
    onPickImages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconTile(
                icon = Icons.Outlined.PhotoLibrary,
                size = 56.dp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Chưa có lịch sử dịch nào",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Chọn ảnh truyện hoặc đọc trên web — các trang đã dịch sẽ lưu gọn tại đây.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onPickImages,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            ) {
                Text("Chọn ảnh đầu tiên")
            }
        }
    }
}

@Composable
private fun PageCard(
    page: SavedPage,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val accents = LocalAccents.current
    Surface(
        modifier = modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = File(
                        LocalContext.current.filesDir,
                        "pages/${page.id}/${page.fileNameThumb}",
                    ),
                    contentDescription = page.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.74f),
                )
                if (accents.imageScrim.alpha > 0f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(accents.imageScrim),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Xoá",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
                if (page.pageCount > 1) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomStart),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = Color.White,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${page.pageIndex + 1}/${page.pageCount}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
                Text(
                    text = page.title.substringBeforeLast('.').ifBlank { "Trang đã dịch" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${page.regions.size} vùng · ${formatDate(page.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xoá trang này?") },
            text = { Text("Ảnh đã dịch và dữ liệu nhận diện sẽ bị xoá khỏi máy.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Xoá") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Huỷ") }
            },
        )
    }
}

@Composable
private fun ProcessingDialog(state: ProcessingState, onCancel: () -> Unit) {
    val accents = LocalAccents.current
    val progress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(360),
        label = "processing",
    )
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = { }) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(Motion.Panel, easing = Motion.Standard)) +
                scaleIn(tween(Motion.Panel, easing = Motion.Emphasized), initialScale = 0.92f),
            exit = fadeOut(tween(Motion.Fast)),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(accents.glowStart, accents.glowEnd)),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(96.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = if (state.pageCount > 1) {
                            "Trang ${state.pageIndex}/${state.pageCount}"
                        } else {
                            "Đang xử lý"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.stage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Huỷ")
                    }
                }
            }
        }
    }
}

private fun TranslationProviderType.shortLabel(): String = when (this) {
    TranslationProviderType.FREE_AUTO -> "Miễn phí"
    TranslationProviderType.ON_DEVICE -> "ML Kit"
    TranslationProviderType.LOCAL_LLM -> "LLM máy"
    TranslationProviderType.GOOGLE_FREE -> "Google Dịch"
    TranslationProviderType.MYMEMORY -> "MyMemory"
    TranslationProviderType.LIBRE_TRANSLATE -> "LibreTranslate"
    TranslationProviderType.GOOGLE_CLOUD -> "Google Cloud"
    TranslationProviderType.DEEPL -> "DeepL"
    TranslationProviderType.OPENAI_COMPATIBLE -> "OpenAI"
    TranslationProviderType.GEMINI -> "Gemini"
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1 -> "Dịch vừa xong"
        minutes < 60 -> "Dịch $minutes phút trước"
        hours < 24 -> "Dịch $hours giờ trước"
        days == 1L -> "Dịch hôm qua"
        days < 7 -> "Dịch $days ngày trước"
        else -> "Dịch ${formatDate(timestamp)}"
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))


