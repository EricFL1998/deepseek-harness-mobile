/**
 * Per-account credit spend tracking for the WorkBuddy route.
 *
 * The gateway states the real billed figure for a turn — `usage.credit` on the last
 * usage frame of the stream — so the spent total is a sum of those, never an estimate
 * from tokens and a rate. Spends accumulate per account in a small JSON file beside the
 * credentials, and fold with the live packages' remaining allowance into one precise
 * spent-against-limit window.
 *
 * @module dsh-plugin-subscription-oauth/providers/workbuddy-credits
 */
/** One account's accumulated spend. */
interface AccountSpend {
    spent: number;
    calls: number;
}
/** The spend ledger, keyed by account id. */
export type SpendLedger = Record<string, AccountSpend>;
/** Read the ledger, tolerating a missing or corrupt file. */
export declare function readSpendLedger(file: string): Promise<SpendLedger>;
/** Add one turn's billed credit to an account's spend, returning the new totals. */
export declare function recordSpend(file: string, account: string, credit: number): Promise<AccountSpend>;
export {};
