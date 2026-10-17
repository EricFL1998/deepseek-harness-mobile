package com.deepseek.dsh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.core.designsystem.DshTheme
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.MessageRole
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.core.model.SurfaceKind
import com.deepseek.dsh.feature.chat.ModernChatScreen
import com.deepseek.dsh.feature.files.FilesScreen
import com.deepseek.dsh.feature.files.WorkspaceManager
import com.deepseek.dsh.feature.files.WorkspaceStorageType
import com.deepseek.dsh.feature.sessions.ModernNavigationDrawerContent
import com.deepseek.dsh.plugin.mobileui.SduiAction
import com.deepseek.dsh.plugin.mobileui.SduiButton
import com.deepseek.dsh.plugin.mobileui.SduiPage
import com.deepseek.dsh.plugin.mobileui.SduiPageRenderer
import com.deepseek.dsh.plugin.mobileui.SduiSection
import com.deepseek.dsh.plugin.mobileui.SduiText
import com.deepseek.dsh.plugin.surface.PluginOverlayContainer
import com.deepseek.dsh.plugin.surface.SurfaceCatalogStore
import com.deepseek.dsh.plugin.surface.SurfaceSheet
import com.deepseek.dsh.runtime.host.DefaultHostRuntimeManager
import com.deepseek.dsh.runtime.host.HostProcessRunner
import com.deepseek.dsh.runtime.host.HostRuntimeManager
import com.deepseek.dsh.runtime.host.RuntimeLayout
import com.deepseek.dsh.runtime.host.RuntimePhase
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var runtimeManager: HostRuntimeManager
    private val runner = HostProcessRunner()
    private val surfaceCatalogStore = SurfaceCatalogStore()
    private lateinit var workspaceManager: WorkspaceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = RuntimeLayout(
            baseDir = filesDir,
            nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
        )
        runtimeManager = DefaultHostRuntimeManager(
            context = applicationContext,
            layout = layout,
            runner = runner
        )

        workspaceManager = WorkspaceManager(
            defaultExternalDir = File(getExternalFilesDir(null) ?: filesDir, "DSH-Workspace"),
            appPrivateDir = filesDir
        )

        surfaceCatalogStore.updateCatalogFromJson(
            """
            {
                "clientId": "local",
                "dshVersion": "4.0.0",
                "surfaces": [
                    {
                        "pluginId": "dsh-context",
                        "slot": "settings.page",
                        "order": 1,
                        "title": "上下文增强插件",
                        "kind": "PAGE"
                    },
                    {
                        "pluginId": "dsh-pet",
                        "slot": "shell.overlay",
                        "order": 2,
                        "title": "桌宠与系统状态浮层",
                        "kind": "OVERLAY"
                    }
                ]
            }
            """.trimIndent()
        )

        setContent {
            DshTheme {
                MainAppHost(
                    runtimeManager = runtimeManager,
                    runner = runner,
                    surfaceStore = surfaceCatalogStore,
                    workspaceManager = workspaceManager
                )
            }
        }
    }
}

enum class ActivePage {
    CHAT,
    FILES,
    PLUGINS,
    SDUI_SAMPLE,
    RUNTIME
}

/**
 * 完整对齐现代 AI 助手主架构：
 * 侧滑侧栏抽屉（菜单/历史/项目/设置）+ 极简顶部状态栏 + 对话核心流 + 底部悬浮药丸输入
 */
