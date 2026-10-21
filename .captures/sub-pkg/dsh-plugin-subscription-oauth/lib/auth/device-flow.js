/**
 * Kimi Code's OAuth 2.0 device authorization grant (RFC 8628).
 *
 * Kimi Code has no loopback redirect: authorization happens on
 * `www.kimi.com`, and this side polls `auth.kimi.com` for the token. The token
 * endpoint answers with a JWT whose payload carries the account identity, so
 * the flow settles the account id from the tokens themselves.
 *
 * @module dsh-plugin-subscription-oauth/auth/device-flow
 */
import { DEFAULT_DEVICE_FLOW_TTL_MS, DEFAULT_POLL_INTERVAL_MS, KIMI_CLIENT_ID, SLOW_DOWN_INCREMENT_MS, } from '../constants.js';
import { kimiIdentityHeaders } from './headers.js';
/** Error every abandoned attempt settles with. */
export const LOGIN_CANCELLED_MESSAGE = 'login cancelled';
/** Read a non-empty string field, or undefined. */
function stringField(value) {
    return typeof value === 'string' && value.length > 0 ? value : undefined;
}
/** Accept a number only when it is finite and in range. */
function positiveNumber(value) {
    return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : undefined;
}
/**
 * Turn a token-endpoint payload into a token set with an absolute expiry.
 *
 * `expires_in` must be a finite non-negative number: a missing or nonsensical
 * lifetime would otherwise mint a credential the harness treats as valid
 * forever.
 *
 * The stored instant is the token's REAL expiry, with no safety margin baked in.
 * Kimi Code's access tokens live only 15 minutes, so subtracting the refresh
 * margin here would report a third of that life as already gone — and the card
 * that shows this field would claim "expired" minutes after a successful login.
 * The margin belongs to the refresh trigger, not to the recorded fact.
 *
 * @param payload - decoded token-endpoint JSON.
 * @param previousRefreshToken - refresh token to retain when the response omits one.
 * @returns the normalised token set.
 * @throws {Error} `Kimi token response missing required fields` when unusable.
 */
export function parseTokenPayload(payload, previousRefreshToken) {
    const record = (payload ?? {});
    const accessToken = stringField(record.access_token);
    const lifetime = record.expires_in;
    const lifetimeOk = typeof lifetime === 'number' && Number.isFinite(lifetime) && lifetime >= 0;
    const refreshToken = stringField(record.refresh_token) ?? previousRefreshToken;
    if (accessToken === undefined || !lifetimeOk) {
        throw new Error('Kimi token response missing required fields');
    }
    // A finite lifetime can still overflow the multiplication; an infinite expiry
    // would be a credential the harness treats as valid forever.
    const expiresAt = Date.now() + lifetime * 1000;
    if (!Number.isFinite(expiresAt)) {
        throw new Error('Kimi token response missing required fields');
    }
    return {
        accessToken,
        ...refreshToken === undefined ? {} : { refreshToken },
        expiresAt,
    };
}
/** Decode one base64url JWT segment into an object, or undefined. */
function decodeSegment(segment) {
    if (segment === undefined || segment.length === 0)
        return undefined;
    try {
        const parsed = JSON.parse(Buffer.from(segment, 'base64url').toString('utf8'));
        return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
            ? parsed
            : undefined;
    }
    catch {
        return undefined;
    }
}
/**
 * Decode one JWT's payload claims.
 * @param token - the JWT, or undefined.
 * @returns the claims object, or undefined for an opaque or malformed token.
 */
export function decodeJwtClaims(token) {
    if (token === undefined)
        return undefined;
    const parts = token.split('.');
    return parts.length < 2 ? undefined : decodeSegment(parts[1]);
}
/**
 * Recover the account identity from Kimi's tokens.
 *
 * The access token is authoritative and the refresh token the fallback; a
 * `user_id` claim outranks `sub` in either token because the CLI's sessions
 * are keyed by user id while `sub` is the OAuth subject.
 *
 * @param accessToken - the issued access token.
 * @param refreshToken - the issued refresh token, when the response carried one.
 * @returns the account id and lowercased email, each when the tokens disclose it.
 */
