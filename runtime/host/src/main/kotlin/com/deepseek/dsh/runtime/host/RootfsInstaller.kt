package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

/** 安装进度：0..1 + 面向用户的步骤文案。 */
data class InstallProgress(
    val step: String,
    val progress: Float
)

/**
 * `rootfs-manifest.json` 的契约：真正的镜像地址/校验值由发布流水线写入，
 * App 只负责下载、校验、解包与安装标记。
 */
@Serializable
data class RootfsManifest(
    val runtimeVersion: String = "",
    val rootfsName: String = RootfsNaming.ARCHIVE_PREFIX,
    val releaseTag: String = "",
    val baseDistro: String = "",
    val libc: String = "",
    val pageSize: String = "",
    val bundledPackages: List<String> = emptyList(),
    val archiveFormat: String = "tar.gz",
    /** ABI -> assets 内自带的 rootfs 归档路径（可选，主要用于离线调试）。 */
    val assetArchives: Map<String, String> = emptyMap(),
    /** ABI -> 归档 SHA-256。 */
    val assetSha256: Map<String, String> = emptyMap(),
    /** ABI -> 首次下载地址（正式发布使用，App 不再内置大体积 rootfs）。 */
    val downloadUrls: Map<String, String> = emptyMap(),
    /** ABI -> 下载归档 SHA-256。 */
    val downloadSha256: Map<String, String> = emptyMap(),
    val downloadUrl: String = "",
    val sha256: String = "",
    val archiveSizeBytes: Long = 0L,
    val prootLibrary: String = "libproot.so",
    /**
     * 镜像是否在 rootfs 构建阶段就已经应用了 Android 运行时补丁。
     *
     * 为 true 时不再要求镜像的 DSH 段与本 APK 的整文件补丁目标一致：镜像里的
     * 上游文件已经是「最新 DSH + 补丁」的组合，App 只需跳过运行时覆盖。
     */
    val androidPatched: Boolean = false,
)

/**
 * rootfs 安装流水线（官方 §4.2/§4.7）：
 * 下载（可跳过）→ SHA-256 校验 → 安全解包（防路径穿越）→ 原子替换 → 工具链门禁 → 安装标记。
 *
 * 开发期可把镜像放到 `cache/rootfs-local.tar.gz`（无需网络），manifest 的
 * `sha256` 留空时跳过校验但会记录告警。
 */
