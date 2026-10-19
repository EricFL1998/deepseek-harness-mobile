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
    marketStatusJson: String? = null,
    onMarketAction: (String, String) -> Unit = { _, _ -> },
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
                marketStatusJson = marketStatusJson,
                onMarketAction = onMarketAction,
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
    marketStatusJson: String?,
    onMarketAction: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingsPalette.current
    val status = remember(marketStatusJson) { MarketStatusUi.parse(marketStatusJson) }
    var envText by remember(status?.buildEnvText) { mutableStateOf(status?.buildEnvText.orEmpty()) }
    var proxyEditing by remember { mutableStateOf(false) }
    var proxyInput by remember { mutableStateOf(status?.githubProxyCustom.orEmpty()) }
    var showUninstallConfirm by remember { mutableStateOf(false) }

    fun post(path: String, body: String) = onMarketAction(path, body)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = 8.dp,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "market_card") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.moduleFill,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL4),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("插件市场", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.labelPrimary)
                        status?.version?.takeIf { it.isNotBlank() }?.let {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("v$it", fontSize = 12.sp, color = palette.labelTertiary)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        SettingsChevronDown(expanded = true, size = 12.dp)
                    }
                    Text("查看插件市场版本与设置。", fontSize = 12.sp, color = palette.labelSecondary)
                    if (status == null) {
                        Text("插件市场未安装或不可用。", fontSize = 12.sp, color = palette.labelTertiary)
                    }
                }
            }
        }

        if (status != null) {
            item(key = "market_channel") {
                MarketSettingRow(
                    title = "更新通道",
                    hint = when (status.channel) {
                        "beta" -> "提前获取待验证版本，可能不稳定。仅影响插件市场自身。"
                        "dev" -> "开发分支构建，未经验证，不建议普通用户使用。"
                        else -> "仅接收正式发布版本。"
                    },
                    action = {
                        MarketSegmented(
                            options = status.channels.ifEmpty { listOf("stable", "beta") },
                            selected = status.channel,
                            label = { id -> if (id == "stable") "稳定版" else if (id == "beta") "Beta" else "开发版" },
                            onSelect = { post("channel", """{"channel":"$it"}""") },
                        )
                    }
                )
            }
            item(key = "market_region") {
                MarketSettingRow(
                    title = "下载区域",
                    hint = if (status.regionAuto) {
                        "已根据网络状况自动选择，可随时切换。"
                    } else if (status.region == "china") {
                        "中国大陆访问更快。仅改变下载线路，插件来源不变。"
                    } else {
                        "从 npm 和 GitHub 官方源下载。"
                    },
                    action = {
                        MarketSegmented(
                            options = status.regions.ifEmpty { listOf("global", "china") },
                            selected = status.region,
                            label = { id -> if (id == "china") "中国大陆" else "全球" },
                            onSelect = { post("region", """{"region":"$it"}""") },
                        )
                    }
                )
            }
            item(key = "market_proxy") {
                MarketSettingRow(
                    title = "GitHub 加速",
                    hint = when {
                        status.githubProxyManaged -> "由环境变量 DSHM_GITHUB_PROXY 管理，界面中不可修改。"
                        status.githubProxyCustom != null -> "优先使用自定义线路，失败后仍会尝试直连。"
                        else -> "自动选择可用线路，失败时才切换。"
                    },
                    action = if (status.githubProxyManaged) null else {
                        {
                            if (status.githubProxyCustom == null) {
                                MarketOutlineButton("自定义") { proxyEditing = true; proxyInput = "" }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MarketOutlineButton("编辑") { proxyEditing = true; proxyInput = status.githubProxyCustom }
                                    MarketOutlineButton("恢复自动") { post("github-proxy", """{"proxy":null}""") }
                                }
                            }
                        }
                    }
                )
            }
            item(key = "market_build_env") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("构建环境变量", style = SettingsText.RowTitle, color = palette.labelPrimary)
                    Text(
                        "作用于市场启动的每个进程（安装、编译、git 等），不只是编译器。每行一个 KEY=value，例如 CC=/usr/bin/gcc-11；PATH 和 CI 由市场管理。留空保存即恢复原有配置。",
                        fontSize = 12.sp,
                        color = palette.labelTertiary
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.moduleFill,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL4),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = envText,
                            onValueChange = { envText = it },
                            textStyle = TextStyle(fontSize = 12.sp, color = palette.labelPrimary),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.labelPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .padding(12.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarketOutlineButton("保存环境变量") {
                            val obj = buildString {
                                append("{\"buildEnv\":{")
                                envText.lines().map { it.trim() }.filter { it.contains("=") }.forEachIndexed { index, line ->
                                    val key = line.substringBefore("=").trim()
                                    val value = line.substringAfter("=").trim()
                                    if (index > 0) append(",")
                                    append("\"${key.replace("\"", "\\\"")}\":\"${value.replace("\"", "\\\"")}\"")
                                }
                                append("}}")
                            }
                            post("build-env", obj)
                        }
                    }
                }
            }
            item(key = "market_uninstall") {
                MarketSettingRow(
                    title = "卸载插件市场",
                    hint = "从这个 profile 卸载市场。已装的其它插件不受影响。",
                    action = { MarketDangerButton("卸载插件市场") { showUninstallConfirm = true } }
                )
            }
            if (showUninstallConfirm) {
                item(key = "market_uninstall_confirm") {
                    Surface(shape = RoundedCornerShape(10.dp), color = palette.moduleFill, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("确定要卸载插件市场吗？卸载后需使用命令行重新安装。", fontSize = 12.sp, color = palette.labelSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MarketOutlineButton("取消") { showUninstallConfirm = false }
                                MarketDangerButton("卸载") { post("self-uninstall", """{"confirm":true,"purge":false}""") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (proxyEditing) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { proxyEditing = false },
            title = "GitHub 加速",
            closeLabel = "关闭",
            description = "输入 HTTPS 加速前缀，留空保存可恢复自动选择。",
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(text = "取消", primary = false, enabled = true, onClick = { proxyEditing = false })
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(text = "保存", primary = true, enabled = true, onClick = {
                    val proxy = proxyInput.trim()
                    post("github-proxy", if (proxy.isEmpty()) """{"proxy":null}""" else """{"proxy":"${proxy.replace("\"", "\\\"")}"}""")
                    proxyEditing = false
                })
            },
            content = {
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = proxyInput,
                    onValueChange = { proxyInput = it },
                    placeholder = "https://...",
                )
            },
        )
    }
}

