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
import { attributedHeaders } from './headers.js';
/** Identity host both the authorize and token endpoints live on. */
export const ARK_SSO_ORIGIN = 'https://signin.volcengine.com';
/** Where the browser is sent to approve the sign-in. */
export const ARK_AUTHORIZE_URL = `${ARK_SSO_ORIGIN}/authorize/oauth/authorize`;
/** Where the authorization code is redeemed. */
export const ARK_TOKEN_URL = `${ARK_SSO_ORIGIN}/authorize/oauth/token`;
/**
 * The CLI's devtools client for a sign-in on this machine.
 *
 * It is a public client identifier, not a secret: `client_secret_post` is used with
 * no secret at all, which is what the binary shows for both devtools clients.
 */
export const ARK_CLIENT_ID = 'trn:signin:::devtools/same-device';
/** Client the CLI uses when the browser is on another device. */
export const ARK_CROSS_DEVICE_CLIENT_ID = 'trn:signin:::devtools/cross-device';
/**
 * Scope the console client asks for.
 *
 * Taken from a live sign-in the official CLI performed: the authorize URL it
 * printed carried `scope=Console:All:All`, which this module originally omitted.
 */
export const ARK_SCOPE = 'Console:All:All';
/** Loopback path the identity service redirects to. */
export const ARK_CALLBACK_PATH = '/oauth/callback';
/**
 * Ports tried in order for the callback listener.
 *
 * The CLI binds an ephemeral port and puts it in the redirect URI, so no port is
 * privileged by the service; a short fixed list keeps the attempt predictable and
 * stays out of the ranges the other routes already use (56121, 51121).
 */
export const ARK_CALLBACK_PORTS = [51789, 51790, 51791, 51792];
/** Read a non-empty trimmed string. */
function text(value) {
    return typeof value === 'string' && value.trim() !== '' ? value.trim() : undefined;
}
/** Read a finite number. */
function finite(value) {
    return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}
/** Decode one JWT's payload without verifying it, for the expiry claim. */
function jwtExpiry(token) {
    const parts = token.split('.');
    if (parts.length < 2)
        return undefined;
    try {
        const payload = JSON.parse(Buffer.from(parts[1] ?? '', 'base64url').toString('utf8'));
        if (typeof payload !== 'object' || payload === null)
            return undefined;
        const exp = finite(payload.exp);
        return exp === undefined ? undefined : exp * 1000;
    }
    catch {
        return undefined;
    }
}
/** How long a token set is assumed to live when nothing states it. */
const ASSUMED_ID_TOKEN_TTL_MS = 60 * 60 * 1000;
/**
 * Read the STS credentials the SSO endpoint hands back.
 *
 * The `access_token` is a JSON document rather than an opaque string, and its
 * `token_type` says what it is. A service that answers with an ordinary opaque
 * access token yields nothing here, which is not an error: the id token still works
 * for the sign-in, only the quota becomes unreadable.
 *
 * @param payload - the decoded token response.
 * @returns the credentials, or undefined when the reply carried none.
 */
function stsFrom(payload) {
    const accessToken = text(payload.access_token);
    if (accessToken === undefined)
        return undefined;
    let parsed;
    try {
        parsed = JSON.parse(accessToken);
    }
    catch {
        return undefined;
    }
    if (typeof parsed !== 'object' || parsed === null)
        return undefined;
    const record = parsed;
    const accessKeyId = text(record.access_key_id);
    const secretAccessKey = text(record.secret_access_key);
    const sessionToken = text(record.session_token);
    if (accessKeyId === undefined || secretAccessKey === undefined || sessionToken === undefined) {
        return undefined;
    }
    const stated = finite(payload.expires_in);
    return {
        accessKeyId,
        secretAccessKey,
        sessionToken,
        expiresAt: Date.now() + (stated === undefined ? 900 : stated) * 1000,
    };
}
/**
 * Normalise the identity service's token response.
 *
 * @param payload - the decoded body.
 * @param previousRefreshToken - the refresh token to keep when the reply omits one.
 * @returns the token set.
 * @throws {Error} when the reply carries no identity token.
 */
