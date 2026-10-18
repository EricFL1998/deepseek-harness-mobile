package com.deepseek.dsh.feature.files

import java.io.File

/**
 * v4.0 设计文档 §8.1 & §8.2:
 * 手机真实工作区管理与路径模型
 */
enum class WorkspaceStorageType {
    REAL_EXTERNAL,     // 真实外部公有目录 (Documents / Downloads, 系统文件直接可见)
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
    private val preferredPublicDir: File? = null,
    private val appPrivateDir: File
) {
    fun resolveActiveWorkspace(type: WorkspaceStorageType): File {
        return when (type) {
            WorkspaceStorageType.REAL_EXTERNAL -> {
                // 1. 优先使用传入的共享公有目录 (例如 /storage/emulated/0/Documents/DSH-Workspace)
                if (preferredPublicDir != null && (preferredPublicDir.exists() || preferredPublicDir.mkdirs())) {
                    preferredPublicDir
                } else {
                    // 2. 尝试标准外部公有 Documents / Downloads 目录
                    val standardDocs = File("/storage/emulated/0/Documents/DSH-Workspace")
                    val standardDownloads = File("/storage/emulated/0/Download/DSH-Workspace")

                    if (standardDocs.exists() || standardDocs.mkdirs()) {
                        standardDocs
                    } else if (standardDownloads.exists() || standardDownloads.mkdirs()) {
                        standardDownloads
                    } else {
                        // 3. 仅在公有目录全部无权限时降级
                        File(appPrivateDir, "workspace").apply { mkdirs() }
                    }
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

    /**
     * 扫描根工作区目录下的所有工作区子项目
     */
    fun listWorkspaceDirectories(baseDir: File): List<File> {
        if (!baseDir.exists() || !baseDir.isDirectory) return listOf(baseDir)
        val children = baseDir.listFiles()?.filter { it.isDirectory }?.toList() ?: emptyList()
        return if (children.isEmpty()) listOf(baseDir) else children
    }
}

