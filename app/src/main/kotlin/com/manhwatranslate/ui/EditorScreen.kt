package com.manhwatranslate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwatranslate.core.model.TextRegion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onNavigateToPage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.saveToGallery()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Message -> snackbar.showSnackbar(event.text)
                is EditorEvent.Share -> context.startActivity(event.intent)
                EditorEvent.Closed -> onBack()
            }
        }
    }

    val page = state.page
    val groupPages = state.groupPages
    val currentIndex = groupPages.indexOfFirst { it.id == page?.id }

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (page != null && page.pageCount > 1) {
                                "Trang ${page.pageIndex + 1}/${page.pageCount}"
                            } else {
                                "Bản dịch"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (page != null) {
                            Text(
                                text = buildString {
                                    append("${page.regions.size} vùng chữ")
                                    if (page.skippedCount > 0) append(" · ${page.skippedCount} SFX bỏ qua")
                                    append(" · ${page.providerName}")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (currentIndex > 0) {
                        IconButton(onClick = { onNavigateToPage(groupPages[currentIndex - 1].id) }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Trang trước")
                        }
                    }
                    if (currentIndex >= 0 && currentIndex < groupPages.lastIndex) {
                        IconButton(onClick = { onNavigateToPage(groupPages[currentIndex + 1].id) }) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Trang sau")
                        }
                    }
                    IconButton(onClick = { viewModel.toggleOriginal() }) {
                        Icon(
                            Icons.Outlined.Compare,
                            contentDescription = if (state.showOriginal) "Xem bản dịch" else "Xem bản gốc",
                            tint = if (state.showOriginal) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.saveToGallery()
                        } else {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }) {
                        Icon(Icons.Outlined.SaveAlt, contentDescription = "Lưu ảnh")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Thêm")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Chia sẻ") },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.share()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Xoá trang") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.deletePage()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (page != null) {
                RegionEditor(
                    page = page,
                    state = state,
                    onSelect = { viewModel.selectRegion(it) },
                    onTextChange = { viewModel.updateEditingText(it) },
                    onApply = { viewModel.applyEdit() },
                    onRetranslate = { viewModel.retranslateSelected() },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val bitmap = state.image
            if (bitmap != null) {
                ZoomablePage(
                        bitmap = bitmap,
                        regions = page?.regions.orEmpty(),
                        selectedRegionId = state.selectedRegionId,
                        showBoxes = !state.showOriginal,
                        onSelect = { viewModel.selectRegion(it) },
                    )
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (state.busy) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(state.busyLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.message?.let { message ->
                LaunchedEffect(message) {
                    snackbar.showSnackbar(message)
                    viewModel.consumeMessage()
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    bitmap: ImageBitmap,
    regions: List<TextRegion>,
    selectedRegionId: Int?,
    showBoxes: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val fitScale = remember(canvasSize, bitmap) {
        if (canvasSize.width <= 0f || canvasSize.height <= 0f) 1f
        else min(
            canvasSize.width / bitmap.width.toFloat(),
            canvasSize.height / bitmap.height.toFloat(),
        )
    }

    LaunchedEffect(canvasSize, bitmap) {
        if (canvasSize.width > 0f && canvasSize.height > 0f) {
            scale = fitScale
            offset = Offset(
                (canvasSize.width - bitmap.width * fitScale) / 2f,
                (canvasSize.height - bitmap.height * fitScale) / 2f,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(fitScale * 0.9f, fitScale * 8f)
                    val factor = newScale / scale
                    offset = (offset - centroid) * factor + centroid + pan
                    scale = newScale
                }
            }
            .pointerInput(regions) {
                detectTapGestures { position ->
                    val imageX = (position.x - offset.x) / scale
                    val imageY = (position.y - offset.y) / scale
                    val hit = regions.firstOrNull { region ->
                        region.box.expanded(12).contains(imageX.roundToInt(), imageY.roundToInt())
                    }
                    onSelect(hit?.id)
                }
            },
        contentAlignment = Alignment.TopStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(bitmap.width, bitmap.height),
                )
                if (showBoxes) {
                    val strokeWidth = max(1.5f, 2.2f / scale)
                    regions.forEach { region ->
                        val selected = region.id == selectedRegionId
                        val color = if (selected) Color(0xFFFFC857) else Color(0xFF38BDF8)
                        val rectSize = Size(region.box.width.toFloat(), region.box.height.toFloat())
                        val topLeft = Offset(region.box.left.toFloat(), region.box.top.toFloat())
                        if (selected) {
                            drawRect(
                                color = color.copy(alpha = 0.18f),
                                topLeft = topLeft,
                                size = rectSize,
                            )
                        }
                        drawRect(
                            color = color,
                            topLeft = topLeft,
                            size = rectSize,
                            style = Stroke(width = if (selected) strokeWidth * 2 else strokeWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionEditor(
    page: com.manhwatranslate.data.SavedPage,
    state: EditorUiState,
    onSelect: (Int?) -> Unit,
    onTextChange: (String) -> Unit,
    onApply: () -> Unit,
    onRetranslate: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(page.regions, key = { it.id }) { region ->
                    val translation = page.translations.firstOrNull { it.regionId == region.id }
                    val selected = region.id == state.selectedRegionId
                    AssistChip(
                        onClick = { onSelect(if (selected) null else region.id) },
                        label = {
                            Text(
                                text = "#${region.id + 1} " + translation?.translatedText.orEmpty().take(24),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    )
                }
            }

            val selectedRegion = page.regions.firstOrNull { it.id == state.selectedRegionId }
            if (selectedRegion != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = selectedRegion.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.editingText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("Bản dịch cho vùng chữ này") },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 4,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onRetranslate,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dịch lại")
                    }
                    androidx.compose.material3.Button(
                        onClick = onApply,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Áp dụng")
                    }
                }
            } else {
                Text(
                    text = "Chạm vào một khung chữ trên ảnh để sửa bản dịch",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}
