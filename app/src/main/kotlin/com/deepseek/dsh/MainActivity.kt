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
        // 统一走 edge-to-edge：键盘只通过 WindowInsets.ime 报告，系统不再 resize
        // 窗口。否则部分 ROM（如 MIUI）既 resize 又报告 IME inset，聊天页的
        // composer 会被抬高两次、离键盘很远。老系统上 enableEdgeToEdge 会做兼容。
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

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

        // 先套用用户选过的界面语言，再组合界面，避免首帧用错语言。
        AppLanguage.applySaved(this)

        // 初始状态不预置任何插件；插件列表完全由 Host/用户安装产生。
        setContent {
            val app = application as DshApplication
            // 深浅色跟随 Host 的 ui-theme 设置（同一账号与浏览器一致），
            // 未连接 Host 时回落到系统。
            val effectiveDark = when (com.deepseek.dsh.feature.settings.ThemeBridge.preference) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            // 原生插件宿主与主题快照必须在 DshTheme 内部创建：插件页/落点读的
            // PluginTheme 由 MaterialTheme + dshPanelColors 映射而来，放在主题外层
            // 会永远拿到浅色 token（深色插件页文字看不清的根因）。
            DshTheme(darkTheme = effectiveDark) {
                val nativeTheme = com.deepseek.dsh.plugin.nativehost.rememberPluginTheme()
                val nativeHost = com.deepseek.dsh.plugin.nativehost.rememberNativeHostApi(
                    dispatcher = { componentId, event, payload ->
                        app.dshRepository.bridge()?.dispatchUiEvent(componentId, event, payload)
                    },
                )
                androidx.compose.runtime.CompositionLocalProvider(
                    com.deepseek.dsh.plugin.nativehost.NativeHost.LocalHost provides nativeHost,
                    com.deepseek.dsh.plugin.nativehost.NativeHost.LocalTheme provides nativeTheme,
                    com.deepseek.dsh.core.designsystem.LocalConversationFontSize provides
                        (com.deepseek.dsh.feature.settings.ThemeBridge.fontSize ?: 14),
                ) {
                    // 系统栏图标跟随应用深浅色：深色引导页需要浅色状态栏图标。
                    androidx.compose.runtime.SideEffect {
                        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = !effectiveDark
                            isAppearanceLightNavigationBars = !effectiveDark
                        }
                    }
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
                    // bridge 插件落后时在启动后弹窗提醒；rootfs 可选更新只在设置页出现。
                    BridgeUpdatePrompt()
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

internal fun wireProtocolFor(providerId: String): String = when {
    providerId.contains("anthropic", ignoreCase = true) -> "anthropic-messages"
    providerId.contains("google", ignoreCase = true) -> "google-generative-ai"
    providerId.contains("openai-responses", ignoreCase = true) -> "openai-responses"
    else -> "openai-completions"
}
