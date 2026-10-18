package com.deepseek.dsh.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * v4.0 设计文档 §13.1 Room 客户端本地缓存实体规范
 */

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val baseUrl: String?,
    val tlsFingerprint: String?,
    val deviceId: String?,
    val status: String?,
    val lastSeenAt: Long?,
    val sortOrder: Int?
)

@Entity(tableName = "sessions", primaryKeys = ["hostId", "sessionId"])
data class SessionEntity(
    val hostId: String,
    val sessionId: String,
    val title: String?,
    val workspacePath: String?,
    val status: String?,
    val updatedAt: Long?,
    val lastSeq: Long?,
    val archived: Int = 0,
    val pinned: Int = 0,
    val model: String? = null,
    val permissionPreset: String? = null
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: String = "default",
    val defaultModel: String,
    val defaultReasoningLevel: String,
    val idleStopMinutes: Int,
    val keepAliveOnTask: Boolean,
    val autoBootRecovery: Boolean,
    val highRiskConfirm: Boolean,
    val showCodingView: Boolean,
    val transcriptView: String,
    val enableSoundNotice: Boolean,
    val notificationsEnabled: Boolean = true,
    val notifyOnTurnComplete: Boolean = true,
    val notifyOnError: Boolean = true,
    val remoteHostUrl: String = "",
    val remoteHostToken: String = "",
    val workspacePath: String
)

@Entity(tableName = "messages", primaryKeys = ["hostId", "sessionId", "messageId"])
data class MessageEntity(
    val hostId: String,
    val sessionId: String,
    val messageId: String,
    val role: String?,
    val seq: Long?,
    val contentJson: String?,
    val createdAt: Long?,
    val status: String?
)

@Entity(tableName = "drafts", primaryKeys = ["hostId", "sessionId"])
data class DraftEntity(
    val hostId: String,
    val sessionId: String,
    val text: String?,
    val attachmentsJson: String?,
    val updatedAt: Long?
)

@Entity(tableName = "approvals", primaryKeys = ["hostId", "approvalId"])
data class ApprovalEntity(
    val hostId: String,
    val approvalId: String,
    val sessionId: String?,
    val payloadJson: String?,
    val state: String?,
    val createdAt: Long?
)

@Entity(tableName = "surface_catalog", primaryKeys = ["clientId", "pluginId", "slot"])
data class SurfaceCatalogEntity(
    val clientId: String,
    val pluginId: String,
    val slot: String,
    val title: String?,
    val kind: String?,
    val orderHint: Int?,
    val capabilitiesJson: String?,
    val updatedAt: Long?
)

