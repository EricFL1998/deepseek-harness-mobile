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
/** One native area a slot can land in. */
export interface SlotPlacement {
    /** Stable id the client switches on. */
    readonly placement: string;
    /** Human label for a placement that has no bespoke UI yet. */
    readonly label: string;
}
/** Native areas, in the order a client should present them. */
export declare const PLACEMENTS: {
    /** A plugin's own Settings page. */
    readonly settings: {
        readonly placement: "settings.page";
        readonly label: "设置";
    };
    /** An entry in the Plugins list. */
    readonly pluginsItem: {
        readonly placement: "plugins.item";
        readonly label: "插件列表";
    };
    /** Configuration of one bundle row. */
    readonly pluginsRow: {
        readonly placement: "plugins.row.config";
        readonly label: "插件配置";
    };
    /** Sidebar / drawer surface: navigation and panels. */
    readonly sidebar: {
        readonly placement: "drawer";
        readonly label: "侧边栏";
    };
    /** Anything inside the conversation: header, flow, composer strip. */
    readonly conversation: {
        readonly placement: "conversation";
        readonly label: "对话";
    };
    /** A tool call's card. */
    readonly toolCard: {
        readonly placement: "tool.card";
        readonly label: "工具卡片";
    };
    /** Transient notice. */
    readonly toast: {
        readonly placement: "toast";
        readonly label: "提示";
    };
    /** Recognised as a surface, but with no native area yet. */
    readonly other: {
        readonly placement: "other";
        readonly label: "其他位置";
    };
};
/**
 * The native area a slot belongs to.
 * @param slot slot name as the Host declares it.
 * @returns the placement; unknown slots land in `other` so they stay visible.
 */
export declare function placementOf(slot: string): SlotPlacement;
/** Every known slot name, for capability reporting and tests. */
export declare function knownSlots(): readonly string[];
