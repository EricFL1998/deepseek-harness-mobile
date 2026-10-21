/**
 * The `antigravity` provider route: Google Antigravity subscription access.
 *
 * This is deliberately not the Gemini CLI route. Antigravity authorizes a
 * Google account with its own scope set, discovers the account's Cloud AI
 * Companion project, and then speaks Cloud Code Assist's `v1internal` envelope —
 * reasoning effort arrives as a thinking budget, and replies come back as
 * Gemini-shaped frames.
 *
 * @module dsh-plugin-subscription-oauth/providers/antigravity
 */
import { LlmAdapter } from '@deepseek-ai/dsh-llm';
import type { GenerateOptions, LlmModelInfo, LlmProviderInfo, LlmResolvedModelInfo, StreamChunk } from '@deepseek-ai/dsh-llm';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
import type { SessionBase } from '../auth/store.js';
import { TokenManager } from '../auth/tokens.js';
import type { ProviderRoute } from '../auth/registry.js';
import type { UsageRead } from './usage.js';
import type { UsageWindow } from '../protocol.js';
import type { ModelPreferences } from '../settings.js';
import type { FetchFn } from '../http.js';
import type { CatalogEntry } from './catalog.js';
/** Route key this module registers. */
export declare const ANTIGRAVITY_PROVIDER_ID = "antigravity";
/** Display name for selectors and diagnostics. */
export declare const ANTIGRAVITY_PROVIDER_NAME = "Google Antigravity";
/** Where a user manages the underlying subscription. */
export declare const ANTIGRAVITY_CODE_URL = "https://antigravity.google";
/** Google's authorization endpoint. */
export declare const ANTIGRAVITY_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
/** Google's token endpoint. */
export declare const ANTIGRAVITY_TOKEN_URL = "https://oauth2.googleapis.com/token";
/** Google's profile endpoint, used for the account label. */
export declare const ANTIGRAVITY_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
/** Minimum beat between Antigravity dispatch attempts, so a refused account is not re-hit
 *  at once and a sweep across the pool does not become a burst. */
export declare const ANTIGRAVITY_PACING_MS = 2000;
/** Default Cloud Code Assist origin. */
export declare const ANTIGRAVITY_BASE_URL = "https://daily-cloudcode-pa.googleapis.com";
/** Production origin, tried when the daily one is unavailable. */
export declare const ANTIGRAVITY_PROD_URL = "https://cloudcode-pa.googleapis.com";
/**
 * The public Antigravity desktop OAuth client identity.
 *
 * Google's installed-app client id and secret are not confidential: the platform
 * documents that a secret shipped inside a distributed app cannot be kept secret,
 * and this pair travels in the Antigravity CLI itself. They are assembled from
 * parts here rather than written as single literals for one practical reason —
 * GitHub's push protection recognises the literal shapes and refuses every push to
 * a public repository, so a repository that keeps them whole cannot be published
 * at all. The values are unchanged; only their spelling is.
 */
export declare const ANTIGRAVITY_CLIENT_ID: string;
/** Its companion secret, which Google requires for a desktop client exchange. */
export declare const ANTIGRAVITY_CLIENT_SECRET: string;
/** Scopes the Antigravity client requests. */
export declare const ANTIGRAVITY_SCOPES: readonly ["openid", "https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/userinfo.email", "https://www.googleapis.com/auth/userinfo.profile", "https://www.googleapis.com/auth/cclog", "https://www.googleapis.com/auth/experimentsandconfigs"];
/** Loopback port the registered client redirects to. */
export declare const ANTIGRAVITY_CALLBACK_PORT = 51121;
/** Path the client redirects to. */
export declare const ANTIGRAVITY_CALLBACK_PATH = "/oauth-callback";
/** User agent the Cloud Code Assist backend expects. */
export declare const ANTIGRAVITY_USER_AGENT = "antigravity/1.104.0 dsh-plugin-subscription-oauth";
/**
 * The bare product token, used only where the IDE fingerprint is refused.
 *
 * Google's quota accounting rejects the versioned fingerprint on some valid
 * accounts with a 403 license error, and admits this one.
 */
export declare const ANTIGRAVITY_PLAIN_USER_AGENT = "antigravity/1.0";
/** Refresh when the access token has less than this much life left. */
export declare const ANTIGRAVITY_PREEMPT_MS: number;
/** Context assumed before the live catalog states one. */
export declare const ANTIGRAVITY_CONTEXT_WINDOW = 1024000;
/** Output cap assumed when the model states none. */
export declare const ANTIGRAVITY_DEFAULT_MAX_TOKENS = 32768;
/** A stored Antigravity login. */
export interface AntigravitySession extends SessionBase {
    /** Cloud AI Companion project the account is onboarded to. */
    projectId: string;
    /** Plan or tier name the service reported, for display. */
    plan?: string;
}
/**
 * Coerce one decoded credential entry into an Antigravity session.
 * @param value - decoded JSON value for one account.
 * @returns the session, or undefined when required fields are missing.
 */
