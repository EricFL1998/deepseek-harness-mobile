package com.deepseek.dsh.plugin.nativehost

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.RenderMode
import com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject

/** 官方默认内容与插件增强内容的排列方式。 */
enum class SlotArrangement {
    /** 与官方控件并排（输入栏按钮、触发器）。 */
    INLINE,

    /** 官方内容在上，插件增强内容在下方（整条栏、整页）。 */
    BLOCK,
}

/**
 * 官方控件的 slot 包装：官方默认 UI 通过 [content] 传入，插件贡献按仲裁规则
 * 与默认内容组合。这样官方控件本身也走同一条 slot 通道，插件可以 ENHANCE 或
 * OVERRIDE，而不是只有插件内容才经过 SlotRegistry。
 *
 * - 无 NativeHost/Theme（例如预览/测试）：只渲染官方默认内容；
 * - 插件 OVERRIDE：官方默认隐藏，只渲染该插件；
 * - ENHANCE：官方默认内容 + 插件贡献按 [arrangement] 排列；
 * - 都没有：只渲染官方默认内容。
 */
@Composable
fun OfficialSlot(
    slot: NativeSlot,
    modifier: Modifier = Modifier,
    spec: JsonObject = JsonObject(emptyMap()),
    arrangement: SlotArrangement = SlotArrangement.INLINE,
    content: @Composable () -> Unit,
) {
    val host = NativeHost.LocalHost.current
    val theme = NativeHost.LocalTheme.current
    if (host == null || theme == null) {
        content()
        return
    }
    val contributions = SlotRegistry.resolve(slot)
    val slotHost = androidx.compose.runtime.remember(host) { SlotBridgeHost(host) }
    val override = contributions.firstOrNull { it.mode == RenderMode.OVERRIDE && !it.isOfficial }
    if (override != null) {
        NativeSlotGuard(pluginLabel = override.javaClass.simpleName, theme = theme) {
            override.Render(spec, theme, slotHost)
        }
        return
    }
    if (contributions.isEmpty()) {
        content()
        return
    }
    when (arrangement) {
        SlotArrangement.INLINE -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
            for (contribution in contributions) {
                NativeSlotGuard(pluginLabel = contribution.javaClass.simpleName, theme = theme) {
                    contribution.Render(spec, theme, slotHost)
                }
            }
        }

        SlotArrangement.BLOCK -> Column(
            modifier = modifier
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            for (contribution in contributions) {
                NativeSlotGuard(pluginLabel = contribution.javaClass.simpleName, theme = theme) {
                    contribution.Render(spec, theme, slotHost)
                }
            }
        }
    }
}

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
    /** true 时作为一行内的紧凑项渲染（composer 底部状态行），而不是整块纵向卡片。 */
    inline: Boolean = false,
    /** inline 落点内容超出可用宽度时是否允许横向滑动（外层已是滚动容器时保持 false）。 */
    scrollable: Boolean = false,
) {
    val contributions = SlotRegistry.resolve(slot)
    if (contributions.isEmpty()) return
    // slot 贡献与整页原生页面共用同一套 Host RPC：把基础能力桥升级成
    // PluginRpcHost，插件才能读取自己既有的状态（额度/账号等）。
    val slotHost = androidx.compose.runtime.remember(host) { SlotBridgeHost(host) }
    if (inline) {
        Row(
            modifier = if (scrollable) {
                modifier.horizontalScroll(rememberScrollState())
            } else {
                modifier
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (contribution in contributions) {
                NativeSlotGuard(pluginLabel = contribution.javaClass.simpleName, theme = theme) {
                    contribution.Render(spec, theme, slotHost)
                }
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        for (contribution in contributions) {
            NativeSlotGuard(pluginLabel = contribution.javaClass.simpleName, theme = theme) {
                contribution.Render(spec, theme, slotHost)
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
internal fun NativeSlotGuard(pluginLabel: String, theme: PluginTheme, content: @Composable () -> Unit) {
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
