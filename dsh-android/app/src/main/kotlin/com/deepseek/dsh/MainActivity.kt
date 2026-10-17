package com.deepseek.dsh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
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
    private val deepseekOAuthManager = com.deepseek.dsh.core.protocol.DeepSeekOAuthManager()
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
            preferredPublicDir = File("/storage/emulated/0/Documents/DSH-Workspace"),
            appPrivateDir = filesDir
        )

        surfaceCatalogStore.updateCatalogFromJson(
            """
            {
                "clientId": "local",
                "dshVersion": "4.0.0",
                "surfaces": [
                    {
                        "pluginId": "dsh-subscription-oauth",
                        "slot": "conversation.composer.dock",
                        "order": 1,
                        "title": "Subscription OAuth",
                        "kind": "DOCK"
                    },
                    {
                        "pluginId": "dsh-context",
                        "slot": "settings.page",
                        "order": 2,
                        "title": "上下文增强插件",
                        "kind": "PAGE"
                    },
                    {
                        "pluginId": "dsh-pet",
                        "slot": "shell.overlay",
                        "order": 3,
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
                    context = applicationContext,
                    runtimeManager = runtimeManager,
                    runner = runner,
                    surfaceStore = surfaceCatalogStore,
                    oauthManager = deepseekOAuthManager,
                    workspaceManager = workspaceManager,
                    layout = layout
                )
            }
        }
    }
}

enum class ActivePage {
    CHAT,
    FILES,
    PLUGINS,
    HOSTS,
    APPROVAL,
    SETTINGS,
    SDUI_SAMPLE,
    RUNTIME
}

/**
 * 完整对齐现代 AI 助手主架构：
 * 侧滑侧栏抽屉（菜单/历史/项目/设置）+ 极简顶部状态栏 + 对话核心流 + 底部悬浮药丸输入
 */
@Composable
fun MainAppHost(
    context: android.content.Context,
    runtimeManager: HostRuntimeManager,
    runner: HostProcessRunner,
    surfaceStore: SurfaceCatalogStore,
    oauthManager: com.deepseek.dsh.core.protocol.DeepSeekOAuthManager,
    workspaceManager: WorkspaceManager,
    layout: RuntimeLayout
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var activePage by remember { mutableStateOf(ActivePage.CHAT) }
    var selectedSessionId by remember { mutableStateOf<String?>("session-1") }

    val availableHosts = remember {
        mutableStateListOf<com.deepseek.dsh.core.model.HostDescriptor>(
            com.deepseek.dsh.core.model.HostDescriptor.Local(),
            com.deepseek.dsh.core.model.HostDescriptor.Remote(
                id = "remote_work_pc",
                name = "开发工作站 (远程网关)",
                baseUrl = "https://192.168.1.100:8443",
                deviceToken = "sec_gw_tok_abc"
            )
        )
    }
    var activeHostId by remember { mutableStateOf("local-host") }
    val activeWorkspace = remember {
        workspaceManager.resolveActiveWorkspace(WorkspaceStorageType.REAL_EXTERNAL)
    }

    var currentAppSettings by remember {
        mutableStateOf(
            com.deepseek.dsh.feature.settings.AppSettings(
                workspacePath = activeWorkspace.absolutePath
            )
        )
    }
    val backupAndDiag = remember {
        com.deepseek.dsh.runtime.host.RuntimeBackupAndDiagnostics(layout)
    }
    var activeSheetSurface by remember { mutableStateOf<SurfaceItem?>(null) }
    var activeOverlaySurface by remember { mutableStateOf<SurfaceItem?>(null) }

    val status by runner.statusFlow.collectAsState()
    val catalog by surfaceStore.catalogFlow.collectAsState()
    val accountState by oauthManager.accountFlow.collectAsState()

    var showOnboardingLanding by remember { mutableStateOf(true) }

    if (showOnboardingLanding) {
        com.deepseek.dsh.feature.runtime.OnboardingLandingScreen(
            onComplete = { apiKey ->
                showOnboardingLanding = false
            },
            onOfficialOAuthClick = {
                val authUrl = oauthManager.buildOAuthAuthorizeUrl()
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl)).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )
        return
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
    val pendingApprovals = remember {
        mutableStateListOf(
            com.deepseek.dsh.feature.approval.ApprovalItem(
                id = "appr_1",
                title = "执行外部 Shell 脚本",
                command = "bash ./deploy.sh --clean",
                dangerReason = "该操作可能覆盖工作区现有构建目录与未提交代码",
                status = com.deepseek.dsh.feature.approval.ApprovalStatus.PENDING
            )
        )
    }

    val workspaceFolders = remember(sessions.size, selectedSessionId) {
        listOf(
            com.deepseek.dsh.feature.sessions.WorkspaceFolder(
                id = "ws_mobile",
                name = "deepseek mobile",
                sessions = sessions.toList(),
                isExpanded = true
            ),
            com.deepseek.dsh.feature.sessions.WorkspaceFolder(
                id = "ws_token",
                name = "token monitor android",
                sessions = emptyList(),
                isExpanded = false
            ),
            com.deepseek.dsh.feature.sessions.WorkspaceFolder(
                id = "ws_oauth",
                name = "subscription-oauth",
                sessions = emptyList(),
                isExpanded = false
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModernNavigationDrawerContent(
                    userName = accountState.profile?.name ?: "DeepSeek 用户",
                    userTag = if (accountState.isSignedIn) "DeepSeek 官方" else "未认证",
                    accountBalance = accountState.wallets.firstOrNull()?.let { "¥${it.balance}" } ?: "¥0.00",
                    workspaces = workspaceFolders,
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
                                hostId = activeHostId,
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
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.PLUGINS
                        }
                    },
                    onNavigateFiles = {
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.FILES
                        }
                    },
                    onNavigateHosts = {
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.HOSTS
                        }
                    },
                    onNavigateSettings = {
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.SETTINGS
                        }
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    // 现代顶栏: 纯粹简洁标题栏 (参考截图 1 & 3: 左侧汉堡菜单，中间会话标题，右侧播报与新建)
                    ModernTopAppBar(
                        title = when (activePage) {
                            ActivePage.CHAT -> sessions.find { it.sessionId == selectedSessionId }?.title ?: stringResource(R.string.app_name)
                            ActivePage.FILES -> stringResource(R.string.nav_files)
                            ActivePage.PLUGINS -> stringResource(R.string.nav_plugins)
                            ActivePage.HOSTS -> stringResource(R.string.nav_hosts)
                            ActivePage.APPROVAL -> stringResource(R.string.nav_approval)
                            ActivePage.SETTINGS -> stringResource(R.string.nav_settings)
                            ActivePage.SDUI_SAMPLE -> "SDUI"
                            ActivePage.RUNTIME -> stringResource(R.string.nav_runtime)
                        },
                        showModeCapsule = false,
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
                            val dockPlugins = catalog.surfaces.filter { it.kind == SurfaceKind.DOCK }
                            var reasoningLevel by remember { mutableStateOf(com.deepseek.dsh.feature.chat.ReasoningLevel.FAST) }

                            ModernChatScreen(
                                messages = messages,
                                onSendMessage = { text ->
                                    messages.add(
                                        ChatMessage(
                                            hostId = activeHostId,
                                            sessionId = selectedSessionId ?: "session-1",
                                            messageId = "user_${System.currentTimeMillis()}",
                                            role = MessageRole.USER,
                                            content = text
                                        )
                                    )
                                    val levelName = context.getString(reasoningLevel.labelResId)
                                    messages.add(
                                        ChatMessage(
                                            hostId = activeHostId,
                                            sessionId = selectedSessionId ?: "session-1",
                                            messageId = "asst_${System.currentTimeMillis()}",
                                            role = MessageRole.ASSISTANT,
                                            content = "[$levelName] Echo: \"$text\" (workspace: ${activeWorkspace.absolutePath})"
                                        )
                                    )
                                },
                                selectedModel = "DeepSeek-V3",
                                reasoningLevel = reasoningLevel,
                                onReasoningLevelChange = { reasoningLevel = it },
                                dockPluginSurfaces = dockPlugins,
                                onDockPluginClick = { pluginItem ->
                                    activeSheetSurface = pluginItem
                                },
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
                                },
                                onInstallPlugin = { pkgName ->
                                    val newSurface = SurfaceItem(
                                        pluginId = pkgName,
                                        slot = "conversation.composer.dock",
                                        order = 10,
                                        title = pkgName.substringAfterLast("/"),
                                        kind = SurfaceKind.DOCK
                                    )
                                    surfaceStore.addSurfaceItem(newSurface)
                                }
                            )
                        }

                        ActivePage.HOSTS -> {
                            com.deepseek.dsh.feature.hosts.HostsScreen(
                                hosts = availableHosts,
                                activeHostId = activeHostId,
                                onSelectHost = { host ->
                                    activeHostId = host.id
                                },
                                onScanPairClick = {
                                    val newRemote = com.deepseek.dsh.core.model.HostDescriptor.Remote(
                                        id = "remote_${System.currentTimeMillis()}",
                                        name = "新配对远程主机",
                                        baseUrl = "https://192.168.1.120:8443",
                                        deviceToken = "sec_pair_tok"
                                    )
                                    availableHosts.add(newRemote)
                                }
                            )
                        }

                        ActivePage.APPROVAL -> {
                            com.deepseek.dsh.feature.approval.ApprovalScreen(
                                approvals = pendingApprovals,
                                onApprove = { item ->
                                    val index = pendingApprovals.indexOfFirst { it.id == item.id }
                                    if (index != -1) {
                                        pendingApprovals[index] = item.copy(
                                            status = com.deepseek.dsh.feature.approval.ApprovalStatus.CONFIRMED
                                        )
                                    }
                                },
                                onDeny = { item ->
                                    val index = pendingApprovals.indexOfFirst { it.id == item.id }
                                    if (index != -1) {
                                        pendingApprovals[index] = item.copy(
                                            status = com.deepseek.dsh.feature.approval.ApprovalStatus.REJECTED
                                        )
                                    }
                                }
                            )
                        }

                        ActivePage.SETTINGS -> {
                            com.deepseek.dsh.feature.settings.SettingsScreen(
                                settings = currentAppSettings,
                                onUpdateSettings = { currentAppSettings = it },
                                accountName = accountState.profile?.name ?: "未登录",
                                accountBalance = accountState.wallets.firstOrNull()?.let { "¥${it.balance}" } ?: "¥0.00",
                                onManageAccount = {
                                    val authUrl = oauthManager.buildOAuthAuthorizeUrl()
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl)).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                onCreateBackup = {
                                    scope.launch {
                                        backupAndDiag.createUserDataBackup()
                                    }
                                },
                                onExportDiagnostics = {
                                    scope.launch {
                                        backupAndDiag.exportDiagnosticsZip()
                                    }
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
    onInstallPlugin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showInstallDialog by remember { mutableStateOf(false) }
    var pluginPackageInput by remember { mutableStateOf("") }
    var installStatusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.plugins_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.plugins_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = { showInstallDialog = true },
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_plugin),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_add_plugin),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

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
                    Text(stringResource(R.string.action_try))
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
                        Text(if (item.kind == SurfaceKind.OVERLAY) stringResource(R.string.action_open_overlay) else stringResource(R.string.action_open))
                    }
                }
            }
        }
    }

    // 官方对齐：按包名/地址添加插件对话框
    if (showInstallDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showInstallDialog = false
                installStatusMessage = null
            },
            title = {
                Text(text = stringResource(R.string.plugin_dialog_title))
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.plugin_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = pluginPackageInput,
                        onValueChange = { pluginPackageInput = it },
                        placeholder = { Text(stringResource(R.string.plugin_dialog_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.plugin_dialog_source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {
                                pluginPackageInput = "dsh-subscription-oauth"
                            }
                        ) {
                            Text(stringResource(R.string.action_fill_example))
                        }
                    }
                    installStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        if (pluginPackageInput.isNotBlank()) {
                            onInstallPlugin(pluginPackageInput.trim())
                            val installedName = pluginPackageInput.trim()
                            installStatusMessage = "OK: $installedName"
                            pluginPackageInput = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_install))
                }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        showInstallDialog = false
                        installStatusMessage = null
                    }
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
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
                text = stringResource(R.string.runtime_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = stringResource(R.string.runtime_phase, status.phase))
            Text(text = stringResource(R.string.runtime_port, status.port?.toString() ?: "--"))
            Text(text = stringResource(R.string.runtime_token, status.token?.take(10)?.plus("...") ?: "--"))

            Spacer(modifier = Modifier.padding(16.dp))

            if (status.phase == RuntimePhase.STOPPED || status.phase == RuntimePhase.UNINSTALLED || status.phase == RuntimePhase.ERROR) {
                androidx.compose.material3.Button(onClick = {
                    scope.launch {
                        runtimeManager.start("/storage/emulated/0/Documents/DSH-Workspace")
                    }
                }) {
                    Text(stringResource(R.string.action_start_container))
                }
            } else {
                androidx.compose.material3.Button(onClick = {
                    scope.launch {
                        runtimeManager.stop()
                    }
                }) {
                    Text(stringResource(R.string.action_stop_container))
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





