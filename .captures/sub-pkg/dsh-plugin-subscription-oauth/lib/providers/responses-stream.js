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
import { EMPTY_RESPONSE_CODE, LlmError } from '@deepseek-ai/dsh-llm';
import { allowanceScheduler } from '../auth/allowance-scheduler.js';
import { streamResponses } from '../translate/responses.js';
import { httpLlmError, idleWatchdog, mapFetchFailure, unknownModelReplacement } from './common.js';
/**
 * Minimum beat between dispatch attempts on one route, so a refused account is not
 * re-hit at once and a sweep across the pool does not become a burst. opencodex keeps
 * this discipline with a request pacer; this is the same idea narrowed to one plugin.
 */
export const DISPATCH_PACING_MS = 2_000;
/** Per-route last-dispatch instants, so each route paces its own attempts. */
const lastDispatchAt = new Map();
/**
 * Wait out a route's pacing beat before a dispatch attempt.
 *
 * @param route - the route key pacing its own attempts.
 * @param signal - the caller's abort signal.
 */
async function paceDispatch(route, signal) {
    const last = lastDispatchAt.get(route) ?? 0;
    const wait = last + DISPATCH_PACING_MS - Date.now();
    lastDispatchAt.set(route, Date.now());
    if (wait <= 0 || signal.aborted)
        return;
    await new Promise((resolve) => {
        const onAbort = () => { clearTimeout(timer); resolve(); };
        const timer = setTimeout(() => {
            signal.removeEventListener('abort', onAbort);
            resolve();
        }, wait);
        signal.addEventListener('abort', onAbort, { once: true });
    });
}
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
export function accountPoolHooks(input) {
    const { provider, tokens, readUsage } = input;
    return {
        accounts: () => tokens.accounts(),
        // Steer away from an account that has no allowance: read its live figures when a
        // reader is wired (so a 100 % window is avoided before it fails there), else fall
        // back to the remembered reset time from a past failure.
        isAccountSpent: async (account) => {
            if (readUsage !== undefined) {
                try {
                    const windows = await readUsage(account);
                    const spentWindow = windows.find((window) => (window.usedPercent ?? 0) >= 100);
                    if (spentWindow !== undefined) {
                        allowanceScheduler.noteReset(provider, account, spentWindow.resetsAt);
                        return true;
                    }
                    return false;
                }
                catch {
                    // A failed read is not a reason to avoid the account; fall through to memory.
                }
            }
            return allowanceScheduler.isSpentUntilReset(provider, account);
        },
        noteAccountSpent: (account, resetsAt) => { allowanceScheduler.noteReset(provider, account, resetsAt); },
        resetsAt: async (account) => allowanceScheduler.resetAt(provider, account),
    };
}
/**
 * Whether a refusal says "this model cannot serve you now", as opposed to "this
 * request is wrong".
 *
 * Only the first kind may be answered by another model. A 400 or an unparseable
 * body is a bug in what this plugin sent: silently re-sending it elsewhere would
 * hide the defect and produce an answer from a model the caller did not ask for.
 */
