package com.dsh.mobile.dsh_plugin_demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import com.deepseek.dsh.plugin.nativesdk.render.PluginPopover
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 输入栏 dock 的 Demo 胶囊：RPC 读状态 + 原生事件写状态。
 *
 * 它演示 SDK 里最常用的两种通信，且都不在 APK 内持有业务状态：
 *
 * - **读**：`PluginRpcHost.callRpc("demo.status")` 取 JS 插件的 Host 状态；
 * - **写**：popover 里的「服务端 +1」把 `{ actionId: "bump" }` 经
 *   `dispatchEvent` 回传 `dsh-plugin-demo#demo-dock`，由 JS 插件的
 *   `mobileUi` 动作改状态；bridge 随后排 `client/refresh`。
 *
 * 样式契约同样保持：浮层用 [PluginPopover]（贴锚点 + App 菜单材质），
 * 颜色/字号/圆角全部来自 [PluginTheme]，不自己画 Card。
 */
class DemoDockContribution : SlotContribution() {
    override val slot: NativeSlot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes: Set<String> = setOf("demo.dock")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val rpc = host as? PluginRpcHost
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<DemoStatus?>(null) }
        var open by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        suspend fun loadState(): DemoStatus? {
            val rpcHost = rpc ?: return null
            val body = rpcHost.demoCall(DemoRpc.STATUS)
            val value = DemoRpc.value(body)
            return if (value != null) {
                error = null
                DemoRpc.statusOf(value)
            } else {
                error = DemoRpc.errorMessage(body, "读取失败")
                null
            }
        }

        // Host 刚启动时 /api 可能先返回 401；重试几次直到 RPC 就绪。
        LaunchedEffect(rpc) {
            if (rpc == null) return@LaunchedEffect
            repeat(3) { attempt ->
                loadState()?.let {
                    status = it
                    return@LaunchedEffect
                }
                delay(if (attempt == 0) 800L else 2_000L)
            }
        }

        // 展示 Host 的权威值：每 30 秒重读一次，原生端不自己累加。
        LaunchedEffect(rpc, status != null) {
            if (rpc == null || status == null) return@LaunchedEffect
            while (true) {
                delay(30_000)
                loadState()?.let { status = it }
            }
        }

        val current = status
        Box {
            Text(
                text = if (current != null) "Demo ${current.count}" else "Demo ·",
                style = theme.textCaption.copy(fontWeight = FontWeight.Medium),
                color = theme.colorPrimary,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { open = !open },
                            onLongPress = { open = true },
                        )
                    }
                    .padding(horizontal = theme.spacingUnit / 2),
            )
            if (open) {
                PluginPopover(theme = theme, onDismiss = { open = false }) {
                    Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                        Text(
                            text = "Demo 插件",
                            style = theme.textPopupTitle,
                            color = theme.colorOnSurfaceVariant,
                        )
                        if (current == null) {
                            Text(
                                text = error ?: "Host RPC 未连接",
                                style = theme.textPopupBody,
                                color = theme.colorError,
                            )
                        } else {
                            DemoRow("状态", "已连接", theme)
                            DemoRow("计数", current.count.toString(), theme)
                            DemoRow("上次动作", current.lastAction, theme)
                            DemoRow("更新时间", DemoRpc.formatClock(current.updatedAt), theme)
                            DemoRow("Uptime", "${current.uptimeMs / 1000}s", theme)
                        }
                        HorizontalDivider(color = theme.colorPopupBorder)
                        DemoActionRow(
                            label = "服务端 +1 · 事件",
                            theme = theme,
                            onClick = {
                                val payload = buildJsonObject {
                                    put("actionId", "bump")
                                    put("by", 1)
                                }
                                runCatching {
                                    host.dispatchEvent(
                                        DemoRpc.DOCK_COMPONENT_ID,
                                        DemoRpc.EVENT_SUBMIT,
                                        payload,
                                    )
                                }.onFailure { error = it.message ?: "事件回传失败" }
                                scope.launch {
                                    delay(700)
                                    loadState()?.let { status = it }
                                }
                            },
                        )
                        DemoActionRow(
                            label = "刷新 · RPC",
                            theme = theme,
                            onClick = {
                                scope.launch { loadState()?.let { status = it } }
                            },
                        )
                        DemoActionRow(
                            label = "重置 · RPC",
                            theme = theme,
                            onClick = {
                                val rpcHost = rpc ?: return@DemoActionRow
                                scope.launch {
                                    val body = rpcHost.demoCall(DemoRpc.RESET)
                                    DemoRpc.value(body)?.let { status = DemoRpc.statusOf(it) }
                                }
                            },
                        )
                        Text(
                            text = "点击外部关闭",
                            style = theme.textCaption,
                            color = theme.colorOutline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoRow(label: String, value: String, theme: PluginTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = theme.textPopupBody, color = theme.colorOnSurfaceVariant)
        Text(
            value,
            style = theme.textPopupBody.copy(fontWeight = FontWeight.Medium),
            color = theme.colorOnSurface,
        )
    }
}

@Composable
private fun DemoActionRow(label: String, theme: PluginTheme, onClick: () -> Unit) {
    Text(
        text = label,
        style = theme.textPopupBody,
        color = theme.colorPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = theme.spacingUnit / 4),
    )
}
