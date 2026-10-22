package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 通用设置的一行：渲染键、后端注册 id（可能没有）与默认顺序。 */
private data class RowSpec(val key: String, val backendId: String?, val fallbackOrder: Int)

/**
 * 通用设置（右侧面板 GeneralSection）。
 *
 * 逐行对齐 dsh-upstream 通用设置项（PermissionRow / LanguageRow / AppearanceRow /
 * FontSizeRow / PreferenceRow 的 Rows 规格）：行内 16dp 上下留白、0.5dp border-l2 分隔线、
 * 标题 14/22、说明 12/18 三级色、右侧胶囊控件 36dp/R12/module 底。
 *
 * 手机端去掉了电脑专属设置项（显示代码工作视图、网页链接默认打开方式）。
 */
@Composable
internal fun SettingsGeneralPane(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
    /** 当前语言选择：`system` / `zh` / `en`。 */
    languageTag: String = "system",
    onLanguageChange: (String) -> Unit = {},
    /**
     * 后端 `settings.general.item` 注册的排序（id → order）。为空时用本文件
     * 的默认顺序；Host 新增/调整通用设置项时无需改 APK。
     */
    itemOrder: Map<String, Int> = emptyMap(),
) {
    val palette = LocalSettingsPalette.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        SessionLogUploadBridge.refresh()
        PermissionPresetBridge.refresh()
        ThemeBridge.refresh()
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
    ) {
        // 行的顺序来自 Host 的 settings.general.item 注册；没有注册过的行保持
        // 本地默认顺序。设置页只负责「这一行怎么画」。
        val rows = listOf(
            RowSpec("general_permission", "permission", 0),
            RowSpec("general_language", "language", 1),
            RowSpec("general_appearance", "appearance", 2),
            RowSpec("general_font_size", "font-size", 3),
            RowSpec("general_transcript", "transcript-view", 4),
            RowSpec("general_collapse", null, 5),
            RowSpec("general_session_log_upload", null, 6),
            RowSpec("general_developer_tools", "developer-tools", 7),
        ).sortedBy { spec -> itemOrder[spec.backendId] ?: (1000 + spec.fallbackOrder) }
        rows.forEach { spec ->
            item(key = spec.key) {
                when (spec.key) {
                    "general_permission" -> {
            // 权限预设来自 Host（permissionPresets/catalog），当前默认值来自
            // settings 的 permission.defaultPreset；选择后写回 Host。
            val presets = PermissionPresetBridge.options
            val currentPreset = PermissionPresetBridge.current
            var showPermissionDialog by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(false)
            }
            PreferenceSelectorRow(
                title = stringResource(R.string.settings_pane_general_permission_title),
                description = stringResource(R.string.settings_pane_general_permission_desc),
                value = currentPreset?.let { value ->
                    presets.firstOrNull { it.value == value }?.name ?: value
                } ?: when (settings.highRiskConfirm) {
                    true -> stringResource(R.string.settings_pane_general_permission_workspace)
                    false -> stringResource(R.string.settings_pane_general_permission_full)
                },
                onClick = {
                    if (presets.isNotEmpty()) {
                        showPermissionDialog = true
                    } else {
                        onUpdateSettings(settings.copy(highRiskConfirm = !settings.highRiskConfirm))
                    }
                },
            )
            if (showPermissionDialog) {
                com.deepseek.dsh.core.designsystem.DshDialog(
                    onDismiss = { showPermissionDialog = false },
                    title = stringResource(R.string.settings_pane_general_permission_title),
                    closeLabel = stringResource(android.R.string.cancel),
                    content = {
                        presets.forEach { preset ->
                            Text(
                                text = preset.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPermissionDialog = false
                                        PermissionPresetBridge.set(preset.value)
                                        onUpdateSettings(
                                            settings.copy(
                                                highRiskConfirm = preset.value != "danger-full-access",
                                            ),
                                        )
                                    }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    },
                )
            }
            GeneralRowDivider()
        }
                    "general_language" -> {
            var showLanguageDialog by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(false)
            }
            PreferenceSelectorRow(
                title = stringResource(R.string.settings_pane_general_language_title),
                description = "",
                value = when (languageTag) {
                    "zh" -> stringResource(R.string.settings_pane_general_language_chinese)
                    "en" -> stringResource(R.string.settings_pane_general_language_english)
                    else -> stringResource(R.string.settings_pane_general_language_system)
                },
                onClick = { showLanguageDialog = true },
            )
            if (showLanguageDialog) {
                com.deepseek.dsh.core.designsystem.DshDialog(
                    onDismiss = { showLanguageDialog = false },
                    title = stringResource(R.string.settings_pane_general_language_title),
                    closeLabel = stringResource(android.R.string.cancel),
                    content = {
                        listOf(
                            "system" to R.string.settings_pane_general_language_system,
                            "zh" to R.string.settings_pane_general_language_chinese,
                            "en" to R.string.settings_pane_general_language_english,
                        ).forEach { (value, label) ->
                            Text(
                                text = stringResource(label),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showLanguageDialog = false
                                        onLanguageChange(value)
                                    }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    },
                )
            }
            GeneralRowDivider()
        }
                    "general_appearance" -> {
            AppearanceRow()
            GeneralRowDivider()
        }
                    "general_font_size" -> {
            FontSizeRow()
            GeneralRowDivider()
        }
                    "general_transcript" -> {
            PreferenceSelectorRow(
                title = stringResource(R.string.settings_pane_general_steps_title),
                description = stringResource(R.string.settings_pane_general_steps_desc),
                value = transcriptViewLabel(settings.transcriptView),
                onClick = {
                    val next = when (settings.transcriptView) {
                        "compact" -> "standard"
                        "standard" -> "detailed"
                        else -> "compact"
                    }
                    onUpdateSettings(settings.copy(transcriptView = next))
                }
            )
            GeneralRowDivider()
        }
                    "general_collapse" -> {
            PreferenceSelectorRow(
                title = stringResource(R.string.settings_pane_general_collapse_title),
                description = stringResource(R.string.settings_pane_general_collapse_desc),
                value = stringResource(R.string.settings_pane_general_collapse_value),
                onClick = {}
            )
            GeneralRowDivider()
        }
                    "general_session_log_upload" -> {
            val uploadEnabled = SessionLogUploadBridge.enabled
            if (uploadEnabled != null) {
                SessionLogUploadRow(
                    checked = uploadEnabled,
                    onCheckedChange = { checked -> SessionLogUploadBridge.set(checked) },
                )
                GeneralRowDivider()
            }
        }
                    "general_developer_tools" -> {
            DeveloperToolsRow(
                checked = settings.showCodingView,
                onCheckedChange = { checked ->
                    onUpdateSettings(settings.copy(showCodingView = checked))
                },
            )
        }
                }
            }
        }
}
}

