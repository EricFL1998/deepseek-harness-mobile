/**
 * Where each of the Host's slots belongs in a native client.
 *
 * The browser vocabulary names positions inside a web page; a phone app has its own
 * screens. Translating in one table — and in the bridge rather than in each client —
 * keeps every client agreeing on the same answer, and makes an unmapped slot visible
 * instead of silently dropped.
 *
 * Placement ids are deliberately coarse: they name a *native* area (the drawer, the
 * composer strip, a tool card), not a pixel slot. A client that implements an area
 * renders everything mapped to it and ignores the rest.
 */
/** Native areas, in the order a client should present them. */
export const PLACEMENTS = {
    /** A plugin's own Settings page. */
    settings: { placement: 'settings.page', label: '设置' },
    /** An entry in the Plugins list. */
    pluginsItem: { placement: 'plugins.item', label: '插件列表' },
    /** Configuration of one bundle row. */
    pluginsRow: { placement: 'plugins.row.config', label: '插件配置' },
    /** Sidebar / drawer surface: navigation and panels. */
    sidebar: { placement: 'drawer', label: '侧边栏' },
    /** Anything inside the conversation: header, flow, composer strip. */
    conversation: { placement: 'conversation', label: '对话' },
    /** A tool call's card. */
    toolCard: { placement: 'tool.card', label: '工具卡片' },
    /** Transient notice. */
    toast: { placement: 'toast', label: '提示' },
    /** Recognised as a surface, but with no native area yet. */
    other: { placement: 'other', label: '其他位置' },
};
/**
 * Exact slot names, mapped to their native area.
 *
 * Listed explicitly rather than prefix-matched, so a new Host slot shows up under
 * `other` (visible, but not silently placed in the wrong screen) until someone
 * decides where it belongs.
 */
