package com.deepseek.dsh

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 终端页（官方 `terminal-controller`）：屏幕缓冲 + 行输入 + 常用按键。
 *
 * Host 的终端帧已经渲染过屏幕（snapshot.screen / output.data），这里只做
 * 去 ANSI 后纯文本展示；输入按行发送（自动补 \r）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(
    buffer: String,
    status: String,
    writable: Boolean,
    /** 缓冲为空且未连接时显示的提示；缺省用 terminal_empty。 */
    emptyHint: String = "",
    onInput: (String) -> Unit,
    onQuickKey: (String) -> Unit,
    onClose: () -> Unit,
    terminals: List<com.deepseek.dsh.core.model.TerminalInfo> = emptyList(),
    activeTerminalId: String = "main",
    shellChoices: List<Pair<String, String>> = emptyList(),
    onSelectTerminal: (String) -> Unit = {},
    onCreateTerminal: (String?) -> Unit = {},
    onRenameTerminal: (String, String) -> Unit = { _, _ -> },
) {
    var line by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    // 有新输出时贴到底部。
    LaunchedEffect(buffer.length) {
        scroll.scrollTo(scroll.maxValue)
    }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.terminal_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = status,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.terminal_close),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 终端页签（官方 terminal/list）：每个网页终端一个页签，右侧 + 新建。
        var shellMenuOpen by remember { mutableStateOf(false) }
        var renameTarget by remember { mutableStateOf<com.deepseek.dsh.core.model.TerminalInfo?>(null) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                terminals.forEach { terminal ->
                    val active = terminal.id == activeTerminalId
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.combinedClickable(
                            onClick = { onSelectTerminal(terminal.id) },
                            onLongClick = { renameTarget = terminal },
                        ),
                    ) {
                        Text(
                            text = terminal.title.ifBlank { terminal.id },
                            fontSize = 11.sp,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Box {
                Text(
                    text = "+",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { shellMenuOpen = true }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                DropdownMenu(expanded = shellMenuOpen, onDismissRequest = { shellMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.terminal_new)) },
                        onClick = { shellMenuOpen = false; onCreateTerminal(null) },
                    )
                    shellChoices.forEach { (path, name) ->
                        DropdownMenuItem(
                            text = { Text(if (name.isBlank()) path else name) },
                            onClick = { shellMenuOpen = false; onCreateTerminal(path) },
                        )
                    }
                }
            }
        }
        // 长按页签改名（官方 terminal/rename）。
        if (renameTarget != null) {
            var draft by remember(renameTarget!!.id) { mutableStateOf(renameTarget!!.title) }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(stringResource(R.string.terminal_rename)) },
                text = {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true)
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = renameTarget ?: return@TextButton
                        val value = draft.trim()
                        renameTarget = null
                        if (value.isNotEmpty()) onRenameTerminal(target.id, value)
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
                },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Text(
                text = buffer.ifBlank { emptyHint.ifBlank { stringResource(R.string.terminal_empty) } },
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(8.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // 常用按键：Ctrl-C / Tab / ↑ / ↓ / Enter。
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Ctrl-C" to "\u0003",
                "Tab" to "\t",
                "\u2191" to "\u001b[A",
                "\u2193" to "\u001b[B",
            ).forEach { (label, key) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable(enabled = writable) { onQuickKey(key) },
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.clickable(enabled = writable) {
                    onQuickKey("\r")
                },
            ) {
                Text(
                    text = stringResource(R.string.terminal_enter),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = line,
                onValueChange = { line = it },
                enabled = writable,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                placeholder = {
                    Text(
                        text = stringResource(R.string.terminal_input_hint),
                        fontSize = 12.sp,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                enabled = writable,
                onClick = {
                    onInput(line)
                    line = ""
                },
            ) {
                Text(stringResource(R.string.terminal_send))
            }
        }
    }
}
