package com.deepseek.dsh.plugin.nativesdk.slots

/**
 * 官方客户端 slot → 原生落点的显式映射表。
 *
 * 这张表是「先扫全、再实现」的代码化结果：`DshSlotRegistry` 里的每一个官方
 * slot 必须出现在 [officialToNativeSlot] 或 [explicitlyUnsupported] 中，测试会
 * 在新增官方 slot 时失败，而不是等用户发现界面缺一块。
 *
 * 未接入的 slot 不是“忘了”，而是明确列在 [explicitlyUnsupported]：这些位置
 * 目前由官方内置控件承担，没有插件宿主容器；插件应继续使用 Web UI。
 */
object NativeSlotMapping {

    /** 有原生宿主容器的官方 slot。 */
    val officialToNativeSlot: Map<String, NativeSlot> = mapOf(
        // 输入区
        "conversation.composer.dock" to NativeSlot.COMPOSER_DOCK,
        "conversation.input.dock" to NativeSlot.COMPOSER_DOCK,
        "conversation.input.overlay" to NativeSlot.COMPOSER_OVERLAY,

        // 会话消息流与动作
        "conversation.chat.flow" to NativeSlot.CHAT_STREAM,
        "conversation.chat.node" to NativeSlot.CHAT_STREAM,
        "conversation.user.text.node" to NativeSlot.CHAT_STREAM,
        "conversation.chat.reasoning.body" to NativeSlot.CHAT_STREAM,
        "conversation.chat.turnTail" to NativeSlot.CHAT_STREAM,
        "conversation.chat.assistant-actions" to NativeSlot.MESSAGE_ACTION,

        // 工具卡片
        "tool.call.toolview" to NativeSlot.TOOL_VIEW_SHEET,
        "tool.call.images" to NativeSlot.TOOL_VIEW_SHEET,

        // 侧栏
        "sidebar.workspaces" to NativeSlot.SIDEBAR_PANEL,
        "sidebar.workspaces.session.menu.item" to NativeSlot.SIDEBAR_PANEL,
        "sidebar.workspaces.session.row.action" to NativeSlot.SIDEBAR_PANEL,
        "sidebar.panellist" to NativeSlot.SIDEBAR_PANEL,
        "sidebar.right.pane.tab" to NativeSlot.SIDEBAR_PANEL,
        "sidebar.right.pane.tab.title" to NativeSlot.SIDEBAR_PANEL,

        // 顶层与设置
        "shell.overlay" to NativeSlot.PLUGIN_OVERLAY,
        "settings.section" to NativeSlot.SETTINGS_SHEET,
        "settings.general.item" to NativeSlot.SETTINGS_SHEET,
        "plugins.tab" to NativeSlot.SETTINGS_SHEET,
    )

    /**
     * 官方存在、但 App 还没有原生宿主容器的 slot。
     *
     * 这些位置目前由输入栏内置控件/官方实现承担；插件贡献不会有落点可渲染。
     * 需要支持时：新增 NativeSlot、宿主容器、映射条目，并从本集合移除。
     */
    val explicitlyUnsupported: Set<String> = setOf(
        "conversation.input.left",
        "conversation.input.right",
        "conversation.input.model",
        "conversation.input.permission",
        "conversation.input.plan",
        "conversation.input.attachments",
        "conversation.composer.bar",
        "conversation.view",
        "conversation.session.header.corner",
    )

    /** 已映射 + 明确不支持的并集；等于官方 slot 全集。 */
    val allOfficialSlots: Set<String>
        get() = officialToNativeSlot.keys + explicitlyUnsupported

    /** 查一个官方 slot 的原生落点；未接入返回 null。 */
    fun destinationFor(officialSlot: String): NativeSlot? = officialToNativeSlot[officialSlot]
}
