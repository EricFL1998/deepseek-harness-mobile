package com.example.subscription.nativeui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * composer.dock 的用量/余额胶囊，复刻插件 Web 版 UsageChip：
 *
 * - 当前模型属于 DeepSeek 账号 → `subscription-oauth.deepseek-balance`，显示 `DeepSeek ¥24.80`；
 * - 当前模型属于插件自己的 provider（Kimi/Grok/GLM…）→ `subscription-oauth.usage`，
 *   显示套餐窗口的百分比；
 * - 其它 provider → `subscription-oauth.ccswitch-balance`，显示该 provider 的余额。
 *
 * 点击/长按打开与 Web 版同等的明细面板。provider 由 App 通过 spec.modelId 传入，
 * 原生端不自己猜当前选择。
 */
class SubscriptionDockContribution : SlotContribution() {
    override val slot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes = setOf("subscription.usage-pill")

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val rpc = host as? PluginRpcHost ?: return
        val modelId = spec["modelId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        var data by remember(modelId) { mutableStateOf<DockData?>(null) }
        var panelOpen by remember(modelId) { mutableStateOf(false) }

        LaunchedEffect(modelId) {
            // Host 刚启动时 /api 可能先返回 401；重试几次直到 RPC 就绪。
            repeat(6) { attempt ->
                val loaded = loadDockData(rpc, modelId)
                if (loaded != null) {
                    data = loaded
                    return@LaunchedEffect
                }
                kotlinx.coroutines.delay(if (attempt == 0) 800L else 2_000L)
            }
        }
        // Web 版 UsageChip 每 60 秒刷新；原生 dock 同样周期刷新。
        LaunchedEffect(modelId, data != null) {
            if (data == null) return@LaunchedEffect
            while (true) {
                kotlinx.coroutines.delay(60_000)
                loadDockData(rpc, modelId)?.let { data = it }
            }
        }

        val current = data ?: return
        Box {
            Text(
                text = current.compact,
                style = theme.textCaption.copy(fontFeatureSettings = "tnum"),
                color = theme.colorOutline,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { panelOpen = !panelOpen },
                            onLongPress = { panelOpen = true },
                        )
                    }
                    .padding(horizontal = theme.spacingUnit / 2),
            )

