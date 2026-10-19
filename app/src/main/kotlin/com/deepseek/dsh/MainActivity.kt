package com.deepseek.dsh

import com.deepseek.dsh.core.designsystem.DshIcons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshTheme
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.MessageRole
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.core.model.SurfaceKind
import com.deepseek.dsh.feature.chat.ModernChatScreen
import com.deepseek.dsh.feature.files.FilesScreen
import com.deepseek.dsh.feature.files.WorkspaceManager
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
import com.deepseek.dsh.runtime.host.HostRuntimeHolder
import com.deepseek.dsh.runtime.host.HostRuntimeManager
import com.deepseek.dsh.runtime.host.RuntimeLayout
import com.deepseek.dsh.runtime.host.RuntimePhase
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    /** 使用 app-specific 外部目录，proot/Android 11+ 才能稳定读写（不受 MANAGE_EXTERNAL_STORAGE 限制）。 */
    private val visibleWorkspaceDir: File by lazy {
        File(android.os.Environment.getExternalStorageDirectory(), "Documents/DSH-Workspace")
    }



    private val app: DshApplication get() = application as DshApplication
    private val runtimeManager: HostRuntimeManager get() = app.runtimeManager
    private val runner: HostProcessRunner get() = app.hostRunner
    private val surfaceCatalogStore = SurfaceCatalogStore()
    private val deepseekOAuthManager = com.deepseek.dsh.core.protocol.DeepSeekOAuthManager()
    private lateinit var workspaceManager: WorkspaceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        workspaceManager = WorkspaceManager(
            preferredPublicDir = visibleWorkspaceDir,
            appPrivateDir = filesDir
        )

        // 初始状态不预置任何插件；插件列表完全由 Host/用户安装产生。

        setContent {
            DshTheme {
                // 系统栏图标跟随应用深浅色：深色引导页需要浅色状态栏图标。
                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !systemDark
                        isAppearanceLightNavigationBars = !systemDark
                    }
                }
                val app = application as DshApplication
                MainAppHost(
                    context = applicationContext,
                    runtimeManager = runtimeManager,
                    runner = runner,
                    surfaceStore = surfaceCatalogStore,
                    oauthManager = deepseekOAuthManager,
                    workspaceManager = workspaceManager,
                    layout = app.runtimeLayout,
                    dshRepository = app.dshRepository,
                    settingsRepository = app.settingsRepository
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            val app = application as DshApplication
            Thread {
                kotlinx.coroutines.runBlocking {
                    runCatching { app.runtimeManager.stop() }
                }
            }.start()
        }
        super.onDestroy()
    }
}

enum class ActivePage {
    CHAT,
    FILES,
    PLUGINS,
    AUTOMATION,
    TASKBOARD,
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
    layout: RuntimeLayout,
    dshRepository: com.deepseek.dsh.core.data.DshRepository,
    settingsRepository: com.deepseek.dsh.core.data.DefaultSettingsRepository
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var activePage by remember { mutableStateOf(ActivePage.CHAT) }
    var settingsInitialSection by remember {
        mutableStateOf<com.deepseek.dsh.feature.settings.SettingsSectionTab?>(null)
    }
    val updateInfo by com.deepseek.dsh.feature.settings.UpdateCenter.update.collectAsState()
    val currentVersion = remember {
        com.deepseek.dsh.feature.settings.UpdateCenter.currentVersion(context)
    }
    LaunchedEffect(Unit) {
        com.deepseek.dsh.feature.settings.UpdateCenter.ensureChecked(context)
    }

    val persistedSettings by settingsRepository.settingsFlow.collectAsState()
    var currentAppSettings by remember(persistedSettings) { mutableStateOf(persistedSettings) }

    val externalWorkspaceDir = remember { java.io.File(android.os.Environment.getExternalStorageDirectory(), "Documents/DSH-Workspace") }
    val visibleWorkspaceDir = remember { java.io.File(android.os.Environment.getExternalStorageDirectory(), "Documents/DSH-Workspace") }

    var hasAllFilesAccess by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
    LaunchedEffect(hasAllFilesAccess) {
        while (!hasAllFilesAccess) {
            kotlinx.coroutines.delay(1000)
            hasAllFilesAccess = android.os.Environment.isExternalStorageManager()
        }
    }
    val openAllFilesSettings: () -> Unit = {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:" + context.packageName)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    // 首次启动已强制授予所有文件访问权限，工作区固定为公有 Documents，无需再迁移/回退。
    val activeWorkspace = remember {
        externalWorkspaceDir.mkdirs()
        java.io.File(externalWorkspaceDir, "默认工作区").mkdirs()
        externalWorkspaceDir
    }

    // UI 一开始就预热 Host，和引导/登录并行，避免用户点击登录时才冷启动。
    LaunchedEffect(Unit) {
        // 首次安装等 rootfs 准备完成后再启动；之后每次重新打开 App 立即启动 Host。
        if (java.io.File(context.filesDir, "rootfs/etc/os-release").exists()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { runtimeManager.start(activeWorkspace.absolutePath) }
            }
        }
    }
    val availableHosts = remember {
        mutableStateListOf<com.deepseek.dsh.core.model.HostDescriptor>(
            com.deepseek.dsh.core.model.HostDescriptor.Local()
        )
    }
    var activeHostId by remember { mutableStateOf("local-host") }

    val repoSessions by dshRepository.getSessions(activeHostId).collectAsState(initial = emptyList())
    val sessions = repoSessions

    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var pendingAgentPreset by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val pendingMessages = remember { androidx.compose.runtime.mutableStateListOf<ChatMessage>() }
    val liveSessionIds by dshRepository.liveSessionIdsFlow.collectAsState()
    val runningSessionIds by dshRepository.runningSessionIdsFlow.collectAsState()
    val unreadSessionIds by dshRepository.unreadSessionIdsFlow.collectAsState()
    LaunchedEffect(selectedSessionId) { dshRepository.markSessionViewed(selectedSessionId) }

    // 会话完成通知：运行中的会话从 running 变为结束且当前不在前台时提醒。
    var wasSessionRunning by remember { mutableStateOf(false) }
    val sessionRunningNow = selectedSessionId != null && selectedSessionId in runningSessionIds
    LaunchedEffect(sessionRunningNow) {
        if (wasSessionRunning && !sessionRunningNow) {
            if (currentAppSettings.notificationsEnabled && currentAppSettings.notifyOnTurnComplete) {
                val title = sessions.find { it.sessionId == selectedSessionId }?.title ?: "会话"
                DshNotifications.notify(context, "DSH 已完成", "$title 已完成本轮回复")
            }
        }
        wasSessionRunning = sessionRunningNow
    }
    var didInitialResume by remember { mutableStateOf(false) }
    LaunchedEffect(sessions, liveSessionIds) {
        // 每次打开 App：自动进入最近更新的会话，而不是停留在空白新会话页。
        if (!didInitialResume && sessions.isNotEmpty()) {
            didInitialResume = true
            val candidates = sessions.filter { !it.archived }.ifEmpty { sessions }
            selectedSessionId = (
                candidates.filter { it.sessionId in liveSessionIds }.ifEmpty { candidates }
                    .maxByOrNull { it.updatedAt }
                )?.sessionId
        }
    }

    val activeSession = sessions.find { it.sessionId == selectedSessionId }

    val repoMessages by dshRepository.getMessages(activeHostId, selectedSessionId ?: "")
        .collectAsState(initial = emptyList())
    val messages = remember(repoMessages) { repoMessages }


    val catalogInfo by dshRepository.modelCatalogFlow.collectAsState()
    val hostModels = remember(catalogInfo) { catalogInfo.groups.flatMap { it.models } }
    val hostModelNames = remember(hostModels) { hostModels.map { it.name } }
    val hostModelIdByName = remember(hostModels) { hostModels.associate { it.name to it.id } }

