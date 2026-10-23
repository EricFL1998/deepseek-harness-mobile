package com.deepseek.dsh.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
    val description: String? = null,
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

/**
 * 智能体团队的一名成员。
 *
 * 来自会话投影 `agentTeam`（官方 `TeamProjection.members`）。`id` 就是成员的
 * 会话 id，点开成员会话时直接用它。
 */
@Serializable
data class AgentTeamMember(
    val id: String,
    val name: String,
    val role: String = "teammate", // lead / teammate
    val phase: String = "active", // provisioning / active / failed
    val error: String? = null,
)

/** 共享任务板里的一项（官方 `TeamTaskView`）。 */
@Serializable
data class AgentTeamTask(
    val id: String,
    val subject: String,
    val description: String = "",
    val status: String = "pending", // pending / in_progress / completed
    val ownerName: String? = null,
    val blockedBy: List<String> = emptyList(),
    val writeScopes: List<String> = emptyList(),
    val ready: Boolean = true,
    val writeScopeWarnings: List<String> = emptyList(),
)

/** 会话投影 `agentTeam` 里原生团队面板要用的部分。 */
@Serializable
data class AgentTeamSummary(
    val members: List<AgentTeamMember> = emptyList(),
    val tasks: List<AgentTeamTask> = emptyList(),
    val failure: String? = null,
)

/** 官方 `todos` 投影的一条待办。 */
@Serializable
data class TodoEntryInfo(
    val content: String,
    val status: String, // pending | in_progress | completed
)

/** 官方 `goal` 投影的当前目标。 */
@Serializable
data class GoalInfo(
    val objective: String,
    val phase: String,
    val blockedReason: String? = null,
    val roundsStarted: Int = 0,
    val maxGoalRounds: Int = 0,
)

/** 官方 `commands/list` 的一条斜杠命令描述。 */
@Serializable
data class HostCommandInfo(
    val name: String,
    val description: String,
    val inputHint: String? = null,
    val acceptsAttachments: Boolean = false,
)

/** 官方 workflow 运行里的一个成员（子会话）。 */
@Serializable
data class WorkflowMemberInfo(
    val seq: Int = 0,
    val label: String = "",
    val childId: String = "",
    val status: String = "running",
)

/** 官方 workflow 运行里的一个阶段（phase 可缺省）。 */
@Serializable
data class WorkflowPhaseInfo(
    val phase: String? = null,
    val members: List<WorkflowMemberInfo> = emptyList(),
)

/** 由官方 tool-workflow 事件聚合出的一次 workflow 运行。 */
@Serializable
data class WorkflowRunInfo(
    val runId: String,
    val name: String = "",
    val status: String = "running",
    val phases: List<WorkflowPhaseInfo> = emptyList(),
)

/** 官方 `schedule/catalog` 里的一个计划任务（提醒）。 */
@Serializable
data class ScheduleInfo(
    val id: String,
    val title: String = "",
    val prompt: String = "",
    val scheduledAt: String = "",
    val sessionId: String = "",
    val status: String = "",
    val kind: String = "",
    /** `after` 的一次性延迟秒数。 */
    val afterSeconds: Long = 0,
    /** `every` 的固定间隔秒数。 */
    val everySeconds: Long = 0,
    /** `daily` / `weekly` 的本地时间 HH:mm:ss.SSS。 */
    val time: String = "",
    /** `weekly` 的星期序号（ISO：1=周一）。 */
    val weekdays: List<Int> = emptyList(),
    /** `cron` 的五段表达式。 */
    val expression: String = "",
)

/** 官方 `account/getUnnotifiedBonuses` 里的一条奖励通知。 */
@Serializable
data class AccountBonusInfo(
    val orderId: String,
    val title: String? = null,
    val message: String = "",
    val expiresAt: String = "",
)

/** 官方 `messageFeedback` 的一条消息反馈。 */
@Serializable
data class MessageFeedbackInfo(
    val messageId: String,
    val rating: String,
    val note: String? = null,
    val version: String = "",
)

/** 官方 `terminal/list` 里的一个网页终端。 */
@Serializable
data class TerminalInfo(
    val id: String,
    val title: String,
    val state: String = "",
    val shellName: String = "",
)

/** 官方 `skills/list` 里人可调用的一个技能。 */
@Serializable
data class SkillInfo(
    val name: String,
    val description: String,
    val whenToUse: String? = null,
    val modelInvocable: Boolean = false,
)