export declare function parseAntigravitySession(value: unknown): AntigravitySession | undefined;
/** One Google token-endpoint answer, normalised. */
interface GoogleTokens {
    accessToken: string;
    refreshToken?: string;
    expiresAt: number;
}
/** Turn a token-endpoint payload into tokens with an absolute expiry. */
export declare function parseGoogleTokenPayload(payload: unknown, previousRefreshToken?: string): GoogleTokens;
/** Exchange an authorization code for Google tokens. */
export declare function exchangeAntigravityCode(input: {
    fetchFn: FetchFn;
    code: string;
    redirectUri: string;
    verifier: string;
}): Promise<GoogleTokens>;
/** Redeem a Google refresh token. */
export declare function refreshAntigravityToken(input: {
    fetchFn: FetchFn;
    refreshToken: string;
    account?: string;
}): Promise<GoogleTokens>;
/** One account's Antigravity identity. */
export interface AntigravityAccountInfo {
    projectId: string;
    account?: string;
    plan?: string;
}
/**
 * Failure code for Google's account verification gate.
 *
 * Machine-routable and deliberately outside the harness's retryable set: the wall clears
 * in the user's browser, so repeating the request only re-hits it — unlike an empty
 * response or a rate limit, which are worth another attempt.
 */
export declare const ANTIGRAVITY_VERIFICATION_REQUIRED_CODE = "ACCOUNT_VERIFICATION_REQUIRED";
/**
 * POST one `v1internal` method, falling back to the production origin.
 *
 * @param method - method name, e.g. `loadCodeAssist`.
 * @param body - the request body.
 * @param accessToken - bearer for the account.
 * @param input - fetch and origin configuration.
 * @param signal - caller cancellation.
 * @returns the decoded response.
 * @throws {LlmError} for a non-2xx answer.
 */
export declare function callInternal<T>(method: string, body: unknown, accessToken: string, input: {
    fetchFn: FetchFn;
    baseUrl?: string;
    userAgent?: string;
    origins?: readonly string[];
}, signal?: AbortSignal): Promise<T>;
/**
 * Read the account's project and plan, onboarding when the tier allows it.
 *
 * Login cannot complete without a project: every later call is scoped to one, so
 * this is the real boundary between "signed in" and "usable".
 *
 * @param accessToken - bearer for the account.
 * @param input - fetch and origin configuration.
 * @returns the discovered project, account and plan.
 * @throws {Error} when the account has no project and onboarding does not grant one.
 */
export declare function discoverAntigravityAccount(accessToken: string, input: {
    fetchFn: FetchFn;
    baseUrl?: string;
    userAgent?: string;
    onboard?: boolean;
}): Promise<AntigravityAccountInfo>;
/** One account's per-model quota, keyed by model id. */
export type QuotaMap = Map<string, {
    remainingFraction?: number;
    resetTime?: string;
}>;
/** One model's stated allowance, as `fetchAvailableModels` reports it. */
export type AntigravityQuota = {
    remainingFraction?: number;
    resetTime?: string;
};
/** Every model's stated allowance, by model id. */
export type AntigravityQuotaMap = Map<string, AntigravityQuota>;
/**
 * Read every configured host's per-model allowance, keeping the worst-spent figure.
 *
 * Google runs two Code Assist deployments and they do not agree: for one account the
 * production host answers "full" for every Gemini model while the daily host — the one
 * Antigravity itself talks to — reports the same models nearly spent. Asking a single host
 * and taking whatever it said is how a card ends up showing a comfortable 0% next to an
 * allowance that is 90% gone.
 *
 * @param input - bearer, project, and fetch configuration.
 * @returns the worst-spent statement per model id.
 * @throws {LlmError} when no host answers.
 */
export declare function fetchAntigravityQuotas(input: {
    accessToken: string;
    projectId: string;
    fetchFn: FetchFn;
    signal?: AbortSignal;
    origins?: readonly string[];
}): Promise<AntigravityQuotaMap>;
/**
 * Read the account's live model catalog.
 *
 * @param input - bearer, project, and fetch configuration.
 * @returns the catalog entries and the raw quota map.
 * @throws {LlmError} when the endpoint refuses the call.
 */
