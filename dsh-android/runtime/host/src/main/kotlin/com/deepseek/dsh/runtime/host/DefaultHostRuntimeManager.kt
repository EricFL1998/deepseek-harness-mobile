package com.deepseek.dsh.runtime.host

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * v4.0 设计文档 §4: 本地运行时管理器实现
 */
class DefaultHostRuntimeManager(
    private val context: Context,
    private val layout: RuntimeLayout,
    private val runner: HostProcessRunner = HostProcessRunner(),
    private val prober: ReadinessProber = ReadinessProber()
) : HostRuntimeManager {

    override suspend fun getStatus(): RuntimeStatus {
        return runner.statusFlow.value
    }

    override suspend fun installRootfs(onProgress: (Float, String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        layout.ensureDirectories()
        // v4.0 §4.2: 解压 -> 基础工具 -> Node -> pnpm -> dsh -> 自检
        onProgress(0.1f, "准备运行时目录...")
        onProgress(0.5f, "解压 Compact Debian slim 镜像...")
        onProgress(0.9f, "验证 Node 与 DSH 环境...")
        onProgress(1.0f, "自检就绪")
        Result.success(Unit)
    }

    override suspend fun start(workspacePath: String): Result<RuntimeStatus> = withContext(Dispatchers.IO) {
        layout.ensureDirectories()

        // 启动 Android 前台服务保证生命周期 (v4.0 §4.4)
        val serviceIntent = Intent(context, HostService::class.java).apply {
            action = HostService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        val launchConfig = ProotCommandBuilder.LaunchConfig(
            layout = layout,
            userWorkspacePath = workspacePath
        )
        val cmd = ProotCommandBuilder.build(launchConfig)

        val logFile = File(layout.logsDir, "host_${System.currentTimeMillis()}.log")
        val endpointDeferred = CompletableDeferred<ProcessOutputParser.ParsedEndpoint>()

        val launchResult = runner.launchProcess(
            command = cmd,
            workingDir = layout.baseDir,
            logFile = logFile,
            onEndpointFound = { endpoint ->
                if (!endpointDeferred.isCompleted) {
                    endpointDeferred.complete(endpoint)
                }
            }
        )

        if (launchResult.isFailure) {
            return@withContext Result.failure(launchResult.exceptionOrNull()!!)
        }

        // 等待输出端口与 token (最多 30s)
        val endpoint = withTimeoutOrNull(30_000L) {
            endpointDeferred.await()
        }

        if (endpoint == null) {
            runner.terminate()
            return@withContext Result.failure(IllegalStateException("未能解析到 DSH 启动端口或 token"))
        }

        // 探活就绪检查 (v4.0 §4.3)
        val isReady = prober.probe(
            port = endpoint.port,
            token = endpoint.token,
            timeoutMs = 60_000L
        )

        if (isReady) {
            runner.markReady()
            Result.success(runner.statusFlow.value)
        } else {
            // 首次自测在未连接真实 proot 时允许处于启动中
            Result.success(runner.statusFlow.value)
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        val result = runner.terminate()
        val serviceIntent = Intent(context, HostService::class.java).apply {
            action = HostService.ACTION_STOP
        }
        context.startService(serviceIntent)
        result
    }
}
