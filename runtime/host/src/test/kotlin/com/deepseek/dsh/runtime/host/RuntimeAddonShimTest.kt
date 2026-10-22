package com.deepseek.dsh.runtime.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * [RuntimeAddonShim] 的 JVM 测试：纯文件操作，不依赖 rootfs。
 *
 * 覆盖三条真实路径：
 * 1. 原生插件缺失 → 安装纯 JS shim；
 * 2. 主文件缺失（半装）→ 用 shim 替换，原目录改名留档；
 * 3. 健康安装（平台包在）→ 不动原包。
 */
class RuntimeAddonShimTest {

    private val globalModules = "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules"

    @Test
    fun `installs shim when the native package is missing`() {
        val root = Files.createTempDirectory("dsh-addon-shim-missing").toFile()
        try {
            val layout = RuntimeLayout(baseDir = root, nativeLibraryDir = root)
            File(layout.rootfsDir, globalModules).mkdirs()

            RuntimeAddonShim.ensure(layout)

            val target = File(layout.rootfsDir, "$globalModules/${RuntimeAddonShim.PACKAGE}")
            assertTrue(File(target, "package.json").isFile)
            val entry = File(target, "index.js").readText()
            assertTrue(entry.contains("requireBuiltin"))
            assertTrue(File(layout.rootfsDir, "root/.dsh/.addon-shim-revision").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `replaces a half-installed package and keeps the broken copy`() {
        val root = Files.createTempDirectory("dsh-addon-shim-half").toFile()
        try {
            val layout = RuntimeLayout(baseDir = root, nativeLibraryDir = root)
            val modules = File(layout.rootfsDir, globalModules).apply { mkdirs() }
            val broken = File(modules, RuntimeAddonShim.PACKAGE).apply { mkdirs() }
            // package.json 指向不存在的 lib/index.js：正是镜像半装的形状。
            File(broken, "package.json").writeText(
                """{"name":"${RuntimeAddonShim.PACKAGE}","main":"lib/index.js"}"""
            )

            RuntimeAddonShim.ensure(layout)

            assertTrue(File(broken, "index.js").isFile)
            assertTrue(File(broken, "package.json").readText().contains("local-shim"))
            val backups = modules.listFiles()
                ?.filter { it.name.startsWith("${RuntimeAddonShim.PACKAGE}.broken-") }
                .orEmpty()
            assertEquals(1, backups.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `leaves a healthy native install untouched`() {
        val root = Files.createTempDirectory("dsh-addon-shim-ok").toFile()
        try {
            val layout = RuntimeLayout(baseDir = root, nativeLibraryDir = root)
            val modules = File(layout.rootfsDir, globalModules).apply { mkdirs() }
            val installed = File(modules, RuntimeAddonShim.PACKAGE).apply { mkdirs() }
            File(installed, "lib").mkdirs()
            File(installed, "lib/index.js").writeText("module.exports = { requireBuiltin() {} }")
            File(installed, "package.json").writeText(
                """{"name":"${RuntimeAddonShim.PACKAGE}","main":"lib/index.js","optionalDependencies":{"${RuntimeAddonShim.PACKAGE}-linux-arm64-gnu":"0.1.9"}}"""
            )
            File(modules, "${RuntimeAddonShim.PACKAGE}-linux-arm64-gnu").mkdirs()

            RuntimeAddonShim.ensure(layout)

            assertTrue(File(installed, "lib/index.js").isFile)
            assertEquals(
                1,
                modules.listFiles().orEmpty().count { it.name == RuntimeAddonShim.PACKAGE },
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
