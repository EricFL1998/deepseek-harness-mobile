/**
 * Host-side action registry: what a native control press runs in a plugin.
 *
 * A statically transpiled tree cannot carry the plugin's `onClick` closure: the
 * closure lives in the plugin's client half, which never runs on a phone Host. The
 * client therefore reports **which** control was pressed, and the plugin's Host half
 * decides what that means.
 *
 * A plugin opts in from its Host half:
 *
 * ```ts
 * export const inject = ['mobileUi']
 * export function apply(ctx) {
 *   const ui = ctx.get('mobileUi')
 *   ui.registerAction('my-plugin#settings', 'add-account', async payload => {
 *     await ctx.accounts.add(payload.provider)
 *   })
 * }
 * ```
 *
 * The bridge calls a registered handler for `client-slots/dispatch` before falling
 * back to the generic `ui/<componentId>/<event>` bus event, so a plugin that
 * registers an action runs its own code and one that does not still sees the event.
 */
export declare class MobileUiService {
    private readonly actions;
    /**
     * Register one action.
     *
     * @param componentId - the `<package>#<id>` identity the plugin registered, or a
     *   bare id when the plugin owns a single component.
     * @param actionId - the id the native control reports (`data-action`, `id`, or the
     *   control's class name).
     * @param handler - runs when that control is pressed.
     * @returns a disposer that removes the registration.
     */
    registerAction(componentId: string, actionId: string, handler: MobileUiActionHandler): () => void;
    /**
     * Run the action a native control reported, when one is registered.
     *
     * @param componentId - component the control belongs to.
     * @param actionId - id the control reports; may be empty.
     * @param payload - full payload from the client.
     * @returns true when a handler ran, false when nothing is registered.
     */
    runAction(componentId: string, actionId: string, payload: Readonly<Record<string, unknown>>): Promise<boolean>;
    /** How many actions are registered, for diagnostics. */
    get size(): number;
}
/** One registered action handler. */
export interface MobileUiActionHandler {
    /**
     * Run the action.
     *
     * @param payload - what the native control reported: `{ event, actionId, …props }`.
     */
    (payload: Readonly<Record<string, unknown>>): void | Promise<void>;
}
