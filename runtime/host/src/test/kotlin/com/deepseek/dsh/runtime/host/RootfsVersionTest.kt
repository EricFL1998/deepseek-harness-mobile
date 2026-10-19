package com.deepseek.dsh.runtime.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本串拆段与更新性质判断。
 *
 * 这一层的核心结论是「插件必需、镜像可选」，因此每个分支都要钉住：插件落后
 * 必须给出 `PLUGIN_REQUIRED`，镜像重建只能给出 `ROOTFS_OPTIONAL`。
 */
class RootfsVersionTest {

    private val current = "1.1.0-dsh0.2.1-rc.2-bridge1.1.0"

    @Test
    fun `parses the three-segment form`() {
        val version = RootfsVersion.parse(current)!!
        assertEquals("1.1.0", version.rootfs)
        assertEquals("0.2.1-rc.2", version.dsh)
        assertEquals("1.1.0", version.bridge)
        assertEquals(current, version.encode())
    }

    @Test
    fun `parses the legacy two-segment form with an unknown bridge`() {
        val version = RootfsVersion.parse("1.0.0-dsh0.2.1-rc.2")!!
        assertEquals("1.0.0", version.rootfs)
        assertEquals("0.2.1-rc.2", version.dsh)
        assertNull(version.bridge)
    }

    @Test
    fun `parses a bare rootfs version`() {
        val version = RootfsVersion.parse("1.0.0")!!
        assertEquals("1.0.0", version.rootfs)
        assertNull(version.dsh)
        assertNull(version.bridge)
    }

    @Test
    fun `an unparsable string keeps its whole text as the rootfs segment`() {
        assertEquals("weird", RootfsVersion.parse("weird")!!.rootfs)
    }

    @Test
    fun `an empty or null version is absent`() {
        assertNull(RootfsVersion.parse(null))
        assertNull(RootfsVersion.parse(""))
        assertNull(RootfsVersion.parse("   "))
    }

    @Test
    fun `everything current needs nothing`() {
        val assessment = assessUpdate(current, current, "1.1.0", "1.1.0")
        assertEquals(UpdateNeed.NONE, assessment.need)
        assertFalse(assessment.hasUpdate)
    }

    @Test
    fun `an uninstalled rootfs is not an update`() {
        // 未安装由首启安装流程处理，不该在这里报更新。
        assertEquals(UpdateNeed.NONE, assessUpdate(null, current, "1.1.0", null).need)
    }

    @Test
    fun `an installed plugin newer than the image needs nothing`() {
        // 用户已经单独更新过插件：镜像里打包的旧版本不再意味着落后。
        val assessment = assessUpdate(
            installed = "1.1.0-dsh0.2.1-rc.2-bridge1.0.0",
            available = "1.1.0-dsh0.2.1-rc.2-bridge1.0.0",
            latestPlugin = "1.1.0",
            installedPlugin = "1.1.0",
        )
        assertEquals(UpdateNeed.NONE, assessment.need)
    }

    @Test
    fun `a plugin behind the latest release is required`() {
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2-bridge1.0.0",
            available = "1.0.0-dsh0.2.1-rc.2-bridge1.0.0",
            latestPlugin = "1.1.0",
            installedPlugin = "1.0.0",
        )
        assertEquals(UpdateNeed.PLUGIN_REQUIRED, assessment.need)
        assertTrue(assessment.hasUpdate)
        assertTrue(assessment.canUpdatePlugin)
    }

    @Test
    fun `a plugin packaged older than the image is required even before the image lands`() {
        // 镜像已经指向新插件，但本地还是旧插件：先按插件必需处理。
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2-bridge1.0.0",
            available = current,
            latestPlugin = "1.1.0",
            installedPlugin = "1.0.0",
        )
        assertEquals(UpdateNeed.PLUGIN_REQUIRED, assessment.need)
    }

    @Test
    fun `an unknown installed plugin falls back to the packaged segment`() {
        // 读不到实际安装版本时，用镜像里打包的版本作为依据。
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2-bridge1.0.0",
            available = current,
            latestPlugin = "1.1.0",
            installedPlugin = null,
        )
        assertEquals(UpdateNeed.PLUGIN_REQUIRED, assessment.need)
        assertEquals("1.0.0", assessment.installedPlugin)
    }

    @Test
    fun `a rebuilt image with a current plugin is only an optional image update`() {
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.1-bridge1.1.0",
            available = "1.1.0-dsh0.2.1-rc.2-bridge1.1.0",
            latestPlugin = "1.1.0",
            installedPlugin = "1.1.0",
        )
        assertEquals(UpdateNeed.ROOTFS_OPTIONAL, assessment.need)
        assertTrue(assessment.hasUpdate)
        // 可选的镜像更新不该让「更新插件」按钮出现。
        assertFalse(assessment.canUpdatePlugin)
    }

    @Test
    fun `a new dsh segment alone is an optional image update`() {
        val assessment = assessUpdate(
            installed = "1.1.0-dsh0.2.1-rc.1-bridge1.1.0",
            available = "1.1.0-dsh0.2.1-rc.2-bridge1.1.0",
            latestPlugin = "1.1.0",
            installedPlugin = "1.1.0",
        )
        assertEquals(UpdateNeed.ROOTFS_OPTIONAL, assessment.need)
    }

    @Test
    fun `an image that only differs by its packaged plugin needs nothing once the plugin is current`() {
        // 镜像里打包的是 1.0.0，但用户已经把插件更新到 1.1.0：不必重下整包。
        val assessment = assessUpdate(
            installed = "1.1.0-dsh0.2.1-rc.2-bridge1.0.0",
            available = current,
            latestPlugin = "1.1.0",
            installedPlugin = "1.1.0",
        )
        assertEquals(UpdateNeed.NONE, assessment.need)
    }

    @Test
    fun `without a latest-plugin answer the plugin is not judged`() {
        // 查询失败时不能凭镜像段推断插件落后，否则会误报。
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2-bridge1.0.0",
            available = current,
            latestPlugin = null,
            installedPlugin = "1.0.0",
        )
        assertEquals(UpdateNeed.ROOTFS_OPTIONAL, assessment.need)
    }

    @Test
    fun `a legacy install without a bridge segment is required on the plugin side`() {
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2",
            available = current,
            latestPlugin = "1.1.0",
            installedPlugin = null,
        )
        assertEquals(UpdateNeed.PLUGIN_REQUIRED, assessment.need)
    }
}
