/**
 * Shared provider plumbing: an idle watchdog for streams, wire-error
 * classification, and transport-failure mapping.
 *
 * @module dsh-plugin-subscription-oauth/providers/common
 */
import { LlmError } from '@deepseek-ai/dsh-llm';
/** Watchdog handle used around one streaming request. */
export interface IdleWatchdog {
    /** Abort signal to hand to `fetch`; also fires when the caller's signal does. */
    readonly signal: AbortSignal;
    /** Report inbound transport activity, resetting the idle timer. */
    pulse(): void;
    /** Stop the timer; safe to call more than once. */
    stop(): void;
    /** Whether the idle budget (rather than the caller) aborted the request. */
    readonly timedOut: boolean;
}
/**
 * Guard one stream with an inactivity budget.
 *
 * The timer aborts the request when no chunk arrives for `idleMs`, so a dead
 * connection fails the turn instead of hanging it forever.
 *
 * @param signal - the caller's cancellation signal, if any.
 * @param idleMs - inactivity budget in milliseconds; a non-positive value disables it.
 * @returns the watchdog.
 */
export declare function idleWatchdog(signal: AbortSignal | undefined, idleMs: number): IdleWatchdog;
/**
 * Turn a non-2xx provider response into an LlmError carrying the status and
 * any rate-limit hint the headers disclosed.
 *
 * @param response - the failed response.
 * @param label - human name of the endpoint, used in the message.
 * @returns the error to throw.
 */
export declare function httpLlmError(response: Response, label: string): Promise<LlmError>;
/** Whether a failure means the stored credential no longer works. */
export declare function isMissingOrInvalidCredential(error: unknown): boolean;
/**
 * The model id a provider's failure names as the one to use instead.
 *
 * A 401/403 is not always about the credential: a provider also answers it when the
 * request names a model id the account does not serve — Kimi says `Your model id does
 * not exist, recognized as other:k3[1m]. Please set model id as \`k3\``. That message
 * is the endpoint's own answer to "which id should I have used", so it is worth acting
 * on rather than reporting: refreshing a healthy token, or marking the account as
 * needing a new login, would both be wrong answers to a model rename.
 *
 * @param message - the failure text.
 * @returns the replacement id, or undefined when the failure is not about the model id.
 */
export declare function unknownModelReplacement(message: string): string | undefined;
/**
 * Map a thrown transport failure onto a harness error, distinguishing caller
 * cancellation from an idle timeout from a genuine network fault.
 *
 * @param label - human name of the endpoint.
 * @param error - the thrown value.
 * @param watchdog - the request's watchdog, when one was installed.
 * @param signal - the caller's signal.
 * @returns the error to throw.
 */
export declare function mapFetchFailure(label: string, error: unknown, watchdog?: Pick<IdleWatchdog, 'timedOut'>, signal?: AbortSignal): unknown;
