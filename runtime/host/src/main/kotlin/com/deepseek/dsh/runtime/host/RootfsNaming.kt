package com.deepseek.dsh.runtime.host

import java.io.File

/**
 * rootfs 命名规范（单一事实来源）。
 *
 * 目录：files/rootfs
 * 标记：files/runtime/rootfs-installed.json
 * 缓存：files/cache/dsh-rootfs-<runtimeVersion>-<abi>.tar.gz
 * 暂存：files/cache/dsh-rootfs-staging-<runtimeVersion>
 * Release：rootfs-v<runtimeVersion> / dsh-rootfs-<runtimeVersion>-<abi>.tar.gz
 *
 * 升级时 runtimeVersion 变化即视为新 rootfs；ABI 变化（换机/备份恢复/新 Android）
 * 会强制重新安装，避免复用其他架构的 rootfs。
 */
object RootfsNaming {
    const val SCHEMA_VERSION = 1
    const val INSTALL_DIR = "rootfs"
    const val MARKER_FILE = "rootfs-installed.json"
    const val MANIFEST_ASSET = "rootfs-manifest.json"
    /** 最近一次成功拉取的清单缓存；清单本身不随 APK 打包。 */
    const val MANIFEST_CACHE_FILE = "rootfs-manifest-cache.json"
    const val ARCHIVE_PREFIX = "dsh-rootfs"
    const val RELEASE_TAG_PREFIX = "rootfs-v"

    fun markerFile(baseDir: File): File = File(baseDir, "runtime/$MARKER_FILE")

    fun releaseTag(runtimeVersion: String): String =
        if (runtimeVersion.startsWith(RELEASE_TAG_PREFIX)) runtimeVersion
        else "$RELEASE_TAG_PREFIX$runtimeVersion"

    fun archiveFileName(runtimeVersion: String, abi: String): String =
        "$ARCHIVE_PREFIX-$runtimeVersion-$abi.tar.gz"

    fun archiveCacheFile(cacheDir: File, runtimeVersion: String, abi: String): File =
        File(cacheDir, archiveFileName(runtimeVersion.replace('/', '-'), abi.replace('/', '-')))

    fun stagingDir(cacheDir: File, runtimeVersion: String): File =
        File(cacheDir, "dsh-rootfs-staging-${runtimeVersion.replace('/', '-')}")

    /**
     * 从 runtimeVersion 里取 DSH 版本段。
     *
     * 复用 [RootfsVersion] 的单一解析实现；格式不符时返回 null。
     * @param runtimeVersion rootfs 的完整版本串。
     * @returns DSH 版本，例如 `0.2.1-rc.2`。
     */
    fun dshVersion(runtimeVersion: String): String? = RootfsVersion.parse(runtimeVersion)?.dsh

    /**
     * rootfs 的 DSH 版本是否与当前 APK 的补丁目标兼容。
     *
     * APK 里的 rootfs 补丁是**整文件替换**，按某个 DSH 版本生成；把 rc.3 的
     * rootfs 套上 rc.2 的补丁会让运行树版本混杂。因此远端/缓存清单只有在
     * DSH 段与 APK 内置清单一致时才可用。内置清单缺失时无法判断，保留旧行为。
     * @param runtimeVersion 待判定的 rootfs 版本。
     * @param expectedDsh APK 内置清单的 DSH 段；null 表示无法判断。
     * @returns 可以接受时为 true。
     */
    fun isDshCompatible(runtimeVersion: String?, expectedDsh: String?): Boolean {
        if (expectedDsh.isNullOrBlank()) return true
        val actual = runtimeVersion?.let(::dshVersion) ?: return false
        return actual == expectedDsh
    }
}

