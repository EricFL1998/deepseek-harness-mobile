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
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * v4.0 设计文档 §10.2:
 * 本地回环 BridgeServer (127.0.0.1 随机端口)，承载 Node 容器侧 dsh-android-bridge 插件发来的 JSON-RPC
 */
class BridgeServer(
    private val context: Context,
    private val port: Int = 0 // 0 = 随机可用端口
) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val json = Json { ignoreUnknownKeys = true }
    private var isRunning = false

    val localPort: Int get() = serverSocket?.localPort ?: 0

    @Synchronized
    fun start(): Int {
        if (isRunning) return localPort

        val ss = ServerSocket(port)
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

    private fun showNotification(title: String, content: String) {
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
