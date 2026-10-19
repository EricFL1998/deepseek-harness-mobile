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
import { localizedString } from "../protocol/json.js";
/** Where the native app puts a contribution. */
const SETTINGS_SURFACE = 'settings.page';
/**
 * Build the `host-slots` provider.
 * @param options - service accessors and locale preference.
 * @returns the provider to register.
 */
export function createHostSlotsProvider(options) {
    return {
        id: 'host-slots',
        version: 1,
        readOnly: true,
        ops: {
            /** Every plugin-declared surface this Host can enumerate. */
            list: async () => {
                const settings = options.settings();
                const localeTags = options.localeTags ?? [];
                if (settings === undefined) {
                    // No settings service means no server-declared surfaces at all: report an
                    // empty list rather than an error, because the rest of the bridge works.
                    return { surfaces: [] };
                }
                const descriptors = settings.describe({ redactSecrets: true });
                const metaOf = await rowMetaById(options.pluginInventory?.(), localeTags);
                const surfaces = [];
                for (const descriptor of descriptors) {
                    const meta = metaOf.get(descriptor.ns);
                    // The namespace always exists; the title may not. Falling back to the
                    // namespace keeps the entry usable instead of dropping a real page.
                    surfaces.push({
                        slot: SETTINGS_SURFACE,
                        id: descriptor.ns,
                        title: meta?.title ?? descriptor.ns,
                        ...(meta?.description === undefined ? {} : { description: meta.description }),
                        ...(meta?.moduleName === undefined ? {} : { module: meta.moduleName }),
                    });
                }
                return { surfaces };
            },
        },
    };
}
/** Title, description and module of each inventory row, keyed by row id. */
async function rowMetaById(inventory, localeTags) {
    const out = new Map();
    if (inventory === undefined)
        return out;
    const snapshot = await inventory.list();
    for (const entry of snapshot.entries) {
        out.set(entry.entryId, {
            title: localizedString(entry.meta, ['title'], localeTags),
            description: localizedString(entry.meta, ['description'], localeTags),
            moduleName: entry.moduleName,
        });
    }
    return out;
}
