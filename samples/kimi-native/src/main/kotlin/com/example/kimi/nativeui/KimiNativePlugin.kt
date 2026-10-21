package com.example.kimi.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.deepseek.dsh.plugin.nativesdk.contract.ComponentRenderer
import com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin
import com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities
import com.deepseek.dsh.plugin.nativesdk.contract.PluginDescriptor
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Kimi OAuth 插件的原生伴侣（自证样例）。
 *
 * 逻辑层（OAuth 流程、token 刷新、用量查询）全部在 Host 侧 JS 里继续跑；
 * 这个 APK 只画「登录卡片 / 用量胶囊」，把点击经 dispatchEvent 回传给 JS。
 */
class KimiNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "changfenhuang.kimi-oauth",
        name = "Kimi 登录",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,
            PluginCapabilities.STORAGE,
            PluginCapabilities.NOTIFY,
        ),
    )

    override fun createRenderer(): ComponentRenderer = KimiRenderer()

    init {
        // 在输入框顶栏（composer.dock）挂一个用量胶囊。
        SlotRegistry.register(KimiDockContribution())
    }
}

/** 落点渲染器：整页/卡片状态。 */
class KimiRenderer : ComponentRenderer {
    override val supportedTypes = setOf("kimi.account-card", "kimi.usage-meter")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        val status = spec.str("status")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Column(modifier = Modifier.padding(theme.spacingUnit * 2)) {
                when (status) {
                    "active" -> {
                        Text("Kimi 已连接", style = theme.textTitle, color = theme.colorOnSurface)
                        Text(
                            "剩余额度：${spec.str("quotaRemaining")}",
                            style = theme.textBody,
                            color = theme.colorOnSurfaceVariant,
                        )
                        KimiButton(theme, "刷新用量") {
                            host.dispatchEvent("changfenhuang.kimi-oauth#section", "refresh", emptyObj())
                        }
                    }
                    "authorizing" -> {
                        Text("等待授权…", style = theme.textTitle, color = theme.colorOnSurface)
                        KimiButton(theme, "打开授权页") {
                            host.openUrl(spec.str("authorizeUrl"))
                        }
                    }
                    else -> {
                        Text("连接 Kimi", style = theme.textTitle, color = theme.colorOnSurface)
                        KimiButton(theme, "登录") {
                            host.dispatchEvent("changfenhuang.kimi-oauth#section", "start-oauth", emptyObj())
                        }
                    }
                }
            }
        }
    }
}

/** composer.dock 落点：输入框上方的用量胶囊。 */
class KimiDockContribution : SlotContribution() {
    override val slot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes = setOf("kimi.usage-pill")

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val quota = spec.str("quotaRemaining")
        if (quota.isBlank()) return  // 未登录/无数据时不占空间
        Card(
            shape = theme.shapeChip,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurfaceVariant),
        ) {
            Text(
                text = "Kimi 额度 $quota",
                style = theme.textCaption,
                color = theme.colorOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = theme.spacingUnit, vertical = theme.spacingUnit / 2),
            )
        }
    }
}

@Composable
private fun KimiButton(theme: PluginTheme, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.colorPrimary,
            contentColor = theme.colorOnPrimary,
        ),
        modifier = Modifier.padding(top = theme.spacingUnit),
    ) {
        Text(label, style = theme.textBody.copy(fontWeight = FontWeight.SemiBold))
    }
}

// --- 内部 JSON 读取小工具 ---
private fun JsonObject.str(vararg keys: String): String =
    keys.firstNotNullOfOrNull { k ->
        this[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
    } ?: ""

private fun emptyObj(): JsonObject = JsonObject(emptyMap())