@Composable
fun MainAppHost(
    runtimeManager: HostRuntimeManager,
    runner: HostProcessRunner,
    surfaceStore: SurfaceCatalogStore,
    workspaceManager: WorkspaceManager
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var activePage by remember { mutableStateOf(ActivePage.CHAT) }
    var selectedSessionId by remember { mutableStateOf<String?>("session-1") }

    var activeSheetSurface by remember { mutableStateOf<SurfaceItem?>(null) }
    var activeOverlaySurface by remember { mutableStateOf<SurfaceItem?>(null) }

    val status by runner.statusFlow.collectAsState()
    val catalog by surfaceStore.catalogFlow.collectAsState()

    val activeWorkspace = remember {
        workspaceManager.resolveActiveWorkspace(WorkspaceStorageType.REAL_EXTERNAL)
    }

    val files = remember(activePage) {
        workspaceManager.listFiles(activeWorkspace)
    }

    val sessions = remember {
        mutableStateListOf(
            SessionInfo(
                hostId = "local-host",
                sessionId = "session-1",
                title = "默认本地 Agent 会话",
                workspacePath = activeWorkspace.absolutePath,
                pinned = true
            ),
            SessionInfo(
                hostId = "local-host",
                sessionId = "session-2",
                title = "分析手机系统环境与工具链",
                workspacePath = activeWorkspace.absolutePath
            ),
            SessionInfo(
                hostId = "local-host",
                sessionId = "session-3",
                title = "Debian slim compact 解压自检",
                workspacePath = activeWorkspace.absolutePath
            )
        )
    }

    val messages = remember {
        mutableStateListOf<ChatMessage>()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModernNavigationDrawerContent(
                    userName = "本地用户",
                    userTag = if (status.phase == RuntimePhase.RUNNING) "Container Online" else "Host Standby",
                    sessions = sessions,
                    selectedSessionId = selectedSessionId,
                    onSelectSession = { session ->
                        selectedSessionId = session.sessionId
                        activePage = ActivePage.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onNewChatClick = {
                        val newId = "session_${System.currentTimeMillis()}"
                        sessions.add(
                            0,
                            SessionInfo(
                                hostId = "local-host",
                                sessionId = newId,
                                title = "新对话 ${sessions.size + 1}"
                            )
                        )
                        selectedSessionId = newId
                        messages.clear()
                        activePage = ActivePage.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onNavigatePlugins = {
                        activePage = ActivePage.PLUGINS
                        scope.launch { drawerState.close() }
                    },
                    onNavigateFiles = {
                        activePage = ActivePage.FILES
                        scope.launch { drawerState.close() }
                    },
                    onNavigateRuntime = {
                        activePage = ActivePage.RUNTIME
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    // 现代顶栏 (参考截图 1, 2, 3: 左侧汉堡菜单，中间胶囊/标题，右侧功能键)
                    ModernTopAppBar(
                        title = when (activePage) {
                            ActivePage.CHAT -> sessions.find { it.sessionId == selectedSessionId }?.title ?: "DSH Agent"
                            ActivePage.FILES -> "工作区文件"
                            ActivePage.PLUGINS -> "插件生态"
                            ActivePage.SDUI_SAMPLE -> "SDUI 原生渲染"
                            ActivePage.RUNTIME -> "本地 Linux 运行时"
                        },
                        showModeCapsule = (activePage == ActivePage.CHAT),
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNewChatClick = {
                            messages.clear()
                            activePage = ActivePage.CHAT
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (activePage) {
                        ActivePage.CHAT -> {
                            ModernChatScreen(
                                messages = messages,
                                onSendMessage = { text ->
                                    messages.add(
                                        ChatMessage(
                                            hostId = "local-host",
                                            sessionId = selectedSessionId ?: "session-1",
                                            messageId = "user_${System.currentTimeMillis()}",
                                            role = MessageRole.USER,
                                            content = text
                                        )
                                    )
                                    messages.add(
                                        ChatMessage(
                                            hostId = "local-host",
                                            sessionId = selectedSessionId ?: "session-1",
                                            messageId = "asst_${System.currentTimeMillis()}",
                                            role = MessageRole.ASSISTANT,
                                            content = "已收到指令并在手机本地 Linux 容器处理: \"$text\"。\n工作区挂载目录: ${activeWorkspace.absolutePath}"
                                        )
                                    )
                                },
                                selectedModel = "DeepSeek-V3",
                                onOpenAttachmentMenu = {
                                    activePage = ActivePage.FILES
                                }
                            )
                        }

                        ActivePage.FILES -> {
                            FilesScreen(
                                currentPath = activeWorkspace.absolutePath,
                                files = files,
                                onFileClick = { }
                            )
                        }

                        ActivePage.PLUGINS -> {
                            PluginsListScreen(
                                surfaces = catalog.surfaces,
                                onOpenSurface = { item ->
                                    if (item.kind == SurfaceKind.OVERLAY) {
                                        activeOverlaySurface = item
                                    } else {
                                        activeSheetSurface = item
                                    }
                                },
                                onOpenSdui = {
                                    activePage = ActivePage.SDUI_SAMPLE
                                }
                            )
                        }

                        ActivePage.SDUI_SAMPLE -> {
                            val samplePage = SduiPage(
                                title = "SDUI 声明式插件原生渲染",
                                description = "基于 v4.0 §7.8.1：由插件 JSON 声明，客户端零 WebView 原生渲染",
                                children = listOf(
                                    SduiSection(
                                        title = "插件控制面板",
                                        children = listOf(
                                            SduiText(text = "这是一个完全由原生 Compose 树渲染的插件界面。"),
                                            SduiButton(
                                                label = "返回插件列表",
                                                action = SduiAction.Close(),
                                                primary = true
                                            )
                                        )
                                    )
                                )
                            )
                            SduiPageRenderer(
                                page = samplePage,
                                onAction = { action ->
                                    if (action is SduiAction.Close) {
                                        activePage = ActivePage.PLUGINS
                                    }
                                }
                            )
                        }

                        ActivePage.RUNTIME -> {
                            RuntimeControlScreen(
                                runtimeManager = runtimeManager,
                                runner = runner
                            )
                        }
                    }
                }
            }
        }

        // 插件 Surface Sheet 呈现 (v4.0 §7.5)
        activeSheetSurface?.let { item ->
            SurfaceSheet(
                item = item,
                hostPort = status.port ?: 8080,
                onDismissRequest = { activeSheetSurface = null }
            )
        }

        // 插件单例悬浮窗 (v4.0 §7.7)
        activeOverlaySurface?.let { item ->
            PluginOverlayContainer(
                item = item,
                hostPort = status.port ?: 8080,
                onClose = { activeOverlaySurface = null }
            )
        }
    }
}

@Composable
fun PluginsListScreen(
    surfaces: List<SurfaceItem>,
    onOpenSurface: (SurfaceItem) -> Unit,
    onOpenSdui: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "插件生态与适配",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "现有插件零改动（Web 岛）+ 未来插件原生（SDUI）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.padding(8.dp))

        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SDUI 原生插件示例",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "JSON layout 声明 -> 原生 Compose 渲染",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                androidx.compose.material3.Button(onClick = onOpenSdui) {
                    Text("体验")
                }
            }
        }

        Spacer(modifier = Modifier.padding(4.dp))

        surfaces.forEach { item ->
            androidx.compose.material3.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.kind == SurfaceKind.OVERLAY) Icons.Default.Widgets else Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title ?: item.pluginId,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "槽位: ${item.slot} | 类型: ${item.kind}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    androidx.compose.material3.Button(onClick = { onOpenSurface(item) }) {
                        Text(if (item.kind == SurfaceKind.OVERLAY) "浮层打开" else "打开")
                    }
                }
            }
        }
    }
}

