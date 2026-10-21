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
import { BridgeFailure, optionalLocale, optionalObject, optionalPositiveInt, optionalString, preferredLocaleTags, requiredString, } from "../protocol/provider.js";
import { projectJson } from "../protocol/json.js";
import { evaluateBundleFile } from "../slots/evaluate.js";
import { extractStaticRegistrations, resolveStaticLabelFor, toSlotRegistration, } from "../slots/static-slots.js";
import { bundleDictionaries } from "../slots/bundle-locale.js";
import { DEFAULT_RENDER_BUDGET, renderComponent, treeToSvg } from "../slots/render-tree.js";
import { readdirSync, readFileSync, realpathSync, statSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { placementOf } from "../slot-placements.js";
import { OFFICIAL_ARTWORK, OFFICIAL_PLUGIN_ITEMS, readOfficialItemText, } from "../official-items.js";
/** The slot the Host's own Plugins page lists its official plugins in. */
const OFFICIAL_ITEM_SLOT = 'plugins.item';
/**
 * How many client bundles one request may evaluate by default.
 *
 * Each evaluation builds a module graph with React stubs, and a phone Host cannot
 * afford many of them at once: one per request keeps each response cheap, and the walk
 * continues across requests until the installation has been covered.
 */
const DEFAULT_MAX_CLIENT_BUNDLES = 1;
/**
 * Build the `client-slots` provider.
 * @param options - service accessor, entry selection, and budgets.
 * @returns the provider to register.
 */
export function createClientSlotsProvider(options) {
    const cache = new Map();
    /** Incremental walk over the installation's client halves, plus what it found. */
    const scan = { cursor: 0, seen: new Set(), registrations: [], emptySlots: new Set() };
    /** 静态抽取结果：不执行 bundle，按「文件版本 + 语言偏好」缓存。 */
    const staticCache = new Map();
    /** 每个 bundle 版本的静态解析：注册与其内联词典，与语言偏好无关。 */
    const staticSources = new Map();
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
        // Execution is opt-in on both discovery paths: evaluating a composed entry runs
        // official client code inside the Host process, and one throwing bundle would end
        // the Host. The static transpiler reads the same declarations from bundle text.
        if (options.executeBundles !== true)
            return [];
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
    /**
     * The next batch of client halves to evaluate.
     *
     * Evaluating every bundle in one request exhausts a phone Host's heap, so the walk
     * is incremental: each request evaluates a small batch, the results accumulate, and
     * a later request continues where this one stopped. Progress is reported as a
     * warning so a client can tell "nothing registered" from "not scanned yet".
     */
    const entriesFromInstallation = () => {
        // Execution is opt-in: a request that only needs formatting must never run a
        // bundle, or one Client/Host package's mount work can end the process.
        if (options.executeBundles !== true)
            return [];
        const wanted = options.entryIds.length === 0 ? undefined : new Set(options.entryIds);
        const batch = options.maxClientBundles ?? DEFAULT_MAX_CLIENT_BUNDLES;
        const catalogue = installationCatalogue();
        const rows = [];
        while (scan.cursor < catalogue.length && rows.length < batch) {
            const bundle = catalogue[scan.cursor];
            scan.cursor += 1;
            if (wanted !== undefined && !wanted.has(bundle.id))
                continue;
            if (scan.seen.has(bundle.id))
                continue;
            scan.seen.add(bundle.id);
            rows.push(bundle);
        }
        return rows;
    };
    /** Static registrations across the configured roots, cached per file version and locale preference. */
    const staticRegistrations = async (localeTags, warnings) => staticRegistrationsFor(options.clientRoots?.() ?? [], staticSources, staticCache, warnings, localeTags);
    /** Every client half found under the configured roots, sorted for a stable walk. */
    const installationCatalogue = () => {
        if (scan.catalogue !== undefined)
            return scan.catalogue;
        const found = [];
        for (const root of options.clientRoots?.() ?? [])
            found.push(...listClientBundles(root));
        found.sort((left, right) => left.id.localeCompare(right.id));
        scan.catalogue = found;
        return found;
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
        // Try the exact componentId first (new format: entryId#id).
        for (const entry of entries) {
            const found = entry.components.get(componentId);
            if (found !== undefined)
                return found;
        }
        // Fall back to the bare id for statically-extracted registrations
        // (old format where componentId did not include the entryId prefix).
        const bareId = componentId.includes('#') ? componentId.slice(componentId.indexOf('#') + 1) : componentId;
        if (bareId !== componentId) {
            for (const entry of entries) {
                const found = entry.components.get(bareId);
                if (found !== undefined)
                    return found;
            }
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
    const officialItems = async (localeTags, warnings) => {
        const modules = options.installModules?.();
        if (modules === undefined)
            return [];
        const tags = localeTags.length === 0 ? ['zh', 'en'] : localeTags;
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
                const localeTags = preferredLocaleTags(optionalLocale(args), options.localeTags ?? []);
                // 静态抽取先做：它只读文本，不受内存限制，也不执行插件代码。标签按本次请求的
                // 语言解析，因此同一份缓存可以服务不同语言的客户端。
                const staticList = await staticRegistrations(localeTags, warnings);
                const entries = await evaluateAll(warnings);
                // Incremental scan: this batch joins what earlier requests collected, and the
                // next request continues the walk. Bundles already evaluated are not repeated.
                for (const entry of entries) {
                    for (const injected of entry.bundle.injectedSlots)
                        scan.emptySlots.add(injected);
                    for (const registration of entry.bundle.registrations) {
                        scan.registrations.push({
                            slot: registration.slot,
                            ...(registration.id === undefined ? {} : { id: registration.id }),
                            ...(registration.key === undefined ? {} : { key: registration.key }),
                            ...(registration.order === undefined ? {} : { order: registration.order }),
                            ...(registration.label === undefined ? {} : { label: registration.label }),
                            ...(registration.priority === undefined ? {} : { priority: registration.priority }),
                            package: entry.bundle.entryId,
                            // The native placement is decided here so clients do not each keep a
                            // slot-to-screen table of their own.
                            placement: placementOf(registration.slot).placement,
                            origin: 'evaluated',
                            ...(registration.locale === undefined ? {} : { locale: registration.locale }),
                            render: { kind: registration.kind, componentId: registration.componentId },
                        });
                    }
                }
                // One contribution per slot/id/package: the same bundle is reachable through
                // several installation roots, and the client must not list a row three times.
                const collected = dedupeRegistrations([...staticList, ...scan.registrations]);
                const touched = new Set(collected.map(registration => registration.slot));
                for (const empty of scan.emptySlots)
                    touched.add(empty);
                const registrations = collected
                    .filter(registration => slot === undefined || registration.slot === slot);
                const occupied = new Set(registrations.map(registration => registration.slot));
                // The official roster answers only while nothing real has been collected for
                // that slot; an incremental scan therefore replaces it as it progresses.
                const asksOfficial = slot === undefined || slot === OFFICIAL_ITEM_SLOT;
                if (asksOfficial && !occupied.has(OFFICIAL_ITEM_SLOT)) {
                    for (const registration of await officialItems(localeTags, warnings)) {
                        registrations.push(registration);
                        occupied.add(registration.slot);
                    }
                }
                const scanned = scan.catalogue === undefined ? 0 : scan.cursor;
                const total = scan.catalogue?.length ?? 0;
                if (total > 0 && scanned < total) {
                    warnings.warn(`client bundles: scanned ${scanned}/${total}; more registrations appear as the walk continues`);
                }
                return {
                    slot: slot ?? null,
                    registrations: dedupeRegistrations(registrations).sort(compareRegistrations),
                    emptySlots: [...touched].filter(name => !occupied.has(name)).sort(),
                };
            },
            /** One registration's complete contribution metadata. */
            describe: async (args, warnings) => {
                const slot = requiredString(args, 'slot');
                const id = requiredString(args, 'id');
                const localeTags = preferredLocaleTags(optionalLocale(args), options.localeTags ?? []);
                // The static read is the phone's only source, and it carries the localized
                // label; the evaluated path answers only when a bundle was executed.
                for (const registration of await staticRegistrations(localeTags, warnings)) {
                    if (registration.slot !== slot)
                        continue;
                    if (registration.id !== id && registration.key !== id
                        && registration.render?.componentId !== id)
                        continue;
                    return registration;
                }
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
                // Registration identities look like `<package>#<slot>`; the artwork table
                // is keyed by the registration id (`subagent`, `web-search`, …). Try the
                // full identity first, then its suffix, so official config rows keep their
                // artwork without evaluating any bundle.
                const artworkKey = componentId.slice(componentId.lastIndexOf('#') + 1);
                const artwork = OFFICIAL_ARTWORK[componentId] ?? OFFICIAL_ARTWORK[artworkKey];
                if (artwork !== undefined) {
                    // 官方条目的图形是固定 artwork，不来自被评估的 bundle。
                    return { componentId, size, svg: artwork };
                }
                const entries = await evaluateAll(warnings);
                const found = findComponent(entries, componentId);
                if (found === undefined) {
                    // 组件未捕获时返回 null SVG，让客户端使用默认图标。
                    // 这是一个正常路径：静态抽取的注册没有可渲染的组件。
                    return { componentId, size, svg: null };
                }
                try {
                    const rendered = renderComponent(found.component, { size }, options.renderBudget);
                    const converted = treeToSvg(rendered.tree, size, options.iconViewBox);
                    for (const note of [...rendered.warnings, ...converted.warnings])
                        warnings.warn(note);
                    return { componentId, size, svg: converted.svg };
                }
                catch (error) {
                    // 渲染失败不阻塞目录：返回 null 让客户端使用默认图标。
                    warnings.warn(`icon ${componentId}: render failed: ${error.message}`);
                    return { componentId, size, svg: null };
                }
            },
            /**
             * Deliver one native UI event to the plugin.
             *
             * The phone never executes plugin JavaScript: a tap, an edit, or a toggle
             * arrives here as data, the bridge re-broadcasts it on the Host event bus
             * as `ui/<componentId>/<event>` (so a plugin listening with `ctx.on` learns
             * about it), and a `client/refresh` message is queued for the phone so the
             * component's tree is re-pulled once the plugin has reacted.
             */
            dispatch: async (args, warnings) => {
                const componentId = requiredString(args, 'componentId');
                const event = requiredString(args, 'event');
                if (!EVENT_NAMES.has(event)) {
                    throw new BridgeFailure('args/invalid', `event must be one of ${[...EVENT_NAMES].join(', ')}`, 200, {
                        known: [...EVENT_NAMES].sort(),
                    });
                }
                const payload = optionalObject(args, 'payload') ?? {};
                const projected = projectJson(payload, 'payload');
                let handled = false;
                for (const path of projected.dropped)
                    warnings.warn(`dropped payload member ${path}`);
                const body = (projected.value ?? {});
                // A registered action runs the plugin's own Host code first: that is what
                // "the plugin's button was pressed" means when its browser half never runs.
                const actionId = typeof body.actionId === 'string' ? body.actionId : '';
                if (options.runAction !== undefined && actionId.length > 0) {
                    try {
                        const ran = await options.runAction(componentId, actionId, body);
                        if (ran) {
                            handled = true;
                            options.notify?.('client/refresh', componentId);
                            return { ok: true, handled: true };
                        }
                    }
                    catch (error) {
                        warnings.warn(`plugin action ${actionId} failed: ${error.message}`);
                    }
                }
                if (options.emit !== undefined) {
                    try {
                        options.emit(`ui/${componentId}/${event}`, body);
                    }
                    catch (error) {
                        warnings.warn(`ui event delivery failed: ${error.message}`);
                    }
                }
                else {
                    warnings.warn('this Host composition has no event bus; the plugin was not notified');
                }
                // The phone re-pulls the tree on its next poll; the refresh message makes
                // the update visible without waiting for the poll interval.
                options.notify?.('client/refresh', componentId);
                // handled:false 表示没有插件逻辑执行（Host 半边没注册动作）；客户端可以据此
                // 降级到整页 Web 界面，而不是让按钮看起来「点了没反应」。
                return { ok: true, handled };
            },
        },
    };
}
/** Events a native renderer may send back through `dispatch`. */
const EVENT_NAMES = new Set(['press', 'input', 'toggle', 'submit']);
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
                // Only browser UI packages declare slots. A Host service that also ships a
                // client half (remote-access, job-controller, …) does real work in `apply`,
                // which must never run inside the bridge VM.
                if (!isUiClientPackage(candidate.dir, candidate.id))
                    continue;
                rows.push({ id: candidate.id, dir: candidate.dir, path: clientPath, rev: `${stat.mtimeMs}:${stat.size}` });
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
/**
 * Whether a package contributes browser UI rather than a Host service.
 *
 * The rule mirrors the repository's own naming: UI packages are `dsh-client-*`. A
 * package outside that namespace can still be a Client/Host package, which it declares
 * with a `dsh.client` manifest field. Everything else is a Host service whose client
 * half exists only for transport, and evaluating it would run real work.
 * @param dir absolute package directory.
 * @param id package name.
 * @returns whether the package's client half may be evaluated.
 */
function isUiClientPackage(dir, id) {
    if (id.startsWith('@deepseek-ai/dsh-client-'))
        return true;
    try {
        const manifest = JSON.parse(readFileSync(join(dir, 'package.json'), 'utf8'));
        return manifest.dsh?.client !== undefined;
    }
    catch {
        return false;
    }
}
/**
 * Remove the duplicate registrations one bundle contributes through more than one root.
 *
 * A phone Host locates its installation's `node_modules` through several path forms
 * (a `require.resolve` answer, the running entry point, and the profile's own
 * `node_modules` symlink). Walking each root re-discovers the same client halves, so
 * the same `settings.section` contribution arrived three times and the Settings page
 * listed it three times. Identity is the contribution itself, not the path it was
 * found through:
 *
 * ```
 * `${slot}|${id ?? key ?? ''}|${package}`
 * ```
 *
 * The first occurrence wins, which is discovery order: roots are ordered install-first,
 * so the authoritative copy supplies the row (and its localized label).
 *
 * @param registrations rows in discovery order.
 * @returns one row per contribution identity, in the original order.
 */
export function dedupeRegistrations(registrations) {
    const seen = new Set();
    const unique = [];
    for (const registration of registrations) {
        const identity = `${registration.slot}|${registration.id ?? registration.key ?? ''}|${registration.package ?? ''}`;
        if (seen.has(identity))
            continue;
        seen.add(identity);
        unique.push(registration);
    }
    return unique;
}
/**
 * The roots to scan, with textual and symlink duplicates resolved away.
 *
 * Two roots that are the same directory reached by different path spellings would
 * otherwise repeat the whole scan. A root that cannot be resolved is kept as written:
 * the scanner already treats an unreadable root as "no bundles", so dropping it would
 * be the one failure mode that hides a plugin's UI.
 *
 * @param roots absolute `node_modules` directories, authoritative first.
 * @returns the distinct directories, in the same order.
 */
function uniqueRoots(roots) {
    const seen = new Set();
    const unique = [];
    for (const root of roots) {
        let key = root;
        try {
            key = realpathSync(root);
        }
        catch {
            // Keep the literal path; the scanner reports an unreadable root by finding nothing.
        }
        if (seen.has(key))
            continue;
        seen.add(key);
        unique.push(root);
    }
    return unique;
}
/**
 * Registrations read from every UI client bundle's source.
 *
 * This is the discovery path that works on a phone Host: it reads text instead of
 * executing bundles, so a client half's mount work never runs and the heap stays flat.
 * Labels are resolved per request: a registration written as `label: () => t("nav")`
 * takes its text from the bundle's own inline copy tables, in the language the
 * caller's tag preference selects. Results are cached per file version and tag
 * preference, and a missing or unreadable bundle contributes nothing rather than
 * failing the whole list.
 *
 * A bundle reachable through several roots is scanned once (`id@rev` is the same file
 * version everywhere), and the returned rows are deduplicated by contribution
 * identity, because a symlinked path can still present the same bundle under another
 * root spelling.
 * @param roots - directories whose packages may ship a `lib/client.js`.
 * @param sources - per-version cache of the static parse and its copy tables.
 * @param cache - per-version-and-language cache of the projected registrations.
 * @param warnings - sink for per-bundle notes.
 * @returns registrations from all bundles, in package order, one per contribution.
 */
async function staticRegistrationsFor(roots, sources, cache, warnings, localeTags) {
    // 客户端换语言会产生新的缓存键；文本很小，重新解析比无界增长便宜。
    if (cache.size > 128)
        cache.clear();
    const all = [];
    /** `id@rev` pairs already scanned in this call: the same file under another root. */
    const scanned = new Set();
    for (const root of uniqueRoots(roots)) {
        for (const bundle of listClientBundles(root)) {
            const versionKey = `${bundle.id}@${bundle.rev}`;
            // The same package version is the same bundle no matter which root reached it.
            if (scanned.has(versionKey))
                continue;
            scanned.add(versionKey);
            const cacheKey = `${versionKey}@${localeTags.join(',')}`;
            const cached = cache.get(cacheKey);
            if (cached !== undefined) {
                all.push(...cached);
                continue;
            }
            try {
                let scan = sources.get(versionKey);
                if (scan === undefined) {
                    const source = await readFile(bundle.path, 'utf8');
                    scan = {
                        registrations: extractStaticRegistrations(source),
                        dictionaries: bundleDictionaries(source),
                    };
                    sources.set(versionKey, scan);
                }
                const locales = await localeDictionaries(bundle.dir, localeTags);
                const registrations = scan.registrations.map(registration => {
                    const projected = toSlotRegistration(bundle.id, registration);
                    const label = resolveStaticLabelFor(registration, scan.dictionaries, locales, localeTags);
                    // A package without any matching text keeps no label: the client shows the id,
                    // which is honest, instead of a name nobody wrote.
                    return label === undefined ? projected : { ...projected, label };
                });
                cache.set(cacheKey, registrations);
                all.push(...registrations);
            }
            catch (error) {
                warnings.warn(`${bundle.id}: cannot read its client bundle (${error.message})`);
            }
        }
    }
    return dedupeRegistrations(all);
}
/**
 * The package's own locale dictionaries, most preferred tag first.
 *
 * A client package ships `locale/<tag>.json`; that file is what the browser reads to
 * label its contributions, so it is also what a static reader must consult. Missing
 * files simply contribute nothing.
 * @param dir absolute package directory.
 * @param tags preferred language tags.
 * @returns parsed dictionaries in preference order.
 */
async function localeDictionaries(dir, tags) {
    if (dir === undefined)
        return [];
    const out = [];
    for (const tag of tags.length === 0 ? ['zh', 'en'] : tags) {
        const candidate = join(dir, 'locale', `${tag}.json`);
        try {
            out.push(JSON.parse(await readFile(candidate, 'utf8')));
        }
        catch {
            // No dictionary for this tag: try the next.
        }
    }
    return out;
}
