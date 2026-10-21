/**
 * `native-plugins`: the catalogue of native companion plugins.
 *
 * A JS plugin whose author also ships a native companion (a Kotlin/Compose APK
 * rendered by the native SDK) declares that fact in its Host half. This provider
 * surfaces those declarations to the Android client so the App can:
 *
 * - tell the user a native enhanced UI is available for an installed plugin;
 * - read the companion's declared capabilities and the ABI version it was built
 *   against, so the store page can mark "compatible ✓ / update App" before any
 *   download;
 * - hand the companion's `pluginId` to the App's discovery, which pairs the APK
 *   with this JS plugin by that id.
 *
 * The companion APK itself is never distributed through the bridge — it ships
 * through the store / package manager. This provider only answers "which plugins
 * have a native half, and what does that half declare".
 */
import { type Provider } from '../protocol/provider.ts';
/** One native companion declaration, as a plugin's Host half reports it. */
export interface NativeCompanionDeclaration {
    /** The plugin id the companion APK binds to (`<package>` / componentId prefix). */
    readonly pluginId: string;
    /** The companion's own display name. */
    readonly name?: string;
    /** The native-SDK ABI version the companion was built against. */
    readonly apiVersion?: number;
    /** The minimum App versionCode the companion needs. */
    readonly minAppVersion?: number;
    /** Capabilities the companion declares (`openUrl`, `storage`, …). */
    readonly capabilities?: readonly string[];
    /** A store/landing URL for the companion APK, when the author publishes one. */
    readonly storeUrl?: string;
}
/** Read the declarations the composition currently holds. */
export interface NativeCompanionSource {
    /**
     * Every native-companion declaration the running Host knows about, keyed by
     * nothing — the caller filters by pluginId.
     */
    readonly list: () => readonly NativeCompanionDeclaration[];
}
/** `native-plugins` configuration. */
export interface NativePluginsOptions {
    readonly companions: NativeCompanionSource;
}
/**
 * Build the `native-plugins` provider.
 * @param options - the declaration source.
 * @returns the provider to register.
 */
export declare function createNativePluginsProvider(options: NativePluginsOptions): Provider;
