package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.protocol.SessionRawFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 「开发者工具」插件的原生界面：调试面板。
 *
 * 官方有两块：`experimental-inspector` 在 `shell.bottom` 嵌 Chrome DevTools，
 * `experimental-session-inspector` 在会话右侧栏展示原始日志/聊天分组。手机
 * 没有右侧栏与快捷键，所以合并成会话头部的调试入口 + 全屏面板，里面两个页签：
 * 「原始数据」= 当前会话收到的原始帧，`NodeJS 诊断` = 同一个 DevTools 前端。
 */
internal object SessionInspectorPluginUi {
    /** 启用这个 profile bundle 后才会出现调试入口（含 inspector + session-inspector）。 */
    const val BUNDLE = "@deepseek-ai/dsh-experimental-inspector-profile"

    /**
     * 调试面板。
     *
     * @param frames 当前会话的原始帧（最新在后）。
     * @param sessionId 会话 id；为空时提示先打开会话。
     * @param onDismiss 关闭面板。
     */
    @Composable
    fun Overlay(
        frames: List<SessionRawFrame>,
        sessionId: String?,
        onDismiss: () -> Unit,
    ) {
        val colors = LocalDshPanelColors.current
        var tab by remember { mutableStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgBase.copy(alpha = 0.99f))
                .padding(horizontal = PluginsDimens.PageHorizontal),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (tab == 0) stringResource(R.string.inspector_session_diagnostics) else stringResource(R.string.inspector_node_diagnostics),
                            color = colors.labelPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (tab == 0) {
                                stringResource(R.string.inspector_session_desc)
                            } else {
                                stringResource(R.string.inspector_devtools_hint)
                            },
                            color = colors.labelTertiary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    InspectorChip(text = stringResource(R.string.inspector_close), onClick = onDismiss)
                }
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InspectorTab(text = stringResource(R.string.inspector_raw_data), selected = tab == 0) { tab = 0 }
                    InspectorTab(text = stringResource(R.string.inspector_node_diagnostics), selected = tab == 1) { tab = 1 }
                }
                if (tab == 1) {
                    NodeJsDevToolsPane()
                } else {
                    RawDataContent(frames = frames, sessionId = sessionId)
                }
            }
        }
    }
}

/** 原始数据页签：序号 / 类型 / UTC 时间 / 数据，带类型关键词过滤。 */
@Composable
private fun RawDataContent(frames: List<SessionRawFrame>, sessionId: String?) {
    val colors = LocalDshPanelColors.current
    var filter by remember { mutableStateOf("") }
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    val keyword = filter.trim().trim('*').lowercase(Locale.US)
    val matched = remember(frames, keyword) {
        if (keyword.isEmpty()) frames else frames.filter { it.type.lowercase(Locale.US).contains(keyword) }
    }
    val timeFormat = remember {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bgModulePlatform)
                .border(0.5.dp, colors.borderL2, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            if (filter.isEmpty()) {
                Text(stringResource(R.string.inspector_filter_hint), color = colors.labelTertiary, fontSize = 12.sp)
            }
            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.labelPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(colors.labelPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = if (keyword.isEmpty()) stringResource(R.string.inspector_total_count, frames.size) else "${matched.size}/${frames.size}",
            color = colors.labelTertiary,
            fontSize = 11.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    when {
        sessionId.isNullOrBlank() -> Text(
            text = stringResource(R.string.inspector_no_session),
            color = colors.labelTertiary,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        frames.isEmpty() -> Text(
            text = stringResource(R.string.inspector_no_records),
            color = colors.labelTertiary,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        matched.isEmpty() -> Text(
            text = stringResource(R.string.inspector_no_match_filter),
            color = colors.labelTertiary,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.inspector_seq), color = colors.labelTertiary, fontSize = 10.5.sp, modifier = Modifier.width(44.dp))
                Text(stringResource(R.string.inspector_type), color = colors.labelTertiary, fontSize = 10.5.sp, modifier = Modifier.width(96.dp))
                Text(stringResource(R.string.inspector_time_utc), color = colors.labelTertiary, fontSize = 10.5.sp, modifier = Modifier.width(88.dp))
                Text(stringResource(R.string.inspector_data), color = colors.labelTertiary, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(matched, key = { _, frame -> frame.index }) { _, frame ->
                    val open = expandedIndex == frame.index
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expandedIndex = if (open) null else frame.index }
                            .padding(vertical = 5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = frame.index.toString(),
                                color = colors.labelTertiary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                modifier = Modifier.width(44.dp),
                            )
                            Text(
                                text = frame.type,
                                color = colors.labelSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(96.dp),
                            )
                            Text(
                                text = timeFormat.format(Date(frame.timestamp)),
                                color = colors.labelTertiary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.width(88.dp),
                            )
                            Text(
                                text = frame.text,
                                color = colors.labelTertiary,
                                fontSize = 10.5.sp,
                                maxLines = if (open) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 小胶囊（关闭等动作）。 */
@Composable
private fun InspectorChip(text: String, onClick: () -> Unit) {
    val colors = LocalDshPanelColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.bgModulePlatform)
            .border(0.5.dp, colors.borderL2, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = colors.labelSecondary, fontSize = 12.sp)
    }
}

/** 调试面板的页签。 */
@Composable
private fun InspectorTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalDshPanelColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.buttonPrimaryFill else colors.bgModulePlatform)
            .border(
                0.5.dp,
                if (selected) colors.buttonPrimaryFill else colors.borderL2,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) colors.labelPrimaryForeground else colors.labelSecondary,
            fontSize = 12.sp,
        )
    }
}

/**
 * 全屏调试面板的宿主入口：把 Dialog 包装留在本文件，`MainAppHost` 只保留一行调用。
 *
 * @param frames 当前会话原始帧。
 * @param sessionId 当前会话 id。
 * @param onDismiss 关闭面板。
 */
@Composable
fun SessionInspectorOverlayDialog(
    frames: List<SessionRawFrame>,
    sessionId: String?,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SessionInspectorPluginUi.Overlay(
            frames = frames,
            sessionId = sessionId,
            onDismiss = onDismiss,
        )
    }
}
