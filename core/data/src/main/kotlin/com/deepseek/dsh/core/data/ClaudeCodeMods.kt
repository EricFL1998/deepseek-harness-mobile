package com.deepseek.dsh.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Claude Code 模组横幅的数据模型。
 *
 * 官方 `client-ui-claude-code-mods` 把 Host 下发的序列化节点树（只允许
 * `Box`/`Text`/`Button`）画成一条横幅：`Text` 的颜色/粗体/暗淡/斜体/下划线，
 * `Box` 的方向/边框/内边距/间距，`Button` 的标签、热键、禁用与 actionId。
 */

/** 横幅里的一个节点。 */
sealed interface BandNode {
    /** 文本节点。 */
    data class Text(
        val text: String,
        val color: String? = null,
        val bold: Boolean = false,
        val dim: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
    ) : BandNode

    /** 布局容器。 */
    data class Box(
        val row: Boolean = false,
        val border: Boolean = false,
        val padding: Int = 0,
        val gap: Int = 0,
        val children: List<BandNode> = emptyList(),
    ) : BandNode

    /** 可点击按钮。 */
    data class Button(
        val label: String,
        val hotkey: String? = null,
        val disabled: Boolean = false,
        val actionId: String? = null,
    ) : BandNode
}

/** 一次重绘的快照（generation 用于按钮回调的过期判断）。 */
data class ClaudeCodeBandSnapshot(
    val generation: Long,
    val root: BandNode?,
)

/** 解析 `SurfaceSnapshot`：`{ generation, tree }`。 */
internal fun parseBandSnapshot(value: JsonElement?): ClaudeCodeBandSnapshot? {
    val obj = value as? JsonObject ?: return null
    val generation = (obj["generation"] as? JsonPrimitive)?.longOrNull ?: 0L
    val tree = obj["tree"]
    return ClaudeCodeBandSnapshot(
        generation = generation,
        root = if (tree == null || tree is kotlinx.serialization.json.JsonNull) null else parseBandNode(tree),
    )
}

/** 解析一个节点；未知节点按文本降级，保持内容可见。 */
internal fun parseBandNode(element: JsonElement?): BandNode? {
    when (element) {
        null -> return null
        is JsonPrimitive -> return element.contentOrNull?.let { BandNode.Text(text = it) }
        is JsonArray -> return BandNode.Box(children = element.mapNotNull { parseBandNode(it) })
        is JsonObject -> {
            val type = (element["type"] as? JsonPrimitive)?.contentOrNull
            val props = element["props"] as? JsonObject
            fun text(key: String): String? =
                (props?.get(key) as? JsonPrimitive)?.contentOrNull
            fun bool(key: String): Boolean =
                (props?.get(key) as? JsonPrimitive)?.booleanOrNull == true
            fun number(key: String): Int =
                (props?.get(key) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            return when (type) {
                "Text" -> BandNode.Text(
                    text = text("text") ?: text("children") ?: "",
                    color = text("color"),
                    bold = bool("bold"),
                    dim = bool("dimColor"),
                    italic = bool("italic"),
                    underline = bool("underline"),
                )
                "Button" -> BandNode.Button(
                    label = text("label") ?: "",
                    hotkey = text("hotkey"),
                    disabled = bool("disabled") || (element["actionId"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank(),
                    actionId = (element["actionId"] as? JsonPrimitive)?.contentOrNull,
                )
                else -> BandNode.Box(
                    row = text("flexDirection") == "row",
                    border = bool("borderStyle") || bool("border"),
                    padding = maxOf(number("padding"), number("paddingX"), number("paddingY")),
                    gap = number("gap"),
                    children = (element["children"] as? JsonArray).orEmpty().mapNotNull { parseBandNode(it) },
                )
            }
        }
        else -> return null
    }
}
