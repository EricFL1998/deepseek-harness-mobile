package com.deepseek.dsh.feature.sessions

import com.deepseek.dsh.core.designsystem.DshIcons
import com.deepseek.dsh.core.designsystem.DshMenuCard
import com.deepseek.dsh.core.designsystem.DshMenuDivider
import com.deepseek.dsh.core.designsystem.DshMenuIcon
import com.deepseek.dsh.core.designsystem.DshMenuLabel
import com.deepseek.dsh.core.designsystem.DshMenuRow
import com.deepseek.dsh.core.designsystem.DshMenuSectionLabel
import com.deepseek.dsh.core.designsystem.DshMenuCheck
import com.deepseek.dsh.core.designsystem.AnchoredMenuPopup
import com.deepseek.dsh.feature.sessions.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.SessionInfo

enum class WorkspaceGroupMode { WORKSPACE, TREE, SINGLE_LIST }
enum class SessionSortMode { MANUAL, RECENT }
enum class ArchiveFilter { HIDE_ARCHIVED, ALL, ONLY_ARCHIVED }

data class SessionDisplayItem(
    val sessionId: String,
    val title: String,
    val timeAgo: String,
    val isActiveDot: Boolean = false,
    val isRunning: Boolean = false,
    val hasUnread: Boolean = false,
    val updatedAt: Long = 0L,
    val archived: Boolean = false,
    val pinned: Boolean = false,
)

