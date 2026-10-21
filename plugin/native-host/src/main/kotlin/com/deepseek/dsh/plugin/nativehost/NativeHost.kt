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
