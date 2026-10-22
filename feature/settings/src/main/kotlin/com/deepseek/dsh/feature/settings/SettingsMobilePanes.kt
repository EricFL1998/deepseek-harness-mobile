package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.runtime.host.RuntimePhase
import com.deepseek.dsh.runtime.host.RuntimeStatus

/**
 * 移动运行时（手机专属设置）。
 *
 * 设置面板没有这一节；沿用右侧面板的卡片材质与行度量：
 * settings card（R20 + 0.5dp 描边）+ 行标题 14/22 500 + 说明 12/18 三级色。
 */
@Composable
internal fun SettingsMobileRuntimePane(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    runtimeStatus: RuntimeStatus? = null,
    onStartRuntime: () -> Unit = {},
    onStopRuntime: () -> Unit = {},
    onCreateBackup: () -> Unit,
    onExportDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentSettings by remember { mutableStateOf(settings) }
    val palette = LocalSettingsPalette.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 运行时状态与运行控制卡片
        item(key = "runtime_status_control") {
            SettingsCard {
                val status = runtimeStatus
                val phase = status?.phase ?: RuntimePhase.STOPPED
                val isRunning = phase == RuntimePhase.RUNNING
                val isStarting = phase == RuntimePhase.STARTING || phase == RuntimePhase.INSTALLING

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = null,
                        tint = if (isRunning) palette.link else palette.labelSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_pane_runtime_status_title),
                                style = SettingsText.RowTitleMedium,
                                color = palette.labelPrimary,
                            )
                            val statusBg = when (phase) {
                                RuntimePhase.RUNNING -> palette.link.copy(alpha = 0.15f)
                                RuntimePhase.ERROR -> palette.error.copy(alpha = 0.15f)
                                else -> palette.cardStroke
                            }
                            val statusColor = when (phase) {
                                RuntimePhase.RUNNING -> palette.link
                                RuntimePhase.ERROR -> palette.error
                                else -> palette.labelSecondary
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(statusBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    // 本地化状态名；直接用 phase.name 会在中文界面里露出
                                    // STARTING / ERROR 这类枚举原文。
                                    text = runtimePhaseLabel(phase),
                                    fontSize = 11.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    color = statusColor
                                )
                            }
                        }
                    }

                    if (isRunning) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onStopRuntime,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.settings_pane_runtime_stop), fontSize = 12.sp)
                        }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = onStartRuntime,
                            // STARTING/INSTALLING 期间不能重复点启动；ERROR 允许重试。
                            enabled = !isStarting,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isStarting) {
                                    stringResource(R.string.settings_pane_runtime_starting)
                                } else {
                                    stringResource(R.string.settings_pane_runtime_start)
                                },
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                // 端口 / PID：启动成功后最关键的定位信息。
                val port = status?.port
                val pid = status?.pid
                val portText = if (port != null) {
                    stringResource(R.string.settings_pane_runtime_port, port)
                } else {
                    null
                }
                val pidText = if (pid != null) {
                    stringResource(R.string.settings_pane_runtime_pid, pid)
                } else {
                    null
                }
                val detail = listOfNotNull(portText, pidText).joinToString("  ")
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = SettingsText.RowDesc,
                        color = palette.labelTertiary,
                        modifier = Modifier.padding(start = 34.dp, end = 4.dp),
                    )
                }

                if (phase == RuntimePhase.ERROR) {
                    // 失败时不再只说「ERROR」：错误文案 + 退出码 + 最近输出一起给，
                    // 用户和开发者都能直接看到 Host 为什么没起来。
                    val lastError = status?.lastError
                    if (!lastError.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_pane_runtime_last_error, lastError),
                            style = SettingsText.RowDesc,
                            color = palette.error,
                            modifier = Modifier.padding(start = 34.dp, end = 4.dp, top = 6.dp),
                        )
                    }
                    val exitCode = status?.exitCode
                    if (exitCode != null) {
                        Text(
                            text = stringResource(R.string.settings_pane_runtime_exit_code, exitCode),
                            style = SettingsText.RowDesc,
                            color = palette.labelTertiary,
                            modifier = Modifier.padding(start = 34.dp, end = 4.dp, top = 2.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_pane_runtime_error_hint),
                        style = SettingsText.RowDesc,
                        color = palette.labelTertiary,
                        modifier = Modifier.padding(start = 34.dp, end = 4.dp, top = 4.dp),
                    )
                    RuntimeOutputBlock(logTail = status?.logTail.orEmpty())
                } else if (isStarting) {
                    Text(
                        text = stringResource(R.string.settings_pane_runtime_starting_hint),
                        style = SettingsText.RowDesc,
                        color = palette.labelTertiary,
                        modifier = Modifier.padding(start = 34.dp, end = 4.dp, top = 4.dp),
                    )
                }
            }
        }

        item(key = "runtime_switches") {
            SettingsCard {
                RuntimeSwitchRow(
                    icon = Icons.Filled.PowerSettingsNew,
                    title = stringResource(R.string.settings_pane_idle_sleep_title),
                    subtitle = stringResource(R.string.settings_pane_idle_sleep_desc),
                    checked = currentSettings.idleStopMinutes > 0,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(idleStopMinutes = if (checked) 5 else 0)
                        onUpdateSettings(currentSettings)
                    },
                )
                SettingsCardDivider()
                RuntimeSwitchRow(
                    icon = Icons.Filled.Memory,
                    title = stringResource(R.string.settings_pane_keep_alive_title),
                    subtitle = stringResource(R.string.settings_pane_keep_alive_desc),
                    checked = currentSettings.keepAliveOnTask,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(keepAliveOnTask = checked)
                        onUpdateSettings(currentSettings)
                    },
                )
                SettingsCardDivider()
                RuntimeSwitchRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.settings_pane_high_risk_title),
                    subtitle = stringResource(R.string.settings_pane_high_risk_desc),
                    checked = currentSettings.highRiskConfirm,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(highRiskConfirm = checked)
                        onUpdateSettings(currentSettings)
                    },
                )
            }
        }
        item(key = "runtime_maintenance") {
            SettingsCard {
                RuntimeActionRow(
                    icon = Icons.Filled.Archive,
                    title = stringResource(R.string.settings_pane_backup_title),
                    subtitle = stringResource(R.string.settings_pane_backup_desc),
                    onClick = onCreateBackup,
                )
                SettingsCardDivider()
                RuntimeActionRow(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.settings_pane_diagnostics_title),
                    subtitle = stringResource(R.string.settings_pane_diagnostics_desc),
                    onClick = onExportDiagnostics,
                )
            }
        }
    }
}

