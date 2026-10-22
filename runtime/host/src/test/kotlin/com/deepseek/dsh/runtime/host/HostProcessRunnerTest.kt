package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * [HostProcessRunner] 的跨平台 JVM 测试：不依赖 Android runtime，也不需要 proot。
 *
 * 覆盖两条真实故障路径：
 * 1. 正常启动：解析 `dsh web:` 行里的端口/token，`markReady` 后进入 RUNNING；
 * 2. 启动中途退出：退出看门狗把状态推到 ERROR，带上退出码与 stderr 尾巴
 *    （这正是「移动运行时永远停在启动中」的根因）。
 */
class HostProcessRunnerTest {

    private val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    @Test
    fun `endpoint line reaches starting then running after markReady`() = runBlocking {
        val runner = HostProcessRunner()
        val workingDir = Files.createTempDirectory("dsh-host-runner-ok").toFile()
        try {
            val launch = runner.launchProcess(
                command = endpointCommand(),
                workingDir = workingDir,
            )
            assertTrue(launch.exceptionOrNull()?.message.orEmpty(), launch.isSuccess)

            val starting = withTimeout(15_000L) {
                runner.statusFlow.first { status ->
                    status.phase == RuntimePhase.STARTING && status.port == 12345
                }
            }
            assertEquals(12345, starting.port)
            assertEquals("abc", starting.token)
            // token 必须先脱敏才能进入状态/UI。
            assertTrue(
                "logTail 应包含脱敏后的 URL，实际=${starting.logTail}",
                starting.logTail.any { it.contains("token=***") && !it.contains("token=abc") },
            )

            runner.markReady()
            val running = withTimeout(5_000L) {
                runner.statusFlow.first { it.phase == RuntimePhase.RUNNING }
            }
            assertEquals(12345, running.port)
            assertEquals("abc", running.token)
        } finally {
            runner.terminate()
            workingDir.deleteRecursively()
        }
    }

    @Test
    fun `process exit during startup becomes error with exit code and log tail`() = runBlocking {
        val runner = HostProcessRunner()
        val workingDir = Files.createTempDirectory("dsh-host-runner-exit").toFile()
        try {
            val launch = runner.launchProcess(
                command = failingCommand(),
                workingDir = workingDir,
            )
            assertTrue(launch.exceptionOrNull()?.message.orEmpty(), launch.isSuccess)

            val error = withTimeout(15_000L) {
                runner.statusFlow.first { it.phase == RuntimePhase.ERROR }
            }
            assertEquals(3, error.exitCode)
            assertTrue(
                "lastError 应包含退出码，实际=${error.lastError}",
                error.lastError.orEmpty().contains("exit=3"),
            )
            assertTrue(
                "logTail 应包含 stderr 的 boom，实际=${error.logTail}",
                error.logTail.any { it.contains("boom") },
            )
        } finally {
            runner.terminate()
            workingDir.deleteRecursively()
        }
    }

    /** 打印一行 DSH Web URL 后保持存活（等测试主动终止）。 */
    private fun endpointCommand(): List<String> = if (isWindows) {
        listOf(
            "cmd",
            "/c",
            "echo dsh web: listening on http://127.0.0.1:12345/?token=abc & ping -n 8 127.0.0.1 >nul",
        )
    } else {
        listOf(
            "sh",
            "-c",
            "echo 'dsh web: listening on http://127.0.0.1:12345/?token=abc'; sleep 10",
        )
    }

    /** 往 stderr 打一行然后以退出码 3 结束。 */
    private fun failingCommand(): List<String> = if (isWindows) {
        listOf("cmd", "/c", "echo boom 1>&2 & exit /b 3")
    } else {
        listOf("sh", "-c", "echo boom 1>&2; exit 3")
    }
}
