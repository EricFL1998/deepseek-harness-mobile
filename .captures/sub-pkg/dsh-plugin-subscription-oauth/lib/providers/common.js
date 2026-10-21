/**
 * Shared provider plumbing: an idle watchdog for streams, wire-error
 * classification, and transport-failure mapping.
 *
 * @module dsh-plugin-subscription-oauth/providers/common
 */
import { errorChain, LlmError } from '@deepseek-ai/dsh-llm';
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
export function idleWatchdog(signal, idleMs) {
    const controller = new AbortController();
    let timedOut = false;
    let timer;
    const onCallerAbort = () => controller.abort(signal?.reason);
    if (signal !== undefined) {
        if (signal.aborted)
            controller.abort(signal.reason);
        else
            signal.addEventListener('abort', onCallerAbort, { once: true });
    }
    const arm = () => {
        if (idleMs <= 0)
            return;
        timer = setTimeout(() => {
            timedOut = true;
            controller.abort(new Error(`Kimi stream idle for more than ${idleMs}ms`));
        }, idleMs);
        timer.unref?.();
    };
    const clear = () => {
        if (timer !== undefined)
            clearTimeout(timer);
        timer = undefined;
    };
    const stop = () => {
        clear();
        signal?.removeEventListener('abort', onCallerAbort);
    };
    arm();
    return {
        signal: controller.signal,
        pulse: () => {
            clear();
            arm();
        },
        stop,
        get timedOut() {
            return timedOut;
        },
    };
}
/** Read a `retry-after` header as an absolute delay in milliseconds. */
function retryAfterMs(response) {
    const raw = response.headers.get('retry-after');
    if (raw === null)
        return undefined;
    const seconds = Number(raw);
    if (Number.isFinite(seconds) && seconds >= 0)
        return Math.round(seconds * 1000);
    const at = Date.parse(raw);
    if (!Number.isNaN(at))
        return Math.max(0, at - Date.now());
    return undefined;
}
/** Provider-neutral failure code for one HTTP status. */
function statusCode(status) {
    if (status === 401 || status === 403)
        return 'INVALID_CREDENTIAL';
    if (status === 404)
        return 'MODEL_NOT_FOUND';
    if (status === 429)
        return 'RATE_LIMIT';
    if (status === 400 || status === 422)
        return 'INVALID_REQUEST';
    if (status >= 500)
        return 'SERVER';
    return 'HTTP_ERROR';
}
/** Best-effort provider message out of an error response body. */
async function errorBodyMessage(response) {
    try {
        const text = await response.text();
        if (text.trim() === '')
            return undefined;
        try {
            const parsed = JSON.parse(text);
            if (typeof parsed === 'object' && parsed !== null) {
                const record = parsed;
                const error = record.error;
                if (typeof error === 'object' && error !== null) {
                    const errorRecord = error;
                    const message = String(errorRecord.message ?? '').trim();
                    // Google puts the offending field in details[].fieldViolations, which the bare
                    // message omits; surface it so a 400 names what to fix instead of "invalid argument".
                    const detail = extractFieldViolations(errorRecord.details);
                    // A blocked account's message names the gate but not the way through it, so the
                    // link Google attached rides with the message, labelled as what to do with it.
                    const action = extractActionUrl(errorRecord.details);
                    const suffix = action === undefined ? '' : ` — verify here: ${action}`;
                    if (detail !== undefined)
                        return `${message} — ${detail}${suffix}`;
                    if (message !== '')
                        return `${message}${suffix}`;
                }
                if (typeof record.message === 'string' && record.message.trim() !== '')
                    return record.message.trim();
                if (typeof error === 'string' && error.trim() !== '')
                    return error.trim();
            }
        }
        catch {
            // Not JSON: fall through to the raw body.
        }
        return text.slice(0, 400).trim();
    }
    catch {
        return undefined;
    }
}
/**
 * The action URL inside a Google RPC error's `details` list, when present.
 *
 * Google answers a blocked account with an `ErrorInfo` whose metadata carries the link
 * the user has to open (and mirrors it as a `Help` entry). The bare message — "Verify
 * your account to continue." — is not actionable on its own: the user cannot tell what
 * to verify or where, so the link travels with the message.
 */
