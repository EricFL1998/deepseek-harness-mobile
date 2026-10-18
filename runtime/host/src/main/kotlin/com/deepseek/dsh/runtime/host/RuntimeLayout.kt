package com.deepseek.dsh.runtime.host

import java.io.File

/**
 * v4.0 设计文档 §4.1: 本地运行时目录结构规范
 *
 * files/
 * ├─ rootfs/                 # Debian slim（默认）/ Ubuntu minimal（备选）
 * │  ├─ opt/dsh/             # Node、pnpm、dsh 入口
 * │  └─ root/.dsh/           # DSH_HOME：配置/会话/插件/凭据
 * ├─ runtime/                # 版本清单、补丁、校验和
 * ├─ workspace-link/         # 工作区绑定信息
 * ├─ cache/                  # 依赖/构建缓存（/cache）
 * ├─ saf/                    # SAF 镜像根
 * ├─ logs/                   # 运行时日志
 * └─ backups/                # 用户数据快照
 */
data class RuntimeLayout(
    val baseDir: File,
    val nativeLibraryDir: File
) {
    val rootfsDir: File get() = File(baseDir, "rootfs")
    val optDshDir: File get() = File(rootfsDir, "opt/dsh")
    val dshHomeDir: File get() = File(rootfsDir, "root/.dsh")
    val runtimeMetaDir: File get() = File(baseDir, "runtime")
    val cacheDir: File get() = File(baseDir, "cache")
    val safDir: File get() = File(baseDir, "saf")
    val logsDir: File get() = File(baseDir, "logs")
    val backupsDir: File get() = File(baseDir, "backups")

    val prootBinary: File get() = File(nativeLibraryDir, "libproot.so")

    fun ensureDirectories() {
        baseDir.mkdirs()
        rootfsDir.mkdirs()
        optDshDir.mkdirs()
        dshHomeDir.mkdirs()
        runtimeMetaDir.mkdirs()
        cacheDir.mkdirs()
        safDir.mkdirs()
        logsDir.mkdirs()
        backupsDir.mkdirs()
    }
}

