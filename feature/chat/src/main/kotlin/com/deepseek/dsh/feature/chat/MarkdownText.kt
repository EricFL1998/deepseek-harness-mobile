package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.deepseek.dsh.core.designsystem.LocalConversationFontSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 极简 Markdown 渲染（官方 markdown 渲染器的移动端子集）。
 *
 * 支持标题（#~######）、无序/有序列表、代码块、表格、引用，以及行内的粗体、
 * 斜体、行内代码与链接文本。不引入第三方依赖，也不做 HTML 与嵌套列表——官方
 * 客户端里对话正文最常见的这几类块已经能正确显示，而不是把 `##`、`|` 当普通字符。
 */
/** 会话正文样式：字号来自 Host 的 ui-theme.fontSize。 */
@Composable
private fun bodyTextStyle(): TextStyle {
    val size = LocalConversationFontSize.current
    return TextStyle(fontSize = size.sp, lineHeight = (size * 1.45f).sp)
}

@Composable
fun MarkdownMessageText(text: String, color: Color = MaterialTheme.colorScheme.onBackground) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val localContext = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = inlineMarkdown(block.text, color),
                    fontSize = when (block.level) {
                        1 -> 22.sp
                        2 -> 20.sp
                        3 -> 18.sp
                        else -> 16.sp
                    },
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    modifier = Modifier.padding(top = 6.dp),
                )

                is MarkdownBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text, color),
                    style = bodyTextStyle(),
                    color = color,
                )

                is MarkdownBlock.ListItem -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (16 * block.depth).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    // 顶部对齐：标记列按首行行高居中，正文换行时标记不再跟着漂移。
                    verticalAlignment = Alignment.Top,
                ) {
                    // 标记列与正文首行同高：圆点、序号与复选框都在这条 20dp 的
                    // 中线居中，所以三者的视觉基线一致。
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            // bodyMedium 的首行行高（20sp ≈ 20dp）：标记在首行垂直居中，
                            // 与正文基线一致。
                            .height(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (block.checked != null) {
                            ChecklistBox(checked = block.checked)
                        } else {
                            Text(
                                text = block.marker,
                                style = bodyTextStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = inlineMarkdown(block.text, color),
                        style = bodyTextStyle(),
                        color = if (block.checked == true) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            color
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                is MarkdownBlock.Code -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "code",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f),
                        )
                        // 官方代码块右上角的复制按钮。
                        Text(
                            text = stringResource(R.string.markdown_copy),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    val clipboard = localContext.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE,
                                    ) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("code", block.code),
                                    )
                                    android.widget.Toast.makeText(
                                        localContext,
                                        localContext.getString(R.string.markdown_copied),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = block.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                    )
                }

                is MarkdownBlock.Table -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    block.rows.forEachIndexed { index, cells ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            cells.forEach { cell ->
                                Text(
                                    text = inlineMarkdown(cell, color),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = if (index == 0) FontWeight.SemiBold else null,
                                    color = if (index == 0) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        color
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .width(3.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .padding(vertical = 2.dp),
                    ) {}
                    Text(
                        text = inlineMarkdown(block.text, color),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Markdown 的块级元素子集。 */
private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListItem(
        val marker: String,
        val text: String,
        val depth: Int = 0,
        /** 任务列表的勾选状态；null 表示普通列表项。 */
        val checked: Boolean? = null,
    ) : MarkdownBlock
    data class Code(val code: String) : MarkdownBlock
    data class Table(val rows: List<List<String>>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
}

/** Markdown 任务项的复选框：勾选后实心强调色，否则描边空框。 */
@Composable
private fun ChecklistBox(checked: Boolean) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(shape)
            .background(
                if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape,
            )
            .border(
                1.dp,
                if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                text = "✓",
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
    }
}

private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
private val listRegex = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
private val taskRegex = Regex("^\\[([ xX])]\\s+(.*)$")
private val tableSeparatorRegex = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")
private val fenceRegex = Regex("^\\s*```(.*)$")

/** 把 Markdown 文本切成块；未知块一律按段落处理，保证原文不丢。 */
private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.replace("\r\n", "\n").split("\n")
    var index = 0
    val paragraph = StringBuilder()
    fun flushParagraph() {
        val value = paragraph.toString().trim()
        if (value.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(value)
        }
        paragraph.setLength(0)
    }
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        val fence = fenceRegex.find(line)
        when {
            fence != null -> {
                flushParagraph()
                val code = StringBuilder()
                index += 1
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code.appendLine(lines[index])
                    index += 1
                }
                blocks += MarkdownBlock.Code(code.toString().trimEnd())
            }
            headingRegex.matches(trimmed) -> {
                flushParagraph()
                val match = headingRegex.find(trimmed)!!
                blocks += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    text = match.groupValues[2].trim(),
                )
            }
            trimmed.startsWith(">") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(trimmed.removePrefix(">").trim())
            }
            trimmed.startsWith("|") && index + 1 < lines.size &&
                tableSeparatorRegex.matches(lines[index + 1].trim()) -> {
                flushParagraph()
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].trim().startsWith("|")) {
                    val cells = lines[index].trim().trim('|').split("|").map { it.trim() }
                    if (!tableSeparatorRegex.matches(lines[index].trim())) {
                        rows += cells
                    }
                    index += 1
                }
                blocks += MarkdownBlock.Table(rows)
                continue
            }
            listRegex.matches(line) -> {
                flushParagraph()
                val match = listRegex.find(line)!!
                val indent = match.groupValues[1].length
                val depth = (indent / 2).coerceIn(0, 3)
                val rawMarker = match.groupValues[2]
                var itemText = match.groupValues[3].trim()
                // 任务列表：- [ ] / - [x]
                val task = taskRegex.find(itemText)
                val checked = task?.let {
                    it.groupValues[1].equals("x", ignoreCase = true)
                }
                val marker = if (rawMarker.first().isDigit()) rawMarker else "•"
                if (task != null) itemText = task.groupValues[2].trim()
                blocks += MarkdownBlock.ListItem(
                    marker = marker,
                    text = itemText,
                    depth = depth,
                    checked = checked,
                )
            }
            trimmed.isEmpty() -> flushParagraph()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        index += 1
    }
    flushParagraph()
    return blocks
}

private val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
private val codeRegex = Regex("`([^`]+)`")
private val linkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val italicRegex = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")

/** 行内样式：粗体、行内代码、链接文本、斜体。 */
@Composable
private fun inlineMarkdown(source: String, color: Color): AnnotatedString = buildAnnotatedString {
    var rest = source
    while (rest.isNotEmpty()) {
        val candidates = listOfNotNull(
            boldRegex.find(rest)?.let { Triple(0, it.range, it.groupValues[1]) },
            codeRegex.find(rest)?.let { Triple(1, it.range, it.groupValues[1]) },
            linkRegex.find(rest)?.let { Triple(2, it.range, it.groupValues[1]) },
            italicRegex.find(rest)?.let { Triple(3, it.range, it.groupValues[1]) },
        ).minByOrNull { it.second.first }
        if (candidates == null) {
            append(rest)
            break
        }
        if (candidates.second.first > 0) {
            append(rest.substring(0, candidates.second.first))
        }
        when (candidates.first) {
            0 -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(candidates.third) }
            1 -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = color.copy(alpha = 0.08f),
                ),
            ) { append(candidates.third) }
            2 -> withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                append(candidates.third)
            }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(candidates.third) }
        }
        rest = rest.substring(candidates.second.last + 1)
    }
}
