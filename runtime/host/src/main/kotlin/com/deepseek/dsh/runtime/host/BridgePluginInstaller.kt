package com.deepseek.dsh.runtime.host

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * bridge 插件的发布信息（APK 内置）。
 *
 * 插件本身不进 APK：它从 GitHub 的 tag 源码归档安装。APK 只带这张小清单，说明
 * 「安装源在哪里」与「首次打包时的基准版本」，因此插件发版不需要重打 APK，也不需要
 * 重下 rootfs。
 */
@Serializable
data class BridgePluginManifest(
    /** 包名，必须与插件仓库 `package.json` 的 name 一致。 */
    val packageName: String = DEFAULT_PACKAGE,
    /**
     * 基准版本，与 rootfs 版本串里的 `-bridge` 段同义。
     *
     * 它**不是**更新目标：更新目标永远是插件仓库的最新发布 tag —— 这份清单随
     * APK 发布，会落后于插件仓库。字段保留是为了离线时说明「打包时用的是哪一版」。
     */
    val version: String = "",
    /** 安装源：GitHub 仓库地址，安装时按 `<repo>` 的 `v<version>` tag 下载归档。 */
    val repository: String = "",
) {
    companion object {
        /** 与插件仓库保持一致的默认包名。 */
        const val DEFAULT_PACKAGE = "dsh-mobile-bridge-plugin"

        /** 内置清单在 assets 里的路径。 */
        const val ASSET = "bridge-plugin.json"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 读取 APK 内置清单。
         * @param context 应用上下文。
         * @return 清单；缺失或损坏时返回 null，调用方据此隐藏更新入口。
         */
        fun load(context: Context): BridgePluginManifest? = runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            json.decodeFromString(BridgePluginManifest.serializer(), raw)
        }.getOrNull()
    }
}

/**
 * rootfs 内的 bridge 插件安装器。
 *
 * 安装 = 下载已发布的 tag 源码归档 → 校验 → 解压到 profile 的 `node_modules`。
 * 不在 Host 里跑 `dsh plugin add`：那条路依赖 proot 内的 git/pnpm 与 DNS，实测在
 * 手机上不可用（proot 内解析不到 github.com），而且 Host 的 pnpm 会重写 profile
 * 清单 —— 插件实际由 web-app 层的 patch 行挂载，profile 清单不是它的家。
 */
