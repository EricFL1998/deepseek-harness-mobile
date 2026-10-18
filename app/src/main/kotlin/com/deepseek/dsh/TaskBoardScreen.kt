package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.designsystem.dshPanelColors

import com.deepseek.dsh.core.model.TeamTaskItem

/** 任务看板的三个状态分组（agent-team locales.ts 的 status.*）。 */
internal enum class TaskBoardStatus(val label: String) {
    Pending("待处理"),
    InProgress("进行中"),
    Completed("已完成"),
}

/**
 * 任务看板中的一张任务卡；Host 的 Team 任务数据接入后按此模型传入。
 */
typealias TaskBoardTaskUi = TeamTaskItem

/** 一个状态分组及其任务列表。 */
internal data class TaskBoardColumnUi(
    val status: TaskBoardStatus,
    val tasks: List<TaskBoardTaskUi>,
)

/**
 * 「任务看板」页面（Task board）。
 */
@Composable
fun TaskBoardScreen(
    tasks: List<TeamTaskItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val pendingList = tasks.filter { it.status == "pending" }
    val inProgressList = tasks.filter { it.status == "in_progress" || it.status == "running" }
    val completedList = tasks.filter { it.status == "completed" || it.status == "done" }

    TaskBoardPage(
        columns = listOf(
            TaskBoardColumnUi(status = TaskBoardStatus.Pending, tasks = pendingList),
            TaskBoardColumnUi(status = TaskBoardStatus.InProgress, tasks = inProgressList),
            TaskBoardColumnUi(status = TaskBoardStatus.Completed, tasks = completedList),
        ),
        modifier = modifier,
    )
}

/**
 * 任务看板的数据化实现：按状态分组渲染，真实数据接入时替换 [TaskBoardScreen]
 * 传入的列表即可。
 *
 * @param columns 待处理 / 进行中 / 已完成三个分组。
 * @param modifier 外部修饰符。
 */
@Composable
internal fun TaskBoardPage(
    columns: List<TaskBoardColumnUi>,
    modifier: Modifier = Modifier,
) {
    val colors = dshPanelColors()
    CompositionLocalProvider(LocalDshPanelColors provides colors) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgBase)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = "任务看板",
                color = colors.labelPrimary,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "团队会话中的共享任务会显示在这里",
                color = colors.labelSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(28.dp))
            columns.forEachIndexed { index, column ->
                TaskBoardSection(column)
                if (index != columns.lastIndex) Spacer(Modifier.height(32.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "任务由 Team 会话中的 Lead 和队友创建，本页只读展示。",
                color = colors.labelTertiary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

/** 一个状态分组：8dp 状态圆点 + 14/22 标题 + 12/18 计数 + 卡区或空态。 */
@Composable
private fun TaskBoardSection(column: TaskBoardColumnUi) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskBoardStatusDot(column.status)
            Spacer(Modifier.width(8.dp))
            Text(
                text = column.status.label,
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = column.tasks.size.toString(),
                color = colors.labelSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (column.tasks.isEmpty()) {
            Text(
                text = "暂无任务",
                color = colors.labelTertiary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                column.tasks.forEach { task -> TaskBoardTaskCard(task) }
            }
        }
    }
}

/** 8dp 状态圆点；三种状态分别使用二级 / 主 / 三级色阶。 */
@Composable
private fun TaskBoardStatusDot(status: TaskBoardStatus) {
    val colors = LocalDshPanelColors.current
    val tint = when (status) {
        TaskBoardStatus.Pending -> colors.labelSecondary
        TaskBoardStatus.InProgress -> colors.labelPrimary
        TaskBoardStatus.Completed -> colors.labelTertiary
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(tint),
    )
}

/** 任务卡：0.5dp border-l2 hairline + 12dp 圆角，标题 / 描述 / 元信息三段。 */
@Composable
private fun TaskBoardTaskCard(task: TaskBoardTaskUi) {
    val colors = LocalDshPanelColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bgLayer2)
            .border(0.5.dp, colors.borderL2, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = task.subject,
            color = colors.labelPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (task.description.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = task.description,
                color = colors.labelSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${task.ownerName ?: "未分配"} · ${task.id}",
            color = colors.labelTertiary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
