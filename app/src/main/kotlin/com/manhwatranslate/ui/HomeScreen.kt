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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.manhwatranslate.ui.theme.Motion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Home-only dark palette (the design is dark-first) ---
private val BgDark = Color(0xFF100B12)
private val CardSurface = Color(0xFF1B1420)
private val CardBorderStart = Color(0x669C5CFF)
private val CardBorderEnd = Color(0x44FF5E9C)
private val HeroGradStart = Color(0xFF3E266B)
private val HeroGradMid = Color(0xFF7A285B)
private val HeroGradEnd = Color(0xFFB54166)
private val PrimaryPillBg = Color(0xFFF7E4EA)
private val PrimaryPillText = Color(0xFF3B1325)
private val AccentPurple = Color(0xFF8B46FF)
private val AccentPink = Color(0xFFFF6B9E)
private val TextPrimary = Color(0xFFF6F1F8)
private val TextSecondary = Color(0xFFA99DB2)
private val StatusGreenBg = Color(0xFF153427)
private val StatusGreenText = Color(0xFF4ADE80)

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
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        var selectedTab by remember { mutableIntStateOf(0) }
        val context = LocalContext.current
        val historyItems = remember(state.pages, context) {
            state.pages.map { page ->
                TranslationHistoryItem(
                    id = page.id,
                    title = page.title.ifBlank { "Trang không tên" },
                    timeAgo = relativeTime(page.createdAt),
                    pageCount = page.pageCount,
                    isCompleted = page.translations.isNotEmpty(),
                    coverFile = File(context.filesDir, "pages/${page.id}/${page.fileNameThumb}"),
                )
            }
        }

        Scaffold(
            containerColor = BgDark,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                ManhwaBottomNavBar(
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                    onOpenSettings = onOpenSettings,
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    HomeTopBar(onSettingsClick = onOpenSettings)
                }

                item {
                    HeroTranslateCard(onPickImageClick = onPickImages, onTakePhotoClick = onCapture)
                }

                item {
                    LanguageEngineCard(
                        sourceLang = state.settings.sourceScript.shortLabel,
                        targetLang = state.settings.target.label,
                        engineName = state.settings.provider.shortLabel(),
                        onClick = onOpenSettings,
                    )
                }

                item {
                    WebReaderCard(onClick = onOpenWebReader)
                }

                item {
                    Text(
                        text = "Lịch sử dịch",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 2.dp),
                    )
                }

                if (historyItems.isEmpty()) {
                    item { EmptyHistoryCard() }
                } else {
                    items(historyItems, key = { it.id }) { item ->
                        HistoryItemCard(
                            item = item,
                            onClick = { onOpenPage(item.id) },
                            onDelete = { onDeletePage(item.id) },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        state.processing?.let { processing ->
            ProcessingDialog(processing, onCancel = onCancelProcessing)
        }
    }
}

// --- Data model for the history list ---
data class TranslationHistoryItem(
    val id: String,
    val title: String,
    val timeAgo: String,
    val pageCount: Int,
    val isCompleted: Boolean = false,
    val coverFile: File? = null,
)

@Composable
private fun HomeTopBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(AccentPurple, AccentPink))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "Manhwa Translate",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF241B2B)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Cài đặt",
                tint = Color(0xFFD6C8E0),
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun HeroTranslateCard(onPickImageClick: () -> Unit, onTakePhotoClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(HeroGradStart, HeroGradMid, HeroGradEnd)))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f)),
                ),
                shape = RoundedCornerShape(26.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(160.dp)
                .offset(x = 30.dp, y = (-30).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x55FF8FB8), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
        ) {
            Text(
                text = "Dịch truyện\ntrong một chạm",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 33.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bản dịch được vẽ thẳng vào bong bóng thoại.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.5.sp,
            )
            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onPickImageClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryPillBg,
                        contentColor = PrimaryPillText,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Chọn ảnh", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                }

                OutlinedButton(
                    onClick = onTakePhotoClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.10f),
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Chụp ảnh", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                }
            }
        }
    }
}

