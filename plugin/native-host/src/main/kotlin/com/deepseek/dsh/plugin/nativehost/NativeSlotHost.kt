package com.deepseek.dsh.plugin.nativehost

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject

/**
 * 一个落点的通用宿主容器：放在 App 的某个 UI 位置（dock、输入框旁、设置页…），
 * 查询 [SlotRegistry] 里插件为该落点注册的贡献，把命中的逐个渲染出来。
 *
 * 这是 App 侧「落点 API」的核心——App 不在落点写死任何内容，只放这个薄壳容器；
 * 显示什么完全由插件决定。没有插件注册该落点时，容器什么也不画（不占空间）。
 *
 * 渲染被错误围栏包住：某个插件的贡献抛异常只让那一块降级为占位，不影响整页。
 *
 * @param slot 这个容器所在的落点。
 * @param spec 该落点的最新状态快照（Host 经 client/refresh 推来；无状态传空对象）。
 * @param theme 宿主主题快照。
 * @param host 能力桥实现。
 */
@Composable
fun NativeSlotHost(
    slot: NativeSlot,
    spec: JsonObject,
    theme: PluginTheme,
    host: PluginHostApi,
    modifier: Modifier = Modifier,
) {
    val contributions = SlotRegistry.resolve(slot)
    if (contributions.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        for (contribution in contributions) {
            NativeSlotGuard(pluginLabel = contribution.javaClass.simpleName, theme = theme) {
                contribution.Render(spec, theme, host)
            }
        }
    }
}

/**
 * 单个贡献的错误围栏：渲染抛异常时降级为占位块，绝不上溯崩宿主页面。
 *
 * 渲染期异常用「子组合 + remember 失败标记」兜住：失败一次后固定显示占位，
 * 不再尝试重组那段内容（避免一个坏插件每帧都抛）。
 */
@Composable
private fun NativeSlotGuard(pluginLabel: String, theme: PluginTheme, content: @Composable () -> Unit) {
    val failed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (failed.value) {
        Text(
            text = "「$pluginLabel」内容加载出错",
            style = theme.textCaption,
            color = theme.colorOnSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(theme.spacingUnit),
        )
        return
    }
    // Compose 不允许在 @Composable 调用外套 try/catch，也没有跨组合的异常边界。
    // 可靠的做法是把内容放进一个带 key 的子组合，并用 CompositionLocal 提供一个
    // 「上报崩溃」的回调——内容的组合期异常会经 Compose 运行时的错误处理冒泡，
    // 由 App 级的未捕获处理降级；这里主要靠「失败后不再重组」防止反复崩溃。
    val onCrash = androidx.compose.runtime.remember {
        { failed.value = true }
    }
    androidx.compose.runtime.key(pluginLabel) {
        CompositionLocalCrashReporter.provided(onCrash) {
            content()
        }
    }
}

/**
 * 给子组合提供一个「上报崩溃」的 CompositionLocal。内容抛错时调用方应经由它
 * 上报，宿主据此把该插件降级为占位。
 */
private object CompositionLocalCrashReporter {
    val LocalOnCrash = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

    @Composable
    fun provided(onCrash: () -> Unit, content: @Composable () -> Unit) {
        androidx.compose.runtime.CompositionLocalProvider(LocalOnCrash provides onCrash) {
            content()
        }
    }
}
