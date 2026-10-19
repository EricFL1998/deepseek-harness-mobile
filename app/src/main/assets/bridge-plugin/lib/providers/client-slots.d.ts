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
    /** Locale tags to read the official roster's text with, preferred first. */
    readonly localeTags?: readonly string[];
}
/**
 * Build the `client-slots` provider.
 * @param options - service accessor, entry selection, and budgets.
 * @returns the provider to register.
 */
export declare function createClientSlotsProvider(options: ClientSlotsOptions): Provider;
/** Re-exported so the plugin assembly can share one budget default. */
export { DEFAULT_RENDER_BUDGET };
