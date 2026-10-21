package com.example.subscription.nativeui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepseek.dsh.plugin.nativesdk.contract.ComponentRenderer
import com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin
import com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities
import com.deepseek.dsh.plugin.nativesdk.contract.PluginDescriptor
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.pages.PageRegistry
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 订阅插件的原生伴侣（自证样例，对应 dsh-plugin-subscription-oauth）。
 *
 * 逻辑层（订阅查询、OAuth、用量）在 Host 侧 JS；这个 APK 画订阅状态卡片 +
 * 设置页顶部的用量落点。
 */
class SubscriptionNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "changfenhuang.subscription",
        name = "订阅",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,
            PluginCapabilities.STORAGE,
            PluginCapabilities.PAGE_ENTRY,
        ),
    )

    override fun createRenderer(): ComponentRenderer = SubscriptionRenderer()

    init {
        // 设置页顶部落点：订阅用量概览。
        SlotRegistry.register(SubscriptionSettingsContribution())
        // 输入框 dock：订阅额度胶囊。
        SlotRegistry.register(SubscriptionDockContribution())
        // 设置 → 模型页 provider 卡片扩展（keyed settings.models.provider-card）。
        SlotRegistry.register(ProviderCardExtrasContribution())
        // 模型选择器左侧的快切模式按钮（conversation.input.right）。
        SlotRegistry.register(FastModeContribution())
        PageRegistry.register(SubscriptionSettingsPage())
    }
}

/** 落点渲染器：订阅状态整卡。 */
class SubscriptionRenderer : ComponentRenderer {
    override val supportedTypes = setOf("subscription.status-card")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Column(modifier = Modifier.padding(theme.spacingUnit * 2)) {
                Text("订阅状态", style = theme.textTitle, color = theme.colorOnSurface)
                Text(
                    "套餐：${spec.str("plan").ifBlank { "免费版" }}",
                    style = theme.textBody,
                    color = theme.colorOnSurfaceVariant,
                )
                Text(
                    "本月用量：${spec.str("usage").ifBlank { "—" }}",
                    style = theme.textBody,
                    color = theme.colorOnSurfaceVariant,
                )
            }
        }
    }
}

/** 设置页顶部落点：订阅用量概览卡。 */
class SubscriptionSettingsContribution : SlotContribution() {
    override val slot = NativeSlot.SETTINGS_SHEET
    override val supportedTypes = setOf("subscription.overview")

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val plan = spec.str("plan")
        if (plan.isBlank()) return
        Card(
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(theme.spacingUnit * 2)) {
                Text("订阅", style = theme.textBody, color = theme.colorOnSurface)
                Text(plan, style = theme.textCaption, color = theme.colorOnSurfaceVariant)
            }
        }
    }
}

private fun JsonObject.str(vararg keys: String): String =
    keys.firstNotNullOfOrNull { k ->
        this[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
    } ?: ""
