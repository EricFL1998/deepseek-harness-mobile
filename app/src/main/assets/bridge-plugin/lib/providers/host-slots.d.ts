/**
 * `host-slots`: plugin contributions the Host itself can enumerate.
 *
 * A native client cannot run client bundles, so anything a plugin contributes to
 * the browser's slots is out of reach. Some surfaces, however, are declared on the
 * server side and are therefore visible here:
 *
 * - **A plugin's Settings page.** Any Loader row whose module declares a `Config`
 *   schema gets a settings namespace; the Host's settings service lists those
 *   namespaces and the row's `meta` supplies the title and description. That is
 *   exactly what the browser's Settings → Plugins list renders.
 *
 * Each reported surface names the native place it belongs in, so the client maps
 * data to a screen without a second table of its own.
 */
import type { Provider } from '../protocol/provider.ts';
/** The slice of the Host's `settings` service this provider reads. */
export interface SettingsFace {
    /** Every active namespace with a generated form, in registration order. */
    describe(options?: {
        redactSecrets?: boolean;
    }): readonly SettingsDescriptor[];
}
/** One settings descriptor, structurally the Host's. */
export interface SettingsDescriptor {
    /** Namespace, which is also the Loader row id that owns it. */
    readonly ns: string;
    /** Whether the Host generates the form itself. */
    readonly autoGenerate?: boolean;
}
/** One Loader row, as `pluginInventory.list()` returns it. */
export interface SlotInventoryEntry {
    readonly entryId: string;
    readonly moduleName: string;
    readonly enabled: boolean;
    readonly meta?: unknown;
}
/** The slice of `pluginInventory` this provider reads. */
export interface SlotInventoryFace {
    list(): Promise<{
        readonly entries: readonly SlotInventoryEntry[];
    }>;
}
/** One bundle row, as `pluginManager.listBundles()` reports it. */
export interface SlotBundleRow {
    readonly rowId: string;
    readonly moduleName: string;
    readonly meta?: unknown;
}
/** One bundle, reduced to the rows its patch declares. */
export interface SlotBundleInfo {
    readonly name: string;
    readonly rows?: readonly SlotBundleRow[];
}
/** The slice of `pluginManager` this provider reads. */
export interface SlotBundleFace {
    listBundles(): Promise<readonly SlotBundleInfo[]>;
}
/** `host-slots` configuration. */
export interface HostSlotsOptions {
    /** Resolve the settings service, or undefined when this composition has none. */
    readonly settings: () => SettingsFace | undefined;
    /** Resolve the plugin inventory, or undefined when absent. */
    readonly pluginInventory: () => SlotInventoryFace | undefined;
    /** Resolve the bundle manager, or undefined when this composition has none. */
    readonly pluginManager?: () => SlotBundleFace | undefined;
    /** Locale tags used to resolve locale-mapped titles. */
    readonly localeTags?: readonly string[];
}
/**
 * Build the `host-slots` provider.
 * @param options - service accessors and locale preference.
 * @returns the provider to register.
 */
export declare function createHostSlotsProvider(options: HostSlotsOptions): Provider;
