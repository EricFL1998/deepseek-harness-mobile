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

@Composable
fun DshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