/** 运行时阶段徽标文案；枚举名不进 UI。 */
@Composable
private fun runtimePhaseLabel(phase: RuntimePhase): String = stringResource(
    when (phase) {
        RuntimePhase.UNINSTALLED -> R.string.settings_pane_runtime_phase_uninstalled
        RuntimePhase.INSTALLING -> R.string.settings_pane_runtime_phase_installing
        RuntimePhase.STOPPED -> R.string.settings_pane_runtime_phase_stopped
        RuntimePhase.STARTING -> R.string.settings_pane_runtime_phase_starting
        RuntimePhase.RUNNING -> R.string.settings_pane_runtime_phase_running
        RuntimePhase.ERROR -> R.string.settings_pane_runtime_phase_error
    }
)

/**
 * Host 最近输出（已由 runner 脱敏）。
 *
 * 启动失败时这是唯一能解释「为什么没起来」的东西：最多 8 行、等宽小字，
 * 没有输出时给占位文案，而不是留一块空白。
 */
@Composable
private fun RuntimeOutputBlock(logTail: List<String>) {
    val palette = LocalSettingsPalette.current
    val lines = logTail.takeLast(8)
    Text(
        text = stringResource(R.string.settings_pane_runtime_log_title),
        fontSize = 11.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        color = palette.labelSecondary,
        modifier = Modifier.padding(start = 34.dp, end = 4.dp, top = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 34.dp, end = 4.dp, top = 2.dp)
    ) {
        if (lines.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_pane_runtime_log_empty),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = palette.labelTertiary,
            )
        } else {
            lines.forEach { line ->
                Text(
                    text = line,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = palette.labelTertiary,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 手机工作区（手机专属设置）。
 *
 * 与设置卡片同一材质：工作区真实目录行 + 容器挂载说明。
 */
@Composable
private fun RuntimeSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SettingsText.RowTitleMedium,
                color = palette.labelPrimary,
            )
            Text(
                text = subtitle,
                style = SettingsText.RowDesc,
                color = palette.labelTertiary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 设置行：图标 + 标题/说明 + 右侧 chevron。 */
@Composable
private fun RuntimeActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SettingsText.RowTitleMedium,
                color = palette.labelPrimary,
            )
            Text(
                text = subtitle,
                style = SettingsText.RowDesc,
                color = palette.labelTertiary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SettingsChevronRight()
    }
}

@Composable
internal fun SettingsNotificationsPane(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var current by remember(settings) { mutableStateOf(settings) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsPaneTitle(stringResource(R.string.settings_section_notifications)) }
        item {
            SettingsCard {
                NotificationSwitchRow(
                    title = stringResource(R.string.settings_pane_notifications_enable_title),
                    subtitle = stringResource(R.string.settings_pane_notifications_enable_desc),
                    checked = current.notificationsEnabled,
                ) {
                    current = current.copy(notificationsEnabled = it)
                    onUpdateSettings(current)
                }
                SettingsCardDivider()
                NotificationSwitchRow(
                    title = stringResource(R.string.settings_pane_notifications_turn_title),
                    subtitle = stringResource(R.string.settings_pane_notifications_turn_desc),
                    checked = current.notifyOnTurnComplete,
                ) {
                    current = current.copy(notifyOnTurnComplete = it)
                    onUpdateSettings(current)
                }
                SettingsCardDivider()
                NotificationSwitchRow(
                    title = stringResource(R.string.settings_pane_notifications_error_title),
                    subtitle = stringResource(R.string.settings_pane_notifications_error_desc),
                    checked = current.notifyOnError,
                ) {
                    current = current.copy(notifyOnError = it)
                    onUpdateSettings(current)
                }
            }
        }
        item {
            SettingsCard(onClick = onRequestNotificationPermission) {
                Text(
                    text = stringResource(R.string.settings_pane_notifications_permission_title),
                    style = SettingsText.RowTitle,
                    color = LocalSettingsPalette.current.labelPrimary,
                )
                Text(
                    text = stringResource(R.string.settings_pane_notifications_permission_desc),
                    style = SettingsText.RowDesc,
                    color = LocalSettingsPalette.current.labelTertiary,
                )
            }
        }
    }
}

