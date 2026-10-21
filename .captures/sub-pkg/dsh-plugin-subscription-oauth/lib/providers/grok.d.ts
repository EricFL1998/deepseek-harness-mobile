/**
 * The `grok` provider route: xAI / X Premium (SuperGrok) subscription access.
 *
 * Login is an OIDC authorization-code flow with PKCE against `auth.x.ai`,
 * discovered from the issuer's well-known document and completed on a loopback
 * callback the CLI's registered client allows. Requests then go over xAI's
 * Responses endpoint, which the shared Responses translator already speaks.
 *
 * @module dsh-plugin-subscription-oauth/providers/grok
 */
import { LlmAdapter } from '@deepseek-ai/dsh-llm';
import type { GenerateOptions, LlmModelInfo, LlmProviderInfo, LlmResolvedModelInfo, StreamChunk } from '@deepseek-ai/dsh-llm';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
import type { PkcePair } from '../auth/pkce-flow.js';
import type { SessionBase } from '../auth/store.js';
import { TokenManager } from '../auth/tokens.js';
import type { ProviderRoute } from '../auth/registry.js';
import type { ModelPreferences } from '../settings.js';
import type { FetchFn } from '../http.js';
import type { CatalogEntry } from './catalog.js';
import type { UsageWindow } from '../protocol.js';
/** Route key this module registers. */
export declare const GROK_PROVIDER_ID = "grok";
/** Display name for selectors and diagnostics. */
export declare const GROK_PROVIDER_NAME = "Grok (X Premium)";
/** Where a user manages the underlying subscription. */
export declare const GROK_CODE_URL = "https://x.ai/grok";
/** OIDC issuer whose well-known document names the endpoints. */
export declare const GROK_ISSUER = "https://auth.x.ai";
/** Discovery document URL. */
export declare const GROK_DISCOVERY_URL = "https://auth.x.ai/.well-known/openid-configuration";
/** The Grok CLI's registered client id. */
export declare const GROK_CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828";
/** Scopes the CLI requests; `grok-cli:access` is what admits subscription traffic. */
export declare const GROK_SCOPE = "openid profile email offline_access grok-cli:access api:access";
/** The one loopback port the registered client redirects to. */
export declare const GROK_CALLBACK_PORT = 56121;
/** Path the client redirects to. */
export declare const GROK_CALLBACK_PATH = "/callback";
/** API base; `<base>/responses` streams and `<base>/models` lists. */
export declare const GROK_API_BASE = "https://api.x.ai/v1";
/** Optional CLI catalog that discloses per-model context and reasoning levels. */
export declare const GROK_CLI_MODELS_URL = "https://cli-chat-proxy.grok.com/v1/models";
/** Context window assumed when neither the CLI catalog nor a seed states one. */
export declare const GROK_CONTEXT_WINDOW = 256000;
/** Default per-request output cap. */
export declare const GROK_DEFAULT_MAX_TOKENS = 32000;
/** Refresh when the access token has less than this much life left. */
export declare const GROK_PREEMPT_MS: number;
/**
 * The cold-start seed: what this build knows about xAI's models before either of the
 * account's own listings has been read.
 *
 * A seed, never a roster. It decides which ids exist only until a listing has been read
 * — the cached one at startup, or a fresh one on "refresh models" — and afterwards it
 * describes only the fields those listings left silent. A fact the endpoint states,
 * a context window included, always wins over anything here, so a model released,
 * renamed or retired server-side reaches the picker without a plugin release.
 */
export declare const GROK_MODELS: readonly CatalogEntry[];
/** A stored Grok (X Premium) login. */
export interface GrokSession extends SessionBase {
    /** Token endpoint discovered at login time; used for later refresh. */
    tokenEndpoint: string;
    /** Issuer the session was created against. */
    issuer: string;
    /** Scopes the tokens were issued with, echoed on refresh. */
    scopes?: string;
}
/**
 * Coerce one decoded credential entry into a Grok session.
 * @param value - decoded JSON value for one account.
 * @returns the session, or undefined when required fields are missing.
 */
