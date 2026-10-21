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
import type { FetchFn } from '../http.js';
/** Backend that brokers the flow; the browser never talks to it directly. */
export declare const GLM_OAUTH_BASE = "https://zcode.z.ai";
/** Flow-initiation path on the broker. */
export declare const GLM_INIT_PATH = "/api/v1/oauth/cli/init";
/** Flow-poll path on the broker; the flow id is appended. */
export declare const GLM_POLL_PATH = "/api/v1/oauth/cli/poll";
/** Provider key the broker knows the China-region coding plan by. */
export declare const GLM_BROKER_PROVIDER = "bigmodel";
/** What one flow needs to talk to the broker (injectable for tests). */
export interface GlmFlowSpec {
    /** Broker origin; defaults to {@link GLM_OAUTH_BASE}. */
    readonly baseUrl?: string;
    /** Fetch implementation; defaults to the global `fetch`. */
    readonly fetchFn?: FetchFn;
    /** Clock, for tests. */
    readonly now?: () => number;
}
/** A flow that has been created and is waiting for the user to sign in. */
export interface GlmFlowStart {
    /** Broker-side identity of this flow. */
    readonly flowId: string;
    /** URL to open in a browser. */
    readonly authorizeUrl: string;
    /** Epoch milliseconds this flow stops being accepted. */
    readonly expiresAt: number;
    /** Cadence the broker asks to be polled at. */
    readonly pollIntervalMs: number;
    /** Secret this client generated; the poll authenticates with it. */
    readonly pollToken: string;
}
/** What a completed flow yields. */
export interface GlmTokenSet {
    /** BigModel access token, sent as the bearer to the coding endpoint. */
    readonly accessToken: string;
    /**
     * The ZCode JWT the plan is bound to.
     *
     * ZCode presents this as the API key for a plan it calls "start plan", which is
     * the credential that exists before an account has minted a coding-plan key of
     * its own.
     */
    readonly zcodeJwt: string;
    /** Refresh token, when the broker returned one. */
    readonly refreshToken?: string;
    /** Epoch milliseconds the access token stops being usable. */
    readonly expiresAt: number;
    /** Stable account id, when the broker stated one. */
    readonly accountId?: string;
    /** Human label for the account row. */
    readonly label?: string;
}
/**
 * Create a flow and return the URL the user signs in at.
 *
 * @param spec - broker origin, fetch, and clock.
 * @param signal - optional caller cancellation.
 * @returns the live flow.
 * @throws {Error} when the broker refuses or answers a shape this build cannot use.
 */
export declare function startGlmFlow(spec?: GlmFlowSpec, signal?: AbortSignal): Promise<GlmFlowStart>;
/**
 * Ask the broker once whether the flow completed.
 *
 * @param spec - broker origin, fetch, and clock.
 * @param start - the flow to ask about.
 * @param signal - optional caller cancellation.
 * @returns the tokens once the flow is ready, or undefined while it is pending.
 * @throws {Error} when the flow expired, failed, or the broker refused.
 */
export declare function pollGlmFlow(spec: GlmFlowSpec, start: GlmFlowStart, signal?: AbortSignal): Promise<GlmTokenSet | undefined>;
