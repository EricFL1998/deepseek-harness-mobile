package com.dsh.mobile.dsh_plugin_demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import com.deepseek.dsh.plugin.nativesdk.pages.PageContent
import com.deepseek.dsh.plugin.nativesdk.pages.PageEntry
import com.deepseek.dsh.plugin.nativesdk.pages.PageLifecycle
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Demo 设置页：SDK 的「JS 插件 + 原生伴生」接入案例。
 *
 * 页面本身不写死任何业务数据——计数、上次动作、RPC 端点、注册清单全部来自
 * Host 的 `demo.status` / `demo.catalog`。这正是要演示的边界：
 *
 * - JS 插件（`samples/demo-js`）拥有状态与 RPC；
 * - 原生页（本文件）只负责画，以及把用户动作回传；
 * - 用户点「服务端 +1」走 `dispatchEvent`，Host 动作改完状态后 bridge 排
 *   `client/refresh`，页面收到事件再重拉 `demo.status`；
 * - 点「重置」直接调 `demo.reset`，演示纯 RPC 写路径。
 *
 * 所有内容都是虚构演示数据，不访问网络、不读写账号。
 */
class DemoSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "demo",
        title = "Demo 插件",
        subtitle = "JS 插件 + 原生伴生：RPC 与落点接入示例（虚构）",
        placement = PageEntry.Placement.SETTINGS,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        DemoSettingsContent(theme = theme, host = host, refreshEvents = null)
    }

    @Composable
    override fun Content(
        spec: JsonObject,
        theme: PluginTheme,
        host: PluginHostApi,
        refreshEvents: SharedFlow<String>?,
    ) {
        DemoSettingsContent(theme = theme, host = host, refreshEvents = refreshEvents)
    }
}

