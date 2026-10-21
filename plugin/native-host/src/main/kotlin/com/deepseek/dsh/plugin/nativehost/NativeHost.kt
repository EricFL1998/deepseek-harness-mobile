package com.deepseek.dsh.plugin.nativehost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme

/**
 * 原生插件宿主门面：App 侧为插件提供能力的总入口。
 *
 * 通过 [LocalNativeHost] 这个 CompositionLocal 暴露给各落点容器，避免把
 * host/theme 一层层穿透参数传下去。App 在根组合（或某页）provide 一次，任何
 * 落点的 [NativeSlotHost] 直接读。
 */
object NativeHost {
    /** 当前生效的能力桥；未 provide 时为 null（落点容器不渲染）。 */
    val LocalHost = staticCompositionLocalOf<PluginHostApi?> { null }

    /** 当前主题快照；未 provide 时为 null。 */
    val LocalTheme = staticCompositionLocalOf<PluginTheme?> { null }

    /**
     * 插件自有 RPC 路由的进程级入口（App 启动/切换 Host 时设置）。
     *
     * 原生页面走 NativePageHost，但 composer dock 等 slot 贡献同样需要读取插件
     * 既有状态（例如额度）。slot 宿主会把普通 PluginHostApi 升级成实现
     * PluginRpcHost 的包装，复用这里同一个 pluginRpc 实现。
     */
    @Volatile
    var rpcHandler: (suspend (method: String, payload: kotlinx.serialization.json.JsonObject) -> Result<String>)? = null

    /** 当前会话的模型/推理档位选择器；由 App 聊天页注入。 */
    @Volatile
    var modelSelectHandler:
        (suspend (provider: String, model: String, reasoningEffort: String?) -> Result<Unit>)? = null
}

/**
 * 一个落点容器的便捷入口：从 CompositionLocal 读 host/theme，缺任一就不渲染。
 *
 * 落点容器调用方只需要这一句：
 *
 * ```kotlin
 * NativeSlot(slot = NativeSlot.COMPOSER_DOCK, spec = currentSpec)
 * ```
 */
@Composable
fun NativeSlot(
    slot: com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot,
    spec: kotlinx.serialization.json.JsonObject,
    modifier: Modifier = Modifier,
) {
    val host = NativeHost.LocalHost.current ?: return
    val theme = NativeHost.LocalTheme.current ?: return
    NativeSlotHost(slot = slot, spec = spec, theme = theme, host = host, modifier = modifier)
}

/**
 * 同一落点的一行紧凑版本：用于 composer 底部状态行这类横向空间。
 *
 * 贡献按 Row 项排列，不占满整行；没有插件注册时同样不占空间。
 */
@Composable
fun NativeSlotInline(
    slot: com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot,
    spec: kotlinx.serialization.json.JsonObject,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val host = NativeHost.LocalHost.current ?: return
    val theme = NativeHost.LocalTheme.current ?: return
    NativeSlotHost(
        slot = slot,
        spec = spec,
        theme = theme,
        host = host,
        modifier = modifier,
        inline = true,
        scrollable = scrollable,
    )
}
