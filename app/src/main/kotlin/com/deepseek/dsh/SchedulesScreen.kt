package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.ScheduleInfo

/**
 * 计划任务页与宿主之间的桥。
 *
 * 目录来自官方 `schedule/catalog`；取消走 `schedule/delete`。与其它桥一样，
 * 宿主只注入引用，`MainAppHost` 不再增加参数。
 */
object ScheduleBridge {
    var items by mutableStateOf<List<ScheduleInfo>>(emptyList())

    /** 宿主在会话 effect 里调用一次即可，避免把 lambda 内联进 MainAppHost。 */
    fun wire(
        repository: com.deepseek.dsh.core.data.DshRepository,
        hostId: String,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        scope.launch {
            runCatching { repository.refreshScheduleCatalog(hostId) }
        }
        canceller = { schedule ->
            scope.launch {
                runCatching {
                    repository.deleteSchedule(hostId, schedule.sessionId, schedule.id)
                }
            }
        }
    }

    private var canceller: ((ScheduleInfo) -> Unit)? = null

    fun cancel(schedule: ScheduleInfo) {
        canceller?.invoke(schedule)
    }
}

/**
 * 计划任务（官方 schedule）：列出 Host 上所有提醒，可取消。
 *
 * 官方 Web 客户端在设置/自动化面板里展示同一份 catalog；这里用列表形式，
 * 每条显示标题、触发时间、状态与会话绑定。
 */
@Composable
fun SchedulesScreen(onCreateTask: () -> Unit) {
    val schedules = ScheduleBridge.items
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.page_automation),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.schedule_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        if (schedules.isEmpty()) {
            Text(
                text = stringResource(R.string.schedule_empty),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateTask)
                    .padding(vertical = 24.dp),
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(schedules, key = { it.id }) { schedule ->
                ScheduleRow(
                    schedule = schedule,
                    onCancel = { ScheduleBridge.cancel(schedule) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(schedule: ScheduleInfo, onCancel: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = schedule.title.ifBlank { schedule.id },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (schedule.status == "active") {
                    stringResource(R.string.schedule_active)
                } else {
                    schedule.status
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (schedule.scheduledAt.isNotBlank()) {
            Text(
                text = schedule.scheduledAt.replace("T", " ").removeSuffix("Z"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = stringResource(R.string.schedule_cancel),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onCancel)
                .padding(top = 6.dp, start = 12.dp),
        )
    }
}
