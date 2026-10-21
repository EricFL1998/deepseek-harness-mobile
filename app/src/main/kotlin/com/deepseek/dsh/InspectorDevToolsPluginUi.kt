package com.deepseek.dsh

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 「开发者工具」里的 NodeJS 诊断（Chrome DevTools）。
 *
 * 官方 `experimental-inspector` 把 chrome-devtools-frontend 放进 `shell.bottom`
 * 的 iframe，并通过 Host 代理 CDP。原生侧没有可移植的等价物，所以按官方同样
 * 的做法：用 WebView 载入同一个 DevTools 前端（Host 的
 * `/inspector/devtools/devtools_app.html`），并把会话 cookie 同步给 WebView。
 *
 * 入口与官方一致：不是插件详情页，而是调试面板（会话数据诊断）里的一个页签；
 * 手机没有 Ctrl/Cmd+Shift+. 的底部面板快捷键。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun NodeJsDevToolsPane() {
    val colors = LocalDshPanelColors.current
    val context = LocalContext.current
    val app = context.applicationContext as? DshApplication
    val port = app?.localHostPort ?: 0
    val token = app?.localHostToken
    var reloadKey by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.inspector_node_diagnostics), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = stringResource(R.string.inspector_devtools_hint),
                    color = colors.labelTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.bgModulePlatform)
                    .border(0.5.dp, colors.borderL2, RoundedCornerShape(9.dp))
                    .clickable { reloadKey += 1 }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.inspector_reload), color = colors.labelSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            port <= 0 -> Text(
                text = stringResource(R.string.inspector_host_not_running),
                color = colors.labelTertiary,
                fontSize = 12.5.sp,
            )
            else -> {
                val url = remember(port, reloadKey) {
                    "http://127.0.0.1:$port/inspector/devtools/devtools_app.html?disableLocaleInfoBar=true"
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.5.dp, colors.borderL2, RoundedCornerShape(10.dp)),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = WebViewClient()
                            // Host 的调试路由走会话 cookie；把 App 的 cookie jar 内容同步给 WebView。
                            runCatching {
                                @Suppress("UNUSED_VARIABLE")
                                val ignored = token
                                val cookies = app?.cookieJar?.loadForRequest(
                                    "http://127.0.0.1:$port/".toHttpUrl(),
                                ).orEmpty()
                                val header = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                                if (header.isNotBlank()) {
                                    CookieManager.getInstance().setCookie("http://127.0.0.1:$port/", header)
                                    CookieManager.getInstance().flush()
                                }
                            }
                            loadUrl(url)
                        }
                    },
                )
            }
        }
    }
}
