package com.manhwatranslate.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.layout.FontMode
import com.manhwatranslate.core.layout.FontWeightLevel
import com.manhwatranslate.core.model.OcrProviderType
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.model.TranslationProviderType
import com.manhwatranslate.core.scan.SfxSensitivity
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.translate.DownloadProgress
import java.util.Locale

// --- Bảng màu tinh chỉnh cho màn hình Cài đặt tối giản ---
private data class SettingsPalette(
    val background: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val divider: Color,
    val iconTileBg: Color,
    val iconTint: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val heroBorder: Color,
    val heroPrimaryBg: Color,
    val heroPrimaryText: Color,
    val heroSecondaryBg: Color,
    val heroSecondaryBorder: Color,
    val accentPurple: Color,
    val accentPink: Color,
    val successBg: Color,
    val successText: Color,
    val warningBg: Color,
    val warningText: Color,
    val sheetBg: Color,
)

@Composable
private fun rememberSettingsPalette(): SettingsPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(dark) {
        if (dark) {
            SettingsPalette(
                background = Color(0xFF0D0A11),
                cardBg = Color(0xFF17121E),
                cardBorder = Color(0x18FFFFFF),
                divider = Color(0x12FFFFFF),
                iconTileBg = Color(0xFF251E30),
                iconTint = Color(0xFFECE4F5),
                textPrimary = Color(0xFFF6F1F8),
                textSecondary = Color(0xFF9C90A8),
                heroStart = Color(0xFF2B1C47),
                heroMid = Color(0xFF3E1C3F),
                heroEnd = Color(0xFF4F2039),
                heroBorder = Color(0x30C084FC),
                heroPrimaryBg = Color(0xFFF7E4EA),
                heroPrimaryText = Color(0xFF3B1325),
                heroSecondaryBg = Color(0x22FFFFFF),
                heroSecondaryBorder = Color(0x45FFFFFF),
                accentPurple = Color(0xFF9355F6),
                accentPink = Color(0xFFEC4899),
                successBg = Color(0xFF143528),
                successText = Color(0xFF4ADE80),
                warningBg = Color(0xFF3D2714),
                warningText = Color(0xFFFBBF24),
                sheetBg = Color(0xFF14101A),
            )
        } else {
            SettingsPalette(
                background = Color(0xFFFAF6F3),
                cardBg = Color(0xFFFFFFFF),
                cardBorder = Color(0xFFE6DCEB),
                divider = Color(0xFFEEE6F2),
                iconTileBg = Color(0xFFF3ECFA),
                iconTint = Color(0xFF4A2574),
                textPrimary = Color(0xFF221C1A),
                textSecondary = Color(0xFF6F625C),
                heroStart = Color(0xFF4E2D87),
                heroMid = Color(0xFF7B2C66),
                heroEnd = Color(0xFFA93C66),
                heroBorder = Color(0x44FFFFFF),
                heroPrimaryBg = Color(0xFFFFF5F8),
                heroPrimaryText = Color(0xFF4A152E),
                heroSecondaryBg = Color(0x26FFFFFF),
                heroSecondaryBorder = Color(0x66FFFFFF),
                accentPurple = Color(0xFF7C3AED),
                accentPink = Color(0xFFDB2777),
                successBg = Color(0xFFDCFCE7),
                successText = Color(0xFF15803D),
                warningBg = Color(0xFFFEF3C7),
                warningText = Color(0xFFB45309),
                sheetBg = Color(0xFFFFFDFB),
            )
        }
    }
}

