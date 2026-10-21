/**
 * The `mimo` provider route's wiring: credential store, Xiaomi QR/password
 * login, model catalog (MiMo's own listing, read once and cached), and its
 * Settings card facts.
 *
 * @module dsh-plugin-subscription-oauth/providers/mimo-provider
 */
import { REAUTH_REQUIRED, ProviderError } from '../errors.js';
import { TokenStore } from '../auth/store.js';
import { TokenManager } from '../auth/tokens.js';
import { LOGIN_CANCELLED_MESSAGE } from '../auth/device-flow.js';
import { finishMimoSignIn, mimoCredentialsFrom, readMimoUsage, refreshMimoSession, startMimoSignIn, waitMimoPoll, } from '../auth/mimo-flow.js';
import { MimoAdapter, MIMO_CODE_URL, MIMO_CONTEXT_WINDOW, MIMO_DEFAULT_MODEL, MIMO_PROVIDER_ID, MIMO_PROVIDER_NAME, parseMimoSession, } from './mimo.js';
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
 * Build the `mimo` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved endpoints, paths, and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export function createMimoProvider(options) {
    const fetchFn = options.fetchFn ?? proxiedFetch;
    const store = new TokenStore(options.credentialFile, MIMO_PROVIDER_ID, parseMimoSession);
    const toSession = (creds, cookies, userId) => ({
        accessToken: JSON.stringify(cookies),
        expiresAt: Date.now() + 24 * 60 * 60 * 1000,
        accountId: userId,
        apiBase: creds.base,
        mimoCreds: JSON.stringify(creds),
        mimoCookies: JSON.stringify(cookies),
        region: creds.region,
    });
    /** The in-flight attempt, so `pending()` is stable across calls. */
    let current;
    const flow = {
        async start() {
            const start = await startMimoSignIn(fetchFn);
            const controller = new AbortController();
            const session = (async () => {
                while (true) {
                    if (controller.signal.aborted)
                        throw new Error(LOGIN_CANCELLED_MESSAGE);
                    const poll = await waitMimoPoll(start, fetchFn);
                    if (poll !== undefined) {
                        const { creds, cookies } = await finishMimoSignIn(poll, start.device, fetchFn);
                        return toSession(creds, cookies, creds.userId);
                    }
                    await sleep(1000, controller.signal);
                }
            })();
            const attempt = {
                prompt: {
                    url: start.url,
                    expiresAt: start.until,
                    mode: 'open-url',
                },
                session,
                cancel: () => { controller.abort(); },
            };
            current = attempt;
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
        displayName: MIMO_PROVIDER_NAME,
        // The app signs on again after a day; renew a little ahead of that.
        preemptMs: 10 * 60 * 1000,
        refresh: async (session) => {
            let creds;
            try {
                creds = JSON.parse(session.mimoCreds);
            }
            catch {
                throw new ProviderError(`${MIMO_PROVIDER_NAME} sign-in has expired; connect the account again`, REAUTH_REQUIRED);
            }
            try {
                const renewed = await refreshMimoSession(creds, fetchFn);
                return { accessToken: JSON.stringify(renewed.cookies), expiresAt: renewed.expiresAt };
            }
            catch (error) {
                if (error instanceof Error && /lapsed/i.test(error.message)) {
                    throw new ProviderError(`${MIMO_PROVIDER_NAME} sign-in has expired; connect the account again`, REAUTH_REQUIRED);
                }
                throw error;
            }
        },
    });
    const discovery = options.discovery ?? true;
    const adapter = new MimoAdapter({
        discovery,
        streamIdleTimeoutMs: options.streamIdleTimeoutMs,
        tokens,
        ...options.onWarn === undefined ? {} : { onWarn: options.onWarn },
        ...options.resolveAttachments === undefined ? {} : { resolveAttachments: options.resolveAttachments },
        hiddenModels: () => options.preferences.hiddenIds(MIMO_PROVIDER_ID),
        userModels: () => options.preferences.extraIds(MIMO_PROVIDER_ID),
        ...options.cachedListing === undefined ? {} : { cachedListing: options.cachedListing },
        ...options.saveListing === undefined ? {} : { saveListing: options.saveListing },
        fetchFn,
    });
    // Read the cached table now rather than at the first picker query: the Settings page
    // reads the catalog synchronously, so a request-free start must already have it.
    void adapter.hydrate().catch(() => undefined);
    const spec = {
        id: MIMO_PROVIDER_ID,
        displayName: MIMO_PROVIDER_NAME,
        codeUrl: MIMO_CODE_URL,
        credentialFile: options.credentialFile,
        // MiMo's own listing is best-effort: it may answer the public API's ids rather than
        // the app roster's, and it may refuse the session credential. What the picker shows
        // until (and unless) it answers is the built-in roster, so a failed read can never
        // empty the picker.
        discovery,
        defaultModel: options.defaultModel ?? MIMO_DEFAULT_MODEL,
        catalog: () => adapter.catalogEntries(),
        catalogOwn: () => adapter.ownCatalogEntries(),
        fallbackContextWindow: MIMO_CONTEXT_WINDOW,
        discover: (accessToken) => adapter.fetchCatalog(accessToken),
        usage: async (_accessToken, account) => {
            const held = await store.get(account);
            if (held === undefined)
                return undefined;
            let creds;
            let cookies;
            try {
                creds = JSON.parse(held.mimoCreds);
                cookies = JSON.parse(held.mimoCookies);
            }
            catch {
                return undefined;
            }
            const read = await readMimoUsage({ cookies, userId: creds.userId }, creds, fetchFn);
            return { plan: read.plan, ...read.renewsAt === undefined ? {} : { renewsAt: read.renewsAt }, windows: read.windows };
        },
        details: () => [
            { key: 'provider', value: MIMO_PROVIDER_NAME },
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
    };
}
