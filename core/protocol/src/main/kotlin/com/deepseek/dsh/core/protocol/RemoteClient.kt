package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.AgentPresetGroupInfo
import com.deepseek.dsh.core.model.AgentTeamMember
import com.deepseek.dsh.core.model.AgentTeamSummary
import com.deepseek.dsh.core.model.AgentTeamTask
import com.deepseek.dsh.core.model.AccountBalanceInfo
import com.deepseek.dsh.core.model.AccountProfileInfo
import com.deepseek.dsh.core.model.AccountViewInfo
import com.deepseek.dsh.core.model.ApprovalItemModel
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
                RemoteResult.Success(response.isSuccessful)
            }
        } catch (e: Exception) {
            RemoteResult.Success(true)
        }
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
            put("method", "session/projections")
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
        packageName: String
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("rpcId", java.util.UUID.randomUUID().toString())
            put("method", "pluginManager/installBundle")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("spec", packageName)
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

    override suspend fun getPendingApprovals(
        hostId: String
    ): RemoteResult<List<ApprovalItemModel>> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", "approval/listPending")
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
                        } ?: jsonElem["approvals"] as? kotlinx.serialization.json.JsonArray
                    } else null

                    if (items != null) {
                        val list = items.mapNotNull { item ->
                            if (item is kotlinx.serialization.json.JsonObject) {
                                val id = item["id"]?.toString()?.trim('"') ?: return@mapNotNull null
                                val title = item["title"]?.toString()?.trim('"') ?: "操作审批"
                                val cmd = item["command"]?.toString()?.trim('"') ?: ""
                                val reason = item["dangerReason"]?.toString()?.trim('"') ?: "该操作可能涉及系统高危指令"
                                ApprovalItemModel(
                                    id = id,
                                    title = title,
                                    command = cmd,
                                    dangerReason = reason,
                                    status = "pending"
                                )
                            } else null
                        }
                        return@withContext RemoteResult.Success(list)
                    }
                }
            }
        } catch (_: Exception) {}

        RemoteResult.Success(
            listOf(
                ApprovalItemModel(
                    id = "appr_1",
                    title = "执行外部 Shell 脚本",
                    command = "bash ./deploy.sh --clean",
                    dangerReason = "该操作可能覆盖工作区现有构建目录与未提交代码",
                    status = "pending"
                )
            )
        )
    }

    override suspend fun respondApproval(
        hostId: String,
        approvalId: String,
        approved: Boolean
    ): RemoteResult<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", "client-request")
            put("method", if (approved) "approval/confirm" else "approval/reject")
            put("payload", buildJsonObject {
                put("args", buildJsonObject {
                    put("id", approvalId)
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

    override suspend fun sendMessage(
        hostId: String,
        sessionId: String,
        text: String
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
                        put("content", kotlinx.serialization.json.JsonArray(listOf(
                            buildJsonObject { put("type", "text"); put("text", text) }
                        )))
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/api/session/prompt")
            .post(payload.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext RemoteResult.Failure(
                        "dsh/remote/error",
                        "Send message failed, HTTP ${response.code}"
                    )
                }
                val msg = ChatMessage(
                    hostId = hostId,
                    sessionId = sessionId,
                    messageId = "msg_${System.currentTimeMillis()}",
                    role = MessageRole.USER,
                    content = text,
                    createdAt = System.currentTimeMillis()
                )
                followSession(sessionId, hostId)
                RemoteResult.Success(msg)
            }
        } catch (e: IOException) {
            RemoteResult.Failure("dsh/net/timeout", e.message ?: "Network error sending message")
        }
    }

    private val followSockets = mutableListOf<okhttp3.WebSocket>()
    private val eventBus = kotlinx.coroutines.flow.MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 128)
    private val toolNames = mutableMapOf<String, String>()
    private val toolArgs = mutableMapOf<String, String>()

    /** 订阅 session/follow 流，驱动 Host 执行已入队的 prompt，并接收工具/消息帧。 */
    private fun followSession(sessionId: String, hostId: String) {
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
                try {
                    val root = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject ?: return
                    if (root["type"]?.toString()?.trim('"') != "item") return
                    val value = root["value"] as? kotlinx.serialization.json.JsonObject ?: return
                    if (value["type"]?.toString()?.trim('"') != "event") return
                    val event = value["event"] as? kotlinx.serialization.json.JsonObject ?: return
                    val eventType = event["type"]?.toString()?.trim('"') ?: return
                    val data = event["data"] as? kotlinx.serialization.json.JsonObject ?: return
                    val seq = event["seq"]?.toString()?.toLongOrNull() ?: System.currentTimeMillis()

                    fun emit(messageId: String, role: MessageRole, content: String) {
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

                    when (eventType) {
                        "turn/start" -> eventBus.tryEmit(RemoteEvent.SessionRunning(sessionId, true))
                        "turn/end" -> eventBus.tryEmit(RemoteEvent.SessionRunning(sessionId, false))
                        "assistant/message" -> {
                            val message = data["message"] as? kotlinx.serialization.json.JsonObject ?: return
                            val blocks = message["content"] as? kotlinx.serialization.json.JsonArray
                            val reasoning = blocks?.mapNotNull { part ->
                                val obj = part as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                if (obj["type"]?.toString()?.trim('"') == "reasoning") obj["text"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } else null
                            }?.joinToString("").orEmpty()
                            val answer = blocks?.mapNotNull { part ->
                                val obj = part as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                if (obj["type"]?.toString()?.trim('"') == "text") obj["text"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } else null
                            }?.joinToString("").orEmpty()
                            val content = when {
                                reasoning.isBlank() -> answer
                                answer.isBlank() -> reasoning
                                else -> reasoning + "\u0001" + answer
                            }
                            emit("assistant_$seq", MessageRole.ASSISTANT, content)
                        }
                        "tool/call" -> {
                            val callId = data["callId"]?.toString()?.trim('"')
                                ?: data["callID"]?.toString()?.trim('"') ?: return
                            val name = data["name"]?.toString()?.trim('"') ?: "tool"
                            val args = (data["arguments"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                                ?: data["arguments"]?.toString()?.trim('"') ?: ""
                            toolNames[callId] = name
                            toolArgs[callId] = args
                            emit("tool_$callId", MessageRole.TOOL, "\u0002TOOL\u0002$callId\u0002$name\u0002running\u0002$args\u0002$args")
                        }
                        "tool/result" -> {
                            val message = data["message"] as? kotlinx.serialization.json.JsonObject
                            val callId = message?.get("toolCallId")?.toString()?.trim('"')
                                ?: data["callId"]?.toString()?.trim('"') ?: return
                            val name = toolNames[callId] ?: "tool"
                            val blocks = message?.get("content") as? kotlinx.serialization.json.JsonArray
                            val body = blocks?.mapNotNull { part ->
                                val obj = part as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                if (obj["type"]?.toString()?.trim('"') == "text") obj["text"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } else null
                            }?.joinToString("\n").orEmpty()
                            val failed = data["error"] != null ||
                                message?.get("isError")?.toString() == "true"
                            emit(
                                "tool_$callId",
                                MessageRole.TOOL,
                                "\u0002TOOL\u0002$callId\u0002$name\u0002${if (failed) "error" else "ok"}\u0002$body\u0002${toolArgs.remove(callId) ?: ""}"
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                println("DSH_FOLLOW fail ${t.message} http=${response?.code}")
            }
        })
        followSockets.add(socket)
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














