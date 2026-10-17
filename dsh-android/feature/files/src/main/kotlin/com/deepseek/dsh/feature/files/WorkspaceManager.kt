package com.deepseek.dsh.feature.files

import java.io.File

/**
 * v4.0 设计文档 §8.1 & §8.2:
 * 手机真实工作区管理与路径模型
 */
enum class WorkspaceStorageType {
    REAL_EXTERNAL,     // 真实外部存储 (内部存储/DSH, 系统文件可见)
    SAF_MIRROR,        // SAF 镜像模式 (受限)
    PRIVATE_ISOLATED   // App 私有目录高性能模式
}

data class FileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L
)

class WorkspaceManager(
    private val defaultExternalDir: File = File("/storage/emulated/0/Documents/DSH-Workspace"),
    private val appPrivateDir: File
) {
    fun resolveActiveWorkspace(type: WorkspaceStorageType): File {
        return when (type) {
            WorkspaceStorageType.REAL_EXTERNAL -> {
                if (defaultExternalDir.exists() || defaultExternalDir.mkdirs()) {
                    defaultExternalDir
                } else {
                    // 降级为私有目录
                    File(appPrivateDir, "workspace").apply { mkdirs() }
                }
            }
            WorkspaceStorageType.SAF_MIRROR -> {
                File(appPrivateDir, "saf_mirror").apply { mkdirs() }
            }
            WorkspaceStorageType.PRIVATE_ISOLATED -> {
                File(appPrivateDir, "workspace_perf").apply { mkdirs() }
            }
        }
    }

    fun listFiles(dir: File): List<FileItem> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles()?.map { f ->
            FileItem(
                name = f.name,
                absolutePath = f.absolutePath,
                isDirectory = f.isDirectory,
                sizeBytes = if (f.isFile) f.length() else 0L,
                lastModified = f.lastModified()
            )
        }?.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name })
            ?: emptyList()
    }
}
