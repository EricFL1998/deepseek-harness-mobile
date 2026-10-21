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
import type { UsageWindow } from '../protocol.js';
import type { CatalogEntry } from './catalog.js';
/** True when a model id names an image-generation model. */
export declare function isImageModel(id: string): boolean;
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
export declare function geminiTierId(base: string, effort: string): string;
/** Antigravity's own reasoning ladder, per model family. */
export declare function antigravityEffortsFor(model: string): readonly string[];
/**
 * The wire id one picker row and effort level must dispatch.
 *
 * @param rowId - the row the picker offered.
 * @param effort - the chosen level, when the caller picked one.
 * @returns the wire id, or undefined when no live listing covers this row.
 */
export declare function wireIdFor(rowId: string, effort?: string): string | undefined;
/** Collapse families into one row each, as magpie does. */
export declare function collapseFamilies(raw: CatalogEntry[]): CatalogEntry[];
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
export declare function parseAntigravityModels(payload: unknown): CatalogEntry[];
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
export declare function quotaSpentPercent(raw: unknown): number | undefined;
/**
 * Every allowance window the usage card can show, in the order it shows them.
 *
 * A summary response names some of these and not others — no host is guaranteed to state
 * all four — so the list is also what a union of several responses is ordered by.
 */
export declare const QUOTA_WINDOW_LABELS: readonly ["gemini", "gemini_weekly", "claude", "claude_weekly"];
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
export declare function parseAntigravityQuotaSummary(payload: unknown): UsageWindow[];
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
export declare function parseAntigravityModelQuota(payload: unknown, now?: number): UsageWindow[];
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
export declare function antigravityWindowsFromModels(quotas: ReadonlyMap<string, {
    remainingFraction?: number;
    resetTime?: string;
}>, now?: number): UsageWindow[];
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
export declare function mergeQuotaWindows(summary: readonly UsageWindow[], perModel: readonly UsageWindow[]): UsageWindow[];