@Composable
fun RuntimeControlScreen(
    runtimeManager: HostRuntimeManager,
    runner: HostProcessRunner,
    modifier: Modifier = Modifier
) {
    val status by runner.statusFlow.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "本地 Linux 运行时控制",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = "运行阶段: ${status.phase}")
            Text(text = "端口: ${status.port ?: "未分配"}")
            Text(text = "Token: ${status.token?.take(10)?.plus("...") ?: "无"}")

            Spacer(modifier = Modifier.padding(16.dp))

            if (status.phase == RuntimePhase.STOPPED || status.phase == RuntimePhase.UNINSTALLED || status.phase == RuntimePhase.ERROR) {
                androidx.compose.material3.Button(onClick = {
                    scope.launch {
                        runtimeManager.start("/storage/emulated/0/Documents/DSH-Workspace")
                    }
                }) {
                    Text("启动 DSH 容器")
                }
            } else {
                androidx.compose.material3.Button(onClick = {
                    scope.launch {
                        runtimeManager.stop()
                    }
                }) {
                    Text("停止 DSH 容器")
                }
            }
        }
    }
}

/**
 * 现代轻量顶栏组件（参考截图 1, 2, 3）
 */
@Composable
fun ModernTopAppBar(
    title: String,
    showModeCapsule: Boolean = false,
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧汉堡菜单圆形按键
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            onClick = onMenuClick,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (showModeCapsule) {
            // 中间切换胶囊 (参考截图 2: 快速 / 进阶)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "快速",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Text(
                        text = "深度思考",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 右侧功能键：播报与新建
        IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Voice Output",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = onNewChatClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "New Session",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}





