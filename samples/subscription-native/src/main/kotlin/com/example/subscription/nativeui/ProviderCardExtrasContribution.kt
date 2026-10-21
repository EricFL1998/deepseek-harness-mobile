package com.example.subscription.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * 设置 → 模型页每个 provider 卡片下的订阅扩展块（官方 keyed slot
 * `settings.models.provider-card`，entryKey = subscriptions）。
 *
 * 对应 Web 版 ProviderCardExtras：连接状态、账号数、用量窗口、连接/取消。
 * 数据全部走插件既有 RPC，原生不新增协议。
 */
class ProviderCardExtrasContribution : SlotContribution() {
    override val slot = NativeSlot.SETTINGS_PROVIDER_CARD
    override val supportedTypes = setOf("subscription.provider-card")

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val rpc = host as? PluginRpcHost ?: return
        val providerId = spec["provider"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (providerId.isBlank()) return
        var status by remember(providerId) { mutableStateOf<JsonObject?>(null) }
        var usage by remember(providerId) { mutableStateOf<JsonObject?>(null) }
        var busy by remember(providerId) { mutableStateOf(false) }

        suspend fun reload() {
            status = rpc.await("subscription-oauth.status", JsonObject(emptyMap()))
                ?.let(::rpcValue)
            usage = rpc.await(
                "subscription-oauth.usage",
                buildJsonObject { put("provider", providerId) },
            )?.let(::rpcValue)
        }

        LaunchedEffect(providerId) {
            repeat(6) { attempt ->
                reload()
                if (status != null) return@LaunchedEffect
                kotlinx.coroutines.delay(if (attempt == 0) 800L else 2_000L)
            }
        }
        // Web 版每 60 秒刷新状态/用量；原生同样周期刷新，离开页面自动取消。
        LaunchedEffect(providerId, status != null) {
            if (status == null) return@LaunchedEffect
            while (true) {
                kotlinx.coroutines.delay(60_000)
                reload()
            }
        }

        val provider = status?.let { findProvider(it, providerId) } ?: return
        val displayName = provider.str("displayName").ifBlank {
            spec["displayName"]?.jsonPrimitive?.contentOrNull ?: providerId
        }
        val loggedIn = (provider["loggedIn"] as? JsonPrimitive)?.booleanOrNull == true
        val pending = provider["busy"]?.jsonPrimitive?.booleanOrNull == true
        val connectable = (provider["connectable"] as? JsonPrimitive)?.booleanOrNull != false
        val accounts = (provider["accounts"] as? JsonArray)?.size ?: 0

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(theme.spacingUnit * 1.5f),
                verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = theme.textBody.copy(fontWeight = FontWeight.SemiBold),
                        color = theme.colorOnSurface,
                    )
                    Text(
                        text = when {
                            loggedIn -> " 已连接 · $accounts 个账号"
                            pending -> " 等待授权…"
                            else -> " 未登录"
                        },
                        style = theme.textCaption,
                        color = if (loggedIn) theme.colorPrimary else theme.colorOnSurfaceVariant,
                    )
                }
                usageRows(usage).forEach { (label, percent) ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, style = theme.textCaption, color = theme.colorOnSurfaceVariant)
                            Text("$percent%", style = theme.textCaption, color = theme.colorOnSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { (percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (connectable) {
                    if (pending) {
                        OutlinedButton(
                            onClick = {
                                busy = true
                                rpc.callRpc("subscription-oauth.cancel", buildJsonObject { put("provider", providerId) }) {
                                    busy = false
                                }
                            },
                            enabled = !busy,
                        ) { Text("取消") }
                    } else {
                        OutlinedButton(
                            onClick = {
                                busy = true
                                rpc.callRpc("subscription-oauth.login", buildJsonObject { put("provider", providerId) }) { result ->
                                    busy = false
                                    val promptUrl = result.getOrNull()?.let(::rpcValue)
                                        ?.get("prompt")?.let { it as? JsonObject }
                                        ?.get("url")?.jsonPrimitive?.contentOrNull
                                    if (!promptUrl.isNullOrBlank()) host.openUrl(promptUrl)
                                }
                            },
                            enabled = !busy,
                        ) { Text(if (accounts == 0) "连接" else "添加账号") }
                    }
                }
            }
        }
    }
}

private fun findProvider(status: JsonObject, providerId: String): JsonObject? =
    (status["providers"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.str("provider") == providerId }

private fun usageRows(value: JsonObject?): List<Pair<String, Int>> {
    val accounts = value?.get("usage") as? JsonArray ?: return emptyList()
    val rows = mutableListOf<Pair<String, Int>>()
    for (element in accounts) {
        val account = element as? JsonObject ?: continue
        val plan = account.str("plan").ifBlank { account.str("account") }
        val windows = account["windows"] as? JsonArray ?: continue
        for (windowElement in windows) {
            val window = windowElement as? JsonObject ?: continue
            val label = window.str("label").ifBlank { "额度" }
            val percent = (window["usedPercent"] as? JsonPrimitive)?.doubleOrNull?.roundToInt() ?: continue
            val name = if (plan.isBlank()) label else "$plan · $label"
            rows += name to percent
        }
    }
    return rows.take(4)
}

private fun rpcValue(body: JsonElement): JsonObject? {
    val root = body as? JsonObject ?: return null
    val result = (root["result"] as? JsonObject) ?: root
    if ((result["ok"] as? JsonPrimitive)?.booleanOrNull == false) return null
    return (result["value"] as? JsonObject) ?: (root["value"] as? JsonObject)
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private suspend fun PluginRpcHost.await(method: String, payload: JsonObject): JsonElement? =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        callRpc(method, payload) { result ->
            continuation.resumeWith(Result.success(result.getOrNull()))
        }
    }
