package com.dsh.mobile.dsh_plugin_demo

import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Demo 插件的 Host RPC 契约（JS 半边见 `samples/demo-js`）。
 *
 * 这里刻意不引入任何新协议：方法名、信封形状、`componentId` 都与真实插件一致。
 * 原生端只做两件事——调既有 RPC 读/写 Host 状态，或把「哪个控件的哪个动作」经
 * `dispatchEvent` 回传；业务状态永远在 Host。
 *
 * 数据流：
 *
 * ```
 * 页面启动 → callRpc("demo.status") → 渲染
 * 点「服务端 +1」→ dispatchEvent(... "submit", {actionId:"bump"})
 *   → Host 动作改状态 → bridge 排 client/refresh → 页面重拉 demo.status
 * ```
 *
 * Demo 的所有数值都是虚构的；这里只有解析与调用逻辑，没有账号、网络或凭证。
 */
object DemoRpc {
    /** 伴生 APK 的插件 id（manifest `dsh.plugin.id`）。 */
    const val PLUGIN_ID = "demo.native-ui"

    /** 配对的 JS 插件包名（manifest `dsh.plugin.jsPackage`）。 */
    const val JS_PACKAGE = "dsh-plugin-demo"

    /** 设置页组件身份：`<JS 包名>#<settings.section id>`。 */
    const val PAGE_COMPONENT_ID = "dsh-plugin-demo#demo"

    /** 输入栏 dock 组件身份：`<JS 包名>#<dock 注册 id>`。 */
    const val DOCK_COMPONENT_ID = "dsh-plugin-demo#demo-dock"

    /** Host RPC 方法名；与 `samples/demo-js/lib/constants.js` 的 RPC_ENDPOINTS 对应。 */
    const val STATUS = "demo.status"
    const val CATALOG = "demo.catalog"
    const val PING = "demo.ping"
    const val BUMP = "demo.bump"
    const val RESET = "demo.reset"
    const val ECHO = "demo.echo"

    /** 原生控件回传事件时使用的 event 名（bridge 允许 press/input/toggle/submit）。 */
    const val EVENT_SUBMIT = "submit"

    /**
     * 解信封并取成功值。
     *
     * @return 成功时的 `result.value`；业务失败、传输失败或形状不符时返回 null。
     */
    fun value(body: JsonElement?): JsonObject? {
        val result = envelopeResult(body) ?: return null
        val ok = (result["ok"] as? JsonPrimitive)?.booleanOrNull ?: true
        if (!ok) return null
        return result["value"] as? JsonObject
    }

    /**
     * 解信封并取可读错误。
     *
     * @param fallback 信封里没有 message 时的兜底文案。
     */
    fun errorMessage(body: JsonElement?, fallback: String): String {
        val result = envelopeResult(body) ?: return fallback
        val error = result["error"] as? JsonObject ?: return fallback
        return (error["message"] as? JsonPrimitive)?.contentOrNull ?: fallback
    }

    /** 取出 `result`；兼容 Host 直接返回 `{ ok, value }` 的简化形状。 */
    private fun envelopeResult(body: JsonElement?): JsonObject? {
        val root = body as? JsonObject ?: return null
        return (root["result"] as? JsonObject) ?: root
    }

    /** 解析 `demo.status` 的 value。 */
    fun statusOf(value: JsonObject): DemoStatus = DemoStatus(
        greeting = value.str("greeting"),
        count = value.int("count"),
        lastAction = value.str("lastAction"),
        updatedAt = value.long("updatedAt"),
        uptimeMs = value.long("uptimeMs"),
    )

    /** 解析 `demo.catalog` 的 value。 */
    fun catalogOf(value: JsonObject): DemoCatalog {
        val registrations = (value["registrations"] as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let { row ->
                DemoCatalogEntry(
                    slot = row.str("slot"),
                    id = row.str("id"),
                    componentId = row.str("componentId"),
                    nativeSlot = row.str("nativeSlot"),
                    purpose = row.str("purpose"),
                )
            }
        }
        val endpoints = (value["endpoints"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return DemoCatalog(registrations = registrations, endpoints = endpoints)
    }

    /** 本地时间 `HH:mm:ss`，用于展示 Host 记录的更新时间。 */
    fun formatClock(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))
    }

    /** 把 JSON 数组安全地转成 List。 */
    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}

/** `demo.status` 的展示模型。 */
data class DemoStatus(
    val greeting: String,
    val count: Int,
    val lastAction: String,
    val updatedAt: Long,
    val uptimeMs: Long,
)

/** `demo.catalog` 里的一条 JS 注册落点。 */
data class DemoCatalogEntry(
    val slot: String,
    val id: String,
    val componentId: String,
    val nativeSlot: String,
    val purpose: String,
)

/** `demo.catalog` 的展示模型。 */
data class DemoCatalog(
    val registrations: List<DemoCatalogEntry>,
    val endpoints: List<String>,
)

/**
 * 把回调式 [PluginRpcHost.callRpc] 包成 suspend，返回原始响应体。
 *
 * Host 返回的是完整信封（`{ type, rpcId, result }`）；解析交给 [DemoRpc.value] /
 * [DemoRpc.errorMessage]，调用点只关心「成功还是失败」。写成顶层扩展函数，
 * 同包内的页面与落点贡献都能直接用，不需要再包一层。
 */
internal suspend fun PluginRpcHost.demoCall(
    method: String,
    payload: JsonObject = JsonObject(emptyMap()),
): JsonElement? = suspendCancellableCoroutine { continuation ->
    callRpc(method, payload) { result ->
        continuation.resume(result.getOrNull())
    }
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
