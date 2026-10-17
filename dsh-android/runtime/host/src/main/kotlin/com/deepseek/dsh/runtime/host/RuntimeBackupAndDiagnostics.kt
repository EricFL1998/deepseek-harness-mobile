package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * v4.0 设计文档 §4.5 & §4.6:
 * 用户数据快照备份与脱敏诊断包导出
 */
class RuntimeBackupAndDiagnostics(
    private val layout: RuntimeLayout
) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun createUserDataBackup(): Result<File> = withContext(Dispatchers.IO) {
        layout.ensureDirectories()
        val timestamp = dateFormat.format(Date())
        val backupZip = File(layout.backupsDir, "dsh_backup_$timestamp.zip")

        try {
            ZipOutputStream(FileOutputStream(backupZip)).use { zos ->
                // 保证至少写入一条根快照 entry
                val markerEntry = ZipEntry("snapshot_meta.txt")
                zos.putNextEntry(markerEntry)
                zos.write("DSH User Data Snapshot $timestamp\n".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                if (layout.dshHomeDir.exists()) {
                    zipDirectory(layout.dshHomeDir, "dsh_home", zos)
                }
            }
            Result.success(backupZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportDiagnosticsZip(): Result<File> = withContext(Dispatchers.IO) {
        layout.ensureDirectories()
        val timestamp = dateFormat.format(Date())
        val diagZip = File(layout.logsDir, "dsh_diagnostics_$timestamp.zip")

        try {
            ZipOutputStream(FileOutputStream(diagZip)).use { zos ->
                // 1. 导出运行日志 (自动脱敏)
                if (layout.logsDir.exists()) {
                    layout.logsDir.listFiles()?.forEach { logFile ->
                        if (logFile.isFile && logFile.name.endsWith(".log")) {
                            val scrubbed = scrubSensitiveInfo(logFile.readText())
                            val entry = ZipEntry("logs/${logFile.name}")
                            zos.putNextEntry(entry)
                            zos.write(scrubbed.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        }
                    }
                }

                // 2. 导出环境描述
                val envSummary = """
                    OS: Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})
                    Brand: ${android.os.Build.BRAND}
                    Model: ${android.os.Build.MODEL}
                    Rootfs Base: Debian slim arm64 (Compact)
                    Created: ${Date()}
                """.trimIndent()
                val envEntry = ZipEntry("env_summary.txt")
                zos.putNextEntry(envEntry)
                zos.write(envSummary.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            Result.success(diagZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scrubSensitiveInfo(rawText: String): String {
        // §4.6 诊断脱敏：token/cookie/API key
        return rawText
            .replace("""token=[a-zA-Z0-9_\-]+""".toRegex(), "token=REDACTED")
            .replace("""Bearer\s+[a-zA-Z0-9_\-]+""".toRegex(), "Bearer REDACTED")
            .replace("""sk-[a-zA-Z0-9_\-]+""".toRegex(), "sk-REDACTED")
    }

    private fun zipDirectory(folder: File, parentFolder: String, zos: ZipOutputStream) {
        for (file in folder.listFiles() ?: emptyArray()) {
            if (file.isDirectory) {
                zipDirectory(file, "$parentFolder/${file.name}", zos)
            } else {
                zos.putNextEntry(ZipEntry("$parentFolder/${file.name}"))
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
