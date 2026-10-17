package com.deepseek.dsh.feature.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceManagerTest {

    @Test
    fun testWorkspaceResolution() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "dsh_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val manager = WorkspaceManager(
            defaultExternalDir = File(tempDir, "external_dsh"),
            appPrivateDir = File(tempDir, "private_app")
        )

        val externalWs = manager.resolveActiveWorkspace(WorkspaceStorageType.REAL_EXTERNAL)
        assertTrue(externalWs.exists())
        assertEquals("external_dsh", externalWs.name)

        val safWs = manager.resolveActiveWorkspace(WorkspaceStorageType.SAF_MIRROR)
        assertTrue(safWs.exists())
        assertEquals("saf_mirror", safWs.name)

        // 创建临时测试文件
        val testFile = File(externalWs, "output.txt")
        testFile.writeText("Agent generation result")

        val list = manager.listFiles(externalWs)
        assertEquals(1, list.size)
        assertEquals("output.txt", list[0].name)
        assertTrue(!list[0].isDirectory)
        assertTrue(list[0].sizeBytes > 0)

        tempDir.deleteRecursively()
    }
}
