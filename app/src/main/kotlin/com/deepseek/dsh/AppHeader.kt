package com.deepseek.dsh

import com.deepseek.dsh.core.designsystem.DshIcons
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.contentOrNull
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshTheme
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.MessageRole
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.core.model.isThirdPartyPlugin
import com.deepseek.dsh.core.model.SurfaceKind
import com.deepseek.dsh.feature.chat.ModernChatScreen
import com.deepseek.dsh.feature.files.FilesScreen
import com.deepseek.dsh.feature.files.WorkspaceManager
import com.deepseek.dsh.feature.sessions.ModernNavigationDrawerContent
import com.deepseek.dsh.plugin.surface.PluginOverlayContainer
import com.deepseek.dsh.plugin.surface.SurfaceCatalogStore
import com.deepseek.dsh.plugin.surface.SurfaceSheet
import com.deepseek.dsh.runtime.host.DefaultHostRuntimeManager
import com.deepseek.dsh.runtime.host.HostProcessRunner
import com.deepseek.dsh.runtime.host.HostRuntimeHolder
import com.deepseek.dsh.runtime.host.HostRuntimeManager
import com.deepseek.dsh.runtime.host.RuntimeLayout
import com.deepseek.dsh.runtime.host.RuntimePhase
import kotlinx.coroutines.launch
import java.io.File


@Composable
fun PageBackBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 与左侧按钮等宽，标题才会真正居中。
        Spacer(modifier = Modifier.size(40.dp))
    }
}

/** 子页面顶栏标题；与抽屉里的条目文案保持一致。 */
@Composable
internal fun pageTitleOf(page: ActivePage): String = when (page) {
    ActivePage.PLUGINS -> stringResource(R.string.page_plugins)
    ActivePage.AUTOMATION -> stringResource(R.string.page_automation)
    ActivePage.FILES -> stringResource(R.string.page_files)
    ActivePage.HOSTS -> stringResource(R.string.page_hosts)
    ActivePage.RUNTIME -> stringResource(R.string.page_runtime)
    ActivePage.TERMINAL -> stringResource(R.string.terminal_title)
    ActivePage.SETTINGS -> stringResource(R.string.page_settings)
    ActivePage.CHAT -> "deepseek"
}
@Composable
internal fun HeaderChip(
    label: String,
    iconResId: Int,
    trailingChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = com.deepseek.dsh.core.designsystem.dshPanelColors()
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = iconResId),
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.labelSecondary,
            maxLines = 1
        )
        if (trailingChevron) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.deepseek.dsh.core.designsystem.R.drawable.ic_chevron_down
                ),
                contentDescription = null,
                tint = colors.labelCaption,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
