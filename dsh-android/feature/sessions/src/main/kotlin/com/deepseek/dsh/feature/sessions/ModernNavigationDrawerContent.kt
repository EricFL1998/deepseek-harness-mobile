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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
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
 * 官方 ui-workspace 严格度量规范（对齐 Figma 与 Rows.module.css）：
 * - 纯净深色底: #17181A (官方深色主题画布)
 * - 选中高亮底: #2B2D31 (选中项饱满灰黑背景)
 * - .projectRow 高度: 32.dp，padding: (0, 8.dp)，gap: 6.dp
 *   展开箭头: ▾ (ArrowDropDown 16.dp) 或 📁 文件夹图标
 *   右侧操作组: ... 更多与 + 新增会话
 * - .sessionRow 高度: 30.dp，padding: (0, 8.dp)，gap: 0.dp
 *   缩进 indent: 18.dp
 *   状态指示槽位: 16.dp (圆形环/实心点)
 *   标题 title: margin (0, 6.dp, 0, 4.dp)，字号 13.5.sp，行高 20.sp
 *   时间戳: 11.5.sp，次级弱化色
 * - 紧凑间距: 列表 item 间距 0.dp (完全消灭过大空隙)
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
    // 严格锁定官方深色主题规范底色
    val bgDrawer = Color(0xFF17181A)
    val btnNewChat = Color(0xFF27292D)
    val selectedRowBg = Color(0xFF2B2D31)
    val textWhite = Color(0xFFEEEEEE)
    val textGray = Color(0xFFA1A2A6)
    val textDim = Color(0xFF65666B)
    val lineSep = Color(0xFF24262A)
    val accentPrimary = Color(0xFF3875F6)

    // 动态可折叠状态驱动
    var expandedMap by remember(workspaces) {
        mutableStateOf(workspaces.associate { it.id to it.isExpanded })
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(bgDrawer)
            .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 14.dp)
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
                    .background(Color.White),
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
                color = textWhite
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
                    color = textGray,
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
                    tint = textWhite,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "新会话",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = textWhite,
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
                textColor = textWhite,
                iconTint = textGray
            )
            OfficialDrawerItem(
                icon = Icons.Outlined.Schedule,
                title = "自动化任务",
                onClick = onNavigateSchedule,
                textColor = textWhite,
                iconTint = textGray
            )
            OfficialDrawerItem(
                icon = Icons.Outlined.ViewKanban,
                title = "任务看板",
                onClick = onNavigateKanban,
                textColor = textWhite,
                iconTint = textGray
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
                color = textDim,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = textDim, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "Filter", tint = textDim, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onNavigateFiles, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Folder", tint = textDim, modifier = Modifier.size(14.dp))
            }
        }

        // 5. 官方度量规范树状工作区与会话列表 (项目高 32dp，会话高 30dp，极紧凑间距)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            workspaces.forEach { folder ->
                val isExpanded = expandedMap[folder.id] ?: folder.isExpanded
                val isCurrentFolder = (folder.id == "ws_mobile")

                // 工作区项目行 (.projectRow: height 32dp, pad 6dp)
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
                        // 展开倒三角 / 右三角 (对齐官方 16px 箭头 slot)
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isCurrentFolder) accentPrimary else textDim,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // 文件夹名
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrentFolder) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = textWhite,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )

                        // 官方右侧工作区悬浮动作槽 (更多 / 新建会话)
                        if (isCurrentFolder) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Actions",
                                tint = textDim,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "New Session",
                                tint = textDim,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // 工作区下的会话列表 (.sessionRow: height 30dp, pad 6dp, margin 0 6px 0 4px)
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
                                    .padding(start = 20.dp, end = 8.dp), // 紧凑缩进
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 14px 状态槽 (空心圈环 / 绿色运行点)
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
                                        // 官方默认空心圈
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.Transparent)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // 会话标题 (占满剩余宽度，字号 13.5sp，行高 20sp)
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                    ),
                                    color = if (isSelected) textWhite else textGray,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // 相对时间戳 (11sp，次级灰色)
                                Text(
                                    text = session.timeAgo,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = textDim
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
                tint = textGray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Remote",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                color = textWhite,
                fontSize = 13.5.sp
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 7. 用户信息卡片 (完全融入全局主题)
        var showAccountMenu by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAccountMenu = true }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 用户头像
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD180)),
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
                    color = textWhite,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                // 右侧 "新版本" 胶囊按钮
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF20283A),
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

            // 官方规范 AccountMenu 弹出层
            DropdownMenu(
                expanded = showAccountMenu,
                onDismissRequest = { showAccountMenu = false },
                modifier = Modifier.background(Color(0xFF212327))
            ) {
                DropdownMenuItem(
                    text = { Text("设置", color = textWhite, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = textGray, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        showAccountMenu = false
                        onNavigateSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("意见反馈", color = textWhite, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = textGray, modifier = Modifier.size(18.dp))
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