export declare function parseGrokSession(value: unknown): GrokSession | undefined;
/** The discovered authorization-server endpoints. */
interface GrokEndpoints {
    authorizationEndpoint: string;
    tokenEndpoint: string;
}
/** Account identity from the id token, falling back to the access token. */
export declare function identityFromGrokTokens(accessToken: string, idToken?: string): {
    accountId?: string;
    email?: string;
};
/** One token-endpoint answer, normalised. */
interface GrokTokens {
    accessToken: string;
    refreshToken?: string;
    expiresAt: number;
    idToken?: string;
}
/** Turn a token-endpoint payload into tokens with an absolute expiry. */
export declare function parseGrokTokenPayload(payload: unknown, previousRefreshToken?: string): GrokTokens;
/** OIDC discovery, cached for the process. */
export declare function createGrokDiscovery(fetchFn: FetchFn, onWarn?: (message: string) => void): () => Promise<GrokEndpoints>;
/** Exchange an authorization code for tokens. */
export declare function exchangeGrokCode(input: {
    fetchFn: FetchFn;
    tokenEndpoint: string;
    code: string;
    redirectUri: string;
    pkce: PkcePair;
    signal: AbortSignal;
}): Promise<GrokTokens>;
/** Redeem a refresh token. */
export declare function refreshGrokToken(input: {
    fetchFn: FetchFn;
    tokenEndpoint: string;
    refreshToken: string;
}): Promise<GrokTokens>;
/** True when a model id names an image-generation model. */
export declare function isImageModel(id: string): boolean;
/** True when a model id names a fast/lightspeed variant of a base model. */
export declare function isFastVariant(id: string): boolean;
/** The fast word a variant's own id names (`grok-4.7-build-fast` → `fast`), lowercased. */
export declare function fastWordOf(id: string): string;
/**
 * Every prefix of one model id, longest first, down to its first token.
 *
 * `grok-4.7-build-fast` → [`grok-4.7-build`, `grok-4.7`, `grok`]. A fast variant pairs
 * with the longest prefix that is itself a listed model id, which is how
 * `grok-4.7-build-fast` lands on `grok-4.7` (its own prefix `grok-4.7-build` is not a
 * model) instead of inventing a base that no catalog row serves.
 *
 * @param id - model id.
 * @returns the candidate bases, longest first.
 */
export declare function baseCandidatesOf(id: string): readonly string[];
/**
 * The base model id a fast variant belongs to, derived only from its own name.
 *
 * The fast token is removed, then any trailing generation qualifier (`build`, `beta`,
 * `rc`, …) left behind by that removal, then any `@Nk` context suffix. What remains is
 * the base (`grok-4.7-build-fast@500k` → `grok-4.7`, `kimi-for-coding-highspeed` →
 * `kimi-for-coding`). Whether the base is a real catalog row is the index's job, not
 * this function's.
 */
export declare function baseModelOf(id: string): string;
/**
 * The fast variant of one picker row, from either side of the pair.
 *
 * Shared by every route that discovers fast ids, so the composer's quick-mode button
 * behaves the same wherever a fast model is identifiable: it finds the pair when asked
 * about the base row, and when asked about the fast id itself — which is what the
 * selection becomes once the mode is on — so it can switch back off again.
 */
