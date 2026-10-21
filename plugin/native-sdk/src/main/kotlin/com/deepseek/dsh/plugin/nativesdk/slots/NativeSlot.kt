package com.deepseek.dsh.plugin.nativesdk.slots

import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonObject

/**
 * 原生落点：一个官方槽位在手机端的承载位置。
 *
 * 与 App `plugin/surface` 模块的 `PlacementDestination` 一一对应——官方插件能改的
 * 30 个槽位（conversation.* / tool.call.* / sidebar.* / shell.overlay /
 * settings.* / plugins.tab）全部映射到这些落点，**没有对应位置的由 App 补容器**，
 * 保证每个官方槽位在原生端都有改点。
 *
 * 插件按落点声明「我在哪里渲染什么」，不需要关心具体槽位名到手机位置的映射。
 *
 * 当前的「官方 slot → 落点 → 宿主位置」覆盖表见 docs/native-ui-slot-discovery-postmortem.md
 * 与 SDK 交付包的 slot-mapping.md；新增落点必须先补宿主容器与映射，避免静默丢失。
 */
enum class NativeSlot {
    /** 设置页 / 插件页（Sheet）。承载 settings.section、settings.general.item、plugins.tab。 */
    SETTINGS_SHEET,

    /**
     * 设置 → 模型页每个 provider 卡片下方的扩展区
     * （官方 keyed slot `settings.models.provider-card`）。
     */
    SETTINGS_PROVIDER_CARD,

    /** 侧栏面板。承载 sidebar.workspaces、sidebar.panellist、sidebar.right.pane.* 等。 */
    SIDEBAR_PANEL,

    /** 输入框顶栏 / 底部药丸栏。承载 conversation.composer.dock、conversation.input.dock。 */
    COMPOSER_DOCK,

    /**
     * Dock 里的 activity 读数（tok/s）：官方把 dock 拆成按 id 注册的条目
     * （activity / usage），这里同样给每一项一个可插拔落点，插件可 ENHANCE/OVERRIDE
     * 单个读数而不影响其它项。对应官方 `conversation.composer.dock` 的 `activity` 条目。
     */
    COMPOSER_DOCK_ACTIVITY,

    /**
     * Dock 里的 usage 读数（缓存命中率）：对应官方 `conversation.composer.dock`
     * 的 `usage` 条目。
     */
    COMPOSER_DOCK_USAGE,

    /** Dock 里的上下文占用计量（环形 + 百分比）。 */
    COMPOSER_DOCK_CONTEXT,

    /** 输入栏左侧扩展位（conversation.input.left）。 */
    COMPOSER_LEFT,

    /** 输入栏右侧扩展位（conversation.input.right）。 */
    COMPOSER_RIGHT,

    /** 模型选择控件（conversation.input.model）。 */
    COMPOSER_MODEL,

    /** 权限预设控件（conversation.input.permission）。 */
    COMPOSER_PERMISSION,

    /** Plan 模式控件（conversation.input.plan）。 */
    COMPOSER_PLAN,

    /** 附件入口（conversation.input.attachments）。 */
    COMPOSER_ATTACHMENTS,

    /** 整条 composer 栏（conversation.composer.bar）。 */
    COMPOSER_BAR,

    /** 会话视图整体（conversation.view）。 */
    CONVERSATION_VIEW,

    /** 会话标题栏角落（conversation.session.header.corner）。 */
    SESSION_HEADER_CORNER,

    /** 输入框悬浮气泡。承载 conversation.input.overlay。 */
    COMPOSER_OVERLAY,

    /** 顶层插件浮层（悬浮/收起/气泡）。承载 shell.overlay。 */
    PLUGIN_OVERLAY,

    /** 消息长按动作 / 助手消息动作。承载 conversation.chat.assistant-actions、turnTail。 */
    MESSAGE_ACTION,

    /** 工具卡片专属弹窗。承载 tool.call.toolview、tool.call.images。 */
    TOOL_VIEW_SHEET,

