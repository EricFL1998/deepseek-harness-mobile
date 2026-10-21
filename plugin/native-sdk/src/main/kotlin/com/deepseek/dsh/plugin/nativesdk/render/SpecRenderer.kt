package com.deepseek.dsh.plugin.nativesdk.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * dsh-ui spec 的原生渲染器。
 *
 * 这是 SDK 提供的核心能力：插件**不需要自己写 Compose 布局**。模型/插件运行时
 * 产出一份 dsh-ui spec（JSON），交给 [Render] 就画成原生界面——卡片、列表、
 * 表格、图表、步骤条等都由这里按统一规范渲染。
 *
 * 设计纪律：
 * - **主题注入**：所有颜色/字号取自 [PluginTheme]，插件不硬编码；
 * - **优雅降级**：不支持的组件类型（mermaid/plot/3D 等引擎组件）渲染为可读占位，
 *   提示「请在完整 Web 界面查看」，绝不把原始 JSON 暴露出来、也绝不崩溃；
 * - **交互回传**：组件上的动作（点击/输入）经 [PluginHostApi.dispatchEvent] 回传
 *   Host，不在原生端执行业务逻辑。
 */
object SpecRenderer {

    /**
     * 渲染一份完整的 dsh-ui spec 为一张卡片。
     *
     * @param spec 解析后的 spec。
     * @param theme 宿主主题快照（`host.currentTheme()`）。
     * @param host 能力桥；组件交互经它回传。
     * @param modifier 外层修饰符。
     */
    @Composable
    fun Render(
        spec: DshUiSpec,
        theme: PluginTheme,
        host: PluginHostApi,
        modifier: Modifier = Modifier,
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(theme.spacingUnit * 2),
                verticalArrangement = Arrangement.spacedBy(spec.gap.dp),
            ) {
                spec.title?.let { title ->
                    Text(text = title, style = theme.textTitle, color = theme.colorOnSurface)
                }
                for (node in spec.items) {
                    SpecNode(node, theme, host, depth = 0)
                }
            }
        }
    }

    /** 渲染单个组件节点，按 `type` 分派到具体实现（见 [SpecNodes]）。 */
    @Composable
    fun SpecNode(
        node: JsonObject,
        theme: PluginTheme,
        host: PluginHostApi,
        depth: Int,
    ) {
        // 深度保护：过深的嵌套说明 spec 异常，降级为提示而不是无限递归。
        if (depth > MAX_DEPTH) {
            SpecNodes.Unsupported("nested-too-deep", node, theme)
            return
        }
        val type = node["type"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: ""
        SpecNodes.Dispatch(type, node, theme, host, depth)
    }

    /** 组件嵌套深度上限。 */
    private const val MAX_DEPTH = 8
}
