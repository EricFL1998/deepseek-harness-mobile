import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Subscriptions settings page.
 *
 * One card per provider route — Kimi Code, Grok (X Premium), Google Antigravity
 * — describing that route's connection, accounts, and model catalog. Every fact
 * arrives from the node half's `subscription-oauth` RPC endpoints, so this
 * component holds no credential state of its own. The status poll runs only
 * while a login is pending, so an idle page performs no requests.
 *
 * Colors resolve through `--dsw-alias-*` design tokens, so the page follows the
 * active theme; every user-visible string goes through the locale-bound `t`.
 *
 * @module dsh-plugin-subscription-oauth/client/SubscriptionsSection
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { en } from './locales.js';
import { subscriptionsCss } from './styles.js';
import { SubscriptionsRpcError } from './subscriptions-rpc.js';
/** Poll cadence while a login attempt is pending. */
const POLL_INTERVAL_MS = 2000;
/**
 * How often an open page re-reads every signed-in route's allowance.
 *
 * Slow on purpose: it is fast enough that a figure shown on screen is current, and
 * slow enough that leaving the page open costs one small request a minute per
 * signed-in provider.
 */
const USAGE_POLL_INTERVAL_MS = 60_000;
/**
 * Disclosures start folded, every time the page is opened.
 *
 * Nothing is remembered between visits on purpose: the section is a list of
 * providers first, and a card left open last time should not decide what this
 * visit looks like. A fold is a gesture within one visit, not a stored setting.
 */
/** Horizontal room the page takes inside the settings panel. */
const SECTION_WIDTH = 560;
/** Minimal translator used when the locale seat is absent (standalone render). */
function fallbackTranslate(key, params) {
    const template = en[key];
    if (params === undefined)
        return template;
    return template.replace(/\{(\w+)\}/g, (match, name) => name in params ? String(params[name]) : match);
}
/** Human-readable message for any thrown value. */
function messageOf(error) {
    if (error instanceof SubscriptionsRpcError)
        return error.message;
    return error instanceof Error ? error.message : String(error);
}
/** Local time for one epoch-millisecond instant. */
function formatTime(at) {
    try {
        return new Date(at).toLocaleString();
    }
    catch {
        return String(at);
    }
}
/** How far ahead a reset is still stated as hours rather than a date. */
const RESET_RELATIVE_MS = 24 * 60 * 60 * 1000;
/**
 * One allowance reset, in the form a reader acts on.
 *
 * How far off it is decides how it is said: minutes inside the hour, hours inside
 * the day, and the date beyond that — "137 小时后" would be arithmetic rather than
 * information, and a window that comes back in forty minutes is better described in
 * minutes than as "1 小时后". Every route's windows go through here, so all of them
 * read the same way.
 *
 * @param at - epoch milliseconds the window resets.
 * @param t - translator, so the wording follows the page language.
 * @returns the reset text.
 */
function formatReset(at, t) {
    const remaining = at - Date.now();
    if (remaining <= 0)
        return t('usageResetDue');
    if (remaining >= RESET_RELATIVE_MS)
        return t('usageReset', { time: formatTime(at) });
    // Rounded, and never below one: "0 分钟后重置" would read as a bug rather than as
    // "any moment now".
    if (remaining < 60 * 60 * 1000) {
        return t('usageResetsInMinutes', { minutes: Math.max(1, Math.round(remaining / 60_000)) });
    }
    return t('usageResetsInHours', { hours: Math.max(1, Math.round(remaining / 3_600_000)) });
}
/** Compact token count for a context window. */
function formatTokens(tokens) {
    if (tokens >= 1_000_000)
        return `${String(Math.round(tokens / 100_000) / 10)}M`;
    if (tokens >= 1_000)
        return `${String(Math.round(tokens / 1_000))}K`;
    return String(tokens);
}
/**
 * Routes worth asking for allowances.
 *
 * A route with no account of its own — `auto` — reports itself as signed in so its
 * card reads as usable, but it holds no allowance to read: asking it every minute
 * spends a request to be told nothing. The routes it stands in for are each read
 * on their own.
 *
 * @param providers - the routes in the current status.
 * @returns the route keys to read usage for.
 */
