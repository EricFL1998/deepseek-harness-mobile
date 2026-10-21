/**
 * Access-token lifecycle for one provider's accounts.
 *
 * Every service issues short-lived access tokens, so a request resolves a
 * usable token first: a token close to expiry is refreshed eagerly rather than
 * after the request already failed. Concurrent resolutions share one in-flight
 * refresh, so a burst of parallel requests cannot stampede the token endpoint.
 *
 * What a refresh *is* stays provider-owned — the manager takes a callback and
 * persists whatever it returns.
 *
 * @module dsh-plugin-subscription-oauth/auth/tokens
 */
import type { SessionBase, TokenStore } from './store.js';
/** Default refresh trigger: this long before expiry. */
export declare const DEFAULT_PREEMPT_MS: number;
/** One provider's token-endpoint answer, normalised. */
export interface TokenSet {
    readonly accessToken: string;
    readonly refreshToken?: string;
    /** Epoch milliseconds after which the access token must not be sent. */
    readonly expiresAt: number;
}
/** A usable access token plus the account it belongs to. */
export interface SessionToken {
    readonly account: string;
    readonly accessToken: string;
    readonly expiresAt: number;
}
/** What the manager needs to resolve tokens for one provider. */
export interface TokenManagerOptions<S extends SessionBase> {
    readonly store: TokenStore<S>;
    /** Human name used in failure messages, e.g. `Kimi Code`. */
    readonly displayName: string;
    /**
     * Redeem one account's refresh token.
     * @throws {ProviderError} with {@link REAUTH_REQUIRED} when the account must sign in again.
     */
    readonly refresh: (session: S) => Promise<TokenSet>;
    /** Refresh when the token has less than this much life left. */
    readonly preemptMs?: number;
}
/** Resolves and refreshes one provider's access tokens. */
export declare class TokenManager<S extends SessionBase> {
    private readonly options;
    private readonly refreshes;
    private readonly preemptMs;
    constructor(options: TokenManagerOptions<S>);
    /** Stored session for one account (default account when omitted). */
    session(account?: string): Promise<{
        account: string;
        session: S;
    } | undefined>;
    /** Every stored account key. */
    accounts(): Promise<string[]>;
    /** The default account key, when one exists. */
    defaultAccount(): Promise<string | undefined>;
    /**
     * Resolve a request-ready token for one account.
     *
     * @param account - account key; the default account is used when omitted.
     * @param force - refresh even when the stored token still has life left.
     * @returns the usable token.
     * @throws {ProviderError} with {@link REAUTH_REQUIRED} when the account cannot be recovered.
     */
    resolve(account?: string, force?: boolean): Promise<SessionToken>;
    /** Refresh one account, sharing one in-flight request per account. */
    private refresh;
}
/**
 * Fold a refreshed token set back onto the identity-bearing session fields.
 * @param tokens - the refreshed tokens.
 * @param previous - the session the refresh started from.
 * @returns the session to persist.
 */
export declare function foldTokens<S extends SessionBase>(tokens: TokenSet, previous: S): S;
