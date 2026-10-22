package com.deepseek.dsh.plugin.nativehost

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.plugin.nativesdk.pages.PageContent
import com.deepseek.dsh.plugin.nativesdk.pages.PageLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject

/**
 * Full-page host for a native settings section. Unlike the Web fallback, this composable
 * never creates a WebView. Events and refresh handling stay with the caller, which owns
 * the Host connection and can supply the latest JSON-only page spec.
 */
@Composable
fun NativePluginComponentPane(
    page: PageContent,
    componentId: String,
    spec: JsonObject,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pluginRpc: (suspend (method: String, payload: JsonObject) -> Result<String>)? = null,
    dispatchEvent: (suspend (componentId: String, event: String, payload: JsonObject) -> Unit)? = null,
    refreshEvents: SharedFlow<String>? = null,
    /** 作为设置页子内容嵌入时传 false：返回条由设置页顶层 Header 提供。 */
    showHeader: Boolean = true,
) {
    val host = NativeHost.LocalHost.current
    val theme = NativeHost.LocalTheme.current
    val context = LocalContext.current
    val pageHost = remember(host, componentId, pluginRpc, dispatchEvent, context) {
        if (host == null) null else NativePageHost(
            componentId = componentId,
            delegate = host,
            pluginRpc = pluginRpc ?: { _, _ -> Result.failure(IllegalStateException("Host RPC 未连接")) },
            dispatch = dispatchEvent ?: { _, _, _ -> },
        )
    }
    val mounted = remember { mutableStateOf(false) }
    DisposableEffect(componentId) {
        mounted.value = true
        onDispose { mounted.value = false }
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = page.entry.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (pageHost != null && theme != null) {
            CompositionLocalProvider(PageLifecycle.LocalIsActive provides { mounted.value }) {
                androidx.compose.runtime.key(componentId) {
                    page.Content(spec = spec, theme = theme, host = pageHost, refreshEvents = refreshEvents)
                }
            }
        }
    }
}
