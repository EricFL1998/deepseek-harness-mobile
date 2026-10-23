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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class SettingsContentKey(
    val section: SettingsSectionTab?,
    val componentId: String?,
    val settingsPageId: String?,
    val native: Boolean,
)

/**
 * 6 大标准 Section (对齐 dsh-upstream/packages/client/ui-settings-*)
 * + 手机专属增强 Section (mobile-runtime)
 */
enum class SettingsSectionTab(
    val id: String,
    val titleResId: Int,
    val subtitleResId: Int,
    val iconResId: Int,
) {
    // 顺序与 settings 导航一致（order: account -10, general 0, models 10, plugins 15, agent-presets 20）
    ACCOUNT("account", R.string.settings_section_account, R.string.settings_section_account_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_user),
    GENERAL("general", R.string.settings_section_general, R.string.settings_section_general_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_menu_settings),
    MODELS("models", R.string.settings_section_models, R.string.settings_section_models_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_model),
    PLUGINS("plugins", R.string.settings_section_plugins, R.string.settings_section_plugins_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_personalization),
    AGENT_PRESETS("agent-presets", R.string.settings_section_agent_presets, R.string.settings_section_agent_presets_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_agent_preset),
    MOBILE_RUNTIME("mobile-runtime", R.string.settings_section_mobile_runtime, R.string.settings_section_mobile_runtime_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_menu_settings),
    NOTIFICATIONS("notifications", R.string.settings_section_notifications, R.string.settings_section_notifications_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_schedule),
    REMOTE("remote", R.string.settings_section_remote, R.string.settings_section_remote_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin),
    UPDATE("update", R.string.settings_section_update, R.string.settings_section_update_desc, com.deepseek.dsh.core.designsystem.R.drawable.ic_row_archive),
}

/**
 * 手机端自适应 SettingsRoot：
 * 手机竖屏状态下，设置主页采用左侧导航（分区列表）-> 点击深入右侧 Section 面板，
 * 支持平滑返回；每个内容页严格对齐 dsh-upstream 对应 settings section 的右侧面板样式。
 */
/**
 * 设置主页的分区顺序来自后端：官方的 `settings.section` 槽注册（bridge 的
 * client-slots）给出每个分区的 id 与 order。App 只负责“这个 id 怎么画”，
 * 排序与「是否出现」都以注册为准；手机专属分区（运行环境/通知/远程/更新）不在
 * 官方注册里，作为附加项排在官方分区之后。
 *
 * @param registrations 插件槽注册（含 `settings.section`）。
 * @returns 排好序的分区列表。
 */
