/**
 * The WorkBuddy China wire adapter serving the `workbuddy` route.
 *
 * WorkBuddy chats are chat completions at `https://copilot.tencent.com/v2/chat/completions`,
 * streamed only, and the API refuses a request whose User-Agent is not
 * `WorkBuddy/<version>`. The model table this route publishes is the console's own
 * listing — `GET /console/enterprises/<enterpriseId || personal>/models`, the route
 * WorkBuddy's web console reads — fetched once and kept in this plugin's cache (see
 * {@link WorkBuddyAdapter.hydrate} and {@link WorkBuddyAdapter.fetchCatalog}).
 * {@link WORKBUDDY_CATALOG} is the cold-start seed used until a listing has been read,
 * the fallback when a read fails, and the metadata overlay for a row the listing
 * describes only partly, so a model released, renamed or retired server-side reaches the
 * picker without a plugin update and without a request per refresh.
 * Adapted from magpie's `workbuddy` for this plugin's contracts.
 *
 * @module dsh-plugin-subscription-oauth/providers/workbuddy
 */
import { LlmAdapter, type GenerateOptions, type LlmModelInfo, type LlmProviderInfo, type LlmResolvedModelInfo, type StreamChunk } from '@deepseek-ai/dsh-llm';
import { type FetchFn } from '../http.js';
import type { TokenManager } from '../auth/tokens.js';
import type { CatalogEntry } from './catalog.js';
import { type WorkBuddySession } from '../auth/workbuddy-flow.js';
/**
 * Code for a turn that produced no content and no tool call.
 *
 * WorkBuddy answers a request whose context has grown past what the model serves with a
 * single empty frame (a bare role, then `[DONE]`): the model generated nothing, but the
 * wire reads as a clean `stop`, so the turn "completes" blank. That is not a real answer;
 * surfacing it lets the harness retry or fall back instead of showing an empty reply.
 */
export declare const WORKBUDDY_EMPTY_RESPONSE_CODE = "WORKBUDDY_EMPTY_RESPONSE";
/** The `workbuddy` provider route key (China build). */
export declare const WORKBUDDY_PROVIDER_ID = "workbuddy";
/** Human name for the route and its cards. */
export declare const WORKBUDDY_PROVIDER_NAME = "WorkBuddy";
/** Where the Settings page sends someone to get an account. */
export declare const WORKBUDDY_CODE_URL = "https://copilot.tencent.com";
/**
 * The model the picker preselects.
 *
 * The console's own default, and a row its listing actually serves, so the page's
 * "recommended" marker never names a model the picker cannot offer. The previous value
 * (`hy4-preview-f`) was a seed-table spelling the live listing does not serve — it offers
 * `hy4-preview` — which is the staleness this route no longer keeps in its catalog.
 */
export declare const WORKBUDDY_DEFAULT_MODEL = "auto";
/** Context advertised for a listed id neither the listing nor the seed table describes. */
export declare const WORKBUDDY_FALLBACK_CONTEXT_WINDOW = 168000;
/**
 * The console route that lists the models one account may use.
 *
 * The scope segment is the enterprise the session is attributed to, or `personal` for an
 * account that states none. This is the route WorkBuddy's own console reads; it is not
 * one of the plugin's `/v2` API routes, which is why probing those found nothing.
 *
 * @param enterpriseId - the session's enterprise, when it has one.
 * @returns the absolute listing URL.
 */
export declare function workbuddyModelsUrl(enterpriseId?: string): string;
/** One signed-in WorkBuddy session, as stored. */
export interface WorkBuddyStored {
    accessToken: string;
    refreshToken?: string;
    expiresAt: number;
    accountId?: string;
    email?: string;
    uid?: string;
    enterpriseId?: string;
    domain?: string;
    tokenType?: string;
    refreshExpiresAt?: number;
}
/**
 * The seed table the route falls back to when no listing has been read (see
 * {@link CN_MODELS}).
 *
 * A table this build ships, never the roster: WorkBuddy's own listing decides which
 * models exist, and each row's own figures decide what they are. This is only the
 * cold-start membership and the overlay for what a listing leaves unsaid.
 */
