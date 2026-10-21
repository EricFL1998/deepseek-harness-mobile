/**
 * The `kimi` provider route's wiring: credential store, device-flow login,
 * token refresh, model catalog, and its Settings card facts.
 *
 * @module dsh-plugin-subscription-oauth/providers/kimi-provider
 */
import { REAUTH_REQUIRED, ProviderError } from '../errors.js';
import { KIMI_CODE_URL, KIMI_CONTEXT_WINDOW, KIMI_DEFAULT_MODEL, KIMI_PROVIDER_ID, KIMI_PROVIDER_NAME } from '../constants.js';
import { TokenStore } from '../auth/store.js';
import { TokenManager } from '../auth/tokens.js';
import { DeviceFlowManager, identityFromTokens, refreshKimiToken } from '../auth/device-flow.js';
import { loadOrCreateDeviceId } from '../auth/device-id.js';
import { kimiIdentityHeaders } from '../auth/headers.js';
import { KimiAdapter, parseKimiSession } from './kimi.js';
import { readKimiUsage } from './usage.js';
import { fastWordOf } from './grok.js';
import { ProviderController } from '../auth/provider-controller.js';
import { proxiedFetch } from '../http.js';
/**
 * Build the `kimi` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved endpoints, paths, and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export function createKimiProvider(options) {
    const fetchFn = options.fetchFn ?? proxiedFetch;
    const store = new TokenStore(options.credentialFile, KIMI_PROVIDER_ID, parseKimiSession);
    const deviceFlow = new DeviceFlowManager();
    /** The wrapped in-flight attempt, so `pending()` is stable across calls. */
    let current;
    const toSession = (tokens) => ({
        accessToken: tokens.accessToken,
        ...tokens.refreshToken === undefined ? {} : { refreshToken: tokens.refreshToken },
        expiresAt: tokens.expiresAt,
        oauthHost: options.oauthHost,
        apiBase: options.apiBase,
        ...identityFromTokens(tokens.accessToken, tokens.refreshToken),
    });
    /** Adapt one device attempt onto the shared login contract. */
    const wrap = (attempt) => ({
        prompt: {
            url: attempt.verificationUrl,
            userCode: attempt.userCode,
            expiresAt: attempt.expiresAt,
            mode: 'device-code',
        },
        session: attempt.waitToken().then(toSession),
        cancel: () => { attempt.cancel(); },
    });
    const flow = {
        async start() {
            const attempt = await deviceFlow.start(KIMI_PROVIDER_ID, {
                host: options.oauthHost,
                deviceId: await loadOrCreateDeviceId(options.dataDir),
                fetchFn,
            });
            const wrapped = wrap(attempt);
            current = wrapped;
            // The manager drops its own record once the attempt settles; mirror that
            // so `pending()` cannot report a finished login.
            void attempt.waitToken().catch(() => undefined).finally(() => {
                if (current === wrapped)
                    current = undefined;
            });
            return wrapped;
        },
        pending() {
            return deviceFlow.pending(KIMI_PROVIDER_ID) === undefined ? undefined : current;
        },
    };
    const tokens = new TokenManager({
        store,
        displayName: KIMI_PROVIDER_NAME,
        refresh: async (session) => {
            const refreshToken = session.refreshToken;
            if (refreshToken === undefined) {
                throw new ProviderError(`${KIMI_PROVIDER_NAME} account has no refresh token; sign in again`, REAUTH_REQUIRED);
            }
            return refreshKimiToken({
                host: session.oauthHost,
                deviceId: await loadOrCreateDeviceId(options.dataDir),
                fetchFn,
            }, refreshToken);
        },
    });
    const adapter = new KimiAdapter({
        apiBase: options.apiBase,
        discovery: options.discovery,
        streamIdleTimeoutMs: options.streamIdleTimeoutMs,
        tokens,
        ...options.onWarn === undefined ? {} : { onWarn: options.onWarn },
        ...options.resolveAttachments === undefined ? {} : { resolveAttachments: options.resolveAttachments },
        identityHeaders: async () => kimiIdentityHeaders(await loadOrCreateDeviceId(options.dataDir)),
        readUsage: async (account) => {
            const token = await tokens.resolve(account);
            const read = await readKimiUsage({ apiBase: options.apiBase, accessToken: token.accessToken, fetchFn });
            return read === undefined ? [] : read.windows;
        },
        hiddenModels: () => options.preferences.hiddenIds(KIMI_PROVIDER_ID),
        userModels: () => options.preferences.extraIds(KIMI_PROVIDER_ID),
        modelOrder: () => options.preferences.orderOf(KIMI_PROVIDER_ID),
        defaultModel: options.defaultModel ?? KIMI_DEFAULT_MODEL,
        ...options.cachedListing === undefined ? {} : { cachedListing: options.cachedListing },
        ...options.saveListing === undefined ? {} : { saveListing: options.saveListing },
        ...options.extraModels === undefined ? {} : { extraModels: options.extraModels },
        fetchFn,
    });
    // Read the cached table now rather than at the first picker query: the Settings page
    // reads the catalog synchronously, so a request-free start must already have it.
    void adapter.hydrate().catch(() => undefined);
    const spec = { id: KIMI_PROVIDER_ID,
        displayName: KIMI_PROVIDER_NAME,
        codeUrl: KIMI_CODE_URL,
        credentialFile: options.credentialFile,
        discovery: options.discovery,
        defaultModel: options.defaultModel ?? KIMI_DEFAULT_MODEL,
        catalog: () => adapter.catalogEntries(),
        catalogOwn: () => adapter.ownCatalogEntries(),
        fallbackContextWindow: KIMI_CONTEXT_WINDOW,
        discover: (accessToken) => adapter.fetchCatalog(accessToken),
        usage: (accessToken) => readKimiUsage({ apiBase: options.apiBase, accessToken, fetchFn }),
        details: () => [
            { key: 'oauthHost', value: options.oauthHost },
            { key: 'apiBase', value: options.apiBase },
            { key: 'credentialFile', value: options.credentialFile },
        ],
        applyDiscovery: (ids) => { adapter.discover(ids); },
        fastVariant: (baseId) => adapter.fastVariant(baseId),
    };
    const controller = new ProviderController({
        spec,
        store,
        tokens,
        flow,
        preferences: options.preferences,
        ...options.onCatalogChanged === undefined ? {} : { onCatalogChanged: options.onCatalogChanged },
        ...options.onWarn === undefined ? {} : { onWarn: options.onWarn },
    });
    // An explicit façade, not a spread: a spread would copy own properties and
    // drop the class prototype, leaving every method undefined.
    return {
        id: controller.id,
        displayName: controller.displayName,
        credentialFile: controller.credentialFile,
        adapter,
        status: () => controller.status(),
        login: (account) => controller.login(account),
        cancel: () => controller.cancel(),
        logout: (account) => controller.logout(account),
        setDefault: (account) => controller.setDefault(account),
        refresh: (account) => controller.refresh(account),
        discover: (force) => controller.discover(force),
        setModelVisible: (id, visible) => controller.setModelVisible(id, visible),
        setModelOrder: (ids) => controller.setModelOrder(ids),
        addModel: (id) => controller.addModel(id),
        removeModel: (id) => controller.removeModel(id),
        usage: (force) => controller.usage(force),
        reload: () => controller.reload(),
        modelVariants: async (id) => {
            // A variant is known only once the route has read its listing, whose row names it.
            // The composer asks exactly once, so the read is awaited here: "no fast variant"
            // must mean the route really has none, not that its table was still loading.
            await adapter.hydrate();
            const pair = adapter.fastPairOf(id);
            const image = adapter.imageModelId();
            return {
                ...pair === undefined ? {} : { fast: pair.wireId, fastBase: pair.baseId, fastRung: fastWordOf(pair.wireId) },
                ...image === undefined ? {} : { image },
                // Nothing read yet: the answer above is provisional, and the caller is told so
                // rather than being left to read silence as "this model has no variant".
                ...adapter.hasListing() ? {} : { pending: true },
            };
        },
    };
}
