import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
/**
 * The usage chip that rides the composer's bottom bar.
 *
 * The bar itself is a slot the shell owns. `conversation.composer.dock` is a
 * session-scoped `list`, and the shell's own stats pills (`tok/s`, cache hit) and its
 * context meter register into it; this chip joins as the last entry, so it reads to the
 * right of the context meter. It is deliberately compact — an abbreviated route name
 * and the percentage — because the bar is a status line, not a dashboard; the full
 * figure, its reset and the meter appear when the chip is clicked.
 *
 * Two silences are deliberate: a provider this plugin does not own shows nothing rather
 * than a fabricated zero, and switching to a foreign provider keeps the last own reading
 * on screen instead of blanking the bar — the shell's own pills already speak for the
 * foreign one.
 *
 * The pure helpers (the reduction from a route's usage to one line, the label and
 * balance formatters) live in `chip-helpers.ts` so the test suite can assert them
 * without evaluating this module's React and popover imports; everything here is
 * re-exported, so the public surface is unchanged.
 *
 * @module dsh-plugin-subscription-oauth/client/UsageChip
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useAnchoredPosition, useDismissOnOutsidePointer } from './popover-hooks.js';
import { usageChipCss } from './styles.js';
export { accountReadingsOf, balanceOf, formatBalance, formatReset, mergeAccountWindows, pickWindow, providerOf, readingOf, shortLabel, shortWindow, windowAmount, windowName, } from './chip-helpers.js';
import { accountReadingsOf, balanceOf, formatBalance, formatReset, mergeAccountWindows, providerOf, shortLabel, shortWindow, windowAmount, windowName, } from './chip-helpers.js';
/** How often a visible chip refreshes its figures. */
const REFRESH_INTERVAL_MS = 60_000;
/**
 * The panel's placement before its first measurement.
 *
 * `useAnchoredPosition` reports `null` until it has run, and the panel must be
 * mounted for that run to read its real size. It renders at the origin and
 * hidden for that one layout pass instead of not rendering at all, so the
 * measurement and the first paint agree.
 */
const HIDDEN_POSITION = { left: 0, top: 0, visibility: 'hidden' };
/**
 * The composer's usage chip.
 *
 * @param props - injected RPC caller, copy, and the session's provider reader.
 * @returns the chip, or null when there is nothing true to say.
 */
