/**
 * `package-inventory`: the Host's own plugin-management records.
 *
 * This provider reuses Host services rather than guessing from the filesystem,
 * so third-party bundle icons and titles stay fully backend-driven.
 */
import { type Provider } from '../protocol/provider.ts';
/** One row a bundle's patch declares, as the Host reports it. */
export interface HostBundleRow {
    readonly rowId: string;
    readonly moduleName: string;
    readonly meta?: unknown;
    /**
     * The Loader entry this row resolves to, when the Host reports one.
     *
     * Absent means the row is not live (typically because its bundle is off), so it
     * cannot be addressed through the profile patch either. Present means the entry
     * exists even while it is disabled, which is what makes a row toggle possible.
     */
    readonly entryId?: string;
    /** Whether this row is enabled, when the Host reports it. */
    readonly enabled?: boolean;
}
/** One bundle record, as the Host's `pluginManager.listBundles()` returns it. */
export interface HostBundleInfo {
    readonly name: string;
    readonly official: boolean;
    readonly optional: boolean;
    readonly removable: boolean;
    readonly enabled: boolean;
    readonly installed: boolean;
    readonly availability: string;
    readonly version?: string;
    readonly source?: string;
    readonly meta?: unknown;
    readonly description?: string;
    readonly rows?: readonly HostBundleRow[];
    readonly error?: {
        readonly code?: string;
    };
}
/** One Loader inventory entry, as `pluginInventory.list()` returns it. */
export interface HostInventoryEntry {
    readonly entryId: string;
    readonly moduleName: string;
    readonly enabled: boolean;
    readonly fiberPhase: string | null;
    readonly meta?: unknown;
}
/** The slice of `pluginManager` this provider reads. */
export interface PluginManagerFace {
    listBundles(): Promise<readonly HostBundleInfo[]>;
}
/** The slice of `pluginInventory` this provider reads. */
export interface PluginInventoryFace {
    list(): Promise<{
        readonly entries: readonly HostInventoryEntry[];
    }>;
}
/** `package-inventory` configuration. */
export interface PackageInventoryOptions {
    /** Resolve the manager service, or undefined when this profile has none. */
    readonly pluginManager: () => PluginManagerFace | undefined;
    /** Resolve the inventory service, or undefined when absent. */
    readonly pluginInventory: () => PluginInventoryFace | undefined;
    /** Locale tags used to resolve locale-mapped titles and descriptions. */
    readonly localeTags?: readonly string[];
}
/**
 * Build the `package-inventory` provider.
 * @param options - service accessors.
 * @returns the provider to register.
 */
export declare function createPackageInventoryProvider(options: PackageInventoryOptions): Provider;
