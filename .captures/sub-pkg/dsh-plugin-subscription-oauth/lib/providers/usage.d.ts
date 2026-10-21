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
import type { UsageWindow } from '../protocol.js';
import type { FetchFn } from '../http.js';
/** Provider-neutral usage read, before the controller stamps it. */
export interface UsageRead {
    plan?: string;
    renewsAt?: number;
    windows: readonly UsageWindow[];
    /**
     * Why the figures are not the provider's primary accounting.
     *
     * Set when a route had to publish a substitute — Antigravity's per-model quota
     * when its account summary was unreachable — so the page can say so instead of
     * presenting the substitute as the plan's own numbers.
     */
    usageError?: string;
}
/** The `/usages` endpoint on the Kimi Code base. */
export declare const KIMI_USAGE_PATH = "/usages";
/** The Grok Build CLI chat proxy's billing endpoint. */
export declare const GROK_BILLING_URL = "https://cli-chat-proxy.grok.com/v1/billing?format=credits";
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
export declare function readKimiUsage(input: {
    apiBase: string;
    accessToken: string;
    fetchFn?: FetchFn;
}): Promise<UsageRead | undefined>;
/** Read the subscription tier a Grok access token carries. */
export declare function grokTierName(accessToken: string): string | undefined;
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
export declare function readGrokUsage(input: {
    accessToken: string;
    fetchFn?: FetchFn;
}): Promise<UsageRead | undefined>;