private data class MarketStatusUi(
    val version: String?,
    val channel: String,
    val channels: List<String>,
    val region: String,
    val regions: List<String>,
    val regionAuto: Boolean,
    val githubProxyCustom: String?,
    val githubProxyManaged: Boolean,
    val selfManaged: Boolean,
    val buildEnvText: String,
) {
    companion object {
        fun parse(raw: String?): MarketStatusUi? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val obj = kotlinx.serialization.json.Json.parseToJsonElement(raw).let {
                    (it as? kotlinx.serialization.json.JsonObject)?.get("value")
                        ?.let { v -> v as? kotlinx.serialization.json.JsonObject } ?: (it as? kotlinx.serialization.json.JsonObject)
                } ?: return null
                fun text(key: String): String? = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
                fun bool(key: String): Boolean = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
                fun arr(key: String): List<String> = (obj[key] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
                val env = (obj["buildEnv"] as? kotlinx.serialization.json.JsonObject)?.entries
                    ?.sortedBy { it.key }
                    ?.joinToString("\\n") { "${it.key}=${(it.value as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()}" }
                    .orEmpty()
                MarketStatusUi(
                    version = text("version"),
                    channel = text("channel") ?: "stable",
                    channels = arr("channels"),
                    region = text("region") ?: "global",
                    regions = arr("regions"),
                    regionAuto = bool("regionAuto"),
                    githubProxyCustom = text("githubProxyCustom"),
                    githubProxyManaged = bool("githubProxyManaged"),
                    selfManaged = obj["selfManaged"]?.toString() != "false",
                    buildEnvText = env,
                )
            }.getOrNull()
        }
    }
}

@Composable
private fun MarketSettingRow(title: String, hint: String, action: (@Composable () -> Unit)?) {
    val palette = LocalSettingsPalette.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = SettingsText.RowTitle, color = palette.labelPrimary)
            Spacer(modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Text(hint, fontSize = 12.sp, color = palette.labelTertiary)
        androidx.compose.material3.HorizontalDivider(color = palette.borderL4)
    }
}

@Composable
private fun MarketSegmented(options: List<String>, selected: String, label: (String) -> String, onSelect: (String) -> Unit) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.labelPrimary.copy(alpha = 0.06f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { id ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (id == selected) palette.moduleFill else Color.Transparent,
                onClick = { onSelect(id) }
            ) {
                Text(
                    label(id),
                    fontSize = 12.sp,
                    color = if (id == selected) palette.labelPrimary else palette.labelTertiary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun MarketOutlineButton(text: String, onClick: () -> Unit) {
    val palette = LocalSettingsPalette.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL4),
        onClick = onClick
    ) {
        Text(text, fontSize = 12.sp, color = palette.labelPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun MarketDangerButton(text: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.Transparent, onClick = onClick) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
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







