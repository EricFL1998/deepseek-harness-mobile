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
    pluginBundles: List<PluginBundleItem> = emptyList(),
    onInstallPlugin: (String) -> Unit = {},
    onTogglePluginBundle: (String, Boolean) -> Unit = { _, _ -> },
    onUninstallPlugin: (String) -> Unit = {},
    onOpenConfigFile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalSettingsPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }
    var isPresetSectionOpen by remember { mutableStateOf(true) }
    var selectedPresetId by remember { mutableStateOf("standard") }
    var selectedTab by remember { mutableStateOf(0) }

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

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsPaneDimens.Horizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            listOf("插件列表" to 0, "插件市场" to 1).forEach { (label, index) ->
                Column(
                    modifier = Modifier.clickable { selectedTab = index },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal,
                        color = if (selectedTab == index) palette.labelPrimary else palette.labelTertiary
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .height(2.dp)
                            .width(if (selectedTab == index) 28.dp else 0.dp)
                            .background(if (selectedTab == index) palette.labelPrimary else Color.Transparent)
                    )
                }
            }
        }

        if (selectedTab == 1) {
            PluginMarketPane(
                bundles = pluginBundles,
                onInstallPlugin = onInstallPlugin,
                onToggleBundle = onTogglePluginBundle,
                onUninstallPlugin = onUninstallPlugin,
                modifier = Modifier.fillMaxSize()
            )
        } else {
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
                SettingsPaneTitle(text = "内置插件", large = true, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL3),
                    onClick = onOpenConfigFile
                ) {
                    Text(
                        text = "打开配置文件",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.labelPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        item(key = "plugins_intro") {
            SettingsIntro(text = "查看内置部署的插件列表")
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
                                    text = "搜索插件",
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
                                text = "会话插件",
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
                                        text = if (currentPresetGroup.id == "standard") "标准模式（默认）" else "${currentPresetGroup.name}模式",
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
                        text = "由 Agent 预设按会话组成",
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
                                text = if (searchQuery.isNotBlank()) "没有匹配的插件。" else "暂无插件。",
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
    }
}

@Composable
private fun PluginMarketPane(
    bundles: List<PluginBundleItem>,
    onInstallPlugin: (String) -> Unit,
    onToggleBundle: (String, Boolean) -> Unit,
    onUninstallPlugin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingsPalette.current
    var packageInput by remember { mutableStateOf("") }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = 8.dp,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "market_head") {
            Column {
                SettingsPaneTitle(text = "插件市场", large = true)
                SettingsIntro(text = "安装、启用和管理 Host 插件；插件由 DSH Host 提供。")
            }
        }
        item(key = "market_install") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsIntro(text = "安装插件")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.moduleFill,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL4),
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = packageInput,
                            onValueChange = { packageInput = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = palette.labelPrimary),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.labelPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = {
                            val spec = packageInput.trim()
                            if (spec.isNotEmpty()) {
                                onInstallPlugin(spec)
                                packageInput = ""
                            }
                        }
                    ) {
                        Text(
                            text = "安装",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
        item(key = "market_installed_title") {
            SettingsIntro(text = if (bundles.isEmpty()) "暂无已安装的市场插件。" else "已安装插件")
        }
        items(bundles.size, key = { "bundle_${bundles[it].packageName}" }) { index ->
            val bundle = bundles[index]
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.moduleFill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bundle.title, fontSize = 14.sp, color = palette.labelPrimary)
                            Text(bundle.packageName, fontSize = 11.sp, color = palette.labelTertiary)
                        }
                        androidx.compose.material3.Switch(
                            checked = bundle.enabled,
                            onCheckedChange = { onToggleBundle(bundle.packageName, it) }
                        )
                    }
                    if (bundle.description.isNotBlank()) {
                        Text(bundle.description, fontSize = 12.sp, color = palette.labelSecondary)
                    }
                    Text(
                        text = "卸载",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { onUninstallPlugin(bundle.packageName) }
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
                        text = plugin.title,
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
                                text = "已停用",
                                fontSize = 11.sp,
                                color = palette.labelTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    SettingsChevronDown(expanded = expanded, size = 12.dp)
                }
                Text(
                    text = plugin.description,
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
                    PluginFactRow(label = "完整名称", value = plugin.packageName)
                    PluginFactRow(label = "启用状态", value = if (plugin.enabled) "已启用" else "已停用")
                    val cond = plugin.condition
                    if (cond != null) {
                        PluginFactRow(label = "禁用条件", value = cond)
                    }
                    PluginFactRow(label = "运行状态", value = if (plugin.phase == "active") "运行中" else plugin.phase)
                    PluginFactRow(label = "来自", value = "Agent 预设")
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




