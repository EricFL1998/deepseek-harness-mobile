package com.deepseek.dsh.plugin.nativesdk.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * dsh-ui 组件白名单的原生实现。
 *
 * 每个组件一个函数，输入是 spec 里的节点 JSON + 主题 + 能力桥。分派入口是
 * [Dispatch]；不识别的类型一律走 [Unsupported] 降级占位。
 *
 * 覆盖清单（与官方 dsh-ui spec 对齐）：
 * text · callout · badge · stat · hero · progress · list · table · keyvalue ·
 * steps · timeline · code · divider · link · image · card · col · row · grid · chart
 *
 * 引擎组件（mermaid / plot / echart / scene3d / diagram / diff 等）在原生端降级为
 * 可读占位——它们需要 Web 渲染引擎，用户在完整 Web 界面能看到完整形态。
 */
object SpecNodes {

    @Composable
    fun Dispatch(
        type: String,
        node: JsonObject,
        theme: PluginTheme,
        host: PluginHostApi,
        depth: Int,
    ) {
        when (type) {
            "text" -> TextNode(node, theme)
            "callout" -> Callout(node, theme)
            "badge" -> Badge(node, theme)
            "stat" -> Stat(node, theme)
            "hero" -> Hero(node, theme)
            "progress" -> Progress(node, theme)
            "list" -> ListNode(node, theme, host, depth)
            "table" -> Table(node, theme)
            "keyvalue" -> KeyValue(node, theme)
            "steps" -> Steps(node, theme)
            "timeline" -> Timeline(node, theme)
            "code" -> Code(node, theme)
            "divider" -> Divider(theme)
            "link" -> Link(node, theme, host)
            "image" -> ImagePlaceholder(node, theme)
            "card" -> Container(node, theme, host, depth, titled = true)
            "col" -> Container(node, theme, host, depth, titled = false)
            "row" -> RowContainer(node, theme, host, depth)
            "grid" -> GridContainer(node, theme, host, depth)
            "chart" -> Chart(node, theme)
            else -> Unsupported(type, node, theme)
        }
    }

    // --- 文本类 ---

    @Composable
    fun TextNode(node: JsonObject, theme: PluginTheme) {
        val text = node.str("value", "text")
        val style = when (node.str("size")) {
            "title" -> theme.textTitle
            "caption", "small" -> theme.textCaption
            else -> theme.textBody
        }
        Text(text = text, style = style, color = theme.colorOnSurface)
    }

