/**
 * The `glm` route: BigModel's coding plan over the OpenAI Responses wire.
 *
 * Sign-in is ZCode's own OAuth flow (see `auth/glm-flow.ts`). What the flow hands
 * back is a BigModel access token and the ZCode JWT the subscription is bound to;
 * requests and the model list then go to the coding-plan host, whose
 * `/responses` route answers the same wire the `kimi` and `grok` routes use.
 *
 * The model table this route publishes is that host's own `/models` answer, read once
 * and kept in this plugin's cache (see `hydrate` and `fetchCatalog`). Nothing in this
 * module decides which models exist, so one released, renamed or retired server-side
 * reaches the picker without a plugin update and without a request per refresh.
 *
 * @module dsh-plugin-subscription-oauth/providers/glm
 */
import { LlmAdapter } from '@deepseek-ai/dsh-llm';
import type { GenerateOptions, LlmModelInfo, LlmProviderInfo, LlmResolvedModelInfo, StreamChunk } from '@deepseek-ai/dsh-llm';
import type { SessionBase } from '../auth/store.js';
import type { TokenManager } from '../auth/tokens.js';
import type { FetchFn } from '../http.js';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
import type { UsageWindow } from '../protocol.js';
import type { CatalogEntry } from './catalog.js';
import type { UsageRead } from './usage.js';
/** Provider route this module serves. */
export declare const GLM_PROVIDER_ID = "glm";
/** Display name for the picker group and the Settings card. */
export declare const GLM_PROVIDER_NAME = "GLM (BigModel)";
/** Page a user opens to manage the coding plan. */
export declare const GLM_CODE_URL = "https://bigmodel.cn/coding-plan/personal/overview";
/** Coding-plan host; the Responses endpoint is `<base>/responses`. */
export declare const GLM_API_BASE = "https://open.bigmodel.cn/api/coding/paas/v4";
/** Origin the account-level endpoints hang off, without the coding path. */
export declare const GLM_ORIGIN = "https://open.bigmodel.cn";
/** Path appended to the API base for a streaming request. */
export declare const GLM_RESPONSES_PATH = "/responses";
/** Path appended to the API base to list the models an account can reach. */
export declare const GLM_MODELS_PATH = "/models";
/**
 * Account allowance endpoint.
 *
 * It sits on the host root rather than under the coding prefix, and BigModel
 * authenticates it with the bare key — no `Bearer` scheme, which the host refuses.
 */
export declare const GLM_QUOTA_PATH = "/api/monitor/usage/quota/limit";
/** Model the picker preselects. */
export declare const GLM_DEFAULT_MODEL = "glm-5.3";
/** Context capacity of the GLM 5.x family. */
export declare const GLM_CONTEXT_WINDOW = 1048576;
/** Output budget used when the caller sets no cap. */
export declare const GLM_DEFAULT_MAX_TOKENS = 131072;
/** One signed-in BigModel account. */
export interface GlmSession extends SessionBase {
    /** ZCode JWT the coding plan is bound to; presented as the API key. */
    zcodeJwt: string;
    /** API base recorded at login time (diagnostics only). */
    apiBase?: string;
}
/**
 * Coerce one decoded credential entry into a GLM session.
 *
 * @param value - decoded JSON value for one account.
 * @returns the session, or undefined when required fields are missing.
 */
export declare function parseGlmSession(value: unknown): GlmSession | undefined;
/** Everything the adapter needs from the route. */
export interface GlmAdapterOptions {
    /** Request base; `<base>/responses` is the endpoint. */
    apiBase: string;
    /** Consult the endpoint's live model list. */
    discovery: boolean;
    /** Abandon a stream after this much silence. */
    streamIdleTimeoutMs: number;
    /** Token manager owning refresh. */
    tokens: TokenManager<GlmSession>;
    /** Model ids the user hid from the picker. */
    hiddenModels?: () => Promise<ReadonlySet<string>> | ReadonlySet<string>;
    /** Ids the user added by hand. */
    userModels?: () => Promise<readonly string[]> | readonly string[];
    /** Extra ids published without shipped metadata. */
    extraModels?: readonly string[];
    /**
     * Model this route preselects, and therefore always offers.
     *
     * Configuration rather than a shipped fact: a subscription that re-points its
     * alias at a new release follows the config instead of this build's release
     * cadence. Defaults to {@link GLM_DEFAULT_MODEL}.
     */
    defaultModel?: string;
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
    /** Resolve the attachment service per request. */
    resolveAttachments?: () => AttachmentStore | undefined;
    onWarn?: (message: string) => void;
    onCredentialRejected?: (account: string) => void;
    /** Read one account's metered windows, so the streamer can steer away from a spent one. */
    readUsage?: ((account: string) => Promise<readonly UsageWindow[]>) | undefined;
    /** Fetch implementation; defaults to the proxy-aware fetch. */
    fetchFn?: FetchFn;
}
/**
 * Read the two windows a coding-plan allowance reports.
 *
 * The host lists one row per metered window and names it by a `unit`/`number`
 * pair: `(3, 5)` is the rolling five-hour window and `(6, 1)` the weekly one.
 * Rows of any other type — MCP tool usage, for instance — are not model quota and
 * are skipped rather than shown as an allowance.
 *
 * @param payload - the decoded `quota/limit` body.
 * @returns one window per metered allowance, rolling first.
 */
