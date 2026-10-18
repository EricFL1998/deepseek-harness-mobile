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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.feature.settings.R

/**
 * v4.0 设计文档 §6.1 & §13.2: 手机端设置主界面
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    accountName: String = "",
    accountBalance: String = "¥0.00",
    onManageAccount: () -> Unit = {},
    onCreateBackup: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentSettings by remember { mutableStateOf(settings) }
    val displayAccountName = if (accountName.isNotBlank()) accountName else stringResource(R.string.drawer_user_name)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 1. DeepSeek 账号状态与余额
        item {
            SettingsGroup(title = stringResource(R.string.settings_group_account)) {
                SettingsRow(
                    icon = Icons.Default.Security,
                    title = displayAccountName,
                    subtitle = stringResource(R.string.drawer_balance, accountBalance),
                    onClick = onManageAccount
                )
            }
        }

        // 2. 模型与推理设置
        item {
            SettingsGroup(title = stringResource(R.string.settings_group_model)) {
                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.setting_default_model),
                    subtitle = currentSettings.defaultModel,
                    onClick = {}
                )
                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.setting_default_reasoning),
                    subtitle = currentSettings.defaultReasoningLevel,
                    onClick = {}
                )
            }
        }

        // 3. 本地 Linux 运行时生命周期 (§4.4)
        item {
            SettingsGroup(title = stringResource(R.string.settings_group_lifecycle)) {
                SettingsSwitchRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = stringResource(R.string.setting_idle_sleep),
                    subtitle = stringResource(R.string.setting_idle_sleep_desc, currentSettings.idleStopMinutes),
                    checked = currentSettings.idleStopMinutes > 0,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(idleStopMinutes = if (checked) 5 else 0)
                        onUpdateSettings(currentSettings)
                    }
                )
                SettingsSwitchRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = stringResource(R.string.setting_keep_alive),
                    subtitle = stringResource(R.string.setting_keep_alive_desc),
                    checked = currentSettings.keepAliveOnTask,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(keepAliveOnTask = checked)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
        }

        // 4. 文件工作区 (§8.1)
        item {
            SettingsGroup(title = stringResource(R.string.settings_group_storage)) {
                SettingsRow(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.setting_workspace_path),
                    subtitle = currentSettings.workspacePath,
                    onClick = {}
                )
            }
        }

        // 5. 安全防护与审计 (§12)
        item {
            SettingsGroup(title = stringResource(R.string.settings_group_security)) {
                SettingsSwitchRow(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.setting_high_risk_confirm),
                    subtitle = stringResource(R.string.setting_high_risk_confirm_desc),
                    checked = currentSettings.highRiskConfirm,
                    onCheckedChange = { checked ->
                        currentSettings = currentSettings.copy(highRiskConfirm = checked)
                        onUpdateSettings(currentSettings)
                    }
                )
            }
        }

        // 6. 备份与诊断导出 (§4.5 & §4.6)
        item {
            SettingsGroup(title = stringResource(R.string.settings_group_maintenance)) {
                SettingsRow(
                    icon = Icons.Default.Archive,
                    title = stringResource(R.string.setting_create_backup),
                    subtitle = stringResource(R.string.setting_create_backup_desc),
                    onClick = onCreateBackup
                )
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.setting_export_diag),
                    subtitle = stringResource(R.string.setting_export_diag_desc),
                    onClick = onExportDiagnostics
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
