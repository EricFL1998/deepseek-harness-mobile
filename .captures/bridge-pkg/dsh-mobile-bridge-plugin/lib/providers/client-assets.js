/**
 * `client-assets`: the web boot graph, one entry's bundle source, and a
 * package's locale dictionary.
 *
 * Everything here comes from what the Host itself serves the official shell —
 * `clientModules.graph()` and the bundle files it resolved. The provider parses
 * no React and interprets no UI; it only reports facts a native client cannot
 * see for itself.
 */
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { BridgeFailure, optionalString, requiredString } from "../protocol/provider.js";
import { stringRecord } from "../protocol/json.js";
/**
 * Build the `client-assets` provider.
 * @param options - service accessor and budgets.
 * @returns the provider to register.
 */
export function createClientAssetsProvider(options) {
    const modules = () => {
        const face = options.clientModules();
        if (face === undefined) {
            throw new BridgeFailure('provider/unavailable', 'this Host composition has no client module registry');
        }
        return face;
    };
    return {
        id: 'client-assets',
        version: 1,
        readOnly: true,
        ops: {
            /** Report the boot graph exactly as the shell receives it. */
            'boot-graph': () => {
                const graph = modules().graph();
                if (graph === undefined) {
                    throw new BridgeFailure('provider/unavailable', 'the Host composes no web boot graph');
                }
                return {
                    rev: graph.rev,
                    release: graph.release === undefined
                        ? null
                        : {
                            version: graph.release.version,
                            ...(graph.release.commit === undefined ? {} : { commit: graph.release.commit }),
                        },
                    entries: graph.entries.map(entry => ({
                        id: entry.id,
                        url: entry.url,
                        rev: entry.rev,
                        ...(entry.inject === undefined ? {} : { inject: entry.inject }),
                        ...(entry.immediately === undefined ? {} : { immediately: entry.immediately }),
                        ...(entry.external === undefined ? {} : { external: entry.external }),
                    })),
                    batches: graph.batches.map(batch => ({
                        phase: batch.phase,
                        url: batch.url,
                        rev: batch.rev,
                        entries: batch.entries,
                    })),
                };
            },
            /** Report the loader rows the client scan had available. */
            'loader-rows': () => ({ rows: options.loaderRows?.() ?? [] }),
            /** Return one entry's bundle bytes, bounded. */
            'module-source': async (args) => {
                const id = requiredString(args, 'id');
                const path = modules().clientPath(id);
                if (path === undefined) {
                    throw new BridgeFailure('args/invalid', `no client bundle is composed for ${id}`);
                }
                let bytes;
                try {
                    bytes = await readFile(path);
                }
                catch (error) {
                    throw new BridgeFailure('provider/unavailable', `cannot read the bundle for ${id}: ${error.message}`);
                }
                const limit = options.maxModuleBytes;
                const head = bytes.byteLength > limit ? bytes.subarray(0, limit) : bytes;
                return {
                    id,
                    path,
                    bytes: head.byteLength,
                    complete: head.byteLength === bytes.byteLength,
                    sha256: createHash('sha256').update(head).digest('hex'),
                    source: head.toString('utf8'),
                };
            },
            /** Return one package's locale dictionary for the requested tag. */
            locale: async (args) => {
                const namespace = requiredString(args, 'namespace');
                const requested = optionalString(args, 'locale') ?? 'en';
                const bundlePath = modules().clientPath(namespace);
                if (bundlePath === undefined) {
                    throw new BridgeFailure('args/invalid', `no client package is composed for ${namespace}`);
                }
                // A client bundle lives at <package>/lib/client.js; its dictionaries sit
                // beside the package manifest, one directory above `lib`.
                const packageRoot = resolve(dirname(bundlePath), '..');
                const dictionaries = new Map();
                for (const tag of options.localeTags) {
                    const dictionary = await readDictionary(join(packageRoot, 'locale', `${tag}.json`));
                    if (dictionary !== undefined)
                        dictionaries.set(tag, dictionary);
                }
                if (dictionaries.size === 0) {
                    throw new BridgeFailure('provider/unavailable', `${namespace} publishes no locale dictionary`);
                }
                const available = [...dictionaries.keys()];
                const chosen = dictionaries.has(requested) ? requested : (dictionaries.get('en') === undefined ? available[0] : 'en');
                return {
                    namespace,
                    locale: chosen,
                    available,
                    dictionary: dictionaries.get(chosen) ?? {},
                };
            },
        },
    };
}
/**
 * Read one locale dictionary file, flattening nested groups into dotted keys.
 * @param path - absolute dictionary path.
 * @returns the flat dictionary, or undefined when the file is absent or unparsable.
 */
async function readDictionary(path) {
    let text;
    try {
        text = await readFile(path, 'utf8');
    }
    catch {
        // A missing dictionary is an ordinary absence: the package simply does not
        // publish this tag.
        return undefined;
    }
    let parsed;
    try {
        parsed = JSON.parse(text);
    }
    catch {
        // A malformed dictionary is not usable data; reporting it as absent keeps
        // the remaining tags readable.
        return undefined;
    }
    const flat = {};
    flatten(parsed, '', flat);
    return flat;
}
/** Flatten nested locale groups into `group.key` entries, keeping leaf strings only. */
function flatten(value, prefix, out) {
    if (typeof value === 'string') {
        if (prefix !== '')
            out[prefix] = value;
        return;
    }
    const record = stringRecord(value);
    const isLeafGroup = Object.keys(record).length > 0;
    if (!isLeafGroup)
        return;
    for (const [key, text] of Object.entries(record)) {
        out[prefix === '' ? key : `${prefix}.${key}`] = text;
    }
    if (typeof value !== 'object' || value === null)
        return;
    for (const [key, child] of Object.entries(value)) {
        if (typeof child === 'object' && child !== null) {
            flatten(child, prefix === '' ? key : `${prefix}.${key}`, out);
        }
    }
}
