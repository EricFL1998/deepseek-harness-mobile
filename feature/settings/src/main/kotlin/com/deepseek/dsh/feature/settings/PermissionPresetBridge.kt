package com.deepseek.dsh.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepseek.dsh.core.data.DshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** 一个可选权限预设（Host 的 `permissionPresets/catalog` 返回项）。 */
data class PermissionPresetOption(val value: String, val name: String)

/**
 * 默认权限预设的桥：选项来自 Host 的 `permissionPresets/catalog`，当前值来自
 * `settings/describe` 的 `permission.defaultPreset`，写入走 `settings/mutate`。
 *
 * 这样权限模式列表不再由客户端写死，Host 装了新的权限预设（例如 auto review）
 * 时设置页直接出现，无需改 APK。
 */
object PermissionPresetBridge {
    /** Host 提供的全部预设；空表示还没读到。 */
    var options by mutableStateOf<List<PermissionPresetOption>>(emptyList())
        private set

    /** 当前默认预设值；null 表示还没读到。 */
    var current by mutableStateOf<String?>(null)
        private set

    /** 最后一次读到的设置 revision，写回时作为 expectedRevision 的 CAS。 */
    private var revision: Int = 0

    private var reader: (() -> Unit)? = null
    private var writer: ((String) -> Unit)? = null

    fun wire(repository: DshRepository, hostId: String, scope: CoroutineScope) {
        reader = { scope.launch { load(repository, hostId) } }
        writer = { value ->
            scope.launch {
                runCatching { mutate(repository, hostId, value) }
                load(repository, hostId)
            }
        }
        reader?.invoke()
    }

    fun refresh() = reader?.invoke()

    fun set(value: String) = writer?.invoke(value)

    private suspend fun load(repository: DshRepository, hostId: String) {
        val catalog = runCatching {
            repository.rpcRaw(hostId, "permissionPresets/catalog", "{}").getOrNull()
        }.getOrNull()
        val settings = runCatching {
            repository.rpcRaw(hostId, "settings/describe", "{}").getOrNull()
        }.getOrNull()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val parsedOptions = runCatching {
            val root = json.parseToJsonElement(catalog.orEmpty())
            val value = ((root as? JsonObject)?.get("result") as? JsonObject)?.get("value") as? JsonObject
            ((value?.get("options") as? JsonArray).orEmpty()).mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val optionValue = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: optionValue
                PermissionPresetOption(optionValue, name)
            }
        }.getOrDefault(emptyList())
        val parsedCurrent = runCatching {
            val root = json.parseToJsonElement(settings.orEmpty())
            val value = ((root as? JsonObject)?.get("result") as? JsonObject)?.get("value") as? JsonObject
            val namespaces = value?.get("namespaces") as? JsonArray ?: return@runCatching null
            namespaces.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                if ((obj["ns"] as? JsonPrimitive)?.contentOrNull != "permission") return@mapNotNull null
                val docValue = obj["value"] as? JsonObject ?: return@mapNotNull null
                revision = (obj["revision"] as? JsonPrimitive)?.intOrNull ?: revision
                (docValue["defaultPreset"] as? JsonPrimitive)?.contentOrNull
            }.firstOrNull()
        }.getOrNull()
        if (parsedOptions.isNotEmpty()) options = parsedOptions
        current = parsedCurrent
    }

    private suspend fun mutate(repository: DshRepository, hostId: String, value: String) {
        val args = buildJsonObject {
            put("ns", "permission")
            put("ops", buildJsonArray {
                add(buildJsonObject {
                    put("op", "set")
                    put("path", buildJsonArray { add(JsonPrimitive("defaultPreset")) })
                    put("value", value)
                })
            })
            put("expectedRevision", revision)
        }
        repository.rpcRaw(hostId, "settings/mutate", args.toString())
    }
}