function extractActionUrl(details) {
    if (!Array.isArray(details))
        return undefined;
    let help;
    for (const entry of details) {
        if (typeof entry !== 'object' || entry === null)
            continue;
        const record = entry;
        const metadata = record.metadata;
        if (typeof metadata === 'object' && metadata !== null) {
            const url = metadata.validation_url;
            if (typeof url === 'string' && url.trim() !== '')
                return url.trim();
        }
        const links = record.links;
        if (!Array.isArray(links))
            continue;
        for (const link of links) {
            if (typeof link !== 'object' || link === null)
                continue;
            const linkRecord = link;
            const url = linkRecord.url;
            const description = linkRecord.description;
            const verifying = typeof description === 'string' && /verif/i.test(description);
            if (verifying && typeof url === 'string' && url.trim() !== '' && help === undefined)
                help = url.trim();
        }
    }
    return help;
}
/** The offending-field text inside a Google RPC error's `details` list, when present. */
function extractFieldViolations(details) {
    if (!Array.isArray(details))
        return undefined;
    const out = [];
    for (const entry of details) {
        if (typeof entry !== 'object' || entry === null)
            continue;
        const violations = entry.fieldViolations;
        if (!Array.isArray(violations))
            continue;
        for (const violation of violations) {
            if (typeof violation !== 'object' || violation === null)
                continue;
            const field = violation.field;
            const description = violation.description;
            if (typeof description === 'string' && description.trim() !== '') {
                out.push(typeof field === 'string' && field.trim() !== '' ? `${field}: ${description.trim()}` : description.trim());
            }
        }
    }
    return out.length === 0 ? undefined : out.join('; ');
}
/**
 * Turn a non-2xx provider response into an LlmError carrying the status and
 * any rate-limit hint the headers disclosed.
 *
 * @param response - the failed response.
 * @param label - human name of the endpoint, used in the message.
 * @returns the error to throw.
 */
export async function httpLlmError(response, label) {
    const detail = await errorBodyMessage(response);
    const retryAfter = retryAfterMs(response);
    const message = detail === undefined
        ? `${label} request failed (HTTP ${response.status} ${response.statusText})`.trim()
        : `${label} request failed (HTTP ${response.status}): ${detail}`;
    return new LlmError(message, statusCode(response.status), {
        status: response.status,
        ...retryAfter === undefined ? {} : { providerRetryAfterMs: retryAfter },
    });
}
/** Whether a failure means the stored credential no longer works. */
export function isMissingOrInvalidCredential(error) {
    if (error instanceof LlmError) {
        return error.failure.code === 'INVALID_CREDENTIAL' || error.failure.status === 401 || error.failure.status === 403;
    }
    return false;
}
/** Body text that says the request named a model the account does not serve. */
const UNKNOWN_MODEL = /model id does not exist|unknown model|model (?:id )?not found|does not exist, recognized as/i;
/** Body text that names the model id to use instead. */
const MODEL_REPLACEMENT = /set model id as\s*`?([A-Za-z0-9][A-Za-z0-9._:@[\]/-]*)`?/i;
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
export function unknownModelReplacement(message) {
    if (!UNKNOWN_MODEL.test(message))
        return undefined;
    const replacement = MODEL_REPLACEMENT.exec(message)?.[1];
    return replacement === undefined || replacement === '' ? undefined : replacement;
}
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
export function mapFetchFailure(label, error, watchdog, signal) {
    if (error instanceof LlmError)
        return error;
    if (signal?.aborted === true)
        return error;
    if (watchdog?.timedOut === true) {
        return new LlmError(`${label} stream timed out waiting for data`, 'TIMEOUT');
    }
    const cause = error instanceof Error ? error.cause : undefined;
    if (cause !== undefined && cause !== error) {
        return new LlmError(`${label} request failed: ${errorChain(error)}`, 'NETWORK', { cause: error });
    }
    return error instanceof Error
        ? new LlmError(`${label} request failed: ${error.message}`, 'NETWORK', { cause: error })
        : new LlmError(`${label} request failed: ${String(error)}`, 'NETWORK');
}
