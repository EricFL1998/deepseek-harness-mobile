package com.deepseek.dsh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import com.deepseek.dsh.feature.chat.ChatScreen
import com.deepseek.dsh.feature.sessions.SessionsScreen
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

        setContent {
            MaterialTheme {
                MainAppScaffold(
                    runtimeManager = runtimeManager,
                    runner = runner
                )
            }
        }
    }
}

enum class NavigationTab {
    CHAT,
    SESSIONS,
    RUNTIME
}

@Composable
fun MainAppScaffold(
    runtimeManager: HostRuntimeManager,
    runner: HostProcessRunner
) {
    var currentTab by remember { mutableStateOf(NavigationTab.CHAT) }
    var selectedSessionId by remember { mutableStateOf<String?>("session-1") }

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
                content = "你好！我是运行在手机本地 Linux 容器里的 DSH Agent。工具和会话全部在手机上执行。"
            )
        )
    }

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
                            // 模拟 Agent 响应
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

                NavigationTab.RUNTIME -> {
                    RuntimeControlScreen(
                        runtimeManager = runtimeManager,
                        runner = runner
                    )
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


