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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.feature.sessions.R

data class WorkspaceFolder(
    val id: String,
    val name: String,
    val sessions: List<SessionInfo> = emptyList(),
    val isExpanded: Boolean = true
)

/**
 * 完整对齐官方最新桌面/移动端侧滑抽屉（Dark 极简深色主题风格，用户置底）
 */
@Composable
fun ModernNavigationDrawerContent(
    userName: String = "DeepSeek 用户",
    userTag: String = "官方 OAuth",
    accountBalance: String = "¥60.00",
    workspaces: List<WorkspaceFolder> = emptyList(),
    selectedSessionId: String?,
    onSelectSession: (SessionInfo) -> Unit,
    onNewChatClick: () -> Unit,
    onNavigatePlugins: () -> Unit,
    onNavigateSchedule: () -> Unit = {},
    onNavigateKanban: () -> Unit = {},
    onNavigateFiles: () -> Unit,
    onNavigateHosts: () -> Unit,
    onNavigateSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 沉浸式高级深色底色 (对齐参考图 #17181A)
    val drawerBg = Color(0xFF161719)
    val cardBg = Color(0xFF212327)
    val textPrimary = Color(0xFFEEEEEE)
    val textSecondary = Color(0xFF9E9EA4)
    val textMuted = Color(0xFF6E7077)
    val accentBlue = Color(0xFF3875F6)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(drawerBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. 顶部 Header: deepseek [HARNESS] Logo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
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
                        .background(drawerBg)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "deepseek",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF282A2E),
                modifier = Modifier.padding(top = 2.dp)
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

        // 2. "+ 新会话" 大药丸按钮 (对齐参考图)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = cardBg,
            onClick = onNewChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
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
                    color = textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 核心功能菜单: 插件 / 自动化任务 / 任务看板
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            DarkDrawerMenuItem(
                icon = Icons.Default.Extension,
                title = "插件",
                onClick = onNavigatePlugins,
                textColor = textPrimary,
                iconTint = textSecondary
            )
            DarkDrawerMenuItem(
                icon = Icons.Default.Schedule,
                title = "自动化任务",
                onClick = onNavigateSchedule,
                textColor = textPrimary,
                iconTint = textSecondary
            )
            DarkDrawerMenuItem(
                icon = Icons.Default.ViewKanban,
                title = "任务看板",
                onClick = onNavigateKanban,
                textColor = textPrimary,
                iconTint = textSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. 工作区与会话列表 Header: "工作区" + 右侧快捷图标 [搜索 / 排序 / 外部]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "工作区",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = textMuted,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = textMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "Filter", tint = textMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onNavigateFiles, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Folder", tint = textMuted, modifier = Modifier.size(16.dp))
            }
        }

        // 5. 树状工作区与会话列表 (中间自适应滚动区)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            workspaces.forEach { ws ->
                item(key = "ws_${ws.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (ws.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (ws.name == "deepseek mobile") accentBlue else textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ws.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (ws.name == "deepseek mobile") FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (ws.name == "deepseek mobile") textPrimary else textSecondary
                        )
                    }
                }

                items(ws.sessions, key = { it.sessionId }) { session ->
                    val isSelected = session.sessionId == selectedSessionId
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF2C2F36) else Color.Transparent,
                        onClick = { onSelectSession(session) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) textPrimary else textSecondary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "10分钟",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = textMuted
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = Color(0xFF26282D),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 6. 底部 Remote 主机入口 (对齐参考图底部上层)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNavigateHosts() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Remote",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = textPrimary
            )
        }

        // 7. 底部用户信息卡片 (用户置底！对齐参考图最底部)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNavigateSettings() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 用户圆形头像
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3875F6)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DS",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textPrimary,
                    maxLines = 1
                )
                Text(
                    text = accountBalance,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = textMuted
                )
            }

            // 右侧“新版本”胶囊按钮 (参考图样式)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF20283A),
                modifier = Modifier.height(26.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "新版本",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = accentBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun DarkDrawerMenuItem(
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
            color = textColor
        )
    }
}
