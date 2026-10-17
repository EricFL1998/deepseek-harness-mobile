package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.SessionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface RemoteResult<out T> {
    @Serializable
    data class Success<T>(val data: T) : RemoteResult<T>

    @Serializable
    data class Failure(val code: String, val message: String) : RemoteResult<Nothing>
}

@Serializable
sealed interface RemoteEvent {
    @Serializable
    data class Ready(val hostId: String, val generation: Long) : RemoteEvent

    @Serializable
    data class MessageDelta(val sessionId: String, val delta: String) : RemoteEvent

    @Serializable
    data class MessageDone(val sessionId: String, val message: ChatMessage) : RemoteEvent

    @Serializable
    data class SessionUpdated(val session: SessionInfo) : RemoteEvent

    @Serializable
    data class Error(val code: String, val message: String) : RemoteEvent
}

interface DshClientProtocol {
    suspend fun authenticate(baseUrl: String, token: String?): RemoteResult<String>
    suspend fun listSessions(hostId: String): RemoteResult<List<SessionInfo>>
    suspend fun sendMessage(hostId: String, sessionId: String, text: String): RemoteResult<ChatMessage>
    fun subscribeEvents(hostId: String): Flow<RemoteEvent>
}
