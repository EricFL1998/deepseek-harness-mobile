package com.deepseek.dsh.runtime.host

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * rootfs 清单的实时来源。
 *
 * 清单不能随 APK 打包：它带着下载地址与 SHA-256，每次 rootfs 发版都会变，
 * 内置的那份会立刻过期，于是新装用户去下一个已经不存在（或校验不过）的地址。
 *
 * 因此清单在运行时从 rootfs 仓库的最新 release 拉取。APK 只保留一个地址常量，
 * 它不会随发版失效。
 *
 * 三级来源，逐级降级：
 * 1. **网络**：最新 release 的 `rootfs-manifest.json`，成功后写入缓存；
 * 2. **缓存**：上一次成功拉取的清单，离线时仍然可用；
 * 3. **内置兜底**：仅用于「连一次都没成功过」的极端情况，并会明确标记为兜底。
 */
class RootfsManifestSource(
    private val context: Context,
    private val layout: RuntimeLayout,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 清单来源，供 UI 与日志说明当前数字可信到什么程度。 */
    enum class Origin {
        /** 刚从网络拉到。 */
        NETWORK,

        /** 上一次成功拉取的缓存。 */
        CACHE,

        /** APK 内置兜底；可能已过期。 */
        BUNDLED,
    }

    /** 一次解析结果：清单本身与它的来源。 */
    data class Resolved(
        val manifest: RootfsManifest,
        val origin: Origin,
    )

    /**
     * 取得当前可用的清单。
     *
     * 联网失败不抛异常：清单决定的是「能不能装」，而离线时缓存或兜底通常仍能
     * 让已装用户继续用。调用方通过 [Resolved.origin] 判断可信度。
     *
     * DSH 段与 APK 内置清单不一致的远端/缓存清单会被忽略：APK 的 rootfs 补丁
     * 是按内置清单的 DSH 版本整文件替换的，跨版本套用会让运行树版本混杂。
     * @param forceRefresh 跳过缓存，强制重新拉取。
     * @return 解析结果；连兜底都没有时为 null（APK 打包损坏）。
     */
    suspend fun resolve(forceRefresh: Boolean = false): Resolved? = withContext(Dispatchers.IO) {
        val expectedDsh = bundledDshVersion()
        if (!forceRefresh) {
            cachedManifest()
                ?.takeIf { compatible(it, expectedDsh) }
                ?.let { return@withContext Resolved(it, Origin.CACHE) }
        }
        fetchRemote()?.let { remote ->
            writeCache(remote)
            if (compatible(remote, expectedDsh)) {
                return@withContext Resolved(remote, Origin.NETWORK)
            }
            android.util.Log.w(
                TAG,
                "忽略 DSH 版本不兼容的 rootfs 清单: ${remote.runtimeVersion}（期望 dsh${expectedDsh ?: "?"}）",
            )
        }
        // 网络失败或远端不兼容：退回上一次成功的兼容缓存。
        cachedManifest()
            ?.takeIf { compatible(it, expectedDsh) }
            ?.let { return@withContext Resolved(it, Origin.CACHE) }
        bundledManifest()?.let { return@withContext Resolved(it, Origin.BUNDLED) }
        null
    }

    /** APK 内置清单的 DSH 版本；缺失或格式不符时为 null。 */
    fun bundledDshVersion(): String? =
        bundledManifest()?.runtimeVersion?.let(RootfsNaming::dshVersion)

    /** 这份清单的 DSH 版本是否与当前 APK 的补丁目标一致。 */
    private fun compatible(manifest: RootfsManifest, expectedDsh: String?): Boolean =
        RootfsNaming.isDshCompatible(manifest.runtimeVersion, expectedDsh)

    /**
     * 取得清单，失败时抛异常。
     *
     * 安装流程需要「装或不装」的确定答案，不能拿一个可能过期的兜底去下载。
     * @param forceRefresh 跳过缓存。
     * @return 清单。
     * @throws IllegalStateException 当网络、缓存、兜底全部不可用时。
     */
    suspend fun requireManifest(forceRefresh: Boolean = true): RootfsManifest =
        resolve(forceRefresh)?.manifest
            ?: error("无法获取 rootfs 清单：网络不可用，且没有本地缓存")

    /** 清单缓存文件；与 rootfs 安装标记同目录，避免污染 rootfs 本身。 */
    private fun cacheFile(): File = File(layout.baseDir, "runtime/${RootfsNaming.MANIFEST_CACHE_FILE}")

    /** 读缓存。 */
    private fun cachedManifest(): RootfsManifest? {
        val file = cacheFile()
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString(RootfsManifest.serializer(), file.readText())
                .takeIf { it.runtimeVersion.isNotBlank() }
        }.getOrNull()
    }

    /** 写缓存；失败不影响本次使用。 */
    private fun writeCache(manifest: RootfsManifest) {
        runCatching {
            val file = cacheFile()
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(RootfsManifest.serializer(), manifest))
        }
    }

    /** 从 rootfs 仓库的最新 release 拉清单。 */
    private fun fetchRemote(): RootfsManifest? {
        val request = Request.Builder()
            .url(LATEST_MANIFEST_URL)
            .header("User-Agent", "dsh-android")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null
                json.decodeFromString(RootfsManifest.serializer(), body)
                    .takeIf { it.runtimeVersion.isNotBlank() }
            }
        }.getOrNull()
    }

    /** APK 内置的兜底清单；缺失或损坏时为 null。 */
    private fun bundledManifest(): RootfsManifest? = runCatching {
        val raw = context.assets.open(RootfsNaming.MANIFEST_ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString(RootfsManifest.serializer(), raw)
            .takeIf { it.runtimeVersion.isNotBlank() }
    }.getOrNull()

    private companion object {
        /** Logcat tag。 */
        const val TAG = "DshRootfsManifest"

        /**
         * 最新 release 的清单地址。
         *
         * `releases/latest/download/...` 由 GitHub 维护重定向，因此这个常量永远
         * 指向最新发布，不需要随发版更新 —— 这正是它可以留在 APK 里的原因。
         */
        const val LATEST_MANIFEST_URL =
            "https://github.com/EricFL1998/deepseek-harness-mobile-rootfs/releases/latest/download/rootfs-manifest.json"
    }
}
