package com.deepseek.dsh.plugin.mobileui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * v4.0 设计文档 §7.8 & §7.8.1: SDUI Schema 与节点定义
 * 预算约束：节点 <= 200，深度 <= 8，spec <= 256 KiB
 */
@Serializable
sealed interface SduiNode {
    val id: String?
}

@Serializable
data class SduiPage(
    val title: String,
    val description: String? = null,
    val children: List<SduiNode> = emptyList()
)

@Serializable
data class SduiSection(
    override val id: String? = null,
    val title: String? = null,
    val children: List<SduiNode> = emptyList()
) : SduiNode

@Serializable
data class SduiRow(
    override val id: String? = null,
    val children: List<SduiNode> = emptyList()
) : SduiNode

@Serializable
data class SduiColumn(
    override val id: String? = null,
    val children: List<SduiNode> = emptyList()
) : SduiNode

@Serializable
data class SduiText(
    override val id: String? = null,
    val text: String,
    val style: String = "body" // title, subtitle, body, caption, code
) : SduiNode

@Serializable
data class SduiButton(
    override val id: String? = null,
    val label: String,
    val action: SduiAction,
    val primary: Boolean = false
) : SduiNode

@Serializable
data class SduiInput(
    override val id: String? = null,
    val key: String,
    val placeholder: String? = null,
    val initialValue: String? = null
) : SduiNode

@Serializable
data class SduiSwitch(
    override val id: String? = null,
    val key: String,
    val label: String,
    val initialValue: Boolean = false
) : SduiNode

@Serializable
data class SduiBadge(
    override val id: String? = null,
    val text: String,
    val color: String = "primary" // primary, success, warning, error
) : SduiNode

@Serializable
sealed interface SduiAction {
    @Serializable
    data class OpenUrl(val url: String) : SduiAction

    @Serializable
    data class Copy(val text: String) : SduiAction

    @Serializable
    data class Refresh(val targetId: String? = null) : SduiAction

    @Serializable
    data class RemoteCall(val endpoint: String, val params: Map<String, String> = emptyMap()) : SduiAction

    @Serializable
    data class Close(val result: String? = null) : SduiAction
}

/**
 * 校验预算安全门禁
 */
object SduiValidator {
    const val MAX_NODES = 200
    const val MAX_DEPTH = 8
    const val MAX_BYTES = 256 * 1024

    fun validate(page: SduiPage, rawSize: Int): Result<Unit> {
        if (rawSize > MAX_BYTES) {
            return Result.failure(IllegalArgumentException("SDUI spec size exceeds 256 KiB"))
        }

        var nodeCount = 0
        fun checkDepth(node: SduiNode, depth: Int): Int {
            if (depth > MAX_DEPTH) {
                throw IllegalArgumentException("SDUI node depth exceeds $MAX_DEPTH")
            }
            nodeCount++
            if (nodeCount > MAX_NODES) {
                throw IllegalArgumentException("SDUI node count exceeds $MAX_NODES")
            }

            var maxChildDepth = depth
            val children = when (node) {
                is SduiSection -> node.children
                is SduiRow -> node.children
                is SduiColumn -> node.children
                else -> emptyList()
            }
            for (child in children) {
                val d = checkDepth(child, depth + 1)
                if (d > maxChildDepth) maxChildDepth = d
            }
            return maxChildDepth
        }

        try {
            for (child in page.children) {
                checkDepth(child, 1)
            }
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
