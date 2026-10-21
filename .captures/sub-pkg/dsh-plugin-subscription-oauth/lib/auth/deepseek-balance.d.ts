/**
 * The DeepSeek account's balance, read on the node half.
 *
 * The host owns the DeepSeek OAuth grant and its platform endpoint, and exposes a
 * `deepseekAccount` service with a `getBalance(client)`. The browser cannot call that
 * service directly — it lives behind the host's Remote controller, not a `/api` fetch
 * route — so this plugin's node half reads it and serves it over the plugin's own RPC,
 * the same channel the rest of the chip's data already travels on.
 *
 * @module dsh-plugin-subscription-oauth/auth/deepseek-balance
 */
import type { FetchFn } from '../http.js';
/** One currency's wallet, as the platform reports it. */
export interface DeepSeekWallet {
    /** ISO currency the balance is denominated in. */
    currency: 'CNY' | 'USD' | string;
    /** Remaining balance, as a decimal string. */
    balance: string;
}
/** The DeepSeek account's wallets, normal and bonus. */
export interface DeepSeekBalance {
    /** Normal (topped-up) wallets, one per currency. */
    wallets: readonly DeepSeekWallet[];
    /** Bonus / promotional wallets, one per currency. */
    bonusWallets: readonly DeepSeekWallet[];
}
/**
 * The slice of the host's `deepseekAccount` service this module reads.
 *
 * Declared locally rather than imported: the host package's published typings differ
 * across DSH lines, and this plugin only needs this one method.
 */
export interface DeepSeekAccountService {
    getBalance: (client: unknown) => Promise<unknown>;
}
/**
 * Read the DeepSeek account's balance through the host's service.
 *
 * @param account - the host's `deepseekAccount` service, when the composition provides
 * one.
 * @param client - the client metadata the service expects.
 * @returns the balance, or undefined when the account is signed out or the read failed.
 */
export declare function readDeepSeekBalance(account: DeepSeekAccountService | undefined, client: unknown): Promise<DeepSeekBalance | undefined>;
/**
 * Reduce the host service's answer to the wallets the chip shows.
 *
 * The service resolves `null` when the account is signed out, `{ status: 'failed' }`
 * when the read did not complete, and `{ status: 'ready', value: <normal>, bonusWallets }`
 * on success. Anything else — an unexpected shape from a newer host — is nothing rather
 * than a wrong figure.
 *
 * @param value - the service's raw answer.
 * @returns the wallets, or undefined when there is nothing usable.
 */
export declare function normalizeDeepSeekBalance(value: unknown): DeepSeekBalance | undefined;
/** A fetch substitute is accepted for symmetry with the other balance readers. */
export type { FetchFn };
