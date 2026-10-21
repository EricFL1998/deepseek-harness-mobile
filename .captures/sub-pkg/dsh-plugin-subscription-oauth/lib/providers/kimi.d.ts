/**
 * The `kimi` provider route: Kimi Code model catalog, request assembly over
 * the OpenAI Responses wire, and streaming.
 *
 * @module dsh-plugin-subscription-oauth/providers/kimi
 */
import { LlmAdapter } from '@deepseek-ai/dsh-llm';
import type { GenerateOptions, LlmModelInfo, LlmProviderInfo, LlmResolvedModelInfo, StreamChunk } from '@deepseek-ai/dsh-llm';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
import type { FetchFn } from '../http.js';
import type { TokenManager } from '../auth/tokens.js';
import type { SessionBase } from '../auth/store.js';
import type { UsageWindow } from '../protocol.js';
import type { CatalogEntry } from './catalog.js';
/** One catalog entry exposed as a selectable model. */
export type KimiModelEntry = CatalogEntry;
/**
 * A stored Kimi Code login.
 *
 * The OAuth host and API base are recorded per account so a later refresh uses
 * the same endpoints the login did, even if the plugin configuration changes.
 */
export interface KimiSession extends SessionBase {
    /** OAuth host the tokens were issued by. */
    oauthHost: string;
    /** API base recorded at login time (diagnostics only). */
    apiBase?: string;
}
/**
 * Coerce one decoded credential entry into a Kimi session.
 * @param value - decoded JSON value for one account.
 * @returns the session, or undefined when required fields are missing.
 */