private enum class SettingsSheet {
    SOURCE_LANG,
    TARGET_LANG,
    PROVIDER,
    OCR,
    LLM_URL,
    API_KEY,
    LIBRE_SERVER,
    TYPOGRAPHY,
    ADVANCED,
    ABOUT,
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPickFont: () -> Unit,
    onPickLlmModel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    SettingsContent(
        settings = state.settings,
        configuredKeys = state.configuredKeys,
        snackbar = snackbar,
        apiKeyFor = { provider -> viewModel.apiKey(provider) },
        onUpdate = { transform -> viewModel.update(transform) },
        onSaveKey = { provider, key -> viewModel.saveApiKey(provider, key) },
        onTestKey = { provider, key ->
            viewModel.saveApiKey(provider, key)
            viewModel.testApiKey()
        },
        onTestOcr = { viewModel.testOcrServer() },
        onTestFree = { viewModel.testFreeSources() },
        onPickFont = onPickFont,
        onPickLlmModel = onPickLlmModel,
        downloadingModel = state.downloadingModel,
        partialModelBytes = state.partialModelBytes,
        downloadProgress = state.downloadProgress,
        onDownloadLlmModel = { viewModel.downloadLlmModel() },
        onClearFont = { viewModel.clearFont() },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    settings: AppSettings,
    configuredKeys: Set<TranslationProviderType>,
    snackbar: SnackbarHostState,
    apiKeyFor: (TranslationProviderType) -> String,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onSaveKey: (TranslationProviderType, String) -> Unit,
    onTestKey: (TranslationProviderType, String) -> Unit,
    onTestOcr: () -> Unit,
    onTestFree: () -> Unit,
    onPickFont: () -> Unit,
    onPickLlmModel: () -> Unit,
    downloadingModel: Boolean,
    partialModelBytes: Long,
    downloadProgress: DownloadProgress,
    onDownloadLlmModel: () -> Unit,
    onClearFont: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var activeSheet by remember { mutableStateOf<SettingsSheet?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        // Ánh sáng tím dịu rất nhẹ phía đỉnh màn hình
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.accentPurple.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        center = Offset(420f, 0f),
                        radius = 650f,
                    ),
                ),
        )

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = Color.Transparent,
            topBar = {
                SettingsTopBar(palette = palette, onBack = onBack)
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Cặp ngôn ngữ Bento đối xứng
                item {
                    BentoLanguageSelector(
                        palette = palette,
                        sourceLabel = settings.sourceScript.shortLabel,
                        targetLabel = settings.target.label,
                        onSourceClick = { activeSheet = SettingsSheet.SOURCE_LANG },
                        onTargetClick = { activeSheet = SettingsSheet.TARGET_LANG },
                    )
                }

                // 2. Thẻ trọng tâm duy nhất: Cấu hình động cho Bộ dịch đang dùng
                item {
                    ActiveProviderHeroCard(
                        palette = palette,
                        settings = settings,
                        hasApiKey = settings.provider in configuredKeys,
                        downloadingModel = downloadingModel,
                        partialModelBytes = partialModelBytes,
                        downloadProgress = downloadProgress,
                        onDownloadLlm = {
                            if (settings.localLlmModelUrl.isBlank()) {
                                activeSheet = SettingsSheet.LLM_URL
                            } else {
                                onDownloadLlmModel()
                            }
                        },
                        onEditLlmUrl = { activeSheet = SettingsSheet.LLM_URL },
                        onPickLlmModel = onPickLlmModel,
                        onConfigureApiKey = { activeSheet = SettingsSheet.API_KEY },
                        onTestApiKey = {
                            val key = apiKeyFor(settings.provider)
                            if (key.isBlank()) {
                                activeSheet = SettingsSheet.API_KEY
                            } else {
                                onTestKey(settings.provider, key)
                            }
                        },
                        onTestFreeSources = onTestFree,
                        onConfigureLibre = { activeSheet = SettingsSheet.LIBRE_SERVER },
                        onChangeProvider = { activeSheet = SettingsSheet.PROVIDER },
                    )
                }

                // 3. Động cơ & nhận diện
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSectionLabel("Động cơ & nhận diện", palette)
                        MinimalSettingsCard(palette) {
                            MinimalSettingRow(
                                palette = palette,
                                icon = Icons.Outlined.AutoAwesome,
                                title = "Đổi bộ dịch",
                                value = settings.provider.shortLabel(),
                                boxedIcon = true,
                                onClick = { activeSheet = SettingsSheet.PROVIDER },
                            )
                            CardHairlineDivider(palette)
                            MinimalSettingRow(
                                palette = palette,
                                icon = Icons.Outlined.DocumentScanner,
                                title = "Nhận diện chữ (OCR)",
                                value = settings.ocrProvider.compactLabel(),
                                boxedIcon = true,
                                onClick = { activeSheet = SettingsSheet.OCR },
                            )
                        }
                    }
                }

                // 4. Trình bày & nâng cao
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSectionLabel("Trình bày & nâng cao", palette)
                        MinimalSettingsCard(palette) {
                            MinimalSettingRow(
                                palette = palette,
                                icon = Icons.Outlined.TextFields,
                                title = "Phông chữ & độ đậm",
                                value = settings.fontMode.label(),
                                boxedIcon = false,
                                onClick = { activeSheet = SettingsSheet.TYPOGRAPHY },
                            )
                            CardHairlineDivider(palette)
                            MinimalToggleRow(
                                palette = palette,
                                icon = Icons.Outlined.FormatSize,
                                title = "Tự cân cỡ chữ",
                                checked = settings.autoSizeFromOriginal,
                                onCheckedChange = { enabled ->
                                    onUpdate { it.copy(autoSizeFromOriginal = enabled) }
                                },
                            )
                            CardHairlineDivider(palette)
                            MinimalSettingRow(
                                palette = palette,
                                icon = Icons.Outlined.Tune,
                                title = "Tinh chỉnh nâng cao",
                                subtitle = "Cỡ chữ, xoá chữ gốc, lọc SFX",
                                boxedIcon = false,
                                onClick = { activeSheet = SettingsSheet.ADVANCED },
                            )
                            CardHairlineDivider(palette)
                            MinimalSettingRow(
                                palette = palette,
                                icon = Icons.Outlined.Info,
                                title = "Về ứng dụng",
                                value = "v1.1.3",
                                boxedIcon = false,
                                onClick = { activeSheet = SettingsSheet.ABOUT },
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Các BottomSheet gọn gàng cho từng nhóm cài đặt ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    activeSheet?.let { sheet ->
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            containerColor = palette.sheetBg,
            contentColor = palette.textPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(palette.textSecondary.copy(alpha = 0.35f)),
                )
            },
        ) {
            when (sheet) {
                SettingsSheet.SOURCE_LANG -> SelectionListSheet(
                    palette = palette,
                    title = "Chữ trên ảnh",
                    options = SourceScript.entries.map { it to it.label },
                    selected = settings.sourceScript,
                    onSelect = { script ->
                        onUpdate { it.copy(sourceScript = script) }
                        activeSheet = null
                    },
                    onClose = { activeSheet = null },
                )

                SettingsSheet.TARGET_LANG -> SelectionListSheet(
                    palette = palette,
                    title = "Dịch sang ngôn ngữ",
                    options = TranslationLanguage.entries.map { it to it.label },
                    selected = settings.target,
                    onSelect = { lang ->
                        onUpdate { it.copy(target = lang) }
                        activeSheet = null
                    },
                    onClose = { activeSheet = null },
                )

                SettingsSheet.PROVIDER -> ProviderPickerSheet(
                    palette = palette,
                    selected = settings.provider,
                    configuredKeys = configuredKeys,
                    onSelect = { provider ->
                        onUpdate { it.copy(provider = provider) }
                        activeSheet = null
                    },
                    onClose = { activeSheet = null },
                )

                SettingsSheet.OCR -> OcrPickerSheet(
                    palette = palette,
                    settings = settings,
                    onUpdate = onUpdate,
                    onTestOcr = onTestOcr,
                    onClose = { activeSheet = null },
                )

                SettingsSheet.LLM_URL -> LlmUrlSheet(
                    palette = palette,
                    currentUrl = settings.localLlmModelUrl,
                    downloading = downloadingModel,
                    partialBytes = partialModelBytes,
                    onSaveUrl = { url -> onUpdate { it.copy(localLlmModelUrl = url) } },
                    onSaveAndDownload = { url ->
                        onUpdate { it.copy(localLlmModelUrl = url) }
                        activeSheet = null
                        onDownloadLlmModel()
                    },
                    onClose = { activeSheet = null },
                )

                SettingsSheet.API_KEY -> ApiKeySheet(
                    palette = palette,
                    settings = settings,
                    hasKey = settings.provider in configuredKeys,
                    currentKey = apiKeyFor(settings.provider),
                    onSave = { key ->
                        onSaveKey(settings.provider, key)
                        activeSheet = null
                    },
                    onTest = { key -> onTestKey(settings.provider, key) },
                    onUpdate = onUpdate,
                    onClose = { activeSheet = null },
                )

                SettingsSheet.LIBRE_SERVER -> LibreServerSheet(
                    palette = palette,
                    currentUrl = settings.libreTranslateUrl,
                    onSave = { url ->
                        onUpdate { it.copy(libreTranslateUrl = url) }
                        activeSheet = null
                    },
                    onTest = { url ->
                        onUpdate { it.copy(libreTranslateUrl = url) }
                        onTestFree()
                    },
                    onClose = { activeSheet = null },
                )

                SettingsSheet.TYPOGRAPHY -> TypographySheet(
                    palette = palette,
                    settings = settings,
                    onUpdate = onUpdate,
                    onPickFont = onPickFont,
                    onClearFont = onClearFont,
                    onClose = { activeSheet = null },
                )

                SettingsSheet.ADVANCED -> AdvancedTuningSheet(
                    palette = palette,
                    settings = settings,
                    onUpdate = onUpdate,
                    onClose = { activeSheet = null },
                )

                SettingsSheet.ABOUT -> AboutSheet(
                    palette = palette,
                    onClose = { activeSheet = null },
                )
            }
        }
    }
}

