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
import com.deepseek.dsh.core.model.AutomationTaskItem
import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.ComposerStats
import com.deepseek.dsh.core.model.MessageRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
import com.deepseek.dsh.core.model.TerminalInfo
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

    /** Host 提示某模型需要重新登录账号（`deepseek-account/model-sign-in-required`）。 */
    private val _modelSignInRequiredFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val modelSignInRequiredFlow: kotlinx.coroutines.flow.SharedFlow<Unit> =
        _modelSignInRequiredFlow.asSharedFlow()

    /** 已经订阅过 `account/watch` 的 Host，避免重复建流。 */
    private val watchedAccountHosts = mutableSetOf<String>()

    /** 等待 App 回答的审批 waterfall（官方 `approval/request`）。 */
    private val _pendingWaterfallApprovalsFlow =
        MutableStateFlow<List<com.deepseek.dsh.core.protocol.ForwardedWaterfall>>(emptyList())
    val pendingWaterfallApprovalsFlow: StateFlow<List<com.deepseek.dsh.core.protocol.ForwardedWaterfall>> =
        _pendingWaterfallApprovalsFlow.asStateFlow()

    /** 等待 App 回答的提问 waterfall（官方 `user-questions/request`）。 */
    private val _pendingQuestionsFlow = MutableStateFlow<List<com.deepseek.dsh.core.protocol.ForwardedWaterfall>>(emptyList())
    val pendingQuestionsFlow: StateFlow<List<com.deepseek.dsh.core.protocol.ForwardedWaterfall>> =
        _pendingQuestionsFlow.asStateFlow()

    /** 已经订阅过 `workspace/follow` 的 Host。 */
    private val watchedWorkspaceHosts = mutableSetOf<String>()

    /** 官方 `job/list` 的当前可见后台任务集合。 */
    private val _jobsFlow = MutableStateFlow<List<com.deepseek.dsh.core.protocol.HostJobInfo>>(emptyList())
    val jobsFlow: StateFlow<List<com.deepseek.dsh.core.protocol.HostJobInfo>> =
        _jobsFlow.asStateFlow()

    /** job/list 订阅的会话；换会话时重订。 */
    private var jobsSessionId: String? = null
    private var jobsHostId: String? = null
    private var jobsJob: kotlinx.coroutines.Job? = null

    /** 当前会话的待办（官方 `todos` 投影）。 */
    private val _todosFlow = MutableStateFlow<List<com.deepseek.dsh.core.model.TodoEntryInfo>>(emptyList())
    val todosFlow: StateFlow<List<com.deepseek.dsh.core.model.TodoEntryInfo>> =
        _todosFlow.asStateFlow()

    /** 官方 `inbox` 投影：下一轮待发消息队列（运行中发送的消息排在这里）。 */
    private val _queuedMessagesFlow =
        MutableStateFlow<List<com.deepseek.dsh.core.model.QueuedMessageInfo>>(emptyList())
    val queuedMessagesFlow: StateFlow<List<com.deepseek.dsh.core.model.QueuedMessageInfo>> =
        _queuedMessagesFlow.asStateFlow()

    /** 当前会话最近一轮的改动汇总（官方 `workspace/changes` + `/api/changes.summary`）。 */
    private val _changesFlow =
        MutableStateFlow<com.deepseek.dsh.core.protocol.ChangesSummaryInfo?>(null)
    val changesFlow: StateFlow<com.deepseek.dsh.core.protocol.ChangesSummaryInfo?> =
        _changesFlow.asStateFlow()
    private var changesSeq: Long = 0L

    /** 当前会话本轮产出的文件（官方 `deliverables/presented`）。 */
    private val _deliverablesFlow =
        MutableStateFlow<List<com.deepseek.dsh.core.protocol.PresentedFileInfo>>(emptyList())
    val deliverablesFlow: StateFlow<List<com.deepseek.dsh.core.protocol.PresentedFileInfo>> =
        _deliverablesFlow.asStateFlow()

    /** 当前会话的目标（官方 `goal` 投影）。 */
    private val _goalFlow = MutableStateFlow<com.deepseek.dsh.core.model.GoalInfo?>(null)
    val goalFlow: StateFlow<com.deepseek.dsh.core.model.GoalInfo?> = _goalFlow.asStateFlow()

    /** 官方 `session/control` 投影出的每会话模型选择（model id）。 */
    private val _sessionModelFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val sessionModelFlow: StateFlow<Map<String, String>> = _sessionModelFlow.asStateFlow()
    private val sessionModels = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val watchedControlHosts = mutableSetOf<String>()

    /** 官方 `workspace/follow` 投影出的工作区树（按 Host 顺序）。 */
    private val _hostWorkspacesFlow = MutableStateFlow<List<HostWorkspaceInfo>>(emptyList())
    val hostWorkspacesFlow: StateFlow<List<HostWorkspaceInfo>> =
        _hostWorkspacesFlow.asStateFlow()
    private val hostWorkspaces = LinkedHashMap<String, HostWorkspaceInfo>()
    private val hostWorkspaceOrder = mutableListOf<String>()
    private val hostArchivedSessionIds = mutableSetOf<String>()
    private val hostPinnedSessionIds = mutableListOf<String>()

    /** workspace/follow 帧触发的会话列表刷新（合并抖动）。 */
    private var workspaceRefreshJob: kotlinx.coroutines.Job? = null

    /** 已经订阅过 `$events` waterfall 的 Host。 */
    private val watchedWaterfallHosts = mutableSetOf<String>()

    private val _sessionStatsFlow = MutableStateFlow<Map<String, ComposerStats>>(emptyMap())
    val sessionStatsFlow: StateFlow<Map<String, ComposerStats>> = _sessionStatsFlow.asStateFlow()

    /** 当前查看会话正在流式输出的助手文本（重连基线 + live chunk）。 */
    private val _streamingTextFlow = MutableStateFlow("")
    val streamingTextFlow: StateFlow<String> = _streamingTextFlow.asStateFlow()

    /** 当前查看会话正在流式输出的推理文本。 */
    private val _streamingReasoningFlow = MutableStateFlow("")
    val streamingReasoningFlow: StateFlow<String> =
        _streamingReasoningFlow.asStateFlow()

    /** 官方 `plugin-manager/install-state` 的实时安装进度。 */
    private val _installProgressFlow = MutableStateFlow<PluginInstallProgressInfo?>(null)
    val installProgressFlow: StateFlow<PluginInstallProgressInfo?> =
        _installProgressFlow.asStateFlow()
    @Volatile
    private var activeInstallRequestId: String? = null
    private var installLogTail: String = ""
    private val watchedEventPayloadHosts = mutableSetOf<String>()

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
    /**
     * 官方 Loader 行拥有的设置命名空间（bridge `host-slots` 的归属）。
     *
     * 模型页用它区分「官方 provider」与「插件 provider」：插件 provider 的命名空间
     * 不在这个集合里，因此不会混进官方模型目录。
     */
    fun officialSettingsNamespaces(): Set<String> =
        _pluginSlotSurfacesFlow.value
            .filter { surface -> surface.module?.startsWith("@deepseek-ai/") == true }
            .mapNotNull { surface -> surface.id.substringAfterLast('#').takeIf { it.isNotBlank() } }
            .toSet()

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
        val surfaces = when (val result = registry.hostSlotSurfaces(java.util.Locale.getDefault().toLanguageTag())) {
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
                secretSlots = result.value.secrets.map {
                    com.deepseek.dsh.core.model.PluginSecretSlot(path = it.path, set = it.set)
                },
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
        val result = BridgePluginCatalog.load(
            registry,
            iconSize,
            java.util.Locale.getDefault().toLanguageTag(),
        )
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

    /** 官方 `workspace/insertBefore`：把工作区移动到某个工作区之前（null = 末尾）。 */
    suspend fun moveWorkspace(
        hostId: String,
        workspaceId: String,
        beforeWorkspaceId: String?,
    ): Boolean {
        val args = buildJsonObject {
            put("request", buildJsonObject {
                put("workspaceId", workspaceId)
                if (beforeWorkspaceId != null) put("beforeWorkspaceId", beforeWorkspaceId)
            })
        }
        return rpcRaw(hostId, "workspace/insertBefore", args.toString()).isSuccess
    }

    /** 官方 `workspace/create`：把一个目录注册成 Host 工作区。 */
    suspend fun createWorkspace(hostId: String, path: String): Boolean {
        val args = buildJsonObject {
            put("request", buildJsonObject { put("path", path) })
        }
        return rpcRaw(hostId, "workspace/create", args.toString()).isSuccess
    }

    /** 官方 `workspace/delete`：取消工作区注册（不删目录）。 */
    suspend fun deleteWorkspace(hostId: String, workspaceId: String): Boolean {
        val args = buildJsonObject {
            put("request", buildJsonObject { put("workspaceId", workspaceId) })
        }
        return rpcRaw(hostId, "workspace/delete", args.toString()).isSuccess
    }

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

    /** 回答一条待审批的 waterfall：`allowed-once` / `rejected`。 */
    suspend fun respondApprovalWaterfall(hostId: String, eventId: String, approved: Boolean) {
        val kept = _pendingWaterfallApprovalsFlow.value.filter { item -> item.eventId != eventId }
        _pendingWaterfallApprovalsFlow.value = kept
        client.answerWaterfall(
            hostId,
            eventId,
            if (approved) "\"allowed-once\"" else "\"rejected\"",
        )
    }

    /** 回答一条提问 waterfall：`{answers:[{id,selected:[label],custom?}]}`。 */
    suspend fun respondUserQuestion(hostId: String, eventId: String, answersJson: String) {
        val kept = _pendingQuestionsFlow.value.filter { item -> item.eventId != eventId }
        _pendingQuestionsFlow.value = kept
        client.answerWaterfall(hostId, eventId, answersJson)
    }

    /** 官方 `session/fork`：从最近的完整轮次分叉一个新会话，返回新会话 id。 */
    suspend fun forkSession(
        hostId: String,
        sessionId: String,
        atSeq: Int? = null,
    ): String? {
        val args = buildJsonObject {
            put("request", buildJsonObject {
                put("sessionId", sessionId)
                if (atSeq != null) put("atSeq", atSeq)
            })
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

    /** 滚动到顶时加载更早的一页历史（官方 session/page）。 */
    suspend fun loadOlderMessages(hostId: String, sessionId: String): Boolean =
        when (val res = client.loadOlderHistory(hostId, sessionId)) {
            is RemoteResult.Success -> res.data
            is RemoteResult.Failure -> false
        }

    /** 打开终端（官方 terminal/create + terminal/attach）。 */
    suspend fun openTerminal(
        hostId: String,
        sessionId: String,
        cols: Int,
        rows: Int,
        terminalId: String = "main",
    ): com.deepseek.dsh.core.protocol.TerminalConnection? =
        when (val res = client.openTerminal(hostId, sessionId, cols, rows, terminalId)) {
            is RemoteResult.Success -> res.data
            is RemoteResult.Failure -> null
        }

    /** 关闭终端进程（官方 terminal/close）。 */
    suspend fun closeTerminal(hostId: String, sessionId: String, terminalId: String = "main") {
        client.closeTerminal(hostId, sessionId, terminalId)
    }

    /** 官方 `terminal/list`：当前会话的网页终端集合。 */
    suspend fun listTerminals(hostId: String, sessionId: String): List<TerminalInfo> {
        val args = buildJsonObject { put("sessionId", sessionId) }
        val raw = rpcRaw(hostId, "terminal/list", args.toString()).getOrNull() ?: return emptyList()
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonArray ?: return emptyList()
        return value.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val shell = obj["shell"] as? JsonObject
            TerminalInfo(
                id = id,
                title = (obj["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                state = (obj["state"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                shellName = (shell?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }
    }

    /** 官方 `terminal/shells`：可选的 shell（路径 + 名称）。 */
    suspend fun listTerminalShells(hostId: String, sessionId: String): List<Pair<String, String>> {
        val args = buildJsonObject { put("agentId", sessionId) }
        val raw = rpcRaw(hostId, "terminal/shells", args.toString()).getOrNull() ?: return emptyList()
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonArray ?: return emptyList()
        return value.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val path = (obj["path"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            path to (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }
    }

    /** 官方 `terminal/create`（显式 id 与 shell），返回新终端信息。 */
    suspend fun createTerminal(
        hostId: String,
        sessionId: String,
        terminalId: String,
        shellPath: String?,
        cols: Int = 80,
        rows: Int = 24,
    ): TerminalInfo? {
        val args = buildJsonObject {
            put("agentId", sessionId)
            put("request", buildJsonObject {
                put("id", terminalId)
                put("cols", cols)
                put("rows", rows)
                if (!shellPath.isNullOrBlank()) put("shellPath", shellPath)
            })
        }
        val raw = rpcRaw(hostId, "terminal/create", args.toString()).getOrNull() ?: return null
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonObject ?: return null
        val shell = value["shell"] as? JsonObject
        return TerminalInfo(
            id = (value["id"] as? JsonPrimitive)?.contentOrNull ?: terminalId,
            title = (value["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            state = (value["state"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            shellName = (shell?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty(),
        )
    }

    /** 官方 `terminal/rename`：改终端标题。 */
    suspend fun renameTerminal(
        hostId: String,
        sessionId: String,
        terminalId: String,
        title: String,
    ): Boolean {
        val args = buildJsonObject {
            put("agentId", sessionId)
            put("id", terminalId)
            put("title", title)
        }
        return rpcRaw(hostId, "terminal/rename", args.toString())
            .getOrNull()?.contains("\"ok\":true") == true
    }

    /** 官方 `commands/list` 的斜杠命令。 */    /** 官方 `commands/list` 的斜杠命令。 */
    private val _hostCommandsFlow = MutableStateFlow<List<com.deepseek.dsh.core.model.HostCommandInfo>>(emptyList())
    val hostCommandsFlow: StateFlow<List<com.deepseek.dsh.core.model.HostCommandInfo>> =
        _hostCommandsFlow.asStateFlow()

    /** 刷新当前会话的斜杠命令目录。 */
    suspend fun refreshHostCommands(hostId: String, sessionId: String) {
        val args = buildJsonObject { put("agentId", sessionId) }
        val raw = rpcRaw(hostId, "commands/list", args.toString()).getOrNull() ?: return
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonArray ?: return
        _hostCommandsFlow.value = value.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val input = obj["input"] as? JsonObject
            com.deepseek.dsh.core.model.HostCommandInfo(
                name = name,
                description = (obj["description"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                inputHint = (input?.get("hint") as? JsonPrimitive)?.contentOrNull,
                acceptsAttachments = input?.get("attachments")?.toString() == "true",
            )
        }
    }

    /** 每个用户消息里的图片附件引用（官方 user/message 的 image 块）。 */
    private val _messageImagesFlow =
        MutableStateFlow<Map<String, List<com.deepseek.dsh.core.protocol.AttachmentImageRef>>>(
            emptyMap(),
        )
    val messageImagesFlow:
        StateFlow<Map<String, List<com.deepseek.dsh.core.protocol.AttachmentImageRef>>> =
        _messageImagesFlow.asStateFlow()

    /** 读取一张已接纳的图片附件字节（官方 `session/attachment`，base64 解码后返回）。 */
    suspend fun loadAttachmentImage(
        hostId: String,
        sessionId: String,
        attachmentId: String,
    ): ByteArray? {
        val args = buildJsonObject {
            put("request", buildJsonObject {
                put("sessionId", sessionId)
                put("attachmentId", attachmentId)
            })
        }
        val raw = rpcRaw(hostId, "session/attachment", args.toString()).getOrNull() ?: return null
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return null
        var value = (root["result"] as? JsonObject)?.get("value") as? JsonObject ?: return null
        if (value["data"] == null) {
            value = value["value"] as? JsonObject ?: value
        }
        val data = (value["data"] as? JsonPrimitive)?.contentOrNull ?: return null
        return runCatching { android.util.Base64.decode(data, android.util.Base64.DEFAULT) }
            .getOrNull()
    }

    private val _messageFeedbackFlow =
        MutableStateFlow<Map<String, com.deepseek.dsh.core.model.MessageFeedbackInfo>>(emptyMap())
    val messageFeedbackFlow:
        StateFlow<Map<String, com.deepseek.dsh.core.model.MessageFeedbackInfo>> =
        _messageFeedbackFlow.asStateFlow()

    /**
     * 应用一次反馈点击：rating 为 null 时删除现有条目，否则写入/更新。
     *
     * 收在数据层是为了让 Compose 侧的 lambda 保持很小 —— MainAppHost 是超长
     * composable，新增内联逻辑会触发 DEX 方法体校验失败（VerifyError）。
     */
    /** 提交一次反馈点击：自己查出当前 version 再 put/delete，宿主只传点击语义。 */
    suspend fun submitMessageFeedback(
        hostId: String,
        sessionId: String,
        messageId: String,
        rating: String?,
        note: String?,
    ): Map<String, com.deepseek.dsh.core.model.MessageFeedbackInfo> {
        val existing = listMessageFeedback(hostId, sessionId)[messageId]
        applyMessageFeedback(
            hostId, sessionId, messageId, rating, note, existing?.version,
        )
        return listMessageFeedback(hostId, sessionId)
    }

    suspend fun applyMessageFeedback(
        hostId: String,
        sessionId: String,
        messageId: String,
        rating: String?,
        note: String?,
        existingVersion: String?,
    ) {
        if (rating == null) {
            if (existingVersion != null) {
                runCatching { deleteMessageFeedback(hostId, sessionId, messageId, existingVersion) }
            }
            return
        }
        runCatching {
            putMessageFeedback(hostId, sessionId, messageId, rating, note, existingVersion)
        }
    }

    /** 读取当前会话的消息反馈（`messageFeedback/list`），返回按消息 id 索引的映射。 */
    suspend fun listMessageFeedback(
        hostId: String,
        sessionId: String,
    ): Map<String, com.deepseek.dsh.core.model.MessageFeedbackInfo> {
        val args = buildJsonObject {
            put("request", buildJsonObject { put("sessionId", sessionId) })
        }
        val raw = rpcRaw(hostId, "messageFeedback/list", args.toString()).getOrNull()
            ?: return emptyMap()
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return emptyMap()
        var value = (root["result"] as? JsonObject)?.get("value") as? JsonObject
            ?: return emptyMap()
        // messageFeedback 的 Remote 返回是 RemoteResult 包了一层：{ok, value:{items}}。
        if (value["items"] == null) {
            value = value["value"] as? JsonObject ?: value
        }
        val items = value["items"] as? JsonArray ?: return emptyMap()
        val map =
            mutableMapOf<String, com.deepseek.dsh.core.model.MessageFeedbackInfo>()
        for (item in items) {
            val obj = item as? JsonObject ?: continue
            val messageId = (obj["messageId"] as? JsonPrimitive)?.contentOrNull ?: continue
            map[messageId] = com.deepseek.dsh.core.model.MessageFeedbackInfo(
                messageId = messageId,
                rating = (obj["rating"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                note = (obj["note"] as? JsonPrimitive)?.contentOrNull,
                version = (obj["version"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }
        return map
    }

    /** 拉取并发布当前会话反馈（供 follow 订阅时刷新）。 */
    suspend fun refreshMessageFeedback(hostId: String, sessionId: String) {
        _messageFeedbackFlow.value = listMessageFeedback(hostId, sessionId)
    }

    /** 写入一条反馈（`messageFeedback/put`），version 为空表示要求当前不存在。 */
    suspend fun putMessageFeedback(
        hostId: String,
        sessionId: String,
        messageId: String,
        rating: String,
        note: String?,
        ifVersion: String?,
    ): Boolean {
        val args = buildJsonObject {
            put("request", buildJsonObject {
                put("sessionId", sessionId)
                put("messageId", messageId)
                put("rating", rating)
                if (!note.isNullOrBlank()) put("note", note)
                if (ifVersion == null) put("ifVersion", kotlinx.serialization.json.JsonNull)
                else put("ifVersion", ifVersion)
            })
        }
        val ok = rpcRaw(hostId, "messageFeedback/put", args.toString())
            .getOrNull()?.contains("\"ok\":true") == true
        if (ok) refreshMessageFeedback(hostId, sessionId)
        return ok
    }

    /** 删除一条反馈（`messageFeedback/delete`），用于再次点击同一评价。 */
    suspend fun deleteMessageFeedback(
        hostId: String,
        sessionId: String,
        messageId: String,
        ifVersion: String,
    ): Boolean {
        val args = buildJsonObject {
            put("request", buildJsonObject {
                put("sessionId", sessionId)
                put("messageId", messageId)
                put("ifVersion", ifVersion)
            })
        }
        val ok = rpcRaw(hostId, "messageFeedback/delete", args.toString())
            .getOrNull()?.contains("\"ok\":true") == true
        if (ok) refreshMessageFeedback(hostId, sessionId)
        return ok
    }

    /** 官方 `skills/list`：当前会话可用的技能（人可调用）。 */
    private val _hostSkillsFlow =
        MutableStateFlow<List<com.deepseek.dsh.core.model.SkillInfo>>(emptyList())
    val hostSkillsFlow: StateFlow<List<com.deepseek.dsh.core.model.SkillInfo>> =
        _hostSkillsFlow.asStateFlow()

    /** 读取当前会话的技能目录并返回（打开加号菜单时直接渲染）。 */
    suspend fun listSkills(
        hostId: String,
        sessionId: String,
    ): List<com.deepseek.dsh.core.model.SkillInfo> {
        val args = buildJsonObject {
            put("request", buildJsonObject { put("sessionId", sessionId) })
        }
        val raw = rpcRaw(hostId, "skills/list", args.toString()).getOrNull() ?: return emptyList()
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonObject
            ?: return emptyList()
        val skills = value["skills"] as? JsonArray ?: return emptyList()
        println("DSH_SKILLS count=${skills.size}")
        return skills.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            com.deepseek.dsh.core.model.SkillInfo(
                name = name,
                description = (obj["description"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                whenToUse = (obj["whenToUse"] as? JsonPrimitive)?.contentOrNull,
                modelInvocable = (obj["modelInvocable"] as? JsonPrimitive)?.contentOrNull == "true",
            )
        }
    }

    /** 刷新当前会话的技能目录（`skills/list`，冷读，不激活 Agent）。 */
    suspend fun refreshHostSkills(hostId: String, sessionId: String) {
        val args = buildJsonObject {
            put("request", buildJsonObject { put("sessionId", sessionId) })
        }
        val raw = rpcRaw(hostId, "skills/list", args.toString()).getOrNull() ?: return
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonObject ?: return
        val skills = value["skills"] as? JsonArray ?: return
        _hostSkillsFlow.value = skills.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            com.deepseek.dsh.core.model.SkillInfo(
                name = name,
                description = (obj["description"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                whenToUse = (obj["whenToUse"] as? JsonPrimitive)?.contentOrNull,
                modelInvocable = (obj["modelInvocable"] as? JsonPrimitive)?.contentOrNull == "true",
            )
        }
    }

    /**
     * 官方 `commands/execute`：执行一条斜杠命令。
     *
     * @returns null 表示 Host 不认识这条命令（调用方应改走普通 prompt）。
     */
    suspend fun executeHostCommand(
        hostId: String,
        sessionId: String,
        line: String,
    ): com.deepseek.dsh.core.model.HostCommandResult? {
        val args = buildJsonObject {
            put("agentId", sessionId)
            put("line", line)
            put("submittedAttachments", kotlinx.serialization.json.JsonArray(emptyList()))
        }
        val raw = rpcRaw(hostId, "commands/execute", args.toString()).getOrNull() ?: return null
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonObject ?: return null
        val result = value["result"] as? JsonObject ?: return null
        return com.deepseek.dsh.core.model.HostCommandResult(
            kind = (result["kind"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            text = (result["text"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    /** 订阅官方 `job/list`（当前会话可见的后台任务集合）。 */    /** 订阅官方 `job/list`（当前会话可见的后台任务集合）。 */
    fun watchJobs(hostId: String, sessionId: String) {
        if (jobsHostId == hostId && jobsSessionId == sessionId) return
        jobsJob?.cancel()
        jobsHostId = hostId
        jobsSessionId = sessionId
        _jobsFlow.value = emptyList()
        jobsJob = scope.launch(Dispatchers.IO) {
            client.jobList(hostId, sessionId).collect { rows ->
                _jobsFlow.value = rows
            }
        }
    }

    /** 停止 job/list 订阅（离开会话）。 */
    fun unwatchJobs() {
        jobsJob?.cancel()
        jobsJob = null
        jobsHostId = null
        jobsSessionId = null
        _jobsFlow.value = emptyList()
    }

    /** 订阅官方 `job/follow`：一条任务的输出流。 */
    fun followJob(hostId: String, sessionId: String?, jobId: String, from: Long? = null) =
        client.jobFollow(hostId, sessionId, jobId, from)

    /** 官方 `job/kill`：人为终止一条后台任务。 */
    suspend fun killJob(hostId: String, sessionId: String, jobId: String): String? =
        when (val res = client.killJob(hostId, sessionId, jobId)) {
            is RemoteResult.Success -> res.data
            is RemoteResult.Failure -> null
        }

    /** 官方 `session/search`：按内容搜索会话。 */
    suspend fun searchSessions(
        hostId: String,
        query: String,
    ): List<com.deepseek.dsh.core.protocol.SessionSearchHit> =
        when (val res = client.searchSessions(hostId, query)) {
            is RemoteResult.Success -> res.data
            is RemoteResult.Failure -> emptyList()
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
        val res = rpcRaw(hostId, method, args.toString())
        if (res.isFailure) {
            android.util.Log.e("DSH_ARCHIVE", "$method failed: ${res.exceptionOrNull()?.message}")
        }
        val ok = res.isSuccess
        if (ok) {
            // Host 归档后 session/list 不再返回它；本地行必须同步标记，否则
            // “隐藏已归档”过滤器看不到变化，按钮看起来像没反应。
            dao.updateSessionArchived(hostId, sessionId, if (archived) 1 else 0)
            refreshSessions(hostId)
        }
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
            put("agentId", sessionId)
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
        val requestId = java.util.UUID.randomUUID().toString()
        activeInstallRequestId = requestId
        installLogTail = ""
        _installProgressFlow.value = PluginInstallProgressInfo(
            phase = "installing", registry = null, index = 1, total = 1, logTail = "",
        )
        return try {
            when (val res = client.installPlugin(hostId, packageName, requestId)) {
                is RemoteResult.Success -> {
                    refreshPluginInventory(hostId)
                    Result.success(res.data)
                }
                is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
            }
        } finally {
            activeInstallRequestId = null
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

    /** 解析 `workspace/follow` 帧并更新工作区树投影。 */
    private fun applyWorkspaceFrame(text: String) {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return
        fun parseWorkspace(element: JsonElement?): HostWorkspaceInfo? {
            val obj = element as? JsonObject ?: return null
            val id = (obj["workspaceId"] as? JsonPrimitive)?.contentOrNull ?: return null
            return HostWorkspaceInfo(
                id = id,
                path = (obj["path"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                title = (obj["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                sessionIds = (obj["sessionIds"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                } ?: emptyList(),
            )
        }
        fun parseBaseline(value: JsonObject) {
            hostWorkspaces.clear()
            hostWorkspaceOrder.clear()
            (value["items"] as? JsonArray)?.forEach { item ->
                val workspace = parseWorkspace(item) ?: return@forEach
                hostWorkspaces[workspace.id] = workspace
                hostWorkspaceOrder.add(workspace.id)
            }
            hostArchivedSessionIds.clear()
            (value["archivedSessionIds"] as? JsonArray)?.forEach { id ->
                (id as? JsonPrimitive)?.contentOrNull?.let { hostArchivedSessionIds.add(it) }
            }
            hostPinnedSessionIds.clear()
            (value["pinnedSessionIds"] as? JsonArray)?.forEach { id ->
                (id as? JsonPrimitive)?.contentOrNull?.let { hostPinnedSessionIds.add(it) }
            }
        }
        when ((root["type"] as? JsonPrimitive)?.contentOrNull) {
            "baseline", "replace" -> (root["value"] as? JsonObject)?.let(::parseBaseline)
            "upsert" -> parseWorkspace(root["workspace"])?.let { workspace ->
                hostWorkspaces[workspace.id] = workspace
                if (workspace.id !in hostWorkspaceOrder) hostWorkspaceOrder.add(workspace.id)
            }
            "remove" -> (root["workspaceId"] as? JsonPrimitive)?.contentOrNull?.let { id ->
                hostWorkspaces.remove(id)
                hostWorkspaceOrder.remove(id)
            }
            "order" -> {
                val ids = (root["workspaceIds"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?: return
                hostWorkspaceOrder.clear()
                hostWorkspaceOrder.addAll(ids.filter { hostWorkspaces.containsKey(it) })
            }
            "archived" -> {
                hostArchivedSessionIds.clear()
                (root["archivedSessionIds"] as? JsonArray)?.forEach { id ->
                    (id as? JsonPrimitive)?.contentOrNull?.let { hostArchivedSessionIds.add(it) }
                }
            }
            "pinned" -> {
                hostPinnedSessionIds.clear()
                (root["pinnedSessionIds"] as? JsonArray)?.forEach { id ->
                    (id as? JsonPrimitive)?.contentOrNull?.let { hostPinnedSessionIds.add(it) }
                }
            }
        }
        _hostWorkspacesFlow.value = hostWorkspaceOrder.mapNotNull { hostWorkspaces[it] }
    }


    /** 解析 `session/control` 帧，维护每会话模型选择。 */
    private fun applyControlFrame(text: String) {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return
        fun modelOf(element: JsonElement?): String? {
            val obj = element as? JsonObject ?: return null
            val selected = (obj["next"] as? JsonObject) ?: (obj["lastUsed"] as? JsonObject)
                ?: return null
            return (selected["model"] as? JsonPrimitive)?.contentOrNull
        }
        when ((root["type"] as? JsonPrimitive)?.contentOrNull) {
            "baseline" -> {
                val projections = (root["value"] as? JsonObject)
                    ?.get("projections") as? JsonObject ?: return
                sessionModels.clear()
                projections.forEach { (sessionId, projection) ->
                    val values = (projection as? JsonObject)?.get("values") as? JsonObject
                    val model = modelOf(values?.get("modelSelection"))
                    if (model != null) sessionModels[sessionId] = model
                    if (sessionId == viewedSessionId) {
                        _todosFlow.value = parseTodos(values?.get("todos"))
                        _goalFlow.value = parseGoal(values?.get("goal"))
                        _queuedMessagesFlow.value =
                            parseQueue(values?.get("inbox"))
                    }
                }
            }
            "projection" -> {
                val sessionId = (root["sessionId"] as? JsonPrimitive)?.contentOrNull ?: return
                when ((root["key"] as? JsonPrimitive)?.contentOrNull) {
                    "modelSelection" -> {
                        val model = modelOf(root["value"])
                        if (model != null) sessionModels[sessionId] = model
                    }
                    "todos" -> if (sessionId == viewedSessionId) {
                        _todosFlow.value = parseTodos(root["value"])
                    }
                    "goal" -> if (sessionId == viewedSessionId) {
                        _goalFlow.value = parseGoal(root["value"])
                    }
                    "inbox" -> if (sessionId == viewedSessionId) {
                        _queuedMessagesFlow.value = parseQueue(root["value"])
                    }
                }
            }
        }
        _sessionModelFlow.value = sessionModels.toMap()
    }

    /** 解析官方 `inbox` 投影里的下一轮队列（next-turn）。 */
    private fun parseQueue(value: JsonElement?): List<com.deepseek.dsh.core.model.QueuedMessageInfo> {
        val rows = (value as? JsonObject)?.get("next-turn") as? JsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val blocks = obj["content"] as? JsonArray
            val text = blocks?.mapNotNull { block ->
                val part = block as? JsonObject ?: return@mapNotNull null
                if ((part["type"] as? JsonPrimitive)?.contentOrNull == "text") {
                    (part["text"] as? JsonPrimitive)?.contentOrNull
                } else {
                    null
                }
            }?.joinToString("").orEmpty()
            if (text.isBlank()) return@mapNotNull null
            com.deepseek.dsh.core.model.QueuedMessageInfo(id = id, text = text)
        }
    }

    /** 官方 `session/updateQueue`：编辑 / 移除 / 转成 steering。 */
    suspend fun updateQueue(
        hostId: String,
        sessionId: String,
        itemId: String,
        action: String,
        text: String? = null,
    ): Boolean {
        val args = buildJsonObject {
            put("request", buildJsonObject {
                put("sessionId", sessionId)
                put("itemId", itemId)
                put("action", buildJsonObject {
                    put("kind", action)
                    if (action == "edit") {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", text.orEmpty())
                            })
                        })
                    }
                })
            })
        }
        return rpcRaw(hostId, "session/updateQueue", args.toString())
            .getOrNull()
            ?.contains("\"ok\":true") == true
    }

    /** 解析官方 `todos` 投影值。 */    /** 解析官方 `todos` 投影值。 */
    private fun parseTodos(value: JsonElement?): List<com.deepseek.dsh.core.model.TodoEntryInfo> {
        val array = value as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val content = (obj["content"] as? JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            com.deepseek.dsh.core.model.TodoEntryInfo(
                content = content,
                status = (obj["status"] as? JsonPrimitive)?.contentOrNull ?: "pending",
            )
        }
    }

    /** 解析官方 `goal` 投影值。 */
    private fun parseGoal(value: JsonElement?): com.deepseek.dsh.core.model.GoalInfo? {
        val obj = value as? JsonObject ?: return null
        val goal = obj["goal"] as? JsonObject ?: return null
        val blocked = goal["blockedReason"] as? JsonObject
        return com.deepseek.dsh.core.model.GoalInfo(
            objective = (goal["objective"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            phase = (goal["phase"] as? JsonPrimitive)?.contentOrNull ?: "active",
            blockedReason = (blocked?.get("message") as? JsonPrimitive)?.contentOrNull,
            roundsStarted = obj["roundsStarted"]?.toString()?.trim('"')?.toIntOrNull() ?: 0,
            maxGoalRounds = goal["maxGoalRounds"]?.toString()?.trim('"')?.toIntOrNull() ?: 0,
        )
    }

    /**
     * 把 Host 的界面语言同步成 App 当前语言（官方 settings 的 `locale` 命名空间）。
     *
     * Host 生成的文案（命令描述、定时任务、插件设置页等）随它走，App 是英文时
     * Host 也切英文，避免中英混排；`preference` 缺省时官方会跟随浏览器语言。
     */
    suspend fun syncHostLocale(hostId: String, languageTag: String) {
        val locale = if (languageTag.lowercase().startsWith("zh")) "zh" else "en"
        val args = buildJsonObject {
            put("ns", "locale")
            put("patch", buildJsonObject { put("preference", locale) })
        }
        rpcRaw(hostId, "settings/update", args.toString())
    }

    /** 官方 `settings/describe` 里 `session-log-deepseek` 的上传开关。 */
    suspend fun fetchSessionLogUploadEnabled(hostId: String): Boolean? {
        val raw = rpcRaw(hostId, "settings/describe", "{}").getOrNull() ?: return null
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonObject ?: return null
        val namespaces = value["namespaces"] as? JsonArray ?: return null
        val ns = namespaces.mapNotNull { it as? JsonObject }
            .firstOrNull { (it["ns"] as? JsonPrimitive)?.contentOrNull == SESSION_LOG_NS }
            ?: return null
        val section = ns["value"] as? JsonObject ?: return null
        return (section["enabled"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
    }

    /** 官方 `settings/mutate`：把 session log 上传开关写到 Host。 */
    suspend fun setSessionLogUploadEnabled(hostId: String, enabled: Boolean): Boolean {
        val revision = sessionLogRevision(hostId)
        val args = buildJsonObject {
            put("ns", SESSION_LOG_NS)
            put("ops", buildJsonArray {
                add(buildJsonObject {
                    put("op", "set")
                    put("path", buildJsonArray { add(JsonPrimitive("enabled")) })
                    put("value", JsonPrimitive(enabled))
                })
            })
            if (revision != null) put("expectedRevision", revision)
        }
        return rpcRaw(hostId, "settings/mutate", args.toString())
            .getOrNull()?.contains("\"ok\":true") == true
    }

    private suspend fun sessionLogRevision(hostId: String): Int? {
        val raw = rpcRaw(hostId, "settings/describe", "{}").getOrNull() ?: return null
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonObject ?: return null
        val namespaces = value["namespaces"] as? JsonArray ?: return null
        return namespaces.mapNotNull { it as? JsonObject }
            .firstOrNull { (it["ns"] as? JsonPrimitive)?.contentOrNull == SESSION_LOG_NS }
            ?.let { (it["revision"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
    }

    /** 当前会话里的 workflow 运行（官方 tool-workflow 事件聚合）。 */
    private val _workflowRunsFlow =
        MutableStateFlow<Map<String, List<com.deepseek.dsh.core.model.WorkflowRunInfo>>>(
            emptyMap(),
        )
    val workflowRunsFlow:
        StateFlow<Map<String, List<com.deepseek.dsh.core.model.WorkflowRunInfo>>> =
        _workflowRunsFlow.asStateFlow()

    /** 某个会话的 workflow 运行列表（按创建顺序）。 */
    fun workflowRuns(sessionId: String): List<com.deepseek.dsh.core.model.WorkflowRunInfo> =
        _workflowRunsFlow.value[sessionId].orEmpty()

    /** 官方 `schedule/catalog`：Host 上所有计划任务（含会话绑定与状态）。 */
    private val _scheduleCatalogFlow =
        MutableStateFlow<List<com.deepseek.dsh.core.model.ScheduleInfo>>(emptyList())
    val scheduleCatalogFlow: StateFlow<List<com.deepseek.dsh.core.model.ScheduleInfo>> =
        _scheduleCatalogFlow.asStateFlow()

    /** 刷新计划任务目录（`schedule/catalog`，不激活会话）。 */
    suspend fun refreshScheduleCatalog(hostId: String) {
        val raw = rpcRaw(hostId, "schedule/catalog", "{}").getOrNull() ?: return
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        val items = (root["result"] as? JsonObject)?.get("value") as? JsonArray ?: return
        _scheduleCatalogFlow.value = items.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            com.deepseek.dsh.core.model.ScheduleInfo(
                id = id,
                title = (obj["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                prompt = (obj["prompt"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                scheduledAt = (obj["scheduledAt"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                sessionId = (obj["sessionId"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                status = (obj["status"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                afterSeconds = (obj["afterSeconds"] as? JsonPrimitive)?.contentOrNull
                    ?.toLongOrNull() ?: 0,
                everySeconds = (obj["everySeconds"] as? JsonPrimitive)?.contentOrNull
                    ?.toLongOrNull() ?: 0,
                time = (obj["time"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                weekdays = (obj["weekdays"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                }.orEmpty(),
                expression = (obj["expression"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }
    }

    /** 官方 `schedule/delete`：取消一条计划任务。 */
    suspend fun deleteSchedule(hostId: String, sessionId: String, id: String): Boolean {
        val args = buildJsonObject {
            put("request", buildJsonObject {
                put("sessionId", sessionId)
                put("id", id)
            })
        }
        val ok = rpcRaw(hostId, "schedule/delete", args.toString())
            .getOrNull()?.contains("\"deleted\":true") == true
        if (ok) refreshScheduleCatalog(hostId)
        return ok
    }

    /** 官方 `account/getUnnotifiedBonuses`：未展示过的奖励列表。 */
    suspend fun fetchUnnotifiedBonuses(
        hostId: String,
    ): Pair<String, List<com.deepseek.dsh.core.model.AccountBonusInfo>>? {
        val args = buildJsonObject {
            put("client", buildJsonObject {
                put("version", "0.2.1")
                put("locale", "zh")
                put("timezoneOffsetSeconds", 0)
            })
        }
        val raw = rpcRaw(hostId, "account/getUnnotifiedBonuses", args.toString())
            .getOrNull() ?: return null
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return null
        val value = (root["result"] as? JsonObject)?.get("value") as? JsonObject
            ?: return null
        val accountId = (value["accountId"] as? JsonPrimitive)?.contentOrNull ?: return null
        val bonuses = (value["bonuses"] as? JsonArray)?.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val orderId = (obj["orderId"] as? JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            com.deepseek.dsh.core.model.AccountBonusInfo(
                orderId = orderId,
                title = (obj["title"] as? JsonPrimitive)?.contentOrNull,
                message = (obj["message"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                expiresAt = (obj["expiresAt"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }.orEmpty()
        return accountId to bonuses
    }

    /** 官方 `account/ackBonusNotified`：标记一条奖励已展示。 */
    suspend fun ackBonusNotified(hostId: String, accountId: String, orderId: String): Boolean {
        val args = buildJsonObject {
            put("accountId", accountId)
            put("orderId", orderId)
            put("client", buildJsonObject {
                put("version", "0.2.1")
                put("locale", "zh")
                put("timezoneOffsetSeconds", 0)
            })
        }
        return rpcRaw(hostId, "account/ackBonusNotified", args.toString())
            .getOrNull()?.contains("\"ok\":true") == true
    }

    /** 下载当前会话日志 ZIP（官方 `/api/session.export`）。 */
    suspend fun exportSessionLog(hostId: String, sessionId: String): ByteArray? =
        when (val res = client.downloadSessionExport(hostId, sessionId)) {
            is RemoteResult.Success -> res.data
            is RemoteResult.Failure -> null
        }

    /** 拉一轮改动里某个文件的逐行对比（官方 `/api/changes.diff`）。 */
    suspend fun loadChangeDiff(
        hostId: String,
        sessionId: String,
        index: Int,
    ): com.deepseek.dsh.core.protocol.ChangesDiffInfo? {
        val seq = changesSeq
        if (seq <= 0L) return null
        val res = client.fetchChangesDiff(hostId, sessionId, seq, index)
        return (res as? RemoteResult.Success)?.data
    }

    /** 打开会话时订阅 `session/follow`，重放历史并恢复产物声明。 */
    fun watchSession(hostId: String, sessionId: String) {
        client.followSession(hostId, sessionId)
        scope.launch(Dispatchers.IO) { refreshMessageFeedback(hostId, sessionId) }
    }

    fun markSessionViewed(sessionId: String?) {
        if (sessionId != viewedSessionId) {
            _streamingTextFlow.value = ""
            _streamingReasoningFlow.value = ""
            _todosFlow.value = emptyList()
            _goalFlow.value = null
            _deliverablesFlow.value = emptyList()
            _changesFlow.value = null
            changesSeq = 0L
            _queuedMessagesFlow.value = emptyList()
            _messageFeedbackFlow.value = emptyMap()
            _messageImagesFlow.value = emptyMap()
            _workflowRunsFlow.value = emptyMap()
        }
        viewedSessionId = sessionId
        if (sessionId != null) unreadSessionIdsFlow.value = unreadSessionIdsFlow.value - sessionId
    }

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
        displayName: String? = null,
    ): Result<Unit> {
        val result = when (val res = client.selectModel(hostId, sessionId, model, provider, reasoningEffort)) {
            is RemoteResult.Success -> Result.success(Unit)
            is RemoteResult.Failure -> Result.failure(IllegalStateException(res.message))
        }
        // 只有 Host 接受后才写本地缓存：失败时界面保持原来的模型，并给出错误。
        if (result.isSuccess) {
            dao.updateSessionModel(hostId, sessionId, displayName ?: model)
        }
        return result
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
            val cleaned = entities.filterNot { entity ->
                // 兼容旧版本已落库的注入消息（Host 的 runtime-context / time-context）。
                entity.role == "USER" && isInjectedContextMessage(entity.contentJson.orEmpty())
            }
            // 兼容旧版本「乐观插库 + Host 回显」造成的同一句话两条记录：只保留
            // 相邻、同内容、1.5s 内的第一条。
            var previous: MessageEntity? = null
            cleaned.filter { entity ->
                val duplicate = previous != null &&
                    entity.role == "USER" &&
                    previous!!.role == "USER" &&
                    previous!!.contentJson == entity.contentJson &&
                    kotlin.math.abs(
                        (entity.createdAt ?: 0L) - (previous!!.createdAt ?: 0L),
                    ) < 1_500
                previous = entity
                !duplicate
            }.map { entity ->
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

    /** Host 注入的上下文消息（不是用户输入）：只给模型，不上聊天记录。 */
    private fun isInjectedContextMessage(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("Current runtime context") ||
            trimmed.startsWith("Time sampled while preparing turn") ||
            trimmed.startsWith("Browser time zone for this request:")
    }

    suspend fun sendMessage(
        hostId: String,
        sessionId: String,
        content: String,
        images: List<com.deepseek.dsh.core.protocol.PromptImagePart> = emptyList(),
    ): ChatMessage {
        val userMsg = ChatMessage(
            hostId = hostId,
            sessionId = sessionId,
            messageId = "msg_user_${System.currentTimeMillis()}",
            role = MessageRole.USER,
            content = content,
            createdAt = System.currentTimeMillis()
        )
        dao.touchSession(hostId, sessionId, System.currentTimeMillis())
        // 官方 inbox 语义：对话记录只认 Host 回放的 user/message（claim 后的记录）。
        // 这里绝不能乐观插库：Host 回显用的是自己的 messageId，两边插入会形成两条
        // 内容相同的用户消息（发送文字重复的根因）。UI 的即时气泡由调用方的
        // pendingMessages 承担，Host 回显到达后按内容去重。

        scope.launch(Dispatchers.IO) {
            // 队列消息不做乐观插入；follow 会带回 claim 后的记录。运行中发送失败也
            // 只写错误行，避免残留一条从未进入对话的“用户消息”。
            val result = client.sendMessage(hostId, sessionId, content, images)
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
                            contentJson = "⚠️ 发送失败：${result.message}",
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
                        if (event.sessionId == viewedSessionId && !event.running) {
                            _streamingTextFlow.value = ""
                            _streamingReasoningFlow.value = ""
                        }
                        if (!event.running && event.sessionId != viewedSessionId) {
                            unreadSessionIdsFlow.value = unreadSessionIdsFlow.value + event.sessionId
                        }
                    }
                    is RemoteEvent.MessageDelta -> {
                        runningSessionIdsFlow.value = runningSessionIdsFlow.value + event.sessionId
                        if (event.sessionId == viewedSessionId) {
                            _streamingTextFlow.value = _streamingTextFlow.value + event.delta
                        }
                    }
                    is RemoteEvent.WorkflowRunUpdated -> {
                        if (event.sessionId == viewedSessionId) {
                            val current = _workflowRunsFlow.value.toMutableMap()
                            val runs = current[event.sessionId].orEmpty().toMutableList()
                            val index = runs.indexOfFirst { it.runId == event.run.runId }
                            if (index >= 0) runs[index] = event.run else runs += event.run
                            current[event.sessionId] = runs
                            _workflowRunsFlow.value = current
                        }
                    }
                    is RemoteEvent.UserImages -> {
                        if (event.sessionId == viewedSessionId) {
                            _messageImagesFlow.value =
                                _messageImagesFlow.value + (event.messageId to event.images)
                        }
                    }
                    is RemoteEvent.ChangesAnnounced -> {
                        if (event.sessionId == viewedSessionId) {
                            changesSeq = event.seq
                            val sid = event.sessionId
                            scope.launch(Dispatchers.IO) {
                                val res = client.fetchChangesSummary(hostId, sid, event.seq)
                                val summary = (res as? RemoteResult.Success)?.data
                                if (summary != null && viewedSessionId == sid) {
                                    _changesFlow.value = summary
                                }
                            }
                        }
                    }
                    is RemoteEvent.DeliverablesPresented -> {
                        if (event.sessionId == viewedSessionId) {
                            // 同一路径去重后按最新顺序排列。
                            val merged = (_deliverablesFlow.value + event.files)
                                .distinctBy { it.path }
                            _deliverablesFlow.value = merged
                        }
                    }
                    is RemoteEvent.ReasoningDelta -> {
                        runningSessionIdsFlow.value = runningSessionIdsFlow.value + event.sessionId
                        if (event.sessionId == viewedSessionId) {
                            _streamingReasoningFlow.value =
                                _streamingReasoningFlow.value + event.delta
                        }
                    }
                    is RemoteEvent.MessageDone -> {
                        // 运行态只由 turn/start / turn/end 决定：这里再标记会导致
                        // turn/end 之后又被一条 message 拉回 running（停止按钮卡住）。
                        if (event.message.sessionId == viewedSessionId &&
                            event.message.role == MessageRole.ASSISTANT
                        ) {
                            _streamingTextFlow.value = ""
                            _streamingReasoningFlow.value = ""
                        }
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
                        // DeepSeek 账号会话过期：交给 UI 清登录态。
                        "deepseek-account/session-expired" -> _accountExpiredFlow.tryEmit(Unit)
                        // 模型需要重新登录账号：弹官方登录入口。
                        "deepseek-account/model-sign-in-required" ->
                            _modelSignInRequiredFlow.tryEmit(Unit)
                        // 会话活动/错误：刷新会话列表状态。
                        "api-session/activity", "api-session/error" -> refreshSessions(hostId)
                        // 权限预置目录变化：刷新设置文档缓存。
                        "permission-presets/catalog-changed" -> syncRemoteSettings(hostId)
                        // 斜杠命令注册变化：刷新命令目录（当前会话）。
                        "commands/change" -> {
                            val sid = viewedSessionId
                            if (sid != null) refreshHostCommands(hostId, sid)
                        }
                        // 会话的 agent 预置或目标状态变化：会话摘要会变。
                        "agent-preset/selected", "goal/activation-changed" ->
                            refreshSessions(hostId)
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
        // 官方 waterfall：approval/request 交给 UI 弹窗；其余（如提问）回 next。
        if (synchronized(watchedWaterfallHosts) { watchedWaterfallHosts.add(hostId) }) {
            scope.launch(Dispatchers.IO) {
                client.forwardedWaterfalls(hostId).collect { waterfall ->
                    if (waterfall.event == "approval/request") {
                        val existing = _pendingWaterfallApprovalsFlow.value
                        val kept = existing.filter { item -> item.eventId != waterfall.eventId }
                        _pendingWaterfallApprovalsFlow.value = kept + waterfall
                    } else if (waterfall.event == "user-questions/request") {
                        val existing = _pendingQuestionsFlow.value
                        val kept = existing.filter { item -> item.eventId != waterfall.eventId }
                        _pendingQuestionsFlow.value = kept + waterfall
                    } else {
                        client.answerWaterfall(hostId, waterfall.eventId, null)
                    }
                }
            }
        }
        // 官方 session/control：全部会话投影（模型选择等）的基线 + 增量。
        if (synchronized(watchedControlHosts) { watchedControlHosts.add(hostId) }) {
            scope.launch(Dispatchers.IO) {
                client.sessionControl(hostId).collect { frame -> applyControlFrame(frame) }
            }
        }
        // 官方 workspace/follow：工作区/会话树的实时基线 + 增量，刷新会话列表。
        if (synchronized(watchedWorkspaceHosts) { watchedWorkspaceHosts.add(hostId) }) {
            scope.launch(Dispatchers.IO) {
                client.workspaceFollow(hostId).collect { frame ->
                    applyWorkspaceFrame(frame)
                    workspaceRefreshJob?.cancel()
                    workspaceRefreshJob = scope.launch {
                        kotlinx.coroutines.delay(250)
                        refreshSessions(hostId)
                    }
                }
            }
        }
        // 官方插件安装进度：install-state 相位 + install-log 输出尾部。
        if (synchronized(watchedEventPayloadHosts) { watchedEventPayloadHosts.add(hostId) }) {
            scope.launch(Dispatchers.IO) {
                client.forwardedEventPayloads(hostId).collect { forwarded ->
                    val requestId = activeInstallRequestId ?: return@collect
                    val payload = forwarded.args.firstOrNull() as? JsonObject
                        ?: return@collect
                    val payloadRequestId = (payload["requestId"] as? JsonPrimitive)
                        ?.contentOrNull
                    if (payloadRequestId != null && payloadRequestId != requestId) {
                        return@collect
                    }
                    when (forwarded.event) {
                        "plugin-manager/install-state" -> {
                            val phase = (payload["phase"] as? JsonPrimitive)
                                ?.contentOrNull ?: "installing"
                            val attempt = payload["attempt"] as? JsonObject
                            val registry = (attempt?.get("registry") as? JsonPrimitive)
                                ?.contentOrNull
                            val index = (attempt?.get("index") as? JsonPrimitive)
                                ?.intOrNull ?: 1
                            val total = (attempt?.get("total") as? JsonPrimitive)
                                ?.intOrNull ?: 1
                            _installProgressFlow.value = PluginInstallProgressInfo(
                                phase = phase,
                                registry = registry,
                                index = index,
                                total = total,
                                logTail = installLogTail,
                            )
                        }
                        "plugin-manager/install-log" -> {
                            val text = (payload["text"] as? JsonPrimitive)
                                ?.contentOrNull.orEmpty()
                            if (text.isNotBlank()) {
                                installLogTail = (installLogTail + text)
                                    .takeLast(400)
                                _installProgressFlow.value =
                                    _installProgressFlow.value?.copy(logTail = installLogTail)
                            }
                        }
                    }
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

/** Host 插件安装进度（官方 `plugin-manager/install-state` + install-log 尾部）。 */
data class PluginInstallProgressInfo(
    val phase: String,
    val registry: String?,
    val index: Int,
    val total: Int,
    val logTail: String,
)

/** 官方 workspace 投影。 */
data class HostWorkspaceInfo(
    val id: String,
    val path: String,
    val title: String,
    val sessionIds: List<String>,
)

/** 官方 session-log 上传配置的命名空间。 */
private const val SESSION_LOG_NS = "session-log-deepseek"
