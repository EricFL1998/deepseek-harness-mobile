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
import type { ReactNode } from 'react';
import type { SubscriptionsKey } from './locales.js';
import type { SubscriptionsCall } from './subscriptions-rpc.js';
/** Injected props the block is registered with. */
export interface ProviderCardExtrasInjected {
    /** The plugin's own RPC caller. */
    call: SubscriptionsCall;
    /** Copy for the active locale. */
    t: (key: SubscriptionsKey, values?: Record<string, string | number>) => string;
}
/**
 * The keyed slot's owner share, declared locally because the owning package
 * ships no public type for it. `provider` carries the card's directory row
 * (route id, display name, settings address, live state); `configured` and
 * `keyConfigured` describe the profile the page resolved for it. A
 * subscription route has no stored profile, so both stay false — the block
 * reads the live sign-in state from the plugin's own RPC instead.
 */
interface ProviderCardOwnerProps {
    provider: {
        provider: string;
        displayName: string;
    };
    configured: boolean;
    keyConfigured: boolean;
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
export declare function ProviderCardExtras({ call, t, provider, }: ProviderCardExtrasInjected & ProviderCardOwnerProps): ReactNode;
export {};
