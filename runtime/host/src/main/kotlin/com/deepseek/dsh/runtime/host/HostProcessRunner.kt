package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * v4.0 设计文档 §4.3 & §4.4:
 * 管理 proot/bxroot 下的 DSH 子进程生命周期
 *
 * 这里同时负责「状态别撒谎」：DSH 可能在启动中途悄悄退出（插件加载失败是常见
 * 原因，见 `.captures/latest-host.log` 的 `startup failed: N required plugins did
 * not activate`），旧实现没有人在等 `waitFor()`，RuntimePhase 就永远停在 STARTING，
 * 端口/token 永远读不到。现在有退出看门狗 + 最近输出环形缓冲：进程一退出就把
 * ERROR、退出码和最后几行（已脱敏）写进 [RuntimeStatus]。
 */
class HostProcessRunner(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    /** 同时保护 [process]、[logLines]、[stopping] 与状态推进。 */
    private val lock = Any()

    @Volatile
    private var process: Process? = null
    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null
    private var watchdogJob: Job? = null

    /** 最近输出（已脱敏），错误时直接给 UI 看。 */
    private val logLines = ArrayDeque<String>()

    /** App 主动 [terminate] 期间为 true：退出看门狗不把它当异常退出。 */
    private var stopping = false

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

        // 新一次启动：清掉上一轮留下的输出、退出码与「主动停止」标记。
        synchronized(lock) {
            logLines.clear()
            stopping = false
            process = null
            _statusFlow.value = RuntimeStatus(
                phase = RuntimePhase.STARTING,
                logTail = emptyList(),
                updatedAtMillis = System.currentTimeMillis()
            )
        }

        try {
            val pb = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(false)
            pb.environment().putAll(environment)

            val proc = pb.start()
            // 与第 66 行的重置不是一个临界区：这期间 stop() 可能已经跑过
            // （stopping=true、process 仍是 null）。此时必须自己把刚拉起的进程
            // 杀掉，否则会留下一个没人看管的 proot，状态却是 STOPPED。
            val aborted = synchronized(lock) {
                if (stopping) {
                    true
                } else {
                    process = proc
                    false
                }
            }
            if (aborted) {
                runCatching { proc.destroyForcibly() }
                return@withContext Result.failure(IllegalStateException("启动已被停止"))
            }

            val pid = try {
                proc.javaClass.getDeclaredMethod("getPid").invoke(proc) as? Long
            } catch (_: Exception) {
                null
            }

            // 监听 stdout 以解析端口与 token
            val outJob = scope.launch(Dispatchers.IO) {
                runCatching {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val current = line ?: continue
                        appendLogLine(current)
                        if (logFile != null) {
                            runCatching { logFile.appendText("[STDOUT] $current\n") }
                        }
                        val endpoint = ProcessOutputParser.parseLine(current)
                        if (endpoint != null) {
                            publishStatus { status ->
                                // 只接受「仍在启动」且未主动停止时的端口：terminate()
                                // 之后的残余输出不能把 STOPPED 翻回 STARTING（看门狗
                                // 已结束，界面会永远停在启动中）；markReady 后的 URL
                                // 同理不能把 RUNNING 降回 STARTING。
                                if (status.phase == RuntimePhase.STARTING && !stopping) {
                                    status.copy(
                                        phase = RuntimePhase.STARTING,
                                        port = endpoint.port,
                                        token = endpoint.token,
                                        pid = pid
                                    )
                                } else {
                                    status
                                }
                            }
                            onEndpointFound?.invoke(endpoint)
                        }
                    }
                }
            }
            stdoutJob = outJob

            // 监听 stderr：只记日志（错误文案由看门狗从环形缓冲里取）。
            val errJob = scope.launch(Dispatchers.IO) {
                runCatching {
                    val reader = BufferedReader(InputStreamReader(proc.errorStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val current = line ?: continue
                        appendLogLine(current)
                        if (logFile != null) {
                            runCatching { logFile.appendText("[STDERR] $current\n") }
                        }
                    }
                }
            }
            stderrJob = errJob

            startExitWatchdog(proc, outJob, errJob)

            Result.success(proc)
        } catch (e: Exception) {
            publishStatus {
                it.copy(
                    phase = RuntimePhase.ERROR,
                    lastError = e.message ?: "无法启动 Host 进程"
                )
            }
            Result.failure(e)
        }
    }

    /**
     * 等子进程退出；退出后把「最近输出 + 退出码」写回状态。
     *
     * 必须是独立协程：DSH 启动失败时不会有人调 [terminate]，没有这条路径
     * UI 就会一直显示 STARTING。
     */
    private fun startExitWatchdog(proc: Process, outJob: Job, errJob: Job) {
        val job = scope.launch(Dispatchers.IO) {
            val exitCode = runCatching { proc.waitFor() }.getOrNull() ?: return@launch
            // 进程退出不代表管道已排空：先给读取协程一点时间把最后几行读完，
            // 错误文案里的「最后一行」才可能是真正的原因（ERR_MODULE_NOT_FOUND 等）。
            withTimeoutOrNull(STREAM_DRAIN_TIMEOUT_MS) {
                outJob.join()
                errJob.join()
            }
            onProcessExited(proc, exitCode)
        }
        synchronized(lock) {
            watchdogJob = job
        }
    }

    private fun onProcessExited(proc: Process, exitCode: Int) {
        synchronized(lock) {
            // 主动停止，或者期间已经换成了新进程：都不算异常退出。
            if (stopping || process !== proc) return
            val current = _statusFlow.value
            if (current.phase != RuntimePhase.STARTING && current.phase != RuntimePhase.RUNNING) {
                return
            }
            val tail = logLines.toList()
            val detail = lastMeaningfulLine(tail) ?: "无输出"
            val message = if (current.phase == RuntimePhase.STARTING && current.port == null) {
                "Host 进程已退出（exit=$exitCode）：$detail"
            } else {
                "Host 进程意外退出（exit=$exitCode）：$detail"
            }
            process = null
            _statusFlow.value = current.copy(
                phase = RuntimePhase.ERROR,
                lastError = redact(message),
                exitCode = exitCode,
                logTail = tail,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun markReady() {
        publishStatus { current ->
            if (current.phase == RuntimePhase.STARTING) {
                current.copy(phase = RuntimePhase.RUNNING)
            } else {
                current
            }
        }
    }

    /** rootfs 安装完成后把阶段推进到 STOPPED（可启动）。 */
    fun markInstalled() {
        synchronized(lock) {
            logLines.clear()
            process = null
            stopping = false
            _statusFlow.value = RuntimeStatus(
                phase = RuntimePhase.STOPPED,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    /**
     * 从外部把状态推到 ERROR（例如启动等待端口超时）。
     *
     * 保留最近的输出尾巴：这条错误会显示在设置页里，用户需要它来判断原因。
     */
    fun markError(message: String) {
        publishStatus { current ->
            current.copy(
                phase = RuntimePhase.ERROR,
                lastError = redact(message)
            )
        }
    }

    suspend fun terminate(): Result<Unit> = withContext(Dispatchers.IO) {
        val proc = synchronized(lock) {
            stopping = true
            process
        }

        try {
            // v4.0 §4.4: SIGTERM -> 5s -> SIGKILL
            if (proc != null && proc.isAlive) {
                proc.destroy()
                var waitedMs = 0L
                while (proc.isAlive && waitedMs < 5000L) {
                    kotlinx.coroutines.delay(200L)
                    waitedMs += 200L
                }
                if (proc.isAlive) {
                    proc.destroyForcibly()
                }
            }

            // 先停看门狗/读流协程，再落 STOPPED，避免看门狗把主动停止误报成 ERROR。
            cancelJobs()
            synchronized(lock) {
                if (process === proc) process = null
                _statusFlow.value = RuntimeStatus(
                    phase = RuntimePhase.STOPPED,
                    logTail = logLines.toList(),
                    updatedAtMillis = System.currentTimeMillis()
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cancelJobs() {
        synchronized(lock) {
            watchdogJob?.cancel()
            stdoutJob?.cancel()
            stderrJob?.cancel()
            watchdogJob = null
            stdoutJob = null
            stderrJob = null
        }
    }

    private fun appendLogLine(line: String) {
        val redacted = redact(line)
        synchronized(lock) {
            logLines.addLast(redacted)
            while (logLines.size > MAX_LOG_LINES) {
                logLines.removeFirst()
            }
        }
    }

    /** 读-改-写状态，并统一补上最近的输出尾巴与更新时间。 */
    private fun publishStatus(transform: (RuntimeStatus) -> RuntimeStatus) {
        synchronized(lock) {
            _statusFlow.value = transform(_statusFlow.value).copy(
                logTail = logLines.toList(),
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    private fun lastMeaningfulLine(lines: List<String>): String? {
        for (line in lines.asReversed()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            // 跳过 stack trace 行（"at ..."）：它们只说明在哪崩，不说明为什么崩。
            if (trimmed.startsWith("at ")) continue
            return trimmed.take(MAX_ERROR_LINE_CHARS)
        }
        return lines.lastOrNull { it.isNotBlank() }?.trim()?.take(MAX_ERROR_LINE_CHARS)
    }

    private fun redact(text: String): String = TOKEN_PATTERN.replace(text, "token=***")

    private companion object {
        /** 环形缓冲保留的行数：够定位启动失败，又不至于把状态流撑爆。 */
        const val MAX_LOG_LINES = 120

        /** 错误文案里「最后一行」的最大长度，避免整段 stack 文本进 UI。 */
        const val MAX_ERROR_LINE_CHARS = 320

        /** 进程退出后等待 stdout/stderr 排空的上限。 */
        const val STREAM_DRAIN_TIMEOUT_MS = 2_000L

        /** 日志与错误文案里的 token 一律脱敏。 */
        val TOKEN_PATTERN = Regex("""token=[A-Za-z0-9_\-]+""")
    }
}
