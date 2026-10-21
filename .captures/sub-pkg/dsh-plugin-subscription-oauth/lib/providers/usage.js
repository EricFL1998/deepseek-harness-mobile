/**
 * Plan and allowance reads.
 *
 * Both providers state what a subscription still has left on their own
 * endpoints, in their own shapes. This module normalises those shapes into the
 * one {@link UsageRead} the Settings page renders, and nothing here ever throws
 * for a merely unsupported or unavailable endpoint: a provider that states no
 * usage simply yields `undefined`.
 *
 * @module dsh-plugin-subscription-oauth/providers/usage
 */
import { attributedHeaders } from '../auth/headers.js';
import { proxiedFetch } from '../http.js';
/** True for a plain object usable as a JSON record. */
function isRecord(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
/** A finite number, from either a number or a numeric string. */
function toNumber(value) {
    if (typeof value === 'number' && Number.isFinite(value))
        return value;
    if (typeof value === 'string' && value.trim().length > 0) {
        const parsed = Number(value);
        if (Number.isFinite(parsed))
            return parsed;
    }
    return undefined;
}
/** A percentage clamped to 0–100, ignoring nonsense. */
function toPercent(value) {
    const parsed = toNumber(value);
    if (parsed === undefined || parsed < 0)
        return undefined;
    return Math.min(100, parsed);
}
/**
 * An instant from an RFC 3339 string or a numeric stamp.
 *
 * A bare number below 1e12 is read as seconds: every provider here states epoch
 * milliseconds or an ISO string, and a seconds value would otherwise land in 1970.
 */
function toInstant(value) {
    if (typeof value === 'string' && value.length > 0) {
        const parsed = Date.parse(value);
        return Number.isFinite(parsed) ? parsed : undefined;
    }
    const numeric = toNumber(value);
    if (numeric === undefined || numeric <= 0)
        return undefined;
    return numeric < 1e12 ? numeric * 1000 : numeric;
}
/**
 * Read one `usages.<key>` row.
 *
 * Kimi states these as a spent fraction (`used_ratio`, 0–1) plus `reset_time`,
 * rather than the `limit`/`used` pair the other rows use, so the percentage is the
 * ratio itself.
 *
 * @param usages - the payload's `usages` object.
 * @param key - `limit_5h` or `limit_7d`.
 * @returns the row, or undefined when it states no figure.
 */
function readUsagesRow(usages, key) {
    if (!isRecord(usages))
        return undefined;
    const row = usages[key];
    if (!isRecord(row))
        return undefined;
    const ratio = toNumber(row.used_ratio);
    if (ratio === undefined)
        return undefined;
    const resetsAt = resetInstant(row);
    return {
        usedPercent: Math.max(0, Math.min(100, ratio * 100)),
        ...resetsAt === undefined ? {} : { resetsAt },
    };
}
/**
 * The first present reset instant among a row's usual field names.
 *
 * `reset_time` is in the list because that is what Kimi's own `/usages` body uses —
 * `usages.limit_5h.reset_time` — and omitting it was why the Kimi card showed its
 * windows without any reset at all. The other spellings cover the shapes the other
 * routes and the earlier payloads use.
 */
function resetInstant(row) {
    for (const key of ['resetsAt', 'resets_at', 'resetAt', 'reset_at', 'resetTime', 'reset_time', 'end', 'endsAt', 'expiresAt']) {
        const instant = toInstant(row[key]);
        if (instant !== undefined)
            return instant;
    }
    const relative = toNumber(row.retry_after_seconds ?? row.retry_after);
    if (relative !== undefined && relative > 0)
        return Date.now() + relative * 1000;
    return undefined;
}
/** Read a `{limit, used|remaining, ...}` row, or a direct utilisation field. */
function readRow(value, resetFallback) {
    if (!isRecord(value))
        return undefined;
    const resetsAt = resetInstant(value) ?? (resetFallback === undefined ? undefined : resetInstant(resetFallback));
    const limit = toNumber(value.limit);
    const usedDirect = toNumber(value.used);
    if (limit !== undefined && limit > 0 && usedDirect !== undefined) {
        return {
            usedPercent: Math.min(100, (usedDirect / limit) * 100),
            used: usedDirect,
            limit,
            ...resetsAt === undefined ? {} : { resetsAt },
        };
    }
    if (limit !== undefined && limit > 0) {
        const remaining = toNumber(value.remaining);
        if (remaining !== undefined) {
            const used = limit - remaining;
            return {
                usedPercent: Math.min(100, (used / limit) * 100),
                used,
                limit,
                ...resetsAt === undefined ? {} : { resetsAt },
            };
        }
    }
    const percent = toPercent(value.utilization ?? value.percent ?? value.usedPercent ?? value.used_percent);
    if (percent === undefined)
        return undefined;
    return {
        usedPercent: percent,
        ...usedDirect === undefined ? {} : { used: usedDirect },
        ...limit === undefined ? {} : { limit },
        ...resetsAt === undefined ? {} : { resetsAt },
    };
}
/** Turn one row into a wire window. */
function toWindow(label, row) {
    return {
        label,
        usedPercent: row.usedPercent,
        ...row.used === undefined ? {} : { used: row.used },
        ...row.limit === undefined ? {} : { limit: row.limit },
        ...row.resetsAt === undefined ? {} : { resetsAt: row.resetsAt },
    };
}
/** The `/usages` endpoint on the Kimi Code base. */
export const KIMI_USAGE_PATH = '/usages';
/** The Grok Build CLI chat proxy's billing endpoint. */
export const GROK_BILLING_URL = 'https://cli-chat-proxy.grok.com/v1/billing?format=credits';
/** Subscription tier encoded in a Grok access token's `tier` claim. */
const GROK_TIER_NAMES = {
    0: 'Free',
    1: 'SuperGrok',
    2: 'X Basic',
    3: 'X Premium',
    4: 'X Premium+',
    5: 'SuperGrok Heavy',
    6: 'SuperGrok Lite',
    7: 'SuperGrok Plus',
};
/** Unwrap a Kimi usage envelope that nests the payload under `data`. */
function unwrapKimiUsage(value) {
    if (!isRecord(value))
        return undefined;
    const nested = value.data;
    if (!isRecord(nested))
        return value;
    const usable = (field) => field !== undefined && field !== null;
    const outer = usable(value.usage) || usable(value.limits) || usable(value.totalQuota) || usable(value.usages);
    const inner = usable(nested.usage) || usable(nested.limits) || usable(nested.totalQuota) || usable(nested.usages);
    return !outer && inner ? nested : value;
}
/** The label a Kimi limit row carries, lowercased. */
function kimiLimitLabel(item, detail) {
    return [item.name, item.title, item.scope, detail.name, detail.title]
        .filter((value) => typeof value === 'string')
        .join(' ')
        .toLowerCase();
}
/** Whether a Kimi limit row describes the rolling five-hour window. */
function isFiveHourLimit(item, detail, window) {
    const duration = toNumber(window.duration ?? item.duration ?? detail.duration);
    const unit = String(window.timeUnit ?? item.timeUnit ?? detail.timeUnit ?? '').toUpperCase();
    if (unit.includes('MINUTE') && duration === 300)
        return true;
    if (unit.includes('HOUR') && duration === 5)
        return true;
    return /(^|\b)5\s*(?:h|hour)/.test(kimiLimitLabel(item, detail));
}
/** Whether a Kimi limit row describes the weekly window. */
function isWeeklyLimit(item, detail, window) {
    const duration = toNumber(window.duration ?? item.duration ?? detail.duration);
    const unit = String(window.timeUnit ?? item.timeUnit ?? detail.timeUnit ?? '').toUpperCase();
    if (unit.includes('DAY') && duration === 7)
        return true;
    if (unit.includes('HOUR') && duration === 168)
        return true;
    return /weekly|7\s*(?:d|day)/.test(kimiLimitLabel(item, detail));
}
/**
 * Read the Kimi Code plan's rolling windows.
 *
 * `GET <base>/usages` states a five-hour window and a weekly window, either of
 * which may be absent depending on the plan. The payload also carries a
 * whole-subscription `totalQuota`, which is deliberately not published as a third
 * meter: what a Kimi Code plan is metered against is the rolling and weekly
 * windows, and a bare credit total next to them reads as a second, contradictory
 * allowance.
 *
 * @param input - endpoint base, bearer, and an injectable fetch.
 * @returns the windows, or undefined when the endpoint states none.
 */
export async function readKimiUsage(input) {
    const fetchFn = input.fetchFn ?? proxiedFetch;
    let response;
    try {
        response = await fetchFn(`${input.apiBase}${KIMI_USAGE_PATH}`, {
            headers: attributedHeaders({ 'authorization': `Bearer ${input.accessToken}`, 'accept': 'application/json' }),
        });
    }
    catch {
        return undefined;
    }
    if (!response.ok)
        return undefined;
    const payload = await response.json().catch(() => undefined);
    const body = unwrapKimiUsage(payload);
    if (body === undefined)
        return undefined;
    // The whole-subscription row is the weekly window; a `limits` entry may
    // restate it (or add the rolling five-hour one) with its own reset.
    let weekly = readRow(body.usage);
    let fiveHour;
    if (Array.isArray(body.limits)) {
        for (const raw of body.limits) {
            if (!isRecord(raw))
                continue;
            const detail = isRecord(raw.detail) ? raw.detail : raw;
            const window = isRecord(raw.window) ? raw.window : {};
            if (fiveHour === undefined && isFiveHourLimit(raw, detail, window)) {
                fiveHour = readRow(detail, window);
            }
            if (weekly === undefined && isWeeklyLimit(raw, detail, window)) {
                weekly = readRow(detail, window);
            }
            if (fiveHour !== undefined && weekly !== undefined)
                break;
        }
    }
    const windows = [];
    if (fiveHour !== undefined)
        windows.push(toWindow('5h', fiveHour));
    if (weekly !== undefined)
        windows.push(toWindow('weekly', weekly));
    // Kimi's own `/usages` body states the two metered windows as
    // `usages.limit_5h` / `usages.limit_7d` with `used_ratio` and `reset_time`. Those
    // are read last so a `limits[]` row, when present, keeps its more specific
    // figures; this is the fallback that stops a payload from rendering as two
    // windows with no reset at all.
    if (windows.length === 0) {
        for (const [key, label] of [['limit_5h', '5h'], ['limit_7d', 'weekly']]) {
            const row = readUsagesRow(body.usages, key);
            if (row !== undefined)
                windows.push(toWindow(label, row));
        }
    }
    if (windows.length === 0)
        return undefined;
    const plan = typeof body.plan === 'string' && body.plan.length > 0 ? body.plan : undefined;
    return { windows, ...plan === undefined ? {} : { plan } };
}
/** Read the subscription tier a Grok access token carries. */
export function grokTierName(accessToken) {
    const parts = accessToken.split('.');
    if (parts.length < 2)
        return undefined;
    try {
        const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
        if (!isRecord(payload))
            return undefined;
        const tier = payload.tier;
        if (typeof tier !== 'number' || !Number.isInteger(tier))
            return undefined;
        return GROK_TIER_NAMES[tier] ?? String(tier);
    }
    catch {
        return undefined;
    }
}
/**
 * Read the Grok (X Premium) plan's metered allowance.
 *
 * The CLI's own chat proxy states included-credit usage as a ready-made
 * percentage plus the current period; an older shape states cent-valued totals
 * from which the percentage is derived.
 *
 * @param input - bearer, and an injectable fetch.
 * @returns the window, or undefined when the endpoint states none.
 */
export async function readGrokUsage(input) {
    const fetchFn = input.fetchFn ?? proxiedFetch;
    let response;
    try {
        response = await fetchFn(GROK_BILLING_URL, {
            headers: attributedHeaders({
                'authorization': `Bearer ${input.accessToken}`,
                // The proxy only honors bearer tokens presented as the Grok CLI.
                'x-xai-token-auth': 'xai-grok-cli',
                'accept': 'application/json',
            }),
        });
    }
    catch {
        return undefined;
    }
    if (!response.ok)
        return undefined;
    const payload = await response.json().catch(() => undefined);
    if (!isRecord(payload))
        return undefined;
    const config = isRecord(payload.config) ? payload.config : {};
    const windows = [];
    const period = isRecord(config.currentPeriod) ? config.currentPeriod : undefined;
    const percent = toPercent(config.creditUsagePercent);
    if (percent !== undefined || period !== undefined) {
        const resetsAt = period === undefined ? undefined : toInstant(period.end);
        windows.push({
            label: period?.type === 'USAGE_PERIOD_TYPE_WEEKLY' ? 'weekly' : 'credits',
            usedPercent: percent ?? 0,
            ...resetsAt === undefined ? {} : { resetsAt },
        });
    }
    else {
        const limit = toNumber(isRecord(config.monthlyLimit) ? config.monthlyLimit.val : undefined);
        if (limit !== undefined && limit > 0) {
            const used = toNumber(isRecord(config.used) ? config.used.val : undefined) ?? 0;
            const resetsAt = toInstant(config.billingPeriodEnd);
            windows.push({
                label: 'credits',
                usedPercent: Math.min(100, (used / limit) * 100),
                used,
                limit,
                ...resetsAt === undefined ? {} : { resetsAt },
            });
        }
    }
    if (windows.length === 0)
        return undefined;
    // The upstream response rarely carries the tier, so the access token's own
    // claim is the working fallback.
    const stated = typeof payload.subscriptionTier === 'string' && payload.subscriptionTier.length > 0
        ? payload.subscriptionTier
        : undefined;
    const plan = stated ?? grokTierName(input.accessToken);
    return { windows, ...plan === undefined ? {} : { plan } };
}
