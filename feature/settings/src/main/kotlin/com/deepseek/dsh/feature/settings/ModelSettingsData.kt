package com.deepseek.dsh.feature.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 后端模型提供商（settings/describe + llm/listConfigurableProviders + credentials/describe）。 */
data class ModelProviderUi(
    val id: String,
    val name: String,
    val models: List<String>,
    val keyRef: String,
    val keyConfigured: Boolean,
    val keyWritable: Boolean = true,
    val settingsNs: String = "",
    val settingsPath: List<String> = emptyList(),
    val baseUrl: String = "",
    /** 目录路由由适配器自带（true）；false 表示要自己声明 api 协议。 */
    val declared: Boolean = true,
    /** 目录声明的默认端点，作为自定义设置里的占位。 */
    val defaultBaseUrl: String = "",
    /**
     * 是否已在 Host 侧配置（存在 profile 值或已存密钥）。
     * 未配置的目录项只在“添加模型提供商”里列出，选中后才进入编辑器。
     */
    val configured: Boolean = true,
)

/** 提取 server-response 里的 result.value。 */
fun responseValue(raw: String?): JsonElement? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val root = Json.parseToJsonElement(raw)
        val obj = root as? JsonObject ?: return null
        val result = obj["result"] as? JsonObject ?: return null
        result["value"] ?: result
    }.getOrNull()
}

fun parseModelProviders(
    providersRaw: String?,
    settingsRaw: String?,
    credentialsRaw: String?,
): List<ModelProviderUi> {
    val providers = (responseValue(providersRaw) as? JsonArray) ?: JsonArray(emptyList())
    val namespaces = runCatching {
        val value = responseValue(settingsRaw) as? JsonObject ?: return@runCatching emptyMap<String, JsonObject?>()
        (value["namespaces"] as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val ns = (obj["ns"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            ns to (obj["value"] as? JsonObject)
        }.toMap()
    }.getOrElse { emptyMap() }
    val credentials = runCatching {
        val value = responseValue(credentialsRaw) as? JsonObject ?: return@runCatching emptyMap()
        value.entries.associate { (ref, info) ->
            val configured = (info as? JsonObject)?.get("configured")?.toString() == "true"
            val writable = (info as? JsonObject)?.get("writable")?.toString() != "false"
            ref to (configured to writable)
        }
    }.getOrElse { emptyMap() }

    val parsed = providers.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val provider = (obj["provider"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        val displayName = (obj["displayName"] as? JsonPrimitive)?.content ?: provider
        val settingsNs = (obj["settingsNs"] as? JsonPrimitive)?.content.orEmpty()
        val settingsPath = (obj["settingsPath"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

        var profile: JsonObject? = namespaces[settingsNs]
        for (segment in settingsPath) {
            profile = profile?.get(segment) as? JsonObject ?: null
        }
        if (provider == "deepseek-account") return@mapNotNull null
        // 目录项即使尚未配置也保留（configured=false）：设置面板据此决定
        // 是否显示为卡片，并把未配置项放进“添加模型提供商”列表。
        val apiKeyEnv = (profile?.get("apiKeyEnv") as? JsonPrimitive)?.content
        val keyRef = apiKeyEnv ?: provider.uppercase().replace('-', '_') + "_API_KEY"
        val credential = credentials[keyRef]
        val baseUrl = (profile?.get("baseURL") as? JsonPrimitive)?.content.orEmpty()
        val modelList = (profile?.get("models") as? JsonArray)?.mapNotNull { model ->
            when (model) {
                is JsonObject -> (model["name"] as? JsonPrimitive)?.content
                    ?: (model["id"] as? JsonPrimitive)?.content
                is JsonPrimitive -> model.content
                else -> null
            }
        }.orEmpty().distinct()

        val declared = obj["declared"]?.toString() != "false"
        ModelProviderUi(
            id = provider,
            name = displayName,
            declared = declared,
            defaultBaseUrl = (obj["defaultBaseURL"] as? JsonPrimitive)?.content.orEmpty(),
            models = modelList,
            keyRef = keyRef,
            keyConfigured = credential?.first ?: false,
            keyWritable = credential?.second ?: true,
            settingsNs = settingsNs,
            settingsPath = settingsPath,
            baseUrl = baseUrl,
            configured = profile != null || credential?.first == true,
        )
    }
    // Host 目录里同时有官方的 `deepseek-official` 和 pi-ai 的通用 `deepseek`
    // 路由，两者显示名几乎一样（DeepSeek / deepseek），会让设置页出现两张
    // 重复的 DeepSeek 卡片。有官方路由时丢掉通用路由。
    val hasOfficialDeepSeek = parsed.any { it.id == "deepseek-official" }
    val deduped = if (hasOfficialDeepSeek) {
        parsed.filterNot { it.id == "deepseek" }
    } else {
        parsed
    }
    // 官方内置 DeepSeek API 提供商始终存在；Host 目录未返回时也要能看到并配置。
    val hasDeepSeek = deduped.any { it.id.contains("deepseek", ignoreCase = true) || it.name.contains("DeepSeek", ignoreCase = true) }
    return if (hasDeepSeek) deduped else deduped + ModelProviderUi(
        id = "deepseek",
        name = "DeepSeek",
        models = emptyList(),
        keyRef = "DEEPSEEK_API_KEY",
        keyConfigured = credentials["DEEPSEEK_API_KEY"]?.first ?: false,
        keyWritable = credentials["DEEPSEEK_API_KEY"]?.second ?: true,
        settingsNs = "llm",
        settingsPath = listOf("providers", "deepseek"),
        configured = credentials["DEEPSEEK_API_KEY"]?.first == true,
    )
}





