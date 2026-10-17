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

        val publicDocs = File(tempDir, "shared_documents/DSH-Workspace")
        val manager = WorkspaceManager(
            preferredPublicDir = publicDocs,
            appPrivateDir = File(tempDir, "private_app")
        )

        val externalWs = manager.resolveActiveWorkspace(WorkspaceStorageType.REAL_EXTERNAL)
        assertTrue(externalWs.exists())
        assertEquals("DSH-Workspace", externalWs.name)

        val safWs = manager.resolveActiveWorkspace(WorkspaceStorageType.SAF_MIRROR)
        assertTrue(safWs.exists())
        assertEquals("saf_mirror", safWs.name)

        val testFile = File(externalWs, "output.txt")
        testFile.writeText("Agent generation result in shared directory")

        val list = manager.listFiles(externalWs)
        assertEquals(1, list.size)
        assertEquals("output.txt", list[0].name)
        assertTrue(!list[0].isDirectory)
        assertTrue(list[0].sizeBytes > 0)

        tempDir.deleteRecursively()
    }
}