export declare function parseGlmQuota(payload: unknown): UsageWindow[];
/**
 * Read an account's coding-plan allowance.
 *
 * @param input - host, bearer, and fetch.
 * @returns the windows the host reported; an empty list when it stated none.
 */
export declare function readGlmUsage(input: {
    origin: string;
    accessToken: string;
    fetchFn: FetchFn;
    signal?: AbortSignal;
}): Promise<UsageRead>;
/** The adapter behind the `glm` route. */
export declare class GlmAdapter extends LlmAdapter {
    private readonly options;
    /**
     * The catalog this route serves: entries derived from the endpoint's own listing,
     * either the one just read or the one the cache kept from the last read.
     *
     * Undefined means neither source has produced a listing yet, so only the configured
     * default model is offered until one does.
     */
    private live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    private hydrated;
    /** Image-generation model id, when the listing reported one. */
    private imageModel;
    /**
     * Fast-variant wire id per picker row.
     *
     * Derived from the listing rather than a curated alias map: a row whose name is a
     * listed model plus the provider's own fast word rides that row instead of becoming
     * one of its own. GLM's own `-flash` id is a distinct model, not a variant, and is
     * left as a row.
     */
    private readonly fastVariants;
    constructor(options: GlmAdapterOptions);
    /**
     * Adopt the rows of one live listing as this route's catalog.
     *
     * Everything the picker shows is derived here from the endpoint's own answer: the
     * rows that become picker entries and the fast variant that pairs with each row.
     * Nothing about which models exist, or what each one is, is decided by this build.
     *
     * @param rows - decoded rows of the endpoint's listing, in the endpoint's order.
     * @returns the entries to publish, in picker order.
     */
    private adoptListing;
    /**
     * Read the cached listing, once, so the picker works without asking the endpoint.
     *
     * Called before the first catalog query. A read failure is a cache miss, never a
     * failure: the route simply has no listing until one is fetched.
     */
    hydrate(): Promise<void>;
    providerInfo(provider: string): LlmProviderInfo;
    providerRetryPolicy(): undefined;
    listModels(_provider: string): Promise<readonly LlmModelInfo[]>;
    resolveModel(_provider: string, model: string): Promise<LlmResolvedModelInfo>;
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
    /** Translate an auth failure into the message a user can act on. */
    private refreshHint;
    /** The catalog served: the route's own entries, then hand-added ids. */
    private catalog;
    /**
     * The catalog to present: the models the endpoint's listing named, in its own order,
     * each with the metadata that listing stated, then the configured extras.
     *
     * There is no shipped roster. A listing is read once and cached (see {@link hydrate}
     * and {@link fetchCatalog}), and that cached table is what the picker shows until the
     * next read, so a model released, renamed or retired server-side is reflected without
     * a plugin update. Only the configured default is always offered, because the picker
     * preselects it.
     */
    private shippedCatalog;
    /** The model the picker preselects, from configuration. */
    private defaultModel;
    /** Ids the user published by hand that this route does not already serve. */
    private addedEntries;
    /** The image-generation model id, when discovery reported one. */
    imageModelId(): string | undefined;
    /**
     * Whether this route has read a model listing yet.
     *
     * The image model is a row of the listing, so until one has been read "there is no image
     * model" and "the listing has not arrived" look identical from outside; a caller that asks
     * once needs to tell them apart.
     */
    hasListing(): boolean;
    /** The ids the picker must currently omit. */
    private hidden;
    /**
     * Adopt the ids a caller reports as the served set, without a listing.
     *
     * {@link fetchCatalog} already derived the entries from the rows it read, and the
     * controller reports those same ids back here; the entries it recorded are kept. A
     * caller that only has ids (a test, another discovery path) gets conservative entries
     * for them.
     *
     * @param ids - model ids the caller reports.
     */
    discover(ids: readonly string[]): void;
    /**
     * Read the endpoint's model listing and adopt it as this route's catalog.
     *
     * This is the one place a model table is produced, and it is the endpoint's own
     * answer: what the listing names is what the picker offers, with the display name,
     * context window, modalities and reasoning ladder the listing states. The rows are
     * also handed to {@link GlmAdapterOptions.saveListing}, so the next session starts
     * from this table instead of asking again — the Settings "refresh models" action is
     * what re-reads it.
     *
     * @param accessToken - bearer for the account.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    fetchCatalog(accessToken: string, signal?: AbortSignal): Promise<readonly string[] | undefined>;
    /** Persist one live listing, reporting a cache failure without failing the read. */
    private saveListing;
    /** Send one streaming request over the Responses wire. */
    private request;
    /** Resolve image blocks through the attachment service, when it is present. */
    private imageResolver;
    /** The catalog the controller publishes without hand-added ids. */
    ownCatalogEntries(): readonly CatalogEntry[];
    /** The catalog the controller publishes. */
    catalogEntries(): readonly CatalogEntry[];
}
