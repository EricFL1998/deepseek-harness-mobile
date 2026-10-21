/**
 * The Xiaomi MiMo wire adapter serving the `mimo` route.
 *
 * Model requests are chat completions at the MiMo server's `/route`, carrying the
 * session cookies the sign-on set — not a bearer token. The open platform's Token
 * Plan spends its own `tp-` key at a separate endpoint; that fallback lives beside
 * the account-level stream here.
 *
 * The model table this route publishes is MiMo's own listing, read once and kept in
 * this plugin's cache (see `hydrate` and `fetchCatalog`), so a model released, renamed
 * or retired server-side reaches the picker without a plugin update and without a
 * request per refresh. `MIMO_MODELS` is the cold-start seed used while no listing has
 * been read, the metadata overlay a live row is merged against, and the fallback a
 * listing that does not answer leaves the picker with — never the authority on which
 * models exist.
 *
 * Two bases are involved, and they are not the same surface:
 *   - the app surface, `<session.apiBase>/route/chat/completions`, which the stored
 *     session's cookies sign; and
 *   - the documented open-platform API at {@link MIMO_PUBLIC_API_BASE}, whose
 *     `/v1/models` route is documented to answer an OpenAI-style listing.
 * The listing is therefore best-effort: it may name the public ids (`mimo-v2.5-pro`, …)
 * rather than the app's (`mimo-pro`, …), and it may refuse the session credential
 * outright. Either way the roster the seed describes stays until a listing answers.
 * The endpoint is documented rather than probed from this machine — no MiMo account is
 * stored here — so the first real read is the verification.
 *
 * @module dsh-plugin-subscription-oauth/providers/mimo
 */
import { LlmAdapter, type GenerateOptions, type LlmModelInfo, type LlmProviderInfo, type LlmResolvedModelInfo, type StreamChunk } from '@deepseek-ai/dsh-llm';
import { type FetchFn } from '../http.js';
import type { CatalogEntry } from './catalog.js';
import type { TokenManager } from '../auth/tokens.js';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
/** The `mimo` provider route key. */
export declare const MIMO_PROVIDER_ID = "mimo";
/** Human name for the route and its cards. */
export declare const MIMO_PROVIDER_NAME = "Xiaomi MiMo";
/** Where the Settings page sends someone to get an account. */
export declare const MIMO_CODE_URL = "https://platform.xiaomimimo.com";
/** MiMo context window shared by Pro and Flash. */
export declare const MIMO_CONTEXT_WINDOW = 1000000;
/** The output ceiling MiMo advertises. */
export declare const MIMO_DEFAULT_MAX_TOKENS = 128000;
/** The model the picker preselects. */
export declare const MIMO_DEFAULT_MODEL = "mimo-pro";
/**
 * The documented open-platform API base.
 *
 * This is the host Xiaomi documents its List Models endpoint on, and it is *not* the
 * app surface a stored session chats on (`{session.apiBase}/route/chat/completions`,
 * per account and per region). The adapter's `apiBase` option overrides it, so a
 * listing that turns out to live on another host needs no code change.
 */
export declare const MIMO_PUBLIC_API_BASE = "https://api.xiaomimimo.com";
/** Path appended to the base to list the models an account can reach. */
export declare const MIMO_MODELS_PATH = "/v1/models";
/** One signed-in MiMo account, as stored. */
export interface MimoSession {
    accessToken: string;
    refreshToken?: string;
    expiresAt: number;
    accountId?: string;
    email?: string;
    /** The MiMo server base this account signs on at. */
    apiBase: string;
    /** The durable Xiaomi credential (JSON), used to re-sign-on. */
    mimoCreds: string;
    /** The live session cookies (JSON), sent as the Cookie header. */
    mimoCookies: string;
    /** The account's region. */
    region?: string;
}
/** Validate one stored value as a MiMo session, or undefined when it is not one. */
export declare function parseMimoSession(value: unknown): MimoSession | undefined;
/** One catalog row exposed as a selectable model. */
export type MimoModelEntry = CatalogEntry;
/**
 * The MiMo *app* surface's models, as this build last knew them.
 *
 * A cold-start seed and a metadata overlay, not the catalog. It decides membership
 * only while no listing has been read, it supplies the name, window and description a
 * live row is silent about (`liveEntry` — a figure the listing states always wins),
 * and it is what a listing that does not answer falls back to, so a refused probe can
 * never empty the picker.
 *
 * Its ids (`mimo-pro`, …) belong to the app surface reached at `{session.apiBase}/route`.
 * The documented public API is a different surface and may name different ids
 * (`mimo-v2.5-pro`, …); a listing that returns those replaces this table rather than
 * adding to it, which is the whole point of reading the listing at all.
 */
