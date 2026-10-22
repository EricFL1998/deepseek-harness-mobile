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


internal fun formatAmount(raw: String): String {
    val clean = raw.removePrefix("¥").trim()
    val num = clean.toDoubleOrNull() ?: return "¥$raw"
    return String.format(java.util.Locale.US, "¥%.2f", num)
}

/**
 * Host 会话目录名：`--workspace-<escaped>--`，非 ASCII 码点转义为 `~XXXX`。
 * 与 Host 实际落盘规则一致，用于在 Android 侧预建会话目录。
 */
internal fun workspaceSessionDirName(workspaceName: String): String {
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
internal fun parseQuotaWindows(body: String?, quotaLabel: String = "Quota"): List<com.deepseek.dsh.feature.chat.ComposerQuota> {
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
internal fun parseWorkspaceId(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return Regex("\"workspaceId\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
}

/**
 * 通用插件目录自愈：插件在 proot 里第一次 mkdir 可能失败（ENOENT/ENOSYS），
 * 从错误里取出它要创建的目录，用 App 进程创建后重试一次。
 *
 * 不按插件名适配：只接受 rootfs 内的 `.dsh/` 路径，任何插件都适用。
 */
internal fun ensureMissingPluginDir(context: android.content.Context, message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val guestPath = Regex("mkdir '([^']+)'").find(message)?.groupValues?.getOrNull(1) ?: return false
    if (!guestPath.startsWith("/root/.dsh/")) return false
    val relative = guestPath.removePrefix("/")
    val target = File(context.filesDir, "rootfs/$relative")
    return runCatching { target.mkdirs() || target.isDirectory }.getOrDefault(false)
}


/** App 侧记住的实验性 bundle 选择（profile 清单会被 Host 启动重置）。 */