function usageTargets(providers) {
    return providers
        .filter((provider) => provider.loggedIn && provider.connectable !== false)
        .map((provider) => provider.provider);
}
const styles = {
    section: {
        display: 'flex', flexDirection: 'column', gap: 12, maxWidth: SECTION_WIDTH,
        color: 'var(--dsw-alias-label-primary)',
    },
    sectionHeader: { display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 2 },
    heading: { margin: 0, fontSize: 18, lineHeight: '26px', fontWeight: 600, color: 'var(--dsw-alias-label-primary)' },
    intro: { margin: 0, color: 'var(--dsw-alias-label-tertiary)', fontSize: 13, lineHeight: '20px', maxWidth: 560 },
    card: {
        border: '0.5px solid var(--dsw-alias-settings-card-stroke, var(--dsw-alias-border-l4))',
        borderRadius: 'var(--dsw-radius-xl, 16px)',
        background: 'var(--dsw-alias-settings-card-fill, var(--dsw-alias-bg-layer-2))',
        padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 8,
        transition: 'border-color 120ms ease, background 120ms ease',
    },
    cardOpen: {
        borderColor: 'var(--dsw-alias-border-l2, var(--dsw-alias-border-l3))',
        background: 'var(--dsw-alias-settings-card-fill-hover, var(--dsw-alias-settings-card-fill, var(--dsw-alias-bg-layer-2)))',
    },
    cardHeader: { display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', userSelect: 'none' },
    /** Everything a folded card hides. */
    cardBody: { display: 'flex', flexDirection: 'column', gap: 10, marginTop: 2 },
    /** The one-line state a folded card shows in place of its body. */
    cardSummary: { fontSize: 12, lineHeight: '18px', color: 'var(--dsw-alias-label-tertiary)' },
    /** The status indicator: filled when live, a hollow ring when not. */
    dot: {
        width: 8, height: 8, borderRadius: '50%', flexShrink: 0,
        border: '1.5px solid var(--dsw-alias-label-dimmed)',
        background: 'transparent',
    },
    dotLive: {
        borderColor: 'var(--dsw-alias-state-success-primary, #067647)',
        background: 'var(--dsw-alias-state-success-primary, #067647)',
    },
    dotBusy: {
        borderColor: 'var(--dsw-alias-state-warn-primary, #f5a524)',
        background: 'var(--dsw-alias-state-warn-primary, #f5a524)',
    },
    name: { fontWeight: 600, fontSize: 14, lineHeight: '22px', color: 'var(--dsw-alias-label-primary)' },
    statusLine: { margin: 0, fontSize: 12, lineHeight: '18px', color: 'var(--dsw-alias-label-tertiary)' },
    errorLine: { margin: 0, fontSize: 12, lineHeight: '18px', color: 'var(--dsw-alias-state-error-primary)' },
    successLine: { margin: 0, fontSize: 12, lineHeight: '18px', color: 'var(--dsw-alias-state-success-primary)' },
    actions: { display: 'flex', gap: 8, marginTop: 4, alignItems: 'center', flexWrap: 'wrap' },
    accountRow: {
        display: 'flex', flexDirection: 'column', gap: 6,
        border: '0.5px solid var(--dsw-alias-border-l2)', borderRadius: 'var(--dsw-radius-lg, 12px)',
        padding: '10px 12px', background: 'var(--dsw-alias-bg-layer-1)',
    },
    accountHeader: { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
    starButton: {
        border: 'none', padding: 0, width: 20, background: 'transparent',
        font: 'inherit', fontSize: 14, lineHeight: '20px', cursor: 'pointer',
        color: 'var(--dsw-alias-state-warn-label)',
    },
    accountName: { fontSize: 13, lineHeight: '20px', color: 'var(--dsw-alias-label-primary)', userSelect: 'all' },
    badge: {
        fontSize: 10, lineHeight: '14px', padding: '1px 7px', borderRadius: 999, fontWeight: 500,
        background: 'var(--dsw-alias-bg-layer-3, var(--dsw-alias-bg-layer-1))',
        border: '0.5px solid var(--dsw-alias-border-l2)',
        color: 'var(--dsw-alias-label-secondary)',
    },
    badgeDefault: {
        fontSize: 10, lineHeight: '14px', padding: '1px 7px', borderRadius: 999, fontWeight: 500,
        background: 'var(--dsw-alias-state-success-primary, #067647)',
        color: 'var(--dsw-alias-label-primary-foreground, #fff)',
    },
    badgeWarn: {
        fontSize: 10, lineHeight: '14px', padding: '1px 7px', borderRadius: 999, fontWeight: 500,
        background: 'var(--dsw-alias-state-warn-primary, #f5a524)',
        color: 'var(--dsw-alias-label-primary-foreground, #fff)',
    },
    pushRight: { marginLeft: 'auto', flexShrink: 0 },
    catalog: {
        display: 'flex', flexDirection: 'column', gap: 6, marginTop: 4,
        borderTop: '0.5px solid var(--dsw-alias-border-l2)', paddingTop: 8,
    },
    catalogHeader: { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
    catalogTitle: { fontSize: 12, lineHeight: '18px', fontWeight: 500, color: 'var(--dsw-alias-label-secondary)' },
    catalogMeta: { fontSize: 12, lineHeight: '18px', color: 'var(--dsw-alias-label-tertiary)' },
    /**
     * Button surface geometry lives in the scoped stylesheet; only the layout a
     * rule cannot express stays inline.
     */
    pill: {},
    pillSmall: {},
    /** The catalog heading: a summary-style disclosure, not a bordered control. */
    disclosure: {
        display: 'inline-flex', alignItems: 'center', gap: 4, margin: 0,
    },
    chevron: { fontSize: 10, lineHeight: '18px', color: 'var(--dsw-alias-label-tertiary)' },
    modelList: { display: 'flex', flexDirection: 'column', gap: 6, marginTop: 2 },
    /** One model line: a visibility toggle and the model's own metadata. */
    modelRowGrow: { display: 'flex', alignItems: 'flex-start', gap: 8, flex: '1 1 auto', minWidth: 0 },
    /** The pinned `auto` line: listed, switchable, and never part of its own chain. */
    autoRow: {
        display: 'flex', alignItems: 'flex-start', gap: 8, cursor: 'pointer',
        fontSize: 13, lineHeight: '20px', color: 'var(--dsw-alias-label-primary)',
    },
    /** The models `auto` will walk, indented under it. */
    chain: {
        display: 'flex', flexDirection: 'column', gap: 3,
        margin: '2px 0 4px 26px', paddingLeft: 8,
        borderLeft: '0.5px solid var(--dsw-alias-border-l2)',
    },
    chainTitle: { fontSize: 11, lineHeight: '16px', color: 'var(--dsw-alias-label-tertiary)' },
    /** The add-a-model-id row: the field, then confirm and cancel. */
    addRow: { display: 'flex', alignItems: 'center', gap: 6, marginTop: 2 },
    addInput: {},
    /** The remove control on a hand-added row: a bare glyph beside the row. */
    removeButton: { flexShrink: 0, alignSelf: 'center', padding: '0 6px' },
    /** One draggable chain row: the grip, then the remove-from-auto switch. */
    chainRowWrap: { display: 'flex', alignItems: 'center', gap: 4 },
    chainRow: {
        display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', flex: '1 1 auto',
        fontSize: 12, lineHeight: '18px', color: 'var(--dsw-alias-label-secondary)',
    },
    usage: {
        display: 'flex', flexDirection: 'column', gap: 8, marginTop: 2,
        borderTop: '0.5px solid var(--dsw-alias-border-l2)', paddingTop: 10,
    },
    usageHeader: { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
    usageRow: { display: 'flex', flexDirection: 'column', gap: 4 },
    usageLine: { display: 'flex', alignItems: 'baseline', gap: 8, justifyContent: 'space-between' },
    usageMeter: {
        height: 6, borderRadius: 999, background: 'var(--dsw-alias-bg-layer-3, var(--dsw-alias-bg-layer-2))',
        overflow: 'hidden', boxShadow: 'inset 0 0 0 1px #8a8f9833',
    },
    usageAccount: {
        display: 'flex', flexDirection: 'column', gap: 6,
        padding: '8px 10px', borderRadius: 'var(--dsw-radius-md, 10px)',
        background: 'var(--dsw-alias-bg-layer-1)', border: '0.5px solid var(--dsw-alias-border-l2)',
    },
    /** The metered windows under one account row: no border, just the meters. */
    usageInline: { display: 'flex', flexDirection: 'column', gap: 6, marginTop: 4 },
    /** The auto provider's on/off row: a label and its switch. */
    autoSwitchRow: { display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'space-between' },
    modelRow: {
        display: 'flex', alignItems: 'flex-start', gap: 8,
        cursor: 'pointer', fontSize: 13, lineHeight: '20px',
        color: 'var(--dsw-alias-label-primary)',
    },
    /** A hidden model stays listed and editable, but reads as inactive. */
    modelRowHidden: {
        display: 'flex', alignItems: 'flex-start', gap: 8,
        cursor: 'pointer', fontSize: 13, lineHeight: '20px',
        color: 'var(--dsw-alias-label-dimmed)',
    },
    modelBody: { display: 'flex', flexDirection: 'column', gap: 1, minWidth: 0 },
    modelName: { fontSize: 13, lineHeight: '20px' },
    modelMeta: { fontSize: 11, lineHeight: '16px', color: 'var(--dsw-alias-label-tertiary)', userSelect: 'all' },
    device: {
        marginTop: 4, display: 'flex', flexDirection: 'column', gap: 6,
        border: '0.5px solid var(--dsw-alias-border-l2)', borderRadius: 'var(--dsw-radius-lg, 16px)',
        padding: '10px 12px', background: 'var(--dsw-alias-bg-layer-1)',
    },
    deviceTitle: { fontWeight: 500, fontSize: 13, lineHeight: '20px', color: 'var(--dsw-alias-label-primary)' },
    deviceCode: {
        fontFamily: 'monospace', fontSize: 18, lineHeight: '24px', letterSpacing: 2,
        color: 'var(--dsw-alias-label-primary)', userSelect: 'all',
    },
    details: {
        display: 'flex', flexDirection: 'column', gap: 4,
        fontSize: 12, lineHeight: '18px', color: 'var(--dsw-alias-label-tertiary)',
    },
    detailRow: { display: 'flex', gap: 8, justifyContent: 'space-between', alignItems: 'baseline' },
    detailValue: { userSelect: 'all', overflowWrap: 'anywhere', textAlign: 'right' },
    /** Dialog layout only: its surface comes from `.dsh-subscription-manager`. */
    modal: {
        width: 440, maxWidth: '100%', maxHeight: '85vh', overflowY: 'auto',
        boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 10,
        padding: '16px 18px', margin: 'auto',
    },
    modalHeader: { display: 'flex', alignItems: 'center', gap: 8 },
    modalTitle: { fontWeight: 500, fontSize: 16, lineHeight: '24px', color: 'var(--dsw-alias-label-primary)' },
    modalActions: { display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 2 },
    divider: { borderTop: '0.5px solid var(--dsw-alias-border-l2)' },
};
/** Status dot style for one provider's connection state. */
function dotStyle(status) {
    if (status.busy)
        return styles.dotBusy;
    if (status.loggedIn)
        return styles.dotLive;
    return {};
}
/** Localized label for one provider-owned detail row. */
function detailLabel(t, row) {
    const key = `detail_${row.key}`;
    return key in en ? t(key) : row.key;
}
/** Localized label for one metered window the provider states. */
function windowLabel(t, window) {
    const key = `usage_${window.label}`;
    return key in en ? t(key) : window.label;
}
/** Credit-like window labels: money, not a share of an allowance. */
const CREDIT_WINDOW_LABELS = /credit|wallet|balance|token|point|积分|额度/i;
/**
 * How much of one window is spent, as a display string.
 *
 * A credit balance (WorkBuddy's Credits) reads as the raw used/limit count; a time
 * quota reads as the spent percent. Told apart by the label, since both state the
 * same fields.
 */
function windowAmount(window) {
    if (CREDIT_WINDOW_LABELS.test(window.label) && window.used !== undefined && window.limit !== undefined) {
        return `${String(window.used)}/${String(window.limit)}`;
    }
    if (window.usedPercent !== undefined)
        return `${String(Math.round(window.usedPercent))}%`;
    if (window.used !== undefined && window.limit !== undefined)
        return `${String(window.used)}/${String(window.limit)}`;
    return '—';
}
/**
 * A window's figure, marked as the share spent.
 *
 * The meter fills as the allowance is spent, so the number beside it is a share spent, not a
 * share left — saying which is what keeps "0%" from reading as "no allowance left". A window
 * that states no figure at all keeps its dash rather than claiming nothing is spent.
 */
function windowSpentLabel(t, window) {
    const amount = windowAmount(window);
    return amount === '—' ? amount : t('usageSpent', { value: amount });
}
/** One account's metered windows and check-in, rendered under its account row. */
function AccountUsage(props) {
    const { entry, t, busyAction, onCheckin, onTasks } = props;
    if (entry === undefined)
        return null;
    const checkin = entry.checkin;
    return (_jsxs("div", { style: styles.usageInline, children: [_jsxs("div", { style: styles.usageLine, children: [_jsx("span", { style: styles.modelName, children: checkin?.active === true
                            ? checkin.todayCheckedIn
                                ? t('checkinDone', { days: checkin.streakDays })
                                : t('checkinPending')
                            : '' }), _jsxs("span", { style: styles.pushRight, children: [onTasks !== undefined && (_jsx("button", { type: "button", className: "dsh-subscription-quiet", disabled: busyAction, onClick: onTasks, children: t('tasksClaim') })), checkin?.active === true && !checkin.todayCheckedIn && onCheckin !== undefined && (_jsx("button", { type: "button", className: "dsh-subscription-quiet", disabled: busyAction, onClick: onCheckin, children: t('checkinClaim') }))] })] }), entry.windows.map((window) => (_jsxs("div", { style: styles.usageRow, children: [_jsxs("div", { style: styles.usageLine, children: [_jsxs("span", { style: styles.modelName, children: [windowLabel(t, window), ' · ', windowSpentLabel(t, window)] }), _jsx("span", { style: styles.modelMeta, children: window.resetsAt === undefined ? '' : formatReset(window.resetsAt, t) })] }), window.usedPercent !== undefined && (_jsx("div", { className: "dsh-subscription-meter", children: _jsx("span", { style: { width: `${String(Math.min(100, Math.max(0, window.usedPercent)))}%` }, "data-exhausted": window.usedPercent >= 90 ? 'true' : 'false' }) }))] }, `${entry.account}-${window.label}`))), entry.windows.length === 0 && (_jsx("p", { style: styles.statusLine, children: t('usageNone') }))] }));
}
/** The plan-and-usage block for the whole route. */
function UsageBlock(props) {
    const { usage, error, t, busyAction, onRefresh } = props;
    return (_jsxs("div", { style: styles.usage, children: [_jsxs("div", { style: styles.usageHeader, children: [_jsx("span", { style: styles.catalogTitle, children: t('usageTitle') }), _jsx("button", { type: "button", className: "dsh-subscription-quiet", style: styles.pushRight, disabled: busyAction, onClick: onRefresh, children: busyAction ? t('refreshing') : t('usageRefresh') })] }), error !== undefined && _jsx("p", { className: "dsh-subscription-error", children: t('usageError', { message: error }) }), usage.length === 0 && error === undefined && _jsx("p", { style: styles.statusLine, children: t('usageNone') }), usage.map((entry) => (_jsxs("div", { style: styles.usageAccount, children: [_jsxs("div", { style: styles.usageLine, children: [_jsxs("span", { style: styles.modelMeta, children: [entry.plan === undefined ? entry.account : t('usagePlan', { plan: entry.plan }), entry.renewsAt === undefined ? '' : ` · ${t('usageRenews', { time: formatTime(entry.renewsAt) })}`] }), _jsx("span", { style: styles.modelMeta, children: t('usageFetched', { time: formatTime(entry.fetchedAt) }) })] }), entry.windows.map((window) => (_jsxs("div", { style: styles.usageRow, children: [_jsxs("div", { style: styles.usageLine, children: [_jsxs("span", { style: styles.modelName, children: [windowLabel(t, window), ' · ', windowAmount(window)] }), _jsx("span", { style: styles.modelMeta, children: window.resetsAt === undefined ? '' : formatReset(window.resetsAt, t) })] }), window.usedPercent !== undefined && (_jsx("div", { className: "dsh-subscription-meter", children: _jsx("span", { style: { width: `${String(Math.min(100, Math.max(0, window.usedPercent)))}%` }, "data-exhausted": window.usedPercent >= 90 ? 'true' : 'false' }) }))] }, `${entry.account}-${window.label}`)))] }, entry.account)))] }));
}
/** The pending-login block: a device code, or a link to the sign-in page. */
function PendingLogin(props) {
    const { prompt, t, copied, onCopy } = props;
    return (_jsxs("div", { style: styles.device, children: [_jsx("span", { style: styles.deviceTitle, children: prompt.mode === 'device-code' ? t('deviceTitle') : t('openLogin') }), _jsx("span", { style: styles.statusLine, children: prompt.mode === 'device-code' ? t('devicePrompt') : t('openLoginHint') }), prompt.userCode !== undefined && _jsx("span", { style: styles.deviceCode, children: prompt.userCode }), _jsx("span", { style: styles.statusLine, children: t('deviceExpires', { time: formatTime(prompt.expiresAt) }) }), _jsxs("div", { style: styles.actions, children: [_jsx("button", { type: "button", onClick: () => { window.open(prompt.url, '_blank', 'noopener'); }, children: prompt.mode === 'device-code' ? t('deviceOpen') : t('openLogin') }), prompt.userCode !== undefined && (_jsx("button", { type: "button", onClick: () => { onCopy(prompt.userCode); }, children: copied ? t('deviceCopied') : t('deviceCopy') }))] })] }));
}
/** One provider's card. */
function ProviderCard(props) {
    const { status, t, busyAction, message } = props;
    const [copied, setCopied] = useState(false);
    // Both disclosures open folded, on every visit: the choice is a gesture, not a
    // stored setting. See the note beside the interval constants.
    const [modelsOpen, setModelsOpen] = useState(false);
    /** Whether this provider's whole card is unfolded. */
    const [cardOpen, setCardOpen] = useState(false);
    const [manageOpen, setManageOpen] = useState(false);
    const mounted = useRef(true);
    const dialog = useRef(null);
    useEffect(() => {
        mounted.current = true;
        return () => { mounted.current = false; };
    }, []);
    // The dialog is a native modal, so the browser owns focus trapping and Esc.
    useEffect(() => {
        const element = dialog.current;
        if (element === null || typeof element.showModal !== 'function')
            return;
        if (manageOpen && !element.open)
            element.showModal();
        if (!manageOpen && element.open)
            element.close();
    }, [manageOpen]);
    const copyCode = useCallback(async (code) => {
        try {
            await navigator.clipboard.writeText(code);
            if (mounted.current) {
                setCopied(true);
                setTimeout(() => { if (mounted.current)
                    setCopied(false); }, 2000);
            }
        }
        catch {
            // A clipboard refusal is not worth an error banner: the code is on screen
            // and selectable, which is the fallback the user already has.
        }
    }, []);
    const toggleModelsOpen = useCallback(() => {
        setModelsOpen((open) => !open);
    }, []);
    /** Fold or unfold this provider's card. */
    const toggleCardOpen = useCallback(() => {
        setCardOpen((open) => !open);
    }, []);
    const accounts = status.accounts;
    const models = status.models;
    /** Whether this route signs in at all; the auto provider does not. */
    const connectable = status.connectable !== false;
    const hiddenCount = models.filter((model) => !model.visible).length;
    /** Whether the add-a-model-id field is open, and what has been typed. */
    const [adding, setAdding] = useState(false);
    const [draft, setDraft] = useState('');
    /** The row being dragged, so it can be dimmed while it moves. */
    const [dragFrom, setDragFrom] = useState(undefined);
    /**
     * The dragged row, mirrored in a ref.
     *
     * The drop is handled by an ancestor whose handler was attached in an earlier
     * render, so it must not depend on state that was set during the drag.
     */
    const dragFromRef = useRef(undefined);
    /** The row the pointer is currently over, so a drop lands where the user aims. */
    const dropTargetRef = useRef(undefined);
    /** The same row in state, purely so it can be highlighted while dragging. */
    const [dropTarget, setDropTarget] = useState(undefined);
    /**
     * Commit one dropped arrangement.
     *
     * The node half takes the full chain in its new order and appends whatever the
     * page left out, so sending just these ids is enough — and a model hidden in
     * another window is never dropped from the stored rank by a stale page.
     */
    const commitOrder = useCallback((ids) => {
        props.onSetModelOrder(ids);
    }, [props]);
    /** Drag-and-drop plus its keyboard equivalent on the drag handle. */
    const drag = {
        start: (event, index) => {
            setDragFrom(index);
            dragFromRef.current = index;
            event.dataTransfer.effectAllowed = 'move';
            // Firefox refuses to start a drag without payload.
            event.dataTransfer.setData('text/plain', String(index));
        },
        over: (event, _index) => {
            // A drop is only possible on a target that cancels the default.
            event.preventDefault();
            event.dataTransfer.dropEffect = 'move';
            const container = event.currentTarget;
            const to = indexAt(container, event.clientY);
            dropTargetRef.current = to;
            if (to !== undefined && to !== dropTarget)
                setDropTarget(to);
        },
        drop: (event, index) => {
            event.preventDefault();
            // The target comes from the last `dragover`, not from this event's own
            // coordinates: `drop` is not guaranteed to carry the pointer position, and a
            // zero there silently collapsed every drop onto the first row.
            const to = dropTargetRef.current ?? indexAt(event.currentTarget, event.clientY) ?? index;
            move(dragFromRef.current, to);
            dragFromRef.current = undefined;
            dropTargetRef.current = undefined;
            setDropTarget(undefined);
            setDragFrom(undefined);
        },
        /** A drop anywhere in the list lands on the row under the pointer. */
        dropAt: (event) => {
            event.preventDefault();
            const container = event.currentTarget;
            const to = dropTargetRef.current ?? indexAt(container, event.clientY);
            if (to !== undefined)
                move(dragFromRef.current, to);
            dragFromRef.current = undefined;
            dropTargetRef.current = undefined;
            setDropTarget(undefined);
            setDragFrom(undefined);
        },
        end: () => {
            dragFromRef.current = undefined;
            dropTargetRef.current = undefined;
            setDropTarget(undefined);
            setDragFrom(undefined);
        },
        key: (event, index) => {
            if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')
                return;
            event.preventDefault();
            move(index, index + (event.key === 'ArrowUp' ? -1 : 1));
        },
    };
    /** Move one model row to another position and persist the result. */
    function move(from, to) {
        if (from === undefined || from === to)
            return;
        if (from < 0 || to < 0 || from >= models.length || to >= models.length)
            return;
        const ids = models.map((model) => model.id);
        const [moved] = ids.splice(from, 1);
        if (moved === undefined)
            return;
        ids.splice(to, 0, moved);
        commitOrder(ids);
    }
    /**
     * The row index a drop at this pointer position lands on.
     *
     * The drop is handled on the whole list rather than per row: rows are tall and
     * separated by gaps, so a drop between two of them hits no target at all and
     * silently does nothing. Comparing the pointer against each row's midpoint also
     * makes the gesture match what the user sees.
     */
    function indexAt(container, clientY) {
        const rows = Array.from(container.querySelectorAll('[data-model-index]'));
        if (rows.length === 0)
            return undefined;
        let closest = 0;
        let best = Number.POSITIVE_INFINITY;
        for (const row of rows) {
            const box = row.getBoundingClientRect();
            const distance = Math.abs(clientY - (box.top + box.height / 2));
            if (distance >= best)
                continue;
            best = distance;
            closest = Number(row.dataset.modelIndex);
        }
        return closest;
    }
    const busy = status.busy;
    const liveCatalog = (status.discovered?.length ?? 0) > 0;
    /**
     * What a folded card says about itself.
     *
     * The connection is the reason to open a card, so the folded row carries it;
     * `auto` has no account and counts the models it will actually try instead.
     */
    const summaryLine = !connectable
        ? t('statusAuto', { count: models.filter((model) => model.visible).length })
        : busy
            ? t('statusBusy')
            : status.loggedIn
                ? t('statusConnected', { count: accounts.length })
                : t('statusSignedOut');
    return (_jsxs("div", { style: cardOpen ? { ...styles.card, ...styles.cardOpen } : styles.card, children: [_jsxs("div", { style: styles.cardHeader, role: "button", tabIndex: 0, "aria-expanded": cardOpen, onClick: toggleCardOpen, onKeyDown: (event) => { if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    toggleCardOpen();
                } }, children: [_jsx("span", { style: { ...styles.dot, ...dotStyle(status) }, "aria-hidden": "true" }), _jsx("span", { style: styles.name, children: status.displayName }), status.loggedIn && _jsx("span", { style: styles.badgeDefault, children: t('badgeLive') }), _jsx("span", { style: styles.cardSummary, children: summaryLine }), _jsx("button", { type: "button", className: "dsh-subscription-quiet", style: styles.pushRight, "aria-expanded": cardOpen, "aria-controls": `dsh-subscription-card-${status.provider}`, onClick: (event) => { event.stopPropagation(); toggleCardOpen(); }, children: cardOpen ? t('collapse') : t('edit') })] }), _jsxs("div", { id: `dsh-subscription-card-${status.provider}`, style: cardOpen ? styles.cardBody : { ...styles.cardBody, display: 'none' }, children: [connectable && !status.loggedIn && !busy && _jsx("p", { style: styles.statusLine, children: t('statusSignIn') }), status.lastError !== undefined && (_jsx("p", { className: "dsh-subscription-error", children: t('lastError', { message: status.lastError }) })), message?.tone === 'error' && _jsx("p", { className: "dsh-subscription-error", children: message.text }), message?.tone === 'success' && _jsx("p", { className: "dsh-subscription-success", children: message.text }), status.provider === 'auto' && props.onSetAutoOn !== undefined && (_jsxs("label", { style: styles.autoSwitchRow, children: [_jsx("span", { style: styles.modelName, children: t('autoSwitchLabel') }), _jsx("input", { type: "checkbox", className: "dsh-subscription-switch", checked: props.autoOn ?? true, disabled: busyAction, onChange: (event) => { props.onSetAutoOn?.(event.target.checked); } })] })), accounts.map((account) => {
                        const usageEntry = (status.usage ?? []).find((entry) => entry.account === account.account);
                        return (_jsxs("div", { style: styles.accountRow, children: [_jsxs("div", { style: styles.accountHeader, children: [_jsx("button", { type: "button", style: styles.starButton, title: account.isDefault ? t('defaultBadge') : t('makeDefault'), "aria-label": account.isDefault ? t('defaultBadge') : t('makeDefault'), disabled: busyAction || account.isDefault, onClick: () => { props.onSetDefault(account.account); }, children: account.isDefault ? '★' : '☆' }), _jsx("span", { style: styles.accountName, children: account.label }), account.isDefault && _jsx("span", { style: styles.badgeDefault, children: t('defaultBadge') }), account.needsLogin === true && (_jsx("span", { style: styles.badgeWarn, title: t('accountNeedsLoginHint'), children: t('accountNeedsLogin') })), usageEntry?.plan !== undefined && (_jsx("span", { style: styles.badge, children: t('usagePlan', { plan: usageEntry.plan }) })), _jsx("span", { style: styles.statusLine, children: account.expired
                                                ? t('accountExpired')
                                                : t('accountExpires', { time: formatTime(account.expiresAt) }) }), _jsx("button", { type: "button", className: "dsh-subscription-quiet", style: styles.pushRight, disabled: busyAction, onClick: () => { props.onLogout(account.account); }, children: t('logout') })] }), _jsx(AccountUsage, { entry: usageEntry, t: t, busyAction: busyAction, ...props.onCheckin === undefined ? {} : { onCheckin: () => { props.onCheckin?.(account.account); } }, ...props.onTasks === undefined ? {} : { onTasks: () => { props.onTasks?.(account.account); } } })] }, account.account));
                    }), status.loggedIn && (_jsxs("div", { style: styles.catalog, children: [_jsxs("div", { style: styles.catalogHeader, children: [_jsxs("button", { type: "button", className: "dsh-subscription-disclosure", style: styles.disclosure, "aria-expanded": modelsOpen, "aria-controls": `dsh-subscription-models-${status.provider}`, onClick: toggleModelsOpen, children: [_jsx("span", { style: styles.chevron, "aria-hidden": "true", children: modelsOpen ? '▾' : '▸' }), _jsx("span", { style: styles.catalogTitle, children: t('modelsTitle') })] }), _jsxs("span", { style: styles.catalogMeta, children: [t('modelsCount', { count: models.length }), ' · ', liveCatalog ? t('modelsLive') : t('modelsBuiltIn'), hiddenCount === 0 ? '' : ` ${t('modelsHiddenCount', { count: hiddenCount })}`] }), _jsx("button", { type: "button", className: "dsh-subscription-quiet", style: styles.pushRight, disabled: busyAction, onClick: props.onRefreshModels, children: busyAction ? t('refreshing') : t('refresh') }), _jsx("button", { type: "button", className: "dsh-subscription-quiet", disabled: busyAction || !connectable, title: t('modelsAddHint'), onClick: () => { setAdding((open) => !open); }, children: t('modelsAdd') })] }), adding && (_jsxs("form", { style: styles.addRow, onSubmit: (event) => {
                                    event.preventDefault();
                                    const id = draft.trim();
                                    if (id.length === 0)
                                        return;
                                    props.onAddModel(id);
                                    setDraft('');
                                    setAdding(false);
                                }, children: [_jsx("input", { type: "text", className: "dsh-subscription-input", style: styles.addInput, value: draft, autoFocus: true, spellCheck: false, placeholder: t('modelsAddPlaceholder'), "aria-label": t('modelsAdd'), disabled: busyAction, onChange: (event) => { setDraft(event.target.value); } }), _jsx("button", { type: "submit", disabled: busyAction || draft.trim().length === 0, children: t('modelsAddConfirm') }), _jsx("button", { type: "button", className: "dsh-subscription-quiet", onClick: () => { setAdding(false); setDraft(''); }, children: t('cancel') })] })), modelsOpen && (_jsxs("div", { id: `dsh-subscription-models-${status.provider}`, style: styles.modelList, 
                                // The whole list is the drop target, so a release between two rows still
                                // lands somewhere instead of being cancelled unnoticed.
                                onDragOver: (event) => { drag.over(event, 0); }, onDrop: drag.dropAt, children: [status.provider === 'auto' && _jsx("span", { style: styles.chainTitle, children: t('modelsAutoChain') }), models.map((model, index) => (_jsxs("div", { "data-model-index": index, draggable: !busyAction, style: {
                                            ...styles.modelRowWrap,
                                            ...dragFrom === index ? { opacity: 0.5 } : {},
                                            // The row a drop would land on, so the gesture is visible while it
                                            // happens rather than only in its result.
                                            ...dropTarget === index && dragFrom !== undefined && dragFrom !== index
                                                ? { boxShadow: 'inset 0 2px 0 0 var(--dsw-alias-brand-primary)' }
                                                : {},
                                        }, onDragStart: (event) => { drag.start(event, index); }, onDragEnd: drag.end, children: [_jsx("button", { type: "button", className: "dsh-subscription-handle", disabled: busyAction, title: t('modelsDrag'), "aria-label": `${t('modelsDrag')} · ${model.name}`, onKeyDown: (event) => { drag.key(event, index); }, children: "\u283F" }), _jsxs("label", { style: model.visible
                                                    ? { ...styles.modelRowGrow, cursor: 'pointer' }
                                                    : { ...styles.modelRowGrow, cursor: 'pointer', color: 'var(--dsw-alias-label-dimmed)' }, title: model.visible ? t('modelsHidden') : t('modelsShow'), children: [_jsx("input", { type: "checkbox", className: "dsh-subscription-switch", role: "switch", checked: model.visible, disabled: busyAction, "aria-label": `${t('modelsShow')} · ${model.name}`, onChange: (event) => { props.onSetModelVisible(model.id, event.target.checked); } }), _jsxs("span", { style: styles.modelBody, children: [_jsxs("span", { style: styles.modelName, children: [model.name, model.source === undefined ? null : _jsx("span", { style: styles.badge, children: model.source })] }), _jsxs("span", { style: styles.modelMeta, children: [model.id, ' · ', t('modelsContext', { tokens: formatTokens(model.contextWindow) }), model.defaultEffort === undefined ? '' : ` · ${model.defaultEffort}`, model.inputModalities === undefined || model.inputModalities.length === 0
                                                                        ? ''
                                                                        : ` · ${model.inputModalities.join('/')}`] })] })] }), model.userAdded === true && (_jsx("button", { type: "button", className: "dsh-subscription-quiet", style: styles.removeButton, disabled: busyAction, title: t('modelsRemove'), "aria-label": `${t('modelsRemove')} · ${model.name}`, onClick: () => { props.onRemoveModel(model.id); }, children: "\u00D7" }))] }, model.id))), models.length === 0 && (_jsx("p", { style: styles.statusLine, children: status.provider === 'auto' ? t('modelsAutoChainEmpty') : t('modelsEmpty') }))] }))] })), connectable && (_jsxs("div", { style: styles.actions, children: [!busy && (_jsx("button", { type: "button", disabled: busyAction, onClick: props.onLogin, children: accounts.length === 0 ? t('connect') : t('addAccount') })), !busy && (_jsx("button", { type: "button", disabled: busyAction, onClick: () => { setManageOpen(true); }, children: t('manage') })), busy && (_jsx("button", { type: "button", disabled: busyAction, onClick: props.onCancel, children: t('cancel') }))] })), connectable && accounts.length > 0 && _jsx("p", { style: styles.statusLine, children: t('hint') }), busy && status.pending !== undefined && (_jsx(PendingLogin, { prompt: status.pending, t: t, copied: copied, onCopy: (code) => { void copyCode(code); } }))] }), _jsxs("dialog", { ref: dialog, className: "dsh-subscription-manager", "aria-labelledby": `dsh-subscription-manage-${status.provider}`, 
                // A closed `<dialog>` is hidden by the user-agent stylesheet, and an
                // inline `display` would override that rule — so the open state has to
                // be restated here rather than left to `showModal()` alone.
                style: manageOpen ? styles.modal : { ...styles.modal, display: 'none' }, onClose: () => { setManageOpen(false); }, onCancel: () => { setManageOpen(false); }, children: [_jsx("div", { style: styles.modalHeader, children: _jsx("span", { style: styles.modalTitle, id: `dsh-subscription-manage-${status.provider}`, children: t('manageTitle', { provider: status.displayName }) }) }), _jsx("span", { style: styles.statusLine, children: t('manageAccounts') }), accounts.map((account) => (_jsxs("div", { style: styles.accountRow, children: [_jsxs("div", { style: styles.accountHeader, children: [_jsx("span", { style: styles.accountName, children: account.label }), account.isDefault && _jsx("span", { style: styles.badge, children: t('defaultBadge') })] }), _jsxs("div", { style: styles.actions, children: [_jsx("button", { type: "button", disabled: busyAction, onClick: () => { props.onRefreshToken(account.account); }, children: t('refreshToken') }), !account.isDefault && (_jsx("button", { type: "button", disabled: busyAction, onClick: () => { props.onSetDefault(account.account); }, children: t('makeDefault') })), _jsx("button", { type: "button", disabled: busyAction, onClick: () => { props.onLogout(account.account); }, children: t('logout') })] })] }, account.account))), accounts.length === 0 && _jsx("p", { style: styles.statusLine, children: t('statusSignedOut') }), _jsx("div", { style: styles.divider }), _jsx("div", { style: styles.actions, children: _jsx("button", { type: "button", disabled: busyAction, onClick: props.onReload, children: t('reload') }) }), _jsx("p", { style: styles.statusLine, children: t('reloadHint') }), _jsx("div", { style: styles.divider }), _jsxs("div", { style: styles.details, children: [status.details.map((row) => (_jsxs("div", { style: styles.detailRow, children: [_jsx("span", { children: detailLabel(t, row) }), _jsx("span", { style: styles.detailValue, children: row.value })] }, row.key))), status.discovered !== undefined && status.discovered.length > 0 && (_jsxs("div", { style: styles.detailRow, children: [_jsx("span", { children: t('modelsLive') }), _jsx("span", { style: styles.detailValue, children: status.discovered.join(', ') })] }))] }), _jsx("div", { className: "dsh-subscription-dialog-actions", style: styles.modalActions, children: _jsx("button", { type: "button", onClick: () => { setManageOpen(false); }, children: t('close') }) })] })] }));
}
/**
 * The Subscriptions settings section.
 * @param props - section props (inject face plus the shell's `close`).
 * @returns the section content.
 */
export function SubscriptionsSection(props) {
    const { call } = props;
    const t = props.t ?? fallbackTranslate;
    const [status, setStatus] = useState(undefined);
    const [error, setError] = useState(undefined);
    const [messages, setMessages] = useState({});
    const [busyAction, setBusyAction] = useState(false);
    const [autoOn, setAutoOn] = useState(true);
    const mounted = useRef(true);
    const timer = useRef(undefined);
    /** Read whether the `auto` provider is offered, so its card can render the switch. */
    useEffect(() => {
        if (call === undefined)
            return;
        let live = true;
        void call('auto-state', undefined)
            .then((result) => { if (live)
            setAutoOn(result.enabled); })
            .catch(() => { });
        return () => { live = false; };
    }, [call]);
    /** Fetch the current state; a failure is shown rather than swallowed. */
    const refresh = useCallback(async () => {
        if (call === undefined)
            return;
        try {
            const next = await call('status', undefined);
            if (!mounted.current)
                return;
            setStatus(next);
            setError(undefined);
        }
        catch (caught) {
            if (mounted.current)
                setError(t('actionFailed', { message: messageOf(caught) }));
        }
    }, [call, t]);
    /** Flip the `auto` provider on or off. */
    const setAuto = useCallback(async (enabled) => {
        if (call === undefined)
            return;
        setBusyAction(true);
        try {
            const result = await call('setAutoOn', { enabled });
            if (!mounted.current)
                return;
            setAutoOn(result.enabled);
            await refresh();
        }
        catch (caught) {
            if (mounted.current)
                setError(t('actionFailed', { message: messageOf(caught) }));
        }
        finally {
            if (mounted.current)
                setBusyAction(false);
        }
    }, [call, refresh, t]);
    /** Claim one account's daily check-in reward, then refresh the figures. */
    const checkin = useCallback(async (provider, account) => {
        if (call === undefined)
            return;
        setBusyAction(true);
        try {
            const result = await call('checkin', { provider, account });
            if (!mounted.current)
                return;
            setMessages((previous) => ({
                ...previous,
                [provider]: {
                    tone: 'success',
                    text: result.already
                        ? t('checkinAlready', { days: result.streakDays })
                        : t('checkinClaimed', { credit: result.credit, days: result.streakDays }),
                },
            }));
            await refresh();
        }
        catch (caught) {
            if (mounted.current)
                setError(t('actionFailed', { message: messageOf(caught) }));
        }
        finally {
            if (mounted.current)
                setBusyAction(false);
        }
    }, [call, refresh, t]);
    /** Auto-claim one account's growth-task rewards, then refresh the figures. */
    const runTasks = useCallback(async (provider, account) => {
        if (call === undefined)
            return;
        setBusyAction(true);
        try {
            const result = await call('tasks', { provider, account, claim: true });
            if (!mounted.current)
                return;
            setMessages((previous) => ({
                ...previous,
                [provider]: {
                    tone: 'success',
                    text: result.claimed > 0
                        ? t('tasksClaimed', { count: result.claimed, credit: result.creditEarned })
                        : result.claimable > 0
                            ? t('tasksClaimable', { count: result.claimable })
                            : t('tasksNone'),
                },
            }));
            await refresh();
        }
        catch (caught) {
            if (mounted.current)
                setError(t('actionFailed', { message: messageOf(caught) }));
        }
        finally {
            if (mounted.current)
                setBusyAction(false);
        }
    }, [call, refresh, t]);
    const stopPolling = useCallback(() => {
        if (timer.current !== undefined) {
            clearInterval(timer.current);
            timer.current = undefined;
        }
    }, []);
    const startPolling = useCallback(() => {
        if (timer.current !== undefined)
            return;
        // The node half clears `busy` once the attempt settles; the effect below
        // observes that and stops this interval.
        timer.current = setInterval(() => { void refresh(); }, POLL_INTERVAL_MS);
    }, [refresh]);
    useEffect(() => {
        mounted.current = true;
        void refresh();
        return () => {
            mounted.current = false;
            stopPolling();
        };
    }, [refresh, stopPolling]);
    /** Every route worth reading, as of the latest render, for the usage poll. */
    const signedInRef = useRef([]);
    useEffect(() => {
        signedInRef.current = usageTargets(status?.providers ?? []);
    });
    /**
     * Read each named route's plan and allowance, applying every answer.
     *
     * A read is not forced: the node half reuses a figure only for a few seconds and
     * never reuses a substitute, so a steady interval refreshes the figures while
     * collapsing bursts into one request per route.
     */
    const readUsage = useCallback(async (providers) => {
        if (call === undefined)
            return;
        for (const provider of providers) {
            try {
                const result = await call('usage', { provider });
                if (!mounted.current)
                    return;
                setStatus(result.status);
            }
            catch {
                // An unreadable quota is already surfaced inside the card by the status it
                // carries; the page itself stays usable.
            }
        }
    }, [call]);
    // The figures are read as soon as the first status lands...
    const usageRequested = useRef(false);
    useEffect(() => {
        if (call === undefined || status === undefined || usageRequested.current)
            return;
        const signedIn = usageTargets(status.providers);
        if (signedIn.length === 0)
            return;
        usageRequested.current = true;
        void readUsage(signedIn);
    }, [call, status, readUsage]);
    // ...and then kept current while the page is open. Allowances move as the account
    // is used, so a figure that is only read on mount reports the state of a page that
    // may have been open for hours.
    useEffect(() => {
        if (call === undefined)
            return;
        const tick = () => {
            // Nothing is refreshed for a page nobody is looking at.
            if (globalThis.document?.visibilityState === 'hidden')
                return;
            void readUsage(signedInRef.current);
        };
        const interval = setInterval(tick, USAGE_POLL_INTERVAL_MS);
        return () => { clearInterval(interval); };
    }, [call, readUsage]);
    // Poll only while some provider is waiting for browser approval.
    const anyBusy = status?.providers.some((provider) => provider.busy) === true;
    useEffect(() => {
        if (anyBusy)
            startPolling();
        else
            stopPolling();
    }, [anyBusy, startPolling, stopPolling]);
    /**
     * Signed-in providers read first: a route with a live account is the one a
     * reader most likely wants, and `auto` (which owns no account) leads the rest.
     * The sort is stable, so equal ranks keep the host's configured order.
     */
    const orderedProviders = useMemo(() => {
        const providers = status?.providers ?? [];
        const rank = (provider) => {
            if (provider.provider === 'auto')
                return 0;
            if (provider.loggedIn)
                return 1;
            return 2;
        };
        return [...providers].sort((left, right) => rank(left) - rank(right));
    }, [status?.providers]);
    /** Run one action for one provider, reporting its own failure. */
    const act = useCallback(async (provider, run) => {
        setBusyAction(true);
        setMessages((current) => {
            const next = { ...current };
            delete next[provider];
            return next;
        });
        try {
            await run();
        }
        catch (caught) {
            if (mounted.current) {
                setMessages((current) => ({ ...current, [provider]: { tone: 'error', text: t('actionFailed', { message: messageOf(caught) }) } }));
            }
        }
        finally {
            if (mounted.current)
                setBusyAction(false);
        }
    }, [t]);
    const login = useCallback((provider) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const started = await call('login', { provider });
            if (!mounted.current)
                return;
            setStatus(started.status);
            // A loopback flow wants the browser opened; a blocked popup still leaves
            // the link in the card, which is the fallback the user already has.
            if (started.prompt.mode === 'open-url') {
                window.open(started.prompt.url, '_blank', 'noopener');
            }
        });
    }, [act, call]);
    const cancel = useCallback((provider) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('cancel', { provider });
            if (mounted.current)
                setStatus(next);
        });
    }, [act, call]);
    const logout = useCallback((provider, account) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('logout', { provider, account });
            if (mounted.current)
                setStatus(next);
        });
    }, [act, call]);
    const setDefault = useCallback((provider, account) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('setDefault', { provider, account });
            if (mounted.current)
                setStatus(next);
        });
    }, [act, call]);
    const refreshToken = useCallback((provider, account) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const result = await call('refresh', { provider, account });
            if (!mounted.current)
                return;
            setStatus(result.status);
            setMessages((current) => ({
                ...current,
                [provider]: { tone: 'success', text: t('tokenRefreshed', { time: formatTime(result.expiresAt) }) },
            }));
        });
    }, [act, call, t]);
    const refreshModels = useCallback((provider) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('discover', { provider, force: true });
            if (!mounted.current)
                return;
            setStatus(next);
            setMessages((current) => ({ ...current, [provider]: { tone: 'success', text: t('modelsRefreshed') } }));
        });
    }, [act, call, t]);
    const setModelVisible = useCallback((provider, id, visible) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('setModelVisible', { provider, id, visible });
            if (mounted.current)
                setStatus(next);
        });
    }, [act, call]);
    const refreshUsage = useCallback((provider) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const result = await call('usage', { provider, force: true });
            if (mounted.current)
                setStatus(result.status);
        });
    }, [act, call]);
    const setModelOrder = useCallback((provider, ids) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('setModelOrder', { provider, ids: [...ids] });
            if (mounted.current)
                setStatus(next);
        });
    }, [act, call]);
    const addModel = useCallback((provider, id) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('addModel', { provider, id });
            if (mounted.current)
                setStatus(next);
        });
    }, [act, call]);
    const removeModel = useCallback((provider, id) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('removeModel', { provider, id });
            if (mounted.current)
                setStatus(next);
        });
    }, [act, call]);
    const reload = useCallback((provider) => {
        if (call === undefined)
            return;
        void act(provider, async () => {
            const next = await call('reload', undefined);
            if (mounted.current)
                setStatus(next);
        });
    }, [act, call]);
    if (call === undefined)
        return _jsx("p", { style: styles.intro, children: t('unavailable') });
    return (_jsxs("div", { className: "dsh-subscriptions-settings", style: styles.section, children: [_jsx("style", { children: subscriptionsCss }), _jsxs("div", { style: styles.sectionHeader, children: [_jsx("h2", { style: styles.heading, children: t('nav') }), _jsx("p", { style: styles.intro, children: t('intro') })] }), error !== undefined && _jsx("p", { className: "dsh-subscription-error", children: error }), status === undefined && _jsx("p", { style: styles.statusLine, children: t('loading') }), orderedProviders.map((provider) => (_jsx(ProviderCard, { status: provider, t: t, busyAction: busyAction, ...messages[provider.provider] === undefined ? {} : { message: messages[provider.provider] }, onLogin: () => { login(provider.provider); }, onCancel: () => { cancel(provider.provider); }, onLogout: (account) => { logout(provider.provider, account); }, onSetDefault: (account) => { setDefault(provider.provider, account); }, onRefreshToken: (account) => { refreshToken(provider.provider, account); }, onRefreshModels: () => { refreshModels(provider.provider); }, onSetModelVisible: (id, visible) => { setModelVisible(provider.provider, id, visible); }, onSetModelOrder: (ids) => { setModelOrder(provider.provider, ids); }, onAddModel: (id) => { addModel(provider.provider, id); }, onRemoveModel: (id) => { removeModel(provider.provider, id); }, onRefreshUsage: () => { refreshUsage(provider.provider); }, onReload: () => { reload(provider.provider); }, ...provider.provider !== 'auto' ? {} : { autoOn, onSetAutoOn: (enabled) => { void setAuto(enabled); } }, ...provider.provider !== 'workbuddy' ? {} : { onCheckin: (account) => { void checkin(provider.provider, account); } }, ...provider.provider !== 'workbuddy' ? {} : { onTasks: (account) => { void runTasks(provider.provider, account); } } }, provider.provider)))] }));
}
