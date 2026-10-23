package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.AgentPresetGroupInfo
import com.deepseek.dsh.core.model.AgentTeamMember
import com.deepseek.dsh.core.model.AgentTeamSummary
import com.deepseek.dsh.core.model.AgentTeamTask
import com.deepseek.dsh.core.model.AccountBalanceInfo
import com.deepseek.dsh.core.model.AccountProfileInfo
import com.deepseek.dsh.core.model.AccountViewInfo
import com.deepseek.dsh.core.model.AutomationTaskItem
import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.ComposerStats
import com.deepseek.dsh.core.model.MessageRole
import com.deepseek.dsh.core.model.ModelCatalogInfo
import com.deepseek.dsh.core.model.ModelItem
import com.deepseek.dsh.core.model.ModelReasoningEffort
import com.deepseek.dsh.core.model.ModelProviderGroup
import com.deepseek.dsh.core.model.PluginBundleItem
import com.deepseek.dsh.core.model.PluginComponentInfo
import com.deepseek.dsh.core.model.PluginRowInfo
import com.deepseek.dsh.core.model.PluginConfigItem
import com.deepseek.dsh.core.model.PluginInventorySnapshotInfo
import com.deepseek.dsh.core.model.PluginManagerSnapshot
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.TeamTaskItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

/**
 * v4.0 设计文档 §5.1:
 * 一元 RPC: POST /api (JSON)
 * 流式 RPC: WS /api/remote.mux (open/item/end/cancel)
 */
