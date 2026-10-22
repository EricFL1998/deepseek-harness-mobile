package com.deepseek.dsh.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DshPrimaryDark,
    onPrimary = DshOnPrimaryDark,
    primaryContainer = DshPrimaryContainerDark,
    onPrimaryContainer = DshOnPrimaryContainerDark,
    secondary = DshSecondaryDark,
    onSecondary = DshOnSecondaryDark,
    secondaryContainer = DshSecondaryContainerDark,
    onSecondaryContainer = DshOnSecondaryContainerDark,
    tertiary = DshTertiaryDark,
    onTertiary = DshOnTertiaryDark,
    tertiaryContainer = DshTertiaryContainerDark,
    onTertiaryContainer = DshOnTertiaryContainerDark,
    background = DshBackgroundDark,
    onBackground = DshOnBackgroundDark,
    surface = DshSurfaceDark,
    onSurface = DshOnSurfaceDark,
    surfaceVariant = DshSurfaceVariantDark,
    onSurfaceVariant = DshOnSurfaceVariantDark,
    outline = DshOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = DshPrimaryLight,
    onPrimary = DshOnPrimaryLight,
    primaryContainer = DshPrimaryContainerLight,
    onPrimaryContainer = DshOnPrimaryContainerLight,
    secondary = DshSecondaryLight,
    onSecondary = DshOnSecondaryLight,
    secondaryContainer = DshSecondaryContainerLight,
    onSecondaryContainer = DshOnSecondaryContainerLight,
    tertiary = DshTertiaryLight,
    onTertiary = DshOnTertiaryLight,
    tertiaryContainer = DshTertiaryContainerLight,
    onTertiaryContainer = DshOnTertiaryContainerLight,
    background = DshBackgroundLight,
    onBackground = DshOnBackgroundLight,
    surface = DshSurfaceLight,
    onSurface = DshOnSurfaceLight,
    surfaceVariant = DshSurfaceVariantLight,
    onSurfaceVariant = DshOnSurfaceVariantLight,
    outline = DshOutlineLight
)

/**
 * 会话正文的字号（sp），来自 Host 的 `ui-theme.fontSize`。
 *
 * 设置页的「字号」只影响对话内容，不缩放整个界面，所以用一个 CompositionLocal
 * 传给聊天区的文本渲染，而不是改全局 density。
 */
val LocalConversationFontSize = androidx.compose.runtime.compositionLocalOf { 14 }

@Composable
fun DshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // 严格跟随手机系统深浅色模式
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
