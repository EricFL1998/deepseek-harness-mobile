package com.deepseek.dsh.core.protocol

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * v4.0 设计文档 §5.5:
 * 本地：Runtime 启动产出 launcher URL（含 token）→ `GET /?token=` → 签名 cookie
 * 远程：网关设备令牌 / CookieJar 隔离
 */
class HostCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostKey = url.host + ":" + url.port
        val existing = cookieStore.getOrPut(hostKey) { mutableListOf() }
        synchronized(existing) {
            cookies.forEach { newCookie ->
                existing.removeAll { it.name == newCookie.name }
                existing.add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostKey = url.host + ":" + url.port
        val list = cookieStore[hostKey] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(list) {
            // 过滤过期 cookie
            list.removeAll { it.expiresAt < now }
            return list.toList()
        }
    }

    fun clearHost(host: String, port: Int) {
        cookieStore.remove("$host:$port")
    }

    fun getCookies(host: String, port: Int): List<Cookie> {
        return cookieStore["$host:$port"]?.toList() ?: emptyList()
    }
}

class HostAuthenticator(
    private val cookieJar: HostCookieJar = HostCookieJar(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .build()
) {
    suspend fun exchangeTokenForCookie(
        baseUrl: String,
        token: String?
    ): RemoteResult<List<Cookie>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val parsedUrl = baseUrl.toHttpUrlOrNull()
            ?: return@withContext RemoteResult.Failure("dsh/auth/failed", "Invalid baseUrl: $baseUrl")

        val urlWithToken = if (token.isNullOrEmpty()) {
            baseUrl
        } else {
            val sep = if (baseUrl.contains("?")) "&" else "?"
            "$baseUrl${sep}token=$token"
        }

        val request = Request.Builder()
            .url(urlWithToken)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code in 200..399) {
                    val cookies = cookieJar.getCookies(parsedUrl.host, parsedUrl.port)
                    RemoteResult.Success(cookies)
                } else if (response.code == 401 || response.code == 403) {
                    RemoteResult.Failure("dsh/auth/failed", "Authentication rejected (HTTP ${response.code})")
                } else {
                    RemoteResult.Failure("dsh/net/error", "Server returned HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            RemoteResult.Failure("dsh/net/timeout", e.message ?: "Network error during auth exchange")
        }
    }
}