class RootfsInstaller(
    private val layout: RuntimeLayout,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseManifest(rawJson: String): RootfsManifest = json.decodeFromString(rawJson)

    /**
     * 尝试联网检查 deepseek-harness-mobile-rootfs 最新 release 中的 rootfs-manifest.json。
     * 若网络不通或未发布，则平滑 fallback 返回本地 bundled manifest。
     */
    suspend fun resolveLatestManifest(bundledManifest: RootfsManifest): RootfsManifest = withContext(Dispatchers.IO) {
        val latestManifestUrl = "https://github.com/EricFL1998/deepseek-harness-mobile-rootfs/releases/latest/download/rootfs-manifest.json"
        try {
            val request = Request.Builder()
                .url(latestManifestUrl)
                .header("User-Agent", "dsh-android")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val remoteManifest = parseManifest(body)
                        if (remoteManifest.runtimeVersion.isNotBlank()) {
                            return@withContext remoteManifest
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 网络异常或 404，无缝回退本地 manifest
        }
        bundledManifest
    }

    fun installedVersion(): String? {
        val marker = markerFile()
        if (!marker.isFile) return null
        return runCatching { json.decodeFromString<InstallMarker>(marker.readText()).runtimeVersion }
            .getOrNull()
    }

    /** 读取安装标记；缺失或损坏时为 null。 */
    internal fun readMarker(): InstallMarker? {
        val marker = markerFile()
        if (!marker.isFile) return null
        return runCatching { json.decodeFromString<InstallMarker>(marker.readText()) }.getOrNull()
    }

    fun isInstalled(manifest: RootfsManifest): Boolean {
        val version = installedVersion() ?: return false
        if (version != manifest.runtimeVersion) return false
        val marker = readMarker()
        val currentAbi = android.os.Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
        if (!marker?.abi.isNullOrBlank() && currentAbi.isNotBlank() && marker.abi != currentAbi) return false
        return File(layout.rootfsDir, "etc/os-release").isFile ||
            File(layout.rootfsDir, "usr/bin/env").isFile
    }

    suspend fun verifyAndInstall(
        archiveFile: File,
        expectedSha256: String? = null,
        onProgress: (InstallProgress) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        installArchive(
            manifest = RootfsManifest(sha256 = expectedSha256.orEmpty()),
            archive = archiveFile,
            onProgress = onProgress,
        )
    }

    /** 把调用方提供的归档（assets 自带 / SAF 选择）按 manifest 契约安装。 */
    suspend fun installFromArchive(
        manifest: RootfsManifest,
        archiveFile: File,
        expectedSha256: String?,
        onProgress: (InstallProgress) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        installArchive(
            manifest = manifest.copy(sha256 = expectedSha256 ?: manifest.sha256),
            archive = archiveFile,
            onProgress = onProgress,
        )
    }

    /** 按 manifest 安装；`cache/rootfs-local.tar.gz` 优先用于无网调试。 */
    suspend fun install(
        manifest: RootfsManifest,
        abi: String? = null,
        onProgress: (InstallProgress) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        layout.ensureDirectories()
        val archiveUrl = abi
            ?.let { manifest.downloadUrls[it] }
            ?.takeIf { it.isNotBlank() }
            ?: manifest.downloadUrl
        val expectedSha = abi
            ?.let { manifest.downloadSha256[it] }
            ?.takeIf { it.isNotBlank() }
            ?: manifest.sha256
        val effectiveManifest = manifest.copy(downloadUrl = archiveUrl, sha256 = expectedSha)
        val localOverride = File(layout.cacheDir, "rootfs-local.tar.gz")
        val archive = when {
            localOverride.isFile -> localOverride
            archiveUrl.isNotBlank() -> {
                val target = RootfsNaming.archiveCacheFile(
                    layout.cacheDir,
                    manifest.runtimeVersion,
                    abi ?: android.os.Build.SUPPORTED_ABIS?.firstOrNull().orEmpty().ifBlank { "unknown" }
                )
                if (!(target.isFile && digestMatches(target, expectedSha))) {
                    onProgress(InstallProgress("下载 rootfs（${manifest.baseDistro.ifBlank { "Debian" }}）...", 0.02f))
                    val downloaded = download(effectiveManifest, target) { fraction ->
                        onProgress(InstallProgress("下载 rootfs... ${(fraction * 100).toInt()}%", 0.02f + fraction * 0.48f))
                    }
                    if (downloaded.isFailure) return@withContext downloaded
                }
                target
            }
            else -> return@withContext Result.failure(
                IllegalStateException(
                    "rootfs 下载地址未配置：请填写 rootfs-manifest.json 的 downloadUrls，或推送 cache/rootfs-local.tar.gz"
                )
            )
        }

        if (!archive.isFile || archive.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("rootfs 压缩包不存在或为空: ${archive.absolutePath}"))
        }
        return@withContext installArchive(effectiveManifest, archive, onProgress)
    }

    private fun installArchive(
        manifest: RootfsManifest,
        archive: File,
        onProgress: (InstallProgress) -> Unit,
    ): Result<Unit> {
        layout.ensureDirectories()
        try {
            if (manifest.sha256.isNotBlank()) {
                onProgress(InstallProgress("校验 rootfs SHA-256...", 0.52f))
                val actual = calculateSha256(archive)
                if (!actual.equals(manifest.sha256.removePrefix("sha256:"), ignoreCase = true)) {
                    return Result.failure(
                        IllegalStateException("Rootfs SHA-256 校验失败: 期望 ${manifest.sha256}，实际 $actual")
                    )
                }
            }

            // 先备份 DSH_HOME 并删除旧 rootfs，避免解包时磁盘空间不足（升级场景）。
            val preservedHome = File(layout.runtimeMetaDir, "dsh-home-preserved")
            if (layout.dshHomeDir.exists()) {
                preservedHome.deleteRecursively()
                preservedHome.parentFile?.mkdirs()
                runCatching { layout.dshHomeDir.copyRecursively(preservedHome, overwrite = true) }
            }
            if (layout.rootfsDir.exists()) layout.rootfsDir.deleteRecursively()

            onProgress(InstallProgress("解包 Compact rootfs...", 0.55f))
            val staging = RootfsNaming.stagingDir(layout.cacheDir, manifest.runtimeVersion)
            staging.deleteRecursively()
            staging.mkdirs()
            extractTarGz(archive, staging) { fraction ->
                onProgress(InstallProgress("解包 rootfs... ${(fraction * 100).toInt()}%", 0.55f + fraction * 0.3f))
            }

            // rootfs 归档通常包含根目录内容；若多包了一层，则下钻。
            val source = unwrapSingleRoot(staging)
            if (!File(source, "etc/os-release").isFile && !File(source, "usr/bin/env").isFile) {
                return Result.failure(IllegalStateException("rootfs 内容不完整：缺少 etc/os-release / usr/bin/env"))
            }

            onProgress(InstallProgress("替换 rootfs 目录...", 0.9f))
            if (!source.renameTo(layout.rootfsDir)) {
                source.copyRecursively(layout.rootfsDir, overwrite = true)
                staging.deleteRecursively()
            }
            layout.optDshDir.mkdirs()
            layout.dshHomeDir.mkdirs()
            if (preservedHome.exists()) {
                // 恢复旧 DSH_HOME（会话/凭证/设置），但 node_modules 单独合并：
                // 新镜像的 node_modules 带着新版 bridge 与官方依赖，旧目录整体盖回去会把
                // 新 bridge 弄丢（「行在、包不在」）；但旧目录里还有用户后来装的第三方插件，
                // 全排除又会把它们弄丢。所以：① 恢复时跳过 node_modules；② 恢复后把旧
                // node_modules 里**新镜像没有的**包（用户插件）补进来，同名以新镜像为准。
                val nodeModulesRel = "profiles/web/node_modules"
                val preservedNodeModules = File(preservedHome, nodeModulesRel).absolutePath
                runCatching {
                    preservedHome.walkTopDown().forEach { src ->
                        if (src.absolutePath.startsWith(preservedNodeModules)) return@forEach
                        val rel = src.relativeTo(preservedHome).path
                        val dst = File(layout.dshHomeDir, rel)
                        if (src.isDirectory) dst.mkdirs()
                        else runCatching { dst.parentFile?.mkdirs(); src.copyTo(dst, overwrite = true) }
                    }
                }
                // 把旧 node_modules 里新镜像缺失的包（用户装的第三方插件）补回来。
                runCatching {
                    val oldModules = File(preservedHome, nodeModulesRel)
                    val newModules = File(layout.dshHomeDir, nodeModulesRel)
                    oldModules.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
                        ?.forEach { pkg ->
                            val target = File(newModules, pkg.name)
                            if (!target.exists()) {
                                newModules.mkdirs()
                                runCatching { pkg.copyRecursively(target, overwrite = false) }
                            }
                        }
                }
                preservedHome.deleteRecursively()
            }
            // 旧 profile 的 pnpm-lock 可能锁定旧版 DSH；删掉让它由新版重新解析。
            runCatching {
                File(layout.dshHomeDir, "profiles/web/pnpm-lock.yaml").delete()
            }

            onProgress(InstallProgress("自检运行时工具链...", 0.95f))
            val toolchain = verifyToolchainIntegrity()
            if (toolchain.isFailure) return toolchain

            markerFile().parentFile?.mkdirs()
            markerFile().writeText(
                json.encodeToString(
                    InstallMarker.serializer(),
                    InstallMarker(
                        runtimeVersion = manifest.runtimeVersion,
                        baseDistro = manifest.baseDistro,
                        installedAt = System.currentTimeMillis(),
                        archiveBytes = archive.length(),
                        abi = android.os.Build.SUPPORTED_ABIS?.firstOrNull().orEmpty(),
                        rootfsName = RootfsNaming.ARCHIVE_PREFIX,
                        releaseTag = RootfsNaming.releaseTag(manifest.runtimeVersion),
                        androidPatched = manifest.androidPatched,
                    )
                )
            )
            onProgress(InstallProgress("运行时安装完成", 1.0f))
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun download(
        manifest: RootfsManifest,
        target: File,
        onProgress: (Float) -> Unit,
    ): Result<Unit> {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, "${target.name}.part")
        return try {
            val request = Request.Builder().url(manifest.downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("rootfs 下载失败: HTTP ${response.code}"))
                }
                val body = response.body ?: return Result.failure(IllegalStateException("rootfs 下载响应为空"))
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (part.renameTo(target)) Result.success(Unit)
            else Result.failure(IllegalStateException("rootfs 下载文件落盘失败"))
        } catch (e: Exception) {
            part.delete()
            Result.failure(e)
        }
    }

    private fun extractTarGz(archive: File, destination: File, onProgress: (Float) -> Unit) {
        val totalBytes = archive.length().coerceAtLeast(1)
        FileInputStream(archive).use { fileIn ->
            GzipCompressorInputStream(fileIn).use { gzipIn ->
                TarArchiveInputStream(gzipIn).use { tar ->
                    var processed = 0L
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        // 归档常带 "./" 前缀；规范化后再做路径穿越校验。
                        val name = entry.name.replace('\\', '/')
                            .split('/')
                            .filter { it.isNotEmpty() && it != "." }
                            .joinToString("/")
                        if (name.isEmpty()) continue
                        val target = File(destination, name)
                        val canonicalDest = destination.canonicalPath + File.separator
                        if (!target.canonicalPath.startsWith(canonicalDest)) {
                            throw IllegalStateException("rootfs 归档包含非法路径: $name")
                        }
                        when {
                            entry.isDirectory -> target.mkdirs()
                            entry.isSymbolicLink -> {
                                target.parentFile?.mkdirs()
                                runCatching {
                                    Files.deleteIfExists(target.toPath())
                                    Files.createSymbolicLink(target.toPath(), Paths.get(entry.linkName))
                                }
                            }
                            entry.isLink -> {
                                val linkSource = File(destination, entry.linkName)
                                if (linkSource.isFile && target.canonicalPath.startsWith(canonicalDest)) {
                                    target.parentFile?.mkdirs()
                                    linkSource.copyTo(target, overwrite = true)
                                    // 硬链接同样要恢复可执行位（pnpm 等二进制依赖它）。
                                    applyMode(target, entry.mode)
                                }
                            }
                            else -> {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var read: Int
                                    while (tar.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        processed += read
                                    }
                                }
                                applyMode(target, entry.mode)
                            }
                        }
                        onProgress((processed.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }

    private fun applyMode(file: File, mode: Int) {
        if (!file.isFile) return
        val executable = (mode and 0b001_001_001) != 0
        file.setExecutable(executable, false)
        file.setReadable(true, false)
        file.setWritable((mode and 0b010_000_000) != 0, true)
    }

    private fun unwrapSingleRoot(staging: File): File {
        val children = staging.listFiles().orEmpty().filterNot { it.name == "." || it.name == ".." }
        return if (children.size == 1 && children[0].isDirectory) children[0] else staging
    }

    private fun verifyToolchainIntegrity(): Result<Unit> {
        val requiredDirs = listOf(
            layout.rootfsDir,
            layout.optDshDir,
            layout.dshHomeDir,
            layout.cacheDir,
            layout.safDir
        )
        for (dir in requiredDirs) {
            if (!dir.exists()) {
                return Result.failure(IllegalStateException("缺少核心运行时目录: ${dir.name}"))
            }
        }
        return Result.success(Unit)
    }

    private fun digestMatches(file: File, expected: String): Boolean {
        if (expected.isBlank()) return false
        return calculateSha256(file).equals(expected.removePrefix("sha256:"), ignoreCase = true)
    }

    private fun markerFile(): File = RootfsNaming.markerFile(layout.baseDir)

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Serializable
    internal data class InstallMarker(
        val runtimeVersion: String,
        val baseDistro: String,
        val installedAt: Long,
        val archiveBytes: Long,
        val schemaVersion: Int = RootfsNaming.SCHEMA_VERSION,
        val abi: String = "",
        val rootfsName: String = RootfsNaming.ARCHIVE_PREFIX,
        val releaseTag: String = "",
        /** 镜像是否已烧入 Android 补丁；旧标记没有该字段时为 false。 */
        val androidPatched: Boolean = false,
    )
}






