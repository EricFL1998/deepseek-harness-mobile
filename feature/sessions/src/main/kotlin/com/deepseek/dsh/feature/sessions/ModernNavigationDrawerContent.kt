package com.deepseek.dsh.feature.sessions

import com.deepseek.dsh.core.designsystem.DshIcons
import com.deepseek.dsh.core.designsystem.DshMenuCard
import com.deepseek.dsh.core.designsystem.DshMenuDivider
import com.deepseek.dsh.core.designsystem.DshMenuIcon
import com.deepseek.dsh.core.designsystem.DshMenuLabel
import com.deepseek.dsh.core.designsystem.DshMenuRow
import com.deepseek.dsh.core.designsystem.AnchoredMenuPopup
import com.deepseek.dsh.feature.sessions.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.SessionInfo

data class SessionDisplayItem(
    val sessionId: String,
    val title: String,
    val timeAgo: String,
    val isActiveDot: Boolean = false,
    val isRunning: Boolean = false,
    val hasUnread: Boolean = false
)

data class WorkspaceFolder(
    val id: String,
    val name: String,
    val sessions: List<SessionDisplayItem> = emptyList(),
    val isExpanded: Boolean = true
)

/**
 * 完整对齐抽屉与系统深浅色自适应 (Light & Dark Parity)
 * 严格按照原生规范渲染工作区树、会话行与精美 AccountMenu
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernNavigationDrawerContent(
    userName: String = "DeepSeek 用户",
    avatarUrl: String? = null,
    updateVersion: String? = null,
    onUpdateClick: () -> Unit = {},
    userTag: String = "未认证",
    accountBalance: String = "¥0.00",
    isSignedIn: Boolean = false,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    workspaces: List<WorkspaceFolder> = emptyList(),
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onNavigatePlugins: () -> Unit,
    onNavigateSchedule: () -> Unit = {},
    onNavigateKanban: () -> Unit = {},
    onNavigateRuntime: () -> Unit = {},
    onNavigateFiles: () -> Unit,
    onNavigateHosts: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onAddWorkspace: () -> Unit = {},
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
    var sessionMenuId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(304.dp)
            .background(bgDrawer)
            .padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 12.dp)
    ) {
        // 1. 顶部品牌行（对齐 ui-sidebar .logoRow / .brandIdentity）：
        //    飞鱼标识 + deepseek 字标 + 反白 HARNESS 徽章
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_dsh_fish_logo),
                contentDescription = null,
                tint = textPrimary,
                modifier = Modifier
                    .height(18.dp)
                    .width(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_brand_wordmark),
                contentDescription = "deepseek",
                tint = textPrimary,
                modifier = Modifier
                    .height(18.dp)
                    .width(72.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 徽章：反白填充 + 内嵌反色 HARNESS
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(textPrimary)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "HARNESS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    ),
                    color = bgDrawer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2.「新会话」按钮（对齐 .newSession：整宽、38 高、12 圆角、0.5px 描边、内容间距 6）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            ),
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
                    imageVector = DshIcons.NewChatOutline,
                    contentDescription = null,
                    tint = textPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.session_new),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 核心功能菜单项: 插件 / 自动化任务 / 任务看板
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            DrawerItem(
                iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin,
                title = "插件",
                onClick = onNavigatePlugins,
                textColor = textPrimary,
                iconTint = textSecondary
            )
            DrawerItem(
                iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_schedule,
                title = "自动化任务",
                onClick = onNavigateSchedule,
                textColor = textPrimary,
                iconTint = textSecondary
            )
            DrawerItem(
                iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_checklist,
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
                .padding(start = 4.dp, end = 2.dp, top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "工作区",
                color = textMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 2.dp)
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_action_search_official),
                    contentDescription = "搜索",
                    tint = textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_action_filter_official),
                    contentDescription = "筛选",
                    tint = textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onAddWorkspace),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_action_project_add_official),
                    contentDescription = "新建工作区",
                    tint = textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))

        // 5. 工作区树（对齐 ui-workspace Rows.module.css）：
        //    项目行 .projectRow 高 34、圆角 12、padding 0 8、gap 6、文件夹图标 16；
        //    会话行 .sessionRow 高 32、圆角 12、起始缩进 20、标题 14/20、时间 10/16 三级色。
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            workspaces.forEachIndexed { folderIndex, folder ->
                val isExpanded = expandedMap[folder.id] ?: folder.isExpanded

                item(key = "fld_${folder.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (folderIndex == 0) 0.dp else 4.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                expandedMap = expandedMap.toMutableMap().apply {
                                    put(folder.id, !isExpanded)
                                }
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isExpanded) {
                                    com.deepseek.dsh.core.designsystem.R.drawable.ic_folder_open
                                } else {
                                    com.deepseek.dsh.core.designsystem.R.drawable.ic_folder_close
                                }
                            ),
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = folder.name,
                            color = textPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isExpanded) {
                    items(folder.sessions, key = { it.sessionId }) { session ->
                        val isSelected = session.sessionId == selectedSessionId
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                        else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = { onSelectSession(session.sessionId) },
                                        onLongClick = { sessionMenuId = session.sessionId },
                                    )
                                    .padding(start = 24.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = session.title,
                                    color = textPrimary,
                                    fontSize = 13.5.sp,
                                    lineHeight = 20.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp, end = 6.dp)
                                )
                                if (session.isRunning) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else if (session.hasUnread) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF34C759))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = session.timeAgo,
                                    color = textMuted,
                                    fontSize = 10.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 1
                                )
                            }

                            // 行操作菜单（原 hover 按钮）改为长按弹出
                            SessionRowMenu(
                                expanded = sessionMenuId == session.sessionId,
                                onDismiss = { sessionMenuId = null },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            color = lineSep,
            thickness = 0.5.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 7. 账户区：点击用户卡片在其正上方展开官方账户菜单（内联紧贴账户面板上沿）
        if (showAccountMenu) {
            DshMenuCard(
                scrollable = false,
                contentWidth = true,
            ) {
                AccountMenuRow(
                    iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_menu_settings,
                    label = stringResource(R.string.drawer_account_settings),
                    onClick = {
                        showAccountMenu = false
                        onNavigateSettings()
                    },
                )
                AccountMenuRow(
                    iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_feedback,
                    label = stringResource(R.string.drawer_account_feedback),
                    onClick = { showAccountMenu = false },
                )
                if (isSignedIn) {
                    AccountMenuRow(
                        iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_menu_logout,
                        label = stringResource(R.string.drawer_account_signout),
                        onClick = {
                            showAccountMenu = false
                            onSignOut()
                        },
                    )
                } else {
                    AccountMenuRow(
                        iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_user,
                        label = "登录",
                        onClick = {
                            showAccountMenu = false
                            onSignIn()
                        },
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showAccountMenu = !showAccountMenu }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSignedIn) {
                        RemoteAvatar(url = avatarUrl)
                    } else {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (isSignedIn) userName else "更多",
                    color = textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (updateVersion != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onUpdateClick,
                        modifier = Modifier.height(24.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "新版本",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 账户菜单的一行：14dp 图标 + 13/20 标签，无快捷键提示。 */
