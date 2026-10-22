package com.deepseek.dsh.core.bridge

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * provider 语义的客户端门面。
 *
 * [BridgeClient] 只懂线协议；本类把四个已知 provider 的操作包成具名方法，
 * 并在 provider 缺失时返回 [BridgeResult.Failure]（`provider/unavailable` 或
 * `provider/unknown`），让调用方降级到 Host 原生清单 —— 从不伪造数据。
 */
class BridgeRegistry(
    private val client: BridgeClient,
    private val json: kotlinx.serialization.json.Json = BridgeJson.instance,
) {
    /** 用一个已带会话的 OkHttp 客户端构造门面。 */
    constructor(
        baseUrl: String,
        httpClient: okhttp3.OkHttpClient,
    ) : this(BridgeClient(baseUrl, httpClient))

    /** 最近一次协商到的 provider 目录，供 UI 判断降级路径。 */
    @Volatile
    var manifest: BridgeManifest? = null
        private set

    /** 协商 provider 目录并缓存。 */
    suspend fun negotiate(forceRefresh: Boolean = false): BridgeResult<BridgeManifest> {
        val result = client.manifest(forceRefresh)
        if (result is BridgeResult.Success) manifest = result.value
        return result
    }

    /** `client-slots` 是否可用。 */
    fun hasClientSlots(): Boolean = manifest?.serves(BridgeProtocol.PROVIDER_CLIENT_SLOTS, 1) == true

    /** `package-inventory` 是否可用。 */
    fun hasPackageInventory(): Boolean =
        manifest?.serves(BridgeProtocol.PROVIDER_PACKAGE_INVENTORY, 1) == true

    /** `client-assets` 是否可用。 */
    fun hasClientAssets(): Boolean = manifest?.serves(BridgeProtocol.PROVIDER_CLIENT_ASSETS, 1) == true

    /**
     * 读取正在运行的插件版本（manifest 的 `pluginVersion`）。
     *
     * 旧插件不上报该字段，此时返回 null；调用方应退回落盘版本，而不是把
     * 「没上报」当成「没安装」。
     * @param forceRefresh 是否丢弃 manifest 缓存重新协商。
     * @return 插件版本；协商失败或 Host 未上报时为 null。
     */
    suspend fun pluginVersion(forceRefresh: Boolean = false): String? {
        if (forceRefresh || manifest == null) {
            val negotiated = negotiate(forceRefresh)
            if (negotiated is BridgeResult.Failure) return null
        }
        return manifest?.pluginVersion?.takeIf { it.isNotBlank() }
    }

    // ── client-assets ────────────────────────────────────────────────────────

    /** 读取 Host 注入给官方 shell 的 boot manifest。 */
    suspend fun bootGraph(): BridgeResult<BridgeBootGraph> = query(
        provider = BridgeProtocol.PROVIDER_CLIENT_ASSETS,
        op = "boot-graph",
    ) { json.decodeFromString(BridgeBootGraph.serializer(), it.toString()) }

    /** 读取一个客户端 entry 的 bundle 源（受 Host 字节上限约束）。 */
    suspend fun moduleSource(id: String): BridgeResult<BridgeModuleSource> = query(
        provider = BridgeProtocol.PROVIDER_CLIENT_ASSETS,
        op = "module-source",
        args = BridgeArgs.of("id", id),
    ) { json.decodeFromString(BridgeModuleSource.serializer(), it.toString()) }

    /** 读取一个包的 locale 词典。 */
    suspend fun locale(namespace: String, locale: String? = null): BridgeResult<BridgeLocale> = query(
        provider = BridgeProtocol.PROVIDER_CLIENT_ASSETS,
        op = "locale",
        args = if (locale == null) BridgeArgs.of("namespace", namespace)
        else BridgeArgs.of("namespace", namespace, "locale", locale),
    ) { json.decodeFromString(BridgeLocale.serializer(), it.toString()) }

    // ── client-slots ─────────────────────────────────────────────────────────

    /**
     * 读取 slot 注册列表；`slot` 为 null 时返回全部。
     *
     * @param locale 客户端界面语言（BCP-47，例如 `zh-Hans-CN`）。插件在 bridge 里
     *   注册的 `label` 通常是一个 `t('key')` thunk，只有给定语言才能解析成与 Web
     *   界面一致的文案；不传时 bridge 用自身配置的默认语言。
     */
    suspend fun slotRegistrations(
        slot: String? = null,
        locale: String? = null,
    ): BridgeResult<BridgeSlotList> {
        val args = buildJsonObject {
            slot?.let { put("slot", JsonPrimitive(it)) }
            locale?.takeIf { it.isNotBlank() }?.let { put("locale", JsonPrimitive(it)) }
        }
        return query(
            provider = BridgeProtocol.PROVIDER_CLIENT_SLOTS,
            op = "list",
            args = args,
        ) { json.decodeFromString(BridgeSlotList.serializer(), it.toString()) }
    }

    /** 读取一条注册的完整元数据。 */
    suspend fun describeSlot(slot: String, id: String): BridgeResult<BridgeSlotRegistration> = query(
        provider = BridgeProtocol.PROVIDER_CLIENT_SLOTS,
        op = "describe",
        args = BridgeArgs.of("slot", slot, "id", id),
    ) { json.decodeFromString(BridgeSlotRegistration.serializer(), it.toString()) }

    /** 渲染一个组件的元素树。 */
    suspend fun renderTree(
        componentId: String,
        props: JsonObject = buildJsonObject { },
    ): BridgeResult<BridgeRenderTree> = query(
        provider = BridgeProtocol.PROVIDER_CLIENT_SLOTS,
        op = "render-tree",
        args = BridgeArgs.ofObject("props", props) + BridgeArgs.of("componentId", componentId),
    ) { json.decodeFromString(BridgeRenderTree.serializer(), it.toString()) }

    /** 把一个组件的图形转换成独立 SVG 文档。 */
    suspend fun iconSvg(componentId: String, size: Int = 24): BridgeResult<BridgeIconSvg> = query(
        provider = BridgeProtocol.PROVIDER_CLIENT_SLOTS,
        op = "icon-svg",
        args = BridgeArgs.ofInt("size", size) + BridgeArgs.of("componentId", componentId),
    ) { json.decodeFromString(BridgeIconSvg.serializer(), it.toString()) }

    /**
     * 把一次原生交互（点击/输入/开关/提交）回传给插件。
     *
     * App 不执行插件 JS：事件到达 Host 后被转成 `ui/<componentId>/<event>`
     * 总线消息，并给手机排一个 `client/refresh`，让组件树重新拉取。
     */
    suspend fun dispatchUiEvent(
        componentId: String,
        event: String,
        payload: JsonObject = buildJsonObject { },
    ): BridgeResult<Boolean> = query(
        provider = BridgeProtocol.PROVIDER_CLIENT_SLOTS,
        op = "dispatch",
        args = BridgeArgs.of("componentId", componentId, "event", event) +
            BridgeArgs.ofObject("payload", payload),
    ) { element ->
        // bridge 用 handled 说明「插件 Host 半边是否真的执行了」；未执行时客户端要兜底。
        ((element as? kotlinx.serialization.json.JsonObject)
            ?.get("handled") as? kotlinx.serialization.json.JsonPrimitive)
            ?.content?.toBooleanStrictOrNull() ?: false
    }

    // ── package-inventory ────────────────────────────────────────────────────

    /** 读取 Host 的 bundle 清单，可按名字收窄。 */
    /**
     * 读取 Host 能枚举的、映射到原生界面的插件贡献。
     *
     * 与 `slotRegistrations` 的区别：那条路要评估客户端 bundle，预构建的 web 客户端
     * 没有可评估的 entry；这条路读的是 Host 自己的服务端数据，因此在这些 Host 上仍然可用。
     */
    /** 读取一份插件设置文档（表单 + 当前值 + 版本）。 */
    suspend fun settingsDescribe(ns: String): BridgeResult<BridgeSettingsDocument> = query(
        provider = BridgeSettingsProvider,
        op = "describe",
        args = BridgeArgs.of("ns", ns),
    ) { json.decodeFromString(BridgeSettingsDocument.serializer(), it.toString()) }

    // ── native-plugins ───────────────────────────────────────────────────────

    /** 一个 JS 插件有没有配套的原生伴侣 APK；有则返回它的声明。 */
    suspend fun nativeCompanionFor(pluginId: String): BridgeResult<BridgeNativeCompanion?> = query(
        provider = BridgeProtocol.PROVIDER_NATIVE_PLUGINS,
        op = "for",
        args = BridgeArgs.of("pluginId", pluginId),
    ) { element ->
        json.decodeFromString(BridgeNativeCompanionResult.serializer(), element.toString()).companion
    }

    /** 全部已声明的原生伴侣（商店「原生插件」tab 的可装推荐）。 */
    suspend fun nativeCompanions(): BridgeResult<BridgeNativeCompanionList> = query(
        provider = BridgeProtocol.PROVIDER_NATIVE_PLUGINS,
        op = "list",
    ) { json.decodeFromString(BridgeNativeCompanionList.serializer(), it.toString()) }

    /** 回写编辑后的字段；`expectedRevision` 不匹配时 Host 会拒绝。 */
    suspend fun settingsUpdate(
        ns: String,
        patch: JsonObject,
        expectedRevision: Int,
    ): BridgeResult<BridgeSettingsWriteResult> = query(
        provider = BridgeSettingsProvider,
        op = "update",
        args = BridgeArgs.ofObject("patch", patch) +
            BridgeArgs.ofInt("expectedRevision", expectedRevision) +
            BridgeArgs.of("ns", ns),
    ) { json.decodeFromString(BridgeSettingsWriteResult.serializer(), it.toString()) }

    /** 取走 Host 排队的瞬态消息；每次调用即清空。 */
    suspend fun pendingEvents(): BridgeResult<BridgeTransientEventList> = query(
        provider = BridgeEventsProvider,
        op = "pending",
    ) { json.decodeFromString(BridgeTransientEventList.serializer(), it.toString()) }

    suspend fun hostSlotSurfaces(locale: String? = null): BridgeResult<BridgeSlotSurfaceList> =
        query(
            provider = BridgeHostSlotsProvider,
            op = "list",
            args = locale?.takeIf { it.isNotBlank() }
                ?.let { BridgeArgs.of("locale", it) } ?: BridgeArgs.none(),
        ) { json.decodeFromString(BridgeSlotSurfaceList.serializer(), it.toString()) }

    suspend fun bundles(
        name: String? = null,
        locale: String? = null,
    ): BridgeResult<BridgeInventory> = query(
        provider = BridgeProtocol.PROVIDER_PACKAGE_INVENTORY,
        op = "bundles",
        args = (if (name == null) BridgeArgs.none() else BridgeArgs.of("name", name)) +
            (locale?.takeIf { it.isNotBlank() }?.let { BridgeArgs.of("locale", it) }
                ?: BridgeArgs.none()),
    ) { json.decodeFromString(BridgeInventory.serializer(), it.toString()) }

    /** 读取 Host 的 Loader row 清单。 */
    suspend fun rows(): BridgeResult<BridgeInventory> = query(
        provider = BridgeProtocol.PROVIDER_PACKAGE_INVENTORY,
        op = "rows",
    ) { json.decodeFromString(BridgeInventory.serializer(), it.toString()) }

    /** 读取一个 bundle 或 row 的 `meta.icon` data URL。 */
    suspend fun metaIcon(name: String): BridgeResult<BridgeMetaIcon> = query(
        provider = BridgeProtocol.PROVIDER_PACKAGE_INVENTORY,
        op = "meta-icon",
        args = BridgeArgs.of("name", name),
    ) { json.decodeFromString(BridgeMetaIcon.serializer(), it.toString()) }

    // ── rpc ──────────────────────────────────────────────────────────────────

    /** 调用一个 Host 白名单内的 remote 方法。 */
    suspend fun invoke(
        namespace: String,
        method: String,
        args: JsonObject = buildJsonObject { },
    ): BridgeResult<JsonElement> = query(
        provider = BridgeProtocol.PROVIDER_RPC,
        op = "invoke",
        args = BridgeArgs.of("namespace", namespace, "method", method) + BridgeArgs.ofObject("args", args),
    ) { element ->
        val record = element.jsonObject
        record["result"] ?: JsonPrimitive("")
    }

    /** 统一的 query 包装：provider 缺失时直接失败，不触碰网络。 */
    private suspend fun <T> query(
        provider: String,
        op: String,
        args: Map<String, JsonElement> = emptyMap(),
        decode: (JsonElement) -> T,
    ): BridgeResult<T> {
        if (manifest == null) {
            val negotiated = negotiate()
            if (negotiated is BridgeResult.Failure) return negotiated
        }
        val known = manifest?.provider(provider)
            ?: return BridgeResult.Failure(
                BridgeErrorCode.PROVIDER_UNKNOWN,
                "this Host does not offer the $provider provider",
            )
        if (op !in known.capabilities) {
            return BridgeResult.Failure(
                BridgeErrorCode.OP_UNKNOWN,
                "provider $provider does not offer $op",
            )
        }
        return client.query(provider, op, args, decode)
    }
}

