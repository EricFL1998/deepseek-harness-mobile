package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.AgentPresetGroupInfo
import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.PluginBundleItem
import com.deepseek.dsh.core.model.PluginInventorySnapshotInfo

/**
 * 内置插件（右侧面板 PluginsSettingsSection + PluginInventorySettingsTab）。
 * 视觉 100% 对齐 Web 原生规范：
 * - 顶部标题「内置插件」、副标题说明「查看内置部署的插件列表」
 * - 直接展示插件列表（与官方一致，不提供「插件市场」页签）
 * - 搜索输入框：36dp 高度，放大镜图标居左
 * - 会话插件折叠组（附带预设选择胶囊「标准模式（默认）▾」）
 * - 卡片网格（手机端自适应排布）：标题、描述、状态标签、code chip
 */
@Composable
internal fun SettingsPluginsPane(
    plugins: List<BuiltInPluginInfo> = emptyList(),
    snapshot: PluginInventorySnapshotInfo? = null,
    onOpenConfigFile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalSettingsPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }
    var isPresetSectionOpen by remember { mutableStateOf(true) }
    var selectedPresetId by remember { mutableStateOf("standard") }

    val presetGroups = snapshot?.agentPresets ?: emptyList()
    val currentPresetGroup = presetGroups.find { it.id == selectedPresetId } ?: presetGroups.firstOrNull()

    // 过滤插件行
    val sourceRows = currentPresetGroup?.rows?.takeIf { it.isNotEmpty() } ?: plugins
    val filteredPresetRows = remember(sourceRows, searchQuery) {
        if (searchQuery.isBlank()) {
            sourceRows
        } else {
            val q = searchQuery.trim().lowercase()
            sourceRows.filter {
                it.title.lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q) ||
                    it.description.lowercase().contains(q)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "plugins_title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsPaneTitle(text = stringResource(R.string.settings_section_plugins), large = true, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL3),
                    onClick = onOpenConfigFile
                ) {
                    Text(
                        text = stringResource(R.string.settings_plugins_open_config),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.labelPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        item(key = "plugins_intro") {
            SettingsIntro(text = stringResource(R.string.settings_plugins_desc))
        }

        // 1. 搜索框：36dp 高度、R12 圆角、border-l4 描边、放大镜图标
        item(key = "plugins_search") {
                val searchShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(searchShape)
                        .background(palette.moduleFill)
                        .border(0.5.dp, palette.borderL4, searchShape),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = palette.labelTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_pane_plugins_search_placeholder),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = palette.labelTertiary,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = palette.labelPrimary
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.labelPrimary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 3.「会话插件」分组头与预设切换器「标准模式（默认）▾」
            item(key = "session_plugins_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { isPresetSectionOpen = !isPresetSectionOpen }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsChevronDown(expanded = isPresetSectionOpen, size = 12.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_pane_plugins_session_group),
                                style = SettingsText.RowTitle,
                                color = palette.labelPrimary
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // 预设选择胶囊
                        if (currentPresetGroup != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = palette.moduleFill,
                                onClick = {
                                    val next = if (selectedPresetId == "standard") "compact" else "standard"
                                    selectedPresetId = next
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (currentPresetGroup.id == "standard") {
                                            stringResource(R.string.settings_pane_plugins_preset_default)
                                        } else {
                                            stringResource(
                                                R.string.settings_pane_plugins_preset_named,
                                                currentPresetGroup.name,
                                            )
                                        },
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = palette.labelPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    SettingsChevronDown(expanded = false, size = 10.dp)
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_pane_plugins_preset_desc),
                        style = SettingsText.RowDesc,
                        color = palette.labelTertiary,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp)
                    )
                }
            }

            // 4. 插件卡片网格列表
            if (isPresetSectionOpen) {
                if (filteredPresetRows.isEmpty()) {
                    item(key = "empty_plugins") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    stringResource(R.string.settings_pane_plugins_empty_search)
                                } else {
                                    stringResource(R.string.settings_pane_plugins_empty)
                                },
                                style = SettingsText.RowDesc,
                                color = palette.labelTertiary
                            )
                        }
                    }
                } else {
                    items(filteredPresetRows.size, key = { filteredPresetRows[it].packageName }) { idx ->
                        val plugin = filteredPresetRows[idx]
                        PluginCard(
                            plugin = plugin,
                            expanded = expandedPackage == plugin.packageName,
                            onToggle = {
                                expandedPackage = if (expandedPackage == plugin.packageName) null else plugin.packageName
                            }
                        )
                    }
                }
            }
        }
}

/** 插件卡：整卡可展开，展开后追加 .cardDetails 事实面板（面板与卡片同宽、贴边）。 */
@Composable
private fun PluginCard(
    plugin: BuiltInPluginInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    val languageTag = androidx.compose.ui.platform.LocalConfiguration.current
        .locales[0]?.language.orEmpty()
    val localized = localizedBuiltinPlugin(languageTag, plugin)
    SettingsCard(
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        onClick = onToggle,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = localized.title,
                        style = SettingsText.CardTitle,
                        color = palette.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!plugin.enabled) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.moduleFill,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_pane_plugins_disabled),
                                fontSize = 11.sp,
                                color = palette.labelTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    SettingsChevronDown(expanded = expanded, size = 12.dp)
                }
                Text(
                    text = localized.description,
                    style = SettingsText.RowDesc,
                    color = palette.labelTertiary,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 标识 CodeChip
                SettingsCodeChip(
                    text = plugin.entryId ?: plugin.packageName,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (expanded) {
                SettingsCardDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.moduleFill)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PluginFactRow(
                        label = stringResource(R.string.settings_pane_plugins_fact_package),
                        value = plugin.packageName,
                    )
                    PluginFactRow(
                        label = stringResource(R.string.settings_pane_plugins_fact_enabled),
                        value = if (plugin.enabled) {
                            stringResource(R.string.settings_pane_plugins_enabled)
                        } else {
                            stringResource(R.string.settings_pane_plugins_disabled)
                        },
                    )
                    val cond = plugin.condition
                    if (cond != null) {
                        PluginFactRow(
                            label = stringResource(R.string.settings_pane_plugins_fact_condition),
                            value = cond,
                        )
                    }
                    PluginFactRow(
                        label = stringResource(R.string.settings_pane_plugins_fact_runtime),
                        value = if (plugin.phase == "active") {
                            stringResource(R.string.settings_pane_plugins_running)
                        } else {
                            plugin.phase
                        },
                    )
                    PluginFactRow(
                        label = stringResource(R.string.settings_pane_plugins_fact_source),
                        value = stringResource(R.string.settings_pane_plugins_source_preset),
                    )
                }
            }
        }
    }
}

/** .details 事实行：76dp 标签列（11sp 三级色）+ 值列（12sp 二级色）。 */
@Composable
private fun PluginFactRow(label: String, value: String) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = SettingsText.Tag,
            color = palette.labelTertiary,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = SettingsText.RowDesc,
            color = palette.labelSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}








