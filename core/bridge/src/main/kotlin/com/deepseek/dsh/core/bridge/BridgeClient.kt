package com.deepseek.dsh.core.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * bridge/v1 客户端。
 *
 * 只做三件事：协商 manifest、发一次 `query`、把结果解码成 [BridgeResult]。
 * 它不认识任何具体 provider —— provider 语义由调用方按 id 分支，未知 id 直接忽略，
 * 因此 Host 增加 provider 不需要客户端升级。
 *
 * Cookie 与鉴权沿用 Host 会话：调用方传入的 [OkHttpClient] 带着同一个 CookieJar，
 * bridge 从不自行铸造凭据。
 */
class BridgeClient(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = BridgeJson.instance,
) {
    private val origin: HttpUrl? = baseUrl.toHttpUrlOrNull()

    /** manifest 缓存：一次会话内 Host 的 provider 目录不会变，除非重连。 */
    @Volatile
    private var cachedManifest: BridgeManifest? = null

    /** 并发协商单飞：多个页面同时启动时只打一次 manifest。 */
    private val manifestLock = Mutex()

    /** 读取（或复用）provider 目录。 */
    suspend fun manifest(forceRefresh: Boolean = false): BridgeResult<BridgeManifest> {
        val cached = cachedManifest
        if (!forceRefresh && cached != null) return BridgeResult.Success(cached)
        return manifestLock.withLock {
            // 等锁期间可能已有别的协程完成协商，再检查一次。
            cachedManifest?.takeIf { !forceRefresh }?.let { return@withLock BridgeResult.Success(it) }
            val url = urlFor(BridgeProtocol.MANIFEST_PATH)
                ?: return@withLock BridgeResult.Failure(
                    BridgeErrorCode.TRANSPORT_UNREACHABLE,
                    "invalid bridge base URL: $baseUrl",
                )
            val request = Request.Builder().url(url).get().build()
            execute(request) { body ->
                val manifest = json.decodeFromString(BridgeManifest.serializer(), body)
                if (!manifest.isCompatible()) {
                    return@execute BridgeResult.Failure(
                        BridgeErrorCode.PROVIDER_VERSION_MISMATCH,
                        "Host speaks bridge ${manifest.bridgeVersion}, client speaks ${BridgeProtocol.BRIDGE_VERSION}",
                    )
                }
                cachedManifest = manifest
                BridgeResult.Success(manifest)
            }
        }
    }

    /**
     * 发送一次 provider 查询。
     *
     * 成功时返回解码后的数据；失败时返回稳定错误码，绝不返回部分结果。
     */
    suspend fun <T> query(
        provider: String,
        op: String,
        args: Map<String, JsonElement> = emptyMap(),
        decode: (JsonElement) -> T,
    ): BridgeResult<T> {
        val url = urlFor(BridgeProtocol.QUERY_PATH)
            ?: return BridgeResult.Failure(
                BridgeErrorCode.TRANSPORT_UNREACHABLE,
                "invalid bridge base URL: $baseUrl",
            )
        val payload = json.encodeToString(
            BridgeQuery.serializer(),
            BridgeQuery(provider = provider, op = op, args = args),
        )
        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request) { body ->
            val response = json.decodeFromString(BridgeQueryResponse.serializer(), body)
            if (!response.ok) {
                val error = response.error
                return@execute BridgeResult.Failure(
                    error?.code ?: BridgeErrorCode.INTERNAL_ERROR,
                    error?.message ?: "the Host refused the query without a reason",
                )
            }
            val data = response.data
                ?: return@execute BridgeResult.Failure(
                    BridgeErrorCode.INTERNAL_ERROR,
                    "$provider/$op answered ok with no data",
                )
            BridgeResult.Success(decode(data), response.warnings)
        }
    }

    /** 清空 manifest 缓存，供重连后重新协商。 */
    fun invalidate() {
        cachedManifest = null
    }

    /** 把一条路由拼到 base 上，返回 null 表示 base 本身不可解析。 */
    private fun urlFor(path: String): HttpUrl? {
        val base = origin ?: return null
        // base 可能带路径前缀（远程网关），因此按段拼接而不是字符串相加。
        return base.newBuilder()
            .encodedPath(path)
            .query(null)
            .fragment(null)
            .build()
    }

    private suspend fun <T> execute(
        request: Request,
        decode: (String) -> BridgeResult<T>,
    ): BridgeResult<T> = withContext(Dispatchers.IO) {
        var last: BridgeResult<T>? = null
        // bridge 在回环上，Host 刚重启/端口切换时会有一次瞬时失败；只对
        // TRANSPORT_UNREACHABLE 重试一次。解码错误、404、协议错误不重试。
        for (attempt in 0..1) {
            val result = executeOnce(request, decode)
            last = result
            val retryable = result is BridgeResult.Failure &&
                result.code == BridgeErrorCode.TRANSPORT_UNREACHABLE
            if (!retryable || attempt == 1) return@withContext result
            delay(90)
        }
        last ?: BridgeResult.Failure(
            BridgeErrorCode.INTERNAL_ERROR,
            "bridge request produced no result",
        )
    }

    private suspend fun <T> executeOnce(
        request: Request,
        decode: (String) -> BridgeResult<T>,
    ): BridgeResult<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // 非 2xx 也带 JSON 错误体；能解码就用 Host 的稳定码，否则报传输失败。
                    val decoded = runCatching { decode(body) }.getOrNull()
                    if (decoded is BridgeResult.Failure) return@use decoded
                    // 404 与「连不上」是两件事：Host 在回应，只是没有这条路由，
                    // 也就是 bridge 插件没装。分开报，UI 才能给出可操作的指引。
                    if (response.code == 404) {
                        return@use BridgeResult.Failure(
                            BridgeErrorCode.PROVIDER_UNKNOWN,
                            "the Host serves no bridge route (HTTP 404): the bridge plugin is not installed",
                        )
                    }
                    return@use BridgeResult.Failure(
                        BridgeErrorCode.TRANSPORT_UNREACHABLE,
                        "bridge answered HTTP ${response.code}",
                    )
                }
                runCatching { decode(body) }.getOrElse { error ->
                    println("DSH-BRIDGE-DECODE len=" + body.length + " err=" + error.message)
                    BridgeResult.Failure(
                        BridgeErrorCode.TRANSPORT_DECODE_FAILED,
                        "cannot decode the bridge response: ${error.message}",
                    )
                }
            }
        } catch (error: IOException) {
            BridgeResult.Failure(
                BridgeErrorCode.TRANSPORT_UNREACHABLE,
                error.message ?: "the Host is unreachable",
            )
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 默认的 OkHttp：短超时，因为 bridge 在回环上回答。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // 整体上限，避免 Host 卡住时占着页面协程。
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }
}

