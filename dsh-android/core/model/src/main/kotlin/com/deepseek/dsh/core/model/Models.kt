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

/**
 * v4.0 设计文档 §11.1: Host 抽象模型
 */
@Serializable
sealed interface HostDescriptor {
    val id: String
    val name: String
    val kind: HostKind

    @Serializable
    data class Local(
        override val id: String = "local-host",
        override val name: String = "本机 Linux 运行时",
        val port: Int? = null,
        val token: String? = null
    ) : HostDescriptor {
        override val kind: HostKind = HostKind.LOCAL
    }

    @Serializable
    data class Remote(
        override val id: String,
        override val name: String,
        val baseUrl: String,
        val deviceToken: String,
        val tlsFingerprint: String? = null
    ) : HostDescriptor {
        override val kind: HostKind = HostKind.REMOTE
    }
}

/**
 * v4.0 设计文档 §11.3: 扫码配对 payload 结构规范
 * 二维码格式: {v, name, url, fp, pair, exp}
 */
@Serializable
data class PairPayload(
    val v: Int = 1,
    val name: String,
    val url: String,
    val fp: String,
    val pair: String,
    val exp: Long = 0L
)

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
