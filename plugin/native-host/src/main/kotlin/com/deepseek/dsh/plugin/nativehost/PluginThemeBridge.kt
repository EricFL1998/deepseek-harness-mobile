package com.deepseek.dsh.plugin.nativehost

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.dshPanelColors
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme

/**
 * 把 App 的 MaterialTheme 适配成插件可见的 [PluginTheme] 只读快照。
 *
 * 深色模式 / 系统字体缩放由 MaterialTheme 统一处理，快照随之更新并触发宿主
 * 容器重组。插件拿到的永远是当前正确取值，不写自己的深浅色分支。
 *
 * 浮层字段直接取 App 菜单系统（DshMenuCard）的材质：半透明菜单底色合成到
 * 基础背景、发丝边、16dp 大圆角，因此插件 popover 和模型/权限菜单是同一套
 * 视觉，而不是另一张 Material Card。
 */
@Composable
fun rememberPluginTheme(): PluginTheme {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    val panel = dshPanelColors()
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
        colorPopupSurface = panel.menuSurfaceFill.compositeOver(panel.bgBase),
        colorPopupBorder = if (panel.isDark) panel.borderL3 else panel.borderL1,
        cornerPopup = 16.dp,
        textTitle = t.titleMedium,
        textBody = t.bodyMedium,
        textCaption = t.bodySmall,
        textPopupTitle = t.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        textPopupBody = t.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
        // 主题由 App/Host 决定（ui-theme），不能用系统深浅色判断；
        // dshPanelColors() 已经反映实际主题。
        isDark = panel.isDark,
        // 设置页/面板与 App 的 DshPanel 完全同源：隔层底色、发丝边、三级文字色。
        colorPanel = panel.bgLayer2,
        colorPanelBorder = panel.borderL2,
        colorPanelElevated = panel.bgLayer3,
        colorLabelPrimary = panel.labelPrimary,
        colorLabelSecondary = panel.labelSecondary,
        colorLabelTertiary = panel.labelTertiary,
        colorLink = c.primary,
        panelCorner = 16.dp,
        rowMinHeight = 44.dp,
        // 按钮/开关/输入框与 App 的 DshDialogButton / DshSwitch 同源。
        colorButtonPrimary = panel.buttonPrimaryFill,
        colorButtonPrimaryHover = panel.buttonPrimaryHover,
        colorButtonTextOnPrimary = panel.labelPrimaryForeground,
        colorButtonSecondaryBorder = panel.borderL3,
        colorButtonSecondaryText = panel.labelPrimary,
        colorButtonDanger = panel.stateErrorPrimary,
        colorSwitchTrackOff = panel.borderL3,
        colorSwitchThumb = panel.switchThumb,
        colorInputBackground = panel.bgLayer3,
        colorInputBorder = panel.borderL2,
        colorChipBackground = panel.bgModulePlatform,
        colorChipText = panel.labelSecondary,
    )
}
