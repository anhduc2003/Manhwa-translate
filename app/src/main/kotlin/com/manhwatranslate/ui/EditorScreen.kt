package com.manhwatranslate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.manhwatranslate.data.SavedPage
import com.manhwatranslate.ui.components.AppTopBar
import com.manhwatranslate.ui.components.Pill
import com.manhwatranslate.ui.theme.LocalAccents
import com.manhwatranslate.ui.theme.Motion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    EditorContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onNavigateToPage = onNavigateToPage,
        onToggleOriginal = { viewModel.toggleOriginal() },
        onSave = {
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
        },
        onShare = { viewModel.share() },
        onDeletePage = { viewModel.deletePage() },
        onSelectRegion = { viewModel.selectRegion(it) },
        onTextChange = { viewModel.updateEditingText(it) },
        onApply = { viewModel.applyEdit() },
        onRetranslate = { viewModel.retranslateSelected() },
        onMessageShown = { viewModel.consumeMessage() },
    )
}

@Composable
internal fun EditorContent(
    state: EditorUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onNavigateToPage: (String) -> Unit,
    onToggleOriginal: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDeletePage: () -> Unit,
    onSelectRegion: (Int?) -> Unit,
    onTextChange: (String) -> Unit,
    onApply: () -> Unit,
    onRetranslate: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val page = state.page
    val groupPages = state.groupPages
    val currentIndex = groupPages.indexOfFirst { it.id == page?.id }
    val accents = LocalAccents.current

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = if (page != null && page.pageCount > 1) {
                    "Trang ${page.pageIndex + 1}/${page.pageCount}"
                } else {
                    "Bản dịch"
                },
                subtitle = page?.let { "${it.regions.size} vùng · ${it.providerName}" },
                onBack = onBack,
                actions = {
                    IconButton(onClick = onToggleOriginal) {
                        Icon(
                            Icons.Outlined.Compare,
                            contentDescription = if (state.showOriginal) {
                                "Xem bản dịch"
                            } else {
                                "Xem bản gốc"
                            },
                            tint = if (state.showOriginal) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onSave) {
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
                                    onShare()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Xoá trang") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onDeletePage()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (page != null) {
                RegionEditor(
                    page = page,
                    state = state,
                    onSelect = onSelectRegion,
                    onTextChange = onTextChange,
                    onApply = onApply,
                    onRetranslate = onRetranslate,
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
                    onSelect = onSelectRegion,
                )
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            AnimatedVisibility(
                visible = groupPages.size > 1 && currentIndex >= 0,
                enter = fadeIn(tween(Motion.Panel, easing = Motion.Standard)) +
                    slideInVertically(tween(Motion.Panel, easing = Motion.Emphasized)) { it / 2 },
                exit = fadeOut(tween(Motion.Fast)) +
                    slideOutVertically(tween(Motion.Panel, easing = Motion.Emphasized)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            ) {
                PageSwitcher(
                    index = currentIndex.coerceAtLeast(0),
                    count = groupPages.size,
                    onPrevious = if (currentIndex > 0) {
                        { onNavigateToPage(groupPages[currentIndex - 1].id) }
                    } else {
                        null
                    },
                    onNext = if (currentIndex in 0 until groupPages.lastIndex) {
                        { onNavigateToPage(groupPages[currentIndex + 1].id) }
                    } else {
                        null
                    },
                )
            }
            AnimatedVisibility(
                visible = state.busy,
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut() + slideOutVertically { -it / 3 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(state.busyLabel, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.message?.let { message ->
                LaunchedEffect(message) {
                    snackbar.showSnackbar(message)
                    onMessageShown()
                }
            }
            if (state.showOriginal) {
                Surface(
                    shape = CircleShape,
                    color = accents.selection.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Bản gốc",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageSwitcher(
    index: Int,
    count: Int,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "Trang trước",
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "${index + 1}/$count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Trang sau",
                    modifier = Modifier.size(20.dp),
                )
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
    val accents = LocalAccents.current

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
                        val color = if (selected) accents.selection else Color(0xFF38BDF8)
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
    page: SavedPage,
    state: EditorUiState,
    onSelect: (Int?) -> Unit,
    onTextChange: (String) -> Unit,
    onApply: () -> Unit,
    onRetranslate: () -> Unit,
) {
    val accents = LocalAccents.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        border = BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(accents.glowStart, accents.glowEnd),
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 12.dp)
                .animateContentSize(tween(Motion.Panel, easing = Motion.Standard)),
        ) {
            val selectedRegion = page.regions.firstOrNull { it.id == state.selectedRegionId }
            if (selectedRegion == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chạm vào khung chữ để sửa bản dịch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val chipListState = rememberLazyListState()
                val selectedIndex = page.regions.indexOfFirst { it.id == state.selectedRegionId }
                LaunchedEffect(selectedIndex) {
                    if (selectedIndex >= 0) chipListState.animateScrollToItem(selectedIndex)
                }
                LazyRow(
                    state = chipListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 10.dp),
                ) {
                    items(page.regions, key = { it.id }) { region ->
                        val translation = page.translations.firstOrNull { it.regionId == region.id }
                        val selected = region.id == state.selectedRegionId
                        RegionChip(
                            label = "#${region.id + 1}",
                            text = translation?.translatedText.orEmpty().ifBlank { region.text },
                            selected = selected,
                            onClick = { onSelect(if (selected) null else region.id) },
                        )
                    }
                }

                Text(
                    text = "Gốc",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selectedRegion.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.editingText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = { Text("Bản dịch") },
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 4,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApply,
                        enabled = !state.busy,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accents.heroButton,
                            contentColor = accents.onHeroButton,
                        ),
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Áp dụng", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onRetranslate,
                        enabled = !state.busy,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Dịch lại")
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionChip(
    label: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(Motion.Fast, easing = Motion.Standard),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(Motion.Fast, easing = Motion.Standard),
        label = "chipContent",
    )
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = container,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
            if (text.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
            }
        }
    }
}
