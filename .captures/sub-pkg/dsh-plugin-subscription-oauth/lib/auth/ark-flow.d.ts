/**
 * Volcengine Ark's `volc-sso` sign-in (China region).
 *
 * The contract here was read out of the official `@volcengine/ark-cli` 1.0.38
 * binary — whose SHA-256 matches the manifest its npm package publishes — and then
 * confirmed by a real sign-in that CLI performed, whose stored state and debug
 * traffic are quoted below.
 *
 * The chain is three-legged, and only the first leg is OAuth:
 *
 * 1. **SSO** — `GET /authorize/oauth/authorize` with `response_type=code`, the
 *    devtools client id, a loopback `redirect_uri`, `scope=Console:All:All`,
 *    `state` and an S256 PKCE challenge; then `POST /authorize/oauth/token` with
 *    `grant_type=authorization_code` and the verifier. The reply is
 *    `{ id_token, refresh_token, client_id, timestamp_ms, expires_in: 900 }`,
 *    which is exactly what the CLI stores in `identities/<id>/token.json`.
 * 2. **STS** — the id token is exchanged for temporary `{ ak, sk, session_token,
 *    expires_at }` credentials (`identities/<id>/sts.json`).
 * 3. **A plan API key** — the signed control plane mints an "ordinary ARK API Key"
 *    for that identity, and *that* key is what the data plane accepts.
 *
 * The data plane refuses the other two outright, which is how the plugin knows the
 * key is the credential to keep: an id token bearer answers
 * `403 Unsupported zti authentication method`, an AK/SK signature answers
 * `403 Unsupported aksk authentication method`, while a UUID-shaped key is looked
 * up (`401 The API key doesn't exist`).
 *
 * It is a public client: the exchange is `client_secret_post` with no secret, and
 * the callback is a loopback listener, so the plugin's existing `LoopbackFlow`
 * serves it unchanged.
 *
 * @module dsh-plugin-subscription-oauth/auth/ark-flow
 */
import type { PkcePair } from './pkce-flow.js';
import type { ArkStsCredentials } from './ark-control.js';
import type { FetchFn } from '../http.js';
/** Identity host both the authorize and token endpoints live on. */
export declare const ARK_SSO_ORIGIN = "https://signin.volcengine.com";
/** Where the browser is sent to approve the sign-in. */
export declare const ARK_AUTHORIZE_URL = "https://signin.volcengine.com/authorize/oauth/authorize";
/** Where the authorization code is redeemed. */
export declare const ARK_TOKEN_URL = "https://signin.volcengine.com/authorize/oauth/token";
/**
 * The CLI's devtools client for a sign-in on this machine.
 *
 * It is a public client identifier, not a secret: `client_secret_post` is used with
 * no secret at all, which is what the binary shows for both devtools clients.
 */
export declare const ARK_CLIENT_ID = "trn:signin:::devtools/same-device";
/** Client the CLI uses when the browser is on another device. */
export declare const ARK_CROSS_DEVICE_CLIENT_ID = "trn:signin:::devtools/cross-device";
/**
 * Scope the console client asks for.
 *
 * Taken from a live sign-in the official CLI performed: the authorize URL it
 * printed carried `scope=Console:All:All`, which this module originally omitted.
 */
export declare const ARK_SCOPE = "Console:All:All";
/** Loopback path the identity service redirects to. */
export declare const ARK_CALLBACK_PATH = "/oauth/callback";
/**
 * Ports tried in order for the callback listener.
 *
 * The CLI binds an ephemeral port and puts it in the redirect URI, so no port is
 * privileged by the service; a short fixed list keeps the attempt predictable and
 * stays out of the ranges the other routes already use (56121, 51121).
 */
export declare const ARK_CALLBACK_PORTS: readonly [51789, 51790, 51791, 51792];
/** What one completed sign-in yields. */
export interface ArkTokenSet {
    /** Identity token the SSO issued. */
    readonly idToken: string;
    /**
     * Temporary STS credentials, when the service returned them.
     *
     * The SSO endpoint's `access_token` is not an opaque bearer: it is a JSON document
     * — `token_type: urn:ietf:params:oauth:token-type:access_token_sts` — carrying
     * `access_key_id` / `secret_access_key` / `session_token`. Those sign the control
     * plane, which is where the plan allowance lives, so this is the credential the
     * quota reader needs. It is obtained from the same call as the id token; the
     * account's broker is not involved.
     */
    readonly sts?: ArkStsCredentials;
    /** Refresh token, when the service issued one. */
    readonly refreshToken?: string;
    /** Epoch milliseconds the id token stops being usable. */
    readonly expiresAt: number;
}
/**
 * Normalise the identity service's token response.
 *
 * @param payload - the decoded body.
 * @param previousRefreshToken - the refresh token to keep when the reply omits one.
 * @returns the token set.
 * @throws {Error} when the reply carries no identity token.
 */
export declare function parseArkTokenPayload(payload: unknown, previousRefreshToken?: string): ArkTokenSet;
/**
 * Build the URL the browser is sent to.
 *
 * @param input - loopback redirect, state, and the PKCE pair.
 * @returns the authorize URL.
 */
export declare function buildArkAuthorizeUrl(input: {
    redirectUri: string;
    state: string;
    pkce: PkcePair;
    clientId?: string;
}): string;
/**
 * Redeem the authorization code.
 *
 * @param input - fetch, the code, and the PKCE pair it was issued against.
 * @returns the token set.
 * @throws {Error} when the service refuses or states no identity token.
 */
export declare function exchangeArkCode(input: {
    fetchFn: FetchFn;
    code: string;
    redirectUri: string;
    pkce: PkcePair;
    signal: AbortSignal;
}): Promise<ArkTokenSet>;
/**
 * Renew an identity token.
 *
 * @param input - fetch and the stored refresh token.
 * @returns the renewed token set, keeping the refresh token when none is reissued.
 * @throws {Error} when the service refuses.
 */
export declare function refreshArkToken(input: {
    fetchFn: FetchFn;
    refreshToken: string;
}): Promise<ArkTokenSet>;
