package com.deepseek.dsh

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
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
private val RIGHT_TABS = listOf("guide", "files", "document", "plan", "deliverables", "terminal", "schedule", "browser")

/** 单次预览读取上限。 */
private const val PREVIEW_LIMIT_BYTES = 256 * 1024

/**
 * 聊天页右侧栏（官方 SidebarRight 的 drawer 版）：顶部只有一个 tab 条，
 * 没有独立标题栏；条尾是「+」（打开 guide）和收起按钮。不提供分栏。
 *
 * guide 页复刻官方 guide tab：居中一枚淡淡的指南针 + 工作区文件 / 新建终端 /
 * 浏览器三个入口胶囊。
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
    pluginTabs: List<com.deepseek.dsh.plugin.nativehost.NativeRightPaneTab> = emptyList(),
    guideEntries: List<com.deepseek.dsh.plugin.nativehost.NativeGuideEntry> = emptyList(),
    showTerminal: Boolean = false,
    onClose: () -> Unit,
) {
    val palette = dshPanelColors()
    val builtinTabs = RIGHT_TABS
    val knownTabs = builtinTabs + pluginTabs.map { "plugin:${it.key}" } +
        guideEntries.map { "guideentry:${it.key}" }
    val effectiveTab = if (tab in knownTabs) tab else "guide"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // 官方右侧栏顶部没有 tab 条：只有当前页标题与右侧的面板控制。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (effectiveTab == "guide") {
                Icon(
                    painter = painterResource(R.drawable.ic_rs_compass),
                    contentDescription = null,
                    tint = palette.labelSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = stringResource(rightTabLabel(effectiveTab)),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = palette.labelPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            // 官方面板控制：抽屉按钮（收起）。
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rs_panel),
                    contentDescription = "Close",
                    tint = palette.labelSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
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
                "guide" -> RightGuideTab(
                    showTerminal = showTerminal,
                    pluginTabs = pluginTabs,
                    guideEntries = guideEntries,
                    onOpenFiles = { onTabChange("files") },
                    onOpenTerminal = { onTabChange("terminal") },
                    onOpenBrowser = { onTabChange("browser") },
                    onOpenPlugin = { key -> onTabChange("plugin:$key") },
                    onOpenGuideEntry = { key -> onTabChange("guideentry:$key") },
                )
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
                    if (effectiveTab.startsWith("guideentry:")) {
                        val entry = guideEntries.firstOrNull { "guideentry:${it.key}" == effectiveTab }
                        if (entry != null) {
                            Box(modifier = Modifier.fillMaxSize()) { entry.render() }
                            return@Box
                        }
                    }
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

/** 官方 tab 标签文案。 */
private fun rightTabLabel(id: String): Int = when (id) {
    "guide" -> R.string.right_sidebar_tab_guide
    "files" -> R.string.right_sidebar_tab_files
    "document" -> R.string.right_sidebar_tab_document
    "plan" -> R.string.right_sidebar_tab_plan
    "deliverables" -> R.string.right_sidebar_tab_deliverables
    "terminal" -> R.string.right_sidebar_tab_terminal
    "schedule" -> R.string.right_sidebar_tab_schedule
    else -> R.string.right_sidebar_tab_browser
}

/** 官方 guide tab：淡指南针 + 三个入口胶囊。 */
@Composable
private fun RightGuideTab(
    showTerminal: Boolean,
    pluginTabs: List<com.deepseek.dsh.plugin.nativehost.NativeRightPaneTab>,
    guideEntries: List<com.deepseek.dsh.plugin.nativehost.NativeGuideEntry>,
    onOpenFiles: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenPlugin: (String) -> Unit,
    onOpenGuideEntry: (String) -> Unit,
) {
    val palette = dshPanelColors()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 入口是列表：空间不够（或以后条目更多）时整块上下滚动，指南针跟着走。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rs_compass),
                contentDescription = null,
                tint = palette.labelTertiary.copy(alpha = 0.45f),
                modifier = Modifier.size(52.dp),
            )
            Spacer(modifier = Modifier.height(28.dp))
            GuideEntry(
                icon = com.deepseek.dsh.core.designsystem.R.drawable.ic_folder_open,
                title = stringResource(R.string.right_guide_files_title),
                description = stringResource(R.string.right_guide_files_desc),
                onClick = onOpenFiles,
            )
            Spacer(modifier = Modifier.height(10.dp))
            GuideEntry(
                icon = R.drawable.ic_rs_terminal,
                title = stringResource(R.string.right_guide_terminal_title),
                description = stringResource(R.string.right_guide_terminal_desc),
                iconTint = dshPanelColors().labelPrimary,
                onClick = onOpenTerminal,
            )
            Spacer(modifier = Modifier.height(10.dp))
            GuideEntry(
                icon = R.drawable.ic_rs_globe,
                title = stringResource(R.string.right_guide_browser_title),
                description = stringResource(R.string.right_guide_browser_desc),
                iconTint = androidx.compose.ui.graphics.Color(0xFF2B6FF6),
                onClick = onOpenBrowser,
            )
            // capsule 列表是 slot：插件注册的右侧栏页面在这里各占一枚入口。
            pluginTabs.forEach { pluginTab ->
                Spacer(modifier = Modifier.height(10.dp))
                GuideEntry(
                    icon = R.drawable.ic_rs_globe,
                    title = pluginTab.title,
                    description = stringResource(R.string.right_guide_plugin_desc),
                    onClick = { onOpenPlugin(pluginTab.key) },
                )
            }
            guideEntries.forEach { entry ->
                Spacer(modifier = Modifier.height(10.dp))
                GuideEntry(
                    icon = R.drawable.ic_rs_globe,
                    title = entry.title,
                    description = entry.description ?: stringResource(R.string.right_guide_plugin_desc),
                    onClick = { onOpenGuideEntry(entry.key) },
                )
            }
        }
    }
}

@Composable
private fun GuideEntry(
    icon: Int,
    title: String,
    description: String,
    shortcut: String? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val palette = dshPanelColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bgLayer2)
            .border(0.5.dp, palette.borderL2, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint ?: palette.menuIcon,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = palette.labelPrimary,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = palette.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
