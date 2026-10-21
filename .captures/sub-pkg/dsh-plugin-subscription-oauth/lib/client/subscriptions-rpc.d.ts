/**
 * Browser caller for the plugin's own RPC channel.
 *
 * The node half mounts one exact `POST /api/subscription-oauth.<endpoint>` route
 * per endpoint, so every call travels on the shared `/api` channel with a
 * prefixed endpoint name — the same wire the rest of the shell uses.
 *
 * @module dsh-plugin-subscription-oauth/client/subscriptions-rpc
 */
import type { ConnectionHandle } from '@deepseek-ai/dsh-api-remotes/client';
import type { CcSwitchBalanceResult, RpcEndpoint, RpcMap } from '../protocol.js';
/** A failed call: either the transport rejected or the endpoint answered `ok: false`. */
export declare class SubscriptionsRpcError extends Error {
    readonly code: string;
    constructor(message: string, code: string);
}
/** Call one endpoint, unwrapping the transport result. */
export type SubscriptionsCall = <K extends RpcEndpoint>(endpoint: K, payload: RpcMap[K]['payload']) => Promise<RpcMap[K]['value']>;
/** A caller for one non-subscription provider's cc-switch balance. */
export interface CcSwitchBalanceCall {
    (provider: string): Promise<CcSwitchBalanceResult>;
}
/**
 * Bind a caller to one connection's RPC transport.
 * @param rpc - the browser connection's RPC caller.
 * @returns a typed caller for this plugin's endpoints.
 */
export declare function createSubscriptionsCall(rpc: ConnectionHandle['rpc']): SubscriptionsCall;
/**
 * The DeepSeek account's balance, as the plugin's node half reports it.
 *
 * The node half reads the host's `deepseekAccount` service (which owns the OAuth grant
 * and the platform endpoint) and serves the wallets here. It answers `undefined` when
 * the account is signed out or the read failed, so a chip simply does not render.
 */
export interface AccountBalance {
    /** Normal (topped-up) wallets, one per currency. */
    wallets: readonly {
        currency: string;
        balance: string;
    }[];
    /** Bonus / promotional wallets, one per currency. */
    bonusWallets: readonly {
        currency: string;
        balance: string;
    }[];
}
/** A narrow caller for the DeepSeek account's balance. */
export interface AccountBalanceCall {
    (): Promise<AccountBalance | undefined>;
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
export declare function createAccountBalanceCall(rpc: ConnectionHandle['rpc']): AccountBalanceCall;
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
export declare function createCcSwitchBalanceCall(rpc: ConnectionHandle['rpc']): CcSwitchBalanceCall;
