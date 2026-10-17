package com.deepseek.dsh.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class HostKind {
    LOCAL,
    REMOTE
}

@Serializable
enum class HostStatus {
    STOPPED,
    STARTING,
    READY,
    DEGRADED,
    ERROR
}

@Serializable
data class HostInfo(
    val id: String,
    val name: String,
    val kind: HostKind,
    val baseUrl: String,
    val token: String? = null,
    val status: HostStatus = HostStatus.STOPPED,
    val port: Int? = null,
    val pid: Long? = null,
    val lastSeenAt: Long = System.currentTimeMillis()
)

@Serializable
data class SessionInfo(
    val hostId: String,
    val sessionId: String,
    val title: String,
    val workspacePath: String? = null,
    val status: String = "idle",
    val updatedAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    val pinned: Boolean = false
)

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

@Serializable
data class ChatMessage(
    val hostId: String,
    val sessionId: String,
    val messageId: String,
    val role: MessageRole,
    val content: String,
    val seq: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class SurfaceKind {
    PAGE,
    SIDEBAR,
    DOCK,
    OVERLAY,
    MESSAGE_ACTION,
    THEME,
    UNKNOWN
}

@Serializable
data class SurfaceItem(
    val pluginId: String,
    val slot: String,
    val order: Int = 0,
    val title: String? = null,
    val kind: SurfaceKind = SurfaceKind.PAGE,
    val capabilities: List<String> = emptyList()
)

@Serializable
data class SurfaceCatalog(
    val clientId: String,
    val dshVersion: String,
    val surfaces: List<SurfaceItem> = emptyList()
)
