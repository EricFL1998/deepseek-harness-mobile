/**
 * Kimi Code, Grok (X Premium) and Google Antigravity subscription access for
 * DeepSeek Harness.
 *
 * Each provider route authorizes once through its own OAuth flow (Settings →
 * Subscriptions) and then streams over the wire that provider's subscription
 * exposes. Tokens live below `~/.dsh/plugins/kimi-oauth/`, one provider section
 * per route.
 *
 * @module dsh-plugin-subscription-oauth
 */
import path from 'node:path';
import { dshHomePath } from '@deepseek-ai/dsh-home-paths';
import z from '@deepseek-ai/schemastery';
import { registerSubscriptionRpc } from './auth/rpc.js';
import { SubscriptionRegistry } from './auth/registry.js';
import { KIMI_DATA_DIR, KIMI_DEFAULT_MODEL, KIMI_STREAM_IDLE_TIMEOUT_MS, normalizeApiBase, normalizeOAuthHost, resolveApiBase, resolveOAuthHost, } from './constants.js';
import { proxiedFetch } from './http.js';
import { SETTINGS_NS } from './protocol.js';
import { AUTO_PROVIDER_ID, createAutoProvider } from './providers/auto.js';
import { createAntigravityProvider } from './providers/antigravity.js';
import { createGrokProvider } from './providers/grok.js';
import { createKimiProvider } from './providers/kimi-provider.js';
import { createGlmProvider } from './providers/glm-provider.js';
import { createArkProvider } from './providers/ark-provider.js';
import { createMimoProvider } from './providers/mimo-provider.js';
import { createWorkBuddyProvider } from './providers/workbuddy-provider.js';
import { ModelPreferences } from './settings.js';
import { ModelCatalogCache } from './providers/model-cache.js';
/** Plugin id; also the name every effect label and log line is prefixed with. */
export const name = 'dsh-plugin-subscription-oauth';
/** The provider routes need the LLM runtime; everything else is optional. */
export const inject = ['llm'];
/** Every route key this plugin accepts in configuration. */
export const PROVIDER_IDS = ['kimi', 'grok', 'antigravity', 'glm', 'ark', 'mimo', 'workbuddy'];
/** The routes this build can actually register. */
export const IMPLEMENTED_PROVIDER_IDS = ['kimi', 'grok', 'antigravity', 'glm', 'ark', 'mimo', 'workbuddy'];
/** Configuration schema, with the config file's own defaults applied. */
export const Config = z.object({
    providers: z.array(z.union([...PROVIDER_IDS])).default([...IMPLEMENTED_PROVIDER_IDS]),
    enabled: z.boolean().default(true),
    oauthHost: z.string(),
    apiBase: z.string(),
    discovery: z.boolean().default(true),
    streamIdleTimeoutMs: z.number().default(KIMI_STREAM_IDLE_TIMEOUT_MS),
    defaultModel: z.string().default(KIMI_DEFAULT_MODEL),
    extraModels: z.array(z.string()).default([]),
    auto: z.boolean().default(true),
    balanceSources: z.dict(z.object({ credentialRef: z.string(), baseUrl: z.string() })).default({}),
    balanceCredentialRefs: z.dict(z.string()).default({}),
});
/**
 * Register every configured provider route, its credential store, and — when
 * the host has a connection service — the Settings page's RPC routes.
 *
 * @param ctx - the plugin's context.
 * @param config - validated plugin configuration.
 */