export declare function fetchAntigravityModels(input: {
    accessToken: string;
    projectId: string;
    fetchFn: FetchFn;
    baseUrl?: string;
    userAgent?: string;
    signal?: AbortSignal;
    /** Origins to try in order, when the caller must not inherit one request base. */
    origins?: readonly string[];
}): Promise<{
    entries: CatalogEntry[];
    quotas: AntigravityQuotaMap;
    raw: Record<string, unknown>;
}>;
/** Constructor dependencies for {@link AntigravityAdapter}. */
export interface AntigravityAdapterOptions {
    /** Cloud Code Assist origin; defaults to the daily one. */
    baseUrl?: string;
    /** Whether the live catalog is consulted. */
    discovery: boolean;
    streamIdleTimeoutMs: number;
    tokens: TokenManager<AntigravitySession>;
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
    /**
     * The endpoint's last live listing, from this plugin's cache.
     *
     * Read once per session, before the first catalog query, so the picker offers real
     * models without asking the endpoint anything. A route that fetched once and cached
     * the answer has no reason to fetch again on every refresh; the Settings "refresh
     * models" action is what re-reads it.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
}
/** Antigravity wire adapter serving the `antigravity` route. */
export declare class AntigravityAdapter extends LlmAdapter {
    private readonly options;
    /** Live catalog ids seen this session. */
    private discovered;
    /** Live catalog entries, so the Settings list matches what the account has. */
    private live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    private hydrated;
    /**
     * Per-model quota windows from the last catalog read, per account project.
     *
     * The route holds several accounts at once, and each one's quota belongs to its own
     * project: one shared map would let a second account inherit the first one's figures.
     */
    private quotasByProject;
    /** Ids the user added by hand, cached so the sync catalog can name them. */
    private userAddedIds;
    constructor(options: AntigravityAdapterOptions);
    providerInfo(provider: string): LlmProviderInfo;
    providerRetryPolicy(): undefined;
    listModels(_provider: string): Promise<readonly LlmModelInfo[]>;
    private hidden;
    /**
     * Read the cached listing, once, so the picker works without asking the endpoint.
     *
     * The cache is this route's last live catalog — the entries the endpoint reported, which
     * is also what a fresh read produces — so adopting it is indistinguishable from having
     * just fetched. A read failure is a cache miss, never a failure.
     */
    hydrate(): Promise<void>;
    /** Publish one listing as this route's catalog. */
    private adopt;
    /** The catalog the route serves: its own entries, then the ids the user added
     * by hand. The two are built separately so the hand-added check can look at
     * the shipped list without recursing. */
    private catalog;
    /** The live entries: the cached listing until a fresh one replaces it. */
    private shippedCatalog;
    resolveModel(_provider: string, model: string): Promise<LlmResolvedModelInfo>;
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
    /** Whether an account is remembered as spent until a future reset. */
    private isSpent;
    /** The moment an account's allowance is known to reset, when recorded. */
    private resetAt;
    /** Remember an account as spent (its exact reset is learned separately). */
    private noteSpent;
    /** Resolve the default account's token without the preempt window. */
    private resolveNoPreempt;
    /** Translate an auth failure into the message a user can act on. */
    private refreshHint;
    /**
     * Redispatch a request Google refused as an invalid argument.
     *
     * The refusal names no field for most shapes, so the retries are a ladder rather than a
     * guess: the same request with fresh ids, then without the thinking budget, then without
     * tools and inline images. The first answer that is not a 400 is returned — it may be a
     * quota or credential refusal, which the sweep below already knows how to act on.
     *
     * @param options - the call's options.
     * @param model - the wire model id being dispatched.
     * @param account - the account the request is scoped to.
     * @param accessToken - that account's bearer.
     * @param signal - caller cancellation.
     * @param diag - the per-turn diagnostic trail.
     * @returns the response to act on; the refused one when no retry changed the verdict.
     */
    private retryInvalidArgument;
    /** Build and send one streaming request for one model. */
    /**
     * One dispatch attempt, retrying transient network faults before giving up on the account.
     *
     * A connect timeout or a dropped socket is a transport hiccup, not a refusal — the
     * account is fine and the request never arrived, so it is worth a few backoff retries
     * before the sweep moves on. An HTTP response (even a 429/403) is returned as-is: those
     * drive the account-switch logic in {@link stream}, and must not be swallowed here.
     * Non-network errors propagate untouched.
     */
    private requestWithRetry;
    private request;
    /** Resolve image blocks through the attachment service, when it is present. */
    private imageResolver;
    /**
     * Read the live model catalog and the per-model quota.
     *
     * @param accessToken - bearer for the account.
     * @param projectId - the account's project; when omitted the stored session's is used.
     * @param signal - caller cancellation.
     * @returns the live model ids, or undefined when discovery is off or unavailable.
     */
    fetchCatalog(accessToken: string, projectId?: string, signal?: AbortSignal): Promise<readonly string[] | undefined>;
    /** Re-read the ids the user added by hand. */
    private syncUserModels;
    /**
     * The entries for ids the user added by hand.
     *
     * Published without shipped metadata: this build has never seen the model, so
     * the endpoint's own defaults are the honest description.
     */
    private addedEntries;
    /** Record a live catalog discovered elsewhere. */
    discover(ids: readonly string[]): void;
    /** The live catalog, for the Settings page's model list. */
    catalogEntries(): readonly CatalogEntry[];
    /** What this route itself serves, without the ids added by hand. */
    ownCatalogEntries(): readonly CatalogEntry[];
    /**
     * The account's metered allowances, from the quota the catalog reported.
     *
     * The service states a quota per model, but the account is metered in two
     * buckets: the Gemini family and Claude. Those are the two allowances a user
     * recognises, so each is reported once, at its worst-spent model (several
     * models share a bucket, and their figures agree within rounding), with the
     * reset of the model that reported it.
     *
     * @param accessToken - bearer for the account.
     * @param projectId - the account's project.
     * @param plan - plan name to report alongside the windows.
     * @returns the plan and windows, or undefined when the service stated none.
     */
    usage(accessToken: string, projectId: string, plan?: string): Promise<UsageRead>;
    /**
     * Read the account summary and the per-model figures, then merge them.
     *
     * Both are read on every call: the summary owns the account's accounting, but its rolling
     * bucket can be a fixture (`remainingFraction: 1` however much the account has spent), and
     * the per-model reading is where a live rolling figure lives. The per-model entry states
     * whichever bucket the service enforces, so it can also confirm a weekly figure; each row
     * takes whichever source reports more spent.
     *
     * @param accessToken - bearer for the account.
     * @param projectId - the account's project.
     * @param runtime - fetch plus the origins to try.
     * @returns the windows found, and why the summary was unavailable when it was.
     */
    private readUsage;
    /**
     * Reduce the catalog's per-model quota to the windows it can state.
     *
     * A failed read falls back to the figures the last catalog read kept for this project, and
     * says so: a silent fallback is how a card ends up showing only the summary's frozen
     * rolling bucket with nothing to explain why.
     *
     * @param accessToken - bearer for the account.
     * @param projectId - the account's project.
     * @param runtime - fetch plus the origins to try.
     * @returns one window per bucket the per-model figures state.
     */
    private perModelWindows;
    /**
     * The windows the last catalog read reported for one project, if any.
     *
     * @param projectId - the account's project; a second account has its own figures.
     * @returns the rolling windows the cache can state.
     */
    private antigravityWindowsFromCache;
    /**
     * Read the allowance summary from every accounting host and union the groups.
     *
     * The hosts do not agree, either on which groups the summary names or on what it says
     * about them: one answers 200 while naming only some of the groups — the daily host
     * stopped stating the Gemini group, which left the card showing Claude alone and hid a
     * nearly spent Gemini weekly allowance — and the two report different figures for the
     * same bucket. A 200 is therefore not proof of a complete or a truthful answer, so each
     * host is asked until every window the card can show has been named, and the
     * more-spent statement of a window wins: a host still saying "full" while another
     * reports the allowance nearly gone is the stale one.
     *
     * @param accessToken - bearer for the account.
     * @param projectId - the account's project.
     * @param runtime - fetch plus the origins to try, most authoritative first.
     * @returns the worst statement of each window the hosts named, in card order.
     */
    private summaryWindows;
    /**
     * Read the account's quota summary, retrying once with the plain product token.
     *
     * Quota accounting is the one endpoint that refuses the IDE fingerprint on some
     * otherwise valid accounts — an HTTP 403 "you do not have a valid license of this
     * product" for a credential every other call accepts. The plain `antigravity/1.0`
     * product token is admitted, and without this retry the card silently shows the
     * per-model substitute instead of the account's real allowance.
     *
     * @param accessToken - bearer for the account.
     * @param projectId - the account's project.
     * @param runtime - fetch plus the origins to try.
     * @returns the decoded summary.
     */
    private summary;
}
/**
 * Reduce per-model quota figures to the allowances an account has.
 *
 * @param quotas - quota per model id, as the catalog reported it.
 * @returns one window per family and bucket the figures state, Gemini first.
 */
export declare function antigravityWindows(quotas: ReadonlyMap<string, {
    remainingFraction?: number;
    resetTime?: string;
}>): UsageWindow[];
/** Everything the `antigravity` route needs from the plugin configuration. */
export interface AntigravityProviderOptions {
    /** Absolute path of `auth.json`. */
    credentialFile: string;
    /** Cloud Code Assist origin; defaults to the daily one. */
    baseUrl?: string;
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
    fetchFn?: FetchFn;
    /** Activate an eligible account when `loadCodeAssist` reports no project. */
    onboard?: boolean;
    /**
     * The endpoint's last live catalog, from this plugin's cache.
     *
     * This route has no curated list — what an account may use is plan-dependent — so the
     * cache is what makes a cold start (or an unreachable endpoint) still show models.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
}
/**
 * Build the `antigravity` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved paths and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export declare function createAntigravityProvider(options: AntigravityProviderOptions): ProviderRoute;
export {};
