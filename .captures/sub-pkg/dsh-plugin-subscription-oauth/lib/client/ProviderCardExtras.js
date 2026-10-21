import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * The per-provider card block on the Models settings page.
 *
 * The Models page declares `settings.models.provider-card` as a keyed slot,
 * dispatched with `entryKey = settingsNs` on every card whose directory row
 * names that namespace. This plugin's node half declares its routes with
 * `settingsNs: 'subscriptions'`, so one registration keyed `subscriptions`
 * receives this block for every one of them — Kimi, Grok, Antigravity, GLM,
 * Ark, MiMo, WorkBuddy — beside the section's own editor area.
 *
 * The block mirrors the full Subscriptions page's per-provider card in
 * miniature: connection state, its metered usage, and the same sign-in
 * actions (connect, cancel, manage) driven by the plugin's own RPC. It holds
 * no state of its own beyond the read and one pending action; every fact
 * arrives from the node half, as on the full page.
 *
 * @module dsh-plugin-subscription-oauth/client/ProviderCardExtras
 */
import { useCallback, useEffect, useState } from 'react';
import { providerCardCss } from './styles.js';
/** One metered window, as the settings cards and the composer chip read it. */
/** Compact reset text for one window (minutes, hours, then a date). */
function shortReset(at) {
    if (at === undefined)
        return undefined;
    const remaining = at - Date.now();
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
/** Whether one status belongs to the route this card renders. */
function statusFor(status, provider) {
    return status.providers.find((entry) => entry.provider === provider);
}
/**
 * One Models-page provider card's subscription block.
 *
 * Renders nothing while the route's status has not been read yet, so a cold
 * page does not flash a wrong card.
 *
 * @param props - the keyed slot's owner share plus the injected RPC and copy.
 * @returns the block, or null when there is no live state to show.
 */
export function ProviderCardExtras({ call, t, provider, }) {
    const [status, setStatus] = useState(undefined);
    const [usage, setUsage] = useState(undefined);
    const [busy, setBusy] = useState(false);
    const routeId = provider.provider;
    const refresh = useCallback(async () => {
        try {
            const next = await call('status', undefined);
            setStatus(next);
        }
        catch {
            // A failed read leaves the previous card on screen; the full page is
            // where the reason is reported.
        }
    }, [call]);
    const refreshUsage = useCallback(async () => {
        try {
            const next = await call('usage', { provider: routeId });
            setUsage(next.usage);
        }
        catch {
            // A route that cannot report usage keeps the card quiet on figures.
        }
    }, [call, routeId]);
    useEffect(() => {
        void refresh();
        const timer = setInterval(() => { void refresh(); }, 60_000);
        return () => { clearInterval(timer); };
    }, [refresh]);
    useEffect(() => {
        void refreshUsage();
        const timer = setInterval(() => { void refreshUsage(); }, 60_000);
        return () => { clearInterval(timer); };
    }, [refreshUsage]);
    const login = useCallback(() => {
        setBusy(true);
        void call('login', { provider: routeId })
            .then(async (started) => {
            setStatus(started.status);
            if (started.prompt.mode === 'open-url') {
                window.open(started.prompt.url, '_blank', 'noopener');
            }
            await refresh();
        })
            .catch(() => { })
            .finally(() => { setBusy(false); });
    }, [call, routeId, refresh]);
    const cancel = useCallback(() => {
        setBusy(true);
        void call('cancel', { provider: routeId })
            .then((next) => { setStatus(next); })
            .catch(() => { })
            .finally(() => { setBusy(false); });
    }, [call, routeId]);
    const route = status === undefined ? undefined : statusFor(status, routeId);
    if (route === undefined)
        return null;
    const loggedIn = route.loggedIn;
    const pending = route.busy && route.pending !== undefined;
    return (_jsxs("div", { className: "dsh-subscription-models-card", children: [_jsx("style", { children: providerCardCss }), _jsxs("div", { className: "dsh-subscription-models-card-head", children: [_jsx("span", { className: `dsh-subscription-models-card-dot${loggedIn ? ' dsh-subscription-models-card-dot-live' : ''}${pending ? ' dsh-subscription-models-card-dot-busy' : ''}`, "aria-hidden": "true" }), _jsx("span", { className: "dsh-subscription-models-card-name", children: route.displayName }), _jsx("span", { className: "dsh-subscription-models-card-state", children: loggedIn
                            ? t('statusConnected', { count: route.accounts.length })
                            : pending
                                ? t('statusBusy')
                                : t('statusSignedOut') })] }), loggedIn && usage !== undefined && usage.length > 0 && (_jsx("div", { className: "dsh-subscription-models-card-usage", children: usage.flatMap((account) => account.windows.map((window) => {
                    const spent = window.usedPercent === undefined ? undefined : Math.round(window.usedPercent);
                    const reset = shortReset(window.resetsAt);
                    return (_jsxs("div", { className: "dsh-subscription-models-card-meter", children: [_jsxs("span", { className: "dsh-subscription-models-card-meterHead", children: [_jsx("span", { children: account.plan ?? account.account }), _jsx("span", { className: "dsh-subscription-models-card-detail", children: `${t('usageSpent', { value: spent === undefined ? '—' : `${String(spent)}%` })}${reset === undefined ? '' : ` · ${reset}`}` })] }), window.usedPercent !== undefined && (_jsx("span", { className: "dsh-subscription-models-card-track", "aria-hidden": "true", children: _jsx("span", { className: "dsh-subscription-models-card-fill", style: { width: `${String(Math.max(0, Math.min(100, window.usedPercent)))}%` } }) }))] }, `${account.account}:${window.label}`));
                })) })), route.connectable !== false && (_jsxs("div", { className: "dsh-subscription-models-card-actions", children: [!route.busy && (_jsx("button", { type: "button", disabled: busy, onClick: login, children: route.accounts.length === 0 ? t('connect') : t('addAccount') })), route.busy && (_jsx("button", { type: "button", disabled: busy, onClick: cancel, children: t('cancel') }))] }))] }));
}
