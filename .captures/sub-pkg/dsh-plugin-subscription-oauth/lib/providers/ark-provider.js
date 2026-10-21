/**
 * The `ark` provider route's wiring: ZCode-style SSO sign-in, the control plane that
 * mints the plan's API key, the model catalog, and the Settings card facts.
 *
 * Ark splits its API in two, and the route has to satisfy both:
 *
 * - the **data plane** (`ark.cn-beijing.volces.com`) serves the models and accepts
 *   only an ordinary **API key** as a plain bearer — it refuses STS credentials
 *   outright;
 * - the **control plane** (`ark.cn-beijing.volcengineapi.com`) is signed with the
 *   temporary **STS** credentials the SSO token response carries, and is where the
 *   plan's API key and its allowance come from.
 *
 * So a sign-in produces both: the key for requests, the STS set for quota.
 *
 * @module dsh-plugin-subscription-oauth/providers/ark-provider
 */
import { REAUTH_REQUIRED, ProviderError } from '../errors.js';
import { TokenStore } from '../auth/store.js';
import { TokenManager } from '../auth/tokens.js';
import { LoopbackFlow } from '../auth/pkce-flow.js';
import { ARK_CALLBACK_PATH, ARK_CALLBACK_PORTS, buildArkAuthorizeUrl, exchangeArkCode, refreshArkToken } from '../auth/ark-flow.js';
import { createArkApiKey, listArkApiKeys, readArkApiKey } from '../auth/ark-control.js';
import { ARK_API_BASE, ARK_CODE_URL, ARK_CONTEXT_WINDOW, ARK_DEFAULT_MODEL, ARK_PROVIDER_ID, ARK_PROVIDER_NAME, ArkAdapter, parseArkSession, readArkUsage, } from './ark.js';
import { ProviderController } from '../auth/provider-controller.js';
import { proxiedFetch } from '../http.js';
/** The name the minted key is created under, so it is recognisable in the console. */
const MINTED_KEY_NAME = 'dsh-plugin-subscription-oauth';
/**
 * Build the `ark` route.
 *
 * @param options - resolved endpoints, paths, and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export function createArkProvider(options) {
    const fetchFn = options.fetchFn ?? proxiedFetch;
    const apiBase = options.apiBase ?? ARK_API_BASE;
    const store = new TokenStore(options.credentialFile, ARK_PROVIDER_ID, parseArkSession);
    /**
     * Turn a completed sign-in into a usable session.
     *
     * The API key is the credential the data plane takes, and the STS set is what
     * mints it. An account that already holds a key this plugin created reuses it
     * rather than piling up one per sign-in.
     */
    const toSession = async (tokens) => {
        const sts = tokens.sts;
        if (sts === undefined) {
            throw new ProviderError('Ark sign-in returned no STS credentials, so the plan API key cannot be obtained', REAUTH_REQUIRED);
        }
        const existing = await listArkApiKeys({ credentials: sts, fetchFn })
            .then((keys) => keys.find((key) => key.name === MINTED_KEY_NAME))
            .catch((error) => {
            options.onWarn?.(`Ark could not list existing keys (${error instanceof Error ? error.message : String(error)})`);
            return undefined;
        });
        const id = existing?.id ?? await createArkApiKey({ credentials: sts, fetchFn, name: MINTED_KEY_NAME });
        const apiKey = await readArkApiKey({ credentials: sts, fetchFn, id });
        return {
            accessToken: apiKey,
            apiKey,
            sts,
            idToken: tokens.idToken,
            expiresAt: tokens.expiresAt,
            apiBase,
            ...tokens.refreshToken === undefined ? {} : { refreshToken: tokens.refreshToken },
        };
    };
    const flow = new LoopbackFlow(ARK_PROVIDER_ID, {
        ports: ARK_CALLBACK_PORTS,
        callbackPath: ARK_CALLBACK_PATH,
        buildAuthorizeUrl: ({ redirectUri, state, pkce }) => buildArkAuthorizeUrl({ redirectUri, state, pkce }),
        exchange: async ({ code, redirectUri, pkce, signal }) => toSession(await exchangeArkCode({ fetchFn, code, redirectUri, pkce, signal })),
    });
    const tokens = new TokenManager({
        store,
        displayName: ARK_PROVIDER_NAME,
        // The identity token lives fifteen minutes, so a session has to renew to stay
        // usable; the refresh grant returns a fresh id token and a fresh STS set.
        preemptMs: 5 * 60 * 1000,
        refresh: async (session) => {
            const refreshToken = session.refreshToken;
            if (refreshToken === undefined) {
                throw new ProviderError(`${ARK_PROVIDER_NAME} account has no refresh token; sign in again`, REAUTH_REQUIRED);
            }
            const renewed = await refreshArkToken({ fetchFn, refreshToken });
            // The API key outlives the identity token, so a renewal only has to carry the
            // new STS set forward for the quota; the key itself is unchanged.
            return {
                accessToken: session.apiKey,
                apiKey: session.apiKey,
                expiresAt: renewed.expiresAt,
                ...renewed.sts === undefined ? {} : { sts: renewed.sts },
                ...renewed.refreshToken === undefined ? {} : { refreshToken: renewed.refreshToken },
            };
        },
    });
    const adapter = new ArkAdapter({
        apiBase,
        discovery: options.discovery,
        streamIdleTimeoutMs: options.streamIdleTimeoutMs,
        tokens,
        readUsage: async (account) => {
            const held = await tokens.session(account);
            const sts = held?.session.sts;
            if (sts === undefined)
                return [];
            const read = await readArkUsage({ credentials: sts, fetchFn });
            return read.windows;
        },
        ...options.onWarn === undefined ? {} : { onWarn: options.onWarn },
        ...options.resolveAttachments === undefined ? {} : { resolveAttachments: options.resolveAttachments },
        hiddenModels: () => options.preferences.hiddenIds(ARK_PROVIDER_ID),
        userModels: () => options.preferences.extraIds(ARK_PROVIDER_ID),
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
        id: ARK_PROVIDER_ID,
        displayName: ARK_PROVIDER_NAME,
        codeUrl: ARK_CODE_URL,
        credentialFile: options.credentialFile,
        discovery: options.discovery,
        defaultModel: options.defaultModel ?? ARK_DEFAULT_MODEL,
        catalog: () => adapter.catalogEntries(),
        catalogOwn: () => adapter.ownCatalogEntries(),
        fallbackContextWindow: ARK_CONTEXT_WINDOW,
        discover: (apiKey) => adapter.fetchCatalog(apiKey),
        // The allowance is read with the STS credentials, not the data-plane key.
        usage: async () => {
            const held = await tokens.session();
            const sts = held?.session.sts;
            if (sts === undefined)
                return { windows: [] };
            return await readArkUsage({ credentials: sts, fetchFn });
        },
        details: () => [
            { key: 'apiBase', value: apiBase },
            { key: 'controlPlane', value: 'https://ark.cn-beijing.volcengineapi.com' },
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