@Composable
private fun AccountMenuRow(
    iconResId: Int,
    label: String,
    onClick: () -> Unit,
) {
    DshMenuRow(onClick = onClick) {
        DshMenuIcon(iconResId, size = 14.dp)
        DshMenuLabel(label)
    }
}
/**
 * 会话行长按菜单：对齐行内 `⋯` 菜单条目
 * （置顶会话 / 重命名 / 分叉会话 / 归档会话 / 复制 Session ID）。
 *
 * 手机端不显示桌面快捷键提示。
 */
@Composable
private fun SessionRowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    data class Entry(val labelResId: Int, val iconResId: Int)

    val entries = listOf(
        Entry(R.string.row_pin, com.deepseek.dsh.core.designsystem.R.drawable.ic_row_pin),
        Entry(R.string.row_rename, com.deepseek.dsh.core.designsystem.R.drawable.ic_row_edit),
        Entry(R.string.row_fork, com.deepseek.dsh.core.designsystem.R.drawable.ic_row_branch),
        Entry(R.string.row_archive, com.deepseek.dsh.core.designsystem.R.drawable.ic_row_archive),
        Entry(R.string.row_session_id, com.deepseek.dsh.core.designsystem.R.drawable.ic_row_copy),
    )

    // 宽度随内容自适应：卡片按最长一行定宽，行铺满卡片（Menu `.item` 100% 宽）。
    AnchoredMenuPopup(
        expanded = expanded,
        onDismiss = onDismiss,
        contentWidth = true,
        // 紧贴被长按的会话行
        anchorGap = 0.dp,
    ) {
        entries.forEach { entry ->
            DshMenuRow(onClick = onDismiss) {
                DshMenuIcon(entry.iconResId)
                DshMenuLabel(stringResource(entry.labelResId))
            }
        }
    }
}

@Composable
private fun DrawerItem(
    iconResId: Int,
    title: String,
    onClick: () -> Unit,
    textColor: Color,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = textColor,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
}

















@Composable
private fun RemoteAvatar(url: String?) {
    var image by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                java.net.URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }?.let { it.asImageBitmap() }
    }
    val bitmap = image
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text(text = "🐱", fontSize = 13.sp)
    }
}






