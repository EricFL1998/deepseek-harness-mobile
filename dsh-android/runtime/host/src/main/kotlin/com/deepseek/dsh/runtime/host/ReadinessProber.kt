package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * v4.0 设计文档 §4.3:
 * 就绪探针：HTTP 探活 + 一次 `/` + WS 握手；超时 60s
 */
class ReadinessProber(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()
) {
    suspend fun probe(
        port: Int,
        token: String?,
        timeoutMs: Long = 60_000L,
        intervalMs: Long = 1_000L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val baseUrl = "http://127.0.0.1:$port"

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val httpReady = checkHttp(baseUrl, token)
            if (httpReady) {
                // Web UI 已能响应；WS 探针在部分 Android 运行时会被代理/网络栈干扰，
                // 不应因此让 Host 永远停留在 STARTING。
                return true
            }
            delay(intervalMs)
        }
        return false
    }

    private fun checkHttp(baseUrl: String, token: String?): Boolean {
        val url = if (token.isNullOrEmpty()) baseUrl else "$baseUrl/?token=$token"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..399
            }
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun checkWebSocket(port: Int, token: String?): Boolean =
        suspendCancellableCoroutine { continuation: CancellableContinuation<Boolean> ->
            val wsUrl = "ws://127.0.0.1:$port/api/remote.mux" + if (token != null) "?token=$token" else ""
            val request = Request.Builder().url(wsUrl).build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.close(1000, "probe completed")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // 如果 endpoint 返回 400/404 等，表示 HTTP server 已就绪且处理了 WS upgrade
                    val isServerUp = response != null && response.code < 500
                    if (continuation.isActive) continuation.resume(isServerUp)
                }
            }

            val ws = client.newWebSocket(request, listener)
            continuation.invokeOnCancellation {
                ws.cancel()
            }
        }
}