class RemoteClient(
    private var baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : DshClientProtocol {

    /** 绑定本地 Host 实际监听的 loopback 地址（端口由运行时解析，不能写死）。 */
    fun updateBaseUrl(newBaseUrl: String) {
        baseUrl = newBaseUrl.trimEnd('/')
    }

    /** 当前实际请求的 Host 地址，供诊断/状态展示。 */
    fun currentBaseUrl(): String = baseUrl

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private fun accountClientMetadata(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("version", "4.0.0")
        put("locale", "zh-CN")
        put(
            "timezoneOffsetSeconds",
            java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000,
        )
    }

    /** 官方 account 系列 RPC 的共用请求与 result.value 解包。 */
    private fun findFirstSessionId(element: kotlinx.serialization.json.JsonElement?): String? {
        when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                (element["sessionId"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.takeIf { it.isNotBlank() }?.let { return it }
                element.values.forEach { value ->
                    findFirstSessionId(value)?.let { return it }
                }
            }
            is kotlinx.serialization.json.JsonArray -> element.forEach { value ->
                findFirstSessionId(value)?.let { return it }
            }
            else -> Unit
        }
        return null
    }
    private suspend fun accountRpc(
        method: String,
        args: kotlinx.serialization.json.JsonObject,
    ): RemoteResult<kotlinx.serialization.json.JsonObject> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", method)
            put("payload", buildJsonObject { put("args", args) })
        }.toString()
        val request = Request.Builder()
            .url("$baseUrl/api/$method")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext RemoteResult.Failure(
                        "dsh/account/http",
                        "Account request failed, HTTP ${response.code}",
                    )
                }
                val bodyStr = response.body?.string() ?: "{}"
                val elem = json.parseToJsonElement(bodyStr)
                val result = (elem as? kotlinx.serialization.json.JsonObject)?.get("result")
                val value = when (result) {
                    is kotlinx.serialization.json.JsonObject ->
                        result["value"] as? kotlinx.serialization.json.JsonObject ?: result
                    else -> elem as? kotlinx.serialization.json.JsonObject
                }
                if (value != null) {
                    RemoteResult.Success(value)
                } else {
                    RemoteResult.Failure("dsh/account/malformed", "Malformed account response")
                }
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/account/network", e.message ?: "Account request failed")
        }
    }

    private fun kotlinx.serialization.json.JsonObject.parseAccountView(): AccountViewInfo {
        val attempt = this["attempt"] as? kotlinx.serialization.json.JsonObject
        return AccountViewInfo(
            status = this["status"]?.toString()?.trim('"') ?: "signed-out",
            attemptId = attempt?.get("id")?.toString()?.trim('"'),
            attemptPhase = attempt?.get("phase")?.toString()?.trim('"'),
            authorizeUrl = attempt?.get("authorizeUrl")?.toString()?.trim('"'),
        )
    }

    override suspend fun authenticate(baseUrl: String, token: String?): RemoteResult<String> {
        val auth = HostAuthenticator(client = client)
        return when (val res = auth.exchangeTokenForCookie(baseUrl, token)) {
            is RemoteResult.Success -> RemoteResult.Success("Authenticated, ${res.data.size} cookies acquired")
            is RemoteResult.Failure -> res
        }
    }

    override suspend fun listSessions(hostId: String): RemoteResult<List<SessionInfo>> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", "session/list")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {})
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext RemoteResult.Failure(
                        "dsh/remote/error",
                        "Failed to list sessions, HTTP ${response.code}"
                    )
                }
                val bodyStr = response.body?.string() ?: "{}"
                try {
                    // Try parsing standard DSH response or fallback to direct array
                    val jsonElem = json.parseToJsonElement(bodyStr)
                    val itemsElem = if (jsonElem is kotlinx.serialization.json.JsonObject) {
                        jsonElem["result"]?.let { res ->
                            if (res is kotlinx.serialization.json.JsonObject) res["value"]?.let { v ->
                                if (v is kotlinx.serialization.json.JsonObject) v["items"] else v
                            } else null
                        } ?: jsonElem["items"] ?: jsonElem
                    } else jsonElem
                    val list = json.decodeFromJsonElement<List<SessionInfo>>(itemsElem)
                    RemoteResult.Success(list)
                } catch (_: Exception) {
                    RemoteResult.Failure(
                        "dsh/session/list-malformed",
                        "Failed to decode session list from DSH Host"
                    )
                }
            }
        } catch (e: IOException) {
            RemoteResult.Failure("dsh/net/timeout", e.message ?: "Network error listing sessions")
        }
    }

    override suspend fun createSession(
        hostId: String,
        title: String,
        workspacePath: String?,
        agentPreset: String?
    ): RemoteResult<SessionInfo> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "session/create")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("request", buildJsonObject {
                        if (workspacePath != null) put("cwd", workspacePath)
                        if (!agentPreset.isNullOrBlank()) put("agentPreset", agentPreset)
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/session/create")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext RemoteResult.Failure(
                        "dsh/session/create-failed",
                        "Host 返回错误: HTTP ${response.code}"
                    )
                }
                val bodyStr = response.body?.string() ?: ""
                val value = runCatching {
                    (json.parseToJsonElement(bodyStr) as? kotlinx.serialization.json.JsonObject)
                        ?.get("result")?.let { it as? kotlinx.serialization.json.JsonObject }
                        ?.get("value") as? kotlinx.serialization.json.JsonObject
                }.getOrNull()
                val createdId = value?.get("sessionId")?.toString()?.trim('"')
                val resolvedId = createdId?.takeIf { it.isNotBlank() }
                    ?: findFirstSessionId(runCatching { json.parseToJsonElement(bodyStr) }.getOrNull())
                if (resolvedId.isNullOrBlank()) {
                    return@withContext RemoteResult.Failure("dsh/session/create-malformed", "Host did not return sessionId")
                }
                RemoteResult.Success(
                    SessionInfo(
                        hostId = hostId,
                        sessionId = resolvedId,
                        title = title,
                        workspacePath = workspacePath,
                        status = "ready",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            RemoteResult.Failure(
                "dsh/session/create-error",
                e.message ?: "无法连接到 Host，创建会话失败"
            )
        }
    }

    override suspend fun deleteSession(hostId: String, sessionId: String): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", "session/cancel")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("sessionId", sessionId)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                RemoteResult.Success(response.isSuccessful)
            }
        } catch (e: Exception) {
            RemoteResult.Success(true)
        }
    }

    override suspend fun selectModel(
        hostId: String,
        sessionId: String,
        modelId: String,
        provider: String?,
        reasoningEffort: String?
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "session/selectModel")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("request", buildJsonObject {
                        put("sessionId", sessionId)
                        if (!provider.isNullOrBlank()) put("provider", provider)
                        put("model", modelId)
                        if (!reasoningEffort.isNullOrBlank()) {
                            put("reasoningEffort", reasoningEffort)
                        }
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/session/selectModel")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use RemoteResult.Failure(
                        "dsh/session/select-model",
                        extractRpcError(body) ?: "HTTP ${response.code}",
                    )
                }
                val root = runCatching { json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject }
                    .getOrNull()
                val result = root?.get("result") as? kotlinx.serialization.json.JsonObject
                if (result?.get("ok")?.toString() == "false") {
                    val error = result["error"] as? kotlinx.serialization.json.JsonObject
                    RemoteResult.Failure(
                        "dsh/session/select-model",
                        (error?.get("message")?.toString() ?: error?.toString()).orEmpty(),
                    )
                } else {
                    RemoteResult.Success(true)
                }
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/session/select-model", e.message ?: "network error")
        }
    }

    /** 从 RPC 错误响应里取一条可读信息；没有就返回 null。 */
    private fun extractRpcError(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject }
            .getOrNull() ?: return null
        val error = (root["result"] as? kotlinx.serialization.json.JsonObject)?.get("error")
            as? kotlinx.serialization.json.JsonObject
        val message = error?.get("message")?.toString()?.trim('"')
        if (!message.isNullOrBlank()) return message
        val diagnostic = error?.get("diagnostic")?.toString()?.trim('"')
        if (!diagnostic.isNullOrBlank()) return diagnostic
        return (root["message"]?.toString())?.trim('"')?.takeIf { it.isNotBlank() }
    }

    override suspend fun selectPermissionPreset(
        hostId: String,
        sessionId: String,
        preset: String
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", "session/selectPermission")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("sessionId", sessionId)
                    put("preset", preset)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                RemoteResult.Success(response.isSuccessful)
            }
        } catch (e: Exception) {
            RemoteResult.Success(true)
        }
    }


    override suspend fun cancelSession(hostId: String, sessionId: String): Boolean =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val payload = buildJsonObject {
                put("type", "client-request")
                put("rpcId", java.util.UUID.randomUUID().toString())
                put("method", "session/cancel")
                put("payload", buildJsonObject {
                    put("args", buildJsonObject { put("sessionId", sessionId) })
                })
            }.toString()
            val request = Request.Builder()
                .url("$baseUrl/api/session/cancel")
                .post(payload.toRequestBody(mediaTypeJson))
                .build()
            runCatching { client.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
        }
    override suspend fun answerUserQuestion(
        hostId: String,
        sessionId: String,
        callId: String,
        questionId: String,
        selected: String
    ): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "userQuestions/answer")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("agentId", sessionId)
                    put("callId", callId)
                    put("answer", buildJsonObject {
                        put("answers", kotlinx.serialization.json.JsonArray(listOf(
                            buildJsonObject {
                                put("id", questionId)
                                put("selected", kotlinx.serialization.json.JsonArray(listOf(
                                    kotlinx.serialization.json.JsonPrimitive(selected)
                                )))
                            }
                        )))
                    })
                })
            })
        }.toString()
        val request = Request.Builder()
            .url("$baseUrl/api/userQuestions/answer")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()
        runCatching {
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
    override suspend fun getModelCatalog(hostId: String): RemoteResult<ModelCatalogInfo> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "session/modelCatalog")
            put("payload", buildJsonObject { put("args", buildJsonObject {}) })
        }.toString()
        val request = Request.Builder()
            .url("$baseUrl/api/session/modelCatalog")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext RemoteResult.Failure("dsh/model/catalog-http", "HTTP ${response.code}")
                }
                val bodyStr = response.body?.string() ?: "{}"
                val root = json.parseToJsonElement(bodyStr) as? kotlinx.serialization.json.JsonObject
                val value = (root?.get("result") as? kotlinx.serialization.json.JsonObject)
                    ?.get("value") as? kotlinx.serialization.json.JsonObject
                    ?: return@withContext RemoteResult.Failure("dsh/model/catalog-malformed", "Missing catalog value")
                val groups = (value["groups"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { g ->
                    val obj = g as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val models = (obj["models"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { m ->
                        val mo = m as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        val id = mo["id"]?.toString()?.trim('"') ?: return@mapNotNull null
                        val reasoning = mo["reasoning"] as? kotlinx.serialization.json.JsonObject
                        val efforts = (reasoning?.get("efforts") as? kotlinx.serialization.json.JsonArray)
                            ?.mapNotNull { e ->
                                val eo = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                val eid = eo["id"]?.toString()?.trim('"') ?: return@mapNotNull null
                                ModelReasoningEffort(
                                    id = eid,
                                    name = eo["name"]?.toString()?.trim('"') ?: eid,
                                    description = eo["description"]?.toString()?.trim('"'),
                                )
                            } ?: emptyList()
                        ModelItem(
                            id = id,
                            name = mo["name"]?.toString()?.trim('"') ?: id,
                            description = mo["description"]?.toString()?.trim('"'),
                            reasoningEfforts = efforts,
                            defaultReasoningEffort = reasoning?.get("defaultEffort")?.toString()?.trim('"'),
                        )
                    } ?: emptyList()
                    ModelProviderGroup(
                        id = obj["id"]?.toString()?.trim('"') ?: "",
                        name = obj["name"]?.toString()?.trim('"') ?: "",
                        models = models,
                    )
                } ?: emptyList()
                val default = value["default"] as? kotlinx.serialization.json.JsonObject
                val defaultModel = default?.get("model")?.toString()?.trim('"') ?: ""
                val routable = (value["routableProviders"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it.toString().trim('"') } ?: emptyList()
                RemoteResult.Success(ModelCatalogInfo(routableProviders = routable, groups = groups, defaultModel = defaultModel))
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/model/catalog-error", e.message ?: "catalog failed")
        }
    }
    override suspend fun getSessionStats(
        hostId: String,
        sessionId: String
    ): RemoteResult<ComposerStats> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "session/projections")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("request", buildJsonObject {
                        put("sessionId", sessionId)
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/session/projections")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    val jsonElem = json.parseToJsonElement(bodyStr)
                    val values = if (jsonElem is kotlinx.serialization.json.JsonObject) {
                        jsonElem["result"]?.let { res ->
                            if (res is kotlinx.serialization.json.JsonObject) {
                                res["value"]?.let { v ->
                                    if (v is kotlinx.serialization.json.JsonObject) v["values"] else null
                                }
                            } else null
                        } ?: jsonElem["values"]
                    } else null

                    if (values is kotlinx.serialization.json.JsonObject) {
                        val statsObj = values["sessionStats"] as? kotlinx.serialization.json.JsonObject
                        if (statsObj != null) {
                            val decodeMs = statsObj["decodeMs"]?.let { runCatching { it.toString().toDouble() }.getOrNull() } ?: 0.0
                            val decodeTokens = statsObj["decodeTokens"]?.let { runCatching { it.toString().toDouble() }.getOrNull() } ?: 0.0
                            val speed = if (decodeMs > 0.0) {
                                Math.round((decodeTokens / (decodeMs / 1000.0)) * 10.0) / 10.0
                            } else 0.0

                            val tokenUsage = values["tokenUsage"] as? kotlinx.serialization.json.JsonObject
                            val uncached = tokenUsage?.get("uncachedInputTokens")?.let { runCatching { it.toString().toDouble() }.getOrNull() } ?: 0.0
                            val cacheRead = tokenUsage?.get("cacheReadTokens")?.let { runCatching { it.toString().toDouble() }.getOrNull() } ?: 0.0
                            val cacheWrite = tokenUsage?.get("cacheWriteTokens")?.let { runCatching { it.toString().toDouble() }.getOrNull() } ?: 0.0
                            val totalInput = uncached + cacheRead + cacheWrite
                            val cacheHit = if (totalInput > 0.0) {
                                Math.round((cacheRead / totalInput) * 100.0).toInt().coerceIn(0, 100)
                            } else 0

                            val contextPercent = if (totalInput > 0.0) {
                                Math.min(100, Math.max(1, Math.round((totalInput / 64000.0) * 100.0).toInt()))
                            } else 0

                            return@withContext RemoteResult.Success(
                                ComposerStats(
                                    outputSpeedTokensPerSec = speed,
                                    contextPercent = contextPercent,
                                    cacheHitPercent = cacheHit
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        RemoteResult.Failure(
            "dsh/session/stats-unavailable",
            "Session stats are unavailable from DSH Host"
        )
    }

    /**
     * 读会话投影 `agentTeam`：官方团队面板的成员名单来源。
     *
     * @param hostId Host 标识。
     * @param sessionId 会话 id。
     * @returns 团队摘要；会话没有团队（或插件没启用）时返回 null。
     */
    override suspend fun getAgentTeam(
        hostId: String,
        sessionId: String,
    ): RemoteResult<AgentTeamSummary?> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", "session/projections")
            put("payload", buildJsonObject {
                put("args", buildJsonObject { put("sessionId", sessionId) })
            })
        }.toString()
        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext RemoteResult.Failure(
                        "dsh/team/unavailable",
                        "session projections failed: ${response.code}",
                    )
                }
                val bodyStr = response.body?.string() ?: "{}"
                val jsonElem = json.parseToJsonElement(bodyStr)
                val values = if (jsonElem is kotlinx.serialization.json.JsonObject) {
                    jsonElem["result"]?.let { res ->
                        if (res is kotlinx.serialization.json.JsonObject) {
                            res["value"]?.let { v ->
                                if (v is kotlinx.serialization.json.JsonObject) v["values"] else null
                            }
                        } else null
                    } ?: jsonElem["values"]
                } else null
                val team = (values as? kotlinx.serialization.json.JsonObject)
                    ?.get("agentTeam") as? kotlinx.serialization.json.JsonObject
                    ?: return@withContext RemoteResult.Success(null)
                val members = (team["members"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                    .mapNotNull { element ->
                        val obj = element as? kotlinx.serialization.json.JsonObject
                            ?: return@mapNotNull null
                        val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)
                            ?.contentOrNull ?: return@mapNotNull null
                        AgentTeamMember(
                            id = id,
                            name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)
                                ?.contentOrNull ?: "成员",
                            role = (obj["role"] as? kotlinx.serialization.json.JsonPrimitive)
                                ?.contentOrNull ?: "teammate",
                            phase = (obj["phase"] as? kotlinx.serialization.json.JsonPrimitive)
                                ?.contentOrNull ?: "active",
                            error = (obj["error"] as? kotlinx.serialization.json.JsonPrimitive)
                                ?.contentOrNull,
                        )
                    }
                val tasks = (team["tasks"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                    .mapNotNull { element ->
                        val obj = element as? kotlinx.serialization.json.JsonObject
                            ?: return@mapNotNull null
                        val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)
                            ?.contentOrNull ?: return@mapNotNull null
                        fun text(key: String): String? =
                            (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                        fun strings(key: String): List<String> =
                            (obj[key] as? kotlinx.serialization.json.JsonArray).orEmpty()
                                .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                        AgentTeamTask(
                            id = id,
                            subject = text("subject") ?: "任务",
                            description = text("description").orEmpty(),
                            status = text("status") ?: "pending",
                            ownerName = text("ownerName"),
                            blockedBy = strings("blockedBy"),
                            writeScopes = strings("writeScopes"),
                            ready = (obj["ready"] as? kotlinx.serialization.json.JsonPrimitive)
                                ?.contentOrNull != "false",
                            writeScopeWarnings = strings("writeScopeWarnings"),
                        )
                    }
                val failure = (team["failure"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.contentOrNull
                RemoteResult.Success(
                    AgentTeamSummary(members = members, tasks = tasks, failure = failure),
                )
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/team/unavailable", e.message ?: "session projections failed")
        }
    }

    override suspend fun getAccountState(hostId: String): RemoteResult<AccountViewInfo> {
        return when (val res = accountRpc("account/getState", buildJsonObject { })) {
            is RemoteResult.Success -> RemoteResult.Success(res.data.parseAccountView())
            is RemoteResult.Failure -> res
        }
    }

    /**
     * 订阅官方 `account/watch` 流（Gateway mux 上的流式 Remote）。
     *
     * 每个 `item` 帧是一份 `AccountView` 快照；`end`/`error` 结束订阅。
     */
    override fun watchAccount(hostId: String): Flow<AccountViewInfo> = callbackFlow {
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val streamId = java.util.UUID.randomUUID().toString()
        val socket = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    val open = buildJsonObject {
                        put("type", "open")
                        put("streamId", streamId)
                        put("endpoint", "account/watch")
                        put("payload", buildJsonObject { put("args", buildJsonObject { }) })
                    }
                    webSocket.send(open.toString())
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    runCatching {
                        val frame = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching
                        fun field(key: String): String? =
                            (frame[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                        if (field("streamId") != streamId) return@runCatching
                        when (field("type")) {
                            "item" -> {
                                val value = frame["value"] as? kotlinx.serialization.json.JsonObject
                                if (value != null) trySend(value.parseAccountView())
                            }
                            "error", "end" -> close()
                        }
                    }
                }

                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
                    close(t)
                }

                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    close()
                }
            },
        )
        awaitClose {
            runCatching { socket.send("{\"type\":\"cancel\",\"streamId\":\"$streamId\"}") }
            runCatching { socket.close(1000, "client closed") }
        }
    }

    override suspend fun startAccountSignIn(
        hostId: String,
        callbackOrigin: String,
    ): RemoteResult<AccountViewInfo> {
        val args = buildJsonObject {
            put("client", accountClientMetadata())
            put("callbackOrigin", callbackOrigin)
            put("loginSource", "web")
        }
        return when (val res = accountRpc("account/startSignIn", args)) {
            is RemoteResult.Success -> RemoteResult.Success(res.data.parseAccountView())
            is RemoteResult.Failure -> res
        }
    }

    override suspend fun signOutAccount(hostId: String): RemoteResult<Boolean> {
        val args = buildJsonObject { put("client", accountClientMetadata()) }
        return when (accountRpc("account/signOut", args)) {
            is RemoteResult.Success -> RemoteResult.Success(true)
            is RemoteResult.Failure -> RemoteResult.Success(false)
        }
    }

    override suspend fun getAccountProfile(hostId: String): RemoteResult<AccountProfileInfo?> {
        val args = buildJsonObject { put("client", accountClientMetadata()) }
        return when (val res = accountRpc("account/getProfile", args)) {
            is RemoteResult.Success -> {
                val value = res.data["value"] as? kotlinx.serialization.json.JsonObject
                if (value == null) {
                    RemoteResult.Success(null)
                } else {
                    RemoteResult.Success(
                        AccountProfileInfo(
                            id = value["id"]?.toString()?.trim('"'),
                            name = value["name"]?.toString()?.trim('"'),
                            contact = value["contact"]?.toString()?.trim('"'),
                            avatarUrl = value["avatarUrl"]?.toString()?.trim('"'),
                        )
                    )
                }
            }
            is RemoteResult.Failure -> res
        }
    }

    override suspend fun getAccountBalance(hostId: String): RemoteResult<AccountBalanceInfo> {
        val args = buildJsonObject { put("client", accountClientMetadata()) }
        fun parseWallets(elem: kotlinx.serialization.json.JsonElement?): List<com.deepseek.dsh.core.model.DeepSeekWallet> {
            val array = elem as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            return array.mapNotNull { item ->
                val obj = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                com.deepseek.dsh.core.model.DeepSeekWallet(
                    currency = obj["currency"]?.toString()?.trim('"') ?: "CNY",
                    balance = obj["balance"]?.toString()?.trim('"') ?: "0.00",
                )
            }
        }
        return when (val res = accountRpc("account/getBalance", args)) {
            is RemoteResult.Success -> RemoteResult.Success(
                AccountBalanceInfo(
                    wallets = parseWallets(res.data["value"]),
                    bonusWallets = parseWallets(res.data["bonusWallets"]),
                )
            )
            is RemoteResult.Failure -> res
        }
    }

    override suspend fun getPluginInventory(
        hostId: String
    ): RemoteResult<List<BuiltInPluginInfo>> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        when (val snapshotRes = getPluginInventorySnapshot(hostId)) {
            is RemoteResult.Success -> {
                val all = (snapshotRes.data.agentPresets.flatMap { it.rows } + snapshotRes.data.entries)
                    .distinctBy { it.packageName }
                RemoteResult.Success(all)
            }
            is RemoteResult.Failure -> snapshotRes
        }
    }

    override suspend fun getPluginInventorySnapshot(
        hostId: String
    ): RemoteResult<PluginInventorySnapshotInfo> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "pluginInventory/list")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {})
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/pluginInventory/list")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    val jsonElem = json.parseToJsonElement(bodyStr)
                    val snapshotObj = if (jsonElem is kotlinx.serialization.json.JsonObject) {
                        jsonElem["result"]?.let { res ->
                            if (res is kotlinx.serialization.json.JsonObject) {
                                res["value"] as? kotlinx.serialization.json.JsonObject
                            } else null
                        } ?: jsonElem
                    } else null

                    if (snapshotObj != null) {
                        val entriesArr = snapshotObj["entries"] as? kotlinx.serialization.json.JsonArray
                        val parsedEntries = entriesArr?.mapNotNull { item ->
                            if (item is kotlinx.serialization.json.JsonObject) {
                                val moduleName = item["moduleName"]?.toString()?.trim('"') ?: return@mapNotNull null
                                val entryId = item["entryId"]?.toString()?.trim('"')
                                val meta = item["meta"] as? kotlinx.serialization.json.JsonObject
                                val title = localizedJsonText(meta?.get("title"))
                                    ?: localizedJsonText(meta?.get("name"))
                                    ?: moduleName.substringAfterLast("/")
                                val description = localizedJsonText(meta?.get("description"))
                                    ?: "系统全局插件"
                                val enabled = item["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true
                                val phase = item["fiberPhase"]?.toString()?.trim('"') ?: "active"
                                BuiltInPluginInfo(
                                    title = title,
                                    packageName = moduleName,
                                    description = description,
                                    enabled = enabled,
                                    phase = phase,
                                    entryId = entryId
                                )
                            } else null
                        } ?: emptyList()

                        val presetsArr = snapshotObj["agentPresets"] as? kotlinx.serialization.json.JsonArray
                        val parsedPresets = presetsArr?.mapNotNull { presetItem ->
                            if (presetItem is kotlinx.serialization.json.JsonObject) {
                                val id = presetItem["id"]?.toString()?.trim('"') ?: return@mapNotNull null
                                val name = presetItem["name"]?.toString()?.trim('"') ?: id
                                val isDefault = presetItem["isDefault"]?.toString()?.toBooleanStrictOrNull() ?: false
                                val broken = presetItem["broken"]?.toString()?.trim('"')
                                val rowsArr = presetItem["rows"] as? kotlinx.serialization.json.JsonArray
                                val rows = rowsArr?.mapNotNull { rowItem ->
                                    if (rowItem is kotlinx.serialization.json.JsonObject) {
                                        val mod = rowItem["moduleName"]?.toString()?.trim('"') ?: return@mapNotNull null
                                        val eid = rowItem["entryId"]?.toString()?.trim('"')
                                        val rMeta = rowItem["meta"] as? kotlinx.serialization.json.JsonObject
                                        val title = localizedJsonText(rMeta?.get("title"))
                                            ?: localizedJsonText(rMeta?.get("name"))
                                            ?: mod.substringAfterLast("/")
                                        val desc = localizedJsonText(rMeta?.get("description"))
                                            ?: "由 Agent 预设按会话组成"
                                        val enabled = rowItem["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true
                                        val phase = rowItem["fiberPhase"]?.toString()?.trim('"') ?: "active"
                                        val cond = rowItem["condition"]?.toString()?.trim('"')
                                        BuiltInPluginInfo(
                                            title = title,
                                            packageName = mod,
                                            description = desc,
                                            enabled = enabled,
                                            phase = phase,
                                            entryId = eid,
                                            presetId = id,
                                            condition = cond
                                        )
                                    } else null
                                } ?: emptyList()

                                AgentPresetGroupInfo(
                                    id = id,
                                    name = name,
                                    description = localizedJsonText(presetItem["description"] as? kotlinx.serialization.json.JsonObject),
                                    isDefault = isDefault,
                                    broken = broken,
                                    rows = rows
                                )
                            } else null
                        } ?: emptyList()

                        return@withContext RemoteResult.Success(
                            PluginInventorySnapshotInfo(
                                managementAvailable = snapshotObj["managementAvailable"]?.toString()?.toBooleanStrictOrNull() ?: false,
                                entries = parsedEntries,
                                agentPresets = parsedPresets
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        RemoteResult.Success(
            PluginInventorySnapshotInfo(
                managementAvailable = false,
                entries = emptyList(),
                agentPresets = emptyList()
            )
        )
    }

    override suspend fun getPluginManagerSnapshot(
        hostId: String
    ): RemoteResult<PluginManagerSnapshot> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "pluginManager/listBundles")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {})
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/pluginManager/listBundles")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        // Host 未提供基础配置时保持为空，不伪造配置项。
        val basicConfigs = emptyList<PluginConfigItem>()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    val jsonElem = json.parseToJsonElement(bodyStr)
                    val bundles = if (jsonElem is kotlinx.serialization.json.JsonObject) {
                        jsonElem["result"]?.let { r ->
                            if (r is kotlinx.serialization.json.JsonObject) r["value"] as? kotlinx.serialization.json.JsonArray else null
                        } ?: jsonElem["bundles"] as? kotlinx.serialization.json.JsonArray
                    } else if (jsonElem is kotlinx.serialization.json.JsonArray) jsonElem else null

                    if (bundles != null) {
                        val experimentalList = mutableListOf<PluginBundleItem>()
                        val installedList = mutableListOf<PluginBundleItem>()

                        bundles.forEach { bElem ->
                            if (bElem is kotlinx.serialization.json.JsonObject) {
                                val name = bElem["name"]?.toString()?.trim('"') ?: return@forEach
                                val meta = bElem["meta"] as? kotlinx.serialization.json.JsonObject
                                val title = localizedJsonText(meta?.get("title"))
                                    ?: localizedJsonText(meta?.get("name"))
                                    ?: localizedJsonText(bElem["title"])
                                    ?: name.substringAfterLast("/")
                                val description = localizedJsonText(meta?.get("description"))
                                    ?: localizedJsonText(bElem["description"])
                                    ?: ""
                                val enabled = bElem["enabled"]?.toString()?.toBooleanStrictOrNull() ?: false
                                val isExperimental = name.contains("experimental") ||
                                    (meta?.get("beta")?.toString()?.toBooleanStrictOrNull() ?: false)

                                val componentRows = (bElem["rows"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { row ->
                                    val rowObj = row as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                    val module = rowObj["moduleName"]?.toString()?.trim('"') ?: return@mapNotNull null
                                    val rowMeta = rowObj["meta"] as? kotlinx.serialization.json.JsonObject
                                    PluginComponentInfo(
                                        rowId = rowObj["rowId"]?.toString()?.trim('"') ?: module,
                                        moduleName = module,
                                        title = localizedJsonText(rowMeta?.get("title"))
                                            ?: localizedJsonText(rowMeta?.get("name"))
                                            ?: module.substringAfterLast("/"),
                                        description = localizedJsonText(rowMeta?.get("description")).orEmpty(),
                                        enabled = rowObj["entryId"] != null,
                                        phase = "active",
                                        entryId = rowObj["entryId"]?.toString()?.trim('"'),
                                    )
                                }.orEmpty()
                                val item = PluginBundleItem(
                                    packageName = name,
                                    title = title,
                                    description = description,
                                    isExperimental = (bElem["official"]?.toString() == "true" && bElem["optional"]?.toString() == "true"),
                                    enabled = enabled,
                                    iconKey = when {
                                        name.contains("team") -> "team"
                                        name.contains("voice") -> "voice"
                                        name.contains("auto-review") -> "shield"
                                        name.contains("inspector") -> "debug"
                                        else -> "default"
                                    },
                                    version = bElem["version"]?.toString()?.trim('"'),
                                    official = bElem["official"]?.toString() == "true",
                                    installed = bElem["installed"]?.toString() == "true",
                                    // `installation` = 随安装自带；`installed` = 用户后来装的；
                                    // `missing` = 声明了但设备上没有。已安装列表只认第二种。
                                    availability = bElem["availability"]?.toString()?.trim('"') ?: "",
                                    removable = bElem["removable"]?.toString() != "false",
                                    error = (bElem["error"] as? kotlinx.serialization.json.JsonObject)?.get("code")?.toString()?.trim('"'),
                                    rows = componentRows,
                                    iconData = localizedJsonText((bElem["meta"] as? kotlinx.serialization.json.JsonObject)?.get("icon")),
                                )
                                // 已安装列表只列第三方：官方包（official）与基础层 profile bundle
                                // 都不该出现在这里——它们要么在实验性分组，要么是底座不该列出。
                                val isOfficial = bElem["official"]?.toString() == "true"
                                val isBuiltinProfile = name in setOf(
                                    "@deepseek-ai/dsh-base", "@deepseek-ai/dsh-web-app",
                                    "@deepseek-ai/dsh-headless", "@deepseek-ai/dsh-sdk-app",
                                    "@deepseek-ai/dsh-acp-app", "@deepseek-ai/dsh-sdk-minimal",
                                )
                                if (isExperimental) {
                                    experimentalList.add(item)
                                } else if (!isOfficial && !isBuiltinProfile) {
                                    installedList.add(item)
                                }
                            }
                        }

                        if (experimentalList.isNotEmpty() || installedList.isNotEmpty()) {
                            return@withContext RemoteResult.Success(
                                PluginManagerSnapshot(
                                    basicConfigs = basicConfigs,
                                    experimentalBundles = experimentalList,
                                    installedBundles = installedList
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Host 未提供插件管理数据时保持空快照，不伪造演示插件。
        RemoteResult.Success(
            PluginManagerSnapshot(
                basicConfigs = emptyList(),
                experimentalBundles = emptyList(),
                installedBundles = emptyList()
            )
        )
    }

    override suspend fun getPluginRows(hostId: String): RemoteResult<List<PluginRowInfo>> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val payload = buildJsonObject {
                put("type", "client-request")
                put("rpcId", java.util.UUID.randomUUID().toString())
                put("method", "pluginManager/listPlugins")
                put("payload", buildJsonObject { put("args", buildJsonObject {}) })
            }.toString()
            val request = Request.Builder()
                .url("$baseUrl/api/pluginManager/listPlugins")
                .post(payload.toRequestBody(mediaTypeJson))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@withContext RemoteResult.Failure("dsh/plugin/list", "HTTP ${response.code}")
                    val root = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return@withContext RemoteResult.Success(emptyList())
                    val arr = (root["result"] as? kotlinx.serialization.json.JsonObject)?.get("value") as? kotlinx.serialization.json.JsonArray
                        ?: root["rows"] as? kotlinx.serialization.json.JsonArray
                        ?: return@withContext RemoteResult.Success(emptyList())
                    val rows = arr.mapNotNull { item ->
                        val obj = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        val module = obj["moduleName"]?.toString()?.trim('"') ?: return@mapNotNull null
                        val meta = obj["meta"] as? kotlinx.serialization.json.JsonObject
                        PluginRowInfo(
                            entryId = obj["entryId"]?.toString()?.trim('"'),
                            moduleName = module,
                            title = localizedJsonText(meta?.get("title")) ?: localizedJsonText(meta?.get("name")) ?: module.substringAfterLast("/"),
                            description = localizedJsonText(meta?.get("description")).orEmpty(),
                            enabled = obj["enabled"]?.toString() != "false",
                            phase = obj["phase"]?.toString()?.trim('"') ?: obj["fiberPhase"]?.toString()?.trim('"') ?: "active",
                            iconData = localizedJsonText(meta?.get("icon")),
                        )
                    }
                    RemoteResult.Success(rows)
                }
            } catch (e: Exception) {
                RemoteResult.Failure("dsh/plugin/list", e.message ?: "list failed")
            }
        }
    override suspend fun installPlugin(
        hostId: String,
        packageName: String,
        requestId: String?,
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "pluginManager/installBundle")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("spec", packageName)
                    if (requestId != null) {
                        put("options", buildJsonObject { put("requestId", requestId) })
                    }
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/pluginManager/installBundle")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    val jsonElem = json.parseToJsonElement(bodyStr)
                    val resultObj = (jsonElem as? kotlinx.serialization.json.JsonObject)?.get("result") as? kotlinx.serialization.json.JsonObject
                    val ok = resultObj?.get("ok")?.toString()?.toBooleanStrictOrNull() ?: false
                    val valueObj = resultObj?.get("value") as? kotlinx.serialization.json.JsonObject
                    val errorObj = valueObj?.get("error") as? kotlinx.serialization.json.JsonObject
                    val application = valueObj?.get("application")?.toString()?.trim('"')
                    // 安装失败有两种形状：RPC 本身 ok=false，或 RPC 成功但 ChangeResult
                    // 的 application=failed（带 error 或只有 error.code）。两者都是失败。
                    val failed = !ok || errorObj != null || application == "failed"
                    if (failed) {
                        val diag = errorObj?.get("diagnostic")?.toString()?.trim('"')
                            ?.takeIf { it.isNotBlank() }
                            ?: errorObj?.get("message")?.toString()?.trim('"')
                                ?.takeIf { it.isNotBlank() }
                            ?: errorObj?.get("code")?.toString()?.trim('"')
                            ?: "安装失败"
                        RemoteResult.Failure("dsh/plugin/install-failed", diag)
                    } else {
                        RemoteResult.Success(true)
                    }
                } else {
                    RemoteResult.Failure("dsh/plugin/install-failed", "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/plugin/install-error", e.message ?: "安装插件网络异常")
        }
    }

    override suspend fun uninstallPlugin(
        hostId: String,
        packageName: String
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "pluginManager/removeBundle")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("name", packageName)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/pluginManager/removeBundle")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    RemoteResult.Success(true)
                } else {
                    RemoteResult.Failure("dsh/plugin/uninstall-failed", "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/plugin/uninstall-error", e.message ?: "卸载插件网络异常")
        }
    }

    override suspend fun setBundleEnabled(
        hostId: String,
        packageName: String,
        enabled: Boolean
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "pluginManager/setBundleEnabled")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("name", packageName)
                    put("enabled", enabled)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/pluginManager/setBundleEnabled")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                RemoteResult.Success(response.isSuccessful)
            }
        } catch (_: Exception) {
            RemoteResult.Success(true)
        }
    }

    override suspend fun setPluginEnabled(
        hostId: String,
        entryId: String,
        enabled: Boolean
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "pluginManager/setPluginEnabled")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("id", entryId)
                    put("enabled", enabled)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/pluginManager/setPluginEnabled")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                // Host 对未知插件/只读行返回 ok:false，不能当成功吞掉。
                val ok = response.isSuccessful &&
                    !body.contains("\"ok\":false")
                if (ok) RemoteResult.Success(true)
                else RemoteResult.Failure(
                    "dsh/plugin/set-enabled-failed",
                    body.take(300).ifBlank { "HTTP ${response.code}" },
                )
            }
        } catch (error: Exception) {
            RemoteResult.Failure("dsh/plugin/set-enabled-error", error.message ?: "网络异常")
        }
    }

    override suspend fun openSettingsDocument(hostId: String): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", "settings/openSettingsDocument")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {})
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                RemoteResult.Success(response.isSuccessful)
            }
        } catch (_: Exception) {
            RemoteResult.Success(true)
        }
    }

    override suspend fun getRemoteSettings(hostId: String): RemoteResult<Map<String, String>> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "settings/describe")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {})
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    val jsonElem = json.parseToJsonElement(bodyStr)
                    val map = mutableMapOf<String, String>()
                    val resObj = if (jsonElem is kotlinx.serialization.json.JsonObject) {
                        jsonElem["result"]?.let { r ->
                            if (r is kotlinx.serialization.json.JsonObject) r["value"] as? kotlinx.serialization.json.JsonObject else null
                        } ?: jsonElem
                    } else null

                    val namespaces = resObj?.get("namespaces") as? kotlinx.serialization.json.JsonArray
                    namespaces?.forEach { nsItem ->
                        if (nsItem is kotlinx.serialization.json.JsonObject) {
                            val ns = nsItem["ns"]?.toString()?.trim('"') ?: return@forEach
                            val valObj = nsItem["value"]
                            if (valObj != null) {
                                map[ns] = valObj.toString()
                            }
                        }
                    }
                    if (map.isNotEmpty()) return@withContext RemoteResult.Success(map)
                }
            }
        } catch (_: Exception) {}

        RemoteResult.Success(emptyMap())
    }

    override suspend fun updateRemoteSettings(
        hostId: String,
        ns: String,
        patchJson: String
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val patchObj = runCatching { json.parseToJsonElement(patchJson) }.getOrNull() ?: buildJsonObject {}
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "settings/update")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("ns", ns)
                    put("patch", patchObj)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                RemoteResult.Success(response.isSuccessful)
            }
        } catch (_: Exception) {
            RemoteResult.Success(true)
        }
    }

    override suspend fun rpcRaw(hostId: String, method: String, argsJson: String): RemoteResult<String> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val args = runCatching { json.parseToJsonElement(argsJson) }.getOrNull() ?: buildJsonObject {}
            val payload = buildJsonObject {
                put("type", "client-request")
                put("rpcId", java.util.UUID.randomUUID().toString())
                put("method", method)
                put("payload", buildJsonObject { put("args", args) })
            }.toString()
            val request = Request.Builder()
                .url("$baseUrl/api/$method")
                .post(payload.toRequestBody(mediaTypeJson))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) RemoteResult.Success(text)
                    else RemoteResult.Failure("dsh/rpc-failed", "HTTP ${response.code}: ${text.take(200)}")
                }
            } catch (e: Exception) {
                RemoteResult.Failure("dsh/rpc-network", e.message ?: "rpc failed")
            }
        }
    override suspend fun pluginRpc(
        hostId: String,
        method: String,
        payloadJson: String,
    ): RemoteResult<String> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        // A plugin route takes its own payload verbatim; unlike `rpcRaw` there is
        // no `{args}` wrapper, because the plugin reads `envelopeRequest.payload`.
        val body = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", method)
            put(
                "payload",
                runCatching { json.parseToJsonElement(payloadJson) }.getOrNull() ?: buildJsonObject {},
            )
        }.toString()
        val request = Request.Builder()
            .url("$baseUrl/api/$method")
            .post(body.toRequestBody(mediaTypeJson))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) RemoteResult.Success(text)
                else RemoteResult.Failure("dsh/rpc-failed", "HTTP ${response.code}: ${text.take(200)}")
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/rpc-network", e.message ?: "plugin rpc failed")
        }
    }

    override suspend fun getAutomationTasks(
        hostId: String
    ): RemoteResult<List<AutomationTaskItem>> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", "schedule/catalog")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {})
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    val jsonElem = json.parseToJsonElement(bodyStr)
                    val items = if (jsonElem is kotlinx.serialization.json.JsonObject) {
                        jsonElem["result"]?.let { r ->
                            if (r is kotlinx.serialization.json.JsonObject) r["value"] as? kotlinx.serialization.json.JsonArray else null
                        } ?: jsonElem["items"] as? kotlinx.serialization.json.JsonArray
                    } else if (jsonElem is kotlinx.serialization.json.JsonArray) jsonElem else null

                    if (items != null) {
                        val list = items.mapNotNull { item ->
                            if (item is kotlinx.serialization.json.JsonObject) {
                                val id = item["id"]?.toString()?.trim('"') ?: return@mapNotNull null
                                val title = item["title"]?.toString()?.trim('"') ?: "自动化定时任务"
                                val prompt = item["prompt"]?.toString()?.trim('"') ?: ""
                                val status = item["status"]?.toString()?.trim('"') ?: "active"
                                val timing = item["at"]?.toString() ?: item["cron"]?.toString() ?: item["every_seconds"]?.toString() ?: "按计划执行"
                                AutomationTaskItem(
                                    id = id,
                                    title = title,
                                    summary = "$timing · $prompt",
                                    inactive = status != "active",
                                    prompt = prompt
                                )
                            } else null
                        }
                        return@withContext RemoteResult.Success(list)
                    }
                }
            }
        } catch (_: Exception) {}

        RemoteResult.Success(emptyList())
    }

    override suspend fun getTeamTasks(
        hostId: String
    ): RemoteResult<List<TeamTaskItem>> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", "team/listTasks")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {})
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    val jsonElem = json.parseToJsonElement(bodyStr)
                    val items = if (jsonElem is kotlinx.serialization.json.JsonObject) {
                        jsonElem["result"]?.let { r ->
                            if (r is kotlinx.serialization.json.JsonObject) r["value"] as? kotlinx.serialization.json.JsonArray else null
                        } ?: jsonElem["tasks"] as? kotlinx.serialization.json.JsonArray
                    } else null

                    if (items != null) {
                        val list = items.mapNotNull { item ->
                            if (item is kotlinx.serialization.json.JsonObject) {
                                val id = item["id"]?.toString()?.trim('"') ?: return@mapNotNull null
                                val subject = item["subject"]?.toString()?.trim('"') ?: item["title"]?.toString()?.trim('"') ?: "任务"
                                val description = item["description"]?.toString()?.trim('"') ?: ""
                                val status = item["status"]?.toString()?.trim('"') ?: "pending"
                                val owner = item["ownerName"]?.toString()?.trim('"')
                                TeamTaskItem(
                                    id = id,
                                    subject = subject,
                                    description = description,
                                    status = status,
                                    ownerName = owner
                                )
                            } else null
                        }
                        return@withContext RemoteResult.Success(list)
                    }
                }
            }
        } catch (_: Exception) {}

        RemoteResult.Success(emptyList())
    }

    override suspend fun sendMessage(
        hostId: String,
        sessionId: String,
        text: String,
        images: List<PromptImagePart>,
    ): RemoteResult<ChatMessage> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val requestId = java.util.UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "session/prompt")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("request", buildJsonObject {
                        put("requestId", requestId)
                        put("sessionId", sessionId)
                        put("mode", "queue")
                        put("content", buildJsonArray {
                            if (text.isNotBlank()) {
                                add(buildJsonObject { put("type", "text"); put("text", text) })
                            }
                            images.forEach { image ->
                                add(buildJsonObject {
                                    put("type", "image")
                                    put("mediaType", image.mediaType)
                                    put("data", image.base64)
                                    image.name?.let { put("name", it) }
                                })
                            }
                        })
                    })
                })
            })
        }.toString()

        // 先订阅会话事件流，再提交 prompt：Host 只在有 follow 订阅者时驱动 turn。
        startSessionFollow(sessionId, hostId)
        val request = Request.Builder()
            .url("$baseUrl/api/session/prompt")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext RemoteResult.Failure(
                        "dsh/remote/error",
                        "Send message failed, HTTP ${response.code}"
                    )
                }
                val envelope = runCatching {
                    json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                }.getOrNull()
                val result = envelope?.get("result") as? kotlinx.serialization.json.JsonObject
                if (result != null && result["ok"]?.toString() == "false") {
                    val error = result["error"] as? kotlinx.serialization.json.JsonObject
                    val code = (error?.get("code") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: "dsh/remote/error"
                    val message = (error?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: "Host rejected the prompt"
                    return@withContext RemoteResult.Failure(code, message)
                }
                val msg = ChatMessage(
                    hostId = hostId,
                    sessionId = sessionId,
                    messageId = "msg_${System.currentTimeMillis()}",
                    role = MessageRole.USER,
                    content = text,
                    createdAt = System.currentTimeMillis()
                )
                RemoteResult.Success(msg)
            }
        } catch (e: IOException) {
            RemoteResult.Failure("dsh/net/timeout", e.message ?: "Network error sending message")
        }
    }

    /** 每个会话最近收到的原始帧（开发者工具/会话数据诊断用），最多 500 条。 */
    /** 官方 workflow 运行聚合状态：sessionId -> runId -> 快照。 */
    private val workflowRuns =
        java.util.concurrent.ConcurrentHashMap<String,
            java.util.concurrent.ConcurrentHashMap<String,
                com.deepseek.dsh.core.model.WorkflowRunInfo>>()

    private val rawFrames =
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedDeque<SessionRawFrame>>()
    private val rawFrameCounters =
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    override fun rawSessionFrames(sessionId: String): List<SessionRawFrame> =
        rawFrames[sessionId]?.toList() ?: emptyList()

    /** 记录一条原始会话帧；按会话封顶 500 条，避免长期运行占内存。 */
    private fun recordRawFrame(sessionId: String, text: String) {
        val counter = rawFrameCounters.getOrPut(sessionId) {
            java.util.concurrent.atomic.AtomicInteger(0)
        }
        val index = counter.incrementAndGet()
        val type = runCatching {
            val root = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
            (root?.get("type") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: "frame"
        }.getOrDefault("frame")
        val queue = rawFrames.getOrPut(sessionId) {
            java.util.concurrent.ConcurrentLinkedDeque()
        }
        queue.addLast(SessionRawFrame(index, type, text, System.currentTimeMillis()))
        while (queue.size > 500) queue.pollFirst()
    }

    private val followSockets = mutableListOf<okhttp3.WebSocket>()
    /** 每个会话只保留一条 follow 连接：打开会话和发送 prompt 都会订阅，重复会翻倍 delta。 */
    private val followSocketsBySession =
        java.util.concurrent.ConcurrentHashMap<String, okhttp3.WebSocket>()
    private val eventBus = kotlinx.coroutines.flow.MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 128)

    /** Host 全局转发事件（Gateway `$events` 流）的事件名。 */
    private val forwardedBus =
        kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 64)
    private val forwardedSockets = mutableListOf<okhttp3.WebSocket>()
    private val forwardedStarted = mutableSetOf<String>()
    private val forwardedScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    /** Host waterfall 请求（approval/request 等），等 App 回答。 */
    private val waterfallBus =
        kotlinx.coroutines.flow.MutableSharedFlow<ForwardedWaterfall>(extraBufferCapacity = 32)
    private val answeredWaterfalls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** 转发事件的带参副本（插件安装进度等）。 */
    private val eventPayloadBus =
        kotlinx.coroutines.flow.MutableSharedFlow<ForwardedEvent>(extraBufferCapacity = 64)

    @Volatile
    private var forwardedClientId: String? = null

    /** follow 快照给出的历史游标：sessionId -> 最早的已加载 seq。 */
    private val historyCursors =
        java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** sessionId 是否还有更早的历史可翻。 */
    private val historyHasMore =
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val toolNames = mutableMapOf<String, String>()
    private val toolArgs = mutableMapOf<String, String>()

    /** 订阅 session/follow 流，驱动 Host 执行已入队的 prompt，并接收工具/消息帧。 */
    private fun startSessionFollow(sessionId: String, hostId: String) {
        if (followSocketsBySession[sessionId] != null) return
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val request = okhttp3.Request.Builder().url(wsUrl).build()
        val socket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                println("DSH_FOLLOW ws open session=$sessionId")
                val streamId = java.util.UUID.randomUUID().toString()
                val open = buildJsonObject {
                    put("type", "open")
                    put("streamId", streamId)
                    put("endpoint", "session/follow")
                    put("payload", buildJsonObject {
                        put("args", buildJsonObject {
                            put("request", buildJsonObject {
                                put("address", buildJsonObject {
                                    put("kind", "session")
                                    put("sessionId", sessionId)
                                })
                                put("assistantStream", true)
                            })
                        })
                    })
                }
                webSocket.send(open.toString())
            }
            override fun onMessage(
                webSocket: okhttp3.WebSocket, text: String) {
                recordRawFrame(sessionId, text)
                try {
                    val root = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject ?: return
                    if (root["type"]?.toString()?.trim('"') != "item") return
                    val value = root["value"] as? kotlinx.serialization.json.JsonObject ?: return

                    when (value["type"]?.toString()?.trim('"')) {
                        // follow 的首帧是完整历史窗口：重放它，并在 hasMore 时用
                        // session/page 继续往前翻，补全更早的历史。
                        "snapshot" -> {
                            val records = value["records"]
                                as? kotlinx.serialization.json.JsonArray
                            var minSeq = 0L
                            // 断线重连时正在输出的助手内容：从 baseline 折叠文本增量，
                            // 后续 live chunk 继续 append。
                            val baselineText = StringBuilder()
                            val baselineReasoning = StringBuilder()
                            run {
                                val active = (value["assistantStream"]
                                    as? kotlinx.serialization.json.JsonObject)
                                    ?.get("activeAttempt")
                                    as? kotlinx.serialization.json.JsonObject
                                val stream = active?.get("stream")
                                    as? kotlinx.serialization.json.JsonArray
                                stream?.forEach { timed ->
                                    val chunk = (timed as? kotlinx.serialization.json.JsonObject)
                                        ?.get("chunk")
                                        as? kotlinx.serialization.json.JsonObject
                                        ?: return@forEach
                                    val text = (chunk["text"]
                                        as? kotlinx.serialization.json.JsonPrimitive)
                                        ?.content.orEmpty()
                                    when (chunk["type"]?.toString()?.trim('"')) {
                                        "text-delta" -> baselineText.append(text)
                                        "reasoning-delta" -> baselineReasoning.append(text)
                                    }
                                }
                            }
                            if (baselineReasoning.isNotBlank()) {
                                eventBus.tryEmit(
                                    RemoteEvent.ReasoningDelta(
                                        sessionId,
                                        baselineReasoning.toString(),
                                    ),
                                )
                            }
                            if (baselineText.isNotBlank()) {
                                eventBus.tryEmit(
                                    RemoteEvent.MessageDelta(sessionId, baselineText.toString()),
                                )
                            }
                            records?.forEach { record ->
                                val entry = record as? kotlinx.serialization.json.JsonObject
                                    ?: return@forEach
                                if (entry["type"]?.toString()?.trim('"') != "event") {
                                    return@forEach
                                }
                                val event = entry["event"]
                                    as? kotlinx.serialization.json.JsonObject ?: return@forEach
                                val seq = event["seq"]?.toString()?.toLongOrNull() ?: 0L
                                if (seq > 0 && (minSeq == 0L || seq < minSeq)) minSeq = seq
                                handleDurableEvent(sessionId, hostId, event)
                            }
                            val hasMore = value["hasMore"]?.toString() == "true"
                            val cursor = value["cursor"]?.toString()?.toLongOrNull()
                                ?: minSeq
                            if (cursor > 0) historyCursors[sessionId] = cursor
                            historyHasMore[sessionId] = hasMore
                        }
                        "event" -> {
                            val event = value["event"]
                                as? kotlinx.serialization.json.JsonObject ?: return
                            handleDurableEvent(sessionId, hostId, event)
                        }
                        // 正在输出的助手文本增量：让 UI 实时显示（而不用等 turn 结束）。
                        "assistant-stream" -> {
                            val frame = value["frame"]
                                as? kotlinx.serialization.json.JsonObject ?: return
                            if (frame["type"]?.toString()?.trim('"') != "chunk") return
                            val chunk = frame["chunk"]
                                as? kotlinx.serialization.json.JsonObject ?: return
                            val text = (chunk["text"]
                                as? kotlinx.serialization.json.JsonPrimitive)?.content
                            if (text.isNullOrEmpty()) return
                            when (chunk["type"]?.toString()?.trim('"')) {
                                "text-delta" -> eventBus.tryEmit(
                                    RemoteEvent.MessageDelta(sessionId, text),
                                )
                                "reasoning-delta" -> eventBus.tryEmit(
                                    RemoteEvent.ReasoningDelta(sessionId, text),
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                followSocketsBySession.remove(sessionId)
                println("DSH_FOLLOW fail ${t.message} http=${response?.code}")
            }

            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                followSocketsBySession.remove(sessionId)
            }
        })
        followSockets.add(socket)
        followSocketsBySession[sessionId] = socket
    }


    /** 把一条 durable 事件转成 ChatMessage 投递给数据层。 */
    private fun emitDurableMessage(
        sessionId: String,
        hostId: String,
        seq: Long,
        messageId: String,
        role: MessageRole,
        content: String,
    ) {
        if (content.isBlank()) return
        eventBus.tryEmit(
            RemoteEvent.MessageDone(
                sessionId = sessionId,
                message = ChatMessage(
                    hostId = hostId,
                    sessionId = sessionId,
                    messageId = messageId,
                    role = role,
                    content = content,
                    seq = seq,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        )
    }


    /** 聚合官方 tool-workflow 事件并推给 UI（每个事件都带完整快照）。 */
    private fun applyWorkflowEvent(
        sessionId: String,
        eventType: String,
        data: kotlinx.serialization.json.JsonObject,
    ) {
        fun field(key: String): String? =
            (data[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        val runId = field("runId") ?: return
        val runs = workflowRuns.getOrPut(sessionId) {
            java.util.concurrent.ConcurrentHashMap()
        }
        val current = runs[runId] ?: com.deepseek.dsh.core.model.WorkflowRunInfo(runId = runId)

        // 成员按 phase 分组；phase 缺省与空串是两种身份（与官方一致）。
        fun updateMembers(transform: (MutableList<com.deepseek.dsh.core.model.WorkflowPhaseInfo>)
            -> Unit): List<com.deepseek.dsh.core.model.WorkflowPhaseInfo> {
            val phases = current.phases.map { phase ->
                com.deepseek.dsh.core.model.WorkflowPhaseInfo(
                    phase = phase.phase,
                    members = phase.members.toMutableList(),
                )
            }.toMutableList()
            transform(phases)
            return phases
        }

        val next = when (eventType) {
            "tool-workflow/run-start" -> current.copy(
                name = field("name") ?: current.name,
                status = "running",
            )
            "tool-workflow/agent-start" -> {
                val phase = field("phase")
                val member = com.deepseek.dsh.core.model.WorkflowMemberInfo(
                    seq = field("seq")?.toIntOrNull() ?: 0,
                    label = field("label").orEmpty(),
                    childId = field("childId").orEmpty(),
                    status = "running",
                )
                val phases = updateMembers { list ->
                    val existing = list.firstOrNull { it.phase == phase }
                    if (existing != null) {
                        list[list.indexOf(existing)] = existing.copy(
                            members = existing.members + member,
                        )
                    } else {
                        list += com.deepseek.dsh.core.model.WorkflowPhaseInfo(
                            phase = phase,
                            members = listOf(member),
                        )
                    }
                }
                current.copy(status = "running", phases = phases)
            }
            "tool-workflow/agent-end" -> {
                val seq = field("seq")?.toIntOrNull()
                val outcome = field("outcome") ?: "completed"
                val phases = updateMembers { list ->
                    list.forEachIndexed { index, phase ->
                        if (phase.members.any { it.seq == seq }) {
                            list[index] = phase.copy(
                                members = phase.members.map { member ->
                                    if (member.seq == seq) member.copy(status = outcome) else member
                                },
                            )
                        }
                    }
                }
                current.copy(phases = phases)
            }
            else -> current.copy(status = field("stopReason").orEmpty().let { reason ->
                when (reason) {
                    "completed" -> "completed"
                    "cancelled" -> "cancelled"
                    "error" -> "failed"
                    else -> reason.ifBlank { "completed" }
                }
            })
        }
        runs[runId] = next
        eventBus.tryEmit(RemoteEvent.WorkflowRunUpdated(sessionId, next))
    }

    /** snapshot 与 live event 共用的 durable 事件处理。 */
    private fun handleDurableEvent(
        sessionId: String,
        hostId: String,
        event: kotlinx.serialization.json.JsonObject,
    ) {
        val eventType = event["type"]?.toString()?.trim('"') ?: return
        val data = event["data"] as? kotlinx.serialization.json.JsonObject ?: return
        val seq = event["seq"]?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
        when (eventType) {
            "tool-workflow/run-start", "tool-workflow/agent-start",
            "tool-workflow/agent-end", "tool-workflow/run-end" -> {
                applyWorkflowEvent(sessionId, eventType, data)
            }
            "user/message" -> {
                // Host 会把「Current runtime context」「Time sampled …」这类注入内容写成
                // user/message；它们只进模型上下文，不是用户输入，聊天记录里不能显示。
                val sourceKind = ((data["source"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("kind") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                if (sourceKind != null && sourceKind != "user") return
                val message = data["message"] as? kotlinx.serialization.json.JsonObject
                val blocks = (message?.get("content") ?: data["content"])
                    as? kotlinx.serialization.json.JsonArray
                val body = blocks?.mapNotNull { part ->
                    val obj = part as? kotlinx.serialization.json.JsonObject
                        ?: return@mapNotNull null
                    if (obj["type"]?.toString()?.trim('"') == "text") {
                        obj["text"]?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                    } else {
                        null
                    }
                }?.joinToString("").orEmpty()
                val userMessageId = "user_$seq"
                emitDurableMessage(sessionId, hostId, seq, userMessageId, MessageRole.USER, body)
                // 官方 user/message 里的图片块：记下引用，气泡里渲染缩略图。
                val imageRefs = blocks?.mapNotNull { part ->
                    val obj = part as? kotlinx.serialization.json.JsonObject
                        ?: return@mapNotNull null
                    if (obj["type"]?.toString()?.trim('"') != "image") return@mapNotNull null
                    val attachment = obj["attachment"] as? kotlinx.serialization.json.JsonObject
                    val id = (attachment?.get("attachmentId")
                        as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                        ?: return@mapNotNull null
                    AttachmentImageRef(
                        attachmentId = id,
                        mediaType = (attachment["mediaType"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty(),
                        width = (attachment["width"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            ?.toIntOrNull() ?: 0,
                        height = (attachment["height"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            ?.toIntOrNull() ?: 0,
                        name = (attachment["name"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull,
                    )
                }.orEmpty()
                if (imageRefs.isNotEmpty()) {
                    eventBus.tryEmit(RemoteEvent.UserImages(sessionId, userMessageId, imageRefs))
                }
            }
            // 官方 present 工具的产物声明：把文件列表交给 UI 展示。
            "deliverables/presented" -> {
                val files = (data["files"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { item ->
                        val obj = item as? kotlinx.serialization.json.JsonObject
                            ?: return@mapNotNull null
                        val path = (obj["path"] as? kotlinx.serialization.json.JsonPrimitive)
                            ?.contentOrNull ?: return@mapNotNull null
                        PresentedFileInfo(
                            path = path,
                            description = (obj["description"]
                                as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull,
                        )
                    } ?: emptyList()
                if (files.isNotEmpty()) {
                    eventBus.tryEmit(
                        RemoteEvent.DeliverablesPresented(sessionId, files),
                    )
                }
            }
            // 官方 workspace/changes：一轮改动落盘，UI 用 seq 去拉 summary。
            "workspace/changes" -> {
                val turn = data["turn"]?.toString()?.toIntOrNull() ?: return
                eventBus.tryEmit(RemoteEvent.ChangesAnnounced(sessionId, seq, turn))
            }
            "turn/start" -> eventBus.tryEmit(RemoteEvent.SessionRunning(sessionId, true))
            "turn/end" -> {
                eventBus.tryEmit(RemoteEvent.SessionRunning(sessionId, false))
                // 模型/网络失败时 Host 只发 turn/end.reason.error，assistant 消息为空；
                // 以前界面就只剩一个空白气泡。把错误原文落成一条可见的助手消息。
                val reason = data["reason"] as? kotlinx.serialization.json.JsonObject
                val error = reason?.get("error") as? kotlinx.serialization.json.JsonObject
                val message = (error?.get("message")
                    as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                val code = (error?.get("code")
                    as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                if (!message.isNullOrBlank()) {
                    // 完整错误：message 全文 + code（不截断），在气泡里逐行显示。
                    val full = buildString {
                        append("⚠️ ")
                        append(message)
                        if (!code.isNullOrBlank()) append("\n\n错误码：").append(code)
                    }
                    emitDurableMessage(
                        sessionId = sessionId,
                        hostId = hostId,
                        seq = seq,
                        messageId = "error_turn_$seq",
                        role = MessageRole.ASSISTANT,
                        content = full,
                    )
                }
            }
            "assistant/message" -> {
                val message = data["message"] as? kotlinx.serialization.json.JsonObject ?: return
                val blocks = message["content"] as? kotlinx.serialization.json.JsonArray
                val reasoning = blocks?.mapNotNull { part ->
                    val obj = part as? kotlinx.serialization.json.JsonObject
                        ?: return@mapNotNull null
                    if (obj["type"]?.toString()?.trim('"') == "reasoning") {
                        obj["text"]?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                    } else {
                        null
                    }
                }?.joinToString("").orEmpty()
                val answer = blocks?.mapNotNull { part ->
                    val obj = part as? kotlinx.serialization.json.JsonObject
                        ?: return@mapNotNull null
                    if (obj["type"]?.toString()?.trim('"') == "text") {
                        obj["text"]?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                    } else {
                        null
                    }
                }?.joinToString("").orEmpty()
                val content = when {
                    reasoning.isBlank() -> answer
                    answer.isBlank() -> reasoning + "\u0001"
                    else -> reasoning + "\u0001" + answer
                }
                // 官方 messageFeedback 以 assistant message 的 MessageId 为准；
                // 优先用事件里的真实 id，缺失时退回 seq。
                val assistantId = (message["id"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.contentOrNull ?: "assistant_$seq"
                emitDurableMessage(sessionId, hostId, seq, assistantId, MessageRole.ASSISTANT, content)
            }
            "tool/call" -> {
                val callId = data["callId"]?.toString()?.trim('"')
                    ?: data["callID"]?.toString()?.trim('"') ?: return
                val name = data["name"]?.toString()?.trim('"') ?: "tool"
                val args = (data["arguments"]
                    as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: data["arguments"]?.toString()?.trim('"') ?: ""
                toolNames[callId] = name
                toolArgs[callId] = args
                emitDurableMessage(
                    sessionId, hostId, seq, "tool_$callId", MessageRole.TOOL,
                    "\u0002TOOL\u0002$callId\u0002$name\u0002running\u0002$args\u0002$args",
                )
            }
            "tool/result" -> {
                val message = data["message"] as? kotlinx.serialization.json.JsonObject
                val callId = message?.get("toolCallId")?.toString()?.trim('"')
                    ?: data["callId"]?.toString()?.trim('"') ?: return
                val name = toolNames[callId] ?: "tool"
                val blocks = message?.get("content") as? kotlinx.serialization.json.JsonArray
                val body = blocks?.mapNotNull { part ->
                    val obj = part as? kotlinx.serialization.json.JsonObject
                        ?: return@mapNotNull null
                    if (obj["type"]?.toString()?.trim('"') == "text") {
                        obj["text"]?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                    } else {
                        null
                    }
                }?.joinToString("\n").orEmpty()
                val failed = data["error"] != null ||
                    message?.get("isError")?.toString() == "true"
                emitDurableMessage(
                    sessionId, hostId, seq, "tool_$callId", MessageRole.TOOL,
                    "\u0002TOOL\u0002$callId\u0002$name\u0002" +
                        "${if (failed) "error" else "ok"}\u0002$body\u0002" +
                        "${toolArgs.remove(callId) ?: ""}",
                )
            }
        }
    }

    private data class HistoryPage(
        val records: List<kotlinx.serialization.json.JsonObject>,
        val hasMore: Boolean,
        val minSeq: Long,
    )

    /** 按需往前翻一页历史（滚动到顶时由 UI 触发）。 */
    override suspend fun loadOlderHistory(
        hostId: String,
        sessionId: String,
    ): RemoteResult<Boolean> {
        if (historyHasMore[sessionId] != true) return RemoteResult.Success(false)
        val through = historyCursors[sessionId] ?: return RemoteResult.Success(false)
        val page = fetchSessionPage(sessionId, through, through)
            ?: return RemoteResult.Success(false)
        if (page.records.isEmpty()) {
            historyHasMore[sessionId] = false
            return RemoteResult.Success(false)
        }
        page.records.forEach { entry ->
            if (entry["type"]?.toString()?.trim('"') != "event") return@forEach
            val event = entry["event"] as? kotlinx.serialization.json.JsonObject
                ?: return@forEach
            handleDurableEvent(sessionId, hostId, event)
        }
        if (page.minSeq > 1L) historyCursors[sessionId] = page.minSeq
        val more = page.hasMore && page.minSeq > 1L
        historyHasMore[sessionId] = more
        return RemoteResult.Success(more)
    }

    /** 请求一页更早的历史（`session/page`）。 */
    private suspend fun fetchSessionPage(
        sessionId: String,
        throughSeq: Long,
        beforeSeq: Long,
    ): HistoryPage? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "session/page")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("request", buildJsonObject {
                        put("address", buildJsonObject {
                            put("kind", "session")
                            put("sessionId", sessionId)
                        })
                        put("throughSeq", throughSeq)
                        put("beforeSeq", beforeSeq)
                        put("maxMessages", 60)
                    })
                })
            })
        }
        runCatching {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/session/page")
                .post(payload.toString().toRequestBody(mediaTypeJson))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = json.parseToJsonElement(response.body?.string().orEmpty())
                    as? kotlinx.serialization.json.JsonObject ?: return@use null
                val value = ((root["result"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("value")) as? kotlinx.serialization.json.JsonObject
                    ?: return@use null
                val records = (value["records"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                    ?: emptyList()
                val hasMore = value["hasMore"]?.toString() == "true"
                val minSeq = records.mapNotNull { record ->
                    ((record["event"] as? kotlinx.serialization.json.JsonObject)
                        ?.get("seq"))?.toString()?.toLongOrNull()
                }.minOrNull() ?: 0L
                HistoryPage(records, hasMore, minSeq)
            }
        }.getOrNull()
    }

    /**
     * 订阅官方 `workspace/follow` 流（工作区/会话树基线 + 增量）。
     *
     * 这里只把帧转成原始 JSON 交给数据层，由它决定刷新范围。
     */
    override fun workspaceFollow(hostId: String): Flow<String> = callbackFlow {
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val streamId = java.util.UUID.randomUUID().toString()
        val socket = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    val open = buildJsonObject {
                        put("type", "open")
                        put("streamId", streamId)
                        put("endpoint", "workspace/follow")
                        put("payload", buildJsonObject { put("args", buildJsonObject { }) })
                    }
                    webSocket.send(open.toString())
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    runCatching {
                        val frame = json.parseToJsonElement(text)
                            as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching
                        val type = (frame["type"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.content
                        val id = (frame["streamId"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (id != streamId) return@runCatching
                        when (type) {
                            "item" -> {
                                val value = frame["value"]
                                if (value != null) trySend(value.toString())
                            }
                            "error", "end" -> close()
                        }
                    }
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    close(t)
                }

                override fun onClosed(
                    webSocket: okhttp3.WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    close()
                }
            },
        )
        awaitClose {
            runCatching { socket.send("{\"type\":\"cancel\",\"streamId\":\"$streamId\"}") }
            runCatching { socket.close(1000, "client closed") }
        }
    }

    /** 官方 `session/search`：内容搜索，返回命中会话与片段。 */
    override suspend fun searchSessions(
        hostId: String,
        query: String,
    ): RemoteResult<List<SessionSearchHit>> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "session/search")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("request", buildJsonObject { put("query", query) })
                })
            })
        }
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/session/search")
                .post(payload.toString().toRequestBody(mediaTypeJson))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use RemoteResult.Failure(
                        "dsh/search-failed",
                        "HTTP ${response.code}",
                    )
                }
                val root = json.parseToJsonElement(response.body?.string().orEmpty())
                    as? kotlinx.serialization.json.JsonObject
                    ?: return@use RemoteResult.Success(emptyList())
                val value = ((root["result"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("value")) as? kotlinx.serialization.json.JsonObject
                val items = value?.get("items")
                    as? kotlinx.serialization.json.JsonArray
                    ?: return@use RemoteResult.Success(emptyList())
                val hits = items.mapNotNull { item ->
                    val obj = item as? kotlinx.serialization.json.JsonObject
                        ?: return@mapNotNull null
                    val sessionId = (obj["sessionId"]
                        as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: return@mapNotNull null
                    val snippet = (obj["snippet"]
                        as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    SessionSearchHit(sessionId, snippet)
                }
                RemoteResult.Success(hits)
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/search-network", e.message ?: "search failed")
        }
    }

    /** 官方 `session/control`：会话投影基线 + 增量帧。 */
    override fun sessionControl(hostId: String): Flow<String> = callbackFlow {
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val streamId = java.util.UUID.randomUUID().toString()
        val socket = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    val open = buildJsonObject {
                        put("type", "open")
                        put("streamId", streamId)
                        put("endpoint", "session/control")
                        put("payload", buildJsonObject { put("args", buildJsonObject { }) })
                    }
                    webSocket.send(open.toString())
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    runCatching {
                        val frame = json.parseToJsonElement(text)
                            as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching
                        val id = (frame["streamId"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (id != streamId) return@runCatching
                        when ((frame["type"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.content) {
                            "item" -> frame["value"]?.let { trySend(it.toString()) }
                            "error", "end" -> close()
                        }
                    }
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    close(t)
                }

                override fun onClosed(
                    webSocket: okhttp3.WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    close()
                }
            },
        )
        awaitClose {
            runCatching { socket.send("{\"type\":\"cancel\",\"streamId\":\"$streamId\"}") }
            runCatching { socket.close(1000, "client closed") }
        }
    }

    /** 官方 `job/list` 流：每个 item 帧是完整的可见任务集合。 */
    override fun jobList(hostId: String, sessionId: String): Flow<List<HostJobInfo>> =
        callbackFlow {
            val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
            val streamId = java.util.UUID.randomUUID().toString()
            val socket = client.newWebSocket(
                okhttp3.Request.Builder().url(wsUrl).build(),
                object : okhttp3.WebSocketListener() {
                    override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                        val open = buildJsonObject {
                            put("type", "open")
                            put("streamId", streamId)
                            put("endpoint", "job/list")
                            put("payload", buildJsonObject {
                                put("args", buildJsonObject {
                                    put("request", buildJsonObject {
                                        put("sessionId", sessionId)
                                    })
                                })
                            })
                        }
                        webSocket.send(open.toString())
                    }

                    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                        runCatching {
                            val frame = json.parseToJsonElement(text)
                                as? kotlinx.serialization.json.JsonObject
                                ?: return@runCatching
                            val id = (frame["streamId"]
                                as? kotlinx.serialization.json.JsonPrimitive)?.content
                            if (id != streamId) return@runCatching
                            when ((frame["type"]
                                as? kotlinx.serialization.json.JsonPrimitive)?.content) {
                                "item" -> {
                                    val value = frame["value"]
                                        as? kotlinx.serialization.json.JsonObject
                                        ?: return@runCatching
                                    val jobs = (value["jobs"]
                                        as? kotlinx.serialization.json.JsonArray)
                                        ?.mapNotNull { item ->
                                            parseJobView(item)
                                        }
                                    if (jobs != null) trySend(jobs)
                                }
                                "error", "end" -> close()
                            }
                        }
                    }

                    override fun onFailure(
                        webSocket: okhttp3.WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        close(t)
                    }

                    override fun onClosed(
                        webSocket: okhttp3.WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        close()
                    }
                },
            )
            awaitClose {
                runCatching {
                    socket.send("{\"type\":\"cancel\",\"streamId\":\"$streamId\"}")
                }
                runCatching { socket.close(1000, "client closed") }
            }
        }

    /** 官方 `job/follow` 流：opened / output / status 帧原样转发。 */
    override fun jobFollow(
        hostId: String,
        sessionId: String?,
        jobId: String,
        from: Long?,
    ): Flow<String> = callbackFlow {
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val streamId = java.util.UUID.randomUUID().toString()
        val socket = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    val open = buildJsonObject {
                        put("type", "open")
                        put("streamId", streamId)
                        put("endpoint", "job/follow")
                        put("payload", buildJsonObject {
                            put("args", buildJsonObject {
                                put("request", buildJsonObject {
                                    put("jobId", jobId)
                                    if (sessionId != null) put("sessionId", sessionId)
                                    if (from != null) put("from", from)
                                })
                            })
                        })
                    }
                    webSocket.send(open.toString())
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    runCatching {
                        val frame = json.parseToJsonElement(text)
                            as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching
                        val id = (frame["streamId"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (id != streamId) return@runCatching
                        when ((frame["type"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.content) {
                            "item" -> frame["value"]?.let { trySend(it.toString()) }
                            "error", "end" -> close()
                        }
                    }
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    close(t)
                }

                override fun onClosed(
                    webSocket: okhttp3.WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    close()
                }
            },
        )
        awaitClose {
            runCatching {
                socket.send("{\"type\":\"cancel\",\"streamId\":\"$streamId\"}")
            }
            runCatching { socket.close(1000, "client closed") }
        }
    }

    /** 官方 `job/kill`：人为终止一条后台任务。 */
    override suspend fun killJob(
        hostId: String,
        sessionId: String,
        jobId: String,
    ): RemoteResult<String> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "job/kill")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("request", buildJsonObject {
                        put("sessionId", sessionId)
                        put("jobId", jobId)
                    })
                })
            })
        }
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/job/kill")
                .post(payload.toString().toRequestBody(mediaTypeJson))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use RemoteResult.Failure("dsh/job-kill-failed", "HTTP ${response.code}")
                }
                val root = json.parseToJsonElement(response.body?.string().orEmpty())
                    as? kotlinx.serialization.json.JsonObject
                val result = root?.get("result") as? kotlinx.serialization.json.JsonObject
                val error = result?.get("error") as? kotlinx.serialization.json.JsonObject
                if (error != null) {
                    val code = (error["code"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.contentOrNull ?: "dsh/job-kill-failed"
                    val message = (error["message"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.contentOrNull ?: "kill failed"
                    return@use RemoteResult.Failure(code, message)
                }
                val value = result?.get("value") as? kotlinx.serialization.json.JsonObject
                val outcome = (value?.get("outcome") as? kotlinx.serialization.json.JsonPrimitive)
                    ?.contentOrNull ?: "requested"
                RemoteResult.Success(outcome)
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/job-kill-network", e.message ?: "kill failed")
        }
    }

    /** 官方 `/api/session.export`：导出会话日志 ZIP。 */
    override suspend fun downloadSessionExport(
        hostId: String,
        sessionId: String,
    ): RemoteResult<ByteArray> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/api/session.export?sessionId=$sessionId&includeDescendants=true")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use RemoteResult.Failure(
                        "dsh/session-export",
                        "HTTP ${response.code}",
                    )
                }
                RemoteResult.Success(response.body?.bytes() ?: ByteArray(0))
            }
        }.getOrElse { e ->
            RemoteResult.Failure("dsh/session-export-network", e.message ?: "export failed")
        }
    }

    /** 官方 `/api/changes.summary`。 */
    override suspend fun fetchChangesSummary(
        hostId: String,
        sessionId: String,
        seq: Long,
    ): RemoteResult<ChangesSummaryInfo> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/api/changes.summary?sessionId=$sessionId&seq=$seq")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use RemoteResult.Failure(
                        "dsh/changes-summary",
                        "HTTP ${response.code}",
                    )
                }
                val value = json.parseToJsonElement(response.body?.string().orEmpty())
                    as? kotlinx.serialization.json.JsonObject
                    ?: return@use RemoteResult.Failure("dsh/changes-summary", "malformed")
                val files = (value["files"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { item ->
                        val obj = item as? kotlinx.serialization.json.JsonObject
                            ?: return@mapNotNull null
                        fun text(key: String) = (obj[key]
                            as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                        fun number(key: String) = (obj[key]
                            as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            ?.toIntOrNull() ?: 0
                        val path = text("path") ?: return@mapNotNull null
                        ChangedFileInfo(
                            path = path,
                            display = text("display") ?: path,
                            added = number("added"),
                            deleted = number("deleted"),
                            binary = text("binary") == "true",
                            oversized = text("oversized") == "true",
                        )
                    } ?: emptyList()
                fun number(key: String) = (value[key]
                    as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    ?.toIntOrNull() ?: 0
                RemoteResult.Success(
                    ChangesSummaryInfo(
                        turn = number("turn"),
                        seq = seq,
                        files = files,
                        total = number("total").takeIf { it > 0 } ?: files.size,
                        added = number("added"),
                        deleted = number("deleted"),
                    ),
                )
            }
        }.getOrElse { e ->
            RemoteResult.Failure("dsh/changes-summary-network", e.message ?: "summary failed")
        }
    }

    /** 官方 `/api/changes.diff`。 */
    override suspend fun fetchChangesDiff(
        hostId: String,
        sessionId: String,
        seq: Long,
        index: Int,
    ): RemoteResult<ChangesDiffInfo> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/api/changes.diff?sessionId=$sessionId&seq=$seq&index=$index")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use RemoteResult.Failure("dsh/changes-diff", "HTTP ${response.code}")
                }
                val value = json.parseToJsonElement(response.body?.string().orEmpty())
                    as? kotlinx.serialization.json.JsonObject
                    ?: return@use RemoteResult.Failure("dsh/changes-diff", "malformed")
                fun text(key: String) = (value[key]
                    as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                fun flag(key: String) = text(key) == "true"
                val hunks = (value["hunks"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { item ->
                        val obj = item as? kotlinx.serialization.json.JsonObject
                            ?: return@mapNotNull null
                        fun num(key: String) = (obj[key]
                            as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            ?.toIntOrNull() ?: 0
                        DiffHunkInfo(
                            oldStart = num("oldStart"),
                            oldLines = num("oldLines"),
                            newStart = num("newStart"),
                            newLines = num("newLines"),
                            lines = (obj["lines"] as? kotlinx.serialization.json.JsonArray)
                                ?.mapNotNull { line ->
                                    (line as? kotlinx.serialization.json.JsonPrimitive)
                                        ?.contentOrNull
                                } ?: emptyList(),
                        )
                    } ?: emptyList()
                RemoteResult.Success(
                    ChangesDiffInfo(
                        kind = text("kind") ?: "text",
                        path = text("path") ?: "",
                        display = text("display") ?: text("path").orEmpty(),
                        hunks = hunks,
                        before = flag("before"),
                        after = flag("after"),
                        coarse = flag("coarse"),
                    ),
                )
            }
        }.getOrElse { e ->
            RemoteResult.Failure("dsh/changes-diff-network", e.message ?: "diff failed")
        }
    }

    /** 官方 `terminal/environment`。 */
    override suspend fun terminalEnvironment(
        hostId: String,
        sessionId: String,
    ): RemoteResult<TerminalEnvironmentInfo> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "terminal/environment")
            put("payload", buildJsonObject { put("args", buildJsonObject { put("agentId", sessionId) }) })
        }
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/terminal/environment")
                .post(payload.toString().toRequestBody(mediaTypeJson))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use RemoteResult.Failure("dsh/terminal-env", "HTTP ${response.code}")
                }
                val root = json.parseToJsonElement(response.body?.string().orEmpty())
                    as? kotlinx.serialization.json.JsonObject
                val value = (root?.get("result") as? kotlinx.serialization.json.JsonObject)
                    ?.get("value") as? kotlinx.serialization.json.JsonObject
                    ?: return@use RemoteResult.Failure("dsh/terminal-env", "missing value")
                RemoteResult.Success(
                    TerminalEnvironmentInfo(
                        cwd = (value["cwd"] as? kotlinx.serialization.json.JsonPrimitive)
                            ?.contentOrNull.orEmpty(),
                        maxCols = value["maxCols"]?.toString()?.trim('"')?.toIntOrNull() ?: 160,
                        maxRows = value["maxRows"]?.toString()?.trim('"')?.toIntOrNull() ?: 48,
                    ),
                )
            }
        } catch (e: Exception) {
            RemoteResult.Failure("dsh/terminal-env-network", e.message ?: "terminal env failed")
        }
    }

    /** 打开（或复用）终端并 attach。 */
    override suspend fun openTerminal(
        hostId: String,
        sessionId: String,
        cols: Int,
        rows: Int,
        terminalId: String,
    ): RemoteResult<TerminalConnection> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        // create 对已打开的 id 幂等，直接调即可。
        val createPayload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "terminal/create")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("agentId", sessionId)
                    put("request", buildJsonObject {
                        put("id", terminalId)
                        put("cols", cols)
                        put("rows", rows)
                    })
                })
            })
        }
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/terminal/create")
                .post(createPayload.toString().toRequestBody(mediaTypeJson))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use RemoteResult.Failure("dsh/terminal-create", "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            return@withContext RemoteResult.Failure(
                "dsh/terminal-create-network",
                e.message ?: "terminal create failed",
            )
        }
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val streamId = java.util.UUID.randomUUID().toString()
        // replay：终端快照常在建连后立刻到达，调用方开始 collect 前不能被丢掉，
        // 否则页面会永远停在「Connecting…」。
        val frames = kotlinx.coroutines.flow.MutableSharedFlow<String>(
            replay = 16,
            extraBufferCapacity = 240,
        )
        val sequence = java.util.concurrent.atomic.AtomicInteger(1)
        lateinit var socket: okhttp3.WebSocket
        var closed = false
        val handle = object : TerminalConnection {
            override val frames: Flow<String> = frames
            override fun write(data: String) {
                if (closed) return
                val seq = sequence.getAndIncrement()
                val item = buildJsonObject {
                    put("type", "item")
                    put("streamId", streamId)
                    put("value", buildJsonObject {
                        put("type", "write")
                        put("sequence", seq)
                        put("data", data)
                    })
                }
                runCatching { socket.send(item.toString()) }
            }

            override fun resize(cols: Int, rows: Int) {
                if (closed) return
                val seq = sequence.getAndIncrement()
                val item = buildJsonObject {
                    put("type", "item")
                    put("streamId", streamId)
                    put("value", buildJsonObject {
                        put("type", "resize")
                        put("sequence", seq)
                        put("cols", cols)
                        put("rows", rows)
                    })
                }
                runCatching { socket.send(item.toString()) }
            }

            override fun close() {
                closed = true
                runCatching { socket.send("{\"type\":\"cancel\",\"streamId\":\"$streamId\"}") }
                runCatching { socket.close(1000, "client closed") }
            }
        }
        val opened = kotlinx.coroutines.CompletableDeferred<Unit>()
        socket = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    val open = buildJsonObject {
                        put("type", "open")
                        put("streamId", streamId)
                        put("endpoint", "terminal/attach")
                        put("payload", buildJsonObject {
                            put("args", buildJsonObject {
                                put("agentId", sessionId)
                                put("id", terminalId)
                            })
                        })
                    }
                    webSocket.send(open.toString())
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    runCatching {
                        val frame = json.parseToJsonElement(text)
                            as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching
                        val id = (frame["streamId"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (id != streamId) return@runCatching
                        when ((frame["type"]
                            as? kotlinx.serialization.json.JsonPrimitive)?.content) {
                            "item" -> frame["value"]?.let { frames.tryEmit(it.toString()) }
                            "error" -> {
                                frames.tryEmit(
                                    "{\"type\":\"error\",\"message\":"
                                        + (frame["error"]?.toString() ?: "\"unknown\"") + "}",
                                )
                                frames.tryEmit("{\"type\":\"closed\"}")
                            }
                            "end" -> frames.tryEmit("{\"type\":\"closed\"}")
                        }
                    }
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    frames.tryEmit(
                        "{\"type\":\"error\",\"message\":"
                            + kotlinx.serialization.json.JsonPrimitive(
                                t.message ?: "terminal socket failed",
                            ).toString()
                            + "}",
                    )
                    frames.tryEmit("{\"type\":\"closed\"}")
                }

                override fun onClosed(
                    webSocket: okhttp3.WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    frames.tryEmit("{\"type\":\"closed\"}")
                }
            },
        )
        kotlinx.coroutines.withTimeoutOrNull(5_000) { opened.await() }
        RemoteResult.Success(handle)
    }

    /** 官方 `terminal/close`。 */
    override suspend fun closeTerminal(
        hostId: String,
        sessionId: String,
        id: String,
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "terminal/close")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("agentId", sessionId)
                    put("id", id)
                })
            })
        }
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/terminal/close")
                .post(payload.toString().toRequestBody(mediaTypeJson))
                .build()
            client.newCall(request).execute().use { response ->
                RemoteResult.Success(response.isSuccessful)
            }
        } catch (_: Exception) {
            RemoteResult.Success(false)
        }
    }

    /** 解析一条官方 JobView 投影。 */
    private fun parseJobView(element: kotlinx.serialization.json.JsonElement): HostJobInfo? {
        val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
        fun text(key: String): String? =
            (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        fun long(key: String): Long? =
            obj[key]?.toString()?.trim('"')?.toLongOrNull()
        val id = text("id") ?: return null
        return HostJobInfo(
            id = id,
            kind = text("kind").orEmpty(),
            label = text("label").orEmpty(),
            owner = text("owner"),
            status = text("status") ?: "running",
            progress = text("progress"),
            detail = text("detail"),
            startedAt = long("startedAt") ?: 0L,
            finishedAt = long("finishedAt"),
        )
    }

    /**
     * 订阅 Claude Code 模组横幅。
     *
     * 走 Gateway 的流式多路复用 WebSocket（`/api/remote.mux`）：先 `open`
     * `claudeCodeMods/watchBand`，随后每个 `item` 帧是一次重绘的
     * `SurfaceSnapshot`，`end`/`error` 结束订阅。
     */
    override fun watchClaudeCodeBand(hostId: String, sessionId: String): Flow<JsonElement?> = callbackFlow {
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val request = Request.Builder().url(wsUrl).build()
        val streamId = java.util.UUID.randomUUID().toString()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val open = buildJsonObject {
                    put("type", "open")
                    put("streamId", streamId)
                    put("endpoint", "claudeCodeMods/watchBand")
                    put("payload", buildJsonObject {
                        put("args", buildJsonObject { put("agentId", sessionId) })
                    })
                }
                webSocket.send(open.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val frame = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
                        ?: return@runCatching
                    fun field(key: String): String? =
                        (frame[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    if (field("streamId") != streamId) return@runCatching
                    when (field("type")) {
                        "item" -> trySend(frame["value"])
                        "error", "end" -> {
                            trySend(null)
                            close()
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        awaitClose {
            runCatching { socket.send("{\"type\":\"cancel\",\"streamId\":\"$streamId\"}") }
            runCatching { socket.close(1000, "client closed") }
        }
    }

    /**
     * Host 全局转发事件（Gateway `$events` 流）的事件名。
     *
     * 官方客户端用 `ctx.remote.$on(...)` 监听 `llm/adapters-updated`、
     * `settings/document-updated`、凭据记录/引用更新来刷新模型目录；
     * 这里只把事件名转出来，由数据层决定刷新范围。
     */
    override fun forwardedHostEvents(hostId: String): Flow<String> =
        forwardedBus.onStart { ensureForwardedStream(hostId) }

    private fun ensureForwardedStream(hostId: String) {
        synchronized(forwardedStarted) {
            if (!forwardedStarted.add(hostId)) return
        }
        forwardedScope.launch {
            while (true) {
                runCatching { pumpForwardedStream(hostId) }
                kotlinx.coroutines.delay(2_000L)
            }
        }
    }

    /** 一条 `$events` 连接；连接结束（正常/失败）后返回，交给 [ensureForwardedStream] 重连。 */
    private suspend fun pumpForwardedStream(hostId: String) {
        if (baseUrl.isBlank()) return
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val streamId = java.util.UUID.randomUUID().toString()
        val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
        var clientId: String? = null
        val socket = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    val open = buildJsonObject {
                        put("type", "open")
                        put("streamId", streamId)
                        put("endpoint", "\$events")
                        put("payload", buildJsonObject { put("args", buildJsonObject { }) })
                    }
                    webSocket.send(open.toString())
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    runCatching {
                        val root = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching
                        if (root["type"]?.toString()?.trim('"') != "item") return@runCatching
                        if (root["streamId"]?.toString()?.trim('"') != streamId) return@runCatching
                        val value = root["value"] as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching
                        when (value["type"]?.toString()?.trim('"')) {
                            "ready" -> {
                                clientId = value["clientId"]?.toString()?.trim('"')
                                forwardedClientId = clientId
                            }
                            "emit" -> {
                                val name = value["event"]?.toString()?.trim('"')
                                    ?: return@runCatching
                                forwardedBus.tryEmit(name)
                                val args = value["args"]
                                    as? kotlinx.serialization.json.JsonArray
                                    ?: kotlinx.serialization.json.JsonArray(emptyList())
                                eventPayloadBus.tryEmit(ForwardedEvent(name, args))
                            }
                            "waterfall" -> {
                                val eventId = value["eventId"]?.toString()?.trim('"')
                                    ?: return@runCatching
                                val event = value["event"]?.toString()?.trim('"').orEmpty()
                                val request = value["request"]
                                    as? kotlinx.serialization.json.JsonObject
                                    ?: kotlinx.serialization.json.JsonObject(emptyMap())
                                val agentId = value["agentId"]?.toString()?.trim('"')
                                if (event == "approval/request" ||
                                    event == "user-questions/request"
                                ) {
                                    // 交给 App：超时未回答自动回 next，Host 不会永远卡住。
                                    waterfallBus.tryEmit(
                                        ForwardedWaterfall(
                                            hostId = hostId,
                                            event = event,
                                            eventId = eventId,
                                            agentId = agentId,
                                            request = request,
                                        ),
                                    )
                                    forwardedScope.launch {
                                        val timeout = if (event == "user-questions/request") {
                                            QUESTION_TIMEOUT_MS
                                        } else {
                                            ANSWER_TIMEOUT_MS
                                        }
                                        kotlinx.coroutines.delay(timeout)
                                        answerForwardedEvent(eventId, null)
                                    }
                                } else {
                                    // 其余 waterfall 直接回 next，交回 Host 默认处理。
                                    answerForwardedEvent(eventId, null)
                                }
                            }
                        }
                    }
                }

                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
                    closed.complete(Unit)
                }

                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }
            },
        )
        synchronized(forwardedSockets) { forwardedSockets.add(socket) }
        try {
            closed.await()
        } finally {
            synchronized(forwardedSockets) { forwardedSockets.remove(socket) }
            runCatching { socket.close(1000, "client closed") }
        }
    }

    /** 客户端不实现的 Host waterfall 一律回 `next`，让 Host 继续默认处理。 */
    private fun answerForwardedEvent(eventId: String, valueJson: String?) {
        val clientId = forwardedClientId ?: return
        if (!answeredWaterfalls.add(eventId)) return
        forwardedScope.launch {
            runCatching {
                val outcome = if (valueJson == null) {
                    buildJsonObject { put("kind", "next") }
                } else {
                    buildJsonObject {
                        put("kind", "result")
                        put("value", json.parseToJsonElement(valueJson))
                    }
                }
                val payload = buildJsonObject {
                    put("type", "client-request")
                    put("rpcId", java.util.UUID.randomUUID().toString())
                    put("method", "\$events/result")
                    put("payload", buildJsonObject {
                        put("args", buildJsonObject {
                            put("clientId", clientId)
                            put("eventId", eventId)
                            put("outcome", outcome)
                        })
                    })
                }
                val request = okhttp3.Request.Builder()
                    .url("$baseUrl/api/\$events/result")
                    .post(payload.toString().toRequestBody(mediaTypeJson))
                    .build()
                client.newCall(request).execute().close()
            }
        }
    }

    /** App 侧的 waterfall 结果回包入口。 */
    override fun forwardedWaterfalls(hostId: String): Flow<ForwardedWaterfall> = waterfallBus

    override fun forwardedEventPayloads(hostId: String): Flow<ForwardedEvent> = eventPayloadBus

    override fun answerWaterfall(hostId: String, eventId: String, valueJson: String?) =
        answerForwardedEvent(eventId, valueJson)

    override fun followSession(hostId: String, sessionId: String) {
        startSessionFollow(sessionId, hostId)
    }

    override fun subscribeEvents(hostId: String): Flow<RemoteEvent> = eventBus
}

/** 官方 meta.title/description 可能是 {en, zh} 本地化对象；优先中文。 */
private fun localizedJsonText(element: kotlinx.serialization.json.JsonElement?): String? = when (element) {
    is kotlinx.serialization.json.JsonPrimitive -> element.content
    is kotlinx.serialization.json.JsonObject -> {
        listOf("zh", "zh-CN", "en").firstNotNullOfOrNull { key ->
            (element[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
        }
    }
    else -> null
}















/** waterfall 等待 App 回答的上限；超时回 next 交回 Host。 */
private const val ANSWER_TIMEOUT_MS = 120_000L

/** 提问类 waterfall 等用户操作的时间上限。 */
private const val QUESTION_TIMEOUT_MS = 600_000L

