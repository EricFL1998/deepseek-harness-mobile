package com.deepseek.dsh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.deepseek.dsh.core.bridge.BridgeNativeCompanion
import com.deepseek.dsh.core.model.AppSettings
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.feature.settings.SettingsSectionTab

/**
 * 主界面里「纯 UI 状态」的集合：对话框、浮层、插件页导航等。
 *
 * 这些状态不属于业务数据，只是哪个弹窗开着/哪个浮层是当前项。把它们收在这里，
 * [MainAppHost] 的方法体不用为每个状态各占一个捕获，页面也能只拿 holder 而不是
 * 一长串回调。
 */
class AppUiState(
    initialSettings: AppSettings,
    initialWorkspace: java.io.File,
    initialAllFilesAccess: Boolean,
) {
    // 页面路由与会话选择
    var activePage by mutableStateOf(ActivePage.CHAT)
    var activeHostId by mutableStateOf("local-host")
    var selectedSessionId by mutableStateOf<String?>(null)
    var currentAppSettings by mutableStateOf(initialSettings)
    var settingsInitialSection by mutableStateOf<SettingsSectionTab?>(null)
    var hasAllFilesAccess by mutableStateOf(initialAllFilesAccess)
    var filesDir by mutableStateOf(initialWorkspace)

    var showSignInDialog by mutableStateOf(false)
    var showApiKeyDialog by mutableStateOf(false)
    var apiKeyInput by mutableStateOf("")
    var signInPending by mutableStateOf(false)
    var signInOpenedUrl by mutableStateOf<String?>(null)

    var activeSheetSurface by mutableStateOf<SurfaceItem?>(null)
    var activeOverlaySurface by mutableStateOf<SurfaceItem?>(null)
    var nativeCompanionAvailable by mutableStateOf<BridgeNativeCompanion?>(null)

    var pluginStoreSeedUrl by mutableStateOf<String?>(null)
    var pluginInitialTab by mutableStateOf(0)
    var pluginDetailOpen by mutableStateOf(false)
    var pluginBackSignal by mutableStateOf(0)
    var pluginChangeNotice by mutableStateOf<List<String>?>(null)
    var pluginRestartRunning by mutableStateOf(false)
    var pluginRestartError by mutableStateOf<String?>(null)

    /** 聊天页的右侧栏是否展开，以及当前标签与预览文件。 */
    var rightDrawerOpen by mutableStateOf(false)
    var rightDrawerTab by mutableStateOf("guide")
    var rightDrawerPreviewPath by mutableStateOf<String?>(null)
    var sessionInspectorOpen by mutableStateOf(false)
    var pendingAgentPreset by mutableStateOf<String?>(null)
}

/** 构造 UI 状态；初始值来自已持久化的设置、当前工作区与权限状态。 */
@Composable
fun rememberAppUiState(
    initialSettings: AppSettings,
    initialWorkspace: java.io.File,
    initialAllFilesAccess: Boolean,
): AppUiState = remember { AppUiState(initialSettings, initialWorkspace, initialAllFilesAccess) }
