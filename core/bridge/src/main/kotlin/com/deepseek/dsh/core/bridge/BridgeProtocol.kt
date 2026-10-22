package com.deepseek.dsh.core.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `bridge/v1` 线协议模型。
 *
 * 这些类型是 Host 侧 `dsh-mobile-bridge` 插件契约的客户端镜像：字段名与
 * `docs/protocol.md` 一一对应。未知字段由 [BridgeJson] 忽略，因此 Host 增加
 * 字段不会破坏已发布的客户端；未知 provider id 同样按「忽略」处理。
 */
object BridgeProtocol {
    /** 协议版本；Host 报出未知主版本时客户端应拒绝。 */
    const val BRIDGE_VERSION: String = "1.0.0"

    /** 所有路由共用的前缀。 */
    const val BASE_PATH: String = "/dsh-mobile/bridge/v1"

    /** provider 目录路由。 */
    const val MANIFEST_PATH: String = "$BASE_PATH/manifest"

    /** 查询路由。 */
    const val QUERY_PATH: String = "$BASE_PATH/query"

    /** `client-assets` provider id。 */
    const val PROVIDER_CLIENT_ASSETS: String = "client-assets"

    /** `client-slots` provider id。 */
    const val PROVIDER_CLIENT_SLOTS: String = "client-slots"

    /** `package-inventory` provider id。 */
    const val PROVIDER_PACKAGE_INVENTORY: String = "package-inventory"

    /** `native-plugins` provider id（原生伴侣清单）。 */
    const val PROVIDER_NATIVE_PLUGINS: String = "native-plugins"

    /** `rpc` provider id。 */
    const val PROVIDER_RPC: String = "rpc"
}

/** Host 侧 `GET /manifest` 的响应体。 */
@Serializable
data class BridgeManifest(
    val bridgeVersion: String = "",
    /** 插件自身的版本；Host 未上报（旧插件）时为 null。 */
    val pluginVersion: String? = null,
    val dshVersion: String? = null,
    val providers: List<BridgeProviderEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    /** 按 id 查找一个 provider。 */
    fun provider(id: String): BridgeProviderEntry? = providers.firstOrNull { it.id == id }

    /** 该 provider 是否以指定版本提供服务。 */
    fun serves(id: String, version: Int): Boolean = provider(id)?.version == version

    /** 客户端是否理解 Host 报出的协议主版本。 */
    fun isCompatible(): Boolean =
        bridgeVersion.substringBefore('.').toIntOrNull() ==
            BridgeProtocol.BRIDGE_VERSION.substringBefore('.').toIntOrNull()
}

/** manifest 中的一个 provider。 */
@Serializable
data class BridgeProviderEntry(
    val id: String = "",
    val version: Int = 0,
    val capabilities: List<String> = emptyList(),
    val readOnly: Boolean = false,
    val authorization: String? = null,
)

/** `POST /query` 的请求体。 */
@Serializable
data class BridgeQuery(
    val provider: String,
    val op: String,
    val args: Map<String, JsonElement> = emptyMap(),
)

/** `POST /query` 的响应体：成功与失败互斥，永不部分成功。 */
@Serializable
data class BridgeQueryResponse(
    val ok: Boolean = false,
    val provider: String? = null,
    val version: Int = 0,
    val data: JsonElement? = null,
    val warnings: List<String> = emptyList(),
    val error: BridgeQueryError? = null,
)

/** 一次失败的稳定错误码与诊断文本。 */
@Serializable
data class BridgeQueryError(
    val code: String = "",
    val message: String = "",
    val details: JsonElement? = null,
)

/** 稳定错误码。客户端按码映射自己的文案，从不解析 message。 */
object BridgeErrorCode {
    const val REQUEST_MALFORMED: String = "request/malformed"
    const val REQUEST_UNSUPPORTED_METHOD: String = "request/unsupported-method"
    const val PROVIDER_UNKNOWN: String = "provider/unknown"
    const val PROVIDER_UNAVAILABLE: String = "provider/unavailable"
    const val PROVIDER_VERSION_MISMATCH: String = "provider/version-mismatch"
    const val OP_UNKNOWN: String = "op/unknown"
    const val ARGS_INVALID: String = "args/invalid"
    const val BUDGET_EXCEEDED: String = "budget/exceeded"
    const val AUTH_REQUIRED: String = "auth/required"
    const val INTERNAL_ERROR: String = "internal/error"

