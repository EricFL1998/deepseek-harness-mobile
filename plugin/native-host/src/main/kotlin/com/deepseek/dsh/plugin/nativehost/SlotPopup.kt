package com.deepseek.dsh.plugin.nativehost

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject

/**
 * 锚定在某个窗口矩形上方的 slot 浮层（用于 conversation.input.overlay 等）。
 *
 * 与普通 NativeSlot 不同，它**不参与父布局测量**：内容放进 Popup，锚点来自
 * App 捕获的输入卡 bounds，所以不会把输入行撑开。
 */
@Composable
fun AnchoredSlotPopup(
    slot: NativeSlot,
    spec: JsonObject,
    anchorBounds: Rect?,
    modifier: Modifier = Modifier,
) {
    val host = NativeHost.LocalHost.current ?: return
    val theme = NativeHost.LocalTheme.current ?: return
    val contributions = SlotRegistry.resolve(slot)
    if (contributions.isEmpty() || anchorBounds == null) return
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    val provider = remember(anchorBounds, gapPx) {
        AnchorTopEndPositionProvider(anchorBounds, gapPx)
    }
    val slotHost = remember(host) { SlotBridgeHost(host) }
    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(focusable = false),
    ) {
        Box(modifier = modifier.widthIn(max = 360.dp)) {
            com.deepseek.dsh.plugin.nativehost.NativeSlotHost(
                slot = slot,
                spec = spec,
                theme = theme,
                host = slotHost,
                inline = false,
            )
        }
    }
}

/** 把浮层放在锚点上方并右对齐；空间不够时贴锚点顶部内侧。 */
private class AnchorTopEndPositionProvider(
    private val anchor: Rect,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val x = (anchor.right.toInt() - popupContentSize.width).coerceIn(0, maxX)
        val above = anchor.top.toInt() - popupContentSize.height - gapPx
        val y = if (above >= 0) above else (anchor.top.toInt() + gapPx).coerceIn(0, maxY)
        return IntOffset(x, y)
    }
}
