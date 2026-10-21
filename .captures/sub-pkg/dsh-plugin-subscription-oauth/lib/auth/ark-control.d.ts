/**
 * The Ark control plane: STS credentials, request signing, and allowance reads.
 *
 * Ark splits its API in two. The **data plane** (`ark.cn-beijing.volces.com`)
 * takes a plan API key as a plain bearer. The **control plane**
 * (`ark.cn-beijing.volcengineapi.com`) is Volcengine OpenAPI: every call is
 * `POST /?Action=<name>&Version=<date>` signed with the temporary AK/SK the SSO
 * identity was exchanged for, and the plan's allowance lives only there.
 *
 * The signing below was written from the official CLI's own algorithm and then
 * checked against the live service with real credentials, which answered 200 with
 * the same body the CLI reports.
 *
 * @module dsh-plugin-subscription-oauth/auth/ark-control
 */
import type { FetchFn } from '../http.js';
/** Control-plane host: the OpenAPI endpoint, not the data-plane one. */
export declare const ARK_CONTROL_HOST = "ark.cn-beijing.volcengineapi.com";
/** Region every Ark OpenAPI action is scoped to. */
export declare const ARK_CONTROL_REGION = "cn-beijing";
/** Service name in the signing scope. */
export declare const ARK_CONTROL_SERVICE = "ark";
/** Version every Ark OpenAPI action takes. */
export declare const ARK_CONTROL_VERSION = "2024-01-01";
/** Temporary credentials the SSO identity is exchanged for. */
export interface ArkStsCredentials {
    /** Access key id, `AKT…` for a session credential. */
    readonly accessKeyId: string;
    /** Secret access key. */
    readonly secretAccessKey: string;
    /** Session token that must accompany the signature. */
    readonly sessionToken: string;
    /** Epoch milliseconds the credentials lapse. */
    readonly expiresAt: number;
}
/**
 * Sign one OpenAPI request the way Volcengine expects.
 *
 * The canonical request lists `content-type;host;x-content-sha256;x-date`, which is
 * the set the official client signs for these actions.
 *
 * @param input - method, query, body, and the credentials to sign with.
 * @returns the headers to send.
 */
export declare function signArkRequest(input: {
    method: 'GET' | 'POST';
    query: string;
    body: string;
    credentials: ArkStsCredentials;
    /** Body media type; the control plane accepts form and JSON, and signs whichever is sent. */
    contentType?: string;
    at?: Date;
}): Record<string, string>;
/** Call one control-plane action. */
export declare function callArkControl<T>(input: {
    action: string;
    credentials: ArkStsCredentials;
    fetchFn: FetchFn;
    body?: string;
    /**
     * Body media type.
     *
     * Actions that take nested structures (creating an API key, for instance) only
     * parse a JSON body; the flat actions accept the form encoding.
     */
    contentType?: string;
    signal?: AbortSignal;
}): Promise<T>;
/**
 * Mint one ordinary API key for the account.
 *
 * The data plane refuses STS credentials (`403 Unsupported sts authentication
 * method`) and takes only an API key, so this is the step that turns a sign-in into
 * a usable credential. The shape is not guesswork — it was learned from the
 * service's own validation, which reports each missing parameter in turn:
 * `Name`, then `ProjectName`, then `ResourceInstances`, then
 * `AccessControlInfo.AllowAll`. `ResourceInstances` must be a non-empty array (an
 * empty one is rejected as invalid, while one empty object is accepted), and the
 * body has to be JSON, because a nested structure is not expressible in the form
 * encoding the flat actions use.
 *
 * The reply carries only an `Id`; the key itself comes from {@link readArkApiKey}.
 *
 * @param input - action inputs plus the credentials to sign with.
 * @returns the new key's id.
 * @throws {Error} when the control plane refuses.
 */
export declare function createArkApiKey(input: {
    credentials: ArkStsCredentials;
    fetchFn: FetchFn;
    name: string;
    project?: string;
    signal?: AbortSignal;
}): Promise<number>;
/**
 * Read one key's value.
 *
 * @param input - the key's id and the credentials to sign with.
 * @returns the key, as the data plane expects it.
 * @throws {Error} when the control plane refuses or states no key.
 */
export declare function readArkApiKey(input: {
    credentials: ArkStsCredentials;
    fetchFn: FetchFn;
    id: number;
    project?: string;
    signal?: AbortSignal;
}): Promise<string>;
/**
 * Every key the account already has, newest first as the service lists them.
 *
 * @param input - the credentials to sign with.
 * @returns the keys' ids and names.
 */
export declare function listArkApiKeys(input: {
    credentials: ArkStsCredentials;
    fetchFn: FetchFn;
    project?: string;
    signal?: AbortSignal;
}): Promise<readonly {
    id: number;
    name?: string;
    status?: string;
}[]>;
/** One model's free inference allowance, as `ListModelChargeItems` reports it. */
export interface ArkFreeUsage {
    /** Model id the allowance belongs to. */
    readonly model: string;
    /** Tokens already spent. */
    readonly consumed: number;
    /** Tokens granted. */
    readonly total: number;
}
/**
 * Read every model's free inference allowance.
 *
 * This is the allowance that exists without a subscription — the plan quota is a
 * separate action that reports `Status: Reclaimed` for an account with no plan.
 * Free usage is per model, and the free-usage field is omitted for models that have
 * none, so those are skipped rather than reported as zero.
 *
 * @param payload - the decoded `ListModelChargeItems` body.
 * @returns one entry per model that states a free allowance.
 */
export declare function parseArkFreeUsage(payload: unknown): ArkFreeUsage[];
/** One metered period of a paid plan, as the CLI documents it. */
export interface ArkPeriod {
    /** `5h`, `weekly`, `monthly`, or `session` for the team edition. */
    readonly label: string;
    /** Units already spent. */
    readonly used?: number;
    /** Units granted. */
    readonly total?: number;
    /** Percent spent, when the service states it. */
    readonly percent?: number;
    /** Epoch milliseconds the period resets. */
    readonly resetsAt?: number;
}
/** A plan's state and its metered periods. */
export interface ArkPlanUsage {
    /** Whether the account holds the plan at all. */
    readonly subscribed: boolean;
    /** Plan state as the service names it, e.g. `Reclaimed`. */
    readonly status?: string;
    /** The metered periods, in the order the service listed them. */
    readonly periods: readonly ArkPeriod[];
}
/**
 * Read a plan's allowance out of a `GetCodingPlanUsage` / `GetAFPUsage` body.
 *
 * The service nests the metered periods under `coding_usage` (Coding Plan) or
 * `afp_usage` (Agent Plan); each period is
 * `{ label, used, total, percent, reset_at }`, which the CLI's own help documents
 * with a worked example. An account with no plan answers `Status: Reclaimed` and no
 * periods, which is a state to report rather than an error.
 *
 * @param payload - the decoded control-plane body.
 * @returns the plan state and its periods.
 */
export declare function parseArkPlanUsage(payload: unknown): ArkPlanUsage;