// ============================================================================
// TOP BAR & BENTO LANGUAGE SELECTOR
// ============================================================================

@Composable
private fun SettingsTopBar(
    palette: SettingsPalette,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.iconTileBg)
                .border(1.dp, palette.cardBorder, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Quay lại",
                tint = palette.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = "Cài đặt",
            color = palette.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BentoLanguageSelector(
    palette: SettingsPalette,
    sourceLabel: String,
    targetLabel: String,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BentoLangTile(
                palette = palette,
                icon = Icons.Outlined.Image,
                caption = "Chữ trên ảnh",
                value = sourceLabel,
                endExtraPadding = 16.dp,
                startExtraPadding = 0.dp,
                onClick = onSourceClick,
                modifier = Modifier.weight(1f),
            )
            BentoLangTile(
                palette = palette,
                icon = Icons.Outlined.Translate,
                caption = "Dịch sang",
                value = targetLabel,
                endExtraPadding = 0.dp,
                startExtraPadding = 16.dp,
                onClick = onTargetClick,
                modifier = Modifier.weight(1f),
            )
        }

        // Nút mũi tên tròn chuyển sắc đặt giữa 2 thẻ Bento
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(palette.accentPurple, palette.accentPink)),
                )
                .border(2.5.dp, palette.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BentoLangTile(
    palette: SettingsPalette,
    icon: ImageVector,
    caption: String,
    value: String,
    startExtraPadding: androidx.compose.ui.unit.Dp,
    endExtraPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(palette.cardBg)
            .border(1.dp, palette.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(
                start = 14.dp + startExtraPadding,
                end = 14.dp + endExtraPadding,
                top = 14.dp,
                bottom = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = caption,
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = palette.textPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ============================================================================
// FOCAL ACTIVE PROVIDER CARD (ĐIỂM NHẤN CHÍNH CỦA MÀN HÌNH)
// ============================================================================

@Composable
private fun ActiveProviderHeroCard(
    palette: SettingsPalette,
    settings: AppSettings,
    hasApiKey: Boolean,
    downloadingModel: Boolean,
    partialModelBytes: Long,
    downloadProgress: DownloadProgress,
    onDownloadLlm: () -> Unit,
    onEditLlmUrl: () -> Unit,
    onPickLlmModel: () -> Unit,
    onConfigureApiKey: () -> Unit,
    onTestApiKey: () -> Unit,
    onTestFreeSources: () -> Unit,
    onConfigureLibre: () -> Unit,
    onChangeProvider: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.heroStart, palette.heroMid, palette.heroEnd),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, 360f),
                ),
            )
            .border(1.dp, palette.heroBorder, shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            when {
                settings.provider == TranslationProviderType.LOCAL_LLM -> {
                    val modelFileLabel = settings.localLlmModelName.ifBlank {
                        settings.localLlmModelUrl
                            .substringAfterLast('/')
                            .substringBefore('?')
                            .ifBlank { "Chưa chọn file .gguf" }
                    }
                    val rightMetric = when {
                        downloadingModel && downloadProgress.total > 0 ->
                            "${formatMegabytes(downloadProgress.bytes)} / ${formatMegabytes(downloadProgress.total)}"
                        downloadingModel -> formatMegabytes(downloadProgress.bytes)
                        partialModelBytes > 0 -> formatMegabytes(partialModelBytes)
                        settings.localLlmModelPath.isNotBlank() -> "Sẵn sàng"
                        else -> "Offline"
                    }
                    val barProgress = when {
                        downloadingModel -> downloadProgress.fraction.coerceIn(0.05f, 1f)
                        partialModelBytes > 0 -> 0.55f
                        settings.localLlmModelPath.isNotBlank() -> 1f
                        else -> 0.0f
                    }

                    HeroCardHeader(
                        palette = palette,
                        icon = Icons.Outlined.Memory,
                        title = "LLM trên máy (GGUF)",
                        badgeText = if (settings.localLlmModelPath.isNotBlank()) "Đang chọn" else "Cần tải",
                        badgePositive = settings.localLlmModelPath.isNotBlank(),
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onEditLlmUrl)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = modelFileLabel,
                            color = Color.White.copy(alpha = 0.80f),
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = rightMetric,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { barProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                        color = Color(0xFFC084FC),
                        trackColor = Color.White.copy(alpha = 0.14f),
                        strokeCap = StrokeCap.Round,
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HeroPrimaryButton(
                            palette = palette,
                            text = when {
                                downloadingModel -> "Đang tải…"
                                partialModelBytes > 0 -> "Tải tiếp"
                                settings.localLlmModelUrl.isBlank() -> "Nhập link tải"
                                else -> "Tải mô hình"
                            },
                            enabled = !downloadingModel,
                            onClick = onDownloadLlm,
                            modifier = Modifier.weight(1f),
                        )
                        HeroGlassButton(
                            palette = palette,
                            text = if (settings.localLlmModelPath.isBlank()) "Chọn file" else "Đổi mô hình",
                            onClick = onPickLlmModel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                settings.provider.requiresApiKey -> {
                    HeroCardHeader(
                        palette = palette,
                        icon = Icons.Outlined.Key,
                        title = settings.provider.label,
                        badgeText = if (hasApiKey) "Đã lưu key" else "Cần API key",
                        badgePositive = hasApiKey,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when (settings.provider) {
                            TranslationProviderType.OPENAI_COMPATIBLE ->
                                "Model: ${settings.openAiModel} · ${settings.openAiBaseUrl}"
                            TranslationProviderType.GEMINI ->
                                "Model: ${settings.geminiModel}"
                            TranslationProviderType.DEEPL ->
                                if (settings.deeplPro) "Đang dùng gói DeepL Pro" else "Đang dùng gói DeepL Free"
                            else -> settings.provider.hint
                        },
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HeroPrimaryButton(
                            palette = palette,
                            text = if (hasApiKey) "Sửa API key" else "Nhập API key",
                            onClick = onConfigureApiKey,
                            modifier = Modifier.weight(1f),
                        )
                        HeroGlassButton(
                            palette = palette,
                            text = "Kiểm tra",
                            onClick = onTestApiKey,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                else -> {
                    HeroCardHeader(
                        palette = palette,
                        icon = Icons.Outlined.Public,
                        title = settings.provider.label,
                        badgeText = "Miễn phí",
                        badgePositive = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = settings.provider.hint,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when {
                            settings.provider == TranslationProviderType.LIBRE_TRANSLATE -> {
                                HeroPrimaryButton(
                                    palette = palette,
                                    text = "Địa chỉ server",
                                    onClick = onConfigureLibre,
                                    modifier = Modifier.weight(1f),
                                )
                                HeroGlassButton(
                                    palette = palette,
                                    text = "Kiểm tra",
                                    onClick = onTestFreeSources,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            settings.provider.isBuiltInFree -> {
                                HeroPrimaryButton(
                                    palette = palette,
                                    text = "Kiểm tra nguồn",
                                    onClick = onTestFreeSources,
                                    modifier = Modifier.weight(1f),
                                )
                                HeroGlassButton(
                                    palette = palette,
                                    text = "Đổi bộ dịch",
                                    onClick = onChangeProvider,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            else -> {
                                HeroPrimaryButton(
                                    palette = palette,
                                    text = "Đổi bộ dịch",
                                    onClick = onChangeProvider,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCardHeader(
    palette: SettingsPalette,
    icon: ImageVector,
    title: String,
    badgeText: String,
    badgePositive: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFE9D5FF),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = if (badgePositive) palette.successBg else palette.warningBg,
        ) {
            Text(
                text = badgeText,
                color = if (badgePositive) palette.successText else palette.warningText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun HeroPrimaryButton(
    palette: SettingsPalette,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.heroPrimaryBg,
            contentColor = palette.heroPrimaryText,
            disabledContainerColor = palette.heroPrimaryBg.copy(alpha = 0.45f),
            disabledContentColor = palette.heroPrimaryText.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroGlassButton(
    palette: SettingsPalette,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, palette.heroSecondaryBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = palette.heroSecondaryBg,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ============================================================================
// MINIMAL GROUP CARDS & ROWS
// ============================================================================

@Composable
private fun SettingsSectionLabel(text: String, palette: SettingsPalette) {
    Text(
        text = text,
        color = palette.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

@Composable
private fun MinimalSettingsCard(
    palette: SettingsPalette,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.cardBg)
            .border(1.dp, palette.cardBorder, shape),
        content = content,
    )
}

@Composable
private fun CardHairlineDivider(palette: SettingsPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(palette.divider),
    )
}

@Composable
private fun MinimalSettingRow(
    palette: SettingsPalette,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    boxedIcon: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (boxedIcon) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.iconTileBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(14.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!value.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                color = palette.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MinimalToggleRow(
    palette: SettingsPalette,
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFDF2F8),
                checkedTrackColor = palette.accentPurple,
                uncheckedThumbColor = palette.textSecondary,
                uncheckedTrackColor = palette.iconTileBg,
                uncheckedBorderColor = palette.cardBorder,
            ),
        )
    }
}

// ============================================================================
// BOTTOM SHEETS
// ============================================================================

@Composable
private fun SheetHeader(
    palette: SettingsPalette,
    title: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Đóng",
                tint = palette.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun <T> SelectionListSheet(
    palette: SettingsPalette,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        SheetHeader(palette, title, onClose)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val shape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            if (isSelected) palette.accentPurple.copy(alpha = 0.16f) else palette.cardBg,
                        )
                        .border(
                            1.dp,
                            if (isSelected) palette.accentPurple.copy(alpha = 0.6f) else palette.cardBorder,
                            shape,
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = palette.accentPurple,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderPickerSheet(
    palette: SettingsPalette,
    selected: TranslationProviderType,
    configuredKeys: Set<TranslationProviderType>,
    onSelect: (TranslationProviderType) -> Unit,
    onClose: () -> Unit,
) {
    val onDeviceProviders = listOf(
        TranslationProviderType.LOCAL_LLM,
        TranslationProviderType.ON_DEVICE,
    )
    val freeOnlineProviders = listOf(
        TranslationProviderType.FREE_AUTO,
        TranslationProviderType.GOOGLE_FREE,
        TranslationProviderType.MYMEMORY,
        TranslationProviderType.LIBRE_TRANSLATE,
    )
    val cloudKeyProviders = listOf(
        TranslationProviderType.GEMINI,
        TranslationProviderType.OPENAI_COMPATIBLE,
        TranslationProviderType.DEEPL,
        TranslationProviderType.GOOGLE_CLOUD,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(palette, "Chọn bộ dịch", onClose)

        ProviderGroup(
            palette = palette,
            header = "Chạy trên máy (Offline)",
            providers = onDeviceProviders,
            selected = selected,
            configuredKeys = configuredKeys,
            onSelect = onSelect,
        )
        ProviderGroup(
            palette = palette,
            header = "Miễn phí (Online)",
            providers = freeOnlineProviders,
            selected = selected,
            configuredKeys = configuredKeys,
            onSelect = onSelect,
        )
        ProviderGroup(
            palette = palette,
            header = "API Key (Chất lượng cao)",
            providers = cloudKeyProviders,
            selected = selected,
            configuredKeys = configuredKeys,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun ProviderGroup(
    palette: SettingsPalette,
    header: String,
    providers: List<TranslationProviderType>,
    selected: TranslationProviderType,
    configuredKeys: Set<TranslationProviderType>,
    onSelect: (TranslationProviderType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionLabel(header, palette)
        providers.forEach { provider ->
            val isSelected = provider == selected
            val shape = RoundedCornerShape(16.dp)
            val badgeText = when {
                !provider.requiresApiKey -> "Miễn phí"
                provider in configuredKeys -> "Đã có key"
                else -> "Cần key"
            }
            val badgePositive = !provider.requiresApiKey || provider in configuredKeys

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        if (isSelected) palette.accentPurple.copy(alpha = 0.16f) else palette.cardBg,
                    )
                    .border(
                        1.dp,
                        if (isSelected) palette.accentPurple.copy(alpha = 0.65f) else palette.cardBorder,
                        shape,
                    )
                    .clickable { onSelect(provider) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.label,
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (badgePositive) palette.successBg else palette.warningBg,
                    ) {
                        Text(
                            text = badgeText,
                            color = if (badgePositive) palette.successText else palette.warningText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
                if (isSelected) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = provider.hint,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrPickerSheet(
    palette: SettingsPalette,
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onTestOcr: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetHeader(palette, "Nhận diện chữ (OCR)", onClose)

        OcrProviderType.entries.forEach { provider ->
            val isSelected = settings.ocrProvider == provider
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        if (isSelected) palette.accentPurple.copy(alpha = 0.16f) else palette.cardBg,
                    )
                    .border(
                        1.dp,
                        if (isSelected) palette.accentPurple.copy(alpha = 0.65f) else palette.cardBorder,
                        shape,
                    )
                    .clickable { onUpdate { it.copy(ocrProvider = provider) } }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.label,
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = palette.accentPurple,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = provider.hint,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        if (settings.ocrProvider == OcrProviderType.PADDLE_OCR) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = settings.paddleOcrUrl,
                onValueChange = { value -> onUpdate { it.copy(paddleOcrUrl = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Địa chỉ server PaddleOCR") },
                placeholder = { Text("http://192.168.1.10:8866") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = settings.paddleOcrToken,
                onValueChange = { value -> onUpdate { it.copy(paddleOcrToken = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Token (nếu cần)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Button(
                onClick = onTestOcr,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = CircleShape,
            ) {
                Text("Kiểm tra server", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LlmUrlSheet(
    palette: SettingsPalette,
    currentUrl: String,
    downloading: Boolean,
    partialBytes: Long,
    onSaveUrl: (String) -> Unit,
    onSaveAndDownload: (String) -> Unit,
    onClose: () -> Unit,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(palette, "Tải mô hình GGUF", onClose)
        Text(
            text = "Dán link trực tiếp tới file .gguf (khuyên dùng bản Q4_K_M 1–4 GB để chạy mượt trên điện thoại).",
            color = palette.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL mô hình") },
            placeholder = { Text("https://huggingface.co/.../model.Q4_K_M.gguf") },
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            singleLine = true,
            enabled = !downloading,
            shape = RoundedCornerShape(14.dp),
        )
        if (!downloading && partialBytes > 0) {
            Text(
                text = "Đã tải ${formatMegabytes(partialBytes)} từ lần trước — bấm Tải tiếp để nối tiếp.",
                color = palette.accentPurple,
                fontSize = 12.5.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onSaveUrl(url.trim())
                    onClose()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = CircleShape,
            ) {
                Text("Chỉ lưu link")
            }
            Button(
                onClick = { onSaveAndDownload(url.trim()) },
                enabled = !downloading && url.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = CircleShape,
            ) {
                Text(if (partialBytes > 0) "Tải tiếp" else "Tải ngay", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ApiKeySheet(
    palette: SettingsPalette,
    settings: AppSettings,
    hasKey: Boolean,
    currentKey: String,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    var key by remember(settings.provider, currentKey) { mutableStateOf(currentKey) }
    var visible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(palette, "Cấu hình ${settings.provider.label}", onClose)

        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            placeholder = { Text("Dán key cho ${settings.provider.label}") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            shape = RoundedCornerShape(14.dp),
        )

        if (settings.provider == TranslationProviderType.OPENAI_COMPATIBLE) {
            OutlinedTextField(
                value = settings.openAiBaseUrl,
                onValueChange = { value -> onUpdate { it.copy(openAiBaseUrl = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = settings.openAiModel,
                onValueChange = { value -> onUpdate { it.copy(openAiModel = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (settings.provider == TranslationProviderType.GEMINI) {
            OutlinedTextField(
                value = settings.geminiModel,
                onValueChange = { value -> onUpdate { it.copy(geminiModel = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (settings.provider == TranslationProviderType.DEEPL) {
            MinimalSettingsCard(palette) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdate { it.copy(deeplPro = !it.deeplPro) } }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Gói DeepL Pro", color = palette.textPrimary, fontSize = 15.sp)
                        Text("Tắt nếu đang dùng key miễn phí", color = palette.textSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.deeplPro,
                        onCheckedChange = { v -> onUpdate { it.copy(deeplPro = v) } },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { onSave(key) },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = CircleShape,
            ) {
                Text("Lưu", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { onTest(key) },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = CircleShape,
            ) {
                Text("Kiểm tra")
            }
        }
        if (hasKey) {
            TextButton(
                onClick = {
                    key = ""
                    onSave("")
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Xoá API key đã lưu", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LibreServerSheet(
    palette: SettingsPalette,
    currentUrl: String,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit,
    onClose: () -> Unit,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(palette, "Máy chủ LibreTranslate", onClose)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Địa chỉ server") },
            placeholder = { Text("http://192.168.1.10:5000") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Text(
            text = "docker run -p 5000:5000 libretranslate/libretranslate",
            color = palette.textSecondary,
            fontSize = 12.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { onSave(url.trim()) },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = CircleShape,
            ) {
                Text("Lưu", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { onTest(url.trim()) },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = CircleShape,
            ) {
                Text("Kiểm tra")
            }
        }
    }
}

@Composable
private fun TypographySheet(
    palette: SettingsPalette,
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
    onClose: () -> Unit,
) {
    val fontModes = listOf(
        FontMode.AUTO to "Tự động",
        FontMode.SANS to "Không chân",
        FontMode.SERIF to "Có chân",
        FontMode.CONDENSED to "Hẹp",
        FontMode.MONOSPACE to "Đều",
        FontMode.CUSTOM to "Font tải lên",
    )
    val weights = listOf(
        (null as FontWeightLevel?) to "Tự động",
        FontWeightLevel.THIN to "Mảnh",
        FontWeightLevel.REGULAR to "Thường",
        FontWeightLevel.MEDIUM to "Vừa",
        FontWeightLevel.BOLD to "Đậm",
        FontWeightLevel.BLACK to "Rất đậm",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHeader(palette, "Phông chữ & độ đậm", onClose)

        SettingsSectionLabel("Kiểu chữ", palette)
        ChipOptionGrid(
            palette = palette,
            options = fontModes,
            selected = settings.fontMode,
            onSelect = { mode -> onUpdate { it.copy(fontMode = mode) } },
        )

        if (settings.fontMode == FontMode.CUSTOM) {
            MinimalSettingsCard(palette) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Font tuỳ chỉnh (.ttf / .otf)", color = palette.textPrimary, fontSize = 14.5.sp)
                        Text(
                            text = settings.customFontName.ifBlank { "Chưa tải font nào" },
                            color = palette.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onPickFont) { Text("Chọn") }
                    if (settings.customFontName.isNotBlank()) {
                        TextButton(onClick = onClearFont) { Text("Xoá") }
                    }
                }
            }
        }

        SettingsSectionLabel("Độ đậm nét chữ", palette)
        ChipOptionGrid(
            palette = palette,
            options = weights,
            selected = settings.fontWeight,
            onSelect = { weight -> onUpdate { it.copy(fontWeight = weight) } },
        )
    }
}

@Composable
private fun <T> ChipOptionGrid(
    palette: SettingsPalette,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (value, label) ->
                    val isSelected = value == selected
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(
                                if (isSelected) palette.accentPurple.copy(alpha = 0.2f) else palette.cardBg,
                            )
                            .border(
                                1.dp,
                                if (isSelected) palette.accentPurple else palette.cardBorder,
                                shape,
                            )
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) palette.textPrimary else palette.textSecondary,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedTuningSheet(
    palette: SettingsPalette,
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHeader(palette, "Tinh chỉnh nâng cao", onClose)

        MinimalSettingsCard(palette) {
            SheetSliderRow(
                palette = palette,
                title = "Cỡ chữ",
                value = settings.textSizeScale,
                range = 0.6f..1.6f,
                label = "${(settings.textSizeScale * 100).toInt()}%",
                onChange = { v -> onUpdate { it.copy(textSizeScale = v) } },
            )
            CardHairlineDivider(palette)
            SheetSliderRow(
                palette = palette,
                title = "Mở rộng khung chữ",
                value = settings.boxExpansion,
                range = 1f..1.5f,
                label = "${(settings.boxExpansion * 100).toInt()}%",
                onChange = { v -> onUpdate { it.copy(boxExpansion = v) } },
            )
            CardHairlineDivider(palette)
            SheetSliderRow(
                palette = palette,
                title = "Viền chữ",
                value = settings.strokeWidthFactor,
                range = 0f..0.2f,
                label = "${(settings.strokeWidthFactor * 100).toInt()}%",
                onChange = { v -> onUpdate { it.copy(strokeWidthFactor = v) } },
            )
        }

        MinimalSettingsCard(palette) {
            SheetToggleRow(
                palette = palette,
                title = "Tự chỉnh màu chữ",
                subtitle = "Nền tối tự chuyển sang chữ sáng",
                checked = settings.autoContrast,
                onCheckedChange = { v -> onUpdate { it.copy(autoContrast = v) } },
            )
            CardHairlineDivider(palette)
            SheetToggleRow(
                palette = palette,
                title = "Khớp màu nền",
                subtitle = "Vá chỗ chữ gốc theo màu bong bóng thoại",
                checked = settings.matchBackgroundColor,
                onCheckedChange = { v -> onUpdate { it.copy(matchBackgroundColor = v) } },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSectionLabel("Xoá chữ gốc", palette)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    EraseMode.AUTO to "Tự động",
                    EraseMode.SOLID to "Tô nền",
                    EraseMode.DIFFUSION to "Lan màu",
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.eraseMode == mode,
                        onClick = { onUpdate { it.copy(eraseMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSectionLabel("Bộ lọc hiệu ứng âm thanh (SFX)", palette)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val levels = listOf(
                    SfxSensitivity.OFF to "Tắt",
                    SfxSensitivity.LOW to "Thấp",
                    SfxSensitivity.MEDIUM to "Vừa",
                    SfxSensitivity.HIGH to "Cao",
                )
                levels.forEachIndexed { index, (level, label) ->
                    SegmentedButton(
                        selected = settings.sfxSensitivity == level,
                        onClick = { onUpdate { it.copy(sfxSensitivity = level) } },
                        shape = SegmentedButtonDefaults.itemShape(index, levels.size),
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSectionLabel("Độ phân giải xử lý", palette)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val resolutions = listOf(
                    10_000_000 to "10 MP",
                    24_000_000 to "24 MP",
                    40_000_000 to "40 MP",
                )
                resolutions.forEachIndexed { index, (pixels, label) ->
                    SegmentedButton(
                        selected = settings.maxPagePixels == pixels,
                        onClick = { onUpdate { it.copy(maxPagePixels = pixels) } },
                        shape = SegmentedButtonDefaults.itemShape(index, resolutions.size),
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetSliderRow(
    palette: SettingsPalette,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = palette.textPrimary, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
            Text(label, color = palette.accentPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SheetToggleRow(
    palette: SettingsPalette,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = palette.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFDF2F8),
                checkedTrackColor = palette.accentPurple,
            ),
        )
    }
}

@Composable
private fun AboutSheet(
    palette: SettingsPalette,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetHeader(palette, "Về ứng dụng", onClose)
        MinimalSettingsCard(palette) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Manhwa Translate v1.1.3",
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Nhận diện chữ bằng ML Kit ngay trên máy. Khi dùng bộ dịch đám mây, chỉ văn bản thoại được gửi đi — hình ảnh truyện luôn nằm trên thiết bị của bạn.",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

// ============================================================================
// FORMATTERS & LABELS
// ============================================================================

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

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

private fun OcrProviderType.compactLabel(): String = when (this) {
    OcrProviderType.ML_KIT -> "ML Kit"
    OcrProviderType.PADDLE_OCR -> "PaddleOCR"
}

private fun FontMode.label(): String = when (this) {
    FontMode.AUTO -> "Tự động"
    FontMode.SANS -> "Không chân"
    FontMode.SERIF -> "Có chân"
    FontMode.CONDENSED -> "Hẹp"
    FontMode.MONOSPACE -> "Đều"
    FontMode.CUSTOM -> "Font tải lên"
}
