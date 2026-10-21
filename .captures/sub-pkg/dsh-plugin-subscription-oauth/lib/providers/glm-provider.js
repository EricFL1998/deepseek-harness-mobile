/**
 * The `glm` provider route's wiring: credential store, ZCode OAuth login, model
 * catalog, and its Settings card facts.
 *
 * @module dsh-plugin-subscription-oauth/providers/glm-provider
 */
import { REAUTH_REQUIRED, ProviderError } from '../errors.js';
import { TokenStore } from '../auth/store.js';
import { TokenManager } from '../auth/tokens.js';
import { LOGIN_CANCELLED_MESSAGE } from '../auth/device-flow.js';
import { startGlmFlow, pollGlmFlow } from '../auth/glm-flow.js';
import { GlmAdapter, GLM_API_BASE, GLM_CODE_URL, GLM_CONTEXT_WINDOW, GLM_DEFAULT_MODEL, GLM_ORIGIN, GLM_PROVIDER_ID, GLM_PROVIDER_NAME, parseGlmSession, readGlmUsage } from './glm.js';
import { ProviderController } from '../auth/provider-controller.js';
import { proxiedFetch } from '../http.js';
/** Wait, honouring cancellation. */
function sleep(ms, signal) {
    return new Promise((resolve, reject) => {
        if (signal.aborted) {
            reject(new Error(LOGIN_CANCELLED_MESSAGE));
            return;
        }
        const timer = setTimeout(() => {
            signal.removeEventListener('abort', onAbort);
            resolve();
        }, ms);
        function onAbort() {
            clearTimeout(timer);
            reject(new Error(LOGIN_CANCELLED_MESSAGE));
        }
        signal.addEventListener('abort', onAbort, { once: true });
    });
}
/**
 * Build the `glm` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved endpoints, paths, and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export function createGlmProvider(options) {
    const fetchFn = options.fetchFn ?? proxiedFetch;
    const apiBase = options.apiBase ?? GLM_API_BASE;
    const store = new TokenStore(options.credentialFile, GLM_PROVIDER_ID, parseGlmSession);
    const toSession = (tokens) => ({
        accessToken: tokens.accessToken,
        zcodeJwt: tokens.zcodeJwt,
        expiresAt: tokens.expiresAt,
        ...tokens.refreshToken === undefined ? {} : { refreshToken: tokens.refreshToken },
        apiBase,
        ...tokens.accountId === undefined ? {} : { accountId: tokens.accountId },
        ...tokens.label === undefined ? {} : { email: tokens.label },
    });
    /** The in-flight attempt, so `pending()` is stable across calls. */
    let current;
    const flow = {
        async start() {
            const start = await startGlmFlow({ fetchFn });
            const controller = new AbortController();
            const session = (async () => {
                // The browser sign-in completes on the broker's own callback, so this side
                // only has to keep asking until the flow is ready or lapses.
                while (true) {
                    if (controller.signal.aborted)
                        throw new Error(LOGIN_CANCELLED_MESSAGE);
                    const tokens = await pollGlmFlow({ fetchFn }, start, controller.signal);
                    if (tokens !== undefined)
                        return toSession(tokens);
                    await sleep(start.pollIntervalMs, controller.signal);
                }
            })();
            const attempt = {
                prompt: {
                    url: start.authorizeUrl,
                    expiresAt: start.expiresAt,
                    mode: 'open-url',
                },
                session,
                cancel: () => { controller.abort(); },
            };
            current = attempt;
            // Drop the record once the attempt settles, so `pending()` cannot report a
            // login that already finished or failed.
            void session.catch(() => undefined).finally(() => {
                if (current === attempt)
                    current = undefined;
            });
            return attempt;
        },
        pending() {
            return current;
        },
    };
    const tokens = new TokenManager({
        store,
        displayName: GLM_PROVIDER_NAME,
        // The ZCode flow has no refresh grant, so there is nothing to pre-empt: asking
        // five minutes early would refuse a token that is still perfectly usable and
        // send the user to sign in again for no reason. Renewal happens when the token
        // has actually lapsed, which is also what the official client does.
        preemptMs: 0,
        refresh: () => {
            throw new ProviderError(`${GLM_PROVIDER_NAME} sign-in has expired; connect the account again`, REAUTH_REQUIRED);
        },
    });
    const adapter = new GlmAdapter({
        apiBase,
        discovery: options.discovery,
        streamIdleTimeoutMs: options.streamIdleTimeoutMs,
        tokens,
        ...options.onWarn === undefined ? {} : { onWarn: options.onWarn },
        ...options.resolveAttachments === undefined ? {} : { resolveAttachments: options.resolveAttachments },
        hiddenModels: () => options.preferences.hiddenIds(GLM_PROVIDER_ID),
        userModels: () => options.preferences.extraIds(GLM_PROVIDER_ID),
        readUsage: async (account) => {
            const token = await tokens.resolve(account);
            const read = await readGlmUsage({ origin: GLM_ORIGIN, accessToken: token.accessToken, fetchFn });
            return read.windows;
        },
        ...options.extraModels === undefined ? {} : { extraModels: options.extraModels },
        ...options.defaultModel === undefined ? {} : { defaultModel: options.defaultModel },
        ...options.cachedListing === undefined ? {} : { cachedListing: options.cachedListing },
        ...options.saveListing === undefined ? {} : { saveListing: options.saveListing },
        fetchFn,
    });
    // Read the cached table now rather than at the first picker query: the Settings page
    // reads the catalog synchronously, so a request-free start must already have it.
    void adapter.hydrate().catch(() => undefined);
    const spec = {
        id: GLM_PROVIDER_ID,
        displayName: GLM_PROVIDER_NAME,
        codeUrl: GLM_CODE_URL,
        credentialFile: options.credentialFile,
        discovery: options.discovery,
        defaultModel: options.defaultModel ?? GLM_DEFAULT_MODEL,
        catalog: () => adapter.catalogEntries(),
        catalogOwn: () => adapter.ownCatalogEntries(),
        fallbackContextWindow: GLM_CONTEXT_WINDOW,
        discover: (accessToken) => adapter.fetchCatalog(accessToken),
        usage: (accessToken) => readGlmUsage({ origin: GLM_ORIGIN, accessToken, fetchFn }),
        details: () => [
            { key: 'apiBase', value: apiBase },
            { key: 'oauthBroker', value: 'https://zcode.z.ai' },
            { key: 'credentialFile', value: options.credentialFile },
        ],
        applyDiscovery: (ids) => { adapter.discover(ids); },
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
    // An explicit façade, not a spread: a spread would copy own properties and drop
    // the class prototype, leaving every method undefined.
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
            // The image model is a row of the listing, so the read is awaited before answering:
            // the composer asks once, and "no image model" must mean there is none.
            void id;
            await adapter.hydrate();
            const image = adapter.imageModelId();
            return {
                ...image === undefined ? {} : { image },
                // Nothing read yet: the answer above is provisional, and the caller is told so.
                ...adapter.hasListing() ? {} : { pending: true },
            };
        },
    };
}
