package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshMenuLabel
import com.deepseek.dsh.core.designsystem.DshMenuRow
import com.deepseek.dsh.core.designsystem.DshMenuSectionLabel
import com.deepseek.dsh.core.designsystem.dshPanelColors
import com.deepseek.dsh.core.model.AgentTeamSummary
import com.deepseek.dsh.core.model.AgentTeamTask
import com.deepseek.dsh.core.model.TeamTaskItem

/**
 * 「智能体团队」插件的原生界面。
 *
 * 官方客户端在会话头部注册一个 `conversation.session.header.actions` 动作，
 * 点开是成员名单 + 共享任务看板的浮层；数据来自当前会话的 `agentTeam` 投影
 * （不是另一套 RPC）。这里按同样的数据与交互做原生版，文案取自官方
 * `experimental/client-ui-agent-team/src/client/locales.ts` 的 zh 字典。
 */
internal object AgentTeamPluginUi {
    /** 启用后才会出现团队动作的 profile bundle。 */
    const val BUNDLE = "@deepseek-ai/dsh-experimental-agent-team-profile"

    /** 团队图标的 path，取自 Material 的 group 图形（24dp 视口）。 */
    private const val USERS_PATH =
        "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 " +
            "2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5" +
            "c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5" +
            "c0-2.33-4.67-3.5-7-3.5z"

    /** 会话头部的团队动作图标。 */
    val usersIcon: ImageVector by lazy {
        val nodes = androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(USERS_PATH)
            .toNodes()
        ImageVector.Builder(
            name = "DshUsers",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(nodes, fill = SolidColor(Color.Black)).build()
    }

    /**
     * 会话头部的团队动作。
     *
     * @param memberCount 成员数（>1 时显示小徽标，与官方 count badge 一致）。
     * @param running 是否有成员在运行。
     * @param onClick 点开/收起面板。
     */
    @Composable
    fun HeaderAction(memberCount: Int, running: Boolean, onClick: () -> Unit) {
        val colors = dshPanelColors()
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.bgModulePlatform)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = usersIcon,
                    contentDescription = stringResource(R.string.official_agent_team_title),
                    tint = colors.labelPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.official_agent_team_title),
                    color = colors.labelPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                if (memberCount > 1) {
                    Text(
                        text = stringResource(R.string.header_agents_count, memberCount),
                        color = colors.labelSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (running) androidx.compose.material3.MaterialTheme.colorScheme.primary else colors.labelTertiary),
                )
            }
        }
    }
}

/**
 * 团队浮层内容（成员 + 共享任务）。
 *
 * @param summary 会话投影；null 表示没有团队数据。
 * @param fallbackTasks bridge `team/listTasks` 的任务；投影里没有任务时用它。
 * @param loading 第一次加载中。
 * @param error 读取失败原因。
 * @param currentSessionId 当前打开的会话 id，用来标「当前会话」。
 * @param onOpenMember 切换到成员会话。
 */
