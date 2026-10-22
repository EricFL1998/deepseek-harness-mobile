package com.deepseek.dsh.core.data

import com.deepseek.dsh.core.bridge.BridgePluginCatalog
import com.deepseek.dsh.core.bridge.BridgeResult
import com.deepseek.dsh.core.bridge.BridgeSlotSurface
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.deepseek.dsh.core.model.PluginSettingsDocument
import com.deepseek.dsh.core.model.PluginSettingsPage
import com.deepseek.dsh.core.model.PluginSlotEntry
import com.deepseek.dsh.core.model.ModelCatalogInfo
import com.deepseek.dsh.core.model.PluginBundleItem
import com.deepseek.dsh.core.model.PluginConfigItem
import com.deepseek.dsh.core.model.PluginInventorySnapshotInfo
import com.deepseek.dsh.core.model.PluginManagerSnapshot
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.AgentTeamSummary
import com.deepseek.dsh.core.model.TeamTaskItem
import com.deepseek.dsh.core.protocol.DshClientProtocol
import com.deepseek.dsh.core.protocol.RemoteEvent
import com.deepseek.dsh.core.protocol.RemoteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** 已经建立 Host `$events` 转订阅的 Host，避免重复连接。 */
    private val forwardedEventHosts = mutableSetOf<String>()

    /** 官方 `account/watch` 的实时账号态；null 表示尚未收到。 */
    private val _hostAccountFlow = MutableStateFlow<AccountViewInfo?>(null)
    val hostAccountFlow: StateFlow<AccountViewInfo?> = _hostAccountFlow.asStateFlow()

    /** Host 通知 DeepSeek 会话过期（`deepseek-account/session-expired`）。 */
    private val _accountExpiredFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val accountExpiredFlow: kotlinx.coroutines.flow.SharedFlow<Unit> = _accountExpiredFlow.asSharedFlow()

    /** 已经订阅过 `account/watch` 的 Host，避免重复建流。 */
    private val watchedAccountHosts = mutableSetOf<String>()

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
     * 仓库不重复拥有。
     *
     * 每次绑定都换成调用方给的新门面，即使地址相同：Host 可能刚重启或刚升级过
     * 插件，沿用旧门面会保留过期的 manifest 缓存。重新协商一次 manifest 是本地
     * 回环请求，代价远小于拿旧缓存当事实。
     */
    fun bindBridge(baseUrl: String, registry: BridgeRegistry) {
        bridgeBaseUrl = baseUrl
        bridgeRegistry = registry
    }

    /** 当前 bridge 门面；未绑定时为 null。 */
    fun bridge(): BridgeRegistry? = bridgeRegistry

    /**
     * 读取 Host **正在运行**的 bridge 插件版本。
     *
     * 这是更新判断最权威的「已装版本」来源：落盘文件可能已经换成新版本，但 Host
     * 还没重启时，运行中的仍是旧代码。bridge 未绑定、Host 不可达或插件没加载时
     * 返回 null，调用方退回落盘版本。
     * @param forceRefresh 是否丢弃 manifest 缓存重新协商（Host 刚重启时用）。
     * @return 插件版本；读不到时为 null。
     */
    suspend fun bridgePluginVersion(forceRefresh: Boolean = false): String? =
        bridgeRegistry?.pluginVersion(forceRefresh)

    private val _pluginSettingsPagesFlow =
        MutableStateFlow<List<PluginSettingsPage>>(emptyList())

    private val _pluginSlotSurfacesFlow =
        MutableStateFlow<List<BridgeSlotSurface>>(emptyList())

    /** 插件声明的全部原生界面入口，按 `slot` 分派；UI 只认自己实现的位置。 */
    val pluginSlotSurfacesFlow: StateFlow<List<BridgeSlotSurface>>
        get() = _pluginSlotSurfacesFlow.asStateFlow()

    private val _pluginSlotEntriesFlow = MutableStateFlow<List<PluginSlotEntry>>(emptyList())

    /**
     * 插件声明的全部界面入口，带原生落点。
     *
     * 来源是真实客户端 bundle 的注册（`client-slots`），落点由 bridge 翻译。已经
     * 有专门屏幕的位置（设置、插件列表）由那些屏幕自行消费，这里保留全量，供
     * 「插件入口」这类汇总视图使用。
     */
    val pluginSlotEntriesFlow: StateFlow<List<PluginSlotEntry>> = _pluginSlotEntriesFlow.asStateFlow()

    /** 插件声明、映射到原生设置页的入口；内容页尚未实现。 */
    val pluginSettingsPagesFlow: StateFlow<List<PluginSettingsPage>> =
        _pluginSettingsPagesFlow.asStateFlow()

    /**
     * 读取插件声明的界面入口与设置页。
     *
     * 两条来源互相独立，失败也互不牵连：
     * - `client-slots/list` 是「插件界面」分组的唯一来源（注册 + 本地化 label）；
     * - `host-slots/list` 提供 Host 侧设置命名空间，用来补「插件设置」分组。
     *
     * Host 刚启动时桥可能还没完成首轮鉴权/扫描：一次失败不能把两条都清空并
     * 再也不重试（调用方会周期性重拉），所以这里分别降级。
     */
    suspend fun refreshPluginSettingsPages() {
        val registry = bridgeRegistry ?: run {
            _pluginSlotSurfacesFlow.value = emptyList()
            _pluginSettingsPagesFlow.value = emptyList()
            _pluginSlotEntriesFlow.value = emptyList()
            return
        }
        // ① 插件界面注册：关键路径，先取。
        // 插件注册的 `label` 是 `t('key')` thunk：只有带上界面语言，bridge 才能解析出
        // 与 Web 界面一致的文案（例如「订阅」而不是注册 id “subscriptions”）。
        val locale = java.util.Locale.getDefault().toLanguageTag()
        when (val all = registry.slotRegistrations(locale = locale)) {
            is BridgeResult.Success -> {
                _pluginSlotEntriesFlow.value = all.value.registrations
                    // 同一个包可能经多个 node_modules 根（安装目录、profile 的
                    // @deepseek-ai 符号链接）被扫描到多次；列表只应出现一次。
                    .distinctBy { registration ->
                        "${registration.slot}|${registration.identity}|${registration.packageName}"
                    }
                    .map { registration ->
                        PluginSlotEntry(
                            placement = registration.placement ?: "other",
                            order = registration.order ?: 0,
                            slot = registration.slot,
                            id = registration.id,
                            key = registration.key,
                            title = registration.label,
                            module = registration.packageName,
                            componentId = registration.componentId,
                        )
                    }
            }
            // 失败时保留已有条目：一次瞬时失败不该让正在使用的入口消失。
            is BridgeResult.Failure -> Unit
        }
        // ② Host 侧设置命名空间：可选路径，失败只影响「插件设置」分组。
        val surfaces = when (val result = registry.hostSlotSurfaces()) {
            is BridgeResult.Success -> result.value.surfaces
            is BridgeResult.Failure -> emptyList()
        }
        _pluginSlotSurfacesFlow.value = surfaces
        _pluginSettingsPagesFlow.value = surfaces
            .filter { it.slot == SETTINGS_PAGE_SLOT }
            .map { surface ->
                PluginSettingsPage(
                    id = surface.id,
                    title = surface.title,
                    description = surface.description,
                    module = surface.module,
                )
            }
    }


    /**
     * 取走 Host 排队的瞬态消息。
     *
     * 没有桥或调用失败时返回空列表：瞬态消息本来就不该阻塞任何东西。
     *
     * @returns 自上次调用以来排队的消息。
     */
    suspend fun pendingPluginEvents(): List<com.deepseek.dsh.core.bridge.BridgeTransientEvent> {
        val registry = bridgeRegistry ?: return emptyList()
        return when (val result = registry.pendingEvents()) {
            is BridgeResult.Success -> result.value.events
            is BridgeResult.Failure -> emptyList()
        }
    }

    /**
     * 插件请求刷新的组件 id 流（`client/refresh` 瞬态消息）。
     *
     * 插件在 Host 侧响应过 `dispatch` 回传的事件后，bridge 给手机排一条
     * `client/refresh`；正在展示该组件树的页面（[pluginSlotEntriesFlow] 的消费
     * 者）collect 这个流并重新拉取 `component-tree`，比固定间隔轮询省得多。
     */
    val pluginComponentRefreshFlow: kotlinx.coroutines.flow.MutableSharedFlow<String> =
        kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 16)

    /**
     * 把一批瞬态消息里的 `client/refresh` 转成组件刷新信号；toast 由调用方另行呈现。
     */
    suspend fun dispatchPluginEvents(events: List<com.deepseek.dsh.core.bridge.BridgeTransientEvent>) {
        for (event in events) {
            if (event.kind == "client/refresh" && event.text.isNotBlank()) {
                pluginComponentRefreshFlow.emit(event.text)
            }
        }
    }

    /**
     * 读取一份插件设置文档。
     *
     * @param ns 设置命名空间，等于 Host 里那一行的 entry id。
     * @returns 表单与当前值；失败时返回 null，由页面显示「暂时读不到」。
     */
    suspend fun pluginSettingsDocument(ns: String): PluginSettingsDocument? {
        val registry = bridgeRegistry ?: return null
        return when (val result = registry.settingsDescribe(ns)) {
            is BridgeResult.Success -> PluginSettingsDocument(
                ns = result.value.ns,
                revision = result.value.revision,
                schema = result.value.schema,
                value = result.value.value,
            )
            is BridgeResult.Failure -> null
        }
    }

    /**
     * 回写插件设置。
     *
     * @param ns 设置命名空间。
     * @param patch 编辑后的字段。
     * @param expectedRevision 打开表单时的版本，Host 用它拒绝覆盖别人的修改。
     * @returns 是否写入成功。
     */
    suspend fun updatePluginSettings(ns: String, patch: JsonObject, expectedRevision: Int): Boolean {
        val registry = bridgeRegistry ?: return false
        return when (val result = registry.settingsUpdate(ns, patch, expectedRevision)) {
            is BridgeResult.Success -> result.value.updated
            is BridgeResult.Failure -> false
        }
    }

    /**
     * 描述一个凭据引用（不返回值，只回可配置状态与可写性）。
     *
     * 凭据不属于设置文档：插件页的 API Key 之类的字段写的是 Host 的 `credentials`
     * 域，bridge 没有这层接口，所以走 Host 自己的 Remote API（`credentials/describe`）。
     * 网页版设置页读的也是同一条路径。
     *
     * @param hostId Host 标识。
     * @param ref 凭据引用名（例如 `DEEPSEEK_API_KEY`）。
     * @returns 状态；Host 不可用或响应无法解析时返回 null。
     */
    suspend fun credentialStatus(hostId: String, ref: String): CredentialStatus? {
        val args = buildJsonObject {
            put("refs", kotlinx.serialization.json.buildJsonArray { add(JsonPrimitive(ref)) })
        }
        val raw = rpcRaw(hostId, "credentials/describe", args.toString()).getOrNull() ?: return null
        val root = runCatching { credentialJson.parseToJsonElement(raw) as JsonObject }.getOrNull() ?: return null
        val value = root["result"]?.jsonObjectOrNull()?.get("value")?.jsonObjectOrNull() ?: return null
        val entry = value[ref]?.jsonObjectOrNull() ?: return null
        return CredentialStatus(
            configured = entry["configured"]?.jsonPrimitive?.booleanOrNull ?: false,
            writable = entry["writable"]?.jsonPrimitive?.booleanOrNull ?: true,
            source = entry["source"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /**
     * 写入一个凭据引用。
     *
     * @param hostId Host 标识。
     * @param ref 凭据引用名。
     * @param value 非空密钥值；写进去的值不会再被任何读接口返回。
     * @returns 是否被 Host 接受。
     */
    suspend fun setCredential(hostId: String, ref: String, value: String): Boolean {
        val args = buildJsonObject {
            put("ref", ref)
            put("value", value)
        }
        val raw = rpcRaw(hostId, "credentials/set", args.toString()).getOrNull() ?: return false
        val root = runCatching { credentialJson.parseToJsonElement(raw) as JsonObject }.getOrNull() ?: return false
        return root["result"]?.jsonObjectOrNull()?.get("ok")?.jsonPrimitive?.booleanOrNull == true
    }

    /**
     * 删除一个凭据引用。
     *
     * @param hostId Host 标识。
     * @param ref 凭据引用名。
     * @returns 是否被 Host 接受。
     */
    suspend fun unsetCredential(hostId: String, ref: String): Boolean {
        val args = buildJsonObject { put("ref", ref) }
        val raw = rpcRaw(hostId, "credentials/unset", args.toString()).getOrNull() ?: return false
        val root = runCatching { credentialJson.parseToJsonElement(raw) as JsonObject }.getOrNull() ?: return false
        return root["result"]?.jsonObjectOrNull()?.get("ok")?.jsonPrimitive?.booleanOrNull == true
    }

    /** 一个凭据引用的可配置状态；值本身永远不会到达客户端。 */
    data class CredentialStatus(
        val configured: Boolean,
        val writable: Boolean,
        val source: String?,
    )

    /**
     * 读取一次插件目录。
     *
     * 失败不是异常：目录会退化成 `source = NONE`，说明进 [bridgeWarningsFlow]，
     * 由 UI 决定展示空态还是回落 Host 原生清单。
     *
     * 未绑定不是错误：Host 还在启动时桥没有地址，此时**不产生**任何提示。
     * 把「尚未就绪」当问题展示给用户只会制造噪音。
     */
    suspend fun refreshBridgePluginCatalog(iconSize: Int = BridgePluginCatalog.DEFAULT_ICON_SIZE) {
        val registry = bridgeRegistry
        if (registry == null) {
            _bridgePluginCatalogFlow.value = BridgePluginCatalogData(source = BridgePluginCatalogSource.NONE)
            _bridgeWarningsFlow.value = emptyList()
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

    /** 当前会话的团队投影（`agentTeam`）；没有团队时为 null。 */
    private val _agentTeamFlow = MutableStateFlow<AgentTeamSummary?>(null)
    val agentTeamFlow: StateFlow<AgentTeamSummary?> = _agentTeamFlow.asStateFlow()

    /** 读取当前会话的团队成员；传 null 表示没有会话，直接清空。 */
    suspend fun refreshAgentTeam(hostId: String, sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            _agentTeamFlow.value = null
            return
        }
        when (val res = client.getAgentTeam(hostId, sessionId)) {
            is RemoteResult.Success -> _agentTeamFlow.value = res.data
            is RemoteResult.Failure -> _agentTeamFlow.value = null
        }
    }

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

    /**
     * 启停一个 bundle。
     *
     * 返回 Host 的完整变更结果，而不是只回布尔：`restart-required` 和 `warnings`
     * 是界面必须告诉用户的（官方网页端同样会提示「请重启 DSH 使更改生效」）。
     *
     * @param hostId Host 标识。
     * @param packageName bundle 包名。
     * @param enabled 目标状态。
     * @returns 变更结果（含 restartRequired / restartReason / warnings）。
     */
    suspend fun setBundleEnabled(
        hostId: String,
        packageName: String,
        enabled: Boolean,
    ): Result<BundleChangeOutcome> {
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
        val args = buildJsonObject {
            put("name", packageName)
            put("enabled", enabled)
        }
        return rpcRaw(hostId, "pluginManager/setBundleEnabled", args.toString()).mapCatching { raw ->
            val value = remoteValueOrThrow(raw) as? JsonObject ?: error("Host 没有返回变更结果")
            fun text(key: String): String? = (value[key] as? JsonPrimitive)?.contentOrNull
            val warnings = (value["warnings"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            BundleChangeOutcome(
                application = text("application"),
                restartReason = text("restartReason"),
                warnings = warnings,
            )
        }
    }

    /**
     * 启停一条 Loader 行插件（非 bundle 安装的第三方插件）。
     *
     * UI 先把开关拨到目标值，再以 Host 的实际状态为准刷新；失败时调用方负责
     * 把界面拨回去（当前实现由 `refreshPluginInventory` 完成）。
     *
     * @param entryId `pluginManager/listPlugins` 的 entryId。
     * @param enabled 目标状态。
     */
    suspend fun setPluginEnabled(hostId: String, entryId: String, enabled: Boolean): Result<Boolean> {
        _pluginRowsFlow.value = _pluginRowsFlow.value.map { row ->
            if (row.entryId == entryId) row.copy(enabled = enabled) else row
        }
        val result = when (val res = client.setPluginEnabled(hostId, entryId, enabled)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }
        refreshPluginInventory(hostId)
        return result
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

    /** 官方 `session/rename`：改会话标题。 */
    suspend fun renameSession(hostId: String, sessionId: String, title: String): Boolean {
        val args = buildJsonObject {
            put("request", buildJsonObject {
                put("sessionId", sessionId)
                put("title", title)
            })
        }
        val ok = rpcRaw(hostId, "session/rename", args.toString()).isSuccess
        if (ok) refreshSessions(hostId)
        return ok
    }

    /** 官方 `session/fork`：从最近的完整轮次分叉一个新会话，返回新会话 id。 */
    suspend fun forkSession(hostId: String, sessionId: String): String? {
        val args = buildJsonObject {
            put("request", buildJsonObject { put("sessionId", sessionId) })
        }
        val raw = rpcRaw(hostId, "session/fork", args.toString()).getOrNull() ?: return null
        val root = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonObject
        }.getOrNull()
        val value = (root?.get("result") as? JsonObject)?.get("value") as? JsonObject
        val newSessionId = (value?.get("sessionId") as? JsonPrimitive)?.contentOrNull
        if (newSessionId != null) refreshSessions(hostId)
        return newSessionId
    }

    /** 官方 `workspace/pinSession` / `unpinSession`：置顶/取消置顶。 */
    suspend fun setSessionPinned(hostId: String, sessionId: String, pinned: Boolean): Boolean {
        val method = if (pinned) "workspace/pinSession" else "workspace/unpinSession"
        val args = buildJsonObject {
            put("request", buildJsonObject { put("sessionId", sessionId) })
        }
        val ok = rpcRaw(hostId, method, args.toString()).isSuccess
        if (ok) refreshSessions(hostId)
        return ok
    }

    /** 官方 `workspace/archiveSession` / `unarchiveSession`：归档/取消归档。 */
    suspend fun setSessionArchived(hostId: String, sessionId: String, archived: Boolean): Boolean {
        val method = if (archived) "workspace/archiveSession" else "workspace/unarchiveSession"
        val args = buildJsonObject {
            put("request", buildJsonObject { put("sessionId", sessionId) })
        }
        val ok = rpcRaw(hostId, method, args.toString()).isSuccess
        if (ok) refreshSessions(hostId)
        return ok
    }

    /** Call a plugin-owned Host route with its payload unchanged. */
    suspend fun pluginRpc(hostId: String, method: String, payloadJson: String): Result<String> =
        when (val res = client.pluginRpc(hostId, method, payloadJson)) {
            is RemoteResult.Success -> Result.success(res.data)
            is RemoteResult.Failure -> Result.failure(IllegalStateException("${res.code}: ${res.message}"))
        }

    // ── 思考过程翻译（cotTranslation Remote）──────────────────────────────────
    //
    // 官方在 conversation.chat.reasoning.body 把展开的思考内容翻译后展示，
    // 提供「查看译文/查看原文」切换；译文按段落缓存，重新展开不再请求。

    /** 读翻译上限、当前偏好与可用服务。 */
    suspend fun cotTranslationLimits(hostId: String): Result<CotTranslationLimits> =
        rpcRaw(hostId, "cotTranslation/limits", "{}").mapCatching { raw ->
            parseCotTranslationLimits(remoteValueOrThrow(raw)) ?: error("翻译服务没有返回配置")
        }

    /**
     * 翻译一段思考内容。
     *
     * 按空行拆段、按 `maxTextChars` 切分后顺序请求（与官方相同），最后按原段落
     * 拼接；任何一段失败都返回失败，由界面显示「翻译暂不可用，已显示原文」。
     *
     * @param hostId Host 标识。
     * @param text 思考内容原文。
     * @param sessionId 会话 id（Host 用于缓存与统计）。
     * @returns 译文。
     */
    suspend fun cotTranslationTranslate(
        hostId: String,
        text: String,
        sessionId: String,
    ): Result<String> {
        val limits = cotTranslationLimits(hostId).getOrElse { return Result.failure(it) }
        val paragraphs = text.split(Regex("\\r?\\n(?:[\\t ]*\\r?\\n)+"))
        val translated = mutableListOf<String>()
        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) continue
            val pieces = paragraph.chunked(limits.maxTextChars.coerceAtLeast(200))
            val builder = StringBuilder()
            for (piece in pieces) {
                val args = buildJsonObject {
                    put(
                        "request",
                        buildJsonObject {
                            put("provider", limits.provider)
                            put("targetLanguage", limits.targetLanguage)
                            put("text", piece)
                            put("sessionId", sessionId)
                        },
                    )
                }
                val result = rpcRaw(hostId, "cotTranslation/translate", args.toString())
                    .mapCatching { raw -> (remoteValueOrThrow(raw) as? JsonPrimitive)?.contentOrNull.orEmpty() }
                    .getOrElse { return Result.failure(it) }
                builder.append(result)
            }
            translated.add(builder.toString())
        }
        return Result.success(translated.joinToString("\n\n"))
    }

    // ── Claude Code 模组横幅（claudeCodeMods Remote）──────────────────────────
    //
    // 官方在 conversation.input.dock 画一条由 Host 下发的动态横幅；watchBand 是
    // 流式方法，走 Gateway 的 `/api/remote.mux` WebSocket。

    /** 最近收到的原始会话帧（开发者工具/会话数据诊断用）。 */
    fun rawSessionFrames(sessionId: String): List<com.deepseek.dsh.core.protocol.SessionRawFrame> =
        client.rawSessionFrames(sessionId)

    /** 订阅会话的模组横幅；每次重绘发一份快照，流结束即完成。 */
    fun claudeCodeBand(hostId: String, sessionId: String): Flow<ClaudeCodeBandSnapshot?> =
        client.watchClaudeCodeBand(hostId, sessionId).map { snapshot -> parseBandSnapshot(snapshot) }

    /**
     * 点横幅上的按钮。
     *
     * @param hostId Host 标识。
     * @param sessionId 会话 id。
     * @param generation 快照代次（过期的按钮会被 Host 拒绝）。
     * @param actionId 按钮的动作 id。
     * @returns 按下的新快照。
     */
    suspend fun pressClaudeCodeBand(
        hostId: String,
        sessionId: String,
        generation: Long,
        actionId: String,
    ): Result<ClaudeCodeBandSnapshot?> {
        val args = buildJsonObject {
            put("agent", sessionId)
            put("generation", generation)
            put("actionId", actionId)
        }
        return rpcRaw(hostId, "claudeCodeMods/pressBand", args.toString())
            .mapCatching { raw -> parseBandSnapshot(remoteValueOrThrow(raw)) }
    }

    // ── 插件市场（pluginMarket Remote）─────────────────────────────────────────
    //
    // 「发现」页的数据来自 Host 的 `pluginMarket` Remote，与网页版读同一份索引。
    // Host 没有挂载这个服务（bundle 关闭）时，这些方法都会返回失败，页面据此
    // 显示官方的「无法读取索引源」。

    /** 已配置的索引源摘要。 */
    suspend fun marketIndexes(hostId: String): Result<List<MarketIndexSummary>> =
        rpcRaw(hostId, "pluginMarket/listIndexes", "{}").mapCatching { raw ->
            parseMarketIndexes(remoteValueOrThrow(raw))
        }

    /**
     * 在某个索引里搜索插件。
     *
     * @param hostId Host 标识。
     * @param url 索引源 URL。
     * @param queryJson 官方 `MarketQuery` 的 JSON（text/category/labels/sort/offset/limit…）。
     * @returns 结果；索引尚未读取成功时 Host 返回 null，这里也返回 null。
     */
    suspend fun marketSearch(hostId: String, url: String, queryJson: String): Result<MarketListing?> {
        val args = buildJsonObject {
            put("url", url)
            put(
                "query",
                runCatching { credentialJson.parseToJsonElement(queryJson) }.getOrNull()
                    ?: JsonObject(emptyMap()),
            )
        }
        return rpcRaw(hostId, "pluginMarket/search", args.toString()).mapCatching { raw ->
            parseMarketSearch(remoteValueOrThrow(raw))
        }
    }

    /** 覆盖式保存索引源列表。 */
    suspend fun marketSetIndexes(hostId: String, urls: List<String>): Result<Boolean> {
        val args = buildJsonObject {
            put(
                "urls",
                kotlinx.serialization.json.buildJsonArray { urls.forEach { add(JsonPrimitive(it)) } },
            )
        }
        return rpcRaw(hostId, "pluginMarket/setIndexes", args.toString()).map { true }
    }

    // ── 语音输入（speech Remote）─────────────────────────────────────────────
    //
    // 官方网页端的语音输入用 `remote.speech`：catalog 给出识别服务/语言/准备
    // 状态，configure/prepare/cancelPreparation 改选择与模型准备。原生页用同一
    // 组方法，因此与网页版共享同一份 Host 状态。

    /** 读取语音识别目录（服务、语言、准备状态）。 */
    suspend fun speechCatalog(hostId: String): Result<SpeechCatalog> =
        rpcRaw(hostId, "speech/catalog", "{}").mapCatching { raw ->
            parseSpeechCatalog(remoteValueOrThrow(raw)) ?: error("语音服务没有返回目录")
        }

    /** 保存识别服务与语言选择（Host 方法签名是 `configure(patch)`）。 */
    suspend fun speechConfigure(
        hostId: String,
        providerId: String,
        language: String,
    ): Result<Boolean> {
        val args = buildJsonObject {
            put(
                "patch",
                buildJsonObject {
                    put("providerId", providerId)
                    put("language", language)
                },
            )
        }
        return rpcRaw(hostId, "speech/configure", args.toString()).map { true }
    }

    /**
     * 准备某个服务（下载/校验/加载本地模型）。
     *
     * @param hostId Host 标识。
     * @param providerId 识别服务 id。
     * @param downloadSource 下载来源；null/空表示自动选择。
     */
    suspend fun speechPrepare(
        hostId: String,
        providerId: String,
        downloadSource: String?,
    ): Result<Boolean> {
        val args = buildJsonObject {
            put("providerId", providerId)
            if (!downloadSource.isNullOrBlank()) {
                put("options", buildJsonObject { put("downloadSource", downloadSource) })
            }
        }
        return rpcRaw(hostId, "speech/prepare", args.toString()).map { true }
    }

    /** 取消进行中的准备。 */
    suspend fun speechCancelPrepare(hostId: String, providerId: String): Result<Boolean> {
        val args = buildJsonObject { put("providerId", providerId) }
        return rpcRaw(hostId, "speech/cancelPreparation", args.toString()).map { true }
    }

    /**
     * 转写一段录音。
     *
     * @param hostId Host 标识。
     * @param providerId 识别服务 id。
     * @param language 识别语言（auto/zh/en/…）。
     * @param audioBase64 规范 16 kHz 单声道 PCM16 WAV 的 base64（无 data URL 前缀）。
     * @returns 识别出的文字。
     */
    suspend fun speechTranscribe(
        hostId: String,
        providerId: String,
        language: String,
        audioBase64: String,
    ): Result<String> {
        val args = buildJsonObject {
            put(
                "request",
                buildJsonObject {
                    put("audioBase64", audioBase64)
                    put("providerId", providerId)
                    put("language", language)
                },
            )
        }
        return rpcRaw(hostId, "speech/transcribe", args.toString()).mapCatching { raw ->
            val value = remoteValueOrThrow(raw) as? JsonObject ?: error("语音转写没有返回结果")
            (value["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }
    }

    /** 取出 Remote 响应信封里的 `result.value`；`ok=false` 时抛出 Host 的错误码。 */
    private fun remoteValueOrThrow(raw: String): JsonElement? {        val root = credentialJson.parseToJsonElement(raw) as? JsonObject
            ?: error("无法解析 Host 响应")
        val result = root["result"] as? JsonObject ?: error("Host 响应缺少 result")
        val ok = (result["ok"] as? JsonPrimitive)?.booleanOrNull == true
        if (!ok) {
            val error = result["error"] as? JsonObject
            error((error?.get("code") as? JsonPrimitive)?.contentOrNull ?: "Host 拒绝了这次请求")
        }
        val value = result["value"]
        return if (value == null || value is kotlinx.serialization.json.JsonNull) null else value
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
            val result = client.sendMessage(hostId, sessionId, content)
            if (result is RemoteResult.Failure) {
                // Host 拒绝了 prompt（模型/权限/网络）：落一条可见错误，避免界面毫无反馈。
                dao.insertMessages(
                    listOf(
                        MessageEntity(
                            hostId = hostId,
                            sessionId = sessionId,
                            messageId = "msg_error_${System.currentTimeMillis()}",
                            role = MessageRole.ASSISTANT.name,
                            seq = 0L,
                            contentJson = "发送失败：${result.message}",
                            createdAt = System.currentTimeMillis(),
                            status = "error"
                        )
                    )
                )
            }
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
        // 官方 model picker 监听的 Host 全局事件：settings/凭据/llm 适配器一变就
        // 重新拉模型目录（对齐 `ctx.remote.$on(...)` 的四个 refresh 触发器）。
        if (synchronized(forwardedEventHosts) { forwardedEventHosts.add(hostId) }) {
            scope.launch(Dispatchers.IO) {
                client.forwardedHostEvents(hostId).collect { eventName ->
                    when (eventName) {
                        in MODEL_CATALOG_REFRESH_EVENTS -> refreshModelCatalog(hostId)
                        // 插件安装/启停/卸载：让插件页与原生伴生列表立刻刷新。
                        "plugin-manager/changed" -> {
                            refreshPluginInventory(hostId)
                            refreshBridgePluginCatalog()
                            refreshPluginSettingsPages()
                        }
                        // 插件市场索引变化（新增/删除市场）。
                        "plugin-market/changed" -> refreshBridgePluginCatalog()
                        // 会话被新建/删除/状态变化：列表与运行态直接重拉。
                        "api-session/added", "api-session/removed", "api-session/status" ->
                            refreshSessions(hostId)
                        // 定时任务变化：自动化列表与团队任务。
                        "schedule/changed" -> {
                            refreshAutomationTasks(hostId)
                            refreshTeamTasks(hostId)
                        }
                        // 审批请求：先刷新待审批列表；waterfall 本身回 next 交给 Host 默认处理。
                        "approval/request" -> refreshApprovals(hostId)
                        // DeepSeek 账号会话过期：交给 UI 清登录态。
                        "deepseek-account/session-expired" -> _accountExpiredFlow.tryEmit(Unit)
                        else -> Unit
                    }
                }
            }
        }
        // 官方账号态流：登录过程/凭据变化直接推送，替代轮询。
        if (synchronized(watchedAccountHosts) { watchedAccountHosts.add(hostId) }) {
            scope.launch(Dispatchers.IO) {
                client.watchAccount(hostId).collect { view ->
                    _hostAccountFlow.value = view
                }
            }
        }
    }
}

/** 原生设置页位置；bridge 用这个名字说明条目该落在设置里。 */
private const val SETTINGS_PAGE_SLOT = "settings.page"

/**
 * 官方 model picker 同样监听这四个 Host 事件刷新模型目录：
 * `ctx.remote.$on('llm/adapters-updated' | 'settings/document-updated' |
 * 'credentials/record-updated' | 'credentials/reference-updated')`。
 */
private val MODEL_CATALOG_REFRESH_EVENTS = setOf(
    "llm/adapters-updated",
    "settings/document-updated",
    "credentials/record-updated",
    "credentials/reference-updated",
)

// 凭据接口的响应只是 Host 自己的 JSON：这里单独一个宽松实例，不影响其他解码。
private val credentialJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

