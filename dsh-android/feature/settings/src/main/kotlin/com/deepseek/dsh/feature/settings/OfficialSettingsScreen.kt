package com.deepseek.dsh.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 官方 6 大标准 Section (对齐 dsh-upstream/packages/client/ui-settings-general)
 * + 手机专属 2 大增强 Section (mobile-runtime, mobile-workspace)
 */
enum class SettingsSectionTab(val id: String, val title: String, val subtitle: String, val icon: ImageVector) {
    ACCOUNT("account", "账号与额度", "DeepSeek 官方 OAuth 授权与余额", Icons.Default.Person),
    MODELS("models", "模型管理", "DeepSeek-V3 / R1 与 API Key 配置", Icons.Default.AutoAwesome),
    AGENT_PRESETS("agent-presets", "预设与偏好", "工作过程模式：紧凑 / 标准 / 详细", Icons.Default.Tune),
    PLUGINS("plugins", "内置插件", "已安装插件与配置", Icons.Default.Widgets),
    ARCHIVED("archived-sessions", "归档会话", "已归档的会话记录与快照", Icons.Default.Archive),
    MOBILE_RUNTIME("mobile-runtime", "移动运行时", "Linux 容器休眠、长任务守护与高危拦截", Icons.Default.Memory),
    MOBILE_WORKSPACE("mobile-workspace", "手机工作区", "公有存储绑定、系统文件可见性", Icons.Default.Folder)
}

/**
 * 手机端自适应官方 SettingsRoot:
 * 手机竖屏状态下采用官方风格主列表 -> 点击深入 Section 详情，支持平滑返回；
 * 保留官方深色面板视觉（#161719 底色、#212327 卡片、#3875F6 品牌蓝）。
 */
@Composable
fun OfficialSettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    accountName: String = "DeepSeek 用户",
    accountBalance: String = "¥60.00",
    initialSection: SettingsSectionTab? = null,
    onManageAccount: () -> Unit = {},
    onCreateBackup: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSection by remember { mutableStateOf<SettingsSectionTab?>(initialSection) }
    var currentSettings by remember { mutableStateOf(settings) }

    val darkBg = Color(0xFF161719)
    val cardBg = Color(0xFF212327)
    val textPrimary = Color(0xFFEEEEEE)
    val textSecondary = Color(0xFF9E9EA4)
    val textMuted = Color(0xFF6E7077)
    val accentBlue = Color(0xFF3875F6)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
    ) {
        // 顶部官方风格 Header: 带标题与右侧返回/关闭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeSection != null) {
                IconButton(onClick = { activeSection = null }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activeSection!!.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF26282D), thickness = 1.dp)

        AnimatedContent(
            targetState = activeSection,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settings_nav"
        ) { section ->
            if (section == null) {
                // 1. 官方 Nav 分区主列表 (手机自适应纵向呈现，对齐官方 Nav Rail 的所有分类)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "通用设置分区",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = textMuted,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    items(SettingsSectionTab.values()) { tab ->
                        val isMobileFeature = (tab == SettingsSectionTab.MOBILE_RUNTIME || tab == SettingsSectionTab.MOBILE_WORKSPACE)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardBg,
                            onClick = { activeSection = tab },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isMobileFeature) Color(0xFF2E3340) else Color(0xFF282A2E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isMobileFeature) accentBlue else textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = textPrimary
                                        )
                                        if (isMobileFeature) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF20283A)
                                            ) {
                                                Text(
                                                    text = "移动端",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = accentBlue),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = tab.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // 2. 具体 Section 的详细配置面板
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (section) {
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
                                                .background(accentBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("DS", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = accountName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = textPrimary
                                            )
                                            Text(
                                                text = "状态: 已通过官方 OAuth 授权",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = textSecondary
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF2C2F36)
                                        ) {
                                            Text(
                                                text = accountBalance,
                                                color = accentBlue,
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
                                        onCheckedChange = { checked: Boolean ->
                                            currentSettings = currentSettings.copy(idleStopMinutes = if (checked) 5 else 0)
                                            onUpdateSettings(currentSettings)
                                        }
                                    )
                                    OfficialSwitchRow(
                                        icon = Icons.Default.Memory,
                                        title = "后台长任务守护",
                                        subtitle = "任务执行期间启用前台服务与系统唤醒锁 (WakeLock)",
                                        checked = currentSettings.keepAliveOnTask,
                                        onCheckedChange = { checked: Boolean ->
                                            currentSettings = currentSettings.copy(keepAliveOnTask = checked)
                                            onUpdateSettings(currentSettings)
                                        }
                                    )
                                    OfficialSwitchRow(
                                        icon = Icons.Default.Lock,
                                        title = "高危工具二次确认",
                                        subtitle = "执行覆盖文件、Shell、系统硬件等动作前强制弹窗审批",
                                        checked = currentSettings.highRiskConfirm,
                                        onCheckedChange = { checked: Boolean ->
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
                                        color = textMuted,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
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