export declare class FastVariantIndex {
    private readonly byBase;
    private readonly rowOfVariant;
    private readonly knownIds;
    /** Forget every pair and every known id, before re-reading a catalog. */
    clear(): void;
    /**
     * Record one id the catalog listed.
     *
     * Listed ids are what a variant may pair with: the pair is the longest listed prefix
     * of the variant's own name, so nothing is invented that no row actually serves.
     *
     * @param id - a wire id from a catalog.
     */
    noteListed(id: string): void;
    /**
     * Pair one fast id with its base, purely from its own name.
     *
     * Every listed prefix of the id is a candidate base, longest first; the first one
     * that is itself a listed model wins. No curated alias or hardcoded map is needed:
     * a provider that names its variants after the base model pairs them automatically.
     *
     * @param id - a wire id the catalog named.
     * @returns the base id, or undefined when no listed prefix names one.
     */
    pair(id: string): string | undefined;
    /**
     * Record one id, if it names a fast variant.
     *
     * The first variant named for a row wins: a catalog that lists two spellings of the
     * same variant (`kimi-for-coding-highspeed` and `kimi-k2.7-code-highspeed`) offers
     * one button, and the earlier entry is the more specific one.
     *
     * @param id - a wire id from a catalog.
     * @param rowOf - maps a base id to the picker row that serves it, when the catalog
     *   spells the base differently from the variant's own prefix.
     */
    note(id: string, rowOf?: (base: string) => string | undefined): void;
    /** The fast variant's wire id for one picker row, when the catalog named one. */
    variantOf(baseId: string): string | undefined;
    /** The pair one picker id belongs to, from either side. */
    pairOf(id: string): {
        baseId: string;
        wireId: string;
    } | undefined;
}
/** Extract the context size from a model id's `@Nk` suffix, or undefined. */
export declare function contextOf(id: string): number | undefined;
/** Strip the `@Nk` context suffix from a model id. */
export declare function stripContext(id: string): string;
/** Constructor dependencies for {@link GrokAdapter}. */
export interface GrokAdapterOptions {
    /** API base; `<base>/responses` and `<base>/models`. */
    apiBase: string;
    /** Whether the live catalog is consulted. */
    discovery: boolean;
    /** Inactivity budget before a stalled stream is abandoned. */
    streamIdleTimeoutMs: number;
    tokens: TokenManager<GrokSession>;
    onWarn?: (message: string) => void;
    resolveAttachments?: () => AttachmentStore | undefined;
    hiddenModels?: () => Promise<ReadonlySet<string>>;
    /**
     * Model ids the user added by hand.
     *
     * Read per catalog query, so the add button takes effect at the picker's next
     * refresh. A hand-added id is published with conservative metadata: the
     * endpoint is the authority on what it serves, not this build.
     */
    userModels?: () => Promise<readonly string[]>;
    /** The user's model rank for this route, most preferred first. */
    modelOrder?: () => Promise<readonly string[]>;
    fetchFn?: FetchFn;
    onCredentialRejected?: (account: string) => void;
    /** Read one account's metered windows, so the streamer can steer away from a spent one. */
    readUsage?: ((account: string) => Promise<readonly UsageWindow[]>) | undefined;
    /**
     * The endpoint's last live payload, from this plugin's cache.
     *
     * Read once per session, before the first catalog query, so the picker offers real
     * models without asking the endpoint anything. The Settings "refresh models" action is
     * what re-reads it; a picker query never does.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
}
/** Grok wire adapter: one instance serves the `grok` provider route. */
export declare class GrokAdapter extends LlmAdapter {
    private readonly options;
    /**
     * The catalog this route serves: entries derived from the endpoint's own two
     * listings, either the pair just read or the pair the cache kept from the last read.
     *
     * Undefined means neither source has produced a listing yet, so the cold-start seed
     * is all this build can honestly offer.
     */
    private live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    private hydrated;
    /** Capability metadata from the CLI catalog, keyed by model id. */
    private readonly metadata;
    /** Fast-variant wire id per base model, for the picker's quick-mode button. */
    private readonly fastVariants;
    /** The context variant one base row dispatches to, so `@500k` is never typed by hand. */
    private readonly defaultContext;
    /** Image-generation model id, when the listing reported one. */
    private imageModel;
    /** Ids the user added by hand, cached so the sync catalog can name them. */
    private userAddedIds;
    constructor(options: GrokAdapterOptions);
    /**
     * Adopt the two listings of one live read as this route's catalog.
     *
     * Everything the picker shows is derived here from the endpoint's own answers: the
     * public `/models` rows decide membership and state each model's display name, window
     * and modalities, and the account's CLI catalog states the reasoning ladder and names
     * the fast variants — the one thing the public list omits.
     *
     * A fast variant or an `@Nk` context variant rides with its base row instead of becoming
     * a row of its own: the variant is a wire id the composer dispatches, never a picker
     * row, so the user always has a way back to the standard model.
     *
     * @param payload - the cached or freshly assembled `{ public, cli }` payload.
     * @returns the entries to publish, in the endpoint's own order; undefined when the
     *   payload names no model at all, so the caller keeps whatever it already had.
     */
    private adoptListing;
    /**
     * Read the cached listing, once, so the picker works without asking the endpoint.
     *
     * Called before the first catalog query, and fired at construction by the route's
     * factory because the Settings page reads the catalog synchronously. A read failure is
     * a cache miss, never a failure: the route simply has no listing until one is fetched.
     */
    hydrate(): Promise<void>;
    providerInfo(provider: string): LlmProviderInfo;
    providerRetryPolicy(): undefined;
    listModels(_provider: string): Promise<readonly LlmModelInfo[]>;
    private hidden;
    resolveModel(_provider: string, model: string): Promise<LlmResolvedModelInfo>;
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
    /**
     * The catalog the route serves: its own entries, then the ids the user added
     * by hand. The two are built separately so the hand-added check can look at
     * the shipped list without recursing.
     */
    private catalog;
    /**
     * The catalog to present: the models both of the endpoint's listings named, in the
     * public list's own order, each with the metadata the listings stated.
     *
     * There is no shipped roster to fall back to. The listings are read once and cached
     * (see {@link hydrate} and {@link fetchCatalog}), and that cached table is what the
     * picker shows until the next read, so a model released, renamed or retired
     * server-side is reflected without a plugin update or a request per refresh.
     *
     * {@link GROK_MODELS} is only the cold-start seed: it decides membership while neither
     * listing has been read, and afterwards describes the fields the listings left silent.
     * A live fact, a context window included, always wins over it.
     */
    private shippedCatalog;
    /** Re-read the ids the user added by hand. */
    private syncUserModels;
    /**
     * The entries for ids the user added by hand.
     *
     * Published without shipped metadata: this build has never seen the model, so
     * the endpoint's own defaults are the honest description.
     */
    private addedEntries;
    /**
     * Adopt the ids a caller reports as the served set, without a listing.
     *
     * {@link fetchCatalog} already derived the entries from the rows it read, and the
     * controller reports the union of every account's ids back here; the entries it
     * recorded are kept, and an id no entry describes gets a conservative row described by
     * the cold-start seed. An id the caller does not report is dropped: the reported set is
     * the endpoint's whole answer.
     *
     * @param ids - model ids the caller reports.
     */
    discover(ids: readonly string[]): void;
    /** The fast/lightspeed variant wire id for a base model, when discovery reported one. */
    fastVariant(baseId: string): string | undefined;
    /** The fast pair one picker id belongs to, from either side. */
    fastPairOf(id: string): {
        baseId: string;
        wireId: string;
    } | undefined;
    /** The image-generation model id, when discovery reported one. */
    imageModelId(): string | undefined;
    /**
     * Whether this route has read a model listing yet.
     *
     * A variant is derived from the listing, so until one has been read "this model has no
     * variant" and "the listing has not arrived" look identical from outside; a caller that
     * asks once (the composer's quick-mode button) needs to tell them apart.
     */
    hasListing(): boolean;
    /** The curated catalog, for the Settings page's model list. */
    catalogEntries(): readonly CatalogEntry[];
    /** What this route itself serves, without the ids added by hand. */
    ownCatalogEntries(): readonly CatalogEntry[];
    /** Build and send one request for one model. */
    /** Resolve a model id to the wire id the endpoint accepts (largest context). */
    resolveModelId(model: string): string;
    /** Record one default-context mapping. */
    private noteContextVariant;
    private request;
    /** Resolve image blocks through the attachment service, when it is present. */
    private imageResolver;
    /**
     * Read both of the endpoint's listings and adopt them as this route's catalog.
     *
     * This is the one place a model table is produced, and it is the endpoint's own answer:
     * what the public list names is what the picker offers, and the account's CLI catalog
     * states the capabilities and names the fast variants that list omits. The rows are also
     * handed to {@link GrokAdapterOptions.saveListing}, so the next session starts from this
     * table instead of asking again — the Settings "refresh models" action is what re-reads
     * it, and a picker query never does.
     *
     * @param accessToken - bearer for the account.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    fetchCatalog(accessToken: string, signal?: AbortSignal): Promise<readonly string[] | undefined>;
    /**
     * Fold the fast and context variants of one id list into their base models.
     *
     * @param ids - wire ids to classify.
     */
    private noteWireVariants;
    /**
     * Read the account's CLI catalog; a failure is an empty listing, not an error.
     *
     * The catalog answers only for the signed-in account's own bearer — an anonymous
     * request is refused — and it, unlike the public `/models` list, states per-model
     * context windows and reasoning ladders and names the account's fast variant. The rows
     * are returned verbatim so both the fresh catalog and the cached payload are derived
     * from the same parse.
     *
     * @param fetchFn - fetch implementation.
     * @param signal - caller cancellation.
     * @param accessToken - the account's bearer.
     * @returns the decoded rows, or an empty list when the catalog is unavailable.
     */
    private readCliCatalog;
}
/** Everything the `grok` route needs from the plugin configuration. */
export interface GrokProviderOptions {
    /** Absolute path of `auth.json`. */
    credentialFile: string;
    /** API base; `<base>/responses` and `<base>/models`. */
    apiBase?: string;
    /** Consult the endpoint's live model list. */
    discovery: boolean;
    /** Abandon a stream after this much silence. */
    streamIdleTimeoutMs: number;
    /** Shared model-visibility preferences. */
    preferences: ModelPreferences;
    /** Resolve the attachment service per request. */
    resolveAttachments?: () => AttachmentStore | undefined;
    /** Invoked when the catalog or its visibility changed. */
    onCatalogChanged?: () => void;
    onWarn?: (message: string) => void;
    /** Fetch implementation; defaults to the proxy-aware fetch. */
    fetchFn?: FetchFn;
    /** Discovery fetch override, for tests. */
    discoveryFetchFn?: FetchFn;
    /**
     * The endpoint's last live payload, from this plugin's cache.
     *
     * The cached table is what the picker shows before any request: the endpoint's own
     * answer from the last read, so a release does not need a plugin update and a refresh
     * does not need a request.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
}
/**
 * Build the `grok` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved paths and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export declare function createGrokProvider(options: GrokProviderOptions): ProviderRoute;
export {};
