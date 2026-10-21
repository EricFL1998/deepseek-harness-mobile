package com.dsh.mobile.dsh_plugin_demo

import com.deepseek.dsh.plugin.nativesdk.contract.ComponentRenderer
import com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin
import com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities
import com.deepseek.dsh.plugin.nativesdk.contract.PluginDescriptor
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.pages.PageRegistry
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject

/**
 * 纯虚构的 Demo 插件：不是真实业务插件，只演示 SDK 的每一种 UI 插入方式。
 *
 * 覆盖：
 * - PageContent（设置页整页）
 * - 全部 NativeSlot 的 SlotContribution（dock、输入栏、消息动作、工具卡片、
 *   provider 卡片、overlay、侧栏、chat stream、web island 等）
 * - PluginPopover（贴锚点 + App 菜单材质）
 * - 主题 token / 原生页面内状态
 *
 * 所有数据都是本地演示数据，不访问网络、不读写账号。
 */
class DemoNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "demo.native-ui",
        name = "Demo 插件",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,
            PluginCapabilities.STORAGE,
            PluginCapabilities.PAGE_ENTRY,
        ),
    )

    override fun createRenderer(): ComponentRenderer = DemoRenderer()

    init {
        PageRegistry.register(DemoSettingsPage())
        // 只注入宿主位置已经正确的落点；宿主待修的落点仍在 Demo 页列出，
        // 但不向真实界面注入，避免用错误布局硬顶。
        DemoSlots.entries
            .filter { it.hostReady && it.slot != NativeSlot.COMPOSER_DOCK }
            .forEach { SlotRegistry.register(DemoSlotContribution(it)) }
        // dock 单独展示 popover + App 菜单材质。
        SlotRegistry.register(DemoDockContribution())
    }
}

/** 一个落点的演示条目。 */
data class DemoSlotEntry(
    val slot: NativeSlot,
    val title: String,
    val detail: String,
    /** App 是否已有正确的宿主位置；false 时 Demo 页只展示说明，不注入真实界面。 */
    val hostReady: Boolean = true,
)

/** 全部 NativeSlot 的演示清单；设置页也会列出来。 */
object DemoSlots {
    val entries: List<DemoSlotEntry> = listOf(
        DemoSlotEntry(NativeSlot.SETTINGS_SHEET, "设置页", "settings.section / plugins.tab"),
        DemoSlotEntry(NativeSlot.SETTINGS_PROVIDER_CARD, "Provider 卡片", "settings.models.provider-card"),
        DemoSlotEntry(NativeSlot.SIDEBAR_PANEL, "侧栏面板", "sidebar.*"),
        DemoSlotEntry(NativeSlot.COMPOSER_DOCK, "输入栏 dock", "额度/状态胶囊"),
        DemoSlotEntry(NativeSlot.COMPOSER_DOCK_ACTIVITY, "dock activity", "tok/s 条目"),
        DemoSlotEntry(NativeSlot.COMPOSER_DOCK_USAGE, "dock usage", "缓存命中条目"),
        DemoSlotEntry(NativeSlot.COMPOSER_DOCK_CONTEXT, "dock context", "上下文计量"),
        DemoSlotEntry(NativeSlot.COMPOSER_LEFT, "输入栏左", "conversation.input.left"),
        DemoSlotEntry(NativeSlot.COMPOSER_RIGHT, "输入栏右", "conversation.input.right"),
        DemoSlotEntry(NativeSlot.COMPOSER_MODEL, "模型选择", "conversation.input.model"),
        DemoSlotEntry(NativeSlot.COMPOSER_PERMISSION, "权限选择", "conversation.input.permission"),
        DemoSlotEntry(NativeSlot.COMPOSER_PLAN, "Plan 模式", "conversation.input.plan"),
        DemoSlotEntry(NativeSlot.COMPOSER_ATTACHMENTS, "附件入口", "conversation.input.attachments"),
        DemoSlotEntry(NativeSlot.COMPOSER_BAR, "整个 composer", "conversation.composer.bar"),
        DemoSlotEntry(NativeSlot.CONVERSATION_VIEW, "会话视图", "conversation.view"),
        DemoSlotEntry(NativeSlot.SESSION_HEADER_CORNER, "会话标题角", "session.header.corner", hostReady = false),
        DemoSlotEntry(NativeSlot.COMPOSER_OVERLAY, "输入框浮层", "conversation.input.overlay", hostReady = false),
        DemoSlotEntry(NativeSlot.PLUGIN_OVERLAY, "插件浮层", "shell.overlay", hostReady = false),
        DemoSlotEntry(NativeSlot.MESSAGE_ACTION, "消息动作", "assistant-actions"),
        DemoSlotEntry(NativeSlot.TOOL_VIEW_SHEET, "工具详情", "tool.call.toolview"),
        DemoSlotEntry(NativeSlot.CHAT_STREAM, "聊天流", "chat.flow / chat.node"),
        DemoSlotEntry(NativeSlot.WEB_ISLAND_FULL, "Web 岛", "完整 Web 回退", hostReady = false),
    )
}

/** 通用 ComponentRenderer：证明 demo 也能被 Host 当作 card 渲染。 */
class DemoRenderer : ComponentRenderer {
    override val supportedTypes = setOf("demo.card")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Column(
                modifier = Modifier.padding(theme.spacingUnit * 2),
                verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2),
            ) {
                Text("Demo 组件渲染", style = theme.textTitle, color = theme.colorOnSurface)
                Text(
                    "这是 ComponentRenderer 的演示内容，不包含真实业务。",
                    style = theme.textBody,
                    color = theme.colorOnSurfaceVariant,
                )
            }
        }
    }
}
