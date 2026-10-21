/**
 * The composer usage chip's pure helpers.
 *
 * Split from the component so the test suite can assert the reduction from a
 * route's usage to one line without evaluating the component's React and
 * popover imports (the primitives package's published artifact pulls a
 * markdown and icon toolchain a bare Node run cannot resolve). `UsageChip.tsx`
 * re-exports everything here, so the public surface is unchanged.
 *
 * @module dsh-plugin-subscription-oauth/client/chip-helpers
 */
import { en } from './locales.js';
/**
 * Abbreviations for the routes this plugin serves.
 *
 * The bar is a status line under the composer, where a full product name crowds out the
 * shell's own pills; the full name is one click away. The fallback shortens an unknown
 * route's display name rather than dropping it.
 */
const SHORT_LABELS = {
    kimi: 'Kimi',
    grok: 'Grok',
    antigravity: 'Anti',
    glm: 'GLM',
    ark: 'Ark',
};
/**
 * Abbreviations for the window names the providers use.
 *
 * The bar has room for a token or two, so `weekly` becomes `7d` — the same figure in a
 * form that reads at a glance. An unrecognised window keeps its own name, shortened
 * only if it is long.
 */
const SHORT_WINDOWS = {
    'weekly': '7d',
    '7d': '7d',
    '5h': '5h',
    'monthly': 'mo',
    'daily': '1d',
    'credits': 'cr',
    'free': 'free',
    'session': 's',
    // Antigravity meters each family twice, and its labels are wire ids: without these the
    // compact line would show four cells all reading `gemi` and name neither the family nor
    // the window it belongs to.
    'gemini': 'G5h',
    'gemini_weekly': 'G7d',
    'claude': 'C5h',
    'claude_weekly': 'C7d',
};
/** The session's provider, from its model-selection projection. */
export function providerOf(projection) {
    return (projection?.next ?? projection?.lastUsed)?.provider;
}
/**
 * Reduce the node half's balance payload to the wallets the chip shows.
 *
 * Bonus wallets are shown after the topped-up ones, so a reader sees their own credit
 * first and any promotional credit as a secondary figure. A bonus wallet that holds
 * nothing is dropped rather than shown as `+¥0`: the platform reports one for every
 * currency whether or not there is a bonus, and an empty one is noise, not information.
 *
 * @param value - the node half's balance, or undefined when there is none.
 * @returns the wallets, topped-up first then bonus, or undefined when nothing is usable.
 */
export function balanceOf(value) {
    if (value === null || value === undefined)
        return undefined;
    const wallets = [
        ...(value.wallets ?? []).map((wallet) => ({ currency: wallet.currency, balance: wallet.balance, bonus: false })),
        // Only a bonus wallet that actually holds something is worth a line.
        ...(value.bonusWallets ?? [])
            .filter((wallet) => Number.parseFloat(wallet.balance) > 0)
            .map((wallet) => ({ currency: wallet.currency, balance: wallet.balance, bonus: true })),
    ];
    return wallets.length === 0 ? undefined : wallets;
}
/**
 * Format one wallet for the chip's compact line.
 *
 * A balance is money, not a percentage, so it renders as `¥6.89` rather than a meter;
 * the currency prefix keeps two currencies from being mistaken for one sum. The amount
 * is rounded to two decimal places (a balance reads to the cent, and a figure like
 * `1.1` for `1.10` would mislead) and always carries both places.
 *
 * @param wallet - the wallet to show.
 * @returns the short form, e.g. `¥6.89` or `+$2.00` for a bonus wallet.
 */
export function formatBalance(wallet) {
    const amount = Number.parseFloat(wallet.balance);
    const trimmed = Number.isFinite(amount) ? amount.toFixed(2) : wallet.balance;
    const symbol = wallet.currency === 'CNY' ? '¥' : wallet.currency === 'USD' ? '$' : `${wallet.currency} `;
    return `${wallet.bonus ? '+' : ''}${symbol}${trimmed}`;
}
/**
 * The short form of a route's name.
 *
 * @param provider - the route id.
 * @param label - the route's own display name.
 * @returns an abbreviation that still identifies the route.
 */
