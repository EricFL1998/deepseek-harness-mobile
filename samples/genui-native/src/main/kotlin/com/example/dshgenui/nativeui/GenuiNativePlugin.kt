package com.example.dshgenui.nativeui

import androidx.compose.runtime.Composable
import com.deepseek.dsh.plugin.nativesdk.contract.ComponentRenderer
import com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin
import com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities
import com.deepseek.dsh.plugin.nativesdk.contract.PluginDescriptor
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.render.DshUiSpecParser
import com.deepseek.dsh.plugin.nativesdk.render.SpecRenderer
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import com.deepseek.dsh.plugin.nativesdk.tools.ToolCallContext
import com.deepseek.dsh.plugin.nativesdk.tools.ToolRenderRegistry
import com.deepseek.dsh.plugin.nativesdk.tools.ToolRenderer
import kotlinx.serialization.json.JsonObject

/**
 * GenUI 插件的原生伴侣（自证样例）。
 *
 * JS 插件 `@changfenhuang/dsh-genui` 的 `render_ui` 工具入参就是一份完整
 * dsh-ui spec。这个伴侣接管该工具的原生渲染：把 spec 交给 SpecRenderer 画成
 * 原生界面，用户不再需要切到完整 Web 界面看图表/卡片。
 *
 * 逻辑层（spec 的产生、render_ui 工具的注册）全部在 Host 侧 JS 里，不动。
 */
class GenuiNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "changfenhuang.dsh-genui",
        name = "GenUI 界面",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,
            PluginCapabilities.TOOL_RENDER,
        ),
    )

    override fun createRenderer(): ComponentRenderer = GenuiRenderer()

    init {
        // 接管 render_ui 工具的原生渲染。
        ToolRenderRegistry.register(GenuiToolRenderer())
    }
}

/** 落点渲染器：GenUI 没有专属整页，主要靠工具卡片；保留一个兜底的 spec 渲染。 */
class GenuiRenderer : ComponentRenderer {
    override val supportedTypes = setOf("genui.card")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        val parsed = DshUiSpecParser.fromJson(spec)
        SpecRenderer.Render(spec = parsed, theme = theme, host = host)
    }
}

/**
 * render_ui 工具的原生渲染器：入参 args 里就是完整 dsh-ui spec。
 *
 * 这是整个移植的核心闭环：模型调 render_ui → App 工具卡片处查询注册表 →
 * 命中本渲染器 → SpecRenderer 把 spec 画成原生 UI。
 */
class GenuiToolRenderer : ToolRenderer {
    override val toolNames = setOf("render_ui")

    @Composable
    override fun Render(call: ToolCallContext, theme: PluginTheme, host: PluginHostApi) {
        val spec = DshUiSpecParser.fromToolArgs(call.args) ?: return
        SpecRenderer.Render(spec = spec, theme = theme, host = host)
    }
}
