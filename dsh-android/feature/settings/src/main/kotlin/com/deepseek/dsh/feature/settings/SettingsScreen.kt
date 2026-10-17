package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * v4.0 设计文档 §6.1 & §13.2: 手机端设置主界面
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onCreateBackup: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentSettings by remember { mutableStateOf(settings) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "设置与偏好",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "模型、本地 Linux 容器策略、文件工作区与安全防护",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 1. 模型与推理设置
        item {
            SettingsGroup(title = "模型与推理") {
                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "默认模型",
                    subtitle = currentSettings.defaultModel,
                    onClick = {}
                )
                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "默认推理等级",
                    subtitle = currentSettings.defaultReasoningLevel,
                    onClick = {}
                )
            }
        }

        // 2. 本地 Linux 运行时生命周期 (§4.4)
        item {
            SettingsGroup(title = "本地容器与生命周期") {
                SettingsSwitchRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "空闲自动休眠",
                    subtitle = "空闲 ${currentSettings.idleStopMinutes} 分钟后停止容器，节省手机电量",
                    checked = currentSettings.idleStopMinutes > 0,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(idleStopMinutes = if (checked) 5 else 0)
                        onUpdateSettings(currentSettings)
                    }
                )
                SettingsSwitchRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "后台长任务守护",
                    subtitle = "任务执行期间启用前台服务与唤醒锁，防止系统杀后台",
                    checked = currentSettings.keepAliveOnTask,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(keepAliveOnTask = checked)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
        }

        // 3. 文件工作区 (§8.1)
        item {
            SettingsGroup(title = "文件与存储") {
                SettingsRow(
                    icon = Icons.Default.Folder,
                    title = "工作区真实目录",
                    subtitle = currentSettings.workspacePath,
                    onClick = {}
                )
            }
        }

        // 4. 安全防护与审计 (§12)
        item {
            SettingsGroup(title = "系统安全与防护") {
                SettingsSwitchRow(
                    icon = Icons.Default.Security,
                    title = "高危工具二次确认",
                    subtitle = "执行覆盖文件、Shell、系统硬件等高危动作前必须弹窗审批",
                    checked = currentSettings.highRiskConfirm,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(highRiskConfirm = checked)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
        }

        // 5. 备份与诊断导出 (§4.5 & §4.6)
        item {
            SettingsGroup(title = "维护与诊断") {
                SettingsRow(
                    icon = Icons.Default.Archive,
                    title = "创建用户数据快照",
                    subtitle = "备份 \$DSH_HOME (会话、配置、凭据与插件)",
                    onClick = {
                        onCreateBackup()
                    }
                )
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    title = "导出脱敏诊断日志包",
                    subtitle = "自动对 token 和密钥脱敏，导出 ZIP 便于排障",
                    onClick = {
                        onExportDiagnostics()
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
