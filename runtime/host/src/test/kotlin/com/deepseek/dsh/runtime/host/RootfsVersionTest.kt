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
    fun `an identical install never asks for anything`() {
        // 回归：启动横幅曾用字符串比较，同版本也会弹「运行时更新可用」。
        // 判断必须走 assessUpdate，且同版本一律 NONE。
        for (manifest in listOf("1.0.1-dsh0.2.1-rc.2-bridge1.1.0", "1.1.0-dsh0.2.1-rc.2-bridge1.1.0")) {
            val assessment = assessUpdate(manifest, manifest, "1.1.0", "1.1.0")
            assertEquals("same manifest must be NONE: $manifest", UpdateNeed.NONE, assessment.need)
            assertFalse("same manifest must not report an update: $manifest", assessment.hasUpdate)
        }
    }

    @Test
    fun `the mandatory image update is the only kind shown at startup`() {
        // 启动横幅只认 MANDATORY；仅插件落后时它必须为 NONE，所以启动不提醒。
        assertEquals(
            RootfsUpdateKind.MANDATORY,
            decideRootfsUpdateKind("1.0.0-dsh0.2.1-rc.1-bridge1.1.0", "1.1.0-dsh0.2.1-rc.2-bridge1.1.0"),
        )
        assertEquals(
            RootfsUpdateKind.NONE,
            decideRootfsUpdateKind("1.1.0-dsh0.2.1-rc.2-bridge1.0.0", "1.1.0-dsh0.2.1-rc.2-bridge1.1.0"),
        )
    }

    @Test
    fun `a rebuilt image with the same engine is not a mandatory update`() {
        // 模拟器上真实踩到：1.0.0-dsh0.2.1-rc.2（旧镜像，无插件）升到
        // 1.0.1-dsh0.2.1-rc.2-bridge1.1.0，dsh 段相同，却在启动时弹了
        // 「重新下载运行时」。重新打包的镜像只算 REPACKAGED：设置页可选，
        // 启动流程（只认 MANDATORY）不拦人。
        assertEquals(
            RootfsUpdateKind.REPACKAGED,
            decideRootfsUpdateKind("1.0.0-dsh0.2.1-rc.2", "1.0.1-dsh0.2.1-rc.2-bridge1.1.0"),
        )
    }

    @Test
    fun `a patched image ignores the dsh segment`() {
        // 构建期烧补丁的镜像内部是「最新 DSH + 补丁」，rc.3 与 rc.2 都可用；
        // 只有 rootfs 段自增才提示可选更新。
        assertEquals(
            RootfsUpdateKind.NONE,
            decideRootfsUpdateKind(
                "1.0.12-dsh0.2.1-rc.3-bridge2.0.7",
                "1.0.12-dsh0.2.1-rc.3-bridge2.0.7",
                installedPatched = true,
                availablePatched = true,
            ),
        )
        assertEquals(
            RootfsUpdateKind.REPACKAGED,
            decideRootfsUpdateKind(
                "1.0.12-dsh0.2.1-rc.3-bridge2.0.7",
                "1.0.13-dsh0.2.1-rc.4-bridge2.0.7",
                installedPatched = true,
                availablePatched = true,
            ),
        )
        // 已装补丁镜像，远端只有旧的未补丁镜像：不引导回退。
        assertEquals(
            RootfsUpdateKind.NONE,
            decideRootfsUpdateKind(
                "1.0.13-dsh0.2.1-rc.4-bridge2.0.7",
                "1.0.12-dsh0.2.1-rc.3-bridge2.0.7",
                installedPatched = true,
                availablePatched = false,
            ),
        )
        val assessment = assessUpdate(
            installed = "1.0.12-dsh0.2.1-rc.3-bridge2.0.7",
            available = "1.0.13-dsh0.2.1-rc.4-bridge2.0.7",
            latestPlugin = null,
            installedPlugin = null,
            installedPatched = true,
            availablePatched = true,
        )
        assertEquals(UpdateNeed.ROOTFS_OPTIONAL, assessment.need)
    }

    @Test
    fun `an engine mismatch wins over a newer rootfs segment`() {
        // 用户先收到了 rc.3 的 1.0.12 镜像，而当前 APK 的补丁目标是 rc.2：
        // 即使 1.0.12 > 1.0.11，也必须引导换回 rc.2 的镜像，不能停在版本混杂的树。
        assertEquals(
            RootfsUpdateKind.MANDATORY,
            decideRootfsUpdateKind(
                "1.0.12-dsh0.2.1-rc.3-bridge2.0.7",
                "1.0.11-dsh0.2.1-rc.2-bridge2.0.7",
            ),
        )
    }

    @Test
    fun `an older bundled manifest never offers a downgrade`() {
        // 内置兜底清单（assets/rootfs-manifest.json）不会随发版更新；离线时它
        // 可能比已装镜像旧。旧版本不能算「可更新」，否则会引导降级。
        assertEquals(
            RootfsUpdateKind.NONE,
            decideRootfsUpdateKind("1.0.9-dsh0.2.1-rc.2-bridge2.0.1", "1.0.4-dsh0.2.1-rc.2-bridge1.3.1"),
        )
    }

    @Test
    fun `an engine change is mandatory even when the rootfs segment matches`() {
        assertEquals(
            RootfsUpdateKind.MANDATORY,
            decideRootfsUpdateKind("1.0.1-dsh0.2.1-rc.1-bridge1.1.0", "1.0.1-dsh0.2.1-rc.2-bridge1.1.0"),
        )
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
    fun `an unknown installed plugin is never assumed current`() {
        // 读不到实际安装版本时，镜像里打包过的版本不能当作「已安装」：
        // 文件可能已经被删掉或替换，按落后处理、提示一次更新最安全。
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2-bridge1.0.0",
            available = current,
            latestPlugin = "1.1.0",
            installedPlugin = null,
        )
        assertEquals(UpdateNeed.PLUGIN_REQUIRED, assessment.need)
        assertNull(assessment.installedPlugin)
        assertTrue(assessment.canUpdatePlugin)
    }

    @Test
    fun `an engine bump is an optional image update even when the plugin is current`() {
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
    fun `an engine bump alone is an optional image update`() {
        val assessment = assessUpdate(
            installed = "1.1.0-dsh0.2.1-rc.1-bridge1.1.0",
            available = "1.1.0-dsh0.2.1-rc.2-bridge1.1.0",
            latestPlugin = "1.1.0",
            installedPlugin = "1.1.0",
        )
        assertEquals(UpdateNeed.ROOTFS_OPTIONAL, assessment.need)
    }

    @Test
    fun `a repackaged image with the same engine offers an optional image update`() {
        // rootfs 段自增说明镜像重新打包过：可能只是把新插件打进去，也可能带着
        // 只存在于镜像里的修复。插件侧已经是最新，因此只给「可选」的镜像更新，
        // 且不打断启动。
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2-bridge1.1.0",
            available = "1.0.1-dsh0.2.1-rc.2-bridge1.1.0",
            latestPlugin = "1.1.0",
            installedPlugin = "1.1.0",
        )
        assertEquals(UpdateNeed.ROOTFS_OPTIONAL, assessment.need)
        assertTrue(assessment.hasUpdate)
        assertFalse(assessment.canUpdatePlugin)
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
        // 查询失败时不能凭镜像段推断插件落后，否则会误报。这里镜像段也一致，
        // 因此既不该报插件更新，也不该报镜像更新。
        val assessment = assessUpdate(
            installed = "1.1.0-dsh0.2.1-rc.2-bridge1.0.0",
            available = current,
            latestPlugin = null,
            installedPlugin = "1.0.0",
        )
        assertEquals(UpdateNeed.NONE, assessment.need)
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

    @Test
    fun `plugin versions compare numerically rather than as text`() {
        // 回归：字符串不等曾把 1.24.0 判成落后于 1.1.0，于是「更新」会降级。
        assertEquals(1, comparePluginVersions("1.24.0", "1.1.0"))
        assertEquals(-1, comparePluginVersions("1.1.0", "1.24.0"))
        assertEquals(1, comparePluginVersions("1.10.0", "1.9.9"))
        assertEquals(0, comparePluginVersions("1.1.0", "1.1.0"))
        assertEquals(0, comparePluginVersions("v1.1.0", "1.1.0"))
        assertEquals(-1, comparePluginVersions("1.1.0-rc.1", "1.1.0"))
        assertNull(comparePluginVersions("1.1", "1.1.0"))
        assertNull(comparePluginVersions(null, "1.1.0"))
    }

    @Test
    fun `a newer installed plugin is never a downgrade target`() {
        // 本地装的是未发布的开发版（1.24.0），仓库最新发布是 1.1.0：
        // 这不是落后，也不能把按钮变成「更新」（那是降级）。镜像本身重新打包过，
        // 因此只允许出现可选的镜像更新。
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2-bridge1.1.0",
            available = current,
            latestPlugin = "1.1.0",
            installedPlugin = "1.24.0",
        )
        assertEquals(UpdateNeed.ROOTFS_OPTIONAL, assessment.need)
        assertFalse(assessment.pluginOutdated)
        assertFalse(assessment.canUpdatePlugin)
    }

    @Test
    fun `a newer published release still counts as an update`() {
        val assessment = assessUpdate(
            installed = current,
            available = current,
            latestPlugin = "1.2.0",
            installedPlugin = "1.1.0",
        )
        assertEquals(UpdateNeed.PLUGIN_REQUIRED, assessment.need)
        assertTrue(assessment.pluginOutdated)
        assertTrue(assessment.canUpdatePlugin)
    }

    @Test
    fun `without a latest release the update action stays disabled`() {
        // 离线时按下去只会失败；界面应保持「无法判断」，按钮不可用。
        val assessment = assessUpdate(
            installed = current,
            available = current,
            latestPlugin = null,
            installedPlugin = "1.0.0",
        )
        assertFalse(assessment.pluginOutdated)
        assertFalse(assessment.canUpdatePlugin)
    }

    @Test
    fun `an unknown installed version can still be updated when the latest release is known`() {
        // 旧镜像没有 `-bridge` 段，插件也没落盘：无从判断，按「落后」处理，
        // 因为更新一次是最安全的收敛动作。
        val assessment = assessUpdate(
            installed = "1.0.0-dsh0.2.1-rc.2",
            available = current,
            latestPlugin = "1.1.0",
            installedPlugin = null,
        )
        assertEquals(UpdateNeed.PLUGIN_REQUIRED, assessment.need)
        assertTrue(assessment.pluginOutdated)
        assertTrue(assessment.canUpdatePlugin)
    }

    @Test
    fun `dsh compatibility rejects a rootfs built for another engine`() {
        val expected = "0.2.1-rc.2"
        assertTrue(RootfsNaming.isDshCompatible("1.0.11-dsh0.2.1-rc.2-bridge2.0.7", expected))
        assertFalse(RootfsNaming.isDshCompatible("1.0.12-dsh0.2.1-rc.3-bridge2.0.7", expected))
        assertFalse(RootfsNaming.isDshCompatible("1.0.0-dsh0.2.1-rc.2", "0.2.1-rc.3"))
        // 无法解析/无法判断时不冒充兼容。
        assertFalse(RootfsNaming.isDshCompatible("weird", expected))
        assertFalse(RootfsNaming.isDshCompatible(null, expected))
        // 内置清单缺失时保留旧行为（无法判断则放行）。
        assertTrue(RootfsNaming.isDshCompatible("1.0.12-dsh0.2.1-rc.3-bridge2.0.7", null))
        assertEquals("0.2.1-rc.2", RootfsNaming.dshVersion("1.0.11-dsh0.2.1-rc.2-bridge2.0.7"))
        assertNull(RootfsNaming.dshVersion("weird"))
    }
}
