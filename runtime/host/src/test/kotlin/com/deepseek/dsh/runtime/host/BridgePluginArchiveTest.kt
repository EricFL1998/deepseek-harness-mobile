package com.deepseek.dsh.runtime.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 插件发布归档（GitHub zipball）的校验与解压。
 *
 * 安装路径只有一条：下载 tag 归档 → 校验 → 铺进 profile。这些用例钉住
 * 「包名/版本/入口不一致就拒绝、路径不许逃出目标目录」这几条安全边界。
 */
class BridgePluginArchiveTest {

    private val packageName = "dsh-mobile-bridge-plugin"

    @Test
    fun `extracts a single-root archive and reads its manifest`() {
        val destination = Files.createTempDirectory("bridge-archive").toFile()
        try {
            val archive = zip(
                "repo-1.1.0/package.json" to manifestJson("1.1.0"),
                "repo-1.1.0/cordis.patch.yml" to "- insert: []\n",
                "repo-1.1.0/lib/index.js" to "export default {};\n",
                "repo-1.1.0/lib/providers/client-slots.js" to "export {};\n",
            )
            val contents = BridgePluginArchive.extract(
                archive = archive.inputStream(),
                destination = destination,
                expectedPackage = packageName,
                expectedVersion = "1.1.0",
            )
            assertEquals(packageName, contents.packageName)
            assertEquals("1.1.0", contents.version)
            assertEquals("lib/index.js", contents.main)
            assertEquals("cordis.patch.yml", contents.patch)
            assertEquals(setOf("@deepseek-ai/schemastery"), contents.dependencies.keys)
            assertTrue(File(destination, "lib/providers/client-slots.js").isFile)
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun `rejects an archive for another version`() {
        val destination = Files.createTempDirectory("bridge-archive").toFile()
        try {
            val archive = zip(
                "repo-1.2.0/package.json" to manifestJson("1.2.0"),
                "repo-1.2.0/cordis.patch.yml" to "- insert: []\n",
                "repo-1.2.0/lib/index.js" to "export default {};\n",
            )
            val error = assertThrows(IllegalArgumentException::class.java) {
                BridgePluginArchive.extract(archive.inputStream(), destination, packageName, "1.1.0")
            }
            assertTrue(error.message.orEmpty().contains("版本不匹配"))
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun `rejects an archive for another package`() {
        val destination = Files.createTempDirectory("bridge-archive").toFile()
        try {
            val archive = zip(
                "repo/package.json" to manifestJson("1.1.0").replace(packageName, "other-plugin"),
                "repo/cordis.patch.yml" to "- insert: []\n",
                "repo/lib/index.js" to "export default {};\n",
            )
            val error = assertThrows(IllegalArgumentException::class.java) {
                BridgePluginArchive.extract(archive.inputStream(), destination, packageName, "1.1.0")
            }
            assertTrue(error.message.orEmpty().contains("包名不匹配"))
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun `rejects an archive without a bundle patch`() {
        val destination = Files.createTempDirectory("bridge-archive").toFile()
        try {
            val archive = zip(
                "repo/package.json" to manifestJson("1.1.0").replace(
                    "\"dsh\": { \"bundle\": { \"patch\": \"./cordis.patch.yml\" } },",
                    "",
                ),
                "repo/lib/index.js" to "export default {};\n",
            )
            val error = assertThrows(IllegalArgumentException::class.java) {
                BridgePluginArchive.extract(archive.inputStream(), destination, packageName, "1.1.0")
            }
            assertTrue(error.message.orEmpty().contains("dsh.bundle.patch"))
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun `rejects an archive whose patch file is missing`() {
        val destination = Files.createTempDirectory("bridge-archive").toFile()
        try {
            val archive = zip(
                "repo/package.json" to manifestJson("1.1.0"),
                "repo/lib/index.js" to "export default {};\n",
            )
            val error = assertThrows(IllegalArgumentException::class.java) {
                BridgePluginArchive.extract(archive.inputStream(), destination, packageName, "1.1.0")
            }
            assertTrue(error.message.orEmpty().contains("bundle patch"))
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun `rejects entries that escape the destination`() {
        val destination = Files.createTempDirectory("bridge-archive").toFile()
        try {
            val archive = zip(
                "repo/package.json" to manifestJson("1.1.0"),
                "repo/cordis.patch.yml" to "- insert: []\n",
                "repo/lib/index.js" to "export default {};\n",
                "repo/../../escape.txt" to "nope\n",
            )
            val error = assertThrows(IllegalArgumentException::class.java) {
                BridgePluginArchive.extract(archive.inputStream(), destination, packageName, "1.1.0")
            }
            assertTrue(error.message.orEmpty().contains("越界"))
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun `builds a codeload archive url for a released version only`() {
        assertEquals(
            "https://codeload.github.com/owner/repo/zip/refs/tags/v1.1.0",
            BridgeReleaseDownloader.archiveUrl("https://github.com/owner/repo", "1.1.0"),
        )
        assertEquals(
            "https://codeload.github.com/owner/repo/zip/refs/tags/v1.1.0",
            BridgeReleaseDownloader.archiveUrl("https://github.com/owner/repo.git/", "1.1.0"),
        )
        assertNull(BridgeReleaseDownloader.archiveUrl("https://github.com/owner/repo", "main"))
        assertNull(BridgeReleaseDownloader.archiveUrl("https://github.com/owner/repo", "1.1.0;rm"))
        assertNull(BridgeReleaseDownloader.archiveUrl("https://example.com/owner/repo", "1.1.0"))
        assertEquals("owner/repo", BridgeReleaseDownloader.repositorySlug("git@github.com:owner/repo.git"))
    }

    /** 一份最小的插件清单；字段与真实发布包保持同形。 */
    private fun manifestJson(version: String): String = """
        {
          "name": "$packageName",
          "version": "$version",
          "type": "module",
          "main": "lib/index.js",
          "dsh": { "bundle": { "patch": "./cordis.patch.yml" } },
          "dependencies": { "@deepseek-ai/schemastery": "^3.18.5" }
        }
    """.trimIndent()

    /** 把若干路径/内容打成一个 zip。 */
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }
}
