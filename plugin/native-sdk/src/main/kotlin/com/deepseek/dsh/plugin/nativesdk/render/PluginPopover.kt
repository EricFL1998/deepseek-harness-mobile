package com.deepseek.dsh.plugin.nativesdk.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme

/**
 * 与 App 菜单同材质的锚定浮层：贴着触发元素向上/右弹出，而不是居中 Dialog。
 *
 * 材质来自 [PluginTheme] 的 popup 字段（App 用 `DshMenuCard` 的
 * `menuSurfaceFill` / 发丝边 / 16dp 圆角映射进来），因此插件浮层和模型菜单、
 * 权限菜单等官方弹层看起来是同一套设计，而不是插件自己造一套 Card。
 *
 * 用法：把触发元素和本组件放进同一个 Box，展开时调用：
 *
 * ```kotlin
 * Box {
 *   Text("DeepSeek ¥24.8", modifier = Modifier.clickable { open = true })
 *   if (open) {
 *     PluginPopover(theme = theme, onDismiss = { open = false }) {
 *       Text("DeepSeek Account 额度", style = theme.textPopupTitle)
 *       ...
 *     }
 *   }
 * }
 * ```
 */
@Composable
fun PluginPopover(
    theme: PluginTheme,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }
    val positionProvider = remember(gapPx) { AboveEndPositionProvider(gapPx) }
    val shape = theme.shapePopup
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = modifier
                .widthIn(min = 220.dp, max = 320.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f),
                    spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f),
                )
                .clip(shape)
                .background(theme.colorPopupSurface)
                .border(0.5.dp, theme.colorPopupBorder, shape)
                .padding(theme.spacingUnit * 1.75f),
            content = content,
        )
    }
}

/** 放在锚点上方并右对齐，水平夹在窗口内，垂直不越过窗口顶。 */
private class AboveEndPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
