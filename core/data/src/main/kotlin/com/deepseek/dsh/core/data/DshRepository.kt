package com.deepseek.dsh.core.data

import com.deepseek.dsh.core.bridge.BridgePluginCatalog
import com.deepseek.dsh.core.bridge.BridgePluginCatalogData
import com.deepseek.dsh.core.bridge.BridgePluginCatalogSource
import com.deepseek.dsh.core.bridge.BridgeRegistry
import com.deepseek.dsh.core.model.AccountBalanceInfo
import com.deepseek.dsh.core.model.AccountProfileInfo
import com.deepseek.dsh.core.model.AccountViewInfo
import com.deepseek.dsh.core.model.AgentPresetGroupInfo
import com.deepseek.dsh.core.model.ApprovalItemModel
import com.deepseek.dsh.core.model.AutomationTaskItem
import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.ComposerStats
import com.deepseek.dsh.core.model.MessageRole
import com.deepseek.dsh.core.model.ModelCatalogInfo
import com.deepseek.dsh.core.model.PluginBundleItem
import com.deepseek.dsh.core.model.PluginConfigItem
import com.deepseek.dsh.core.model.PluginInventorySnapshotInfo
import com.deepseek.dsh.core.model.PluginManagerSnapshot
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.TeamTaskItem
import com.deepseek.dsh.core.protocol.DshClientProtocol
import com.deepseek.dsh.core.protocol.RemoteEvent
import com.deepseek.dsh.core.protocol.RemoteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DshRepository(
    private val dao: DshDao,
    private val client: DshClientProtocol,
    private val scope: CoroutineScope
) {
    private val _modelCatalogFlow = MutableStateFlow(
        ModelCatalogInfo(
            routableProviders = emptyList(),
            groups = emptyList(),
            defaultModel = "",
        )
    )
    val modelCatalogFlow: StateFlow<ModelCatalogInfo> = _modelCatalogFlow.asStateFlow()

    private val _sessionStatsFlow = MutableStateFlow<Map<String, ComposerStats>>(emptyMap())
    val sessionStatsFlow: StateFlow<Map<String, ComposerStats>> = _sessionStatsFlow.asStateFlow()

    private val _builtInPluginsFlow = MutableStateFlow<List<BuiltInPluginInfo>>(emptyList())
    val builtInPluginsFlow: StateFlow<List<BuiltInPluginInfo>> = _builtInPluginsFlow.asStateFlow()

    private val _pluginSnapshotFlow = MutableStateFlow<PluginInventorySnapshotInfo?>(null)
    val pluginSnapshotFlow: StateFlow<PluginInventorySnapshotInfo?> = _pluginSnapshotFlow.asStateFlow()

    private val _pluginManagerSnapshotFlow = MutableStateFlow<PluginManagerSnapshot?>(null)
    private val _pluginRowsFlow = MutableStateFlow<List<com.deepseek.dsh.core.model.PluginRowInfo>>(emptyList())
    val pluginRowsFlow: StateFlow<List<com.deepseek.dsh.core.model.PluginRowInfo>> = _pluginRowsFlow.asStateFlow()
    val pluginManagerSnapshotFlow: StateFlow<PluginManagerSnapshot?> = _pluginManagerSnapshotFlow.asStateFlow()

    /**
     * 插件页目录：由 bridge 的 `client-slots` / `package-inventory` 归一而来。
     *
     * 与 [pluginManagerSnapshotFlow] 的区别是来源：快照走 Host 的 Remote API，
     * 本目录走 `bridge/v1`，因此能拿到官方客户端的 slot 注册与图形。两者并存：
     * bridge 缺失时 UI 回落到快照，绝不显示伪造分类。
     */
    private val _bridgePluginCatalogFlow = MutableStateFlow(
        BridgePluginCatalogData(source = BridgePluginCatalogSource.NONE)
    )
    val bridgePluginCatalogFlow: StateFlow<BridgePluginCatalogData> =
        _bridgePluginCatalogFlow.asStateFlow()

    /** bridge 协商与读取过程中产生的非致命说明，供诊断展示。 */
    private val _bridgeWarningsFlow = MutableStateFlow<List<String>>(emptyList())
    val bridgeWarningsFlow: StateFlow<List<String>> = _bridgeWarningsFlow.asStateFlow()

    /** 当前 host 的 bridge 门面；地址随 Host 绑定变化时重建。 */
    private var bridgeRegistry: BridgeRegistry? = null
    private var bridgeBaseUrl: String? = null

    /**
     * 把 bridge 门面绑到当前 Host。
     *
     * 门面由调用方构造，因为它需要 Host 的会话 CookieJar —— 那是网络层的知识，
     * 仓库不重复拥有。地址相同时保留已有门面，避免丢掉 manifest 缓存。
     */
    fun bindBridge(baseUrl: String, registry: BridgeRegistry) {
        if (baseUrl == bridgeBaseUrl && bridgeRegistry != null) return
        bridgeBaseUrl = baseUrl
        bridgeRegistry = registry
    }

    /** 当前 bridge 门面；未绑定时为 null。 */
    fun bridge(): BridgeRegistry? = bridgeRegistry

    /**
     * 读取一次插件目录。
     *
     * 失败不是异常：目录会退化成 `source = NONE`，说明进 [bridgeWarningsFlow]，
     * 由 UI 决定展示空态还是回落 Host 原生清单。
     */
    suspend fun refreshBridgePluginCatalog(iconSize: Int = BridgePluginCatalog.DEFAULT_ICON_SIZE) {
        val registry = bridgeRegistry
        if (registry == null) {
            _bridgePluginCatalogFlow.value = BridgePluginCatalogData(source = BridgePluginCatalogSource.NONE)
            _bridgeWarningsFlow.value = listOf("bridge is not bound to a Host")
            return
        }
        val result = BridgePluginCatalog.load(registry, iconSize)
        _bridgePluginCatalogFlow.value = result.catalog
        _bridgeWarningsFlow.value = result.warnings
    }

    private val _automationTasksFlow = MutableStateFlow<List<AutomationTaskItem>>(emptyList())
    val automationTasksFlow: StateFlow<List<AutomationTaskItem>> = _automationTasksFlow.asStateFlow()

    private val _teamTasksFlow = MutableStateFlow<List<TeamTaskItem>>(emptyList())
    val teamTasksFlow: StateFlow<List<TeamTaskItem>> = _teamTasksFlow.asStateFlow()

    private val _pendingApprovalsFlow = MutableStateFlow<List<ApprovalItemModel>>(emptyList())
    val pendingApprovalsFlow: StateFlow<List<ApprovalItemModel>> = _pendingApprovalsFlow.asStateFlow()

    suspend fun refreshAutomationTasks(hostId: String) {
        when (val res = client.getAutomationTasks(hostId)) {
            is RemoteResult.Success -> _automationTasksFlow.value = res.data
            is RemoteResult.Failure -> {}
        }
    }

    suspend fun refreshTeamTasks(hostId: String) {
        when (val res = client.getTeamTasks(hostId)) {
            is RemoteResult.Success -> _teamTasksFlow.value = res.data
            is RemoteResult.Failure -> {}
        }
    }

    suspend fun refreshApprovals(hostId: String) {
        when (val res = client.getPendingApprovals(hostId)) {
            is RemoteResult.Success -> _pendingApprovalsFlow.value = res.data
            is RemoteResult.Failure -> {}
        }
    }

    suspend fun respondApproval(hostId: String, approvalId: String, approved: Boolean) {
        val current = _pendingApprovalsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == approvalId }
        if (index != -1) {
            current[index] = current[index].copy(status = if (approved) "confirmed" else "rejected")
            _pendingApprovalsFlow.value = current
        }
        client.respondApproval(hostId, approvalId, approved)
    }

    fun getStatsForSession(sessionId: String): Flow<ComposerStats> {
        return sessionStatsFlow.map { map ->
            map[sessionId] ?: ComposerStats()
        }
    }

    suspend fun refreshSessionStats(hostId: String, sessionId: String) {
        when (val res = client.getSessionStats(hostId, sessionId)) {
            is RemoteResult.Success -> {
                val current = _sessionStatsFlow.value.toMutableMap()
                current[sessionId] = res.data
                _sessionStatsFlow.value = current
            }
            is RemoteResult.Failure -> {}
        }
    }

    suspend fun refreshPluginInventory(hostId: String) {
        when (val res = client.getPluginInventorySnapshot(hostId)) {
            is RemoteResult.Success -> {
                _pluginSnapshotFlow.value = res.data
                val all = (res.data.agentPresets.flatMap { it.rows } + res.data.entries).distinctBy { it.packageName }
                _builtInPluginsFlow.value = all
            }
            is RemoteResult.Failure -> {}
        }
        when (val pmRes = client.getPluginManagerSnapshot(hostId)) {
            is RemoteResult.Success -> {
                _pluginManagerSnapshotFlow.value = pmRes.data
            }
            is RemoteResult.Failure -> {}
        }
        when (val rowsRes = client.getPluginRows(hostId)) {
            is RemoteResult.Success -> _pluginRowsFlow.value = rowsRes.data
            is RemoteResult.Failure -> {}
        }
    }

    suspend fun getAccountState(hostId: String): Result<AccountViewInfo> =
        when (val res = client.getAccountState(hostId)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }

    suspend fun startAccountSignIn(hostId: String, callbackOrigin: String): Result<AccountViewInfo> =
        when (val res = client.startAccountSignIn(hostId, callbackOrigin)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }

    suspend fun signOutAccount(hostId: String): Result<Boolean> =
        when (val res = client.signOutAccount(hostId)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }

    suspend fun getAccountProfile(hostId: String): Result<AccountProfileInfo?> =
        when (val res = client.getAccountProfile(hostId)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }

    suspend fun getAccountBalance(hostId: String): Result<AccountBalanceInfo> =
        when (val res = client.getAccountBalance(hostId)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }

    suspend fun setBundleEnabled(hostId: String, packageName: String, enabled: Boolean): Result<Boolean> {
        val current = _pluginManagerSnapshotFlow.value
        if (current != null) {
            val updatedExp = current.experimentalBundles.map {
                if (it.packageName == packageName) it.copy(enabled = enabled) else it
            }
            val updatedInst = current.installedBundles.map {
                if (it.packageName == packageName) it.copy(enabled = enabled) else it
            }
            _pluginManagerSnapshotFlow.value = current.copy(
                experimentalBundles = updatedExp,
                installedBundles = updatedInst
            )
        }
        return when (val res = client.setBundleEnabled(hostId, packageName, enabled)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }
    }

    suspend fun workspaceCreate(hostId: String, path: String): Result<String> =
        rpcRaw(hostId, "workspace/create", "{\"request\":{\"path\":\"${path.replace("\"", "\\\"")}\"}}")

    suspend fun workspaceRename(hostId: String, workspaceId: String, title: String): Result<String> =
        rpcRaw(
            hostId,
            "workspace/rename",
            "{\"request\":{\"workspaceId\":\"${workspaceId.replace("\"", "\\\"")}\",\"title\":\"${title.replace("\"", "\\\"")}\"}}"
        )

    suspend fun workspaceDelete(hostId: String, workspaceId: String): Result<String> =
        rpcRaw(
            hostId,
            "workspace/delete",
            "{\"request\":{\"workspaceId\":\"${workspaceId.replace("\"", "\\\"")}\"}}"
        )
    suspend fun rpcRaw(hostId: String, method: String, argsJson: String): Result<String> =
        when (val res = client.rpcRaw(hostId, method, argsJson)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException("${res.code}: ${res.message}"))
        }

    suspend fun installPlugin(hostId: String, packageName: String): Result<Boolean> {
        return when (val res = client.installPlugin(hostId, packageName)) {
            is RemoteResult.Success -> {
                refreshPluginInventory(hostId)
                Result.success(res.data)
            }
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }
    }

    suspend fun uninstallPlugin(hostId: String, packageName: String): Result<Boolean> {
        return when (val res = client.uninstallPlugin(hostId, packageName)) {
            is RemoteResult.Success -> {
                refreshPluginInventory(hostId)
                Result.success(res.data)
            }
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }
    }

    fun getSessions(hostId: String): Flow<List<SessionInfo>> {
        return dao.getSessionsForHost(hostId).map { entities ->
            entities.map { entity ->
                SessionInfo(
                    hostId = entity.hostId,
                    sessionId = entity.sessionId,
                    title = entity.title ?: "未命名会话",
                    workspacePath = entity.workspacePath,
                    status = entity.status ?: "idle",
                    updatedAt = entity.updatedAt ?: System.currentTimeMillis(),
                    archived = entity.archived != 0,
                    pinned = entity.pinned != 0,
                    model = entity.model,
                    permissionPreset = entity.permissionPreset
                )
            }
        }
    }

    val liveSessionIdsFlow = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val runningSessionIdsFlow = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val unreadSessionIdsFlow = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    private var viewedSessionId: String? = null
    fun markSessionViewed(sessionId: String?) { viewedSessionId = sessionId; if (sessionId != null) unreadSessionIdsFlow.value = unreadSessionIdsFlow.value - sessionId }

    suspend fun refreshSessions(hostId: String) {
        when (val res = client.listSessions(hostId)) {
            is RemoteResult.Success -> {
                liveSessionIdsFlow.value = res.data.map { it.sessionId }.toSet()
                val entities = res.data.map { session ->
                    SessionEntity(
                        hostId = session.hostId,
                        sessionId = session.sessionId,
                        title = session.title,
                        workspacePath = session.workspacePath,
                        status = session.status,
                        updatedAt = session.updatedAt,
                        lastSeq = 0L,
                        archived = if (session.archived) 1 else 0,
                        pinned = if (session.pinned) 1 else 0,
                        model = session.model,
                        permissionPreset = session.permissionPreset
                    )
                }
                dao.insertSessions(entities)
            }
            is RemoteResult.Failure -> {}
        }
    }

    suspend fun createSession(hostId: String, title: String, workspacePath: String?, agentPreset: String? = null): Result<SessionInfo> {
        val res = client.createSession(hostId, title, workspacePath, agentPreset)
        return when (res) {
            is RemoteResult.Success -> {
                val session = res.data
                liveSessionIdsFlow.value = liveSessionIdsFlow.value + session.sessionId
                dao.insertSessions(
                    listOf(
                        SessionEntity(
                            hostId = session.hostId,
                            sessionId = session.sessionId,
                            title = session.title,
                            workspacePath = session.workspacePath,
                            status = session.status,
                            updatedAt = session.updatedAt,
                            lastSeq = 0L,
                            archived = 0,
                            pinned = 0,
                            model = session.model,
                            permissionPreset = session.permissionPreset
                        )
                    )
                )
                Result.success(session)
            }
            is RemoteResult.Failure -> {
                Result.failure(IllegalStateException(res.message))
            }
        }
    }

    suspend fun deleteSession(hostId: String, sessionId: String) {
        dao.deleteSession(hostId, sessionId)
        client.deleteSession(hostId, sessionId)
    }

    suspend fun selectModel(
        hostId: String,
        sessionId: String,
        model: String,
        provider: String? = null,
        reasoningEffort: String? = null,
    ) {
        dao.updateSessionModel(hostId, sessionId, model)
        client.selectModel(hostId, sessionId, model, provider, reasoningEffort)
    }

    suspend fun cancelSession(hostId: String, sessionId: String): Boolean {
        runningSessionIdsFlow.value = runningSessionIdsFlow.value - sessionId
        return client.cancelSession(hostId, sessionId)
    }

    suspend fun answerUserQuestion(hostId: String, sessionId: String, callId: String, questionId: String, selected: String) {
        client.answerUserQuestion(hostId, sessionId, callId, questionId, selected)
    }

    suspend fun selectPermissionPreset(hostId: String, sessionId: String, preset: String) {
        dao.updateSessionPermission(hostId, sessionId, preset)
        client.selectPermissionPreset(hostId, sessionId, preset)
    }

    suspend fun refreshModelCatalog(hostId: String) {
        when (val res = client.getModelCatalog(hostId)) {
            is RemoteResult.Success -> {
                _modelCatalogFlow.value = res.data
            }
            is RemoteResult.Failure -> {}
        }
    }

    suspend fun openSettingsDocument(hostId: String): Boolean {
        return when (val res = client.openSettingsDocument(hostId)) {
            is RemoteResult.Success -> res.data
            is RemoteResult.Failure -> false
        }
    }

    suspend fun syncRemoteSettings(hostId: String) {
        when (val res = client.getRemoteSettings(hostId)) {
            is RemoteResult.Success -> {
                // Read from remote namespaces if present
            }
            is RemoteResult.Failure -> {}
        }
    }

    suspend fun updateRemoteSetting(hostId: String, ns: String, patchJson: String): Boolean {
        return when (val res = client.updateRemoteSettings(hostId, ns, patchJson)) {
            is RemoteResult.Success -> res.data
            is RemoteResult.Failure -> false
        }
    }

    fun getMessages(hostId: String, sessionId: String): Flow<List<ChatMessage>> {
        return dao.getMessages(hostId, sessionId).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    hostId = entity.hostId,
                    sessionId = entity.sessionId,
                    messageId = entity.messageId,
                    role = runCatching { MessageRole.valueOf(entity.role ?: "USER") }.getOrDefault(MessageRole.USER),
                    content = entity.contentJson ?: "",
                    seq = entity.seq ?: 0L,
                    createdAt = entity.createdAt ?: System.currentTimeMillis()
                )
            }
        }
    }

    suspend fun sendMessage(hostId: String, sessionId: String, content: String): ChatMessage {
        val userMsg = ChatMessage(
            hostId = hostId,
            sessionId = sessionId,
            messageId = "msg_user_${System.currentTimeMillis()}",
            role = MessageRole.USER,
            content = content,
            createdAt = System.currentTimeMillis()
        )
        dao.touchSession(hostId, sessionId, System.currentTimeMillis())
        dao.insertMessages(
            listOf(
                MessageEntity(
                    hostId = userMsg.hostId,
                    sessionId = userMsg.sessionId,
                    messageId = userMsg.messageId,
                    role = userMsg.role.name,
                    seq = userMsg.seq,
                    contentJson = userMsg.content,
                    createdAt = userMsg.createdAt,
                    status = "sent"
                )
            )
        )

        scope.launch(Dispatchers.IO) {
            // 用户消息已乐观插入；这里只提交 prompt，避免同一条消息被插入两次。
            client.sendMessage(hostId, sessionId, content)
        }

        return userMsg
    }

    fun subscribeRemoteEvents(hostId: String) {
        scope.launch(Dispatchers.IO) {
            client.subscribeEvents(hostId).collect { event ->
                when (event) {
                    is RemoteEvent.SessionRunning -> {
                        runningSessionIdsFlow.value = if (event.running) {
                            runningSessionIdsFlow.value + event.sessionId
                        } else {
                            runningSessionIdsFlow.value - event.sessionId
                        }
                        if (!event.running && event.sessionId != viewedSessionId) {
                            unreadSessionIdsFlow.value = unreadSessionIdsFlow.value + event.sessionId
                        }
                    }
                    is RemoteEvent.MessageDelta -> {
                        runningSessionIdsFlow.value = runningSessionIdsFlow.value + event.sessionId
                    }
                    is RemoteEvent.MessageDone -> {
                        runningSessionIdsFlow.value = runningSessionIdsFlow.value + event.message.sessionId
                        dao.touchSession(event.message.hostId, event.message.sessionId, System.currentTimeMillis())
                        dao.insertMessages(
                            listOf(
                                MessageEntity(
                                    hostId = event.message.hostId,
                                    sessionId = event.message.sessionId,
                                    messageId = event.message.messageId,
                                    role = event.message.role.name,
                                    seq = event.message.seq,
                                    contentJson = event.message.content,
                                    createdAt = event.message.createdAt,
                                    status = "done"
                                )
                            )
                        )
                    }
                    is RemoteEvent.SessionUpdated -> {
                        dao.insertSessions(
                            listOf(
                                SessionEntity(
                                    hostId = event.session.hostId,
                                    sessionId = event.session.sessionId,
                                    title = event.session.title,
                                    workspacePath = event.session.workspacePath,
                                    status = event.session.status,
                                    updatedAt = event.session.updatedAt,
                                    lastSeq = 0L,
                                    archived = if (event.session.archived) 1 else 0,
                                    pinned = if (event.session.pinned) 1 else 0,
                                    model = event.session.model,
                                    permissionPreset = event.session.permissionPreset
                                )
                            )
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}



















