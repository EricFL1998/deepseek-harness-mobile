package com.deepseek.dsh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.deepseek.dsh.core.data.ClaudeCodeBandSnapshot
import com.deepseek.dsh.core.model.TerminalInfo
import com.deepseek.dsh.core.protocol.HostJobInfo
import com.deepseek.dsh.core.protocol.SessionRawFrame
import com.deepseek.dsh.core.protocol.TerminalConnection
import com.deepseek.dsh.feature.sessions.SessionDisplayItem
import com.deepseek.dsh.feature.sessions.WorkspaceFolder
import com.deepseek.dsh.plugin.nativehost.NativePluginItem

/**
 * 主界面的运行期 UI 状态：模型目录缓存、插件页开关、语音、终端、rootfs 安装
 * 进度、Host 启动提示、工作区/重命名/任务输出等对话框。
 *
 * 与 [AppUiState] 一样，目的是把状态从 MainAppHost 的方法体里搬出来：状态一多，
 * 巨型 Composable 的捕获和寄存器数量就会把 ART 校验顶爆。
 */
class AppRuntimeUiState(
    initialOnboarding: Boolean,
    initialRootfsProgress: Pair<Float, String>,
) {
    var showOnboarding by mutableStateOf(initialOnboarding)

    // 模型 provider 目录原始数据
    var modelProvidersRaw by mutableStateOf<String?>(null)
    var modelSettingsRaw by mutableStateOf<String?>(null)
    var modelCredentialsRaw by mutableStateOf<String?>(null)
    var registeredProviderIds by mutableStateOf<List<String>>(emptyList())
    var agentTeamLoading by mutableStateOf(false)

    // 主界面插件页与安装
    var managedPluginPackages by mutableStateOf(emptySet<String>())
    var nativePluginItems by mutableStateOf<List<NativePluginItem>>(emptyList())

    // Claude Code mods 状态条
    var bandSnapshot by mutableStateOf<ClaudeCodeBandSnapshot?>(null)
    var bandPressing by mutableStateOf(false)

    // 语音输入
    var voiceRecording by mutableStateOf(false)
    var voiceSeconds by mutableStateOf(0)
    var voiceLevel by mutableStateOf(0f)
    var voiceBusy by mutableStateOf(false)
    var voiceError by mutableStateOf<String?>(null)
    var voiceTranscript by mutableStateOf<String?>(null)

    // 会话/发送状态
    var isSending by mutableStateOf(false)
    var chatPlanMode by mutableStateOf(false)
    var wasSessionRunning by mutableStateOf(false)
    var didInitialResume by mutableStateOf(false)
    var rawSessionFrames by mutableStateOf<List<SessionRawFrame>>(emptyList())

    // rootfs 安装与更新
    var rootfsReady by mutableStateOf<Boolean?>(null)
    var rootfsSetupProgress by mutableStateOf(initialRootfsProgress)
    var rootfsSetupError by mutableStateOf<String?>(null)
    var rootfsInstallAttempt by mutableStateOf(0)
    var rootfsUpdateAvailable by mutableStateOf(false)
    var installedRootfsVersion by mutableStateOf<String?>(null)
    var availableRootfsVersion by mutableStateOf<String?>(null)
    var showRootfsUpdatePrompt by mutableStateOf(false)
    var rootfsUpdateBannerDismissed by mutableStateOf(false)
    var forceRootfsInstall by mutableStateOf(false)

    // Host 启动提示
    var hostProgressReady by mutableStateOf(false)
    var hostStartupError by mutableStateOf(false)
    var hostStartupDetail by mutableStateOf<String?>(null)
    var hostRetryKey by mutableStateOf(0)

    // 终端
    var terminalBuffer by mutableStateOf("")
    var terminalStatus by mutableStateOf("")
    var terminalWritable by mutableStateOf(false)
    var terminalList by mutableStateOf<List<TerminalInfo>>(emptyList())
    var activeTerminalId by mutableStateOf("main")
    var terminalShells by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var terminalConnection by mutableStateOf<TerminalConnection?>(null)
    var pluginsHydrated by mutableStateOf(false)
    var accountHydrated by mutableStateOf(false)
    var localHostBound by mutableStateOf(false)

    // 工作区与各类对话框
    var workspaceRevision by mutableStateOf(0)
    var activeWorkspaceName by mutableStateOf(DEFAULT_WORKSPACE_DIR_NAME)
    var showAddWorkspaceDialog by mutableStateOf(false)
    var newWorkspaceName by mutableStateOf("")
    var renameWorkspaceFolder by mutableStateOf<WorkspaceFolder?>(null)
    var renameWorkspaceName by mutableStateOf("")
    var renameSessionTarget by mutableStateOf<SessionDisplayItem?>(null)
    var renameSessionTitle by mutableStateOf("")
    var remoteSearchResults by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var jobOutputTarget by mutableStateOf<HostJobInfo?>(null)
    var jobOutputText by mutableStateOf("")
    var jobOutputStatus by mutableStateOf<String?>(null)
    var deleteWorkspaceFolder by mutableStateOf<WorkspaceFolder?>(null)

    // 文件/工作区刷新计数
    var filesRevision by mutableStateOf(0)
}

/** 构造运行期 UI 状态；初始值依赖 context 与首次引导标记。 */
@Composable
fun rememberAppRuntimeUiState(
    initialOnboarding: Boolean,
    initialRootfsProgress: Pair<Float, String>,
): AppRuntimeUiState = remember { AppRuntimeUiState(initialOnboarding, initialRootfsProgress) }
