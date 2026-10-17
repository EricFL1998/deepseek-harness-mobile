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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.MessageRole
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.core.model.SurfaceKind
import com.deepseek.dsh.feature.chat.ChatScreen
import com.deepseek.dsh.feature.sessions.SessionsScreen
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

        // 初始化模拟插件 Surface 注册
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
            MaterialTheme {
                MainAppScaffold(
                    runtimeManager = runtimeManager,
                    runner = runner,
                    surfaceStore = surfaceCatalogStore
                )
            }
        }
    }
}

enum class NavigationTab {
    CHAT,
    SESSIONS,
    PLUGINS,
    RUNTIME
}

@Composable
fun MainAppScaffold(
    runtimeManager: HostRuntimeManager,
    runner: HostProcessRunner,
    surfaceStore: SurfaceCatalogStore
) {
    var currentTab by remember { mutableStateOf(NavigationTab.CHAT) }
    var selectedSessionId by remember { mutableStateOf<String?>("session-1") }

    var activeSheetSurface by remember { mutableStateOf<SurfaceItem?>(null) }
    var activeOverlaySurface by remember { mutableStateOf<SurfaceItem?>(null) }

    val status by runner.statusFlow.collectAsState()
    val catalog by surfaceStore.catalogFlow.collectAsState()

    val sessions = remember {
        mutableStateListOf(
            SessionInfo(
                hostId = "local-host",
                sessionId = "session-1",
                title = "默认本地 Agent 会话",
                workspacePath = "/workspace",
                pinned = true
            )
        )
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                hostId = "local-host",
                sessionId = "session-1",
                messageId = "welcome",
                role = MessageRole.ASSISTANT,
                content = "你好！我是运行在手机本地 Linux 容器里的 DSH Agent。工具、插件和会话全部在手机上执行。"
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.CHAT,
                        onClick = { currentTab = NavigationTab.CHAT },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                        label = { Text("对话") }
                    )
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.SESSIONS,
                        onClick = { currentTab = NavigationTab.SESSIONS },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Sessions") },
                        label = { Text("会话") }
                    )
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.PLUGINS,
                        onClick = { currentTab = NavigationTab.PLUGINS },
                        icon = { Icon(Icons.Default.Extension, contentDescription = "Plugins") },
                        label = { Text("插件") }
                    )
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.RUNTIME,
                        onClick = { currentTab = NavigationTab.RUNTIME },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Runtime") },
                        label = { Text("运行时") }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    NavigationTab.CHAT -> {
                        ChatScreen(
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
                                        content = "已收到指令: \"$text\" (本地 Linux 用户空间处理)"
                                    )
                                )
                            }
                        )
                    }

                    NavigationTab.SESSIONS -> {
                        SessionsScreen(
                            sessions = sessions,
                            selectedSessionId = selectedSessionId,
                            onSelectSession = { session ->
                                selectedSessionId = session.sessionId
                                currentTab = NavigationTab.CHAT
                            }
                        )
                    }

                    NavigationTab.PLUGINS -> {
                        PluginsListScreen(
                            surfaces = catalog.surfaces,
                            onOpenSurface = { item ->
                                if (item.kind == SurfaceKind.OVERLAY) {
                                    activeOverlaySurface = item
                                } else {
                                    activeSheetSurface = item
                                }
                            }
                        )
                    }

                    NavigationTab.RUNTIME -> {
                        RuntimeControlScreen(
                            runtimeManager = runtimeManager,
                            runner = runner
                        )
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "内置 Surface 适配层插件",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "插件零改动，入口原生化，内容由 Web 岛承载",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.padding(8.dp))

        surfaces.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.kind == SurfaceKind.OVERLAY) Icons.Default.Widgets else Icons.Default.Extension,
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
                    Button(onClick = { onOpenSurface(item) }) {
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
                Button(onClick = {
                    scope.launch {
                        runtimeManager.start("/storage/emulated/0/Documents/DSH-Workspace")
                    }
                }) {
                    Text("启动 DSH 容器")
                }
            } else {
                Button(onClick = {
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



