package com.deepseek.dsh.runtime.host

import android.content.Context
import android.content.Intent
import com.deepseek.dsh.runtime.bridge.BridgeServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
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
        val available = runCatching {
            manifestSource().resolve(forceRefresh = true)?.manifest
        }.getOrNull()
        decideRootfsUpdateKind(
            installed = installedRootfsVersion(),
            available = available?.runtimeVersion,
            installedPatched = installedAndroidPatched(),
            availablePatched = available?.androidPatched == true,
        )
    }

    /** 已安装镜像是否在构建期烧入了 Android 补丁。 */
    private fun installedAndroidPatched(): Boolean =
        runCatching { RootfsInstaller(layout).readMarker()?.androidPatched == true }.getOrDefault(false)

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
        val availableManifest = runCatching {
            manifestSource().resolve(forceRefresh = true)?.manifest
        }.getOrNull()
        val installedPlugin = runCatching {
            runningBridgeVersion() ?: BridgePluginInstaller(context, layout).installedVersion()
        }.getOrNull()
        assessUpdate(
            installed = installedRootfs,
            available = availableManifest?.runtimeVersion,
            latestPlugin = latest,
            installedPlugin = installedPlugin,
            installedPatched = installedAndroidPatched(),
            availablePatched = availableManifest?.androidPatched == true,
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
            // 新镜像换掉了 usr/local 里的引擎文件，而补丁标记/ shim 标记在
            // /root/.dsh（用户数据，跨镜像保留）里。不先让旧标记失效，
            // applyBundledRuntimePatches 会以为「已经打过补丁」直接返回，
            // 新镜像就带着未打补丁的引擎启动（proot 下会再次踩 ENOSYS）。
            File(layout.rootfsDir, "root/.dsh/.runtime-patch-revision").delete()
            File(layout.rootfsDir, "root/.dsh/.addon-shim-revision").delete()
            applyBundledRuntimePatches()
            RuntimeAddonShim.ensure(layout)
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
        // 构建期已烧入补丁的镜像：DSH 文件本身就是「上游 + 补丁」，再用 APK 里的
        // 整文件覆盖反而会把版本钉回旧引擎。只保留 /root/.dsh 下的 Node preload。
        if (installedAndroidPatched()) return
        // 补丁只在版本变化时复制，避免每次启动都重写 ~1MB JS 拖慢 Host。
        val patchMarker = File(layout.rootfsDir, "root/.dsh/.runtime-patch-revision")
        // 34: app-boot 的 internal 模块改走 --expose-internals，绕开缺平台二进制的
        // node-addon-require-builtin（Host 启动报 Cannot find module 的直接原因）。
        // 35: bash / fs-search 的 bridge socket 超时从 15s 提到 30min，长命令不再被
        // android bridge timeout 打断。
        // 36: fs.link 兜底只复制、不删除源文件，修官方 attachment store 的
        // ATTACHMENT_WRITE_FAILED（它自己在 link 成功后还会 unlink staging）。
        // 37: subprocess 控制通道 fd7 恢复为真实管道（之前被改成 "ignore"，
        // 导致 workflow PTC worker 拿到 null control，卡在 reading 'on' of null）。
        // 38: bash / fs-search 的设备桥请求携带本地 token；桥按 fail-closed 鉴权，
        // 阻止同机其他应用连回环端口执行设备命令。
        // 39: bash 工具移回 Host 容器执行（不再走 android_exec_shell）；Host 的
        // proot 参数同时对齐 DSHA（-0 / --kill-on-exit / -L / system、apex、fd 绑定）。
        // fs-search 仍走设备桥，因为它在 Android 侧的 proot 里做 find/grep。
        val patchRevision = "39"
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
            // SenseVoice 的模型检查用 fs.access；proot 返回 ENOSYS，改用 stat
            // 验证存在性，后续大小和 SHA-256 校验保持不变。
            "rootfs-patches/dsh-speech-sensevoice-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/" +
                "@deepseek-ai/dsh-experimental-speech-to-text-sensevoice/lib/index.js",
            // bash 工具在 proot/远程调用下 spec.workdir 可能为空，而输出 schema
            // 要求 cwd 必填；补 result.cwd/process.cwd 兜底，避免工具结果被判定 invalid。
            "rootfs-patches/dsh-tool-bash-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/" +
                "@deepseek-ai/dsh-tool-bash/lib/index.js",
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
    /**
     * web-app 层 patch 在 rootfs 内的固定位置。
     *
     * 这一层是「随安装发布的状态」，不受 profile 清单核对影响，bridge 与第三方
     * 插件的挂载行都写在这里；也正因为它持久化，卸载/半装留下的死行会一直存在。
     */
    private fun webAppPatchFile(): File = File(
        layout.rootfsDir,
        "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/" +
            "@deepseek-ai/dsh-web-app/cordis.patch.yml",
    )

    /** rootfs 全局 `@deepseek-ai` 包目录（官方 bundle 的解析锚点）。 */
    private fun rootfsGlobalModulesDir(): File =
        File(layout.rootfsDir, "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules")

    /**
     * 挂载行里的 `name:` 对应的包是否真的装上了。
     *
     * 先看 profile 的 `node_modules`；`@deepseek-ai/<包名>` 与 bridge 插件还可能在 rootfs
     * 全局目录里（官方 bundle 从那里解析）。`name:` 允许是包子路径（例如
     * `@deepseek-ai/dsh-tool-subagent/model-selection-settings`），所以先归一到包根名，
     * 否则会把 web-app 自带的合法子路径行误删。
     */
    private fun pluginPackageInstalled(packageName: String): Boolean {
        val rootName = packageRootName(packageName)
        if (rootName.isBlank()) return false
        val profileModules = File(layout.rootfsDir, "root/.dsh/profiles/web/node_modules")
        if (File(profileModules, "$rootName/package.json").isFile) return true
        val mayLiveInRootfs = rootName.startsWith("@deepseek-ai/") ||
            rootName == BridgePluginManifest.DEFAULT_PACKAGE
        if (!mayLiveInRootfs) return false
        return File(rootfsGlobalModulesDir(), "$rootName/package.json").isFile
    }

    /** `@scope/pkg/sub/path` / `pkg/sub` 归一成包根名。 */
    private fun packageRootName(specifier: String): String {
        val name = specifier.trim().trim('"', '\'')
        if (name.isEmpty()) return ""
        val parts = name.split('/')
        return when {
            name.startsWith("@") -> parts.take(2).joinToString("/")
            parts[0].isNotEmpty() -> parts[0]
            else -> name
        }
    }

    /**
     * 启动前自愈 web-app 层 patch 里的悬空挂载行。
     *
     * 只处理「本类自己追加的」3 行独立形状（`- insert:` + `- id:` + `name:`，后面
     * 紧跟空行/下一条顶层行/注释）；带 `config`/`disabled` 等额外字段的块属于插件
     * 随包发布的 patch，一律不碰。删两类行：
     * 1. `name:` 指向的包已经不在了（卸载残留、半装失败）——一条这样的 entry 就
     *    足以让 Web 客户端停在启动失败页；
     * 2. 同名重复挂载（保留第一条）——同一插件被 apply 两次会直接抛错。
     *
     * 与 [prunePluginRows] 的区别：这里按「包是否存在」判断，因此也能清掉
     * `@deepseek-ai/<包名>` 的悬空行（prunePluginRows 出于安全不碰官方 scope）。
     */
    private fun sanitizeWebAppPatchRows() {
        val webAppPatch = webAppPatchFile()
        if (!webAppPatch.isFile) return
        try {
            val original = webAppPatch.readText()
            val profileModules = File(layout.rootfsDir, "root/.dsh/profiles/web/node_modules")
            // 插件随包发布的 patch 内容与「本类追加的独立行」形状可能完全一致，
            // 必须按内容保护，否则会把插件自己的挂载行当成残留删掉。
            val protectedPatches = readInstalledBundlePatches(profileModules)
            // bridge 行自带 `config:` 块，不走下面的独立行形状；包不在时必须整块
            // 删除，否则一条 import 不到的 entry 就足以让 Web 客户端启动失败。
            val bridgeInstalled = pluginPackageInstalled(BridgePluginManifest.DEFAULT_PACKAGE)
            val (sanitized, dropped) = dropDanglingMountRows(
                text = original,
                protectedPatches = protectedPatches,
                dropBridgeRow = !bridgeInstalled,
            )
            if (dropped.isEmpty() || sanitized == original) return
            writeTextAtomically(webAppPatch, sanitized)
            dropped.forEach { name ->
                android.util.Log.w(TAG, "web-app patch 移除失效插件挂载行: $name")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "检查 web-app patch 挂载行失败", e)
        }
    }

    /** 先写临时文件再改名：写到一半被杀不会留下半截 YAML 把后续启动全弄坏。 */
    private fun writeTextAtomically(file: File, text: String) {
        val temp = File(file.parentFile, "${file.name}.tmp-${System.nanoTime()}")
        try {
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.writeText(text)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /**
     * [sanitizeWebAppPatchRows] 的纯文本部分：返回处理后的内容与被移除的包名。
     *
     * 保持逐行删除而不是 YAML 重新序列化：patch 里大量注释与 `!!js` 表达式必须
     * 原样保留，重排会改变语义。
     *
     * @param protectedPatches 插件随包发布的 patch 文本；内容一致的块原样保留。
     * @param dropBridgeRow bridge 插件包不在时，把它带 `config:` 的整块挂载删掉。
     */
    private fun dropDanglingMountRows(
        text: String,
        protectedPatches: List<String> = emptyList(),
        dropBridgeRow: Boolean = false,
    ): Pair<String, List<String>> {
        val bridgeResult = if (dropBridgeRow) {
            dropBridgeMountBlock(text.lines().toMutableList())
        } else {
            text.lines().toMutableList() to mutableListOf<String>()
        }
        val lines = bridgeResult.first
        val dropped = bridgeResult.second
        val keptNames = mutableSetOf<String>()
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
                // 还带着 config/disabled 等字段：插件作者自己的 patch，不动。
                index += 1
                continue
            }
            val blockText = "${lines[index]}\n${lines[index + 1]}\n${lines[index + 2]}"
            if (protectedPatches.any { it.contains(blockText) }) {
                // 与插件随包发布的 patch 内容一致：是它的挂载行，不是本类残留。
                index += 3
                continue
            }
            val name = nameLine.removePrefix("      name: ").trim().trim('"', '\'')
            val duplicated = name.isNotEmpty() && name in keptNames
            if (name.isNotEmpty() && !duplicated && pluginPackageInstalled(name)) {
                keptNames += name
                index += 3
                continue
            }
            dropped += name.ifEmpty { idLine.removePrefix("    - id: ").trim().trim('"', '\'') }
            repeat(3) { if (index < lines.size) lines.removeAt(index) }
            if (index < lines.size && lines[index].isBlank()) lines.removeAt(index)
        }
        return lines.joinToString("\n") to dropped
    }

    /**
     * 删除 bridge 插件的整块挂载（含 `config:`）。
     *
     * 只认顶层 `- insert:` 到下一个顶层行之前的块，且块里出现 bridge 的 id；
     * 这样不会误删别的 insert 块，也不会动注释掉的文本。
     */
    private fun dropBridgeMountBlock(
        lines: MutableList<String>,
    ): Pair<MutableList<String>, MutableList<String>> {
        val dropped = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            if (lines[index] != "- insert:") {
                index += 1
                continue
            }
            var end = index + 1
            while (end < lines.size &&
                !lines[end].startsWith("- ") && !lines[end].startsWith("#")
            ) {
                end += 1
            }
            val block = lines.subList(index, end)
            if (block.any { it.contains(BRIDGE_BUNDLE_ID) }) {
                dropped += BridgePluginManifest.DEFAULT_PACKAGE
                repeat(end - index) { lines.removeAt(index) }
                if (index < lines.size && lines[index].isBlank()) lines.removeAt(index)
                continue
            }
            index = end
        }
        return lines to dropped
    }

    private fun applyBridgeBundleRow() {
        val webAppPatch = webAppPatchFile()
        if (!webAppPatch.isFile) return
        // 包不在时绝不补行：Loader 里一条 import 不到的 entry 足以让整个 Web 客户端
        // 启动失败，比「bridge 缺失」更糟。等插件装好后下一次启动再补。
        if (!pluginPackageInstalled(BridgePluginManifest.DEFAULT_PACKAGE)) return
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
        /** Logcat tag。 */
        const val TAG = "DshHostRuntime"

        /** 启动时等待 `dsh web:` URL 的上限；超时按启动失败处理，不再无限 STARTING。 */
        const val ENDPOINT_TIMEOUT_MS = 90_000L

        /** 等待端口期间轮询运行时状态的间隔：进程失败时能立刻返回，而不是等满超时。 */
        const val ENDPOINT_POLL_INTERVAL_MS = 200L

        /** 超时错误文案里附带的最近输出行数（与设置页展示的条数一致）。 */
        const val LOG_TAIL_ON_ERROR = 8

        /** bridge 插件在 cordis 树里的行 id；用于幂等判断。 */
        const val BRIDGE_BUNDLE_ID = "id: dsh-mobile-bridge-plugin"

        /** 插件包中声明的 HTTPS 域名；用于通用 hosts 预解析。 */
    }

    /** 后台补 hosts 等非关键启动任务；start 返回后继续跑。 */
    private val backgroundScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    /** 解析并写入 /etc/hosts；失败的域名跳过。 */
    private fun writeHostsFile(hosts: List<String>) {
        runCatching {
            val entries = hosts.distinct().take(160).mapNotNull { host ->
                runCatching { java.net.InetAddress.getByName(host).hostAddress }.getOrNull()
                    ?.let { "$it $host" }
            }
            if (entries.isNotEmpty()) {
                File(layout.rootfsDir, "etc/hosts").writeText(entries.joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** 扫描已安装插件 JS 里声明的域名（官方 hosts 预解析的补齐部分）。 */
    private fun scanDeclaredHosts(): List<String> = runCatching {
        val urlHost = Regex("""https://([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)""")
        val modules = File(layout.rootfsDir, "root/.dsh/profiles/web/node_modules")
        if (!modules.isDirectory) return@runCatching emptyList()
        modules.walkTopDown()
            .maxDepth(3)
            .filter { file ->
                file.isFile && file.length() in 1..2_000_000 &&
                    file.extension.lowercase() in setOf("js", "mjs", "cjs")
            }
            .take(240)
            .flatMap { file ->
                runCatching {
                    urlHost.findAll(file.readText().take(400_000))
                        .map { it.groupValues[1] }
                        .toList()
                }.getOrDefault(emptyList())
            }
            .filter { host -> host.contains('.') }
            .toList()
    }.getOrDefault(emptyList())

    override suspend fun start(workspacePath: String): Result<RuntimeStatus> = startMutex.withLock {

        if (runner.isRunning()) return@withLock Result.success(runner.statusFlow.value)
        // NonCancellable：调用方的 scope 被取消（切页/重组/返回）时，不能把 runner
        // 丢在 STARTING 里。启动要么跑完、要么落到明确的 ERROR，由看门狗和超时兜底。
        withContext(Dispatchers.IO + NonCancellable) {
        layout.ensureDirectories()

        val hasRootfs = File(layout.rootfsDir, "etc/os-release").isFile ||
            File(layout.rootfsDir, "usr/bin/env").isFile
        if (!hasRootfs) {
            return@withContext Result.failure(
                IllegalStateException("rootfs 未安装：请先在运行控制页安装 Rootfs")
            )
        }
        // APK 的 rootfs 补丁按内置清单的 DSH 版本整文件替换；已安装镜像若来自
        // 另一个 DSH 版本（例如 rootfs 仓库先发了 rc.3），再套 rc.2 的补丁会让
        // 运行树版本混杂。构建期烧入补丁的镜像不受此限：它内部已经是
        // 「最新 DSH + 补丁」，启动时只需跳过整文件覆盖。
        val expectedDsh = manifestSource().bundledDshVersion()
        val installedMarker = runCatching { RootfsInstaller(layout).readMarker() }.getOrNull()
        val installedRootfsVersion = installedMarker?.runtimeVersion
        if (installedMarker?.androidPatched != true &&
            installedRootfsVersion != null &&
            !RootfsNaming.isDshCompatible(installedRootfsVersion, expectedDsh)
        ) {
            return@withContext Result.failure(
                IllegalStateException(
                    "rootfs 与当前 APK 不兼容：已安装 $installedRootfsVersion，" +
                        "当前 APK 的补丁目标是 dsh$expectedDsh。" +
                        "请在设置 → 运行控制中重新安装匹配的 Rootfs。"
                )
            )
        }
        applyBundledRuntimePatches()
        // 图片镜像可能没有打进 node-addon-require-builtin 的平台二进制；缺了就换
        // 纯 JS 兜底（配合 --expose-internals），否则 Host 在加载 Loader 时直接退出。
        RuntimeAddonShim.ensure(layout)
        // 先清掉上次卸载/半装留下的悬空挂载行，再补行：顺序反了会把刚补的又删掉。
        // 一条 import 不到的 Loader 行就足以让整个 Web 客户端停在启动失败页。
        sanitizeWebAppPatchRows()
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
        // GenUI initializes ArtifactRegistry and DesignStore whenever a profile reload
        // activates it. Create their parent directories on Android first so the loader
        // never depends on proot's recursive-mkdir behavior for a fresh storage root.
        File(layout.rootfsDir, "root/.dsh/genui/.designs").mkdirs()
        // 通用插件存储根：插件自行创建子目录。只建根目录，不按插件名适配。
        File(layout.rootfsDir, "root/.dsh/plugins").mkdirs()
        File(layout.rootfsDir, "root/.dsh/node-compile-cache").mkdirs()
        File(layout.rootfsDir, "root/.dsh/cpu-prof").mkdirs()
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
        // 通用 DNS：优先使用 Android 当前网络的 DNS 服务器，任意域名都能解析；
        // 公共 DNS 仅作兜底，不再为每个插件/域名单独写解析。
        val dnsServers = runCatching {
            val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
            manager?.activeNetwork?.let { network ->
                manager.getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }
            }.orEmpty()
        }.getOrDefault(emptyList())
        val resolvers = (dnsServers + listOf("8.8.8.8", "1.1.1.1"))
            .filter { it.isNotBlank() && !it.contains(':') }
            .distinct()
            .take(3)
        File(layout.rootfsDir, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            writeText(resolvers.joinToString("") { "nameserver $it\n" })
        }
        // 把域名预先解析并写入 hosts，绕开 proot 内 glibc DNS 的 EAI_AGAIN。
        //
        // 除了插件安装/平台自身需要的固定域名，还会扫描已安装插件包中声明的
        // https 域名。这样任何插件（OAuth、API、CDN）都不需要单独适配 hosts。
        // 启动关键路径只解析平台自身必需的几个域名；扫描 node_modules 里
        // 插件声明的域名 + 逐个 DNS 解析可能要几百毫秒到数秒，改为进程起来后
        // 在后台补齐（glibc 每次 lookup 都会重新读 /etc/hosts，后写入同样生效）。
        val infraHosts = listOf(
            "platform.deepseek.com",
            "api.deepseek.com",
            "github.com",
            "codeload.github.com",
            "api.github.com",
            "raw.githubusercontent.com",
            "registry.npmjs.org",
            "registry.npmmirror.com",
        )
        writeHostsFile(infraHosts)
        backgroundScope.launch {
            runCatching {
                val declared = scanDeclaredHosts()
                if (declared.isNotEmpty()) {
                    writeHostsFile((infraHosts + declared).distinct().take(160))
                }
            }
        }
        // 上次异常退出可能残留 credentials 写锁，启动前清理。
        File(layout.rootfsDir, "root/.dsh").listFiles()
            ?.filter { it.name.startsWith(".credentials.yaml.lock") }
            ?.forEach { it.delete() }

        // 1. 启动本地 BridgeServer 并获得 loopback 端口；token 写入 guest 的 DSH_HOME，
        // Host 侧补丁按请求携带（见 BridgeAuth）。
        val bridge = BridgeServer(context, layout.bridgeTokenFile)
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
        // 终端/子进程要在 proot 里解析 uid，先把 passwd/group 覆盖写好。
        layout.ensureIdentityOverlays()
        layout.repairProfilePatch()
        layout.ensureNodePatches()
        layout.ensureHostRuntimePatches()
        // 官方 workspace/changes 的 git baseline 放到 Host 启动后后台补：每个
        // 工作区目录都要 spawn 一次 proot+git，放在关键路径上会明显拖慢启动。
        // 首个 turn 之后完成的初始化同样能被录制器用上。
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
            command = launchCmd,
            workingDir = layout.baseDir,
            logFile = logFile,
            environment = mapOf(
                // 新版 proot 自带独立 loader：loader 也随 jniLibs 解压到 nativeLibraryDir。
                "PROOT_LOADER" to "${layout.nativeLibraryDir.absolutePath}/libproot-loader.so",
                "PROOT_TMP_DIR" to layout.cacheDir.absolutePath,
                // proot 动态依赖 libtalloc/libandroid-shmem，和 proot 一起解压到 nativeLibraryDir。
                "LD_LIBRARY_PATH" to layout.nativeLibraryDir.absolutePath,
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
        backgroundScope.launch {
            runCatching { ensureWorkspaceGitBaseline(workspacePath) }
        }

        // 等端口的同时盯住进程状态：DSH 启动失败（插件 import 不到）通常几秒内
        // 就退出，不该让调用方把 90 秒超时等满才拿到结果；看门狗落的状态已经带着
        // 退出码与最近输出，直接用它。STOPPED 表示用户在启动途中主动停止（例如
        // 划掉任务或前台服务被回收），这是正常结束，不能改写成「启动超时」。
        val endpoint: ProcessOutputParser.ParsedEndpoint? = withTimeoutOrNull(ENDPOINT_TIMEOUT_MS) {
            while (!endpointDeferred.isCompleted) {
                when (runner.statusFlow.value.phase) {
                    RuntimePhase.ERROR, RuntimePhase.STOPPED -> return@withTimeoutOrNull null
                    else -> kotlinx.coroutines.delay(ENDPOINT_POLL_INTERVAL_MS)
                }
            }
            endpointDeferred.await()
        }

        if (endpoint == null) {
            val failed = runner.statusFlow.value
            if (failed.phase == RuntimePhase.ERROR) {
                // 进程已在启动中途退出：保留看门狗写的错误（含退出码/日志尾巴），
                // 不要用「超时」覆盖掉真正的原因。
                bridge.stop()
                return@withContext Result.failure(
                    IllegalStateException(failed.lastError ?: "Host 进程在启动中途退出")
                )
            }
            if (failed.phase == RuntimePhase.STOPPED) {
                // 主动停止：保持 STOPPED，不杀进程、不写 ERROR，也不留下半启动的 bridge。
                bridge.stop()
                return@withContext Result.success(failed)
            }
            // 端口一直没出现：先杀进程，再把最近输出写进状态。用户需要的不是
            // 一句「未能解析到端口」，而是 Host 最后打印了什么。
            runner.terminate()
            val tail = runner.statusFlow.value.logTail.takeLast(LOG_TAIL_ON_ERROR)
            val detail = if (tail.isEmpty()) "无输出" else tail.joinToString("\n")
            val message = "Host 启动超时（${ENDPOINT_TIMEOUT_MS / 1000} 秒内未解析到端口或 token）：\n$detail"
            runner.markError(message)
            bridge.stop()
            return@withContext Result.failure(IllegalStateException(message))
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

    /**
     * 给工作区下的每个会话目录初始化 git 仓库（幂等）。
     *
     * Host 的 `workspace/changes` 录制器用 git 快照做 turn baseline；没有仓库时它
     * 只能记录官方文件工具的改动，而移动端的 bash 走 Android shell，改动不会被看见。
     * rootfs 自带 `/usr/bin/git`，所以这里在 proot 里跑一次 `git init`。
     */
    private suspend fun ensureWorkspaceGitBaseline(userWorkspacePath: String) = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(userWorkspacePath)
            val directories = root.listFiles()
                ?.filter { it.isDirectory && it.name != ".git" }
                .orEmpty()
            if (directories.isEmpty()) return@withContext
            directories.forEach { directory ->
                val gitDir = File(directory, ".git")
                if (gitDir.exists()) {
                    // 中断过的 init 会留下缺 objects/refs 的残缺仓库（git 会报
                    // "not a git repository"，workspace/changes 也取不到 baseline）。
                    val complete = File(gitDir, "objects").isDirectory &&
                        File(gitDir, "refs").isDirectory
                    if (complete) return@forEach
                    runCatching { gitDir.deleteRecursively() }
                }
                val hostPath = "/workspace/" + directory.name
                val exec = ProotCommandBuilder.buildExec(
                    layout,
                    listOf("/usr/bin/git", "-C", hostPath, "init", "-q"),
                )
                val builder = ProcessBuilder(exec.command)
                builder.environment().putAll(exec.environment)
                builder.redirectErrorStream(true)
                val process = builder.start()
                process.inputStream.close()
                process.waitFor()
            }
        }
    }
}




