/**
 * 把 slot 注册列表按 slot 名字分组，保持 Host 给的顺序。
 *
 * 客户端按 slot 取用（`plugins.item`、`settings.section` 等），因此分组是
 * 唯一需要的组织方式；不再引入第二层分类表。
 */
fun BridgeSlotList.bySlot(): Map<String, List<BridgeSlotRegistration>> =
    registrations.groupBy { it.slot }

/** 元素树里某个 prop 的字符串值，缺失时为 null。 */
fun BridgeRenderNode.stringProp(name: String): String? {
    val value = props[name] ?: return null
    return runCatching { value.jsonPrimitive.content }.getOrNull()
}

/** 元素树的子节点里所有文本，按顺序拼接。 */
fun BridgeRenderNode.textContent(): String = when (kind) {
    "text" -> value.orEmpty()
    "element" -> children.joinToString(separator = "") { it.textContent() }
    else -> ""
}

/** 元素树是否是宿主标签（而非组件或 fragment）。 */
fun BridgeRenderNode.isHostTag(): Boolean =
    kind == "element" && type != null && !type.startsWith("component:") && type != "fragment"

/** 按类型名在树中查找第一个元素。 */
fun BridgeRenderNode.findByType(typeName: String): BridgeRenderNode? {
    if (this.type == typeName) return this
    for (child in children) {
        val found = child.findByType(typeName)
        if (found != null) return found
    }
    return null
}

/** 从 `svg` 文档中取出 `viewBox`，缺失时回落到 24×24。 */
fun BridgeIconSvg.viewBoxOrNull(): String? {
    val svg = svg ?: return null
    val match = VIEW_BOX.find(svg) ?: return null
    return match.groupValues.getOrNull(1)
}

private val VIEW_BOX = Regex("""viewBox="([^"]+)"""")

/** 把元素树的 JSON 数组读成节点列表，供自定义解码使用。 */
internal fun JsonElement.asNodeList(): List<BridgeRenderNode> = jsonArray.map {
    BridgeJson.instance.decodeFromString(BridgeRenderNode.serializer(), it.toString())
}

internal const val BridgeHostSlotsProvider = "host-slots"
internal const val BridgeSettingsProvider = "settings"
internal const val BridgeEventsProvider = "events"