export function UsageChip({ call, t, isOwnProvider, accountBalance, ccSwitchBalance, useProjection }) {
    const [reading, setReading] = useState(undefined);
    const [accountReadings, setAccountReadings] = useState(undefined);
    const [balance, setBalance] = useState(undefined);
    const [ccBalance, setCcBalance] = useState(undefined);
    const [now, setNow] = useState(() => Date.now());
    const [open, setOpen] = useState(false);
    // The last route that answered, so a switch to a foreign provider leaves the last own
    // reading up rather than blanking the bar.
    const lastOwn = useRef(undefined);
    // A projection read, so the chip follows the picker: changing the model re-renders it
    // with the new provider instead of waiting for the next unrelated render.
    const selection = useProjection('modelSelection', (value) => value);
    const selected = providerOf(selection);
    // A non-subscription provider has no usage windows to meter, so the chip shows its
    // balance instead, read by cc-switch's query. The host's account (deepseek-account)
    // has its own balance source and takes that over cc-switch; a subscription this plugin
    // serves keeps its usage meter.
    const isDeepSeek = selected === 'deepseek-account';
    const isBalanceProvider = selected !== undefined && !isOwnProvider(selected) && !isDeepSeek;
    // Read a non-subscription provider's balance while it is in force, on the same cadence
    // as the other reads. It resolves to undefined when the provider has no balance source
    // or did not answer, which leaves the chip silent.
    useEffect(() => {
        if (!isBalanceProvider || selected === undefined) {
            setCcBalance(undefined);
            return;
        }
        let live = true;
        const target = selected;
        const refresh = () => {
            void ccSwitchBalance(target)
                .then((value) => { if (live)
                setCcBalance(value === undefined ? undefined : { currency: value.currency, total: value.total }); })
                .catch(() => { });
        };
        refresh();
        const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
        return () => {
            live = false;
            clearInterval(timer);
        };
    }, [isBalanceProvider, selected, ccSwitchBalance]);
    const load = useCallback(async (target) => {
        try {
            const result = await call('usage', { provider: target });
            return accountReadingsOf(result.usage);
        }
        catch {
            // A failed read is not worth a chip of its own: the settings page is where a user
            // goes to find out why a route cannot report.
            return undefined;
        }
    }, [call]);
    // Read the DeepSeek account's balance while that provider is in force, on the same
    // cadence as a usage read. It resolves to undefined when the account is signed out or
    // the read fails, which leaves the chip silent.
    useEffect(() => {
        if (!isDeepSeek) {
            setBalance(undefined);
            return;
        }
        let live = true;
        const refresh = () => {
            void accountBalance()
                .then((value) => { if (live)
                setBalance(balanceOf(value)); })
                .catch(() => { });
        };
        refresh();
        const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
        return () => {
            live = false;
            clearInterval(timer);
        };
    }, [isDeepSeek, accountBalance]);
    useEffect(() => {
        const target = selected !== undefined && isOwnProvider(selected)
            ? selected
            // Not one of ours (or none selected): keep showing the last own reading.
            : lastOwn.current;
        if (target === undefined) {
            setAccountReadings(undefined);
            return;
        }
        let live = true;
        const refresh = () => {
            setNow(Date.now());
            void load(target).then((next) => {
                if (!live || next === undefined)
                    return;
                lastOwn.current = target;
                setAccountReadings(next);
            });
        };
        refresh();
        const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
        return () => {
            live = false;
            clearInterval(timer);
        };
    }, [selected, isOwnProvider, load]);
    // A click anywhere else puts the detail away, which is what a status chip should do.
    // See ChipFrame for the popover mechanics (portal, clamp, dismiss, ARIA).
    const rootRef = useRef(null);
    const panelRef = useRef(null);
    // The hook contract reads `RefObject<HTMLElement | null>`; React 18's
    // `useRef<T>` types the ref object with a non-null `current` on assignment
    // sites, so the two are bridged with callback refs that only ever write.
    const setRootRef = useCallback((el) => { rootRef.current = el; }, []);
    const setPanelRef = useCallback((el) => { panelRef.current = el; }, []);
    const title = t('usageChipTitle', { provider: selected ?? '' });
    const panelLabel = t('usageChipFull', { provider: selected ?? '' });
    // A non-subscription provider reads as money, through cc-switch's query. It shows the
    // one currency's total the provider answered with, nothing when there is no usable
    // balance.
    if (isBalanceProvider) {
        if (ccBalance === undefined)
            return null;
        const short = shortLabel(selected ?? '', selected ?? '');
        return (_jsx(ChipFrame, { open: open, setOpen: setOpen, rootRef: rootRef, panelRef: panelRef, setRootRef: setRootRef, setPanelRef: setPanelRef, title: title, panelLabel: panelLabel, cells: (_jsx("span", { className: "dsh-subscription-usage-chip-cell", children: _jsx("span", { className: "dsh-subscription-usage-chip-value", children: `${ccBalance.currency === 'CNY' ? '¥' : ccBalance.currency === 'USD' ? '$' : `${ccBalance.currency} `}${ccBalance.total}` }) })), panel: (_jsxs(_Fragment, { children: [_jsx("span", { className: "dsh-subscription-usage-chip-full", children: panelLabel }), _jsx("span", { className: "dsh-subscription-usage-chip-meter", children: _jsxs("span", { className: "dsh-subscription-usage-chip-meterHead", children: [_jsx("span", { children: ccBalance.currency }), _jsx("span", { className: "dsh-subscription-usage-chip-detail", children: ccBalance.total })] }) })] })), children: _jsx("span", { className: "dsh-subscription-usage-chip-label", children: short }) }));
    }
    // The DeepSeek account reads as money, not as a percentage, so it takes its own line:
    // the route abbreviated, then each wallet. An unknown selection leaves the chip silent.
    if (isDeepSeek) {
        if (balance === undefined)
            return null;
        const short = shortLabel('deepseek-account', 'DeepSeek Account');
        return (_jsx(ChipFrame, { open: open, setOpen: setOpen, rootRef: rootRef, panelRef: panelRef, setRootRef: setRootRef, setPanelRef: setPanelRef, title: t('usageChipTitle', { provider: 'DeepSeek Account' }), panelLabel: t('usageChipFull', { provider: 'DeepSeek Account' }), cells: balance.map((wallet) => (_jsx("span", { className: "dsh-subscription-usage-chip-cell", children: _jsx("span", { className: "dsh-subscription-usage-chip-value", children: formatBalance(wallet) }) }, `${wallet.currency}${wallet.bonus ? ':bonus' : ''}`))), panel: (_jsxs(_Fragment, { children: [_jsx("span", { className: "dsh-subscription-usage-chip-full", children: t('usageChipFull', { provider: 'DeepSeek Account' }) }), balance.map((wallet) => (_jsx("span", { className: "dsh-subscription-usage-chip-meter", children: _jsxs("span", { className: "dsh-subscription-usage-chip-meterHead", children: [_jsx("span", { children: `${wallet.bonus ? 'Bonus · ' : ''}${wallet.currency}` }), _jsx("span", { className: "dsh-subscription-usage-chip-detail", children: formatBalance(wallet) })] }) }, `${wallet.currency}${wallet.bonus ? ':bonus' : ''}`)))] })), children: _jsx("span", { className: "dsh-subscription-usage-chip-label", children: short }) }));
    }
    if (accountReadings === undefined || accountReadings.length === 0)
        return null;
    const short = shortLabel(selected ?? '', selected ?? '');
    // The compact line integrates the whole pool; the panel shows each account's own
    // figures, so a merged glance hides nothing a per-account look cannot recover.
    const merged = mergeAccountWindows(accountReadings);
    return (_jsx(ChipFrame, { open: open, setOpen: setOpen, rootRef: rootRef, panelRef: panelRef, setRootRef: setRootRef, setPanelRef: setPanelRef, title: title, panelLabel: panelLabel, cells: merged.map((window) => (_jsxs("span", { className: "dsh-subscription-usage-chip-cell", children: [_jsx("span", { className: "dsh-subscription-usage-chip-window", children: shortWindow(window.label) }), _jsx("span", { className: "dsh-subscription-usage-chip-value", children: windowAmount(window) })] }, window.label))), panel: (_jsxs(_Fragment, { children: [_jsx("span", { className: "dsh-subscription-usage-chip-full", children: panelLabel }), accountReadings.map((account) => (_jsxs("span", { className: "dsh-subscription-usage-chip-account", children: [_jsx("span", { className: "dsh-subscription-usage-chip-meterHead", children: _jsx("span", { children: account.plan ?? account.account }) }), account.windows.map((window) => {
                            const windowReset = formatReset(window.resetsAt, now);
                            return (_jsxs("span", { className: "dsh-subscription-usage-chip-meter", children: [_jsxs("span", { className: "dsh-subscription-usage-chip-meterHead", children: [_jsx("span", { children: windowName(t, window.label) }), _jsx("span", { className: "dsh-subscription-usage-chip-detail", children: `${t('usageSpent', { value: windowAmount(window) })}${windowReset === undefined ? '' : ` · ${windowReset}`}` })] }), window.usedPercent !== undefined && (_jsx("span", { className: "dsh-subscription-usage-chip-track", "aria-hidden": "true", children: _jsx("span", { className: "dsh-subscription-usage-chip-fill", style: { width: `${String(Math.max(0, Math.min(100, window.usedPercent ?? 0)))}%` } }) }))] }, window.label));
                        })] }, account.account)))] })), children: _jsx("span", { className: "dsh-subscription-usage-chip-label", children: short }) }));
}
/**
 * The chip's shared frame: trigger button plus its detail popover.
 *
 * The popover is a dialog, not a status line: it opens on a user gesture and
 * carries per-account rows a reader can act on, so it renders through a portal
 * anchored to the trigger (viewport-clamped, the same `useAnchoredPosition`
 * the shell's own stat pills use) rather than an absolutely-positioned child
 * of a centered flex row. Outside-pointer dismissal runs in the capture phase
 * and Escape closes, with focus returning to the trigger on close.
 */
