package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshTextInput
import com.deepseek.dsh.core.model.QueuedMessageInfo

/**
 * 排队消息条（对齐官方 `QueueDock`）：输入框上方显示本轮未开始执行的队列，
 * 每条可编辑 / 移除 / 转成 steering；多条时默认折叠成计数头。
 */
@Composable
fun QueuedMessagesDock(
    messages: List<QueuedMessageInfo>,
    running: Boolean,
    onEdit: (QueuedMessageInfo, String) -> Unit,
    onRemove: (QueuedMessageInfo) -> Unit,
    onSteer: (QueuedMessageInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return
    var expanded by remember(messages.size) { mutableStateOf(messages.size == 1) }
    var editing by remember { mutableStateOf<QueuedMessageInfo?>(null) }
    val shape = RoundedCornerShape(12.dp)
    val fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(fill, shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        if (messages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.queue_title, messages.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) {
                        stringResource(R.string.changes_collapse)
                    } else {
                        stringResource(R.string.changes_all, messages.size)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (expanded) {
            messages.forEach { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = message.text,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (running) {
                            QueueActionText(
                                text = stringResource(R.string.queue_steer),
                                onClick = { onSteer(message) },
                            )
                        }
                        QueueActionText(
                            text = stringResource(R.string.queue_edit),
                            onClick = { editing = message },
                        )
                        QueueActionText(
                            text = stringResource(R.string.queue_remove),
                            onClick = { onRemove(message) },
                        )
                    }
                }
            }
        }
    }
    editing?.let { message ->
        var draft by remember(message.id) { mutableStateOf(message.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.queue_edit_title)) },
            text = {
                DshTextInput(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = stringResource(R.string.queue_edit_placeholder),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = draft.trim()
                        editing = null
                        if (value.isNotEmpty()) onEdit(message, value)
                    },
                ) {
                    Text(stringResource(R.string.queue_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.queue_cancel))
                }
            },
        )
    }
}

@Composable
private fun QueueActionText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 4.dp, bottom = 2.dp),
    )
}
