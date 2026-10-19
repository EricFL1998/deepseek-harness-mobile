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
import { BridgeFailure, optionalObject, optionalPositiveInt, optionalString, requiredString, } from "../protocol/provider.js";
import { projectJson } from "../protocol/json.js";
import { evaluateBundleFile } from "../slots/evaluate.js";
import { DEFAULT_RENDER_BUDGET, renderComponent, treeToSvg } from "../slots/render-tree.js";
import { readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { placementOf } from "../slot-placements.js";
import { OFFICIAL_ARTWORK, OFFICIAL_PLUGIN_ITEMS, readOfficialItemText, } from "../official-items.js";
/** The slot the Host's own Plugins page lists its official plugins in. */
const OFFICIAL_ITEM_SLOT = 'plugins.item';
/**
 * How many client bundles one request may evaluate by default.
 *
 * Each evaluation builds a module graph with React stubs; a phone Host cannot afford
 * hundreds of them at once, so the walk is bounded and a caller that needs more names
 * the packages it wants.
 */
const DEFAULT_MAX_CLIENT_BUNDLES = 8;
/**
 * Build the `client-slots` provider.
 * @param options - service accessor, entry selection, and budgets.
 * @returns the provider to register.
 */
export function createClientSlotsProvider(options) {
    const cache = new Map();
    /**
     * The client bundles this provider may evaluate, in declared order.
     *
     * Two sources, because they answer different Hosts: a Host that composes rows
     * server-side names its entries in the boot graph, while a Host serving a prebuilt
     * web client composes none — yet every package still ships its client half as
     * `lib/client.js`. Reading those files is what makes the *real* slot declarations
     * reachable there, with no table of expected entries.
     */
    const selectedEntries = () => {
        const fromGraph = entriesFromGraph();
        return fromGraph.length > 0 ? fromGraph : entriesFromInstallation();
    };
    /** Entries named by the Host's composed boot graph. */
    const entriesFromGraph = () => {
        const face = options.clientModules();
        const graph = face?.graph();
        if (face === undefined || graph === undefined)
            return [];
        const wanted = options.entryIds.length === 0
            ? graph.entries
            : graph.entries.filter(entry => options.entryIds.includes(entry.id));
        const rows = [];
        for (const entry of wanted) {
            const path = face.clientPath(entry.id);
            // An entry the Host composes but cannot locate on disk is skipped rather
            // than reported as a broken registration source.
            if (path === undefined)
                continue;
            rows.push({ id: entry.id, path, rev: entry.rev });
        }
        return rows;
    };
    /** Client halves found on disk, one per package that ships `lib/client.js`. */
    const entriesFromInstallation = () => {
        const wanted = options.entryIds.length === 0 ? undefined : new Set(options.entryIds);
        const limit = options.maxClientBundles ?? DEFAULT_MAX_CLIENT_BUNDLES;
        const rows = [];
        for (const root of options.clientRoots?.() ?? []) {
            for (const bundle of listClientBundles(root)) {
                if (wanted !== undefined && !wanted.has(bundle.id))
                    continue;
                // Evaluating every client half on a phone exhausts the Host's heap, so the
                // scan stops at a bound and reports the rest as not walked.
                if (rows.length >= limit)
                    return rows;
                rows.push(bundle);
            }
        }
        return rows;
    };
    /** Evaluate one entry, reusing a cached result for the same revision. */
    const evaluationOf = async (id, path, rev) => {
        const cached = cache.get(id);
        if (cached !== undefined && cached.rev === rev)
            return cached;
        const bundle = await evaluateBundleFile(id, path);
        const components = new Map();
        for (const registration of bundle.registrations) {
            components.set(registration.componentId, {
                componentId: registration.componentId,
                component: registration.component,
                slot: registration.slot,
                entryId: id,
            });
        }
        const entry = { rev, bundle, components };
        cache.set(id, entry);
        return entry;
    };
    /** Evaluate every selected entry, reporting per-bundle failures as warnings. */
    const evaluateAll = async (warnings) => {
        const entries = [];
        for (const row of selectedEntries()) {
            try {
                const evaluation = await evaluationOf(row.id, row.path, row.rev);
                entries.push(evaluation);
                for (const note of evaluation.bundle.warnings)
                    warnings.warn(note);
                for (const specifier of evaluation.bundle.missingExternals) {
                    warnings.warn(`${row.id} requested ${specifier}, which the bridge VM does not provide`);
                }
            }
            catch (error) {
                warnings.warn(error.message);
            }
        }
        return entries;
    };
    const findComponent = (entries, componentId) => {
        for (const entry of entries) {
            const found = entry.components.get(componentId);
            if (found !== undefined)
                return found;
        }
        return undefined;
    };
    /**
     * The official roster, as registrations the client can render.
     *
     * Used only when no evaluated bundle contributed to `plugins.item` — a prebuilt
     * web client composes no entry graph at all, so the slot would otherwise be
     * empty even though the page has an Official group. Text is read from each
     * package's own locale file; a package that is not installed contributes
     * nothing, so the roster always describes this Host rather than a design.
     */
    const officialItems = async (warnings) => {
        const modules = options.installModules?.();
        if (modules === undefined)
            return [];
        const tags = options.localeTags ?? [];
        const registrations = [];
        for (const item of OFFICIAL_PLUGIN_ITEMS) {
            let text;
            for (const tag of tags) {
                text = await readOfficialItemText(modules, item, tag);
                if (text !== undefined)
                    break;
            }
            if (text === undefined) {
                warnings.warn(`official plugin ${item.id} has no readable locale; skipped`);
                continue;
            }
            registrations.push({
                slot: OFFICIAL_ITEM_SLOT,
                id: item.id,
                order: item.order,
                label: text.title,
                package: item.packageName,
            });
        }
        return registrations;
    };
    return {
        id: 'client-slots',
        version: 1,
        readOnly: true,
        ops: {
            /** Every captured registration, optionally narrowed to one slot. */
            list: async (args, warnings) => {
                const slot = optionalString(args, 'slot');
                const entries = await evaluateAll(warnings);
                const registrations = [];
                const touched = new Set();
                for (const entry of entries) {
                    for (const injected of entry.bundle.injectedSlots)
                        touched.add(injected);
                    for (const registration of entry.bundle.registrations) {
                        touched.add(registration.slot);
                        if (slot !== undefined && registration.slot !== slot)
                            continue;
                        registrations.push({
                            slot: registration.slot,
                            ...(registration.id === undefined ? {} : { id: registration.id }),
                            ...(registration.key === undefined ? {} : { key: registration.key }),
                            ...(registration.order === undefined ? {} : { order: registration.order }),
                            ...(registration.label === undefined ? {} : { label: registration.label }),
                            ...(registration.priority === undefined ? {} : { priority: registration.priority }),
                            package: entry.bundle.entryId,
                            // 原生落点由 bridge 判定：客户端不再各自维护一张 slot→屏幕的对照表。
                            placement: placementOf(registration.slot).placement,
                            ...(registration.locale === undefined ? {} : { locale: registration.locale }),
                            render: { kind: registration.kind, componentId: registration.componentId },
                        });
                    }
                }
                const occupied = new Set(registrations.map(registration => registration.slot));
                // 只有官方分组整列为空时才回落：评估到的真实注册永远优先。
                const asksOfficial = slot === undefined || slot === OFFICIAL_ITEM_SLOT;
                if (asksOfficial && !occupied.has(OFFICIAL_ITEM_SLOT)) {
                    for (const registration of await officialItems(warnings)) {
                        registrations.push(registration);
                        occupied.add(registration.slot);
                    }
                }
                return {
                    slot: slot ?? null,
                    registrations: registrations.sort(compareRegistrations),
                    emptySlots: [...touched].filter(name => !occupied.has(name)).sort(),
                };
            },
            /** One registration's complete contribution metadata. */
            describe: async (args, warnings) => {
                const slot = requiredString(args, 'slot');
                const id = requiredString(args, 'id');
                const entries = await evaluateAll(warnings);
                for (const entry of entries) {
                    const match = entry.bundle.registrations.find(registration => registration.slot === slot
                        && (registration.id === id || registration.key === id || registration.componentId === id));
                    if (match === undefined)
                        continue;
                    return {
                        slot: match.slot,
                        ...(match.id === undefined ? {} : { id: match.id }),
                        ...(match.key === undefined ? {} : { key: match.key }),
                        ...(match.order === undefined ? {} : { order: match.order }),
                        ...(match.label === undefined ? {} : { label: match.label }),
                        ...(match.priority === undefined ? {} : { priority: match.priority }),
                        package: entry.bundle.entryId,
                        ...(match.locale === undefined ? {} : { locale: match.locale }),
                        render: { kind: match.kind, componentId: match.componentId },
                    };
                }
                throw new BridgeFailure('args/invalid', `no registration ${slot}/${id}`);
            },
            /** Serialize one captured component's element tree. */
            'render-tree': async (args, warnings) => {
                const componentId = requiredString(args, 'componentId');
                const props = optionalObject(args, 'props') ?? {};
                const entries = await evaluateAll(warnings);
                const found = findComponent(entries, componentId);
                if (found === undefined) {
                    throw new BridgeFailure('args/invalid', `no captured component ${componentId}`, 200, {
                        known: entries.flatMap(entry => [...entry.components.keys()]),
                    });
                }
                const projected = projectJson(props, 'props');
                for (const path of projected.dropped)
                    warnings.warn(`dropped prop ${path}`);
                const rendered = renderComponent(found.component, (projected.value ?? {}), options.renderBudget);
                for (const note of rendered.warnings)
                    warnings.warn(note);
                return { componentId, tree: rendered.tree };
            },
            /** Convert one captured component's artwork into a standalone SVG document. */
            'icon-svg': async (args, warnings) => {
                const componentId = requiredString(args, 'componentId');
                const size = optionalPositiveInt(args, 'size', 24, options.maxIconSize);
                const artwork = OFFICIAL_ARTWORK[componentId];
                if (artwork !== undefined) {
                    // 官方条目的图形是固定 artwork，不来自被评估的 bundle。
                    return { componentId, size, svg: artwork };
                }
                const entries = await evaluateAll(warnings);
                const found = findComponent(entries, componentId);
                if (found === undefined) {
                    throw new BridgeFailure('args/invalid', `no captured component ${componentId}`, 200, {
                        known: entries.flatMap(entry => [...entry.components.keys()]),
                    });
                }
                const rendered = renderComponent(found.component, { size }, options.renderBudget);
                const converted = treeToSvg(rendered.tree, size, options.iconViewBox);
                for (const note of [...rendered.warnings, ...converted.warnings])
                    warnings.warn(note);
                return { componentId, size, svg: converted.svg };
            },
        },
    };
}
/**
 * Order registrations the way the client dispatches them: slot, then declared
 * order, then id, so two calls return the same sequence.
 * @param left - first registration.
 * @param right - second registration.
 * @returns the comparison result.
 */
function compareRegistrations(left, right) {
    if (left.slot !== right.slot)
        return left.slot < right.slot ? -1 : 1;
    const leftOrder = left.order ?? 0;
    const rightOrder = right.order ?? 0;
    if (leftOrder !== rightOrder)
        return leftOrder - rightOrder;
    const leftId = left.id ?? left.key ?? '';
    const rightId = right.id ?? right.key ?? '';
    return leftId < rightId ? -1 : leftId > rightId ? 1 : 0;
}
/** Re-exported so the plugin assembly can share one budget default. */
export { DEFAULT_RENDER_BUDGET };
/**
 * Every `lib/client.js` under one `node_modules` root.
 *
 * The client half's own file is the declaration source: it is the exact code the
 * browser would run, so a registration read here is the plugin's real declaration.
 * @param root absolute `node_modules` directory.
 * @returns entry ids and paths, in directory order.
 */
function listClientBundles(root) {
    const rows = [];
    let entries;
    try {
        entries = readdirSync(root, { withFileTypes: true });
    }
    catch {
        return rows;
    }
    for (const entry of entries) {
        if (!isDirectoryEntry(entry))
            continue;
        const scoped = entry.name.startsWith('@');
        const packages = scoped
            ? readdirSync(join(root, entry.name), { withFileTypes: true })
                .filter(child => isDirectoryEntry(child))
                .map(child => ({ id: `${entry.name}/${child.name}`, dir: join(root, entry.name, child.name) }))
            : [{ id: entry.name, dir: join(root, entry.name) }];
        for (const candidate of packages) {
            const clientPath = join(candidate.dir, 'lib', 'client.js');
            try {
                const stat = statSync(clientPath);
                if (!stat.isFile())
                    continue;
                rows.push({ id: candidate.id, path: clientPath, rev: `${stat.mtimeMs}:${stat.size}` });
            }
            catch {
                // No client half: most packages are Host-only.
            }
        }
    }
    return rows;
}
/**
 * Whether a directory entry leads to a directory.
 *
 * A pnpm `node_modules` links each package into place, and a link's own dirent is
 * neither a file nor a directory — treating links as non-directories would skip
 * every installed package, which is precisely the layout this Host ships.
 * @param entry dirent to classify.
 * @returns whether the entry can be read as a directory.
 */
function isDirectoryEntry(entry) {
    return entry.isDirectory() || entry.isSymbolicLink();
}
