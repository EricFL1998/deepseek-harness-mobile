package com.deepseek.dsh.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 语音输入（`speech` Remote）的数据模型与解析。
 *
 * 官方网页端的语音输入读 `remote.speech`：`catalog()` 给出识别服务、语言、
 * 准备状态；`configure/prepare/cancelPreparation` 改选择与模型准备。
 * 原生只用到这几项，字段名与 Host `SpeechCatalog` 对齐。
 */

/** 首次准备的工作量估算。 */
data class SpeechSetupEstimate(
    val recommendedDiskBytes: Long,
    val expectedMemoryBytes: Long,
    val minimumMinutes: Int,
    val maximumMinutes: Int,
)

/** 一个准备步骤（check/model/vad/verify/load）。 */
data class SpeechStep(val kind: String, val status: String)

/** 一个识别服务的模型准备状态。 */
data class SpeechPreparation(
    /** checking / unprepared / downloading / verifying / loading / ready / standby / waking / failed / cancelled / cancelling。 */
    val phase: String,
    val message: String? = null,
    val steps: List<SpeechStep> = emptyList(),
    val completedBytes: Long? = null,
    val totalBytes: Long? = null,
    /** 本次准备使用的下载来源。 */
    val source: String? = null,
)

/** 一个语音识别服务。 */
data class SpeechProvider(
    val id: String,
    val name: String,
    /** host-local / cloud。 */
    val location: String,
    val languages: List<String>,
    val downloadSources: List<String>,
    val setupEstimate: SpeechSetupEstimate?,
    val preparation: SpeechPreparation?,
)

/** `speech/catalog` 的结果。 */
data class SpeechCatalog(
    val providers: List<SpeechProvider>,
    val providerId: String,
    val language: String,
    val maxAudioBytes: Long,
    val maxDurationSeconds: Int,
) {
    /** 当前选中的服务。 */
    val selected: SpeechProvider?
        get() = providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()
}

/** 解析 `speech/catalog` 的 `value`。 */
internal fun parseSpeechCatalog(value: JsonElement?): SpeechCatalog? {
    val root = value as? JsonObject ?: return null
    val providers = (root["providers"] as? JsonArray).orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.string("id") ?: return@mapNotNull null
        SpeechProvider(
            id = id,
            name = obj.string("name") ?: id,
            location = obj.string("location") ?: "host-local",
            languages = (obj["languages"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            downloadSources = (obj["downloadSources"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            setupEstimate = (obj["setupEstimate"] as? JsonObject)?.let { estimate ->
                SpeechSetupEstimate(
                    recommendedDiskBytes = estimate.long("recommendedDiskBytes") ?: 0L,
                    expectedMemoryBytes = estimate.long("expectedMemoryBytes") ?: 0L,
                    minimumMinutes = (estimate.int("minimumMinutes") ?: 1),
                    maximumMinutes = (estimate.int("maximumMinutes") ?: 10),
                )
            },
            preparation = (obj["preparation"] as? JsonObject)?.let { preparation ->
                SpeechPreparation(
                    phase = preparation.string("phase") ?: "unprepared",
                    message = preparation.string("message"),
                    steps = (preparation["steps"] as? JsonArray).orEmpty().mapNotNull { step ->
                        val stepObj = step as? JsonObject ?: return@mapNotNull null
                        SpeechStep(
                            kind = stepObj.string("kind") ?: return@mapNotNull null,
                            status = stepObj.string("status") ?: "pending",
                        )
                    },
                    completedBytes = preparation.long("completedBytes"),
                    totalBytes = preparation.long("totalBytes"),
                    source = preparation.string("source"),
                )
            },
        )
    }
    val selection = root["selection"] as? JsonObject
    return SpeechCatalog(
        providers = providers,
        providerId = selection?.string("providerId") ?: providers.firstOrNull()?.id.orEmpty(),
        language = selection?.string("language") ?: "auto",
        maxAudioBytes = root.long("maxAudioBytes") ?: 4L * 1024 * 1024,
        maxDurationSeconds = (root.int("maxDurationSeconds") ?: 120),
    )
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
