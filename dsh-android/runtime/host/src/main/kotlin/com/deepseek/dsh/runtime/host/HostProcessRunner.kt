package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * v4.0 设计文档 §4.3 & §4.4:
 * 管理 proot/bxroot 下的 DSH 子进程生命周期
 */
class HostProcessRunner(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var process: Process? = null
    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null

    private val _statusFlow = MutableStateFlow(
        RuntimeStatus(phase = RuntimePhase.STOPPED)
    )
    val statusFlow: StateFlow<RuntimeStatus> = _statusFlow.asStateFlow()

    @Synchronized
    fun isRunning(): Boolean {
        return process?.isAlive == true
    }

    suspend fun launchProcess(
        command: List<String>,
        workingDir: File,
        logFile: File? = null,
        environment: Map<String, String> = emptyMap(),
        onEndpointFound: ((ProcessOutputParser.ParsedEndpoint) -> Unit)? = null
    ): Result<Process> = withContext(Dispatchers.IO) {
        if (isRunning()) {
            return@withContext Result.failure(IllegalStateException("Host process is already running"))
        }

        try {
            _statusFlow.value = RuntimeStatus(phase = RuntimePhase.STARTING)

            val pb = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(false)
            // 不继承 App 进程环境：某些 Android 运行时变量会干扰 proot 内的 Node。
            pb.environment().clear()
            pb.environment()["PATH"] = "/system/bin:/system/xbin"
            pb.environment()["ANDROID_ROOT"] = System.getenv("ANDROID_ROOT") ?: "/system"
            pb.environment()["ANDROID_DATA"] = System.getenv("ANDROID_DATA") ?: "/data"
            pb.environment()["TMPDIR"] = System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"
            pb.environment().putAll(environment)

            val proc = pb.start()
            process = proc

            val pid = try {
                proc.javaClass.getDeclaredMethod("getPid").invoke(proc) as? Long
            } catch (_: Exception) {
                null
            }

            // 监听 stdout 以解析端口与 token
            stdoutJob = scope.launch(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line ?: continue
                    if (logFile != null) {
                        logFile.appendText("[STDOUT] $current\n")
                    }
                    val endpoint = ProcessOutputParser.parseLine(current)
                    if (endpoint != null) {
                        _statusFlow.value = RuntimeStatus(
                            phase = RuntimePhase.STARTING,
                            port = endpoint.port,
                            token = endpoint.token,
                            pid = pid
                        )
                        onEndpointFound?.invoke(endpoint)
                    }
                }
            }

            // 监听 stderr 记日志
            stderrJob = scope.launch(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(proc.errorStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line ?: continue
                    if (logFile != null) {
                        logFile.appendText("[STDERR] $current\n")
                    }
                }
            }

            Result.success(proc)
        } catch (e: Exception) {
            _statusFlow.value = RuntimeStatus(
                phase = RuntimePhase.ERROR,
                lastError = e.message
            )
            Result.failure(e)
        }
    }

    fun markReady() {
        val current = _statusFlow.value
        _statusFlow.value = current.copy(phase = RuntimePhase.RUNNING)
    }

    /** rootfs 安装完成后把阶段推进到 STOPPED（可启动）。 */
    fun markInstalled() {
        _statusFlow.value = RuntimeStatus(phase = RuntimePhase.STOPPED)
    }

    suspend fun terminate(): Result<Unit> = withContext(Dispatchers.IO) {
        val proc = process ?: return@withContext Result.success(Unit)
        if (!proc.isAlive) {
            process = null
            _statusFlow.value = RuntimeStatus(phase = RuntimePhase.STOPPED)
            return@withContext Result.success(Unit)
        }

        try {
            // v4.0 §4.4: SIGTERM -> 5s -> SIGKILL
            proc.destroy()
            var waitedMs = 0L
            while (proc.isAlive && waitedMs < 5000L) {
                kotlinx.coroutines.delay(200L)
                waitedMs += 200L
            }

            if (proc.isAlive) {
                proc.destroyForcibly()
            }

            stdoutJob?.cancel()
            stderrJob?.cancel()
            process = null
            _statusFlow.value = RuntimeStatus(phase = RuntimePhase.STOPPED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
