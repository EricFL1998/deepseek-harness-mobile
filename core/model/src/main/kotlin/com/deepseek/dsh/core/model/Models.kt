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
    val pinned: Boolean = false,
    val model: String? = null,
    val permissionPreset: String? = null
)

@Serializable
data class ModelReasoningEffort(
    val id: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class ModelItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val reasoningEfforts: List<ModelReasoningEffort> = emptyList(),
    val defaultReasoningEffort: String? = null,
)

@Serializable
data class ModelProviderGroup(
    val id: String,
    val name: String,
    val models: List<ModelItem> = emptyList()
)

@Serializable
data class ModelCatalogInfo(
    val routableProviders: List<String> = emptyList(),
    val groups: List<ModelProviderGroup> = emptyList(),
    val defaultModel: String = ""
)

/**
 * v4.0 设计文档 §13.2:
 * 手机端用户偏好与设置数据模型
 */
@Serializable
data class AppSettings(
    val defaultModel: String = "",
    val defaultReasoningLevel: String = "",
    val idleStopMinutes: Int = 5,           // 空闲 5 分钟后停止容器 (0 = 常驻)
    val keepAliveOnTask: Boolean = true,    // 后台有任务时保持运行
    val autoBootRecovery: Boolean = false,  // 开机恢复
    val highRiskConfirm: Boolean = true,    // 高危操作强确认
    val showCodingView: Boolean = true,     // 显示代码工作视图（轨迹 / 代码差异 / 完整预设切换）
    val transcriptView: String = "standard", // 工作步骤展示：compact / standard / detailed（首次引导写入）
    val enableSoundNotice: Boolean = true,  // 任务完成语音/提示音
    val notificationsEnabled: Boolean = true,
    val notifyOnTurnComplete: Boolean = true,
    val notifyOnError: Boolean = true,
    val remoteHostUrl: String = "",
    val remoteHostToken: String = "",
    val workspacePath: String = "/storage/emulated/0/Documents/DSH-Workspace"
)

interface SettingsRepository {
    suspend fun getSettings(): AppSettings
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
}

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
data class ComposerStats(
    val outputSpeedTokensPerSec: Double = 0.0,
    val contextPercent: Int = 0,
    val cacheHitPercent: Int = 0
)

@Serializable
data class BuiltInPluginInfo(
    val title: String,
    val packageName: String,
    val description: String,
    val enabled: Boolean = true,
    val phase: String = "active",
    val entryId: String? = null,
    val presetId: String? = null,
    val condition: String? = null
)

@Serializable
data class AgentPresetGroupInfo(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val broken: String? = null,
    val rows: List<BuiltInPluginInfo> = emptyList()
)

@Serializable
data class PluginInventorySnapshotInfo(
    val managementAvailable: Boolean = false,
    val entries: List<BuiltInPluginInfo> = emptyList(),
    val agentPresets: List<AgentPresetGroupInfo> = emptyList()
)

@Serializable
data class AutomationTaskItem(
    val id: String,
    val title: String,
    val summary: String,
    val inactive: Boolean = false,
    val prompt: String = ""
)

@Serializable
data class TeamTaskItem(
    val id: String,
    val subject: String,
    val description: String,
    val status: String = "pending", // pending, in_progress, completed
    val ownerName: String? = null
)

@Serializable
data class ApprovalItemModel(
    val id: String,
    val title: String,
    val command: String,
    val dangerReason: String,
    val status: String = "pending" // pending, submitting, confirmed, rejected
)

@Serializable
data class PluginConfigItem(
    val id: String,
    val title: String,
    val description: String,
    val iconKey: String = "default"
)

@Serializable
data class PluginBundleItem(
    val packageName: String,
    val title: String,
    val description: String,
    val isExperimental: Boolean = false,
    val enabled: Boolean = false,
    val iconKey: String = "default",
    val version: String? = null,
    val official: Boolean = false,
    val removable: Boolean = true,
    val error: String? = null,
    val rows: List<PluginComponentInfo> = emptyList(),
    val iconData: String? = null,
)

@Serializable
data class PluginComponentInfo(
    val rowId: String,
    val moduleName: String,
    val title: String,
    val description: String,
    val enabled: Boolean = true,
    val phase: String = "active",
    val entryId: String? = null,
)

@Serializable
data class PluginRowInfo(
    val entryId: String? = null,
    val moduleName: String,
    val title: String,
    val description: String,
    val enabled: Boolean = true,
    val phase: String = "active",
    val iconData: String? = null,
)

@Serializable
data class PluginManagerSnapshot(
    val basicConfigs: List<PluginConfigItem> = emptyList(),
    val experimentalBundles: List<PluginBundleItem> = emptyList(),
    val installedBundles: List<PluginBundleItem> = emptyList()
)

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





