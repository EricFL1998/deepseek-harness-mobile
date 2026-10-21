package com.deepseek.dsh.runtime.host

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * GitHub release 归档（zipball）的校验与解压。
 *
 * 插件的发布带没有资产：tag `v<version>` 本身就是产物，客户端下载的是该 tag 的
 * 源码归档。归档里已经包含构建好的 `lib/`，因此这里只做三件事：路径安全、
 * 版本一致性、入口完整性 —— 不做构建，也不信任归档里的任何路径。
 *
 * 这个对象刻意不依赖 Android API，解压与校验因此可以用普通 JVM 单元测试覆盖。
 */
internal object BridgePluginArchive {

    /** 插件清单文件名。 */
    private const val MANIFEST_FILE = "package.json"

    /** 允许下载的最大归档字节数；当前发布约 130KB，留足余量。 */
    const val MAX_ARCHIVE_BYTES: Long = 32L * 1024 * 1024

    /** 允许解压的最大累计字节数，挡住 zip 炸弹。 */
    const val MAX_UNCOMPRESSED_BYTES: Long = 64L * 1024 * 1024

    /** 允许的最大条目数。 */
    const val MAX_ENTRIES: Int = 4096

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 归档里读出的一份插件清单。
     * @param packageName `package.json` 的 `name`。
     * @param version `package.json` 的 `version`。
     * @param main 入口文件（相对包目录）。
     * @param patch bundle patch 文件（相对包目录）。
     * @param dependencies 运行时依赖，用于在安装前确认解析得到。
     */
    data class Contents(
        val packageName: String,
        val version: String,
        val main: String,
        val patch: String,
        val dependencies: Map<String, String>,
    )

    /**
     * 把一份 GitHub zipball 解压到 [destination]，并校验插件身份。
     *
     * 归档必须只有一个顶层目录（GitHub 的惯例）；条目路径不得逃出 [destination]。
     * 校验失败时抛 [IllegalArgumentException]，由调用方清理未完成的目录。
     * @param archive zip 字节流。
     * @param destination 目标目录；调用方负责在失败时删除它。
     * @param expectedPackage 期望的包名，必须与归档清单一致。
     * @param expectedVersion 期望的版本，必须与归档清单一致。
     * @return 校验过的清单内容。
     */
    fun extract(
        archive: InputStream,
        destination: File,
        expectedPackage: String,
        expectedVersion: String,
    ): Contents {
        destination.mkdirs()
        val canonicalRoot = destination.canonicalFile
        var rootPrefix: String? = null
        var manifest: String? = null
        var entryCount = 0
        var writtenBytes = 0L

        ZipInputStream(archive).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                if (entryCount > MAX_ENTRIES) {
                    throw IllegalArgumentException("归档条目数超过 $MAX_ENTRIES")
                }
                val name = entry.name
                if (name.isBlank() || name.contains('\u0000')) {
                    throw IllegalArgumentException("归档含非法条目名")
                }
                val prefix = rootPrefix ?: run {
                    val detected = name.substringBefore('/', missingDelimiterValue = "")
                    if (detected.isBlank()) throw IllegalArgumentException("归档缺少顶层目录：$name")
                    (detected + "/").also { rootPrefix = it }
                }
                if (!name.startsWith(prefix)) {
                    throw IllegalArgumentException("归档含顶层目录之外的条目：$name")
                }
                val relative = name.removePrefix(prefix).trimEnd('/')
                if (relative.isBlank()) {
                    zip.closeEntry()
                    continue
                }
                val target = safeTarget(canonicalRoot, relative)
                if (entry.isDirectory) {
                    target.mkdirs()
                    zip.closeEntry()
                    continue
                }
                if (entry.size > MAX_UNCOMPRESSED_BYTES) {
                    throw IllegalArgumentException("归档条目过大：$relative")
                }
                target.parentFile?.mkdirs()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                target.outputStream().use { output ->
                    while (true) {
                        val read = zip.read(buffer)
                        if (read <= 0) break
                        writtenBytes += read
                        if (writtenBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw IllegalArgumentException("解压后体积超过 $MAX_UNCOMPRESSED_BYTES 字节")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                if (relative == MANIFEST_FILE) manifest = target.readText()
                zip.closeEntry()
            }
        }

        val manifestText = manifest
            ?: throw IllegalArgumentException("归档里没有 $MANIFEST_FILE")
        val parsed = runCatching {
            json.decodeFromString(ArchiveManifest.serializer(), manifestText)
        }.getOrElse { error ->
            throw IllegalArgumentException("$MANIFEST_FILE 无法解析：${error.message}")
        }
        if (parsed.name != expectedPackage) {
            throw IllegalArgumentException("包名不匹配：归档是 ${parsed.name}，期望 $expectedPackage")
        }
        if (parsed.version != expectedVersion) {
            throw IllegalArgumentException("版本不匹配：归档是 ${parsed.version}，期望 $expectedVersion")
        }
        val main = parsed.main.ifBlank { "lib/index.js" }.normalizeArchivePath()
        val patch = (parsed.dsh?.bundle?.patch
            ?: throw IllegalArgumentException("插件没有声明 dsh.bundle.patch")).normalizeArchivePath()
        requireFile(canonicalRoot, main, "入口")
        requireFile(canonicalRoot, patch, "bundle patch")
        return Contents(
            packageName = parsed.name,
            version = parsed.version,
            main = main,
            patch = patch,
            dependencies = parsed.dependencies.orEmpty(),
        )
    }

    /** 把归档内的相对路径映射成目标文件，并拒绝任何越界路径。 */
    private fun safeTarget(root: File, relative: String): File {
        if (relative.startsWith('/') || relative.startsWith('\\')) {
            throw IllegalArgumentException("归档含绝对路径：$relative")
        }
        val segments = relative.split('/')
        if (segments.any { it == ".." || it == "." || it.isEmpty() }) {
            throw IllegalArgumentException("归档含越界路径：$relative")
        }
        if (relative.contains('\\')) {
            throw IllegalArgumentException("归档含非法路径分隔符：$relative")
        }
        val target = File(root, relative)
        val canonical = runCatching { target.canonicalFile }.getOrElse {
            throw IllegalArgumentException("归档路径无法解析：$relative")
        }
        if (canonical != root && !canonical.toPath().startsWith(root.toPath())) {
            throw IllegalArgumentException("归档路径逃出目标目录：$relative")
        }
        return target
    }

    /** 要求归档里确实存在某个文件；清单里的路径同样不许逃出包目录。 */
    private fun requireFile(root: File, relative: String, label: String) {
        if (relative.isBlank()) throw IllegalArgumentException("插件缺少$label 路径")
        val file = safeTarget(root, relative)
        if (!file.isFile) throw IllegalArgumentException("插件缺少$label：$relative")
    }

    /** 清单里的路径可能带 `./` 前缀，统一成包内相对路径。 */
    private fun String.normalizeArchivePath(): String =
        trim().removePrefix("./").replace('\\', '/').trimStart('/')

    @Serializable
    private data class ArchiveManifest(
        val name: String = "",
        val version: String = "",
        val main: String = "",
        val dsh: ArchiveDsh? = null,
        val dependencies: Map<String, String>? = null,
    )

    @Serializable
    private data class ArchiveDsh(val bundle: ArchiveBundle? = null)

    @Serializable
    private data class ArchiveBundle(val patch: String? = null)
}
