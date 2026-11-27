package com.manhwatranslate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.manhwatranslate.ui.components.AppTopBar
import com.manhwatranslate.ui.components.ExpandableGroup
import com.manhwatranslate.ui.components.GroupCard
import com.manhwatranslate.ui.components.Pill
import com.manhwatranslate.ui.components.RowDivider
import com.manhwatranslate.ui.components.SectionTitle
import com.manhwatranslate.ui.components.ToggleRow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPickFont: () -> Unit,
    onPickLlmModel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val settings = state.settings

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    SettingsContent(
        settings = settings,
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
        onClearFont = { viewModel.clearFont() },
        onBack = onBack,
    )
}

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
    onClearFont: () -> Unit,
    onBack: () -> Unit,
) {
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(title = "Cài đặt", onBack = onBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle("Ngôn ngữ", modifier = Modifier.animateItem()) }
            item {
                GroupCard(modifier = Modifier.animateItem()) {
                    DropdownRow(
                        title = "Chữ trên ảnh",
                        value = settings.sourceScript.displayLabel(),
                        options = SourceScript.entries.map { it to it.label },
                        onSelect = { script -> onUpdate { it.copy(sourceScript = script) } },
                    )
                    RowDivider()
                    DropdownRow(
                        title = "Dịch sang",
                        value = settings.target.label,
                        options = TranslationLanguage.entries.map { it to it.label },
                        onSelect = { language -> onUpdate { it.copy(target = language) } },
                    )
                }
            }

            item { SectionTitle("Nhận diện chữ", modifier = Modifier.animateItem()) }
            item {
                GroupCard(modifier = Modifier.animateItem()) {
                    Column(Modifier.selectableGroup()) {
                        OcrProviderType.entries.forEachIndexed { index, provider ->
                            if (index > 0) RowDivider()
                            ChoiceRow(
                                title = provider.shortLabel(),
                                description = provider.description(),
                                selected = settings.ocrProvider == provider,
                                onSelect = { onUpdate { it.copy(ocrProvider = provider) } },
                            )
                        }
                    }
                    if (settings.ocrProvider == OcrProviderType.PADDLE_OCR) {
                        RowDivider()
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            OutlinedTextField(
                                value = settings.paddleOcrUrl,
                                onValueChange = { value ->
                                    onUpdate { it.copy(paddleOcrUrl = value) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Địa chỉ server") },
                                placeholder = { Text("http://192.168.1.10:8866") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = settings.paddleOcrToken,
                                onValueChange = { value ->
                                    onUpdate { it.copy(paddleOcrToken = value) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Token (nếu cần)") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onTestOcr,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("Kiểm tra server")
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Bộ dịch", modifier = Modifier.animateItem()) }
            item {
                GroupCard(modifier = Modifier.animateItem()) {
                    Column(Modifier.selectableGroup()) {
                        TranslationProviderType.entries.forEachIndexed { index, provider ->
                            if (index > 0) RowDivider()
                            ChoiceRow(
                                title = provider.label,
                                hint = provider.hint,
                                selected = settings.provider == provider,
                                onSelect = { onUpdate { it.copy(provider = provider) } },
                                badge = when {
                                    !provider.requiresApiKey -> "Miễn phí"
                                    provider !in configuredKeys -> "Cần key"
                                    else -> null
                                },
                            )
                        }
                    }
                }
            }

            if (settings.provider.requiresApiKey) {
                item {
                    ApiKeyCard(
                        modifier = Modifier.animateItem(),
                        settings = settings,
                        hasKey = settings.provider in configuredKeys,
                        currentKey = apiKeyFor(settings.provider),
                        onTest = { key -> onTestKey(settings.provider, key) },
                        onSave = { key -> onSaveKey(settings.provider, key) },
                        onUpdate = onUpdate,
                    )
                }
            } else if (settings.provider.isBuiltInFree) {
                item {
                    FreeSourcesCard(
                        settings = settings,
                        onTest = onTestFree,
                        onUpdate = onUpdate,
                    )
                }
            }

            if (settings.provider == TranslationProviderType.LOCAL_LLM) {
                item {
                    GroupCard {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = "Mô hình GGUF",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (settings.localLlmModelName.isNotBlank()) {
                                    settings.localLlmModelName
                                } else {
                                    "Chưa chọn mô hình. Dùng checkpoint uncensored (Dolphin, WizardLM…) bản Q4_K_M để chạy mượt."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onPickLlmModel, modifier = Modifier.fillMaxWidth()) {
                                Text(if (settings.localLlmModelPath.isBlank()) "Chọn file mô hình" else "Đổi mô hình")
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Trình bày bản dịch", modifier = Modifier.animateItem()) }
            item {
                GroupCard(modifier = Modifier.animateItem()) {
                    DropdownRow(
                        title = "Phông chữ",
                        value = settings.fontMode.label(),
                        options = listOf(
                            FontMode.AUTO to "Tự động theo bản gốc",
                            FontMode.SANS to "Không chân (Sans)",
                            FontMode.SERIF to "Có chân (Serif)",
                            FontMode.CONDENSED to "Hẹp (Condensed)",
                            FontMode.MONOSPACE to "Đều (Mono)",
                            FontMode.CUSTOM to "Font đã tải lên",
                        ),
                        onSelect = { mode -> onUpdate { it.copy(fontMode = mode) } },
                    )
                    RowDivider()
                    DropdownRow(
                        title = "Độ đậm",
                        value = settings.fontWeight?.label() ?: "Tự động",
                        options = listOf(
                            (null as FontWeightLevel?) to "Tự động",
                            FontWeightLevel.THIN to "Mảnh",
                            FontWeightLevel.REGULAR to "Thường",
                            FontWeightLevel.MEDIUM to "Vừa",
                            FontWeightLevel.BOLD to "Đậm",
                            FontWeightLevel.BLACK to "Rất đậm",
                        ),
                        onSelect = { weight -> onUpdate { it.copy(fontWeight = weight) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = "Tự cân cỡ chữ",
                        subtitle = "Co chữ cho vừa khung gốc",
                        checked = settings.autoSizeFromOriginal,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(autoSizeFromOriginal = value) }
                        },
                    )
                    if (settings.fontMode == FontMode.CUSTOM) {
                        RowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Font tuỳ chỉnh", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = settings.customFontName.ifBlank { "Chưa tải font nào" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            }

            item {
                ExpandableGroup(
                    modifier = Modifier.animateItem(),
                    title = "Tinh chỉnh",
                    subtitle = "Cỡ chữ, xoá chữ gốc, bộ lọc quét",
                    expanded = advancedOpen,
                    onToggle = { advancedOpen = !advancedOpen },
                ) {
                    SliderRow(
                        title = "Cỡ chữ",
                        value = settings.textSizeScale,
                        range = 0.6f..1.6f,
                        label = "${(settings.textSizeScale * 100).toInt()}%",
                        onChange = { value -> onUpdate { it.copy(textSizeScale = value) } },
                    )
                    RowDivider()
                    SliderRow(
                        title = "Mở rộng khung chữ",
                        value = settings.boxExpansion,
                        range = 1f..1.5f,
                        label = "${(settings.boxExpansion * 100).toInt()}%",
                        onChange = { value -> onUpdate { it.copy(boxExpansion = value) } },
                    )
                    RowDivider()
                    SliderRow(
                        title = "Viền chữ",
                        value = settings.strokeWidthFactor,
                        range = 0f..0.2f,
                        label = "${(settings.strokeWidthFactor * 100).toInt()}%",
                        onChange = { value -> onUpdate { it.copy(strokeWidthFactor = value) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = "Tự chỉnh màu chữ",
                        subtitle = "Nền tối dùng chữ sáng",
                        checked = settings.autoContrast,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(autoContrast = value) }
                        },
                    )
                    RowDivider()
                    LabeledBlock("Xoá chữ gốc") {
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
                                    label = {
                                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                )
                            }
                        }
                    }
                    ToggleRow(
                        title = "Khớp màu nền",
                        subtitle = "Vá chỗ chữ bằng màu ước lượng từ bong bóng",
                        checked = settings.matchBackgroundColor,
                        onCheckedChange = { value ->
                            onUpdate { it.copy(matchBackgroundColor = value) }
                        },
                    )
                    RowDivider()
                    LabeledBlock("Bộ lọc hiệu ứng âm thanh") {
                        Text(
                            text = "Bỏ qua chữ như CRASH!, BAM… vẽ đè lên hình.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
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
                                    label = {
                                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                )
                            }
                        }
                    }
                    RowDivider()
                    DropdownRow(
                        title = "Độ phân giải xử lý",
                        value = "${settings.maxPagePixels / 1_000_000} MP",
                        options = listOf(
                            10_000_000 to "10 MP (nhẹ)",
                            24_000_000 to "24 MP (cân bằng)",
                            40_000_000 to "40 MP (nét, tốn RAM)",
                        ),
                        onSelect = { value -> onUpdate { it.copy(maxPagePixels = value) } },
                    )
                }
            }

            item {
                ExpandableGroup(
                    modifier = Modifier.animateItem(),
                    title = "Về ứng dụng",
                    subtitle = "Phiên bản 1.0.0 · quyền riêng tư",
                    expanded = aboutOpen,
                    onToggle = { aboutOpen = !aboutOpen },
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Manhwa Translate 1.0.0",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Nhận diện chữ bằng ML Kit ngay trên máy. Với nhà cung cấp đám mây, " +
                                "chỉ văn bản được gửi đi, ảnh không rời thiết bị.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    description: String? = null,
    hint: String? = null,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val secondary = if (selected) hint ?: description else description
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Pill(text = badge, maxWidth = 76.dp)
        }
    }
}

@Composable
private fun <T> DropdownRow(
    title: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionValue, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        expanded = false
                        onSelect(optionValue)
                    },
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

@Composable
private fun LabeledBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun ApiKeyCard(
    modifier: Modifier = Modifier,
    settings: AppSettings,
    hasKey: Boolean,
    currentKey: String,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    var key by remember(settings.provider, currentKey) { mutableStateOf(currentKey) }
    var visible by remember { mutableStateOf(false) }

    GroupCard(modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Kết nối",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(text = if (hasKey) "Đã lưu" else "Chưa có", positive = hasKey)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { Text("Dán key cho ${settings.provider.label}") },
                singleLine = true,
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
            )

            if (settings.provider == TranslationProviderType.OPENAI_COMPATIBLE) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.openAiBaseUrl,
                    onValueChange = { value -> onUpdate { it.copy(openAiBaseUrl = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.openAiModel,
                    onValueChange = { value -> onUpdate { it.copy(openAiModel = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
            }

            if (settings.provider == TranslationProviderType.GEMINI) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.geminiModel,
                    onValueChange = { value -> onUpdate { it.copy(geminiModel = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
            }

            if (settings.provider == TranslationProviderType.DEEPL) {
                ToggleRow(
                    title = "Gói DeepL Pro",
                    subtitle = "Tắt nếu đang dùng key miễn phí",
                    checked = settings.deeplPro,
                    onCheckedChange = { value -> onUpdate { it.copy(deeplPro = value) } },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(key) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Lưu")
                }
                OutlinedButton(
                    onClick = { onTest(key) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kiểm tra")
                }
                if (hasKey) {
                    OutlinedButton(
                        onClick = {
                            key = ""
                            onSave("")
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("Xoá")
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeSourcesCard(
    settings: AppSettings,
    onTest: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    GroupCard {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Nguồn miễn phí",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(text = "Không cần key", positive = true)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Bộ dịch tự đổi nguồn khi một máy chủ bị chặn hoặc hết hạn mức; " +
                    "ML Kit trên máy là chốt cuối nên trang vẫn dịch được khi mạng có sự cố.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.provider == TranslationProviderType.LIBRE_TRANSLATE) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.libreTranslateUrl,
                    onValueChange = { value -> onUpdate { it.copy(libreTranslateUrl = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Địa chỉ server") },
                    placeholder = { Text("http://192.168.1.10:5000") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "docker run -p 5000:5000 libretranslate/libretranslate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onTest,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kiểm tra các nguồn")
            }
        }
    }
}

private fun SourceScript.displayLabel(): String = when (this) {
    SourceScript.AUTO -> "Tự động"
    SourceScript.LATIN -> "Latinh"
    SourceScript.KOREAN -> "Tiếng Hàn"
    SourceScript.CHINESE -> "Tiếng Trung"
    SourceScript.JAPANESE -> "Tiếng Nhật"
}

private fun OcrProviderType.shortLabel(): String = when (this) {
    OcrProviderType.ML_KIT -> "ML Kit trên máy"
    OcrProviderType.PADDLE_OCR -> "PaddleOCR (server)"
}

private fun OcrProviderType.description(): String = when (this) {
    OcrProviderType.ML_KIT -> "Chạy offline, không cần server"
    OcrProviderType.PADDLE_OCR -> "Server riêng, bắt chữ tốt hơn"
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (positive) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun FontMode.label(): String = when (this) {
    FontMode.AUTO -> "Tự động"
    FontMode.SANS -> "Không chân"
    FontMode.SERIF -> "Có chân"
    FontMode.CONDENSED -> "Hẹp"
    FontMode.MONOSPACE -> "Đều"
    FontMode.CUSTOM -> "Font tải lên"
}

private fun FontWeightLevel.label(): String = when (this) {
    FontWeightLevel.THIN -> "Mảnh"
    FontWeightLevel.REGULAR -> "Thường"
    FontWeightLevel.MEDIUM -> "Vừa"
    FontWeightLevel.BOLD -> "Đậm"
    FontWeightLevel.BLACK -> "Rất đậm"
}
