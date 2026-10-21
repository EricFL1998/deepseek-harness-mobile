package com.dsh.mobile.dsh_plugin_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通用 slot 演示贡献：在几乎每个 NativeSlot 上放一个可识别的 Demo 标记。
 *
 * 这不是业务 UI，只用于展示“落点插到哪里、宿主长什么样”。
 */
class DemoSlotContribution(
    private val entry: DemoSlotEntry,
) : SlotContribution() {
    override val slot: NativeSlot = entry.slot
    override val supportedTypes: Set<String> = setOf("demo.slot")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        if (slot in blockSlots) {
            DemoBlock(entry = entry, theme = theme, spec = spec)
        } else {
            DemoBadge(entry = entry, theme = theme)
        }
    }

    private companion object {
        val blockSlots = setOf(
            NativeSlot.SETTINGS_SHEET,
            NativeSlot.SETTINGS_PROVIDER_CARD,
            NativeSlot.SIDEBAR_PANEL,
            NativeSlot.TOOL_VIEW_SHEET,
            NativeSlot.CHAT_STREAM,
            NativeSlot.WEB_ISLAND_FULL,
        )
    }
}

@Composable
private fun DemoBadge(entry: DemoSlotEntry, theme: PluginTheme) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(theme.colorPrimary.copy(alpha = 0.12f))
            .semantics { contentDescription = "Demo ${entry.slot.name}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "D",
            style = theme.textCaption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
            color = theme.colorPrimary,
        )
    }
}

@Composable
private fun DemoBlock(entry: DemoSlotEntry, theme: PluginTheme, spec: JsonObject) {
    val provider = spec["provider"]?.jsonPrimitive?.content ?: ""
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = theme.shapeCard,
        colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
    ) {
        Column(
            modifier = Modifier.padding(theme.spacingUnit * 1.5f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Demo · ${entry.title}", style = theme.textBody, color = theme.colorOnSurface)
            Text(
                if (provider.isNotBlank()) "${entry.detail} · provider=$provider" else entry.detail,
                style = theme.textCaption,
                color = theme.colorOnSurfaceVariant,
            )
        }
    }
}
