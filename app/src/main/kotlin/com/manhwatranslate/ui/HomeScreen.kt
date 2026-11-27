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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.data.SavedPage
import com.manhwatranslate.ui.components.GroupCard
import com.manhwatranslate.ui.components.IconTile
import com.manhwatranslate.ui.components.Pill
import com.manhwatranslate.ui.components.SectionTitle
import com.manhwatranslate.ui.theme.LocalAccents
import com.manhwatranslate.ui.theme.Motion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWebReader: () -> Unit,
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
        onCancelProcessing = { viewModel.cancel() },
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
    onCancelProcessing: () -> Unit,
) {
    val pageRows = remember(state.pages) { state.pages.chunked(2) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { HomeHeader(onOpenSettings = onOpenSettings) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeroCard(onPickImages = onPickImages, onCapture = onCapture)
            }

            item {
                ConfigRow(settings = state.settings, onOpenSettings = onOpenSettings)
            }

            item {
                WebReaderRow(onClick = onOpenWebReader)
            }

            item {
                SectionTitle(
                    text = "Thư viện",
                    trailing = if (state.pages.isEmpty()) null else "${state.pages.size} trang",
                    modifier = Modifier.animateItem(),
                )
            }

            if (state.pages.isEmpty()) {
                item { EmptyLibrary(modifier = Modifier.animateItem()) }
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

    state.processing?.let { processing ->
        ProcessingDialog(processing, onCancel = onCancelProcessing)
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    val accents = LocalAccents.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 15.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(accents.heroStart, accents.heroEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "M",
                color = accents.onHero,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = "Manhwa Translate",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Cài đặt",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(onPickImages: () -> Unit, onCapture: () -> Unit) {
    val accents = LocalAccents.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(accents.heroStart, accents.heroMiddle, accents.heroEnd),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .align(Alignment.TopEnd)
                .offset(x = 34.dp, y = (-34).dp)
                .clip(CircleShape)
                .background(accents.onHero.copy(alpha = 0.08f)),
        )
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 22.dp)) {
            Text(
                text = "Dịch truyện\ntrong một chạm",
                style = MaterialTheme.typography.displaySmall,
                color = accents.onHero,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Bản dịch được vẽ thẳng vào bong bóng thoại.",
                style = MaterialTheme.typography.bodyMedium,
                color = accents.onHeroMuted,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPickImages,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accents.heroButton,
                        contentColor = accents.onHeroButton,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Chọn ảnh", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onCapture,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, accents.onHero.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.onHero),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Chụp ảnh")
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(settings: AppSettings, onOpenSettings: () -> Unit) {
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = settings.sourceScript.shortLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
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
            Pill(text = settings.provider.shortLabel(), maxWidth = 120.dp)
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(-90f),
            )
        }
    }
}

@Composable
private fun WebReaderRow(onClick: () -> Unit) {
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon = Icons.Outlined.Public)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Đọc trên web",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Dịch ảnh trang truyện trực tiếp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(-90f),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    GroupCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Chưa có trang nào",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ảnh đã dịch sẽ được lưu ở đây.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
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
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f),
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
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
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
    TranslationProviderType.GOOGLE_FREE -> "Google Dịch"
    TranslationProviderType.MYMEMORY -> "MyMemory"
    TranslationProviderType.LIBRE_TRANSLATE -> "LibreTranslate"
    TranslationProviderType.GOOGLE_CLOUD -> "Google Cloud"
    TranslationProviderType.DEEPL -> "DeepL"
    TranslationProviderType.OPENAI_COMPATIBLE -> "OpenAI"
    TranslationProviderType.GEMINI -> "Gemini"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