class BridgePluginInstaller(
    private val context: Context,
    private val layout: RuntimeLayout,
    private val downloader: BridgeReleaseDownloader = BridgeReleaseDownloader(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** profile 目录；DSH 在 rootfs 内的固定位置。 */
    private val profileDir: File
        get() = File(layout.rootfsDir, "root/.dsh/profiles/web")

    /**
     * 读取 rootfs 内已安装的插件版本。
     *
     * 直接读安装目录的 `package.json`：这是实际落盘的内容，比问 DSH 更快，也
     * 不依赖 Host 正在运行。
     * @param packageName 目标包名。
     * @return 版本号；未安装或读不到时为 null。
     */
    fun installedVersion(packageName: String = BridgePluginManifest.DEFAULT_PACKAGE): String? {
        val manifest = File(profileDir, "node_modules/$packageName/package.json")
        if (!manifest.isFile) return null
        return runCatching {
            val node = json.parseToJsonElement(manifest.readText())
            (node as? JsonObject)
                ?.get("version")
                ?.let { (it as? JsonPrimitive)?.content }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * 把插件的某个已发布版本装进 rootfs。
     *
     * 目标版本来自插件仓库的最新 release，而不是 APK 内置清单：清单随 APK 发布，
     * 必然落后于插件仓库。安装前先校验归档的包名/版本/入口，任何一步失败都不会
     * 改动已安装的插件。
     * @param repository GitHub 仓库地址（APK 清单里的 `repository`）。
     * @param version 已发布版本，例如 `1.1.0`。
     * @param onProgress 进度与步骤文案。
     * @return 成功后读到的插件版本。
     */
    suspend fun installFromRelease(
        repository: String,
        version: String,
        onProgress: (Float, String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetVersion = version.trim()
        val rootfsInstalled = File(layout.rootfsDir, "etc/os-release").isFile ||
            File(layout.rootfsDir, "usr/bin/env").isFile
        if (!rootfsInstalled) {
            return@withContext Result.failure(IllegalStateException("rootfs 未安装，无法更新插件"))
        }
        if (!profileDir.isDirectory) {
            return@withContext Result.failure(
                IllegalStateException("Host profile 尚未初始化：请先启动一次 Host 再更新插件")
            )
        }
        if (BridgeReleaseDownloader.archiveUrl(repository, targetVersion) == null) {
            return@withContext Result.failure(
                IllegalStateException("插件安装源或目标版本不可用：$repository#v$targetVersion")
            )
        }

        ensureProfilePresetLink(profileDir)

        // 上次中断可能留下 staging/backup 目录；它们不是安装的一部分，先清掉，
        // 免得坏掉的半装版本被下一次替换当成现状。
        val nodeModules = File(profileDir, "node_modules").apply { mkdirs() }
        nodeModules.listFiles()
            ?.filter { it.name.startsWith(".dsh-plugin-") }
            ?.forEach { runCatching { it.deleteRecursively() } }

        onProgress(0.02f, "准备下载插件 $targetVersion…")
        val archive = try {
            downloader.download(repository, targetVersion) { fraction, step ->
                onProgress(0.02f + fraction * 0.5f, step)
            }
        } catch (error: Exception) {
            return@withContext Result.failure(
                IllegalStateException("插件归档下载失败：${error.message}")
            )
        }

        onProgress(0.55f, "校验插件归档…")
        val target = File(nodeModules, BridgePluginManifest.DEFAULT_PACKAGE)
        val staging = File(nodeModules, ".dsh-plugin-incoming")
        staging.deleteRecursively()
        val contents = try {
            BridgePluginArchive.extract(
                archive = archive.inputStream(),
                destination = staging,
                expectedPackage = BridgePluginManifest.DEFAULT_PACKAGE,
                expectedVersion = targetVersion,
            )
        } catch (error: Exception) {
            staging.deleteRecursively()
            return@withContext Result.failure(
                IllegalStateException("插件归档校验失败：${error.message}")
            )
        }

        // 运行时依赖必须在 Host 的模块解析路径上已经存在，否则插件装上也会
        // 在 import 阶段失败。这里提前失败，已安装的插件保持不动。
        val missingDependencies = contents.dependencies.keys.filterNot { dependency ->
            File(nodeModules, dependency).exists()
        }
        if (missingDependencies.isNotEmpty()) {
            staging.deleteRecursively()
            return@withContext Result.failure(
                IllegalStateException(
                    "插件依赖 ${missingDependencies.joinToString()} 未随运行时提供；" +
                        "请先更新 Rootfs 镜像，再更新插件"
                )
            )
        }

        onProgress(0.8f, "替换已安装的插件…")
        try {
            replaceDirectory(target = target, staging = staging)
        } catch (error: Exception) {
            staging.deleteRecursively()
            return@withContext Result.failure(
                IllegalStateException("替换插件文件失败：${error.message}")
            )
        }

        onProgress(0.92f, "读取已安装版本…")
        val installed = installedVersion()
        if (installed != targetVersion) {
            return@withContext Result.failure(
                IllegalStateException(
                    "插件文件已替换，但读到的版本是 ${installed ?: "未知"}（期望 $targetVersion）"
                )
            )
        }
        onProgress(1f, "插件已更新到 $installed")
        Result.success(installed)
    }

    /**
     * 是否把 bridge 插件登记成 profile 的 bundle。
     *
     * bridge 的挂载点在 `dsh-web-app/cordis.patch.yml`，不依赖 profile 的
     * `package.json`/`bundles`；往 profile 写会在重启时把用户/官方安装的痕迹
     * 冲掉（此前每次启动都把 profile 恢复成模板，原因就在这里）。因此这里只读
     * 不写：确认插件目录存在即可，写 profile 的职责交给 Host 自己的安装路径。
     *
     * @param packageName 目标包名。
     * @return 插件目录是否存在。
     */
    fun ensureProfileEntry(
        packageName: String = BridgePluginManifest.DEFAULT_PACKAGE,
        @Suppress("UNUSED_PARAMETER") version: String? = null,
    ): Boolean {
        val inProfile = File(profileDir, "node_modules/$packageName")
        return File(inProfile, "package.json").isFile
    }

    /**
     * 用 [staging] 整目录替换 [target]。
     *
     * 先把旧目录改名让位，再让新目录就位：任何一步失败都会把旧目录放回去，
     * 因此「已安装的插件」要么是旧的、要么是新的，不会消失。
     * @param target 已安装的插件目录。
     * @param staging 已经校验过的新插件目录。
     */
    private fun replaceDirectory(target: File, staging: File) {
        val parent = target.parentFile ?: throw IllegalStateException("插件目录没有父目录")
        val backup = File(parent, ".dsh-plugin-previous-${System.nanoTime()}")
        val hadPrevious = target.exists()
        if (hadPrevious && !target.renameTo(backup)) {
            throw IllegalStateException("无法移开已安装的插件目录")
        }
        if (!staging.renameTo(target)) {
            if (hadPrevious) backup.renameTo(target)
            throw IllegalStateException("无法把新插件目录放到安装位置")
        }
        if (hadPrevious) backup.deleteRecursively()
    }
}

/**
 * 确保 profile 的 `node_modules/@deepseek-ai` 指向 Host 安装自带的 @deepseek-ai。
 *
 * profile 里的 bundle 与 bridge 插件都按名字解析 `@deepseek-ai/<name>`，而手机上没有
 * pnpm 源；这个符号链接是它们唯一的解析锚点。启动流程与插件安装都需要它，
 * 因此只在这里定义一次。
 * @param profileDir `root/.dsh/profiles/web`。
 */
internal fun ensureProfilePresetLink(profileDir: File) {
    val nodeModules = File(profileDir, "node_modules").apply { mkdirs() }
    val presetLink = File(nodeModules, "@deepseek-ai")
    // 旧版本/手工排查留下的备份目录必须清掉：Host 会把它们当作独立包扫描，
    // 在设置和插件页里显示重复条目。
    File(nodeModules, "@deepseek-ai.merged").deleteRecursively()
    // profiles/web -> profiles -> .dsh -> root -> rootfs 根
    val installed = File(
        profileDir,
        "../../../../usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai",
    ).canonicalFile
    if (!installed.isDirectory) return

    val presetPath = presetLink.toPath()
    // 悬空符号链接：link 文件还在，但目标已经不存在（rootfs 升级换掉目录、或旧
    // 相对深度的链接）。不先删掉，下面的 createSymbolicLink 会因「已存在」失败，
    // 而失败一被吞掉，profile 就永远解析不到 @deepseek-ai/*——Host 启动时报
    // 「N required plugins did not activate」，端口永远不出现。
    if (java.nio.file.Files.isSymbolicLink(presetPath) && !presetLink.exists()) {
        runCatching { java.nio.file.Files.deleteIfExists(presetPath) }
    }

    // 新 rootfs 自带一个真实的 @deepseek-ai 目录（只有 cosmokit/schemastery
    // 这类 hoisted 包）。它不是链接，Host 按名字解析官方 bundle 会失败。
    // 复制整个目录会打乱 profile 的依赖布局，改用符号链接：先备份真实目录，
    // 链接失败时再恢复并用复制兜底。
    // 链接位于 `node_modules/@deepseek-ai`，比 profileDir 多一层。
    val installedLinkTarget = "../../../../../usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai"
    if (presetLink.exists() && !java.nio.file.Files.isSymbolicLink(presetPath)) {
        val backup = File(nodeModules, "@deepseek-ai.local")
        if (backup.exists()) backup.deleteRecursively()
        if (!presetLink.renameTo(backup)) return
        val linked = runCatching {
            java.nio.file.Files.createSymbolicLink(
                presetPath,
                java.nio.file.Paths.get(installedLinkTarget),
            )
        }.isSuccess
        if (linked) {
            backup.deleteRecursively()
            return
        }
        // 链接不可用（文件系统限制）时恢复原目录，并合并缺失的包。
        backup.renameTo(presetLink)
        mergeInstalledPackages(installed, presetLink)
        return
    }
    if (presetLink.exists()) {
        File(nodeModules, "@deepseek-ai.local").deleteRecursively()
        return
    }
    val linked = runCatching {
        java.nio.file.Files.createSymbolicLink(
            presetPath,
            java.nio.file.Paths.get(installedLinkTarget),
        )
    }.isSuccess
    if (linked) return
    // 符号链接不可用：退化成真实目录 + 复制已安装包。不能悄悄什么都不做，
    // 否则 profile 缺官方 bundle，Host 启动必然失败（用户只看到一直 STARTING）。
    presetLink.mkdirs()
    mergeInstalledPackages(installed, presetLink)
}

/** 把 [installed] 里有、[target] 里没有的包复制过去（符号链接不可用时的兜底）。 */
private fun mergeInstalledPackages(installed: File, target: File) {
    installed.listFiles()?.forEach { source ->
        val destination = File(target, source.name)
        if (destination.exists()) return@forEach
        runCatching {
            if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = false)
            } else {
                source.copyTo(destination, overwrite = false)
            }
        }
    }
}
