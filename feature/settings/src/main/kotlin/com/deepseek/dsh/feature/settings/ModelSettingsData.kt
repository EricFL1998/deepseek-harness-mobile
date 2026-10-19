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
    val providers = (responseValue(providersRaw) as? JsonArray) ?: return emptyList()
    val namespaces = runCatching {
        val value = responseValue(settingsRaw) as? JsonObject ?: return emptyList()
        (value["namespaces"] as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val ns = (obj["ns"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            ns to (obj["value"] as? JsonObject)
        }.toMap()
    }.getOrElse { emptyMap() }
    val credentials = runCatching {
        val value = responseValue(credentialsRaw) as? JsonObject ?: return emptyList()
        value.entries.associate { (ref, info) ->
            val configured = (info as? JsonObject)?.get("configured")?.toString() == "true"
            val writable = (info as? JsonObject)?.get("writable")?.toString() != "false"
            ref to (configured to writable)
        }
    }.getOrElse { emptyMap() }

    return providers.mapNotNull { item ->
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
        val apiKeyEnv = (profile?.get("apiKeyEnv") as? JsonPrimitive)?.content
        val keyRef = apiKeyEnv ?: provider.uppercase().replace('-', '_') + "_API_KEY"
        val modelList = (profile?.get("models") as? JsonArray)?.mapNotNull { model ->
            when (model) {
                is JsonObject -> (model["name"] as? JsonPrimitive)?.content
                    ?: (model["id"] as? JsonPrimitive)?.content
                is JsonPrimitive -> model.content
                else -> null
            }
        }.orEmpty().distinct()

        ModelProviderUi(
            id = provider,
            name = displayName,
            models = modelList,
            keyRef = keyRef,
            keyConfigured = credentials[keyRef]?.first ?: false,
            keyWritable = credentials[keyRef]?.second ?: true,
        )
    }
}

