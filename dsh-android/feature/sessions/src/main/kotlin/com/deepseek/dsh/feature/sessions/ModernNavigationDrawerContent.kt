package com.deepseek.dsh.feature.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SessionDisplayItem(
    val sessionId: String,
    val title: String,
    val timeAgo: String,
    val isActiveDot: Boolean = false
)

data class WorkspaceFolder(
    val id: String,
    val name: String,
    val sessions: List<SessionDisplayItem> = emptyList(),
    val isExpanded: Boolean = true
)

/**
 * 侧边栏抽屉：完全对齐应用 Material 3 统一色彩系统
 * 严格跟随整个 App 的 surface / surfaceVariant / primary 配色体系，消除孤立纯黑色块。
 */
@Composable
fun ModernNavigationDrawerContent(
    userName: String = "海盐味薯片",
    userTag: String = "官方 OAuth",
    accountBalance: String = "¥60.00",
    workspaces: List<WorkspaceFolder> = emptyList(),
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onNavigatePlugins: () -> Unit,
    onNavigateSchedule: () -> Unit = {},
    onNavigateKanban: () -> Unit = {},
    onNavigateFiles: () -> Unit,
    onNavigateHosts: () -> Unit,
    onNavigateSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 动态遵循全局 MaterialTheme 主题色系
    val bgDrawer = MaterialTheme.colorScheme.surface
    val btnNewChat = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val textMuted = MaterialTheme.colorScheme.outline
    val lineSep = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val accentPrimary = MaterialTheme.colorScheme.primary
    val greenDot = Color(0xFF22C55E)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(bgDrawer)
            .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 16.dp)
    ) {
        // 1. 顶部 Header: deepseek 图标 + deepseek 标题 + HARNESS 药丸徽章
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accentPrimary),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(bgDrawer)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "deepseek",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    fontSize = 20.sp
                ),
                color = textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = btnNewChat,
                modifier = Modifier.padding(top = 1.dp)
            ) {
                Text(
                    text = "HARNESS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textSecondary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. "+ 新会话" 圆角药丸大按钮
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = btnNewChat,
            onClick = onNewChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "新会话",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = textPrimary,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. 核心功能菜单项: 插件 / 自动化任务 / 任务看板
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            OfficialDrawerItem(
                icon = Icons.Outlined.Extension,
                title = "插件",
                onClick = onNavigatePlugins,
                textColor = textPrimary,
                iconTint = textSecondary
            )
            OfficialDrawerItem(
                icon = Icons.Outlined.Schedule,
                title = "自动化任务",
                onClick = onNavigateSchedule,
                textColor = textPrimary,
                iconTint = textSecondary
            )
            OfficialDrawerItem(
                icon = Icons.Outlined.ViewKanban,
                title = "任务看板",
                onClick = onNavigateKanban,
                textColor = textPrimary,
                iconTint = textSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. "工作区" 标题行与右侧操作按钮 [搜索 / 排序 / 外部打开]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "工作区",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = textMuted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = textMuted, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "Filter", tint = textMuted, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onNavigateFiles, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Folder", tint = textMuted, modifier = Modifier.size(15.dp))
            }
        }

        // 5. 树状工作区与会话列表 (跟随全局色系)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            workspaces.forEach { folder ->
                val isCurrentFolder = (folder.id == "ws_mobile")
                item(key = "fld_${folder.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (folder.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isCurrentFolder) accentPrimary else textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrentFolder) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isCurrentFolder) textPrimary else textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                if (folder.isExpanded) {
                    items(folder.sessions, key = { it.sessionId }) { session ->
                        val isSelected = session.sessionId == selectedSessionId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
                            onClick = { onSelectSession(session.sessionId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, top = 1.dp, bottom = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (session.isActiveDot) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(greenDot)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else textSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = session.timeAgo,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = textMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = lineSep,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // 6. 底部 Remote 主机入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNavigateHosts() }
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Remote",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                color = textPrimary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 7. 用户信息卡片 (完全融入全局主题)
        var showAccountMenu by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAccountMenu = true }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 用户头像
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD180)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🐱",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = userName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                // 右侧 "新版本" 胶囊按钮
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = accentPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "新版本",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            color = accentPrimary
                        )
                    }
                }
            }

            // 官方规范 AccountMenu 弹出层
            DropdownMenu(
                expanded = showAccountMenu,
                onDismissRequest = { showAccountMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DropdownMenuItem(
                    text = { Text("设置", color = textPrimary, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        showAccountMenu = false
                        onNavigateSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("意见反馈", color = textPrimary, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        showAccountMenu = false
                    }
                )
                HorizontalDivider(color = lineSep)
                DropdownMenuItem(
                    text = { Text("退出登录", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        showAccountMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OfficialDrawerItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    textColor: Color,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
            color = textColor,
            fontSize = 14.sp
        )
    }
}
