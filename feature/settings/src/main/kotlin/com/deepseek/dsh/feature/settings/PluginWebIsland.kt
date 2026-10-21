package com.deepseek.dsh.feature.settings

import android.annotation.SuppressLint
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.deepseek.dsh.plugin.runtimeweb.WebIslandContainer

/**
 * 插件的「完整界面」：把 Host 的 Web 客户端放进一个 WebView 岛屿里运行。
 *
 * 静态转译只能给出插件**声明的结构**；服务商列表、登录态、实时用量这类内容，
 * 以及登录/登出/刷新令牌这些操作，都需要插件自己的 JS 和它到 Host 的 RPC。
 * 与其假装能转译出来，不如让真实界面在这里跑：岛屿加载的是同一个 Host、
 * 同一份会话 cookie，所以里面的操作就是真实操作，效果与浏览器一致。
 *
 * 这个组件只负责**一块 WebView**：尺寸由调用方给（必须是受限高度，不能塞进
 * `verticalScroll`），加载/失败状态经 [onLoadingChanged] / [onFailed] 交给调用方。
 *
 * WebView 外面套一层 `FrameLayout`：WebView 的布局视口修复需要一次真实的
 * 父级重新布局（见 [WebIslandContainer] 的 `nudgeViewport`），而 Compose 的
 * `AndroidView` 只保证它直接持有的那个 View 的尺寸，改变 WebView 自己的
 * LayoutParams 不会被 Compose 的测量采用。
 *
 * @param port 本地 Host 的 loopback 端口；0 表示 Host 未运行。
 * @param token 启动 token，用于在 WebView 里换取会话 cookie。
 * @param reloadKey 变化时重载页面（宿主 Header 上的刷新按钮）。
 * @param focusSettingsLabels 自动打开的设置分区标签候选。
 * @param onLoadingChanged 主框架加载开始/结束。
 * @param onFailed 主框架加载失败或 Web 客户端启动失败。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluginWebIsland(
    port: Int,
    token: String?,
    modifier: Modifier = Modifier,
    reloadKey: Int = 0,
    focusSettingsLabels: List<String> = emptyList(),
    focusSectionId: String? = null,
    onLoadingChanged: (Boolean) -> Unit = {},
    onFailed: (String) -> Unit = {},
) {
    if (port <= 0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Host 未运行，无法打开完整界面",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    // AndroidView 的 factory 只跑一次；后续重组只能经 update 改状态。记住“已经
    // 应用的 reloadKey / focus 标签”，update 时对比，只有真正变化才重载页面。
    val currentReloadKey = rememberUpdatedState(reloadKey)
    val currentFocusLabels = rememberUpdatedState(focusSettingsLabels)
    val currentFocusSectionId = rememberUpdatedState(focusSectionId)
    val appliedReloadKey = remember { intArrayOf(reloadKey) }
    val appliedFocusLabels = remember { androidx.compose.runtime.mutableStateOf(focusSettingsLabels) }
    val appliedFocusSectionId = remember { androidx.compose.runtime.mutableStateOf(focusSectionId) }
    val loadingSink = rememberUpdatedState(onLoadingChanged)
    val failedSink = rememberUpdatedState(onFailed)
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val frame = FrameLayout(context)
            val web = WebIslandContainer(
                context = context,
                allowedPort = port,
                onLoadingChanged = { loading -> loadingSink.value(loading) },
                onFailed = { reason -> failedSink.value(reason) },
            )
            frame.addView(
                web,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            web.apply {
                // 自动走到这个插件在 Web 客户端里的设置分区；失败则停在客户端本身。
                focusSettingsSection(focusSettingsLabels, focusSectionId)
                // 非隔离加载：插件页面是 Web 客户端里的一层，没有独立路由；
                // 岛屿给出完整界面，用户在其中进入 设置 → 插件分区。
                loadSurface(
                    surfaceSlot = "settings.section",
                    pluginId = "",
                    isolated = false,
                    token = token,
                )
            }
            frame
        },
        update = { frame ->
            val view = frame.getChildAt(0) as? WebIslandContainer ?: return@AndroidView
            val target = currentReloadKey.value
            if (appliedReloadKey[0] != target) {
                appliedReloadKey[0] = target
                view.reloadLast()
            }
            val labels = currentFocusLabels.value
            val focusId = currentFocusSectionId.value
            if (labels != appliedFocusLabels.value || focusId != appliedFocusSectionId.value) {
                appliedFocusLabels.value = labels
                appliedFocusSectionId.value = focusId
                view.focusSettingsSection(labels, focusId)
            }
        },
        onRelease = { frame ->
            (frame.getChildAt(0) as? WebIslandContainer)?.let { web ->
                runCatching { web.destroy() }
            }
        },
    )
}
