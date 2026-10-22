package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.AccountBalanceInfo
import com.deepseek.dsh.core.model.AccountProfileInfo
import com.deepseek.dsh.core.model.AccountViewInfo
import com.deepseek.dsh.core.model.AutomationTaskItem
import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.ComposerStats
import com.deepseek.dsh.core.model.ModelCatalogInfo
import com.deepseek.dsh.core.model.PluginBundleItem
import com.deepseek.dsh.core.model.PluginConfigItem
import com.deepseek.dsh.core.model.PluginInventorySnapshotInfo
import com.deepseek.dsh.core.model.PluginManagerSnapshot
import com.deepseek.dsh.core.model.PluginRowInfo
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.TeamTaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface RemoteResult<out T> {
    @Serializable
    data class Success<T>(val data: T) : RemoteResult<T>

    @Serializable
    data class Failure(val code: String, val message: String) : RemoteResult<Nothing>
}

/** 一条原始会话帧（开发者工具/会话数据诊断用）。 */
@Serializable
data class SessionRawFrame(
    /** 自增序号（诊断表的「序号」列）。 */
    val index: Int,
    /** 帧的顶层类型（`item`/`end`/…）。 */
    val type: String,
    /** 原始 JSON 文本。 */
    val text: String,
    /** 收到时间（epoch ms）。 */
    val timestamp: Long,
)

@Serializable
sealed interface RemoteEvent {
    @Serializable
    data class Ready(val hostId: String, val generation: Long) : RemoteEvent

    @Serializable
    data class MessageDelta(val sessionId: String, val delta: String) : RemoteEvent
    data class ReasoningDelta(val sessionId: String, val delta: String) : RemoteEvent
    /** 官方 workflow 运行状态变化（每个事件都带当前完整快照）。 */
    data class WorkflowRunUpdated(
        val sessionId: String,
        val run: com.deepseek.dsh.core.model.WorkflowRunInfo,
    ) : RemoteEvent

    /** 官方 `user/message` 里的图片附件（用于在气泡里渲染缩略图）。 */
    data class UserImages(
        val sessionId: String,
        val messageId: String,
        val images: List<AttachmentImageRef>,
    ) : RemoteEvent

    /** 官方 `workspace/changes`：一轮改动已记录，可按 seq 拉 summary。 */
    data class ChangesAnnounced(val sessionId: String, val seq: Long, val turn: Int) : RemoteEvent

    /** 官方 `deliverables/presented`：本轮产出的文件。 */
    data class DeliverablesPresented(
        val sessionId: String,
        val files: List<PresentedFileInfo>,
    ) : RemoteEvent

    @Serializable
    data class MessageDone(val sessionId: String, val message: ChatMessage) : RemoteEvent

    @Serializable
    data class SessionUpdated(val session: SessionInfo) : RemoteEvent
    data class SessionRunning(val sessionId: String, val running: Boolean) : RemoteEvent

    @Serializable
    data class Error(val code: String, val message: String) : RemoteEvent
}

/**
 * Host 侧一条等待客户端回答的 waterfall 请求。
 *
 * 官方客户端的 `approval/request` / `user-questions/request` 就是这种帧：
 * 客户端必须通过 `$events/result` 回 `result`（或 `next` 交回 Host 默认处理）。
 *
 * @property hostId 所属 Host。
 * @property event 事件名（如 `approval/request`）。
 * @property eventId 回包时用于关联的事件 id。
 * @property agentId 该请求所属 agent（会话）。
 * @property request 事件请求体（`toolName` / `reason` 等）。
 */
data class ForwardedWaterfall(
    val hostId: String,
    val event: String,
    val eventId: String,
    val agentId: String?,
    val request: kotlinx.serialization.json.JsonObject,
)

/** 官方 `changes.summary` 里的一个改动文件。 */
data class ChangedFileInfo(
    val path: String,
    val display: String,
    val added: Int,
    val deleted: Int,
    val binary: Boolean = false,
    val oversized: Boolean = false,
)

/** 官方 `changes.summary`：一轮改动的汇总。 */
data class ChangesSummaryInfo(
    val turn: Int,
    val seq: Long,
    val files: List<ChangedFileInfo>,
    val total: Int,
    val added: Int,
    val deleted: Int,
)

