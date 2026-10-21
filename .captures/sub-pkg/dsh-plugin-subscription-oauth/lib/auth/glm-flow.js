/**
 * The ZCode CLI-style OAuth flow BigModel's coding plan signs in with.
 *
 * ZCode does not authorize by device code or by a loopback redirect. It asks its
 * own backend for a *flow*: `init` returns an `authorize_url` plus a flow id, the
 * user signs in at `bigmodel.cn` in a browser, and the callback is completed
 * server-side by `zcode.z.ai`. The client then polls the flow until the backend
 * reports it ready and hands back the tokens.
 *
 * That shape is what makes this implementable outside the ZCode app: nothing has
 * to receive a `zcode://oauth/callback` custom-protocol redirect, and no app
 * secret is involved. The poll is authenticated with a secret this client
 * generates itself, so the flow belongs to whoever started it.
 *
 * Wire contract, read out of ZCode 3.14.4's host bundle and verified against the
 * live endpoint:
 *
 * - `POST /api/v1/oauth/cli/init`, `Authorization: Bearer <64 hex>`,
 *   `{ "provider": "bigmodel" }`
 *   → `{ code: 0, data: { flow_id, poll_token, authorize_url, expires_at,
 *     poll_interval_sec } }`
 * - `GET /api/v1/oauth/cli/poll/<flow_id>`, same bearer
 *   → `{ code: 0, data: { status: "pending" } }` while waiting, then
 *     `{ code: 0, data: { status: "ready", token, user, bigmodel: {
 *     access_token, refresh_token } } }`
 *
 * @module dsh-plugin-subscription-oauth/auth/glm-flow
 */
import { randomBytes } from 'node:crypto';
import { decodeJwtClaims } from './device-flow.js';
/** Backend that brokers the flow; the browser never talks to it directly. */
export const GLM_OAUTH_BASE = 'https://zcode.z.ai';
/** Flow-initiation path on the broker. */
export const GLM_INIT_PATH = '/api/v1/oauth/cli/init';
/** Flow-poll path on the broker; the flow id is appended. */
export const GLM_POLL_PATH = '/api/v1/oauth/cli/poll';
/** Provider key the broker knows the China-region coding plan by. */
export const GLM_BROKER_PROVIDER = 'bigmodel';
/** How long one request to the broker may take. */
const REQUEST_TIMEOUT_MS = 20_000;
/** Poll cadence used when `init` states none. */
const DEFAULT_POLL_INTERVAL_MS = 2_000;
/** Fallback token lifetime when neither the token nor the response states one. */
const DEFAULT_TOKEN_TTL_MS = 24 * 60 * 60 * 1000;
/** Non-empty trimmed string, or undefined. */
function text(value) {
    return typeof value === 'string' && value.trim() !== '' ? value.trim() : undefined;
}
/** A finite number, or undefined. */
function finite(value) {
    return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}
/** A JSON object, or undefined. */
function record(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
        ? value
        : undefined;
}
/** Read the token from either casing the broker has used. */
function tokenField(source, snake, camel) {
    if (source === undefined)
        return undefined;
    return text(source[snake]) ?? text(source[camel]);
}
/**
 * Decode one broker response body.
 *
 * The broker answers HTTP 200 with a business `code` for most refusals, so the
 * envelope is checked before the payload is.
 *
 * @param payload - the decoded body.
 * @param what - label used in the error message.
 * @returns the `data` object.
 * @throws {Error} when the envelope is a refusal or carries no data.
 */
function dataOf(payload, what) {
    const envelope = record(payload);
    if (envelope === undefined)
        throw new Error(`${what}: the broker sent no JSON object`);
    const code = finite(envelope.code);
    if (code !== undefined && code !== 0) {
        throw new Error(`${what}: ${text(envelope.msg) ?? `the broker refused with code ${String(code)}`}`);
    }
    const data = record(envelope.data);
    if (data === undefined)
        throw new Error(`${what}: the broker sent no data`);
    return data;
}
/**
 * Create a flow and return the URL the user signs in at.
 *
 * @param spec - broker origin, fetch, and clock.
 * @param signal - optional caller cancellation.
 * @returns the live flow.
 * @throws {Error} when the broker refuses or answers a shape this build cannot use.
 */