    /** 客户端本地产生的错误码：Host 不可达。 */
    const val TRANSPORT_UNREACHABLE: String = "transport/unreachable"

    /** 客户端本地产生的错误码：响应无法解析。 */
    const val TRANSPORT_DECODE_FAILED: String = "transport/decode-failed"
}

/** `client-assets` / `boot-graph` 的数据。 */
@Serializable
data class BridgeBootGraph(
    val rev: String = "",
    val release: BridgeBootRelease? = null,
    val entries: List<BridgeBootEntry> = emptyList(),
    val batches: List<BridgeBootBatch> = emptyList(),
)

/** Host 发布标识。 */
@Serializable
data class BridgeBootRelease(
    val version: String = "",
    val commit: String? = null,
)

/** boot manifest 中的一行。 */
@Serializable
data class BridgeBootEntry(
    val id: String = "",
    val url: String = "",
    val rev: String = "",
    val inject: List<String> = emptyList(),
    val immediately: Boolean = false,
    val external: List<String> = emptyList(),
)

/** 一个初始 combo 脚本描述。 */
@Serializable
data class BridgeBootBatch(
    val phase: String = "",
    val url: String = "",
    val rev: String = "",
    val entries: List<String> = emptyList(),
)

/** `client-assets` / `module-source` 的数据。 */
@Serializable
data class BridgeModuleSource(
    val id: String = "",
    val path: String = "",
    val bytes: Long = 0,
    val complete: Boolean = false,
    val sha256: String = "",
    val source: String = "",
)

/** `client-assets` / `locale` 的数据。 */
@Serializable
data class BridgeLocale(
    val namespace: String = "",
    val locale: String = "",
    val available: List<String> = emptyList(),
    val dictionary: Map<String, String> = emptyMap(),
)

/** 一条 slot 注册（已渲染成数据的形态）。 */
@Serializable
data class BridgeSlotRegistration(
    val slot: String = "",
    val id: String? = null,
    val key: String? = null,
    val order: Int? = null,
    val label: String? = null,
    val priority: Int? = null,
    /** bridge 判定的原生落点，例如 `drawer` / `conversation` / `settings.page`。 */
    val placement: String? = null,
    @SerialName("package") val packageName: String? = null,
    val locale: String? = null,
    /** 注册来源：`static`（静态提取）或 `evaluated`（VM 求值）；旧插件不上报。 */
    val origin: String? = null,
    val render: BridgeSlotRender? = null,
) {
    /** 客户端用于 dispatch 的稳定身份：`id` 优先，keyed slot 回落到 `key`。 */
    val identity: String get() = id ?: key ?: render?.componentId ?: slot

    /** 组件身份，`render-tree` / `icon-svg` 的入参。 */
    val componentId: String? get() = render?.componentId
}

/** 一条注册的组件身份。 */
@Serializable
data class BridgeSlotRender(
    val kind: String = "",
    val componentId: String = "",
) {
    val isFactory: Boolean get() = kind == "factory"
}

/** `client-slots` / `list` 的数据。 */
@Serializable
data class BridgeSlotList(
    val slot: String? = null,
    val registrations: List<BridgeSlotRegistration> = emptyList(),
    val emptySlots: List<String> = emptyList(),
)

/** 序列化后的元素树节点：元素、文本或不可解析的占位。 */
@Serializable
data class BridgeRenderNode(
    val kind: String = "",
    val type: String? = null,
    val key: String? = null,
    val props: Map<String, JsonElement> = emptyMap(),
    val children: List<BridgeRenderNode> = emptyList(),
    val value: String? = null,
    val reason: String? = null,
)

/** `client-slots` / `render-tree` 的数据。 */
@Serializable
data class BridgeRenderTree(
    val componentId: String = "",
    val tree: BridgeRenderNode? = null,
)

/** `client-slots` / `icon-svg` 的数据。 */
@Serializable
data class BridgeIconSvg(
    val componentId: String = "",
    val size: Int = 0,
    val svg: String? = null,
)

