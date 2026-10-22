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

/**
 * 当前界面是否为深色主题（由 DshTheme 提供）。
 *
 * 自定义面板/设置页以前直接读系统深浅色：Host 把主题设成 dark 而手机系统是
 * light 时会出现混搭。所有自绘配色都应该读这个值。
 */
val LocalDshDarkTheme = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun DshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // 严格跟随手机系统深浅色模式
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    // 自绘面板也跟随主题：以前它只读系统深浅色，Host 选择暗色而系统是浅色时
    // 会出现「深色背景 + 浅色输入框」的混搭。
    val panelColors = if (darkTheme) DshDarkPanelColors else DshLightPanelColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalDshPanelColors provides panelColors,
        LocalDshDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
