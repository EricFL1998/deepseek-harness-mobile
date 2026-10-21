/**
 * Browser caller for the plugin's own RPC channel.
 *
 * The node half mounts one exact `POST /api/subscription-oauth.<endpoint>` route
 * per endpoint, so every call travels on the shared `/api` channel with a
 * prefixed endpoint name — the same wire the rest of the shell uses.
 *
 * @module dsh-plugin-subscription-oauth/client/subscriptions-rpc
 */
import { RPC_PREFIX } from '../protocol.js';
/** The shared channel the node half's routes are mounted on. */
const CHANNEL = '/api';
/** A failed call: either the transport rejected or the endpoint answered `ok: false`. */
export class SubscriptionsRpcError extends Error {
    code;
    constructor(message, code) {
        super(message);
        this.code = code;
        this.name = 'SubscriptionsRpcError';
    }
}
/** Human-readable message for any thrown value. */
function messageOf(error) {
    return error instanceof Error ? error.message : String(error);
}
/**
 * Bind a caller to one connection's RPC transport.
 * @param rpc - the browser connection's RPC caller.
 * @returns a typed caller for this plugin's endpoints.
 */
export function createSubscriptionsCall(rpc) {
    return async function call(endpoint, payload) {
        let result;
        try {
            result = await rpc.call(CHANNEL, `${RPC_PREFIX}${endpoint}`, payload);
        }
        catch (error) {
            throw new SubscriptionsRpcError(messageOf(error), 'RPC_TRANSPORT');
        }
        if (!result.ok)
            throw new SubscriptionsRpcError(result.error.message, result.error.code);
        return result.value;
    };
}
/**
 * Read the DeepSeek account's balance through the plugin's own RPC.
 *
 * The browser cannot call the host's account service directly — it sits behind the
 * host's Remote controller, not a `/api` fetch route — so the node half reads it and
 * serves it on the plugin's channel, the same one the rest of the chip's data travels.
 *
 * @param rpc - the browser connection's RPC caller.
 * @returns the balance, or undefined when there is nothing to show.
 */
export function createAccountBalanceCall(rpc) {
    return async function call() {
        let result;
        try {
            result = await rpc.call(CHANNEL, `${RPC_PREFIX}deepseek-balance`, {
                version: '1',
                locale: Intl.DateTimeFormat().resolvedOptions().locale ?? 'en-US',
                // East-positive whole seconds, as the host's account service expects (the same
                // value DSH's own account card sends).
                timezoneOffsetSeconds: -new Date().getTimezoneOffset() * 60,
            });
        }
        catch (error) {
            throw new SubscriptionsRpcError(messageOf(error), 'RPC_TRANSPORT');
        }
        if (!result.ok)
            throw new SubscriptionsRpcError(result.error.message, result.error.code);
        return result.value;
    };
}
/**
 * Read a manually added provider's balance through the plugin's cc-switch endpoint.
 *
 * The caller names the credential reference the provider's profile uses (its
 * Read a non-subscription provider's balance through the plugin's cc-switch endpoint.
 *
 * The caller names the provider; the server maps it to the credential reference the
 * provider's profile names (`apiKeyEnv`) and resolves that to the API key. The result
 * is `undefined` when there is no key or the provider did not answer — never a wrong
 * figure.
 *
 * @param rpc - the browser connection's RPC caller.
 * @returns a narrow caller taking the provider id.
 */
export function createCcSwitchBalanceCall(rpc) {
    return async function call(provider) {
        let result;
        try {
            result = await rpc.call(CHANNEL, `${RPC_PREFIX}ccswitch-balance`, { provider });
        }
        catch (error) {
            throw new SubscriptionsRpcError(messageOf(error), 'RPC_TRANSPORT');
        }
        if (!result.ok)
            throw new SubscriptionsRpcError(result.error.message, result.error.code);
        return result.value;
    };
}
