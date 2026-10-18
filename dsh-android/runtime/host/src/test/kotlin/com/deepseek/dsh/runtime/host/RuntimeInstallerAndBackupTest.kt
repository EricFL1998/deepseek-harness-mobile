package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class RuntimeInstallerAndBackupTest {

    @Test
    fun testRootfsInstallerVerification() {
        runBlocking {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "dsh_r1_test_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            val layout = RuntimeLayout(
                baseDir = tempDir,
                nativeLibraryDir = File(tempDir, "lib")
            )
            val installer = RootfsInstaller(layout)

            // 构造一个符合契约的最小 rootfs tar.gz（etc/os-release + usr/bin/env）。
            val archive = File(tempDir, "rootfs.tar.gz")
            FileOutputStream(archive).use { fileOut ->
                GzipCompressorOutputStream(fileOut).use { gzip ->
                    TarArchiveOutputStream(gzip).use { tar ->
                        fun put(name: String, content: String, mode: Int = 0b110_100_100) {
                            val bytes = content.toByteArray()
                            val entry = TarArchiveEntry(name).apply {
                                size = bytes.size.toLong()
                                this.mode = mode
                            }
                            tar.putArchiveEntry(entry)
                            tar.write(bytes)
                            tar.closeArchiveEntry()
                        }
                        put("etc/os-release", "ID=debian\n")
                        put("usr/bin/env", "#!/bin/sh\n", 0b111_101_101)
                    }
                }
            }

            val progressList = mutableListOf<String>()
            val result = installer.verifyAndInstall(archive) { progress ->
                progressList.add(progress.step)
            }

            assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
            assertTrue(progressList.isNotEmpty())
            assertTrue(File(layout.rootfsDir, "etc/os-release").isFile)
            assertTrue(File(layout.rootfsDir, "usr/bin/env").isFile)
            assertTrue(installer.installedVersion() != null)

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBackupAndDiagnostics() {
        runBlocking {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "dsh_diag_test_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            val layout = RuntimeLayout(
                baseDir = tempDir,
                nativeLibraryDir = File(tempDir, "lib")
            )
            layout.ensureDirectories()

            File(layout.dshHomeDir, "config.json").writeText("{\"token\": \"sec_token_123\"}")
            File(layout.logsDir, "host.log").writeText("User authenticated token=secret_xyz")

            val backupTool = RuntimeBackupAndDiagnostics(layout)
            val backupResult = backupTool.createUserDataBackup()
            assertTrue(backupResult.isSuccess)
            val backupFile = backupResult.getOrNull()
            assertNotNull(backupFile)
            assertTrue(backupFile!!.exists())
            assertTrue(backupFile.length() > 0)

            val diagResult = backupTool.exportDiagnosticsZip()
            assertTrue(diagResult.isSuccess)
            val diagFile = diagResult.getOrNull()
            assertNotNull(diagFile)
            assertTrue(diagFile!!.exists())

            tempDir.deleteRecursively()
        }
    }
}
