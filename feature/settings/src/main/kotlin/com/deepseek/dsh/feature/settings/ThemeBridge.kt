package com.deepseek.dsh.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepseek.dsh.core.data.DshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * 外观与字号：Host 的 `ui-theme` 设置。
 *
 * 官方设置页把深浅色与字号存在 Host（`ui-theme.preference` / `fontSize`），
 * 这样手机与浏览器看同一个账号时显示一致。App 读这里的状态渲染主题，选择写回
 * Host；bridge 不可用时回落到跟随系统。
 */
object ThemeBridge {
    /** `light` / `dark` / `system`；默认跟随系统。 */
    var preference by mutableStateOf("system")
        private set

    /** 正文字号（Host `ui-theme.fontSize`）；null 表示还没读到。 */
    var fontSize by mutableStateOf<Int?>(null)
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

    fun setPreference(value: String) = writer?.invoke(value)

    private suspend fun load(repository: DshRepository, hostId: String) {
        val raw = runCatching {
            repository.rpcRaw(hostId, "settings/describe", "{}").getOrNull()
        }.getOrNull()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        runCatching {
            val root = json.parseToJsonElement(raw.orEmpty())
            val value = ((root as? JsonObject)?.get("result") as? JsonObject)?.get("value") as? JsonObject
            val namespaces = value?.get("namespaces") as? JsonArray ?: return@runCatching
            namespaces.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                if ((obj["ns"] as? JsonPrimitive)?.contentOrNull != "ui-theme") return@forEach
                val docValue = obj["value"] as? JsonObject ?: return@forEach
                revision = (obj["revision"] as? JsonPrimitive)?.intOrNull ?: revision
                (docValue["preference"] as? JsonPrimitive)?.contentOrNull?.let { preference = it }
                fontSize = (docValue["fontSize"] as? JsonPrimitive)?.intOrNull
            }
        }
    }

    private suspend fun mutate(repository: DshRepository, hostId: String, value: String) {
        val args = buildJsonObject {
            put("ns", "ui-theme")
            put("ops", buildJsonArray {
                add(buildJsonObject {
                    put("op", "set")
                    put("path", buildJsonArray { add(JsonPrimitive("preference")) })
                    put("value", value)
                })
            })
            put("expectedRevision", revision)
        }
        repository.rpcRaw(hostId, "settings/mutate", args.toString())
    }
}
