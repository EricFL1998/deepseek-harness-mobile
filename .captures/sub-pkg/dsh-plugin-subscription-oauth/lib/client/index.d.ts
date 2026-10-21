/**
 * Subscriptions settings page, browser half.
 *
 * Registers one `settings.section` entry titled 订阅 / Subscriptions. Every
 * connection fact arrives through the node half's `subscription-oauth` RPC
 * endpoints, so this half holds no credential state of its own, and the copy
 * rides the client locale service under the `settings.subscriptions` namespace.
 *
 * @module dsh-plugin-subscription-oauth/client
 */
import type { Context as ClientContext } from '@deepseek-ai/cordis';
import type { SubscriptionsKey } from './locales.js';
export type { SubscriptionsSectionInjected, SubscriptionsSectionProps } from './SubscriptionsSection.js';
export type { FastModeButtonInjected } from './FastModeButton.js';
export type { UsageChipInjected } from './UsageChip.js';
export type { SubscriptionsKey } from './locales.js';
export { createSubscriptionsCall, SubscriptionsRpcError } from './subscriptions-rpc.js';
export type { SubscriptionsCall, AccountBalanceCall } from './subscriptions-rpc.js';
declare module '@deepseek-ai/dsh-client-ui-slots' {
    interface LocaleNamespaceMap {
        /** The Subscriptions settings page copy. */
        'settings.subscriptions': SubscriptionsKey;
    }
    interface SlotMap {
        /**
         * The composer's bottom bar.
         *
         * Declared here as the shell declares it — a session-scoped list — because the
         * owning package ships no type declarations to a plugin that only consumes it.
         * The shell's own stats pills are one entry in this list; this plugin's usage chip
         * is another.
         */
        'conversation.composer.dock': {
            kind: 'list';
            scope: 'session';
        };
        /**
         * The composer's row of compact controls, rendered immediately before its model seat.
         *
         * The shell declares it as a session-scoped list; this plugin's quick-mode button is
         * one entry, which is what puts it at the model selector's left.
         */
        'conversation.input.right': {
            kind: 'list';
            scope: 'session';
        };
        /**
         * One provider card's adapter extension area on the Models settings page.
         *
         * Declared by the Models section at runtime and dispatched with
         * `entryKey = settingsNs`, so a plugin whose routes share one settings
         * namespace registers once under that namespace and receives every card of
         * the family. The owning package ships no type declarations a plugin can
         * see, so the seat is declared here as the shell declares it — a keyed,
         * root-scoped slot — with the owner share the page passes to each
         * occurrence.
         */
        'settings.models.provider-card': {
            kind: 'keyed';
            scope: 'root';
            owner: {
                /** The card's directory row (route id and display name). */
                provider: {
                    provider: string;
                    displayName: string;
                };
                /** Whether any layer configures this provider. */
                configured: boolean;
                /** Whether the row's referenced api-key credential is configured. */
                keyConfigured: boolean;
            };
        };
    }
    /**
     * The session standard kit, of which this plugin reads one member.
     *
     * `ui-session` merges the session identity and the projection reader into every
     * session-scoped component's props, and it ships no declarations a plugin can see, so
     * the member is declared here. It reads one session projection, optionally narrowed
     * by a selector, and re-renders the component when that projection changes — which is
     * what lets the usage chip follow the model picker.
     */
    interface SessionStandardProps {
        useProjection<T>(key: string, selector: (value: unknown) => T): T;
    }
}
/**
 * Required services: `slots` carries the registration seat, `connection` the
 * RPC caller, and `locale` the copy dictionaries.
 *
 * `modelDirectories` is deliberately NOT in this list: the fiber's `inject` is
 * wait semantics, and the quick-mode button is the only consumer, which reads
 * the resolver lazily with `ctx.get` (below) and renders nothing when the host
 * exposes none. Declaring it would park the whole browser half on a service
 * whose presence is not guaranteed, taking the settings page with it.
 */
export declare const inject: string[];
/**
 * Register the Subscriptions settings section.
 *
 * Registration goes through `slots.inject`, so the shell's own apply order
 * relative to this one is unconstrained.
 *
 * @param ctx - client root context.
 */
export declare function apply(ctx: ClientContext): void;
