package com.deepseek.dsh.plugin.runtimeweb

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * v4.0 设计文档 §12.3: WebView 安全加固与 Web 岛容器
 * - JS 开，File/Content 关，Mixed Content 拒，第三方 Cookie 仅 host
 * - shouldOverrideUrlLoading 白名单
 * - onRenderProcessGone 恢复
 */
class WebIslandContainer(
    context: Context,
    private val allowedHost: String = "127.0.0.1",
    private val allowedPort: Int = 8080,
    private val onBridgeMessage: ((String, String) -> Unit)? = null
) : WebView(context) {

    @Serializable
    private data class BridgeEnvelope(val type: String, val payload: kotlinx.serialization.json.JsonElement? = null)

    init {
        setupSecuritySettings()
        setupClients()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSecuritySettings() {
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            domStorageEnabled = true
            databaseEnabled = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@WebIslandContainer, false)
        }

        // 注册安全 JSBridge
        addJavascriptInterface(JsBridge(), "DshNativeBridge")
    }

    private fun setupClients() {
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                val host = uri.host
                val port = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80

                // 仅允许本应用关联的 local loopback 或受信域名
                if (host == allowedHost && (port == allowedPort || allowedPort == 0)) {
                    return false
                }
                // 外链不允许直接在岛内导航
                return true
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                // 崩溃安全兜底
                destroy()
                return true
            }
        }
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun postMessage(jsonString: String) {
            try {
                val envelope = Json.decodeFromString<BridgeEnvelope>(jsonString)
                onBridgeMessage?.invoke(envelope.type, envelope.payload?.toString() ?: "")
            } catch (e: Exception) {
                // Ignore parse errors from untrusted web
            }
        }
    }

    fun loadSurface(surfaceSlot: String, pluginId: String, isolated: Boolean = true, token: String? = null) {
        val base = "http://$allowedHost:$allowedPort"
        val queryParts = mutableListOf<String>()
        if (isolated) {
            queryParts.add("__dsh_surface=${Uri.encode(surfaceSlot)}")
            queryParts.add("__dsh_plugin=${Uri.encode(pluginId)}")
            queryParts.add("__dsh_shell=none")
        }
        val query = if (queryParts.isNotEmpty()) {
            "?" + queryParts.joinToString("&")
        } else {
            ""
        }
        val targetUrl = "$base/$query"

        // 如果提供了 token 且 CookieManager 尚未包含 cookie，先交换或直接设置
        if (!token.isNullOrBlank()) {
            val cm = CookieManager.getInstance()
            val existing = cm.getCookie(base)
            if (existing.isNullOrBlank() || !existing.contains("dsh-web-session")) {
                // 加载带 token 的根地址完成换取 cookie，然后再定向到目标表面
                loadUrl("$base/?token=${Uri.encode(token)}")
                postDelayed({
                    loadUrl(targetUrl)
                }, 500)
                return
            }
        }

        loadUrl(targetUrl)
    }
}
