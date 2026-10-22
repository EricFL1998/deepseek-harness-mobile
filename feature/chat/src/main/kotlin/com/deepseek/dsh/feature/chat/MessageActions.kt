package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.MessageRole

/**
 * 消息操作菜单（官方消息长按菜单的移动端形态）：复制、从这条消息分叉。
 *
 * @param message 被长按的消息。
 * @param onCopy 复制文本。
 * @param onFork 以这条消息的 seq 分叉新会话。
 * @param onDismiss 关闭菜单。
 */
@Composable
fun MessageActionDialog(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onFork: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_actions_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MessageActionRow(stringResource(R.string.message_action_copy)) {
                    onCopy(message.content)
                    onDismiss()
                }
                if (message.seq > 0L &&
                    (message.role == MessageRole.USER || message.role == MessageRole.ASSISTANT)
                ) {
                    MessageActionRow(stringResource(R.string.message_action_fork)) {
                        onFork(message.seq.toInt())
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.trajectory_close))
            }
        },
    )
}

@Composable
private fun MessageActionRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}
