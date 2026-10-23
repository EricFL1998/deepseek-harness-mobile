package com.deepseek.dsh

import com.deepseek.dsh.core.designsystem.DshIcons
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.contentOrNull
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

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
    val onboardingPreferences = remember {
        com.deepseek.dsh.feature.runtime.OnboardingPreferences(context)
    }
    val runtimeUi = rememberAppRuntimeUiState(
        initialOnboarding = !onboardingPreferences.isCompleted,
        initialRootfsProgress = 0f to context.getString(R.string.runtime_checking),
    )

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 插件详情页打开时，顶栏返回/系统返回先退回插件列表，而不是直接离开插件页。
    // 「原生插件」tab 的列表：NativePluginDiscovery 扫描系统里已装的伴侣 APK。
    // 进入插件页时扫一次；卸载/安装由 PackageManager 广播驱动重扫（见 onResume）。
    val updateInfo by com.deepseek.dsh.feature.settings.UpdateCenter.update.collectAsState()
    val currentVersion = remember {
        com.deepseek.dsh.feature.settings.UpdateCenter.currentVersion(context)
    }
    LaunchedEffect(Unit) {
        com.deepseek.dsh.feature.settings.UpdateCenter.ensureChecked(context)
    }

    val persistedSettings by settingsRepository.settingsFlow.collectAsState()

    val externalWorkspaceDir = remember { java.io.File(android.os.Environment.getExternalStorageDirectory(), "Documents/DSH-Workspace") }
    val visibleWorkspaceDir = remember { java.io.File(android.os.Environment.getExternalStorageDirectory(), "Documents/DSH-Workspace") }

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

    val ui = rememberAppUiState(
        initialSettings = persistedSettings,
        initialWorkspace = activeWorkspace,
        initialAllFilesAccess = android.os.Environment.isExternalStorageManager(),
    )
    LaunchedEffect(ui.hasAllFilesAccess) {
        while (!ui.hasAllFilesAccess) {
            kotlinx.coroutines.delay(1000)
            ui.hasAllFilesAccess = android.os.Environment.isExternalStorageManager()
        }
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
    // slot 贡献（composer dock 等）与整页原生页面共用同一个 pluginRpc；在这里
    // 注入给 NativeHost，NativeSlotHost 会把基础 host 升级成 PluginRpcHost。
    val slotPluginRpc: suspend (String, kotlinx.serialization.json.JsonObject) -> Result<String> =
        remember(ui.activeHostId) {
            { method, payload ->
                val first = dshRepository.pluginRpc(ui.activeHostId, method, payload.toString())
                val firstText = first.getOrNull() ?: first.exceptionOrNull()?.message
                if (ensureMissingPluginDir(context, firstText)) {
                    dshRepository.pluginRpc(ui.activeHostId, method, payload.toString())
                } else {
                    first
                }
            }
        }
    com.deepseek.dsh.plugin.nativehost.NativeHost.rpcHandler = slotPluginRpc

    val repoSessions by dshRepository.getSessions(ui.activeHostId).collectAsState(initial = emptyList())
    val sessions = repoSessions

    val pendingMessages = remember { androidx.compose.runtime.mutableStateListOf<ChatMessage>() }

    // 计划模式：横幅状态；placeholder 与发送文案在 ChatScreen 内切换。
    // 不按会话 id 重置——首个 prompt 会创建会话并改变 ui.selectedSessionId，
    // 按 id 重置会让刚开启的计划模式在第一次发送后神秘关闭。

    // 系统文件选择器选回的附件（已复制进当前工作区 attachments/）。
    val chatAttachments = remember { androidx.compose.runtime.mutableStateListOf<com.deepseek.dsh.feature.chat.ChatAttachment>() }
    // 切换会话时清空待发附件，避免附件串到另一个会话。
    LaunchedEffect(ui.selectedSessionId) { chatAttachments.clear() }
    val liveSessionIds by dshRepository.liveSessionIdsFlow.collectAsState()
    val runningSessionIds by dshRepository.runningSessionIdsFlow.collectAsState()
    val unreadSessionIds by dshRepository.unreadSessionIdsFlow.collectAsState()
    LaunchedEffect(ui.selectedSessionId) { dshRepository.markSessionViewed(ui.selectedSessionId) }

    // 会话完成通知：运行中的会话从 running 变为结束且当前不在前台时提醒。
    val sessionRunningNow = ui.selectedSessionId != null && ui.selectedSessionId in runningSessionIds
    LaunchedEffect(sessionRunningNow) {
        if (runtimeUi.wasSessionRunning && !sessionRunningNow) {
            if (ui.currentAppSettings.notificationsEnabled && ui.currentAppSettings.notifyOnTurnComplete) {
                val title = sessions.find { it.sessionId == ui.selectedSessionId }?.title ?: context.getString(R.string.notify_session)
                DshNotifications.notify(context, context.getString(R.string.notify_done_title), context.getString(R.string.notify_done_body, title))
            }
        }
        runtimeUi.wasSessionRunning = sessionRunningNow
    }
    LaunchedEffect(sessions, liveSessionIds) {
        // 每次打开 App：自动进入最近更新的会话，而不是停留在空白新会话页。
        if (!runtimeUi.didInitialResume && sessions.isNotEmpty()) {
            runtimeUi.didInitialResume = true
            val candidates = sessions.filter { !it.archived }.ifEmpty { sessions }
            ui.selectedSessionId = (
                candidates.filter { it.sessionId in liveSessionIds }.ifEmpty { candidates }
                    .maxByOrNull { it.updatedAt }
                )?.sessionId
        }
    }

    val activeSession = sessions.find { it.sessionId == ui.selectedSessionId }

    val repoMessages by dshRepository.getMessages(ui.activeHostId, ui.selectedSessionId ?: "")
        .collectAsState(initial = emptyList())
    val messages = remember(repoMessages, pendingMessages.toList()) {
        // Host 回显 user/message 与本地乐观气泡可能同时存在：按内容去重，
        // 避免同一条发送出现两个气泡。
        val echoed = repoMessages.filter { it.role == MessageRole.USER }.takeLast(16)
        repoMessages + pendingMessages.filter { pending ->
            // 同内容且时间接近（60s）才算同一条回显；避免把用户重复发送的
            // 同一条文字误判成回显而吞掉。
            echoed.none { real ->
                real.content.trim() == pending.content.trim() &&
                    kotlin.math.abs(real.createdAt - pending.createdAt) < 60_000
            }
        }
    }


    val catalogInfo by dshRepository.modelCatalogFlow.collectAsState()
    val hostModels = remember(catalogInfo) { catalogInfo.groups.flatMap { it.models } }
    val hostModelNames = remember(hostModels) { hostModels.map { it.name } }
    val hostModelIdByName = remember(hostModels) { hostModels.associate { it.name to it.id } }

    // Host 冷启动需要先拉起 proot/DSH，模型目录返回前用上次缓存让菜单立刻可用。
    val cachedModels = remember { ModelCatalogCacheState(readCachedModelCatalog(context)) }
    LaunchedEffect(catalogInfo.groups) {
        if (catalogInfo.groups.isNotEmpty()) {
            cachedModels.groups = catalogInfo.groups
            writeCachedModelGroups(context, catalogInfo.groups)
        }
    }
    // 模型目录改为事件驱动（对齐官方 model picker）：`$events` 里的
    // llm/adapters-updated、settings/document-updated、credentials/*-updated
    // 会触发 DshRepository 重新拉目录，这里不再轮询。
    LaunchedEffect(hostModels) {
        if (hostModels.isNotEmpty()) {
            cachedModels.names = hostModels.map { it.name }
            cachedModels.ids = hostModels.map { it.id }
            writeCachedModelNames(context, cachedModels.names, cachedModels.ids)
        }
    }

    val currentSessionStats by dshRepository.getStatsForSession(ui.selectedSessionId ?: "").collectAsState(
        initial = com.deepseek.dsh.core.model.ComposerStats()
    )

    // 录音输入：权限、录音与转写由 App 负责；草稿在 ChatScreen 里，转写结果
    // 作为一次性事件传下去（官方 composer activity 的同样职责划分）。
    val voiceRecorder = remember { com.deepseek.dsh.voice.VoiceRecorder() }
    val voiceEnabled = remember(dshRepository.bridgePluginCatalogFlow.value) {
        dshRepository.bridgePluginCatalogFlow.value.bundles.any {
            it.name == VoiceInputPluginUi.bundle && it.enabled
        }
    }
    val voicePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            runtimeUi.voiceError = null
            runtimeUi.voiceSeconds = 0
            runtimeUi.voiceRecording = voiceRecorder.start()
            if (!runtimeUi.voiceRecording) runtimeUi.voiceError = context.getString(R.string.voice_device_unsupported)
        } else {
            runtimeUi.voiceError = context.getString(R.string.voice_permission_denied)
        }
    }
    fun startVoice() {
        val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            runtimeUi.voiceError = null
            runtimeUi.voiceSeconds = 0
            runtimeUi.voiceRecording = voiceRecorder.start()
            if (!runtimeUi.voiceRecording) runtimeUi.voiceError = context.getString(R.string.voice_device_unsupported)
        } else {
            voicePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    fun stopVoice() {
        if (!runtimeUi.voiceRecording || runtimeUi.voiceBusy) return
        val wav = voiceRecorder.stop()
        runtimeUi.voiceRecording = false
        runtimeUi.voiceLevel = 0f
        if (wav == null) {
            runtimeUi.voiceError = context.getString(R.string.voice_no_speech)
            return
        }
        runtimeUi.voiceBusy = true
        scope.launch {
            val catalog = dshRepository.speechCatalog(ui.activeHostId).getOrNull()
            val provider = catalog?.selected
            if (catalog == null || provider == null) {
                runtimeUi.voiceBusy = false
                runtimeUi.voiceError = context.getString(R.string.voice_service_unavailable)
                return@launch
            }
            // 本地识别器未就绪时先准备；准备失败要如实告诉用户，
            // 而不是在转写阶段抛出一个笼统的 transcription-failed。
            val phase = provider.preparation?.phase
            if (phase != "ready" && phase != "standby") {
                runtimeUi.voiceError = context.getString(R.string.voice_prepare_required)
                runtimeUi.voiceBusy = false
                return@launch
            }
            val encoded = android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP)
            dshRepository.speechTranscribe(ui.activeHostId, provider.id, catalog.language, encoded).fold(
                onSuccess = { text ->
                    if (text.isBlank()) {
                        runtimeUi.voiceError = context.getString(R.string.voice_no_speech)
                    } else {
                        runtimeUi.voiceTranscript = text
                        runtimeUi.voiceError = null
                    }
                },
                onFailure = { error ->
                    runtimeUi.voiceError = context.getString(R.string.voice_failed, error.message ?: error.toString())
                },
            )
            runtimeUi.voiceBusy = false
        }
    }
    fun cancelVoice() {
        voiceRecorder.cancel()
        runtimeUi.voiceRecording = false
        runtimeUi.voiceLevel = 0f
    }
    DisposableEffect(voiceRecorder) {
        voiceRecorder.onLevel = { level -> runtimeUi.voiceLevel = level }
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
    LaunchedEffect(ui.activeHostId, ui.selectedSessionId, claudeCodeModsEnabled) {
        runtimeUi.bandSnapshot = null
        val sid = ui.selectedSessionId
        if (claudeCodeModsEnabled && sid != null) {
            dshRepository.claudeCodeBand(ui.activeHostId, sid).collect { snapshot -> runtimeUi.bandSnapshot = snapshot }
        }
    }

    // 会话数据诊断（开发者工具）：只读展示当前会话收到的原始帧。
    val sessionInspectorEnabled = remember(dshRepository.bridgePluginCatalogFlow.value) {
        dshRepository.bridgePluginCatalogFlow.value.bundles.any {
            it.name == SessionInspectorPluginUi.BUNDLE && it.enabled
        }
    }
    LaunchedEffect(ui.sessionInspectorOpen, ui.selectedSessionId) {
        while (ui.sessionInspectorOpen) {
            runtimeUi.rawSessionFrames = ui.selectedSessionId?.let { dshRepository.rawSessionFrames(it) } ?: emptyList()
            kotlinx.coroutines.delay(1_000)
        }
    }
    /**
     * 启停实验性插件后真正让新代码生效：官方要求重启 Host 时自动重启并等到
     * RUNNING，再把 bridge 目录 / 插件清单 / 设置页重新拉一遍，使对应的功能与
     * UI（智能体团队、语音输入、终端、诊断、翻译、插件市场…）立刻出现，而不是
     * 停在「请重启」对话框里。
     */
    suspend fun restartHostForPluginChange(): Boolean {
        val restart = HostRuntimeHolder.restartHost?.invoke() ?: return false
        if (restart.isFailure) return false
        val finished = kotlinx.coroutines.withTimeoutOrNull(90_000) {
            runner.statusFlow.first { it.phase != RuntimePhase.RUNNING }
            runner.statusFlow.first { it.phase == RuntimePhase.RUNNING }
            true
        } ?: false
        runCatching { dshRepository.refreshBridgePluginCatalog() }
        runCatching { dshRepository.refreshPluginInventory(ui.activeHostId) }
        runCatching { dshRepository.refreshPluginSettingsPages() }
        return finished
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
    ui.pluginChangeNotice?.let { lines ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!ui.pluginRestartRunning) {
                    ui.pluginChangeNotice = null
                    ui.pluginRestartError = null
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
                    ui.pluginRestartError?.let { message ->
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
                    enabled = !ui.pluginRestartRunning,
                    onClick = {
                        scope.launch {
                            ui.pluginRestartRunning = true
                            ui.pluginRestartError = null
                            val restart = HostRuntimeHolder.restartHost?.invoke()
                                ?: Result.failure(IllegalStateException(context.getString(R.string.host_not_ready)))
                            ui.pluginRestartRunning = false
                            if (restart.isSuccess) {
                                ui.pluginChangeNotice = null
                            } else {
                                ui.pluginRestartError = restart.exceptionOrNull()?.message ?: context.getString(R.string.restart_failed)
                            }
                        }
                    },
                ) {
                    Text(if (ui.pluginRestartRunning) stringResource(R.string.restarting) else stringResource(R.string.restart_now))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !ui.pluginRestartRunning,
                    onClick = {
                        ui.pluginChangeNotice = null
                        ui.pluginRestartError = null
                    },
                ) { Text(stringResource(R.string.restart_later)) }
            },
        )
    }
    if (ui.sessionInspectorOpen) {
        SessionInspectorOverlayDialog(
            frames = runtimeUi.rawSessionFrames,
            sessionId = ui.selectedSessionId,
            onDismiss = { ui.sessionInspectorOpen = false },
        )
    }

    fun pressClaudeCodeBand(generation: Long, actionId: String) {
        val sid = ui.selectedSessionId ?: return
        scope.launch {
            runtimeUi.bandPressing = true
            dshRepository.pressClaudeCodeBand(ui.activeHostId, sid, generation, actionId)
                .onSuccess { snapshot -> if (snapshot != null) runtimeUi.bandSnapshot = snapshot }
            runtimeUi.bandPressing = false
        }
    }

    LaunchedEffect(runtimeUi.voiceRecording) {
        while (runtimeUi.voiceRecording) {
            kotlinx.coroutines.delay(1_000)
            runtimeUi.voiceSeconds += 1
            // Host 的录音上限是 120 秒（catalog.maxDurationSeconds），到点自动停止。
            if (runtimeUi.voiceSeconds >= 120) {
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
    // 官方/插件归属由后端决定（bridge 的 host-slots 归属），只在需要时读一次。
    val officialModelNamespaces = dshRepository.officialSettingsNamespaces()

    // 已注册（可路由）的 provider：官方添加目录会排除它们，改由各自卡片配置。

    val automationTasks by dshRepository.automationTasksFlow.collectAsState()
    val teamTasks by dshRepository.teamTasksFlow.collectAsState()
    val agentTeam by dshRepository.agentTeamFlow.collectAsState()
    // 团队动作只在「智能体团队」profile 启用时出现（官方由插件注册 header action）。
    val agentTeamEnabled = remember(bridgePluginCatalog) {
        dshRepository.bridgePluginCatalogFlow.value.bundles.any {
            it.name == AgentTeamPluginUi.BUNDLE && it.enabled
        }
    }
    LaunchedEffect(ui.selectedSessionId, ui.activeHostId, agentTeamEnabled) {
        if (agentTeamEnabled) {
            runtimeUi.agentTeamLoading = true
            dshRepository.refreshAgentTeam(ui.activeHostId, ui.selectedSessionId)
            runtimeUi.agentTeamLoading = false
        }
    }
    LaunchedEffect(ui.activeHostId, ui.selectedSessionId, messages.size) {
        ui.selectedSessionId?.let { sid ->
            dshRepository.refreshSessionStats(ui.activeHostId, sid)
        }
    }

    // 运行中定期刷新底部统计（tok/s、缓存命中、上下文占用），结束后再刷新一次落定。
    LaunchedEffect(ui.activeHostId, ui.selectedSessionId, sessionRunningNow) {
        val sid = ui.selectedSessionId ?: return@LaunchedEffect
        if (sessionRunningNow) {
            while (true) {
                dshRepository.refreshSessionStats(ui.activeHostId, sid)
                kotlinx.coroutines.delay(1200)
            }
        } else {
            dshRepository.refreshSessionStats(ui.activeHostId, sid)
        }
    }

    // bridge 插件更新的「运行中版本」来源与「重启 Host」动作由 App 层注入：
    // runtime 模块不认识数据层，而重启需要当前工作区路径，只有这里知道。
    // 真正的安装（下载 tag 归档、解压进 rootfs）由 HostRuntimeManager 完成。

    LaunchedEffect(ui.activeHostId) {
        dshRepository.refreshSessions(ui.activeHostId)
        dshRepository.refreshModelCatalog(ui.activeHostId)
        dshRepository.refreshPluginInventory(ui.activeHostId)
        // bridge 目录与 Host 原生清单并行读取：前者带来官方 slot 与图形，
        // 后者是它不可用时的兜底，两者互不阻塞。
        dshRepository.refreshBridgePluginCatalog()
        dshRepository.refreshPluginSettingsPages()
        dshRepository.refreshAutomationTasks(ui.activeHostId)
        dshRepository.refreshTeamTasks(ui.activeHostId)
        // 这里不能订阅远程流：Host 还没绑定地址（占位 127.0.0.1:1）时开九个
        // WebSocket 只会全部连接失败。订阅放到 Host 就绪（port 非空）之后。
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
    LaunchedEffect(appPluginInstaller) {
        runtimeUi.managedPluginPackages = runCatching { appPluginInstaller.managedPackages() }
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
        runtimeUi.nativePluginItems
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
                        val managed = runtimeUi.managedPluginPackages.any {
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
            runtimeUi.nativePluginItems = discovered.map { installed ->
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
    // 反向联动：刚装的 JS 插件有原生伴侣时，这里带上它的声明，UI 提示可装。
    // 从“JS 已装、伴生 APK 可装”跳转时，直接打开插件商店的对应仓库详情。

    val status by runner.statusFlow.collectAsState()

    // 官方 workflow 工具在手机 profile 默认关闭：用官方 API 打开，保持与桌面一致。
    LaunchedEffect(ui.activeHostId, status.phase) {
        if (status.phase == RuntimePhase.RUNNING) {
            ensureOfficialWorkflowEnabled(dshRepository, ui.activeHostId)
        }
    }
    val catalog by surfaceStore.catalogFlow.collectAsState()
    val accountState by oauthManager.accountFlow.collectAsState()
    val pendingApprovalWaterfalls by dshRepository.pendingWaterfallApprovalsFlow.collectAsState()
    val pendingQuestions by dshRepository.pendingQuestionsFlow.collectAsState()
    val hostJobs by dshRepository.jobsFlow.collectAsState()
    val hostCommands by dshRepository.hostCommandsFlow.collectAsState()
    val hostSkills by dshRepository.hostSkillsFlow.collectAsState()
    val sessionTodos by dshRepository.todosFlow.collectAsState()
    val sessionGoal by dshRepository.goalFlow.collectAsState()
    val sessionDeliverables by dshRepository.deliverablesFlow.collectAsState()
    val sessionChanges by dshRepository.changesFlow.collectAsState()
    val sessionQueuedMessages by dshRepository.queuedMessagesFlow.collectAsState()
    // 终端页状态：屏幕缓冲（去 ANSI）、状态行、可写标志。
    // Host 客户端是否已绑定到真实地址（决定能否开远程流）。
    // 未读奖励提示（官方 account/getUnnotifiedBonuses）；无奖励时什么都不渲染。
    AccountBonusHost(
        repository = dshRepository,
        hostId = ui.activeHostId,
        hostBound = runtimeUi.localHostBound,
    )

    // 多终端（官方 terminal/list）：页签集合、当前页签、可用 shell。
    // ANSI 屏幕模型：光标定位/清屏由它处理，渲染文本给 UI。
    val terminalBufferModel = remember { TerminalBuffer(cols = 80, rows = 24) }
    DisposableEffect(
        ui.activePage,
        ui.selectedSessionId,
        runtimeUi.activeTerminalId,
        ui.rightDrawerOpen,
        ui.rightDrawerTab,
    ) {
        // 终端不要求用户先选中会话：新会话页打开终端时回退到最近会话，
        // 这样这枚胶囊点开就会自动 create/open，而不是停在“无会话”。
        val sid = ui.selectedSessionId ?: sessions.firstOrNull()?.sessionId
        var job: kotlinx.coroutines.Job? = null
        val terminalVisible = ui.activePage == ActivePage.TERMINAL ||
            (ui.rightDrawerOpen && ui.rightDrawerTab == "terminal")
        if (terminalVisible && sid == null) {
            // 没有会话时 Host 的 terminal/create 没有归属，页面会永远停在“正在连接…”。
            runtimeUi.terminalStatus = context.getString(R.string.terminal_no_session)
            runtimeUi.terminalBuffer = ""
            return@DisposableEffect onDispose {
                runtimeUi.terminalConnection?.close()
                runtimeUi.terminalConnection = null
            }
        }
        if (terminalVisible && sid != null) {
            job = scope.launch {
                runtimeUi.terminalList = dshRepository.listTerminals(ui.activeHostId, sid)
                runtimeUi.terminalShells = dshRepository.listTerminalShells(ui.activeHostId, sid)
                if (runtimeUi.terminalList.none { it.id == runtimeUi.activeTerminalId }) {
                    val created = dshRepository.createTerminal(
                        ui.activeHostId, sid, runtimeUi.activeTerminalId, shellPath = null, cols = 80, rows = 24,
                    )
                    if (created != null) {
                        runtimeUi.terminalList = dshRepository.listTerminals(ui.activeHostId, sid)
                    }
                }
                val connection = dshRepository.openTerminal(
                    ui.activeHostId, sid, 80, 24, runtimeUi.activeTerminalId,
                )
                if (connection == null) {
                    runtimeUi.terminalStatus = context.getString(R.string.terminal_unavailable)
                    return@launch
                }
                runtimeUi.terminalConnection = connection
                runtimeUi.terminalStatus = context.getString(R.string.terminal_connecting)
                connection.frames.collect { raw ->
                    val frame = runCatching {
                        kotlinx.serialization.json.Json.parseToJsonElement(raw)
                            as? kotlinx.serialization.json.JsonObject
                    }.getOrNull() ?: return@collect
                    fun text(key: String): String? =
                        (frame[key] as? kotlinx.serialization.json.JsonPrimitive)
                            ?.contentOrNull
                    when (text("type")) {
                        "snapshot" -> {
                            terminalBufferModel.setScreen(text("screen").orEmpty())
                            runtimeUi.terminalBuffer = terminalBufferModel.render()
                            runtimeUi.terminalWritable = frame["writable"]?.toString() == "true"
                            val info = frame["info"]
                                as? kotlinx.serialization.json.JsonObject
                            runtimeUi.terminalStatus = (info?.get("state")
                                as? kotlinx.serialization.json.JsonPrimitive)
                                ?.contentOrNull.orEmpty()
                        }
                        "output" -> {
                            terminalBufferModel.feed(text("data").orEmpty())
                            runtimeUi.terminalBuffer = terminalBufferModel.render()
                        }
                        "state" -> {
                            runtimeUi.terminalWritable = frame["writable"]?.toString() == "true"
                            val info = frame["info"]
                                as? kotlinx.serialization.json.JsonObject
                            runtimeUi.terminalStatus = (info?.get("state")
                                as? kotlinx.serialization.json.JsonPrimitive)
                                ?.contentOrNull.orEmpty()
                        }
                        "closed" -> runtimeUi.terminalStatus =
                            context.getString(R.string.terminal_closed)
                        "error" -> runtimeUi.terminalStatus =
                            text("message").orEmpty()
                    }
                }
            }
        }
        onDispose {
            job?.cancel()
            runtimeUi.terminalConnection?.close()
            runtimeUi.terminalConnection = null
        }
    }
    // 斜杠命令目录跟着会话走；commands/change 事件也会触发刷新。
    LaunchedEffect(ui.activeHostId, ui.selectedSessionId) {
        val sid = ui.selectedSessionId
        if (sid != null) {
            runCatching { dshRepository.refreshHostCommands(ui.activeHostId, sid) }
            runCatching { dshRepository.refreshHostSkills(ui.activeHostId, sid) }
        }
    }
    // job/list 跟着当前会话走。
    LaunchedEffect(ui.activeHostId, ui.selectedSessionId, runtimeUi.localHostBound) {
        val sid = ui.selectedSessionId
        // Host 未绑定时 baseUrl 还是占位 127.0.0.1:1，开流只会连接失败。
        val bound = runtimeUi.localHostBound || ui.currentAppSettings.remoteHostUrl.isNotBlank()
        if (sid != null && bound) {
            dshRepository.watchJobs(ui.activeHostId, sid)
            // 打开会话即订阅 follow：snapshot 会补历史消息与产物声明。
            dshRepository.watchSession(ui.activeHostId, sid)
            // 轨迹面板的帧来源（同样走桥，不增加 MainAppHost 参数）。
            ScheduleBridge.wire(dshRepository, ui.activeHostId, scope)
            com.deepseek.dsh.feature.chat.WorkflowRunsBridge.provider = {
                ui.selectedSessionId?.let { dshRepository.workflowRuns(it) } ?: emptyList()
            }
            com.deepseek.dsh.feature.settings.SessionLogUploadBridge.wire(
                dshRepository, ui.activeHostId, scope,
            )
            // 默认权限预设：选项与当前值都来自 Host（catalog + settings）。
            com.deepseek.dsh.feature.settings.PermissionPresetBridge.wire(
                dshRepository, ui.activeHostId, scope,
            )
            // 外观（深浅色）与字号来自 Host 的 ui-theme 设置。
            com.deepseek.dsh.feature.settings.ThemeBridge.wire(
                dshRepository, ui.activeHostId, scope,
            )
            // 会话日志导出（官方 /api/session.export）。
            com.deepseek.dsh.feature.chat.MessageActionBridge.exportSession = {
                ui.selectedSessionId?.let { session ->
                    scope.launch { performSessionExport(context, dshRepository, ui.activeHostId, session) }
                }
            }
            // 消息级操作：从某条消息分叉（session/fork + atSeq）。
            com.deepseek.dsh.feature.chat.MessageActionBridge.forkAt = { seq ->
                ui.selectedSessionId?.let { session ->
                    scope.launch {
                        val forked = dshRepository.forkSession(ui.activeHostId, session, seq)
                        if (forked != null) {
                            ui.selectedSessionId = forked
                            runCatching { dshRepository.watchSession(ui.activeHostId, forked) }
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.session_fork_failed),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
            // 用户消息图片：引用快照 + 字节加载能力桥接给 chat 模块。
            com.deepseek.dsh.feature.chat.MessageMediaBridge.provider = {
                dshRepository.messageImagesFlow.value
            }
            com.deepseek.dsh.feature.chat.MessageMediaBridge.load = { ref ->
                ui.selectedSessionId?.let {
                    dshRepository.loadAttachmentImage(ui.activeHostId, it, ref.attachmentId)
                }
            }
            com.deepseek.dsh.feature.chat.SessionFramesBridge.provider = {
                ui.selectedSessionId?.let { dshRepository.rawSessionFrames(it) } ?: emptyList()
            }
            // 消息反馈能力以桥接注入 chat 模块（避免给 MainAppHost 增加参数）。
            com.deepseek.dsh.feature.chat.MessageFeedbackBridge.refresh = {
                ui.selectedSessionId?.let { dshRepository.listMessageFeedback(ui.activeHostId, it) }
                    ?: emptyMap()
            }
            com.deepseek.dsh.feature.chat.MessageFeedbackBridge.submit = { messageId, rating, note ->
                ui.selectedSessionId?.let { session ->
                    scope.launch {
                        com.deepseek.dsh.feature.chat.MessageFeedbackBridge.items =
                            dshRepository.submitMessageFeedback(
                                ui.activeHostId, session, messageId, rating, note,
                            )
                    }
                }
            }
        }
        else dshRepository.unwatchJobs()
    }
    val streamingText by dshRepository.streamingTextFlow.collectAsState()
    val streamingReasoning by dshRepository.streamingReasoningFlow.collectAsState()
    val hostWorkspaces by dshRepository.hostWorkspacesFlow.collectAsState()
    val hostSessionModels by dshRepository.sessionModelFlow.collectAsState()
    LaunchedEffect(accountState.isSignedIn, accountState.hasApiKey) {
        if (accountState.isSignedIn || accountState.hasApiKey) {
            kotlinx.coroutines.delay(600)
            runtimeUi.modelSettingsRaw = dshRepository.rpcRaw(ui.activeHostId, "settings/describe", "{}").getOrNull()
            runtimeUi.modelProvidersRaw = dshRepository.rpcRaw(ui.activeHostId, "llm/listConfigurableProviders", "{}").getOrNull()
            val providerRefs = com.deepseek.dsh.feature.settings.parseModelProviders(
                runtimeUi.modelProvidersRaw, runtimeUi.modelSettingsRaw, null, officialModelNamespaces
            ).map { it.keyRef }.distinct()
            val refsJson = providerRefs.joinToString(prefix = "{\"refs\":[", postfix = "]}") { "\"$it\"" }
            runtimeUi.modelCredentialsRaw = dshRepository.rpcRaw(ui.activeHostId, "credentials/describe", refsJson).getOrNull()
            dshRepository.refreshModelCatalog(ui.activeHostId)
            dshRepository.refreshPluginInventory(ui.activeHostId)
        }
    }
    val dshApplication = context.applicationContext as DshApplication

    // Host 目录优先；Host 冷启动期间用上次缓存立即渲染，避免“请选择模型”闪烁。
    val effectiveModelNames = remember(hostModelNames, cachedModels.names) {
        if (hostModelNames.isNotEmpty()) hostModelNames else cachedModels.names
    }
    val effectiveModelIdByName = remember(
        hostModelNames, hostModelIdByName, cachedModels.names, cachedModels.ids
    ) {
        if (hostModelNames.isNotEmpty()) hostModelIdByName
        else cachedModels.names.zip(cachedModels.ids).toMap()
    }
    val availableModelNames = effectiveModelNames.distinct()

    // 官方账号菜单的登录入口：浏览器 OAuth 为主，API Key 为备选（与官方 SignInDialog 一致）。
    val openUrl: (String) -> Unit = { url ->
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
    }

    // 官方 account/watch：登录中/已存凭据/退出都由 Host 推送，整个登录流程不再轮询。
    LaunchedEffect(ui.activeHostId) {
        kotlinx.coroutines.coroutineScope {
            launch {
                dshRepository.hostAccountFlow.collect { view ->
                    val pending = ui.signInPending
                    when (view?.status) {
                        "credential-stored" -> {
                            ui.signInPending = false
                            ui.signInOpenedUrl = null
                            if (!oauthManager.accountFlow.value.isSignedIn) {
                                val profile = dshRepository.getAccountProfile(ui.activeHostId).getOrNull()
                                val balance = dshRepository.getAccountBalance(ui.activeHostId).getOrNull()
                                oauthManager.applyHostAccount(profile, balance)
                            }
                        }
                        "signed-out" -> {
                            ui.signInPending = false
                            if (oauthManager.accountFlow.value.isSignedIn) oauthManager.signOut()
                        }
                        else -> if (pending && view != null) {
                            // Host 异步拿到平台授权链接后由 watch 推送，不需要 getState 轮询。
                            val url = view.authorizeUrl
                            if (!url.isNullOrBlank() && url != ui.signInOpenedUrl) {
                                ui.signInOpenedUrl = url
                                openUrl(url)
                            }
                            if (view.attemptPhase in setOf("failed", "expired", "cancelled")) {
                                ui.signInPending = false
                                ui.signInOpenedUrl = null
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
            launch {
                // 模型需要重新登录账号：直接打开官方登录入口。
                dshRepository.modelSignInRequiredFlow.collect { ui.showSignInDialog = true }
            }
        }
    }

    val hostSignOut: () -> Unit = {
        scope.launch {
            dshRepository.signOutAccount(ui.activeHostId)
            oauthManager.signOut()
        }
    }

    // 官方流程：Host 的 account/startSignIn 生成带本次 attempt 的授权链接，
    // 链接与登录结果都由 account/watch 推送；这里只负责发起 + 兜底提示。
    val beginHostSignIn: () -> Unit = {
        scope.launch {
            ui.signInPending = true
            ui.signInOpenedUrl = null
            val callbackOrigin = dshApplication.defaultClient.currentBaseUrl()
            var view = dshRepository.startAccountSignIn(ui.activeHostId, callbackOrigin).getOrNull()
            if (view == null) {
                // Host 冷启动需要时间；等待就绪后再重试，而不是直接报错。
                runCatching { runtimeManager.start(activeWorkspace.absolutePath) }
                repeat(10) {
                    kotlinx.coroutines.delay(2000)
                    view = dshRepository.startAccountSignIn(
                        ui.activeHostId,
                        dshApplication.defaultClient.currentBaseUrl()
                    ).getOrNull()
                    if (view != null) return@repeat
                }
            }
            if (view == null) {
                ui.signInPending = false
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.login_failed_host),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            when {
                view.status == "credential-stored" -> ui.signInPending = false
                !view.authorizeUrl.isNullOrBlank() -> {
                    ui.signInOpenedUrl = view.authorizeUrl
                    openUrl(view.authorizeUrl!!)
                }
                else -> {
                    // 链接由 watch 异步推送；30 秒还拿不到就提示失败。
                    kotlinx.coroutines.delay(30_000)
                    if (ui.signInPending && ui.signInOpenedUrl == null) {
                        ui.signInPending = false
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.login_failed_link),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            if (ui.signInPending) {
                // 授权页停留超时：watch 没有报成功/失败时提示一次。
                kotlinx.coroutines.delay(10 * 60_000)
                if (ui.signInPending) {
                    ui.signInPending = false
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
            if (ui.currentAppSettings.remoteHostUrl.isBlank()) {
                dshApplication.bindLocalHost(port, status.token)
            }
            dshRepository.refreshSessions(ui.activeHostId)
            runtimeUi.modelSettingsRaw = dshRepository.rpcRaw(ui.activeHostId, "settings/describe", "{}").getOrNull()
            runtimeUi.modelProvidersRaw = dshRepository.rpcRaw(ui.activeHostId, "llm/listConfigurableProviders", "{}").getOrNull()
            val providerRefs = com.deepseek.dsh.feature.settings.parseModelProviders(
                runtimeUi.modelProvidersRaw, runtimeUi.modelSettingsRaw, null, officialModelNamespaces
            ).map { it.keyRef }.distinct()
            val refsJson = providerRefs.joinToString(prefix = "{\"refs\":[", postfix = "]}") { "\"$it\"" }
            runtimeUi.modelCredentialsRaw = dshRepository.rpcRaw(ui.activeHostId, "credentials/describe", refsJson).getOrNull()
            dshRepository.refreshModelCatalog(ui.activeHostId)
            dshRepository.refreshPluginInventory(ui.activeHostId)
            // Host 一就绪就主动读账号，且必须拿到一个明确的 signed-in /
            // signed-out 才标记 hydrated；读失败（Host 还没完全起来）继续重试，
            // 否则首屏会先显示“未登录”，一两秒后才跳成已登录。
            // 与插件目录并行：两者互不依赖，串行只会把启动时间相加。
            val accountHydrationJob = async {
                if (!runtimeUi.accountHydrated) {
                    val deadline = System.currentTimeMillis() + 4_000
                    while (System.currentTimeMillis() < deadline) {
                        val state = runCatching {
                            dshRepository.getAccountState(ui.activeHostId).getOrNull()
                        }.getOrNull()
                        if (state != null) {
                            if (state.status == "credential-stored") {
                                val profile = runCatching {
                                    dshRepository.getAccountProfile(ui.activeHostId).getOrNull()
                                }.getOrNull()
                                val balance = runCatching {
                                    dshRepository.getAccountBalance(ui.activeHostId).getOrNull()
                                }.getOrNull()
                                oauthManager.applyHostAccount(profile, balance)
                            } else if (state.status == "signed-out" && oauthManager.accountFlow.value.isSignedIn) {
                                oauthManager.signOut()
                            }
                            runtimeUi.accountHydrated = true
                            return@async
                        }
                        kotlinx.coroutines.delay(150)
                    }
                }
            }
            // 插件清单、bridge 目录、插件设置页都拿到后才算插件数据就绪。
            dshRepository.refreshBridgePluginCatalog()
            dshRepository.refreshPluginSettingsPages()
            accountHydrationJob.await()
            runtimeUi.pluginsHydrated = true
            dshRepository.refreshAutomationTasks(ui.activeHostId)
            dshRepository.refreshTeamTasks(ui.activeHostId)
            val baseUrl = dshApplication.defaultClient.currentBaseUrl()
            if (baseUrl.endsWith(":1")) {
                android.util.Log.w("DSH", "Host not bound yet, skip remote streams: $baseUrl")
            } else {
                runtimeUi.localHostBound = true
                dshRepository.subscribeRemoteEvents(ui.activeHostId)
                // App 显示语言同步给 Host：英文界面下 Host 返回的文案也用英文。
                val appLanguage = context.resources.configuration.locales[0].language
                dshRepository.syncHostLocale(ui.activeHostId, appLanguage)
            }
            // 账号态之后由 account/watch 持续推送；首屏已主动拉过一次。
        }
    }

    // 首次启动先准备 rootfs：未安装/未下载完成之前不进入 onboarding。
    // 已安装过就直接视为就绪，避免重新打开时闪一下安装进度条；首次安装仍走 null→安装。


    /**
     * 只在镜像**必需**重装时才在启动时提示。
     *
     * 比较交给 `rootfsUpdateKind`：它按段比版本，只有 rootfs 段或 dsh 段变化才
     * 返回 MANDATORY。插件落后不在这里处理 —— 那是设置页的职责，且可以就地升级，
     * 不该在启动时打断用户。
     */
    LaunchedEffect(runtimeUi.rootfsReady) {
        if (runtimeUi.rootfsReady != true) return@LaunchedEffect
        val installed = runtimeManager.installedRootfsVersion()
        val available = runtimeManager.availableRootfsVersion()
        runtimeUi.installedRootfsVersion = installed
        runtimeUi.availableRootfsVersion = available
        val kind = runCatching { runtimeManager.rootfsUpdateKind() }
            .getOrDefault(com.deepseek.dsh.runtime.host.RootfsUpdateKind.NONE)
        val mandatory = kind == com.deepseek.dsh.runtime.host.RootfsUpdateKind.MANDATORY
        runtimeUi.rootfsUpdateAvailable = mandatory
        runtimeUi.showRootfsUpdatePrompt = mandatory
    }

    if (runtimeUi.showRootfsUpdatePrompt) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { runtimeUi.showRootfsUpdatePrompt = false },
            title = stringResource(R.string.runtime_update_title),
            closeLabel = stringResource(R.string.runtime_update_later),
            description = stringResource(R.string.runtime_update_desc, runtimeUi.availableRootfsVersion ?: "", runtimeUi.installedRootfsVersion ?: ""),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.runtime_update_later),
                    primary = false,
                    enabled = true,
                    onClick = { runtimeUi.showRootfsUpdatePrompt = false },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.runtime_update_now),
                    primary = true,
                    enabled = true,
                    onClick = {
                        runtimeUi.showRootfsUpdatePrompt = false
                        runtimeUi.forceRootfsInstall = true
                        runtimeUi.rootfsReady = false
                        scope.launch {
                            runCatching { runtimeManager.stop() }
                            runtimeUi.rootfsInstallAttempt += 1
                        }
                    },
                )
            },
        )
    }


    LaunchedEffect(runtimeUi.rootfsInstallAttempt) {
        if (runtimeManager.isRootfsInstalled()) {
            runtimeUi.forceRootfsInstall = false
            runtimeUi.rootfsReady = true
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
        if (!runtimeUi.forceRootfsInstall && rootfsFilesPresent &&
            kind == com.deepseek.dsh.runtime.host.RootfsUpdateKind.MANDATORY
        ) {
            runtimeUi.installedRootfsVersion = installedVersion
            runtimeUi.availableRootfsVersion = availableVersion
            runtimeUi.rootfsUpdateAvailable = true
            runtimeUi.showRootfsUpdatePrompt = true
            runtimeUi.rootfsReady = true
            return@LaunchedEffect
        }
        runtimeUi.rootfsReady = false
        runtimeUi.rootfsSetupError = null
        val result = runtimeManager.installRootfs { progress, message ->
            runtimeUi.rootfsSetupProgress = progress to message
        }
        result.exceptionOrNull()?.let { error ->
            runtimeUi.rootfsSetupError = error.message ?: context.getString(R.string.rootfs_install_failed)
        }
        if (result.isSuccess) runtimeUi.rootfsReady = true
    }

    // rootfs 就绪后再拉起本地 Host，避免在未安装时产生启动错误。
    LaunchedEffect(runtimeUi.rootfsReady) {
        if (runtimeUi.rootfsReady == true &&
            (status.phase == RuntimePhase.STOPPED ||
                status.phase == RuntimePhase.UNINSTALLED ||
                status.phase == RuntimePhase.ERROR)
        ) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runtimeManager.start(activeWorkspace.absolutePath)
            }
        }
    }

    if (runtimeUi.rootfsReady == null) {
        HostStartupSplash(
            phase = RuntimePhase.STARTING,
            title = stringResource(R.string.runtime_checking),
            onRetry = {}
        )
        return
    }

    if (runtimeUi.rootfsReady != true) {
        RootfsSetupScreen(
            progress = runtimeUi.rootfsSetupProgress.first,
            message = runtimeUi.rootfsSetupProgress.second,
            error = runtimeUi.rootfsSetupError,
            onRetry = { runtimeUi.rootfsInstallAttempt++ },
        )
        return
    }

    // 首次进入必须先授予「所有文件访问权限」，否则不能进入引导/主界面。
    if (!ui.hasAllFilesAccess) {
        StoragePermissionGate(onRequest = openAllFilesSettings)
        return
    }

    // 已安装后的每次重新打开：等 Host 真正就绪（markReady / RUNNING）再进 UI。
    // 不能只看 endpoint/port：DSH 打印 Web URL 前端口可能已经监听，此时模型、
    // 插件与会话接口还没准备好，提前进主界面会看到空状态或“未就绪”报错。
    LaunchedEffect(runtimeUi.rootfsReady, runtimeUi.hostRetryKey) {
        if (runtimeUi.rootfsReady != true) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (!runtimeUi.hostProgressReady) {
            if (System.currentTimeMillis() - startedAt > 90_000L) {
                runtimeUi.hostStartupError = true
                runtimeUi.hostStartupDetail = context.getString(R.string.host_startup_timeout)
                break
            }
            val current = runCatching { runtimeManager.getStatus() }.getOrNull()
            if (current == null) {
                kotlinx.coroutines.delay(300)
                continue
            }
            when {
                current.phase == RuntimePhase.RUNNING -> {
                    runtimeUi.hostProgressReady = true
                    runtimeUi.hostStartupError = false
                    runtimeUi.hostStartupDetail = null
                }
                current.phase == RuntimePhase.ERROR -> {
                    runtimeUi.hostStartupError = true
                    runtimeUi.hostStartupDetail = null
                    break
                }
                else -> kotlinx.coroutines.delay(300)
            }
        }
    }
    if (runtimeUi.rootfsReady == true && !runtimeUi.hostProgressReady) {
        HostStartupSplash(
            phase = if (runtimeUi.hostStartupError) RuntimePhase.ERROR else RuntimePhase.STARTING,
            // 失败原因和最近输出必须显示在这里：启动页是唯一入口，只给一个
            // 「重试」按钮会让用户既修不了、也没法把原因发出来。
            detail = runtimeUi.hostStartupDetail ?: if (runtimeUi.hostStartupError) status.lastError else null,
            logTail = if (runtimeUi.hostStartupError) status.logTail else emptyList(),
            onRetry = {
                runtimeUi.hostStartupError = false
                runtimeUi.hostStartupDetail = null
                runtimeUi.hostRetryKey += 1
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { runtimeManager.start(activeWorkspace.absolutePath) }
                    }
                }
            },
            // Host 起不来时仍允许进入应用：设置 → 移动运行时 有完整日志，
            // 也可以在那里导出脱敏诊断包。
            onContinue = { runtimeUi.hostProgressReady = true },
        )
        return
    }
    // 首次打开引导：完全替代旧 Landing 页，完成后写入本地进度，仅首次展示。

    // Host 端口就绪不等于数据就绪：账号与插件目录还要一次 RPC。首屏先停在
    // 加载页，最多等 6 秒；拿到账号结果与插件快照后再放行，避免打开后界面
    // 先空几秒再跳变。
    var startupHydrated by remember { mutableStateOf(false) }
    LaunchedEffect(runtimeUi.hostProgressReady, status.port, runtimeUi.accountHydrated, runtimeUi.pluginsHydrated, pluginSnapshot) {
        if (!runtimeUi.hostProgressReady) {
            startupHydrated = false
            return@LaunchedEffect
        }
        val deadline = System.currentTimeMillis() + 6_000
        while (System.currentTimeMillis() < deadline) {
            if (runtimeUi.accountHydrated && runtimeUi.pluginsHydrated && pluginSnapshot != null) break
            kotlinx.coroutines.delay(120)
        }
        startupHydrated = true
    }
    if (runtimeUi.hostProgressReady && !startupHydrated) {
        HostStartupSplash(
            phase = RuntimePhase.STARTING,
            title = stringResource(R.string.host_loading_data),
            onRetry = {},
        )
        return
    }

    if (runtimeUi.showOnboarding) {
        val funded = accountState.wallets.any { (it.balance.toDoubleOrNull() ?: 0.0) > 0.0 } ||
            accountState.bonusWallets.any { (it.balance.toDoubleOrNull() ?: 0.0) > 0.0 }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        com.deepseek.dsh.feature.runtime.OnboardingScreen(
            funded = funded,
            authenticated = accountState.isSignedIn || accountState.hasApiKey,
            // 授权页的「登录」直接走 Host 官方 OAuth（打开授权链接），
            // 不再先弹一个二次选择对话框。
            onSignIn = { beginHostSignIn() },
            onAddApiKey = { ui.showApiKeyDialog = true },
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
                val updated = ui.currentAppSettings.copy(
                    showCodingView = result.developerTools,
                    transcriptView = result.process.storageValue,
                )
                ui.currentAppSettings = updated
                scope.launch { settingsRepository.updateSettings { updated } }
                runtimeUi.showOnboarding = false
            }
        )
        // 授权页的两个弹窗必须挂在 onboarding 分支里：MainAppHost 在 onboarding
        // 结束后才组合根界面的 SignInDialogs，否则这里点了登录没有弹窗。
        SignInDialogs(
            showSignIn = ui.showSignInDialog,
            onDismissSignIn = { ui.showSignInDialog = false },
            onAddKey = {
                ui.showSignInDialog = false
                ui.showApiKeyDialog = true
            },
            onSignIn = {
                ui.showSignInDialog = false
                beginHostSignIn()
            },
            showApiKey = ui.showApiKeyDialog,
            apiKeyInput = ui.apiKeyInput,
            onApiKeyChange = { ui.apiKeyInput = it },
            onDismissApiKey = { ui.showApiKeyDialog = false },
            onSubmitApiKey = {
                val key = ui.apiKeyInput.trim()
                if (key.isNotBlank()) {
                    scope.launch {
                        // 真正写入 Host：把 Key 写到官方 DeepSeek provider 的
                        // credentials + settings（与设置→模型里保存 Key 同一路径），
                        // 然后刷新模型目录，首轮对话就能用这个 Key。
                        val settingsRaw = dshRepository.rpcRaw(ui.activeHostId, "settings/describe", "{}").getOrNull()
                        val providersRaw = dshRepository.rpcRaw(ui.activeHostId, "llm/listConfigurableProviders", "{}").getOrNull()
                        val providers = com.deepseek.dsh.feature.settings.parseModelProviders(
                            providersRaw, settingsRaw, null, officialModelNamespaces,
                        )
                        val official = providers.firstOrNull {
                            it.id == "deepseek-official" || it.id.contains("deepseek") ||
                                it.keyRef.contains("deepseek")
                        }
                        if (official == null) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.onboarding_key_failed),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                            return@launch
                        }
                        saveModelProvider(
                            scope = scope,
                            repository = dshRepository,
                            hostId = ui.activeHostId,
                            provider = official,
                            key = key,
                            baseUrl = "",
                            models = emptyList(),
                            onSettingsRaw = { runtimeUi.modelSettingsRaw = it },
                            onCredentialsRaw = { runtimeUi.modelCredentialsRaw = it },
                        )
                        // 等 Host 写完再刷新目录；官方 provider 已拿到 key。
                        kotlinx.coroutines.delay(600)
                        runtimeUi.modelProvidersRaw = providersRaw
                        runtimeUi.modelSettingsRaw = dshRepository.rpcRaw(ui.activeHostId, "settings/describe", "{}").getOrNull()
                        dshRepository.refreshModelCatalog(ui.activeHostId)
                        oauthManager.signInWithApiKey(key)
                        ui.apiKeyInput = ""
                        ui.showApiKeyDialog = false
                    }
                }
            },
        )
        }
        return
    }

    // 文件夹名保持固定（用于磁盘路径），界面上按当前语言显示。
    val defaultWorkspaceLabel = stringResource(R.string.workspace_default)
    val activeWorkspaceCwd = "/workspace/${runtimeUi.activeWorkspaceName}"
    val activeWorkspaceFolderPath = java.io.File(activeWorkspace, runtimeUi.activeWorkspaceName).absolutePath

    // 真正的系统文件选择器（SAF）：可多选图片/任意文件，复制进当前工作区后
    // 在输入卡片显示；发送时以 @<工作区路径> 附在消息开头。
    val attachmentPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            val workspaceDir = java.io.File(activeWorkspace, runtimeUi.activeWorkspaceName)
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
                runtimeUi.workspaceRevision += 1
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
    // job/follow 输出窗：目标任务、聚合输出、结算状态行。

    // bridge 插件更新后需要两件事：读到「正在运行」的版本（bridge manifest），
    // 以及重启 Host 让新代码加载。两者都只有 App 层能提供，因此注入给 runtime。
    DisposableEffect(ui.activeHostId, activeWorkspace.absolutePath) {
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
    val currentSessionForTeam by androidx.compose.runtime.rememberUpdatedState(ui.selectedSessionId)
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
                        runCatching { dshRepository.setBundleEnabled(ui.activeHostId, name, true) }
                    }
                    runCatching { dshRepository.refreshBridgePluginCatalog() }
                    runCatching { dshRepository.refreshPluginInventory(ui.activeHostId) }
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
                    runCatching { dshRepository.refreshAgentTeam(ui.activeHostId, currentSessionForTeam) }
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
    if (runtimeUi.showAddWorkspaceDialog) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { runtimeUi.showAddWorkspaceDialog = false },
            title = stringResource(R.string.workspace_new_title),
            closeLabel = stringResource(R.string.action_close),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_cancel),
                    primary = false,
                    enabled = true,
                    onClick = { runtimeUi.showAddWorkspaceDialog = false },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_create),
                    primary = true,
                    enabled = runtimeUi.newWorkspaceName.isNotBlank(),
                    onClick = {
                        val name = runtimeUi.newWorkspaceName.trim()
                        if (name.isNotEmpty()) {
                            val dir = java.io.File(activeWorkspace, name)
                            dir.mkdirs()
                            runtimeUi.activeWorkspaceName = name
                            runtimeUi.workspaceRevision += 1
                            scope.launch {
                                dshRepository.createWorkspace(ui.activeHostId, dir.absolutePath)
                            }
                        }
                        runtimeUi.showAddWorkspaceDialog = false
                    },
                )
            },
            content = {
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = runtimeUi.newWorkspaceName,
                    onValueChange = { runtimeUi.newWorkspaceName = it },
                    placeholder = stringResource(R.string.workspace_name_placeholder),
                )
            },
        )
    }
    runtimeUi.renameWorkspaceFolder?.let { folder ->
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { runtimeUi.renameWorkspaceFolder = null },
            title = stringResource(R.string.workspace_rename_title),
            closeLabel = stringResource(R.string.action_close),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_cancel), primary = false, enabled = true,
                    onClick = { runtimeUi.renameWorkspaceFolder = null },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_save), primary = true,
                    enabled = runtimeUi.renameWorkspaceName.isNotBlank(),
                    onClick = {
                        val target = java.io.File(
                            folder.path.ifBlank { java.io.File(activeWorkspace, folder.name).absolutePath }
                        )
                        val renamed = java.io.File(target.parentFile, runtimeUi.renameWorkspaceName.trim())
                        if (target.exists() && renamed.absolutePath != target.absolutePath) {
                            runCatching { target.renameTo(renamed) }
                            if (folder.name == runtimeUi.activeWorkspaceName) runtimeUi.activeWorkspaceName = runtimeUi.renameWorkspaceName.trim()
                            runtimeUi.workspaceRevision += 1
                            // Host 工作区跟着改名（先按原路径找 workspaceId）。
                            val hostWorkspace = hostWorkspaces.firstOrNull { it.path == folder.path }
                            if (hostWorkspace != null) {
                                scope.launch {
                                    dshRepository.workspaceRename(
                                        ui.activeHostId,
                                        hostWorkspace.id,
                                        runtimeUi.renameWorkspaceName.trim(),
                                    )
                                }
                            }
                        }
                        runtimeUi.renameWorkspaceFolder = null
                    },
                )
            },
            content = {
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = runtimeUi.renameWorkspaceName,
                    onValueChange = { runtimeUi.renameWorkspaceName = it },
                    placeholder = stringResource(R.string.workspace_name_placeholder),
                )
            },
        )
    }

    // 会话重命名：官方 `session/rename`。
    runtimeUi.renameSessionTarget?.let { target ->
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { runtimeUi.renameSessionTarget = null },
            title = stringResource(R.string.row_rename),
            closeLabel = stringResource(R.string.action_close),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_cancel), primary = false, enabled = true,
                    onClick = { runtimeUi.renameSessionTarget = null },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_save), primary = true,
                    enabled = runtimeUi.renameSessionTitle.isNotBlank(),
                    onClick = {
                        val title = runtimeUi.renameSessionTitle.trim()
                        runtimeUi.renameSessionTarget = null
                        if (title.isNotEmpty() && title != target.title) {
                            scope.launch {
                                dshRepository.renameSession(ui.activeHostId, target.sessionId, title)
                            }
                        }
                    },
                )
            },
            content = {
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = runtimeUi.renameSessionTitle,
                    onValueChange = { runtimeUi.renameSessionTitle = it },
                    placeholder = stringResource(R.string.row_rename),
                )
            },
        )
    }

    // 官方 approval/request waterfall：实时弹窗，允许一次 / 拒绝直接回 `$events/result`。
    pendingApprovalWaterfalls.firstOrNull()?.let { waterfall ->
        val toolName = (waterfall.request["toolName"]
            as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty().ifBlank { "tool" }
        val reason = (waterfall.request["reason"]
            as? kotlinx.serialization.json.JsonPrimitive)?.content
        fun answer(approved: Boolean) {
            scope.launch {
                dshRepository.respondApprovalWaterfall(ui.activeHostId, waterfall.eventId, approved)
            }
        }
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { answer(false) },
            title = stringResource(R.string.approval_request_title),
            closeLabel = stringResource(R.string.action_close),
            description = if (reason.isNullOrBlank()) toolName else "$toolName · $reason",
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.approval_reject), primary = false, enabled = true,
                    onClick = { answer(false) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.approval_allow_once), primary = true, enabled = true,
                    onClick = { answer(true) },
                )
            },
            content = {},
        )
    }

    // 官方 user-questions/request：原生回答弹窗（选项 / 自定义回答）。
    pendingQuestions.firstOrNull()?.let { waterfall ->
        val questions = waterfall.request["questions"]
            as? kotlinx.serialization.json.JsonArray
            ?: kotlinx.serialization.json.JsonArray(emptyList())
        UserQuestionDialog(
            questions = questions,
            onSubmit = { answers ->
                scope.launch {
                    dshRepository.respondUserQuestion(ui.activeHostId, waterfall.eventId, answers)
                }
            },
            onDismiss = {
                scope.launch {
                    dshRepository.respondUserQuestion(
                        ui.activeHostId,
                        waterfall.eventId,
                        "{\"answers\":[]}",
                    )
                }
            },
        )
    }

    // job/follow 输出窗。
    runtimeUi.jobOutputTarget?.let { job ->
        JobOutputDialog(
            job = job,
            output = runtimeUi.jobOutputText,
            statusLine = runtimeUi.jobOutputStatus,
            onDismiss = { runtimeUi.jobOutputTarget = null },
        )
    }

    runtimeUi.deleteWorkspaceFolder?.let { folder ->
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { runtimeUi.deleteWorkspaceFolder = null },
            title = stringResource(R.string.workspace_delete_title),
            closeLabel = stringResource(R.string.action_close),
            description = stringResource(R.string.workspace_delete_desc, folder.name),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_cancel), primary = false, enabled = true,
                    onClick = { runtimeUi.deleteWorkspaceFolder = null },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_delete), primary = true, enabled = true,
                    onClick = {
                        val target = java.io.File(
                            folder.path.ifBlank { java.io.File(activeWorkspace, folder.name).absolutePath }
                        )
                        // 先取消 Host 注册，再删本地目录。
                        val hostWorkspace = hostWorkspaces.firstOrNull { it.path == folder.path }
                        if (hostWorkspace != null) {
                            scope.launch { dshRepository.deleteWorkspace(ui.activeHostId, hostWorkspace.id) }
                        }
                        runCatching { target.deleteRecursively() }
                        if (folder.name == runtimeUi.activeWorkspaceName) runtimeUi.activeWorkspaceName = DEFAULT_WORKSPACE_DIR_NAME
                        runtimeUi.workspaceRevision += 1
                        runtimeUi.deleteWorkspaceFolder = null
                    },
                )
            },
        )
    }
    // 文件页：展示工作区（Host 只能看到 Documents/DSH-Workpace），
    // 手机其它位置的文件用系统 picker 选后复制进工作区做「中转」。
    DisposableEffect(ui.activePage, ui.filesDir) {
        val observer = if (ui.activePage == ActivePage.FILES) {
            object : android.os.FileObserver(
                ui.filesDir.absolutePath,
                android.os.FileObserver.CREATE or
                    android.os.FileObserver.DELETE or
                    android.os.FileObserver.MOVED_FROM or
                    android.os.FileObserver.MOVED_TO or
                    android.os.FileObserver.MOVE_SELF or
                    android.os.FileObserver.ATTRIB or
                    android.os.FileObserver.CLOSE_WRITE,
            ) {
                override fun onEvent(event: Int, path: String?) {
                    runtimeUi.filesRevision += 1
                }
            }.apply { startWatching() }
        } else {
            null
        }
        onDispose { observer?.stopWatching() }
    }
    val files = remember(ui.activePage, ui.filesDir, runtimeUi.filesRevision) {
        workspaceManager.listFiles(ui.filesDir)
    }
    // 全文件选择器 → 复制进当前浏览目录（工作区内），Host 才能访问。
    val filesImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.deepseek.dsh.AttachmentImporter.importInto(context, ui.filesDir, uris)
                }
                runtimeUi.filesRevision += 1
                runtimeUi.workspaceRevision += 1
                if (result.added.isNotEmpty()) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(
                            com.deepseek.dsh.feature.files.R.string.files_imported,
                            result.added.size,
                        ),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
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



    // 文件面板内容：FILES 页与聊天右侧栏复用同一份列表与回调。
    val workspaceFilesContent: @Composable () -> Unit = {
        FilesScreen(
            currentPath = ui.filesDir.absolutePath,
            files = files,
            onFileClick = { item ->
                if (item.isDirectory) {
                    ui.filesDir = java.io.File(item.absolutePath)
                }
            },
            onNavigateUp = ui.filesDir.parentFile?.let { parent ->
                {
                    // 不越过工作区根：Host 只能访问这里。
                    if (ui.filesDir.absolutePath != activeWorkspace.absolutePath) {
                        ui.filesDir = parent
                    }
                }
            },
            onImportClick = {
                filesImportLauncher.launch(arrayOf("*/*"))
            },
            onSendFile = { item ->
                // 中转完成后的文件直接加入待发附件：发送时以 @工作区相对路径 引用。
                val rel = item.absolutePath
                    .removePrefix(activeWorkspace.absolutePath)
                    .trimStart('/')
                    .replace('\\', '/')
                if (rel.isNotBlank() && chatAttachments.none { it.workspaceRelativePath == rel }) {
                    chatAttachments.add(
                        com.deepseek.dsh.feature.chat.ChatAttachment(
                            displayName = item.name,
                            workspaceRelativePath = rel,
                            sizeBytes = item.sizeBytes,
                        ),
                    )
                }
                ui.activePage = ActivePage.CHAT
            },
        )
    }


    // 终端面板内容：TERMINAL 页与右侧栏终端标签复用同一份状态与回调。
    val terminalContent: @Composable () -> Unit = {
    
                                TerminalScreen(
                                    buffer = runtimeUi.terminalBuffer,
                                    status = runtimeUi.terminalStatus,
                                    emptyHint = runtimeUi.terminalStatus,
                                    writable = runtimeUi.terminalWritable,
                                    terminals = runtimeUi.terminalList,
                                    activeTerminalId = runtimeUi.activeTerminalId,
                                    shellChoices = runtimeUi.terminalShells,
                                    onSelectTerminal = { id -> runtimeUi.activeTerminalId = id },
                                    onCreateTerminal = { shellPath ->
                                        val sid = ui.selectedSessionId
                                        if (sid != null) {
                                            scope.launch {
                                                val id = "term-" + System.currentTimeMillis()
                                                dshRepository.createTerminal(
                                                    ui.activeHostId, sid, id, shellPath, 80, 24,
                                                )
                                                runtimeUi.terminalList = dshRepository.listTerminals(ui.activeHostId, sid)
                                                runtimeUi.activeTerminalId = id
                                            }
                                        }
                                    },
                                    onRenameTerminal = { id, title ->
                                        val sid = ui.selectedSessionId
                                        if (sid != null) {
                                            scope.launch {
                                                dshRepository.renameTerminal(ui.activeHostId, sid, id, title)
                                                runtimeUi.terminalList = dshRepository.listTerminals(ui.activeHostId, sid)
                                            }
                                        }
                                    },
                                    onInput = { line ->
                                        runtimeUi.terminalConnection?.write(line + "\r")
                                    },
                                    onQuickKey = { key ->
                                        runtimeUi.terminalConnection?.write(key)
                                    },
                                    onClose = {
                                        val sid = ui.selectedSessionId
                                        if (sid != null) {
                                            scope.launch {
                                                dshRepository.closeTerminal(
                                                    ui.activeHostId, sid, runtimeUi.activeTerminalId,
                                                )
                                                runtimeUi.terminalConnection?.close()
                                                runtimeUi.terminalConnection = null
                                                runtimeUi.terminalStatus = context.getString(
                                                    R.string.terminal_closed
                                                )
                                            }
                                        }
                                    },
                                )
                            
    }

    // 插件注册到右侧栏的标签：App 只放容器，标签与内容都由插件贡献。
    val rightPanePluginTabs = com.deepseek.dsh.plugin.nativehost.nativeRightPaneTabs()
    val rightPaneGuideEntries = com.deepseek.dsh.plugin.nativehost.nativeGuideEntries()

    val workspaceFolders = remember(
        sessions, ui.selectedSessionId, activeWorkspace, runtimeUi.workspaceRevision, hostWorkspaces,
    ) {
        // 根目录 DSH-Workspace 本身不是工作区，只展示其子文件夹。
        val workspaceDirs = workspaceManager.listWorkspaceDirectories(activeWorkspace)
            .filter { it.absolutePath != activeWorkspace.absolutePath }
        // Host 的 workspace/follow 投影决定顺序与显示名；没有投影时退回本地目录名。
        val hostByPath = hostWorkspaces.associateBy { it.path }
        val orderedDirs = workspaceDirs.sortedBy { dir ->
            hostWorkspaces.indexOfFirst { it.path == dir.absolutePath }
                .let { if (it < 0) Int.MAX_VALUE else it }
        }
        orderedDirs.mapIndexed { idx, dir ->
            val dirSessions = sessions.filter { s ->
                val wsPath = s.workspacePath
                wsPath != null && (wsPath == dir.absolutePath || wsPath.startsWith(dir.absolutePath))
            }.ifEmpty {
                if (idx == 0) sessions else emptyList()
            }
            val hostSessionOrder = hostByPath[dir.absolutePath]?.sessionIds.orEmpty()
            val orderedSessions = dirSessions.sortedBy { s ->
                hostSessionOrder.indexOf(s.sessionId).let { if (it < 0) Int.MAX_VALUE else it }
            }
            com.deepseek.dsh.feature.sessions.WorkspaceFolder(
                id = "ws_${dir.name.hashCode()}",
                name = hostByPath[dir.absolutePath]?.title?.takeIf { it.isNotBlank() }
                    ?: dir.name.ifEmpty { context.getString(R.string.workspace_main) },
                path = dir.absolutePath,
                sessions = orderedSessions.mapIndexed { sIdx, s ->
                    com.deepseek.dsh.feature.sessions.SessionDisplayItem(
                        sessionId = s.sessionId,
                        title = s.title,
                        isRunning = s.sessionId in runningSessionIds,
                        hasUnread = s.sessionId in unreadSessionIds,
                        updatedAt = s.updatedAt,
                        archived = s.archived,
                        pinned = s.pinned,
                        timeAgo = run {
                            val elapsed = (System.currentTimeMillis() - s.updatedAt).coerceAtLeast(0L)
                            when {
                                elapsed < 60_000L -> context.getString(R.string.time_just_now)
                                elapsed < 60L * 60 * 1000 -> context.getString(R.string.time_minutes, (elapsed / 60_000L).toInt())
                                elapsed < 24L * 60 * 60 * 1000 -> context.getString(R.string.time_hours, (elapsed / (60 * 60 * 1000)).toInt())
                                else -> context.getString(R.string.time_days, (elapsed / (24 * 60 * 60 * 1000)).toInt())
                            }
                        },
                        isActiveDot = (s.sessionId == ui.selectedSessionId)
                    )
                },
                isExpanded = idx == 0
            )
        }
    }

    // 终端页跟随官方插件行：Host 的 Loader 里 `@deepseek-ai/dsh-api-terminal-controller`
    // 启用时才出现。这与官方插件页里那一行的开关是同一个事实源，客户端不加设置项。
    val terminalSupported = remember(bridgePluginCatalog) {
        // 官方终端的开关是可选 bundle「持久终端」(@deepseek-ai/dsh-experimental-terminal-
        // bundle)：它默认关闭，用户在插件页打开后才安装/启用终端。之前按
        // terminal-controller 行判断，而该行属于基础 bundle 且默认启用，导致移动端
        // 默认就露出终端页/标签（电脑版没有）。
        bridgePluginCatalog.bundles.any { bundle ->
            bundle.name == "@deepseek-ai/dsh-experimental-terminal-bundle" && bundle.enabled
        }
    }
    LaunchedEffect(terminalSupported, ui.activePage) {
        if (!terminalSupported && ui.activePage == ActivePage.TERMINAL) ui.activePage = ActivePage.CHAT
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
                        ui.settingsInitialSection = com.deepseek.dsh.feature.settings.SettingsSectionTab.UPDATE
                        ui.activePage = ActivePage.SETTINGS
                    },
                    userTag = if (accountState.isSignedIn) {
                        stringResource(R.string.account_official_tag)
                    } else {
                        stringResource(R.string.account_unauth_tag)
                    },
                    accountBalance = accountState.wallets.firstOrNull()?.let { formatAmount(it.balance) } ?: "¥0.00",
                    isSignedIn = accountState.isSignedIn,
                    onSignIn = { ui.showSignInDialog = true },
                    onSignOut = hostSignOut,
                    workspaces = workspaceFolders,
                    workspaceDisplayName = { name ->
                        if (name == DEFAULT_WORKSPACE_DIR_NAME) defaultWorkspaceLabel else name
                    },
                    selectedWorkspaceName = runtimeUi.activeWorkspaceName,
                    onSelectWorkspace = { folder -> runtimeUi.activeWorkspaceName = folder.name },
                    selectedSessionId = ui.selectedSessionId,
                    onSelectSession = { sessionId ->
                        ui.selectedSessionId = sessionId
                        ui.activePage = ActivePage.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onRenameSession = { item ->
                        runtimeUi.renameSessionTarget = item
                        runtimeUi.renameSessionTitle = item.title
                    },
                    onTogglePinSession = { item ->
                        scope.launch {
                            dshRepository.setSessionPinned(ui.activeHostId, item.sessionId, !item.pinned)
                        }
                    },
                    onToggleArchiveSession = { item ->
                        scope.launch {
                            dshRepository.setSessionArchived(ui.activeHostId, item.sessionId, !item.archived)
                        }
                    },
                    remoteSearchResults = runtimeUi.remoteSearchResults,
                    onSearchQueryChange = { query ->
                        if (query.isBlank()) {
                            runtimeUi.remoteSearchResults = emptyList()
                        } else {
                            scope.launch {
                                val hits = dshRepository.searchSessions(ui.activeHostId, query)
                                val titles = sessions.associate { it.sessionId to it.title }
                                runtimeUi.remoteSearchResults = hits.map { hit ->
                                    val title = titles[hit.sessionId]
                                    hit.sessionId to if (title.isNullOrBlank()) {
                                        hit.snippet
                                    } else {
                                        "$title · ${hit.snippet}"
                                    }
                                }
                            }
                        }
                    },
                    onForkSession = { item ->
                        scope.launch {
                            val forked = dshRepository.forkSession(ui.activeHostId, item.sessionId)
                            if (forked != null) {
                                ui.selectedSessionId = forked
                                ui.activePage = ActivePage.CHAT
                                drawerState.close()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.session_fork_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onNewChatClick = {
                        // 官方行为：新会话在发出第一条消息前不创建 Host 会话；
                        // 离开后自然消失，不会留下空白会话。
                        ui.selectedSessionId = null
                        ui.activePage = ActivePage.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onNavigatePlugins = {
                        scope.launch {
                            drawerState.close()
                            ui.activePage = ActivePage.PLUGINS
                        }
                    },
                    showTerminal = terminalSupported,
                    onNavigateTerminal = {
                        scope.launch {
                            drawerState.close()
                            ui.activePage = ActivePage.TERMINAL
                        }
                    },
                    onNavigateSchedule = {
                        scope.launch {
                            drawerState.close()
                            ui.activePage = ActivePage.AUTOMATION
                        }
                    },
                    onNavigateRuntime = {
                        scope.launch {
                            drawerState.close()
                            ui.activePage = ActivePage.RUNTIME
                        }
                    },
                    onNavigateFiles = {
                        scope.launch {
                            drawerState.close()
                            ui.activePage = ActivePage.FILES
                        }
                    },
                    onAddWorkspace = {
                        runtimeUi.newWorkspaceName = ""
                        runtimeUi.showAddWorkspaceDialog = true
                    },
                    onRenameWorkspace = { folder ->
                        runtimeUi.renameWorkspaceFolder = folder
                        runtimeUi.renameWorkspaceName = folder.name
                    },
                    onDeleteWorkspace = { folder ->
                        runtimeUi.deleteWorkspaceFolder = folder
                    },
                    onMoveWorkspace = { folder, delta ->
                        val index = hostWorkspaces.indexOfFirst { it.path == folder.path }
                        val targetIndex = index + delta
                        if (index >= 0 && delta != 0 &&
                            targetIndex in hostWorkspaces.indices
                        ) {
                            // 向下移动时插到目标后一位之前，向上时插到目标之前；
                            // 目标越界（移到末尾）则省略 beforeWorkspaceId。
                            val beforeId = hostWorkspaces
                                .getOrNull(if (delta > 0) targetIndex + 1 else targetIndex)
                                ?.id
                            scope.launch {
                                dshRepository.moveWorkspace(
                                    ui.activeHostId,
                                    hostWorkspaces[index].id,
                                    beforeId,
                                )
                            }
                        }
                    },
                    onNavigateSettings = {
                        scope.launch {
                            drawerState.close()
                            ui.activePage = ActivePage.SETTINGS
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
            BackHandler(enabled = ui.activePage != ActivePage.CHAT) {
                // 插件子页面（详情 / 更多插件）打开时，系统返回先关子页面；顶栏返回走同一个
                // 信号，两条返回路径的行为保持一致。
                if (ui.activePage == ActivePage.PLUGINS && ui.pluginDetailOpen) {
                    ui.pluginBackSignal += 1
                } else {
                    ui.activePage = ActivePage.CHAT
                }
            }
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
            BackHandler(enabled = ui.rightDrawerOpen) {
                ui.rightDrawerOpen = false
            }
            val chatPage = ui.activePage == ActivePage.CHAT
            val pageHasOwnHeader = ui.activePage == ActivePage.SETTINGS
            Scaffold(
                // 内容区不再让 Scaffold 自动消费系统栏/IME：聊天页自己按
                // WindowInsets.ime 动态留白，其它页在下面补 navigationBars，
                // 避免「窗口已 resize + Scaffold 又加一次 IME 内边距」的双重上移。
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                topBar = {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                        when {
                            chatPage -> ModernTopAppBar(
                                title = if (messages.isEmpty()) {
                                    ""
                                } else {
                                    sessions.find { it.sessionId == ui.selectedSessionId }?.title
                                        ?: stringResource(R.string.app_name)
                                },
                                showModeCapsule = false,
                                onToggleRightPane = {
                                    // 每次打开都从 guide 开始；终端是 guide 里的一枚胶囊，
                                    // 不是抽屉的默认页面。
                                    if (!ui.rightDrawerOpen) ui.rightDrawerTab = "guide"
                                    ui.rightDrawerOpen = !ui.rightDrawerOpen
                                },
                                rightPaneOpen = ui.rightDrawerOpen,
                                teamTasks = teamTasks,
                                hostJobs = hostJobs,
                                onKillJob = { job ->
                                    val sid = ui.selectedSessionId
                                    if (sid != null) {
                                        scope.launch {
                                            dshRepository.killJob(ui.activeHostId, sid, job.id)
                                        }
                                    }
                                },
                                onOpenJobOutput = { job ->
                                    runtimeUi.jobOutputTarget = job
                                    runtimeUi.jobOutputText = ""
                                    runtimeUi.jobOutputStatus = null
                                    scope.launch {
                                        dshRepository.followJob(
                                            ui.activeHostId,
                                            job.owner ?: ui.selectedSessionId,
                                            job.id,
                                        ).collect { frameJson ->
                                            val frame = runCatching {
                                                kotlinx.serialization.json.Json
                                                    .parseToJsonElement(frameJson)
                                                    as? kotlinx.serialization.json.JsonObject
                                            }.getOrNull() ?: return@collect
                                            when ((frame["type"]
                                                as? kotlinx.serialization.json.JsonPrimitive)
                                                ?.content) {
                                                "output" -> {
                                                    val chunks = frame["chunks"]
                                                        as? kotlinx.serialization.json.JsonArray
                                                    chunks?.forEach { chunk ->
                                                        val text = ((chunk
                                                            as? kotlinx.serialization.json.JsonObject)
                                                            ?.get("text")
                                                            as? kotlinx.serialization.json.JsonPrimitive)
                                                            ?.content
                                                        if (!text.isNullOrEmpty()) {
                                                            runtimeUi.jobOutputText = (runtimeUi.jobOutputText + text)
                                                                .takeLast(60000)
                                                        }
                                                    }
                                                }
                                                "status" -> {
                                                    val settled = frame["job"]
                                                        as? kotlinx.serialization.json.JsonObject
                                                    val status = (settled?.get("status")
                                                        as? kotlinx.serialization.json.JsonPrimitive)
                                                        ?.content.orEmpty()
                                                    val detail = (settled?.get("detail")
                                                        as? kotlinx.serialization.json.JsonPrimitive)
                                                        ?.content
                                                    runtimeUi.jobOutputStatus = buildString {
                                                        append(status)
                                                        if (!detail.isNullOrBlank()) {
                                                            append(" · ")
                                                            append(detail)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                inspectorEnabled = sessionInspectorEnabled,
                                onOpenInspector = { ui.sessionInspectorOpen = true },
                                agentTeam = agentTeam,
                                agentTeamLoading = runtimeUi.agentTeamLoading,
                                teamEnabled = agentTeamEnabled,
                                currentSessionId = ui.selectedSessionId,
                                onOpenMember = { memberSessionId ->
                                    ui.selectedSessionId = memberSessionId
                                    ui.activePage = ActivePage.CHAT
                                    scope.launch {
                                        dshRepository.refreshAgentTeam(ui.activeHostId, memberSessionId)
                                    }
                                },
                                onMenuClick = { scope.launch { drawerState.open() } },
                            )

                            !pageHasOwnHeader -> PageBackBar(
                                title = pageTitleOf(ui.activePage),
                                onBack = {
                                    // 插件详情页打开时，App 的返回先关详情；再按一次才离开插件页。
                                    if (ui.activePage == ActivePage.PLUGINS && ui.pluginDetailOpen) {
                                        ui.pluginBackSignal += 1
                                    } else {
                                        ui.activePage = ActivePage.CHAT
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
                        .then(
                            if (chatPage) Modifier
                            else Modifier.navigationBarsPadding()
                        )
                ) {
                    // 更新提示属于页面内容，不属于顶栏：放进内容区顶部，随内容滚动，
                    // 不再压住页面自己的标题栏。
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (runtimeUi.rootfsUpdateAvailable && !runtimeUi.rootfsUpdateBannerDismissed) {
                            RootfsUpdateBanner(
                                installed = runtimeUi.installedRootfsVersion.orEmpty(),
                                available = runtimeUi.availableRootfsVersion.orEmpty(),
                                onUpdate = { runtimeUi.showRootfsUpdatePrompt = true },
                                onDismiss = { runtimeUi.rootfsUpdateBannerDismissed = true },
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                    when (ui.activePage) {
                        ActivePage.CHAT -> PageContentSlot {
                            var reasoningLevel by remember {
                                mutableStateOf(
                                    runCatching {
                                        com.deepseek.dsh.feature.chat.ReasoningLevel.valueOf(ui.currentAppSettings.defaultReasoningLevel)
                                    }.getOrDefault(com.deepseek.dsh.feature.chat.ReasoningLevel.FAST)
                                )
                            }

                            val configuredModel = activeSession?.model ?: ui.currentAppSettings.defaultModel
                            val modelPrefs = remember {
                                context.getSharedPreferences("dsh_model_prefs", android.content.Context.MODE_PRIVATE)
                            }
                            var lastUsedModel by remember {
                                mutableStateOf(modelPrefs.getString("last_model_name", "").orEmpty())
                            }
                            val catalogDefaultName = hostModels
                                .firstOrNull { it.id == catalogInfo.defaultModel }?.name
                            // 官方逻辑：会话的模型来自 Host `modelSelection` 投影（hostSessionModels），
                            // 数据库里的 configuredModel 是 Host 暂时没连上时的兜底；全局 lastUsedModel
                            // 只用于还没有会话的新会话。以前 lastUsedModel 优先，重启后打开旧会话会
                            // 被全局选择覆盖，看起来“没有选中上次用的模型”。
                            fun modelNameOf(value: String?): String? = when {
                                value.isNullOrBlank() -> null
                                value in availableModelNames -> value
                                else -> effectiveModelIdByName.entries
                                    .firstOrNull { it.value == value }?.key
                            }
                            val sessionModelName = modelNameOf(configuredModel)
                            val hostModelName = ui.selectedSessionId?.let { sid ->
                                modelNameOf(hostSessionModels[sid])
                            }
                            val currentModel = when {
                                // Host 目录尚未返回时，先用上次使用的模型占位，避免闪“请选择模型”。
                                availableModelNames.isEmpty() && lastUsedModel.isNotBlank() -> lastUsedModel
                                availableModelNames.isEmpty() -> ""
                                hostModelName != null -> hostModelName
                                sessionModelName != null -> sessionModelName
                                lastUsedModel in availableModelNames -> lastUsedModel
                                // 首次进入：选 Host 目录的默认/最新模型（当前为 DeepSeek-V41-Flash）。
                                else -> catalogDefaultName ?: availableModelNames.first()
                            }
                            LaunchedEffect(currentModel, availableModelNames) {
                                if (currentModel in availableModelNames && currentModel != lastUsedModel) {
                                    lastUsedModel = currentModel
                                    modelPrefs.edit().putString("last_model_name", currentModel).apply()
                                }
                            }
                            LaunchedEffect(hostModelName) {
                                if (!hostModelName.isNullOrBlank() &&
                                    hostModelName != lastUsedModel &&
                                    hostModelName in availableModelNames
                                ) {
                                    lastUsedModel = hostModelName
                                    modelPrefs.edit()
                                        .putString("last_model_name", hostModelName)
                                        .apply()
                                }
                            }
                            // Host 目录还没回来时用上次缓存的目录：推理等级、默认档位
                            // 全部来自 Host 的 modelCatalog，而不是客户端预置文案。
                            val modelGroupsForUi = catalogInfo.groups.ifEmpty { cachedModels.groups }
                            val currentProviderGroup = modelGroupsForUi.firstOrNull { group ->
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
                                .orEmpty()
                            val currentPermission = remember(activeSession?.permissionPreset) {
                                when (activeSession?.permissionPreset) {
                                    "read_only" -> com.deepseek.dsh.feature.chat.PermissionPreset.READ_ONLY
                                    "danger_full_access" -> com.deepseek.dsh.feature.chat.PermissionPreset.FULL_ACCESS
                                    "auto_review" -> com.deepseek.dsh.feature.chat.PermissionPreset.AUTO_REVIEW
                                    else -> com.deepseek.dsh.feature.chat.PermissionPreset.WORKSPACE_WRITE
                                }
                            }

                            ModernChatScreen(
                                messages = messages,
                                streamingText = streamingText,
                                streamingReasoning = streamingReasoning,
                                hostCommands = hostCommands,
                                skills = hostSkills,
                                loadSkills = {
                                    val sid = ui.selectedSessionId
                                    if (sid == null) emptyList()
                                    else dshRepository.listSkills(ui.activeHostId, sid)
                                },
                                onRefreshSkills = {
                                    val sid = ui.selectedSessionId
                                    if (sid != null) {
                                        scope.launch {
                                            runCatching { dshRepository.refreshHostSkills(ui.activeHostId, sid) }
                                        }
                                    }
                                },
                                todos = sessionTodos,
                                goal = sessionGoal,
                                deliverables = sessionDeliverables,
                                queuedMessages = sessionQueuedMessages,
                                onQueueAction = { id, kind, text ->
                                    val sid = ui.selectedSessionId
                                    if (sid != null) {
                                        scope.launch {
                                            dshRepository.updateQueue(ui.activeHostId, sid, id, kind, text)
                                        }
                                    }
                                },
                                changes = sessionChanges,
                                onLoadChangeDiff = { index ->
                                    val sid = ui.selectedSessionId
                                    if (sid == null) null
                                    else dshRepository.loadChangeDiff(ui.activeHostId, sid, index)
                                },
                                onOpenDeliverable = { file ->
                                    // Host 路径 /workspace/<x> 对应本机工作区 <activeWorkspace>/<x>。
                                    val hostPrefix = "/workspace/"
                                    val localPath = when {
                                        file.path.startsWith(hostPrefix) -> java.io.File(
                                            activeWorkspace,
                                            file.path.removePrefix(hostPrefix),
                                        ).absolutePath
                                        file.path.startsWith(activeWorkspace.absolutePath) ->
                                            file.path
                                        else -> null
                                    }
                                    val dir = localPath?.let { java.io.File(it).parentFile }
                                    if (dir != null && dir.exists()) {
                                        ui.filesDir = dir
                                        ui.activePage = ActivePage.FILES
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            file.path,
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                onHostCommand = { line ->
                                    val sid = ui.selectedSessionId
                                    if (sid != null) {
                                        scope.launch {
                                            val result = dshRepository.executeHostCommand(
                                                ui.activeHostId, sid, line,
                                            )
                                            val message = result?.text
                                                ?: result?.kind
                                                ?: context.getString(R.string.cmd_unknown)
                                            android.widget.Toast.makeText(
                                                context,
                                                message,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                                onLoadOlder = {
                                    val sid = ui.selectedSessionId
                                    if (sid != null) {
                                        scope.launch {
                                            dshRepository.loadOlderMessages(ui.activeHostId, sid)
                                        }
                                    }
                                },
                                onAnswerUserQuestion = { callId, questionId, selected ->
                                    val sid = ui.selectedSessionId
                                    if (sid != null) {
                                        scope.launch {
                                            dshRepository.answerUserQuestion(
                                                ui.activeHostId, sid, callId, questionId, selected
                                            )
                                        }
                                    }
                                },
                                onSendMessage = { text ->
                                    println("DSH_SEND start text=$text selected=$ui.selectedSessionId live=$liveSessionIds")
                                    // 斜杠命令走官方 commands/execute；Host 不认识的
                                    // 回退成普通 prompt。
                                    val commandLine = text.trim()
                                    val commandSession = ui.selectedSessionId
                                    val handledAsCommand = commandLine.startsWith("/") &&
                                        commandSession != null
                                    if (handledAsCommand) {
                                        val sid = commandSession!!
                                        scope.launch {
                                            val result = dshRepository.executeHostCommand(
                                                ui.activeHostId, sid, commandLine,
                                            )
                                            if (result != null) {
                                                val message = result.text ?: result.kind
                                                android.widget.Toast.makeText(
                                                    context,
                                                    message,
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                                dshRepository.refreshSessionStats(
                                                    ui.activeHostId, sid,
                                                )
                                            } else {
                                                // Host 不认识这条命令：按普通消息发送。
                                                runCatching {
                                                    dshRepository.sendMessage(
                                                        ui.activeHostId, sid, commandLine,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (!handledAsCommand) {
                                    val localId = "local_${System.nanoTime()}"
                                    val isPlanPrompt = runtimeUi.chatPlanMode
                                    val outgoingText = if (isPlanPrompt) {
                                        context.getString(R.string.plan_prompt_prefix) + text
                                    } else {
                                        text
                                    }
                                    // 图片附件走官方 prompt 的内联 image part（base64），
                                    // 这样视觉模型能直接看到内容；普通文件仍用 @ 引用。
                                    val outgoingAttachments = chatAttachments.toList()
                                    val outgoingImages = outgoingAttachments.mapNotNull { attachment ->
                                        val name = attachment.displayName
                                        val extension = name.substringAfterLast('.', "").lowercase()
                                        val mediaType = when (extension) {
                                            "png" -> "image/png"
                                            "jpg", "jpeg" -> "image/jpeg"
                                            "webp" -> "image/webp"
                                            "gif" -> "image/gif"
                                            else -> return@mapNotNull null
                                        }
                                        // 相对路径可能相对工作区根，也可能相对会话目录
                                        // （附件实际复制进 <root>/<workspaceName>/attachments）。
                                        val relative = attachment.workspaceRelativePath
                                        val file = listOf(
                                            java.io.File(relative),
                                            java.io.File(activeWorkspace, relative),
                                            java.io.File(java.io.File(activeWorkspace, runtimeUi.activeWorkspaceName), relative),
                                        ).firstOrNull { it.isFile }
                                        if (file == null || !file.isFile || file.length() > 20L * 1024 * 1024) {
                                            return@mapNotNull null
                                        }
                                        val encoded = android.util.Base64.encodeToString(
                                            file.readBytes(),
                                            android.util.Base64.NO_WRAP,
                                        )
                                        com.deepseek.dsh.core.protocol.PromptImagePart(
                                            mediaType = mediaType,
                                            base64 = encoded,
                                            name = name,
                                        )
                                    }
                                    chatAttachments.clear()
                                    pendingMessages.add(
                                        ChatMessage(
                                            hostId = ui.activeHostId,
                                            sessionId = ui.selectedSessionId ?: "pending",
                                            messageId = localId,
                                            role = MessageRole.USER,
                                            content = text,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                    runtimeUi.isSending = true
                                    scope.launch {
                                        try {
                                        var sid = ui.selectedSessionId
                                        var createdSession = false
                                        // 官方语义：当前会话存在就一直复用它。liveSessionIds 只表示
                                        // “正在运行”，上一轮结束后就为空；以前用它判断会每发第二条
                                        // 消息就新建会话（于是看起来自动开新会话、没有回复）。
                                        if (sid == null) {
                                            suspend fun createOnce(): String? = dshRepository.createSession(
                                                ui.activeHostId,
                                                text.take(24),
                                                activeWorkspaceCwd,
                                                (ui.pendingAgentPreset ?: ui.currentAppSettings.transcriptView).takeIf { it.isNotBlank() }
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
                                                    "rootfs/root/.dsh/sessions/${workspaceSessionDirName(runtimeUi.activeWorkspaceName)}/$sid"
                                                ).mkdirs()
                                                ui.selectedSessionId = sid
                                                createdSession = true
                                            }
                                        }
                                        if (sid != null) {
                                            println("DSH_SEND session=$sid")
                                            // 只有刚创建的会话需要把当前选择写入 modelSelection；
                                            // 已有会话不能再覆盖（之前每次都按账号 provider 重选，
                                            // Kimi/订阅模型的 provider 被改错，回复变空白）。
                                            if (createdSession) {
                                                dshRepository.selectModel(
                                                    ui.activeHostId,
                                                    sid,
                                                    effectiveModelIdByName[currentModel] ?: currentModel,
                                                    currentProviderGroup?.id
                                                        ?: if (accountState.hasApiKey) "deepseek-official" else "deepseek-account",
                                                )
                                            }
                                            dshRepository.sendMessage(ui.activeHostId, sid, outgoingText, outgoingImages)
                                        } else {
                                            android.widget.Toast.makeText(
                                                context, context.getString(R.string.session_create_failed), android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            pendingMessages.add(
                                                ChatMessage(
                                                    hostId = ui.activeHostId,
                                                    sessionId = "pending",
                                                    messageId = "local_error_${System.nanoTime()}",
                                                    role = MessageRole.ASSISTANT,
                                                    content = "⚠️ " + context.getString(R.string.session_create_failed),
                                                    createdAt = System.currentTimeMillis(),
                                                )
                                            )
                                        }
                                        } finally {
                                            pendingMessages.removeAll { it.messageId == localId }
                                            runtimeUi.isSending = false
                                        }
                                    }
                                    }
                                },
                                isSending = runtimeUi.isSending,
                                workspaceName = if (runtimeUi.activeWorkspaceName == DEFAULT_WORKSPACE_DIR_NAME) {
                                    defaultWorkspaceLabel
                                } else {
                                    runtimeUi.activeWorkspaceName
                                },
                                // 工作区列表来自 Host；选中即切换当前工作区，末行开新建对话框。
                                workspaceNames = workspaceFolders.map { folder ->
                                    if (folder.name == DEFAULT_WORKSPACE_DIR_NAME) defaultWorkspaceLabel else folder.name
                                },
                                onWorkspaceSelected = { name -> runtimeUi.activeWorkspaceName = name },
                                onAddWorkspace = { runtimeUi.newWorkspaceName = ""; runtimeUi.showAddWorkspaceDialog = true },
                                onWorkspaceClick = { scope.launch { drawerState.open() } },
                                isThinking = ui.selectedSessionId != null && ui.selectedSessionId in runningSessionIds,
                                onStopClick = {
                                    val sid = ui.selectedSessionId
                                    if (sid != null) {
                                        scope.launch { dshRepository.cancelSession(ui.activeHostId, sid) }
                                    }
                                },
                                selectedModel = currentModel,
                                selectedModelId = effectiveModelIdByName[currentModel] ?: currentModel,
                                onNativeModelSelect = { provider, model, effort ->
                                    runCatching {
                                        val sid = ui.selectedSessionId
                                            ?: error(context.getString(R.string.no_active_session_fast))
                                        dshRepository.selectModel(
                                            ui.activeHostId,
                                            sid,
                                            model,
                                            provider,
                                            effort,
                                        )
                                    }
                                },
                                availableModels = availableModelNames,
                                agentPresetLabel = com.deepseek.dsh.feature.chat.agentPresetLabel(
                                    ui.pendingAgentPreset ?: ui.currentAppSettings.transcriptView
                                ),
                                agentPresetSelectedId = ui.pendingAgentPreset ?: ui.currentAppSettings.transcriptView,
                                onAgentPresetSelected = { preset ->
                                    // 立即生效：新会话用新预设；当前会话保持原样。
                                    ui.pendingAgentPreset = preset
                                    scope.launch {
                                        settingsRepository.updateSettings { current ->
                                            current.copy(transcriptView = preset)
                                        }
                                    }
                                },                                modelGroups = modelGroupsForUi,
                                onModelSelected = { provider, modelId, name ->
                                    // provider/模型 id 直接来自 Host 模型目录的分组，
                                    // 不再按名字猜 provider（否则 Claude/Kimi 会被写成 deepseek-*）。
                                    val sid = ui.selectedSessionId
                                    val displayName = name.ifBlank { modelId }
                                    // 新会话还没有 sessionId：先把选择记成默认模型，下一条
                                    // 消息创建会话时会带着它走。
                                    if (displayName in availableModelNames) {
                                        lastUsedModel = displayName
                                        modelPrefs.edit().putString("last_model_name", displayName).apply()
                                    }
                                    if (sid != null) {
                                        scope.launch {
                                            val result = dshRepository.selectModel(
                                                ui.activeHostId,
                                                sid,
                                                modelId.ifBlank {
                                                    effectiveModelIdByName[displayName] ?: displayName
                                                },
                                                provider.ifBlank {
                                                    if (accountState.hasApiKey) {
                                                        "deepseek-official"
                                                    } else {
                                                        "deepseek-account"
                                                    }
                                                },
                                                displayName = displayName,
                                            )
                                            result.onSuccess {
                                                // 立刻更新本地选择，别等 Host 投影回来才换标签。
                                                if (displayName in availableModelNames) {
                                                    lastUsedModel = displayName
                                                    modelPrefs.edit()
                                                        .putString("last_model_name", displayName)
                                                        .apply()
                                                }
                                            }.onFailure { error ->
                                                android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(
                                                        R.string.model_switch_failed,
                                                        error.message.orEmpty(),
                                                    ),
                                                    android.widget.Toast.LENGTH_LONG,
                                                ).show()
                                            }
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
                                    ui.selectedSessionId?.let { sid ->
                                        scope.launch {
                                            dshRepository.selectPermissionPreset(ui.activeHostId, sid, presetStr)
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
                                        ui.selectedSessionId?.let { sid ->
                                            dshRepository.selectModel(
                                                ui.activeHostId,
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
                                voiceEnabled = voiceEnabled,
                                voiceRecording = runtimeUi.voiceRecording,
                                voiceSeconds = runtimeUi.voiceSeconds,
                                voiceLevel = runtimeUi.voiceLevel,
                                voiceBusy = runtimeUi.voiceBusy,
                                voiceError = runtimeUi.voiceError,
                                voiceTranscript = runtimeUi.voiceTranscript,
                                onVoiceStart = { startVoice() },
                                onVoiceStop = { stopVoice() },
                                onVoiceCancel = { cancelVoice() },
                                onVoiceTranscriptConsumed = { runtimeUi.voiceTranscript = null },
                                translateReasoning = if (cotTranslationEnabled) {
                                    { text ->
                                        dshRepository.cotTranslationTranslate(
                                            ui.activeHostId,
                                            text,
                                            ui.selectedSessionId ?: "",
                                        )
                                    }
                                } else null,
                                bandSnapshot = runtimeUi.bandSnapshot,
                                bandPressing = runtimeUi.bandPressing,
                                onBandPress = { generation, actionId -> pressClaudeCodeBand(generation, actionId) },
                                attachments = chatAttachments,
                                onRemoveAttachment = { attachment ->
                                    chatAttachments.remove(attachment)
                                },
                                onPickAttachments = {
                                    // SAF 选择器：任意类型文件 + 图片；图片走同一通道。
                                    attachmentPickerLauncher.launch(arrayOf("*/*"))
                                },
                                planMode = runtimeUi.chatPlanMode,
                                onPlanModeChange = { enabled -> runtimeUi.chatPlanMode = enabled },
                            )
                        }

                        ActivePage.FILES -> PageContentSlot { workspaceFilesContent() }

                        ActivePage.PLUGINS -> PageContentSlot {
                            PluginsScreen(
                                surfaces = catalog.surfaces,
                                pluginManagerSnapshot = pluginManagerSnapshot,
                                pluginRows = pluginRows,
                                bridgeCatalog = bridgePluginCatalog,
                                bridgeWarnings = bridgeWarnings,
                                managedPluginPackages = runtimeUi.managedPluginPackages,
                                storeSeedUrl = ui.pluginStoreSeedUrl,
                                initialTab = ui.pluginInitialTab,
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
                                loadCredential = { ref -> dshRepository.credentialStatus(ui.activeHostId, ref) },
                                saveCredential = { ref, value ->
                                    dshRepository.setCredential(ui.activeHostId, ref, value)
                                },
                                backSignal = ui.pluginBackSignal,
                                onDetailOpenChange = { ui.pluginDetailOpen = it },
                                onOpenSurface = { item ->
                                    if (item.kind == SurfaceKind.OVERLAY) {
                                        ui.activeOverlaySurface = item
                                    } else {
                                        ui.activeSheetSurface = item
                                    }
                                },
                                onOpenSdui = { },
                                onInstallPlugin = { pkgName, onProgress ->
                                    installPluginWithProgress(
                                        context = context,
                                        repository = dshRepository,
                                        hostId = ui.activeHostId,
                                        pkgName = pkgName,
                                        installer = appPluginInstaller,
                                        onProgress = onProgress,
                                        onManagedPackages = { runtimeUi.managedPluginPackages = it },
                                        onNativeCompanion = { ui.nativeCompanionAvailable = it },
                                    )
                                },
                                onToggleBundle = { packageName, enabled ->
                                    scope.launch {
                                        val result = dshRepository.setBundleEnabled(ui.activeHostId, packageName, enabled)
                                        // profile 的 bundle 清单在 Host 启动时会被重置成模板，
                                        // 所以把用户明确拨动过的开关记一份，Host 起来后补回去。
                                        result.fold(
                                            onSuccess = { outcome ->
                                                rememberBundleSelection(packageName, enabled)
                                                if (outcome.restartRequired) {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        context.getString(R.string.plugin_toggle_restarting),
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                    if (restartHostForPluginChange()) {
                                                        ui.pluginChangeNotice = null
                                                    } else {
                                                        ui.pluginChangeNotice = listOf(
                                                            context.getString(R.string.restart_dsh_hint),
                                                            restartReasonText(outcome.restartReason),
                                                        )
                                                    }
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
                                        dshRepository.refreshPluginInventory(ui.activeHostId)
                                        dshRepository.refreshPluginSettingsPages()
                                    }
                                },
                                onToggleBundleAwait = { packageName, enabled ->
                                    val result = dshRepository.setBundleEnabled(ui.activeHostId, packageName, enabled)
                                    result.fold(
                                        onSuccess = { outcome ->
                                            rememberBundleSelection(packageName, enabled)
                                            if (outcome.restartRequired) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(R.string.plugin_toggle_restarting),
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                                if (restartHostForPluginChange()) {
                                                    ui.pluginChangeNotice = null
                                                } else {
                                                    ui.pluginChangeNotice = listOf(
                                                        context.getString(R.string.restart_dsh_hint),
                                                        restartReasonText(outcome.restartReason),
                                                    )
                                                }
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
                                    dshRepository.refreshPluginInventory(ui.activeHostId)
                                    dshRepository.refreshPluginSettingsPages()
                                    result.isSuccess
                                },
                                onTogglePluginRow = { entryId, enabled ->
                                    scope.launch {
                                        // 行插件不是 bundle：Host 用 setPluginEnabled 写
                                        // profile patch 的 disabled 覆盖，立即生效；失败时
                                        // refreshPluginInventory 会把开关拨回真实状态。
                                        dshRepository.setPluginEnabled(ui.activeHostId, entryId, enabled)
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
                                        dshRepository.setPluginEnabled(ui.activeHostId, entryId, enabled)
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
                                                runtimeUi.managedPluginPackages = runCatching {
                                                    appPluginInstaller.managedPackages()
                                                }.getOrDefault(runtimeUi.managedPluginPackages)
                                                HostRuntimeHolder.restartHost?.invoke()
                                                kotlinx.coroutines.delay(1_200)
                                            }
                                        } else {
                                            dshRepository.uninstallPlugin(ui.activeHostId, item.pluginId)
                                            // 同包名可能曾被 App 兜底以 Loader 行挂上：摘掉
                                            // 残留的挂载名，避免 Host 重建后它又出现。
                                            appPluginInstaller.stripRowMountName(item.pluginId)
                                        }
                                        surfaceStore.removeSurfaceItem(item.pluginId, item.slot)
                                        dshRepository.refreshPluginInventory(ui.activeHostId)
                                        dshRepository.refreshBridgePluginCatalog()
                                        dshRepository.refreshPluginSettingsPages()
                                    }
                                }
                            ,
                                onCreatePlugin = {
                                    ui.pendingAgentPreset = "cordis"
                                    ui.selectedSessionId = null
                                    ui.activePage = ActivePage.CHAT
                                },
                                nativePluginItems = runtimeUi.nativePluginItems,
                                // 「发现」tab 的数据源：Host 的 pluginMarket Remote。
                                marketBackend = MarketBackend(
                                    loadIndexes = { dshRepository.marketIndexes(ui.activeHostId) },
                                    search = { url, query ->
                                        dshRepository.marketSearch(ui.activeHostId, url, query)
                                    },
                                    saveIndexes = { urls ->
                                        dshRepository.marketSetIndexes(ui.activeHostId, urls)
                                    },
                                ),
                                // 语音输入插件的 Host speech 接口（准备卡片 / 选择服务）。
                                speechHooks = SpeechHooks(
                                    catalog = { dshRepository.speechCatalog(ui.activeHostId) },
                                    configure = { providerId, language ->
                                        dshRepository.speechConfigure(ui.activeHostId, providerId, language)
                                    },
                                    prepare = { providerId, source ->
                                        dshRepository.speechPrepare(ui.activeHostId, providerId, source)
                                    },
                                    cancelPrepare = { providerId ->
                                        dshRepository.speechCancelPrepare(ui.activeHostId, providerId)
                                    },
                                ),
                                onUninstallCompanion = { companion ->
                                    requestApkUninstall(companion.packageName)
                                },
                            )
                        }

                        ActivePage.AUTOMATION -> PageContentSlot {
                            SchedulesScreen(
                                onCreateTask = { ui.activePage = ActivePage.CHAT },
                            )
                        }

                        ActivePage.HOSTS -> {
                            com.deepseek.dsh.feature.hosts.HostsScreen(
                                hosts = availableHosts,
                                activeHostId = ui.activeHostId,
                                onSelectHost = { host ->
                                    ui.activeHostId = host.id
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

                        ActivePage.TERMINAL -> PageContentSlot { terminalContent() }

                        ActivePage.SETTINGS -> AppSettingsPage(
                            settings = ui.currentAppSettings,
                            onSettingsChange = { updated -> ui.currentAppSettings = updated },
                            initialSection = ui.settingsInitialSection,
                            repository = dshRepository,
                            settingsRepository = settingsRepository,
                            activeHostId = ui.activeHostId,
                            scope = scope,
                            availableModels = availableModelNames,
                            modelProvidersRaw = runtimeUi.modelProvidersRaw,
                            onProvidersRaw = { runtimeUi.modelProvidersRaw = it },
                            modelSettingsRaw = runtimeUi.modelSettingsRaw,
                            onSettingsRaw = { runtimeUi.modelSettingsRaw = it },
                            modelCredentialsRaw = runtimeUi.modelCredentialsRaw,
                            onCredentialsRaw = { runtimeUi.modelCredentialsRaw = it },
                            routableProviders = runtimeUi.registeredProviderIds,
                            onRoutableProviders = { runtimeUi.registeredProviderIds = it },
                            builtInPlugins = builtInPluginsList,
                            pluginSlotEntries = pluginSlotEntries,
                            pluginSettingsPages = pluginSettingsPages,
                            runtimeStatus = status,
                            hostPort = status?.port ?: dshApplication.localHostPort,
                            hostToken = status?.token ?: dshApplication.localHostToken,
                            hostPortProvider = {
                                dshApplication.localHostPort.takeIf { it > 0 } ?: (status?.port ?: 0)
                            },
                            onStartRuntime = {
                                scope.launch { runtimeManager.start(activeWorkspace.absolutePath) }
                            },
                            onStopRuntime = { scope.launch { runtimeManager.stop() } },
                            onOpenSettingsDocument = {
                                scope.launch { dshRepository.openSettingsDocument(ui.activeHostId) }
                            },
                            accountName = accountState.profile?.name
                                ?: stringResource(R.string.drawer_default_user),
                            accountBalance = accountState.wallets.firstOrNull()
                                ?.let { formatAmount(it.balance) } ?: "¥0.00",
                            accountPhone = accountState.profile?.contact ?: "",
                            isSignedIn = accountState.isSignedIn,
                            avatarUrl = accountState.profile?.avatarUrl,
                            onSignIn = { ui.showSignInDialog = true },
                            onSignOut = hostSignOut,
                            onManageAccount = { openUrl(accountState.platformUrl) },
                            onCreateBackup = {
                                scope.launch { backupAndDiag.createUserDataBackup() }
                            },
                            onCreateAgentPreset = {
                                // 官方「让 Agent 帮我创建预设模式」：新建创造模式会话。
                                ui.pendingAgentPreset = "cordis"
                                ui.selectedSessionId = null
                                ui.activePage = ActivePage.CHAT
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.agent_presets_create_hint),
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            },
                            onExportDiagnostics = {
                                scope.launch { backupAndDiag.exportDiagnosticsZip() }
                            },
                            onClose = { ui.activePage = ActivePage.CHAT },
                            pluginComponentRefresh = dshRepository.pluginComponentRefreshFlow,
                            pluginRpc = { method, payload ->
                                val first = dshRepository.pluginRpc(
                                    ui.activeHostId,
                                    method,
                                    payload.toString(),
                                )
                                val firstText = first.getOrNull() ?: first.exceptionOrNull()?.message
                                if (ensureMissingPluginDir(context, firstText)) {
                                    dshRepository.pluginRpc(ui.activeHostId, method, payload.toString())
                                } else {
                                    first
                                }
                            },
                            loadPluginSettings = { ns -> dshRepository.pluginSettingsDocument(ns) },
                            savePluginSettings = { ns, patch, revision ->
                                dshRepository.updatePluginSettings(ns, patch, revision)
                            },
                        )

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
        ui.activeSheetSurface?.let { item ->
            SurfaceSheet(
                item = item,
                hostPort = status.port ?: 8080,
                token = status.token,
                onDismissRequest = { ui.activeSheetSurface = null }
            )
        }

        // 插件单例悬浮窗 (v4.0 §7.7)
        ui.activeOverlaySurface?.let { item ->
            PluginOverlayContainer(
                item = item,
                hostPort = status.port ?: 8080,
                token = status.token,
                onClose = { ui.activeOverlaySurface = null }
            )
        }

        // 反向联动：刚装的 JS 插件有原生伴侣时，底部提示可去商店装原生界面。
        ui.nativeCompanionAvailable?.let { companion ->
            CompanionAvailableCard(
                companion = companion,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                onGoInstall = {
                    ui.pluginStoreSeedUrl = companion.storeUrl
                    ui.pluginInitialTab = 1
                    ui.nativeCompanionAvailable = null
                    ui.activePage = ActivePage.PLUGINS
                },
                onIgnore = { ui.nativeCompanionAvailable = null },
            )
        }
        PluginIslandOverlays()
        SignInDialogs(
            showSignIn = ui.showSignInDialog,
            onDismissSignIn = { ui.showSignInDialog = false },
            onAddKey = {
                ui.showSignInDialog = false
                ui.showApiKeyDialog = true
            },
            onSignIn = {
                ui.showSignInDialog = false
                beginHostSignIn()
            },
            showApiKey = ui.showApiKeyDialog,
            apiKeyInput = ui.apiKeyInput,
            onApiKeyChange = { ui.apiKeyInput = it },
            onDismissApiKey = { ui.showApiKeyDialog = false },
            onSubmitApiKey = {
                oauthManager.signInWithApiKey(ui.apiKeyInput)
                ui.apiKeyInput = ""
                ui.showApiKeyDialog = false
            },
        )
    
        // 聊天右侧栏：文件面板。覆盖在内容之上，点遮罩/× 关闭。
        RightSideDrawer(
            open = ui.activePage == ActivePage.CHAT && ui.rightDrawerOpen,
            onClose = { ui.rightDrawerOpen = false },
        ) {
            RightSidebarPanel(
                tab = ui.rightDrawerTab,
                onTabChange = { ui.rightDrawerTab = it },
                previewPath = ui.rightDrawerPreviewPath,
                onPreviewPathChange = { ui.rightDrawerPreviewPath = it },
                files = files,
                currentPath = ui.filesDir.absolutePath,
                onOpenDirectory = { item ->
                    ui.filesDir = java.io.File(item.absolutePath)
                },
                onNavigateUp = ui.filesDir.parentFile?.let { parent ->
                    {
                        if (ui.filesDir.absolutePath != activeWorkspace.absolutePath) {
                            ui.filesDir = parent
                        }
                    }
                },
                onImport = { filesImportLauncher.launch(arrayOf("*/*")) },
                onAttachFile = { item ->
                    // 与文件页一致：加入待发附件，发送时以 @ 相对路径引用。
                    val rel = item.absolutePath
                        .removePrefix(activeWorkspace.absolutePath)
                        .trimStart('/')
                        .replace('\\', '/')
                    if (rel.isNotBlank() && chatAttachments.none { it.workspaceRelativePath == rel }) {
                        chatAttachments.add(
                            com.deepseek.dsh.feature.chat.ChatAttachment(
                                displayName = item.name,
                                workspaceRelativePath = rel,
                                sizeBytes = item.sizeBytes,
                            ),
                        )
                    }
                    ui.rightDrawerOpen = false
                },
                todos = sessionTodos,
                deliverables = sessionDeliverables,
                terminalContent = terminalContent,
                scheduleContent = {
                    SchedulesScreen(onCreateTask = { ui.activePage = ActivePage.CHAT })
                },
                pluginTabs = rightPanePluginTabs,
                guideEntries = rightPaneGuideEntries,
                showTerminal = terminalSupported,
                onClose = { ui.rightDrawerOpen = false },
                onOpenDeliverable = { file ->
                    // Host 的 /workspace/<x> 对应本机工作区 <activeWorkspace>/<x>。
                    val hostPrefix = "/workspace/"
                    val localPath = when {
                        file.path.startsWith(hostPrefix) ->
                            java.io.File(activeWorkspace, file.path.removePrefix(hostPrefix)).absolutePath
                        file.path.startsWith(activeWorkspace.absolutePath) -> file.path
                        else -> null
                    }
                    if (localPath != null) {
                        ui.rightDrawerPreviewPath = localPath
                        ui.rightDrawerTab = "document"
                    }
                },
            )
        }
}

}
