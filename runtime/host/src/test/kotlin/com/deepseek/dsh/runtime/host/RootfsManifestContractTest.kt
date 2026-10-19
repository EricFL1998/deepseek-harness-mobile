package com.deepseek.dsh.runtime.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * APK 内置清单与版本串形态的一致性。
 *
 * 三个仓库在这里交汇：插件仓库的 release tag、rootfs 打包脚本写出的
 * `runtimeVersion`、以及 APK 里的 `rootfs-manifest.json`。任何一处漂移都会让
 * 「插件必需 / 镜像可选」的判断失效，所以在客户端这一侧把它钉住。
 */
class RootfsManifestContractTest {

    /** 从仓库根开始找一个文件，兼容从模块目录运行测试。 */
    private fun locate(relative: String): File? {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("../../$relative"),
            File("../../../$relative"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    @Test
    fun `the bundled rootfs manifest declares all three version segments`() {
        val file = locate("app/src/main/assets/rootfs-manifest.json")
            ?: return // 在只编译本模块时不强制存在。
        val text = file.readText()
        val version = RootfsVersion.parse(
            Regex("\"runtimeVersion\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1),
        )
        assertNotNull("runtimeVersion must parse into segments", version)
        assertNotNull("the rootfs segment must be present", version!!.rootfs)
        assertNotNull("the dsh segment must be present", version.dsh)
        assertNotNull("the bridge segment must be present", version.bridge)
    }

    @Test
    fun `the bridge version in the manifest agrees with its own field`() {
        val file = locate("app/src/main/assets/rootfs-manifest.json") ?: return
        val text = file.readText()
        val runtimeVersion = Regex("\"runtimeVersion\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        val bridgeField = Regex("\"bridgeVersion\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        val parsed = RootfsVersion.parse(runtimeVersion)!!
        assertEquals(
            "bridgeVersion must match the -bridge segment of runtimeVersion",
            parsed.bridge,
            bridgeField,
        )
    }

    @Test
    fun `the bundled plugin manifest names the repository the client installs from`() {
        val file = locate("app/src/main/assets/bridge-plugin.json") ?: return
        val text = file.readText()
        val packageName = Regex("\"packageName\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        val repository = Regex("\"repository\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        val version = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        assertEquals("dsh-mobile-bridge-plugin", packageName)
        assertNotNull(repository)
        assertTrue(
            "the repository must be a GitHub URL the installer can append a tag to",
            repository!!.startsWith("https://github.com/"),
        )
        assertTrue("the version must be a plain semver triple", Regex("""^\d+\.\d+\.\d+$""").matches(version!!))
    }

    @Test
    fun `the plugin manifest version is never older than the version baked into the image`() {
        // APK 可以比镜像新：插件是就地升级的，客户端自带的那份通常会先于下一次
        // 镜像重建。但反过来不行 —— 客户端带着比镜像更旧的插件，等于启动即报
        // 「插件必须更新」。
        val rootfs = locate("app/src/main/assets/rootfs-manifest.json") ?: return
        val plugin = locate("app/src/main/assets/bridge-plugin.json") ?: return
        val imageBridge = RootfsVersion.parse(
            Regex("\"runtimeVersion\"\\s*:\\s*\"([^\"]+)\"").find(rootfs.readText())?.groupValues?.get(1),
        )?.bridge ?: return
        val manifestBridge = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(plugin.readText())?.groupValues?.get(1)
        assertNotNull(manifestBridge)
        assertTrue(
            "the APK's plugin version $manifestBridge must not be older than the image's $imageBridge",
            compareVersions(manifestBridge!!, imageBridge) >= 0,
        )
    }

    /** 逐段比较 `major.minor.patch`；解析不了的段按 0 处理。 */
    private fun compareVersions(left: String, right: String): Int {
        val a = left.split(".").map { it.toIntOrNull() ?: 0 }
        val b = right.split(".").map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val delta = (a.getOrNull(index) ?: 0) - (b.getOrNull(index) ?: 0)
            if (delta != 0) return delta
        }
        return 0
    }
}
