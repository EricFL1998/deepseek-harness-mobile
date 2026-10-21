package com.deepseek.dsh.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 思考过程翻译（`cotTranslation` Remote）的模型。
 *
 * 官方客户端读 `limits()` 得到可用的翻译服务、当前偏好与单段上限，再逐段调用
 * `translate(request)`；译文缓存在客户端，重新展开不重复请求。
 */

/** `cotTranslation/limits` 的结果。 */
data class CotTranslationLimits(
    /** 单段最大字符数；超过要切开。 */
    val maxTextChars: Int,
    /** 当前偏好的翻译服务（bing/google/deepseek-account/deepseek-official）。 */
    val provider: String,
    /** 目标语言；`auto` 跟随界面语言。 */
    val targetLanguage: String,
    /** 当前部署下可用的服务。 */
    val availableProviders: List<String>,
)

internal fun parseCotTranslationLimits(value: JsonElement?): CotTranslationLimits? {
    val root = value as? JsonObject ?: return null
    val preferences = root["preferences"] as? JsonObject
    return CotTranslationLimits(
        maxTextChars = (root["maxTextChars"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 4000,
        provider = (preferences?.get("provider") as? JsonPrimitive)?.contentOrNull ?: "bing",
        targetLanguage = (preferences?.get("targetLanguage") as? JsonPrimitive)?.contentOrNull ?: "auto",
        availableProviders = (root["availableProviders"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
    )
}
