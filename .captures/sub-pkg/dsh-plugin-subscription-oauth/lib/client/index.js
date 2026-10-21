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
import { SubscriptionsSection } from './SubscriptionsSection.js';
import { FastModeButton } from './FastModeButton.js';
import { UsageChip } from './UsageChip.js';
import { ProviderCardExtras } from './ProviderCardExtras.js';
import { createSubscriptionsCall, createAccountBalanceCall, createCcSwitchBalanceCall } from './subscriptions-rpc.js';
import { en, zh } from './locales.js';
import { SETTINGS_NS } from '../protocol.js';
export { createSubscriptionsCall, SubscriptionsRpcError } from './subscriptions-rpc.js';
/**
 * Routes this plugin serves.
 *
 * The chip only reports for these; a provider somebody else registered gets no chip,
 * because this plugin has no way to read its allowance.
 */
const OWN_PROVIDER_IDS = new Set(['kimi', 'grok', 'antigravity', 'glm', 'ark', 'mimo', 'workbuddy']);
/** Dictionary namespace owned by this plugin. */
const NS = 'settings.subscriptions';
/** Settings nav position: after the shipped sections, before plugin tooling. */
const SECTION_ORDER = 92;
/**
 * Composer-dock position: after the shell's own stats pills, which sit at 0.
 *
 * The shell owns that bar and registers `tok/s` and the cache-hit pill into it, so the
 * chip follows them rather than displacing them.
 */
const CHIP_ORDER = 10;
/**
 * Composer-control position: ahead of the shell's own entries in that row, if it ever
 * adds any, so the quick-mode button stays adjacent to the model selector it belongs to.
 */
const FAST_MODE_ORDER = 10;
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
export const inject = ['slots', 'connection', 'locale'];
/**
 * Register the Subscriptions settings section.
 *
 * Registration goes through `slots.inject`, so the shell's own apply order
 * relative to this one is unconstrained.
 *
 * @param ctx - client root context.
 */
export function apply(ctx) {
    ctx.effect(() => ctx.locale.register(NS, { zh, en }), 'dsh-plugin-subscription-oauth: copy dictionaries');
    // The shell's Context merge types `connection` as the host handle; in the
    // browser the same key holds the full client ConnectionHandle.
    const connection = ctx.get('connection');
    const t = ctx.locale.bind(NS);
    const call = createSubscriptionsCall(connection.rpc);
    // Read once: the resolver is a service, and the per-session directory is resolved at
    // registration time (see `modelDirectoryFor`).
    const modelDirectories = ctx.get('modelDirectories');
    ctx.slots.inject('settings.section', () => ctx.slots.register({
        name: 'settings.section',
        id: 'subscriptions',
        order: SECTION_ORDER,
        // A thunk re-evaluated per read, so the nav label follows the active locale.
        label: () => t('nav'),
        inject: () => ({ call, t }),
    }, SubscriptionsSection));
    // The composer's bottom bar is a session-scoped list the shell owns; its own stats
    // pills sit at order 0. This chip rides beside them and shows the allowance of the
    // provider the session is using. The scope hands `inject` the session id, but the chip
    // reads the selection from its own projection prop, so nothing here needs it.
    ctx.slots.inject('conversation.composer.dock', () => ctx.slots.register({
        name: 'conversation.composer.dock',
        id: 'subscription-usage',
        order: CHIP_ORDER,
        inject: () => ({
            call,
            t: t,
            isOwnProvider: (provider) => OWN_PROVIDER_IDS.has(provider),
            accountBalance: createAccountBalanceCall(connection.rpc),
            ccSwitchBalance: createCcSwitchBalanceCall(connection.rpc),
        }),
    }, UsageChip));
    // The composer renders this seat immediately before its model selector, so the
    // quick-mode button lands at the picker's left. It renders nothing unless the current
    // model really has a fast variant, so a route without one shows no control at all.
    ctx.slots.inject('conversation.input.right', () => ctx.slots.register({
        name: 'conversation.input.right',
        id: 'subscription-fast-mode',
        order: FAST_MODE_ORDER,
        inject: (sessionId) => ({
            call,
            isOwnProvider: (provider) => OWN_PROVIDER_IDS.has(provider),
            t: t,
            modelDirectory: modelDirectoryFor(modelDirectories, sessionId),
        }),
    }, FastModeButton));
    // The Models settings page declares a keyed card-extension seat per provider
    // row, dispatched with `entryKey = settingsNs`. The node half declares every
    // route under the shared namespace `subscriptions`, so one registration here
    // gives each of them a Connect block beside the section's own editor.
    ctx.slots.inject('settings.models.provider-card', () => ctx.slots.register({
        name: 'settings.models.provider-card',
        key: SETTINGS_NS,
        inject: () => ({
            call,
            t: t,
        }),
    }, ProviderCardExtras));
}
/**
 * The session's model directory, in the shape the quick-mode button writes through.
 *
 * The lookup is lazy: the returned `select` resolves the host's directory on first use,
 * not at inject time. The host's `directoryFor` throws while its own `remote.session`
 * dependency is still starting (observed on desktop), so resolving eagerly at mount hid
 * the button for the whole session even though the directory became ready a moment later.
 *
 * @param modelDirectories - the host's directory resolver, when it has one.
 * @param sessionId - the session whose directory is wanted.
 * @returns the writer, or undefined when there is no resolver or session at all.
 */
function modelDirectoryFor(modelDirectories, sessionId) {
    if (sessionId === undefined || modelDirectories?.directoryFor === undefined)
        return undefined;
    // A lazily-resolved select: the first call walks the host's directory; a throw there is
    // a transient startup miss, so it is retried once after a short beat before failing.
    return {
        select: async (selection) => {
            const resolve = () => modelDirectories.directoryFor?.(sessionId);
            let directory = (() => { try {
                return resolve();
            }
            catch {
                return undefined;
            } })();
            if (directory?.select === undefined) {
                await new Promise((resolveWait) => setTimeout(resolveWait, 500));
                try {
                    directory = resolve();
                }
                catch {
                    directory = undefined;
                }
            }
            if (directory?.select === undefined)
                throw new Error('the model directory is not available yet; try again');
            return directory.select(selection);
        },
    };
}
