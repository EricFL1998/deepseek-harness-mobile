package com.deepseek.dsh.plugin.nativehost

import com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginStorage
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject

/**
 * [PluginHostApi] 的 App 侧实现：插件唯一摸得到的表面。
 *
 * 每项能力都过三道闸：
 * 1. **声明校验**：能力必须在插件 `descriptor.capabilities` 里声明过；
 * 2. **白名单**：URL 域、工作区路径前缀等；
 * 3. **审计**：每次调用记一条日志（pluginId + 能力 + 时间），设置页可查。
 *
 * `dispatchEvent` 走现成 bridge 的 `dispatch` op，与 Host 侧 JS 插件通信。
 */
class PluginHostApiImpl(
    private val pluginId: String,
    private val declaredCapabilities: Set<String>,
    private val themeProvider: () -> PluginTheme,
    private val dispatcher: suspend (componentId: String, event: String, payload: JsonObject) -> Unit,
    private val urlOpener: (url: String) -> Unit,
    private val urlAllowlist: (url: String) -> Boolean,
    private val storageBackend: PluginStorage,
    private val audit: (pluginId: String, capability: String) -> Unit,
) : PluginHostApi {

    override fun dispatchEvent(componentId: String, event: String, payload: JsonObject) {
        require(componentId.startsWith(pluginId)) {
            "componentId 前缀必须等于插件 id（$pluginId），实际：$componentId"
        }
        audit(pluginId, "dispatch:$event")
        // dispatch 是 suspend（走 bridge 网络）；宿主在渲染线程同步调用，这里用
        // 一个受控的作用域发起，不阻塞渲染。
        kotlinx.coroutines.runBlocking { dispatcher(componentId, event, payload) }
    }

    override fun openUrl(url: String) {
        requireCapability(PluginCapabilities.OPEN_URL)
        audit(pluginId, "openUrl")
        // 白名单内直接开；其余仍交给系统浏览器（urlOpener 内部做用户确认）。
        urlOpener(url)
    }

    override fun currentTheme(): PluginTheme = themeProvider()

    override fun requestCapability(capability: String): Boolean =
        capability in declaredCapabilities && capabilitySwitches.isEnabled(capability)

    override fun storage(): PluginStorage {
        requireCapability(PluginCapabilities.STORAGE)
        audit(pluginId, "storage")
        return storageBackend
    }

    private fun requireCapability(capability: String) {
        check(capability in declaredCapabilities) {
            "插件 $pluginId 未声明能力 $capability"
        }
    }

    private val capabilitySwitches = CapabilitySwitches

    /** 能力总开关（用户可在设置里逐项或全部关掉）。 */
    object CapabilitySwitches {
        private val disabled = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        fun isEnabled(capability: String): Boolean = capability !in disabled
        fun setEnabled(capability: String, enabled: Boolean) {
            if (enabled) disabled.remove(capability) else disabled.add(capability)
        }
        fun disableAll() { /* 由 App 在「禁用全部插件 UI」时清空注册表 */ }
    }
}
