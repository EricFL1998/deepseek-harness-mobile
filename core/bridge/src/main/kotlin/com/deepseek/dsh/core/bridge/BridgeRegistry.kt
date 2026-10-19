package com.deepseek.dsh.core.bridge

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

    /** 读取 slot 注册列表；`slot` 为 null 时返回全部。 */
    suspend fun slotRegistrations(slot: String? = null): BridgeResult<BridgeSlotList> = query(
        provider = BridgeProtocol.PROVIDER_CLIENT_SLOTS,
        op = "list",
        args = if (slot == null) BridgeArgs.none() else BridgeArgs.of("slot", slot),
    ) { json.decodeFromString(BridgeSlotList.serializer(), it.toString()) }

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

    // ── package-inventory ────────────────────────────────────────────────────

    /** 读取 Host 的 bundle 清单，可按名字收窄。 */
    /**
     * 读取 Host 能枚举的、映射到原生界面的插件贡献。
     *
     * 与 `slotRegistrations` 的区别：那条路要评估客户端 bundle，预构建的 web 客户端
     * 没有可评估的 entry；这条路读的是 Host 自己的服务端数据，因此在这些 Host 上仍然可用。
     */
    suspend fun hostSlotSurfaces(): BridgeResult<BridgeSlotSurfaceList> = query(
        provider = BridgeHostSlotsProvider,
        op = "list",
    ) { json.decodeFromString(BridgeSlotSurfaceList.serializer(), it.toString()) }

    suspend fun bundles(name: String? = null): BridgeResult<BridgeInventory> = query(
        provider = BridgeProtocol.PROVIDER_PACKAGE_INVENTORY,
        op = "bundles",
        args = if (name == null) BridgeArgs.none() else BridgeArgs.of("name", name),
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