export async function startGlmFlow(spec = {}, signal) {
    const base = (spec.baseUrl ?? GLM_OAUTH_BASE).replace(/\/+$/, '');
    const fetchFn = spec.fetchFn ?? fetch;
    const now = spec.now ?? Date.now;
    // The flow is authenticated by a secret only this caller knows, so a poll can
    // never pick up somebody else's login.
    const pollToken = randomBytes(32).toString('hex');
    const response = await fetchFn(`${base}${GLM_INIT_PATH}`, {
        method: 'POST',
        headers: {
            authorization: `Bearer ${pollToken}`,
            'content-type': 'application/json',
        },
        body: JSON.stringify({ provider: GLM_BROKER_PROVIDER }),
        signal: signal ?? AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!response.ok) {
        throw new Error(`GLM sign-in could not start (HTTP ${response.status})`);
    }
    const data = dataOf(await response.json(), 'GLM sign-in could not start');
    const flowId = text(data.flow_id);
    const authorizeUrl = text(data.authorize_url);
    if (flowId === undefined || authorizeUrl === undefined) {
        throw new Error('GLM sign-in could not start: the broker stated no flow');
    }
    let parsed;
    try {
        parsed = new URL(authorizeUrl);
    }
    catch {
        throw new Error('GLM sign-in could not start: the broker stated an unusable authorize URL');
    }
    if (parsed.protocol !== 'https:') {
        throw new Error('GLM sign-in could not start: the authorize URL is not https');
    }
    const expiresAtSeconds = finite(data.expires_at);
    const intervalSeconds = finite(data.poll_interval_sec);
    return {
        flowId,
        authorizeUrl: parsed.toString(),
        expiresAt: expiresAtSeconds === undefined ? now() + 15 * 60 * 1000 : expiresAtSeconds * 1000,
        pollIntervalMs: intervalSeconds === undefined ? DEFAULT_POLL_INTERVAL_MS : Math.max(1_000, intervalSeconds * 1000),
        pollToken,
    };
}
/**
 * Ask the broker once whether the flow completed.
 *
 * @param spec - broker origin, fetch, and clock.
 * @param start - the flow to ask about.
 * @param signal - optional caller cancellation.
 * @returns the tokens once the flow is ready, or undefined while it is pending.
 * @throws {Error} when the flow expired, failed, or the broker refused.
 */
export async function pollGlmFlow(spec, start, signal) {
    const base = (spec.baseUrl ?? GLM_OAUTH_BASE).replace(/\/+$/, '');
    const fetchFn = spec.fetchFn ?? fetch;
    const now = spec.now ?? Date.now;
    if (now() >= start.expiresAt)
        throw new Error('GLM sign-in expired; start it again');
    const response = await fetchFn(`${base}${GLM_POLL_PATH}/${encodeURIComponent(start.flowId)}`, {
        headers: { authorization: `Bearer ${start.pollToken}` },
        signal: signal ?? AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!response.ok)
        throw new Error(`GLM sign-in polling failed (HTTP ${response.status})`);
    const data = dataOf(await response.json(), 'GLM sign-in polling failed');
    const status = text(data.status);
    if (status === 'pending')
        return undefined;
    if (status === 'failed')
        throw new Error('GLM sign-in was refused in the browser');
    if (status !== 'ready')
        throw new Error(`GLM sign-in polling failed: unknown status ${JSON.stringify(status)}`);
    const bigmodel = record(data.bigmodel);
    const accessToken = tokenField(bigmodel, 'access_token', 'accessToken');
    const zcodeJwt = text(data.token);
    if (accessToken === undefined || zcodeJwt === undefined) {
        throw new Error('GLM sign-in completed without the tokens this build needs');
    }
    const refreshToken = tokenField(bigmodel, 'refresh_token', 'refreshToken');
    const user = record(data.user);
    const accountId = text(user?.user_id) ?? text(user?.userId);
    const label = text(user?.name) ?? text(user?.email);
    // The JWT states its own expiry; the access token does not, so the earlier of
    // the two is what the session treats as the deadline.
    const claims = decodeJwtClaims(zcodeJwt);
    const expSeconds = finite(claims?.exp);
    const jwtExpiry = expSeconds === undefined ? undefined : expSeconds * 1000;
    return {
        accessToken,
        zcodeJwt,
        ...refreshToken === undefined ? {} : { refreshToken },
        expiresAt: jwtExpiry ?? now() + DEFAULT_TOKEN_TTL_MS,
        ...accountId === undefined ? {} : { accountId },
        ...label === undefined ? {} : { label },
    };
}