export declare function parseKimiSession(value: unknown): KimiSession | undefined;
/** Constructor dependencies for {@link KimiAdapter}. */
export interface KimiAdapterOptions {
    /** Resolved Kimi Code API base (`<base>/responses`). */
    apiBase: string;
    /** Whether the live `/models` catalog is consulted when logged in. */
    discovery: boolean;
    /** Inactivity budget before a stalled stream is abandoned. */
    streamIdleTimeoutMs: number;
    /** OAuth token manager owning refresh and invalidation. */
    tokens: TokenManager<KimiSession>;
    /** Warning sink for discovery failures that fall back to the static catalog. */
    onWarn?: (message: string) => void;
    /** Resolve the attachment service per request, so a late service start still works. */
    resolveAttachments?: () => AttachmentStore | undefined;
    /**
     * Client-identity headers (`X-Msh-*` + `User-Agent`) for this machine.
     * Resolved per request because the device id file is read lazily.
     */
    identityHeaders?: () => Promise<Record<string, string>>;
    /** Fetch implementation; defaults to the proxy-aware fetch. */
    fetchFn?: FetchFn;
    /** Called when a call proved the stored credential unusable. */
    onCredentialRejected?: (account: string) => void;
    /**
     * Read one account's metered windows, so the streamer can steer away from an account
     * whose allowance is spent before it fails there.
     *
     * Resolved per request; absence means the streamer only learns an account is spent by
     * failing against it once.
     */
    readUsage?: (account: string) => Promise<readonly UsageWindow[]>;
    /** Extra model ids from the plugin configuration, appended to the catalog. */
    extraModels?: readonly string[];
    /**
     * Model this route preselects, and therefore always offers.
     *
     * Configuration rather than a shipped fact: a subscription that re-points its
     * alias at a new release follows the config instead of this build's release
     * cadence. Defaults to {@link KIMI_DEFAULT_MODEL}.
     */
    defaultModel?: string;
    /**
     * The user's model rank for this route, most preferred first.
     *
     * Read per catalog query, so a drag-and-drop re-order takes effect at the
     * picker's next refresh without rebuilding the adapter.
     */
    modelOrder?: () => Promise<readonly string[]>;
    /**
     * Model ids the session model picker must not offer.
     *
     * Read per catalog query, so a settings change takes effect at the picker's
     * next refresh without rebuilding the adapter. A hidden model stays
     * resolvable: an existing session that already selected it keeps working.
     */
    hiddenModels?: () => Promise<ReadonlySet<string>>;
    /**
     * Model ids the user added by hand.
     *
     * Read per catalog query, so the add button takes effect at the picker's next
     * refresh. A hand-added id is published with conservative metadata: the
     * endpoint is the authority on what it serves, not this build.
     */
    userModels?: () => Promise<readonly string[]>;
    /**
     * The payload of the endpoint's last live listing, as the cache kept it.
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
/** Kimi Code adapter serving the `kimi` provider route. */
export declare class KimiAdapter extends LlmAdapter {
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
     * Fast-variant wire id per picker row, for the composer's quick-mode button.
     *
     * Derived from the listing: `kimi-for-coding-highspeed` is a row of its own there,
     * and pairs with `kimi-for-coding` because its name is that of a listed model plus
     * the provider's own fast word.
     */
    private readonly fastVariants;
    /** Ids the user added by hand, cached so the sync catalog can name them. */
    private userAddedIds;
    /**
     * Model ids the endpoint renamed during this session, and what each became.
     *
     * Learned from the endpoint's own refusal (`Please set model id as \`k3\``), so a
     * spelling retired after this build shipped is repaired without a plugin update. The
     * first turn pays one refused request; every later turn goes straight to the current id.
     */
    private readonly renamed;
    /** Tools already reported as flattened, so each is stated once. */
    private readonly flattenedTools;
    constructor(options: KimiAdapterOptions);
    /**
     * Adopt the rows of one live listing as this route's catalog.
     *
     * Everything the picker shows is derived here from the endpoint's own answer: the
     * rows that become picker entries, the fast variant that pairs with each row, and
     * the image model that is called by an agent rather than chosen. Nothing about
     * which models exist, or what each one is, is decided by this build.
     *
     * Fast variants ride with their base row instead of becoming rows of their own: they
     * share the row's visibility switch and are reached by the composer's quick-mode
     * button. A variant is recognized by its own name — the provider's fast word appended
     * to a listed model's id — so no curated alias map is needed to pair them.
     *
     * @param rows - decoded rows of the endpoint's listing, in the endpoint's order.
     * @returns the entries to publish, in picker order.
     */
    private adoptListing;
    /**
     * Put this route's stable label on one entry, when the id has one.
     *
     * The listing states what the model *is*; the label states what the *id* is, which only
     * this route knows (see {@link STABLE_MODEL_LABELS}).
     *
     * @param entry - the entry built from the listing.
     * @returns the entry, relabelled when its id has a stable label.
     */
    private labelled;
    /**
     * Read the cached listing, once, so the picker works without asking the endpoint.
     *
     * Called before the first catalog query. A read failure is a cache miss, never a
     * failure: the route simply has no listing until one is fetched.
     */
    hydrate(): Promise<void>;
    /** The fast pair one picker id belongs to, from either side. */
    fastPairOf(id: string): {
        baseId: string;
        wireId: string;
    } | undefined;
    /**
     * Whether this route has read a model listing yet.
     *
     * A variant is derived from the listing, so until one has been read "this model has no
     * variant" and "the listing has not arrived" look identical from outside; a caller that
     * asks once (the composer's quick-mode button) needs to tell them apart.
     */
    hasListing(): boolean;
    /**
     * Tool schemas in the shape Moonshot accepts: no `$ref`, and under its size
     * ceiling. A declaration too large to send keeps its arguments, and that is
     * reported once per tool rather than on every turn.
     */
    private moonshotTools;
    providerInfo(provider: string): LlmProviderInfo;
    providerRetryPolicy(): undefined;
    listModels(_provider: string): Promise<readonly LlmModelInfo[]>;
    /** The image-generation model id, when discovery reported one. */
    imageModelId(): string | undefined;
    /** The fast/lightspeed variant wire id for a picker row. */
    fastVariant(baseId: string): string | undefined;
    /** The ids the picker must currently omit. */
    private hidden;
    resolveModel(_provider: string, model: string): Promise<LlmResolvedModelInfo>;
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
    /** Translate an auth failure into the message a user can act on. */
    private refreshHint;
    /**
     * The catalog the route serves: its own entries, then the ids the user added
     * by hand. The two are built separately so the hand-added check can look at
     * the shipped list without recursing.
     */
    private catalog;
    /**
     * The catalog to present: the models the endpoint's listing named, in its own order,
     * each with the metadata that listing stated, then the configured extras.
     *
     * There is no shipped roster to fall back to. A listing is read once and cached (see
     * {@link hydrate} and {@link fetchCatalog}), and that cached table is what the picker
     * shows until the next read, so a model released, renamed or retired server-side is
     * reflected without a plugin update or a request per refresh.
     */
    private shippedCatalog;
    /** Resolve a requested model id onto the entry the endpoint's listing described. */
    private entryFor;
    /**
     * The model one wire id names, when the endpoint has retired the spelling.
     *
     * Consulted only for an id the current listing does not serve, so an id the endpoint
     * itself reports is always dispatched exactly as reported.
     *
     * @param model - the requested model id.
     * @returns the current spelling, or undefined when the id is not a known retirement.
     */
    private repairModelId;
    /** Re-read the ids the user added by hand. */
    private syncUserModels;
    /**
     * The entries for ids the user added by hand, in the order they added them.
     *
     * Published without shipped metadata: this build has never seen the model, so
     * the endpoint's own defaults are the honest description.
     */
    private addedEntries;
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
    /** The catalog, for the Settings page's model list. */
    catalogEntries(): readonly KimiModelEntry[];
    /** What this route itself serves, without the ids added by hand. */
    ownCatalogEntries(): readonly KimiModelEntry[];
    /** Build and send one request. */
    private request;
    /** Client-identity headers for this machine, when configured. */
    private identity;
    /** Resolve image blocks through the attachment service, when it is present. */
    private imageResolver;
    /**
     * Read the endpoint's model listing and adopt it as this route's catalog.
     *
     * This is the one place a model table is produced, and it is the endpoint's own
     * answer: what the listing names is what the picker offers, with the display name,
     * context window, modalities and reasoning ladder the listing states. The rows are
     * also handed to {@link KimiAdapterOptions.saveListing}, so the next session starts
     * from this table instead of asking again — the Settings "refresh models" action is
     * what re-reads it.
     *
     * @param accessToken - bearer for the account.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    fetchCatalog(accessToken: string, signal?: AbortSignal): Promise<readonly string[] | undefined>;
}