    /** 聊天消息流本体（chat.flow / chat.node / user.text.node / reasoning.body）。 */
    CHAT_STREAM,

    /** 整岛回退：实在没有原生位置的，嵌入插件的完整 Web 界面。 */
    WEB_ISLAND_FULL,
}

/**
 * 一个落点贡献的渲染模式：插件是「增强」官方 UI 还是「覆盖」它。
 *
 * - [ENHANCE]：官方内容照常渲染，插件的内容加在旁边（默认，安全）。
 * - [OVERRIDE]：插件接管这个落点，官方默认内容隐藏；插件渲染崩了或被卸载时，
 *   落点回退到官方默认——App 永远可用。
 */
enum class RenderMode { ENHANCE, OVERRIDE }

/**
 * 一个落点的渲染贡献。
 *
 * 官方 UI 本身也是一个贡献（[isOfficial] = true）：它是该落点的默认实现，
 * 永远在场。第三方插件的贡献按 [mode] 决定是增强还是覆盖它。
 *
 * @property slot 目标落点。
 * @property supportedTypes 该贡献能渲染的 spec 类型；命中才走原生，否则降级。
 * @property order 同落点多个贡献的排序（小的在前）。
 * @property mode 增强还是覆盖。
 * @property isOfficial 是否官方默认实现（App 内置）。
 */
abstract class SlotContribution {
    abstract val slot: NativeSlot
    abstract val supportedTypes: Set<String>
    open val order: Int = 0
    open val mode: RenderMode = RenderMode.ENHANCE
    open val isOfficial: Boolean = false

    /** 渲染这段内容。 */
    @Composable
    abstract fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi)
}

/**
 * 落点注册表与覆盖仲裁。
 *
 * 一个落点的渲染规则：
 * 1. 官方默认贡献永远在场（[isOfficial] = true）；
 * 2. 第三方插件的 [RenderMode.ENHANCE] 贡献加在官方内容旁边；
 * 3. 第一个 [RenderMode.OVERRIDE] 贡献接管落点，官方与其余内容隐藏；该贡献
 *    渲染抛错或被卸载时，落点回退到官方默认——App 永远可用。
 *
 * App 在每个落点的宿主容器里查询 [resolve]，把仲裁后的贡献逐个渲染。
 */
object SlotRegistry {
    private val contributions = mutableListOf<SlotContribution>()

    /** 注册表版本：插件 APK 异步装载完成后，已组合的落点容器需要重组一次。 */
    private var revision by mutableStateOf(0)

    /** 注册一个落点贡献。官方 UI 与插件都在初始化时调用。 */
    fun register(contribution: SlotContribution) {
        contributions.removeAll {
            it.slot == contribution.slot && it.isOfficial == contribution.isOfficial &&
                it.supportedTypes == contribution.supportedTypes
        }
        contributions.add(contribution)
        contributions.sortBy { it.order }
        revision++
    }

    /**
     * 仲裁一个落点要渲染的贡献。
     *
     * @return 若有插件 OVERRIDE 接管，只返回它（官方隐藏）；否则返回官方默认 +
     *   全部 ENHANCE 贡献（按 order 排序）。
     */
    fun resolve(slot: NativeSlot): List<SlotContribution> {
        @Suppress("UNUSED_VARIABLE")
        val observed = revision
        val all = contributions.filter { it.slot == slot }
        if (all.isEmpty()) return emptyList()
        val override = all.firstOrNull { it.mode == RenderMode.OVERRIDE && !it.isOfficial }
        if (override != null) return listOf(override)
        return all
    }

    /** 取一个落点的全部贡献（不仲裁；诊断/调试用）。 */
    fun forSlot(slot: NativeSlot): List<SlotContribution> =
        contributions.filter { it.slot == slot }

    /** 卸载一个插件的全部贡献（插件 APK 卸载时调用）；官方默认回退可见。 */
    fun unregisterAll(predicate: (SlotContribution) -> Boolean) {
        contributions.removeAll(predicate)
        revision++
    }
}
