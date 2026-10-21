package com.example.subscription.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginModelHost
import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * conversation.input.right 的快切模式按钮（Web 版 FastModeButton）。
 *
 * - 用 `subscription-oauth.model-variants {provider,id}` 判断当前模型是否有 fast 变体；
 * - 有变体时显示闪电；点击通过 [PluginModelHost] 把 reasoningEffort 切到
 *   provider 自己的 rung（如 highspeed/lightspeed），再点切回默认；
 * - model id 不变，避免把模型选择器里的行换掉。
 */
class FastModeContribution : SlotContribution() {
    override val slot = NativeSlot.COMPOSER_RIGHT
    override val supportedTypes = setOf("subscription.fast-mode")
    override val order: Int = 10

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val rpc = host as? PluginRpcHost ?: return
        val modelHost = host as? PluginModelHost ?: return
        val modelId = spec["modelId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val currentEffort = spec["reasoningEffort"]?.jsonPrimitive?.contentOrNull.orEmpty()
        var provider by remember(modelId) { mutableStateOf("") }
        var rung by remember(modelId) { mutableStateOf<String?>(null) }
        var pending by remember(modelId) { mutableStateOf(false) }
        var failure by remember(modelId) { mutableStateOf<String?>(null) }

        LaunchedEffect(modelId) {
            repeat(6) { attempt ->
                val resolved = resolveProvider(rpc, modelId)
                if (resolved != null) {
                    provider = resolved
                    val variants = rpc.await(
                        "subscription-oauth.model-variants",
                        JsonObject(
                            mapOf(
                                "provider" to JsonPrimitive(resolved),
                                "id" to JsonPrimitive(modelId),
                            )
                        ),
                    )?.let(::rpcValue)
                    if ((variants?.get("fast") as? JsonPrimitive)?.booleanOrNull == true) {
                        rung = (variants["fastRung"] as? JsonPrimitive)?.contentOrNull ?: "fast"
                    } else if ((variants?.get("pending") as? JsonPrimitive)?.booleanOrNull != true) {
                        return@LaunchedEffect
                    }
                    return@LaunchedEffect
                }
                kotlinx.coroutines.delay(if (attempt == 0) 800L else 1_500L)
            }
        }

        val fastRung = rung ?: return
        if (provider.isBlank()) return
        val active = currentEffort == fastRung
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (active) theme.colorPrimary else Color.Transparent)
                .clickable(enabled = !pending) {
                    pending = true
                    failure = null
                    modelHost.selectModel(provider, modelId, if (active) null else fastRung) { result ->
                        pending = false
                        failure = result.exceptionOrNull()?.message
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⚡",
                style = theme.textBody,
                color = if (active) theme.colorOnPrimary else theme.colorOnSurfaceVariant,
            )
        }
    }
}

private suspend fun resolveProvider(rpc: PluginRpcHost, modelId: String): String? {
    if (modelId.startsWith("deepseek-account/")) return "deepseek-account"
    val status = rpc.await("subscription-oauth.status", JsonObject(emptyMap()))?.let(::rpcValue) ?: return null
    val providers = status["providers"] as? JsonArray ?: return null
    for (element in providers) {
        val provider = element as? JsonObject ?: continue
        val id = (provider["provider"] as? JsonPrimitive)?.contentOrNull ?: continue
        if ((provider["defaultModel"] as? JsonPrimitive)?.contentOrNull == modelId) return id
        val models = provider["models"] as? JsonArray ?: continue
        if (models.any { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == modelId }) return id
    }
    return null
}

private fun rpcValue(body: JsonElement): JsonObject? {
    val root = body as? JsonObject ?: return null
    val result = (root["result"] as? JsonObject) ?: root
    if ((result["ok"] as? JsonPrimitive)?.booleanOrNull == false) return null
    return (result["value"] as? JsonObject) ?: (root["value"] as? JsonObject)
}

private suspend fun PluginRpcHost.await(method: String, payload: JsonObject): JsonElement? =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        callRpc(method, payload) { result ->
            continuation.resumeWith(Result.success(result.getOrNull()))
        }
    }