/** `package-inventory` 的一个 bundle。 */
@Serializable
data class BridgeBundle(
    val name: String = "",
    val official: Boolean = false,
    val optional: Boolean = false,
    val removable: Boolean = false,
    val enabled: Boolean = false,
    val installed: Boolean = false,
    val availability: String = "",
    val version: String? = null,
    val source: String? = null,
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val rows: List<BridgeBundleRow> = emptyList(),
    val errorCode: String? = null,
    /** bridge 下发的官方插件页策略（旧版 bridge 缺省时回落 App 侧）。 */
    val profileRoot: Boolean = false,
    val pinned: Boolean = false,
    val order: Int = 0,
    val hidden: Boolean = false,
    val market: Boolean = false,
    val locked: Boolean = false,
)

/** bundle 的一条 row。 */
@Serializable
data class BridgeBundleRow(
    val rowId: String = "",
    val moduleName: String = "",
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val enabled: Boolean? = null,
    /** Loader 入口 id；行没被 Host 解析成入口时为空，也就不能拨动它的开关。 */
    val entryId: String? = null,
)

/** `package-inventory` 的一条 Loader row。 */
@Serializable
data class BridgeInventoryRow(
    val entryId: String = "",
    val moduleName: String = "",
    val enabled: Boolean = false,
    val fiberPhase: String? = null,
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
)

/** `package-inventory` / `bundles` 与 `/rows` 的数据。 */
@Serializable
data class BridgeInventory(
    val bundles: List<BridgeBundle> = emptyList(),
    val rows: List<BridgeInventoryRow> = emptyList(),
    val managementAvailable: Boolean = false,
)

/** `package-inventory` / `meta-icon` 的数据。 */
@Serializable
data class BridgeMetaIcon(
    val name: String = "",
    val icon: String? = null,
)

/** `native-plugins`：一个原生伴侣插件的声明（JS 插件的 package.json 里 `dsh.native`）。 */
@Serializable
data class BridgeNativeCompanion(
    val pluginId: String = "",
    val name: String? = null,
    val apiVersion: Int? = null,
    val minAppVersion: Int? = null,
    val capabilities: List<String> = emptyList(),
    val storeUrl: String? = null,
)

/** `native-plugins` / `for` 的数据。 */
@Serializable
data class BridgeNativeCompanionResult(
    val companion: BridgeNativeCompanion? = null,
)

/** `native-plugins` / `list` 的数据。 */
@Serializable
data class BridgeNativeCompanionList(
    val companions: List<BridgeNativeCompanion> = emptyList(),
)

/**
 * `host-slots/list` 的一条条目。
 *
 * `slot` 是**原生**位置（例如 `settings.page`），不是浏览器 slot 名：bridge 已经把
 * 服务端能枚举的贡献归好类，客户端直接照位置渲染，不再自己推导。
 */
@Serializable
data class BridgeSlotSurface(
    val slot: String,
    val id: String,
    val title: String,
    val description: String? = null,
    val module: String? = null,
)

/** `host-slots/list` 的响应体。 */
@Serializable
data class BridgeSlotSurfaceList(
    val surfaces: List<BridgeSlotSurface> = emptyList(),
)

/** Host 报告的一个已配置密钥位置（值永远不下发）。 */
@Serializable
data class BridgeSecretSlot(
    val path: List<String> = emptyList(),
    val set: Boolean = false,
)

/** `settings/describe` 的响应体。 */
@Serializable
data class BridgeSettingsDocument(
    val ns: String,
    val revision: Int = 0,
    val applies: String? = null,
    val schema: JsonObject = JsonObject(emptyMap()),
    val value: JsonObject = JsonObject(emptyMap()),
    /** 密钥字段位置；值为空时客户端据此显示「已配置」而不回显密钥。 */
    val secrets: List<BridgeSecretSlot> = emptyList(),
)

/** `settings/update` 的响应体。 */
@Serializable
data class BridgeSettingsWriteResult(
    val ns: String,
    val updated: Boolean = false,
)

/** `events/pending` 里的一条瞬态消息。 */
@Serializable
data class BridgeTransientEvent(
    val kind: String,
    val text: String,
    val seq: Long = 0,
)

/** `events/pending` 的响应体。 */
@Serializable
data class BridgeTransientEventList(
    val events: List<BridgeTransientEvent> = emptyList(),
)
