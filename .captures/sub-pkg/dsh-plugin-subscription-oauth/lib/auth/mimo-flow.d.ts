/**
 * Xiaomi MiMo's sign-in, as the MiMo desktop app performs it.
 *
 * A Xiaomi account signed in on account.xiaomi.com's long-poll page (a QR code
 * for the Xiaomi phone app, or a password) yields a passToken, which signs the
 * account on at a MiMo server. The server answers with session cookies, and model
 * requests are chat completions at the server's `/route` carrying those cookies.
 *
 * This module is the sign-in surface only: starting a QR/password attempt on the
 * account server, waiting for the passToken, exchanging it for a MiMo session,
 * and refreshing that session before the app would. Adapted from magpie's
 * `opencode-mimo-auth` for this plugin's typed flow/store contracts.
 *
 * @module dsh-plugin-subscription-oauth/auth/mimo-flow
 */
/** A MiMo server's regional hosts. */
export declare const MIMO_HOSTS: {
    readonly SGP: "https://mimo-server-sgp.xiaomimimo.com/api";
    readonly RU: "https://mimo-server-ru.xiaomimimimo.com/api";
    readonly IN: "https://mimo-server-in.xiaomimimo.com/api";
};
/** The region this build signs in at by default. */
export declare const MIMO_REGION = "SGP";
/** The app signs on again after a day; renew this close to that ahead of time. */
export declare const MIMO_RENEW_AFTER_MS: number;
export declare const MIMO_RENEW_LEAD_MS: number;
/** One cookie, enough for the sign-on's redirects. */
interface Cookie {
    name: string;
    value: string;
    domain: string;
    hostOnly: boolean;
    path: string;
    secure: boolean;
    gone?: boolean;
}
/** A cookie jar across the sign-on's redirects. */
export declare class Jar {
    private cookies;
    put(cookie: Cookie): void;
    seed(url: string, name: string, value: string): void;
    take(response: Response, url: string): void;
    private for;
    /** All cookies for one URL, as a single Cookie header value. */
    header(url: string): string;
    /** Every cookie for one origin, as a name→value record. */
    forOrigin(origin: string): Record<string, string>;
}
/** The Xiaomi-account credential that signs on at a MiMo server. */
export interface MimoCredentials {
    userId: string;
    cUserId: string;
    passToken: string;
    deviceId: string;
    region: string;
    base: string;
}
/** A signed-on MiMo session: the cookies the server set, plus who signed on. */
export interface MimoSessionCookies {
    cookies: Record<string, string>;
    userId: string;
}
/** The Xiaomi sign-in has lapsed and the account must sign in again. */
export declare class MimoSignInLapsed extends Error {
}
/** The open platform's origin (the Token Plan lives here, not at a MiMo server). */
export declare const MIMO_PLATFORM = "https://platform.xiaomimimo.com";
/**
 * Sign the account on at a MiMo server, following the serviceLogin redirects.
 *
 * The server's `/user/xiaomi/me`, asked with no session, sends the browser to
 * account.xiaomi.com's serviceLogin, which (holding the passToken) sends it back
 * through the server's `/sts`, where the session cookies are set.
 *
 * @param creds - the Xiaomi-account credential.
 * @param base - the MiMo server base for the account's region.
 * @param fetchFn - fetch implementation.
 * @returns the session cookies and the server's user id.
 */
export declare function mimoSignOn(creds: MimoCredentials, base: string, fetchFn: typeof fetch): Promise<MimoSessionCookies>;
/** The sid and callback the MiMo server sends a sessionless browser to the account server with. */
export interface MimoServiceLogin {
    sid: string;
    callback: string;
}
/** Read the serviceLogin the MiMo server would send a sessionless browser to. */
export declare function mimoServiceLogin(base: string, fetchFn: typeof fetch): Promise<MimoServiceLogin>;
/** One started sign-in attempt: the page URL, its long-poll endpoint, and its lifetime. */
export interface MimoSignInStart {
    url: string;
    lp: string;
    device: string;
    until: number;
}
/** Start a Xiaomi sign-in: the QR/password page URL and its long-poll endpoint. */
export declare function startMimoSignIn(fetchFn: typeof fetch): Promise<MimoSignInStart>;
/** Wait for the long poll to report the account signed in; resolves with the poll's fields. */
export declare function waitMimoPoll(start: MimoSignInStart, fetchFn: typeof fetch): Promise<Record<string, unknown>>;
/** Build the durable credential from a finished poll and the device it signed on with. */
export declare function mimoCredentialsFrom(poll: Record<string, unknown>, device: string): MimoCredentials;
/**
 * Complete a sign-in: exchange the poll's passToken for a MiMo session, following a
 * region redirect when the account belongs to another region's server.
 *
 * @param poll - the long poll's settled fields.
 * @param device - the device id the sign-in started with.
 * @param fetchFn - fetch implementation.
 * @returns the durable credentials and the session cookies.
 */
export declare function finishMimoSignIn(poll: Record<string, unknown>, device: string, fetchFn: typeof fetch): Promise<{
    creds: MimoCredentials;
    cookies: Record<string, string>;
}>;
/** Refresh: the session is re-signed-on at the MiMo server from the durable credential. */
export declare function refreshMimoSession(creds: MimoCredentials, fetchFn: typeof fetch): Promise<{
    cookies: Record<string, string>;
    expiresAt: number;
}>;
/** One metered window the Settings card reads. */
export interface MimoUsageWindow {
    label: string;
    usedPercent?: number;
    used?: number;
    limit?: number;
    resetsAt?: number;
}
/** The account's plan and weekly allowance, for the Settings card and chip. */
export declare function readMimoUsage(session: MimoSessionCookies, creds: MimoCredentials, fetchFn: typeof fetch): Promise<{
    plan: string;
    renewsAt?: number;
    windows: MimoUsageWindow[];
}>;
/** The account's Token Plan as a usable API credential, or null when it has none. */
export declare function readMimoPlanKey(creds: MimoCredentials, fetchFn: typeof fetch): Promise<{
    key: string;
    base: string;
} | null>;
export {};
