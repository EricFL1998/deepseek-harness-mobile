package com.deepseek.dsh.plugin.nativehost

import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginModelHost
import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import com.deepseek.dsh.plugin.nativesdk.host.PluginStorage
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 落点贡献的能力桥包装：在普通 [PluginHostApi] 上补齐 [PluginRpcHost]。
 *
 * slot 贡献（composer dock、设置页落点等）与整页原生页面一样，需要读取插件
 * 既有的 Host 状态。RPC 实现统一来自 [NativeHost.rpcHandler]（App 注入的
 * pluginRpc），不新增协议；未连接 Host 时返回可读失败。
 */
internal class SlotBridgeHost(
    private val delegate: PluginHostApi,
) : PluginHostApi, PluginRpcHost, PluginModelHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun selectModel(
        provider: String,
        model: String,
        reasoningEffort: String?,
        onResult: (Result<Unit>) -> Unit,
    ) {
        scope.launch {
            val handler = NativeHost.modelSelectHandler
            if (handler == null) {
                onResult(Result.failure(IllegalStateException("当前页面不支持切换模型")))
                return@launch
            }
            onResult(handler(provider, model, reasoningEffort))
        }
    }

    override fun callRpc(method: String, payload: JsonObject, onResult: (Result<JsonElement>) -> Unit) {
        scope.launch {
            val handler = NativeHost.rpcHandler
            if (handler == null) {
                onResult(Result.failure(IllegalStateException("Host 未连接")))
                return@launch
            }
            onResult(handler(method, payload).map { Json.parseToJsonElement(it) })
        }
    }

    override fun dispatchEvent(componentId: String, event: String, payload: JsonObject) =
        delegate.dispatchEvent(componentId, event, payload)

    override fun openUrl(url: String) = delegate.openUrl(url)
    override fun currentTheme(): PluginTheme = delegate.currentTheme()
    override fun requestCapability(capability: String): Boolean = delegate.requestCapability(capability)
    override fun storage(): PluginStorage = delegate.storage()
}
