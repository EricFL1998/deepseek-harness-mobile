/**
 * `package-inventory`: the Host's own plugin-management records.
 *
 * This provider reuses Host services rather than guessing from the filesystem,
 * so third-party bundle icons and titles stay fully backend-driven.
 */
import { BridgeFailure, optionalString, requiredString } from "../protocol/provider.js";
import { firstString, localizedString } from "../protocol/json.js";
/** Localized display text of one package or row, as the Host resolves it. */
function displayText(meta, localeTags = []) {
    const title = localizedString(meta, ['title'], localeTags);
    const description = localizedString(meta, ['description'], localeTags);
    const icon = localizedString(meta, ['icon'], localeTags);
    return {
        ...(title === undefined ? {} : { title }),
        ...(description === undefined ? {} : { description }),
        ...(icon === undefined ? {} : { icon }),
    };
}
/**
 * Build the `package-inventory` provider.
 * @param options - service accessors.
 * @returns the provider to register.
 */
export function createPackageInventoryProvider(options) {
    const managementAvailable = () => options.pluginManager() !== undefined;
    return {
        id: 'package-inventory',
        version: 1,
        readOnly: true,
        ops: {
            /** Every bundle the Host knows about, with its rows and `meta.icon`. */
            bundles: async (args) => {
                const manager = options.pluginManager();
                if (manager === undefined) {
                    throw new BridgeFailure('provider/unavailable', 'this Host composition has no plugin manager');
                }
                const localeTags = options.localeTags ?? [];
                const name = optionalString(args, 'name');
                const bundles = await manager.listBundles();
                // A row's on/off state lives in the Loader inventory, not in the bundle
                // record: read it once and join by `entryId`, the same join the browser's
                // own Plugins page performs. A row whose entry is not inventoried is off.
                const entries = await rowEntries(options.pluginInventory());
                const rows = (name === undefined ? bundles : bundles.filter(bundle => bundle.name === name))
                    .map(bundle => projectBundle(bundle, localeTags, entries));
                return { bundles: rows, managementAvailable: true };
            },
            /** Every running Loader entry the Host reports. */
            rows: async () => {
                const inventory = options.pluginInventory();
                if (inventory === undefined) {
                    throw new BridgeFailure('provider/unavailable', 'this Host composition has no plugin inventory');
                }
                const snapshot = await inventory.list();
                const rows = snapshot.entries.map(entry => ({
                    entryId: entry.entryId,
                    moduleName: entry.moduleName,
                    enabled: entry.enabled,
                    fiberPhase: entry.fiberPhase,
                    ...displayText(entry.meta),
                }));
                return { rows, managementAvailable: managementAvailable() };
            },
            /** One bundle's or row's `meta.icon` data URL, passed through untouched. */
            'meta-icon': async (args) => {
                const name = requiredString(args, 'name');
                const manager = options.pluginManager();
                if (manager !== undefined) {
                    const bundle = (await manager.listBundles()).find(item => item.name === name);
                    if (bundle !== undefined)
                        return { name, icon: displayText(bundle.meta).icon ?? null };
                }
                const inventory = options.pluginInventory();
                if (inventory !== undefined) {
                    const snapshot = await inventory.list();
                    const row = snapshot.entries.find(entry => entry.entryId === name || entry.moduleName === name);
                    if (row !== undefined)
                        return { name, icon: displayText(row.meta).icon ?? null };
                }
                throw new BridgeFailure('args/invalid', `no bundle or row is named ${name}`);
            },
        },
    };
}
/**
 * The Loader inventory keyed by entry id, or undefined when this composition has no
 * inventory service (then a row keeps whatever `enabled` the bundle record carried).
 * @param inventory - the plugin inventory face, when present.
 * @returns entry id → entry, or undefined.
 */
async function rowEntries(inventory) {
    if (inventory === undefined)
        return undefined;
    const snapshot = await inventory.list();
    return new Map(snapshot.entries.map(entry => [entry.entryId, entry]));
}
/**
 * Project one Host bundle record onto the wire row.
 * @param bundle - the Host record.
 * @param localeTags - locale preference for title and description text.
 * @param entries - the Loader inventory, when the composition has one.
 * @returns the wire row, with absent optional fields omitted.
 */
function projectBundle(bundle, localeTags, entries) {
    const text = displayText(bundle.meta, localeTags);
    const rows = (bundle.rows ?? []).map(row => {
        const live = row.entryId === undefined ? undefined : entries?.get(row.entryId);
        // Same rule as the browser page: a row the Host resolved to an entry takes its
        // state from that entry; without an entry it cannot be running.
        const enabled = entries === undefined ? row.enabled : (live?.enabled ?? false);
        return {
            rowId: row.rowId,
            moduleName: row.moduleName,
            ...(row.entryId === undefined ? {} : { entryId: row.entryId }),
            ...(enabled === undefined ? {} : { enabled }),
            ...displayText(row.meta, localeTags),
        };
    });
    return {
        name: bundle.name,
        official: bundle.official,
        optional: bundle.optional,
        removable: bundle.removable,
        enabled: bundle.enabled,
        installed: bundle.installed,
        availability: bundle.availability,
        ...(bundle.version === undefined ? {} : { version: bundle.version }),
        ...(bundle.source === undefined ? {} : { source: bundle.source }),
        ...text,
        ...(bundle.description === undefined ? {} : { description: text.description ?? bundle.description }),
        ...(bundle.error?.code === undefined ? {} : { errorCode: bundle.error.code }),
        rows,
    };
}
