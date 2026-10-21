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
 * 「开发者工具」插件的原生界面：会话数据诊断（只读）。
 *
 * 官方 `experimental-session-inspector` 在会话右侧栏提供原始日志/聊天分组两
 * 种视图；手机上没有右侧栏，这里做成会话头部的调试入口 + 全屏只读面板，
 * 展示 App 实际收到的原始会话帧：序号、类型、时间 (UTC)、数据，支持按类型
 * 关键词过滤（与官方「类型筛选」相同的包含匹配语义）。
 */
internal object SessionInspectorPluginUi {
    /** 启用后才会出现调试入口的 bundle（开发者工具 = inspector + session-inspector）。 */
    const val BUNDLE = "@deepseek-ai/dsh-experimental-inspector-profile"

    /**
     * 会话数据诊断面板。
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
        var filter by remember { mutableStateOf("") }
        var expandedIndex by remember { mutableStateOf<Int?>(null) }
        val keyword = filter.trim().trim('*').lowercase(Locale.US)
        val matched = remember(frames, keyword) {
            if (keyword.isEmpty()) frames else frames.filter { it.type.lowercase(Locale.US).contains(keyword) }
        }
        val timeFormat = remember {
            SimpleDateFormat("HH:mm:ss.SSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        }
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
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("会话数据诊断", color = colors.labelPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "在会话侧边栏分析当前会话原始日志和聊天分组数据",
                            color = colors.labelTertiary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    InspectorChip(text = "关闭", onClick = onDismiss)
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
                            Text("输入类型片段，如 delta", color = colors.labelTertiary, fontSize = 12.sp)
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
                        text = if (keyword.isEmpty()) {
                            "共 ${frames.size} 条"
                        } else {
                            "${matched.size}/${frames.size}"
                        },
                        color = colors.labelTertiary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                when {
                    sessionId.isNullOrBlank() -> Text(
                        text = "请先打开一个会话。",
                        color = colors.labelTertiary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    frames.isEmpty() -> Text(
                        text = "暂无记录",
                        color = colors.labelTertiary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    matched.isEmpty() -> Text(
                        text = "当前表格没有匹配记录，请修改类型关键词。",
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
                            Text("序号", color = colors.labelTertiary, fontSize = 10.5.sp, modifier = Modifier.width(44.dp))
                            Text("类型", color = colors.labelTertiary, fontSize = 10.5.sp, modifier = Modifier.width(96.dp))
                            Text("时间 (UTC)", color = colors.labelTertiary, fontSize = 10.5.sp, modifier = Modifier.width(88.dp))
                            Text("数据", color = colors.labelTertiary, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
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
        }
    }
}

/** 小胶囊按钮（与插件页同一套视觉）。 */
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
