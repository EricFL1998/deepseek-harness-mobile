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

    override suspend fun getStatus(): RuntimeStatus {
        return runner.statusFlow.value
    }

    override suspend fun isRootfsInstalled(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val installer = RootfsInstaller(layout)
            val raw = context.assets.open("rootfs-manifest.json").bufferedReader().use { it.readText() }
            installer.isInstalled(installer.parseManifest(raw))
        }.getOrDefault(false)
    }

    override suspend fun installRootfs(onProgress: (Float, String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        layout.ensureDirectories()
        val installer = RootfsInstaller(layout)
        val manifest = try {
            val raw = context.assets.open("rootfs-manifest.json").bufferedReader().use { it.readText() }
            installer.parseManifest(raw)
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException("读取 rootfs-manifest.json 失败: ${e.message}"))
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
        Thread.sleep(200)
    }

    /**
     * App 侧补丁：修复 DSH 在 proot/Android 下会触发 ENOSYS 的路径解析、
     * 文件锁释放与 spill 临时目录逻辑，无需重新下载整份 rootfs。
     */
    private fun applyBundledRuntimePatches() {
        val patches = mapOf(
            "rootfs-patches/dsh-app-boot-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-app-boot/lib/index.js",
            "rootfs-patches/dsh-spill-local-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-spill-local/lib/index.js",
            "rootfs-patches/dsh-atomic-write-index.js" to
                "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-atomic-write/lib/index.js",
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
        killStaleHostProcesses()
        if (!layout.prootBinary.isFile) {
            return@withContext Result.failure(
                IllegalStateException("缺少 ${layout.prootBinary.name}：需把对应 ABI 的 proot 打包到 jniLibs")
            )
        }
        // proot 的 bind 目标需要存在；DSH 配置目录先创建，避免首次启动 mkdir 失败。
        File(layout.rootfsDir, "root/.dsh/profiles/web").mkdirs()
        // rootfs 内没有 Android 的 DNS 配置，登录/模型请求会因解析失败报 network。
        File(layout.rootfsDir, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        // 把平台域名预先解析并写入 hosts，绕开 proot 内 glibc DNS 的 EAI_AGAIN。
        runCatching {
            val hosts = File(layout.rootfsDir, "etc/hosts")
            val entries = listOf("platform.deepseek.com", "api.deepseek.com").mapNotNull { host ->
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


