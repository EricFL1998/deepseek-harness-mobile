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
/** Token endpoint payload normalised into an absolute expiry. */
export interface KimiTokenSet {
    readonly accessToken: string;
    readonly refreshToken?: string;
    /** Epoch milliseconds after which the access token must not be sent. */
    readonly expiresAt: number;
}
/** How to reach Kimi's device and token endpoints for one flow. */
export interface DeviceFlowSpec {
    /** OAuth host, e.g. `https://auth.kimi.com` (no trailing slash). */
    readonly host: string;
    /** This machine's persisted device id, sent on every request. */
    readonly deviceId: string;
    /** Injected transport; defaults to the global `fetch`. */
    readonly fetchFn?: typeof fetch;
}
/** One pending device authorization the browser can display and cancel. */
export interface DeviceAttempt {
    /** URL the user opens (the `verification_uri_complete` form when offered). */
    readonly verificationUrl: string;
    /** Code the user would type when the complete URL is not usable. */
    readonly userCode: string;
    /** Epoch milliseconds after which the code is refused and the attempt lapses. */
    readonly expiresAt: number;
    /** Resolves with the tokens once the user approves; rejects on failure/cancel. */
    waitToken(): Promise<KimiTokenSet>;
    /** Abandon the attempt; any in-flight poll settles as a cancellation. */
    cancel(): void;
}
/** Error every abandoned attempt settles with. */
export declare const LOGIN_CANCELLED_MESSAGE = "login cancelled";
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
export declare function parseTokenPayload(payload: unknown, previousRefreshToken?: string): KimiTokenSet;
/**
 * Decode one JWT's payload claims.
 * @param token - the JWT, or undefined.
 * @returns the claims object, or undefined for an opaque or malformed token.
 */
export declare function decodeJwtClaims(token: string | undefined): Record<string, unknown> | undefined;
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
export declare function identityFromTokens(accessToken: string | undefined, refreshToken?: string): {
    accountId?: string;
    email?: string;
};
/**
 * Owns at most one in-flight device authorization per provider route.
 *
 * Only one attempt can settle into the login: a second `start()` supersedes
 * the first, whose poll is aborted so it cannot overwrite the newer session.
 */
export declare class DeviceFlowManager {
    private readonly attempts;
    /** Whether an authorization is currently being polled for this route. */
    isBusy(provider: string): boolean;
    /** The in-flight attempt for a route, when one exists. */
    pending(provider: string): DeviceAttempt | undefined;
    /**
     * Begin a device authorization.
     * @param provider - provider route key owning this attempt.
     * @param spec - endpoint host, device id, and transport.
     * @returns the pending attempt, whose `verificationUrl`/`userCode` the caller shows.
     */
    start(provider: string, spec: DeviceFlowSpec): Promise<DeviceAttempt>;
    /** Drop the attempt record, but never one that a newer `start()` replaced. */
    private settle;
    private requestDeviceCode;
    private poll;
}
/**
 * Exchange a refresh token for a fresh access token.
 * @param spec - endpoint host, device id, and transport.
 * @param refreshToken - the refresh token to redeem.
 * @returns the refreshed token set (retaining the previous refresh token when omitted).
 */
export declare function refreshKimiToken(spec: DeviceFlowSpec, refreshToken: string): Promise<KimiTokenSet>;