    // Host 冷启动需要先拉起 proot/DSH，模型目录返回前用上次缓存让菜单立刻可用。
    val modelCatalogCache = remember {
        context.getSharedPreferences("dsh_model_catalog_cache", android.content.Context.MODE_PRIVATE)
    }
    var cachedModelNames by remember {
        mutableStateOf(
            modelCatalogCache.getString("names", "").orEmpty()
                .split('\u0001').filter { it.isNotBlank() }
        )
    }
    var cachedModelIds by remember {
        mutableStateOf(
            modelCatalogCache.getString("ids", "").orEmpty()
                .split('\u0001').filter { it.isNotBlank() }
        )
    }
    LaunchedEffect(hostModels) {
        if (hostModels.isNotEmpty()) {
            cachedModelNames = hostModels.map { it.name }
            cachedModelIds = hostModels.map { it.id }
            modelCatalogCache.edit()
                .putString("names", cachedModelNames.joinToString("\u0001"))
                .putString("ids", cachedModelIds.joinToString("\u0001"))
                .apply()
        }
    }

    val currentSessionStats by dshRepository.getStatsForSession(selectedSessionId ?: "").collectAsState(
        initial = com.deepseek.dsh.core.model.ComposerStats()
    )

    val builtInPluginsList by dshRepository.builtInPluginsFlow.collectAsState()
    val pluginSnapshot by dshRepository.pluginSnapshotFlow.collectAsState()
    val pluginManagerSnapshot by dshRepository.pluginManagerSnapshotFlow.collectAsState()
    val pluginRows by dshRepository.pluginRowsFlow.collectAsState()
    // 插件页目录来自 bridge/v1：官方 slot 注册优先，Host 原生清单兜底。
    val bridgePluginCatalog by dshRepository.bridgePluginCatalogFlow.collectAsState()
    val bridgeWarnings by dshRepository.bridgeWarningsFlow.collectAsState()

    var modelProvidersRaw by remember { mutableStateOf<String?>(null) }
    var modelSettingsRaw by remember { mutableStateOf<String?>(null) }
    var modelCredentialsRaw by remember { mutableStateOf<String?>(null) }

    val automationTasks by dshRepository.automationTasksFlow.collectAsState()
    val teamTasks by dshRepository.teamTasksFlow.collectAsState()
    val pendingApprovalsModels by dshRepository.pendingApprovalsFlow.collectAsState()

    val pendingApprovals = remember(pendingApprovalsModels) {
        pendingApprovalsModels.map { model ->
            val status = when (model.status) {
                "submitting" -> com.deepseek.dsh.feature.approval.ApprovalStatus.SUBMITTING
                "confirmed" -> com.deepseek.dsh.feature.approval.ApprovalStatus.CONFIRMED
                "rejected" -> com.deepseek.dsh.feature.approval.ApprovalStatus.REJECTED
                else -> com.deepseek.dsh.feature.approval.ApprovalStatus.PENDING
            }
            com.deepseek.dsh.feature.approval.ApprovalItem(
                id = model.id,
                title = model.title,
                command = model.command,
                dangerReason = model.dangerReason,
                status = status
            )
        }
    }

    LaunchedEffect(activeHostId, selectedSessionId) {
        selectedSessionId?.let { sid ->
            dshRepository.refreshSessionStats(activeHostId, sid)
        }
    }

    // bridge 插件更新走 Host 自己的安装 RPC，而不是由 App 另起 proot：
    // 应用进程的 seccomp 会让 glibc 的 mkdtemp 拿到 ENOSYS，而 Host 进程执行
    // 同一条命令正常。这也是插件页「添加插件」用的同一条路径。
    DisposableEffect(activeHostId) {
        HostRuntimeHolder.pluginInstaller = { spec ->
            // fold/runCatching 都是 inline，因此可以在 suspend lambda 里安全地
            // 调用挂起的版本读取。
            dshRepository.installPlugin(activeHostId, spec).fold(
                onSuccess = {
                    runCatching { runtimeManager.installedBridgeVersion() }.getOrNull()
                        ?.let { version -> Result.success(version) }
                        ?: Result.failure(
                            IllegalStateException("安装命令已返回，但读不到插件版本")
                        )
                },
                onFailure = { error -> Result.failure(error) },
            )
        }
        onDispose { HostRuntimeHolder.pluginInstaller = null }
    }

    LaunchedEffect(activeHostId) {
        dshRepository.refreshSessions(activeHostId)
        dshRepository.refreshModelCatalog(activeHostId)
        dshRepository.refreshPluginInventory(activeHostId)
        // bridge 目录与 Host 原生清单并行读取：前者带来官方 slot 与图形，
        // 后者是它不可用时的兜底，两者互不阻塞。
        dshRepository.refreshBridgePluginCatalog()
        dshRepository.refreshAutomationTasks(activeHostId)
        dshRepository.refreshTeamTasks(activeHostId)
        dshRepository.refreshApprovals(activeHostId)
        dshRepository.subscribeRemoteEvents(activeHostId)
    }
    val onboardingPreferences = remember {
        com.deepseek.dsh.feature.runtime.OnboardingPreferences(context)
    }

    val backupAndDiag = remember {
        com.deepseek.dsh.runtime.host.RuntimeBackupAndDiagnostics(layout)
    }
    var activeSheetSurface by remember { mutableStateOf<SurfaceItem?>(null) }
    var activeOverlaySurface by remember { mutableStateOf<SurfaceItem?>(null) }

    val status by runner.statusFlow.collectAsState()
    val catalog by surfaceStore.catalogFlow.collectAsState()
    val accountState by oauthManager.accountFlow.collectAsState()
    LaunchedEffect(accountState.isSignedIn, accountState.hasApiKey) {
        if (accountState.isSignedIn || accountState.hasApiKey) {
            kotlinx.coroutines.delay(600)
            modelSettingsRaw = dshRepository.rpcRaw(activeHostId, "settings/describe", "{}").getOrNull()
            modelProvidersRaw = dshRepository.rpcRaw(activeHostId, "llm/listConfigurableProviders", "{}").getOrNull()
            val providerRefs = com.deepseek.dsh.feature.settings.parseModelProviders(
                modelProvidersRaw, modelSettingsRaw, null
            ).map { it.keyRef }.distinct()
            val refsJson = providerRefs.joinToString(prefix = "{\"refs\":[", postfix = "]}") { "\"$it\"" }
            modelCredentialsRaw = dshRepository.rpcRaw(activeHostId, "credentials/describe", refsJson).getOrNull()
            dshRepository.refreshModelCatalog(activeHostId)
            dshRepository.refreshPluginInventory(activeHostId)
        }
    }
    val dshApplication = context.applicationContext as DshApplication

    // Host 目录优先；Host 冷启动期间用上次缓存立即渲染，避免“请选择模型”闪烁。
    val effectiveModelNames = remember(hostModelNames, cachedModelNames) {
        if (hostModelNames.isNotEmpty()) hostModelNames else cachedModelNames
    }
    val effectiveModelIdByName = remember(
        hostModelNames, hostModelIdByName, cachedModelNames, cachedModelIds
    ) {
        if (hostModelNames.isNotEmpty()) hostModelIdByName
        else cachedModelNames.zip(cachedModelIds).toMap()
    }
    val availableModelNames = effectiveModelNames.distinct()

    // 官方账号菜单的登录入口：浏览器 OAuth 为主，API Key 为备选（与官方 SignInDialog 一致）。
    var showSignInDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    val openUrl: (String) -> Unit = { url ->
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
    }

    val hostSignOut: () -> Unit = {
        scope.launch {
            dshRepository.signOutAccount(activeHostId)
            oauthManager.signOut()
        }
    }

