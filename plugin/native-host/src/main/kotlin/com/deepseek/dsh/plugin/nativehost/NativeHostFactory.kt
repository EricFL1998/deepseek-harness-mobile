package com.deepseek.dsh.plugin.nativehost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginStorage

/**
 * 组装一个 [PluginHostApi] 实例。
 *
 * 这是个通用宿主桥的**最简可工作**装配：dispatch 走 bridge，openUrl 走系统
 * 浏览器，storage 用内存命名空间（持久化可后续换成 DataStore）。插件 id 与
 * 声明能力在真实加载伴侣 APK 时按 descriptor 填入；这里给一个「总线级」实例，
 * 让落点容器在没有具体插件在场时也能安全工作（dispatch 直接发给 Host）。
 *
 * @param dispatcher 把 dispatchEvent 转发给 bridge `dispatch` op 的实现。
 */
@Composable
fun rememberNativeHostApi(
    dispatcher: suspend (componentId: String, event: String, payload: kotlinx.serialization.json.JsonObject) -> Unit,
): PluginHostApi {
    val theme = rememberPluginTheme()
    return remember(dispatcher) {
        PluginHostApiImpl(
            pluginId = "",
            declaredCapabilities = setOf(
                com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities.OPEN_URL,
                com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities.STORAGE,
            ),
            themeProvider = { theme },
            dispatcher = dispatcher,
            urlOpener = { /* 由 App 注入系统浏览器打开 */ },
            urlAllowlist = { true },
            storageBackend = InMemoryPluginStorage,
            audit = { _, _ -> },
        )
    }
}

/** 内存版键值存储（进程内）；持久化版本后续接 DataStore。 */
private object InMemoryPluginStorage : PluginStorage {
    private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}
