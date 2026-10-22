package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deepseek.dsh.core.protocol.SessionRawFrame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 会话原始帧的提供者（宿主注入）。
 *
 * 与消息反馈一样用桥接：轨迹面板需要的帧来自 RemoteClient 的环形缓存，宿主只在
 * 已有的会话 effect 里赋值，不再扩大 `MainAppHost`。
 */
object SessionFramesBridge {
    var provider: (() -> List<SessionRawFrame>)? = null
}

/** 一条轨迹记录（对齐官方 ui-trajectory 的一行）。 */
data class TrajectoryRow(
    val seq: Long,
    val kind: String,
    val title: String,
    val detail: String,
    val turn: Int,
    val step: Int,
    val raw: String,
)

private val trajectoryJson = Json { ignoreUnknownKeys = true }

/**
 * 从原始帧里解析出轨迹行：只保留真正有语义的会话事件。
 *
 * follow 流有两种 item 帧：`{type:'item', value:{type:'snapshot', records:[…]}}`
 * 与 `{type:'item', value:{type:'event', event:{…}}}`，两者都要展开。
 */
fun buildTrajectory(frames: List<SessionRawFrame>): List<TrajectoryRow> {
    val events = frames.flatMap { frame ->
        val root = runCatching {
            trajectoryJson.parseToJsonElement(frame.text) as? JsonObject
        }.getOrNull() ?: return@flatMap emptyList()
        if (root["type"].textOrNull() != "item") return@flatMap emptyList()
        val value = root["value"] as? JsonObject ?: return@flatMap emptyList()
        when (value["type"].textOrNull()) {
            "snapshot" -> (value["records"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { record ->
                    ((record as? JsonObject)?.get("event") as? JsonObject)
                }.orEmpty()
            "event" -> listOfNotNull(value["event"] as? JsonObject)
            else -> emptyList()
        }
    }
    return events.mapNotNull { event -> runCatching { trajectoryRow(event) }.getOrNull() }
        .sortedBy { it.seq }
}

/** 取消息文本摘要（user/assistant 事件的 data.message.content）。 */
private fun textOfMessage(data: JsonObject?): String {
    val message = data?.get("message") as? JsonObject ?: return ""
    val content = message["content"] ?: return ""
    return content.toString().replace("\\n", " ").take(120)
}

/** 单个事件的轨迹行；形状异常时返回 null 而不是让面板崩溃。 */
private fun trajectoryRow(event: JsonObject): TrajectoryRow? {
    val type = event["type"].textOrNull() ?: return null
    val seq = event["seq"].textOrNull()?.toLongOrNull() ?: return null
    val data = event["data"] as? JsonObject
    val turn = data?.get("turn").textOrNull()?.toIntOrNull() ?: 0
    val step = data?.get("step").textOrNull()?.toIntOrNull() ?: 0
    val detail = when (type) {
        "tool/call" -> data?.get("name").textOrNull().orEmpty()
        "assistant/message" -> textOfMessage(data).take(80)
        "user/message" -> textOfMessage(data).take(80)
        "request/header" -> data?.get("model").textOrNull().orEmpty()
        "turn/end" -> data?.get("reason").textOrNull().orEmpty()
        else -> ""
    }
    val title = when (type) {
        "assistant/message" -> "assistant"
        "user/message" -> "user"
        "request/header" -> "request"
        "session/title" -> "title"
        else -> type
    }
    return TrajectoryRow(
        seq = seq,
        kind = type,
        title = title,
        detail = detail,
        turn = turn,
        step = step,
        raw = event.toString(),
    )
}

/**
 * 轨迹面板（官方 ui-trajectory 的移动端形态）：按 seq 列出会话事件，
 * 每行显示序号、事件类型、所在 turn/step 与摘要，点击展开原始 JSON。
 */
@Composable
fun TrajectoryDialog(onDismiss: () -> Unit) {
    val frames = remember { SessionFramesBridge.provider?.invoke().orEmpty() }
    val rows = remember(frames) { buildTrajectory(frames) }
    var expandedSeq by remember { mutableStateOf<Long?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.trajectory_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.trajectory_subtitle, rows.size),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.trajectory_close))
                    }
                }
                if (rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.trajectory_empty),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(rows, key = { it.seq }) { row ->
                            TrajectoryRowView(
                                row = row,
                                expanded = expandedSeq == row.seq,
                                onClick = {
                                    expandedSeq = if (expandedSeq == row.seq) null else row.seq
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrajectoryRowView(row: TrajectoryRow, expanded: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.seq.toString(),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = row.title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (row.turn > 0) {
                Text(
                    text = "  T${row.turn}" + if (row.step > 0) "/S${row.step}" else "",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (row.detail.isNotBlank() && !expanded) {
            Text(
                text = row.detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (expanded) {
            Text(
                text = row.raw,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 安全取字符串：字段可能是对象/数组（如 turn/end 的 reason），不能直接 jsonPrimitive。 */
private fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
