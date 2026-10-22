package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.AccountBalanceInfo
import com.deepseek.dsh.core.model.AccountProfileInfo
import com.deepseek.dsh.core.model.AccountViewInfo
import com.deepseek.dsh.core.model.ApprovalItemModel
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

    @Serializable
    data class MessageDone(val sessionId: String, val message: ChatMessage) : RemoteEvent

    @Serializable
    data class SessionUpdated(val session: SessionInfo) : RemoteEvent
    data class SessionRunning(val sessionId: String, val running: Boolean) : RemoteEvent

    @Serializable
    data class Error(val code: String, val message: String) : RemoteEvent
}

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
    suspend fun startAccountSignIn(hostId: String, callbackOrigin: String): RemoteResult<AccountViewInfo>
    suspend fun signOutAccount(hostId: String): RemoteResult<Boolean>
    suspend fun getAccountProfile(hostId: String): RemoteResult<AccountProfileInfo?>
    suspend fun getAccountBalance(hostId: String): RemoteResult<AccountBalanceInfo>
    suspend fun getPluginInventory(hostId: String): RemoteResult<List<BuiltInPluginInfo>>
    suspend fun getPluginInventorySnapshot(hostId: String): RemoteResult<PluginInventorySnapshotInfo>
    suspend fun getPluginManagerSnapshot(hostId: String): RemoteResult<PluginManagerSnapshot>
    suspend fun getPluginRows(hostId: String): RemoteResult<List<PluginRowInfo>>
    suspend fun installPlugin(hostId: String, packageName: String): RemoteResult<Boolean>
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
    suspend fun getPendingApprovals(hostId: String): RemoteResult<List<ApprovalItemModel>>
    suspend fun respondApproval(hostId: String, approvalId: String, approved: Boolean): RemoteResult<Boolean>
    suspend fun sendMessage(hostId: String, sessionId: String, text: String): RemoteResult<ChatMessage>
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










