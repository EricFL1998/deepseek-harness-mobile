package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

            val dummyArchive = File(tempDir, "rootfs.tar.gz").apply {
                writeText("dummy-rootfs-bytes")
            }

            val progressList = mutableListOf<String>()
            val result = installer.verifyAndInstall(dummyArchive) { progress ->
                progressList.add(progress.step)
            }

            assertTrue(result.isSuccess)
            assertTrue(progressList.isNotEmpty())
            assertTrue(layout.rootfsDir.exists())
            assertTrue(layout.dshHomeDir.exists())

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