data class WorkspaceFolder(
    val id: String,
    val name: String,
    val path: String = "",
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
    /** 把磁盘工作区名映射成界面显示名（默认工作区需本地化）。 */
    workspaceDisplayName: (String) -> String = { it },
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
    /** 长按会话行的操作：重命名 / 置顶 / 归档。 */
    onRenameSession: (SessionDisplayItem) -> Unit = {},
    onTogglePinSession: (SessionDisplayItem) -> Unit = {},
    onToggleArchiveSession: (SessionDisplayItem) -> Unit = {},
    onForkSession: (SessionDisplayItem) -> Unit = {},
    /** Host 内容搜索结果（sessionId + 命中片段）。 */
    remoteSearchResults: List<Pair<String, String>> = emptyList(),
    /** 搜索词变化（由宿主去抖后调用 Host `session/search`）。 */
    onSearchQueryChange: (String) -> Unit = {},
    onNewChatClick: () -> Unit,
    onNavigatePlugins: () -> Unit,
    onNavigateSchedule: () -> Unit = {},
    /** 打开终端页（官方 terminal-controller）。 */
    onNavigateTerminal: () -> Unit = {},
    /**
     * 终端页是否出现：由 Host 的官方 Loader 行 `@deepseek-ai/dsh-api-terminal-controller`
     * 是否启用决定（等价于官方插件页里那一行的开关），客户端不另造设置。
     */
    showTerminal: Boolean = true,
    onNavigateRuntime: () -> Unit = {},
    onNavigateFiles: () -> Unit,
    onNavigateHosts: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onAddWorkspace: () -> Unit = {},
    onRenameWorkspace: (WorkspaceFolder) -> Unit = {},
    selectedWorkspaceName: String = "",
    onSelectWorkspace: (WorkspaceFolder) -> Unit = {},
    onDeleteWorkspace: (WorkspaceFolder) -> Unit = {},
    /** delta = -1 上移 / +1 下移（按 Host 工作区顺序）。 */
    onMoveWorkspace: (WorkspaceFolder, Int) -> Unit = { _, _ -> },
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
    var workspaceMenuId by remember { mutableStateOf<String?>(null) }
    // 工作区长按拖动排序：拖动的文件夹、纵向位移、目标文件夹。
    var draggingFolderId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragStartCenter by remember { mutableStateOf(0f) }
    var dragTargetFolderId by remember { mutableStateOf<String?>(null) }
    // 拖动时手指在列表坐标系中的位置，用于边缘自动滚动与目标判定。
    var dragPointerY by remember { mutableStateOf(0f) }
    var dragAutoScrollDir by remember { mutableStateOf(0) }
    var dragScrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val dragScope = rememberCoroutineScope()
    var searchOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    var groupMode by remember { mutableStateOf(WorkspaceGroupMode.WORKSPACE) }
    var sortMode by remember { mutableStateOf(SessionSortMode.MANUAL) }
    var archiveFilter by remember { mutableStateOf(ArchiveFilter.HIDE_ARCHIVED) }
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchOpen, searchQuery) {
        if (!searchOpen || searchQuery.isBlank()) {
            onSearchQueryChange("")
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        onSearchQueryChange(searchQuery)
    }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocus.requestFocus()
    }
    var sessionMenuId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(304.dp)
            .background(bgDrawer)
            .statusBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 12.dp)
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

        // 3. 核心功能菜单项: 插件 / 自动化任务
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            DrawerItem(
                iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin,
                title = stringResource(R.string.drawer_nav_plugins),
                onClick = onNavigatePlugins,
                textColor = textPrimary,
                iconTint = textSecondary
            )
            DrawerItem(
                iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_schedule,
                title = stringResource(R.string.drawer_nav_schedule),
                onClick = onNavigateSchedule,
                textColor = textPrimary,
                iconTint = textSecondary
            )
            if (showTerminal) {
                DrawerItem(
                    iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_compact,
                    title = stringResource(R.string.drawer_nav_terminal),
                    onClick = onNavigateTerminal,
                    textColor = textPrimary,
                    iconTint = textSecondary
                )
            }

        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. "工作区" 标题行；点击搜索后整行变为搜索栏（官方 Drawer 行为）。
        if (searchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 2.dp, bottom = 2.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_action_search_official),
                    contentDescription = null,
                    tint = textMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = textPrimary,
                            fontSize = 13.sp
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocus)
                    )
                    if (searchQuery.isEmpty()) {
                        Text(
                            "搜索会话名称",
                            color = textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.offset(y = (-1.5).dp)
                        )
                    }
                }
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_close),
                    contentDescription = "关闭搜索",
                    tint = textMuted,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable {
                            if (searchQuery.isNotEmpty()) {
                                searchQuery = ""
                            } else {
                                searchOpen = false
                            }
                        }
                )
            }
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(start = 4.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.drawer_workspaces),
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
                    .clickable {
                        searchQuery = ""
                        searchOpen = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_action_search_official),
                    contentDescription = "搜索",
                    tint = textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { viewMenuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_action_filter_official),
                        contentDescription = "视图选项",
                        tint = textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
                AnchoredMenuPopup(
                    expanded = viewMenuOpen,
                    onDismiss = { viewMenuOpen = false },
                    contentWidth = true,
                    anchorGap = 4.dp
                ) {
                    ViewOptionsMenu(
                        groupMode = groupMode,
                        sortMode = sortMode,
                        archiveFilter = archiveFilter,
                        onGroupMode = { groupMode = it; viewMenuOpen = false },
                        onSortMode = { sortMode = it; viewMenuOpen = false },
                        onArchiveFilter = { archiveFilter = it; viewMenuOpen = false },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onAddWorkspace),
                contentAlignment = Alignment.Center
            ) {                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_action_project_add_official),
                    contentDescription = "新建工作区",
                    tint = textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        }
        Spacer(modifier = Modifier.height(2.dp))

        // 5. 工作区树（对齐 ui-workspace Rows.module.css）：
        //    项目行 .projectRow 高 34、圆角 12、padding 0 8、gap 6、文件夹图标 16；
        //    会话行 .sessionRow 高 32、圆角 12、起始缩进 20、标题 14/20、时间 10/16 三级色。
        val sessionListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val scrubTitles = workspaces.flatMap { folder ->
            folder.sessions.filterNot { it.archived }.map { it.title }
        }
        val remoteSearchTitle = stringResource(R.string.search_host_results)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = sessionListState,
            modifier = Modifier
                .fillMaxSize()
                .drawerScrollbar(sessionListState, textMuted),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            val searching = searchOpen && searchQuery.isNotBlank()
            val filteredWorkspaces = workspaces.map { folder ->
                val sessions = folder.sessions.filter { session ->
                    val archiveOk = when (archiveFilter) {
                        ArchiveFilter.HIDE_ARCHIVED -> !session.archived
                        ArchiveFilter.ALL -> true
                        ArchiveFilter.ONLY_ARCHIVED -> session.archived
                    }
                    val searchOk = !searching || session.title.contains(searchQuery, ignoreCase = true)
                    archiveOk && searchOk
                }.let { list ->
                    if (sortMode == SessionSortMode.RECENT) list.sortedByDescending { it.updatedAt } else list
                }
                folder.copy(sessions = sessions, isExpanded = true)
            }.filter { folder ->
                // 官方 groupByWorkspace：除「仅归档」外，空工作区也保留。
                archiveFilter != ArchiveFilter.ONLY_ARCHIVED || folder.sessions.isNotEmpty()
            }
            val allMatching = workspaces.flatMap { folder -> folder.sessions }
                .filter { session ->
                    val archiveOk = when (archiveFilter) {
                        ArchiveFilter.HIDE_ARCHIVED -> !session.archived
                        ArchiveFilter.ALL -> true
                        ArchiveFilter.ONLY_ARCHIVED -> session.archived
                    }
                    val searchOk = !searching || session.title.contains(searchQuery, ignoreCase = true)
                    archiveOk && searchOk
                }
                .let { list ->
                    if (sortMode == SessionSortMode.RECENT) list.sortedByDescending { it.updatedAt } else list
                }
            val visibleWorkspaces = if (searching || groupMode == WorkspaceGroupMode.SINGLE_LIST) {
                if (allMatching.isEmpty()) emptyList() else listOf(
                    WorkspaceFolder(id = "__all", name = "", sessions = allMatching, isExpanded = true)
                )
            } else {
                filteredWorkspaces
            }

            val remoteFolder = if (searching && remoteSearchResults.isNotEmpty()) {
                val visibleIds = visibleWorkspaces
                    .flatMap { it.sessions }
                    .map { it.sessionId }
                    .toSet()
                val remoteRows = remoteSearchResults
                    .filterNot { (sessionId, _) -> sessionId in visibleIds }
                    .map { (sessionId, snippet) ->
                        SessionDisplayItem(
                            sessionId = sessionId,
                            title = snippet,
                            timeAgo = "",
                            isActiveDot = sessionId == selectedSessionId,
                        )
                    }
                if (remoteRows.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        WorkspaceFolder(
                            id = "__remote",
                            name = remoteSearchTitle,
                            sessions = remoteRows,
                            isExpanded = true,
                        )
                    )
                }
            } else {
                emptyList()
            }
            (visibleWorkspaces + remoteFolder).forEachIndexed { folderIndex, folder ->
                val isExpanded = if ((searchOpen && searchQuery.isNotBlank()) || groupMode == WorkspaceGroupMode.SINGLE_LIST) true else (expandedMap[folder.id] ?: folder.isExpanded)

                item(key = "fld_${folder.id}") {
                    if (folder.id != "__all") {
                    Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (folderIndex == 0) 0.dp else 4.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    draggingFolderId == folder.id ->
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                    folder.name == selectedWorkspaceName &&
                                        selectedSessionId.isNullOrBlank() ->
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    draggingFolderId != null &&
                                        dragTargetFolderId == folder.id ->
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    else -> Color.Transparent
                                }
                            )
                            .zIndex(if (draggingFolderId == folder.id) 1f else 0f)
                            .graphicsLayer {
                                if (draggingFolderId == folder.id) {
                                    translationY = dragOffsetY
                                }
                            }
                            .combinedClickable(
                                onClick = {
                                    onSelectWorkspace(folder)
                                    expandedMap = expandedMap.toMutableMap().apply {
                                        put(folder.id, !isExpanded)
                                    }
                                },
                            )
                            // 长按后拖动：按拖动中心的相对位置找目标文件夹，松手时回写
                            // 官方 workspace/insertBefore。
                            .pointerInput(folder.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val info = sessionListState.layoutInfo
                                            .visibleItemsInfo
                                            .firstOrNull {
                                                (it.key as? String) == "fld_${folder.id}"
                                            }
                                        dragStartCenter = info?.let {
                                            it.offset + it.size / 2f
                                        } ?: 0f
                                        dragPointerY = (info?.offset ?: 0) + offset.y
                                        dragOffsetY = dragPointerY - dragStartCenter
                                        draggingFolderId = folder.id
                                        dragTargetFolderId = folder.id
                                        dragAutoScrollDir = 0
                                        dragScrollJob?.cancel()
                                        dragScrollJob = dragScope.launch {
                                            while (true) {
                                                val dir = dragAutoScrollDir
                                                if (dir != 0) {
                                                    sessionListState.scrollBy(dir * 14f)
                                                }
                                                kotlinx.coroutines.delay(16)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val from = visibleWorkspaces
                                            .indexOfFirst { it.id == folder.id }
                                        val to = visibleWorkspaces
                                            .indexOfFirst { it.id == dragTargetFolderId }
                                        if (from >= 0 && to >= 0 && to != from) {
                                            onMoveWorkspace(folder, to - from)
                                        }
                                        dragScrollJob?.cancel()
                                        dragScrollJob = null
                                        dragAutoScrollDir = 0
                                        draggingFolderId = null
                                        dragOffsetY = 0f
                                        dragTargetFolderId = null
                                    },
                                    onDragCancel = {
                                        dragScrollJob?.cancel()
                                        dragScrollJob = null
                                        dragAutoScrollDir = 0
                                        draggingFolderId = null
                                        dragOffsetY = 0f
                                        dragTargetFolderId = null
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragPointerY += amount.y
                                        val layout = sessionListState.layoutInfo
                                        val edge = 72f
                                        dragAutoScrollDir = when {
                                            dragPointerY <
                                                layout.viewportStartOffset + edge -> -1
                                            dragPointerY >
                                                layout.viewportEndOffset - edge -> 1
                                            else -> 0
                                        }
                                        // 拖动行始终跟住手指（列表滚动时也保持）。
                                        val selfInfo = layout.visibleItemsInfo
                                            .firstOrNull {
                                                (it.key as? String) == "fld_${folder.id}"
                                            }
                                        if (selfInfo != null) {
                                            dragOffsetY = dragPointerY -
                                                (selfInfo.offset + selfInfo.size / 2f)
                                        }
                                        val folderItems = layout.visibleItemsInfo
                                            .filter {
                                                (it.key as? String)
                                                    ?.startsWith("fld_") == true
                                            }
                                        val target = folderItems.firstOrNull {
                                            dragPointerY < it.offset + it.size / 2f
                                        } ?: folderItems.lastOrNull()
                                        dragTargetFolderId =
                                            (target?.key as? String)?.removePrefix("fld_")
                                    },
                                )
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isExpanded || folder.name == selectedWorkspaceName) {
                                    com.deepseek.dsh.core.designsystem.R.drawable.ic_folder_open
                                } else {
                                    com.deepseek.dsh.core.designsystem.R.drawable.ic_folder_close
                                }
                            ),
                            contentDescription = null,
                            tint = if (folder.name == selectedWorkspaceName) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                textSecondary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = workspaceDisplayName(folder.name),
                            color = textPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // 长按改为拖动排序后，菜单改由行尾的「⋯」打开。
                        IconButton(
                            onClick = { workspaceMenuId = folder.id },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreHoriz,
                                contentDescription = null,
                                tint = textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    WorkspaceRowMenu(
                        expanded = workspaceMenuId == folder.id,
                        onDismiss = { workspaceMenuId = null },
                        onRename = { onRenameWorkspace(folder) },
                        onDelete = { onDeleteWorkspace(folder) },
                    )
                    }
                }
                }

                if (isExpanded || folder.id == "__all") {
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
                                // 归档：不再藏在长按菜单里，行尾直接给一个按钮。
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onToggleArchiveSession(session) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(
                                            com.deepseek.dsh.core.designsystem.R.drawable.ic_row_archive
                                        ),
                                        contentDescription = if (session.archived) "取消归档" else "归档会话",
                                        tint = textMuted,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }

                            // 行操作菜单（原 hover 按钮）改为长按弹出
                            SessionRowMenu(
                                expanded = sessionMenuId == session.sessionId,
                                session = session,
                                onDismiss = { sessionMenuId = null },
                                onRename = { onRenameSession(session) },
                                onFork = { onForkSession(session) },
                                onTogglePin = { onTogglePinSession(session) },
                                onToggleArchive = { onToggleArchiveSession(session) },
                            )
                        }
                    }
                }
            }
        }
        DrawerScrollbarScrubber(
            state = sessionListState,
            titles = scrubTitles,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(14.dp)
        )
        }

        HorizontalDivider(
            color = lineSep,
            thickness = 0.5.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // 7. 账户区：点击用户卡片在其正上方展开官方账户菜单
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
                    text = if (isSignedIn) userName else stringResource(R.string.drawer_account_menu_more),
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

            AnchoredMenuPopup(
                expanded = showAccountMenu,
                onDismiss = { showAccountMenu = false },
                contentWidth = true,
                align = com.deepseek.dsh.core.designsystem.DshMenuAlign.Start,
                anchorGap = 0.dp
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
                        label = stringResource(R.string.drawer_account_login),
                        onClick = {
                            showAccountMenu = false
                            onSignIn()
                        },
                    )
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
 * 会话行长按菜单：置顶 / 重命名 / 归档 / 复制 Session ID。
 *
 * 每一项都直接落到官方 Remote（`workspace/pinSession`、`session/rename`、
 * `workspace/archiveSession`），手机端不显示桌面快捷键提示。
 */
@Composable
private fun SessionRowMenu(
    expanded: Boolean,
    session: SessionDisplayItem,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFork: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
) {
    data class Entry(val labelResId: Int, val iconResId: Int, val action: () -> Unit)

    val context = androidx.compose.ui.platform.LocalContext.current
    val entries = listOf(
        Entry(
            if (session.pinned) R.string.row_unpin else R.string.row_pin,
            com.deepseek.dsh.core.designsystem.R.drawable.ic_row_pin,
        ) { onTogglePin() },
        Entry(
            R.string.row_rename,
            com.deepseek.dsh.core.designsystem.R.drawable.ic_action_rename_official,
        ) { onRename() },
        Entry(
            R.string.row_fork,
            com.deepseek.dsh.core.designsystem.R.drawable.ic_row_branch,
        ) { onFork() },
        Entry(
            if (session.archived) R.string.row_unarchive else R.string.row_archive,
            com.deepseek.dsh.core.designsystem.R.drawable.ic_row_archive,
        ) { onToggleArchive() },
        Entry(
            R.string.row_session_id,
            com.deepseek.dsh.core.designsystem.R.drawable.ic_row_copy,
        ) {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("sessionId", session.sessionId)
            )
        },
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
            DshMenuRow(onClick = { onDismiss(); entry.action() }) {
                DshMenuIcon(entry.iconResId)
                DshMenuLabel(stringResource(entry.labelResId))
            }
        }
    }
}

