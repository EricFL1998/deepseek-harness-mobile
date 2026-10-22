package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.deepseek.dsh.core.protocol.PresentedFileInfo

/** 官方一个 turn 尾部最多铺 4 张文件卡，其余折叠。 */
private const val COLLAPSED_PRESENTED_COUNT = 4

/**
 * 产物卡片行：对齐官方 `ui-deliverables` 的 `PresentedFileCard`。
 *
 * 单文件整行，多个两列；卡片 60dp 高、40dp 图标框、文件名 13sp、
 * 描述 10sp（Host 的 description，缺省回落成扩展名）。超过 4 个折叠。
 */
@Composable
fun PresentedFilesRow(
    files: List<PresentedFileInfo>,
    onOpen: (PresentedFileInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) return
    var expanded by remember(files.size) { mutableStateOf(false) }
    val collapsible = files.size > COLLAPSED_PRESENTED_COUNT
    val visible = if (collapsible && !expanded) {
        files.take(COLLAPSED_PRESENTED_COUNT)
    } else {
        files
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (files.size == 1) {
            PresentedFileCard(file = files.first(), onOpen = onOpen)
        } else {
            visible.chunked(2).forEach { rowFiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowFiles.forEach { file ->
                        PresentedFileCard(
                            file = file,
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 奇数个时右侧留空，避免最后一张被拉成整行。
                    if (rowFiles.size == 1) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (collapsible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.presented_collapse)
                    } else {
                        stringResource(R.string.presented_all, files.size)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun PresentedFileCard(
    file: PresentedFileInfo,
    onOpen: (PresentedFileInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = file.path.substringAfterLast('/')
    val extension = name.substringAfterLast('.', "")
        .takeIf { it.isNotEmpty() && it.length <= 5 }
        ?.uppercase()
        ?: stringResource(R.string.presented_file)
    val description = file.description
        ?.replace(Regex("\\s*(?:\\([^()]*\\)|（[^（）]*）)\\s*$"), "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: extension
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onOpen(file) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = extension.take(4),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