/** 行分隔线：0.5dp border-l2，通栏。 */
@Composable
private fun GeneralRowDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = LocalSettingsPalette.current.borderL2,
    )
}

/** 首次引导写入的 transcriptView 模式映射为展示文案。 */
@Composable
private fun transcriptViewLabel(value: String): String = when (value) {
    "compact" -> stringResource(R.string.settings_pane_transcript_compact)
    "detailed" -> stringResource(R.string.settings_pane_transcript_detailed)
    else -> stringResource(R.string.settings_pane_transcript_standard)
}

/** 偏好行：标题/说明 14/22 + 12/18，右侧 36dp 胶囊选择器。 */
@Composable
private fun PreferenceSelectorRow(
    title: String,
    description: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = SettingsText.RowTitle,
                color = palette.labelPrimary,
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = SettingsText.RowDesc,
                    color = palette.labelTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        SettingsSelectorPill(text = value)
    }
}

/** 通用设置选择器胶囊：H36 / R12 / module 底 / 14/22 + 12dp chevron。 */
@Composable
private fun SettingsSelectorPill(text: String) {
    val palette = LocalSettingsPalette.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.moduleFill,
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = text,
                style = SettingsText.RowTitle,
                color = palette.labelPrimary,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = palette.labelPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 外观行：标题 14/22 + 三张外观卡（R20、0.5dp 描边；选中为 module 底 + bluish-400 描边）。 */
@Composable
private fun AppearanceRow() {
    val palette = LocalSettingsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_pane_general_appearance_title),
            style = SettingsText.RowTitle,
            color = palette.labelPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppearanceCube(
                label = stringResource(R.string.settings_pane_general_appearance_light),
                icon = Icons.Outlined.LightMode,
                selected = ThemeBridge.preference == "light",
                onClick = { ThemeBridge.setPreference("light") },
            )
            AppearanceCube(
                label = stringResource(R.string.settings_pane_general_appearance_dark),
                icon = Icons.Outlined.DarkMode,
                selected = ThemeBridge.preference == "dark",
                onClick = { ThemeBridge.setPreference("dark") },
            )
            AppearanceCube(
                label = stringResource(R.string.settings_pane_general_appearance_system),
                icon = Icons.Outlined.Monitor,
                selected = ThemeBridge.preference == "system",
                onClick = { ThemeBridge.setPreference("system") },
            )
        }
    }
}

/** 外观立方卡（Figma '.Selector Cube'）：居中图标 + 14/22 标签，R20。 */
@Composable
private fun RowScope.AppearanceCube(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit = {},
) {
    val palette = LocalSettingsPalette.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) palette.moduleFill else androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(
            0.5.dp,
            if (selected) palette.activeStroke else palette.borderL4,
        ),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.labelPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = SettingsText.RowTitle,
                color = palette.labelPrimary,
                maxLines = 1,
            )
        }
    }
}

/** 字号行：静态数值步进胶囊（H36/R12/module 底，minWidth 72）+ 外部 px 单位 14/22。 */
@Composable
private fun FontSizeRow() {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_pane_general_font_size_title),
                style = SettingsText.RowTitle,
                color = palette.labelPrimary,
            )
            Text(
                text = stringResource(R.string.settings_pane_general_font_size_desc),
                style = SettingsText.RowDesc,
                color = palette.labelTertiary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.moduleFill,
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 80.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (ThemeBridge.fontSize ?: 14).toString(),
                        style = SettingsText.RowTitle,
                        color = palette.labelPrimary,
                    )
                }
            }
            Text(
                text = "px",
                style = SettingsText.RowTitle,
                color = palette.labelSecondary,
            )
        }
    }
}

/** 开发者工具行（DeveloperToolsRow）：标题 14/20 + 说明 12/18 二级色 + 右侧 36×20 开关。 */
@Composable
private fun DeveloperToolsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_pane_general_developer_title),
                style = SettingsText.RowTitle,
                color = palette.labelPrimary,
            )
            Text(
                text = stringResource(R.string.settings_pane_general_developer_desc),
                style = SettingsText.RowDesc,
                color = palette.labelSecondary,
            )
        }
        SettingsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** Session log 上传开关（官方 `ui-settings-session-log` 的 UploadRow）。 */
@Composable
private fun SessionLogUploadRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_pane_general_upload_title),
                style = SettingsText.RowTitle,
                color = palette.labelPrimary,
            )
            Text(
                text = stringResource(R.string.settings_pane_general_upload_desc),
                style = SettingsText.RowDesc,
                color = palette.labelSecondary,
            )
        }
        SettingsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