/** diff 里的一个 hunk。 */
data class DiffHunkInfo(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<String>,
)

/** 官方 `changes.diff`：单个文件的对比。 */
data class ChangesDiffInfo(
    val kind: String,
    val path: String,
    val display: String,
    val hunks: List<DiffHunkInfo> = emptyList(),
    val before: Boolean = true,
    val after: Boolean = true,
    val coarse: Boolean = false,
)

/** 会话里一条已被 Host 接纳的图片附件引用（不携带字节）。 */
data class AttachmentImageRef(
    val attachmentId: String,
    val mediaType: String,
    val width: Int = 0,
    val height: Int = 0,
    val name: String? = null,
)

/** prompt 里内联的图片（官方 PromptContentPart 的 image 分支，base64）。 */
data class PromptImagePart(
    val mediaType: String,
    val base64: String,
    val name: String? = null,
)

/** 一条已打开的终端连接（官方 `terminal/attach` 双向流）。 */
interface TerminalConnection {
    /** 终端帧（snapshot / output / state / ack 的原始 JSON）。 */
    val frames: Flow<String>
    /** 写入输入（自动带递增 sequence）。 */
    fun write(data: String)
    /** 调整尺寸。 */
    fun resize(cols: Int, rows: Int)
    /** 关闭这次附件（进程保留，可重连）。 */
    fun close()
}

/** 终端环境信息（官方 `terminal/environment`）。 */
data class TerminalEnvironmentInfo(
    val cwd: String,
    val maxCols: Int,
    val maxRows: Int,
)

/** 官方 `deliverables/presented` 里的一份产物。 */
data class PresentedFileInfo(
    val path: String,
    val description: String? = null,
)

/** Host 转发事件（事件名 + 参数）。 */
data class ForwardedEvent(
    val event: String,
    val args: kotlinx.serialization.json.JsonArray,
)

/** 官方 `job/list` 流里的一条后台任务投影。 */
data class HostJobInfo(
    val id: String,
    val kind: String,
    val label: String,
    val owner: String?,
    val status: String,
    val progress: String?,
    val detail: String?,
    val startedAt: Long,
    val finishedAt: Long?,
)

/** 官方 `session/search` 的一条命中。 */
data class SessionSearchHit(val sessionId: String, val snippet: String)

interface DshClientProtocol {
    suspend fun authenticate(baseUrl: String, token: String?): RemoteResult<String>
    suspend fun listSessions(hostId: String): RemoteResult<List<SessionInfo>>
    suspend fun createSession(hostId: String, title: String, workspacePath: String?, agentPreset: String? = null): RemoteResult<SessionInfo>
    suspend fun deleteSession(hostId: String, sessionId: String): RemoteResult<Boolean>
    suspend fun selectModel(hostId: String, sessionId: String, modelId: String, provider: String? = null, reasoningEffort: String? = null): RemoteResult<Boolean>
    suspend fun selectPermissionPreset(hostId: String, sessionId: String, preset: String): RemoteResult<Boolean>
    suspend fun cancelSession(hostId: String, sessionId: String): Boolean

