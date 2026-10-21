/**
 * dsh-mobile-bridge: a Host plugin that exposes the official web client's own
 * runtime data to native clients as a versioned, capability-negotiated JSON
 * protocol.
 *
 * The plugin owns two routes on the composition's `webServer`
 * (`/dsh-mobile/bridge/v1/manifest` and `/dsh-mobile/bridge/v1/query`) and a
 * provider table behind them. It extracts data; clients render. Every feature is
 * a provider, so a new compatibility feature adds a provider and leaves the wire
 * format alone.
 */
import { readdirSync, realpathSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import z from '@deepseek-ai/schemastery';
import { webServerOf } from "./host-services.js";
import { createBridgeHandler, writeReply } from "./handler.js";
import { createEventQueue } from "./events.js";
import { MobileUiService } from "./mobile-ui-service.js";
import { createEventsProvider } from "./providers/events.js";
import { createSettingsProvider } from "./providers/settings.js";
import { ProviderRegistry } from "./protocol/provider.js";
import { BRIDGE_BASE_PATH, BRIDGE_VERSION, EVENTS_PATH, MANIFEST_PATH, QUERY_PATH } from "./protocol/types.js";
import { createClientAssetsProvider, } from "./providers/client-assets.js";
import { createClientSlotsProvider, } from "./providers/client-slots.js";
import { createPackageInventoryProvider, } from "./providers/package-inventory.js";
import { createHostSlotsProvider } from "./providers/host-slots.js";
import { createRpcProvider } from "./providers/rpc.js";
import { createNativePluginsProvider } from "./providers/native-plugins.js";
import { DEFAULT_RENDER_BUDGET } from "./slots/render-tree.js";
/** Cordis function-plugin name. */
export const name = 'dsh-mobile-bridge-plugin';
/** The routes need the Host's HTTP carrier; every other service is optional. */
export const inject = ['webServer'];
/** Config schema; `schemastery` applies these defaults through the Loader. */
export const Config = z.object({
    enabled: z.boolean().default(true),
    slotEntries: z.array(z.string()).default([]),
    /**
     * Evaluate the client halves found on disk when the Host composes no boot graph.
     *
     * Off by default: on a phone Host even one evaluated client bundle has taken the
     * process down, so the walk is only safe where the heap is not shared with the agent
     * runtime. Extracting registration metadata without executing the bundle is the path
     * that removes this constraint; until then the official roster answers `plugins.item`.
     */
    clientBundleScan: z.boolean().default(false),
    maxModuleBytes: z.natural().min(1024).max(16 * 1024 * 1024).default(512 * 1024),
    maxIconSize: z.natural().min(8).max(1024).default(256),
    iconViewBox: z.string().default('0 0 24 24'),
    maxRenderDepth: z.natural().min(1).max(64).default(DEFAULT_RENDER_BUDGET.maxDepth),
    maxRenderNodes: z.natural().min(1).max(100_000).default(DEFAULT_RENDER_BUDGET.maxNodes),
    maxRenderBytes: z.natural().min(4096).max(16 * 1024 * 1024).default(DEFAULT_RENDER_BUDGET.maxBytes),
    localeTags: z.array(z.string()).default(['zh', 'en']),
    rpcAllowlist: z.array(z.object({
        namespace: z.string().required(),
        method: z.string().required(),
    })).default([]),
    maxRpcResultBytes: z.natural().min(1024).max(16 * 1024 * 1024).default(1024 * 1024),
    rpcTimeoutMs: z.natural().min(100).max(600_000).default(30_000),
    requireHostSession: z.boolean().default(true),
});
/** The Host release label this plugin reports on the manifest. */
function hostVersion() {
    try {
        const require = createRequire(import.meta.url);
        const manifest = require('@deepseek-ai/dsh-client-modules/package.json');
        return typeof manifest.version === 'string' ? manifest.version : null;
    }
    catch {
        // A composition without the client-modules package publishes no release
        // label; the manifest says so instead of inventing one.
        return null;
    }
}
/**
 * The trust fence: ask the composition's own connection service, exactly as the
 * Host's other browser-facing routes do.
 * @param ctx - the plugin context.
 * @param request - the incoming request.
 * @returns the HTTP status to refuse with, or undefined to admit.
 */
function admissionOf(ctx, request) {
    const connection = ctx.get('connection');
    if (connection === undefined || typeof connection.requestRejection !== 'function') {
        // Without the connection service there is no session to check. The bridge
        // still serves, because a loopback-only Host with no browser auth has no
        // session to require; the profile opts into the stricter behavior by
        // composing the connection service.
        return undefined;
    }
    return connection.requestRejection({ headers: request.headers });
}
/**
 * Absolute path of the Host installation's own `node_modules`.
 *
 * The official roster lives beside the DSH installation rather than in the
 * profile, so this is the one directory whose `@deepseek-ai/*` packages describe
 * what this Host actually ships.
 * @returns the directory, or undefined when the installation cannot be located.
 */
function installModulesPath() {
    return installModulesPaths()[0];
}
/**
 * Candidate `node_modules` directories of the running Host installation.
 *
 * The bridge itself lives in the profile, so resolving `@deepseek-ai/dsh` from here
 * fails on a Host that installed DSH globally — which is exactly how the shipped
 * rootfs is laid out. The running entry point still names the installation, so that
 * is tried as well; both answers are returned so a caller can scan whichever exist.
 * @returns absolute directories, most authoritative first.
 */
function installModulesPaths() {
    const roots = [];
    try {
        const require = createRequire(import.meta.url);
        roots.push(join(dirname(require.resolve('@deepseek-ai/dsh/package.json')), 'node_modules'));
    }
    catch {
        // The profile does not depend on the Host; the entry point below answers instead.
    }
    // `<prefix>/lib/node_modules/@deepseek-ai/dsh/lib/bin.js` -> `<prefix>/lib/node_modules/@deepseek-ai/dsh/node_modules`
    const entry = process.argv[1];
    const marker = '/@deepseek-ai/dsh/';
    if (entry !== undefined) {
        const at = entry.lastIndexOf(marker);
        if (at >= 0)
            roots.push(join(entry.slice(0, at), '@deepseek-ai', 'dsh', 'node_modules'));
    }
    // Third-party plugins installed into a profile live under that profile's
    // node_modules. The Host serves a prebuilt web client (no clientModules
    // registry), so scanning these roots is the only way the phone can see the
    // slots an installed plugin registers.
    //
    // The path is added without an `existsSync` gate on purpose: inside proot a
    // `stat` on a translated path can fail where the same directory is plainly
    // readable through `readdir`, and the scanner already treats an absent root as
    // "no bundles" — so a false negative here would silently hide every
    // third-party plugin's UI, while a false positive costs one failed readdir.
    const home = process.env.DSH_HOME;
    profileRootsNote = home === undefined ? 'DSH_HOME is not set' : `DSH_HOME=${home}`;
    if (home !== undefined) {
        const profilesDir = join(home, 'profiles');
        try {
            const names = readdirSync(profilesDir);
            for (const name of names)
                roots.push(join(profilesDir, name, 'node_modules'));
            profileRootsNote += ` → profiles=[${names.join(',')}] roots=${roots.length}`;
        }
        catch (error) {
            // A profiles directory that cannot be read is reported, not silently
            // skipped: without these roots a third-party plugin's UI is invisible,
            // and "nothing registered" would look like "nothing installed".
            profileRootsNote += ` → scan failed: ${error.message}`;
        }
    }
    return uniquePaths(roots);
}
/**
 * Absolute paths with path-spelling duplicates removed (directory identity, not text).
 *
 * The same `node_modules` is reachable through several spellings on a phone Host — a
 * `require.resolve` answer, the running entry point, and a profile's own symlink — and
 * scanning it twice would discover every client half twice. Realpath is the identity
 * key; a path that cannot be resolved keeps its literal spelling, because the scanners
 * already treat an unreadable root as "no bundles" and dropping it would hide a UI.
 * @param paths absolute directories, authoritative first.
 * @returns the distinct directories, in the same order.
 */
function uniquePaths(paths) {
    const seen = new Set();
    const unique = [];
    for (const path of paths) {
        let key = path;
        try {
            key = realpathSync(path);
        }
        catch {
            // Keep the literal path; resolution failing says nothing about readability.
        }
        if (seen.has(key))
            continue;
        seen.add(key);
        unique.push(path);
    }
    return unique;
}
/** Why the profile roots look the way they do; surfaced as an assembly warning. */
let profileRootsNote;
/**
 * Assemble the provider table from whatever the composition provides.
 * @param ctx - the plugin context.
 * @param config - resolved config.
 * @returns the registry plus the notes describing what could not bind.
 */
function assembleProviders(ctx, config, events, mobileUi) {
    const registry = new ProviderRegistry();
    const warnings = [];
    const clientModules = () => ctx.get('clientModules');
    registry.add(createClientAssetsProvider({
        maxModuleBytes: config.maxModuleBytes,
        localeTags: config.localeTags,
        clientModules,
        loaderRows: () => {
            const loader = ctx.get('loader');
            const rows = [];
            if (loader?.entries !== undefined) {
                for (const entry of loader.entries()) {
                    const name = entry?.options?.name;
                    if (typeof name === 'string')
                        rows.push(name);
                }
            }
            return rows;
        },
    }));
    // A Host that serves a prebuilt web client only exposes slot declarations through
    // the client halves on disk; saying so is better than silently serving nothing.
    const roots = installModulesPaths();
    if (roots.length === 0) {
        warnings.push('client bundles: the Host installation could not be located on disk');
    }
    if (profileRootsNote !== undefined)
        warnings.push(profileRootsNote);
    registry.add(createClientSlotsProvider({
        clientModules,
        entryIds: config.slotEntries,
        renderBudget: {
            maxDepth: config.maxRenderDepth,
            maxNodes: config.maxRenderNodes,
            maxBytes: config.maxRenderBytes,
        },
        maxIconSize: config.maxIconSize,
        iconViewBox: config.iconViewBox,
        installModules: () => installModulesPath(),
        // A prebuilt web client composes no boot graph, so the client halves on disk are
        // the only place its real slot declarations can be read from.
        // Text is read for static extraction; execution stays behind its own switch.
        clientRoots: () => roots,
        // Evaluating client bundles runs plugin code inside the Host. On a phone Host
        // that path has crashed the process for an official bundle, so execution stays
        // off: the static transpiler reads the same declarations from bundle text.
        executeBundles: false,
        localeTags: config.localeTags,
        // A native control press runs the action the plugin's Host half registered;
        // without a registration the event still reaches the plugin bus.
        runAction: (componentId, actionId, payload) => mobileUi.runAction(componentId, actionId, payload),
        // `dispatch` re-broadcasts a native UI event on the Host bus and queues a
        // refresh so the phone re-pulls the tree once the plugin has reacted.
        notify: (kind, text) => { events.push(kind, text); },
        emit: (event, payload) => {
            try {
                ;
                ctx.emit?.(event, payload);
            }
            catch {
                // A composition without an event bus simply never notifies the plugin.
            }
        },
    }));
    registry.add(createHostSlotsProvider({
        settings: () => ctx.get('settings'),
        pluginInventory: () => ctx.get('pluginInventory'),
        pluginManager: () => ctx.get('pluginManager'),
        localeTags: config.localeTags,
    }));
    registry.add(createPackageInventoryProvider({
        pluginManager: () => ctx.get('pluginManager'),
        pluginInventory: () => ctx.get('pluginInventory'),
        localeTags: config.localeTags,
    }));
    // The phone renders the same generated form the browser does: the Host's
    // settings service projects each plugin's Config schema into a field tree.
    registry.add(createSettingsProvider({
        settings: () => ctx.get('settings'),
    }));
    registry.add(createEventsProvider({ drain: () => events.drain() }));
    // The native-companion catalogue: which installed plugins ship a Kotlin/Compose
    // half, and what that half declares. The APK itself ships through the store;
    // this only answers the App's "is there a native half and is it compatible".
    registry.add(createNativePluginsProvider({
        companions: {
            list: () => collectNativeCompanions(ctx),
        },
    }));
    const allowlist = config.rpcAllowlist.map(entry => ({
        namespace: entry.namespace,
        method: entry.method,
    }));
    registry.add(createRpcProvider({
        gateway: () => ctx.get('typertGateway'),
        allowlist,
        maxResultBytes: config.maxRpcResultBytes,
        timeoutMs: config.rpcTimeoutMs,
        // The bridge speaks for the owner principal: the carrier already admitted
        // this caller through the Host's own fence.
        peer: ctx.get('connection')?.browserPeer,
    }));
    if (allowlist.length === 0) {
        warnings.push('rpc provider is registered with an empty allowlist; every invoke is refused');
    }
    if (clientModules() === undefined) {
        warnings.push('no client module registry: client-assets and client-slots report provider/unavailable');
    }
    if (ctx.get('pluginManager') === undefined) {
        warnings.push('no plugin manager: package-inventory reports provider/unavailable');
    }
    return { registry, warnings };
}
/**
 * Collect every native-companion declaration the installed plugins make.
 *
 * A JS plugin declares its Kotlin/Compose half in its `package.json` under
 * `dsh.native` (`{ pluginId, name, apiVersion, minAppVersion, capabilities,
 * storeUrl }`). The bridge reads those declarations off the plugin manager's
 * bundle records — never from the filesystem — so a plugin that is installed but
 * disabled, or present but not yet mounted, still reports its native half.
 *
 * @param ctx - Host plugin context.
 * @returns the declarations; empty when no plugin ships a native half or the
 *   plugin manager is unavailable.
 */
function collectNativeCompanions(ctx) {
    const manager = ctx.get('pluginManager');
    const bundles = manager?.listBundles?.();
    if (!Array.isArray(bundles))
        return [];
    const out = [];
    for (const raw of bundles) {
        const meta = raw?.meta;
        if (typeof meta !== 'object' || meta === null)
            continue;
        const native = meta['native'] ?? meta['dsh']?.['native'];
        if (typeof native !== 'object' || native === null)
            continue;
        const decl = native;
        const pluginId = typeof decl['pluginId'] === 'string' ? decl['pluginId'] : raw.name;
        if (typeof pluginId !== 'string' || pluginId.length === 0)
            continue;
        out.push({
            pluginId,
            name: typeof decl['name'] === 'string' ? decl['name'] : undefined,
            apiVersion: typeof decl['apiVersion'] === 'number' ? decl['apiVersion'] : undefined,
            minAppVersion: typeof decl['minAppVersion'] === 'number' ? decl['minAppVersion'] : undefined,
            capabilities: Array.isArray(decl['capabilities'])
                ? decl['capabilities'].filter((c) => typeof c === 'string')
                : undefined,
            storeUrl: typeof decl['storeUrl'] === 'string' ? decl['storeUrl'] : undefined,
        });
    }
    return out;
}
/** Largest accepted event body; a toast is one short line. */
const MAX_EVENT_BODY_BYTES = 4 * 1024;
/** Longest accepted toast text. */
const MAX_EVENT_TEXT_CHARS = 500;
/**
 * Register the bridge routes.
 * @param ctx - Host plugin context.
 * @param config - resolved plugin config.
 */
export function apply(ctx, config) {
    if (!config.enabled)
        return;
    const webServer = webServerOf(ctx);
    if (webServer === undefined) {
        // The route carrier is a declared injection, so the Loader waits for it;
        // reaching this point means the composition unloaded the carrier while the
        // bridge stayed mounted. Reporting it is the only honest option.
        ctx.logger.warn('dsh-mobile-bridge: no web server; the bridge is not serving');
        return;
    }
    const events = createEventQueue();
    const mobileUi = new MobileUiService();
    // Publish the service so a plugin Host half can register what its native
    // controls do (`ctx.get('mobileUi')`). A composition without a reflection
    // layer (tests, minimal hosts) keeps the registry private.
    if (typeof ctx.reflect?.provide === "function") {
        ctx.effect(() => ctx.reflect.provide("mobileUi", mobileUi), "dsh-mobile-bridge: mobileUi service");
    }
    const { registry, warnings } = assembleProviders(ctx, config, events, mobileUi);
    const handler = createBridgeHandler({
        registry,
        pluginVersion: packageVersion,
        dshVersion: hostVersion,
        assemblyWarnings: warnings,
        ...(config.requireHostSession
            ? { admit: (request) => admissionOf(ctx, request) }
            : {}),
    });
    const serve = (req, res) => {
        handler(req)
            .then(reply => { writeReply(res, reply); })
            .catch((error) => {
            ctx.logger.error(error);
            res.statusCode = 500;
            res.setHeader('content-type', 'application/json; charset=utf-8');
            res.end(JSON.stringify({ ok: false, error: { code: 'internal/error', message: 'bridge handler failed' } }));
        });
    };
    ctx.effect(() => webServer.register({
        kind: 'exact',
        path: MANIFEST_PATH,
        handler: serve,
    }), `dsh-mobile-bridge: GET ${MANIFEST_PATH}`);
    ctx.effect(() => webServer.register({
        kind: 'exact',
        path: QUERY_PATH,
        handler: serve,
    }), `dsh-mobile-bridge: POST ${QUERY_PATH}`);
    // Transient messages: a plugin POSTs one and the phone shows it with its own toast.
    // This is the only route that accepts plugin-authored payloads, so it stays
    // deliberately narrow — one line of text, nothing else.
    ctx.effect(() => webServer.register({
        kind: 'exact',
        path: EVENTS_PATH,
        handler: (req, res) => {
            if (req.method !== 'POST') {
                res.statusCode = 405;
                res.end();
                return;
            }
            const chunks = [];
            let size = 0;
            req.on('data', (chunk) => {
                size += chunk.length;
                // A toast is one line; anything larger is not this channel's job.
                if (size > MAX_EVENT_BODY_BYTES) {
                    res.statusCode = 413;
                    res.end();
                    req.destroy();
                    return;
                }
                chunks.push(chunk);
            });
            req.on('end', () => {
                if (res.writableEnded)
                    return;
                try {
                    const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
                    if (body.kind !== 'toast' || typeof body.text !== 'string' || body.text.trim() === '') {
                        res.statusCode = 400;
                        res.setHeader('content-type', 'application/json; charset=utf-8');
                        res.end(JSON.stringify({ ok: false, error: { code: 'args/invalid', message: 'kind must be toast and text must be a non-empty string' } }));
                        return;
                    }
                    events.push('toast', body.text.slice(0, MAX_EVENT_TEXT_CHARS));
                    res.statusCode = 200;
                    res.setHeader('content-type', 'application/json; charset=utf-8');
                    res.end(JSON.stringify({ ok: true }));
                }
                catch {
                    res.statusCode = 400;
                    res.setHeader('content-type', 'application/json; charset=utf-8');
                    res.end(JSON.stringify({ ok: false, error: { code: 'request/malformed', message: 'body must be JSON' } }));
                }
            });
        },
    }), `dsh-mobile-bridge: POST ${EVENTS_PATH}`);
    // A prefix registration keeps the base path discoverable to a client that
    // probes it, and answers the one useful thing a bare base can: where to go.
    ctx.effect(() => webServer.register({
        kind: 'prefix',
        path: BRIDGE_BASE_PATH,
        handler: (req, res) => {
            if (req.url === BRIDGE_BASE_PATH || req.url === `${BRIDGE_BASE_PATH}/`) {
                writeReply(res, {
                    status: 200,
                    body: { bridgeVersion: BRIDGE_VERSION, manifest: MANIFEST_PATH, query: QUERY_PATH },
                });
                return;
            }
            serve(req, res);
        },
    }), `dsh-mobile-bridge: ${BRIDGE_BASE_PATH}`);
    ctx.logger.info(`dsh-mobile-bridge: serving ${BRIDGE_BASE_PATH} with ${registry.list().length} providers`);
}
/** Re-export the protocol surface for a standalone carrier and for tests. */
export * from "./protocol/types.js";
export { ProviderRegistry, BridgeFailure } from "./protocol/provider.js";
export { createBridgeHandler, writeReply } from "./handler.js";
export { evaluateBundleSource } from "./slots/evaluate.js";
export { createReactStub } from "./slots/react-stub.js";
export { renderComponent, treeToSvg } from "./slots/render-tree.js";
export { bundleDictionaries, chooseBundleDictionary, resolveDictionaryKey, decodeEscapes } from "./slots/bundle-locale.js";
/** Read this package's own version, for a standalone carrier's manifest. */
export function packageVersion() {
    const require = createRequire(import.meta.url);
    const manifest = require('../package.json');
    return typeof manifest.version === 'string' ? manifest.version : '0.0.0';
}
