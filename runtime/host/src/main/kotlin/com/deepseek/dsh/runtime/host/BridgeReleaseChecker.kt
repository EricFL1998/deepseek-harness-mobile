package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 查询 bridge 插件仓库的最新发布版本。
 *
 * 插件仓库的 release 不带资产，tag `v<version>` 本身就是「最新版本」的声明。
 * 这里是唯一的读取点：Android 用它决定设置页要不要提示更新，rootfs 流水线
 * 用它决定打包哪个版本。
 *
 * 查询失败不是错误状态 —— 离线时不该把「查不到」当成「已是最新」，也不该
 * 因此报错打断用户；返回 null，调用方按「无法判断」处理。
 */
class BridgeReleaseChecker(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 读取插件仓库最新 release 的版本。
     *
     * 结果会短暂缓存：设置页每次刷新都会问一次，而未授权的 GitHub API 每小时
     * 只有 60 次额度；没有缓存时反复打开面板就会把额度用完。
     *
     * 先问 API（能过滤草稿与预发布）；API 不可用（离线、限流、404）时退回
     * `releases/latest` 的 302 跳转——那个入口没有 API 额度限制，取到的 tag 就是
     * 「最新发布」。两条路都失败才返回 null。
     * @param repository GitHub 仓库地址，例如 `https://github.com/owner/repo`。
     * @return 版本号（去掉 `v` 前缀）；离线、404 或格式异常时返回 null。
     */
    suspend fun latestVersion(repository: String): String? = withContext(Dispatchers.IO) {
        val slug = repositorySlug(repository) ?: return@withContext null
        val now = System.currentTimeMillis()
        cached(slug, now)?.let { return@withContext it.version }
        val version = fetchLatestFromApi(slug) ?: fetchLatestFromRedirect(slug)
        remember(slug, version, now)
        version
    }

    /** 问 releases API；调用方负责缓存。 */
    private fun fetchLatestFromApi(slug: String): String? {
        val url = "https://api.github.com/repos/$slug/releases?per_page=10"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "dsh-android")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                // 列表按发布时间倒序；取第一个非草稿、非预发布的 tag。
                json.parseToJsonElement(body).jsonArray
                    .asSequence()
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { release ->
                        release["draft"]?.jsonPrimitive?.content?.toBoolean() != true &&
                            release["prerelease"]?.jsonPrimitive?.content?.toBoolean() != true
                    }
                    ?.get("tag_name")
                    ?.jsonPrimitive
                    ?.content
                    ?.removePrefix("v")
                    ?.takeIf { isPlainVersion(it) }
            }
        } catch (_: Exception) {
            // 离线或限流：交给跳转兜底。
            null
        }
    }

    /**
     * 用 `https://github.com/<slug>/releases/latest` 的 302 兜底。
     *
     * 不跟随跳转（否则会下载整个 release 页面），只读 `Location` 里的 tag。
     * 没有 release 时 GitHub 跳到 `/releases`，那里没有 tag，返回 null。
     */
    private fun fetchLatestFromRedirect(slug: String): String? {
        val request = Request.Builder()
            .url("https://github.com/$slug/releases/latest")
            .header("User-Agent", "dsh-android")
            .build()
        return try {
            // 复用同一个连接池与调度器，但这次不跟随跳转。
            val client = httpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code !in 300..399) return null
                val location = response.header("Location") ?: return null
                val resolved = request.url.resolve(location) ?: return null
                val segments = resolved.pathSegments
                val tag = segments.getOrNull(segments.indexOf("tag") + 1)
                    ?.removePrefix("v")
                    ?.takeIf { isPlainVersion(it) }
                tag
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从仓库地址里取出 `owner/repo`。
     * @param repository 完整的 GitHub 地址，允许带 `.git` 后缀与末尾斜杠。
     * @return `owner/repo`，无法解析时为 null。
     */
    private fun repositorySlug(repository: String): String? {
        val trimmed = repository.trim().removeSuffix("/").removeSuffix(".git")
        val match = GITHUB_REPO.find(trimmed) ?: return null
        return "${match.groupValues[1]}/${match.groupValues[2]}"
    }

    private companion object {
        private val GITHUB_REPO = Regex("""github\.com[/:]([^/]+)/([^/]+)$""")

        /** 发布 tag 只接受纯数字三段；带后缀的 tag 不是本插件发布的版本标记。 */
        private val PLAIN_VERSION = Regex("""^\d+\.\d+\.\d+$""")

        /** 是否是插件发布使用的版本形态。 */
        private fun isPlainVersion(value: String): Boolean = PLAIN_VERSION.matches(value)

        /** 成功结果的有效期：发布是人工动作，5 分钟内不会变。 */
        private const val SUCCESS_TTL_MS = 5 * 60 * 1000L

        /** 失败结果的有效期：留一分钟，避免限流期间把每次刷新都变成一次请求。 */
        private const val FAILURE_TTL_MS = 60 * 1000L

        /** 一次查询结果：`version` 为 null 表示当时没查到。 */
        private data class CacheEntry(val version: String?, val fetchedAt: Long)

        private val cache = HashMap<String, CacheEntry>()

        /** 取仍然有效的缓存；过期或没有就返回 null。 */
        private fun cached(slug: String, now: Long): CacheEntry? = synchronized(cache) {
            val entry = cache[slug] ?: return null
            val ttl = if (entry.version == null) FAILURE_TTL_MS else SUCCESS_TTL_MS
            entry.takeIf { now - it.fetchedAt <= ttl }
        }

        /** 记下一次查询结果。 */
        private fun remember(slug: String, version: String?, now: Long) {
            synchronized(cache) { cache[slug] = CacheEntry(version, now) }
        }
    }
}
