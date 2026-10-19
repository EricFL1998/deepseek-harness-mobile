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
     * @param repository GitHub 仓库地址，例如 `https://github.com/owner/repo`。
     * @return 版本号（去掉 `v` 前缀）；离线、404 或格式异常时返回 null。
     */
    suspend fun latestVersion(repository: String): String? = withContext(Dispatchers.IO) {
        val slug = repositorySlug(repository) ?: return@withContext null
        val url = "https://api.github.com/repos/$slug/releases?per_page=10"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "dsh-android")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
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
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            // 离线或限流：交给调用方按「无法判断」处理。
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
    }
}
