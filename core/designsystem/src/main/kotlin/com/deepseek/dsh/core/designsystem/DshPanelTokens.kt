package com.deepseek.dsh.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * DeepSeek Harness surface tokens consumed by menus, panels and dialogs.
 *
 * Values mirror the web client's `ui-theme` design platform
 * (`design-platform.css`): menu material fills, label inks, hairlines, hover
 * fills and the danger family. Theme selection follows the phone's dark mode,
 * exactly like the client's `data-ds-dark-theme` switch.
 *
 * @property isDark whether these values describe the dark palette.
 * @property labelPrimary primary text and menu row label ink.
 * @property labelSecondary secondary text (dialog descriptions, menu icons on some surfaces).
 * @property labelTertiary tertiary text (menu section labels and trailing values).
 * @property labelCaption caption ink (shortcut keys).
 * @property labelPrimaryForeground text/icon ink on a primary-filled control.
 * @property menuIcon leading icon ink inside menu rows.
 * @property bgBase page background.
 * @property bgLayer2 dialog/secondary layer background.
 * @property bgMask1 modal mask fill.
 * @property borderL1 menu elevation stroke (hairline).
 * @property borderL2 separators (menu separator, footer hairline).
 * @property borderL3 outlined controls.
 * @property borderL4 stronger outlines.
 * @property interactiveHover pointer/keyboard highlight fill.
 * @property interactiveHoverDanger destructive row highlight fill.
 * @property interactiveActive pressed fill.
 * @property stateErrorPrimary destructive label/icon ink.
 * @property menuSurfaceFill translucent menu material.
 * @property menuGroupHeaderFill sticky menu group heading fill.
 * @property skeleton loading skeleton fill.
 * @property buttonPrimaryFill primary button fill (brand primary).
 * @property buttonPrimaryHover primary button pressed/hover fill.
 * @property dialogFill dialog card fill (bg-layer-2).
 */
data class DshPanelColors(
    val isDark: Boolean,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelCaption: Color,
    val labelPrimaryForeground: Color,
    val menuIcon: Color,
    val bgBase: Color,
    val bgLayer2: Color,
    val bgLayer3: Color,
    val bgModulePlatform: Color,
    val bgMask1: Color,
    val borderL1: Color,
    val borderL2: Color,
    val borderL3: Color,
    val borderL4: Color,
    val interactiveHover: Color,
    val interactiveHoverDanger: Color,
    val interactiveActive: Color,
    val stateErrorPrimary: Color,
    val menuSurfaceFill: Color,
    val menuGroupHeaderFill: Color,
    val skeleton: Color,
    val buttonPrimaryFill: Color,
    val buttonPrimaryHover: Color,
    val switchThumb: Color,
) {
    /** Dialog card fill: the secondary layer. */
    val dialogFill: Color get() = bgLayer2
}

/** Light-theme panel tokens. */
val DshLightPanelColors = DshPanelColors(
    isDark = false,
    labelPrimary = Color(0xFF0F1115),
    labelSecondary = Color(0xFF61666B),
    labelTertiary = Color(0xFF81858C),
    labelCaption = Color(0xFFADB2B8),
    labelPrimaryForeground = Color(0xFFFFFFFF),
    menuIcon = Color(0xFF353638),
    bgBase = Color(0xFFFFFFFF),
    bgLayer2 = Color(0xFFFFFFFF),
    bgLayer3 = Color(0xFFFFFFFF),
    bgModulePlatform = Color(0xFFF5F6F7),
    bgMask1 = Color(0x3D000000),
    borderL1 = Color(0x0A000000),
    borderL2 = Color(0x1A000000),
    borderL3 = Color(0x1F000000),
    borderL4 = Color(0x29000000),
    interactiveHover = Color(0x0F263148),
    interactiveHoverDanger = Color(0x0DEC1313),
    interactiveActive = Color(0x1A263148),
    stateErrorPrimary = Color(0xFFEC1313),
    menuSurfaceFill = Color(0x94F8F9FA),
    menuGroupHeaderFill = Color(0xF0F8F9FA),
    skeleton = Color(0x0A000000),
    buttonPrimaryFill = Color(0xFF0F1115),
    buttonPrimaryHover = Color(0xFF43454A),
    switchThumb = Color(0xFFFFFFFF),
)

/** Dark-theme panel tokens. */
val DshDarkPanelColors = DshPanelColors(
    isDark = true,
    labelPrimary = Color(0xFFF9FAFB),
    labelSecondary = Color(0xFFCFD3D6),
    labelTertiary = Color(0xFFADB2B8),
    labelCaption = Color(0xFF81858C),
    labelPrimaryForeground = Color(0xFF0F1115),
    menuIcon = Color(0xFFEBEEF2),
    bgBase = Color(0xFF151517),
    bgLayer2 = Color(0xFF2C2C2E),
    bgLayer3 = Color(0xFF353638),
    bgModulePlatform = Color(0xFF353638),
    bgMask1 = Color(0x80000000),
    borderL1 = Color(0x0FFFFFFF),
    borderL2 = Color(0x1FFFFFFF),
    borderL3 = Color(0x29FFFFFF),
    borderL4 = Color(0x33FFFFFF),
    interactiveHover = Color(0x14FFFFFF),
    interactiveHoverDanger = Color(0x26F25A5A),
    interactiveActive = Color(0x24FFFFFF),
    stateErrorPrimary = Color(0xFFF25A5A),
    menuSurfaceFill = Color(0x7343454A),
    menuGroupHeaderFill = Color(0xF0303136),
    skeleton = Color(0x14FFFFFF),
    buttonPrimaryFill = Color(0xFFF9FAFB),
    buttonPrimaryHover = Color(0xFFEBEEF2),
    switchThumb = Color(0xFFADB2B8),
)

/** Resolve the panel tokens for the current light/dark mode. */
@Composable
fun dshPanelColors(): DshPanelColors {
    val provided = LocalDshPanelColors.current
    // DshTheme 会按实际主题 provide；未被 provide（默认 light）时，深色系统仍取暗色，
    // 保持预览与旧调用点的行为。
    return if (provided.isDark || !isSystemInDarkTheme()) {
        provided
    } else {
        DshDarkPanelColors
    }
}

/** Panel palette for descendants; defaults to light so previews stay deterministic. */
val LocalDshPanelColors = staticCompositionLocalOf { DshLightPanelColors }