const SLOT_PLACEMENTS = {
    'settings.section': PLACEMENTS.settings,
    'settings.plugins.tab': PLACEMENTS.settings,
    'settings.general.item': PLACEMENTS.settings,
    'settings.models.provider-card': PLACEMENTS.settings,
    'settings.models.footer': PLACEMENTS.settings,
    'settings.models.sign-in': PLACEMENTS.settings,
    'settings.onboarding': PLACEMENTS.settings,
    'settings.launcher': PLACEMENTS.settings,
    'settings.trigger': PLACEMENTS.settings,
    'settings.header': PLACEMENTS.settings,
    'settings.action': PLACEMENTS.settings,
    'settings.close': PLACEMENTS.settings,
    'plugins.item': PLACEMENTS.pluginsItem,
    'plugins.tab': PLACEMENTS.pluginsItem,
    'plugins.add.actions': PLACEMENTS.pluginsItem,
    'plugins.bundle.activation': PLACEMENTS.pluginsRow,
    'plugins.bundle.config': PLACEMENTS.pluginsRow,
    'dsh.plugin-manager.install-registry': PLACEMENTS.pluginsItem,
    'sidebar.panellist': PLACEMENTS.sidebar,
    'sidebar.brand.mark': PLACEMENTS.other,
    'sidebar.brand.name': PLACEMENTS.other,
    'sidebar.footer.action': PLACEMENTS.sidebar,
    'sidebar.session.row.hover': PLACEMENTS.other,
    'sidebar.session.row.leading': PLACEMENTS.other,
    'sidebar.settings': PLACEMENTS.other,
    'sidebar.toggle.badge': PLACEMENTS.other,
    'sidebar.workspaces': PLACEMENTS.other,
    'sidebar.workspaces.session.menu.item': PLACEMENTS.other,
    'sidebar.workspaces.session.row.action': PLACEMENTS.other,
    'sidebar.chat.conversation': PLACEMENTS.other,
    'sidebar.right.pane.tab': PLACEMENTS.other,
    'sidebar.right.pane.tab.title': PLACEMENTS.other,
    'sidebar.right.tab.document': PLACEMENTS.other,
    'sidebar.right.tab.document.action': PLACEMENTS.other,
    'sidebar.right.tab.document.actions': PLACEMENTS.other,
    'sidebar.right.tab.document.office.pdf': PLACEMENTS.other,
    'sidebar.right.tab.document.unpreviewable': PLACEMENTS.other,
    'sidebar.right.tab.files.actions': PLACEMENTS.other,
    'sidebar.right.tab.guide': PLACEMENTS.other,
    'sidebar.right.tab.guide.entry': PLACEMENTS.other,
    'sidebar.right.tab.menu.item': PLACEMENTS.other,
    'rightbar.session': PLACEMENTS.other,
    'shell.leading': PLACEMENTS.conversation,
    'shell.bottom': PLACEMENTS.conversation,
    'shell.overlay': PLACEMENTS.conversation,
    'shell.quota-notice': PLACEMENTS.conversation,
    'main.conversation': PLACEMENTS.conversation,
    'conversation.view': PLACEMENTS.conversation,
    'conversation.content': PLACEMENTS.conversation,
    'conversation.header': PLACEMENTS.conversation,
    'conversation.session': PLACEMENTS.conversation,
    'conversation.session.header': PLACEMENTS.conversation,
    'conversation.session.header.actions': PLACEMENTS.conversation,
    'conversation.session.header.corner': PLACEMENTS.conversation,
    'conversation.session.header.lineage': PLACEMENTS.conversation,
    'conversation.session.header.utilities': PLACEMENTS.conversation,
    'conversation.chat.flow': PLACEMENTS.conversation,
    'conversation.chat.node': PLACEMENTS.conversation,
    'conversation.chat.assistant-actions': PLACEMENTS.conversation,
    'conversation.chat.message.images': PLACEMENTS.conversation,
    'conversation.chat.reasoning.body': PLACEMENTS.conversation,
    'conversation.chat.reasoning.content': PLACEMENTS.conversation,
    'conversation.chat.user.content': PLACEMENTS.conversation,
    'conversation.chat.user.images': PLACEMENTS.conversation,
    'conversation.user.text': PLACEMENTS.conversation,
    'conversation.user.text.node': PLACEMENTS.conversation,
    'conversation.approval.detail': PLACEMENTS.conversation,
    'conversation.plan-review.actions': PLACEMENTS.conversation,
    'conversation.hero.workspace': PLACEMENTS.conversation,
    'conversation.composer': PLACEMENTS.conversation,
    'conversation.composer.bar': PLACEMENTS.conversation,
    'conversation.composer.dock': PLACEMENTS.conversation,
    'conversation.input.activity': PLACEMENTS.conversation,
    'conversation.input.attachments': PLACEMENTS.conversation,
    'conversation.input.dock': PLACEMENTS.conversation,
    'conversation.input.model': PLACEMENTS.conversation,
    'conversation.input.model.label': PLACEMENTS.conversation,
    'conversation.input.overlay': PLACEMENTS.conversation,
    'conversation.input.permission': PLACEMENTS.conversation,
    'conversation.input.plan': PLACEMENTS.conversation,
    'conversation.images.content': PLACEMENTS.conversation,
    'conversation.trajectory.images': PLACEMENTS.conversation,
    'conversation.uiparts': PLACEMENTS.conversation,
    'tool.call.toolview': PLACEMENTS.toolCard,
    'tool.call.images': PLACEMENTS.toolCard,
    'deliverables.file.actions': PLACEMENTS.toolCard,
    'deliverables.review.file.actions': PLACEMENTS.toolCard,
    'workspace.toast': PLACEMENTS.toast,
    'workspace.toast.content': PLACEMENTS.toast,
};
/**
 * The native area a slot belongs to.
 * @param slot slot name as the Host declares it.
 * @returns the placement; unknown slots land in `other` so they stay visible.
 */
export function placementOf(slot) {
    return SLOT_PLACEMENTS[slot] ?? PLACEMENTS.other;
}
/** Every known slot name, for capability reporting and tests. */
export function knownSlots() {
    return Object.keys(SLOT_PLACEMENTS);
}