export function shortLabel(provider, label) {
    const known = SHORT_LABELS[provider];
    if (known !== undefined)
        return known;
    // An unknown route keeps its first word, so the chip names something recognisable
    // rather than a generic placeholder.
    const [first] = label.trim().split(/\s+/);
    return first === undefined || first === '' ? provider : first;
}
/**
 * The short form of a window's name.
 *
 * @param label - the window's own name, e.g. `weekly`.
 * @returns an abbreviation, or the label itself when it is already short.
 */
export function shortWindow(label) {
    const known = SHORT_WINDOWS[label.toLowerCase()];
    if (known !== undefined)
        return known;
    return label.length <= 4 ? label : label.slice(0, 4);
}
/**
 * The name a window reads as in the panel.
 *
 * A provider's window label is a wire id — `gemini_weekly` is not something to put in front
 * of a reader — so a label this build has copy for is named in the reader's language, and
 * anything else keeps the name its provider gave it.
 *
 * @param t - copy for the active locale.
 * @param label - the window's own label.
 * @returns the display name.
 */
export function windowName(t, label) {
    const key = `usage_${label}`;
    return key in en ? t(key) : label;
}
/**
 * How one window reads as a figure.
 *
 * A credit balance (WorkBuddy's Credits, a wallet) is money: it reads as the raw
 * `used`/`limit` count. A quota is a share of an allowance: it reads as the spent
 * percent. The two are told apart by what the window is called — a credit or wallet
 * names money, a time window names an allowance — because both state the same fields.
 *
 * @param window - the metered window.
 * @returns the display figure.
 */
export function windowAmount(window) {
    if (isCreditWindow(window.label) && window.used !== undefined && window.limit !== undefined) {
        return `${String(window.used)}/${String(window.limit)}`;
    }
    return `${String(Math.round(window.usedPercent ?? 0))}%`;
}
/** Credit-like window labels: money, not a share of an allowance. */
const CREDIT_LABELS = /credit|wallet|balance|token|point|积分|额度/i;
/** Whether a window's label names a credit balance rather than a time quota. */
function isCreditWindow(label) {
    return CREDIT_LABELS.test(label);
}
/**
 * Pick the window a single line should show.
 *
 * A plan can meter several windows at once (a rolling five-hour one and a weekly one).
 * The most constrained is the one that will stop work soonest, so it earns the space;
 * ties fall to the earlier window, which is the order the providers state them in.
 *
 * @param windows - the route's metered windows.
 * @returns the window to show, or undefined when none states a figure.
 */
export function pickWindow(windows) {
    let best;
    for (const window of windows) {
        if (window.usedPercent === undefined)
            continue;
        if (best?.usedPercent === undefined || window.usedPercent > best.usedPercent)
            best = window;
    }
    return best;
}
/**
 * Reduce a route's per-account usage to one reading per account.
 *
 * A provider can hold several accounts (two Antigravity sign-ins, say), and the chip
 * shows each one's allowance on its own line, the way the settings cards do — a merged
 * figure would hide that one account is exhausted while the other is free.
 *
 * @param usage - every account the route answered for.
 * @returns one reading per account that states a figure, in the route's order.
 */
/**
 * Reduce a route's per-account usage to one reading per account.
 *
 * A provider can hold several accounts (two Antigravity sign-ins, say). Every account's
 * windows are kept as the route stated them — a percentage-metered quota and a credit
 * balance (`credits`) alike — so the per-account panel shows the real figures, and the
 * compact line's merge decides which to integrate.
 *
 * @param usage - every account the route answered for.
 * @returns one reading per account that states any window, in the route's order.
 */
export function accountReadingsOf(usage) {
    const out = [];
    for (const entry of usage) {
        if (entry.windows.length === 0)
            continue;
        out.push({ account: entry.account, windows: entry.windows, ...entry.plan === undefined ? {} : { plan: entry.plan } });
    }
    return out;
}
/**
 * Merge every account's percentage-metered windows into one figure per label.
 *
 * The chip's compact line integrates the whole pool: two accounts at 50 percent read as
 * one window at 50 percent (the same allowance spent twice as wide), not two lines. A
 * credit balance (`credits`) is money, not a share of a shared allowance, so it is not
 * merged — it stays on the per-account panel, where it reads correctly. Only windows
 * that state a `usedPercent` (a share of an allowance) merge.
 *
 * @param readings - one per account, from {@link accountReadingsOf}.
 * @returns one merged window per percentage-metered label, most-spent first.
 */
