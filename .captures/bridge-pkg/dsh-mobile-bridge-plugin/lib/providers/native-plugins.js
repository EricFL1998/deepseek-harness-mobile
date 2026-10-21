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
import { optionalString } from "../protocol/provider.js";
/**
 * Build the `native-plugins` provider.
 * @param options - the declaration source.
 * @returns the provider to register.
 */
export function createNativePluginsProvider(options) {
    return {
        id: 'native-plugins',
        version: 1,
        readOnly: true,
        ops: {
            /** Every native-companion declaration: `{ companions: [...] }`. */
            list: () => ({ companions: options.companions.list() }),
            /**
             * One plugin's declaration: `for { pluginId }` → the declaration, or
             * `null` when this plugin ships no native half.
             */
            for: (args) => {
                const pluginId = optionalString(args, 'pluginId');
                if (pluginId === undefined)
                    return { companion: null };
                const found = options.companions.list().find((c) => c.pluginId === pluginId);
                return { companion: found ?? null };
            },
        },
    };
}
