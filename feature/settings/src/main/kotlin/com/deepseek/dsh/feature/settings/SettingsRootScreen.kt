package com.deepseek.dsh.feature.settings

import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.PluginInventorySnapshotInfo
import com.deepseek.dsh.core.model.isThirdPartyPlugin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 6 大标准 Section (对齐 dsh-upstream/packages/client/ui-settings-*)
 * + 手机专属增强 Section (mobile-runtime)
 */
enum class SettingsSectionTab(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
) {
    // 顺序与 settings 导航一致（order: account -10, general 0, models 10, plugins 15, agent-presets 20）
    ACCOUNT("account", "账号与余额", "DeepSeek 官方 OAuth 授权与余额", com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_user),
    GENERAL("general", "通用设置", "界面、语言与启动行为", com.deepseek.dsh.core.designsystem.R.drawable.ic_menu_settings),
    MODELS("models", "模型", "提供商、模型与 API Key 配置", com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_model),
    PLUGINS("plugins", "内置插件", "已安装插件与配置", com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_personalization),
    AGENT_PRESETS("agent-presets", "Agent 预设", "工作过程模式：紧凑 / 标准 / 详细", com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_agent_preset),
    ARCHIVED("archived-sessions", "归档会话", "已归档的会话记录与快照", com.deepseek.dsh.core.designsystem.R.drawable.ic_row_archive),
    MOBILE_RUNTIME("mobile-runtime", "移动运行时", "Linux 容器休眠、长任务守护与高危拦截", com.deepseek.dsh.core.designsystem.R.drawable.ic_menu_settings),
    NOTIFICATIONS("notifications", "通知", "任务完成通知与系统权限", com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_schedule),
    REMOTE("remote", "远程连接", "连接另一台机器上的 DSH Host", com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin),
    UPDATE("update", "更新", "检查 GitHub Release 与新版本", com.deepseek.dsh.core.designsystem.R.drawable.ic_row_archive),
}

/**
 * 手机端自适应 SettingsRoot：
 * 手机竖屏状态下，设置主页采用左侧导航（分区列表）-> 点击深入右侧 Section 面板，
 * 支持平滑返回；每个内容页严格对齐 dsh-upstream 对应 settings section 的右侧面板样式。
 */