export declare const WORKBUDDY_CATALOG: readonly CatalogEntry[];
/** Constructor dependencies for {@link WorkBuddyAdapter}. */
export interface WorkBuddyAdapterOptions {
    /** Whether the console's live model listing is consulted. */
    discovery: boolean;
    streamIdleTimeoutMs: number;
    tokens: TokenManager<WorkBuddySession>;
    onWarn?: (message: string) => void;
    hiddenModels?: () => Promise<ReadonlySet<string>>;
    userModels?: () => Promise<readonly string[]>;
    /**
     * The listing WorkBuddy's console answered last, as this plugin's cache kept it.
     *
     * Read once per session, before the first catalog query, so the picker offers the real
     * models without asking the console anything. A route that fetched once and cached the
     * answer has no reason to fetch again on every refresh; the Settings "refresh models"
     * action is what re-reads it.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
    /** Where per-account credit spends accumulate; beside the credential file. */
    spendFile?: string;
    fetchFn?: FetchFn;
}
/** A message content's signature: its text, and a digest of every part that isn't text. */
export declare function signatureOf(content: unknown): string;
/** The conversation a chat belongs to: the session the harness names, else the first user message. */
export declare function conversationKey(messages: Record<string, unknown>[], session: string | undefined): string;
/** The four ids one chat carries, named as WorkBuddy's own client names them. */
export interface ConversationIds {
    message: string;
    turn: string;
    conversation: string | undefined;
}
/** Derive the conversation ids one request's body carries. */
export declare function conversationIdsFor(body: Record<string, unknown>, options: GenerateOptions): ConversationIds;
/** The WorkBuddy China wire adapter serving the `workbuddy` route. */
export declare class WorkBuddyAdapter extends LlmAdapter {
    private readonly options;
    private readonly fetchFn;
    /**
     * The catalog this route serves: entries derived from WorkBuddy's own listing, either
     * the one just read or the one the cache kept from the last read.
     *
     * Undefined means neither source has produced a listing yet, so the seed table
     * ({@link WORKBUDDY_CATALOG}) is what the picker shows.
     */
    private live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    private hydrated;
    constructor(options: WorkBuddyAdapterOptions);
    providerInfo(provider: string): LlmProviderInfo;
    /**
     * Adopt one listing as this route's catalog.
     *
     * Everything the picker shows is derived here from WorkBuddy's own answer: which models
     * exist (the account's `cli` agent when the listing states one), in the order that
     * listing states them, each with the name, window, modalities and reasoning rung its row
     * states. The seed table is consulted only for what a row leaves unsaid, so a live
     * figure is never overridden by a shipped guess.
     *
     * @param payload - the decoded listing, or the `{models, agents}` object a cache kept.
     * @returns the entries to publish, in picker order.
     */
    private adoptListing;
    /**
     * Read the cached listing, once, so the picker works without asking the console.
     *
     * Called before the first catalog query. A read failure is a cache miss, never a
     * failure: the route falls back to its seed table until a listing is fetched, so a
     * broken cache can never empty the picker.
     */
    hydrate(): Promise<void>;
    listModels(_provider: string): Promise<readonly LlmModelInfo[]>;
    private hidden;
    /**
     * The catalog to present: the models WorkBuddy's listing named, in the order its own
     * console states them, each with the metadata that listing stated.
     *
     * The seed table is the membership only while no listing has been read, and the metadata
     * overlay for a row the listing describes partly. It is never allowed to add a row back:
     * a model the console stopped serving must leave the picker without a plugin update,
     * which is the whole point of reading the listing instead of shipping a roster.
     */
    private shippedCatalog;
    /** The catalog the Settings list reads. */
    catalogEntries(): readonly CatalogEntry[];
    /** The entries the route itself serves, without hand-added extras. */
    ownCatalogEntries(): readonly CatalogEntry[];
    /**
     * Adopt the ids a caller reports as the served set, without a listing.
     *
     * {@link fetchCatalog} already derived the entries from the listing it read, and the
     * controller reports those same ids back here; the entries it recorded are kept, so a
     * live name, window or rung is not replaced by the seed table's. A caller that only has
     * ids (a test, another discovery path) gets the seed table's metadata where it knows the
     * id and conservative entries otherwise.
     *
     * @param ids - model ids the caller reports.
     */
    discover(ids: readonly string[]): void;
    resolveModel(_provider: string, model: string): Promise<LlmResolvedModelInfo>;
    /**
     * Read WorkBuddy's own console listing and adopt it as this route's catalog.
     *
     * This is the one place a model table is produced, and it is WorkBuddy's own answer: the
     * account's `cli` agent decides which models are available, and each row states its own
     * name, window, modalities and reasoning rung (see {@link adoptListing}). The payload is
     * also handed to {@link WorkBuddyAdapterOptions.saveListing}, so the next session starts
     * from this table instead of asking again — the Settings "refresh models" action is what
     * re-reads it, and a picker query never does.
     *
     * A failed or empty read keeps the catalog the route already had, and says so once.
     *
     * @param accessToken - bearer for the account.
     * @param enterpriseId - the enterprise to read; taken from the stored session when omitted.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    fetchCatalog(accessToken: string, enterpriseId?: string, signal?: AbortSignal): Promise<readonly string[] | undefined>;
    /**
     * The stored session one access token belongs to.
     *
     * The chat signs with the resolved token, but the request still needs the session's
     * attribution fields (`uid`, `enterpriseId`, `domain`). They are read back by matching
     * the resolved token against each stored account; a token that matches none (already
     * rotated away) leaves the caller to sign with the bare token.
     */
    private sessionFor;
    providerRetryPolicy(): undefined;
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
    /**
     * One chat-completions POST, named the way WorkBuddy's own client names a chat: the
     * message, the request, the turn, and the conversation. Without these the gateway
     * counts the request under no client and can cut the stream.
     */
    private post;
    /** The stored session's fields as the token the request signs with. */
    private tokenOf;
    private requestBody;
    /**
     * Mend the tool schemas the way WorkBuddy's model gateway accepts.
     *
     * A tool name may hold only letters, digits, `_`, `-`; another character is refused, and
     * a schema with a union at the parameter root or a `\0` escape is turned away by the
     * model. Each unsafe name is made safe and the safe→original map returned to the caller
     * so the model's calls can be renamed back in the answer. Adapted from magpie's fitTools.
     *
     * @param tools - the declared tool schemas, when any.
     * @param history - the request's assistant/tool turns, renamed in place to the safe names.
     * @returns the mended wire tools, or undefined when there are none.
     */
    private fittedTools;
    /**
     * Restore the original tool names the model was given, in one parsed chunk.
     *
     * @param value - a decoded SSE payload.
     * @param names - the safe→original map the request that produced this stream mended.
     * @returns whether any name changed.
     */
    private namedBack;
    /** The system prompt: the caller's, else WorkBuddy's default. */
    private systemPrompt;
    private toChatMessage;
    /** Turn a non-2xx into an LlmError, naming WorkBuddy's specific refusals. */
    private errorOf;
    /**
     * Reassemble whole SSE `data:` payloads from decoded text.
     *
     * The gateway may deliver one SSE event over several network reads, and may also pack
     * several events (or a heartbeat comment) into a single read. The previous reader split
     * on blank lines and joined every `data:` line with `\n`, so a frame that straddled a
     * read boundary was cut in half and dropped by `JSON.parse` — and a dropped tool-call
     * fragment left its block forever without a `block-end`, so the turn never finished.
     * DeepSeek streams a tool call's arguments as one-character frames, which made the cut
     * frequent and the stall look random.
     *
     * This keeps the leftover of the previous read and walks complete lines instead. A
     * frame is yielded only once it parses as one JSON value, so a value split across reads
     * (and one packed into a single read) both reassemble; `[DONE]` is surfaced verbatim.
     *
     * @param chunk - newly decoded text.
     * @param carry - the leftover partial line, updated in place.
     * @returns the whole payloads found, in wire order.
     */
    private ssePayloads;
    private readStream;
}
