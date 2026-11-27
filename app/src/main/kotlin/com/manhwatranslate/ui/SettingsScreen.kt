package com.manhwatranslate.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.layout.FontMode
import com.manhwatranslate.core.layout.FontWeightLevel
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.scan.SfxSensitivity
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.model.TranslationProviderType
import com.manhwatranslate.ui.components.Divider
import com.manhwatranslate.ui.components.SectionHeader
import com.manhwatranslate.ui.components.SettingsCard
import com.manhwatranslate.ui.components.SettingRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPickFont: () -> Unit,
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { SectionHeader("Ngôn ngữ") }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DropdownSetting(
                        title = "Chữ trên ảnh",
                        subtitle = "Chọn tay khi truyện chỉ có một ngôn ngữ để nhận diện nhanh hơn",
                        value = settings.sourceScript.label,
                        icon = Icons.Outlined.Language,
                        options = SourceScript.entries.map { it to it.label },
                        onSelect = { script -> viewModel.update { it.copy(sourceScript = script) } },
                    )
                    Divider()
                    DropdownSetting(
                        title = "Dịch sang",
                        subtitle = null,
                        value = settings.target.label,
                        icon = Icons.Outlined.Language,
                        options = TranslationLanguage.entries.map { it to it.label },
                        onSelect = { language -> viewModel.update { it.copy(target = language) } },
                    )
                }
            }

            item { SectionHeader("Nhà cung cấp dịch", modifier = Modifier.padding(top = 8.dp)) }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TranslationProviderType.entries.forEach { provider ->
                        ProviderRow(
                            provider = provider,
                            selected = settings.provider == provider,
                            configured = !provider.requiresApiKey || provider in state.configuredKeys,
                            onSelect = { viewModel.update { it.copy(provider = provider) } },
                        )
                    }
                }
            }

            if (settings.provider.requiresApiKey) {
                item {
                    ApiKeyCard(
                        settings = settings,
                        hasKey = settings.provider in state.configuredKeys,
                        currentKey = viewModel.apiKey(settings.provider),
                        onSave = { key -> viewModel.saveApiKey(settings.provider, key) },
                        onUpdate = viewModel::update,
                    )
                }
            }

            item { SectionHeader("Bản dịch", modifier = Modifier.padding(top = 8.dp)) }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DropdownSetting(
                        title = "Phông chữ",
                        subtitle = "Tự động chọn theo nét chữ gốc trong ảnh",
                        value = settings.fontMode.label(),
                        icon = Icons.Outlined.TextFields,
                        options = listOf(
                            FontMode.AUTO to "Tự động theo bản gốc",
                            FontMode.SANS to "Không chân (Sans)",
                            FontMode.SERIF to "Có chân (Serif)",
                            FontMode.CONDENSED to "Hẹp (Condensed)",
                            FontMode.MONOSPACE to "Đều (Mono)",
                            FontMode.CUSTOM to "Font đã tải lên",
                        ),
                        onSelect = { mode -> viewModel.update { it.copy(fontMode = mode) } },
                    )
                    Divider()
                    DropdownSetting(
                        title = "Độ đậm",
                        subtitle = "Tự động đo theo độ dày nét chữ gốc",
                        value = settings.fontWeight?.label() ?: "Tự động",
                        icon = Icons.Outlined.FormatBold,
                        options = listOf(
                            (null as FontWeightLevel?) to "Tự động",
                            FontWeightLevel.THIN to "Mảnh",
                            FontWeightLevel.REGULAR to "Thường",
                            FontWeightLevel.MEDIUM to "Vừa",
                            FontWeightLevel.BOLD to "Đậm",
                            FontWeightLevel.BLACK to "Rất đậm",
                        ),
                        onSelect = { weight -> viewModel.update { it.copy(fontWeight = weight) } },
                    )
                    Divider()
                    SettingRow(
                        title = "Tự cân cỡ chữ",
                        subtitle = "Lấy cỡ chữ gốc làm mốc rồi co lại cho vừa khung",
                        icon = Icons.Outlined.FormatSize,
                        trailing = {
                            Switch(
                                checked = settings.autoSizeFromOriginal,
                                onCheckedChange = { value -> viewModel.update { it.copy(autoSizeFromOriginal = value) } },
                            )
                        },
                    )
                    if (settings.fontMode == FontMode.CUSTOM) {
                        Divider()
                        SettingRow(
                            title = "Font tuỳ chỉnh",
                            subtitle = settings.customFontName.ifBlank { "Chưa tải font nào (.ttf/.otf)" },
                            icon = Icons.Outlined.FileOpen,
                            trailing = {
                                androidx.compose.material3.TextButton(onClick = onPickFont) { Text("Chọn") }
                            },
                        )
                    }
                    Divider()
                    SettingRow(
                        title = "Tự chỉnh màu chữ",
                        subtitle = "Bong bóng tối sẽ dùng chữ sáng, bong bóng sáng dùng chữ đậm",
                        icon = Icons.Outlined.Palette,
                        trailing = {
                            Switch(
                                checked = settings.autoContrast,
                                onCheckedChange = { value -> viewModel.update { it.copy(autoContrast = value) } },
                            )
                        },
                    )
                    Divider()
                    SliderSetting(
                        title = "Cỡ chữ",
                        value = settings.textSizeScale,
                        range = 0.6f..1.6f,
                        label = "${(settings.textSizeScale * 100).toInt()}%",
                        onChange = { value -> viewModel.update { it.copy(textSizeScale = value) } },
                    )
                    Divider()
                    SliderSetting(
                        title = "Mở rộng khung chữ",
                        value = settings.boxExpansion,
                        range = 1f..1.5f,
                        label = "${(settings.boxExpansion * 100).toInt()}%",
                        onChange = { value -> viewModel.update { it.copy(boxExpansion = value) } },
                    )
                    Divider()
                    SliderSetting(
                        title = "Viền chữ",
                        value = settings.strokeWidthFactor,
                        range = 0f..0.2f,
                        label = "${(settings.strokeWidthFactor * 100).toInt()}%",
                        onChange = { value -> viewModel.update { it.copy(strokeWidthFactor = value) } },
                    )
                }
            }

            item { SectionHeader("Xoá chữ gốc", modifier = Modifier.padding(top = 8.dp)) }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Tự động chọn cách xoá phù hợp với từng bong bóng.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            EraseMode.AUTO to "Tự động",
                            EraseMode.SOLID to "Tô nền",
                            EraseMode.DIFFUSION to "Lan màu",
                        )
                        modes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = settings.eraseMode == mode,
                                onClick = { viewModel.update { it.copy(eraseMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SettingRow(
                        title = "Khớp màu nền",
                        subtitle = "Vá chỗ chữ với bề mặt màu ước lượng từ bong bóng, không phải một màu phẳng",
                        icon = Icons.Outlined.Colorize,
                        trailing = {
                            Switch(
                                checked = settings.matchBackgroundColor,
                                onCheckedChange = { value -> viewModel.update { it.copy(matchBackgroundColor = value) } },
                            )
                        },
                    )
                    Divider()
                }
            }

            item { SectionHeader("Bộ lọc khi quét", modifier = Modifier.padding(top = 8.dp)) }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Hiệu ứng âm thanh (CRASH!, BAM…) được vẽ thẳng lên hình nên rất dễ bị dịch " +
                            "và xoá nhầm. Mức cao bỏ qua nhiều hơn, đổi lại có thể bỏ sót vài câu thoại lớn.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
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
                                onClick = { viewModel.update { it.copy(sfxSensitivity = level) } },
                                shape = SegmentedButtonDefaults.itemShape(index, levels.size),
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Thiết bị", modifier = Modifier.padding(top = 8.dp)) }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DropdownSetting(
                        title = "Độ phân giải xử lý",
                        subtitle = "Giảm nếu máy yếu hoặc ảnh quá dài bị lỗi bộ nhớ",
                        value = "${settings.maxPagePixels / 1_000_000} MP",
                        icon = Icons.Outlined.Palette,
                        options = listOf(
                            6_000_000 to "6 MP (nhẹ)",
                            10_000_000 to "10 MP (cân bằng)",
                            16_000_000 to "16 MP (nét)",
                        ),
                        onSelect = { value -> viewModel.update { it.copy(maxPagePixels = value) } },
                    )
                }
            }

            item {
                Text(
                    text = "Manhwa Translate 1.0.0\n" +
                        "Nhận diện chữ bằng ML Kit trên máy. Khi dùng nhà cung cấp đám mây, " +
                        "chỉ phần văn bản được gửi đi, ảnh không rời khỏi thiết bị.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: TranslationProviderType,
    selected: Boolean,
    configured: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = provider.label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (provider.requiresApiKey && configured) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Đã có API key",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = provider.hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeyCard(
    settings: com.manhwatranslate.data.AppSettings,
    hasKey: Boolean,
    currentKey: String,
    onSave: (String) -> Unit,
    onUpdate: ((com.manhwatranslate.data.AppSettings) -> com.manhwatranslate.data.AppSettings) -> Unit,
) {
    var key by remember(settings.provider, currentKey) { mutableStateOf(currentKey) }
    var visible by remember { mutableStateOf(false) }

    SettingsCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = "API key cho ${settings.provider.label}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (hasKey) "Đã lưu key trên máy (mã hoá bằng Keystore)." else "Chưa có key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
        )

        if (settings.provider == TranslationProviderType.OPENAI_COMPATIBLE) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = settings.openAiBaseUrl,
                onValueChange = { value -> onUpdate { it.copy(openAiBaseUrl = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = settings.openAiModel,
                onValueChange = { value -> onUpdate { it.copy(openAiModel = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
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
                shape = RoundedCornerShape(16.dp),
            )
        }

        if (settings.provider == TranslationProviderType.DEEPL) {
            Spacer(Modifier.height(6.dp))
            SettingRow(
                title = "Gói DeepL Pro",
                subtitle = "Tắt nếu đang dùng API key miễn phí",
                trailing = {
                    Switch(
                        checked = settings.deeplPro,
                        onCheckedChange = { value -> onUpdate { it.copy(deeplPro = value) } },
                    )
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(key) }, modifier = Modifier.weight(1f)) {
                Text("Lưu key")
            }
            if (hasKey) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        key = ""
                        onSave("")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Xoá key")
                }
            }
        }
    }
}

@Composable
private fun <T> DropdownSetting(
    title: String,
    subtitle: String?,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            onClick = { expanded = true },
            trailing = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionValue, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
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
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
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
