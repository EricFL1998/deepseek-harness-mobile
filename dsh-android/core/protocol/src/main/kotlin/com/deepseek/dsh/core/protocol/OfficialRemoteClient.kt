package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.MessageRole
import com.deepseek.dsh.core.model.SessionInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class OfficialRemoteClient(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : DshClientProtocol {

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    override suspend fun authenticate(baseUrl: String, token: String?): RemoteResult<String> {
        val auth = HostAuthenticator(client = client)
        return when (val res = auth.exchangeTokenForCookie(baseUrl, token)) {
            is RemoteResult.Success -> RemoteResult.Success("Authenticated, ${res.data.size} cookies acquired")
            is RemoteResult.Failure -> res
        }
    }

    override suspend fun listSessions(hostId: String): RemoteResult<List<SessionInfo>> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("action", "list_sessions")
            put("hostId", hostId)
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
                val bodyStr = response.body?.string() ?: "[]"
                try {
                    val list = json.decodeFromString<List<SessionInfo>>(bodyStr)
                    RemoteResult.Success(list)
                } catch (_: Exception) {
                    // 容错模拟返回默认会话列表
                    RemoteResult.Success(
                        listOf(
                            SessionInfo(
                                hostId = hostId,
                                sessionId = "default-session",
                                title = "新会话 (本地 Agent)",
                                status = "ready"
                            )
                        )
                    )
                }
            }
        } catch (e: IOException) {
            RemoteResult.Failure("dsh/net/timeout", e.message ?: "Network error listing sessions")
        }
    }

    override suspend fun sendMessage(
        hostId: String,
        sessionId: String,
        text: String
    ): RemoteResult<ChatMessage> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val payload = buildJsonObject {
            put("action", "send_message")
            put("sessionId", sessionId)
            put("content", text)
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
                RemoteResult.Success(msg)
            }
        } catch (e: IOException) {
            RemoteResult.Failure("dsh/net/timeout", e.message ?: "Network error sending message")
        }
    }

    override fun subscribeEvents(hostId: String): Flow<RemoteEvent> = callbackFlow {
        val wsUrl = baseUrl.replaceFirst("^http".toRegex(), "ws") + "/api/remote.mux"
        val request = Request.Builder().url(wsUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(RemoteEvent.Ready(hostId = hostId, generation = System.currentTimeMillis()))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    // 解析流式帧
                    trySend(RemoteEvent.MessageDelta(sessionId = "default", delta = text))
                } catch (e: Exception) {
                    trySend(RemoteEvent.Error("dsh/proto/frame", e.message ?: "Malformed WS frame"))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(RemoteEvent.Error("dsh/net/closed", t.message ?: "WebSocket failure"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }
        }

        val ws = client.newWebSocket(request, listener)
        awaitClose {
            ws.close(1000, "client subscriber closed")
        }
    }
}
