package com.deepseek.dsh.feature.settings

import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.PluginInventorySnapshotInfo
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 6 大标准 Section (对齐 dsh-upstream/packages/client/ui-settings-*)
 * + 手机专属 2 大增强 Section (mobile-runtime, mobile-workspace)
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
    MODELS("models", "模型", "DeepSeek-V3 / R1 与 API Key 配置", com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_model),
    PLUGINS("plugins", "内置插件", "已安装插件与配置", com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_personalization),
    AGENT_PRESETS("agent-presets", "Agent 预设", "工作过程模式：紧凑 / 标准 / 详细", com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_agent_preset),
    ARCHIVED("archived-sessions", "归档会话", "已归档的会话记录与快照", com.deepseek.dsh.core.designsystem.R.drawable.ic_row_archive),
    MOBILE_RUNTIME("mobile-runtime", "移动运行时", "Linux 容器休眠、长任务守护与高危拦截", com.deepseek.dsh.core.designsystem.R.drawable.ic_menu_settings),
    MOBILE_WORKSPACE("mobile-workspace", "手机工作区", "公有存储绑定、系统文件可见性", com.deepseek.dsh.core.designsystem.R.drawable.ic_folder_open),
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
    availableModels: List<String> = emptyList(),
    modelProvidersRaw: String? = null,
    modelSettingsRaw: String? = null,
    modelCredentialsRaw: String? = null,
    onSetModelApiKey: (String, String) -> Unit = { _, _ -> },
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
    var currentSettings by remember(settings) { mutableStateOf(settings) }
    var selectedAgentPreset by remember { mutableStateOf(settings.transcriptView) }

    BackHandler(enabled = true) {
        if (activeSection != null) activeSection = null else onClose()
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
            // 顶部 Header：圆底返回按钮 + 居中标题（与全屏子页面统一的 72dp 高度）
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

            AnimatedContent(
                targetState = activeSection,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "settings_nav"
            ) { section ->
                if (section == null) {
                    // 移动端设置主页：分组卡片内的分区行（图标 + 标签 + 箭头）。
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    ) {
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
                            onSetApiKey = onSetModelApiKey,
                        )

                        SettingsSectionTab.PLUGINS -> SettingsPluginsPane(
                            plugins = builtInPlugins,
                            snapshot = pluginSnapshot,
                            onOpenConfigFile = onOpenSettingsDocument
                        )

                        SettingsSectionTab.AGENT_PRESETS -> SettingsAgentPresetsPane(
                            selectedPresetId = selectedAgentPreset,
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
                        SettingsSectionTab.MOBILE_WORKSPACE -> SettingsMobileWorkspacePane(
                            settings = currentSettings,
                        )
                    }
                }
            }
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










