package com.deepseek.dsh.runtime.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * v4.0 设计文档 §10.2:
 * 本地回环 BridgeServer (127.0.0.1 随机端口)，承载 Node 容器侧 dsh-android-bridge 插件发来的 JSON-RPC。
 *
 * 鉴权：每个请求必须携带 [BridgeRequest.auth]，其值由 [BridgeAuth] 在每次 Host
 * 启动时轮换并写入 guest 的 `/root/.dsh/.bridge_token`。未配置 token 时拒绝所有
 * 请求（fail closed），不允许退化成无鉴权模式。
 */
class BridgeServer(
    private val context: Context,
    private val tokenFile: File? = null,
    private val port: Int = 0 // 0 = 随机可用端口
) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val json = Json { ignoreUnknownKeys = true }
    private var isRunning = false

    /** 本次 Host 启动使用的 token；[start] 之前为 null，此时拒绝所有请求。 */
    @Volatile
    private var expectedToken: String? = null

    val localPort: Int get() = serverSocket?.localPort ?: 0

    @Synchronized
    fun start(): Int {
        if (isRunning) return localPort

        // 每次 Host 启动轮换 token，并写到 guest 的 DSH_HOME。Host 侧补丁按请求
        // 携带；没有 token 文件就拒绝服务，不能因为「忘了传 token」退化成无鉴权。
        expectedToken = tokenFile?.let { file ->
            BridgeAuth.newToken().also { BridgeAuth.writeTokenFile(file, it) }
        }

        // 显式绑定 127.0.0.1：ServerSocket(port) 默认绑定所有网卡，仅靠注释声称
        // loopback 不成立；桥的能力是设备级命令，绝不能对局域网开放。
        val ss = ServerSocket()
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        serverSocket = ss
        isRunning = true

        executor.execute {
            while (isRunning && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    executor.execute { handleClient(client) }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return ss.localPort
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        executor.shutdownNow()
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = PrintWriter(s.getOutputStream(), true)

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val reqStr = line ?: continue
                try {
                    val request = json.decodeFromString<BridgeRequest>(reqStr)
                    val response = dispatchRequest(request)
                    writer.println(json.encodeToString(BridgeResponse.serializer(), response))
                    // 鉴权失败直接断开，不给同一连接继续试 token 的机会。
                    if (response.error?.code == BridgeAuth.UNAUTHORIZED_CODE) return
                } catch (e: Exception) {
                    val err = BridgeResponse(
                        id = "error",
                        error = BridgeError(code = -32700, message = "Parse error: ${e.message}")
                    )
                    writer.println(json.encodeToString(BridgeResponse.serializer(), err))
                }
            }
        }
    }

    fun dispatchRequest(request: BridgeRequest): BridgeResponse {
        if (!BridgeAuth.verify(expectedToken, request.auth)) {
            return BridgeResponse(
                id = request.id,
                error = BridgeError(
                    code = BridgeAuth.UNAUTHORIZED_CODE,
                    message = "bridge token missing or invalid",
                ),
            )
        }
        return try {
            when (request.method) {
                BridgeMethod.GET_DEVICE_INFO.methodName -> {
                    val info = buildJsonObject {
                        put("brand", Build.BRAND)
                        put("model", Build.MODEL)
                        put("sdkInt", Build.VERSION.SDK_INT)
                        put("release", Build.VERSION.RELEASE)
                    }
                    BridgeResponse(id = request.id, result = info)
                }

                BridgeMethod.GET_CLIPBOARD.methodName -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    val res = buildJsonObject { put("text", text) }
                    BridgeResponse(id = request.id, result = res)
                }

                BridgeMethod.SET_CLIPBOARD.methodName -> {
                    val text = (request.params as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: ""
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("DSH", text))
                    val res = buildJsonObject { put("success", true) }
                    BridgeResponse(id = request.id, result = res)
                }

                BridgeMethod.SHOW_NOTIFICATION.methodName -> {
                    val title = (request.params as? JsonObject)?.get("title")?.jsonPrimitive?.content ?: "DSH 提示"
                    val content = (request.params as? JsonObject)?.get("content")?.jsonPrimitive?.content ?: ""
                    showNotification(title, content)
                    val res = buildJsonObject { put("success", true) }
                    BridgeResponse(id = request.id, result = res)
                }

                BridgeMethod.SHARE_TEXT.methodName -> {
                    val text = (request.params as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: ""
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(Intent.createChooser(intent, "分享").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    val res = buildJsonObject { put("success", true) }
                    BridgeResponse(id = request.id, result = res)
                }

                BridgeMethod.EXEC_PROOT.methodName -> {
                    val params = request.params as? JsonObject
                    val argv = (params?.get("argv") as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
                    val cwd = params?.get("cwd")?.jsonPrimitive?.content ?: "/"
                    val timeoutMs = params?.get("timeoutMs")?.jsonPrimitive?.content?.toLongOrNull() ?: 120_000L
                    BridgeResponse(id = request.id, result = execProot(argv, cwd, timeoutMs))
                }
                BridgeMethod.EXEC_SHELL.methodName -> {
                    val params = request.params as? JsonObject
                    val command = params?.get("command")?.jsonPrimitive?.content ?: ""
                    val cwd = params?.get("cwd")?.jsonPrimitive?.content ?: ""
                    val timeoutMs = params?.get("timeoutMs")?.jsonPrimitive?.content?.toLongOrNull() ?: 120_000L
                    val result = execShell(command, cwd, timeoutMs)
                    BridgeResponse(id = request.id, result = result)
                }

                else -> {
                    BridgeResponse(
                        id = request.id,
                        error = BridgeError(code = -32601, message = "Method not found: ${request.method}")
                    )
                }
            }
        } catch (e: Exception) {
            BridgeResponse(
                id = request.id,
                error = BridgeError(code = -32000, message = e.message ?: "Execution error")
            )
        }
    }


    /**
     * 执行一条 bash 工具命令。
     *
     * 必须走 proot 里的 `/bin/sh`：Agent 的 bash 工具面对的是工作区（`/workspace`），
     * 需要 rootfs 里的 git/diff/grep 等工具链；早期实现用 Android 的 `/system/bin/sh`，
     * 导致 git 不存在、工作区路径也不匹配，还会让 workspace/changes 收不到改动。
     */
    private fun execShell(command: String, cwd: String, timeoutMs: Long): JsonObject {
        val nativeLib = context.applicationInfo.nativeLibraryDir
        val rootfs = java.io.File(context.filesDir, "rootfs").absolutePath
        val visibleWorkspace = java.io.File(
            android.os.Environment.getExternalStorageDirectory(), "Documents/DSH-Workspace"
        ).absolutePath
        val passwd = java.io.File(context.filesDir, "runtime/etc/passwd").absolutePath
        val group = java.io.File(context.filesDir, "runtime/etc/group").absolutePath
        val env = mutableListOf(
            "/usr/bin/env",
            "-i",
            "HOME=/root",
            "DSH_HOME=/root/.dsh",
            "PATH=/opt/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8",
            "TMPDIR=/root/.dsh/tmp",
            "/bin/sh",
            "-lc",
            command,
        )
        val proot = mutableListOf(
            "$nativeLib/libproot.so", "-r", rootfs,
            "-b", "/proc", "-b", "/dev", "-b", "/sys",
            "-b", "$visibleWorkspace:/workspace",
            "--link2symlink",
        )
        if (java.io.File(passwd).isFile) {
            proot.addAll(listOf("-b", "$passwd:/etc/passwd"))
        }
        if (java.io.File(group).isFile) {
            proot.addAll(listOf("-b", "$group:/etc/group"))
        }
        proot.addAll(listOf("-w", cwd.ifBlank { "/workspace" }))
        proot.addAll(env)
        val builder = ProcessBuilder(proot)
        builder.redirectErrorStream(false)
        builder.environment()["PROOT_LOADER"] = "$nativeLib/libproot-loader.so"
        // libproot.so 依赖 nativeLibraryDir 里的 libtalloc 等库；直接 exec 时必须显式给
        // LD_LIBRARY_PATH，否则会报 "CANNOT LINK EXECUTABLE ... libtalloc.so not found"。
        builder.environment()["LD_LIBRARY_PATH"] = nativeLib
        builder.environment()["PROOT_TMP_DIR"] =
            java.io.File(context.cacheDir, "proot-tmp").apply { mkdirs() }.absolutePath
        val process = builder.start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread {
            runCatching { process.inputStream.bufferedReader().use { r -> r.forEachLine { stdout.appendLine(it) } } }
        }
        val errThread = Thread {
            runCatching { process.errorStream.bufferedReader().use { r -> r.forEachLine { stderr.appendLine(it) } } }
        }
        outThread.start(); errThread.start()
        val finished = process.waitFor(timeoutMs.coerceAtLeast(1_000), java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outThread.join(2_000); errThread.join(2_000)
        return buildJsonObject {
            put("exitCode", if (finished) process.exitValue() else -1)
            put("stdout", stdout.toString())
            put("stderr", stderr.toString())
            put("timedOut", !finished)
        }
    }
    private fun execProot(argv: List<String>, cwd: String, timeoutMs: Long): JsonObject {
        val nativeLib = context.applicationInfo.nativeLibraryDir
        val rootfs = java.io.File(context.filesDir, "rootfs").absolutePath
        val visible = java.io.File(
            android.os.Environment.getExternalStorageDirectory(), "Documents/DSH-Workspace"
        ).absolutePath
        val command = mutableListOf(
            "$nativeLib/libproot.so", "-r", rootfs,
            "-b", "/proc", "-b", "/dev", "-b", "/sys",
            "-b", "$visible:/workspace", "--link2symlink", "-w", cwd
        )
        command.addAll(argv)
        val builder = ProcessBuilder(command)
        builder.redirectErrorStream(false)
        builder.environment()["PROOT_LOADER"] = "$nativeLib/libproot-loader.so"
        builder.environment()["LD_LIBRARY_PATH"] = nativeLib
        builder.environment()["PROOT_TMP_DIR"] = java.io.File(context.cacheDir, "proot-tmp").apply { mkdirs() }.absolutePath
        return runProcess(builder, timeoutMs)
    }

    private fun runProcess(builder: ProcessBuilder, timeoutMs: Long): JsonObject {
        val process = builder.start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread { runCatching { process.inputStream.bufferedReader().use { r -> r.forEachLine { stdout.appendLine(it) } } } }
        val errThread = Thread { runCatching { process.errorStream.bufferedReader().use { r -> r.forEachLine { stderr.appendLine(it) } } } }
        outThread.start(); errThread.start()
        val finished = process.waitFor(timeoutMs.coerceAtLeast(1_000), java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outThread.join(2_000); errThread.join(2_000)
        return buildJsonObject {
            put("exitCode", if (finished) process.exitValue() else -1)
            put("stdout", stdout.toString())
            put("stderr", stderr.toString())
            put("timedOut", !finished)
        }
    }    private fun showNotification(title: String, content: String) {
        val channelId = "dsh_bridge_notify"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "DSH 设备通知", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}




