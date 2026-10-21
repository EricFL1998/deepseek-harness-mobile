/**
 * WorkBuddy China's sign-in and token handling.
 *
 * A WorkBuddy account signs in in the browser (Tencent's page, polled for the
 * token as WorkBuddy's desktop app does). Chats are chat completions at
 * `<endpoint>/v2`, streamed only, and the API answers `{code, msg, data}` where a
 * non-zero code is an error. Adapted from magpie's `workbuddy` for this plugin's
 * typed flow/store contracts.
 *
 * @module dsh-plugin-subscription-oauth/auth/workbuddy-flow
 */
/** WorkBuddy China build's endpoints. */
export declare const WORKBUDDY_ENDPOINT = "https://copilot.tencent.com";
export declare const WORKBUDDY_PLATFORM = "workbuddy";
/** The version the sign-in page is opened with. */
export declare const WORKBUDDY_APP_VERSION = "2.0.0";
/** The User-Agent version the chat API accepts; another is refused (10085). */
export declare const WORKBUDDY_UA_VERSION = "5.5.6";
/** A token this close to its end is refreshed before a request. */
export declare const WORKBUDDY_EARLY_MS = 60000;
/** A sign-in attempt is abandoned after this long. */
export declare const WORKBUDDY_SIGN_IN_MS: number;
/** One signed-in WorkBuddy session, as stored. */
export interface WorkBuddySession {
    accessToken: string;
    refreshToken?: string;
    expiresAt: number;
    accountId?: string;
    email?: string;
    uid?: string;
    enterpriseId?: string;
    domain?: string;
    tokenType?: string;
    refreshExpiresAt?: number;
}
/** Validate one stored value as a WorkBuddy session, or undefined when it is not one. */
export declare function parseWorkBuddySession(value: unknown): WorkBuddySession | undefined;
/** A WorkBuddy API failure, carrying the wire code and magpie's phrasing. */
export declare class WBError extends Error {
    readonly code: number | string | undefined;
    readonly status: number;
    readonly said: string;
    constructor(message: string, code: number | string | undefined, status: number, said: string);
}
/** The chat completions endpoint for the China build. */
export declare const workbuddyApi: (endpoint: string) => string;
/**
 * Make one request of WorkBuddy's plugin API, which answers `{code, msg, data}`.
 * A non-zero `code`, or a non-2xx status, is an error.
 *
 * @param endpoint - the site base, e.g. `https://copilot.tencent.com`.
 * @param method - HTTP method.
 * @param path - path beneath the base.
 * @param headers - extra headers.
 * @param body - JSON body, when present.
 * @param fetchFn - fetch implementation.
 * @returns the response's `data`.
 */
export declare function workbuddyCall(endpoint: string, method: string, path: string, headers: Record<string, string>, body: unknown, fetchFn: typeof fetch): Promise<unknown>;
/** The state WorkBuddy's sign-in page starts from. */
export interface WorkBuddySignInStart {
    state: string;
    url: string;
    expiresAt: number;
}
/** Start a browser sign-in: the page URL to open, and the state to poll for the token. */
export declare function startWorkBuddySignIn(fetchFn: typeof fetch): Promise<WorkBuddySignInStart>;
/** One WorkBuddy token's fields, normalised from the sign-in or refresh response. */
export interface WorkBuddyTokenSet {
    accessToken: string;
    refreshToken?: string;
    expiresAt: number;
    refreshExpiresAt?: number;
    domain?: string;
    tokenType?: string;
    uid?: string;
    enterpriseId?: string;
}
/** Merge a token WorkBuddy gave into the stored sign-in's fields. */
export declare function mergeWorkBuddyToken(base: WorkBuddyTokenSet, got: Record<string, unknown>): WorkBuddyTokenSet;
/** Poll for the token a finished browser sign-in produced. */
export declare function finishWorkBuddySignIn(start: WorkBuddySignInStart, fetchFn: typeof fetch): Promise<{
    token: WorkBuddyTokenSet;
    name: string;
}>;
/** The domain a token names, else the API host. */
export declare function workbuddyDomain(token: Pick<WorkBuddyTokenSet, 'domain'>): string;
/** The headers WorkBuddy's desktop client sends with a chat, as magpie's workbuddy sets. */
export declare function workbuddyHeaders(token: WorkBuddyTokenSet): Record<string, string>;
/** Whether a token is close enough to its end that a request should refresh it. */
export declare function workbuddyStale(token: Pick<WorkBuddyTokenSet, 'expiresAt'>): boolean;
/** Whether a token can still be refreshed. */
export declare function workbuddyRefreshable(token: WorkBuddyTokenSet): boolean;
/** Spend a refresh token for a new access token. */
export declare function refreshWorkBuddyToken(token: WorkBuddyTokenSet, fetchFn: typeof fetch): Promise<WorkBuddyTokenSet>;
/** One metered window the Settings card reads. */
export interface WorkBuddyUsageWindow {
    label: string;
    usedPercent?: number;
    used?: number;
    limit?: number;
    resetsAt?: number;
}
/** The account's check-in state, from the billing activity endpoint. */
export interface WorkBuddyCheckin {
    active: boolean;
    todayCheckedIn: boolean;
    streakDays: number;
    todayCredit: number;
}
/** One growth task, as the activity endpoint frames it. */
export interface WorkBuddyTask {
    taskCode: string;
    title: string;
    credit: number;
    energy: number;
    target: number;
    current: number;
    claimable: boolean;
    claimed: boolean;
    locked: boolean;
    acceptStatus: string;
}
/** Read the account's growth tasks, parsed the way the pool plugin parses them. */
export declare function listWorkBuddyTasks(token: WorkBuddyTokenSet, fetchFn: typeof fetch): Promise<WorkBuddyTask[]>;
/** Accept (enrol in) tasks by code; replaying an accepted task is a safe no-op. */
export declare function acceptWorkBuddyTasks(token: WorkBuddyTokenSet, taskCodes: readonly string[], fetchFn: typeof fetch): Promise<void>;
/** Claim one task's reward; already-claimed answers zero and counts as success. */
export declare function claimWorkBuddyTask(token: WorkBuddyTokenSet, taskCode: string, fetchFn: typeof fetch): Promise<{
    credit: number;
    energy: number;
}>;
/** Read the account's daily check-in state, without changing anything. */
export declare function readWorkBuddyCheckin(token: WorkBuddyTokenSet, fetchFn: typeof fetch): Promise<WorkBuddyCheckin>;
/** Claim today's check-in reward; resolves with the credit granted and the streak. */
export declare function claimWorkBuddyCheckin(token: WorkBuddyTokenSet, fetchFn: typeof fetch): Promise<{
    credit: number;
    streakDays: number;
}>;
/** The account's precise remaining credits, summed across its live packages. */
export declare function readWorkBuddyCredits(token: WorkBuddyTokenSet, fetchFn: typeof fetch): Promise<{
    remaining: number;
    total: number;
    nearestExpiryMs?: number;
} | null>;
/** Read the account's plan and metered credits for the Settings card. */
export declare function readWorkBuddyUsage(token: WorkBuddyTokenSet, plan: string | undefined, fetchFn: typeof fetch, spend?: {
    spent: number;
}): Promise<{
    plan: string;
    windows: WorkBuddyUsageWindow[];
    checkin?: WorkBuddyCheckin;
}>;
