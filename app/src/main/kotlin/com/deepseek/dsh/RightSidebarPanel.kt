package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshDialogCloseButton
import com.deepseek.dsh.core.designsystem.dshPanelColors
import com.deepseek.dsh.core.model.TodoEntryInfo
import com.deepseek.dsh.core.protocol.PresentedFileInfo
import com.deepseek.dsh.feature.files.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 右侧栏标签（对应官方 sidebar.right.pane.tab 里手机端可实现的部分）。 */
private val RIGHT_TABS = listOf("files", "document", "plan", "deliverables", "terminal", "schedule", "browser")

/** 单次预览读取上限。 */
private const val PREVIEW_LIMIT_BYTES = 256 * 1024

/**
 * 聊天页右侧栏：标签条就是面板顶部（跟官方一样没有独立标题栏），末尾挂关闭。
 *
 * 文件/文档直接读手机工作区（Host 绑定的同一份目录）；计划与交付物来自官方
 * 会话投影；浏览器面板在手机端没有内核，给出明确说明。
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
    terminalContent: @Composable () -> Unit,
    scheduleContent: @Composable () -> Unit,
    /** 插件注册的右侧栏标签（App 新增的 RIGHT_PANEL 落点 API）。 */
    pluginTabs: List<com.deepseek.dsh.plugin.nativehost.NativeRightPaneTab> = emptyList(),
    /** 官方终端插件（@deepseek-ai/dsh-api-terminal-controller）启用时才显示终端标签。 */
    showTerminal: Boolean = false,
    onClose: () -> Unit,
) {
    val palette = dshPanelColors()
    // 官方未启用终端插件时，右侧栏不出现终端标签（和电脑版一致）。
    val builtinTabs = if (showTerminal) RIGHT_TABS else RIGHT_TABS.filter { it != "terminal" }
    // 选中的插件标签被卸载后回退到 Files，避免空白页。
    val knownTabs = builtinTabs + pluginTabs.map { "plugin:${it.key}" }
    val effectiveTab = if (tab in knownTabs) tab else "files"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .drawBehind {
                // 与左侧内容分隔的发丝线，官方 dock 分隔线同款。
                drawRect(
                    color = palette.borderL2,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(1f, size.height),
                )
            },
    ) {
        // 顶部标签条：36dp 高、发丝下边框，选中项下划线，末尾关闭按钮。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                builtinTabs.forEach { id ->
                    val selected = effectiveTab == id
                    val label = stringResource(
                        when (id) {
                            "files" -> R.string.right_sidebar_tab_files
                            "document" -> R.string.right_sidebar_tab_document
                            "plan" -> R.string.right_sidebar_tab_plan
                            "deliverables" -> R.string.right_sidebar_tab_deliverables
                            "terminal" -> R.string.right_sidebar_tab_terminal
                            "schedule" -> R.string.right_sidebar_tab_schedule
                            else -> R.string.right_sidebar_tab_browser
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable { onTabChange(id) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) palette.labelPrimary else palette.labelSecondary,
                        )
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(palette.labelPrimary),
                            )
                        }
                    }
                }
            }
            pluginTabs.forEach { pluginTab ->
                val id = "plugin:${pluginTab.key}"
                val selected = effectiveTab == id
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { onTabChange(id) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = pluginTab.title,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) palette.labelPrimary else palette.labelSecondary,
                        maxLines = 1,
                    )
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(palette.labelPrimary),
                        )
                    }
                }
            }
            DshDialogCloseButton(onClick = onClose, label = "Close")
            Spacer(modifier = Modifier.width(2.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.borderL2),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (effectiveTab) {
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
                "terminal" -> Box(modifier = Modifier.fillMaxSize()) { terminalContent() }
                "schedule" -> Box(modifier = Modifier.fillMaxSize()) { scheduleContent() }
                "browser" -> RightInfoTab(
                    text = stringResource(R.string.right_sidebar_browser_unavailable),
                )
                else -> {
                    val pluginTab = pluginTabs.firstOrNull { "plugin:${it.key}" == effectiveTab }
                    if (pluginTab != null) {
                        Box(modifier = Modifier.fillMaxSize()) { pluginTab.render() }
                    } else {
                        RightInfoTab(text = stringResource(R.string.right_sidebar_files_empty))
                    }
                }
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
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onNavigateUp != null) {
                Text(
                    text = stringResource(R.string.right_sidebar_up),
                    fontSize = 11.sp,
                    color = palette.labelSecondary,
                    modifier = Modifier.clickable(onClick = onNavigateUp),
                )
            }
            Text(
                text = currentPath.substringAfterLast('/').ifBlank { currentPath },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = palette.labelTertiary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.right_sidebar_import),
                fontSize = 11.sp,
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
                            .clickable { if (item.isDirectory) onOpenDirectory(item) else onPreviewFile(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (item.isDirectory) "\u25B8" else "",
                            fontSize = 10.sp,
                            color = palette.labelTertiary,
                            modifier = Modifier.width(10.dp),
                        )
                        Text(
                            text = item.name,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = palette.labelPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!item.isDirectory) {
                            Text(
                                text = formatSize(item.sizeBytes),
                                fontSize = 10.sp,
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
                .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = file.name,
                fontSize = 11.sp,
                color = palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.right_sidebar_attach),
                fontSize = 11.sp,
                color = palette.labelSecondary,
                modifier = Modifier.clickable { onAttach(path) },
            )
            Text(
                text = stringResource(R.string.right_sidebar_close_preview),
                fontSize = 11.sp,
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (done) palette.labelPrimary else palette.bgBase)
                        .border(1.dp, if (done) palette.labelPrimary else palette.borderL3, RoundedCornerShape(3.dp)),
                )
                Text(
                    text = text,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = if (done) palette.labelTertiary else palette.labelPrimary,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
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
            val description = file.description
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(file) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = file.path.substringAfterLast('/'),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = palette.labelPrimary,
                )
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
    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = palette.labelTertiary,
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