    suspend fun answerUserQuestion(hostId: String, sessionId: String, callId: String, questionId: String, selected: String): Boolean = false
    suspend fun getModelCatalog(hostId: String): RemoteResult<ModelCatalogInfo>
    suspend fun getSessionStats(hostId: String, sessionId: String): RemoteResult<ComposerStats>
    suspend fun getAccountState(hostId: String): RemoteResult<AccountViewInfo>
    /**
     * 订阅官方 `account/watch` 流：Host 侧账号态（登录中/已存凭据/退出）的实时投影。
     *
     * 官方客户端用它在登录过程中即时更新 UI；这里用来替代 App 的轮询。
     *
     * @param hostId Host 标识。
     * @returns 每次账号态变化推送一次 `AccountViewInfo`。
     */
    fun watchAccount(hostId: String): Flow<AccountViewInfo>
    suspend fun startAccountSignIn(hostId: String, callbackOrigin: String): RemoteResult<AccountViewInfo>
    suspend fun signOutAccount(hostId: String): RemoteResult<Boolean>
    suspend fun getAccountProfile(hostId: String): RemoteResult<AccountProfileInfo?>
    suspend fun getAccountBalance(hostId: String): RemoteResult<AccountBalanceInfo>
    suspend fun getPluginInventory(hostId: String): RemoteResult<List<BuiltInPluginInfo>>
    suspend fun getPluginInventorySnapshot(hostId: String): RemoteResult<PluginInventorySnapshotInfo>
    suspend fun getPluginManagerSnapshot(hostId: String): RemoteResult<PluginManagerSnapshot>
    suspend fun getPluginRows(hostId: String): RemoteResult<List<PluginRowInfo>>
    suspend fun installPlugin(
        hostId: String,
        packageName: String,
        requestId: String? = null,
    ): RemoteResult<Boolean>
    suspend fun uninstallPlugin(hostId: String, packageName: String): RemoteResult<Boolean>
    suspend fun setBundleEnabled(hostId: String, packageName: String, enabled: Boolean): RemoteResult<Boolean>

    /**
     * 启停 profile 里的一条 Loader 行（非 bundle 安装的插件）。
     *
     * @param entryId `pluginManager/listPlugins` 返回的 entryId，如 `include:<id>`。
     * @param enabled 目标状态。
     */
    suspend fun setPluginEnabled(hostId: String, entryId: String, enabled: Boolean): RemoteResult<Boolean>
    suspend fun openSettingsDocument(hostId: String): RemoteResult<Boolean>
    suspend fun getRemoteSettings(hostId: String): RemoteResult<Map<String, String>>
    suspend fun rpcRaw(hostId: String, method: String, argsJson: String): RemoteResult<String>

    /**
     * 调一条插件注册在 Host 上的 /api/<method> 路由（裸 payload，不再包 args）。
     *
     * 插件把它的 RPC 注册成 Host 的 HTTP 路由（如 subscription-oauth.status），
     * 请求体是 {type,rpcId,method,payload}，payload 就是插件自己的参数对象。App 用
     * 这个通道取插件数据，再把数据作为 props 交给静态转译——不执行插件代码。
     */
    suspend fun pluginRpc(hostId: String, method: String, payloadJson: String): RemoteResult<String>
    suspend fun updateRemoteSettings(hostId: String, ns: String, patchJson: String): RemoteResult<Boolean>
    suspend fun getAutomationTasks(hostId: String): RemoteResult<List<AutomationTaskItem>>
    suspend fun getTeamTasks(hostId: String): RemoteResult<List<TeamTaskItem>>
    /** 读取当前会话的 `agentTeam` 投影（团队成员）；没有团队时返回 null。 */
    suspend fun getAgentTeam(
        hostId: String,
        sessionId: String,
    ): RemoteResult<com.deepseek.dsh.core.model.AgentTeamSummary?>
    suspend fun sendMessage(
        hostId: String,
        sessionId: String,
        text: String,
        images: List<PromptImagePart> = emptyList(),
    ): RemoteResult<ChatMessage>
    fun subscribeEvents(hostId: String): Flow<RemoteEvent>
    /**
     * 订阅 Host 全局转发事件（Gateway `$events` 流）的事件名。
     *
     * 官方客户端用 `ctx.remote.$on(...)` 监听 `llm/adapters-updated`、
     * `settings/document-updated`、凭据记录/引用更新等事件刷新模型目录；
     * App 这里只取事件名，由数据层决定刷新什么。
     *
     * @param hostId Host 标识。
     * @returns 事件名流；连接建立后持续投递，断开后在内部重连。
     */
    fun forwardedHostEvents(hostId: String): Flow<String>
    /** 同一条转发流的带参版本（插件安装进度等需要读参数）。 */
    fun forwardedEventPayloads(hostId: String): Flow<ForwardedEvent>
    /**
     * 订阅 Host 的 waterfall 请求（`approval/request` 等）。
     *
     * 不回包超过 [ANSWER_TIMEOUT_MS] 时协议层会自动回 `next`，避免 Host 工具卡死。
     */
    fun forwardedWaterfalls(hostId: String): Flow<ForwardedWaterfall>
    /**
     * 回答一条 [ForwardedWaterfall]。
     *
     * @param hostId Host 标识。
     * @param eventId 待回答事件的 id。
     * @param valueJson 结果 JSON（如 `"allowed-once"`）；null 表示回 `next` 交回 Host。
     */
    fun answerWaterfall(hostId: String, eventId: String, valueJson: String?)
    /**
     * 订阅官方 `workspace/follow`：工作区与其中会话的完整基线 + 增量帧。
     *
     * 帧是原始 JSON（`baseline` / `upsert` / `remove` / `order` / `archived` /
     * `pinned` / `replace`），调用方只需据此刷新列表即可。
     */
    fun workspaceFollow(hostId: String): Flow<String>
    /** 订阅官方 `session/control`：全部会话投影的基线 + 增量（模型选择等）。 */
    fun sessionControl(hostId: String): Flow<String>
    /** 打开一条 `session/follow`：首帧 snapshot 会重放历史（含产物声明）。 */
    fun followSession(hostId: String, sessionId: String)
    /** 官方 `/api/session.export`：下载会话日志 ZIP。 */
    suspend fun downloadSessionExport(
        hostId: String,
        sessionId: String,
    ): RemoteResult<ByteArray>

