package com.deepseek.dsh.plugin.nativehost

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme

/**
 * 把 App 的 MaterialTheme 适配成插件可见的 [PluginTheme] 只读快照。
 *
 * 深色模式 / 系统字体缩放由 MaterialTheme 统一处理，快照随之更新并触发宿主
 * 容器重组。插件拿到的永远是当前正确取值，不写自己的深浅色分支。
 */
@Composable
fun rememberPluginTheme(): PluginTheme {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    return PluginTheme(
        colorPrimary = c.primary,
        colorOnPrimary = c.onPrimary,
        colorSurface = c.surface,
        colorOnSurface = c.onSurface,
        colorSurfaceVariant = c.surfaceVariant,
        colorOnSurfaceVariant = c.onSurfaceVariant,
        colorError = c.error,
        colorOnError = c.onError,
        colorOutline = c.outline,
        textTitle = t.titleMedium,
        textBody = t.bodyMedium,
        textCaption = t.bodySmall,
        isDark = androidx.compose.foundation.isSystemInDarkTheme(),
    )
}
