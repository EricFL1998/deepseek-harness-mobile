package com.deepseek.dsh.runtime.host

import android.content.Context
import android.content.Intent
import com.deepseek.dsh.runtime.bridge.BridgeServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * v4.0 设计文档 §4 & §10.2: 本地运行时管理器实现（联动设备能力桥 BridgeServer）
 */
class DefaultHostRuntimeManager(
    private val context: Context,
    private val layout: RuntimeLayout,
    private val runner: HostProcessRunner = HostProcessRunner(),
    private val prober: ReadinessProber = ReadinessProber()
) : HostRuntimeManager {

    private var bridgeServer: BridgeServer? = null
    private val startMutex = Mutex()

    /** 清单来源：实时拉取，失败时退缓存，最后才用 APK 兜底。 */
    private val manifestSourceCache by lazy { RootfsManifestSource(context, layout) }

    private fun manifestSource(): RootfsManifestSource = manifestSourceCache

    override suspend fun getStatus(): RuntimeStatus {
        return runner.statusFlow.value
    }

    override suspend fun isRootfsInstalled(): Boolean = withContext(Dispatchers.IO) {
        // 只看本地文件与安装标记：这里判断的是「装没装」，不该因为离线而改变结论。
        runCatching {
            val installer = RootfsInstaller(layout)
            val installed = installer.installedVersion() ?: return@runCatching false
            val marker = installer.readMarker()
            val currentAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            if (!marker?.abi.isNullOrBlank() && currentAbi.isNotBlank() && marker.abi != currentAbi) {
                return@runCatching false
            }
            File(layout.rootfsDir, "etc/os-release").isFile ||
                File(layout.rootfsDir, "usr/bin/env").isFile
        }.getOrDefault(false)
    }

    override suspend fun installedRootfsVersion(): String? = withContext(Dispatchers.IO) {
        RootfsInstaller(layout).installedVersion()
    }

    override suspend fun availableRootfsVersion(): String? = withContext(Dispatchers.IO) {
        // 实时拉取：这个方法回答的是「最新可用是什么」，缓存值会让它报旧版本。
        // 离线时 resolve 仍会退回缓存，因此不会因为断网就变成 null。
        runCatching {
            manifestSource().resolve(forceRefresh = true)?.manifest?.runtimeVersion
        }.getOrNull()
    }

    override suspend fun isRootfsUpdateAvailable(): Boolean = withContext(Dispatchers.IO) {
        val installed = installedRootfsVersion()
        val available = availableRootfsVersion()
        installed != null && available != null && installed != available
    }

    override suspend fun rootfsUpdateKind(): RootfsUpdateKind = withContext(Dispatchers.IO) {
        decideRootfsUpdateKind(installedRootfsVersion(), availableRootfsVersion())
    }

    override suspend fun installedBridgeVersion(): String? = withContext(Dispatchers.IO) {
        runningBridgeVersion() ?: BridgePluginInstaller(context, layout).installedVersion()
    }

    /**
     * 读 Host **正在运行**的插件版本。
     *
     * 落盘的 `package.json` 与运行中的代码可能不一致（装完还没重启），而更新判断
     * 关心的是「现在跑的是哪一版」。Host 未运行、bridge 不可达或插件没加载时报 null，
     * 调用方退回落盘版本。
     */
    private suspend fun runningBridgeVersion(): String? = runCatching {
        HostRuntimeHolder.runningBridgeVersionProvider?.invoke()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    override suspend fun assessUpdates(): UpdateAssessment = withContext(Dispatchers.IO) {
        // 每一步各自容错：查询插件仓库要联网、读 rootfs 要访问文件系统，
        // 任何一步失败都不该让整页版本信息变成「未知」。
        val manifest = runCatching { BridgePluginManifest.load(context) }.getOrNull()
        val latest = manifest?.repository
            ?.takeIf { it.isNotBlank() }
            ?.let { repository ->
                runCatching { BridgeReleaseChecker().latestVersion(repository) }.getOrNull()
            }
        val installedRootfs = runCatching { installedRootfsVersion() }.getOrNull()
        val availableRootfs = runCatching { availableRootfsVersion() }.getOrNull()
        val installedPlugin = runCatching {
            runningBridgeVersion() ?: BridgePluginInstaller(context, layout).installedVersion()
        }.getOrNull()
        assessUpdate(
            installed = installedRootfs,
            available = availableRootfs,
            latestPlugin = latest,
            installedPlugin = installedPlugin,
        )
    }

    override suspend fun updateBridgePlugin(
        version: String,
        onProgress: (Float, String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        val manifest = BridgePluginManifest.load(context)
            ?: return@withContext Result.failure(
                IllegalStateException("APK 未内置插件清单（assets/${BridgePluginManifest.ASSET}）")
            )
        if (manifest.repository.isBlank()) {
            return@withContext Result.failure(IllegalStateException("插件清单未配置安装源"))
        }
        BridgePluginInstaller(context, layout).installFromRelease(
            repository = manifest.repository,
            version = version,
            onProgress = onProgress,
        )
    }

    override suspend fun installRootfs(onProgress: (Float, String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        layout.ensureDirectories()
        val installer = RootfsInstaller(layout)
        // 清单实时拉取：下载地址与校验值随每次发版变化，内置的那份会过期，
        // 用它去下载只会得到 404 或校验失败。装不上就直接报错，不猜。
        onProgress(0.01f, "检查最新 rootfs 版本...")
        val resolved = try {
            manifestSource().resolve(forceRefresh = true)
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalStateException("获取 rootfs 清单失败: ${e.message}")
            )
        } ?: return@withContext Result.failure(
            IllegalStateException("无法获取 rootfs 清单：网络不可用，且没有本地缓存")
        )
        val manifest = resolved.manifest
        if (resolved.origin == RootfsManifestSource.Origin.BUNDLED) {
            onProgress(0.01f, "使用 APK 内置清单（可能已过期）…")
        }
        onProgress(0f, "准备安装 ${manifest.baseDistro.ifBlank { "rootfs" }}...")

        // App 自带 rootfs 优先；否则按 ABI 下载首次运行所需的归档。
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull {
            manifest.assetArchives.containsKey(it) || manifest.downloadUrls.containsKey(it)
        }
        val assetPath = abi?.let { manifest.assetArchives[it] }
        val result = if (abi != null && !assetPath.isNullOrBlank()) {
            val cacheArchive = File(layout.cacheDir, "rootfs-$abi.tar.gz")
            val expectedSha = manifest.assetSha256[abi]
            if (!cacheArchive.isFile || cacheArchive.length() == 0L) {
                onProgress(0.02f, "释放内置 rootfs ($abi)...")
                try {
                    context.assets.open(assetPath).use { input ->
                        cacheArchive.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 1 shl 20)
                        }
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        IllegalStateException("释放内置 rootfs 失败: ${e.message}")
                    )
                }
            }
            installer.installFromArchive(manifest, cacheArchive, expectedSha) { progress ->
                onProgress(progress.progress, progress.step)
            }
        } else {
            installer.install(manifest, abi = abi) { progress ->
                onProgress(progress.progress, progress.step)
            }
        }
        if (result.isSuccess) {
            applyBundledRuntimePatches()
            runner.markInstalled()
            if (!layout.prootBinary.isFile) {
                onProgress(1f, "rootfs 已安装；缺少 ${manifest.prootLibrary}（需按 ABI 打包到 jniLibs）")
            }
        }
        result
    }

    /**
     * 清理上次崩溃遗留的 proot/node 子进程；多个 proot 同时操作同一 rootfs 会触发
     * glibc "double free or corruption"。
     */
    private fun killStaleHostProcesses() {
        val procRoot = File("/proc")
        procRoot.listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            if (pid == android.os.Process.myPid()) return@forEach
            val cmdline = runCatching { File(dir, "cmdline").readText() }.getOrNull() ?: return@forEach
            if (cmdline.contains("libproot-userland") || cmdline.contains("dsh/lib/bin.js")) {
                runCatching { android.os.Process.sendSignal(pid, 9) }
            }
        }
        Thread.sleep(50)
    }

    /**
     * App 侧补丁：修复 DSH 在 proot/Android 下会触发 ENOSYS 的路径解析、
     * 文件锁释放与 spill 临时目录逻辑，无需重新下载整份 rootfs。
     */
    private fun applyBundledRuntimePatches() {
        // 补丁只在版本变化时复制，避免每次启动都重写 ~1MB JS 拖慢 Host。
        val patchMarker = File(layout.rootfsDir, "root/.dsh/.runtime-patch-revision")
        val patchRevision = "22"
        if (patchMarker.isFile && patchMarker.readText().trim() == patchRevision) return
        val patches = mapOf(
            "rootfs-patches/dsh-app-boot-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-app-boot/lib/index.js",
            "rootfs-patches/dsh-spill-local-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-spill-local/lib/index.js",
            "rootfs-patches/dsh-atomic-write-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-atomic-write/lib/index.js",
            "rootfs-patches/dsh-plugin-package-inventory-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-plugin-package-inventory-deepseek/lib/index.js",
            "rootfs-patches/dsh-subprocess-output.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-subprocess-local/lib/output.js",
            "rootfs-patches/dsh-subprocess-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-subprocess-local/lib/index.js",
            "rootfs-patches/dsh-subprocess-runner-launch.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-subprocess-local/lib/runner-launch-B2zsQ1Dz.js",
            "rootfs-patches/dsh-fs-local-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-fs-local/lib/index.js",
            "rootfs-patches/dsh-tool-bash-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-tool-bash/lib/index.js",
            "rootfs-patches/dsh-tool-fs-search-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-tool-fs-search/lib/index.js",
            "rootfs-patches/dsh-storage-json-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-storage-json/lib/index.js",
            // 客户端模块的包解析在 Android/proot 下走 Loader 解析器会全部落空，
            // 启动清单因此为空、浏览器外壳起不来；这份补丁改成从 tree 的
            // node_modules 直接读清单。
            "rootfs-patches/dsh-client-modules-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-client-modules/lib/index.js",
        )
        patches.forEach { (asset, relative) ->
            val target = File(layout.rootfsDir, relative)
            runCatching {
                target.parentFile?.mkdirs()
                context.assets.open(asset).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        File(layout.rootfsDir, "root/.dsh/tmp").mkdirs()
        runCatching {
            patchMarker.parentFile?.mkdirs()
            patchMarker.writeText(patchRevision)
        }
    }

    /**
     * 把 bridge 插件挂进 web-app 这一层，而不是 profile 的 bundles。
     *
     * profile 的 `dsh.profile.bundles` 在每次启动时会被核对：设备上没有可用的
     * pnpm/git 源，外部依赖判定不成立，条目被删掉，bridge 路由随之 404 —— 实测
     * 重启一次就复现。web-app 自己的 patch 属于「随安装发布的状态」，不受那次
     * 核对影响，因此插件在这一层是稳定的。
     *
     * 幂等：已经追加过就不再重复写，避免文件无限增长。
     */

    /**
     * 给已安装的第三方插件补一行 Loader 行。
     *
     * 插件装进 profile 的 node_modules 之后，Host 不会自己把它挂上：profile 的
     * `dsh.profile.bundles` 每次启动都会被核对，设备上没有 pnpm/git 源时外部依赖判定
     * 不成立、条目被删；profile 的 `cordis.patch.yml` 也会被运行时重写。web-app 的
     * patch 属于「随安装发布的状态」，不受这两处影响，因此第三方插件的行写在这一层。
     *
     * 只处理非 `@deepseek-ai/` 的包，并且只在包里确实带客户端半边（`dsh.client`）时
     * 补行——那是「这个包会在界面里出现」的判据。幂等：行已存在就跳过。
     */
    private fun applyInstalledPluginRows() {
        val webAppPatch = File(
            layout.rootfsDir,
            "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/" +
                "@deepseek-ai/dsh-web-app/cordis.patch.yml",
        )
        if (!webAppPatch.isFile) return
        val profileModules = File(layout.rootfsDir, "root/.dsh/profiles/web/node_modules")
        val packages = profileModules.listFiles()?.filter { it.isDirectory } ?: return
        runCatching {
            var current = webAppPatch.readText()
            // ① 先清掉本类历次追加过的独立挂载行（形状见 prunePluginRows）。
            //
            // 不清会出两类故障：
            // - 「行在、包不在」的残留在 Loader 里创建失败 entry，整个 Web 客户端
            //   停在启动失败页；
            // - 同一包重复挂载（scoped 包的引号写法、安装器自己的 marker 块）会让
            //   同一个插件被 apply 两次 —— 例如 genui 第二次注册同名 prompt section
            //   直接抛错，插件被禁用。
            // 只删「本类自己追加的」行：插件作者随包发布的 patch 内容（安装器按
            // marker 原样写入）形状完全一样，必须保护，否则会删掉插件自己的挂载行。
            val protectedPatches = readInstalledBundlePatches(profileModules)
            val (pruned, removed) = prunePluginRows(current, protectedPatches)
            if (removed.isNotEmpty()) {
                current = pruned
                webAppPatch.writeText(current)
            }
            // ② 再补齐已安装插件的挂载行（幂等）。安装器自己的 marker 块里已经
            // 挂过的包（任何引号写法）跳过，不重复挂。
            val appended = StringBuilder()
            for (dir in packages) {
                val name = dir.name
                // 官方包由 profile 自带的 bundle 层负责，重复挂一行会加载两次；
                // 第三方 scoped 包（@scope/name）要照挂，只排除官方 scope。
                if (name == "@deepseek-ai" || name.startsWith(".")) continue
                if (name.startsWith("@")) {
                    val scopeDir = dir
                    scopeDir.listFiles()?.forEach { scoped ->
                        if (!scoped.isDirectory) return@forEach
                        val scopedName = "${name}/${scoped.name}"
                        if (!declaresClientHalf(scoped)) return@forEach
                        if (rowMounted(current, scopedName) || appended.contains(scopedName)) return@forEach
                        appended.appendLine().append("- insert:").appendLine().append("    - id: \"").append(scopedName).append("\"")
                            .appendLine().append("      name: \"").append(scopedName).append("\"").appendLine()
                    }
                    continue
                }
                if (name == "dsh-mobile-bridge-plugin") continue
                if (rowMounted(current, name) || appended.contains(name)) continue
                if (!declaresClientHalf(dir)) continue
                appended.appendLine().append("- insert:").appendLine().append("    - id: ").append(name)
                    .appendLine().append("      name: ").append(name).appendLine()
            }
            if (appended.isNotEmpty()) webAppPatch.appendText(appended.toString())
        }
    }

    /**
     * 读取已安装插件随包发布的 cordis patch 文本（`package.json` 的
     * `dsh.bundle.patch`）。
     *
     * 安装器会把这些文本原样写进 web-app patch（带 `# android-installed:` 标记）。
     * 它们和本类追加的独立行形状可能完全相同，清理时必须按内容保护，不能只看形状。
     */
    private fun readInstalledBundlePatches(profileModules: File): List<String> {
        val texts = mutableListOf<String>()
        fun readPackage(dir: File) {
            val manifest = File(dir, "package.json")
            val text = runCatching { manifest.readText() }.getOrNull() ?: return
            val patchPath = runCatching {
                org.json.JSONObject(text)
                    .optJSONObject("dsh")
                    ?.optJSONObject("bundle")
                    ?.optString("patch")
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull() ?: return
            val file = File(dir, patchPath)
            runCatching { file.readText() }.getOrNull()?.let { texts += it }
        }
        for (dir in profileModules.listFiles() ?: emptyArray()) {
            if (!dir.isDirectory) continue
            if (dir.name.startsWith("@")) {
                dir.listFiles()?.filter { it.isDirectory }?.forEach(::readPackage)
            } else {
                readPackage(dir)
            }
        }
        return texts
    }

    /** patch 里是否已经挂过这个包的行（`name:` 可能带单/双引号，也可能裸写）。 */
    private fun rowMounted(patchText: String, packageName: String): Boolean =
        patchText.contains("name: $packageName") ||
            patchText.contains("name: \"$packageName\"") ||
            patchText.contains("name: '$packageName'")

    /**
     * 一份 `package.json` 是否声明了浏览器半边（`dsh.client`）。
     *
     * 判据用 JSON 结构而不是文本包含：`"dsh"`/`"client"` 这样的子串在依赖名或
     * description 里也会出现，误判会把纯 Host 服务当 UI 插件挂进浏览器。
     */
    private fun declaresClientHalf(dir: File): Boolean {
        val manifest = File(dir, "package.json")
        val text = runCatching { manifest.readText() }.getOrNull() ?: return false
        return runCatching {
            val root = org.json.JSONObject(text)
            val dsh = root.optJSONObject("dsh") ?: return@runCatching false
            dsh.has("client")
        }.getOrDefault(false)
    }

    /**
     * 清掉本类追加过的独立挂载块（全部，不做保留判断）。
     *
     * 只认同一种形状：顶层 `- insert:` 紧跟两行 `- id: X` / `name: X`，且后面
     * 没有更多字段（补丁自带的 insert 块通常在 name 之后还有 config/disabled
     * 或并列的多条 row；安装器写入的 marker 块也带 `# android-installed:` 注释
     * 和插件自己的多行 patch，因此都不会被误删）。官方 scope 的包不碰。
     *
     * 调用方随后会按当前 profile 目录重建需要的行，因此这里不区分「包是否还在」：
     * 先全删，避免同一形状在多次启动/安装之间累积成重复 entry。
     *
     * @param text patch 文件内容。
     * @param protectedPatches 插件随包发布的 patch 文本；这些内容原样保留。
     * @returns 处理后的内容与被移除的包名。
     */
    private fun prunePluginRows(
        text: String,
        protectedPatches: List<String>,
    ): Pair<String, List<String>> {
        val lines = text.lines().toMutableList()
        val removed = mutableListOf<String>()
        var index = 0
        while (index + 2 < lines.size) {
            if (lines[index] != "- insert:") {
                index += 1
                continue
            }
            val idLine = lines[index + 1]
            val nameLine = lines[index + 2]
            if (!idLine.startsWith("    - id: ") || !nameLine.startsWith("      name: ")) {
                index += 1
                continue
            }
            val following = lines.getOrNull(index + 3)
            val standalone = following == null || following.isBlank() ||
                following.startsWith("- ") || following.startsWith("#")
            if (!standalone) {
                index += 1
                continue
            }
            // 插件作者随包发布的行：内容完全一致时保留。
            val blockText = "${lines[index]}\n${lines[index + 1]}\n${lines[index + 2]}"
            if (protectedPatches.any { it.contains(blockText) }) {
                index += 3
                continue
            }
            val packageName = nameLine.removePrefix("      name: ").trim().trim('"', '\'')
            if (packageName.isEmpty() || packageName.startsWith("@deepseek-ai/")) {
                index += 3
                continue
            }
            removed += packageName
            repeat(3) { if (index < lines.size) lines.removeAt(index) }
            if (index < lines.size && lines[index].isBlank()) lines.removeAt(index)
        }
        return lines.joinToString("\n") to removed
    }
    private fun applyBridgeBundleRow() {
        val webAppPatch = File(
            layout.rootfsDir,
            "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/" +
                "@deepseek-ai/dsh-web-app/cordis.patch.yml",
        )
        if (!webAppPatch.isFile) return
        val row = runCatching {
            context.assets.open("rootfs-patches/bridge-bundle-row.yml").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        runCatching {
            val current = webAppPatch.readText()
            if (current.contains(BRIDGE_BUNDLE_ID)) return
            webAppPatch.appendText("\n$row\n")
        }
    }

    private companion object {
        /** bridge 插件在 cordis 树里的行 id；用于幂等判断。 */
        const val BRIDGE_BUNDLE_ID = "id: dsh-mobile-bridge-plugin"
    }

    override suspend fun start(workspacePath: String): Result<RuntimeStatus> = startMutex.withLock {
        if (runner.isRunning()) return@withLock Result.success(runner.statusFlow.value)
        withContext(Dispatchers.IO) {
        layout.ensureDirectories()

        val hasRootfs = File(layout.rootfsDir, "etc/os-release").isFile ||
            File(layout.rootfsDir, "usr/bin/env").isFile
        if (!hasRootfs) {
            return@withContext Result.failure(
                IllegalStateException("rootfs 未安装：请先在运行控制页安装 Rootfs")
            )
        }
        applyBundledRuntimePatches()
        // 插件挂载层每次启动都核对一遍：卸载留下的「id 在、包不在」的 Loader 行
        // 会让整个 Web 客户端停在启动失败页（一个失败的 entry 就够），而补丁
        // 文件是持久化的，不修就一直是坏的。这是幂等的文件核对，成本只有一次读。
        applyBridgeBundleRow()
        applyInstalledPluginRows()
        // bridge 插件由 rootfs 内置或 GitHub 安装；APK 不再内置代码。
        runCatching {
            val installer = BridgePluginInstaller(context, layout)
            installer.ensureProfileEntry()
        }
        killStaleHostProcesses()
        if (!layout.prootBinary.isFile) {
            return@withContext Result.failure(
                IllegalStateException("缺少 ${layout.prootBinary.name}：需把对应 ABI 的 proot 打包到 jniLibs")
            )
        }
        // proot 的 bind 目标需要存在；DSH 配置目录先创建，避免首次启动 mkdir 失败。
        File(layout.rootfsDir, "root/.dsh/profiles/web").mkdirs()
        // profile 需要从自身目录解析 @deepseek-ai/* bundle；链接到 rootfs 全局 node_modules。
        ensureProfilePresetLink(File(layout.rootfsDir, "root/.dsh/profiles/web"))
        File(layout.rootfsDir, "root/.dsh/sessions/--workspace--").mkdirs()
        File(layout.rootfsDir, "root/.dsh/storages").mkdirs()
        File(layout.rootfsDir, "root/.dsh/storages/session-projection").mkdirs()
        File(workspacePath).mkdirs()
        // proot 需要能 chdir 进 bind 目标；rootfs 内 /workspace 必须是可进入目录。
        File(layout.rootfsDir, "workspace").apply {
            mkdirs()
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }
        listOf("spill", "launch", "shell", "staging").forEach {
            File(layout.rootfsDir, "root/.dsh/tmp/$it").mkdirs()
        }
        File(layout.rootfsDir, "root/.dsh/tmp").mkdirs()
        // rootfs 内没有 Android 的 DNS 配置，登录/模型请求会因解析失败报 network。
        File(layout.rootfsDir, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        // 把域名预先解析并写入 hosts，绕开 proot 内 glibc DNS 的 EAI_AGAIN。
        //
        // 这里不只列平台域名：插件页「添加插件」会在 Host 内跑 git/pnpm，它们
        // 同样只认 hosts。缺了 GitHub 与 npm registry 的条目，安装会停在
        // 「Could not resolve host」——这也是插件页安装失败的根因。每次启动
        // 重新解析，IP 变了也能跟上。
        runCatching {
            val hosts = File(layout.rootfsDir, "etc/hosts")
            val hostNames = listOf(
                // 平台账号与模型
                "platform.deepseek.com",
                "api.deepseek.com",
                // 官方插件安装：git 仓库与源码归档
                "github.com",
                "codeload.github.com",
                "api.github.com",
                "raw.githubusercontent.com",
                // 依赖包：官方 npm 与国内镜像
                "registry.npmjs.org",
                "registry.npmmirror.com",
            )
            val entries = hostNames.mapNotNull { host ->
                runCatching { java.net.InetAddress.getByName(host).hostAddress }.getOrNull()
                    ?.let { "$it $host" }
            }
            if (entries.isNotEmpty()) {
                hosts.writeText(entries.joinToString("\n", postfix = "\n"))
            }
        }
        // 上次异常退出可能残留 credentials 写锁，启动前清理。
        File(layout.rootfsDir, "root/.dsh").listFiles()
            ?.filter { it.name.startsWith(".credentials.yaml.lock") }
            ?.forEach { it.delete() }

        // 1. 启动本地 BridgeServer 并获得 loopback 端口
        val bridge = BridgeServer(context)
        val bridgePort = bridge.start()
        bridgeServer = bridge

        // 2. 启动 Android 前台服务保证生命周期 (v4.0 §4.4)
        val serviceIntent = Intent(context, HostService::class.java).apply {
            action = HostService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 3. 将 DSH_ANDROID_BRIDGE_PORT 环境变量注入 proot 容器
        val launchConfig = ProotCommandBuilder.LaunchConfig(
            layout = layout,
            userWorkspacePath = workspacePath,
            customEnv = mapOf(
                "DSH_ANDROID_BRIDGE_PORT" to bridgePort.toString()
            )
        )
        val cmd = ProotCommandBuilder.build(launchConfig)
        // 通过 shell 启动：直接 exec proot 时会继承 App 进程的 linker 环境，
        // 导致 proot 内 Node 的模块解析与手动执行不一致。
        val launchCmd = listOf(
            "/system/bin/sh",
            "-c",
            cmd.joinToString(" ") { arg ->
                if (arg.any { it.isWhitespace() }) "'" + arg.replace("'", "'\\''") + "'" else arg
            }
        )

        val logFile = File(layout.logsDir, "host_${System.currentTimeMillis()}.log")
        logFile.appendText("[CMD] ${cmd.joinToString(" ")}\n")
        val endpointDeferred = CompletableDeferred<ProcessOutputParser.ParsedEndpoint>()

        val launchResult = runner.launchProcess(
            command = cmd,
            workingDir = layout.baseDir,
            logFile = logFile,
            environment = mapOf(
                // 新版 proot 自带独立 loader：loader 也随 jniLibs 解压到 nativeLibraryDir。
                "PROOT_LOADER" to "${layout.nativeLibraryDir.absolutePath}/libproot-loader.so",
                "PROOT_TMP_DIR" to layout.cacheDir.absolutePath,
            ),
            onEndpointFound = { endpoint ->
                if (!endpointDeferred.isCompleted) {
                    endpointDeferred.complete(endpoint)
                }
            }
        )

        if (launchResult.isFailure) {
            bridge.stop()
            return@withContext Result.failure(launchResult.exceptionOrNull()!!)
        }

        val endpoint = withTimeoutOrNull(30_000L) {
            endpointDeferred.await()
        }

        if (endpoint == null) {
            runner.terminate()
            bridge.stop()
            return@withContext Result.failure(IllegalStateException("未能解析到 DSH 启动端口或 token"))
        }

        // DSH 打印出 Web URL 即代表服务已监听；Android 运行时下额外 HTTP/WS 探针
        // 可能被网络策略干扰，直接把阶段推进到 RUNNING。
        runner.markReady()
        Result.success(runner.statusFlow.value)
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        bridgeServer?.stop()
        bridgeServer = null

        val result = runner.terminate()
        val serviceIntent = Intent(context, HostService::class.java).apply {
            action = HostService.ACTION_STOP
        }
        context.startService(serviceIntent)
        result
    }
}




















