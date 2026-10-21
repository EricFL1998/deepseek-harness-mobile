package com.dsh.mobile.dsh_plugin_demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.pages.PageContent
import com.deepseek.dsh.plugin.nativesdk.pages.PageEntry
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject

/**
 * Demo 设置页：PageContent 的完整例子。
 *
 * 页面本身不依赖 Host 数据；它列出 Demo 插件注册的全部落点，方便对照
 * “每个 NativeSlot 在 App 的哪里出现”。
 */
class DemoSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "demo",
        title = "Demo 插件",
        subtitle = "原生 UI 插入方式总览（虚构示例）",
        placement = PageEntry.Placement.SETTINGS,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        DemoSettingsContent(theme)
    }
}

@Composable
private fun DemoSettingsContent(theme: PluginTheme) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(theme.spacingUnit * 2),
        verticalArrangement = Arrangement.spacedBy(theme.spacingUnit),
    ) {
        Text("Demo 插件", style = theme.textTitle, color = theme.colorOnSurface)
        Text(
            "这是一个纯虚构的示例，不包含真实登录、网络或账号逻辑。它覆盖 PageContent、" +
                "全部 NativeSlot 的 SlotContribution，以及贴锚点的 PluginPopover。",
            style = theme.textBody,
            color = theme.colorOnSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Column(
                modifier = Modifier.padding(theme.spacingUnit * 2),
                verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2),
            ) {
                Text(
                    "已注入 ${DemoSlots.entries.count { it.hostReady }} / ${DemoSlots.entries.size} 个原生落点",
                    style = theme.textBody,
                    color = theme.colorOnSurface,
                )
                Text(
                    "其中 4 个（标题角、输入浮层、顶层 overlay、Web 岛）宿主位置仍在修，Demo 页只展示说明，不向真实界面注入。",
                    style = theme.textCaption,
                    color = theme.colorOnSurfaceVariant,
                )
            }
        }

        DemoSlots.entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = theme.textBody, color = theme.colorOnSurface)
                    Text(entry.detail, style = theme.textCaption, color = theme.colorOnSurfaceVariant)
                }
                Text(
                    entry.slot.name,
                    style = theme.textCaption,
                    color = if (entry.hostReady) theme.colorOutline else theme.colorError,
                )
                if (!entry.hostReady) {
                    Text(
                        "宿主待修",
                        style = theme.textCaption,
                        color = theme.colorError,
                    )
                }
            }
            HorizontalDivider()
        }

        Text(
            "真实插件应把数据和动作放在 Host，通过 PluginRpcHost / PluginModelHost 调用；" +
                "Demo 只演示渲染与落点。",
            style = theme.textCaption,
            color = theme.colorOnSurfaceVariant,
        )
    }
}
