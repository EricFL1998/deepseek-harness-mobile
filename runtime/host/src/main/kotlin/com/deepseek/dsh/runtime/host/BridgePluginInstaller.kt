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
 * 插件本身不进 APK：它从 GitHub 源码安装。APK 只带这张小清单，说明「期望
 * 哪个版本」和「从哪里装」，因此插件发版不需要重打 APK，也不需要重下 rootfs。
 */
@Serializable
data class BridgePluginManifest(
    /** 包名，必须与插件仓库 `package.json` 的 name 一致。 */
    val packageName: String = DEFAULT_PACKAGE,
    /** 期望版本，与 rootfs 版本串里的 `-bridge` 段比较。 */
    val version: String = "",
    /** 安装源：GitHub 仓库地址；安装时拼成 `<repo>#v<version>`。 */
    val repository: String = "",
    /** 插件在 profile 里的行 id，用于判断是否已装。 */
    val entryId: String = DEFAULT_PACKAGE,
) {
    /** 可直接交给 `dsh plugin add` 的安装 spec。 */
    fun installSpec(): String =
        if (version.isBlank()) repository else "$repository#v$version"

    companion object {
        /** 与插件仓库保持一致的默认包名。 */
        const val DEFAULT_PACKAGE = "dsh-mobile-bridge-plugin"

        /** 内置清单在 assets 里的路径。 */
        const val ASSET = "bridge-plugin.json"

        /** APK 内置的插件文件目录；启动时就地铺进 Host 安装目录。 */
        const val ASSET_DIR = "bridge-plugin"

        /** 插件的 bundle patch 文件名，用于校验安装完整性。 */
        const val PATCH_FILE = "cordis.patch.yml"

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
 * 只用 `dsh plugin add` —— 与人在 Host 里手敲的命令完全相同，因此走的是官方
 * 的安装、版本校验与 profile 写入路径，没有第二套事实来源。
 */
class BridgePluginInstaller(
    private val context: Context,
    private val layout: RuntimeLayout,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** profile 目录；DSH 在 rootfs 内的固定位置。 */
    private val profileDir: File
        get() = File(layout.rootfsDir, "root/.dsh/profiles/web")

    /**
     * 确保 profile 里有 bridge 插件条目，然后才启动 Host。
     *
     * DSH 在启动时会核对 profile：依赖装不上（例如 git 源在设备上取不到）就会被
     * 清掉，于是插件在 Host 里彻底消失、bridge 路由 404。插件的存在属于客户端的
     * 契约责任，所以在每次启动前显式补齐，不依赖上一次运行留下的状态。
     *
     * 只登记真正可以加载的包：先确认宿主安装目录或 profile 的 `node_modules` 里有
     * 它，否则写进 bundles 只会让 Host 启动报错。
     * @return 是否补上了条目。
     */
    fun ensureProfileEntry(packageName: String = BridgePluginManifest.DEFAULT_PACKAGE): Boolean {
        val inHostInstall = File(hostInstallModulesDir(), packageName)
        val inProfile = File(profileDir, "node_modules/$packageName")
        if (!File(if (inHostInstall.isDirectory) inHostInstall else inProfile, "package.json").isFile) {
            return false
        }
        val manifestFile = File(profileDir, "package.json")
        if (!manifestFile.isFile) return false
        return runCatching {
            val root = json.parseToJsonElement(manifestFile.readText()) as JsonObject
            val dsh = root["dsh"] as? JsonObject ?: return@runCatching false
            val profile = dsh["profile"] as? JsonObject ?: return@runCatching false
            val bundles = (profile["bundles"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?: emptyList()
            if (bundles.contains(packageName)) return@runCatching false

            val updatedProfile = JsonObject(
                profile + mapOf(
                    "bundles" to JsonArray(bundles.map { JsonPrimitive(it) } + JsonPrimitive(packageName))
                )
            )
            val updatedRoot = JsonObject(root + mapOf("dsh" to JsonObject(dsh + mapOf("profile" to updatedProfile))))
            manifestFile.writeText(json.encodeToString(JsonObject.serializer(), updatedRoot))
            true
        }.getOrDefault(false)
    }

    /**
     * 把 APK 内置的插件文件铺进 Host 的安装目录。
     *
     * 为什么放在安装目录而不是 profile：DSH 启动时会核对 profile 的外部依赖，装不
     * 上的会被移除；而设备上没有可用的 pnpm/git 源。安装目录里的包会被当作「随
     * DSH 一起发布的 bundle」直接解析，不经过那次核对，因此能稳定加载。
     *
     * 文件按版本整目录替换：内容变了才重写，避免每次启动都复制十几万字节。
     * @param packageName 目标包名。
     * @return 目标目录是否可用。
     */
    fun ensureInstalledInHost(packageName: String = BridgePluginManifest.DEFAULT_PACKAGE): Boolean {
        if (!File(layout.rootfsDir, "etc/os-release").isFile &&
            !File(layout.rootfsDir, "usr/bin/env").isFile
        ) {
            return false
        }
        val version = BridgePluginManifest.load(context)?.version.orEmpty()
        val target = File(hostInstallModulesDir(), packageName)
        val stamp = File(target, ".bundled-version")
        if (target.isDirectory && stamp.isFile && stamp.readText().trim() == version) {
            // 版本一致且文件齐备：不必重拷。
            if (File(target, "package.json").isFile &&
                File(target, BridgePluginManifest.PATCH_FILE).isFile
            ) {
                return true
            }
        }
        return runCatching {
            target.deleteRecursively()
            target.mkdirs()
            copyAssetTree(BridgePluginManifest.ASSET_DIR, target)
            stamp.writeText(version)
            File(target, "package.json").isFile && File(target, BridgePluginManifest.PATCH_FILE).isFile
        }.getOrDefault(false)
    }

    /** 把 assets 下的一棵树复制到磁盘目录，保留相对路径。 */
    private fun copyAssetTree(assetRoot: String, target: File) {
        val children = context.assets.list(assetRoot).orEmpty()
        if (children.isEmpty()) {
            // 叶子：是文件而不是目录。
            val output = target
            output.parentFile?.mkdirs()
            context.assets.open(assetRoot).use { input ->
                output.outputStream().use { out -> input.copyTo(out) }
            }
            return
        }
        target.mkdirs()
        children.forEach { child -> copyAssetTree("$assetRoot/$child", File(target, child)) }
    }

    /** DSH 安装自带的 bundle 目录；随安装发布，不参与 profile 依赖核对。 */
    private fun hostInstallModulesDir(): File = File(
        layout.rootfsDir,
        "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules",
    )

    /**
     * 读取 rootfs 内已安装的插件版本。
     *
     * 直接读安装目录的 `package.json`：这是 pnpm 实际落盘的内容，比问 DSH
     * 更快且不依赖 Host 正在运行。
     * @return 版本号；未安装或读不到时为 null。
     */
    fun installedVersion(packageName: String = BridgePluginManifest.DEFAULT_PACKAGE): String? {
        val manifest = File(profileDir, "node_modules/$packageName/package.json")
        if (!manifest.isFile) return null
        return runCatching {
            val node = json.parseToJsonElement(manifest.readText())
            (node as? kotlinx.serialization.json.JsonObject)
                ?.get("version")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrNull()
    }

    /**
     * 在 rootfs 内安装或更新插件。
     *
     * @param spec `dsh plugin add` 的安装 spec（通常是 `<repo>#v<version>`）。
     * @param onProgress 进度与步骤文案。
     * @return 成功后读到的插件版本。
     */
    suspend fun install(
        spec: String,
        onProgress: (Float, String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!File(layout.rootfsDir, "etc/os-release").isFile &&
            !File(layout.rootfsDir, "usr/bin/env").isFile
        ) {
            return@withContext Result.failure(IllegalStateException("rootfs 未安装，无法更新插件"))
        }
        if (!layout.prootBinary.isFile) {
            return@withContext Result.failure(
                IllegalStateException("缺少 ${layout.prootBinary.name}，无法在 rootfs 内执行命令")
            )
        }
        onProgress(0.05f, "准备在 rootfs 内安装插件…")
        prepareHostDirectories()
        val argv = listOf(
            "/usr/local/bin/node",
            "/usr/local/lib/node_modules/@deepseek-ai/dsh/lib/bin.js",
            "plugin",
            "--profile",
            "web",
            "add",
            spec,
        )
        val result = runProot(argv, timeoutMs = BRIDGE_INSTALL_TIMEOUT_MS) { fraction, step ->
            // 安装耗时主要在网络与 pnpm 解析；把命令输出阶段映射到 0.1..0.9。
            onProgress(0.1f + fraction * 0.8f, step)
        }
        result.fold(
            onSuccess = {
                onProgress(0.95f, "读取已安装版本…")
                val version = installedVersion()
                    ?: return@fold Result.failure(
                        IllegalStateException("安装命令成功，但读不到插件版本；请检查 profile 目录")
                    )
                onProgress(1f, "插件已更新到 $version")
                Result.success(version)
            },
            onFailure = { error ->
                Result.failure(IllegalStateException("插件更新失败：${error.message}"))
            },
        )
    }

    /**
     * 预建插件管理器要用的目录。
     *
     * 在模拟器上实测到：`.plugin-manager/logs` 不存在时，安装器里的
     * `mkdtemp` 会拿到 `ENOSYS`（proot 在「父目录缺失」的路径上创建临时目录会
     * 这样失败），于是整次安装以「退出码 1」结束。目录建好后同一命令立刻成功，
     * 因此这不是权限问题，而是目录不存在。
     *
     * 这些目录由 DSH 自己维护，提前建出来是幂等的：已存在就什么都不做。
     */
    private fun prepareHostDirectories() {
        val dshHome = File(layout.rootfsDir, "root/.dsh")
        listOf(
            File(dshHome, "tmp"),
            File(dshHome, "profiles/web/.plugin-manager/logs"),
        ).forEach { directory ->
            runCatching { directory.mkdirs() }
        }
    }

    /**
     * 在 rootfs 里执行一条 DSH 命令。
     *
     * 复用 [ProotCommandBuilder] 的绑定与环境保护，只替换尾部命令，因此插件
     * 安装看到的是与运行 Host 完全相同的环境。
     */
    private fun runProot(
        argv: List<String>,
        timeoutMs: Long,
        onOutput: (Float, String) -> Unit,
    ): Result<String> {
        val builder = ProotCommandBuilder.buildExec(layout, argv)
        val process = runCatching {
            ProcessBuilder(builder.command).apply {
                environment().putAll(builder.environment)
                redirectErrorStream(true)
            }.start()
        }.getOrElse { error ->
            return Result.failure(IllegalStateException("无法启动 proot：${error.message}"))
        }
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { stream ->
                    stream.forEachLine { line ->
                        synchronized(output) { output.appendLine(line) }
                        onOutput(0.5f, line.take(120))
                    }
                }
            }
        }
        reader.isDaemon = true
        reader.start()
        val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return Result.failure(IllegalStateException("插件安装超时（${timeoutMs / 1000}s）"))
        }
        reader.join(2_000)
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val tail = synchronized(output) { output.toString() }.trim().takeLast(600)
            return Result.failure(IllegalStateException("退出码 $exitCode：$tail"))
        }
        return Result.success(synchronized(output) { output.toString() })
    }

    private companion object {
        /** 插件安装要拉 GitHub tarball 并跑 pnpm，给足 5 分钟。 */
        const val BRIDGE_INSTALL_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