export function identityFromTokens(accessToken, refreshToken) {
    const access = decodeJwtClaims(accessToken);
    const refresh = decodeJwtClaims(refreshToken);
    const accountId = stringField(access?.user_id)
        ?? stringField(refresh?.user_id)
        ?? stringField(access?.sub)
        ?? stringField(refresh?.sub);
    const email = (stringField(access?.email) ?? stringField(refresh?.email))?.toLowerCase();
    return {
        ...accountId === undefined ? {} : { accountId },
        ...email === undefined ? {} : { email },
    };
}
/** The single error a cancelled attempt settles with. */
function cancelledError() {
    return new Error(LOGIN_CANCELLED_MESSAGE);
}
/** Sleep, settling immediately as a cancellation once the signal aborts. */
function sleep(ms, signal) {
    return new Promise((resolve, reject) => {
        if (signal.aborted) {
            reject(cancelledError());
            return;
        }
        const onAbort = () => {
            clearTimeout(timer);
            reject(cancelledError());
        };
        const timer = setTimeout(() => {
            signal.removeEventListener('abort', onAbort);
            resolve();
        }, ms);
        timer.unref?.();
        signal.addEventListener('abort', onAbort, { once: true });
    });
}
/** Decode a response body as JSON, tolerating an empty or non-JSON error page. */
async function readJson(response) {
    const text = await response.text();
    if (text.length === 0)
        return undefined;
    try {
        return JSON.parse(text);
    }
    catch {
        return undefined;
    }
}
/** Human description of a failed OAuth response. */
function describeFailure(status, payload) {
    const record = (payload ?? {});
    return stringField(record.error_description) ?? stringField(record.error) ?? `HTTP ${status}`;
}
/** One user-cancellable device attempt: a poll loop plus its abort handle. */
class Attempt {
    verificationUrl;
    userCode;
    expiresAt;
    controller = new AbortController();
    tokenPromise;
    cancelled = false;
    constructor(verificationUrl, userCode, expiresAt, poll) {
        this.verificationUrl = verificationUrl;
        this.userCode = userCode;
        this.expiresAt = expiresAt;
        this.tokenPromise = poll(this.controller.signal);
        // A rejected token promise must not surface as an unhandled rejection when
        // the caller stops caring about this attempt.
        this.tokenPromise.catch(() => undefined);
    }
    waitToken() {
        return this.tokenPromise;
    }
    cancel() {
        if (this.cancelled)
            return;
        this.cancelled = true;
        this.controller.abort();
    }
}
/**
 * Owns at most one in-flight device authorization per provider route.
 *
 * Only one attempt can settle into the login: a second `start()` supersedes
 * the first, whose poll is aborted so it cannot overwrite the newer session.
 */
