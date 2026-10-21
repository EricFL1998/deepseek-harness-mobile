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
export const WORKBUDDY_ENDPOINT = 'https://copilot.tencent.com';
export const WORKBUDDY_PLATFORM = 'workbuddy';
/** The version the sign-in page is opened with. */
export const WORKBUDDY_APP_VERSION = '2.0.0';
/** The User-Agent version the chat API accepts; another is refused (10085). */
export const WORKBUDDY_UA_VERSION = '5.5.6';
/** A token this close to its end is refreshed before a request. */
export const WORKBUDDY_EARLY_MS = 60_000;
/** A sign-in attempt is abandoned after this long. */
export const WORKBUDDY_SIGN_IN_MS = 5 * 60 * 1000;
const POLL_MS = 1000;
/** Validate one stored value as a WorkBuddy session, or undefined when it is not one. */
export function parseWorkBuddySession(value) {
    if (typeof value !== 'object' || value === null)
        return undefined;
    const record = value;
    if (typeof record.accessToken !== 'string' || record.accessToken.length === 0)
        return undefined;
    if (typeof record.expiresAt !== 'number')
        return undefined;
    return {
        accessToken: record.accessToken,
        expiresAt: record.expiresAt,
        ...typeof record.refreshToken === 'string' ? { refreshToken: record.refreshToken } : {},
        ...typeof record.accountId === 'string' ? { accountId: record.accountId } : {},
        ...typeof record.email === 'string' ? { email: record.email } : {},
        ...typeof record.uid === 'string' ? { uid: record.uid } : {},
        ...typeof record.enterpriseId === 'string' ? { enterpriseId: record.enterpriseId } : {},
        ...typeof record.domain === 'string' ? { domain: record.domain } : {},
        ...typeof record.tokenType === 'string' ? { tokenType: record.tokenType } : {},
        ...typeof record.refreshExpiresAt === 'number' ? { refreshExpiresAt: record.refreshExpiresAt } : {},
    };
}
/** A WorkBuddy API failure, carrying the wire code and magpie's phrasing. */
export class WBError extends Error {
    code;
    status;
    said;
    constructor(message, code, status, said) {
        super(message);
        this.code = code;
        this.status = status;
        this.said = said;
    }
}
const isRecord = (value) => typeof value === 'object' && value !== null && !Array.isArray(value);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
/** The chat completions endpoint for the China build. */
export const workbuddyApi = (endpoint) => `${endpoint}/v2`;
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
export async function workbuddyCall(endpoint, method, path, headers, body, fetchFn) {
    const response = await fetchFn(endpoint + path, {
        method,
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'User-Agent': `WorkBuddy/${WORKBUDDY_UA_VERSION}`,
            ...headers,
        },
        ...body === undefined ? {} : { body: JSON.stringify(body) },
        signal: AbortSignal.timeout(30_000),
    });
    const text = await response.text();
    let env = {};
    try {
        env = JSON.parse(text);
    }
    catch {
        env = {};
    }
    const code = Number.parseInt(String(env.code ?? ''), 10) || 0;
    if (!response.ok) {
        const why = String(env.msg ?? env.message ?? (isRecord(env.error) ? env.error.message : '') ?? '').trim() ||
            text.slice(0, 200) || response.statusText;
        const said = code ? String(env.msg ?? `error ${code}`) : String(env.msg ?? `${response.status}`);
        throw new WBError(`WorkBuddy: ${why} (HTTP ${response.status}${env.code ? `, code ${String(env.code)}` : ''})`, env.code, response.status, said);
    }
    if (code !== 0) {
        throw new WBError(`WorkBuddy: ${String(env.msg ?? 'error')} (${String(env.code)})`, env.code, response.status, String(env.msg ?? `error ${code}`));
    }
    return env.data;
}
/** Start a browser sign-in: the page URL to open, and the state to poll for the token. */
export async function startWorkBuddySignIn(fetchFn) {
    const data = await workbuddyCall(WORKBUDDY_ENDPOINT, 'POST', `/v2/plugin/auth/state?platform=${encodeURIComponent(WORKBUDDY_PLATFORM)}`, {
        'X-No-Authorization': 'true',
        'X-No-User-Id': 'true',
        'X-No-Enterprise-Id': 'true',
        'X-No-Department-Info': 'true',
    }, {}, fetchFn);
    if (!isRecord(data) || typeof data.state !== 'string' || typeof data.authUrl !== 'string') {
        throw new Error('WorkBuddy gave no sign-in page');
    }
    const url = new URL(data.authUrl);
    if (url.protocol !== 'https:')
        throw new Error("WorkBuddy's sign-in page isn't https");
    url.searchParams.set('version', WORKBUDDY_APP_VERSION);
    url.searchParams.set('loginSessionId', randomHex());
    return { state: data.state, url: url.toString(), expiresAt: Date.now() + WORKBUDDY_SIGN_IN_MS };
}
const randomHex = () => {
    const bytes = new Uint8Array(16);
    crypto.getRandomValues(bytes);
    return [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
};
/** Ask a path until it answers data, retrying the codes that mean "not yet". */
async function workbuddyPoll(path, headers, retry, deadline, fetchFn) {
    for (;;) {
        if (Date.now() > deadline)
            throw new Error('WorkBuddy sign-in timed out');
        try {
            const data = await workbuddyCall(WORKBUDDY_ENDPOINT, 'GET', path, headers, undefined, fetchFn);
            if (isRecord(data) && Object.keys(data).length > 0)
                return data;
        }
        catch (error) {
            if (!(error instanceof WBError && (retry.includes(Number(error.code)) || error.status === 408 || error.status === 429))) {
                throw error;
            }
        }
        await sleep(POLL_MS);
    }
}
/** Merge a token WorkBuddy gave into the stored sign-in's fields. */
export function mergeWorkBuddyToken(base, got) {
    const now = Date.now();
    const out = { ...base, accessToken: String(got.accessToken ?? '') };
    if (typeof got.refreshToken === 'string' && got.refreshToken !== '')
        out.refreshToken = got.refreshToken;
    if (typeof got.domain === 'string' && got.domain !== '')
        out.domain = got.domain;
    if (typeof got.tokenType === 'string' && got.tokenType !== '')
        out.tokenType = got.tokenType;
    const expiresAt = Number(got.expiresAt);
    const expiresIn = Number(got.expiresIn);
    if (expiresAt > 0)
        out.expiresAt = expiresAt;
    else if (expiresIn > 0)
        out.expiresAt = now + expiresIn * 1000;
    const refreshExpiresAt = Number(got.refreshExpiresAt);
    const refreshExpiresIn = Number(got.refreshExpiresIn);
    if (refreshExpiresAt > 0)
        out.refreshExpiresAt = refreshExpiresAt;
    else if (refreshExpiresIn > 0)
        out.refreshExpiresAt = now + refreshExpiresIn * 1000;
    return out;
}
/** Poll for the token a finished browser sign-in produced. */
export async function finishWorkBuddySignIn(start, fetchFn) {
    const deadline = start.expiresAt;
    const query = encodeURIComponent(start.state);
    const tokenData = await workbuddyPoll(`/v2/plugin/auth/token?state=${query}`, {}, [11217], deadline, fetchFn);
    if (typeof tokenData.accessToken !== 'string' || tokenData.accessToken === '') {
        throw new Error('WorkBuddy sent back no token');
    }
    const token = mergeWorkBuddyToken({ accessToken: '', expiresAt: 0 }, tokenData);
    const who = await workbuddyPoll(`/v2/plugin/login/account?state=${query}`, {
        'Authorization': `Bearer ${token.accessToken}`,
        'X-Domain': workbuddyDomain(token),
        'X-No-User-Id': 'true',
        'X-No-Enterprise-Id': 'true',
    }, [12151], deadline, fetchFn);
    token.uid = typeof who.uid === 'string' ? who.uid : '';
    token.enterpriseId = await workbuddyEnterprise(token, who, fetchFn);
    const name = String(who.nickname ?? who.phoneNumber ?? who.uid ?? '');
    return { token, name };
}
/** The domain a token names, else the API host. */
export function workbuddyDomain(token) {
    return token.domain || new URL(workbuddyApi(WORKBUDDY_ENDPOINT)).host;
}
/** The enterprise an account signed in as, when it isn't a personal account. */
async function workbuddyEnterprise(token, who, fetchFn) {
    const own = typeof who.enterpriseId === 'string' ? who.enterpriseId.trim() : '';
    if (own)
        return own;
    const type = String(who.type ?? '').trim().toLowerCase();
    if (type === '' || type === 'personal')
        return '';
    let list;
    try {
        list = await workbuddyCall(WORKBUDDY_ENDPOINT, 'GET', '/v2/plugin/accounts', { 'Authorization': `Bearer ${token.accessToken}`, 'X-Domain': workbuddyDomain(token) }, undefined, fetchFn);
    }
    catch {
        return '';
    }
    const accounts = isRecord(list) && Array.isArray(list.accounts) ? list.accounts.filter(isRecord) : [];
    const mine = accounts.filter((entry) => entry.uid === who.uid && typeof entry.enterpriseId === 'string' && entry.enterpriseId.trim() !== '');
    const same = mine.filter((entry) => String(entry.type ?? '').trim().toLowerCase() === type);
    const last = mine.filter((entry) => entry.lastLogin);
    const pick = same.length === 1 ? same[0] : last.length === 1 ? last[0] : mine.length === 1 ? mine[0] : undefined;
    return pick && typeof pick.enterpriseId === 'string' ? pick.enterpriseId.trim() : '';
}
/** The headers WorkBuddy's desktop client sends with a chat, as magpie's workbuddy sets. */
export function workbuddyHeaders(token) {
    const headers = {
        'Authorization': `Bearer ${token.accessToken}`,
        'X-User-Id': token.uid ?? '',
        'X-Domain': workbuddyDomain(token),
        'X-Product': 'SaaS',
        'X-IDE-Type': 'WorkBuddy',
        'User-Agent': `WorkBuddy/${WORKBUDDY_UA_VERSION}`,
        'X-Requested-With': 'XMLHttpRequest',
        'X-Agent-Intent': 'craft',
        'X-Agent-Purpose': 'conversation',
        'X-IDE-Name': 'WorkBuddy',
        'X-IDE-Version': WORKBUDDY_UA_VERSION,
    };
    const enterprise = typeof token.enterpriseId === 'string' ? token.enterpriseId.trim() : '';
    if (enterprise !== '') {
        headers['X-Enterprise-Id'] = enterprise;
        headers['X-Tenant-Id'] = enterprise;
    }
    return headers;
}
/** Whether a token is close enough to its end that a request should refresh it. */
export function workbuddyStale(token) {
    return token.expiresAt > 0 && Date.now() >= token.expiresAt - WORKBUDDY_EARLY_MS;
}
/** Whether a token can still be refreshed. */
export function workbuddyRefreshable(token) {
    if (typeof token.refreshToken !== 'string' || token.refreshToken === '')
        return false;
    return !(token.refreshExpiresAt !== undefined && Date.now() >= token.refreshExpiresAt);
}
/** Spend a refresh token for a new access token. */
export async function refreshWorkBuddyToken(token, fetchFn) {
    const data = await workbuddyCall(WORKBUDDY_ENDPOINT, 'POST', '/v2/plugin/auth/token/refresh', {
        'X-Refresh-Token': token.refreshToken ?? '',
        'X-Auth-Refresh-Source': 'plugin',
        'X-Domain': workbuddyDomain(token),
        ...workbuddyEnterpriseHeaders(token),
    }, {}, fetchFn);
    if (!isRecord(data) || typeof data.accessToken !== 'string' || data.accessToken === '') {
        throw new Error('WorkBuddy gave no refreshed token');
    }
    return mergeWorkBuddyToken(token, data);
}
function workbuddyEnterpriseHeaders(token) {
    const enterprise = typeof token.enterpriseId === 'string' ? token.enterpriseId.trim() : '';
    return enterprise === '' ? {} : { 'X-Enterprise-Id': enterprise, 'X-Tenant-Id': enterprise };
}
/** The chat request headers: the account's, plus the CLI conventions. */
function chatHeaders(token) {
    return {
        'Accept': 'application/json, text/plain, */*',
        'X-Requested-With': 'XMLHttpRequest',
        'Origin': 'https://copilot.tencent.com',
        'Referer': 'https://copilot.tencent.com/',
        'User-Agent': 'CLI/2.63.2 CodeBuddy/2.63.2',
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token.accessToken}`,
        ...token.uid === undefined || token.uid === '' ? { 'X-No-User-Id': '1' } : { 'X-User-Id': token.uid },
        ...token.enterpriseId === undefined || token.enterpriseId === '' ? { 'X-No-Enterprise-Id': '1' } : { 'X-Enterprise-Id': token.enterpriseId },
        'X-Product': 'SaaS',
    };
}
/** Ask the chat host one question, returning the decoded envelope's data. */
async function chatCall(token, method, path, body, fetchFn) {
    const response = await fetchFn(`https://copilot.tencent.com${path}`, {
        method,
        headers: chatHeaders(token),
        ...body === undefined ? {} : { body: JSON.stringify(body) },
        signal: AbortSignal.timeout(20_000),
    });
    let env = {};
    try {
        env = JSON.parse(await response.text());
    }
    catch {
        env = {};
    }
    const code = Number.parseInt(String(env.code ?? ''), 10) || 0;
    if (!response.ok || code !== 0) {
        throw new Error(`WorkBuddy ${path}: ${String(env.msg ?? `error ${code}`)}`);
    }
    return isRecord(env.data) ? env.data : null;
}
/** Read the account's growth tasks, parsed the way the pool plugin parses them. */
export async function listWorkBuddyTasks(token, fetchFn) {
    const data = await chatCall(token, 'GET', '/v2/activity/growth/tasks', undefined, fetchFn);
    if (data === null)
        return [];
    const raw = Array.isArray(data.tasks) ? data.tasks.filter(isRecord) : [];
    const out = [];
    for (const entry of raw) {
        const taskCode = typeof entry.task_code === 'string' ? entry.task_code : '';
        if (taskCode === '')
            continue;
        const num = (key) => capacity(entry[key]);
        let current = num('current');
        let target = num('target');
        if (isRecord(entry.progress)) {
            const nestedCurrent = capacity(entry.progress.current);
            const nestedTarget = capacity(entry.progress.target);
            if (nestedTarget > 0 || nestedCurrent > 0) {
                current = nestedCurrent;
                target = nestedTarget;
            }
        }
        const acceptStatus = typeof entry.accept_status === 'string' ? entry.accept_status : '';
        const claimed = acceptStatus === 'claimed';
        out.push({
            taskCode,
            title: typeof entry.title === 'string' && entry.title !== '' ? entry.title : taskCode,
            credit: num('reward_credit'),
            energy: num('reward_energy'),
            target,
            current,
            claimable: !claimed && target > 0 && current >= target,
            claimed,
            locked: entry.locked === true,
            acceptStatus,
        });
    }
    return out;
}
/** Accept (enrol in) tasks by code; replaying an accepted task is a safe no-op. */
export async function acceptWorkBuddyTasks(token, taskCodes, fetchFn) {
    if (taskCodes.length === 0)
        return;
    await chatCall(token, 'POST', '/v2/activity/growth/tasks/accept', { task_codes: [...taskCodes] }, fetchFn);
}
/** Claim one task's reward; already-claimed answers zero and counts as success. */
export async function claimWorkBuddyTask(token, taskCode, fetchFn) {
    const headers = {
        ...workbuddyHeaders(token),
        'Accept': 'application/json, text/plain, */*',
        'Origin': 'https://www.workbuddy.cn',
        'Referer': 'https://www.workbuddy.cn/profile/growth-center',
        'x-client-platform': 'web',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    };
    const response = await fetchFn(`https://www.workbuddy.cn/activity/growth/tasks/${encodeURIComponent(taskCode)}/claim`, {
        method: 'POST',
        headers,
        signal: AbortSignal.timeout(20_000),
    });
    let env = {};
    try {
        env = JSON.parse(await response.text());
    }
    catch {
        env = {};
    }
    const code = Number.parseInt(String(env.code ?? ''), 10) || 0;
    if (!response.ok || code !== 0) {
        throw new Error(`WorkBuddy task ${taskCode}: ${String(env.msg ?? `error ${code}`)}`);
    }
    const data = isRecord(env.data) ? env.data : {};
    if (data.already_claimed === true)
        return { credit: 0, energy: 0 };
    return { credit: capacity(data.credit), energy: capacity(data.energy) };
}
/** A capacity the billing API sends, as a string or number; empty is 0. */
function capacity(value) {
    if (value === null || value === undefined || value === '')
        return 0;
    if (typeof value === 'number')
        return value;
    const text = String(value);
    const number = Number(text);
    return Number.isNaN(number) ? 0 : number;
}
/** A count as the card says one: whole, else to two places. */
function compact(number) {
    return Number.isInteger(number)
        ? String(number)
        : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
}
/** Ask the billing API, saying its message, else its code, else the HTTP status. */
async function workbuddyMeter(token, path, body, fetchFn) {
    const response = await fetchFn(WORKBUDDY_ENDPOINT + path, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'User-Agent': `WorkBuddy/${WORKBUDDY_UA_VERSION}`,
            ...workbuddyHeaders(token),
        },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(20_000),
    });
    let env = {};
    try {
        env = JSON.parse(await response.text());
    }
    catch {
        env = {};
    }
    const code = Number.parseInt(String(env.code ?? ''), 10) || 0;
    if (!response.ok) {
        if (code)
            throw new Error(String(env.msg ?? `error ${code}`));
        if (env.msg)
            throw new Error(String(env.msg));
        throw new Error(`HTTP ${response.status}`);
    }
    if (code !== 0)
        throw new Error(String(env.msg ?? `error ${code}`));
    return isRecord(env.data) ? env.data : null;
}
/** Read the account's daily check-in state, without changing anything. */
export async function readWorkBuddyCheckin(token, fetchFn) {
    const data = await workbuddyMeter(token, '/billing/meter/checkin-activity-status', {}, fetchFn);
    if (data === null)
        return { active: false, todayCheckedIn: false, streakDays: 0, todayCredit: 0 };
    const num = (key) => typeof data[key] === 'number' ? data[key] : 0;
    return {
        active: data.active === true,
        todayCheckedIn: data.today_checked_in === true,
        streakDays: num('streak_days'),
        todayCredit: num('today_credit'),
    };
}
/** Claim today's check-in reward; resolves with the credit granted and the streak. */
export async function claimWorkBuddyCheckin(token, fetchFn) {
    const data = await workbuddyMeter(token, '/billing/meter/daily-checkin', {}, fetchFn);
    if (data === null)
        return { credit: 0, streakDays: 0 };
    const num = (key) => typeof data[key] === 'number' ? data[key] : 0;
    return { credit: num('credit'), streakDays: num('streak_days') };
}
/** The account's precise remaining credits, summed across its live packages. */
export async function readWorkBuddyCredits(token, fetchFn) {
    const pad = (n) => n.toString().padStart(2, '0');
    const fmt = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    const now = new Date();
    const data = await workbuddyMeter(token, '/billing/meter/get-user-resource', {
        PageNumber: 1,
        PageSize: 100,
        ProductCode: 'p_tcaca',
        Status: [0, 3],
        PackageEndTimeRangeBegin: fmt(now),
        PackageEndTimeRangeEnd: fmt(new Date(now.getTime() + 3185136e6)),
    }, fetchFn);
    if (data === null)
        return null;
    const response = isRecord(data.Response) ? data.Response : {};
    const inner = isRecord(response.Data) ? response.Data : {};
    const accounts = Array.isArray(inner.Accounts) ? inner.Accounts.filter(isRecord) : [];
    const parseDate = (raw) => {
        if (typeof raw === 'number' && raw > 0xe8d4a51000)
            return raw;
        if (typeof raw === 'string' && raw !== '') {
            const parsed = Date.parse(raw);
            if (!Number.isNaN(parsed))
                return parsed;
        }
        return undefined;
    };
    let remaining = 0;
    let totalSize = 0;
    let nearestExpiryMs;
    let livePackages = 0;
    for (const account of accounts) {
        const num = (key) => capacity(account[key]);
        const monthly = num('CapacityType') === 4;
        const remain = monthly ? num('CycleCapacityRemain') : num('CapacityRemain');
        const size = monthly ? num('CycleCapacitySize') : num('CapacitySize');
        const capped = remain < 0 ? 0 : remain;
        const cycleEndMs = parseDate(account.CycleEndTime);
        const expiresAtMs = monthly ? undefined : parseDate(account.ExpiredTime) ?? cycleEndMs;
        if (!monthly && (capped <= 0 || (expiresAtMs !== undefined && expiresAtMs <= Date.now())))
            continue;
        livePackages += 1;
        remaining += capped;
        totalSize += size > 0 ? size : capped;
        if (expiresAtMs !== undefined && (nearestExpiryMs === undefined || expiresAtMs < nearestExpiryMs)) {
            nearestExpiryMs = expiresAtMs;
        }
    }
    // No live package reads as "this account doesn't use the package list" — an enterprise
    // account, say — so the caller falls through to its own meter.
    if (livePackages === 0)
        return null;
    return { remaining, total: totalSize, ...nearestExpiryMs === undefined ? {} : { nearestExpiryMs } };
}
/** The account's credits from its resource summary: the plan's used against granted. */
function usageOfSummary(summary, plan) {
    const isPaid = summary?.IsPaidUser === true;
    const out = { plan: plan ?? (isPaid ? 'Pro' : 'Free'), windows: [] };
    let total = 0;
    let used = 0;
    const packages = Array.isArray(summary?.Packages) ? summary.Packages.filter(isRecord) : [];
    for (const pkg of packages) {
        total += capacity(pkg.CycleTotalCapacity);
        used += capacity(pkg.CycleUsedCapacity);
    }
    if (total > 0) {
        out.windows.push({
            label: 'Credits',
            usedPercent: (100 * used) / total,
            used,
            limit: total,
        });
    }
    return out;
}
/** An enterprise account's credits, from the enterprise meter; null when it grants none. */
function enterpriseUsageOf(data) {
    const limit = capacity(data?.limitNum);
    if (!(limit > 0))
        return null;
    const used = capacity(data?.credit);
    const window = { label: 'Credits', usedPercent: (100 * used) / limit, used, limit };
    const at = (value) => typeof value === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value.trim())
        ? Date.parse(value.trim().replace(' ', 'T') + '+08:00')
        : 0;
    const reset = at(data?.cycleResetTime);
    const start = at(data?.cycleStartTime);
    if (reset > 0)
        window.resetsAt = reset;
    return window;
}
/** Read the account's plan and metered credits for the Settings card. */
export async function readWorkBuddyUsage(token, plan, fetchFn, spend) {
    // The precise figures: the live packages' remaining and total size, and the account's
    // accumulated spend from the real per-turn credits. Spent-against-limit reads as the
    // true billed share, not an estimate from tokens.
    try {
        const credits = await readWorkBuddyCredits(token, fetchFn);
        if (credits !== null) {
            const window = { label: 'Credits', used: spend?.spent ?? 0 };
            // The bar reads spent against the total the packages grant, not remaining-plus-spent,
            // so a fresh cycle's spend shows small against its full grant.
            if (credits.total > 0) {
                window.limit = credits.total;
                window.usedPercent = (100 * (spend?.spent ?? 0)) / credits.total;
            }
            else if ((spend?.spent ?? 0) > 0) {
                window.limit = spend?.spent ?? 0;
                window.usedPercent = 100;
            }
            if (credits.nearestExpiryMs !== undefined)
                window.resetsAt = credits.nearestExpiryMs;
            const out = { plan: plan ?? 'Free', windows: [window] };
            try {
                out.checkin = await readWorkBuddyCheckin(token, fetchFn);
            }
            catch {
                // A check-in read failure never hides the credits.
            }
            return out;
        }
    }
    catch {
        // Fall through to the summary when the package list can't be read.
    }
    const summary = await workbuddyMeter(token, '/billing/meter/get-user-resource-summary', {}, fetchFn);
    const out = usageOfSummary(summary, plan);
    // An enterprise account's summary has no packages: its credits are the enterprise meter's.
    if (out.windows.length === 0) {
        try {
            const enterprise = enterpriseUsageOf(await workbuddyMeter(token, '/billing/meter/get-enterprise-user-usage', {}, fetchFn));
            if (enterprise !== null) {
                out.windows = [enterprise];
                out.plan = plan ?? 'Enterprise';
            }
        }
        catch {
            // The personal-account refusal (10001) leaves the card as the summary said.
        }
    }
    // Fold the check-in state in beside the credits: the card shows today's status and streak.
    try {
        out.checkin = await readWorkBuddyCheckin(token, fetchFn);
    }
    catch {
        // A check-in read failure never hides the credits.
    }
    return out;
}
