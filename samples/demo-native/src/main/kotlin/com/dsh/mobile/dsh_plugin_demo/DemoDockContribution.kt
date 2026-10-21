package com.dsh.mobile.dsh_plugin_demo

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.render.PluginPopover
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject

/**
 * 输入栏 dock 的 Demo 胶囊：点击/长按弹出贴锚点的 popup。
 *
 * 这里展示两个关键点：
 * - `PluginPopover` 自动贴着胶囊上方、右对齐；
 * - popup 内容使用 App 菜单材质（PluginTheme 的 popup 字段），不自己画 Card。
 */
class DemoDockContribution : SlotContribution() {
    override val slot: NativeSlot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes: Set<String> = setOf("demo.dock")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        var open by remember { mutableStateOf(false) }
        Box {
            Text(
                text = "Demo 42%",
                style = theme.textCaption.copy(fontWeight = FontWeight.Medium),
                color = theme.colorPrimary,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { open = !open },
                            onLongPress = { open = true },
                        )
                    }
                    .padding(horizontal = theme.spacingUnit / 2),
            )
            if (open) {
                PluginPopover(theme = theme, onDismiss = { open = false }) {
                    Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                        Text(
                            text = "Demo 插件",
                            style = theme.textPopupTitle,
                            color = theme.colorOnSurfaceVariant,
                        )
                        DemoRow("状态", "已连接", theme)
                        DemoRow("请求数", "42", theme)
                        Text(
                            text = "点击外部关闭",
                            style = theme.textCaption,
                            color = theme.colorOutline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoRow(label: String, value: String, theme: PluginTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = theme.textPopupBody, color = theme.colorOnSurfaceVariant)
        Text(
            value,
            style = theme.textPopupBody.copy(fontWeight = FontWeight.Medium),
            color = theme.colorOnSurface,
        )
    }
}
