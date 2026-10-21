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
import { createHash, createHmac } from 'node:crypto';
import { attributedHeaders } from './headers.js';
/** Control-plane host: the OpenAPI endpoint, not the data-plane one. */
export const ARK_CONTROL_HOST = 'ark.cn-beijing.volcengineapi.com';
/** Region every Ark OpenAPI action is scoped to. */
export const ARK_CONTROL_REGION = 'cn-beijing';
/** Service name in the signing scope. */
export const ARK_CONTROL_SERVICE = 'ark';
/** Version every Ark OpenAPI action takes. */
export const ARK_CONTROL_VERSION = '2024-01-01';
/** An RFC 3339 UTC instant in the compact form the signature wants. */
function amzDate(at) {
    return at.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
}
/** HMAC-SHA256 of `value` under `key`. */
function hmac(key, value) {
    return createHmac('sha256', key).update(value).digest();
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
export function signArkRequest(input) {
    const contentType = input.contentType ?? 'application/x-www-form-urlencoded';
    const at = input.at ?? new Date();
    const xDate = amzDate(at);
    const contentSha = createHash('sha256').update(input.body).digest('hex');
    const signedHeaders = 'content-type;host;x-content-sha256;x-date';
    const canonical = [
        input.method,
        '/',
        input.query,
        `content-type:${contentType}`,
        `host:${ARK_CONTROL_HOST}`,
        `x-content-sha256:${contentSha}`,
        `x-date:${xDate}`,
        '',
        signedHeaders,
        contentSha,
    ].join('\n');
    const scope = `${xDate.slice(0, 8)}/${ARK_CONTROL_REGION}/${ARK_CONTROL_SERVICE}/request`;
    const stringToSign = [
        'HMAC-SHA256',
        xDate,
        scope,
        createHash('sha256').update(canonical).digest('hex'),
    ].join('\n');
    // The derived signing key is a four-step HMAC chain: date, region, service, then
    // the literal "request".
    const kSigning = hmac(hmac(hmac(hmac(input.credentials.secretAccessKey, xDate.slice(0, 8)), ARK_CONTROL_REGION), ARK_CONTROL_SERVICE), 'request');
    const signature = createHmac('sha256', kSigning).update(stringToSign).digest('hex');
    return {
        'content-type': contentType,
        'host': ARK_CONTROL_HOST,
        'x-content-sha256': contentSha,
        'x-date': xDate,
        'x-security-token': input.credentials.sessionToken,
        'authorization': `HMAC-SHA256 Credential=${input.credentials.accessKeyId}/${scope}, SignedHeaders=${signedHeaders}, Signature=${signature}`,
    };
}
/** Call one control-plane action. */
export async function callArkControl(input) {
    const query = `Action=${input.action}&Version=${ARK_CONTROL_VERSION}`;
    const body = input.body ?? '';
    const contentType = input.contentType;
    const response = await input.fetchFn(`https://${ARK_CONTROL_HOST}/?${query}`, {
        method: 'POST',
        headers: {
            ...attributedHeaders(signArkRequest({
                method: 'POST',
                query,
                body,
                credentials: input.credentials,
                ...contentType === undefined ? {} : { contentType },
            })),
        },
        body,
        ...input.signal === undefined ? {} : { signal: input.signal },
    });
    const payload = await response.json().catch(() => undefined);
    if (!response.ok) {
        throw new Error(`Ark control plane refused ${input.action} (HTTP ${response.status})`);
    }
    return payload;
}
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
export async function createArkApiKey(input) {
    const payload = await callArkControl({
        action: 'CreateApiKey',
        credentials: input.credentials,
        fetchFn: input.fetchFn,
        contentType: 'application/json',
        body: JSON.stringify({
            Name: input.name,
            ProjectName: input.project ?? 'default',
            ResourceInstances: [{}],
            AccessControlInfo: { AllowAll: true },
        }),
        ...input.signal === undefined ? {} : { signal: input.signal },
    });
    const id = num(payload.Result?.Id);
    if (id === undefined)
        throw new Error('Ark CreateApiKey returned no key id');
    return id;
}
/**
 * Read one key's value.
 *
 * @param input - the key's id and the credentials to sign with.
 * @returns the key, as the data plane expects it.
 * @throws {Error} when the control plane refuses or states no key.
 */
export async function readArkApiKey(input) {
    const payload = await callArkControl({
        action: 'GetRawApiKey',
        credentials: input.credentials,
        fetchFn: input.fetchFn,
        contentType: 'application/json',
        body: JSON.stringify({ Id: input.id, ProjectName: input.project ?? 'default' }),
        ...input.signal === undefined ? {} : { signal: input.signal },
    });
    const key = typeof payload.Result?.ApiKey === 'string' ? payload.Result.ApiKey.trim() : '';
    if (key === '')
        throw new Error('Ark GetRawApiKey returned no key');
    return key;
}
/**
 * Every key the account already has, newest first as the service lists them.
 *
 * @param input - the credentials to sign with.
 * @returns the keys' ids and names.
 */
export async function listArkApiKeys(input) {
    const payload = await callArkControl({
        action: 'ListApiKeys',
        credentials: input.credentials,
        fetchFn: input.fetchFn,
        contentType: 'application/json',
        body: JSON.stringify({ PageSize: 50, ProjectName: input.project ?? 'default' }),
        ...input.signal === undefined ? {} : { signal: input.signal },
    });
    const items = payload.Result?.Items;
    if (!Array.isArray(items))
        return [];
    const out = [];
    for (const raw of items) {
        if (typeof raw !== 'object' || raw === null)
            continue;
        const record = raw;
        const id = num(record.Id);
        if (id === undefined)
            continue;
        const name = typeof record.Name === 'string' && record.Name.trim() !== '' ? record.Name.trim() : undefined;
        const status = typeof record.Status === 'string' && record.Status.trim() !== '' ? record.Status.trim() : undefined;
        out.push({ id, ...name === undefined ? {} : { name }, ...status === undefined ? {} : { status } });
    }
    return out;
}
/** Read a finite number from a wire field. */
function num(value) {
    if (typeof value === 'number' && Number.isFinite(value))
        return value;
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : undefined;
    }
    return undefined;
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
export function parseArkFreeUsage(payload) {
    if (typeof payload !== 'object' || payload === null)
        return [];
    const result = payload.Result;
    if (typeof result !== 'object' || result === null)
        return [];
    const items = result.Items;
    if (!Array.isArray(items))
        return [];
    const out = [];
    for (const raw of items) {
        if (typeof raw !== 'object' || raw === null)
            continue;
        const item = raw;
        const model = typeof item.FoundationModelName === 'string' ? item.FoundationModelName.trim() : '';
        const usage = item.InferenceFreeUsage;
        if (model === '' || typeof usage !== 'object' || usage === null)
            continue;
        const record = usage;
        const total = num(record.Total);
        const consumed = num(record.Consumed) ?? 0;
        if (total === undefined || total <= 0)
            continue;
        out.push({ model, consumed, total });
    }
    return out;
}
/** A reset instant from an RFC 3339 string. */
function instant(value) {
    if (typeof value === 'number' && Number.isFinite(value))
        return value;
    if (typeof value !== 'string' || value.trim() === '')
        return undefined;
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : undefined;
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
export function parseArkPlanUsage(payload) {
    if (typeof payload !== 'object' || payload === null)
        return { subscribed: false, periods: [] };
    const result = payload.Result;
    if (typeof result !== 'object' || result === null)
        return { subscribed: false, periods: [] };
    const record = result;
    const status = typeof record.Status === 'string' && record.Status.trim() !== '' ? record.Status.trim() : undefined;
    // The two editions name the same structure differently; either may be present.
    const usage = [record.coding_usage, record.afp_usage, record.QuotaUsage]
        .find((candidate) => typeof candidate === 'object' && candidate !== null);
    const rawPeriods = usage?.periods ?? record.periods;
    const periods = [];
    if (Array.isArray(rawPeriods)) {
        for (const raw of rawPeriods) {
            if (typeof raw !== 'object' || raw === null)
                continue;
            const period = raw;
            const label = typeof period.label === 'string' && period.label.trim() !== '' ? period.label.trim() : undefined;
            if (label === undefined)
                continue;
            const resetsAt = instant(period.reset_at);
            const used = num(period.used);
            const total = num(period.total);
            const percent = num(period.percent);
            periods.push({
                label,
                ...used === undefined ? {} : { used },
                ...total === undefined ? {} : { total },
                ...percent === undefined ? {} : { percent },
                ...resetsAt === undefined ? {} : { resetsAt },
            });
        }
    }
    const subscribed = typeof record.subscribed === 'boolean'
        ? record.subscribed
        : periods.length > 0 || (status !== undefined && status.toLowerCase() !== 'reclaimed');
    return { subscribed, ...status === undefined ? {} : { status }, periods };
}
