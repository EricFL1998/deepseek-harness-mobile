/**
 * `client-slots`: the generic compatibility primitive.
 *
 * It evaluates official client bundles in the isolated VM, captures their slot
 * registrations as data, and renders a captured component under the React stub
 * so a native client can obtain official artwork without a WebView.
 *
 * Execution is deterministic and side-effect free: the VM provides no DOM, no
 * timers, and no network, and one evaluation is cached per bundle revision so a
 * repeated query re-reads nothing.
 */
import { type Provider } from '../protocol/provider.ts';
import type { SlotRegistration } from '../protocol/types.ts';
import { DEFAULT_RENDER_BUDGET, type RenderBudget } from '../slots/render-tree.ts';
import type { ClientModulesFace } from './client-assets.ts';
/** `client-slots` configuration. */
export interface ClientSlotsOptions {
    /** Resolve the client-modules service, or undefined when absent. */
    readonly clientModules: () => ClientModulesFace | undefined;
    /** Entry ids to evaluate, in order. Empty means every composed entry. */
    readonly entryIds: readonly string[];
    /** Ceilings applied to one `render-tree` call. */
    readonly renderBudget: RenderBudget;
    /** Largest `size` an `icon-svg` call may request. */
    readonly maxIconSize: number;
    /** Coordinate space the client's icons are authored in. */
    readonly iconViewBox: string;
    /** Absolute path of the Host installation's `node_modules`, or undefined when unknown. */
    readonly installModules?: () => string | undefined;
    /** Directories whose packages may ship a `lib/client.js` client half. */
    readonly clientRoots?: () => readonly string[];
    /**
     * Whether a bundle may be *executed* in the VM.
     *
     * Reading a bundle's text is always safe; running it is not, because a Client/Host
     * package's `apply` performs real mount work (`ctx.slots.provideRoot`, transports) and
     * throwing there has taken the Host down. Execution therefore stays opt-in and never
     * happens behind an icon or format request.
     */
    readonly executeBundles?: boolean;
    /** How many client bundles one request may evaluate; discovery beyond it is skipped. */
    readonly maxClientBundles?: number;
    /** Locale tags to read the official roster's text with, preferred first. */
    readonly localeTags?: readonly string[];
    /**
     * Push one transient message toward the phone. `dispatch` uses it to confirm
     * an interaction reached the Host, and to surface the requested refresh so
     * the client re-pulls the component tree.
     */
    readonly notify?: (kind: 'toast' | 'client/refresh', text: string) => void;
    /**
     * Deliver one UI event to a plugin through the Host event bus
     * (`ctx.emit('ui/<componentId>/<event>', payload)`), so a plugin that
     * listens with `ctx.on` learns about the interaction.
     */
    readonly emit?: (event: string, payload: Readonly<Record<string, unknown>>) => void;
    /**
     * Run the action a native control reported, when the plugin registered one.
     *
     * A transpiled tree carries no onClick closure: the closure lives in the plugin's
     * client half. The plugin's Host half registers actions through the mobileUi`n   * service, and dispatch calls them here before the generic bus event.
     */
    readonly runAction?: (componentId: string, actionId: string, payload: Readonly<Record<string, unknown>>) => Promise<boolean>;
}
/**
 * Build the `client-slots` provider.
 * @param options - service accessor, entry selection, and budgets.
 * @returns the provider to register.
 */
export declare function createClientSlotsProvider(options: ClientSlotsOptions): Provider;
/** Re-exported so the plugin assembly can share one budget default. */
export { DEFAULT_RENDER_BUDGET };
/**
 * Remove the duplicate registrations one bundle contributes through more than one root.
 *
 * A phone Host locates its installation's `node_modules` through several path forms
 * (a `require.resolve` answer, the running entry point, and the profile's own
 * `node_modules` symlink). Walking each root re-discovers the same client halves, so
 * the same `settings.section` contribution arrived three times and the Settings page
 * listed it three times. Identity is the contribution itself, not the path it was
 * found through:
 *
 * ```
 * `${slot}|${id ?? key ?? ''}|${package}`
 * ```
 *
 * The first occurrence wins, which is discovery order: roots are ordered install-first,
 * so the authoritative copy supplies the row (and its localized label).
 *
 * @param registrations rows in discovery order.
 * @returns one row per contribution identity, in the original order.
 */
export declare function dedupeRegistrations(registrations: readonly SlotRegistration[]): SlotRegistration[];
