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
export declare const KIMI_CLIENT_ID = "17e5f671-d194-4dfb-9706-5516cb48c098";
/** OAuth host; overridable for a staging relay (`KIMI_CODE_OAUTH_HOST` / `KIMI_OAUTH_HOST`). */
export declare const KIMI_DEFAULT_OAUTH_HOST = "https://auth.kimi.com";
/** Kimi Code API base; the Responses endpoint is `<base>/responses`. */
export declare const KIMI_DEFAULT_API_BASE = "https://api.kimi.com/coding/v1";
/** Path appended to the API base for a streaming request. */
export declare const KIMI_RESPONSES_PATH = "/responses";
/** Path appended to the API base to list the account's models. */
export declare const KIMI_MODELS_PATH = "/models";
/** Model the subscription alias points at, and this plugin's recommended default. */
export declare const KIMI_DEFAULT_MODEL = "kimi-for-coding";
/**
 * Kimi Code header version. The service admits a known client family through
 * `X-Msh-*`; an absent or stale version is refused rather than dispatched.
 */
export declare const KIMI_CLI_VERSION = "0.14.0";
/** File (inside the plugin data directory) that persists this machine's device id. */
export declare const KIMI_DEVICE_ID_FILENAME = "kimi-device-id";
/** Section-relative directory, below the DSH home, that holds this plugin's state. */
export declare const KIMI_DATA_DIR: readonly ["plugins", "kimi-oauth"];
/** RFC 8628 polling interval used when the authorization response omits one. */
export declare const DEFAULT_POLL_INTERVAL_MS = 5000;
/** Device-flow lifetime used when the authorization response omits `expires_in`. */
export declare const DEFAULT_DEVICE_FLOW_TTL_MS: number;
/** `slow_down` penalty added to the polling interval, per RFC 8628 §3.5. */
export declare const SLOW_DOWN_INCREMENT_MS = 5000;
/** Expiry skew subtracted from `expires_in`, so a token is never used as it lapses. */
export declare const OAUTH_EXPIRY_SKEW_MS: number;
/** Refresh an access token once less than this much life remains. */
export declare const KIMI_PREEMPT_MS: number;
/** Message with `finish_reason === 'length'` stops here when the caller sets no cap. */
export declare const KIMI_DEFAULT_MAX_TOKENS = 32000;
/** Context capacity of the standard Kimi Code window. */
export declare const KIMI_CONTEXT_WINDOW = 262144;
/** Context capacity of the 1M-token Kimi Code window. */
export declare const KIMI_LONG_CONTEXT_WINDOW = 1048576;
/** Provider route this plugin registers on `ctx.llm`. */
export declare const KIMI_PROVIDER_ID = "kimi";
/** Provider display name for selectors and diagnostics. */
export declare const KIMI_PROVIDER_NAME = "Kimi Code";
/** Default inactivity budget before a stalled Kimi stream is abandoned. */
export declare const KIMI_STREAM_IDLE_TIMEOUT_MS = 300000;
/** Human-facing page a user opens to authorize the device code. */
export declare const KIMI_CODE_URL = "https://www.kimi.com/code";
/** Normalise any configured OAuth host to a bare origin. */
export declare function normalizeOAuthHost(value: string): string;
/** Effective OAuth host: an environment override wins over the shipped default. */
export declare function resolveOAuthHost(env?: NodeJS.ProcessEnv): string;
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
export declare function normalizeApiBase(value: string): string;
/**
 * Effective coding API base, without the request path.
 * @param env - environment to read the override from.
 * @returns the base URL without a trailing slash or request path.
 */
export declare function resolveApiBase(env?: NodeJS.ProcessEnv): string;
