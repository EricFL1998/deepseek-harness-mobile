package com.example.subscription.nativeui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import java.util.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * composer.dock 的额度胶囊：复用 `dsh-plugin-subscription-oauth` 已有的两个 RPC，
 * 不新增 Host 协议、不复制业务逻辑。
 *
 * - `subscription-oauth.deepseek-balance`：当前选 DeepSeek 账号时，Web 版 UsageChip
 *   显示钱包余额（如 `DeepSeek ¥27.27`）；原生 dock 用同一份数据。
 * - `subscription-oauth.status`：没选中 DeepSeek 时退回订阅套餐的用量窗口
 *   （`5h 42%`），与 Web 版其它 provider 的表现一致。
 *
 * 未登录/没有可展示数据时不占空间。
 */
class SubscriptionDockContribution : SlotContribution() {
    override val slot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes = setOf("subscription.usage-pill")

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val rpc = host as? PluginRpcHost ?: return
        var label by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            // Host 刚启动时 /api 可能先返回 401；重试几次直到 RPC 就绪，
            // 避免旧会话的 dock 在首帧失败后永久空白。
            repeat(6) { attempt ->
                val balance = rpc.await(
                    "subscription-oauth.deepseek-balance",
                    balancePayload(),
                )?.let(::balanceLabel).orEmpty()
                val text = balance.ifBlank {
                    rpc.await("subscription-oauth.status", JsonObject(emptyMap()))
                        ?.let(::quotaLabel)
                        .orEmpty()
                }
                if (text.isNotBlank()) {
                    label = text
                    return@LaunchedEffect
                }
                kotlinx.coroutines.delay(if (attempt == 0) 800L else 2_000L)
            }
        }
        if (label.isBlank()) return
        // 与 composer 底部状态行的 tok/s、缓存命中、上下文读数同款：
        // 无底色、无边框、labelSmall + outline 色，紧随上下文计量之后。
        Text(
            text = label,
            style = theme.textCaption.copy(fontFeatureSettings = "tnum"),
            color = theme.colorOutline,
            modifier = Modifier.padding(horizontal = theme.spacingUnit / 2),
        )
    }
}

/** 把回调式 PluginRpcHost.callRpc 包成可重试的 suspend 调用。 */
private suspend fun PluginRpcHost.await(method: String, payload: JsonObject): JsonElement? =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        callRpc(method, payload) { result ->
            continuation.resumeWith(Result.success(result.getOrNull()))
        }
    }

/** DeepSeek 余额接口要求的时区参数（东为正的整秒），与 Web 版一致。 */
private fun balancePayload(): JsonObject {
    val offsetSeconds = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
    return buildJsonObject {
        put("version", "1")
        put("locale", "zh-CN")
        put("timezoneOffsetSeconds", offsetSeconds)
    }
}

/** 从 `deepseek-balance` 的 value 中取第一个钱包，格式化成 `DeepSeek ¥27.27`。 */
private fun balanceLabel(body: JsonElement): String {
    val value = rpcValue(body) ?: return ""
    val wallet = (value["wallets"] as? JsonArray)
        ?.firstOrNull()
        ?.let { it as? JsonObject }
        ?: return ""
    val currency = (wallet["currency"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val raw = (wallet["balance"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val amount = raw.toDoubleOrNull()?.let { String.format("%.2f", it) } ?: raw
    if (amount.isBlank()) return ""
    val symbol = when (currency) {
        "CNY" -> "¥"
        "USD" -> "$"
        else -> if (currency.isBlank()) "" else "$currency "
    }
    return "DeepSeek $symbol$amount"
}

/** 订阅套餐的用量窗口（Kimi/Grok/GLM 等），与 Web 版用量胶囊同一数据源。 */
private fun quotaLabel(body: JsonElement): String {
    val value = rpcValue(body) ?: return ""
    val providers = value["providers"] as? JsonArray ?: return ""
    for (element in providers) {
        val provider = element as? JsonObject ?: continue
        if ((provider["loggedIn"] as? JsonPrimitive)?.booleanOrNull != true) continue
        val name = providerName(provider) ?: continue
        val window = (provider["usage"] as? JsonArray)
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("windows")
            ?.let { it as? JsonArray }
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?: continue
        val windowLabel = (window["label"] as? JsonPrimitive)?.contentOrNull ?: "额度"
        val percent = (window["usedPercent"] as? JsonPrimitive)?.doubleOrNull ?: continue
        return "$name $windowLabel ${percent.roundToInt()}%"
    }
    return ""
}

/** Host RPC 信封：`{result:{ok,value}}`；页面/落点拿到的通常是整封回包。 */
private fun rpcValue(body: JsonElement): JsonObject? {
    val root = body as? JsonObject ?: return null
    val result = (root["result"] as? JsonObject) ?: root
    if ((result["ok"] as? JsonPrimitive)?.booleanOrNull == false) return null
    return (result["value"] as? JsonObject) ?: (root["value"] as? JsonObject)
}

private fun providerName(provider: JsonObject): String? =
    (provider["displayName"] as? JsonPrimitive)?.contentOrNull
        ?: (provider["provider"] as? JsonPrimitive)?.contentOrNull