export class DeviceFlowManager {
    attempts = new Map();
    /** Whether an authorization is currently being polled for this route. */
    isBusy(provider) {
        return this.attempts.has(provider);
    }
    /** The in-flight attempt for a route, when one exists. */
    pending(provider) {
        return this.attempts.get(provider);
    }
    /**
     * Begin a device authorization.
     * @param provider - provider route key owning this attempt.
     * @param spec - endpoint host, device id, and transport.
     * @returns the pending attempt, whose `verificationUrl`/`userCode` the caller shows.
     */
    async start(provider, spec) {
        const fetchFn = spec.fetchFn ?? fetch;
        const headers = kimiIdentityHeaders(spec.deviceId);
        const authorization = await this.requestDeviceCode(provider, spec.host, headers, fetchFn);
        const attempt = new Attempt(authorization.verificationUrl, authorization.userCode, Date.now() + authorization.expiresInMs, (signal) => this.poll(provider, spec, authorization, headers, fetchFn, signal));
        this.attempts.set(provider, attempt);
        // The poll owns its own lifetime; a superseded start() aborts this one.
        void attempt.waitToken().catch(() => undefined).finally(() => { this.settle(provider, attempt); });
        return attempt;
    }
    /** Drop the attempt record, but never one that a newer `start()` replaced. */
    settle(provider, attempt) {
        if (this.attempts.get(provider) === attempt)
            this.attempts.delete(provider);
    }
    async requestDeviceCode(provider, host, headers, fetchFn) {
        const response = await fetchFn(`${host}/api/oauth/device_authorization`, {
            method: 'POST',
            headers: { ...headers, accept: 'application/json', 'content-type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ client_id: KIMI_CLIENT_ID }).toString(),
        });
        const payload = await readJson(response);
        if (!response.ok) {
            throw new Error(`${provider} device authorization failed: ${describeFailure(response.status, payload)}`);
        }
        const record = (payload ?? {});
        const deviceCode = stringField(record.device_code);
        const userCode = stringField(record.user_code);
        const verificationUri = stringField(record.verification_uri);
        if (deviceCode === undefined || userCode === undefined || verificationUri === undefined) {
            throw new Error('Kimi device authorization response missing required fields');
        }
        return {
            deviceCode,
            userCode,
            verificationUrl: stringField(record.verification_uri_complete) ?? verificationUri,
            expiresInMs: positiveNumber(record.expires_in) === undefined
                ? DEFAULT_DEVICE_FLOW_TTL_MS
                : record.expires_in * 1000,
            intervalMs: positiveNumber(record.interval) === undefined
                ? DEFAULT_POLL_INTERVAL_MS
                : record.interval * 1000,
        };
    }
    async poll(provider, spec, authorization, headers, fetchFn, signal) {
        const deadline = Date.now() + authorization.expiresInMs;
        let intervalMs = authorization.intervalMs;
        for (;;) {
            if (signal.aborted)
                throw cancelledError();
            if (Date.now() >= deadline) {
                throw new Error('Kimi device code expired before authorization completed');
            }
            await sleep(intervalMs, signal);
            const response = await fetchFn(`${spec.host}/api/oauth/token`, {
                method: 'POST',
                headers: { ...headers, accept: 'application/json', 'content-type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({
                    client_id: KIMI_CLIENT_ID,
                    device_code: authorization.deviceCode,
                    grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
                }).toString(),
                signal,
            });
            const payload = await readJson(response);
            const record = (payload ?? {});
            // Success is "a token arrived", not merely a 2xx: an error body can ride
            // a 200, and reporting it as a malformed token would hide the real cause.
            if (response.ok && stringField(record.access_token) !== undefined) {
                return parseTokenPayload(payload);
            }
            switch (stringField(record.error)) {
                case 'authorization_pending':
                    break;
                case 'slow_down':
                    // RFC 8628 §3.5: back off by a fixed increment per `slow_down`.
                    intervalMs += SLOW_DOWN_INCREMENT_MS;
                    break;
                case 'access_denied':
                    throw new Error('Kimi authorization was declined in the browser');
                case 'expired_token':
                    throw new Error('Kimi device code expired before authorization completed');
                default:
                    if (response.ok) {
                        // A 2xx with no error code and no token: let the parser name the
                        // missing fields rather than inventing a provider failure.
                        return parseTokenPayload(payload);
                    }
                    throw new Error(`${provider} device authorization failed: ${describeFailure(response.status, payload)}`);
            }
        }
    }
}
/**
 * Exchange a refresh token for a fresh access token.
 * @param spec - endpoint host, device id, and transport.
 * @param refreshToken - the refresh token to redeem.
 * @returns the refreshed token set (retaining the previous refresh token when omitted).
 */
export async function refreshKimiToken(spec, refreshToken) {
    const fetchFn = spec.fetchFn ?? fetch;
    const response = await fetchFn(`${spec.host}/api/oauth/token`, {
        method: 'POST',
        headers: {
            ...kimiIdentityHeaders(spec.deviceId),
            accept: 'application/json',
            'content-type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            client_id: KIMI_CLIENT_ID,
            grant_type: 'refresh_token',
            refresh_token: refreshToken,
        }).toString(),
    });
    const payload = await readJson(response);
    if (!response.ok) {
        throw new Error(`Kimi token refresh failed: ${describeFailure(response.status, payload)}`);
    }
    return parseTokenPayload(payload, refreshToken);
}
