/**
 * Failure taxonomy shared by every provider route in this plugin.
 *
 * A coded error is what lets the browser half (and the transport) react without
 * matching on provider prose: the codes are provider-neutral, and only the
 * message names the service.
 *
 * @module dsh-plugin-subscription-oauth/errors
 */
/** The stored credential can no longer be used; the account must sign in again. */
export declare const REAUTH_REQUIRED = "REAUTH_REQUIRED";
/** An account the caller named is not signed in. */
export declare const ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
/** A model id the caller named is not in the catalog. */
export declare const MODEL_NOT_FOUND = "MODEL_NOT_FOUND";
/** The request itself was malformed. */
export declare const BAD_REQUEST = "BAD_REQUEST";
/** A failure a caller can classify without string matching provider prose. */
export declare class ProviderError extends Error {
    readonly code: string;
    constructor(message: string, code: string, options?: ErrorOptions);
}
/**
 * True when `error` means the account has to sign in again.
 * @param error - any thrown value.
 * @returns whether the code is {@link REAUTH_REQUIRED}.
 */
export declare function isReauthRequired(error: unknown): boolean;
