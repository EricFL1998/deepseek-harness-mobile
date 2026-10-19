package com.deepseek.dsh

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 直接加载 DSH Host 提供的官方 Web Client，并切换到指定面板。
 * UI、分类、图标、插件项全部来自 Host 的前端资源，客户端不复制任何插件元数据。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficialClientPanel(
    port: Int,
    token: String?,
    panelLabel: String,
    modifier: Modifier = Modifier,
) {
    val baseUrl = "http://127.0.0.1:$port/"
    val clickPanelScript = remember(panelLabel) {
        """
        (function() {
          var tries = 0;
          var timer = setInterval(function() {
            tries++;
            var buttons = Array.from(document.querySelectorAll('button[aria-label]'));
            var target = buttons.find(function(b) { return b.getAttribute('aria-label') === '$panelLabel'; });
            if (!target && tries % 4 === 0) {
              target = buttons.find(function(b) { return (b.textContent || '').trim() === '$panelLabel'; });
            }
            if (target) { target.click(); clearInterval(timer); }
            else if (tries > 40) { clearInterval(timer); }
          }, 250);
        })();
        """.trimIndent()
    }
    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(clickPanelScript, null)
                        }
                    }
                    val target = if (token.isNullOrBlank()) baseUrl else "$baseUrl?token=${Uri.encode(token)}"
                    loadUrl(target)
                }
            },
            update = { webView ->
                if (webView.url == null) {
                    val target = if (token.isNullOrBlank()) baseUrl else "$baseUrl?token=${Uri.encode(token)}"
                    webView.loadUrl(target)
                }
            },
        )
    }
}
