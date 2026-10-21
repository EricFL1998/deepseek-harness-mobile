/**
 * Wire contract shared by the plugin's node half (the RPC routes) and its
 * browser half (the Settings page).
 *
 * Only this module crosses the boundary: the client bundle imports the types
 * and the channel name, and every call travels as one JSON envelope over a
 * `POST /api/<prefix><endpoint>` route. One status document describes every
 * provider route the plugin serves, so the page renders one card per entry.
 *
 * @module dsh-plugin-subscription-oauth/protocol
 */
/** Prefix of every RPC method this plugin serves. */
export const RPC_PREFIX = 'subscription-oauth.';
/**
 * The settings namespace the Models page keys this plugin's provider cards by.
 *
 * The settings service projects a plugin's `Config` schema under the Cordis row
 * id that mounted it (`settings/src/index.ts`: `ns = entry.options.id`), and the
 * Models section dispatches `settings.models.provider-card` with that same
 * namespace as `entryKey`. This constant therefore names the row id declared in
 * `cordis.patch.yml`; the two must stay in step, or the Models page renders the
 * routes with no subscription block (the standalone Subscriptions page is
 * unaffected).
 */
export const SETTINGS_NS = 'llm-subscription-oauth';
/** Endpoints the Settings page may call. */
export const RPC_ENDPOINTS = [
    'status',
    'login',
    'cancel',
    'logout',
    'setDefault',
    'refresh',
    'discover',
    'setModelVisible',
    'setModelOrder',
    'addModel',
    'removeModel',
    'usage',
    'reload',
    'auto-state',
    'setAutoOn',
    'checkin',
    'tasks',
    'deepseek-balance',
    'ccswitch-balance',
    'model-variants',
];