    @Composable
    fun Callout(node: JsonObject, theme: PluginTheme) {
        val tone = node.str("tone")
        val (bg, fg) = when (tone) {
            "success" -> theme.colorSurface to theme.colorPrimary
            "warning" -> theme.colorSurfaceVariant to theme.colorOnSurfaceVariant
            "error" -> theme.colorError to theme.colorOnError
            else -> theme.colorSurfaceVariant to theme.colorOnSurfaceVariant
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg, theme.shapeChip)
                .padding(theme.spacingUnit * 1.5f),
        ) {
            node.str("title").takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = theme.textBody.copy(fontWeight = FontWeight.SemiBold), color = fg)
            }
            Text(text = node.str("content", "text"), style = theme.textBody, color = fg)
        }
    }

    @Composable
    fun Badge(node: JsonObject, theme: PluginTheme) {
        Box(
            modifier = Modifier
                .background(theme.colorSurfaceVariant, theme.shapeChip)
                .padding(horizontal = theme.spacingUnit, vertical = theme.spacingUnit / 2),
        ) {
            Text(text = node.str("label", "text"), style = theme.textCaption, color = theme.colorOnSurfaceVariant)
        }
    }

    @Composable
    fun Stat(node: JsonObject, theme: PluginTheme) {
        Column {
            Text(text = node.str("label"), style = theme.textCaption, color = theme.colorOnSurfaceVariant)
            Text(text = node.str("value"), style = theme.textTitle, color = theme.colorOnSurface)
        }
    }

    @Composable
    fun Hero(node: JsonObject, theme: PluginTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.colorPrimary, theme.shapeCard)
                .padding(theme.spacingUnit * 2),
        ) {
            Text(text = node.str("title"), style = theme.textTitle, color = theme.colorOnPrimary)
            node.str("subtitle", "content").takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = theme.textBody, color = theme.colorOnPrimary)
            }
        }
    }

    @Composable
    fun Progress(node: JsonObject, theme: PluginTheme) {
        val value = node.num("value").coerceIn(0.0, 100.0) / 100f
        Column {
            node.str("label").takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = theme.textCaption, color = theme.colorOnSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { value.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = theme.colorPrimary,
                trackColor = theme.colorSurfaceVariant,
            )
        }
    }

    // --- 集合类 ---

    @Composable
    fun ListNode(node: JsonObject, theme: PluginTheme, host: PluginHostApi, depth: Int) {
        val items = node.arr("items")
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2)) {
            for (item in items) {
                when {
                    item is JsonObject && item["type"] != null ->
                        SpecRenderer.SpecNode(item, theme, host, depth + 1)
                    else -> Bullet(item.asText(), theme)
                }
            }
        }
    }

    @Composable
    private fun Bullet(text: String, theme: PluginTheme) {
        Row(horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
            Text(text = "•", style = theme.textBody, color = theme.colorPrimary)
            Text(text = text, style = theme.textBody, color = theme.colorOnSurface)
        }
    }

    @Composable
    fun Table(node: JsonObject, theme: PluginTheme) {
        val columns = node.arr("columns").map { it.asText() }
        val rows = node.arr("rows")
        Column(modifier = Modifier.fillMaxWidth()) {
            if (columns.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in columns) {
                        Text(
                            text = col,
                            style = theme.textCaption.copy(fontWeight = FontWeight.SemiBold),
                            color = theme.colorOnSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            for (row in rows) {
                val cells = row.asList()
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (cell in cells) {
                        Text(
                            text = cell,
                            style = theme.textBody,
                            color = theme.colorOnSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun KeyValue(node: JsonObject, theme: PluginTheme) {
        val pairs = node.arr("pairs")
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2)) {
            for (pair in pairs) {
                if (pair !is JsonObject) continue
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = pair.str("key"),
                        style = theme.textBody,
                        color = theme.colorOnSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = pair.str("value"),
                        style = theme.textBody,
                        color = theme.colorOnSurface,
                    )
                }
            }
        }
    }

    @Composable
    fun Steps(node: JsonObject, theme: PluginTheme) {
        val steps = node.arr("steps")
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
            steps.forEachIndexed { index, step ->
                val title = if (step is JsonObject) step.str("title") else step.asText()
                Row(horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                    Box(
                        modifier = Modifier
                            .background(theme.colorPrimary, theme.shapeChip)
                            .padding(horizontal = theme.spacingUnit, vertical = 2.dp),
                    ) {
                        Text(text = "${index + 1}", style = theme.textCaption, color = theme.colorOnPrimary)
                    }
                    Text(text = title, style = theme.textBody, color = theme.colorOnSurface)
                }
            }
        }
    }

    @Composable
    fun Timeline(node: JsonObject, theme: PluginTheme) {
        val events = node.arr("events", "items")
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
            for (event in events) {
                if (event !is JsonObject) continue
                Row(horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                    Text(
                        text = event.str("time", "label"),
                        style = theme.textCaption,
                        color = theme.colorOnSurfaceVariant,
                    )
                    Text(
                        text = event.str("title", "text", "content"),
                        style = theme.textBody,
                        color = theme.colorOnSurface,
                    )
                }
            }
        }
    }

    // --- 内容类 ---

    @Composable
    fun Code(node: JsonObject, theme: PluginTheme) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.colorSurfaceVariant, theme.shapeChip)
                .horizontalScroll(rememberScrollState())
                .padding(theme.spacingUnit * 1.5f),
        ) {
            Text(
                text = node.str("code", "value", "content"),
                style = theme.textBody.copy(fontFamily = FontFamily.Monospace),
                color = theme.colorOnSurfaceVariant,
            )
        }
    }

    @Composable
    fun Divider(theme: PluginTheme) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(theme.colorOutline),
        )
    }

    @Composable
    fun Link(node: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val url = node.str("url", "href")
        val label = node.str("label", "text").ifBlank { url }
        if (url.isBlank()) {
            Text(text = label, style = theme.textBody, color = theme.colorOnSurface)
            return
        }
        Text(
            text = label,
            style = theme.textBody,
            color = theme.colorPrimary,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .clickable { host.openUrl(url) },
        )
    }

    @Composable
    fun ImagePlaceholder(node: JsonObject, theme: PluginTheme) {
        Text(
            text = node.str("alt").ifBlank { "图片" } + "（图片需在完整 Web 界面查看）",
            style = theme.textCaption,
            color = theme.colorOnSurfaceVariant,
        )
    }

    // --- 布局容器 ---

    @Composable
    fun Container(node: JsonObject, theme: PluginTheme, host: PluginHostApi, depth: Int, titled: Boolean) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
            if (titled) {
                node.str("title").takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = theme.textTitle, color = theme.colorOnSurface)
                }
            }
            for (child in node.arr("items", "children")) {
                if (child is JsonObject) SpecRenderer.SpecNode(child, theme, host, depth + 1)
            }
        }
    }

    @Composable
    fun RowContainer(node: JsonObject, theme: PluginTheme, host: PluginHostApi, depth: Int) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (child in node.arr("items", "children")) {
                if (child is JsonObject) {
                    Box(modifier = Modifier.weight(1f)) {
                        SpecRenderer.SpecNode(child, theme, host, depth + 1)
                    }
                }
            }
        }
    }

    @Composable
    fun GridContainer(node: JsonObject, theme: PluginTheme, host: PluginHostApi, depth: Int) {
        // 简化：grid 在原生端按列分组为纵向堆叠的行，每行 weight 均分。
        val columns = node.num("columns").takeIf { it > 0 }?.toInt() ?: 2
        val children = node.arr("items", "children").filterIsInstance<JsonObject>()
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
            children.chunked(columns).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                    for (child in rowItems) {
                        Box(modifier = Modifier.weight(1f)) {
                            SpecRenderer.SpecNode(child, theme, host, depth + 1)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Chart(node: JsonObject, theme: PluginTheme) {
        val kind = node.str("kind").ifBlank { "chart" }
        val data = node.arr("data")
        if (data.isEmpty()) {
            Text(text = "图表数据为空。", style = theme.textCaption, color = theme.colorOnSurfaceVariant)
            return
        }
        // 原生端只画简易柱状图；折线/饼图等引擎图形降级提示。
        if (kind != "bars" && kind != "bar") {
            Unsupported(kind, node, theme)
            return
        }
        val values = data.mapNotNull { point ->
            if (point is JsonObject) point.str("label") to point.num("value") else null
        }
        val max = values.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2)) {
            for ((label, value) in values) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit),
                ) {
                    Text(
                        text = label,
                        style = theme.textCaption,
                        color = theme.colorOnSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height((8 + 24 * (value / max)).dp)
                            .background(theme.colorPrimary, theme.shapeChip),
                    )
                }
            }
        }
    }

    // --- 降级 ---

    /** 不支持/引擎组件的可读占位：不暴露原始 JSON，不崩溃。 */
    @Composable
    fun Unsupported(type: String, node: JsonObject, theme: PluginTheme) {
        val name = when (type) {
            "mermaid", "diagram" -> "流程图"
            "plot", "echart" -> "图表"
            "scene3d" -> "3D 场景"
            "diff" -> "差异对比"
            "nested-too-deep" -> "过深的嵌套"
            else -> type.ifBlank { "该" }
        }
        val title = node.str("title").takeIf { it.isNotBlank() }
        Text(
            text = "「$name」组件" + (title?.let { "：$it" } ?: "") + "需在完整 Web 界面查看",
            style = theme.textCaption,
            color = theme.colorOnSurfaceVariant,
        )
    }

    // --- JSON 读取小工具（内部） ---

    internal fun JsonObject.str(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        } ?: ""

    internal fun JsonObject.num(key: String): Double =
        this[key]?.let { runCatching { it.jsonPrimitive.content.toDouble() }.getOrNull() } ?: 0.0

    internal fun JsonObject.arr(vararg keys: String): List<JsonElement> =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.let { runCatching { (it as? JsonArray)?.toList() }.getOrNull() }
        } ?: emptyList()

    internal fun JsonElement.asText(): String =
        runCatching { this.jsonPrimitive.content }.getOrElse { this.toString() }

    internal fun JsonElement.asList(): List<String> =
        (this as? JsonArray)?.map { it.asText() } ?: listOf(this.asText())
}