function ChipFrame({ open, setOpen, rootRef, panelRef, title, panelLabel, cells, panel, children, setRootRef, setPanelRef, }) {
    const position = useAnchoredPosition({
        open,
        anchorRef: rootRef,
        panelRef,
        side: 'top',
        align: 'end',
        gap: 6,
        margin: 12,
    });
    useDismissOnOutsidePointer(rootRef, open, (next) => setOpen(() => next), panelRef);
    // Escape closes the dialog and returns focus to the trigger.
    useEffect(() => {
        if (!open)
            return;
        const onKey = (event) => {
            if (event.key === 'Escape') {
                event.stopPropagation();
                setOpen(() => false);
                rootRef.current?.querySelector('button')?.focus();
            }
        };
        document.addEventListener('keydown', onKey, true);
        return () => { document.removeEventListener('keydown', onKey, true); };
    }, [open, setOpen, rootRef]);
    return (_jsxs("span", { className: "dsh-subscription-usage-chip", ref: setRootRef, children: [_jsx("style", { children: usageChipCss }), _jsxs("button", { type: "button", className: "dsh-subscription-usage-chip-trigger", "aria-haspopup": "dialog", "aria-expanded": open, title: title, onClick: () => { setOpen((was) => !was); }, children: [children, cells] }), open && createPortal(_jsx("div", { ref: setPanelRef, className: "dsh-subscription-usage-chip-panel", role: "dialog", "aria-label": panelLabel, 
                // The first measurement happens in the layout effect that opened the
                // panel, so it needs the panel mounted to read its real size. Until a
                // position exists the panel is mounted but invisible, which keeps the
                // clamp honest without flashing it at the origin.
                style: position ?? HIDDEN_POSITION, children: panel }), document.body)] }));
}