    // 官方流程：Host 的 account/startSignIn 生成带本次 attempt 的授权链接；
    // 浏览器完成授权后 Host 写入 credential，客户端轮询 account/getState 同步登录态。
    val beginHostSignIn: () -> Unit = {
        scope.launch {
            val callbackOrigin = dshApplication.defaultClient.currentBaseUrl()
            var view = dshRepository.startAccountSignIn(activeHostId, callbackOrigin).getOrNull()
            if (view == null) {
                // Host 冷启动需要时间；等待就绪后再重试，而不是直接报错。
                runCatching { runtimeManager.start(activeWorkspace.absolutePath) }
                repeat(10) {
                    kotlinx.coroutines.delay(2000)
                    view = dshRepository.startAccountSignIn(
                        activeHostId,
                        dshApplication.defaultClient.currentBaseUrl()
                    ).getOrNull()
                    if (view != null) return@repeat
                }
            }
            if (view == null) {
                android.widget.Toast.makeText(
                    context,
                    "登录失败：Host 未就绪",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            var authorizeUrl = view.authorizeUrl
            // Host 先返回 initializing，随后异步拿到平台授权链接。
            var polls = 0
            while (authorizeUrl.isNullOrBlank() && polls < 20) {
                kotlinx.coroutines.delay(500)
                authorizeUrl = dshRepository.getAccountState(activeHostId).getOrNull()?.authorizeUrl
                polls++
            }
            if (authorizeUrl.isNullOrBlank()) {
                android.widget.Toast.makeText(
                    context,
                    "登录失败：未获取授权链接",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            openUrl(authorizeUrl)
            repeat(400) {
                kotlinx.coroutines.delay(1500)
                val state = dshRepository.getAccountState(activeHostId).getOrNull() ?: return@repeat
                if (state.status == "credential-stored") {
                    val profile = dshRepository.getAccountProfile(activeHostId).getOrNull()
                    val balance = dshRepository.getAccountBalance(activeHostId).getOrNull()
                    oauthManager.applyHostAccount(profile, balance)
                    modelSettingsRaw = dshRepository.rpcRaw(activeHostId, "settings/describe", "{}").getOrNull()
            modelProvidersRaw = dshRepository.rpcRaw(activeHostId, "llm/listConfigurableProviders", "{}").getOrNull()
            val providerRefs = com.deepseek.dsh.feature.settings.parseModelProviders(
                modelProvidersRaw, modelSettingsRaw, null
            ).map { it.keyRef }.distinct()
            val refsJson = providerRefs.joinToString(prefix = "{\"refs\":[", postfix = "]}") { "\"$it\"" }
            modelCredentialsRaw = dshRepository.rpcRaw(activeHostId, "credentials/describe", refsJson).getOrNull()
            dshRepository.refreshModelCatalog(activeHostId)
                    dshRepository.refreshPluginInventory(activeHostId)
                    return@launch
                }
                if (state.attemptPhase in setOf("failed", "expired", "cancelled")) {
                    android.widget.Toast.makeText(
                        context,
                        "登录未完成，请重试",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
            }
        }
    }

    // 本地运行时启动后：把仓库客户端绑定到实际端口/token，并重新拉取全部 Host 数据。
    LaunchedEffect(status.phase, status.port, status.token) {
        val port = status.port
        if (port != null) {
            if (currentAppSettings.remoteHostUrl.isBlank()) {
                dshApplication.bindLocalHost(port, status.token)
            }
            dshRepository.refreshSessions(activeHostId)
            modelSettingsRaw = dshRepository.rpcRaw(activeHostId, "settings/describe", "{}").getOrNull()
            modelProvidersRaw = dshRepository.rpcRaw(activeHostId, "llm/listConfigurableProviders", "{}").getOrNull()
            val providerRefs = com.deepseek.dsh.feature.settings.parseModelProviders(
                modelProvidersRaw, modelSettingsRaw, null
            ).map { it.keyRef }.distinct()
            val refsJson = providerRefs.joinToString(prefix = "{\"refs\":[", postfix = "]}") { "\"$it\"" }
            modelCredentialsRaw = dshRepository.rpcRaw(activeHostId, "credentials/describe", refsJson).getOrNull()
            dshRepository.refreshModelCatalog(activeHostId)
            dshRepository.refreshPluginInventory(activeHostId)
            dshRepository.refreshAutomationTasks(activeHostId)
            dshRepository.refreshTeamTasks(activeHostId)
            dshRepository.refreshApprovals(activeHostId)
            dshRepository.subscribeRemoteEvents(activeHostId)
            // bridge 必须在绑定之后才能读：Host 未就绪时桥没有地址，只会报
            // "not bound"。这里重新拉一次，插件页才会用上官方分类与图形。
            dshRepository.refreshBridgePluginCatalog()
            // 从 Host 恢复账号态（官方 account/watch 的初值）。
            val accountView = dshRepository.getAccountState(activeHostId).getOrNull()
            if (accountView?.status == "credential-stored") {
                val profile = dshRepository.getAccountProfile(activeHostId).getOrNull()
                val balance = dshRepository.getAccountBalance(activeHostId).getOrNull()
                oauthManager.applyHostAccount(profile, balance)
            }
        }
    }

    // 首次启动先准备 rootfs：未安装/未下载完成之前不进入 onboarding。
    // 已安装过就直接视为就绪，避免重新打开时闪一下安装进度条；首次安装仍走 null→安装。
    var rootfsReady by remember { mutableStateOf<Boolean?>(null) }
    var rootfsSetupProgress by remember { mutableStateOf(0f to "检查本地运行时...") }
    var rootfsSetupError by remember { mutableStateOf<String?>(null) }
    var rootfsInstallAttempt by remember { mutableStateOf(0) }

    var rootfsUpdateAvailable by remember { mutableStateOf(false) }
    var installedRootfsVersion by remember { mutableStateOf<String?>(null) }
    var availableRootfsVersion by remember { mutableStateOf<String?>(null) }
    var showRootfsUpdatePrompt by remember { mutableStateOf(false) }
    var rootfsUpdateBannerDismissed by remember { mutableStateOf(false) }
    var forceRootfsInstall by remember { mutableStateOf(false) }

    /**
     * 只在镜像**必需**重装时才在启动时提示。
     *
     * 比较交给 `rootfsUpdateKind`：它按段比版本，只有 rootfs 段或 dsh 段变化才
     * 返回 MANDATORY。插件落后不在这里处理 —— 那是设置页的职责，且可以就地升级，
     * 不该在启动时打断用户。
     */
    LaunchedEffect(rootfsReady) {
        if (rootfsReady != true) return@LaunchedEffect
        val installed = runtimeManager.installedRootfsVersion()
        val available = runtimeManager.availableRootfsVersion()
        installedRootfsVersion = installed
        availableRootfsVersion = available
        val kind = runCatching { runtimeManager.rootfsUpdateKind() }
            .getOrDefault(com.deepseek.dsh.runtime.host.RootfsUpdateKind.NONE)
        val mandatory = kind == com.deepseek.dsh.runtime.host.RootfsUpdateKind.MANDATORY
        rootfsUpdateAvailable = mandatory
        showRootfsUpdatePrompt = mandatory
    }

    if (showRootfsUpdatePrompt) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { showRootfsUpdatePrompt = false },
            title = "运行时更新",
            closeLabel = "稍后",
            description = "发现新的 rootfs：$availableRootfsVersion（当前 $installedRootfsVersion）。更新会重新下载运行时，并保留你的账号、会话与工作区。",
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "稍后",
                    primary = false,
                    enabled = true,
                    onClick = { showRootfsUpdatePrompt = false },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "立即更新",
                    primary = true,
                    enabled = true,
                    onClick = {
                        showRootfsUpdatePrompt = false
                        forceRootfsInstall = true
                        rootfsReady = false
                        scope.launch {
                            runCatching { runtimeManager.stop() }
                            rootfsInstallAttempt += 1
                        }
                    },
                )
            },
        )
    }


    LaunchedEffect(rootfsInstallAttempt) {
        if (runtimeManager.isRootfsInstalled()) {
            forceRootfsInstall = false
            rootfsReady = true
            return@LaunchedEffect
        }
        // 旧版 rootfs 仍完整可用时不要自动覆盖：只在**必需**重装时提示，
        // 且由用户确认；插件落后不拦启动（在设置页就地升级）。
        val installedVersion = runtimeManager.installedRootfsVersion()
        val availableVersion = runtimeManager.availableRootfsVersion()
        val rootfsFilesPresent = java.io.File(context.filesDir, "rootfs/etc/os-release").isFile ||
            java.io.File(context.filesDir, "rootfs/usr/bin/env").isFile
        val kind = runCatching { runtimeManager.rootfsUpdateKind() }
            .getOrDefault(com.deepseek.dsh.runtime.host.RootfsUpdateKind.NONE)
        if (!forceRootfsInstall && rootfsFilesPresent &&
            kind == com.deepseek.dsh.runtime.host.RootfsUpdateKind.MANDATORY
        ) {
            installedRootfsVersion = installedVersion
            availableRootfsVersion = availableVersion
            rootfsUpdateAvailable = true
            showRootfsUpdatePrompt = true
            rootfsReady = true
            return@LaunchedEffect
        }
        rootfsReady = false
        rootfsSetupError = null
        val result = runtimeManager.installRootfs { progress, message ->
            rootfsSetupProgress = progress to message
        }
        result.exceptionOrNull()?.let { error ->
            rootfsSetupError = error.message ?: "rootfs 安装失败"
        }
        if (result.isSuccess) rootfsReady = true
    }

    // rootfs 就绪后再拉起本地 Host，避免在未安装时产生启动错误。
    LaunchedEffect(rootfsReady) {
        if (rootfsReady == true &&
            (status.phase == RuntimePhase.STOPPED ||
                status.phase == RuntimePhase.UNINSTALLED ||
                status.phase == RuntimePhase.ERROR)
        ) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runtimeManager.start(activeWorkspace.absolutePath)
            }
        }
    }

