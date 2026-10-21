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
import { REAUTH_REQUIRED, ProviderError } from '../errors.js';
/** Default refresh trigger: this long before expiry. */
export const DEFAULT_PREEMPT_MS = 5 * 60 * 1000;
/** Resolves and refreshes one provider's access tokens. */
export class TokenManager {
    options;
    refreshes = new Map();
    preemptMs;
    constructor(options) {
        this.options = options;
        this.preemptMs = options.preemptMs ?? DEFAULT_PREEMPT_MS;
    }
    /** Stored session for one account (default account when omitted). */
    async session(account) {
        const key = account ?? await this.options.store.defaultAccount();
        if (key === undefined)
            return undefined;
        const session = await this.options.store.get(key);
        return session === undefined ? undefined : { account: key, session };
    }
    /** Every stored account key. */
    async accounts() {
        return Object.keys(await this.options.store.list());
    }
    /** The default account key, when one exists. */
    async defaultAccount() {
        return this.options.store.defaultAccount();
    }
    /**
     * Resolve a request-ready token for one account.
     *
     * @param account - account key; the default account is used when omitted.
     * @param force - refresh even when the stored token still has life left.
     * @returns the usable token.
     * @throws {ProviderError} with {@link REAUTH_REQUIRED} when the account cannot be recovered.
     */
    async resolve(account, force = false) {
        const found = await this.session(account);
        if (found === undefined) {
            throw new ProviderError(account === undefined
                ? `no ${this.options.displayName} account is signed in`
                : `${this.options.displayName} account ${account} is not signed in`, REAUTH_REQUIRED);
        }
        const { account: key, session } = found;
        if (!force && Date.now() < session.expiresAt) {
            return { account: key, accessToken: session.accessToken, expiresAt: session.expiresAt };
        }
        const refreshed = await this.refresh(key, session);
        return { account: key, accessToken: refreshed.accessToken, expiresAt: refreshed.expiresAt };
    }
    /** Refresh one account, sharing one in-flight request per account. */
    async refresh(key, session) {
        const existing = this.refreshes.get(key);
        if (existing !== undefined)
            return existing;
        const pending = (async () => {
            if (session.refreshToken === undefined) {
                throw new ProviderError(`${this.options.displayName} account ${key} has no refresh token; sign in again`, REAUTH_REQUIRED);
            }
            const tokens = await this.options.refresh(session);
            const stored = await this.options.store.get(key);
            if (stored === undefined) {
                throw new ProviderError(`${this.options.displayName} account ${key} was removed while refreshing`, REAUTH_REQUIRED);
            }
            await this.options.store.save(key, foldTokens(tokens, stored), { makeDefault: false });
            return tokens;
        })();
        this.refreshes.set(key, pending);
        try {
            return await pending;
        }
        finally {
            this.refreshes.delete(key);
        }
    }
}
/**
 * Fold a refreshed token set back onto the identity-bearing session fields.
 * @param tokens - the refreshed tokens.
 * @param previous - the session the refresh started from.
 * @returns the session to persist.
 */
export function foldTokens(tokens, previous) {
    return {
        ...previous,
        accessToken: tokens.accessToken,
        expiresAt: tokens.expiresAt,
        ...tokens.refreshToken === undefined ? {} : { refreshToken: tokens.refreshToken },
    };
}
