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
}

