package com.deepseek.dsh.runtime.host

import kotlinx.serialization.Serializable

@Serializable
enum class RuntimePhase {
    UNINSTALLED,
    INSTALLING,
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

@Serializable
data class RuntimeStatus(
    val phase: RuntimePhase,
    val port: Int? = null,
    val token: String? = null,
    val pid: Long? = null,
    val lastError: String? = null
)

interface HostRuntimeManager {
    suspend fun getStatus(): RuntimeStatus
    suspend fun installRootfs(onProgress: (Float, String) -> Unit): Result<Unit>
    suspend fun start(workspacePath: String): Result<RuntimeStatus>
    suspend fun stop(): Result<Unit>
}
