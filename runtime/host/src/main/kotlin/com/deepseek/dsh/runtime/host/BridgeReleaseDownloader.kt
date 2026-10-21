package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * bridge 插件发布包（GitHub tag 的 zipball）的下载器。
 *
 * 走 App 自己的 HTTP 栈，而不是在 Host 里跑 `git`/`pnpm`：
 * Host 跑在 proot 里，Android 的 DNS 解析在 proot 内不可用（`getent` 返回
 * EAI_AGAIN），GitHub 与 npm registry 都解析不到；App 进程用的是系统解析器，
 * 同一网络下可以正常下载。插件发布带没有资产，一个源码 zipball 就够。
 */
class BridgeReleaseDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    /**
     * 下载一个 tag 的源码归档。
     * @param repository GitHub 仓库地址，例如 `https://github.com/owner/repo`。
     * @param version 已发布的插件版本（不带 `v` 前缀）。
     * @param onProgress 下载进度（0..1）与步骤文案。
     * @return 归档字节；超出体积上限、HTTP 失败或版本号非法时抛异常。
     */
    suspend fun download(
        repository: String,
        version: String,
        onProgress: (Float, String) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = archiveUrl(repository, version)
            ?: throw IllegalArgumentException("安装源不是可识别的 GitHub 仓库：$repository")
        onProgress(0f, "连接 GitHub…")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "dsh-android")
            .header("Accept", "application/zip")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("下载插件归档失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("插件归档响应没有内容")
            val contentLength = body.contentLength()
            if (contentLength > BridgePluginArchive.MAX_ARCHIVE_BYTES) {
                throw IllegalStateException("插件归档过大：${contentLength} 字节")
            }
            val output = ByteArrayOutputStream(
                contentLength.takeIf { it in 1..BridgePluginArchive.MAX_ARCHIVE_BYTES }?.toInt() ?: 64 * 1024
            )
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var received = 0L
            var lastReported = -1
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    received += read
                    if (received > BridgePluginArchive.MAX_ARCHIVE_BYTES) {
                        throw IllegalStateException("插件归档超过 32MB 上限")
                    }
                    output.write(buffer, 0, read)
                    if (contentLength > 0) {
                        val percent = ((received * 100) / contentLength).toInt().coerceIn(0, 100)
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent / 100f, "下载插件归档 $percent%")
                        }
                    }
                }
            }
            onProgress(1f, "插件归档已下载（${output.size()} 字节）")
            output.toByteArray()
        }
    }

    companion object {
        /** 已发布版本只接受纯数字三元组，避免把任意字符串拼进 URL 路径。 */
        private val VERSION_PATTERN = Regex("""^\d+\.\d+\.\d+$""")

        /** 从仓库地址里取出 `owner/repo`；不是 GitHub 地址时为 null。 */
        fun repositorySlug(repository: String): String? {
            val trimmed = repository.trim().removeSuffix("/").removeSuffix(".git")
            val match = GITHUB_REPOSITORY.find(trimmed) ?: return null
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            if (owner.isBlank() || repo.isBlank()) return null
            return "$owner/$repo"
        }

        /**
         * 构造 tag 归档地址。
         *
         * 直接用 codeload 而不是 GitHub API：API 有未授权的速率限制，且
         * `/tarball` 只会多一次重定向；codeload 的归档地址是稳定入口。
         * @param repository GitHub 仓库地址。
         * @param version 已发布版本。
         * @return 归档 URL；仓库或版本不可用时为 null。
         */
        fun archiveUrl(repository: String, version: String): String? {
            if (!VERSION_PATTERN.matches(version)) return null
            val slug = repositorySlug(repository) ?: return null
            return "https://codeload.github.com/$slug/zip/refs/tags/v$version"
        }

        private val GITHUB_REPOSITORY = Regex("""github\.com[/:]([^/]+)/([^/]+)$""")
    }
}
