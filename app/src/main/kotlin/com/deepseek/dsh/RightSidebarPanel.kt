package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshDialogButton
import com.deepseek.dsh.core.designsystem.dshPanelColors
import com.deepseek.dsh.core.model.TodoEntryInfo
import com.deepseek.dsh.core.protocol.PresentedFileInfo
import com.deepseek.dsh.feature.files.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 右侧栏的标签集合（对应官方 sidebar.right.pane.tab 里手机端可实现的部分）。 */
private val RIGHT_TABS = listOf("files", "document", "plan", "deliverables", "browser")

/** 单次预览读取上限，避免大文件把 UI 拖死。 */
private const val PREVIEW_LIMIT_BYTES = 256 * 1024

/**
 * 聊天页右侧栏内容：文件 / 文档预览 / 计划 / 交付物 / 浏览器。
 *
 * 文件与文档都直接读手机工作区（Host 绑定到 /workspace 的同一份目录），
 * 不额外走 RPC；计划与交付物来自官方会话投影。浏览器面板在桌面端是嵌入页，
 * 手机端不提供浏览器内核，这里给出明确说明而不是空白。
 */
@Composable
fun RightSidebarPanel(
    tab: String,
    onTabChange: (String) -> Unit,
    previewPath: String?,
    onPreviewPathChange: (String?) -> Unit,
    files: List<FileItem>,
    currentPath: String,
    onOpenDirectory: (FileItem) -> Unit,
    onNavigateUp: (() -> Unit)?,
    onImport: () -> Unit,
    onAttachFile: (FileItem) -> Unit,
    todos: List<TodoEntryInfo>,
    deliverables: List<PresentedFileInfo>,
    onOpenDeliverable: (PresentedFileInfo) -> Unit,
) {
    val palette = dshPanelColors()
    Column(modifier = Modifier.fillMaxSize()) {
        // 标签行：可横向滚动，选中项用胶囊底。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RIGHT_TABS.forEach { id ->
                val selected = tab == id
                val label = stringResource(
                    when (id) {
                        "files" -> R.string.right_sidebar_tab_files
                        "document" -> R.string.right_sidebar_tab_document
                        "plan" -> R.string.right_sidebar_tab_plan
                        "deliverables" -> R.string.right_sidebar_tab_deliverables
                        else -> R.string.right_sidebar_tab_browser
                    },
                )
                Text(
                    text = label,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) palette.labelPrimary else palette.labelSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) palette.bgModulePlatform else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .clickable { onTabChange(id) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                "files" -> RightFilesTab(
                    files = files,
                    currentPath = currentPath,
                    onOpenDirectory = onOpenDirectory,
                    onNavigateUp = onNavigateUp,
                    onImport = onImport,
                    onPreviewFile = { item ->
                        onPreviewPathChange(item.absolutePath)
                        onTabChange("document")
                    },
                )
                "document" -> RightDocumentTab(
                    path = previewPath,
                    onAttach = { path ->
                        files.firstOrNull { it.absolutePath == path }?.let(onAttachFile)
                    },
                    onClose = { onPreviewPathChange(null) },
                )
                "plan" -> RightListTab(
                    emptyText = stringResource(R.string.right_sidebar_plan_empty),
                    items = todos.map { it.content to it.status },
                )
                "deliverables" -> RightDeliverablesTab(
                    deliverables = deliverables,
                    onOpen = onOpenDeliverable,
                )
                else -> RightInfoTab(
                    text = stringResource(R.string.right_sidebar_browser_unavailable),
                )
            }
        }
    }
}

@Composable
private fun RightFilesTab(
    files: List<FileItem>,
    currentPath: String,
    onOpenDirectory: (FileItem) -> Unit,
    onNavigateUp: (() -> Unit)?,
    onImport: () -> Unit,
    onPreviewFile: (FileItem) -> Unit,
) {
    val palette = dshPanelColors()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onNavigateUp != null) {
                Text(
                    text = stringResource(R.string.right_sidebar_up),
                    fontSize = 12.sp,
                    color = palette.labelSecondary,
                    modifier = Modifier.clickable(onClick = onNavigateUp),
                )
            }
            Text(
                text = currentPath.substringAfterLast('/').ifBlank { currentPath },
                fontSize = 12.sp,
                color = palette.labelTertiary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.right_sidebar_import),
                fontSize = 12.sp,
                color = palette.labelSecondary,
                modifier = Modifier.clickable(onClick = onImport),
            )
        }
        if (files.isEmpty()) {
            RightInfoTab(text = stringResource(R.string.right_sidebar_files_empty))
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                files.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.isDirectory) onOpenDirectory(item) else onPreviewFile(item)
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (item.isDirectory) "\u25B8" else "\u00B7",
                            fontSize = 13.sp,
                            color = palette.labelTertiary,
                            modifier = Modifier.width(14.dp),
                        )
                        Text(
                            text = item.name,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = palette.labelPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        if (!item.isDirectory) {
                            Text(
                                text = formatSize(item.sizeBytes),
                                fontSize = 11.sp,
                                color = palette.labelTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RightDocumentTab(
    path: String?,
    onAttach: (String) -> Unit,
    onClose: () -> Unit,
) {
    val palette = dshPanelColors()
    if (path == null) {
        RightInfoTab(text = stringResource(R.string.right_sidebar_document_empty))
        return
    }
    val file = remember(path) { File(path) }
    val content by produceState<String?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { readPreview(file) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = file.name,
                fontSize = 13.sp,
                color = palette.labelPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.right_sidebar_attach),
                fontSize = 12.sp,
                color = palette.labelSecondary,
                modifier = Modifier.clickable { onAttach(path) },
            )
            Text(
                text = stringResource(R.string.right_sidebar_close_preview),
                fontSize = 12.sp,
                color = palette.labelSecondary,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }
        Text(
            text = content ?: stringResource(R.string.right_sidebar_loading),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.Monospace,
            color = palette.labelPrimary,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun RightListTab(emptyText: String, items: List<Pair<String, String>>) {
    val palette = dshPanelColors()
    if (items.isEmpty()) {
        RightInfoTab(text = emptyText)
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        items.forEach { (text, status) ->
            val done = status == "completed"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (done) palette.labelPrimary else palette.borderL3),
                )
                Text(
                    text = text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (done) palette.labelSecondary else palette.labelPrimary,
                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

@Composable
private fun RightDeliverablesTab(
    deliverables: List<PresentedFileInfo>,
    onOpen: (PresentedFileInfo) -> Unit,
) {
    val palette = dshPanelColors()
    if (deliverables.isEmpty()) {
        RightInfoTab(text = stringResource(R.string.right_sidebar_deliverables_empty))
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        deliverables.forEach { file ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(file) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = file.path.substringAfterLast('/'),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = palette.labelPrimary,
                )
                val description = file.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = palette.labelTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RightInfoTab(text: String) {
    val palette = dshPanelColors()
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = palette.labelSecondary,
        )
    }
}

/** 读取文本预览：二进制/超限/不存在都给出人话说明。 */
private fun readPreview(file: File): String {
    if (!file.isFile) return "文件不存在"
    if (file.length() > PREVIEW_LIMIT_BYTES) return "文件过大，未预览（${formatSize(file.length())}）"
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return "读取失败"
    if (bytes.take(4096).any { it == 0.toByte() }) return "二进制文件，未预览（${formatSize(file.length())}）"
    return String(bytes, Charsets.UTF_8)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