    /** 官方 `/api/changes.summary`：某轮改动的文件汇总。 */
    suspend fun fetchChangesSummary(
        hostId: String,
        sessionId: String,
        seq: Long,
    ): RemoteResult<ChangesSummaryInfo>
    /** 官方 `/api/changes.diff`：某个改动文件的逐行对比。 */
    suspend fun fetchChangesDiff(
        hostId: String,
        sessionId: String,
        seq: Long,
        index: Int,
    ): RemoteResult<ChangesDiffInfo>

    /** 官方 `terminal/environment`：当前目录与尺寸上限。 */
    suspend fun terminalEnvironment(
        hostId: String,
        sessionId: String,
    ): RemoteResult<TerminalEnvironmentInfo>
    /** 打开（或复用）一个终端并 attach；返回可写连接。 */
    suspend fun openTerminal(
        hostId: String,
        sessionId: String,
        cols: Int,
        rows: Int,
        terminalId: String = "main",
    ): RemoteResult<TerminalConnection>
    /** 官方 `terminal/close`：关闭并杀掉终端进程。 */
    suspend fun closeTerminal(hostId: String, sessionId: String, id: String): RemoteResult<Boolean>
    /** 订阅官方 `job/list`：当前会话可见的后台任务集合（整集帧）。 */
    fun jobList(hostId: String, sessionId: String): Flow<List<HostJobInfo>>
    /** 订阅官方 `job/follow`：一条任务的保留输出（opened/output/status 原始 JSON）。 */
    fun jobFollow(hostId: String, sessionId: String?, jobId: String, from: Long? = null): Flow<String>
    /** 官方 `job/kill`：人为终止一条后台任务。 */
    suspend fun killJob(hostId: String, sessionId: String, jobId: String): RemoteResult<String>
    /** 官方 `session/page`：按需加载更早的一页历史（滚动到顶时调用）。 */
    suspend fun loadOlderHistory(hostId: String, sessionId: String): RemoteResult<Boolean>
    /** 官方 `session/search`：按内容搜索会话（最多 20 条）。 */
    suspend fun searchSessions(hostId: String, query: String): RemoteResult<List<SessionSearchHit>>
    /**
     * 订阅会话的 Claude Code 模组横幅（流式 `claudeCodeMods/watchBand`）。
     *
     * @param hostId Host 标识。
     * @param sessionId 会话 id（Host 侧的 agent 标识）。
     * @returns 每次重绘的 `SurfaceSnapshot` 原始 JSON；流结束时完成。
     */
    fun watchClaudeCodeBand(
        hostId: String,
        sessionId: String,
    ): Flow<kotlinx.serialization.json.JsonElement?>
    /**
     * 最近收到的原始会话帧（只读诊断）。最多保留 500 条，按时间顺序。
     * @param sessionId 会话 id。
     */
    fun rawSessionFrames(sessionId: String): List<SessionRawFrame>
}










