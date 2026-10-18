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
                val phase = runtimeStatus?.phase ?: RuntimePhase.STOPPED
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
                                text = "容器运行状态",
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
                                    text = phase.name,
                                    fontSize = 11.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    color = statusColor
                                )
                            }
                        }
                        val detail = buildString {
                            runtimeStatus?.port?.let { append("端口: $it  ") }
                            runtimeStatus?.pid?.let { append("PID: $it  ") }
                            runtimeStatus?.lastError?.let { append("\n异常: $it") }
                        }
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail,
                                style = SettingsText.RowDesc,
                                color = palette.labelTertiary,
                            )
                        }
                    }

                    if (isRunning) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onStopRuntime,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("停止", fontSize = 12.sp)
                        }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = onStartRuntime,
                            enabled = !isStarting,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (isStarting) "启动中..." else "启动", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item(key = "runtime_switches") {
            SettingsCard {
                RuntimeSwitchRow(
                    icon = Icons.Filled.PowerSettingsNew,
                    title = "空闲自动休眠",
                    subtitle = "无交互 5 分钟后停止容器进程，极大降低手机功耗与发热",
                    checked = currentSettings.idleStopMinutes > 0,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(idleStopMinutes = if (checked) 5 else 0)
                        onUpdateSettings(currentSettings)
                    },
                )
                SettingsCardDivider()
                RuntimeSwitchRow(
                    icon = Icons.Filled.Memory,
                    title = "后台长任务守护",
                    subtitle = "任务执行期间启用前台服务与系统唤醒锁 (WakeLock)",
                    checked = currentSettings.keepAliveOnTask,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(keepAliveOnTask = checked)
                        onUpdateSettings(currentSettings)
                    },
                )
                SettingsCardDivider()
                RuntimeSwitchRow(
                    icon = Icons.Filled.Lock,
                    title = "高危工具二次确认",
                    subtitle = "执行覆盖文件、Shell、系统硬件等动作前强制弹窗审批",
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
                    title = "创建用户数据快照",
                    subtitle = "备份 \$DSH_HOME (会话、配置、凭据与插件)",
                    onClick = onCreateBackup,
                )
                SettingsCardDivider()
                RuntimeActionRow(
                    icon = Icons.Filled.BugReport,
                    title = "导出脱敏诊断日志包",
                    subtitle = "自动对 token 和密钥脱敏，导出 ZIP 便于排障",
                    onClick = onExportDiagnostics,
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
internal fun SettingsMobileWorkspacePane(
    settings: AppSettings,
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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "workspace_path") {
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = {})
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = palette.labelSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "工作区真实目录",
                            style = SettingsText.RowTitleMedium,
                            color = palette.labelPrimary,
                        )
                        Text(
                            text = settings.workspacePath,
                            style = SettingsText.RowDesc,
                            color = palette.labelTertiary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SettingsChevronRight()
                }
                SettingsCardDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "容器内自动 bind 挂载为 /workspace，系统自带文件管理器可直接查阅与导出产物。",
                    style = SettingsText.RowDesc,
                    color = palette.labelTertiary,
                )
            }
        }
    }
}

/** 设置行：图标 + 标题/说明 + 右侧开关。 */
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