internal fun HeaderTeamPanel(
    members: List<Pair<String, Boolean>>,
    tasks: List<com.deepseek.dsh.core.model.TeamTaskItem>,
) {
    com.deepseek.dsh.core.designsystem.DshMenuSectionLabel(stringResource(R.string.agent_team_members) + " " + members.size)
    members.take(8).forEach { (name, running) ->
        com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
            com.deepseek.dsh.core.designsystem.DshMenuLabel(name, Modifier.weight(1f))
            Text(
                text = if (running) stringResource(R.string.agent_team_phase_active) else stringResource(R.string.agent_team_phase_idle),
                fontSize = 11.sp,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
    com.deepseek.dsh.core.designsystem.DshMenuSectionLabel(stringResource(R.string.agent_team_tasks) + " " + tasks.size)
    tasks.take(8).forEach { task ->
        com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(task.subject, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
                Text(
                    text = "${task.ownerName ?: stringResource(R.string.agent_team_owner_unassigned)} · ${when (task.status) {
                        "in_progress" -> stringResource(R.string.agent_team_task_in_progress)
                        "completed" -> stringResource(R.string.agent_team_task_completed)
                        else -> stringResource(R.string.agent_team_task_pending)
                    }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
internal fun HeaderJobsPanel(tasks: List<com.deepseek.dsh.core.model.TeamTaskItem>) {
    val running = tasks.filter { it.status == "in_progress" }
    val settled = tasks.filter { it.status != "in_progress" }
    if (running.isNotEmpty()) {
        com.deepseek.dsh.core.designsystem.DshMenuSectionLabel(stringResource(R.string.agent_team_task_in_progress))
        running.forEach { task ->
            com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(task.subject, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
                    Text(task.ownerName ?: "lead", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
    if (settled.isNotEmpty()) {
        com.deepseek.dsh.core.designsystem.DshMenuSectionLabel(stringResource(R.string.finished_section, settled.size))
        settled.take(8).forEach { task ->
            com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
                Text(task.subject, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
            }
        }
    }
}
@Composable
internal fun RootfsUpdateBanner(
    installed: String,
    available: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = com.deepseek.dsh.core.designsystem.dshPanelColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgModulePlatform)
            .clickable(onClick = onUpdate)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                id = com.deepseek.dsh.core.designsystem.R.drawable.ic_stat_gauge
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.runtime_update_available),
                fontSize = 13.sp,
                color = colors.labelPrimary
            )
            Text(
                text = "$installed → $available",
                fontSize = 11.sp,
                color = colors.labelTertiary
            )
        }
        Text(
            text = stringResource(R.string.action_update),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                id = com.deepseek.dsh.core.designsystem.R.drawable.ic_close
            ),
            contentDescription = stringResource(R.string.dismiss_reminder),
            tint = colors.labelTertiary,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDismiss)
        )
    }
}
@Composable
fun ModernTopAppBar(
    title: String,
    showModeCapsule: Boolean = false,
    teamTasks: List<com.deepseek.dsh.core.model.TeamTaskItem> = emptyList(),
    /** 当前会话的后台任务（官方 job/list）；非空时显示任务按钮。 */
    hostJobs: List<com.deepseek.dsh.core.protocol.HostJobInfo> = emptyList(),
    onKillJob: (com.deepseek.dsh.core.protocol.HostJobInfo) -> Unit = {},
    onOpenJobOutput: (com.deepseek.dsh.core.protocol.HostJobInfo) -> Unit = {},
    /** 当前会话的团队成员投影（官方 header 的团队动作数据）。 */
    agentTeam: com.deepseek.dsh.core.model.AgentTeamSummary? = null,
    /** 团队投影第一次读取中（浮层显示「正在加载团队…」）。 */
    agentTeamLoading: Boolean = false,
    /** 「智能体团队」插件是否启用；启用才显示团队动作。 */
    teamEnabled: Boolean = false,
    currentSessionId: String? = null,
    onOpenMember: (String) -> Unit = {},
    /** 「开发者工具」插件是否启用；启用才显示会话数据诊断入口。 */
    inspectorEnabled: Boolean = false,
    onOpenInspector: () -> Unit = {},
    /** 右侧栏开关；null 表示当前页面没有右侧栏。 */
    onToggleRightPane: (() -> Unit)? = null,
    rightPaneOpen: Boolean = false,
    onMenuClick: () -> Unit
) {
    val barColors = com.deepseek.dsh.core.designsystem.dshPanelColors()
    val members = remember(teamTasks) {
        buildList {
            add("lead" to teamTasks.any { it.ownerName == "lead" && it.status == "in_progress" })
            teamTasks.mapNotNull { it.ownerName }
                .filter { it != "lead" }
                .distinct()
                .forEach { name ->
                    add(name to teamTasks.any { it.ownerName == name && it.status == "in_progress" })
                }
        }
    }
    val runningTasks = teamTasks.count { it.status == "in_progress" }
    var teamPanelOpen by remember { mutableStateOf(false) }
    var jobsPanelOpen by remember { mutableStateOf(false) }
    val teamMemberCount = agentTeam?.members?.size ?: 0
    val teamRunning = agentTeam?.members?.any { it.phase == "active" || it.phase == "provisioning" } == true
    Box(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧汉堡菜单圆形按键
            Surface(
                shape = CircleShape,
                color = barColors.bgModulePlatform,
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = DshIcons.PanelLeftOutline,
                        contentDescription = "Menu",
                        tint = barColors.labelPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (showModeCapsule) {
                // 中间切换胶囊 (参考截图 2: 快速 / 进阶)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.mode_fast),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.mode_deep_thinking),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = title,
                    color = barColors.labelPrimary,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // 动作区：标题右边的按钮统一放进可横向滚动的一行。
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {

            if (onToggleRightPane != null) {
                Surface(
                    shape = CircleShape,
                    color = if (rightPaneOpen) barColors.bgModulePlatform else androidx.compose.ui.graphics.Color.Transparent,
                    onClick = onToggleRightPane,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = DshIcons.PanelLeftOutline,
                            contentDescription = "Right sidebar",
                            tint = barColors.labelPrimary,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(180f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // 团队动作：官方由「智能体团队」插件注册到
            // conversation.session.header.actions；启用时在标题右侧。
            if (inspectorEnabled) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(barColors.bgModulePlatform)
                        .clickable(onClick = onOpenInspector)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.BugReport,
                        contentDescription = stringResource(R.string.session_diagnostics),
                        tint = barColors.labelPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (teamEnabled) {
                AgentTeamPluginUi.HeaderAction(
                    memberCount = teamMemberCount,
                    running = teamRunning,
                    onClick = { teamPanelOpen = !teamPanelOpen },
                )
            }
            // 后台任务：官方 JobListAction 的触发按钮（点 + 数量文案 + 状态点 + chevron）。
            if (hostJobs.isNotEmpty()) {
                val jobsRunning = hostJobs.any { it.status == "running" || it.status == "stopping" }
                val liveCount = hostJobs.count { it.status == "running" || it.status == "stopping" }
                val countLabel = if (jobsRunning) {
                    stringResource(R.string.header_jobs_running, liveCount)
                } else {
                    stringResource(R.string.header_jobs_idle, hostJobs.size)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(barColors.bgModulePlatform)
                        .clickable { jobsPanelOpen = !jobsPanelOpen }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (jobsRunning) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(barColors.labelPrimary),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = countLabel,
                            fontSize = 12.sp,
                            color = barColors.labelPrimary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (jobsPanelOpen) "\u25B4" else "\u25BE",
                            fontSize = 10.sp,
                            color = barColors.labelSecondary,
                        )
                    }
                }
            }
            }
        }
        // header 的 0.5dp border-l3 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(barColors.borderL3)
        )
    }
        // 团队面板：官方是锚定在标题动作下方的浮层；用 Popup 实现「点外部关闭」。
        if (teamPanelOpen) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(
                    x = 0,
                    y = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.roundToPx() },
                ),
                onDismissRequest = { teamPanelOpen = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Box(modifier = Modifier.padding(end = 12.dp)) {
                    com.deepseek.dsh.core.designsystem.DshMenuCard {
                        AgentTeamMenuPanel(
                            summary = agentTeam,
                            fallbackTasks = teamTasks,
                            loading = agentTeamLoading,
                            error = null,
                            currentSessionId = currentSessionId,
                            onOpenMember = { memberId ->
                                teamPanelOpen = false
                                onOpenMember(memberId)
                            },
                        )
                    }
                }
            }
        }
        // 后台任务面板：与团队面板同样的锚定浮层。
        if (jobsPanelOpen) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(
                    x = 0,
                    y = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.roundToPx() },
                ),
                onDismissRequest = { jobsPanelOpen = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Box(modifier = Modifier.padding(end = 12.dp)) {
                    com.deepseek.dsh.core.designsystem.DshMenuCard {
                        JobsPanel(
                            jobs = hostJobs,
                            onKill = { job -> onKillJob(job) },
                            onOpenOutput = { job ->
                                jobsPanelOpen = false
                                onOpenJobOutput(job)
                            },
                        )
                    }
                }
            }
        }
    }
}
