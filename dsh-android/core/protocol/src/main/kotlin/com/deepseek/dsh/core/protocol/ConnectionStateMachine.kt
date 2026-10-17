package com.deepseek.dsh.core.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * v4.0 设计文档 §5.3: 连接状态机
 *
 * Idle -> Resolving -> Authenticating -> Handshaking -> Ready
 * Ready -> Degraded -> Retrying -> Authenticating
 * Authenticating -> FatalAuth (401/403)
 * Retrying -> FatalNetwork (超限)
 * Ready -> Closed
 */
@Serializable
sealed interface ConnectionState {
    @Serializable
    data object Idle : ConnectionState

    @Serializable
    data class Resolving(val hostId: String, val generation: Long) : ConnectionState

    @Serializable
    data class Authenticating(val hostId: String, val generation: Long) : ConnectionState

    @Serializable
    data class Handshaking(val hostId: String, val generation: Long) : ConnectionState

    @Serializable
    data class Ready(val hostId: String, val generation: Long) : ConnectionState

    @Serializable
    data class Degraded(val hostId: String, val generation: Long, val reason: String) : ConnectionState

    @Serializable
    data class Retrying(val hostId: String, val generation: Long, val attempt: Int, val delayMs: Long) : ConnectionState

    @Serializable
    data class FatalAuth(val hostId: String, val generation: Long, val message: String) : ConnectionState

    @Serializable
    data class FatalNetwork(val hostId: String, val generation: Long, val message: String) : ConnectionState

    @Serializable
    data class Closed(val hostId: String, val generation: Long) : ConnectionState
}

class ConnectionStateMachine {
    private var currentGeneration: Long = 0L
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Synchronized
    fun nextGeneration(): Long {
        currentGeneration++
        return currentGeneration
    }

    @Synchronized
    fun getGeneration(): Long = currentGeneration

    @Synchronized
    fun transitionTo(newState: ConnectionState): Boolean {
        // 丢弃旧 generation 的状态跃迁
        val targetGen = when (newState) {
            is ConnectionState.Idle -> currentGeneration
            is ConnectionState.Resolving -> newState.generation
            is ConnectionState.Authenticating -> newState.generation
            is ConnectionState.Handshaking -> newState.generation
            is ConnectionState.Ready -> newState.generation
            is ConnectionState.Degraded -> newState.generation
            is ConnectionState.Retrying -> newState.generation
            is ConnectionState.FatalAuth -> newState.generation
            is ConnectionState.FatalNetwork -> newState.generation
            is ConnectionState.Closed -> newState.generation
        }

        if (targetGen < currentGeneration) {
            return false
        }

        _state.value = newState
        return true
    }

    fun isReady(): Boolean = _state.value is ConnectionState.Ready
}
