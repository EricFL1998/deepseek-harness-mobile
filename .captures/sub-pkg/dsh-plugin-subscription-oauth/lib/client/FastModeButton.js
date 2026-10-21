import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
/**
 * The composer's quick-mode button.
 *
 * The route's own fast/lightspeed variant is not a row in the model picker: it shares its
 * base model's visibility and is reached from here. The composer renders
 * `conversation.input.right` immediately before its model seat, so this button sits at
 * the model selector's left — the control a reader looks for when they want the same
 * model, faster.
 *
 * Off, the selection is the base model and pressing the button selects the fast variant.
 * On, the selection *is* the fast variant, so the button reports active and pressing it
 * returns to the base — which is why `model-variants` answers from both sides of the pair
 * rather than only from the base id.
 *
 * The button renders nothing when the model has no fast variant, when the route is not
 * one this plugin serves, or when the host exposes no way to change the selection: an
 * absent control is honest, a disabled one is noise.
 *
 * @module dsh-plugin-subscription-oauth/client/FastModeButton
 */
import { useEffect, useState } from 'react';
import { providerOf } from './UsageChip.js';
import { fastModeCss } from './styles.js';
/**
 * How many times a *loading* route is re-asked, and how long between attempts.
 *
 * The route answers `pending` while it has not read its listing, which is the only case
 * worth asking again: a route that has read its listing and reports no variant means the
 * model truly has none, and the button stays absent without further reads.
 */
const VARIANT_READ_ATTEMPTS = 6;
const VARIANT_READ_INTERVAL_MS = 1_000;
/**
 * The quick-mode control, for the composer's row of standard controls.
 *
 * @param props - injected RPC caller, directory, and the session standard kit.
 * @returns the button, or null when this model has no fast variant.
 */
export function FastModeButton({ call, isOwnProvider, modelDirectory, t, useProjection }) {
    const [pair, setPair] = useState(undefined);
    // A switch that the host refuses must not look like a dead button: the reason is kept
    // and shown, because the only alternative is a control that silently does nothing.
    const [failure, setFailure] = useState(undefined);
    const [pending, setPending] = useState(false);
    const selection = useProjection('modelSelection', (value) => value);
    const provider = providerOf(selection);
    const model = selection?.next?.model ?? selection?.lastUsed?.model;
    useEffect(() => {
        if (provider === undefined || model === undefined || !isOwnProvider(provider)) {
            setPair(undefined);
            return;
        }
        let live = true;
        let timer;
        // A variant is derived from the route's model listing, which is read asynchronously and
        // may still be in flight when the composer mounts (a cold start, or the first run after
        // an update). The route says so with `pending`, and only then is it worth asking again:
        // a route that has read its listing and names no variant has answered for good.
        let attempts = 0;
        const ask = () => {
            attempts += 1;
            void call('model-variants', { provider, id: model })
                .then((result) => {
                if (!live)
                    return;
                if (result?.fast !== undefined) {
                    // The rung is the provider's own word (xAI `lightspeed`, Kimi `highspeed`), so
                    // the picker reads a name the model's own docs use, not a generic "Fast".
                    setPair({ rung: result.fastRung ?? 'fast' });
                    return;
                }
                setPair(undefined);
                // Still reading its listing: ask again, up to the cap, then accept the answer.
                if (result?.pending === true && attempts < VARIANT_READ_ATTEMPTS) {
                    timer = setTimeout(ask, VARIANT_READ_INTERVAL_MS);
                }
            })
                .catch(() => {
                // A read that failed outright is worth one more try, on the same budget.
                if (!live)
                    return;
                setPair(undefined);
                if (attempts < VARIANT_READ_ATTEMPTS)
                    timer = setTimeout(ask, VARIANT_READ_INTERVAL_MS);
            });
        };
        ask();
        return () => {
            live = false;
            if (timer !== undefined)
                clearTimeout(timer);
        };
    }, [provider, model, isOwnProvider, call]);
    // A switch that landed clears the reason, so the marker follows the truth on screen.
    useEffect(() => { setFailure(undefined); }, [model]);
    // The button renders on the fast pair alone. The host's model directory can be a moment
    // late (its `remote.session` dependency starts after the composer mounts), so gating
    // rendering on the directory being ready here hid the button for the whole session.
    // `modelDirectory.select` resolves the directory lazily on click instead.
    if (pair === undefined || provider === undefined || model === undefined)
        return null;
    // Fast mode is the row's fast reasoning rung, so the model id never changes and the
    // picker always shows the row the user can switch back to.
    const active = (selection?.next ?? selection?.lastUsed)?.reasoningEffort === pair.rung;
    const target = model;
    const base = active ? t('fastModeOn') : t('fastModeOff');
    const title = failure === undefined
        ? base
        : `${base}\n${t('fastModeRefused')} — ${`${failure.code ?? ''} ${failure.message}`.trim()}`;
    return (_jsxs(_Fragment, { children: [_jsx("style", { children: fastModeCss }), _jsxs("button", { type: "button", className: "dsh-subscription-fast-mode", "data-active": active, "data-pending": pending, "data-failed": failure !== undefined, "aria-pressed": active, "aria-busy": pending, title: title, "aria-label": title, onClick: (event) => {
                    event.stopPropagation();
                    setPending(true);
                    setFailure(undefined);
                    // Turning it off drops the rung, which the host then reads as the model's own
                    // default. The model id is never swapped, so this cannot strand the selection.
                    const next = active ? { provider, model: target } : { provider, model: target, reasoningEffort: pair.rung };
                    if (modelDirectory?.select === undefined) {
                        setFailure({ message: t('fastModeUnavailable') });
                        setPending(false);
                        return;
                    }
                    void Promise.resolve(modelDirectory.select(next))
                        .then((result) => {
                        // The directory reports its own outcome: a refused selection carries why.
                        const outcome = result;
                        if (outcome?.ok !== false)
                            return;
                        const code = outcome.error?.code;
                        const message = outcome.error?.message ?? code ?? 'the host refused the selection';
                        setFailure({ ...code === undefined ? {} : { code }, message });
                        console.warn(`[subscription-oauth] fast mode ${provider}/${target} refused:`, outcome.error);
                    })
                        .catch((error) => {
                        setFailure({ message: error instanceof Error ? error.message : String(error) });
                        console.warn(`[subscription-oauth] fast mode ${provider}/${target} failed:`, error);
                    })
                        .finally(() => { setPending(false); });
                }, children: [_jsx("svg", { className: "dsh-subscription-fast-mode-bolt", viewBox: "0 0 24 24", width: "16", height: "16", "aria-hidden": "true", fill: "none", children: _jsx("path", { d: "M13 2 3.6 13.2a1.2 1.2 0 0 0 .92 1.98H11l-.86 6.42a1.2 1.2 0 0 0 2.2.7L21.4 10.6a1.2 1.2 0 0 0-.92-1.98H13l.86-6.42A1.2 1.2 0 0 0 13 2z", fill: "currentColor", stroke: "currentColor", strokeWidth: "1.2", strokeLinejoin: "round" }) }), failure !== undefined && (_jsx("span", { className: "dsh-subscription-fast-mode-reason", children: failure.code ?? failure.message }))] })] }));
}