/** 工作区长按菜单：与账户/会话菜单同一 Dsh 菜单材质。 */
/** 视图选项菜单：分组方式 / 排序方式 / 筛选会话（对齐官方 Drawer 菜单）。 */
/** 抽屉会话列表的官方样式滚动条：右侧细圆角、随滚动位置移动。 */
private fun Modifier.drawerScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    color: Color,
): Modifier = this.drawWithContent {
    drawContent()
    val layout = state.layoutInfo
    val visible = layout.visibleItemsInfo
    if (visible.isEmpty() || layout.totalItemsCount <= visible.size) return@drawWithContent
    val first = visible.first()
    val last = visible.last()
    val viewportHeight = size.height
    if (viewportHeight <= 0f) return@drawWithContent
    val contentHeight = (last.offset + last.size - first.offset).toFloat().coerceAtLeast(1f)
    val thumbHeight = (viewportHeight * visible.size / layout.totalItemsCount.toFloat())
        .coerceIn(24.dp.toPx(), viewportHeight)
    val scrollRange = (layout.totalItemsCount - 1).coerceAtLeast(1)
    val fraction = ((first.index + (first.offset - layout.viewportStartOffset).toFloat() / first.size) / scrollRange)
        .coerceIn(0f, 1f)
    val top = fraction * (viewportHeight - thumbHeight)
    drawRoundRect(
        color = color.copy(alpha = 0.35f),
        topLeft = androidx.compose.ui.geometry.Offset(size.width - 7.dp.toPx(), top),
        size = androidx.compose.ui.geometry.Size(5.dp.toPx(), thumbHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx())
    )
}
/** 可拖动的会话滚动条：拖动时跳转到对应位置并显示会话标题提示。 */
@Composable
private fun DrawerScrollbarScrubber(
    state: androidx.compose.foundation.lazy.LazyListState,
    titles: List<String>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var tooltipTitle by remember { mutableStateOf("") }
    var tooltipFraction by remember { mutableStateOf(0f) }
    val colors = com.deepseek.dsh.core.designsystem.dshPanelColors()
    Box(
        modifier = modifier
            .pointerInput(state, titles) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        tooltipFraction = (offset.y / size.height).coerceIn(0f, 1f)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                        tooltipFraction = fraction
                        val total = state.layoutInfo.totalItemsCount
                        if (total > 0) {
                            scope.launch {
                                state.scrollToItem((fraction * (total - 1)).toInt().coerceIn(0, total - 1))
                            }
                        }
                        val index = (fraction * (titles.size - 1)).toInt().coerceIn(0, (titles.size - 1).coerceAtLeast(0))
                        tooltipTitle = titles.getOrNull(index).orEmpty()
                    }
                )
            }
    ) {
        if (dragging && tooltipTitle.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.bgModulePlatform,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = (tooltipFraction * 800f).dp - 20.dp)
                ) {
                    Text(
                        text = tooltipTitle,
                        color = colors.labelPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
@Composable
private fun ViewOptionsMenu(
    groupMode: WorkspaceGroupMode,
    sortMode: SessionSortMode,
    archiveFilter: ArchiveFilter,
    onGroupMode: (WorkspaceGroupMode) -> Unit,
    onSortMode: (SessionSortMode) -> Unit,
    onArchiveFilter: (ArchiveFilter) -> Unit,
) {
    DshMenuSectionLabel("分组方式")
    GroupModeRow("按工作区", groupMode == WorkspaceGroupMode.WORKSPACE) { onGroupMode(WorkspaceGroupMode.WORKSPACE) }
    GroupModeRow("按工作区树", groupMode == WorkspaceGroupMode.TREE) { onGroupMode(WorkspaceGroupMode.TREE) }
    GroupModeRow("单列表", groupMode == WorkspaceGroupMode.SINGLE_LIST) { onGroupMode(WorkspaceGroupMode.SINGLE_LIST) }
    DshMenuSectionLabel("排序方式")
    GroupModeRow("手动排序", sortMode == SessionSortMode.MANUAL) { onSortMode(SessionSortMode.MANUAL) }
    GroupModeRow("最近更新", sortMode == SessionSortMode.RECENT) { onSortMode(SessionSortMode.RECENT) }
    DshMenuSectionLabel("筛选会话")
    GroupModeRow("隐藏已归档", archiveFilter == ArchiveFilter.HIDE_ARCHIVED) {
        onArchiveFilter(ArchiveFilter.HIDE_ARCHIVED)
    }
    GroupModeRow("全部对话（显示已归档）", archiveFilter == ArchiveFilter.ALL) {
        onArchiveFilter(ArchiveFilter.ALL)
    }
    GroupModeRow("仅显示已归档", archiveFilter == ArchiveFilter.ONLY_ARCHIVED) {
        onArchiveFilter(ArchiveFilter.ONLY_ARCHIVED)
    }
}

@Composable
private fun GroupModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    DshMenuRow(onClick = onClick) {
        DshMenuLabel(label, Modifier.weight(1f))
        if (selected) DshMenuCheck()
    }
}
@Composable
private fun WorkspaceRowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    AnchoredMenuPopup(
        expanded = expanded,
        onDismiss = onDismiss,
        contentWidth = true,
        anchorGap = 0.dp,
    ) {
        DshMenuRow(onClick = { onDismiss(); onRename() }) {
            DshMenuIcon(com.deepseek.dsh.core.designsystem.R.drawable.ic_action_rename_official)
            DshMenuLabel("重命名")
        }
        DshMenuRow(onClick = { onDismiss(); onDelete() }, danger = true) {
            val error = com.deepseek.dsh.core.designsystem.dshPanelColors().stateErrorPrimary
            DshMenuIcon(
                com.deepseek.dsh.core.designsystem.R.drawable.ic_action_delete_workspace_official,
                tint = error
            )
            Text(
                text = "删除工作区",
                color = error,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal
            )
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











































