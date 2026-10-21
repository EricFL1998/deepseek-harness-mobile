/**
 * The shared request/stream dance for providers that speak the OpenAI Responses
 * wire (Kimi Code, xAI/Grok).
 *
 * Everything route-specific stays with the route: building the URL, assembling
 * the body, and its headers. What is shared is the part that is easy to get
 * subtly wrong twice — resolving a usable token, retrying exactly once after a
 * rejected credential, walking a model chain when one model is unavailable,
 * mapping transport and HTTP failures, guarding an idle stream, and translating
 * the SSE body into harness chunks.
 *
 * @module dsh-plugin-subscription-oauth/providers/responses-stream
 */
import type { GenerateOptions, StreamChunk } from '@deepseek-ai/dsh-llm';
import type { SessionBase } from '../auth/store.js';
import type { TokenManager } from '../auth/tokens.js';
import type { UsageWindow } from '../protocol.js';
/**
 * Minimum beat between dispatch attempts on one route, so a refused account is not
 * re-hit at once and a sweep across the pool does not become a burst. opencodex keeps
 * this discipline with a request pacer; this is the same idea narrowed to one plugin.
 */
export declare const DISPATCH_PACING_MS = 2000;
/**
 * Build the cross-account hooks one route needs to draw on its whole account pool.
 *
 * Every Responses-wire route (Kimi, Grok, GLM, Ark) meters per account and can run out;
 * this wires the three account hooks identically, so a route only supplies how to read
 * one account's allowance and its provider id. A route with no `readUsage` still rotates
 * accounts on failure, it just cannot predict exhaustion before the first failure.
 *
 * @param provider - the provider route id, the scheduler's key.
 * @param tokens - the route's token manager, enumerates accounts.
 * @param readUsage - read one account's metered windows, when wired.
 */
export declare function accountPoolHooks<S extends SessionBase>(input: {
    provider: string;
    tokens: TokenManager<S>;
    readUsage?: ((account: string) => Promise<readonly UsageWindow[]>) | undefined;
}): Pick<ResponsesStreamInput<S>, 'accounts' | 'isAccountSpent' | 'noteAccountSpent' | 'resetsAt'>;
/** Constructor inputs for {@link streamProviderResponses}. */
export interface ResponsesStreamInput<S extends SessionBase> {
    /** Endpoint name used in failure messages, e.g. `Kimi API`. */
    label: string;
    /** Token manager owning refresh. */
    tokens: TokenManager<S>;
    /** Send one request for one model with the given access token. */
    request(options: GenerateOptions, model: string, accessToken: string, signal: AbortSignal): Promise<Response>;
    /** Inactivity budget before the stream is abandoned. */
    streamIdleTimeoutMs: number;
    /**
     * The models to try, most preferred first, resolved lazily.
     *
     * Omitted means "only the requested model". `auto` supplies the user's whole
     * ranked chain here; a concrete id supplies just itself.
     */
    models?: () => Promise<readonly string[]>;
    /** Called when a call proved the stored credential unusable. */
    onCredentialRejected?: (account: string) => void;
    /**
     * Called when the endpoint answered a request by naming the model id to use instead.
     *
     * The request is retried with that id, so the turn still succeeds; the caller records
     * the rename so the next turn goes straight to the current id instead of learning it
     * again from a refused request.
     */
    onModelRenamed?: (from: string, to: string) => void;
    /** Rewrite a thrown failure before it is mapped, e.g. to name the fix. */
    describeFailure?(error: unknown): unknown;
    /** Called when a model in the chain was skipped, with the reason. */
    onFallback?: (from: string, to: string, reason: string) => void;
    /**
     * Whether a 402/429 on this provider means "this account's allowance is spent",
     * so the call should move to the next account rather than fail.
     *
     * When omitted, an exhausted allowance fails the call (the old behaviour). When
     * set, the caller enumerates the provider's other accounts and retries each until
     * one serves the request or every account is spent.
     */
    isAllowanceExhausted?: (status: number) => boolean;
    /** Enumerate the provider's accounts, most preferred first; enables cross-account retry. */
    accounts?: () => Promise<readonly string[]>;
    /** Called when a call moved to another account because the first was spent. */
    onAccountSwitch?: (from: string, to: string, reason: string) => void;
    /**
     * Whether one account's allowance is best not used for a fresh request right now.
     *
     * The caller supplies this to steer a request away from an account that is nearly
     * spent (or already spent until a remembered reset), so the request starts on a fuller
     * account instead of failing once to find out. It may read the account's live figures;
     * an account flagged here is tried last, not skipped, so a lone account still serves.
     */
    isAccountSpent?: (account: string) => Promise<boolean>;
    /** Remember that an account's allowance is spent until a given reset moment. */
    noteAccountSpent?: (account: string, resetsAt: number | undefined) => void;
    /** The moment one account's allowance is known to reset, when it was recorded. */
    resetsAt?: (account: string) => Promise<number | undefined>;
}
/**
 * Run one model call over the Responses wire, walking a model chain.
 *
 * The chain is what `auto` means to a user: request the most preferred model,
 * and — only while nothing has been streamed yet — fall through to the next one
 * when the provider says that model cannot serve this request. Once any chunk has
 * been produced the turn belongs to that model, so a later failure is reported
 * rather than papered over by a second model's text.
 *
 * A 401/403 triggers exactly one forced token refresh and retry of the same
 * model before the chain advances; if the retry is refused too, the credential is
 * reported as dead so the caller can ask the user to sign in again.
 *
 * @param options - the fully assembled request for this call.
 * @param input - the route's transport hooks and budgets.
 * @yields harness stream chunks in contract order.
 */
export declare function streamProviderResponses<S extends SessionBase>(options: GenerateOptions, input: ResponsesStreamInput<S>): AsyncIterable<StreamChunk>;