    if (rootfsReady == null) {
        HostStartupSplash(
            phase = RuntimePhase.STARTING,
            title = "正在检查本地运行时…",
            onRetry = {}
        )
        return
    }

    if (rootfsReady != true) {
        RootfsSetupScreen(
            progress = rootfsSetupProgress.first,
            message = rootfsSetupProgress.second,
            error = rootfsSetupError,
            onRetry = { rootfsInstallAttempt++ },
        )
        return
    }

    // 首次进入必须先授予「所有文件访问权限」，否则不能进入引导/主界面。
    if (!hasAllFilesAccess) {
        StoragePermissionGate(onRequest = openAllFilesSettings)
        return
    }

    // 已安装后的每次重新打开：轮询 Host 的真实运行状态；
    // 一旦 endpoint（port）就绪就进入 UI，不等 markReady/RUNNING。
    val rootfsWasInstalled = remember {
        java.io.File(context.filesDir, "rootfs/etc/os-release").exists()
    }
    var hostProgressReady by remember { mutableStateOf(!rootfsWasInstalled) }
    var hostStartupError by remember { mutableStateOf(false) }
    var hostRetryKey by remember { mutableStateOf(0) }
    LaunchedEffect(rootfsWasInstalled, hostRetryKey) {
        if (!rootfsWasInstalled) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (!hostProgressReady) {
            if (System.currentTimeMillis() - startedAt > 20_000L) {
                hostProgressReady = true
                break
            }
            val current = runCatching { runtimeManager.getStatus() }.getOrNull()
            if (current == null) {
                kotlinx.coroutines.delay(300)
                continue
            }
            when {
                current.phase == RuntimePhase.RUNNING || current.port != null -> {
                    hostProgressReady = true
                    hostStartupError = false
                }
                current.phase == RuntimePhase.ERROR -> {
                    hostStartupError = true
                    break
                }
                else -> kotlinx.coroutines.delay(300)
            }
        }
    }
    if (rootfsWasInstalled && !hostProgressReady) {
        HostStartupSplash(
            phase = if (hostStartupError) RuntimePhase.ERROR else RuntimePhase.STARTING,
            onRetry = {
                hostStartupError = false
                hostRetryKey += 1
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { runtimeManager.start(activeWorkspace.absolutePath) }
                    }
                }
            }
        )
        return
    }
    // 首次打开引导：完全替代旧 Landing 页，完成后写入本地进度，仅首次展示。
    var showOnboarding by remember { mutableStateOf(!onboardingPreferences.isCompleted) }

    if (showOnboarding) {
        val funded = accountState.wallets.any { (it.balance.toDoubleOrNull() ?: 0.0) > 0.0 } ||
            accountState.bonusWallets.any { (it.balance.toDoubleOrNull() ?: 0.0) > 0.0 }
        com.deepseek.dsh.feature.runtime.OnboardingScreen(
            funded = funded,
            onRecharge = {
                val rechargeIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://platform.deepseek.com/top_up")
                ).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                runCatching { context.startActivity(rechargeIntent) }
            },
            onComplete = { result ->
                onboardingPreferences.save(result)
                val updated = currentAppSettings.copy(
                    showCodingView = result.developerTools,
                    transcriptView = result.process.storageValue,
                )
                currentAppSettings = updated
                scope.launch { settingsRepository.updateSettings { updated } }
                showOnboarding = false
            }
        )
        return
    }

    var workspaceRevision by remember { mutableStateOf(0) }
    var activeWorkspaceName by remember { mutableStateOf("默认工作区") }
    val activeWorkspaceCwd = "/workspace/$activeWorkspaceName"
    val activeWorkspaceFolderPath = java.io.File(activeWorkspace, activeWorkspaceName).absolutePath
    var showAddWorkspaceDialog by remember { mutableStateOf(false) }
    var newWorkspaceName by remember { mutableStateOf("") }
    var renameWorkspaceFolder by remember {
        mutableStateOf<com.deepseek.dsh.feature.sessions.WorkspaceFolder?>(null)
    }
    var renameWorkspaceName by remember { mutableStateOf("") }
    var deleteWorkspaceFolder by remember {
        mutableStateOf<com.deepseek.dsh.feature.sessions.WorkspaceFolder?>(null)
    }
    if (showAddWorkspaceDialog) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { showAddWorkspaceDialog = false },
            title = "新建工作区",
            closeLabel = "关闭",
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "取消",
                    primary = false,
                    enabled = true,
                    onClick = { showAddWorkspaceDialog = false },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "创建",
                    primary = true,
                    enabled = newWorkspaceName.isNotBlank(),
                    onClick = {
                        val name = newWorkspaceName.trim()
                        if (name.isNotEmpty()) {
                            java.io.File(activeWorkspace, name).mkdirs()
                            activeWorkspaceName = name
                            workspaceRevision += 1
                        }
                        showAddWorkspaceDialog = false
                    },
                )
            },
            content = {
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = newWorkspaceName,
                    onValueChange = { newWorkspaceName = it },
                    placeholder = "工作区名称",
                )
            },
        )
    }
    renameWorkspaceFolder?.let { folder ->
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { renameWorkspaceFolder = null },
            title = "重命名工作区",
            closeLabel = "关闭",
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "取消", primary = false, enabled = true,
                    onClick = { renameWorkspaceFolder = null },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "保存", primary = true,
                    enabled = renameWorkspaceName.isNotBlank(),
                    onClick = {
                        val target = java.io.File(
                            folder.path.ifBlank { java.io.File(activeWorkspace, folder.name).absolutePath }
                        )
                        val renamed = java.io.File(target.parentFile, renameWorkspaceName.trim())
                        if (target.exists() && renamed.absolutePath != target.absolutePath) {
                            runCatching { target.renameTo(renamed) }
                            if (folder.name == activeWorkspaceName) activeWorkspaceName = renameWorkspaceName.trim()
                            workspaceRevision += 1
                        }
                        renameWorkspaceFolder = null
                    },
                )
            },
            content = {
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = renameWorkspaceName,
                    onValueChange = { renameWorkspaceName = it },
                    placeholder = "工作区名称",
                )
            },
        )
    }

    deleteWorkspaceFolder?.let { folder ->
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { deleteWorkspaceFolder = null },
            title = "删除工作区",
            closeLabel = "关闭",
            description = "将删除「${folder.name}」及其中的所有文件，此操作不可撤销。",
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "取消", primary = false, enabled = true,
                    onClick = { deleteWorkspaceFolder = null },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "删除", primary = true, enabled = true,
                    onClick = {
                        val target = java.io.File(
                            folder.path.ifBlank { java.io.File(activeWorkspace, folder.name).absolutePath }
                        )
                        runCatching { target.deleteRecursively() }
                        if (folder.name == activeWorkspaceName) activeWorkspaceName = "默认工作区"
                        workspaceRevision += 1
                        deleteWorkspaceFolder = null
                    },
                )
            },
        )
    }
    val files = remember(activePage, activeWorkspace) {
        workspaceManager.listFiles(activeWorkspace)
    }


    val workspaceFolders = remember(sessions, selectedSessionId, activeWorkspace, workspaceRevision) {
        // 根目录 DSH-Workspace 本身不是工作区，只展示其子文件夹。
        val workspaceDirs = workspaceManager.listWorkspaceDirectories(activeWorkspace)
            .filter { it.absolutePath != activeWorkspace.absolutePath }
        workspaceDirs.mapIndexed { idx, dir ->
            val dirSessions = sessions.filter { s ->
                val wsPath = s.workspacePath
                wsPath != null && (wsPath == dir.absolutePath || wsPath.startsWith(dir.absolutePath))
            }.ifEmpty {
                if (idx == 0) sessions else emptyList()
            }
            com.deepseek.dsh.feature.sessions.WorkspaceFolder(
                id = "ws_${dir.name.hashCode()}",
                name = dir.name.ifEmpty { "主工作区" },
                path = dir.absolutePath,
                sessions = dirSessions.mapIndexed { sIdx, s ->
                    com.deepseek.dsh.feature.sessions.SessionDisplayItem(
                        sessionId = s.sessionId,
                        title = s.title,
                        isRunning = s.sessionId in runningSessionIds,
                        hasUnread = s.sessionId in unreadSessionIds,
                        updatedAt = s.updatedAt,
                        archived = s.archived,
                        timeAgo = run {
                            val elapsed = (System.currentTimeMillis() - s.updatedAt).coerceAtLeast(0L)
                            when {
                                elapsed < 60_000L -> "刚刚"
                                elapsed < 60L * 60 * 1000 -> "${elapsed / 60_000L}分钟"
                                elapsed < 24L * 60 * 60 * 1000 -> "${elapsed / (60 * 60 * 1000)}小时"
                                else -> "${elapsed / (24 * 60 * 60 * 1000)}天"
                            }
                        },
                        isActiveDot = (s.sessionId == selectedSessionId)
                    )
                },
                isExpanded = idx == 0
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModernNavigationDrawerContent(
                    userName = accountState.profile?.name ?: "DeepSeek 用户",
                    avatarUrl = accountState.profile?.avatarUrl,
                    updateVersion = updateInfo?.tag?.takeIf {
                        com.deepseek.dsh.feature.settings.UpdateCenter.hasUpdate(context)
                    },
                    onUpdateClick = {
                        scope.launch { drawerState.close() }
                        settingsInitialSection = com.deepseek.dsh.feature.settings.SettingsSectionTab.UPDATE
                        activePage = ActivePage.SETTINGS
                    },
                    userTag = if (accountState.isSignedIn) "DeepSeek 官方" else "未认证",
                    accountBalance = accountState.wallets.firstOrNull()?.let { formatAmount(it.balance) } ?: "¥0.00",
                    isSignedIn = accountState.isSignedIn,
                    onSignIn = { showSignInDialog = true },
                    onSignOut = hostSignOut,
                    workspaces = workspaceFolders,
                    selectedWorkspaceName = activeWorkspaceName,
                    onSelectWorkspace = { folder -> activeWorkspaceName = folder.name },
                    selectedSessionId = selectedSessionId,
                    onSelectSession = { sessionId ->
                        selectedSessionId = sessionId
                        activePage = ActivePage.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onNewChatClick = {
                        // 官方行为：新会话在发出第一条消息前不创建 Host 会话；
                        // 离开后自然消失，不会留下空白会话。
                        selectedSessionId = null
                        activePage = ActivePage.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onNavigatePlugins = {
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.PLUGINS
                        }
                    },
                    onNavigateSchedule = {
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.AUTOMATION
                        }
                    },
                    onNavigateKanban = {
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.TASKBOARD
                        }
                    },
                    onNavigateRuntime = {
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.RUNTIME
                        }
                    },
                    onNavigateFiles = {
                        scope.launch {
                            drawerState.close()
                            activePage = ActivePage.FILES
                        }
                    },
                    onAddWorkspace = {
                        newWorkspaceName = ""
                        showAddWorkspaceDialog = true
                    },
                    onRenameWorkspace = { folder ->
                        renameWorkspaceFolder = folder
                        renameWorkspaceName = folder.name
                    },
                    onDeleteWorkspace = { folder ->
                        deleteWorkspaceFolder = folder
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
            // 对话页使用完整顶栏；设置页自带标题栏（不再叠加）；其余全屏页只保留返回条，
            // 避免出现双 header。
            BackHandler(enabled = activePage != ActivePage.CHAT) {
                activePage = ActivePage.CHAT
            }
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
            val chatPage = activePage == ActivePage.CHAT
            val pageHasOwnHeader = activePage == ActivePage.SETTINGS
            Scaffold(
                topBar = {
                    Column {
                        when {
                            chatPage -> ModernTopAppBar(
                                title = if (messages.isEmpty()) {
                                    ""
                                } else {
                                    sessions.find { it.sessionId == selectedSessionId }?.title
                                        ?: stringResource(R.string.app_name)
                                },
                                showModeCapsule = false,
                                teamTasks = teamTasks,
                                onMenuClick = { scope.launch { drawerState.open() } },
                            )

                            !pageHasOwnHeader -> PageBackBar(
                                title = pageTitleOf(activePage),
                                onBack = { activePage = ActivePage.CHAT },
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .then(if (chatPage || pageHasOwnHeader) Modifier else Modifier)
                ) {
                    // 更新提示属于页面内容，不属于顶栏：放进内容区顶部，随内容滚动，
                    // 不再压住页面自己的标题栏。
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (rootfsUpdateAvailable && !rootfsUpdateBannerDismissed) {
                            RootfsUpdateBanner(
                                installed = installedRootfsVersion.orEmpty(),
                                available = availableRootfsVersion.orEmpty(),
                                onUpdate = { showRootfsUpdatePrompt = true },
                                onDismiss = { rootfsUpdateBannerDismissed = true },
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                    when (activePage) {
                        ActivePage.CHAT -> {
                            val dockPlugins = catalog.surfaces.filter { it.kind == SurfaceKind.DOCK }
                            var reasoningLevel by remember {
                                mutableStateOf(
                                    runCatching {
                                        com.deepseek.dsh.feature.chat.ReasoningLevel.valueOf(currentAppSettings.defaultReasoningLevel)
                                    }.getOrDefault(com.deepseek.dsh.feature.chat.ReasoningLevel.FAST)
                                )
                            }

                            val configuredModel = activeSession?.model ?: currentAppSettings.defaultModel
                            val modelPrefs = remember {
                                context.getSharedPreferences("dsh_model_prefs", android.content.Context.MODE_PRIVATE)
                            }
                            var lastUsedModel by remember {
                                mutableStateOf(modelPrefs.getString("last_model_name", "").orEmpty())
                            }
                            val catalogDefaultName = hostModels
                                .firstOrNull { it.id == catalogInfo.defaultModel }?.name
                            val currentModel = when {
                                // Host 目录尚未返回时，先用上次使用的模型占位，避免闪“请选择模型”。
                                availableModelNames.isEmpty() && lastUsedModel.isNotBlank() -> lastUsedModel
                                availableModelNames.isEmpty() -> ""
                                lastUsedModel in availableModelNames -> lastUsedModel
                                configuredModel in availableModelNames -> configuredModel
                                // 首次进入：选 Host 目录的默认/最新模型（当前为 DeepSeek-V41-Flash）。
                                else -> catalogDefaultName ?: availableModelNames.first()
                            }
                            LaunchedEffect(currentModel, availableModelNames) {
                                if (currentModel in availableModelNames && currentModel != lastUsedModel) {
                                    lastUsedModel = currentModel
                                    modelPrefs.edit().putString("last_model_name", currentModel).apply()
                                }
                            }
                            val currentProviderGroup = catalogInfo.groups.firstOrNull { group ->
                                group.models.any { it.name == currentModel }
                            }
                            val currentModelMeta = currentProviderGroup?.models?.firstOrNull { it.name == currentModel }
                            val backendEfforts = currentModelMeta?.reasoningEfforts?.map { effort ->
                                com.deepseek.dsh.feature.chat.ReasoningOption(effort.id, effort.name)
                            } ?: emptyList()
                            var selectedReasoningId by remember(currentModel) {
                                mutableStateOf(
                                    currentModelMeta?.defaultReasoningEffort
                                        ?: backendEfforts.firstOrNull()?.id.orEmpty()
                                )
                            }
                            LaunchedEffect(currentModel, currentModelMeta) {
                                if (backendEfforts.none { it.id == selectedReasoningId }) {
                                    selectedReasoningId = currentModelMeta?.defaultReasoningEffort
                                        ?: backendEfforts.firstOrNull()?.id.orEmpty()
                                }
                            }

                            val effortLabelText = backendEfforts
                                .firstOrNull { it.id == selectedReasoningId }?.label
                                ?: stringResource(reasoningLevel.labelResId)
                            val currentPermission = remember(activeSession?.permissionPreset) {
                                when (activeSession?.permissionPreset) {
                                    "read_only" -> com.deepseek.dsh.feature.chat.PermissionPreset.READ_ONLY
                                    "danger_full_access" -> com.deepseek.dsh.feature.chat.PermissionPreset.FULL_ACCESS
                                    "auto_review" -> com.deepseek.dsh.feature.chat.PermissionPreset.AUTO_REVIEW
                                    else -> com.deepseek.dsh.feature.chat.PermissionPreset.WORKSPACE_WRITE
                                }
                            }

                            ModernChatScreen(
                                messages = messages + pendingMessages,
                                onAnswerUserQuestion = { callId, questionId, selected ->
                                    val sid = selectedSessionId
                                    if (sid != null) {
                                        scope.launch {
                                            dshRepository.answerUserQuestion(
                                                activeHostId, sid, callId, questionId, selected
                                            )
                                        }
                                    }
                                },
                                onSendMessage = { text ->
                                    println("DSH_SEND start text=$text selected=$selectedSessionId live=$liveSessionIds")
                                    val localId = "local_${System.nanoTime()}"
                                    pendingMessages.add(
                                        ChatMessage(
                                            hostId = activeHostId,
                                            sessionId = selectedSessionId ?: "pending",
                                            messageId = localId,
                                            role = MessageRole.USER,
                                            content = text,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                    isSending = true
                                    scope.launch {
                                        try {
                                        var sid = selectedSessionId
                                        if (sid == null || sid !in liveSessionIds) {
                                            suspend fun createOnce(): String? = dshRepository.createSession(
                                                activeHostId,
                                                text.take(24),
                                                activeWorkspaceCwd,
                                                (pendingAgentPreset ?: currentAppSettings.transcriptView).takeIf { it.isNotBlank() }
                                            ).getOrNull()?.sessionId
                                            sid = createOnce()
                                            if (sid == null) {
                                                // Host 可能刚崩溃/尚未就绪：重启一次再重试。
                                                runtimeManager.start(activeWorkspace.absolutePath)
                                                kotlinx.coroutines.delay(10000)
                                                sid = createOnce()
                                            }
                                            if (sid != null) {
                                                // proot 下 Host 的 mkdir 可能返回 ENOENT；先在 Android 侧建好会话目录。
                                                java.io.File(
                                                    context.filesDir,
                                                    "rootfs/root/.dsh/sessions/--workspace--/$sid"
                                                ).mkdirs()
                                                selectedSessionId = sid
                                            }
                                        }
                                        if (sid != null) {
                                            println("DSH_SEND session=$sid")
                                            dshRepository.selectModel(
                                                activeHostId,
                                                sid,
                                                effectiveModelIdByName[currentModel] ?: currentModel,
                                                if (accountState.hasApiKey) "deepseek-official" else "deepseek-account"
                                            )
                                            dshRepository.sendMessage(activeHostId, sid, text)
                                        } else {
                                            android.widget.Toast.makeText(
                                                context, "创建会话失败，请检查 Host 运行状态", android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        } finally {
                                            pendingMessages.removeAll { it.messageId == localId }
                                            isSending = false
                                        }
                                    }
                                },
                                isSending = isSending,
                                workspaceName = activeWorkspaceName,
                                // 工作区列表来自 Host；选中即切换当前工作区，末行开新建对话框。
                                workspaceNames = workspaceFolders.map { it.name },
                                onWorkspaceSelected = { name -> activeWorkspaceName = name },
                                onAddWorkspace = { newWorkspaceName = ""; showAddWorkspaceDialog = true },
                                onWorkspaceClick = { scope.launch { drawerState.open() } },
                                isThinking = selectedSessionId != null && selectedSessionId in runningSessionIds,
                                onStopClick = {
                                    val sid = selectedSessionId
                                    if (sid != null) {
                                        scope.launch { dshRepository.cancelSession(activeHostId, sid) }
                                    }
                                },
                                selectedModel = currentModel,
                                availableModels = availableModelNames,
                                agentPresetLabel = pluginSnapshot?.agentPresets
                                    ?.firstOrNull { it.id == (pendingAgentPreset ?: currentAppSettings.transcriptView) }
                                    ?.let { group -> group.name.takeIf { it.isNotBlank() && it != group.id } }
                                    ?: when (pendingAgentPreset ?: currentAppSettings.transcriptView) {
                                        "cordis" -> "创造模式"
                                        "minimal" -> "极简模式"
                                        "ptc" -> "PTC 模式"
                                        else -> "标准模式"
                                    },                                modelGroups = catalogInfo.groups,
                                onModelSelected = { model ->
                                    selectedSessionId?.let { sid ->
                                        scope.launch {
                                            dshRepository.selectModel(
                                                activeHostId,
                                                sid,
                                                effectiveModelIdByName[model] ?: model,
                                                if (accountState.hasApiKey) "deepseek-official" else "deepseek-account"
                                            )
                                        }
                                    }
                                },
                                selectedPermission = currentPermission,
                                onPermissionSelected = { preset ->
                                    val presetStr = when (preset) {
                                        com.deepseek.dsh.feature.chat.PermissionPreset.READ_ONLY -> "read_only"
                                        com.deepseek.dsh.feature.chat.PermissionPreset.FULL_ACCESS -> "danger_full_access"
                                        com.deepseek.dsh.feature.chat.PermissionPreset.AUTO_REVIEW -> "auto_review"
                                        else -> "workspace_write"
                                    }
                                    selectedSessionId?.let { sid ->
                                        scope.launch {
                                            dshRepository.selectPermissionPreset(activeHostId, sid, presetStr)
                                        }
                                    }
                                },
                                reasoningLevel = reasoningLevel,
                                reasoningOptions = backendEfforts,
                                selectedReasoningId = selectedReasoningId,
                                effortLabelOverride = effortLabelText,
                                onReasoningSelected = { option ->
                                    selectedReasoningId = option.id
                                    scope.launch {
                                        settingsRepository.updateSettings { current ->
                                            current.copy(defaultReasoningLevel = option.id)
                                        }
                                        selectedSessionId?.let { sid ->
                                            dshRepository.selectModel(
                                                activeHostId,
                                                sid,
                                                effectiveModelIdByName[currentModel] ?: currentModel,
                                                currentProviderGroup?.id,
                                                option.id
                                            )
                                        }
                                    }
                                },
                                onReasoningLevelChange = { level ->
                                    reasoningLevel = level
                                    scope.launch {
                                        settingsRepository.updateSettings { it.copy(defaultReasoningLevel = level.name) }
                                    }
                                },
                                dockPluginSurfaces = dockPlugins,
                                onDockPluginClick = { pluginItem ->
                                    activeSheetSurface = pluginItem
                                },
                                stats = currentSessionStats,
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
                            PluginsScreen(
                                surfaces = catalog.surfaces,
                                pluginManagerSnapshot = pluginManagerSnapshot,
                                pluginRows = pluginRows,
                                bridgeCatalog = bridgePluginCatalog,
                                bridgeWarnings = bridgeWarnings,
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
                                    scope.launch {
                                        val hostRes = dshRepository.installPlugin(activeHostId, pkgName)
                                        if (hostRes.isSuccess) {
                                            android.widget.Toast.makeText(context, "插件安装成功", android.widget.Toast.LENGTH_SHORT).show()
                                            dshRepository.refreshPluginInventory(activeHostId)
                                        } else {
                                            val errMsg = hostRes.exceptionOrNull()?.message ?: "安装失败"
                                            android.widget.Toast.makeText(context, "插件安装失败: $errMsg", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onToggleBundle = { packageName, enabled ->
                                    scope.launch {
                                        dshRepository.setBundleEnabled(activeHostId, packageName, enabled)
                                        // 双向同步：开关值以 Host 的实际状态为准。成功时
                                        // 这只是确认；失败时它会把开关拨回真实状态，而不是
                                        // 让界面停在用户按下的那个位置。
                                        dshRepository.refreshBridgePluginCatalog()
                                        dshRepository.refreshPluginInventory(activeHostId)
                                    }
                                },
                                onUninstall = { item ->
                                    scope.launch {
                                        dshRepository.uninstallPlugin(activeHostId, item.pluginId)
                                        surfaceStore.removeSurfaceItem(item.pluginId, item.slot)
                                    }
                                }
                            ,
                                onCreatePlugin = {
                                    pendingAgentPreset = "cordis"
                                    selectedSessionId = null
                                    activePage = ActivePage.CHAT
                                }
                            )
                        }

                        ActivePage.AUTOMATION -> {
                            AutomationTasksScreen(
                                tasks = automationTasks,
                                onCreateTask = {
                                    activePage = ActivePage.CHAT
                                }
                            )
                        }

                        ActivePage.TASKBOARD -> {
                            TaskBoardScreen(
                                tasks = teamTasks
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
                                    scope.launch {
                                        dshRepository.respondApproval(activeHostId, item.id, true)
                                    }
                                },
                                onDeny = { item ->
                                    scope.launch {
                                        dshRepository.respondApproval(activeHostId, item.id, false)
                                    }
                                }
                            )
                        }

                        ActivePage.SETTINGS -> {
                            com.deepseek.dsh.feature.settings.SettingsRootScreen(
                                settings = currentAppSettings,
                                initialSection = settingsInitialSection,
                                onUpdateSettings = { updated ->
                                    currentAppSettings = updated
                                    scope.launch {
                                        settingsRepository.updateSettings { updated }
                                    }
                                },
                                availableModels = availableModelNames,
                                modelProvidersRaw = modelProvidersRaw,
                                modelSettingsRaw = modelSettingsRaw,
                                modelCredentialsRaw = modelCredentialsRaw,
                                onSaveModelProvider = { provider, key, baseUrl, models ->
                                    scope.launch {
                                        if (key.isNotBlank()) {
                                            val credArgs = org.json.JSONObject()
                                                .put("ref", provider.keyRef)
                                                .put("value", key)
                                                .toString()
                                            dshRepository.rpcRaw(activeHostId, "credentials/set", credArgs)
                                        }
                                        if (provider.settingsNs.isNotBlank()) {
                                            val ops = org.json.JSONArray()
                                            if (key.isNotBlank()) {
                                                ops.put(org.json.JSONObject()
                                                    .put("op", "set")
                                                    .put("path", org.json.JSONArray(provider.settingsPath + "apiKeyEnv"))
                                                    .put("value", provider.keyRef))
                                            }
                                            if (baseUrl.isNotBlank()) {
                                                ops.put(org.json.JSONObject()
                                                    .put("op", "set")
                                                    .put("path", org.json.JSONArray(provider.settingsPath + "baseURL"))
                                                    .put("value", baseUrl))
                                            }
                                            if (models.isNotEmpty()) {
                                                val modelArray = org.json.JSONArray()
                                                models.forEach { model ->
                                                    modelArray.put(org.json.JSONObject().put("id", model).put("name", model))
                                                }
                                                ops.put(org.json.JSONObject()
                                                    .put("op", "set")
                                                    .put("path", org.json.JSONArray(provider.settingsPath + "models"))
                                                    .put("value", modelArray))
                                            }
                                            val args = org.json.JSONObject()
                                                .put("ns", provider.settingsNs)
                                                .put("ops", ops)
                                                .put("expectedRevision", org.json.JSONObject.NULL)
                                                .toString()
                                            dshRepository.rpcRaw(activeHostId, "settings/mutate", args)
                                        }
                                        modelSettingsRaw = dshRepository
                                            .rpcRaw(activeHostId, "settings/describe", "{}")
                                            .getOrNull()
                                        val refsJson = "{\"refs\":[\"${provider.keyRef}\"]}"
                                        modelCredentialsRaw = dshRepository
                                            .rpcRaw(activeHostId, "credentials/describe", refsJson)
                                            .getOrNull()
                                        dshRepository.refreshModelCatalog(activeHostId)
                                    }
                                },                                builtInPlugins = builtInPluginsList,

                                runtimeStatus = status,
                                onStartRuntime = {
                                    scope.launch {
                                        runtimeManager.start(activeWorkspace.absolutePath)
                                    }
                                },
                                onStopRuntime = {
                                    scope.launch {
                                        runtimeManager.stop()
                                    }
                                },
                                onOpenSettingsDocument = {
                                    scope.launch {
                                        dshRepository.openSettingsDocument(activeHostId)
                                    }
                                },
                                accountName = accountState.profile?.name ?: "DeepSeek 用户",
                                accountBalance = accountState.wallets.firstOrNull()?.let { formatAmount(it.balance) } ?: "¥0.00",
                                accountPhone = accountState.profile?.contact ?: "",
                                isSignedIn = accountState.isSignedIn,
                                avatarUrl = accountState.profile?.avatarUrl,
                                onSignIn = { showSignInDialog = true },
                                onSignOut = hostSignOut,
                                onManageAccount = {
                                    openUrl(accountState.platformUrl)
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
                                },
                                onClose = {
                                    activePage = ActivePage.CHAT
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
            }
        }

        // 插件 Surface Sheet 呈现 (v4.0 §7.5)
        activeSheetSurface?.let { item ->
            SurfaceSheet(
                item = item,
                hostPort = status.port ?: 8080,
                token = status.token,
                onDismissRequest = { activeSheetSurface = null }
            )
        }

        // 插件单例悬浮窗 (v4.0 §7.7)
        activeOverlaySurface?.let { item ->
            PluginOverlayContainer(
                item = item,
                hostPort = status.port ?: 8080,
                token = status.token,
                onClose = { activeOverlaySurface = null }
            )
        }

        // 官方登录对话框：浏览器 OAuth 为主，API Key 为备选。
        if (showSignInDialog) {
            com.deepseek.dsh.core.designsystem.DshDialog(
                onDismiss = { showSignInDialog = false },
                title = "开始使用",
                closeLabel = "关闭",
                description = "登录 DeepSeek 账号，或添加 API Key，即可开始使用。你的项目和文件保存在本地。",
                footer = {
                    com.deepseek.dsh.core.designsystem.DshDialogButton(
                        text = "添加 API Key",
                        primary = false,
                        enabled = true,
                        onClick = {
                            showSignInDialog = false
                            showApiKeyDialog = true
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.deepseek.dsh.core.designsystem.DshDialogButton(
                        text = "登录",
                        primary = true,
                        enabled = true,
                        onClick = {
                            showSignInDialog = false
                            beginHostSignIn()
                        },
                    )
                },
            )
        }

        if (showApiKeyDialog) {
            com.deepseek.dsh.core.designsystem.DshDialog(
                onDismiss = { showApiKeyDialog = false },
                title = "添加一个 API Key 开始使用",
                closeLabel = "关闭",
                description = "配置 DeepSeek 官方模型，即可开始使用。",
                footer = {
                    com.deepseek.dsh.core.designsystem.DshDialogButton(
                        text = "取消",
                        primary = false,
                        enabled = true,
                        onClick = { showApiKeyDialog = false },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.deepseek.dsh.core.designsystem.DshDialogButton(
                        text = "登录",
                        primary = true,
                        enabled = apiKeyInput.isNotBlank(),
                        onClick = {
                            oauthManager.signInWithApiKey(apiKeyInput)
                            apiKeyInput = ""
                            showApiKeyDialog = false
                        },
                    )
                },
                content = {
                    com.deepseek.dsh.core.designsystem.DshTextInput(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = "输入 API 密钥",
                    )
                },
            )
        }
    }

}

/** 首次启动的强制权限门：未授权时不允许进入任何下一步。 */

/** 重新打开 App 时的 Host 启动页：等本地运行时就绪后再进入主界面。 */
@Composable
private fun HostStartupSplash(
    phase: RuntimePhase,
    title: String = "",
    onRetry: () -> Unit,
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.deepseek.dsh.core.designsystem.R.drawable.ic_dsh_fish_logo
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = when {
                    phase == RuntimePhase.ERROR -> "本地 Host 启动失败"
                    title.isNotBlank() -> title
                    else -> "正在启动本地 Host…"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (phase == RuntimePhase.ERROR) {
                androidx.compose.material3.Button(onClick = onRetry) { Text("重试") }
            } else {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
@Composable
private fun StoragePermissionGate(onRequest: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = DshIcons.ShieldOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "需要文件访问权限",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "DSH 的工作区位于 Documents/DSH-Workspace，必须授予「所有文件访问权限」才能读写文件并继续使用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            androidx.compose.material3.Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("去开启权限")
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val workspaceDir = remember { java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "DSH-Workspace") }
    var installProgress by remember { mutableStateOf<Pair<Float, String>?>(null) }
    var isInstalling by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(20.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.runtime_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 阶段状态徽章
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (status.phase) {
                                RuntimePhase.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                                RuntimePhase.ERROR -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.runtime_phase, status.phase),
                        color = when (status.phase) {
                            RuntimePhase.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                            RuntimePhase.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.runtime_port, status.port?.toString() ?: "--"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.runtime_token, status.token?.take(10)?.plus("...") ?: "--"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                status.pid?.let {
                    Text(
                        text = "PID: $it",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                status.lastError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "错误: $it",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                installProgress?.let { (progress, msg) ->
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (status.phase != RuntimePhase.RUNNING && status.phase != RuntimePhase.STARTING) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isInstalling = true
                                    val result = runtimeManager.installRootfs { p, msg ->
                                        installProgress = p to msg
                                    }
                                    isInstalling = false
                                    result.exceptionOrNull()?.let { error ->
                                        installProgress = 0f to "安装失败：${error.message}"
                                    }
                                }
                            },
                            enabled = !isInstalling
                        ) {
                            Text(if (isInstalling) "安装中..." else "安装/重置 Rootfs")
                        }
                    }

                    if (status.phase == RuntimePhase.STOPPED || status.phase == RuntimePhase.UNINSTALLED || status.phase == RuntimePhase.ERROR) {
                        androidx.compose.material3.Button(
                            onClick = {
                                scope.launch {
                                    val result = runtimeManager.start(workspaceDir.absolutePath)
                                    result.exceptionOrNull()?.let { error ->
                                        installProgress = 0f to "启动失败：${error.message}"
                                    }
                                }
                            },
                            enabled = !isInstalling && status.phase != RuntimePhase.STARTING
                        ) {
                            Text(stringResource(R.string.action_start_container))
                        }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = {
                                scope.launch {
                                    runtimeManager.stop()
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.action_stop_container))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 现代轻量顶栏组件（参考截图 1, 2, 3）
 */
/**
 * 全屏子页面的顶栏，与设置页一致：左侧返回、标题居中、右侧留空。
 *
 * 手势返回由外层 `BackHandler` 提供，所以这个按钮只是同一动作的可见入口。
 */
@Composable
fun PageBackBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 与左侧按钮等宽，标题才会真正居中。
        Spacer(modifier = Modifier.size(40.dp))
    }
}

/** 子页面顶栏标题；与抽屉里的条目文案保持一致。 */
private fun pageTitleOf(page: ActivePage): String = when (page) {
    ActivePage.PLUGINS -> "插件"
    ActivePage.AUTOMATION -> "自动化任务"
    ActivePage.TASKBOARD -> "任务看板"
    ActivePage.FILES -> "文件"
    ActivePage.HOSTS -> "远程连接"
    ActivePage.APPROVAL -> "授权请求"
    ActivePage.RUNTIME -> "运行时"
    ActivePage.SDUI_SAMPLE -> "界面示例"
    ActivePage.SETTINGS -> "设置"
    ActivePage.CHAT -> "deepseek"
}
@Composable
private fun HeaderChip(
    label: String,
    iconResId: Int,
    trailingChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = com.deepseek.dsh.core.designsystem.dshPanelColors()
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = iconResId),
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.labelSecondary,
            maxLines = 1
        )
        if (trailingChevron) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.deepseek.dsh.core.designsystem.R.drawable.ic_chevron_down
                ),
                contentDescription = null,
                tint = colors.labelCaption,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun HeaderTeamPanel(
    members: List<Pair<String, Boolean>>,
    tasks: List<com.deepseek.dsh.core.model.TeamTaskItem>,
) {
    com.deepseek.dsh.core.designsystem.DshMenuSectionLabel("成员 ${members.size}")
    members.take(8).forEach { (name, running) ->
        com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
            com.deepseek.dsh.core.designsystem.DshMenuLabel(name, Modifier.weight(1f))
            Text(
                text = if (running) "运行中" else "未运行",
                fontSize = 11.sp,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
    com.deepseek.dsh.core.designsystem.DshMenuSectionLabel("共享任务 ${tasks.size}")
    tasks.take(8).forEach { task ->
        com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(task.subject, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
                Text(
                    text = "${task.ownerName ?: "未分配"} · ${when (task.status) {
                        "in_progress" -> "进行中"
                        "completed" -> "已完成"
                        else -> "待处理"
                    }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun HeaderJobsPanel(tasks: List<com.deepseek.dsh.core.model.TeamTaskItem>) {
    val running = tasks.filter { it.status == "in_progress" }
    val settled = tasks.filter { it.status != "in_progress" }
    if (running.isNotEmpty()) {
        com.deepseek.dsh.core.designsystem.DshMenuSectionLabel("进行中")
        running.forEach { task ->
            com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(task.subject, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
                    Text(task.ownerName ?: "lead", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
    if (settled.isNotEmpty()) {
        com.deepseek.dsh.core.designsystem.DshMenuSectionLabel("已结束 ${settled.size}")
        settled.take(8).forEach { task ->
            com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
                Text(task.subject, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
            }
        }
    }
}
@Composable
private fun RootfsUpdateBanner(
    installed: String,
    available: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = com.deepseek.dsh.core.designsystem.dshPanelColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgModulePlatform)
            .clickable(onClick = onUpdate)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                id = com.deepseek.dsh.core.designsystem.R.drawable.ic_stat_gauge
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "运行时更新可用",
                fontSize = 13.sp,
                color = colors.labelPrimary
            )
            Text(
                text = "$installed → $available",
                fontSize = 11.sp,
                color = colors.labelTertiary
            )
        }
        Text(
            text = "更新",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                id = com.deepseek.dsh.core.designsystem.R.drawable.ic_close
            ),
            contentDescription = "关闭提醒",
            tint = colors.labelTertiary,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDismiss)
        )
    }
}
@Composable
fun ModernTopAppBar(
    title: String,
    showModeCapsule: Boolean = false,
    teamTasks: List<com.deepseek.dsh.core.model.TeamTaskItem> = emptyList(),
    onMenuClick: () -> Unit
) {
    val barColors = com.deepseek.dsh.core.designsystem.dshPanelColors()
    val members = remember(teamTasks) {
        buildList {
            add("lead" to teamTasks.any { it.ownerName == "lead" && it.status == "in_progress" })
            teamTasks.mapNotNull { it.ownerName }
                .filter { it != "lead" }
                .distinct()
                .forEach { name ->
                    add(name to teamTasks.any { it.ownerName == name && it.status == "in_progress" })
                }
        }
    }
    val runningTasks = teamTasks.count { it.status == "in_progress" }
    var teamPanelOpen by remember { mutableStateOf(false) }
    var jobsPanelOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧汉堡菜单圆形按键
            Surface(
                shape = CircleShape,
                color = barColors.bgModulePlatform,
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = DshIcons.PanelLeftOutline,
                        contentDescription = "Menu",
                        tint = barColors.labelPrimary,
                        modifier = Modifier.size(18.dp)
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
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = title,
                    color = barColors.labelPrimary,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // 右侧不再放置播报与编辑按钮，保持官方标题栏的单菜单入口。
        }
        // header 的 0.5dp border-l3 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(barColors.borderL3)
        )
    }
}










@Composable
private fun RootfsSetupScreen(
    progress: Float,
    message: String,
    error: String?,
    onRetry: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "正在准备本地 Linux 运行时",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "首次启动需要下载并解压 rootfs，完成后才能继续设置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "准备失败：$error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                androidx.compose.material3.Button(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}

private fun formatAmount(raw: String): String {
    val clean = raw.removePrefix("¥").trim()
    val num = clean.toDoubleOrNull() ?: return "¥$raw"
    return String.format(java.util.Locale.US, "¥%.2f", num)
}

/** 从 workspace/create|rename 的 response 中取 workspaceId。 */
private fun parseWorkspaceId(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return Regex("\"workspaceId\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
}





