package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.core.bridge.BridgeRegistry
import kotlinx.coroutines.flow.SharedFlow

/**
 * 插件界面页：直接运行插件自己的**完整 Web 界面**。
 *
 * 原生静态转译兜底已随 `plugin/mobile-ui` 一并移除——同一个 Host、同一份会话
 * cookie 下，插件自己的服务商列表、登录态、实时用量以及登录/登出这些运行时
 * 操作都在真实 Web 客户端里发生；不再维护一份「声明结构」的原生副本。
 *
 * 这是插件界面唯一的返回条：设置根页面在子页面打开时不再画顶层 Header。
 *
 * @param componentId `<package>#<id>` 形式的组件身份（Web 端用它定位 settings.section）。
 * @param title 页面标题（也作为 Web 客户端分区的候选标签）。
 * @param registry bridge 门面；为 null 时显示「bridge 未连接」。
 * @param onBack 返回上一级。
 * @param refreshEvents 插件请求刷新的组件 id 流（`client/refresh`），命中即重载 Web 页。
 * @param hostPort 本地 Host 的 loopback 端口。
 * @param hostToken Host 启动 token。
 * @param hostPortProvider 需要时再取一次 Host 端口：Host 异步就绪，组合时的值可能是 0。
 */
@Composable
fun PluginComponentPane(
    componentId: String,
    title: String,
    registry: BridgeRegistry?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    refreshEvents: SharedFlow<String>? = null,
    hostPort: Int = 0,
    hostToken: String? = null,
    hostPortProvider: (() -> Int)? = null,
    /** 原生页未接管的原因（伴侣未加载 / 未注册设置页 / id 不匹配）；null 表示没有原生候选。 */
    nativeFallbackReason: String? = null,
    /** 作为设置页子内容嵌入时传 false：返回条由设置页顶层 Header 提供。 */
    showHeader: Boolean = true,
) {
    var webReloadKey by remember { mutableIntStateOf(0) }
    var webLoading by remember { mutableStateOf(true) }
    var webFailed by remember { mutableStateOf<String?>(null) }

    // Host 是异步就绪的：组合时端口可能是 0。等它出现后自动加载 Web 界面。
    val availablePort: Int = hostPort.takeIf { it > 0 }
        ?: hostPortProvider?.invoke()?.takeIf { it > 0 }
        ?: 0
    val webAvailable = availablePort > 0
    var livePortValue by remember { mutableIntStateOf(0) }

    LaunchedEffect(webAvailable) {
        if (webAvailable && livePortValue == 0) {
            livePortValue = availablePort
            webFailed = null
            webLoading = true
            webReloadKey += 1
        }
    }

    // 事件驱动刷新：插件在 Host 侧响应过回传后，bridge 排一条 client/refresh，
    // 命中本组件就重载 Web 页让它取到最新状态。
    LaunchedEffect(refreshEvents, componentId) {
        refreshEvents?.collect { refreshed ->
            if (refreshed == componentId) {
                webFailed = null
                webLoading = true
                webReloadKey += 1
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = when {
                            registry == null -> "bridge 未连接"
                            webFailed != null -> "完整界面 · 加载失败"
                            else -> "完整界面 · 真实 Web UI"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    webFailed = null
                    webLoading = true
                    webReloadKey += 1
                }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        if (nativeFallbackReason != null) {
            // 装过伴生 APK 却仍走 Web UI 时，必须让用户看到是哪一步没对上：
            // 伴侣没加载、没注册设置页，还是注册的 id 与分区 id 不一致。
            Text(
                text = nativeFallbackReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (registry == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    Text(
                        text = "插件界面暂不可用",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "bridge 未连接。等 Host 就绪后重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                PluginWebIsland(
                    port = livePortValue.takeIf { it > 0 } ?: availablePort,
                    token = hostToken,
                    reloadKey = webReloadKey,
                    // Web 客户端同一个壳里走：用本地化 label 与注册 id 两个候选把
                    // 它自己带到这个插件的 settings.section。
                    focusSettingsLabels = remember(componentId, title) {
                        listOf(title, componentId.substringAfter('#'))
                            .filter { it.isNotBlank() }
                            .distinct()
                    },
                    focusSectionId = componentId.substringAfter('#').takeIf { it.isNotBlank() },
                    modifier = Modifier.fillMaxSize(),
                    onLoadingChanged = { webLoading = it },
                    onFailed = { message -> webFailed = message },
                )
                if (webLoading && webFailed == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                // Web 客户端没有启动成功时，页面本身只是一张启动失败卡片；给出
                // 原生可读的原因和一个重试入口，不让用户把「Host 半边坏了」误认为
                // 「插件界面就是这样」。
                webFailed?.let { reason ->
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "完整界面启动失败",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = reason.take(320),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = {
                                webFailed = null
                                webLoading = true
                                webReloadKey += 1
                            }) { Text("重试") }
                        }
                    }
                }
            }
        }
    }
}
