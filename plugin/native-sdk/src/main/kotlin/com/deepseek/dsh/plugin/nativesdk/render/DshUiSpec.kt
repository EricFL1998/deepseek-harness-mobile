package com.deepseek.dsh.plugin.nativesdk.render

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 一份 dsh-ui spec 的解析结果。
 *
 * spec 是模型/插件运行时产出的界面描述（JSON）：一个 `title` + 一组 `items`
 * 组件节点 + 可选 `gap`。本层只做「文本 → 结构化数据」，不碰 Compose。
 */
data class DshUiSpec(
    val title: String? = null,
    val gap: Int = DEFAULT_GAP_DP,
    val items: List<JsonObject> = emptyList(),
) {
    companion object {
        const val DEFAULT_GAP_DP = 12
    }
}

/** spec 文本/JSON 解析。解析失败返回 null，由调用方降级为占位。 */
object DshUiSpecParser {

    /** 从 `render_ui` 工具的入参（JSON 字符串里的 `spec` 字段）解析。 */
    fun fromToolArgs(args: String?): DshUiSpec? =
        args?.takeIf { it.isNotBlank() }
            ?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?.let { root -> root["spec"] }
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.let(::fromJson)

    /** 从一段 ```dsh-ui``` 围栏正文或裸 spec JSON 解析。 */
    fun fromRaw(raw: String): DshUiSpec? =
        runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw.trim()).jsonObject }
            .getOrNull()
            ?.let(::fromJson)

    /** 从已解析的 JSON 对象构造。 */
    fun fromJson(obj: JsonObject): DshUiSpec = DshUiSpec(
        title = obj["title"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            ?.takeIf { it.isNotBlank() },
        gap = obj["gap"]?.let { runCatching { it.jsonPrimitive.content.toInt() }.getOrNull() }
            ?: DshUiSpec.DEFAULT_GAP_DP,
        items = obj["items"]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?: emptyList(),
    )
}
