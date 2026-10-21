package com.deepseek.dsh.plugin.nativesdk.tools

import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject

/**
 * tool call 渲染钩子：插件接管某个工具调用结果的原生渲染。
 *
 * 模型调用一个工具（`render_ui`、`web_search`、自定义工具…）后，App 在工具卡片处
 * 查询 [ToolRenderRegistry]：某插件声明了渲染这个工具，就把该工具的调用上下文
 * （工具名/状态/入参/产出）交给它的渲染器画成原生 UI；没有插件接管时，App 用默认
 * 的工具卡片样式。
 *
 * 典型例子：GenUI 插件的 `render_ui` 工具——入参里就是完整 dsh-ui spec，插件用
 * `SpecRenderer` 直接画出来。
 */

/** 一次工具调用的上下文。 */
data class ToolCallContext(
    /** 工具名（`render_ui` / `web_search` / …）。 */
    val name: String,
    /** 调用状态（`running` / `done` / `error`）。 */
    val status: String,
    /** 入参（JSON 字符串；`render_ui` 的入参里就是完整 spec）。 */
    val args: String,
    /** 产出正文。 */
    val body: String,
    /** 调用 id。 */
    val callId: String,
)

/** 一个工具的原生渲染器。 */
interface ToolRenderer {
    /** 本渲染器接管的工具名集合（精确匹配）。 */
    val toolNames: Set<String>

    /** 渲染这次工具调用的结果。 */
    @Composable
    fun Render(call: ToolCallContext, theme: PluginTheme, host: PluginHostApi)
}

/**
 * 工具渲染注册表。插件在入口处注册自己的工具渲染器；App 在工具卡片处按
 * 工具名查询接管者。
 */
object ToolRenderRegistry {
    private val renderers = mutableMapOf<String, ToolRenderer>()

    /** 注册一个工具渲染器；同一工具名后注册的覆盖先注册的。 */
    fun register(renderer: ToolRenderer) {
        for (name in renderer.toolNames) renderers[name] = renderer
    }

    /** 取某工具的渲染器；没有则 null（App 用默认卡片）。 */
    fun rendererFor(toolName: String): ToolRenderer? = renderers[toolName]

    /** 卸载一批工具渲染器（插件卸载时）。 */
    fun unregister(renderer: ToolRenderer) {
        renderers.entries.removeAll { it.value === renderer }
    }
}
