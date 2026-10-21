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
export const REAUTH_REQUIRED = 'REAUTH_REQUIRED';
/** An account the caller named is not signed in. */
export const ACCOUNT_NOT_FOUND = 'ACCOUNT_NOT_FOUND';
/** A model id the caller named is not in the catalog. */
export const MODEL_NOT_FOUND = 'MODEL_NOT_FOUND';
/** The request itself was malformed. */
export const BAD_REQUEST = 'BAD_REQUEST';
/** A failure a caller can classify without string matching provider prose. */
export class ProviderError extends Error {
    code;
    constructor(message, code, options) {
        super(message, options);
        this.code = code;
        this.name = 'ProviderError';
    }
}
/**
 * True when `error` means the account has to sign in again.
 * @param error - any thrown value.
 * @returns whether the code is {@link REAUTH_REQUIRED}.
 */
export function isReauthRequired(error) {
    return error instanceof ProviderError && error.code === REAUTH_REQUIRED;
}