@Composable
private fun DemoSettingsContent(
    theme: PluginTheme,
    host: PluginHostApi,
    refreshEvents: SharedFlow<String>?,
) {
    val rpc = host as? PluginRpcHost
    val isActive = PageLifecycle.LocalIsActive.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<DemoStatus?>(null) }
    var catalog by remember { mutableStateOf<DemoCatalog?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    /** 重新拉一次权威状态；所有动作结束后都回到这里。 */
    fun refresh() {
        refreshKey += 1
    }

    LaunchedEffect(rpc, refreshKey) {
        val rpcHost = rpc
        if (rpcHost == null) {
            error = "Host RPC 未连接：先安装并启用 dsh-plugin-demo"
            return@LaunchedEffect
        }
        val body = rpcHost.demoCall(DemoRpc.STATUS)
        if (!isActive()) return@LaunchedEffect
        val value = DemoRpc.value(body)
        if (value != null) {
            status = DemoRpc.statusOf(value)
            error = null
        } else {
            error = DemoRpc.errorMessage(body, "无法读取 demo.status")
        }
    }

    // 接入清单只读一次：它证明原生端能消费任意 JSON 结构，而不只是 status。
    LaunchedEffect(rpc) {
        val rpcHost = rpc ?: return@LaunchedEffect
        val body = rpcHost.demoCall(DemoRpc.CATALOG)
        if (!isActive()) return@LaunchedEffect
        DemoRpc.value(body)?.let { catalog = DemoRpc.catalogOf(it) }
    }

    // Host 侧动作执行后 bridge 会排 client/refresh；这里按组件身份过滤后重拉。
    LaunchedEffect(refreshEvents) {
        refreshEvents?.collect { componentId ->
            if (componentId == DemoRpc.PAGE_COMPONENT_ID || componentId == DemoRpc.JS_PACKAGE) refresh()
        }
    }

    /** 直接调用一个 RPC，并使用返回的 value 更新页面。 */
    fun callRpcAndApply(method: String) {
        val rpcHost = rpc ?: return
        if (busy) return
        busy = true
        scope.launch {
            val body = rpcHost.demoCall(method)
            if (isActive()) {
                val value = DemoRpc.value(body)
                if (value != null) {
                    status = DemoRpc.statusOf(value)
                    error = null
                } else {
                    error = DemoRpc.errorMessage(body, "操作失败：$method")
                }
            }
            busy = false
        }
    }

    /** 把「服务端 +1」作为原生事件回传给 JS 插件，而不是在 APK 里改状态。 */
    fun dispatchBump() {
        val payload = buildJsonObject {
            put("actionId", "bump")
            put("by", 1)
        }
        runCatching {
            host.dispatchEvent(DemoRpc.PAGE_COMPONENT_ID, DemoRpc.EVENT_SUBMIT, payload)
        }.onFailure { failure ->
            error = failure.message ?: "事件回传失败"
        }
        // bridge 未安装时的兜底：稍后主动重拉一次，页面不会停在旧值。
        scope.launch {
            delay(700)
            if (isActive()) refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(theme.spacingUnit * 2),
        verticalArrangement = Arrangement.spacedBy(theme.spacingUnit),
    ) {
        Text("Demo 插件", style = theme.textTitle, color = theme.colorOnSurface)
        Text(
            "JS 插件 dsh-plugin-demo 负责状态与 RPC；这个页面是原生伴生 APK。" +
                "页面上的数据全部来自 Host，原生端不自己维护业务状态。",
            style = theme.textBody,
            color = theme.colorOnSurfaceVariant,
        )

        // ── Host 状态（demo.status） ─────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Column(
                modifier = Modifier.padding(theme.spacingUnit * 2),
                verticalArrangement = Arrangement.spacedBy(theme.spacingUnit),
            ) {
                Text("Host 状态", style = theme.textBody, color = theme.colorOnSurface)
                Text(
                    "RPC · demo.status",
                    style = theme.textCaption,
                    color = theme.colorOutline,
                )
                when {
                    rpc == null -> Text(
                        "Host RPC 未连接：安装并启用 dsh-plugin-demo 后重进本页。",
                        style = theme.textCaption,
                        color = theme.colorError,
                    )

                    status == null -> Text(
                        error ?: "读取中…",
                        style = theme.textCaption,
                        color = if (error != null) theme.colorError else theme.colorOnSurfaceVariant,
                    )

                    else -> {
                        val current = status!!
                        DemoValueRow("计数", current.count.toString(), theme)
                        DemoValueRow("上次动作", current.lastAction, theme)
                        DemoValueRow("更新时间", DemoRpc.formatClock(current.updatedAt), theme)
                        Text(
                            current.greeting,
                            style = theme.textCaption,
                            color = theme.colorOnSurfaceVariant,
                        )
                    }
                }
                if (error != null && status != null) {
                    Text(error!!, style = theme.textCaption, color = theme.colorError)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit),
                ) {
                    Button(
                        onClick = { refresh() },
                        enabled = rpc != null && !busy,
                        modifier = Modifier.weight(1f),
                        shape = theme.shapeChip,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.colorPrimary,
                            contentColor = theme.colorOnPrimary,
                        ),
                    ) {
                        Text("刷新", style = theme.textCaption)
                    }
                    OutlinedButton(
                        onClick = { dispatchBump() },
                        enabled = rpc != null,
                        modifier = Modifier.weight(1f),
                        shape = theme.shapeChip,
                    ) {
                        Text("服务端 +1", style = theme.textCaption)
                    }
                    OutlinedButton(
                        onClick = { callRpcAndApply(DemoRpc.RESET) },
                        enabled = rpc != null && !busy,
                        modifier = Modifier.weight(1f),
                        shape = theme.shapeChip,
                    ) {
                        Text("重置", style = theme.textCaption)
                    }
                }
                Text(
                    "「服务端 +1」→ dispatchEvent → Host 动作 → client/refresh → 重读 demo.status；" +
                        "「重置」直接调 demo.reset。",
                    style = theme.textCaption,
                    color = theme.colorOnSurfaceVariant,
                )
            }
        }

        // ── 接入清单（demo.catalog） ─────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Column(
                modifier = Modifier.padding(theme.spacingUnit * 2),
                verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2),
            ) {
                Text("接入清单", style = theme.textBody, color = theme.colorOnSurface)
                Text(
                    "RPC · demo.catalog",
                    style = theme.textCaption,
                    color = theme.colorOutline,
                )
                catalog?.registrations.orEmpty().forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${entry.slot} → ${entry.nativeSlot}",
                                style = theme.textCaption,
                                color = theme.colorOnSurface,
                            )
                            Text(
                                entry.purpose,
                                style = theme.textCaption,
                                color = theme.colorOnSurfaceVariant,
                            )
                        }
                        Text(
                            entry.componentId,
                            style = theme.textCaption,
                            color = theme.colorOutline,
                        )
                    }
                }
                catalog?.endpoints.orEmpty().takeIf { it.isNotEmpty() }?.let { endpoints ->
                    HorizontalDivider(color = theme.colorOutline.copy(alpha = 0.3f))
                    Text(
                        "RPC 端点：${endpoints.joinToString(" · ")}",
                        style = theme.textCaption,
                        color = theme.colorOnSurfaceVariant,
                    )
                }
            }
        }

        // ── 全部原生落点总览 ────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Column(
                modifier = Modifier.padding(theme.spacingUnit * 2),
                verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2),
            ) {
                Text(
                    "已注入 ${DemoSlots.entries.count { it.hostReady }} / ${DemoSlots.entries.size} 个原生落点",
                    style = theme.textBody,
                    color = theme.colorOnSurface,
                )
                Text(
                    "其中 WEB_ISLAND_FULL 是整岛 Web 回退容器（宿主已就绪），" +
                        "Demo 不注入它，避免整屏覆盖 App；其余 21 个全部注入。",
                    style = theme.textCaption,
                    color = theme.colorOnSurfaceVariant,
                )
            }
        }

        DemoSlots.entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = theme.textBody, color = theme.colorOnSurface)
                    Text(entry.detail, style = theme.textCaption, color = theme.colorOnSurfaceVariant)
                }
                Text(
                    entry.slot.name,
                    style = theme.textCaption,
                    color = theme.colorOutline,
                )
                if (!entry.hostReady) {
                    Text(
                        "不注入",
                        style = theme.textCaption,
                        color = theme.colorOnSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = theme.colorOutline.copy(alpha = 0.3f))
        }

        Text(
            "真实插件应把数据和动作放在 Host，通过 PluginRpcHost / dispatchEvent 通信；" +
                "Demo 只演示接入形状，不含真实业务。",
            style = theme.textCaption,
            color = theme.colorOnSurfaceVariant,
        )
    }
}

@Composable
private fun DemoValueRow(label: String, value: String, theme: PluginTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = theme.textBody, color = theme.colorOnSurfaceVariant)
        Text(value, style = theme.textBody, color = theme.colorOnSurface)
    }
}
