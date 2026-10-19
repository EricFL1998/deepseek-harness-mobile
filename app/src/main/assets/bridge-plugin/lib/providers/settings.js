/**
 * `settings`: the Host's live plugin configuration documents.
 *
 * A plugin's Settings page is a generated form: the Host projects the plugin's
 * `Config` schema into a field tree and stores the user's overrides. This provider
 * exposes exactly that pair — the form and its values — plus the write the page
 * needs, so a native client renders the *same* form the browser does instead of
 * guessing at fields.
 */
import { BridgeFailure, requiredString } from "../protocol/provider.js";
/**
 * Build the `settings` provider.
 * @param options - service accessor.
 * @returns the provider to register.
 */
export function createSettingsProvider(options) {
    const service = () => {
        const face = options.settings();
        if (face === undefined) {
            throw new BridgeFailure('provider/unavailable', 'this Host composition has no settings service');
        }
        return face;
    };
    return {
        id: 'settings',
        version: 1,
        ops: {
            /** The form and live values of one settings document. */
            describe: (args) => {
                const ns = requiredString(args, 'ns');
                const document = service().describe({ redactSecrets: true }).find(row => row.ns === ns);
                // A namespace that vanished between listing and opening is a real state, not
                // a bug: report it so the page can explain instead of showing blank fields.
                if (document === undefined) {
                    throw new BridgeFailure('args/invalid', `no configurable settings document "${ns}"`);
                }
                return {
                    ns: document.ns,
                    revision: document.revision,
                    applies: document.applies,
                    schema: document.schema ?? {},
                    value: document.value ?? {},
                };
            },
            /** Merge edited fields into the document, rejecting a stale revision. */
            update: async (args) => {
                const ns = requiredString(args, 'ns');
                const patch = args.patch;
                if (patch === undefined || patch === null || typeof patch !== 'object' || Array.isArray(patch)) {
                    throw new BridgeFailure('args/invalid', 'patch must be an object of edited fields');
                }
                const expected = args.expectedRevision;
                if (expected !== undefined && typeof expected !== 'number') {
                    throw new BridgeFailure('args/invalid', 'expectedRevision must be a number');
                }
                await service().update(ns, patch, expected);
                return { ns, updated: true };
            },
        },
    };
}
