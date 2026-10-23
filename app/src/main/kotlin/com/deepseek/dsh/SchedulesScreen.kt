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
import androidx.compose.runtime.collectAsState
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
    /** 计划任务目录的实时数据源（取消后会自动跟着刷新）。 */
    var flow: kotlinx.coroutines.flow.StateFlow<List<ScheduleInfo>>? = null
        private set

    /** 进入页面时主动拉一次（Host 侧新提醒不会推给客户端）。 */
    var refresh: (suspend () -> Unit)? = null
        private set

    /** 宿主在会话 effect 里调用一次即可，避免把 lambda 内联进 MainAppHost。 */
    fun wire(
        repository: com.deepseek.dsh.core.data.DshRepository,
        hostId: String,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        flow = repository.scheduleCatalogFlow
        refresh = { runCatching { repository.refreshScheduleCatalog(hostId) } }
        scope.launch { refresh?.invoke() }
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
    val source = ScheduleBridge.flow
    val schedules = if (source == null) emptyList() else source.collectAsState().value
    androidx.compose.runtime.LaunchedEffect(Unit) {
        ScheduleBridge.refresh?.invoke()
    }
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
        Spacer(Modifier.height(16.dp))
        if (schedules.isEmpty()) {
            Text(
                text = stringResource(R.string.schedule_empty),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.Surface(
                onClick = onCreateTask,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    text = stringResource(R.string.schedule_empty_action),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
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
        val frequency = frequencyLabel(schedule)
        if (frequency.isNotBlank()) {
            Text(
                text = frequency,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (schedule.scheduledAt.isNotBlank()) {
            val absolute = localTimeOf(schedule.scheduledAt)
            val relative = relativeToNow(schedule.scheduledAt)
            Text(
                text = if (relative.isBlank()) absolute else "$absolute · $relative",
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

/** 官方 ui-schedule 的频率文案：after/every/daily/weekly/cron。 */
@Composable
private fun frequencyLabel(schedule: ScheduleInfo): String = when (schedule.kind) {
    "after" -> androidx.compose.ui.res.stringResource(
        R.string.schedule_frequency_after, humanDuration(schedule.afterSeconds),
    )
    "every" -> androidx.compose.ui.res.stringResource(
        R.string.schedule_frequency_every, humanDuration(schedule.everySeconds),
    )
    "daily" -> androidx.compose.ui.res.stringResource(
        R.string.schedule_frequency_daily, schedule.time.take(5),
    )
    "weekly" -> androidx.compose.ui.res.stringResource(
        R.string.schedule_frequency_weekly,
        schedule.weekdays.joinToString(",") + " " + schedule.time.take(5),
    )
    "cron" -> androidx.compose.ui.res.stringResource(
        R.string.schedule_frequency_cron, schedule.expression,
    )
    else -> ""
}

private fun humanDuration(seconds: Long): String = when {
    seconds <= 0 -> "0m"
    seconds % 3600 == 0L -> "${seconds / 3600}h"
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0L -> "${seconds / 60}m"
    else -> "${seconds / 60}m"
}

/** ISO 时间转本地 "MMM d, HH:mm"；解析失败时原样返回。 */
private fun localTimeOf(iso: String): String = runCatching {
    java.time.Instant.parse(iso)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"))
}.getOrDefault(iso)

/** 距离下次触发的相对时间（官方列表同样显示 "in 30 min"）。 */
@Composable
private fun relativeToNow(iso: String): String {
    val seconds = runCatching {
        java.time.Duration.between(java.time.Instant.now(), java.time.Instant.parse(iso)).seconds
    }.getOrDefault(0)
    return when {
        seconds <= 0 -> stringResource(R.string.schedule_due)
        seconds < 3600 -> stringResource(R.string.schedule_in_minutes, (seconds / 60).toInt())
        seconds < 86_400 -> stringResource(R.string.schedule_in_hours, (seconds / 3600).toInt())
        else -> stringResource(R.string.schedule_in_days, (seconds / 86_400).toInt())
    }
}
