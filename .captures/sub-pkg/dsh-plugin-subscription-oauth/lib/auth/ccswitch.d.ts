/**
 * The cc-switch balance query, reimplemented for the plugin's node half.
 *
 * cc-switch (github.com/farion1231/cc-switch) reads a DeepSeek-compatible provider's
 * balance with `GET https://api.deepseek.com/user/balance`, an `Authorization: Bearer
 * <api key>` header, and a 15 s timeout. This is that request, with the URL hardcoded
 * exactly as cc-switch does — the provider's own base URL is never substituted in.
 *
 * The endpoint answers `{ is_available, balance_infos: [{ currency, total_balance, … }] }`,
 * where a balance is a number or a numeric string. The module reduces that to the one
 * figure a status chip needs: the first currency's total. Anything else — a missing key,
 * a non-2xx answer, malformed JSON — resolves to nothing, so the caller shows no balance
 * rather than a wrong one.
 *
 * @module dsh-plugin-subscription-oauth/auth/ccswitch
 */
import type { FetchFn } from '../http.js';
/** One currency's balance, normalised from the endpoint's answer. */
export interface CcSwitchBalance {
    /** The currency the balance is denominated in. */
    currency: string;
    /** The remaining balance, as a decimal string. */
    total: string;
    /** Whether the account reports itself as usable. */
    available: boolean;
}
/**
 * Query the balance endpoint some OpenAI-compatible providers expose at `{base}/v1/usage`.
 *
 * huanling is one: it answers `GET {base}/v1/usage` with `{ remaining, unit, … }`, which is
 * the figure cc-switch shows for it. This is the provider's own shape, not cc-switch's
 * `balance_infos`, so it is read separately. The base has any trailing `/v1` stripped before
 * the path is added, so both `https://api.huanling.icu` and `https://api.huanling.icu/v1`
 * resolve to the same `{base}/v1/usage`.
 *
 * @param input - the provider's API key and base URL.
 * @returns the balance, or undefined when the provider did not answer with one.
 */
export declare function queryUsageBalance(input: {
    apiKey: string;
    baseUrl: string;
    fetchFn?: FetchFn;
}): Promise<CcSwitchBalance | undefined>;
/**
 * Query one provider's balance the way cc-switch does.
 *
 * cc-switch reads a DeepSeek-compatible provider's balance with `GET <base>/user/balance`
 * and a bearer API key. For the official DeepSeek that base is `api.deepseek.com`, so the
 * path is effectively hardcoded there; for a provider the user added through a custom
 * endpoint (like huanling), the same path is read against that provider's own base URL,
 * which is the only place its balance can live.
 *
 * @param input - the provider's API key and base URL.
 * @returns the balance, or undefined when the provider did not answer with one.
 */
export declare function queryCcSwitchBalance(input: {
    apiKey: string;
    /** The provider's own base URL; `https://api.deepseek.com` for the official one. */
    baseUrl: string;
    fetchFn?: FetchFn;
}): Promise<CcSwitchBalance | undefined>;
