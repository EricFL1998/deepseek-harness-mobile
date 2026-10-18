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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
) {
    val palette = LocalSettingsPalette.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
    ) {
        item(key = "general_permission") {
            PreferenceSelectorRow(
                title = "权限",
                description = "选择新会话的默认权限模式",
                value = when (settings.highRiskConfirm) {
                    true -> "工作区内修改"
                    false -> "完全权限"
                },
                onClick = {
                    onUpdateSettings(settings.copy(highRiskConfirm = !settings.highRiskConfirm))
                }
            )
            GeneralRowDivider()
        }
        item(key = "general_language") {
            PreferenceSelectorRow(
                title = "语言",
                description = "",
                value = "中文",
                onClick = {}
            )
            GeneralRowDivider()
        }
        item(key = "general_appearance") {
            AppearanceRow()
            GeneralRowDivider()
        }
        item(key = "general_font_size") {
            FontSizeRow()
            GeneralRowDivider()
        }
        item(key = "general_transcript") {
            PreferenceSelectorRow(
                title = "工作步骤展示",
                description = "选择希望看到多少工具调用细节",
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
        // 最后一行不画分隔线（GeneralSection 会移除末尾 separator）。
        item(key = "general_collapse") {
            PreferenceSelectorRow(
                title = "工作步骤收起时机",
                description = "选择何时自动收起工作步骤",
                value = "回答结束后",
                onClick = {}
            )
            GeneralRowDivider()
        }
        item(key = "general_developer_tools") {
            DeveloperToolsRow(
                checked = settings.showCodingView,
                onCheckedChange = { checked ->
                    onUpdateSettings(settings.copy(showCodingView = checked))
                },
            )
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
private fun transcriptViewLabel(value: String): String = when (value) {
    "compact" -> "聚焦结果"
    "detailed" -> "完整过程"
    else -> "标准"
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
            text = "外观",
            style = SettingsText.RowTitle,
            color = palette.labelPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppearanceCube(
                label = "浅色",
                icon = Icons.Outlined.LightMode,
                selected = false,
            )
            AppearanceCube(
                label = "深色",
                icon = Icons.Outlined.DarkMode,
                selected = false,
            )
            AppearanceCube(
                label = "跟随系统",
                icon = Icons.Outlined.Monitor,
                selected = true,
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
            .heightIn(min = 88.dp),
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
                text = "字号大小",
                style = SettingsText.RowTitle,
                color = palette.labelPrimary,
            )
            Text(
                text = "仅影响会话内容的字号",
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
                        text = "14",
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
                text = "显示代码工作视图",
                style = SettingsText.RowTitle,
                color = palette.labelPrimary,
            )
            Text(
                text = "开启后，显示轨迹、本轮代码差异，可选择完整的 Agent 预设切换",
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
