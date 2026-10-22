package com.deepseek.dsh

import com.deepseek.dsh.core.designsystem.DshIcons
import kotlinx.serialization.builtins.ListSerializer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
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
import com.deepseek.dsh.core.model.isThirdPartyPlugin
import com.deepseek.dsh.core.model.SurfaceKind
import com.deepseek.dsh.feature.chat.ModernChatScreen
import com.deepseek.dsh.feature.files.FilesScreen
import com.deepseek.dsh.feature.files.WorkspaceManager
import com.deepseek.dsh.feature.sessions.ModernNavigationDrawerContent
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

    /** 原生伴侣插件重扫信号：onResume 时递增，组合里据此重扫（卸载/安装即更新）。 */
    private val nativePluginRescanFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    private var nativePackageReceiver: android.content.BroadcastReceiver? = null

    override fun onResume() {
        super.onResume()
        nativePluginRescanFlow.value += 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        workspaceManager = WorkspaceManager(
            preferredPublicDir = visibleWorkspaceDir,
            appPrivateDir = filesDir
        )
        nativePackageReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                nativePluginRescanFlow.value += 1
            }
        }
        val packageFilter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            nativePackageReceiver,
            packageFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // 初始状态不预置任何插件；插件列表完全由 Host/用户安装产生。
        setContent {

            // 原生插件宿主：能力桥 + 主题快照 provide 给所有落点容器。
            // host 为 null 时（bridge 未连）各落点不渲染，页面照常。
            val nativeTheme = com.deepseek.dsh.plugin.nativehost.rememberPluginTheme()
            val nativeHost = com.deepseek.dsh.plugin.nativehost.rememberNativeHostApi(
                dispatcher = { componentId, event, payload ->
                    app.dshRepository.bridge()?.dispatchUiEvent(componentId, event, payload)
                },
            )
            androidx.compose.runtime.CompositionLocalProvider(
                com.deepseek.dsh.plugin.nativehost.NativeHost.LocalHost provides nativeHost,
                com.deepseek.dsh.plugin.nativehost.NativeHost.LocalTheme provides nativeTheme,
            ) {
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
                    settingsRepository = app.settingsRepository,
                    nativePluginRescanFlow = nativePluginRescanFlow,
                )
            }
            }
        }
    }

    override fun onDestroy() {
        nativePackageReceiver?.let { runCatching { unregisterReceiver(it) } }
        nativePackageReceiver = null
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
    HOSTS,
    APPROVAL,
    SETTINGS,
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
    settingsRepository: com.deepseek.dsh.core.data.DefaultSettingsRepository,
    /** 原生伴侣插件重扫信号（onResume 递增）；卸载/安装后回前台即重扫。 */
    nativePluginRescanFlow: kotlinx.coroutines.flow.StateFlow<Int> =
        kotlinx.coroutines.flow.MutableStateFlow(0),
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var activePage by remember { mutableStateOf(ActivePage.CHAT) }
    // 插件详情页打开时，顶栏返回/系统返回先退回插件列表，而不是直接离开插件页。
    var pluginDetailOpen by remember { mutableStateOf(false) }
    var pluginBackSignal by remember { mutableStateOf(0) }
    // 「原生插件」tab 的列表：NativePluginDiscovery 扫描系统里已装的伴侣 APK。
    // 进入插件页时扫一次；卸载/安装由 PackageManager 广播驱动重扫（见 onResume）。
    var nativePluginItems by remember {
        mutableStateOf<List<com.deepseek.dsh.plugin.nativehost.NativePluginItem>>(emptyList())
    }
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
        java.io.File(externalWorkspaceDir, DEFAULT_WORKSPACE_DIR_NAME).mkdirs()
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
    // slot 贡献（composer dock 等）与整页原生页面共用同一个 pluginRpc；在这里
    // 注入给 NativeHost，NativeSlotHost 会把基础 host 升级成 PluginRpcHost。
    val slotPluginRpc: suspend (String, kotlinx.serialization.json.JsonObject) -> Result<String> =
        remember(activeHostId) {
            { method, payload ->
                val first = dshRepository.pluginRpc(activeHostId, method, payload.toString())
                val firstText = first.getOrNull() ?: first.exceptionOrNull()?.message
                if (ensureMissingPluginDir(context, firstText)) {
                    dshRepository.pluginRpc(activeHostId, method, payload.toString())
                } else {
                    first
                }
            }
        }
    com.deepseek.dsh.plugin.nativehost.NativeHost.rpcHandler = slotPluginRpc

    val repoSessions by dshRepository.getSessions(activeHostId).collectAsState(initial = emptyList())
    val sessions = repoSessions

    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var pendingAgentPreset by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val pendingMessages = remember { androidx.compose.runtime.mutableStateListOf<ChatMessage>() }

    // 计划模式：横幅状态；placeholder 与发送文案在 ChatScreen 内切换。
    // 不按会话 id 重置——首个 prompt 会创建会话并改变 selectedSessionId，
    // 按 id 重置会让刚开启的计划模式在第一次发送后神秘关闭。
    var chatPlanMode by remember { mutableStateOf(false) }

    // 系统文件选择器选回的附件（已复制进当前工作区 attachments/）。
    val chatAttachments = remember { androidx.compose.runtime.mutableStateListOf<com.deepseek.dsh.feature.chat.ChatAttachment>() }
    // 切换会话时清空待发附件，避免附件串到另一个会话。
    LaunchedEffect(selectedSessionId) { chatAttachments.clear() }
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
                val title = sessions.find { it.sessionId == selectedSessionId }?.title ?: context.getString(R.string.notify_session)
                DshNotifications.notify(context, context.getString(R.string.notify_done_title), context.getString(R.string.notify_done_body, title))
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
    // provider 分组也缓存：目录请求失败时仍按真实 provider 分组（Claude/Gemini 同属
    // Antigravity），不会退化成按模型名前缀拆出来的假分组。
    var cachedModelGroups by remember {
        mutableStateOf(
            runCatching {
                kotlinx.serialization.json.Json.decodeFromString(
                    ListSerializer(com.deepseek.dsh.core.model.ModelProviderGroup.serializer()),
                    modelCatalogCache.getString("groups", "[]").orEmpty(),
                )
            }.getOrDefault(emptyList())
        )
    }
    LaunchedEffect(catalogInfo.groups) {
        if (catalogInfo.groups.isNotEmpty()) {
            cachedModelGroups = catalogInfo.groups
            runCatching {
                modelCatalogCache.edit().putString(
                    "groups",
                    kotlinx.serialization.json.Json.encodeToString(
                        ListSerializer(com.deepseek.dsh.core.model.ModelProviderGroup.serializer()),
                        catalogInfo.groups,
                    ),
                ).apply()
            }
        }
    }
    // 模型目录改为事件驱动（对齐官方 model picker）：`$events` 里的
    // llm/adapters-updated、settings/document-updated、credentials/*-updated
    // 会触发 DshRepository 重新拉目录，这里不再轮询。
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

    // 录音输入：权限、录音与转写由 App 负责；草稿在 ChatScreen 里，转写结果
    // 作为一次性事件传下去（官方 composer activity 的同样职责划分）。
    val voiceRecorder = remember { com.deepseek.dsh.voice.VoiceRecorder() }
    var voiceRecording by remember { mutableStateOf(false) }
    var voiceSeconds by remember { mutableStateOf(0) }
    var voiceLevel by remember { mutableStateOf(0f) }
    var voiceBusy by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var voiceTranscript by remember { mutableStateOf<String?>(null) }
    val voiceEnabled = remember(dshRepository.bridgePluginCatalogFlow.value) {
        dshRepository.bridgePluginCatalogFlow.value.bundles.any {
            it.name == VoiceInputPluginUi.bundle && it.enabled
        }
    }
    val voicePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceError = null
            voiceSeconds = 0
            voiceRecording = voiceRecorder.start()
            if (!voiceRecording) voiceError = context.getString(R.string.voice_device_unsupported)
        } else {
            voiceError = context.getString(R.string.voice_permission_denied)
        }
    }
    fun startVoice() {
        val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceError = null
            voiceSeconds = 0
            voiceRecording = voiceRecorder.start()
            if (!voiceRecording) voiceError = context.getString(R.string.voice_device_unsupported)
        } else {
            voicePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    fun stopVoice() {
        val wav = voiceRecorder.stop()
        voiceRecording = false
        voiceLevel = 0f
        if (wav == null) {
            voiceError = context.getString(R.string.voice_no_speech)
            return
        }
        voiceBusy = true
        scope.launch {
            val catalog = dshRepository.speechCatalog(activeHostId).getOrNull()
            val provider = catalog?.selected
            if (catalog == null || provider == null) {
                voiceBusy = false
                voiceError = context.getString(R.string.voice_service_unavailable)
                return@launch
            }
            // 本地识别器未就绪时先准备；准备失败要如实告诉用户，
            // 而不是在转写阶段抛出一个笼统的 transcription-failed。
            val phase = provider.preparation?.phase
            if (phase != "ready" && phase != "standby") {
                voiceError = context.getString(R.string.voice_prepare_required)
                voiceBusy = false
                return@launch
            }
            val encoded = android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP)
            dshRepository.speechTranscribe(activeHostId, provider.id, catalog.language, encoded).fold(
                onSuccess = { text ->
                    if (text.isBlank()) {
                        voiceError = context.getString(R.string.voice_no_speech)
                    } else {
                        voiceTranscript = text
                        voiceError = null
                    }
                },
                onFailure = { error ->
                    voiceError = context.getString(R.string.voice_failed, error.message ?: error.toString())
                },
            )
            voiceBusy = false
        }
    }
    fun cancelVoice() {
        voiceRecorder.cancel()
        voiceRecording = false
        voiceLevel = 0f
    }
    DisposableEffect(voiceRecorder) {
        voiceRecorder.onLevel = { level -> voiceLevel = level }
        onDispose {
            voiceRecorder.onLevel = null
            voiceRecorder.cancel()
        }
    }
    // 思考内容翻译（官方 cot-translation 的 reasoning body）：插件启用时给
    // ReasoningCard 提供「查看译文/查看原文」能力。
    val cotTranslationEnabled = remember(dshRepository.bridgePluginCatalogFlow.value) {
        dshRepository.bridgePluginCatalogFlow.value.bundles.any {
            it.name == CotTranslationPluginUi.bundle && it.enabled
        }
    }

    // Claude Code 模组横幅：官方在 conversation.input.dock 画一条 Host 下发的
    // 动态横幅；只有 Claude Code 子智能体插件启用且当前会话存在时订阅。
    val claudeCodeModsEnabled = remember(dshRepository.bridgePluginCatalogFlow.value) {
        dshRepository.bridgePluginCatalogFlow.value.bundles.any {
            it.name == com.deepseek.dsh.feature.chat.ClaudeCodeModsPluginUi.BUNDLE && it.enabled
        }
    }
    var bandSnapshot by remember { mutableStateOf<com.deepseek.dsh.core.data.ClaudeCodeBandSnapshot?>(null) }
    var bandPressing by remember { mutableStateOf(false) }
    LaunchedEffect(activeHostId, selectedSessionId, claudeCodeModsEnabled) {
        bandSnapshot = null
        val sid = selectedSessionId
        if (claudeCodeModsEnabled && sid != null) {
            dshRepository.claudeCodeBand(activeHostId, sid).collect { snapshot -> bandSnapshot = snapshot }
        }
    }

    // 会话数据诊断（开发者工具）：只读展示当前会话收到的原始帧。
    val sessionInspectorEnabled = remember(dshRepository.bridgePluginCatalogFlow.value) {
        dshRepository.bridgePluginCatalogFlow.value.bundles.any {
            it.name == SessionInspectorPluginUi.BUNDLE && it.enabled
        }
    }
    var sessionInspectorOpen by remember { mutableStateOf(false) }
    var rawSessionFrames by remember {
        mutableStateOf<List<com.deepseek.dsh.core.protocol.SessionRawFrame>>(emptyList())
    }
    LaunchedEffect(sessionInspectorOpen, selectedSessionId) {
        while (sessionInspectorOpen) {
            rawSessionFrames = selectedSessionId?.let { dshRepository.rawSessionFrames(it) } ?: emptyList()
            kotlinx.coroutines.delay(1_000)
        }
    }
    /** Host `restartReason` → 官方「请重启 DSH 使更改生效」的具体说明。 */
    fun restartReasonText(reason: String?): String = when (reason) {
        "package-moved" ->
            context.getString(R.string.restart_reason_package_moved)
        "no-hmr" -> context.getString(R.string.restart_reason_no_hmr)
        "host-module" -> context.getString(R.string.restart_reason_host_module)
        "unmanaged-plugin" -> context.getString(R.string.restart_reason_unmanaged_plugin)
        "module-comparison" -> context.getString(R.string.restart_reason_module_comparison)
        else -> context.getString(R.string.restart_reason_generic)
    }
    // 启停插件后 Host 若要求重启或带提示，按官方做法明确告诉用户。
    var pluginChangeNotice by remember { mutableStateOf<List<String>?>(null) }
    var pluginRestartRunning by remember { mutableStateOf(false) }
    var pluginRestartError by remember { mutableStateOf<String?>(null) }
    pluginChangeNotice?.let { lines ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!pluginRestartRunning) {
                    pluginChangeNotice = null
                    pluginRestartError = null
                }
            },
            title = { Text(stringResource(R.string.restart_needed_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.restart_needed_desc),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    lines.forEach { line ->
                        Text(
                            text = line,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    pluginRestartError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !pluginRestartRunning,
                    onClick = {
                        scope.launch {
                            pluginRestartRunning = true
                            pluginRestartError = null
                            val restart = HostRuntimeHolder.restartHost?.invoke()
                                ?: Result.failure(IllegalStateException(context.getString(R.string.host_not_ready)))
                            pluginRestartRunning = false
                            if (restart.isSuccess) {
                                pluginChangeNotice = null
                            } else {
                                pluginRestartError = restart.exceptionOrNull()?.message ?: context.getString(R.string.restart_failed)
                            }
                        }
                    },
                ) {
                    Text(if (pluginRestartRunning) stringResource(R.string.restarting) else stringResource(R.string.restart_now))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !pluginRestartRunning,
                    onClick = {
                        pluginChangeNotice = null
                        pluginRestartError = null
                    },
                ) { Text(stringResource(R.string.restart_later)) }
            },
        )
    }
    if (sessionInspectorOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { sessionInspectorOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SessionInspectorPluginUi.Overlay(
                frames = rawSessionFrames,
                sessionId = selectedSessionId,
                onDismiss = { sessionInspectorOpen = false },
            )
        }
    }

    fun pressClaudeCodeBand(generation: Long, actionId: String) {
        val sid = selectedSessionId ?: return
        scope.launch {
            bandPressing = true
            dshRepository.pressClaudeCodeBand(activeHostId, sid, generation, actionId)
                .onSuccess { snapshot -> if (snapshot != null) bandSnapshot = snapshot }
            bandPressing = false
        }
    }

    LaunchedEffect(voiceRecording) {
        while (voiceRecording) {
            kotlinx.coroutines.delay(1_000)
            voiceSeconds += 1
            // Host 的录音上限是 120 秒（catalog.maxDurationSeconds），到点自动停止。
            if (voiceSeconds >= 120) {
                stopVoice()
                break
            }
        }
    }

    val builtInPluginsList by dshRepository.builtInPluginsFlow.collectAsState()
    val pluginSnapshot by dshRepository.pluginSnapshotFlow.collectAsState()
    val pluginManagerSnapshot by dshRepository.pluginManagerSnapshotFlow.collectAsState()
    val pluginRows by dshRepository.pluginRowsFlow.collectAsState()
    // 插件页目录来自 bridge/v1：官方 slot 注册优先，Host 原生清单兜底。
    val bridgePluginCatalog by dshRepository.bridgePluginCatalogFlow.collectAsState()
    val bridgeWarnings by dshRepository.bridgeWarningsFlow.collectAsState()
    // 插件声明的 UI 入口（settings.section 等 slot 注册）：设置页按落点渲染。
    val pluginSlotEntries by dshRepository.pluginSlotEntriesFlow.collectAsState()
    val pluginSettingsPages by dshRepository.pluginSettingsPagesFlow.collectAsState()
    // Host 声明的行级/页面级设置位置：插件页的「内置插件配置」用它把官方设置页
    // 映射到真正的设置命名空间。
    val pluginSlotSurfaces by dshRepository.pluginSlotSurfacesFlow.collectAsState()

    var modelProvidersRaw by remember { mutableStateOf<String?>(null) }
    var modelSettingsRaw by remember { mutableStateOf<String?>(null) }
    var modelCredentialsRaw by remember { mutableStateOf<String?>(null) }

    val automationTasks by dshRepository.automationTasksFlow.collectAsState()
    val teamTasks by dshRepository.teamTasksFlow.collectAsState()
    val agentTeam by dshRepository.agentTeamFlow.collectAsState()
    var agentTeamLoading by remember { mutableStateOf(false) }
    // 团队动作只在「智能体团队」profile 启用时出现（官方由插件注册 header action）。
    val agentTeamEnabled = remember(bridgePluginCatalog) {
        dshRepository.bridgePluginCatalogFlow.value.bundles.any {
            it.name == AgentTeamPluginUi.BUNDLE && it.enabled
        }
    }
    LaunchedEffect(selectedSessionId, activeHostId, agentTeamEnabled) {
        if (agentTeamEnabled) {
            agentTeamLoading = true
            dshRepository.refreshAgentTeam(activeHostId, selectedSessionId)
            agentTeamLoading = false
        }
    }
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

    LaunchedEffect(activeHostId, selectedSessionId, messages.size) {
        selectedSessionId?.let { sid ->
            dshRepository.refreshSessionStats(activeHostId, sid)
        }
    }

    // 运行中定期刷新底部统计（tok/s、缓存命中、上下文占用），结束后再刷新一次落定。
    LaunchedEffect(activeHostId, selectedSessionId, sessionRunningNow) {
        val sid = selectedSessionId ?: return@LaunchedEffect
        if (sessionRunningNow) {
            while (true) {
                dshRepository.refreshSessionStats(activeHostId, sid)
                kotlinx.coroutines.delay(1200)
            }
        } else {
            dshRepository.refreshSessionStats(activeHostId, sid)
        }
    }

    // 订阅插件额度：composer dock 上方的用量窗口（如 Kimi 5 小时额度）。
    var quotaUsage by remember { mutableStateOf<List<com.deepseek.dsh.feature.chat.ComposerQuota>>(emptyList()) }
    LaunchedEffect(activeHostId, sessionRunningNow) {
        while (true) {
            quotaUsage = parseQuotaWindows(
                dshRepository.pluginRpc(activeHostId, "subscription-oauth.status", "{}").getOrNull(),
                quotaLabel = context.getString(R.string.quota_label),
            )
            kotlinx.coroutines.delay(if (sessionRunningNow) 15_000L else 60_000L)
        }
    }

    // bridge 插件更新的「运行中版本」来源与「重启 Host」动作由 App 层注入：
    // runtime 模块不认识数据层，而重启需要当前工作区路径，只有这里知道。
    // 真正的安装（下载 tag 归档、解压进 rootfs）由 HostRuntimeManager 完成。

    LaunchedEffect(activeHostId) {
        dshRepository.refreshSessions(activeHostId)
        dshRepository.refreshModelCatalog(activeHostId)
        dshRepository.refreshPluginInventory(activeHostId)
        // bridge 目录与 Host 原生清单并行读取：前者带来官方 slot 与图形，
        // 后者是它不可用时的兜底，两者互不阻塞。
        dshRepository.refreshBridgePluginCatalog()
        dshRepository.refreshPluginSettingsPages()
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
    // 「添加插件」的 App 侧兜底安装器：Host 内的 pnpm 在 proot 下拿不到
    // rename/unlink/mkdtemp（ENOSYS），所以 Host 安装失败后由 App 自己下载、
    // 解压、装依赖并挂到 profile 的 patch 层。
    val appPluginInstaller = remember(layout) {
        com.deepseek.dsh.runtime.host.PluginBundleInstaller(context, layout)
    }
    // App 安装器挂过的包名：用来区分「随安装自带」的官方插件和用户后来装的。
    var managedPluginPackages by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(appPluginInstaller) {
        managedPluginPackages = runCatching { appPluginInstaller.managedPackages() }
            .getOrDefault(emptySet())
    }

    // 待卸载的伴侣 APK。
    //
    // 主 JS 插件卸载时要连带卸载配对的 APK，但 Android 的卸载确认框一次只能显示
    // 一个：这里排队，每次回到前台（onResume 递增重扫信号）再发起下一个。
    val pendingApkUninstalls = remember { mutableStateListOf<String>() }
    val apkUninstallTick by nativePluginRescanFlow.collectAsState()
    LaunchedEffect(apkUninstallTick, pendingApkUninstalls.size) {
        if (pendingApkUninstalls.isEmpty()) return@LaunchedEffect
        val next = pendingApkUninstalls.removeAt(0)
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_DELETE,
                    android.net.Uri.parse("package:$next"),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * 把一个伴侣 APK 排进卸载队列。
     * @param packageName Android 包名。
     */
    fun requestApkUninstall(packageName: String?) {
        val target = packageName?.takeIf { it.isNotBlank() } ?: return
        if (target !in pendingApkUninstalls) pendingApkUninstalls.add(target)
    }

    /**
     * 记住用户通过 App 打开/关闭过的实验性 bundle。
     *
     * Device 上的 profile 清单在 Host 启动时会被重建为模板（实测：写入的
     * `dsh.profile.bundles` 在下次启动后消失），因此不能只依赖 profile；
     * 这里留一份本地选择，Host 起来后补回去。
     * @param name bundle 包名。
     * @param enabled 打开还是关闭。
     */
    fun rememberBundleSelection(name: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PLUGIN_SELECTION_PREFS, android.content.Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PLUGIN_SELECTION_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) current.add(name) else current.remove(name)
        prefs.edit().putStringSet(PLUGIN_SELECTION_KEY, current).apply()
    }

    /** 用户打开过的实验性 bundle 包名。 */
    fun savedEnabledBundles(): Set<String> =
        context.getSharedPreferences(PLUGIN_SELECTION_PREFS, android.content.Context.MODE_PRIVATE)
            .getStringSet(PLUGIN_SELECTION_KEY, emptySet())?.toSet() ?: emptySet()

    /**
     * 把某个 JS 插件配对的伴侣 APK 全部排进卸载队列。
     *
     * 配对依据是 APK manifest 的 `dsh.plugin.jsPackage`，不按包名猜。
     * @param jsPackageName JS 插件在 Host 里的模块名。
     */
    fun queueCompanionApkUninstalls(jsPackageName: String) {
        val normalized = jsPackageName.substringAfterLast('/').substringBefore('@')
        nativePluginItems
            .filter { it.installed && it.packageName != null && it.jsPackage != null }
            .filter { item ->
                val declared = item.jsPackage!!
                declared == jsPackageName ||
                    declared.substringAfterLast('/').substringBefore('@') == normalized
            }
            .forEach { item -> requestApkUninstall(item.packageName) }
    }

    // 扫描系统里已装的原生伴侣 APK，填充「原生插件」tab，并把 READY 的插件
    // 装载进 App 进程（触发它们的落点/工具/页面注册）。卸载/安装后回前台时
    // 重扫（onResume 递增信号），保证「卸载即从列表摘除」。装 APK 时连带装
    // 配对的 JS 插件（甲 manifest 声明 / 乙商店索引兜底），装完重启 Host。
    val nativePluginRescanSignal by nativePluginRescanFlow.collectAsState()
    LaunchedEffect(nativePluginRescanSignal, appPluginInstaller) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
            val discovery = com.deepseek.dsh.plugin.nativehost.NativePluginDiscovery(context)
            val loader = com.deepseek.dsh.plugin.nativehost.NativePluginLoader(context)
            val appVersionCode = context.packageManager
                .getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            val discovered = discovery.scan(
                appVersionCode = appVersionCode,
                supportedApiVersion = com.deepseek.dsh.plugin.nativehost.NativeAbi.CURRENT,
                pinnedFingerprints = emptyMap(),
            )
            // 装载 READY 插件进 App 进程：它们的 init 块会跑 SlotRegistry.register 等。
            val ready = discovered.filter {
                it.status == com.deepseek.dsh.plugin.nativehost.InstalledNativePlugin.Status.READY
            }
            // Drop pages whose package disappeared before loading the next snapshot.
            // Otherwise an already open page could keep executing an uninstalled ClassLoader.
            val discoveredIds = discovered.map { it.pluginId }.toSet()
            com.deepseek.dsh.plugin.nativesdk.pages.PageRegistry.snapshot()
                .map { it.first }.filter { it.isNotBlank() && it !in discoveredIds }
                .forEach { com.deepseek.dsh.plugin.nativesdk.pages.PageRegistry.removeOwner(it) }
            // Only successfully loaded companions may replace a Web settings section.
            // Replacing the whole snapshot also drops routes for uninstalled/incompatible APKs.
            val loaded = ready.mapNotNull { installed ->
                com.deepseek.dsh.plugin.nativesdk.pages.PageRegistry.removeOwner(installed.pluginId)
                com.deepseek.dsh.plugin.nativesdk.pages.PageRegistry.withOwner(installed.pluginId) {
                    loader.load(installed)
                }?.let { plugin -> installed to plugin }
            }
            // 注册的是「这个伴侣最后加载出来的全部页面」，不按 placement 预过滤：
            // 匹配设置分区时按 id/标题/一一对应兜底，页面自己带 placement 即可。
            val nativePageRegistrations = loaded.map { (installed, plugin) ->
                com.deepseek.dsh.plugin.nativehost.NativePageRegistration(
                    pluginId = plugin.descriptor.id,
                    jsPackage = installed.jsPackage,
                    pages = com.deepseek.dsh.plugin.nativesdk.pages.PageRegistry.snapshot()
                        .filter { (owner, _) -> owner == installed.pluginId }
                        .map { (_, page) -> page },
                )
            }
            com.deepseek.dsh.plugin.nativehost.NativePageRegistry.replaceAll(nativePageRegistrations)
            // 伴侣「装了但设置页还是 Web UI」时，先看这条日志：哪个伴侣加载成功、
            // jsPackage 是什么、注册了哪些页面（placement:id）。
            android.util.Log.i(
                "NativePluginHost",
                "loaded companions=" + nativePageRegistrations.joinToString { registration ->
                    "${registration.pluginId}/${registration.jsPackage}=" +
                        registration.pages.joinToString { page ->
                            "${page.entry.placement}:${page.entry.id}"
                        }
                },
            )
            ready.forEach { plugin ->
                runCatching {
                    val jsPackage = com.deepseek.dsh.plugin.nativehost.NativePluginPairing
                        .resolveJsPackage(plugin, storeLookup = null)
                    if (jsPackage != null) {
                        val pkgName = jsPackage.substringAfterLast('/').substringBefore('@')
                            .ifBlank { jsPackage }
                        val inProfile = java.io.File(
                            layout.rootfsDir,
                            "root/.dsh/profiles/web/node_modules/$pkgName/package.json",
                        ).isFile
                        val managed = managedPluginPackages.any {
                            it.equals(pkgName, ignoreCase = true) || it.equals(jsPackage, ignoreCase = true)
                        }
                        if (!inProfile && !managed) {
                            val installed = appPluginInstaller.install(jsPackage) { _, _ -> }
                            if (installed.isSuccess) {
                                HostRuntimeHolder.restartHost?.invoke()
                            }
                        }
                    }
                }
            }
            nativePluginItems = discovered.map { installed ->
                com.deepseek.dsh.plugin.nativehost.NativePluginItem(
                    pluginId = installed.pluginId,
                    name = installed.pluginId.substringAfterLast('.'),
                    description = context.getString(R.string.plugin_native_description, installed.packageName),
                    version = installed.version.toString(),
                    capabilities = emptyList(),
                    installed = true,
                    statusLabel = when (installed.status) {
                        com.deepseek.dsh.plugin.nativehost.InstalledNativePlugin.Status.READY -> context.getString(R.string.plugin_status_installed)
                        com.deepseek.dsh.plugin.nativehost.InstalledNativePlugin.Status.ABI_TOO_NEW -> context.getString(R.string.plugin_status_update_plugin)
                        com.deepseek.dsh.plugin.nativehost.InstalledNativePlugin.Status.APP_TOO_OLD -> context.getString(R.string.plugin_status_update_app)
                        com.deepseek.dsh.plugin.nativehost.InstalledNativePlugin.Status.SIGNATURE_MISMATCH -> context.getString(R.string.plugin_status_identity_mismatch)
                        com.deepseek.dsh.plugin.nativehost.InstalledNativePlugin.Status.DISABLED -> context.getString(R.string.plugin_status_disabled)
                    },
                    signatureFingerprint = installed.signatureFingerprint,
                    jsPackage = installed.jsPackage,
                    packageName = installed.packageName,
                    lastUpdateTime = installed.lastUpdateTime,
                )
            }
            }
        }
    }
    var activeSheetSurface by remember { mutableStateOf<SurfaceItem?>(null) }
    var activeOverlaySurface by remember { mutableStateOf<SurfaceItem?>(null) }
    // 反向联动：刚装的 JS 插件有原生伴侣时，这里带上它的声明，UI 提示可装。
    var nativeCompanionAvailable by remember {
        mutableStateOf<com.deepseek.dsh.core.bridge.BridgeNativeCompanion?>(null)
    }
    // 从“JS 已装、伴生 APK 可装”跳转时，直接打开插件商店的对应仓库详情。
    var pluginStoreSeedUrl by remember { mutableStateOf<String?>(null) }
    var pluginInitialTab by remember { mutableStateOf(0) }

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

    // 官方 account/watch：登录中/已存凭据/退出都由 Host 推送，整个登录流程不再轮询。
    var signInPending by remember { mutableStateOf(false) }
    var signInOpenedUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeHostId) {
        kotlinx.coroutines.coroutineScope {
            launch {
                dshRepository.hostAccountFlow.collect { view ->
                    val pending = signInPending
                    when (view?.status) {
                        "credential-stored" -> {
                            signInPending = false
                            signInOpenedUrl = null
                            if (!oauthManager.accountFlow.value.isSignedIn) {
                                val profile = dshRepository.getAccountProfile(activeHostId).getOrNull()
                                val balance = dshRepository.getAccountBalance(activeHostId).getOrNull()
                                oauthManager.applyHostAccount(profile, balance)
                            }
                        }
                        "signed-out" -> {
                            signInPending = false
                            if (oauthManager.accountFlow.value.isSignedIn) oauthManager.signOut()
                        }
                        else -> if (pending && view != null) {
                            // Host 异步拿到平台授权链接后由 watch 推送，不需要 getState 轮询。
                            val url = view.authorizeUrl
                            if (!url.isNullOrBlank() && url != signInOpenedUrl) {
                                signInOpenedUrl = url
                                openUrl(url)
                            }
                            if (view.attemptPhase in setOf("failed", "expired", "cancelled")) {
                                signInPending = false
                                signInOpenedUrl = null
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.login_incomplete),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                }
            }
            launch {
                // Host 明确报告 DeepSeek 会话过期：直接清掉本地登录态（API Key 保留）。
                dshRepository.accountExpiredFlow.collect { oauthManager.signOut() }
            }
        }
    }

    val hostSignOut: () -> Unit = {
        scope.launch {
            dshRepository.signOutAccount(activeHostId)
            oauthManager.signOut()
        }
    }

    // 官方流程：Host 的 account/startSignIn 生成带本次 attempt 的授权链接，
    // 链接与登录结果都由 account/watch 推送；这里只负责发起 + 兜底提示。
    val beginHostSignIn: () -> Unit = {
        scope.launch {
            signInPending = true
            signInOpenedUrl = null
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
                signInPending = false
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.login_failed_host),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            when {
                view.status == "credential-stored" -> signInPending = false
                !view.authorizeUrl.isNullOrBlank() -> {
                    signInOpenedUrl = view.authorizeUrl
                    openUrl(view.authorizeUrl!!)
                }
                else -> {
                    // 链接由 watch 异步推送；30 秒还拿不到就提示失败。
                    kotlinx.coroutines.delay(30_000)
                    if (signInPending && signInOpenedUrl == null) {
                        signInPending = false
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.login_failed_link),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            if (signInPending) {
                // 授权页停留超时：watch 没有报成功/失败时提示一次。
                kotlinx.coroutines.delay(10 * 60_000)
                if (signInPending) {
                    signInPending = false
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.login_incomplete),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
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
            dshRepository.refreshPluginSettingsPages()
            // 账号态由 account/watch 的首个快照推送，这里不再主动 getState。
        }
    }

    // 首次启动先准备 rootfs：未安装/未下载完成之前不进入 onboarding。
    // 已安装过就直接视为就绪，避免重新打开时闪一下安装进度条；首次安装仍走 null→安装。
    var rootfsReady by remember { mutableStateOf<Boolean?>(null) }
    var rootfsSetupProgress by remember { mutableStateOf(0f to context.getString(R.string.runtime_checking)) }
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
            title = stringResource(R.string.runtime_update_title),
            closeLabel = stringResource(R.string.runtime_update_later),
            description = stringResource(R.string.runtime_update_desc, availableRootfsVersion ?: "", installedRootfsVersion ?: ""),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.runtime_update_later),
                    primary = false,
                    enabled = true,
                    onClick = { showRootfsUpdatePrompt = false },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.runtime_update_now),
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
            rootfsSetupError = error.message ?: context.getString(R.string.rootfs_install_failed)
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
            title = stringResource(R.string.runtime_checking),
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

    // 已安装后的每次重新打开：等 Host 真正就绪（markReady / RUNNING）再进 UI。
    // 不能只看 endpoint/port：DSH 打印 Web URL 前端口可能已经监听，此时模型、
    // 插件与会话接口还没准备好，提前进主界面会看到空状态或“未就绪”报错。
    var hostProgressReady by remember { mutableStateOf(false) }
    var hostStartupError by remember { mutableStateOf(false) }
    var hostStartupDetail by remember { mutableStateOf<String?>(null) }
    var hostRetryKey by remember { mutableStateOf(0) }
    LaunchedEffect(rootfsReady, hostRetryKey) {
        if (rootfsReady != true) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (!hostProgressReady) {
            if (System.currentTimeMillis() - startedAt > 90_000L) {
                hostStartupError = true
                hostStartupDetail = context.getString(R.string.host_startup_timeout)
                break
            }
            val current = runCatching { runtimeManager.getStatus() }.getOrNull()
            if (current == null) {
                kotlinx.coroutines.delay(300)
                continue
            }
            when {
                current.phase == RuntimePhase.RUNNING -> {
                    hostProgressReady = true
                    hostStartupError = false
                    hostStartupDetail = null
                }
                current.phase == RuntimePhase.ERROR -> {
                    hostStartupError = true
                    hostStartupDetail = null
                    break
                }
                else -> kotlinx.coroutines.delay(300)
            }
        }
    }
    if (rootfsReady == true && !hostProgressReady) {
        HostStartupSplash(
            phase = if (hostStartupError) RuntimePhase.ERROR else RuntimePhase.STARTING,
            // 失败原因和最近输出必须显示在这里：启动页是唯一入口，只给一个
            // 「重试」按钮会让用户既修不了、也没法把原因发出来。
            detail = hostStartupDetail ?: if (hostStartupError) status.lastError else null,
            logTail = if (hostStartupError) status.logTail else emptyList(),
            onRetry = {
                hostStartupError = false
                hostStartupDetail = null
                hostRetryKey += 1
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { runtimeManager.start(activeWorkspace.absolutePath) }
                    }
                }
            },
            // Host 起不来时仍允许进入应用：设置 → 移动运行时 有完整日志，
            // 也可以在那里导出脱敏诊断包。
            onContinue = { hostProgressReady = true },
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
    var activeWorkspaceName by remember { mutableStateOf(DEFAULT_WORKSPACE_DIR_NAME) }
    // 文件夹名保持固定（用于磁盘路径），界面上按当前语言显示。
    val defaultWorkspaceLabel = stringResource(R.string.workspace_default)
    val activeWorkspaceCwd = "/workspace/$activeWorkspaceName"
    val activeWorkspaceFolderPath = java.io.File(activeWorkspace, activeWorkspaceName).absolutePath

    // 真正的系统文件选择器（SAF）：可多选图片/任意文件，复制进当前工作区后
    // 在输入卡片显示；发送时以 @<工作区路径> 附在消息开头。
    val attachmentPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            val workspaceDir = java.io.File(activeWorkspace, activeWorkspaceName)
            // 大文件复制放 IO 线程，避免卡住输入法/界面。
            scope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.deepseek.dsh.AttachmentImporter.import(context, workspaceDir, uris)
                }
                result.added.forEach { added ->
                    if (chatAttachments.none { it.workspaceRelativePath == added.workspaceRelativePath }) {
                        chatAttachments.add(added)
                    }
                }
                workspaceRevision += 1
                result.skipped.forEach { name ->
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.attachment_copy_failed, name),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
    var showAddWorkspaceDialog by remember { mutableStateOf(false) }
    var newWorkspaceName by remember { mutableStateOf("") }
    var renameWorkspaceFolder by remember {
        mutableStateOf<com.deepseek.dsh.feature.sessions.WorkspaceFolder?>(null)
    }
    var renameWorkspaceName by remember { mutableStateOf("") }
    var deleteWorkspaceFolder by remember {
        mutableStateOf<com.deepseek.dsh.feature.sessions.WorkspaceFolder?>(null)
    }

    // bridge 插件更新后需要两件事：读到「正在运行」的版本（bridge manifest），
    // 以及重启 Host 让新代码加载。两者都只有 App 层能提供，因此注入给 runtime。
    DisposableEffect(activeHostId, activeWorkspace.absolutePath) {
        HostRuntimeHolder.runningBridgeVersionProvider = {
            runCatching { dshRepository.bridgePluginVersion(forceRefresh = true) }.getOrNull()
        }
        HostRuntimeHolder.restartHost = {
            val stopped = runtimeManager.stop()
            if (stopped.isFailure) {
                stopped
            } else {
                runtimeManager.start(activeWorkspace.absolutePath).map { }
            }
        }
        onDispose {
            HostRuntimeHolder.runningBridgeVersionProvider = null
            HostRuntimeHolder.restartHost = null
        }
    }
    // 插件瞬态消息：Host 排队、客户端轮询取走，用系统 Toast 呈现。
    // 没有事件通道时这段是空转，取不到消息也不会打扰任何东西。
    // profile 清单每次 Host 启动会被重置，这个集合保证每个 App 会话里每个
    // bundle 只补一次，避免反复触发 Host 重载。
    val restoredBundles = remember { mutableSetOf<String>() }
    // 轮询协程只启动一次，用 latest-value 快照读当前会话，避免拿到旧值。
    val currentSessionForTeam by androidx.compose.runtime.rememberUpdatedState(selectedSessionId)
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            kotlinx.coroutines.delay(4_000)
            ticks += 1
            // 桥对客户端 bundle 是增量扫描的：每轮只求值一个，结果累积。定时刷新让
            // 后续批次继续推进，扫描完成后不再产生新条目，刷新也就变成一次廉价读。
            if (ticks % 4 == 0) {
                runCatching { dshRepository.refreshBridgePluginCatalog() }
                // 把用户打开过的实验性 bundle 补回 profile（清单会被启动重置）。
                val catalog = dshRepository.bridgePluginCatalogFlow.value
                val missing = savedEnabledBundles().filter { name ->
                    name !in restoredBundles && catalog.bundles.none { it.name == name && it.enabled }
                }
                if (missing.isNotEmpty()) {
                    missing.forEach { name -> restoredBundles += name }
                    missing.forEach { name ->
                        runCatching { dshRepository.setBundleEnabled(activeHostId, name, true) }
                    }
                    runCatching { dshRepository.refreshBridgePluginCatalog() }
                    runCatching { dshRepository.refreshPluginInventory(activeHostId) }
                }
            }
            // 插件界面的入口不能只依赖「Host 状态刚变成有端口」那一刻：Host 冷启动时
            // 首个 bridge 请求可能先于会话 cookie 就绪而失败，一次失败就会让设置里的
            // 「插件界面」整组消失。入口为空时用更短的周期重试，稳定后降低频率。
            val entriesEmpty = dshRepository.pluginSlotEntriesFlow.value.isEmpty()
            val settingsRefreshEvery = if (entriesEmpty) 2 else 8
            if (ticks % settingsRefreshEvery == 0) {
                runCatching { dshRepository.refreshPluginSettingsPages() }
            }
            // 团队面板的成员状态随工具调用变化，定时刷新投影（只读一次轻量读）。
            if (ticks % 4 == 0) {
                val teamOn = dshRepository.bridgePluginCatalogFlow.value.bundles.any {
                    it.name == AgentTeamPluginUi.BUNDLE && it.enabled
                }
                if (teamOn) {
                    runCatching { dshRepository.refreshAgentTeam(activeHostId, currentSessionForTeam) }
                }
            }
            runCatching {
                val events = dshRepository.pendingPluginEvents()
                // client/refresh 转成组件刷新信号；toast 用系统 Toast 呈现。
                dshRepository.dispatchPluginEvents(events)
                events.forEach { event ->
                    if (event.kind == "toast" && event.text.isNotBlank()) {
                        android.widget.Toast.makeText(context, event.text, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    if (showAddWorkspaceDialog) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { showAddWorkspaceDialog = false },
            title = stringResource(R.string.workspace_new_title),
            closeLabel = stringResource(R.string.action_close),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_cancel),
                    primary = false,
                    enabled = true,
                    onClick = { showAddWorkspaceDialog = false },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_create),
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
                    placeholder = stringResource(R.string.workspace_name_placeholder),
                )
            },
        )
    }
    renameWorkspaceFolder?.let { folder ->
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { renameWorkspaceFolder = null },
            title = stringResource(R.string.workspace_rename_title),
            closeLabel = stringResource(R.string.action_close),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_cancel), primary = false, enabled = true,
                    onClick = { renameWorkspaceFolder = null },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_save), primary = true,
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
                    placeholder = stringResource(R.string.workspace_name_placeholder),
                )
            },
        )
    }

    deleteWorkspaceFolder?.let { folder ->
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { deleteWorkspaceFolder = null },
            title = stringResource(R.string.workspace_delete_title),
            closeLabel = stringResource(R.string.action_close),
            description = stringResource(R.string.workspace_delete_desc, folder.name),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_cancel), primary = false, enabled = true,
                    onClick = { deleteWorkspaceFolder = null },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_delete), primary = true, enabled = true,
                    onClick = {
                        val target = java.io.File(
                            folder.path.ifBlank { java.io.File(activeWorkspace, folder.name).absolutePath }
                        )
                        runCatching { target.deleteRecursively() }
                        if (folder.name == activeWorkspaceName) activeWorkspaceName = DEFAULT_WORKSPACE_DIR_NAME
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
                name = dir.name.ifEmpty { context.getString(R.string.workspace_main) },
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
                                elapsed < 60_000L -> context.getString(R.string.time_just_now)
                                elapsed < 60L * 60 * 1000 -> context.getString(R.string.time_minutes, (elapsed / 60_000L).toInt())
                                elapsed < 24L * 60 * 60 * 1000 -> context.getString(R.string.time_hours, (elapsed / (60 * 60 * 1000)).toInt())
                                else -> context.getString(R.string.time_days, (elapsed / (24 * 60 * 60 * 1000)).toInt())
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
                androidx.compose.foundation.layout.Column {
                    ModernNavigationDrawerContent(
                    userName = accountState.profile?.name ?: stringResource(R.string.drawer_default_user),
                    avatarUrl = accountState.profile?.avatarUrl,
                    updateVersion = updateInfo?.tag?.takeIf {
                        com.deepseek.dsh.feature.settings.UpdateCenter.hasUpdate(context)
                    },
                    onUpdateClick = {
                        scope.launch { drawerState.close() }
                        settingsInitialSection = com.deepseek.dsh.feature.settings.SettingsSectionTab.UPDATE
                        activePage = ActivePage.SETTINGS
                    },
                    userTag = if (accountState.isSignedIn) {
                        stringResource(R.string.account_official_tag)
                    } else {
                        stringResource(R.string.account_unauth_tag)
                    },
                    accountBalance = accountState.wallets.firstOrNull()?.let { formatAmount(it.balance) } ?: "¥0.00",
                    isSignedIn = accountState.isSignedIn,
                    onSignIn = { showSignInDialog = true },
                    onSignOut = hostSignOut,
                    workspaces = workspaceFolders,
                    workspaceDisplayName = { name ->
                        if (name == DEFAULT_WORKSPACE_DIR_NAME) defaultWorkspaceLabel else name
                    },
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
                    // sidebar.*：原生插件在侧栏面板底部的落点。
                    com.deepseek.dsh.plugin.nativehost.NativeSlot(
                        slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.SIDEBAR_PANEL,
                        spec = kotlinx.serialization.json.JsonObject(emptyMap()),
                    )
                }
            }
        ) {
            // 对话页使用完整顶栏；设置页自带标题栏（不再叠加）；其余全屏页只保留返回条，
            // 避免出现双 header。
            BackHandler(enabled = activePage != ActivePage.CHAT) {
                // 插件子页面（详情 / 更多插件）打开时，系统返回先关子页面；顶栏返回走同一个
                // 信号，两条返回路径的行为保持一致。
                if (activePage == ActivePage.PLUGINS && pluginDetailOpen) {
                    pluginBackSignal += 1
                } else {
                    activePage = ActivePage.CHAT
                }
            }
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
            val chatPage = activePage == ActivePage.CHAT
            val pageHasOwnHeader = activePage == ActivePage.SETTINGS
            Scaffold(
                topBar = {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
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
                                inspectorEnabled = sessionInspectorEnabled,
                                onOpenInspector = { sessionInspectorOpen = true },
                                agentTeam = agentTeam,
                                agentTeamLoading = agentTeamLoading,
                                teamEnabled = agentTeamEnabled,
                                currentSessionId = selectedSessionId,
                                onOpenMember = { memberSessionId ->
                                    selectedSessionId = memberSessionId
                                    activePage = ActivePage.CHAT
                                    scope.launch {
                                        dshRepository.refreshAgentTeam(activeHostId, memberSessionId)
                                    }
                                },
                                onMenuClick = { scope.launch { drawerState.open() } },
                            )

                            !pageHasOwnHeader -> PageBackBar(
                                title = pageTitleOf(activePage),
                                onBack = {
                                    // 插件详情页打开时，App 的返回先关详情；再按一次才离开插件页。
                                    if (activePage == ActivePage.PLUGINS && pluginDetailOpen) {
                                        pluginBackSignal += 1
                                    } else {
                                        activePage = ActivePage.CHAT
                                    }
                                },
                            )
                        }
                        }
                        // conversation.session.header.corner：真正叠在标题栏右上角，
                        // 不再作为一行掉到标题栏下面。
                        if (chatPage) {
                            com.deepseek.dsh.plugin.nativehost.OfficialSlot(
                                slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.SESSION_HEADER_CORNER,
                                modifier = Modifier
                                    .align(androidx.compose.ui.Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(top = 8.dp, end = 12.dp),
                            ) {}
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
                                    val isPlanPrompt = chatPlanMode
                                    val outgoingText = if (isPlanPrompt) {
                                        context.getString(R.string.plan_prompt_prefix) + text
                                    } else {
                                        text
                                    }
                                    chatAttachments.clear()
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
                                                // Host 会把工作区目录名中的非 ASCII 码点转义为 ~XXXX。
                                                java.io.File(
                                                    context.filesDir,
                                                    "rootfs/root/.dsh/sessions/${workspaceSessionDirName(activeWorkspaceName)}/$sid"
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
                                            dshRepository.sendMessage(activeHostId, sid, outgoingText)
                                        } else {
                                            android.widget.Toast.makeText(
                                                context, context.getString(R.string.session_create_failed), android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        } finally {
                                            pendingMessages.removeAll { it.messageId == localId }
                                            isSending = false
                                        }
                                    }
                                },
                                isSending = isSending,
                                workspaceName = if (activeWorkspaceName == DEFAULT_WORKSPACE_DIR_NAME) {
                                    defaultWorkspaceLabel
                                } else {
                                    activeWorkspaceName
                                },
                                // 工作区列表来自 Host；选中即切换当前工作区，末行开新建对话框。
                                workspaceNames = workspaceFolders.map { folder ->
                                    if (folder.name == DEFAULT_WORKSPACE_DIR_NAME) defaultWorkspaceLabel else folder.name
                                },
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
                                selectedModelId = effectiveModelIdByName[currentModel] ?: currentModel,
                                onNativeModelSelect = { provider, model, effort ->
                                    runCatching {
                                        val sid = selectedSessionId
                                            ?: error(context.getString(R.string.no_active_session_fast))
                                        dshRepository.selectModel(
                                            activeHostId,
                                            sid,
                                            model,
                                            provider,
                                            effort,
                                        )
                                    }
                                },
                                availableModels = availableModelNames,
                                agentPresetLabel = com.deepseek.dsh.feature.chat.agentPresetLabel(
                                    pendingAgentPreset ?: currentAppSettings.transcriptView
                                ),
                                agentPresetSelectedId = pendingAgentPreset ?: currentAppSettings.transcriptView,
                                onAgentPresetSelected = { preset ->
                                    // 立即生效：新会话用新预设；当前会话保持原样。
                                    pendingAgentPreset = preset
                                    scope.launch {
                                        settingsRepository.updateSettings { current ->
                                            current.copy(transcriptView = preset)
                                        }
                                    }
                                },                                modelGroups = catalogInfo.groups.ifEmpty { cachedModelGroups },
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
                                stats = currentSessionStats,
                                quotaUsage = quotaUsage,
                                voiceEnabled = voiceEnabled,
                                voiceRecording = voiceRecording,
                                voiceSeconds = voiceSeconds,
                                voiceLevel = voiceLevel,
                                voiceBusy = voiceBusy,
                                voiceError = voiceError,
                                voiceTranscript = voiceTranscript,
                                onVoiceStart = { startVoice() },
                                onVoiceStop = { stopVoice() },
                                onVoiceCancel = { cancelVoice() },
                                onVoiceTranscriptConsumed = { voiceTranscript = null },
                                translateReasoning = if (cotTranslationEnabled) {
                                    { text ->
                                        dshRepository.cotTranslationTranslate(
                                            activeHostId,
                                            text,
                                            selectedSessionId ?: "",
                                        )
                                    }
                                } else null,
                                bandSnapshot = bandSnapshot,
                                bandPressing = bandPressing,
                                onBandPress = { generation, actionId -> pressClaudeCodeBand(generation, actionId) },
                                attachments = chatAttachments,
                                onRemoveAttachment = { attachment ->
                                    chatAttachments.remove(attachment)
                                },
                                onPickAttachments = {
                                    // SAF 选择器：任意类型文件 + 图片；图片走同一通道。
                                    attachmentPickerLauncher.launch(arrayOf("*/*"))
                                },
                                planMode = chatPlanMode,
                                onPlanModeChange = { enabled -> chatPlanMode = enabled },
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
                                managedPluginPackages = managedPluginPackages,
                                storeSeedUrl = pluginStoreSeedUrl,
                                initialTab = pluginInitialTab,
                                hostSettingsPages = pluginSettingsPages,
                                rowConfigSurfaces = pluginSlotSurfaces,
                                // 「内置插件配置」的每行背后是一个 Host 设置命名空间：
                                // 表单和回写都走同一对接口，原生控件照官方设置页渲染。
                                loadPluginSettings = { ns -> dshRepository.pluginSettingsDocument(ns) },
                                savePluginSettings = { ns, patch, revision ->
                                    dshRepository.updatePluginSettings(ns, patch, revision)
                                },
                                // 凭据域不是设置文档：官方页面的 API Key 走 Host 的
                                // credentials 接口，bridge 没有这层，直接调 Host Remote API。
                                loadCredential = { ref -> dshRepository.credentialStatus(activeHostId, ref) },
                                saveCredential = { ref, value ->
                                    dshRepository.setCredential(activeHostId, ref, value)
                                },
                                backSignal = pluginBackSignal,
                                onDetailOpenChange = { pluginDetailOpen = it },
                                onOpenSurface = { item ->
                                    if (item.kind == SurfaceKind.OVERLAY) {
                                        activeOverlaySurface = item
                                    } else {
                                        activeSheetSurface = item
                                    }
                                },
                                onOpenSdui = { },
                                onInstallPlugin = { pkgName, onProgress ->
                                    // 安装耗时以秒计：先报一个「已受理」，再让安装器
                                    // 把下载/解压/依赖/写盘的每一步透传给对话框。
                                    onProgress(0.02f, context.getString(R.string.plugins_install_preparing))
                                    val hostRes = dshRepository.installPlugin(activeHostId, pkgName)
                                    // Host 内 pnpm 依赖 proot 的 rename/unlink/mkdtemp，
                                    // 这台设备上全部返回 ENOSYS。失败后改用 App 自己的
                                    // 文件与网络能力完成同一件事，再重启 Host 加载。
                                    val usedFallback = hostRes.isFailure
                                    val result = if (hostRes.isSuccess) {
                                        onProgress(0.6f, context.getString(R.string.plugins_install_progress_title))
                                        Result.success(pkgName)
                                    } else {
                                        onProgress(0.05f, context.getString(R.string.plugins_install_fallback))
                                        appPluginInstaller.install(pkgName) { fraction, step ->
                                            onProgress(0.05f + fraction * 0.75f, step)
                                        }
                                    }
                                    result.fold(
                                        onSuccess = { installed ->
                                            var restartFailure: Throwable? = null
                                            if (usedFallback) {
                                                // 文件已就位，重启 Host 才会让新插件进入 Loader。
                                                // 重启失败不能当成安装成功：界面会显示插件「已装」，
                                                // 但 Host 里并没有加载它。
                                                onProgress(0.85f, context.getString(R.string.plugins_install_restarting))
                                                restartFailure = HostRuntimeHolder.restartHost
                                                    ?.invoke()
                                                    ?.exceptionOrNull()
                                                kotlinx.coroutines.delay(1_200)
                                            }
                                            managedPluginPackages = runCatching {
                                                appPluginInstaller.managedPackages()
                                            }.getOrDefault(managedPluginPackages)
                                            dshRepository.refreshPluginInventory(activeHostId)
                                            dshRepository.refreshBridgePluginCatalog()
                                            dshRepository.refreshPluginSettingsPages()
                                            // 反向联动：这个 JS 插件有原生伴侣 APK 的话，提示可装。
                                            // 查 bridge 的 native-plugins provider；有则通知用户去商店。
                                            runCatching {
                                                val pkgName = installed.substringAfterLast('/')
                                                    .substringBefore('@').ifBlank { installed }
                                                val companion = dshRepository.bridge()
                                                    ?.nativeCompanionFor(pkgName)
                                                if (companion is com.deepseek.dsh.core.bridge.BridgeResult.Success &&
                                                    companion.value != null
                                                ) {
                                                    nativeCompanionAvailable = companion.value
                                                }
                                            }
                                            val failure = restartFailure
                                            if (failure != null) {
                                                Result.failure(
                                                    IllegalStateException(
                                                        context.getString(
                                                            R.string.plugins_install_restart_failed,
                                                            failure.message ?: "",
                                                        )
                                                    )
                                                )
                                            } else {
                                                onProgress(1f, context.getString(R.string.plugins_install_success, installed))
                                                Result.success(installed)
                                            }
                                        },
                                        onFailure = { error ->
                                            Result.failure(
                                                IllegalStateException(error.message ?: context.getString(R.string.plugin_install_failed))
                                            )
                                        },
                                    )
                                },
                                onToggleBundle = { packageName, enabled ->
                                    scope.launch {
                                        val result = dshRepository.setBundleEnabled(activeHostId, packageName, enabled)
                                        // profile 的 bundle 清单在 Host 启动时会被重置成模板，
                                        // 所以把用户明确拨动过的开关记一份，Host 起来后补回去。
                                        result.fold(
                                            onSuccess = { outcome ->
                                                rememberBundleSelection(packageName, enabled)
                                                if (outcome.restartRequired) {
                                                    pluginChangeNotice = listOf(
                                                        context.getString(R.string.restart_dsh_hint),
                                                        restartReasonText(outcome.restartReason),
                                                    )
                                                } else if (outcome.warnings.isNotEmpty()) {
                                                    // 不阻塞界面：官方把一般提示放在插件页内；原生用短 toast 暴露。
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        outcome.warnings.first().take(120),
                                                        android.widget.Toast.LENGTH_LONG,
                                                    ).show()
                                                }
                                            },
                                            onFailure = {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(R.string.plugin_toggle_failed, context.getString(if (enabled) R.string.plugin_enable else R.string.plugin_disable)),
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            },
                                        )
                                        // 双向同步：开关值以 Host 的实际状态为准。成功时
                                        // 这只是确认；失败时它会把开关拨回真实状态，而不是
                                        // 让界面停在用户按下的那个位置。
                                        dshRepository.refreshBridgePluginCatalog()
                                        dshRepository.refreshPluginInventory(activeHostId)
                                    }
                                },
                                onToggleBundleAwait = { packageName, enabled ->
                                    val result = dshRepository.setBundleEnabled(activeHostId, packageName, enabled)
                                    result.fold(
                                        onSuccess = { outcome ->
                                            rememberBundleSelection(packageName, enabled)
                                            if (outcome.restartRequired) {
                                                pluginChangeNotice = listOf(
                                                    context.getString(R.string.restart_dsh_hint),
                                                    restartReasonText(outcome.restartReason),
                                                )
                                            } else if (outcome.warnings.isNotEmpty()) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    outcome.warnings.first().take(120),
                                                    android.widget.Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        },
                                        onFailure = {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.plugin_toggle_failed, context.getString(if (enabled) R.string.plugin_enable else R.string.plugin_disable)),
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    )
                                    dshRepository.refreshBridgePluginCatalog()
                                    dshRepository.refreshPluginInventory(activeHostId)
                                    result.isSuccess
                                },
                                onTogglePluginRow = { entryId, enabled ->
                                    scope.launch {
                                        // 行插件不是 bundle：Host 用 setPluginEnabled 写
                                        // profile patch 的 disabled 覆盖，立即生效；失败时
                                        // refreshPluginInventory 会把开关拨回真实状态。
                                        dshRepository.setPluginEnabled(activeHostId, entryId, enabled)
                                        // profile patch 每次启动都会被重建，所以再把覆盖
                                        // 持久化到 web-app patch，重启后开关状态不丢。
                                        val patchId = entryId.removePrefix("include:")
                                        appPluginInstaller.setRowEnabled(patchId, enabled)
                                        dshRepository.refreshBridgePluginCatalog()
                                        dshRepository.refreshPluginSettingsPages()
                                    }
                                },
                                onToggleComponent = { entryId, enabled ->
                                    scope.launch {
                                        // 子组件就是 bundle patch 插进来的一行：Host 把它
                                        // 记进 profile 的 cordis.patch.yml
                                        // （`- id: <rowId> / disabled: true`）并只重载这一行，
                                        // bundle 其余部分照常运行。失败时
                                        // refreshPluginInventory 会把开关拨回真实状态。
                                        dshRepository.setPluginEnabled(activeHostId, entryId, enabled)
                                        dshRepository.refreshBridgePluginCatalog()
                                        dshRepository.refreshPluginSettingsPages()
                                    }
                                },
                                onUninstall = { item ->
                                    scope.launch {
                                        // 主插件卸载时连带卸载它的伴侣 APK（APK 自己装不了也
                                        // 卸不了，必须用户确认；队列保证一次弹一个）。
                                        queueCompanionApkUninstalls(item.pluginId)
                                        if (item.slot == "plugin.row") {
                                            // 行插件没有 bundle 记录：直接走 App 侧卸载。
                                            // Host 的 removeBundle 对非 bundle 目标不适用，
                                            // 而且可能长时间不返回，不能先等它。
                                            appPluginInstaller.uninstall(item.pluginId).onSuccess {
                                                managedPluginPackages = runCatching {
                                                    appPluginInstaller.managedPackages()
                                                }.getOrDefault(managedPluginPackages)
                                                HostRuntimeHolder.restartHost?.invoke()
                                                kotlinx.coroutines.delay(1_200)
                                            }
                                        } else {
                                            dshRepository.uninstallPlugin(activeHostId, item.pluginId)
                                            // 同包名可能曾被 App 兜底以 Loader 行挂上：摘掉
                                            // 残留的挂载名，避免 Host 重建后它又出现。
                                            appPluginInstaller.stripRowMountName(item.pluginId)
                                        }
                                        surfaceStore.removeSurfaceItem(item.pluginId, item.slot)
                                        dshRepository.refreshPluginInventory(activeHostId)
                                        dshRepository.refreshBridgePluginCatalog()
                                        dshRepository.refreshPluginSettingsPages()
                                    }
                                }
                            ,
                                onCreatePlugin = {
                                    pendingAgentPreset = "cordis"
                                    selectedSessionId = null
                                    activePage = ActivePage.CHAT
                                },
                                nativePluginItems = nativePluginItems,
                                // 「发现」tab 的数据源：Host 的 pluginMarket Remote。
                                marketBackend = MarketBackend(
                                    loadIndexes = { dshRepository.marketIndexes(activeHostId) },
                                    search = { url, query ->
                                        dshRepository.marketSearch(activeHostId, url, query)
                                    },
                                    saveIndexes = { urls ->
                                        dshRepository.marketSetIndexes(activeHostId, urls)
                                    },
                                ),
                                // 语音输入插件的 Host speech 接口（准备卡片 / 选择服务）。
                                speechHooks = SpeechHooks(
                                    catalog = { dshRepository.speechCatalog(activeHostId) },
                                    configure = { providerId, language ->
                                        dshRepository.speechConfigure(activeHostId, providerId, language)
                                    },
                                    prepare = { providerId, source ->
                                        dshRepository.speechPrepare(activeHostId, providerId, source)
                                    },
                                    cancelPrepare = { providerId ->
                                        dshRepository.speechCancelPrepare(activeHostId, providerId)
                                    },
                                ),
                                onUninstallCompanion = { companion ->
                                    requestApkUninstall(companion.packageName)
                                },
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
                                        name = context.getString(R.string.new_paired_remote_host),
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
                            // 打开设置页时补一次拉取：用户不一定等得到定时轮询的下一拍，
                            // 进入页面就先让「插件界面」有数据。
                            LaunchedEffect(Unit) {
                                runCatching { dshRepository.refreshPluginSettingsPages() }
                            }
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
                                },
                                builtInPlugins = builtInPluginsList,

                                bridgeRegistry = dshRepository.bridge(),
                                pluginSlotEntries = pluginSlotEntries,
                                pluginSettingsPages = pluginSettingsPages,
                                pluginComponentRefresh = dshRepository.pluginComponentRefreshFlow,
                                // 端口要跟着运行时状态走：Host 是异步启动的，直接读
                                // Application 上的普通字段拿到的是启动前的 0。
                                hostPort = status?.port ?: dshApplication.localHostPort,
                                hostToken = status?.token ?: dshApplication.localHostToken,
                                hostPortProvider = { dshApplication.localHostPort.takeIf { it > 0 } ?: (status?.port ?: 0) },
                                pluginRpc = { method, payload ->
                                    val first = dshRepository.pluginRpc(activeHostId, method, payload.toString())
                                    val firstText = first.getOrNull() ?: first.exceptionOrNull()?.message
                                    if (ensureMissingPluginDir(context, firstText)) {
                                        dshRepository.pluginRpc(activeHostId, method, payload.toString())
                                    } else {
                                        first
                                    }
                                },
                                loadPluginSettings = { ns -> dshRepository.pluginSettingsDocument(ns) },
                                savePluginSettings = { ns, patch, revision ->
                                    dshRepository.updatePluginSettings(ns, patch, revision)
                                },
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
                                accountName = accountState.profile?.name ?: stringResource(R.string.drawer_default_user),
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

        // 反向联动：刚装的 JS 插件有原生伴侣时，底部提示可去商店装原生界面。
        nativeCompanionAvailable?.let { companion ->
            androidx.compose.material3.Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.companion_available_title, companion.name ?: companion.pluginId),
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        )
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.companion_available_desc),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        pluginStoreSeedUrl = companion.storeUrl
                        pluginInitialTab = 1
                        nativeCompanionAvailable = null
                        activePage = ActivePage.PLUGINS
                    }) { androidx.compose.material3.Text(stringResource(R.string.companion_go_install)) }
                    androidx.compose.material3.TextButton(onClick = { nativeCompanionAvailable = null }) {
                        androidx.compose.material3.Text(stringResource(R.string.companion_ignore))
                    }
                }
            }
        }

        // web-island.full：插件整岛 Web UI 的原生容器。只有插件显式注册时才占屏。
        if (com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
                .resolve(com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.WEB_ISLAND_FULL)
                .isNotEmpty()
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                com.deepseek.dsh.plugin.nativehost.NativeSlot(
                    slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.WEB_ISLAND_FULL,
                    spec = kotlinx.serialization.json.JsonObject(emptyMap()),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // shell.overlay：companion 的原生浮层。宿主只提供安全区全屏画布，
        // 鲸鱼这类挂件由 companion 自己决定位置、拖动、吸附与动画。
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            com.deepseek.dsh.plugin.nativehost.NativeSlot(
                slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.PLUGIN_OVERLAY,
                spec = kotlinx.serialization.json.JsonObject(emptyMap()),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 官方登录对话框：浏览器 OAuth 为主，API Key 为备选。
        if (showSignInDialog) {
            com.deepseek.dsh.core.designsystem.DshDialog(
                onDismiss = { showSignInDialog = false },
                title = stringResource(R.string.onboarding_title),
                closeLabel = stringResource(R.string.action_close),
                description = stringResource(R.string.onboarding_desc),
                footer = {
                    com.deepseek.dsh.core.designsystem.DshDialogButton(
                        text = stringResource(R.string.onboarding_add_key),
                        primary = false,
                        enabled = true,
                        onClick = {
                            showSignInDialog = false
                            showApiKeyDialog = true
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.deepseek.dsh.core.designsystem.DshDialogButton(
                        text = stringResource(R.string.onboarding_login),
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
                title = stringResource(R.string.onboarding_key_title),
                closeLabel = stringResource(R.string.action_close),
                description = stringResource(R.string.onboarding_key_desc),
                footer = {
                    com.deepseek.dsh.core.designsystem.DshDialogButton(
                        text = stringResource(R.string.action_cancel),
                        primary = false,
                        enabled = true,
                        onClick = { showApiKeyDialog = false },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.deepseek.dsh.core.designsystem.DshDialogButton(
                        text = stringResource(R.string.onboarding_login),
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
                        placeholder = stringResource(R.string.onboarding_key_placeholder),
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
    /** 失败原因（已由 runner 脱敏）。 */
    detail: String? = null,
    /** 最近输出（已脱敏）。 */
    logTail: List<String> = emptyList(),
    onRetry: () -> Unit,
    /** Host 不可用时仍进入应用（去设置页看日志/导出诊断包）。 */
    onContinue: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val copiedLabel = stringResource(R.string.host_startup_copied)
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
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
                    phase == RuntimePhase.ERROR -> stringResource(R.string.host_startup_failed)
                    title.isNotBlank() -> title
                    else -> stringResource(R.string.host_starting)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (phase == RuntimePhase.ERROR) {
                detail?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                val lines = logTail.takeLast(10)
                if (lines.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.host_startup_log_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        lines.forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
                androidx.compose.material3.Button(onClick = onRetry) {
                    Text(stringResource(R.string.action_host_retry))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val payload = buildString {
                            appendLine("DSH Host startup failed")
                            appendLine("device: ${android.os.Build.BRAND} ${android.os.Build.MODEL}")
                            appendLine(
                                "android: ${android.os.Build.VERSION.RELEASE} " +
                                    "(SDK ${android.os.Build.VERSION.SDK_INT})"
                            )
                            appendLine("abi: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
                            detail?.takeIf { it.isNotBlank() }?.let { appendLine("error: $it") }
                            // 全量带上最近输出（已脱敏，最多 120 行）：Host 的报错
                            // 常常是一段序列化对象，截断后正好丢掉最关键的路径/模块名。
                            logTail.forEach { appendLine(it) }
                        }
                        val clipboard = context.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newPlainText("DSH host startup", payload)
                        )
                        android.widget.Toast.makeText(
                            context,
                            copiedLabel,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) { Text(stringResource(R.string.host_startup_copy)) }
                if (onContinue != null) {
                    androidx.compose.material3.TextButton(onClick = onContinue) {
                        Text(stringResource(R.string.host_startup_continue))
                    }
                    Text(
                        text = stringResource(R.string.host_startup_continue_hint),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                text = stringResource(R.string.storage_permission_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.storage_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            androidx.compose.material3.Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.storage_permission_grant))
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
                        text = stringResource(R.string.error_prefix, it),
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
                                        installProgress = 0f to context.getString(R.string.plugin_install_failed) + ": " + (error.message ?: "")
                                    }
                                }
                            },
                            enabled = !isInstalling
                        ) {
                            Text(if (isInstalling) stringResource(R.string.runtime_installing) else stringResource(R.string.runtime_install_reset))
                        }
                    }

                    if (status.phase == RuntimePhase.STOPPED || status.phase == RuntimePhase.UNINSTALLED || status.phase == RuntimePhase.ERROR) {
                        androidx.compose.material3.Button(
                            onClick = {
                                scope.launch {
                                    val result = runtimeManager.start(workspaceDir.absolutePath)
                                    result.exceptionOrNull()?.let { error ->
                                        installProgress = 0f to context.getString(R.string.startup_failed, error.message ?: "")
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
                    contentDescription = stringResource(R.string.action_back),
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
@Composable
private fun pageTitleOf(page: ActivePage): String = when (page) {
    ActivePage.PLUGINS -> stringResource(R.string.page_plugins)
    ActivePage.AUTOMATION -> stringResource(R.string.page_automation)
    ActivePage.FILES -> stringResource(R.string.page_files)
    ActivePage.HOSTS -> stringResource(R.string.page_hosts)
    ActivePage.APPROVAL -> stringResource(R.string.page_approvals)
    ActivePage.RUNTIME -> stringResource(R.string.page_runtime)
    ActivePage.SETTINGS -> stringResource(R.string.page_settings)
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
    com.deepseek.dsh.core.designsystem.DshMenuSectionLabel(stringResource(R.string.agent_team_members) + " " + members.size)
    members.take(8).forEach { (name, running) ->
        com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
            com.deepseek.dsh.core.designsystem.DshMenuLabel(name, Modifier.weight(1f))
            Text(
                text = if (running) stringResource(R.string.agent_team_phase_active) else stringResource(R.string.agent_team_phase_idle),
                fontSize = 11.sp,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
    com.deepseek.dsh.core.designsystem.DshMenuSectionLabel(stringResource(R.string.agent_team_tasks) + " " + tasks.size)
    tasks.take(8).forEach { task ->
        com.deepseek.dsh.core.designsystem.DshMenuRow(onClick = {}) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(task.subject, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
                Text(
                    text = "${task.ownerName ?: stringResource(R.string.agent_team_owner_unassigned)} · ${when (task.status) {
                        "in_progress" -> stringResource(R.string.agent_team_task_in_progress)
                        "completed" -> stringResource(R.string.agent_team_task_completed)
                        else -> stringResource(R.string.agent_team_task_pending)
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
        com.deepseek.dsh.core.designsystem.DshMenuSectionLabel(stringResource(R.string.agent_team_task_in_progress))
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
        com.deepseek.dsh.core.designsystem.DshMenuSectionLabel(stringResource(R.string.finished_section, settled.size))
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
                text = stringResource(R.string.runtime_update_available),
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
            text = stringResource(R.string.action_update),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                id = com.deepseek.dsh.core.designsystem.R.drawable.ic_close
            ),
            contentDescription = stringResource(R.string.dismiss_reminder),
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
    /** 当前会话的团队成员投影（官方 header 的团队动作数据）。 */
    agentTeam: com.deepseek.dsh.core.model.AgentTeamSummary? = null,
    /** 团队投影第一次读取中（浮层显示「正在加载团队…」）。 */
    agentTeamLoading: Boolean = false,
    /** 「智能体团队」插件是否启用；启用才显示团队动作。 */
    teamEnabled: Boolean = false,
    currentSessionId: String? = null,
    onOpenMember: (String) -> Unit = {},
    /** 「开发者工具」插件是否启用；启用才显示会话数据诊断入口。 */
    inspectorEnabled: Boolean = false,
    onOpenInspector: () -> Unit = {},
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
    val teamMemberCount = agentTeam?.members?.size ?: 0
    val teamRunning = agentTeam?.members?.any { it.phase == "active" || it.phase == "provisioning" } == true
    Box(modifier = Modifier.fillMaxWidth()) {
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
                                text = stringResource(R.string.mode_fast),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.mode_deep_thinking),
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

            // 团队动作：官方由「智能体团队」插件注册到
            // conversation.session.header.actions；启用时在标题右侧。
            if (inspectorEnabled) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(barColors.bgModulePlatform)
                        .clickable(onClick = onOpenInspector)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.BugReport,
                        contentDescription = stringResource(R.string.session_diagnostics),
                        tint = barColors.labelPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (teamEnabled) {
                AgentTeamPluginUi.HeaderAction(
                    memberCount = teamMemberCount,
                    running = teamRunning,
                    onClick = { teamPanelOpen = !teamPanelOpen },
                )
            }
        }
        // header 的 0.5dp border-l3 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(barColors.borderL3)
        )
    }
        // 团队面板：官方是锚定在标题动作下方的浮层；用 Popup 实现「点外部关闭」。
        if (teamPanelOpen) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(
                    x = 0,
                    y = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.roundToPx() },
                ),
                onDismissRequest = { teamPanelOpen = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Box(modifier = Modifier.padding(end = 12.dp)) {
                    com.deepseek.dsh.core.designsystem.DshMenuCard {
                        AgentTeamMenuPanel(
                            summary = agentTeam,
                            fallbackTasks = teamTasks,
                            loading = agentTeamLoading,
                            error = null,
                            currentSessionId = currentSessionId,
                            onOpenMember = { memberId ->
                                teamPanelOpen = false
                                onOpenMember(memberId)
                            },
                        )
                    }
                }
            }
        }
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
                text = stringResource(R.string.runtime_preparing),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.runtime_prepare_desc),
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
                    text = stringResource(R.string.runtime_prepare_failed, error ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                androidx.compose.material3.Button(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
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

/**
 * Host 会话目录名：`--workspace-<escaped>--`，非 ASCII 码点转义为 `~XXXX`。
 * 与 Host 实际落盘规则一致，用于在 Android 侧预建会话目录。
 */
private fun workspaceSessionDirName(workspaceName: String): String {
    val escaped = buildString {
        workspaceName.codePoints().forEach { codePoint ->
            if (codePoint in 0x21..0x7E && codePoint != 0x2F) {
                appendCodePoint(codePoint)
            } else {
                append('~')
                append(codePoint.toString(16).uppercase().padStart(4, '0'))
            }
        }
    }
    return "--workspace-$escaped--"
}

/**
 * 解析 `subscription-oauth.status` 返回的额度窗口，供 composer dock 上方展示。
 * 只做展示，不参与鉴权或计费判断；没有登录/没有额度时返回空列表。
 */
private fun parseQuotaWindows(body: String?, quotaLabel: String = "Quota"): List<com.deepseek.dsh.feature.chat.ComposerQuota> {
    if (body.isNullOrBlank()) return emptyList()
    val root = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
    }.getOrNull() ?: return emptyList()
    val result = root["result"] as? kotlinx.serialization.json.JsonObject
    val value = (result?.get("value") ?: root["value"] ?: root) as? kotlinx.serialization.json.JsonObject
        ?: return emptyList()
    val providers = value["providers"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    val windows = mutableListOf<com.deepseek.dsh.feature.chat.ComposerQuota>()
    providers.forEach { element ->
        val provider = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
        val providerName = ((provider["displayName"] ?: provider["provider"]) as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: return@forEach
        (provider["usage"] as? kotlinx.serialization.json.JsonArray)?.forEach { usageElement ->
            val usage = usageElement as? kotlinx.serialization.json.JsonObject ?: return@forEach
            (usage["windows"] as? kotlinx.serialization.json.JsonArray)?.forEach { windowElement ->
                val window = windowElement as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val label = (window["label"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: quotaLabel
                val percent = ((window["usedPercent"] as? kotlinx.serialization.json.JsonPrimitive)?.content)?.toDoubleOrNull()
                if (percent != null) {
                    windows += com.deepseek.dsh.feature.chat.ComposerQuota(
                        label = if (label.contains(providerName)) label else "$providerName $label",
                        percent = percent,
                    )
                }
            }
        }
    }
    return windows.distinctBy { it.label }.take(3)
}

/** 从 workspace/create|rename 的 response 中取 workspaceId。 */
private fun parseWorkspaceId(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return Regex("\"workspaceId\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
}

/**
 * 通用插件目录自愈：插件在 proot 里第一次 mkdir 可能失败（ENOENT/ENOSYS），
 * 从错误里取出它要创建的目录，用 App 进程创建后重试一次。
 *
 * 不按插件名适配：只接受 rootfs 内的 `.dsh/` 路径，任何插件都适用。
 */
private fun ensureMissingPluginDir(context: android.content.Context, message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val guestPath = Regex("mkdir '([^']+)'").find(message)?.groupValues?.getOrNull(1) ?: return false
    if (!guestPath.startsWith("/root/.dsh/")) return false
    val relative = guestPath.removePrefix("/")
    val target = File(context.filesDir, "rootfs/$relative")
    return runCatching { target.mkdirs() || target.isDirectory }.getOrDefault(false)
}


/** App 侧记住的实验性 bundle 选择（profile 清单会被 Host 启动重置）。 */
private const val PLUGIN_SELECTION_PREFS = "dsh_plugin_selection"
private const val PLUGIN_SELECTION_KEY = "enabled_bundles"

/** 磁盘上的默认工作区目录名；界面显示走 R.string.workspace_default。 */
private const val DEFAULT_WORKSPACE_DIR_NAME = "默认工作区"

