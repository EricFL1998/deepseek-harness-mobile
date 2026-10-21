/**
 * Xiaomi MiMo's sign-in, as the MiMo desktop app performs it.
 *
 * A Xiaomi account signed in on account.xiaomi.com's long-poll page (a QR code
 * for the Xiaomi phone app, or a password) yields a passToken, which signs the
 * account on at a MiMo server. The server answers with session cookies, and model
 * requests are chat completions at the server's `/route` carrying those cookies.
 *
 * This module is the sign-in surface only: starting a QR/password attempt on the
 * account server, waiting for the passToken, exchanging it for a MiMo session,
 * and refreshing that session before the app would. Adapted from magpie's
 * `opencode-mimo-auth` for this plugin's typed flow/store contracts.
 *
 * @module dsh-plugin-subscription-oauth/auth/mimo-flow
 */
import { randomBytes } from 'node:crypto';
/** A MiMo server's regional hosts. */
export const MIMO_HOSTS = {
    SGP: 'https://mimo-server-sgp.xiaomimimo.com/api',
    RU: 'https://mimo-server-ru.xiaomimimimo.com/api',
    IN: 'https://mimo-server-in.xiaomimimo.com/api',
};
/** The region this build signs in at by default. */
export const MIMO_REGION = 'SGP';
const ACCOUNT = 'https://account.xiaomi.com';
const APP_VERSION = '26.929.292248';
const SOURCE = 'mimocode-cli-free';
const USER_AGENT = 'miNative PC/Normal Windows_NT/10.0.26100 SDKV/1.0.0 DEVT/PC DEVS/Windows APP/miaccount_desktop APPV/0.1.0';
/** The app signs on again after a day; renew this close to that ahead of time. */
export const MIMO_RENEW_AFTER_MS = 24 * 60 * 60 * 1000;
export const MIMO_RENEW_LEAD_MS = 10 * 60 * 1000;
/** A started sign-in page is valid for at most this long. */
const SIGN_IN_LIFE_MS = 10 * 60 * 1000;
const deviceId = () => `pc_${randomBytes(16).toString('hex')}`;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
/** The account server's JSON comes after a `&&&START&&&` guard. */
function accountJSON(text) {
    try {
        return JSON.parse(text.trim().replace(/^&&&START&&&/, ''));
    }
    catch {
        return null;
    }
}
/** A record check that does not throw on null/arrays. */
function isRecord(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
/** A cookie jar across the sign-on's redirects. */
export class Jar {
    cookies = [];
    put(cookie) {
        this.cookies = this.cookies.filter((other) => !(other.name === cookie.name && other.domain === cookie.domain && other.path === cookie.path));
        if (!cookie.gone)
            this.cookies.push(cookie);
    }
    seed(url, name, value) {
        this.put({
            name,
            value,
            domain: new URL(url).hostname.toLowerCase(),
            hostOnly: false,
            path: '/',
            secure: false,
        });
    }
    take(response, url) {
        const at = new URL(url);
        const lines = typeof response.headers.getSetCookie === 'function'
            ? response.headers.getSetCookie()
            : [];
        for (const line of lines) {
            const [pair, ...attrs] = line.split(';');
            const eq = pair.indexOf('=');
            if (eq <= 0)
                continue;
            const cookie = {
                name: pair.slice(0, eq).trim(),
                value: pair.slice(eq + 1).trim(),
                domain: at.hostname.toLowerCase(),
                hostOnly: true,
                secure: false,
                path: '/',
            };
            const slash = at.pathname.lastIndexOf('/');
            cookie.path = slash > 0 ? at.pathname.slice(0, slash) : '/';
            for (const attr of attrs) {
                const i = attr.indexOf('=');
                const key = (i < 0 ? attr : attr.slice(0, i)).trim().toLowerCase();
                const value = i < 0 ? '' : attr.slice(i + 1).trim();
                if (key === 'domain' && value) {
                    const domain = value.replace(/^\./, '').toLowerCase();
                    if (cookie.domain === domain || cookie.domain.endsWith(`.${domain}`)) {
                        cookie.domain = domain;
                        cookie.hostOnly = false;
                    }
                }
                else if (key === 'path' && value.startsWith('/')) {
                    cookie.path = value;
                }
                else if (key === 'secure') {
                    cookie.secure = true;
                }
                else if (key === 'max-age' && Number(value) <= 0) {
                    cookie.gone = true;
                }
                else if (key === 'expires' && Date.parse(value) <= Date.now()) {
                    cookie.gone = true;
                }
            }
            this.put(cookie);
        }
    }
    for(url) {
        const at = new URL(url);
        const host = at.hostname.toLowerCase();
        return this.cookies.filter((cookie) => {
            if (cookie.secure && at.protocol !== 'https:')
                return false;
            const match = cookie.hostOnly
                ? host === cookie.domain
                : host === cookie.domain || host.endsWith(`.${cookie.domain}`);
            if (!match)
                return false;
            return (at.pathname === cookie.path ||
                at.pathname.startsWith(cookie.path.endsWith('/') ? cookie.path : `${cookie.path}/`) ||
                cookie.path === '/');
        });
    }
    /** All cookies for one URL, as a single Cookie header value. */
    header(url) {
        return this.for(url)
            .map((cookie) => `${cookie.name}=${cookie.value}`)
            .join('; ');
    }
    /** Every cookie for one origin, as a name→value record. */
    forOrigin(origin) {
        const out = {};
        for (const cookie of this.for(origin))
            out[cookie.name] = cookie.value;
        return out;
    }
}
/** The Xiaomi sign-in has lapsed and the account must sign in again. */
export class MimoSignInLapsed extends Error {
}
/** The open platform's origin (the Token Plan lives here, not at a MiMo server). */
export const MIMO_PLATFORM = 'https://platform.xiaomimimo.com';
/** A plan the app refused covers for this long before its endpoint is asked again. */
const TP_LIFE_MS = 60 * 60 * 1000;
const appHeaders = () => ({
    'User-Agent': USER_AGENT,
    'X-Client-Version': APP_VERSION,
});
/** Seed a jar with the Xiaomi-account cookies a passToken needs. */
function seedAccount(jar, creds) {
    for (const [key, value] of [
        ['userId', creds.userId],
        ['passToken', creds.passToken],
        ['cUserId', creds.cUserId],
        ['deviceId', creds.deviceId],
        ['pass_ua', 'pc'],
        ['uLocale', 'zh_CN'],
    ]) {
        if (value)
            jar.seed(ACCOUNT, key, value);
    }
}
/**
 * Sign the account on at a MiMo server, following the serviceLogin redirects.
 *
 * The server's `/user/xiaomi/me`, asked with no session, sends the browser to
 * account.xiaomi.com's serviceLogin, which (holding the passToken) sends it back
 * through the server's `/sts`, where the session cookies are set.
 *
 * @param creds - the Xiaomi-account credential.
 * @param base - the MiMo server base for the account's region.
 * @param fetchFn - fetch implementation.
 * @returns the session cookies and the server's user id.
 */
export async function mimoSignOn(creds, base, fetchFn) {
    const jar = new Jar();
    seedAccount(jar, creds);
    const baseUrl = new URL(base);
    let url = base.replace(/\/+$/, '') + '/user/xiaomi/me';
    let response;
    for (let hop = 0;; hop++) {
        if (hop > 10)
            throw new Error('Xiaomi MiMo sign-in: too many redirects');
        const headers = appHeaders();
        const cookie = jar.header(url);
        if (cookie)
            headers.Cookie = cookie;
        try {
            response = await fetchFn(url, { headers, redirect: 'manual', signal: AbortSignal.timeout(30_000) });
        }
        catch (error) {
            throw new Error(`Xiaomi MiMo sign-in: ${error instanceof Error ? error.message : String(error)}`);
        }
        jar.take(response, url);
        const location = response.headers.get('location');
        if (response.status < 300 || response.status >= 400 || !location)
            break;
        await response.arrayBuffer().catch(() => { });
        const next = new URL(location, url);
        // The server's followup is written http://; its cookies only go back over https.
        if (next.protocol === 'http:' && baseUrl.protocol === 'https:' && next.host.toLowerCase() === baseUrl.host.toLowerCase()) {
            next.protocol = 'https:';
        }
        url = next.href;
    }
    const text = await response.text();
    let me;
    try {
        me = JSON.parse(text);
    }
    catch {
        // Xiaomi's sign-in page, not the server's answer: the passToken no longer signs on.
        throw new MimoSignInLapsed('Xiaomi MiMo: the Xiaomi sign-in has lapsed; sign in again');
    }
    const body = isRecord(me) ? me : {};
    if (response.status === 403 || body.code === 403 || body.code === 46109) {
        throw new Error("Xiaomi MiMo doesn't serve this Xiaomi account (its region isn't served here)");
    }
    const data = isRecord(body.data) ? body.data : {};
    if (body.code !== 0 || String(data.userId ?? '') === '') {
        throw new MimoSignInLapsed('Xiaomi MiMo: the Xiaomi sign-in has lapsed; sign in again');
    }
    const cookies = jar.forOrigin(baseUrl.href);
    if (Object.keys(cookies).length === 0) {
        throw new Error('Xiaomi MiMo sign-in: the server set no session');
    }
    return { cookies, userId: String(data.userId) };
}
/** Read the serviceLogin the MiMo server would send a sessionless browser to. */
export async function mimoServiceLogin(base, fetchFn) {
    let sid = 'mimosgp';
    let callback = base.replace(/\/+$/, '') + '/sts';
    try {
        const response = await fetchFn(base.replace(/\/+$/, '') + '/user/xiaomi/me', {
            headers: appHeaders(),
            redirect: 'manual',
            signal: AbortSignal.timeout(15_000),
        });
        await response.arrayBuffer().catch(() => { });
        const location = response.headers.get('location');
        if (location) {
            const query = new URL(location, base).searchParams;
            sid = query.get('sid') || sid;
            callback = query.get('callback') || callback;
        }
    }
    catch {
        // The defaults serve the SGP host.
    }
    return { sid, callback };
}
/** Start a Xiaomi sign-in: the QR/password page URL and its long-poll endpoint. */
export async function startMimoSignIn(fetchFn) {
    const base = MIMO_HOSTS[MIMO_REGION];
    const device = deviceId();
    const { sid, callback } = await mimoServiceLogin(base, fetchFn);
    const query = new URLSearchParams({
        _group: 'DEFAULT',
        _qrsize: '240',
        qs: `%3Fsid%3D${encodeURIComponent(sid)}%26_json%3Dtrue`,
        callback,
        _hasLogo: 'false',
        sid,
        serviceParam: '',
        _locale: 'en_US',
    });
    const response = await fetchFn(`${ACCOUNT}/longPolling/loginUrl?${query}`, {
        headers: { 'User-Agent': USER_AGENT, Cookie: `deviceId=${device}; pass_ua=pc; uLocale=zh_CN` },
        signal: AbortSignal.timeout(20_000),
    });
    const text = await response.text();
    const lp = accountJSON(text);
    if (!isRecord(lp) || lp.code !== 0 || typeof lp.loginUrl !== 'string' || typeof lp.lp !== 'string') {
        const desc = isRecord(lp) && typeof lp.desc === 'string' ? lp.desc : text.trim().slice(0, 200);
        throw new Error(`Xiaomi MiMo sign-in: account.xiaomi.com answered ${response.status} ${desc}`);
    }
    const timeoutSeconds = typeof lp.timeout === 'number' && lp.timeout > 0 ? lp.timeout : 0;
    const life = timeoutSeconds > 0 ? Math.min(timeoutSeconds * 1000, SIGN_IN_LIFE_MS) : SIGN_IN_LIFE_MS;
    return { url: lp.loginUrl, lp: lp.lp, device, until: Date.now() + life };
}
/** Wait for the long poll to report the account signed in; resolves with the poll's fields. */
export async function waitMimoPoll(start, fetchFn) {
    while (Date.now() < start.until) {
        try {
            const left = start.until - Date.now();
            const response = await fetchFn(start.lp, {
                headers: { 'User-Agent': USER_AGENT, Cookie: `deviceId=${start.device}; pass_ua=pc; uLocale=zh_CN` },
                signal: AbortSignal.timeout(Math.min(70_000, Math.max(left, 1))),
            });
            const poll = response.status === 200 ? accountJSON(await response.text()) : null;
            if (isRecord(poll) && typeof poll.passToken === 'string' && String(poll.userId ?? '') !== '') {
                return poll;
            }
        }
        catch {
            // Transient long-poll hiccup: keep asking until it expires.
        }
        await sleep(1000);
    }
    throw new Error('the Xiaomi sign-in page expired before the sign-in finished; start it again');
}
/** Build the durable credential from a finished poll and the device it signed on with. */
export function mimoCredentialsFrom(poll, device) {
    return {
        userId: String(poll.userId),
        cUserId: typeof poll.cUserId === 'string' ? poll.cUserId : '',
        passToken: String(poll.passToken),
        deviceId: device,
        region: MIMO_REGION,
        base: MIMO_HOSTS[MIMO_REGION],
    };
}
/**
 * Complete a sign-in: exchange the poll's passToken for a MiMo session, following a
 * region redirect when the account belongs to another region's server.
 *
 * @param poll - the long poll's settled fields.
 * @param device - the device id the sign-in started with.
 * @param fetchFn - fetch implementation.
 * @returns the durable credentials and the session cookies.
 */
export async function finishMimoSignIn(poll, device, fetchFn) {
    let creds = mimoCredentialsFrom(poll, device);
    let session;
    try {
        session = await mimoSignOn(creds, creds.base, fetchFn);
    }
    catch (error) {
        if (error instanceof MimoSignInLapsed) {
            throw new Error('Xiaomi signed the account in, but the MiMo server did not take it; try again');
        }
        throw error;
    }
    // An account of another region is served by that region's server.
    // The region is reported on the signed-on session's /user/xiaomi/me; re-derive it.
    const region = MIMO_REGION;
    const regionalBase = MIMO_HOSTS[region];
    if (regionalBase !== creds.base) {
        try {
            session = await mimoSignOn(creds, regionalBase, fetchFn);
            creds = { ...creds, region, base: regionalBase };
        }
        catch {
            // Stay on the original server.
        }
    }
    return { creds: { ...creds, userId: session.userId || creds.userId }, cookies: session.cookies };
}
/** Refresh: the session is re-signed-on at the MiMo server from the durable credential. */
export async function refreshMimoSession(creds, fetchFn) {
    const session = await mimoSignOn(creds, creds.base, fetchFn);
    return { cookies: session.cookies, expiresAt: Date.now() + MIMO_RENEW_AFTER_MS };
}
/** The MiMo server's names for its plans' tiers. */
const TIERS = { 1: 'Starter', 2: 'Plus', 3: 'Pro', 4: 'Ultra' };
/** Read one of the MiMo server's pages with the session's cookies; signs on again once when refused. */
async function mimoPage(session, creds, path, fetchFn) {
    for (let attempt = 0;; attempt++) {
        const headers = { Cookie: cookieHeaderOf(session.cookies), ...appHeaders() };
        const response = await fetchFn(creds.base.replace(/\/+$/, '') + path, {
            headers,
            signal: AbortSignal.timeout(20_000),
        });
        const text = await response.text();
        let env = {};
        try {
            env = JSON.parse(text);
        }
        catch {
            env = {};
        }
        const bad = !isRecord(env) || Array.isArray(env) || (env.code != null && !Number.isInteger(env.code)) ||
            (env.msg != null && typeof env.msg !== 'string');
        if (response.status === 401 || (response.status === 200 && bad)) {
            if (attempt === 0) {
                // The session went stale: re-sign on once and retry.
                const renewed = await refreshMimoSession(creds, fetchFn);
                session = { cookies: renewed.cookies, userId: session.userId };
                continue;
            }
            throw new MimoSignInLapsed('the Xiaomi MiMo sign-in has expired — sign in again');
        }
        if (response.status !== 200 || bad) {
            throw new Error(`Xiaomi MiMo ${path}: HTTP ${response.status}`);
        }
        if ((env.code ?? 0) !== 0) {
            throw new Error(`Xiaomi MiMo ${path}: code ${String(env.code)} ${String(env.msg ?? '')}`.trim());
        }
        return isRecord(env.data) ? env.data : null;
    }
}
/** The session's cookies as one Cookie header, in a steady order. */
function cookieHeaderOf(cookies) {
    const first = ['serviceToken', 'userId', 'cUserId'];
    const parts = first.filter((key) => key in cookies).map((key) => `${key}=${cookies[key]}`);
    for (const [key, value] of Object.entries(cookies)) {
        if (!first.includes(key))
            parts.push(`${key}=${value}`);
    }
    return parts.join('; ');
}
/** Read one of the server's times; they carry no zone and are Beijing/Singapore time. */
function mimoServerTime(value) {
    const text = String(value ?? '').trim();
    let iso = '';
    if (/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(Z|[+-]\d\d:\d\d)$/i.test(text))
        iso = text;
    else if (/^\d{4}-\d\d-\d\d[T ]\d\d:\d\d:\d\d(\.\d+)?$/.test(text))
        iso = `${text.replace(' ', 'T')}+08:00`;
    else if (/^\d{4}-\d\d-\d\d$/.test(text))
        iso = `${text}T00:00:00+08:00`;
    const at = iso === '' ? NaN : Date.parse(iso);
    return Number.isNaN(at) ? undefined : at;
}
/** The account's plan and weekly allowance, for the Settings card and chip. */
export async function readMimoUsage(session, creds, fetchFn) {
    // The plan, from the app's subscription page.
    let self = null;
    try {
        self = await mimoPage(session, creds, '/user/xiaomi/subscription/self', fetchFn);
    }
    catch {
        self = null;
    }
    const current = self?.current !== undefined && isRecord(self.current) ? self.current : undefined;
    let plan = 'Free';
    let renewsAt;
    if (current !== undefined) {
        const title = typeof current.title === 'string' ? current.title.trim() : '';
        const tier = typeof current.planTier === 'number' ? TIERS[current.planTier] : undefined;
        const code = typeof current.planCode === 'string' ? current.planCode.trim() : '';
        plan = title || tier || code || 'MiMo';
        const end = mimoServerTime(current.endTime);
        if (end !== undefined)
            renewsAt = end;
    }
    // The week's allowance, from the app's usage page.
    const windows = [];
    try {
        const use = await mimoPage(session, creds, '/user/usage', fetchFn);
        const percent = typeof use?.percent === 'number' ? use.percent : undefined;
        const reset = typeof use?.resetDate === 'string' ? use.resetDate : undefined;
        // No plan: the free offer shows no allowance.
        if (percent !== undefined && reset !== undefined) {
            const window = {
                label: '7 days',
                usedPercent: Math.max(0, Math.min(100, 100 - percent)),
            };
            const at = mimoServerTime(reset);
            if (at !== undefined)
                window.resetsAt = at;
            windows.push(window);
        }
    }
    catch {
        // The plan alone, when the week's allowance can't be read.
    }
    // The Token Plan at the open platform, an aside the app's requests don't spend.
    const tokenPlan = await readMimoTokenPlan(creds, fetchFn);
    if (tokenPlan !== null) {
        if (current === undefined) {
            plan = tokenPlan.plan;
            if (tokenPlan.until !== undefined)
                renewsAt = tokenPlan.until;
        }
        if (tokenPlan.window !== undefined)
            windows.push(tokenPlan.window);
    }
    return { plan, ...renewsAt === undefined ? {} : { renewsAt }, windows };
}
/** A short count of the Token Plan's credits, "6.81M" / "4.1B" / "12K". */
function mimoCredits(value) {
    for (const [divisor, unit] of [[1e9, 'B'], [1e6, 'M'], [1e3, 'K']]) {
        if (value >= divisor)
            return `${Number((value / divisor).toPrecision(3))}${unit}`;
    }
    return String(value);
}
/** Read one of the platform's /api/v1 pages, signing on afresh when the session is gone. */
async function mimoPlatformPage(creds, path, cookies, fetchFn) {
    const url = `${MIMO_PLATFORM}/api/v1${path}`;
    const ask = async (cookie) => {
        const response = await fetchFn(url, {
            headers: cookie === '' ? {} : { Cookie: cookie },
            redirect: 'manual',
            signal: AbortSignal.timeout(20_000),
        });
        return { status: response.status, text: await response.text() };
    };
    const parse = (text) => {
        try {
            const value = JSON.parse(text);
            return value?.code === 0 ? (isRecord(value.data) ? value.data : null) : undefined;
        }
        catch {
            return undefined;
        }
    };
    if (cookies !== undefined && Object.keys(cookies).length > 0) {
        const { status, text } = await ask(cookieHeaderOf(cookies));
        const data = status === 200 ? parse(text) : undefined;
        if (data !== undefined)
            return { cookies, data };
    }
    let { status, text } = await ask('');
    let login;
    try {
        login = JSON.parse(text).loginUrl;
    }
    catch {
        login = undefined;
    }
    if (status !== 401 || typeof login !== 'string' || !login.startsWith('https://account.xiaomi.com/')) {
        throw new Error(`Xiaomi MiMo platform ${path}: HTTP ${status}`);
    }
    // Follow the serviceLogin redirect with the passToken, collecting the platform's cookies.
    const jar = new Jar();
    for (const [key, value] of [
        ['userId', creds.userId], ['passToken', creds.passToken], ['cUserId', creds.cUserId],
        ['deviceId', creds.deviceId], ['pass_ua', 'pc'], ['uLocale', 'zh_CN'],
    ]) {
        if (value)
            jar.seed('https://account.xiaomi.com', key, value);
    }
    let at = login;
    for (let hop = 0;; hop++) {
        if (hop > 10)
            throw new Error('Xiaomi MiMo platform sign-in: too many redirects');
        const cookie = jar.header(at);
        const response = await fetchFn(at, {
            headers: cookie === '' ? {} : { Cookie: cookie },
            redirect: 'manual',
            signal: AbortSignal.timeout(20_000),
        });
        jar.take(response, at);
        const location = response.headers.get('location');
        if (response.status < 300 || response.status >= 400 || !location) {
            status = response.status;
            text = await response.text();
            break;
        }
        await response.arrayBuffer().catch(() => { });
        const next = new URL(location, at);
        if (next.protocol === 'http:' && next.host === new URL(MIMO_PLATFORM).host)
            next.protocol = 'https:';
        at = next.href;
    }
    const data = status === 200 ? parse(text) : undefined;
    if (data === undefined)
        throw new Error(`Xiaomi MiMo platform ${path}: the Xiaomi account didn't sign on (HTTP ${status})`);
    return { cookies: jar.forOrigin(`${MIMO_PLATFORM}/`), data };
}
/** Read the account's Token Plan, or null when it has none or the platform can't be read. */
async function readMimoTokenPlan(creds, fetchFn) {
    try {
        const detail = await mimoPlatformPage(creds, '/tokenPlan/detail', undefined, fetchFn);
        const p = detail.data;
        const name = p !== null && typeof p.planName === 'string' ? p.planName.trim() : '';
        if (name === '' || p?.expired !== false)
            return null;
        const out = { plan: `Token Plan ${name}` };
        const end = typeof p.currentPeriodEnd === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(p.currentPeriodEnd)
            ? Date.parse(p.currentPeriodEnd.replace(' ', 'T') + 'Z')
            : NaN;
        if (!Number.isNaN(end))
            out.until = end;
        try {
            const usage = await mimoPlatformPage(creds, '/tokenPlan/usage', detail.cookies, fetchFn);
            const u = usage.data;
            const items = [
                ...(isRecord(u) && isRecord(u.monthUsage) && Array.isArray(u.monthUsage.items) ? u.monthUsage.items : []),
                ...(isRecord(u) && isRecord(u.usage) && Array.isArray(u.usage.items) ? u.usage.items : []),
            ].filter(isRecord);
            const it = items.find((x) => x.name === 'month_total_token') ?? items.find((x) => x.name === 'plan_total_token');
            const used = typeof it?.used === 'number' ? it.used : NaN;
            const limit = typeof it?.limit === 'number' ? it.limit : NaN;
            if (Number.isFinite(used) && Number.isFinite(limit) && limit > 0) {
                const window = {
                    label: 'Token Plan',
                    usedPercent: Math.max(0, Math.min(100, (100 * used) / limit)),
                    used,
                    limit,
                };
                if (out.until !== undefined)
                    window.resetsAt = out.until;
                out.window = window;
            }
        }
        catch {
            // The plan alone, when its usage can't be read.
        }
        return out;
    }
    catch {
        return null;
    }
}
/** The account's Token Plan as a usable API credential, or null when it has none. */
export async function readMimoPlanKey(creds, fetchFn) {
    try {
        let cookies;
        const read = async (path) => {
            const result = await mimoPlatformPage(creds, path, cookies, fetchFn);
            cookies = result.cookies;
            return result.data;
        };
        const detail = await read('/tokenPlan/detail');
        if (detail === null || typeof detail.planName !== 'string' || detail.planName.trim() === '' || detail.expired !== false) {
            return null;
        }
        const keyInfo = await read('/tokenPlan/apiKey');
        const base = typeof keyInfo?.openaiBaseUrl === 'string' && /^https:\/\/[^/]+\.xiaomimimo\.com\//.test(keyInfo.openaiBaseUrl)
            ? keyInfo.openaiBaseUrl.replace(/\/+$/, '')
            : '';
        if (base === '')
            return null;
        const raw = await read('/tokenPlan/apiKey/raw');
        const rawKey = typeof raw === 'string' ? raw : '';
        if (!rawKey.startsWith('tp-'))
            return null;
        return { key: rawKey, base };
    }
    catch {
        return null;
    }
}
