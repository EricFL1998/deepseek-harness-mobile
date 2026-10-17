package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.feature.settings.R

/**
 * 官方 6 大核心 Section 分区 (对齐 dsh-upstream/packages/client/ui-settings-general)
 * + 手机移动端特有分区 (MOBILE_RUNTIME, MOBILE_WORKSPACE)
 */
enum class SettingsSectionTab(val id: String, val title: String, val icon: ImageVector) {
    ACCOUNT("account", "账号与额度", Icons.Default.Person),
    MODELS("models", "模型管理", Icons.Default.AutoAwesome),
    AGENT_PRESETS("agent-presets", "预设与偏好", Icons.Default.Tune),
    PLUGINS("plugins", "内置插件", Icons.Default.Widgets),
    ARCHIVED("archived-sessions", "归档会话", Icons.Default.Archive),
    MOBILE_RUNTIME("mobile-runtime", "移动运行时", Icons.Default.Memory),
    MOBILE_WORKSPACE("mobile-workspace", "手机工作区", Icons.Default.Folder)
}

/**
 * 手机端深度对齐官方 SettingsRoot 的双层结构：
 * 顶部/侧向分类 Rail + 内容选项 Panel + 手机端专属特性配置
 */
@Composable
fun OfficialSettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    accountName: String = "DeepSeek 用户",
    accountBalance: String = "¥60.00",
    initialSection: SettingsSectionTab = SettingsSectionTab.ACCOUNT,
    onManageAccount: () -> Unit = {},
    onCreateBackup: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(initialSection) }
    var currentSettings by remember { mutableStateOf(settings) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF161719))
            .padding(top = 16.dp)
    ) {
        // 1. 顶部横向 Section 导航栏 (对齐官方 800x800 Nav Rail 的移动端自适应表现)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsSectionTab.values().forEach { tab ->
                val isSelected = tab == selectedTab
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF2C2F36) else Color(0xFF212327),
                    onClick = { selectedTab = tab },
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF3875F6) else Color(0xFF9E9EA4),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isSelected) Color.White else Color(0xFF9E9EA4)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = Color(0xFF26282D),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 2. 当前分区内容区 (长页面平滑滚动)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                SettingsSectionTab.ACCOUNT -> {
                    item {
                        OfficialSectionCard(title = "DeepSeek 官方账号与额度") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onManageAccount() }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3875F6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("DS", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = accountName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "状态: 已通过官方 OAuth 授权",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9E9EA4)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF2C2F36)
                                ) {
                                    Text(
                                        text = accountBalance,
                                        color = Color(0xFF3875F6),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    item {
                        OfficialSectionCard(title = "订阅管理与账单") {
                            OfficialSettingRow(
                                icon = Icons.Default.Security,
                                title = "打开平台管理后台",
                                subtitle = "前往 platform.deepseek.com 查看 API 计费、账单与充值记录",
                                onClick = onManageAccount
                            )
                        }
                    }
                }

                SettingsSectionTab.MODELS -> {
                    item {
                        OfficialSectionCard(title = "官方模型与接入") {
                            OfficialSettingRow(
                                icon = Icons.Default.AutoAwesome,
                                title = "DeepSeek-V3",
                                subtitle = "通用基座大模型 · 官方推荐默认",
                                onClick = {}
                            )
                            OfficialSettingRow(
                                icon = Icons.Default.AutoAwesome,
                                title = "DeepSeek-R1",
                                subtitle = "深度推理与强化学习模型",
                                onClick = {}
                            )
                        }
                    }
                    item {
                        OfficialSectionCard(title = "移动端 API Key 备用") {
                            OfficialSettingRow(
                                icon = Icons.Default.Key,
                                title = "自定义 API 密钥",
                                subtitle = "未登录 OAuth 时可使用 sk-... 密钥直接调用",
                                onClick = {}
                            )
                        }
                    }
                }

                SettingsSectionTab.AGENT_PRESETS -> {
                    item {
                        OfficialSectionCard(title = "工作过程模式 (Onboarding Presets)") {
                            OfficialSettingRow(
                                icon = Icons.Default.Tune,
                                title = "Compact (紧凑模式)",
                                subtitle = "折叠中间过程，仅展示关键结论与最终产物",
                                onClick = {}
                            )
                            OfficialSettingRow(
                                icon = Icons.Default.Tune,
                                title = "Standard (标准模式)",
                                subtitle = "平衡展示工具执行步骤与关键日志",
                                onClick = {}
                            )
                            OfficialSettingRow(
                                icon = Icons.Default.Tune,
                                title = "Detailed (详细模式)",
                                subtitle = "全量打印工具参数、标准输出与详细调用耗时",
                                onClick = {}
                            )
                        }
                    }
                }

                SettingsSectionTab.PLUGINS -> {
                    item {
                        OfficialSectionCard(title = "官方内置插件配置") {
                            OfficialSettingRow(
                                icon = Icons.Default.Widgets,
                                title = "dsh-subscription-oauth",
                                subtitle = "订阅 OAuth 授权与配额管理 (已安装在 composer.dock)",
                                onClick = {}
                            )
                            OfficialSettingRow(
                                icon = Icons.Default.Widgets,
                                title = "dsh-context",
                                subtitle = "长上下文与会话压缩增强插件",
                                onClick = {}
                            )
                            OfficialSettingRow(
                                icon = Icons.Default.Widgets,
                                title = "dsh-pet",
                                subtitle = "系统状态监视浮层与桌宠",
                                onClick = {}
                            )
                        }
                    }
                }

                SettingsSectionTab.ARCHIVED -> {
                    item {
                        OfficialSectionCard(title = "已归档的会话与快照") {
                            OfficialSettingRow(
                                icon = Icons.Default.Archive,
                                title = "查看归档记录",
                                subtitle = "没有已归档的历史长会话",
                                onClick = {}
                            )
                        }
                    }
                }

                SettingsSectionTab.MOBILE_RUNTIME -> {
                    item {
                        OfficialSectionCard(title = "手机 Linux 容器策略 (特色)") {
                            OfficialSwitchRow(
                                icon = Icons.Default.PowerSettingsNew,
                                title = "空闲自动休眠",
                                subtitle = "无交互 5 分钟后停止容器进程，极大降低手机功耗与发热",
                                checked = currentSettings.idleStopMinutes > 0,
                                onCheckedChange = { checked ->
                                    currentSettings = currentSettings.copy(idleStopMinutes = if (checked) 5 else 0)
                                    onUpdateSettings(currentSettings)
                                }
                            )
                            OfficialSwitchRow(
                                icon = Icons.Default.Memory,
                                title = "后台长任务守护",
                                subtitle = "任务执行期间启用前台服务与系统唤醒锁 (WakeLock)",
                                checked = currentSettings.keepAliveOnTask,
                                onCheckedChange = { checked ->
                                    currentSettings = currentSettings.copy(keepAliveOnTask = checked)
                                    onUpdateSettings(currentSettings)
                                }
                            )
                            OfficialSwitchRow(
                                icon = Icons.Default.Lock,
                                title = "高危工具二次确认",
                                subtitle = "执行覆盖文件、Shell、系统硬件等动作前强制弹窗审批",
                                checked = currentSettings.highRiskConfirm,
                                onCheckedChange = { checked ->
                                    currentSettings = currentSettings.copy(highRiskConfirm = checked)
                                    onUpdateSettings(currentSettings)
                                }
                            )
                        }
                    }
                    item {
                        OfficialSectionCard(title = "维护与诊断导出") {
                            OfficialSettingRow(
                                icon = Icons.Default.Archive,
                                title = "创建用户数据快照",
                                subtitle = "备份 \$DSH_HOME (会话、配置、凭据与插件)",
                                onClick = onCreateBackup
                            )
                            OfficialSettingRow(
                                icon = Icons.Default.BugReport,
                                title = "导出脱敏诊断日志包",
                                subtitle = "自动对 token 和密钥脱敏，导出 ZIP 便于排障",
                                onClick = onExportDiagnostics
                            )
                        }
                    }
                }

                SettingsSectionTab.MOBILE_WORKSPACE -> {
                    item {
                        OfficialSectionCard(title = "手机公有存储工作区 (特色)") {
                            OfficialSettingRow(
                                icon = Icons.Default.Folder,
                                title = "工作区真实目录",
                                subtitle = currentSettings.workspacePath,
                                onClick = {}
                            )
                            Text(
                                text = "容器内自动 bind 挂载为 /workspace，系统自带文件管理器可直接查阅与导出产物。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6E7077),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF9E9EA4),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF212327),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun OfficialSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF9E9EA4),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Color.White)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6E7077)
            )
        }
    }
}

@Composable
private fun OfficialSwitchRow(
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
            tint = Color(0xFF9E9EA4),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Color.White)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6E7077)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