            // 贴着 pill 的锚定浮层；材质/圆角/描边/阴影来自 App 的 DshMenuCard 映射，
            // 与模型菜单、权限菜单同一套样式，不是插件自造 Card。
            if (panelOpen) {
                com.deepseek.dsh.plugin.nativesdk.render.PluginPopover(
                    theme = theme,
                    onDismiss = { panelOpen = false },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(theme.spacingUnit),
                    ) {
                        Text(
                            text = current.title,
                            style = theme.textPopupTitle,
                            color = theme.colorOnSurfaceVariant,
                        )
                        current.rows.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = theme.textPopupBody,
                                    color = theme.colorOnSurfaceVariant,
                                )
                                Text(
                                    text = value,
                                    style = theme.textPopupBody.copy(fontWeight = FontWeight.Medium),
                                    color = theme.colorOnSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DockData(
    val compact: String,
    val title: String,
    val rows: List<Pair<String, String>>,
)

private suspend fun loadDockData(rpc: PluginRpcHost, modelId: String): DockData? {
    // 1) DeepSeek 账号：Web 版直接读账号余额，不走套餐 usage。
    if (modelId.startsWith("deepseek-account/")) {
        balanceData(rpc)?.let { return it }
    }
    // 2) 插件自己的 provider：用模型 id 在 status 里反查 provider，再读 usage。
    val status = rpc.await("subscription-oauth.status", JsonObject(emptyMap()))
    val provider = resolveProvider(status, modelId)
    if (provider != null && provider == "deepseek-account") {
        balanceData(rpc)?.let { return it }
    }
    if (provider != null) {
        usageData(rpc, status, provider)?.let { return it }
    }
    // 3) 其它 provider（OpenAI 兼容等）：cc-switch 余额。
    val guess = modelId.substringBefore('/').takeIf { it.isNotBlank() && it != modelId }
    if (guess != null) ccSwitchData(rpc, guess)?.let { return it }
    // 4) 没有 modelId（旧宿主）/无法判定时：保持原行为，先余额后退回第一个已登录套餐。
    balanceData(rpc)?.let { return it }
    return status?.let { fallbackUsageData(it) }
}

private fun resolveProvider(status: JsonElement?, modelId: String): String? {
    if (modelId.isBlank()) return null
    if (modelId.startsWith("deepseek-account/")) return "deepseek-account"
    val providers = rpcValue(status ?: return null)?.get("providers") as? JsonArray ?: return null
    for (element in providers) {
        val provider = element as? JsonObject ?: continue
        val id = (provider["provider"] as? JsonPrimitive)?.contentOrNull ?: continue
        val defaultModel = (provider["defaultModel"] as? JsonPrimitive)?.contentOrNull
        if (defaultModel == modelId) return id
        val models = provider["models"] as? JsonArray ?: continue
        if (models.any { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == modelId }) {
            return id
        }
    }
    return null
}

private suspend fun balanceData(rpc: PluginRpcHost): DockData? {
    val value = rpcValue(rpc.await("subscription-oauth.deepseek-balance", balancePayload()) ?: return null) ?: return null
    val wallets = value["wallets"] as? JsonArray ?: return null
    val rows = wallets.mapNotNull { element ->
        val wallet = element as? JsonObject ?: return@mapNotNull null
        val currency = wallet.str("currency")
        val amount = wallet.str("balance")
        if (currency.isBlank() && amount.isBlank()) return@mapNotNull null
        val text = formatMoney(currency, amount)
        currency to text
    }
    if (rows.isEmpty()) return null
    val withBonus = rows.toMutableList()
    (value["bonusWallets"] as? JsonArray)?.forEach { element ->
        val wallet = element as? JsonObject ?: return@forEach
        val amount = wallet.str("balance").toDoubleOrNull() ?: return@forEach
        if (amount <= 0) return@forEach
        withBonus += "赠金 ${wallet.str("currency")}" to "+${formatMoney(wallet.str("currency"), wallet.str("balance"))}"
    }
    return DockData(
        compact = "DeepSeek ${rows.first().second}",
        title = "DeepSeek Account 额度",
        rows = withBonus,
    )
}

private suspend fun usageData(rpc: PluginRpcHost, status: JsonElement?, providerId: String): DockData? {
    val providers = rpcValue(status ?: return null)?.get("providers") as? JsonArray
        ?: return null
    val provider = providers.mapNotNull { it as? JsonObject }
        .firstOrNull { it.str("provider") == providerId }
        ?: return null
    val displayName = provider.str("displayName").ifBlank { providerId }
    val payload = buildJsonObject { put("provider", providerId) }
    val value = rpcValue(rpc.await("subscription-oauth.usage", payload) ?: return null) ?: return null
        ?: return null
    val accounts = value["usage"] as? JsonArray ?: return null
    val rows = mutableListOf<Pair<String, String>>()
    var compactPercent: Int? = null
    for (element in accounts) {
        val account = element as? JsonObject ?: continue
        val plan = account.str("plan").ifBlank { account.str("account") }
        val windows = account["windows"] as? JsonArray ?: continue
        for (windowElement in windows) {
            val window = windowElement as? JsonObject ?: continue
            val label = window.str("label").ifBlank { "额度" }
            val percent = window["usedPercent"]?.jsonPrimitive?.doubleOrNull ?: continue
            compactPercent = compactPercent ?: percent.roundToInt()
            val reset = (window["resetsAt"] as? JsonPrimitive)?.longOrNull?.let(::resetLabel)
            val rowLabel = if (plan.isBlank()) label else "$plan · $label"
            rows += rowLabel to buildString {
                append(percent.roundToInt()).append('%')
                if (!reset.isNullOrBlank()) append(" · ").append(reset)
            }
        }
    }
    if (rows.isEmpty()) return null
    return DockData(
        compact = "${shortName(displayName, providerId)} ${rows.first().first} ${compactPercent}%",
        title = "$displayName 用量",
        rows = rows,
    )
}

private suspend fun ccSwitchData(rpc: PluginRpcHost, providerId: String): DockData? {
    val payload = buildJsonObject { put("provider", providerId) }
    val value = rpcValue(rpc.await("subscription-oauth.ccswitch-balance", payload) ?: return null) ?: return null
    val currency = value.str("currency")
    val total = value.str("total")
    if (total.isBlank()) return null
    val text = formatMoney(currency, total)
    return DockData(
        compact = "${shortName(providerId, providerId)} $text",
        title = "$providerId 余额",
        rows = listOf((currency.ifBlank { "余额" }) to text),
    )
}

private fun fallbackUsageData(status: JsonElement): DockData? {
    val providers = rpcValue(status)?.get("providers") as? JsonArray ?: return null
    for (element in providers) {
        val provider = element as? JsonObject ?: continue
        if ((provider["loggedIn"] as? JsonPrimitive)?.booleanOrNull != true) continue
        val value = provider
        val usage = value["usage"] as? JsonArray ?: continue
        val window = usage.firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("windows")
            ?.let { it as? JsonArray }
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?: continue
        val label = window.str("label").ifBlank { "额度" }
        val percent = window["usedPercent"]?.jsonPrimitive?.doubleOrNull?.roundToInt() ?: continue
        val name = shortName(provider.str("displayName"), provider.str("provider"))
        return DockData(
            compact = "$name $label $percent%",
            title = "${provider.str("displayName").ifBlank { name }} 用量",
            rows = listOf(label to "$percent%"),
        )
    }
    return null
}

private fun balancePayload(): JsonObject {
    val offsetSeconds = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
    return buildJsonObject {
        put("version", "1")
        put("locale", "zh-CN")
        put("timezoneOffsetSeconds", offsetSeconds)
    }
}

/** 把回调式 PluginRpcHost.callRpc 包成 suspend。 */
private suspend fun PluginRpcHost.await(method: String, payload: JsonObject): JsonElement? =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        callRpc(method, payload) { result ->
            continuation.resumeWith(Result.success(result.getOrNull()))
        }
    }

/** Host RPC 信封：`{result:{ok,value}}`。 */
private fun rpcValue(body: JsonElement): JsonObject? {
    val root = body as? JsonObject ?: return null
    val result = (root["result"] as? JsonObject) ?: root
    if ((result["ok"] as? JsonPrimitive)?.booleanOrNull == false) return null
    return (result["value"] as? JsonObject) ?: (root["value"] as? JsonObject)
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun resetLabel(resetsAt: Long): String {
    val remaining = resetsAt - System.currentTimeMillis()
    if (remaining <= 0) return "即将重置"
    val hours = remaining / 3_600_000
    return when {
        hours < 1 -> "${(remaining / 60_000).coerceAtLeast(1)}m 后重置"
        hours < 24 -> "${hours}h 后重置"
        else -> "${hours / 24}d 后重置"
    }
}

private fun formatMoney(currency: String, raw: String): String {    val amount = raw.toDoubleOrNull()?.let { String.format("%.2f", it) } ?: raw
    val symbol = when (currency) {
        "CNY" -> "¥"
        "USD" -> "$"
        else -> if (currency.isBlank()) "" else "$currency "
    }
    return "$symbol$amount"
}

private fun shortName(displayName: String, providerId: String): String {
    val known = mapOf(
        "deepseek-account" to "DeepSeek",
        "kimi" to "Kimi",
        "grok" to "Grok",
        "antigravity" to "Anti",
        "glm" to "GLM",
        "ark" to "Ark",
    )
    known[providerId]?.let { return it }
    val first = displayName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    return first.ifBlank { providerId.ifBlank { "用量" } }
}