@Composable
internal fun AgentTeamMenuPanel(
    summary: AgentTeamSummary?,
    fallbackTasks: List<TeamTaskItem>,
    loading: Boolean,
    error: String?,
    currentSessionId: String?,
    onOpenMember: (String) -> Unit,
) {
    val colors = dshPanelColors()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    if (error != null) {
        Text(
            text = error,
            color = colors.stateErrorPrimary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
    when {
        summary == null && loading -> Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.tertiary),
            )
            Text(stringResource(R.string.agent_team_loading), color = colors.labelSecondary, fontSize = 12.5.sp)
        }
        summary == null -> Text(
            text = stringResource(R.string.agent_team_unavailable),
            color = colors.labelTertiary,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        summary.failure != null -> Text(
            text = stringResource(R.string.agent_team_invalid, summary.failure ?: ""),
            color = colors.stateErrorPrimary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        else -> {
            val members = summary.members
            if (members.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.agent_team_members), color = colors.labelPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (members.size > 1) {
                        Text(
                            text = members.size.toString(),
                            color = colors.labelTertiary,
                            fontSize = 11.sp,
                        )
                    }
                }
                members.forEach { member ->
                    val running = member.phase == "active" || member.phase == "provisioning"
                    DshMenuRow(onClick = { onOpenMember(member.id) }, enabled = member.phase != "provisioning") {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = member.name,
                                    color = colors.labelPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (member.id == currentSessionId) {
                                    TagChip(stringResource(R.string.agent_team_current_session), colors.labelSecondary)
                                }
                            }
                            Text(
                                text = when (member.phase) {
                                    "provisioning" -> stringResource(R.string.agent_team_phase_provisioning)
                                    "failed" -> stringResource(R.string.agent_team_phase_failed)
                                    "active" -> stringResource(R.string.agent_team_phase_active)
                                    else -> stringResource(R.string.agent_team_phase_idle)
                                },
                                color = when (member.phase) {
                                    "failed" -> colors.stateErrorPrimary
                                    "active", "provisioning" -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    else -> colors.labelTertiary
                                },
                                fontSize = 11.sp,
                            )
                            val memberError = member.error
                            if (memberError != null) {
                                Text(
                                    text = memberError,
                                    color = colors.labelTertiary,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                    }
                }
            }

            val tasks = if (summary.tasks.isNotEmpty()) {
                summary.tasks.map { task ->
                    TeamTaskItem(
                        id = task.id,
                        subject = task.subject,
                        description = task.description,
                        status = task.status,
                        ownerName = task.ownerName,
                    ) to task
                }
            } else {
                fallbackTasks.map { it to null }
            }
            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.agent_team_no_tasks),
                    color = colors.labelTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.agent_team_tasks), color = colors.labelPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(tasks.size.toString(), color = colors.labelTertiary, fontSize = 11.sp)
                }
                tasks.take(12).forEach { (task, full) ->
                    DshMenuRow(onClick = { expanded[task.id] = !(expanded[task.id] ?: false) }) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.subject,
                                color = colors.labelPrimary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (task.description.isNotBlank()) {
                                val open = expanded[task.id] == true
                                Text(
                                    text = task.description,
                                    color = colors.labelTertiary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = if (open) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (task.description.length > 40) {
                                    Text(
                                        text = if (open) stringResource(R.string.agent_team_collapse) else stringResource(R.string.agent_team_expand),
                                        color = colors.labelSecondary,
                                        fontSize = 10.5.sp,
                                    )
                                }
                            }
                            val blockedLabel = stringResource(R.string.agent_team_blocked)
                            val readyLabel = stringResource(R.string.agent_team_ready)
                            val depsLabel = stringResource(R.string.agent_team_dependencies)
                            val scopesLabel = stringResource(R.string.agent_team_write_scopes)
                            val meta = buildString {
                                append(
                                    when (task.status) {
                                        "in_progress" -> stringResource(R.string.agent_team_task_in_progress)
                                        "completed" -> stringResource(R.string.agent_team_task_completed)
                                        else -> stringResource(R.string.agent_team_task_pending)
                                    },
                                )
                                append(" · Owner: ").append(task.ownerName ?: stringResource(R.string.agent_team_owner_unassigned))
                                if (task.status == "pending") {
                                    append(" · ").append(if (full?.ready == false) blockedLabel else readyLabel)
                                }
                                full?.blockedBy?.takeIf { it.isNotEmpty() }?.let { blocked ->
                                    append(" · ").append(depsLabel).append(": ").append(blocked.joinToString(", "))
                                }
                                full?.writeScopes?.takeIf { it.isNotEmpty() }?.let { scopes ->
                                    append(" · ").append(scopesLabel).append(": ").append(scopes.joinToString(", "))
                                }
                            }
                            Text(
                                text = meta,
                                color = colors.labelTertiary,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                            )
                            full?.writeScopeWarnings?.forEach { warning ->
                                Text(warning, color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 官方面板里的小标签（当前会话等）。 */
@Composable
private fun TagChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text = text, color = color, fontSize = 10.sp)
    }
}
