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
        "conversation.input.left" to NativeSlot.COMPOSER_LEFT,
        "conversation.input.right" to NativeSlot.COMPOSER_RIGHT,
        "conversation.input.model" to NativeSlot.COMPOSER_MODEL,
        "conversation.input.permission" to NativeSlot.COMPOSER_PERMISSION,
        "conversation.input.plan" to NativeSlot.COMPOSER_PLAN,
        "conversation.input.attachments" to NativeSlot.COMPOSER_ATTACHMENTS,
        "conversation.composer.bar" to NativeSlot.COMPOSER_BAR,
        "conversation.view" to NativeSlot.CONVERSATION_VIEW,
        "conversation.session.header.corner" to NativeSlot.SESSION_HEADER_CORNER,

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
        "settings.models.provider-card" to NativeSlot.SETTINGS_PROVIDER_CARD,
        "plugins.tab" to NativeSlot.SETTINGS_SHEET,
    )

    /**
     * `conversation.composer.dock` 的内部条目。官方同一个 slot 下按 id 注册
     * activity / usage 两个条目（上下文计量是 dock 的兄弟节点），因此这里也把
     * 它们拆成独立落点，让插件能只替换其中一个读数。
     */
    val composerDockEntries: List<NativeSlot> = listOf(
        NativeSlot.COMPOSER_DOCK_ACTIVITY,
        NativeSlot.COMPOSER_DOCK_USAGE,
        NativeSlot.COMPOSER_DOCK_CONTEXT,
        NativeSlot.COMPOSER_DOCK,
    )

    /**
     * 官方存在、但 App 还没有原生宿主容器的 slot。
     *
     * 目前为空：所有官方 slot 都已有原生落点与宿主容器。保留这个集合是为了未来
     * 新增官方 slot 时能显式标记“暂不支持”，而不是让它静默丢失。
     */
    val explicitlyUnsupported: Set<String> = emptySet()

    /** 已映射 + 明确不支持的并集；等于官方 slot 全集。 */
    val allOfficialSlots: Set<String>
        get() = officialToNativeSlot.keys + explicitlyUnsupported

    /** 查一个官方 slot 的原生落点；未接入返回 null。 */
    fun destinationFor(officialSlot: String): NativeSlot? = officialToNativeSlot[officialSlot]
}
