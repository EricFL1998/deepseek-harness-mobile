package com.deepseek.dsh.feature.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.SessionInfo

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
 * 完整对齐官方抽屉与系统深浅色自适应 (Light & Dark Parity)
 * 严格按照官方原生规范渲染工作区树、会话行与精美 AccountMenu
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
    // 严格动态跟随手机系统的 MaterialTheme 调色板
    val bgDrawer = MaterialTheme.colorScheme.surface
    val btnNewChat = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val textMuted = MaterialTheme.colorScheme.outline
    val lineSep = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val accentPrimary = MaterialTheme.colorScheme.primary
    val selectedRowBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val selectedText = MaterialTheme.colorScheme.primary

    var expandedMap by remember(workspaces) {
        mutableStateOf(workspaces.associate { it.id to it.isExpanded })
    }

    var showAccountMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(bgDrawer)
            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 16.dp)
    ) {
        // 1. 顶部 Header: deepseek 图标 + deepseek 标题 + HARNESS 药丸徽章
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
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
                color = MaterialTheme.colorScheme.surfaceVariant,
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
            shape = RoundedCornerShape(10.dp),
            color = btnNewChat,
            onClick = onNewChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
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
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "新会话",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = textPrimary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 核心功能菜单项: 插件 / 自动化任务 / 任务看板
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
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

        Spacer(modifier = Modifier.height(8.dp))

        // 4. "工作区" 标题行与右侧操作按钮 [搜索 / 排序 / 外部打开]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "工作区",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = textMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = textMuted, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "Filter", tint = textMuted, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onNavigateFiles, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Folder", tint = textMuted, modifier = Modifier.size(14.dp))
            }
        }

        // 5. 官方度量规范树状工作区与会话列表 (项目高 32dp，会话高 30dp)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            workspaces.forEach { folder ->
                val isExpanded = expandedMap[folder.id] ?: folder.isExpanded
                val isCurrentFolder = (folder.id == "ws_mobile")

                // 工作区项目行 (.projectRow: height 32dp)
                item(key = "fld_${folder.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                expandedMap = expandedMap.toMutableMap().apply {
                                    put(folder.id, !isExpanded)
                                }
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 展开倒三角 / 右三角
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isCurrentFolder) accentPrimary else textMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrentFolder) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = textPrimary,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )

                        if (isCurrentFolder) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Actions",
                                tint = textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "New Session",
                                tint = textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // 工作区下的会话列表 (.sessionRow: height 30dp)
                if (isExpanded) {
                    items(folder.sessions, key = { it.sessionId }) { session ->
                        val isSelected = session.sessionId == selectedSessionId
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) selectedRowBg else Color.Transparent,
                            onClick = { onSelectSession(session.sessionId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .padding(horizontal = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 20.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 14px 状态槽
                                Box(
                                    modifier = Modifier.size(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (session.isActiveDot) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF22C55E))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.Transparent)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) selectedText else textSecondary,
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
                .height(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onNavigateHosts() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Remote",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                color = textPrimary,
                fontSize = 13.5.sp
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 7. 贴附式 AccountMenu 弹窗 (紧挨着用户卡片正上方，参考图 2 的贴紧布局)
        val menuBg = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF25272B) else MaterialTheme.colorScheme.surfaceVariant
        val menuBorder = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF383A40) else MaterialTheme.colorScheme.outlineVariant
        val menuText = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFEEEEEE) else MaterialTheme.colorScheme.onSurface
        val menuIconTint = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFCFD0D4) else MaterialTheme.colorScheme.onSurfaceVariant
        val menuShortcut = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF7E8086) else MaterialTheme.colorScheme.outline

        AnimatedVisibility(
            visible = showAccountMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = menuBg,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .width(196.dp)
                    .border(0.5.dp, menuBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // 1. 设置 (齿轮 + "设置" + "Ctrl + ,")
                    Surface(
                        color = Color.Transparent,
                        onClick = {
                            showAccountMenu = false
                            onNavigateSettings()
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = menuIconTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "设置",
                                color = menuText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Ctrl + ,",
                                color = menuShortcut,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    // 2. 意见反馈 (纸飞机 Send + "意见反馈")
                    Surface(
                        color = Color.Transparent,
                        onClick = {
                            showAccountMenu = false
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Send,
                                contentDescription = null,
                                tint = menuIconTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "意见反馈",
                                color = menuText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    // 3. 退出登录 (ExitToApp 登出图标 + "退出登录")
                    Surface(
                        color = Color.Transparent,
                        onClick = {
                            showAccountMenu = false
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                                contentDescription = null,
                                tint = menuIconTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "退出登录",
                                color = menuText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 8. 用户卡片 (底栏紧挨其上方的菜单)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { showAccountMenu = !showAccountMenu }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 用户头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🐱",
                    fontSize = 16.sp
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
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.height(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = accentPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "新版本",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                        color = accentPrimary
                    )
                }
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
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
            color = textColor,
            fontSize = 13.5.sp
        )
    }
}
