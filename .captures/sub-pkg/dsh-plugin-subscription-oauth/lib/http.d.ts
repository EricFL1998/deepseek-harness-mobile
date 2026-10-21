/**
 * Outbound HTTP for the Kimi routes.
 *
 * The plugin respects the conventional proxy environment variables so a user
 * behind a corporate relay can still reach `auth.kimi.com`. `NO_PROXY` is
 * honoured, and a loopback destination is never proxied.
 *
 * @module dsh-plugin-subscription-oauth/http
 */
/** Fetch-compatible callable used across the plugin (injectable for tests). */
export type FetchFn = typeof fetch;
/**
 * Fetch an absolute URL, honouring the proxy environment.
 *
 * Falls back to a direct request when no proxy applies or when the dispatcher
 * cannot be constructed, so a malformed proxy setting degrades to a direct
 * attempt instead of breaking every call.
 *
 * @param input - absolute request URL (a `Request` is inspected for its URL).
 * @param init - standard request options.
 * @param env - environment to read proxy settings from.
 * @returns the response.
 */
export declare function proxiedFetch(input: RequestInfo | URL, init?: RequestInit, env?: NodeJS.ProcessEnv): Promise<Response>;
