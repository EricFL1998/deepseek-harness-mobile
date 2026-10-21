package com.deepseek.dsh.plugin.nativehost

import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import com.deepseek.dsh.plugin.nativesdk.host.PluginStorage
import kotlinx.serialization.json.JsonElement
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * Restricts a native page to its own component and forwards its events to the Host.
 * Generic events use the existing client-slots/dispatch route; [callRpc] invokes
 * an existing plugin-owned Host route without adding a Host-side protocol.
 */
internal class NativePageHost(
    private val componentId: String,
    private val delegate: PluginHostApi,
    private val pluginRpc: suspend (method: String, payload: JsonObject) -> Result<String>,
    private val dispatch: suspend (componentId: String, event: String, payload: JsonObject) -> Unit,
) : PluginHostApi, PluginRpcHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun callRpc(method: String, payload: JsonObject, onResult: (Result<JsonElement>) -> Unit) {
        scope.launch {
            val result = pluginRpc(method, payload).map { text ->
                kotlinx.serialization.json.Json.parseToJsonElement(text)
            }
            onResult(result)
        }
    }

    override fun dispatchEvent(componentId: String, event: String, payload: JsonObject) {
        require(componentId == this.componentId) { "原生页面只能向自己的组件发送事件" }
        scope.launch { dispatch(componentId, event, payload) }
    }

    override fun openUrl(url: String) = delegate.openUrl(url)
    override fun currentTheme(): PluginTheme = delegate.currentTheme()
    override fun requestCapability(capability: String): Boolean = delegate.requestCapability(capability)
    override fun storage(): PluginStorage = delegate.storage()
}