/** 官方 `commands/execute` 的结果。 */
@Serializable
data class HostCommandResult(
    val kind: String,
    val text: String? = null,
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
    /** 这个包是否真的落在设备上（与「随安装提供」相对）。 */
    val installed: Boolean = false,
    /** Host 报告的可用性：`installation` / `installed` / `missing`。 */
    val availability: String = "",
    val removable: Boolean = true,
    val error: String? = null,
    val rows: List<PluginComponentInfo> = emptyList(),
    val iconData: String? = null,
    // 插件页策略由 bridge 下发（旧版 bridge 缺省时这些值保持 false/0）。
    val profileRoot: Boolean = false,
    val pinned: Boolean = false,
    val order: Int = 0,
    val hidden: Boolean = false,
    val market: Boolean = false,
    val locked: Boolean = false,
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






/**
 * 一个插件声明的、映射到原生界面的入口。
 *
 * 目前只有设置页一种：插件只要声明了 `Config` schema，Host 就会为它生成一个设置
 * 命名空间，原生设置里也就应当出现对应条目。内容页尚未实现，条目先只负责导航。
 */
@Serializable
data class PluginSettingsPage(
    val id: String,
    val title: String,
    val description: String? = null,
    val module: String? = null,
)

/**
 * 一份插件设置文档：Host 投影出来的表单、它的当前值与版本。
 *
 * 表单来自插件自己的 `Config` schema，客户端只负责画字段与回写，不重新定义字段。
 */
@Serializable
data class PluginSettingsDocument(
    val ns: String,
    val revision: Int = 0,
    val schema: JsonObject = JsonObject(emptyMap()),
    val value: JsonObject = JsonObject(emptyMap()),
    /** 已配置的密钥位置（Host 只下发 path + set，值本身被剥离）。 */
    val secretSlots: List<PluginSecretSlot> = emptyList(),
)

/** 一个密钥字段位置（路径 + 是否已配置）。 */
@Serializable
data class PluginSecretSlot(
    val path: List<String> = emptyList(),
    val set: Boolean = false,
)

/**
 * 插件声明的一个界面入口，已经带有原生落点。
 *
 * 落点由 bridge 从 slot 名翻译而来，客户端只负责把每个落点渲染到对应屏幕；
 * 尚未实现的位置也会出现在列表里，而不是被丢掉。
 */
@Serializable
data class PluginSlotEntry(
    val placement: String,
    val order: Int = 0,
    val slot: String,
    val id: String? = null,
    /** keyed 槽位的身份（例如 `tool.call.toolview` 按工具名注册的 `render_ui`）。 */
    val key: String? = null,
    val title: String? = null,
    val module: String? = null,
    /** bridge 给出的组件身份 `<package>#<id|key|slot>`，优先于客户端自行拼接。 */
    val componentId: String? = null,
) {
    /** 用于按工具名/会话位匹配的稳定身份：id 优先，keyed 槽位回落到 key。 */
    val identity: String? get() = id ?: key

    /** 组件身份：bridge 有给就用，否则按 `<module>#<identity|slot>` 拼。 */
    fun resolvedComponentId(): String? =
        componentId ?: module?.let { "$it#${identity ?: slot}" }
}

/**
 * 这个入口是否由**第三方**插件声明。
 *
 * 官方 `@deepseek-ai/…` 包向同一批浏览器槽位注册，但 App 对这些位置已有自己的
 * 原生实现（输入区、设置分区、模型卡）。只有第三方贡献需要按插件树现画，
 * 否则用户会看到两份同样的界面。
 */
val PluginSlotEntry.isThirdPartyPlugin: Boolean
    get() = module != null && !module.startsWith("@deepseek-ai/")

/** 原生落点 id 到中文名，用于尚未实现的位置。 */
val PLACEMENT_LABELS: Map<String, String> = mapOf(
    "settings.page" to "设置",
    "plugins.item" to "插件列表",
    "plugins.row.config" to "插件配置",
    "drawer" to "侧边栏",
    "conversation" to "对话",
    "tool.card" to "工具卡片",
    "toast" to "提示",
    "other" to "其他位置",
)

/** 官方 inbox 投影里一条待发消息（下一轮队列）。 */
data class QueuedMessageInfo(
    val id: String,
    val text: String,
)
