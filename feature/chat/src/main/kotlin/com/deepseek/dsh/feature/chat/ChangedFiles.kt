package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.deepseek.dsh.core.protocol.ChangedFileInfo
import com.deepseek.dsh.core.protocol.ChangesDiffInfo
import com.deepseek.dsh.core.protocol.ChangesSummaryInfo

/** 官方 ChangedFiles 折叠前显示的行数。 */
private const val CHANGED_COLLAPSED_ROWS = 4

/**
 * 一轮改动的文件卡（对齐官方 `ui-deliverables` 的 ChangedFiles）：
 * 单文件只显示头部「Edited name + +n/−n」，多文件头部 + 行列表，超过 4 行折叠。
 * 点击任意行回调文件在 summary 里的原始下标。
 */
@Composable
fun ChangedFilesCard(
    summary: ChangesSummaryInfo,
    onOpenDiff: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (summary.files.isEmpty()) return
    var expanded by remember(summary.seq) { mutableStateOf(false) }
    val single = summary.files.size == 1
    val foldable = summary.files.size > CHANGED_COLLAPSED_ROWS
    val rows = if (foldable && !expanded) {
        summary.files.take(CHANGED_COLLAPSED_ROWS)
    } else {
        summary.files
    }
    val shape = RoundedCornerShape(12.dp)
    val fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val border = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fill, shape)
            .border(0.5.dp, border, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDiff(0) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CodeTile()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (single) {
                        stringResource(
                            R.string.changes_single_title,
                            summary.files.first().display.substringAfterLast('/'),
                        )
                    } else {
                        stringResource(R.string.changes_title, summary.files.size)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ChangeCounts(
                    file = summary.files.firstOrNull(),
                    added = summary.added,
                    deleted = summary.deleted,
                )
            }
        }
        if (!single) {
            rows.forEachIndexed { visibleIndex, file ->
                val originalIndex = summary.files.indexOf(file)
                    .let { if (it >= 0) it else visibleIndex }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDiff(originalIndex) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = file.display,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    ChangeCounts(file = file, added = file.added, deleted = file.deleted)
                }
            }
            if (foldable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.changes_collapse)
                        } else {
                            stringResource(R.string.changes_all, summary.files.size)
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeTile() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(10.dp),
            )
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "</>",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ChangeCounts(file: ChangedFileInfo?, added: Int, deleted: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            file?.binary == true -> Text(
                text = stringResource(R.string.changes_binary),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            file?.oversized == true -> Text(
                text = stringResource(R.string.changes_oversized),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            else -> {
                Text(
                    text = stringResource(R.string.changes_added, added),
                    fontSize = 11.sp,
                    color = DiffAdded,
                )
                Text(
                    text = stringResource(R.string.changes_deleted, deleted),
                    fontSize = 11.sp,
                    color = DiffDeleted,
                )
            }
        }
    }
}

/** 官方 diff 的新增/删除色。 */
private val DiffAdded = androidx.compose.ui.graphics.Color(0xFF2F8A4B)
private val DiffDeleted = androidx.compose.ui.graphics.Color(0xFFC2453D)

/**
 * 单文件 diff 面板：hunk 头 + 逐行 +/- 着色，等宽字体、可横向滚动。
 * @param diff 已拉到的对比；null 表示加载中或失败。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeDiffSheet(
    title: String,
    diff: ChangesDiffInfo?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            when {
                diff == null -> Text(
                    text = stringResource(R.string.changes_diff_failed),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
                diff.kind == "binary" -> Text(
                    text = stringResource(R.string.changes_binary),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
                diff.kind == "oversized" -> Text(
                    text = stringResource(R.string.changes_oversized),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
                else -> {
                    val scroll = rememberScrollState()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .horizontalScroll(scroll),
                    ) {
                        diff.hunks.forEachIndexed { hunkIndex, hunk ->
                            item(key = "hunk_$hunkIndex") {
                                Text(
                                    text = "@@ -" + hunk.oldStart + "," + hunk.oldLines +
                                        " +" + hunk.newStart + "," + hunk.newLines + " @@",
                                    fontSize = 11.sp,
                                    lineHeight = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 2.dp,
                                    ),
                                )
                            }
                            items(
                                count = hunk.lines.size,
                                key = { lineIndex -> "line_${hunkIndex}_$lineIndex" },
                            ) { lineIndex ->
                                DiffLine(line = hunk.lines[lineIndex])
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLine(line: String) {
    val sign = line.firstOrNull() ?: ' '
    val background = when (sign) {
        '+' -> DiffAdded.copy(alpha = 0.12f)
        '-' -> DiffDeleted.copy(alpha = 0.12f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background),
    ) {
        Text(
            text = line,
            fontSize = 11.sp,
            lineHeight = 20.sp,
            fontFamily = FontFamily.Monospace,
            color = when (sign) {
                '+' -> DiffAdded
                '-' -> DiffDeleted
                else -> MaterialTheme.colorScheme.onSurface
            },
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
