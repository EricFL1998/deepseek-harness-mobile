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
import type { ReactNode } from 'react';
import type { SessionStandardProps } from '@deepseek-ai/dsh-client-ui-slots';
import type { SubscriptionsCall } from './subscriptions-rpc.js';
import type { SubscriptionsKey } from './locales.js';
/** Injected props the button is registered with. */
export interface FastModeButtonInjected {
    call: SubscriptionsCall;
    /** Whether the provider is one this plugin serves. */
    isOwnProvider: (provider: string) => boolean;
    /** Copy for the active locale (title and refusal reason). */
    t: (key: SubscriptionsKey, values?: Record<string, string | number>) => string;
    /**
     * The session's model directory, when the host exposes one.
     *
     * Written when the button switches the model's `fast` rung. Absent on a host without
     * it, in which case the button renders nothing.
     */
    modelDirectory?: {
        /** Select one model and its reasoning rung; resolves when the picker accepted it. */
        select?: (selection: {
            provider: string;
            model: string;
            reasoningEffort?: string;
        }) => Promise<unknown>;
    } | undefined;
}
/**
 * The quick-mode control, for the composer's row of standard controls.
 *
 * @param props - injected RPC caller, directory, and the session standard kit.
 * @returns the button, or null when this model has no fast variant.
 */
export declare function FastModeButton({ call, isOwnProvider, modelDirectory, t, useProjection }: FastModeButtonInjected & SessionStandardProps): ReactNode;