function isModelFallbackWorthy(status) {
    // Gone or renamed model, exhausted allowance, rate limit, provider trouble.
    return status === 402 || status === 404 || status === 408 || status === 429 || status >= 500;
}
/** One short reason for a log line. */
function reasonOf(error) {
    if (error instanceof LlmError)
        return `${error.failure.code}: ${error.message}`;
    return error instanceof Error ? error.message : String(error);
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
export async function* streamProviderResponses(options, input) {
    const { label, tokens, request } = input;
    const watchdog = idleWatchdog(options.signal, input.streamIdleTimeoutMs);
    let lastError;
    try {
        // A chain is resolved once per call, so a settings change can never make the
        // fallbacks disagree with the model the caller asked for.
        const models = input.models === undefined ? [options.model] : await input.models();
        if (models.length === 0) {
            throw new LlmError(`${label} has no enabled model to dispatch to`, EMPTY_RESPONSE_CODE);
        }
        // Stay on the default account until it refuses. opencodex keeps account affinity — a
        // request stays on one account and only moves after a 429 — because switching accounts
        // on every request is what reads to a provider as several accounts in play at once and
        // trips the safety gate. Reordering candidates here caused exactly that.
        let accounts = input.accounts === undefined
            ? undefined
            : [...await input.accounts()];
        if (accounts !== undefined && accounts.length > 1) {
            const defaultAccount = await tokens.defaultAccount();
            if (defaultAccount !== undefined) {
                accounts = [defaultAccount, ...accounts.filter((a) => a !== defaultAccount)];
            }
        }
        const tryAccounts = accounts === undefined || accounts.length === 0
            ? [undefined]
            : accounts;
        for (const accountKey of tryAccounts) {
            let token = await tokens.resolve(accountKey);
            // Pace each dispatch: a request right after a refused one is what turns a
            // transient safety check into a sustained block, so the streamer waits out a
            // short beat before each attempt (the same discipline opencodex keeps).
            await paceDispatch(label, watchdog.signal);
            // Track which accounts a spent-allowance already moved past, so a later model
            // in the chain does not re-try an account known to be exhausted.
            const spent = new Set();
            for (const [index, model] of models.entries()) {
                try {
                    if (spent.has(token.account))
                        continue;
                    let response = await request(options, model, token.accessToken, watchdog.signal);
                    if (response.status === 401 || response.status === 403) {
                        // Two different failures share this status: a credential the provider no
                        // longer accepts, and a model id it does not know. Only the first is answered
                        // by a token refresh — treating a model rename that way would refresh a
                        // healthy token and, worse, mark the account as needing a new sign-in. The
                        // body says which it is, so it is read before deciding, and an unknown model
                        // is retried once under the id the provider named.
                        const refusal = await httpLlmError(response, label);
                        const replacement = unknownModelReplacement(refusal.message);
                        if (replacement !== undefined) {
                            lastError = refusal;
                            input.onModelRenamed?.(model, replacement);
                            input.onFallback?.(model, replacement, reasonOf(refusal));
                            response = await request(options, replacement, token.accessToken, watchdog.signal);
                        }
                        else {
                            token = await tokens.resolve(token.account, true);
                            response = await request(options, model, token.accessToken, watchdog.signal);
                            if (response.status === 401 || response.status === 403) {
                                input.onCredentialRejected?.(token.account);
                            }
                        }
                    }
                    // A spent allowance moves to the next account (when the provider supports
                    // it), not to the next model: the model is fine, the account is not.
                    if (!response.ok
                        && input.isAllowanceExhausted !== undefined
                        && input.accounts !== undefined
                        && input.isAllowanceExhausted(response.status)
                        && tryAccounts.length > 1) {
                        spent.add(token.account);
                        // Remember when this account's allowance refills, so later requests go
                        // straight to another account until then instead of failing here again.
                        input.noteAccountSpent?.(token.account, await input.resetsAt?.(token.account));
                        const next = tryAccounts.find((candidate) => candidate !== undefined && !spent.has(candidate));
                        if (next !== undefined) {
                            lastError = await httpLlmError(response, label);
                            input.onAccountSwitch?.(token.account, next, reasonOf(lastError));
                            token = await tokens.resolve(next);
                            // Re-try this same model on the fresh account.
                            response = await request(options, model, token.accessToken, watchdog.signal);
                        }
                    }
                    if (!response.ok) {
                        const error = await httpLlmError(response, label);
                        if (models.length > 1 && isModelFallbackWorthy(response.status) && index < models.length - 1) {
                            const next = models[index + 1];
                            if (next !== undefined) {
                                lastError = error;
                                input.onFallback?.(model, next, reasonOf(error));
                                continue;
                            }
                        }
                        throw error;
                    }
                    if (response.body === null) {
                        throw new LlmError(`${label} returned no response body`, EMPTY_RESPONSE_CODE);
                    }
                    yield* streamResponses(response.body, () => { watchdog.pulse(); });
                    return;
                }
                catch (error) {
                    // A transport failure on a chain is also worth another model.
                    const transport = error instanceof TypeError || error.name === 'AbortError';
                    if (transport && options.signal?.aborted !== true && index < models.length - 1) {
                        const next = models[index + 1];
                        if (next !== undefined) {
                            lastError = error;
                            input.onFallback?.(model, next, reasonOf(error));
                            continue;
                        }
                    }
                    throw input.describeFailure === undefined ? error : input.describeFailure(error);
                }
            }
        }
        throw lastError ?? new LlmError(`${label} served none of the requested models`, EMPTY_RESPONSE_CODE);
    }
    catch (error) {
        const described = input.describeFailure === undefined ? error : input.describeFailure(error);
        throw mapFetchFailure(label, described, watchdog, options.signal);
    }
    finally {
        watchdog.stop();
    }
}
