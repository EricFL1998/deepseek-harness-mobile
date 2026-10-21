/**
 * Antigravity's live catalog and its two metered allowances.
 *
 * `v1internal:fetchAvailableModels` reports far more than the agent can call —
 * tabs, commit-message models, transcription, standalone image generation — and it
 * publishes one row per reasoning tier. The authoritative list of callable models
 * is the response's own `agentModelSorts` grouping, with two documented extras, and
 * complete `low`/`medium`/`high` families belong to one picker row.
 *
 * The account's allowance is stated by `v1internal:retrieveUserQuotaSummary` in
 * groups (Gemini and Claude/3P), each with a rolling and a weekly bucket — except that
 * the Gemini group's rolling bucket never moves, so the rolling figure also comes from
 * the per-model quota the same catalog publishes.
 *
 * @module dsh-plugin-subscription-oauth/providers/antigravity-catalog
 */
import { baseCandidatesOf } from './grok.js';
/** Effort rungs that fold into one picker row. */
const EFFORTS = ['low', 'medium', 'high'];
/** True for a plain object usable as a JSON record. */
function isRecord(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
/** Read a string field, or undefined when it is absent or blank. */
function text(value) {
    return typeof value === 'string' && value.length > 0 ? value : undefined;
}
/** Read one model's display name. */
function displayNameOf(id, info) {
    const stated = text(info.displayName);
    if (stated !== undefined)
        return stated;
    return id.split('-').map((word) => word.length === 0 ? word : word[0]?.toUpperCase() + word.slice(1)).join(' ');
}
/** Read one model's limits from the wire shape. */
function modelInfo(raw) {
    if (!isRecord(raw))
        return {};
    const maxTokens = typeof raw.maxTokens === 'number' && Number.isFinite(raw.maxTokens) ? raw.maxTokens : undefined;
    const maxInputTokens = typeof raw.maxInputTokens === 'number' && Number.isFinite(raw.maxInputTokens)
        ? raw.maxInputTokens
        : undefined;
    const inputTokenLimit = typeof raw.inputTokenLimit === 'number' && Number.isFinite(raw.inputTokenLimit)
        ? raw.inputTokenLimit
        : undefined;
    return {
        ...text(raw.displayName) === undefined ? {} : { displayName: text(raw.displayName) },
        ...maxTokens === undefined ? {} : { maxTokens },
        ...maxInputTokens === undefined ? {} : { maxInputTokens },
        ...inputTokenLimit === undefined ? {} : { inputTokenLimit },
        ...raw.supportsImages === true ? { supportsImages: true } : {},
    };
}
/** Model ids that are image-generation models, never picker rows. */
const IMAGE_MODEL = /(?:^|[-_])(?:image|imagine|draw|pic|photo)(?:[-_]|$)/i;
/** True when a model id names an image-generation model. */
export function isImageModel(id) {
    return IMAGE_MODEL.test(id);
}
/**
 * The wire id one gemini flash family and effort level map to when no live listing
 * has been parsed yet.
 *
 * The live listing is the authority (see `wireIdFor`); this is the fallback that
 * keeps a cold start — a persisted selection used before discovery ran — from
 * sending a bare family id the endpoint does not serve. The service names tiers
 * inconsistently: the High tier of 3.5 Flash is `gemini-3.5-flash-high` in some
 * groups and `gemini-3.5-flash-agent` in others; 3.8 Flash is
 * `gemini-3.8-flash-tiered`.
 */
export function geminiTierId(base, effort) {
    if (effort === 'high') {
        if (base === 'gemini-3.5-flash')
            return 'gemini-3.5-flash-agent';
        if (base === 'gemini-3.8-flash')
            return 'gemini-3.8-flash-tiered';
        if (base === 'gemini-3-flash')
            return 'gemini-3-flash-agent';
    }
    return `${base}-${effort}`;
}
/** Antigravity's own reasoning ladder, per model family. */
export function antigravityEffortsFor(model) {
    if (/^claude-/.test(model))
        return ['low', 'medium', 'high', 'max'];
    if (/^gpt-oss-/.test(model))
        return ['low', 'medium', 'high'];
    if (/^gemini-/.test(model))
        return ['low', 'medium', 'high'];
    return [];
}
// ── magpie-style family collapse ─────────────────────────────────────────
/** One effort word of Antigravity's ids and the level it means. */
const EFFORT_WORDS = [
    { word: 'extra-high', level: 'xhigh' },
    { word: 'extra-low', level: 'minimal' },
    { word: 'xhigh', level: 'xhigh' },
    { word: 'minimal', level: 'minimal' },
    // A flash family's High tier is spelled `-high`, `-tiered` or `-agent` depending on
    // the generation, so all three mean the same rung of the ladder.
    { word: 'tiered', level: 'high' },
    { word: 'agent', level: 'high' },
    { word: 'low', level: 'low' },
    { word: 'medium', level: 'medium' },
    { word: 'high', level: 'high' },
    { word: 'max', level: 'max' },
];
/** A ladder's canonical order. */
const LEVEL_ORDER = ['minimal', 'low', 'medium', 'high', 'xhigh', 'max'];
/** An id's family and the level its effort word says, '' when it has none. */
function splitEffortId(id) {
    for (const { word, level } of EFFORT_WORDS) {
        if (id.endsWith(`-${word}`)) {
            const base = id.slice(0, id.length - word.length - 1);
            if (base.length > 0)
                return { family: base, effort: level };
        }
    }
    return { family: id, effort: '' };
}
/** The level a display name's bracketed suffix says ("Gemini 3.7 Flash (High)" → high). */
function nameEffort(name) {
    const open = name.lastIndexOf(' (');
    if (open < 0 || !name.endsWith(')'))
        return { base: name, effort: '' };
    const inner = name.slice(open + 2, -1).toLowerCase();
    let effort = '';
    if (inner === 'minimal' || inner === 'extra low')
        effort = 'minimal';
    else if (inner === 'low')
        effort = 'low';
    else if (inner === 'medium')
        effort = 'medium';
    else if (inner === 'high')
        effort = 'high';
    else if (inner === 'extra high' || inner === 'xhigh')
        effort = 'xhigh';
    if (effort === '')
        return { base: name, effort: '' };
    return { base: name.slice(0, open).trim(), effort };
}
/**
 * Group raw entries into the families the service names.
 *
 * Grouping follows the display name, not the id, because the service names the family
 * and lets the wire id drift: the High tier of Gemini 3.5 Flash is
 * `gemini-3-flash-agent` — an id that names a different generation than the name it
 * carries. A level is stated by the name's own bracket when it has one, and by the
 * id's effort word otherwise; the family a variant belongs to is the longest listed
 * prefix of its own id, so `gemini-3.5-flash-low` folds into whichever listed row its
 * prefix names with no hand-written family map.
 *
 * @param raw - one entry per wire id the listing exposed.
 * @returns the families, in the order their first member appeared.
 */
function familiesOf(raw) {
    const byName = new Map();
    const order = [];
    const listed = new Set(raw.map((entry) => entry.id));
    const familyOf = (id, fallback) => {
        for (const base of baseCandidatesOf(id)) {
            if (listed.has(base))
                return base;
        }
        return fallback;
    };
    for (const entry of raw) {
        const { base: name, effort: named } = nameEffort(entry.name);
        const { family: idBase, effort: idLevel } = splitEffortId(entry.id);
        // The family's key: the name's bracket-free base, when the name states a level, so
        // "Gemini 3.5 Flash (Medium)" is always that family even if no listed id starts
        // with `gemini-3.5-flash`; otherwise the longest listed prefix of the id itself.
        const familyBase = named !== '' ? name : (idBase !== entry.id ? familyOf(entry.id, name) : name);
        let f = byName.get(familyBase);
        if (f === undefined) {
            f = { name: familyBase, variants: [], base: '' };
            byName.set(familyBase, f);
            order.push(f);
        }
        if (f.base === '' && idBase !== entry.id)
            f.base = idBase;
        f.variants.push({
            id: entry.id,
            contextWindow: entry.contextWindow,
            inputModalities: entry.inputModalities ?? ['text'],
            level: named !== '' ? named : idLevel,
        });
    }
    return order;
}
/** The rungs a family defaults to, best first, when no variant is the bare model. */
const DEFAULT_ORDER = ['xhigh', 'high', 'medium', 'low', 'minimal', 'max'];
/** The variant whose id the family's row carries: the bare model, else its top rung. */
function defaultVariant(f) {
    const bare = f.variants.find((v) => v.level === '');
    if (bare !== undefined)
        return bare;
    for (const level of DEFAULT_ORDER) {
        const found = f.variants.find((v) => v.level === level);
        if (found !== undefined)
            return found;
    }
    return f.variants[0];
}
/**
 * The wire id each picker row must send per effort level, as the last parsed family
 * listing reported it.
 *
 * Collapsing gives the picker one row per family, but the endpoint only serves the
 * suffixed ids the listing named: the High tier of Gemini 3.5 Flash is
 * `gemini-3-flash-agent`, and `gpt-oss-120b` only exists as `gpt-oss-120b-medium`.
 * Nothing here is invented — a level the listing did not name falls back to the
 * family's own default id, and only a tiered gemini family may synthesise the tier
 * spelling the endpoint accepts (see `geminiTierId`).
 */
const wireIds = new Map();
/**
 * The wire id one picker row and effort level must dispatch.
 *
 * @param rowId - the row the picker offered.
 * @param effort - the chosen level, when the caller picked one.
 * @returns the wire id, or undefined when no live listing covers this row.
 */
export function wireIdFor(rowId, effort) {
    const row = wireIds.get(rowId);
    if (row === undefined)
        return undefined;
    const chosen = effort === undefined ? undefined : row.levels.get(effort);
    if (chosen !== undefined)
        return chosen;
    // A tiered gemini family the listing exposed only one way still takes its own tier
    // spelling for the three levels this build knows (`gemini-3.8-flash` + low →
    // `gemini-3.8-flash-low`). Nothing else is synthesised.
    const tiered = effort === 'low' || effort === 'medium' || effort === 'high';
    if (tiered && row.base !== '' && row.base !== rowId)
        return geminiTierId(row.base, effort);
    return row.levels.get('') ?? rowId;
}
/** Collapse families into one row each, as magpie does. */
export function collapseFamilies(raw) {
    const out = [];
    for (const f of familiesOf(raw)) {
        const levels = new Map();
        for (const v of f.variants)
            if (!levels.has(v.level))
                levels.set(v.level, v.id);
        const def = defaultVariant(f);
        levels.set('', def.id);
        wireIds.set(def.id, { levels, base: f.base });
        const aliases = f.variants.map((v) => v.id).filter((id) => id !== def.id);
        out.push({
            // The row keeps an id the account listed, so a selection made from it stays
            // dispatchable; the name is what the picker shows.
            id: def.id,
            name: f.name,
            ...aliases.length === 0 ? {} : { aliases },
            contextWindow: Math.min(...f.variants.map((v) => v.contextWindow)),
            inputModalities: def.inputModalities,
            // Only the rungs this listing really serves: a level with no id of its own must
            // not be offered as a choice the request would ignore.
            reasoningEfforts: LEVEL_ORDER.filter((level) => levels.has(level)),
        });
    }
    return out;
}
/**
 * The ids the agent can actually call, in the order the response groups them.
 *
 * @param payload - the decoded `fetchAvailableModels` body.
 * @returns the agent-callable wire ids, or an empty array when the shape is unknown.
 */
function agentIds(payload) {
    if (!isRecord(payload))
        return [];
    const models = payload.models;
    if (!isRecord(models))
        return [];
    const ids = [];
    const push = (id) => {
        if (typeof id !== 'string' || id.length === 0 || !Object.hasOwn(models, id))
            return;
        if (!ids.includes(id))
            ids.push(id);
    };
    // The response's own agent grouping is the authority on what the agent may call.
    const sorts = payload.agentModelSorts;
    if (Array.isArray(sorts)) {
        for (const sort of sorts) {
            if (!isRecord(sort) || !Array.isArray(sort.groups))
                continue;
            for (const group of sort.groups) {
                if (!isRecord(group) || !Array.isArray(group.modelIds))
                    continue;
                for (const id of group.modelIds)
                    push(id);
            }
        }
    }
    // Two ids the agent surface exposes outside that grouping.
    if (Array.isArray(payload.imageGenerationModelIds)) {
        for (const id of payload.imageGenerationModelIds) {
            if (id === 'gemini-3.1-flash-image')
                push(id);
        }
    }
    if (isRecord(payload.tieredModelIds)) {
        for (const key of Object.keys(payload.tieredModelIds)) {
            const list = payload.tieredModelIds[key];
            if (Array.isArray(list)) {
                for (const id of list)
                    push(id);
            }
        }
    }
    // No usable grouping: fall back to every entry, which is still better than nothing
    // as long as the non-agent surfaces are excluded.
    if (ids.length === 0) {
        for (const id of Object.keys(models)) {
            if (NON_AGENT.test(id))
                continue;
            push(id);
        }
    }
    // The response also carries models under API_PROVIDER_GOOGLE_GEMINI /
    // ANTHROPIC_VERTEX / OPENAI_VERTEX that the agent grouping does not name (3.1 Pro,
    // 3 Flash, the flash family). Keep every one of those; NON_AGENT surfaces stay out.
    // Old generations (2.5) are dropped: the picker is for the current models.
    const OLD_GENERATION = /gemini-2\./;
    for (const [id, value] of Object.entries(models)) {
        if (NON_AGENT.test(id))
            continue;
        if (OLD_GENERATION.test(id))
            continue;
        if (!isRecord(value))
            continue;
        const api = value.apiProvider;
        if (api === 'API_PROVIDER_GOOGLE_GEMINI' || api === 'API_PROVIDER_ANTHROPIC_VERTEX' || api === 'API_PROVIDER_OPENAI_VERTEX') {
            push(id);
        }
    }
    return ids;
}
/** Surfaces the agent envelope cannot call. */
const NON_AGENT = /(?:^|[-_])(?:tab|tabs|commit|transcription|audio|embedding|code-review)(?:$|[-_])/i;
/**
 * Read the agent-callable catalog from a `fetchAvailableModels` response.
 *
 * The response's own grouping decides which ids the agent envelope may call; this
 * removes the surfaces it cannot (tabs, commit messages, transcription, standalone
 * image generation) and keeps the response's own order. Tiers of one family then
 * collapse into the single picker row that serves them, with a wire-id map so the
 * request still names an id the account listed (see `wireIdFor`).
 *
 * @param payload - the decoded response.
 * @returns the catalog entries, in the response's order.
 */
export function parseAntigravityModels(payload) {
    if (!isRecord(payload) || !isRecord(payload.models))
        return [];
    const models = payload.models;
    const raw = [];
    for (const wireId of agentIds(payload)) {
        // Image-generation models are never picker rows: they are agent-called, always on.
        if (isImageModel(wireId))
            continue;
        const info = modelInfo(models[wireId]);
        const contextWindow = info.maxTokens ?? info.maxInputTokens ?? info.inputTokenLimit ?? 1_048_576;
        raw.push({
            id: wireId,
            name: displayNameOf(wireId, models[wireId] ?? {}),
            contextWindow,
            inputModalities: info.supportsImages === true ? ['text', 'image'] : ['text'],
        });
    }
    return collapseFamilies(raw);
}
/** The object that states one quota reading, unwrapping a nested `remaining`. */
function quotaTargetOf(raw) {
    if (!isRecord(raw))
        return undefined;
    return isRecord(raw.remaining) ? raw.remaining : raw;
}
/** A quota reading normalised to a percentage spent, or undefined when it states none. */
function usedPercentOf(raw) {
    const target = quotaTargetOf(raw);
    if (target === undefined)
        return undefined;
    const fraction = typeof target.remainingFraction === 'number' && Number.isFinite(target.remainingFraction)
        ? target.remainingFraction
        : undefined;
    const percentage = typeof target.remainingPercentage === 'number' && Number.isFinite(target.remainingPercentage)
        ? target.remainingPercentage
        : undefined;
    const remaining = fraction !== undefined ? fraction * 100 : percentage;
    if (remaining === undefined)
        return undefined;
    return Math.max(0, Math.min(100, 100 - remaining));
}
/**
 * A quota reading as a percentage spent, counting an unstated remainder as spent.
 *
 * The service drops `remainingFraction` from a bucket once that allowance is gone — a spent
 * bucket states only its reset — so an object that says anything at all but states no
 * remainder is a spent one, not an unknown one. Reading it as unknown is what left the
 * Claude allowance pinned at zero while the account's own summary called it exhausted.
 *
 * @param raw - one bucket or one model's `quotaInfo`.
 * @returns the percentage spent, or undefined when the object states nothing.
 */
export function quotaSpentPercent(raw) {
    const stated = usedPercentOf(raw);
    if (stated !== undefined)
        return stated;
    const target = quotaTargetOf(raw);
    if (target === undefined)
        return undefined;
    // An empty object names no allowance, so it states nothing either way.
    return Object.keys(target).length > 0 ? 100 : undefined;
}
/** A reset instant from an RFC 3339 string. */
function resetOf(raw) {
    if (!isRecord(raw))
        return undefined;
    const value = raw.resetTime;
    if (typeof value !== 'string' || value.length === 0)
        return undefined;
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : undefined;
}
/**
 * Every allowance window the usage card can show, in the order it shows them.
 *
 * A summary response names some of these and not others — no host is guaranteed to state
 * all four — so the list is also what a union of several responses is ordered by.
 */
export const QUOTA_WINDOW_LABELS = ['gemini', 'gemini_weekly', 'claude', 'claude_weekly'];
/**
 * How far ahead a weekly bucket resets, and so the least a reset must be to be the weekly one.
 *
 * The service states one bucket per model: whichever one it is enforcing. A reset a few hours
 * out is the rolling window; one a week out is the weekly bucket the summary also reports.
 */
const ROLLING_RESET_MAX_MS = 8 * 60 * 60 * 1000;
/**
 * Read the two allowances from a `retrieveUserQuotaSummary` response.
 *
 * The summary groups models by family and reports a rolling and a weekly bucket per
 * group, which is what a user recognises as "the Gemini allowance" and "the Claude
 * allowance".
 *
 * A bucket the service marks `disabled` does not apply at that instant — with the weekly
 * limit spent, Google reports the group's five-hour bucket as full and disables it, and the
 * five-hour limit is not what is holding the account back. Reporting that as "0% used" tells
 * a user the family is free for the next five hours, which is the opposite of the truth, so
 * a disabled bucket states no window at all.
 *
 * @param payload - the decoded response.
 * @returns one window per bucket found, Gemini first.
 */
export function parseAntigravityQuotaSummary(payload) {
    if (!isRecord(payload) || !Array.isArray(payload.groups))
        return [];
    const found = new Map();
    for (const raw of payload.groups) {
        if (!isRecord(raw))
            continue;
        const label = `${text(raw.displayName) ?? ''} ${text(raw.description) ?? ''}`.toLowerCase();
        const family = label.includes('gemini') ? 'gemini'
            : label.includes('claude') || label.includes('3p') || label.includes('gpt') ? 'claude'
                : undefined;
        if (family === undefined || !Array.isArray(raw.buckets))
            continue;
        for (const rawBucket of raw.buckets) {
            if (!isRecord(rawBucket))
                continue;
            if (rawBucket.disabled === true)
                continue;
            const window = `${text(rawBucket.window) ?? ''} ${text(rawBucket.bucketId) ?? ''} ${text(rawBucket.displayName) ?? ''}`.toLowerCase();
            const weekly = window.includes('week');
            const rolling = window.includes('5h') || window.includes('five');
            if (!weekly && !rolling)
                continue;
            const key = weekly ? `${family}_weekly` : family;
            if (found.has(key))
                continue;
            const usedPercent = quotaSpentPercent(rawBucket);
            if (usedPercent === undefined)
                continue;
            const resetsAt = resetOf(rawBucket);
            found.set(key, { label: key, usedPercent, ...resetsAt === undefined ? {} : { resetsAt } });
        }
    }
    return QUOTA_WINDOW_LABELS.flatMap((key) => {
        const window = found.get(key);
        return window === undefined ? [] : [window];
    });
}
/**
 * Read the per-model quota as this account's allowance windows.
 *
 * Each model states one bucket: whichever the service is enforcing right now. A reset a few
 * hours out is the rolling window and one a week out is the weekly bucket, so the reset is
 * what tells the two apart — which matters once the weekly limit is what binds, because the
 * per-model figure then carries the weekly one instead of the five-hour one.
 *
 * A model whose `quotaInfo` states its reset but no remainder has spent its allowance: the
 * service drops `remainingFraction` once nothing is left.
 *
 * @param payload - the decoded `fetchAvailableModels` body.
 * @param now - the instant a reset is measured from.
 * @returns one window per family and bucket, at the worst-spent model.
 */
export function parseAntigravityModelQuota(payload, now = Date.now()) {
    if (!isRecord(payload) || !isRecord(payload.models))
        return [];
    const models = payload.models;
    const worst = new Map();
    for (const [id, raw] of Object.entries(models)) {
        if (!isRecord(raw))
            continue;
        const entries = Array.isArray(raw.quotaInfo) ? raw.quotaInfo : [raw.quotaInfo, ...(Array.isArray(raw.quotaInfos) ? raw.quotaInfos : [])];
        for (const quota of entries) {
            if (!isRecord(quota))
                continue;
            const tier = text(quota.tier) ?? '';
            const haystack = `${id} ${text(raw.displayName) ?? ''} ${tier}`.toLowerCase();
            const family = haystack.includes('gemini') ? 'gemini'
                : /claude|opus|sonnet|gpt-oss|gpt_oss/.test(haystack) ? 'claude'
                    : undefined;
            if (family === undefined)
                continue;
            const usedPercent = quotaSpentPercent(quota);
            if (usedPercent === undefined)
                continue;
            // An entry that names a window says which bucket it means; one that names none states
            // whichever bucket the service is enforcing, which its reset tells apart.
            const window = `${text(quota.window) ?? ''} ${text(quota.bucketId) ?? ''} ${text(raw.window) ?? ''} ${text(raw.bucketId) ?? ''}`.toLowerCase();
            const resetsAt = resetOf(quota) ?? resetOf(raw);
            const weekly = window.includes('week') || (resetsAt !== undefined && resetsAt - now > ROLLING_RESET_MAX_MS);
            const key = weekly ? `${family}_weekly` : family;
            const seen = worst.get(key);
            if (seen !== undefined && (seen.usedPercent ?? 0) >= usedPercent)
                continue;
            worst.set(key, { label: key, usedPercent, ...resetsAt === undefined ? {} : { resetsAt } });
        }
    }
    return QUOTA_WINDOW_LABELS.flatMap((key) => {
        const window = worst.get(key);
        return window === undefined ? [] : [window];
    });
}
/**
 * Reduce the catalog's per-model quota to this account's allowance windows.
 *
 * The catalog is rebuilt into the wire shape the shared parser reads, so both this route's
 * cache fallback and its merge read a per-model figure the same way.
 *
 * @param quotas - quota per model id, as the catalog stated it.
 * @param now - the instant a reset is measured from.
 * @returns one window per family and bucket that stated a figure, Gemini first.
 */
export function antigravityWindowsFromModels(quotas, now = Date.now()) {
    const models = {};
    for (const [id, quota] of quotas)
        models[id] = { quotaInfo: quota };
    return parseAntigravityModelQuota({ models }, now);
}
/**
 * Take a row's figure from the per-model quota when it reports more spent.
 *
 * The summary is not always the live number. Its Gemini group states a five-hour bucket that
 * can answer `remainingFraction: 1` however much the account has spent, which pins the
 * rolling row at zero for as long as the account is used. The same family's quota is metered
 * per model, and `fetchAvailableModels` states the enforced bucket there, so a row takes
 * whichever source reports more spent — the account summary's own accounting, or the
 * per-model reading of the bucket it names.
 *
 * @param summary - windows from `retrieveUserQuotaSummary`, in card order.
 * @param perModel - windows reduced from the catalog's per-model quota.
 * @returns one row per window either source stated, in card order.
 */
export function mergeQuotaWindows(summary, perModel) {
    const found = new Map();
    for (const window of summary)
        found.set(window.label, window);
    for (const window of perModel) {
        const stated = found.get(window.label);
        if (stated !== undefined && (stated.usedPercent ?? 0) >= (window.usedPercent ?? 0))
            continue;
        found.set(window.label, window);
    }
    return QUOTA_WINDOW_LABELS.flatMap((label) => {
        const window = found.get(label);
        return window === undefined ? [] : [window];
    });
}
