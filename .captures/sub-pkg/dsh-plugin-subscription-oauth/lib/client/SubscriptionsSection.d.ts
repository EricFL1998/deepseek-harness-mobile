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
import type { SubscriptionsKey } from './locales.js';
import type { SubscriptionsCall } from './subscriptions-rpc.js';
/** What the node half injects into this section. */
export interface SubscriptionsSectionInjected {
    /** Typed caller for the plugin's RPC endpoints. */
    call: SubscriptionsCall;
    /** Translate a key of this plugin's settings namespace. */
    t: (key: SubscriptionsKey, params?: Record<string, unknown>) => string;
}
/** Component props: the shell supplies `close`, the plugin supplies the rest. */
export type SubscriptionsSectionProps = Partial<SubscriptionsSectionInjected> & {
    /** Close the settings panel (owned by the settings shell). */
    close?: () => void;
};
/**
 * The Subscriptions settings section.
 * @param props - section props (inject face plus the shell's `close`).
 * @returns the section content.
 */
export declare function SubscriptionsSection(props: SubscriptionsSectionProps): import("react").JSX.Element;
