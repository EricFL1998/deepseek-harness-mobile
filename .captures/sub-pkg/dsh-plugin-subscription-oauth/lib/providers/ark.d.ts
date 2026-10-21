/**
 * The `ark` route: Volcengine Ark's Coding Plan over the OpenAI Responses wire.
 *
 * Everything in this module is pinned to what the endpoints themselves said:
 *
 * - `POST /api/coding/v3/responses` exists and wants a credential (401).
 * - It accepts a **bearer Ark API key** and nothing else. An SSO identity token in
 *   the same header is refused with `403 Unsupported zti authentication method`,
 *   and an AK/SK signature with `403 Unsupported aksk authentication method`, while
 *   a UUID-shaped key is looked up (`401 The API key doesn't exist`) and a
 *   malformed one is told so (`401 The API key format is incorrect`). So the
 *   credential is a UUID key, sent as a plain bearer — no request signing.
 * - `/api/coding/v3` is documented by the official CLI as "OpenTOP + CodingPlan
 *   API", and its `/models` listing — read once and kept in this plugin's cache, see
 *   `hydrate` and `fetchCatalog` — is the roster this route publishes. Nothing in this
 *   module decides which models the plan serves, so one released, renamed or retired
 *   server-side reaches the picker without a plugin update.
 *
 * @module dsh-plugin-subscription-oauth/providers/ark
 */
import { LlmAdapter } from '@deepseek-ai/dsh-llm';
import type { GenerateOptions, LlmModelInfo, LlmProviderInfo, LlmResolvedModelInfo, StreamChunk } from '@deepseek-ai/dsh-llm';
import type { SessionBase } from '../auth/store.js';
import type { TokenManager } from '../auth/tokens.js';
import type { ArkStsCredentials } from '../auth/ark-control.js';
import type { FetchFn } from '../http.js';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
import type { UsageWindow } from '../protocol.js';
import type { CatalogEntry } from './catalog.js';
import type { UsageRead } from './usage.js';
/** Provider route this module serves. */
export declare const ARK_PROVIDER_ID = "ark";
/** Display name for the picker group and the Settings card. */
export declare const ARK_PROVIDER_NAME = "Volcengine Ark";
/** Page a user opens to manage the plan. */
export declare const ARK_CODE_URL = "https://console.volcengine.com/ark/region:ark+cn-beijing/overview";
/** Coding-plan base; the Responses endpoint is `<base>/responses`. */
export declare const ARK_API_BASE = "https://ark.cn-beijing.volces.com/api/coding/v3";
/** Origin the account-level endpoints hang off. */
export declare const ARK_ORIGIN = "https://ark.cn-beijing.volces.com";
/** Path appended to the API base for a streaming request. */
export declare const ARK_RESPONSES_PATH = "/responses";
/** Model the picker preselects: the plan's own alias for its coding model. */
export declare const ARK_DEFAULT_MODEL = "ark-code-latest";
/**
 * Context capacity advertised for the plan's models.
 *
 * The plan states no window in this build; the endpoint's own listing supplies one
 * where it names a model, and discovery refines this where it does not.
 */
export declare const ARK_CONTEXT_WINDOW = 262144;
/** Output budget used when the caller sets no cap. */
export declare const ARK_DEFAULT_MAX_TOKENS = 32000;
/** One signed-in Ark account. */
export interface ArkSession extends SessionBase {
    /**
     * The plan's API key.
     *
     * The data plane takes this as a plain bearer; the SSO identity token authorises
     * the control plane that mints it, so the key — not the identity token — is what
     * every model request carries.
     */
    apiKey: string;
    /**
     * Temporary STS credentials, used to sign control-plane calls.
     *
     * The SSO endpoint returns these in its `access_token`, which is where the plan
     * allowance is read from; without them the route still serves models but states
     * no quota.
     */
    sts?: ArkStsCredentials;
    /** Identity token the key was minted under, for the control plane. */
    idToken?: string;
    /** API base recorded at login time (diagnostics only). */
    apiBase?: string;
}
/**
 * Coerce one decoded credential entry into an Ark session.
 *
 * @param value - decoded JSON value for one account.
 * @returns the session, or undefined when required fields are missing.
 */
export declare function parseArkSession(value: unknown): ArkSession | undefined;
/** Everything the adapter needs from the route. */
export interface ArkAdapterOptions {
    apiBase: string;
    discovery: boolean;
    streamIdleTimeoutMs: number;
    tokens: TokenManager<ArkSession>;
    hiddenModels?: () => Promise<ReadonlySet<string>> | ReadonlySet<string>;
    userModels?: () => Promise<readonly string[]> | readonly string[];
    extraModels?: readonly string[];
    /**
     * Model this route preselects, and therefore always offers.
     *
     * Configuration rather than a shipped fact: the plan re-points its alias at a new
     * release on its own schedule, and the picker must be able to preselect it. Defaults
     * to {@link ARK_DEFAULT_MODEL}.
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
    resolveAttachments?: () => AttachmentStore | undefined;
    onWarn?: (message: string) => void;
    onCredentialRejected?: (account: string) => void;
    /** Read one account's metered windows, so the streamer can steer away from a spent one. */
    readUsage?: ((account: string) => Promise<readonly UsageWindow[]>) | undefined;
    fetchFn?: FetchFn;
}
/** The adapter behind the `ark` route. */
export declare class ArkAdapter extends LlmAdapter {
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
     * one of its own.
     */
    private readonly fastVariants;
    constructor(options: ArkAdapterOptions);
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
    resolveModel(_provider: string, model: string): Promise<LlmResolvedModelInfo>;
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
    /** Translate an auth failure into the message a user can act on. */
    private refreshHint;
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
    private addedEntries;
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
     * Read the plan's live model list and adopt it as this route's catalog.
     *
     * The Coding Plan answers the OpenAI models shape on its own base, so the rows are
     * parsed exactly as the other routes parse theirs: what the listing names is what the
     * picker offers, with the display name, window, modalities and ladder it states. The
     * rows are also handed to {@link ArkAdapterOptions.saveListing}, so the next session
     * starts from this table instead of asking again — the Settings "refresh models"
     * action is what re-reads it.
     *
     * @param apiKey - bearer for the account.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    fetchCatalog(apiKey: string, signal?: AbortSignal): Promise<readonly string[] | undefined>;
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
/**
 * Read the plan's allowance.
 *
 * Two allowances exist, and both live on the **control plane** — a different host
 * from the data plane, reached with the STS AK/SK the SSO identity was exchanged
 * for (see `auth/ark-control.ts`) rather than with the plan API key:
 *
 * - the **subscription** periods (`GetCodingPlanUsage` → `coding_usage.periods[]`,
 *   each `{ label, used, total, percent, reset_at }`), which an account without a
 *   plan answers as `Status: Reclaimed`;
 * - the **free inference allowance** per model (`ListModelChargeItems` →
 *   `InferenceFreeUsage { Consumed, Total }`), which exists with no subscription.
 *
 * The free allowance is what a plan-less account actually has, so it is reported
 * even when the subscription is absent — otherwise the card would say "no quota"
 * while the account still had tokens to spend.
 *
 * @param input - the signed control-plane client.
 * @returns the windows the service stated, subscription first.
 */
export declare function readArkUsage(input: {
    credentials: ArkStsCredentials;
    fetchFn: FetchFn;
    signal?: AbortSignal;
}): Promise<UsageRead>;