/** 一次 bridge 调用的结果：成功带数据与 warnings，失败带稳定码。 */
sealed class BridgeResult<out T> {
    /** 成功。`warnings` 是 Host 报告的非致命说明，客户端应展示或记录。 */
    data class Success<T>(
        val value: T,
        val warnings: List<String> = emptyList(),
    ) : BridgeResult<T>()

    /** 失败。`code` 是稳定错误码，客户端按码决定降级路径。 */
    data class Failure(
        val code: String,
        val message: String,
    ) : BridgeResult<Nothing>()

    /** 成功时的值，失败时为 null。 */
    fun valueOrNull(): T? = (this as? Success)?.value

    /** 失败时的码，成功时为 null。 */
    fun errorCodeOrNull(): String? = (this as? Failure)?.code
}

/** 共享的 JSON 配置：忽略未知字段，让 Host 的增量字段不破坏已发布客户端。 */
object BridgeJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
    }
}

/** 构造 `args` 的便捷工具，避免调用点手写 `buildJsonObject`。 */
object BridgeArgs {
    /** 无参数。 */
    fun none(): Map<String, JsonElement> = emptyMap()

    /** 单个字符串参数。 */
    fun of(key: String, value: String): Map<String, JsonElement> =
        buildJsonObject { put(key, value) }

    /** 两个字符串参数。 */
    fun of(key: String, value: String, key2: String, value2: String): Map<String, JsonElement> =
        buildJsonObject {
            put(key, value)
            put(key2, value2)
        }

    /** 单个整数参数。 */
    fun ofInt(key: String, value: Int): Map<String, JsonElement> =
        buildJsonObject { put(key, value) }

    /** 单个对象参数（例如 `render-tree` 的 props）。 */
    fun ofObject(key: String, value: JsonObject): Map<String, JsonElement> =
        buildJsonObject { put(key, value) }

    /** 把任意 JsonElement 读成对象，供解码器使用。 */
    fun objectOf(element: JsonElement): JsonObject = element.jsonObject
}