export function mergeAccountWindows(readings) {
    const byLabel = new Map();
    for (const reading of readings) {
        for (const window of reading.windows) {
            // Only a percentage-metered window merges. A credit balance (a bare used/limit
            // pair) is money, not a share of a shared allowance, and averaging it across
            // accounts would misread money as a quota.
            if (window.usedPercent === undefined)
                continue;
            const list = byLabel.get(window.label) ?? [];
            list.push(window);
            byLabel.set(window.label, list);
        }
    }
    const merged = [];
    for (const [label, list] of byLabel) {
        // Percentages average across accounts: the pool's spent share is the mean of each
        // account's spent share (accounts share an allowance, they do not add to it).
        const withPercent = list.filter((window) => window.usedPercent !== undefined);
        if (withPercent.length === 0)
            continue;
        const usedPercent = withPercent.reduce((sum, window) => sum + (window.usedPercent ?? 0), 0) / withPercent.length;
        // A credit window carries the raw figures too; keep them so the compact line can read
        // money as money (0/1500) rather than as a spent share (0%), which misreads an empty
        // balance as nothing to meter.
        const used = list.reduce((sum, window) => sum + (window.used ?? 0), 0);
        const limit = list.reduce((sum, window) => sum + (window.limit ?? 0), 0);
        // The pool's reset is the soonest account reset: the window refills when the first
        // account's does.
        const resets = list.map((window) => window.resetsAt).filter((at) => typeof at === 'number');
        merged.push({
            label,
            usedPercent: Math.max(0, Math.min(100, usedPercent)),
            ...limit > 0 ? { used, limit } : {},
            ...resets.length === 0 ? {} : { resetsAt: Math.min(...resets) },
        });
    }
    // Order by window period, shortest first: the rolling window reads before the weekly one,
    // the way the providers state them and the way a reader scans (what resets soonest is the
    // nearer limit). A family's rolling window is named after the family (`gemini`, `claude`),
    // so it is the abbreviation — not the label — that says which period a cell is.
    const order = (label) => {
        const short = shortWindow(label).toLowerCase();
        if (short.endsWith('5h') || short === 's')
            return 0;
        if (short === '1d' || short === 'mo')
            return 1;
        if (short.endsWith('7d'))
            return 2;
        return 3;
    };
    return merged.sort((left, right) => order(left.label) - order(right.label));
}
/**
 * Reduce one route's usage to the single line the compact chip renders.
 *
 * Kept for the balance-free providers that still show one merged line.
 *
 * @param provider - the route id.
 * @param label - the route's display name.
 * @param usage - the accounts the route answered for.
 * @returns the first account's reading, or undefined when none states a figure.
 */
export function readingOf(provider, label, usage) {
    for (const entry of usage) {
        const window = pickWindow(entry.windows);
        if (window === undefined)
            continue;
        const windows = entry.windows.filter((candidate) => candidate.usedPercent !== undefined);
        return {
            provider,
            label,
            windows,
            window,
            ...entry.plan === undefined ? {} : { plan: entry.plan },
        };
    }
    return undefined;
}
/**
 * Format a window's reset the way the settings cards do.
 *
 * The rule is shared with the cards on purpose: a figure reading `6d20h` here and
 * something else there would be two answers to one question.
 *
 * @param at - epoch milliseconds, or undefined when the provider states none.
 * @param now - the instant to measure from.
 * @returns a short phrase, or undefined when there is no reset to show.
 */
export function formatReset(at, now) {
    if (at === undefined)
        return undefined;
    const remaining = at - now;
    if (remaining <= 0)
        return 'now';
    const hours = remaining / 3_600_000;
    if (hours < 1)
        return `${String(Math.max(1, Math.round(remaining / 60_000)))}m`;
    if (hours < 24)
        return `${String(Math.round(hours))}h`;
    const days = Math.floor(hours / 24);
    return `${String(days)}d${String(Math.round(hours - days * 24))}h`;
}
