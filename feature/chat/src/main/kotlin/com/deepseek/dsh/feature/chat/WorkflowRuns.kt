package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.WorkflowRunInfo

/**
 * workflow 运行卡片的数据桥（宿主注入）。
 *
 * 官方 `ui-workflow-run` 把一次 workflow 画成会话里的节点：运行名 + 状态 +
 * 各 phase 下的成员（子会话）。手机端用同样的信息画成卡片，数据来自客户端对
 * tool-workflow 事件的聚合。
 */
object WorkflowRunsBridge {
    var items by mutableStateOf<List<WorkflowRunInfo>>(emptyList())
    var provider: (() -> List<WorkflowRunInfo>)? = null
}

/** 一次 workflow 运行：名称/状态 + 按 phase 分组的成员。 */
@Composable
fun WorkflowRunCard(run: WorkflowRunInfo, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.workflow_run_title),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = run.name.ifBlank { run.runId },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusLabel(run.status),
                fontSize = 11.sp,
                color = when (run.status) {
                    "completed" -> MaterialTheme.colorScheme.primary
                    "failed", "cancelled", "interrupted" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        run.phases.forEach { phase ->
            val phaseName = phase.phase
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (phaseName != null) {
                    Text(
                        text = phaseName.ifBlank { stringResource(R.string.workflow_phase_default) },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                phase.members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (phaseName != null) 8.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = statusGlyph(member.status),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when (member.status) {
                                "completed" -> MaterialTheme.colorScheme.primary
                                "failed", "cancelled", "interrupted" ->
                                    MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = member.label.ifBlank { member.childId },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = statusLabel(member.status),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    "running" -> stringResource(R.string.workflow_status_running)
    "completed" -> stringResource(R.string.workflow_status_completed)
    "failed" -> stringResource(R.string.workflow_status_failed)
    "cancelled" -> stringResource(R.string.workflow_status_cancelled)
    "interrupted" -> stringResource(R.string.workflow_status_interrupted)
    else -> status
}

private fun statusGlyph(status: String): String = when (status) {
    "completed" -> "✓"
    "failed" -> "✕"
    "cancelled" -> "⊘"
    "interrupted" -> "!"
    else -> "•"
}
