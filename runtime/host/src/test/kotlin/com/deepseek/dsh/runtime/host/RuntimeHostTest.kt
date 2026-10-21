package com.deepseek.dsh.runtime.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuntimeHostTest {

    @Test
    fun testProcessOutputParser() {
        val line1 = "dsh web: listening on http://127.0.0.1:45321/?token=sec_abc12345"
        val parsed1 = ProcessOutputParser.parseLine(line1)
        assertNotNull(parsed1)
        assertEquals(45321, parsed1!!.port)
        assertEquals("sec_abc12345", parsed1.token)

        val line2 = "dsh web: listening on http://localhost:8080/?token=token_xyz"
        val parsed2 = ProcessOutputParser.parseLine(line2)
        assertNotNull(parsed2)
        assertEquals(8080, parsed2!!.port)
        assertEquals("token_xyz", parsed2.token)

        val line3 = "Unrelated log line from runtime"
        assertNull(ProcessOutputParser.parseLine(line3))
    }

    @Test
    fun testProotCommandBuilder() {
        val baseDir = File("/data/user/0/com.deepseek.dsh/files")
        val nativeDir = File("/data/app/com.deepseek.dsh/lib/arm64")
        val layout = RuntimeLayout(baseDir = baseDir, nativeLibraryDir = nativeDir)

        val config = ProotCommandBuilder.LaunchConfig(
            layout = layout,
            userWorkspacePath = "/storage/emulated/0/Documents/DSH-Workspace",
            port = 0
        )

        val cmd = ProotCommandBuilder.build(config)
        assertTrue(cmd.contains("-r"))
        assertTrue(cmd.contains(layout.rootfsDir.absolutePath))
        assertTrue(cmd.contains("${config.userWorkspacePath}:/workspace"))
        assertTrue(cmd.contains("--link2symlink"))
        // Relative plugin storage such as GenUI's .dsh/genui must resolve below
        // the Harness home, never the root-owned /.dsh directory.
        assertEquals("/root", cmd[cmd.indexOf("-w") + 1])
        assertTrue(cmd.contains("HOME=/root"))
        assertTrue(cmd.contains("DSH_HOME=/root/.dsh"))
        // 直接跑 dsh 的 bin.js，而不是 /opt/dsh/bin/dsh 包装脚本：包装脚本会让
        // import.meta.url 指向包装层，profile bundle 的解析锚点随之出错。
        assertTrue(cmd.contains("/usr/local/bin/node"))
        assertTrue(cmd.any { it.endsWith("@deepseek-ai/dsh/lib/bin.js") })
        assertTrue(cmd.contains("--profile"))
        assertTrue(cmd.contains("web"))
    }
}
