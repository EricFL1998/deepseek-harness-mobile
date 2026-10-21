package com.deepseek.dsh.plugin.nativesdk.host

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Optional Host-RPC capability exposed by the full-page native companion container. */
interface PluginRpcHost {
    /** Invoke an existing plugin-owned Host route; no credentials cross into the APK. */
    fun callRpc(method: String, payload: JsonObject, onResult: (Result<JsonElement>) -> Unit)
}