export declare const MIMO_MODELS: readonly MimoModelEntry[];
/** Constructor dependencies for {@link MimoAdapter}. */
export interface MimoAdapterOptions {
    /**
     * Base the model listing is read from; defaults to {@link MIMO_PUBLIC_API_BASE}.
     *
     * The chat call does not use it: that one goes to the signed-in account's own
     * `apiBase`, which is a per-region app host rather than the documented API.
     */
    apiBase?: string;
    /** Consult MiMo's own live model listing. Defaults to on. */
    discovery?: boolean;
    streamIdleTimeoutMs: number;
    tokens: TokenManager<MimoSession>;
    onWarn?: (message: string) => void;
    resolveAttachments?: () => AttachmentStore | undefined;
    hiddenModels?: () => Promise<ReadonlySet<string>>;
    userModels?: () => Promise<readonly string[]>;
    /**
     * MiMo's last live listing, from this plugin's cache.
     *
     * Read once per session, before the first catalog query, so the picker offers real
     * models without asking the endpoint anything. A route that fetched once and cached
     * the answer has no reason to fetch again on every refresh; the Settings "refresh
     * models" action is what re-reads it.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
    fetchFn?: FetchFn;
}
/** The MiMo wire adapter serving the `mimo` route. */
export declare class MimoAdapter extends LlmAdapter {
    private readonly options;
    private readonly fetchFn;
    /**
     * The catalog this route serves: entries derived from MiMo's own listing, either
     * the one just read or the one the cache kept from the last read.
     *
     * Undefined means no listing has been read (yet), so {@link MIMO_MODELS} decides
     * membership and the picker is never empty.
     */
    private live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    private hydrated;
    /** Ids the user added by hand, cached so the sync catalog can name them. */
    private userAddedIds;
    /**
     * Listing failures already reported, so one reason is stated once per session.
     *
     * A listing that does not answer is expected — the documented public API and the
     * app surface are different hosts — and saying so on every refresh would drown out
     * the warnings a user can act on.
     */
    private readonly reportedListingFailures;
    constructor(options: MimoAdapterOptions);
    /**
     * Adopt the rows of one live listing as this route's catalog.
     *
     * Everything the picker shows is derived here from MiMo's own answer: the ids it
     * names, in its own order, with the name, window, modalities and ladder it states.
     * {@link MIMO_MODELS} is consulted only as the metadata overlay for a figure the
     * row is silent about, so a live figure is never overridden by a shipped guess.
     *
     * @param rows - decoded rows of the listing, in the listing's order.
     * @returns the entries to publish, in picker order.
     */
    private adoptListing;
    /**
     * Read the cached listing, once, so the picker works without asking the endpoint.
     *
     * Called before the first catalog query. A read failure is a cache miss, never a
     * failure: the route simply has no listing until one is fetched, and the seed roster
     * is what the picker shows in the meantime.
     */
    hydrate(): Promise<void>;
    providerInfo(provider: string): LlmProviderInfo;
    listModels(_provider: string): Promise<readonly LlmModelInfo[]>;
    private hidden;
    resolveModel(_provider: string, model: string): Promise<LlmResolvedModelInfo>;
    providerRetryPolicy(): undefined;
    /** The catalog the Settings list reads: the route's own entries plus hand-added ids. */
    catalogEntries(): readonly MimoModelEntry[];
    /** The ids the route itself serves, without hand-added extras. */
    ownCatalogEntries(): readonly MimoModelEntry[];
    /**
     * The catalog served: the membership the listing named — or the seed roster while no
     * listing has been read — each merged with the metadata of the source that described
     * it, then the ids the user added by hand.
     */
    private catalog;
    /**
     * What the route itself serves, without the ids added by hand.
     *
     * No model is injected here: the configured default is an app-surface id, and the
     * listing may come from the public API, where that id does not exist. What the
     * listing names is exactly what the picker offers; a configured default that the
     * listing does not name stays dispatchable through {@link resolveModel}.
     */
    private ownCatalog;
    /** Entries for ids the user added by hand that the route does not already serve. */
    private addedEntries;
    /** Re-read the ids the user added by hand. */
    private syncUserModels;
    /**
     * Adopt the ids a caller reports as the served set, without a listing.
     *
     * {@link fetchCatalog} already derived the entries from the rows it read, and the
     * controller reports those same ids back here; the entries it recorded are kept, so
     * a reported id keeps the metadata the listing gave it. A caller that only has ids (a
     * test, another discovery path) gets a conservative entry overlaid with the seed.
     *
     * @param ids - model ids the caller reports.
     */
    discover(ids: readonly string[]): void;
    /**
     * Read MiMo's model listing and adopt it as this route's catalog.
     *
     * The documented endpoint is `GET <apiBase>/v1/models`, answering an OpenAI-style
     * `{ object: 'list', data: [{ id, object, owned_by }] }`. Xiaomi documents either an
     * `api-key` header or Bearer authentication; this sends the bearer for the account
     * the caller resolved, plus the client identity the chat call uses.
     *
     * The read is best-effort by design. The documented public API is not the app surface
     * the chat call goes to, so the answer may be a refusal, a body in another shape, or
     * rows naming ids the app does not serve; in every one of those cases the roster
     * already published stays exactly as it was, the failure is stated once through
     * {@link MimoAdapterOptions.onWarn}, and the picker is never emptied by a probe.
     *
     * @param accessToken - bearer for the account, as the controller resolved it.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    fetchCatalog(accessToken: string, signal?: AbortSignal): Promise<readonly string[] | undefined>;
    /** The base the listing is read from, without a trailing slash. */
    private listingBase;
    /** State one listing failure, once per distinct reason. */
    private warnListingFailure;
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
    /** The app's names for its models, as the Token Plan's endpoint names them. */
    private static readonly TP_MODELS;
    /**
     * Re-ask a membership_required chat at the Token Plan's endpoint.
     *
     * Reads the account's tp- API key from the open platform, then posts the chat to the
     * plan's OpenAI base with that key. Undefined when the account has no live plan or no
     * readable key.
     */
    private tokenPlanRequest;
    /**
     * The session cookies as one Cookie header, in a steady order.
     *
     * MiMo is a cookie session, not a bearer: the wire credential is the cookie
     * header built from the stored session's cookie record.
     */
    private cookieHeader;
    private requestBody;
    private toChatMessage;
    private readStream;
}
