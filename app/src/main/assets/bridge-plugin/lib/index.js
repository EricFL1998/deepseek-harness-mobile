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
import { createRequire } from 'node:module';
import z from '@deepseek-ai/schemastery';
import { webServerOf } from "./host-services.js";
import { createBridgeHandler, writeReply } from "./handler.js";
import { ProviderRegistry } from "./protocol/provider.js";
import { BRIDGE_BASE_PATH, BRIDGE_VERSION, MANIFEST_PATH, QUERY_PATH } from "./protocol/types.js";
import { createClientAssetsProvider, } from "./providers/client-assets.js";
import { createClientSlotsProvider, } from "./providers/client-slots.js";
import { createPackageInventoryProvider, } from "./providers/package-inventory.js";
import { createRpcProvider } from "./providers/rpc.js";
import { DEFAULT_RENDER_BUDGET } from "./slots/render-tree.js";
/** Cordis function-plugin name. */
export const name = 'dsh-mobile-bridge-plugin';
/** The routes need the Host's HTTP carrier; every other service is optional. */
export const inject = ['webServer'];
/** Config schema; `schemastery` applies these defaults through the Loader. */
export const Config = z.object({
    enabled: z.boolean().default(true),
    slotEntries: z.array(z.string()).default([]),
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
 * Assemble the provider table from whatever the composition provides.
 * @param ctx - the plugin context.
 * @param config - resolved config.
 * @returns the registry plus the notes describing what could not bind.
 */
function assembleProviders(ctx, config) {
    const registry = new ProviderRegistry();
    const warnings = [];
    const clientModules = () => ctx.get('clientModules');
    registry.add(createClientAssetsProvider({
        maxModuleBytes: config.maxModuleBytes,
        localeTags: config.localeTags,
        clientModules,
    }));
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
    }));
    registry.add(createPackageInventoryProvider({
        pluginManager: () => ctx.get('pluginManager'),
        pluginInventory: () => ctx.get('pluginInventory'),
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
    const { registry, warnings } = assembleProviders(ctx, config);
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
/** Read this package's own version, for a standalone carrier's manifest. */
export function packageVersion() {
    const require = createRequire(import.meta.url);
    const manifest = require('../package.json');
    return typeof manifest.version === 'string' ? manifest.version : '0.0.0';
}
