package com.deepseek.dsh.plugin.mobileui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * v4.0 设计文档 §7.8.1: SDUI 原生 Compose 渲染引擎
 */
@Composable
fun SduiPageRenderer(
    page: SduiPage,
    onAction: (SduiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val inputStates = remember { mutableStateMapOf<String, String>() }
    val switchStates = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium
            )
            page.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(page.children) { node ->
            SduiNodeRenderer(
                node = node,
                inputStates = inputStates,
                switchStates = switchStates,
                onAction = onAction
            )
        }
    }
}

@Composable
fun SduiNodeRenderer(
    node: SduiNode,
    inputStates: MutableMap<String, String>,
    switchStates: MutableMap<String, Boolean>,
    onAction: (SduiAction) -> Unit
) {
    when (node) {
        is SduiSection -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    node.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    node.children.forEach { child ->
                        SduiNodeRenderer(
                            node = child,
                            inputStates = inputStates,
                            switchStates = switchStates,
                            onAction = onAction
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        is SduiRow -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                node.children.forEach { child ->
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        SduiNodeRenderer(
                            node = child,
                            inputStates = inputStates,
                            switchStates = switchStates,
                            onAction = onAction
                        )
                    }
                }
            }
        }

        is SduiColumn -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                node.children.forEach { child ->
                    SduiNodeRenderer(
                        node = child,
                        inputStates = inputStates,
                        switchStates = switchStates,
                        onAction = onAction
                    )
                }
            }
        }

        is SduiText -> {
            val style = when (node.style) {
                "title" -> MaterialTheme.typography.titleLarge
                "subtitle" -> MaterialTheme.typography.titleMedium
                "caption" -> MaterialTheme.typography.bodySmall
                "code" -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else -> MaterialTheme.typography.bodyMedium
            }
            Text(text = node.text, style = style)
        }

        is SduiButton -> {
            if (node.primary) {
                Button(onClick = { onAction(node.action) }) {
                    Text(node.label)
                }
            } else {
                OutlinedButton(onClick = { onAction(node.action) }) {
                    Text(node.label)
                }
            }
        }

        is SduiInput -> {
            val value = inputStates.getOrPut(node.key) { node.initialValue ?: "" }
            OutlinedTextField(
                value = value,
                onValueChange = { inputStates[node.key] = it },
                placeholder = { node.placeholder?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        }

        is SduiSwitch -> {
            val checked = switchStates.getOrPut(node.key) { node.initialValue }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = node.label, modifier = Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = { switchStates[node.key] = it }
                )
            }
        }

        is SduiBadge -> {
            val bg = when (node.color) {
                "success" -> Color(0xFF2E7D32)
                "warning" -> Color(0xFFED6C02)
                "error" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = node.text,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