private fun orderedSections(
    registrations: List<com.deepseek.dsh.core.model.PluginSlotEntry>,
): List<SettingsSectionTab> {
    val backendOrder = registrations
        .filter { it.slot == "settings.section" }
        .mapNotNull { entry -> entry.order?.let { order -> entry.id to order } }
        .toMap()
    val mobileOnly = setOf(
        SettingsSectionTab.MOBILE_RUNTIME.id,
        SettingsSectionTab.NOTIFICATIONS.id,
        SettingsSectionTab.REMOTE.id,
        SettingsSectionTab.UPDATE.id,
    )
    return SettingsSectionTab.values().sortedWith(
        compareBy(
            { tab ->
                if (backendOrder.isEmpty() || tab.id in mobileOnly) {
                    // 没有后端数据时保持声明顺序；手机专属分区固定排在官方分区之后。
                    if (backendOrder.isEmpty()) tab.ordinal else 1000 + tab.ordinal
                } else {
                    backendOrder[tab.id] ?: (1000 + tab.ordinal)
                }
            },
        ),
    )
}

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
    /** 原生伴生页面调用 Host 插件 RPC；参数原样传递，不包 args。 */
    pluginRpc: (suspend (method: String, payload: kotlinx.serialization.json.JsonObject) -> Result<String>)? = null,
    /** 当前界面语言选择：`system` / `zh` / `en`。 */
    languageTag: String = "system",
    /** 用户切换界面语言（同时同步 Host）。 */
    onLanguageChange: (String) -> Unit = {},
    /** 读取一份插件设置表单。 */
    loadPluginSettings: suspend (String) -> com.deepseek.dsh.core.model.PluginSettingsDocument? = { null },
    /** 回写插件设置字段。 */
    savePluginSettings: suspend (String, kotlinx.serialization.json.JsonObject, Int) -> Boolean =
        { _, _, _ -> false },
    availableModels: List<String> = emptyList(),
    modelProvidersRaw: String? = null,
    modelSettingsRaw: String? = null,
    modelCredentialsRaw: String? = null,
    /** Host 当前已注册（可路由）的 provider id；它们不该再出现在“添加”列表里。 */
    routableProviders: List<String> = emptyList(),
    onSaveModelProvider: (ModelProviderUi, String, String, List<String>) -> Unit = { _, _, _, _ -> },
    /** 无自定义预设时，让 Agent 在会话里创建一个（官方 CTA）。 */
    onCreateAgentPreset: () -> Unit = {},
    /** 打开模型设置/添加弹窗时从 Host 重新拉提供商目录（不用旧快照）。 */
    onRefreshModelProviders: () -> Unit = {},
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
    val nativePageRegistrations by com.deepseek.dsh.plugin.nativehost.NativePageRegistry
        .registrations.collectAsState()

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
            // 插件界面（settings.section）不再隐藏顶层 Header：它要像普通设置项一样
            // 留在设置页框架里，由顶层 Header 负责返回，插件页自己不再画一条 header。
            val subPageOpen = activePluginPage != null
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
                            onClick = {
                                when {
                                    activePluginComponent != null -> activePluginComponent = null
                                    activeSection == null -> onClose()
                                    else -> activeSection = null
                                }
                            },
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
                            text = activePluginComponent?.title
                                ?: activeSection?.let { stringResource(it.titleResId) }
                                ?: stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = textPrimary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }
            }

            val activeEntry = activePluginComponent
            val activeComponentId = activeEntry?.resolvedComponentId()
                ?: activeEntry?.let { "${it.module}#${it.slot}" }
            // 原生页匹配：先精确 id，再标题，最后在「该插件只有一个 settings.section」
            // 时按一一对应兜底。未命中时把原因带到 Web 兜底页上，避免用户只看到
            // 「还是 Web UI」而不知道是伴侣没加载、没注册设置页，还是 id 对不上。
            val activeNativeMatch = activeComponentId?.let { componentId ->
                val sectionCount = activeEntry?.module?.let { module ->
                    pluginSlotEntries.count {
                        it.module == module && it.slot == "settings.section"
                    }
                } ?: 0
                com.deepseek.dsh.plugin.nativehost.NativePageRegistry.findSettingsSection(
                    componentId = componentId,
                    title = activeEntry?.title,
                    registrations = nativePageRegistrations,
                    allowSinglePageFallback = sectionCount == 1,
                )
            }
            val activeNativePage = activeNativeMatch?.page
            AnimatedContent(
                targetState = SettingsContentKey(
                    section = activeSection,
                    componentId = activeComponentId,
                    settingsPageId = activePluginPage?.id,
                    native = activeNativePage != null,
                ),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "settings_nav"
            ) { content ->
                if (content.componentId != null && activeEntry != null) {
                    val entry = activeEntry
                    val componentId = content.componentId
                    val nativePage = activeNativePage
                    if (nativePage != null) {
                        // A loaded companion owns this exact settings.section. Do not even
                        // create the Web fallback: OAuth and quota updates stay on Host RPC.
                        com.deepseek.dsh.plugin.nativehost.NativePluginComponentPane(
                            page = nativePage,
                            componentId = componentId,
                            spec = kotlinx.serialization.json.JsonObject(emptyMap()),
                            onBack = { activePluginComponent = null },
                            pluginRpc = pluginRpc,
                            dispatchEvent = { id, event, payload ->
                                bridgeRegistry?.dispatchUiEvent(id, event, payload)
                            },
                            refreshEvents = pluginComponentRefresh,
                            showHeader = false,
                        )
                    } else {
                        PluginComponentPane(
                            componentId = componentId,
                            title = entry.title ?: entry.identity ?: entry.slot,
                            registry = bridgeRegistry,
                            onBack = { activePluginComponent = null },
                            refreshEvents = pluginComponentRefresh,
                            hostPort = hostPort,
                            hostToken = hostToken,
                            hostPortProvider = hostPortProvider,
                            nativeFallbackReason = activeNativeMatch?.reason,
                            showHeader = false,
                        )
                    }
                } else if (content.settingsPageId != null && activePluginPage != null) {
                    val page = activePluginPage!!
                    PluginSettingsPage(
                        ns = page.id,
                        title = page.title,
                        description = page.description,
                        loadDocument = loadPluginSettings,
                        saveDocument = savePluginSettings,
                        onBack = { activePluginPage = null },
                    )
                } else if (content.section == null) {
                    // 移动端设置主页：官方分区与插件入口共用同一张设置卡，插件入口紧挨官方设置。
                    val pluginEntryRows = if (bridgeRegistry != null) {
                        pluginSlotEntries.filter {
                            it.slot == "settings.section" && it.isThirdPartyPlugin
                        }
                    } else {
                        emptyList()
                    }
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
                                    val totalRows = orderedSections(pluginSlotEntries).size +
                                        pluginEntryRows.size +
                                        pluginSettingsPages.size
                                    var rowIndex = 0
                                    orderedSections(pluginSlotEntries).forEach { tab ->
                                        SettingsNavRow(
                                            tab = tab,
                                            onClick = { activeSection = tab },
                                        )
                                        if (rowIndex != totalRows - 1) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                            )
                                        }
                                        rowIndex += 1
                                    }
                                    // 插件注册的 settings.section 入口：直接排在官方设置后面，
                                    // 不再单独成组。
                                    pluginEntryRows.forEach { entry ->
                                        SettingsNavRow(
                                            title = entry.title ?: entry.id ?: entry.slot,
                                            // 不展示插件包名：设置列表只给用户看得懂的入口名。
                                            subtitle = null,
                                            iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin,
                                            onClick = { activePluginComponent = entry },
                                        )
                                        if (rowIndex != totalRows - 1) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                            )
                                        }
                                        rowIndex += 1
                                    }
                                    // 插件声明了 Config 的设置页（settings.page）同样并入这张卡。
                                    pluginSettingsPages.forEach { page ->
                                        SettingsNavRow(
                                            title = page.title,
                                            subtitle = page.description ?: page.id,
                                            iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin,
                                            onClick = { activePluginPage = page },
                                        )
                                        if (rowIndex != totalRows - 1) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                            )
                                        }
                                        rowIndex += 1
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 右侧内容面板：与 dsh-upstream 对应 settings section 一一对应。
                    when (content.section) {
                        SettingsSectionTab.ACCOUNT -> SettingsAccountPane(
                            accountName = accountName,
                            accountBalance = accountBalance,
                            accountPhone = accountPhone.ifEmpty { stringResource(R.string.settings_misc_phone_unbound) },
                            isSignedIn = isSignedIn,
                        avatarUrl = avatarUrl,
                            onManageAccount = onManageAccount,
                    onOpenUpdate = { activeSection = SettingsSectionTab.UPDATE },
                            onSignIn = onSignIn,
                            onSignOut = onSignOut,
                        )

                        SettingsSectionTab.GENERAL -> SettingsGeneralPane(
                            settings = currentSettings,
                            languageTag = languageTag,
                            onLanguageChange = onLanguageChange,
                            itemOrder = pluginSlotEntries
                                .filter { it.slot == "settings.general.item" }
                                .mapNotNull { entry ->
                                    entry.id?.let { id -> id to entry.order }
                                }
                                .toMap(),
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
                            onRefreshProviders = onRefreshModelProviders,
                            routableProviders = routableProviders,
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
                            onCreatePreset = onCreateAgentPreset,
                            onSelectPreset = { preset ->
                                selectedAgentPreset = preset
                                val updated = currentSettings.copy(transcriptView = preset)
                                currentSettings = updated
                                onUpdateSettings(updated)
                            },
                        )

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
                contentDescription = stringResource(R.string.settings_misc_back),
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
                text = stringResource(R.string.settings_misc_page_not_ready),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
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
            text = stringResource(tab.titleResId),
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














