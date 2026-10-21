/**
 * Kimi Code endpoints, credentials, and catalog facts.
 *
 * Every value here is a wire fact pinned to what the Kimi Code CLI
 * (`KimiCLI/<version>`) sends: the OAuth client id, the device-authorization
 * endpoints on `auth.kimi.com`, and the coding API base the CLI streams from.
 * Requests are dispatched over the OpenAI Responses wire, whose endpoint is
 * `<base>/responses`. The catalog window/effort constants mirror the provider
 * registry the reference proxy uses, so a model selected in DeepSeek Harness
 * resolves the same context capacity and reasoning ladder the CLI would use.
 *
 * @module dsh-plugin-subscription-oauth/constants
 */
/** OAuth client id the Kimi Code CLI is registered under. */
export const KIMI_CLIENT_ID = '17e5f671-d194-4dfb-9706-5516cb48c098';
/** OAuth host; overridable for a staging relay (`KIMI_CODE_OAUTH_HOST` / `KIMI_OAUTH_HOST`). */
export const KIMI_DEFAULT_OAUTH_HOST = 'https://auth.kimi.com';
/** Kimi Code API base; the Responses endpoint is `<base>/responses`. */
export const KIMI_DEFAULT_API_BASE = 'https://api.kimi.com/coding/v1';
/** Path appended to the API base for a streaming request. */
export const KIMI_RESPONSES_PATH = '/responses';
/** Path appended to the API base to list the account's models. */
export const KIMI_MODELS_PATH = '/models';
/** Model the subscription alias points at, and this plugin's recommended default. */
export const KIMI_DEFAULT_MODEL = 'kimi-for-coding';
/**
 * Kimi Code header version. The service admits a known client family through
 * `X-Msh-*`; an absent or stale version is refused rather than dispatched.
 */
export const KIMI_CLI_VERSION = '0.14.0';
/** File (inside the plugin data directory) that persists this machine's device id. */
export const KIMI_DEVICE_ID_FILENAME = 'kimi-device-id';
/** Section-relative directory, below the DSH home, that holds this plugin's state. */
export const KIMI_DATA_DIR = ['plugins', 'kimi-oauth'];
/** RFC 8628 polling interval used when the authorization response omits one. */
export const DEFAULT_POLL_INTERVAL_MS = 5_000;
/** Device-flow lifetime used when the authorization response omits `expires_in`. */
export const DEFAULT_DEVICE_FLOW_TTL_MS = 15 * 60 * 1000;
/** `slow_down` penalty added to the polling interval, per RFC 8628 §3.5. */
export const SLOW_DOWN_INCREMENT_MS = 5_000;
/** Expiry skew subtracted from `expires_in`, so a token is never used as it lapses. */
export const OAUTH_EXPIRY_SKEW_MS = 5 * 60 * 1000;
/** Refresh an access token once less than this much life remains. */
export const KIMI_PREEMPT_MS = 5 * 60 * 1000;
/** Message with `finish_reason === 'length'` stops here when the caller sets no cap. */
export const KIMI_DEFAULT_MAX_TOKENS = 32_000;
/** Context capacity of the standard Kimi Code window. */
export const KIMI_CONTEXT_WINDOW = 262_144;
/** Context capacity of the 1M-token Kimi Code window. */
export const KIMI_LONG_CONTEXT_WINDOW = 1_048_576;
/** Provider route this plugin registers on `ctx.llm`. */
export const KIMI_PROVIDER_ID = 'kimi';
/** Provider display name for selectors and diagnostics. */
export const KIMI_PROVIDER_NAME = 'Kimi Code';
/** Default inactivity budget before a stalled Kimi stream is abandoned. */
export const KIMI_STREAM_IDLE_TIMEOUT_MS = 300_000;
/** Human-facing page a user opens to authorize the device code. */
export const KIMI_CODE_URL = 'https://www.kimi.com/code';
/** Normalise any configured OAuth host to a bare origin. */
export function normalizeOAuthHost(value) {
    return value.trim().replace(/\/+$/, '');
}
/** Effective OAuth host: an environment override wins over the shipped default. */
export function resolveOAuthHost(env = process.env) {
    const override = env.KIMI_CODE_OAUTH_HOST ?? env.KIMI_OAUTH_HOST;
    const host = override !== undefined && override.trim() !== '' ? override : KIMI_DEFAULT_OAUTH_HOST;
    return normalizeOAuthHost(host);
}
/**
 * Normalise a configured API base to one without a request path.
 *
 * An override may be given either as the bare base (`.../coding/v1`) or as the
 * full Responses URL (`.../coding/v1/responses`); both normalise to the base so
 * the request path is never doubled.
 *
 * @param value - the configured base.
 * @returns the base URL without a trailing slash or request path.
 */
export function normalizeApiBase(value) {
    const trimmed = value.trim().replace(/\/+$/, '');
    return trimmed.endsWith(KIMI_RESPONSES_PATH) ? trimmed.slice(0, -KIMI_RESPONSES_PATH.length) : trimmed;
}
/**
 * Effective coding API base, without the request path.
 * @param env - environment to read the override from.
 * @returns the base URL without a trailing slash or request path.
 */
export function resolveApiBase(env = process.env) {
    const override = env.KIMI_CODE_API_BASE ?? env.KIMI_API_BASE;
    return normalizeApiBase(override !== undefined && override.trim() !== '' ? override : KIMI_DEFAULT_API_BASE);
}