@Composable
private fun NotificationSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SettingsText.RowTitle, color = LocalSettingsPalette.current.labelPrimary)
            Text(subtitle, style = SettingsText.RowDesc, color = LocalSettingsPalette.current.labelTertiary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SettingsRemotePane(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onConnectRemote: (String, String) -> Unit = { _, _ -> },
    onDisconnectRemote: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var url by remember(settings) { mutableStateOf(settings.remoteHostUrl) }
    var token by remember(settings) { mutableStateOf(settings.remoteHostToken) }
    val palette = LocalSettingsPalette.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsPaneTitle(stringResource(R.string.settings_section_remote)) }
        item {
            SettingsCard {
                Text(stringResource(R.string.settings_pane_remote_host_label), style = SettingsText.RowTitle, color = palette.labelPrimary)
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("http://192.168.1.10:45153") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Text(stringResource(R.string.settings_pane_remote_token_label), style = SettingsText.RowTitle, color = palette.labelPrimary, modifier = Modifier.padding(top = 12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    placeholder = { Text("token") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            onUpdateSettings(settings.copy(remoteHostUrl = url.trim(), remoteHostToken = token.trim()))
                            onConnectRemote(url.trim(), token.trim())
                        },
                        enabled = url.isNotBlank(),
                    ) { Text(stringResource(R.string.settings_pane_remote_connect)) }
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            onUpdateSettings(settings.copy(remoteHostUrl = "", remoteHostToken = ""))
                            onDisconnectRemote()
                        },
                    ) { Text(stringResource(R.string.settings_pane_remote_disconnect)) }
                }
                Text(
                    text = if (url.isBlank()) {
                        stringResource(R.string.settings_pane_remote_local_host)
                    } else {
                        stringResource(R.string.settings_pane_remote_active_host, url)
                    },
                    style = SettingsText.RowDesc,
                    color = palette.labelTertiary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