export function apply(ctx, config = {}) {
    if (config.enabled === false)
        return;
    const streamIdleTimeoutMs = config.streamIdleTimeoutMs ?? KIMI_STREAM_IDLE_TIMEOUT_MS;
    if (!Number.isFinite(streamIdleTimeoutMs) || streamIdleTimeoutMs <= 0) {
        throw new Error(`${name}: streamIdleTimeoutMs must be a positive finite number`);
    }
    const selected = new Set(config.providers ?? [...IMPLEMENTED_PROVIDER_IDS]);
    const onWarn = (message) => {
        ctx.logger.warn(`${name}: ${message}`);
    };
    const dataDir = dshHomePath(...KIMI_DATA_DIR);
    const credentialFile = path.join(dataDir, 'auth.json');
    const preferences = new ModelPreferences(path.join(dataDir, 'preferences.json'));
    // The endpoint's own last answer per route. It is written whenever a route reads its
    // live listing — at startup, after a login, and on the page's "refresh models" action
    // — and read as the route's model table, so a picker query is never a request.
    const catalogCache = new ModelCatalogCache(path.join(dataDir, 'model-catalog.json'));
    /** Registration handles, so a catalog change can re-announce one route. */
    const handles = new Map();
    const reannounce = (id) => { handles.get(id)?.replace([id]); };
    const routes = [];
    for (const id of selected) {
        switch (id) {
            case 'kimi':
                routes.push(createKimiProvider({
                    dataDir,
                    credentialFile,
                    oauthHost: normalizeOAuthHost(config.oauthHost ?? resolveOAuthHost()),
                    apiBase: normalizeApiBase(config.apiBase ?? resolveApiBase()),
                    discovery: config.discovery ?? true,
                    streamIdleTimeoutMs,
                    ...config.defaultModel === undefined ? {} : { defaultModel: config.defaultModel },
                    ...config.extraModels === undefined ? {} : { extraModels: config.extraModels },
                    cachedListing: () => catalogCache.read('kimi'),
                    saveListing: (rows) => catalogCache.write('kimi', rows),
                    preferences,
                    // Resolved per request: the attachment service may start after this plugin.
                    resolveAttachments: () => ctx.get('attachments'),
                    onCatalogChanged: () => { reannounce('kimi'); },
                    onWarn,
                    fetchFn: proxiedFetch,
                }));
                break;
            case 'grok':
                routes.push(createGrokProvider({
                    credentialFile,
                    discovery: config.discovery ?? true,
                    streamIdleTimeoutMs,
                    preferences,
                    cachedListing: () => catalogCache.read('grok'),
                    saveListing: (payload) => catalogCache.write('grok', payload),
                    resolveAttachments: () => ctx.get('attachments'),
                    onCatalogChanged: () => { reannounce('grok'); },
                    onWarn,
                    fetchFn: proxiedFetch,
                }));
                break;
            case 'antigravity':
                routes.push(createAntigravityProvider({
                    credentialFile,
                    discovery: config.discovery ?? true,
                    streamIdleTimeoutMs,
                    preferences,
                    resolveAttachments: () => ctx.get('attachments'),
                    onCatalogChanged: () => { reannounce('antigravity'); },
                    onWarn,
                    cachedListing: () => catalogCache.read('antigravity'),
                    saveListing: (payload) => catalogCache.write('antigravity', payload),
                    fetchFn: proxiedFetch,
                }));
                break;
            case 'glm':
                routes.push(createGlmProvider({
                    credentialFile,
                    discovery: config.discovery ?? true,
                    streamIdleTimeoutMs,
                    preferences,
                    cachedListing: () => catalogCache.read('glm'),
                    saveListing: (payload) => catalogCache.write('glm', payload),
                    resolveAttachments: () => ctx.get('attachments'),
                    onCatalogChanged: () => { reannounce('glm'); },
                    onWarn,
                    fetchFn: proxiedFetch,
                }));
                break;
            case 'ark':
                routes.push(createArkProvider({
                    credentialFile,
                    discovery: config.discovery ?? true,
                    streamIdleTimeoutMs,
                    preferences,
                    cachedListing: () => catalogCache.read('ark'),
                    saveListing: (payload) => catalogCache.write('ark', payload),
                    resolveAttachments: () => ctx.get('attachments'),
                    onCatalogChanged: () => { reannounce('ark'); },
                    onWarn,
                    fetchFn: proxiedFetch,
                }));
                break;
            case 'mimo':
                routes.push(createMimoProvider({
                    credentialFile,
                    discovery: config.discovery ?? true,
                    streamIdleTimeoutMs,
                    preferences,
                    cachedListing: () => catalogCache.read('mimo'),
                    saveListing: (payload) => catalogCache.write('mimo', payload),
                    resolveAttachments: () => ctx.get('attachments'),
                    onCatalogChanged: () => { reannounce('mimo'); },
                    onWarn,
                    fetchFn: proxiedFetch,
                }));
                break;
            case 'workbuddy':
                routes.push(createWorkBuddyProvider({
                    credentialFile,
                    discovery: config.discovery ?? true,
                    streamIdleTimeoutMs,
                    preferences,
                    cachedListing: () => catalogCache.read('workbuddy'),
                    saveListing: (payload) => catalogCache.write('workbuddy', payload),
                    onCatalogChanged: () => { reannounce('workbuddy'); },
                    onWarn,
                    fetchFn: proxiedFetch,
                }));
                break;
            default:
                // A configured route with no module yet must not silently disappear.
                onWarn(`provider ${id} is configured but not implemented in this build`);
                break;
        }
    }
    const registry = new SubscriptionRegistry(routes, preferences);
    // `auto` stands in for the other routes, so it is built after them and added
    // to the same registry it reads. Its list spans routes, which is why it is a
    // provider of its own rather than a model inside each one.
    if (config.auto ?? true) {
        const auto = createAutoProvider({
            preferences,
            registry,
            // Auto spans every provider the harness serves — the built-in DeepSeek
            // routes and any provider the user adds included — so it is enumerated
            // through the runtime rather than through this plugin's own registry. The
            // runtime is read per call, so a provider that registers later (another
            // plugin, a route the user configures) joins the chain without a restart.
            host: {
                providers: () => ctx.llm.listProviders().map((entry) => ({ id: entry.id, name: entry.name })),
                models: (provider) => ctx.llm.listModels(provider),
                resolve: async (provider, model) => {
                    const resolved = await ctx.llm.resolveModelInfo(provider, model);
                    return {
                        ...resolved.context?.contextWindow === undefined ? {} : { contextWindow: resolved.context.contextWindow },
                        ...resolved.reasoning === undefined
                            ? {}
                            : { reasoningEfforts: resolved.reasoning.efforts.map((effort) => String(effort.id)) },
                        ...resolved.inputModalities === undefined ? {} : { inputModalities: resolved.inputModalities },
                    };
                },
                // Dispatch through the harness, which selects the adapter that owns the
                // provider. Auto's own adapter is never asked to guess.
                stream: (options) => ctx.llm.stream(options),
            },
            onCatalogChanged: () => { reannounce(AUTO_PROVIDER_ID); },
            onWarn,
        });
        registry.register(auto);
        routes.push(auto);
    }
    // Registered in the page's reading order, not construction order: the model
    // picker groups by the order the adapters arrive, and Auto leads it.
    for (const id of registry.ids()) {
        const route = registry.get(id);
        if (route === undefined)
            continue;
        const handle = ctx.llm.registerAdapter([route.id], route.adapter);
        handles.set(route.id, handle);
        ctx.effect(() => () => handle(), `${name}: ${route.id} provider route`);
    }
    // Declare the same routes in the configurable-provider directory, so the
    // Models settings page renders one card per route instead of treating every
    // subscription route as foreign. The settings namespace is the Cordis row id
    // that mounted this plugin (the settings service projects a plugin's Config
    // under it), which is also the key the Models page dispatches its
    // provider-card extension slot with — the seat the browser half's Connect
    // block registers into. `settingsPath` is empty because the whole section is
    // this plugin's own configuration: the extension owns the card's content
    // rather than pointing at a nested profile. `auto` is excluded: it is a chain
    // over the other routes, not a sign-in of its own, so it gets no card.
    const settingsNs = SETTINGS_NS;
    const directoryEntries = registry.ids()
        .filter((id) => id !== AUTO_PROVIDER_ID)
        .map((id) => {
        const route = registry.get(id);
        if (route === undefined)
            return undefined;
        return {
            provider: route.id,
            displayName: route.displayName,
            settingsNs,
            settingsPath: [],
            declared: false,
        };
    })
        .filter((entry) => entry !== undefined);
    if (directoryEntries.length > 0) {
        const directory = ctx.llm.registerConfigurableProviders(directoryEntries);
        ctx.effect(() => () => directory(), `${name}: provider directory`);
    }
    // A non-subscription provider's balance is read by cc-switch's query against the
    // provider's own base URL (the balance endpoint lives beside its OpenAI-compatible
    // wire, not on DeepSeek's), which needs the provider's API key. Each configured source
    // names the credential reference the provider's profile uses (`apiKeyEnv`) and the base
    // URL that profile connects to; the host's credential service resolves the reference.
    const balanceSources = config.balanceSources ?? {};
    const credentialRefs = { ...config.balanceCredentialRefs };
    const baseUrls = {};
    for (const [provider, source] of Object.entries(balanceSources)) {
        credentialRefs[provider] = source.credentialRef;
        baseUrls[provider] = source.baseUrl;
    }
    // The DeepSeek account's balance is read on the node half through the host's
    // `deepseekAccount` service; the browser reaches it through the plugin's own RPC, the
    // same channel as the rest of the chip's data. The service is read lazily, per request
    // (not once in apply), because it may register after this plugin applies; a composition
    // without it still serves every other endpoint, and the balance endpoint answers nothing.
    const balanceContext = {
        credentialRefs,
        baseUrls,
        resolveDeepSeekAccount: () => {
            try {
                return ctx.get('deepseekAccount');
            }
            catch {
                return undefined;
            }
        },
        resolveCredential: async (ref) => {
            try {
                const credentials = ctx.get('credentials');
                const hit = await credentials?.resolve?.(ref);
                // The resolve answer is `{ value, source }`; an env-var source may name the var
                // rather than carry the value, so both are considered before giving up.
                if (typeof hit === 'object' && hit !== null) {
                    const record = hit;
                    if (typeof record.value === 'string' && record.value.trim() !== '')
                        return record.value;
                    if (typeof record.source === 'string') {
                        const fromEnv = process.env[record.source];
                        if (typeof fromEnv === 'string' && fromEnv.trim() !== '')
                            return fromEnv;
                    }
                }
                return undefined;
            }
            catch {
                return undefined;
            }
        },
    };
    registerSubscriptionRpc(ctx, registry, balanceContext);
    // Warm each route's live catalog for an install that is already signed in.
    // Discovery uses the stored token as-is (no resolve, no refresh), so a dead grant
    // is skipped silently rather than opening the browser.
    for (const id of registry.ids()) {
        void registry.discover(id, false).catch((error) => {
            onWarn(`${id} startup discovery failed: ${error instanceof Error ? error.message : String(error)}`);
        });
    }
}