@Composable
private fun LanguageEngineCard(
    sourceLang: String,
    targetLang: String,
    engineName: String,
    onClick: () -> Unit,
) {
    GlowingFeatureCard(onClick = onClick) {
        FeatureIconBox(icon = Icons.Rounded.SwapHoriz)

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ngôn ngữ và bộ dịch",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sourceLang,
                    color = TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = AccentPink,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(15.dp),
                )
                Text(
                    text = targetLang,
                    color = TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Color(0xFF7E3FF2), Color(0xFF9E5CF8))))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = engineName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
        )
    }
}

@Composable
private fun WebReaderCard(onClick: () -> Unit) {
    GlowingFeatureCard(onClick = onClick) {
        FeatureIconBox(icon = Icons.Rounded.Public)

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Đọc trên web",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Dịch ảnh trang truyện trực tiếp",
                color = TextSecondary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF4ADE80)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = Color(0xFF0F172A),
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
        )
    }
}

@Composable
private fun GlowingFeatureCard(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardSurface)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(CardBorderStart, CardBorderEnd)),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun FeatureIconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2A1D38))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(AccentPurple.copy(0.7f), AccentPink.copy(0.5f))),
                shape = RoundedCornerShape(14.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFE9D5FF),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun HistoryItemCard(
    item: TranslationHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardSurface)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.coverFile != null) {
            AsyncImage(
                model = item.coverFile,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF701A75)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.timeAgo} • ${item.pageCount} trang",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            if (item.isCompleted) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(StatusGreenBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Đã hoàn thành",
                        color = StatusGreenText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        IconButton(onClick = { confirmDelete = true }) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Xoá",
                tint = TextSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xoá trang này?", color = TextPrimary) },
            text = { Text("Ảnh đã dịch và dữ liệu nhận diện sẽ bị xoá khỏi máy.", color = TextSecondary) },
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
private fun EmptyHistoryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardSurface)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF281F31)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Chưa có trang nào",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ảnh đã dịch sẽ được lưu ở đây.",
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ManhwaBottomNavBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val navItems = listOf(
        Pair("Trang chủ", Icons.Rounded.Home),
        Pair("Khám phá", Icons.Rounded.Explore),
        Pair("Thư viện", Icons.Rounded.PhotoLibrary),
        Pair("Cài đặt", Icons.Rounded.Settings),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF2151019))
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navItems.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedIndex == index
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (index == 3) onOpenSettings() else onSelect(index)
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) Color(0xFF42344D) else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Color.White else TextSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
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
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Huỷ")
                    }
                }
            }
        }
    }
}


private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1 -> "Vừa xong"
        minutes < 60 -> "Dịch $minutes phút trước"
        hours < 24 -> "Dịch $hours giờ trước"
        days < 7 -> "Dịch $days ngày trước"
        else -> "Dịch ${SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))}"
    }
}

private fun com.manhwatranslate.core.model.TranslationProviderType.shortLabel(): String = when (this) {
    com.manhwatranslate.core.model.TranslationProviderType.FREE_AUTO -> "Miễn phí"
    com.manhwatranslate.core.model.TranslationProviderType.ON_DEVICE -> "ML Kit"
    com.manhwatranslate.core.model.TranslationProviderType.LOCAL_LLM -> "LLM máy"
    com.manhwatranslate.core.model.TranslationProviderType.GOOGLE_FREE -> "Google Dịch"
    com.manhwatranslate.core.model.TranslationProviderType.MYMEMORY -> "MyMemory"
    com.manhwatranslate.core.model.TranslationProviderType.LIBRE_TRANSLATE -> "LibreTranslate"
    com.manhwatranslate.core.model.TranslationProviderType.GOOGLE_CLOUD -> "Google Cloud"
    com.manhwatranslate.core.model.TranslationProviderType.DEEPL -> "DeepL"
    com.manhwatranslate.core.model.TranslationProviderType.OPENAI_COMPATIBLE -> "OpenAI"
    com.manhwatranslate.core.model.TranslationProviderType.GEMINI -> "Gemini"
}