export function parseArkTokenPayload(payload, previousRefreshToken) {
    if (typeof payload !== 'object' || payload === null) {
        throw new Error('Ark sign-in returned no token payload');
    }
    const record = payload;
    const idToken = text(record.id_token) ?? text(record.access_token);
    if (idToken === undefined)
        throw new Error('Ark sign-in returned an empty id_token');
    const refreshToken = text(record.refresh_token) ?? previousRefreshToken;
    const sts = stsFrom(record);
    const stated = finite(record.id_token_expires_at);
    const expiresAt = stated === undefined
        // The JWT states its own expiry; a reply that omits both is assumed short-lived
        // rather than trusted forever.
        ? jwtExpiry(idToken) ?? Date.now() + ASSUMED_ID_TOKEN_TTL_MS
        : stated < 1e12 ? stated * 1000 : stated;
    return {
        idToken,
        ...sts === undefined ? {} : { sts },
        ...refreshToken === undefined ? {} : { refreshToken },
        expiresAt,
    };
}
/** Describe a refusal from the identity service. */
async function failure(response, what) {
    const payload = await response.json().catch(() => undefined);
    const record = typeof payload === 'object' && payload !== null ? payload : {};
    const described = text(record.error_description) ?? text(record.error) ?? text(record.message);
    return new Error(`${what} failed (HTTP ${response.status})${described === undefined ? '' : `: ${described}`}`);
}
/**
 * Build the URL the browser is sent to.
 *
 * @param input - loopback redirect, state, and the PKCE pair.
 * @returns the authorize URL.
 */
export function buildArkAuthorizeUrl(input) {
    const url = new URL(ARK_AUTHORIZE_URL);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('client_id', input.clientId ?? ARK_CLIENT_ID);
    url.searchParams.set('redirect_uri', input.redirectUri);
    url.searchParams.set('scope', ARK_SCOPE);
    url.searchParams.set('state', input.state);
    url.searchParams.set('code_challenge', input.pkce.challenge);
    url.searchParams.set('code_challenge_method', 'S256');
    return url.toString();
}
/**
 * Redeem the authorization code.
 *
 * @param input - fetch, the code, and the PKCE pair it was issued against.
 * @returns the token set.
 * @throws {Error} when the service refuses or states no identity token.
 */
export async function exchangeArkCode(input) {
    const response = await input.fetchFn(ARK_TOKEN_URL, {
        method: 'POST',
        headers: {
            ...attributedHeaders(),
            'content-type': 'application/x-www-form-urlencoded',
            accept: 'application/json',
        },
        body: new URLSearchParams({
            grant_type: 'authorization_code',
            client_id: ARK_CLIENT_ID,
            code: input.code,
            redirect_uri: input.redirectUri,
            code_verifier: input.pkce.verifier,
        }).toString(),
        signal: input.signal,
    });
    if (!response.ok)
        throw await failure(response, 'Ark token exchange');
    return parseArkTokenPayload(await response.json());
}
/**
 * Renew an identity token.
 *
 * @param input - fetch and the stored refresh token.
 * @returns the renewed token set, keeping the refresh token when none is reissued.
 * @throws {Error} when the service refuses.
 */
export async function refreshArkToken(input) {
    const response = await input.fetchFn(ARK_TOKEN_URL, {
        method: 'POST',
        headers: {
            ...attributedHeaders(),
            'content-type': 'application/x-www-form-urlencoded',
            accept: 'application/json',
        },
        body: new URLSearchParams({
            grant_type: 'refresh_token',
            client_id: ARK_CLIENT_ID,
            refresh_token: input.refreshToken,
        }).toString(),
    });
    if (!response.ok)
        throw await failure(response, 'Ark token refresh');
    return parseArkTokenPayload(await response.json(), input.refreshToken);
}