@Composable
fun SettingsRootScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    accountName: String = "DeepSeek 用户",
    accountBalance: String = "¥0.00",
    accountPhone: String = "",
    isSignedIn: Boolean = false,
    avatarUrl: String? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    initialSection: SettingsSectionTab? = null,
    /** 插件声明的设置页入口；点开只有标题与说明，内容页尚未实现。 */
    pluginSettingsPages: List<com.deepseek.dsh.core.model.PluginSettingsPage> = emptyList(),
    /** 插件声明的全部入口（带原生落点），用于汇总尚未有专门屏幕的位置。 */
    pluginSlotEntries: List<com.deepseek.dsh.core.model.PluginSlotEntry> = emptyList(),
    /** bridge 门面；提供时插件声明的组件（settings.section 等）可渲染成原生控件。 */
    bridgeRegistry: com.deepseek.dsh.core.bridge.BridgeRegistry? = null,
    /** 插件请求刷新的组件 id 流（client/refresh 事件）。 */
    pluginComponentRefresh: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    /** 本地 Host 的 loopback 端口与启动 token；插件的完整界面（WebView）用它们。 */
    hostPort: Int = 0,
    hostToken: String? = null,
    /** 需要时再取一次 Host 端口（异步就绪）。 */
    hostPortProvider: (() -> Int)? = null,
    /** 读取一份插件设置表单。 */
    loadPluginSettings: suspend (String) -> com.deepseek.dsh.core.model.PluginSettingsDocument? = { null },
    /** 回写插件设置字段。 */
    savePluginSettings: suspend (String, kotlinx.serialization.json.JsonObject, Int) -> Boolean =
        { _, _, _ -> false },
    availableModels: List<String> = emptyList(),
    modelProvidersRaw: String? = null,
    modelSettingsRaw: String? = null,
    modelCredentialsRaw: String? = null,
    onSaveModelProvider: (ModelProviderUi, String, String, List<String>) -> Unit = { _, _, _, _ -> },
    builtInPlugins: List<BuiltInPluginInfo> = emptyList(),
    pluginSnapshot: PluginInventorySnapshotInfo? = null,
    runtimeStatus: com.deepseek.dsh.runtime.host.RuntimeStatus? = null,
    onStartRuntime: () -> Unit = {},
    onStopRuntime: () -> Unit = {},
    onOpenSettingsDocument: () -> Unit = {},
    onManageAccount: () -> Unit = {},
    onCreateBackup: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onConnectRemote: (String, String) -> Unit = { _, _ -> },
    onDisconnectRemote: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSection by remember { mutableStateOf<SettingsSectionTab?>(initialSection) }
    var activePluginPage by remember {
        mutableStateOf<com.deepseek.dsh.core.model.PluginSettingsPage?>(null)
    }
    var activePluginComponent by remember { mutableStateOf<com.deepseek.dsh.core.model.PluginSlotEntry?>(null) }
    var currentSettings by remember(settings) { mutableStateOf(settings) }
    var selectedAgentPreset by remember { mutableStateOf(settings.transcriptView) }

    BackHandler(enabled = true) {
        when {
            activePluginComponent != null -> activePluginComponent = null
            activePluginPage != null -> activePluginPage = null
            activeSection != null -> activeSection = null
            else -> onClose()
        }
    }

    val bgTheme = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onBackground

    SettingsTheme {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(bgTheme)
                .statusBarsPadding()
        ) {
            // 顶层 Header 只在「设置主页 / 分区面板」显示。插件界面与插件设置页
            // 自带同一套返回条，这里再画一次就会出现两个返回按钮（且顶层的那个
            // 返回的是设置根，不是上一级插件界面）。
            val subPageOpen = activePluginComponent != null || activePluginPage != null
            if (!subPageOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            onClick = { if (activeSection == null) onClose() else activeSection = null },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = textPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = activeSection?.title ?: "设置",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = textPrimary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }
            }

            AnimatedContent(
                targetState = activeSection,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "settings_nav"
            ) { section ->
                if (activePluginComponent != null) {
                    val entry = activePluginComponent!!
                    PluginComponentPane(
                        componentId = entry.resolvedComponentId() ?: "${entry.module}#${entry.slot}",
                        title = entry.title ?: entry.identity ?: entry.slot,
                        registry = bridgeRegistry,
                        onBack = { activePluginComponent = null },
                        refreshEvents = pluginComponentRefresh,
                        hostPort = hostPort,
                        hostToken = hostToken,
                        hostPortProvider = hostPortProvider,
                    )
                } else if (activePluginPage != null) {
                    val page = activePluginPage!!
                    PluginSettingsPage(
                        ns = page.id,
                        title = page.title,
                        description = page.description,
                        loadDocument = loadPluginSettings,
                        saveDocument = savePluginSettings,
                        onBack = { activePluginPage = null },
                    )
                } else if (section == null) {
                    // 移动端设置主页：分组卡片内的分区行（图标 + 标签 + 箭头）。
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    ) {
                        // settings.general.item 等：原生插件在设置主页顶部的落点。
                        // 插件在此显示状态卡/快捷入口等原生内容；没有注册时不占空间。
                        item(key = "native_slot_settings") {
                            com.deepseek.dsh.plugin.nativehost.NativeSlot(
                                slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.SETTINGS_SHEET,
                                spec = kotlinx.serialization.json.JsonObject(emptyMap()),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        item(key = "settings_card") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    SettingsSectionTab.values().forEachIndexed { index, tab ->
                                        SettingsNavRow(
                                            tab = tab,
                                            onClick = { activeSection = tab },
                                        )
                                        if (index != SettingsSectionTab.values().lastIndex) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // 插件声明的设置页：Host 为每个声明了 Config 的插件行生成命名空间，
                        // 这里按同一个顺序列出来；内容页尚未实现。
                        if (pluginSettingsPages.isNotEmpty()) {
                            item(key = "plugin_settings_card_head") {
                                Text(
                                    text = "插件设置",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
                                )
                            }
                            item(key = "plugin_settings_card") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = cardBg,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column {
                                        pluginSettingsPages.forEachIndexed { index, page ->
                                            SettingsNavRow(
                                                title = page.title,
                                                subtitle = page.description ?: page.id,
                                                iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin,
                                                onClick = { activePluginPage = page },
                                            )
                                            if (index != pluginSettingsPages.lastIndex) {
                                                HorizontalDivider(
                                                    thickness = 0.5.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 插件声明的 UI（settings.section 等 slot 注册）：点进去是该插件
                        // 静态转译出的原生渲染树，而不是只有表单 schema 的页面。
                        // 官方分区已有本页面自己的原生面板，这里只列第三方插件。
                        val pluginComponents = pluginSlotEntries
                            .filter {
                                it.slot == "settings.section" &&
                                    it.isThirdPartyPlugin
                            }
                        if (pluginComponents.isNotEmpty() && bridgeRegistry != null) {
                            item(key = "plugin_components_card_head") {
                                Text(
                                    text = "插件界面",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
                                )
                            }
                            item(key = "plugin_components_card") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = cardBg,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column {
                                        pluginComponents.forEachIndexed { index, entry ->
                                            SettingsNavRow(
                                                title = entry.title ?: entry.id ?: entry.slot,
                                                subtitle = entry.module ?: entry.slot,
                                                iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin,
                                                onClick = { activePluginComponent = entry },
                                            )
                                            if (index != pluginComponents.lastIndex) {
                                                HorizontalDivider(
                                                    thickness = 0.5.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 右侧内容面板：与 dsh-upstream 对应 settings section 一一对应。
                    when (section) {
                        SettingsSectionTab.ACCOUNT -> SettingsAccountPane(
                            accountName = accountName,
                            accountBalance = accountBalance,
                            accountPhone = accountPhone.ifEmpty { "未绑定手机" },
                            isSignedIn = isSignedIn,
                        avatarUrl = avatarUrl,
                            onManageAccount = onManageAccount,
                    onOpenUpdate = { activeSection = SettingsSectionTab.UPDATE },
                            onSignIn = onSignIn,
                            onSignOut = onSignOut,
                        )

                        SettingsSectionTab.GENERAL -> SettingsGeneralPane(
                            settings = currentSettings,
                            onUpdateSettings = {
                                currentSettings = it
                                onUpdateSettings(it)
                            },
                        )

                        SettingsSectionTab.MODELS -> SettingsModelsPane(
                            accountSignedIn = isSignedIn,
                            accountName = accountName,
                            accountModels = availableModels,
                            providersRaw = modelProvidersRaw,
                            settingsRaw = modelSettingsRaw,
                            credentialsRaw = modelCredentialsRaw,
                            onOpenConfigFile = onOpenSettingsDocument,
                            onSaveProvider = onSaveModelProvider,
                            bridgeRegistry = bridgeRegistry,
                            pluginComponentRefresh = pluginComponentRefresh,
                        )

                        SettingsSectionTab.PLUGINS -> SettingsPluginsPane(
                            plugins = builtInPlugins,
                            snapshot = pluginSnapshot,
                            onOpenConfigFile = onOpenSettingsDocument
                        )

                        SettingsSectionTab.AGENT_PRESETS -> SettingsAgentPresetsPane(
                            selectedPresetId = selectedAgentPreset,
                            presetGroups = pluginSnapshot?.agentPresets.orEmpty(),
                            onSelectPreset = { preset ->
                                selectedAgentPreset = preset
                                val updated = currentSettings.copy(transcriptView = preset)
                                currentSettings = updated
                                onUpdateSettings(updated)
                            },
                        )

                        SettingsSectionTab.ARCHIVED -> SettingsArchivedPane()

                        SettingsSectionTab.MOBILE_RUNTIME -> SettingsMobileRuntimePane(
                            settings = currentSettings,
                            onUpdateSettings = {
                                currentSettings = it
                                onUpdateSettings(it)
                            },
                            runtimeStatus = runtimeStatus,
                            onStartRuntime = onStartRuntime,
                            onStopRuntime = onStopRuntime,
                            onCreateBackup = onCreateBackup,
                            onExportDiagnostics = onExportDiagnostics,
                        )

                        SettingsSectionTab.NOTIFICATIONS -> SettingsNotificationsPane(
                            settings = currentSettings,
                            onUpdateSettings = {
                                currentSettings = it
                                onUpdateSettings(it)
                            },
                            onRequestNotificationPermission = onRequestNotificationPermission,
                        )

                        SettingsSectionTab.REMOTE -> SettingsRemotePane(
                            settings = currentSettings,
                            onUpdateSettings = {
                                currentSettings = it
                                onUpdateSettings(it)
                            },
                            onConnectRemote = onConnectRemote,
                            onDisconnectRemote = onDisconnectRemote,
                        )

                        SettingsSectionTab.UPDATE -> SettingsUpdatePane()
                    }
                }
            }
        }
    }
}


/** 设置列表行：插件声明的条目用这个重载（只有标题、说明与图标）。 */
@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String?,
    iconResId: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 插件设置页的占位内容。
 *
 * 这一版只打通「入口出现」：真正渲染插件自己的表单需要 Host 的 schema 投影与保存
 * 通道，留到下一步。这里明确写出来，避免看起来像加载失败。
 */
@Composable
private fun PluginSettingsStub(
    page: com.deepseek.dsh.core.model.PluginSettingsPage,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回设置",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            page.description?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = "该插件的设置界面尚未接入，条目已按 Host 声明生成。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = page.module ?: page.id,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp,
            )
        }
    }
}

/** 设置列表行：图标 + 标签 + 右箭头（移动端大触控行高）。 */
@Composable
private fun SettingsNavRow(
    tab: SettingsSectionTab,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = tab.iconResId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = tab.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}














