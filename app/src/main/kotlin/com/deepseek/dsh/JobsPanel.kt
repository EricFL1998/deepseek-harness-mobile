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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshDialog
import com.deepseek.dsh.core.designsystem.DshDialogButton
import com.deepseek.dsh.core.protocol.HostJobInfo

/**
 * 官方 `job/list` 的后台任务面板：列表 + 终止按钮 + 输出查看。
 *
 * 任务集合由 `job/list` 流实时推送；点「输出」打开 `job/follow` 的输出窗。
 */
@Composable
fun JobsPanel(
    jobs: List<HostJobInfo>,
    onKill: (HostJobInfo) -> Unit,
    onOpenOutput: (HostJobInfo) -> Unit,
) {
    if (jobs.isEmpty()) {
        Text(
            text = stringResource(R.string.jobs_empty),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        return
    }
    Column {
        jobs.forEach { job ->
            val running = job.status == "running" || job.status == "stopping"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenOutput(job) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态点：running 旋转、settled 实心。
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (job.status) {
                                    "completed" -> androidx.compose.ui.graphics.Color(0xFF34C759)
                                    "failed", "killed" -> androidx.compose.ui.graphics.Color(0xFFFF3B30)
                                    else -> MaterialTheme.colorScheme.outline
                                },
                            ),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.label.ifBlank { job.id },
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                    )
                    val sub = buildString {
                        append(job.kind)
                        job.progress?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        if (!running) job.detail?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    }
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                        )
                    }
                }
                if (running) {
                    TextButton(onClick = { onKill(job) }) {
                        Text(
                            text = stringResource(R.string.jobs_kill),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * `job/follow` 输出窗：opened/output/status 帧由宿主聚合后传入。
 */
@Composable
fun JobOutputDialog(
    job: HostJobInfo,
    output: String,
    statusLine: String?,
    onDismiss: () -> Unit,
) {
    DshDialog(
        onDismiss = onDismiss,
        title = job.label.ifBlank { job.id },
        closeLabel = stringResource(R.string.action_close),
        footer = {
            DshDialogButton(
                text = stringResource(R.string.action_close),
                primary = true,
                enabled = true,
                onClick = onDismiss,
            )
        },
        content = {
            Column {
                statusLine?.takeIf { it.isNotBlank() }?.let { line ->
                    Text(
                        text = line,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (output.isBlank()) {
                    Text(
                        text = stringResource(R.string.jobs_output_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = Int.MAX_VALUE / 2,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            items(listOf(output)) { text ->
                                Text(
                                    text = text,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
