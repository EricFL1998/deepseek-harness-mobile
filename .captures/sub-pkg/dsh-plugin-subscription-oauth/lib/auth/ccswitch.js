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
import { proxiedFetch } from '../http.js';
/** Read a balance field that may be a number or a numeric string. */
function num(value) {
    if (typeof value === 'number' && Number.isFinite(value))
        return String(value);
    if (typeof value === 'string' && value.trim() !== '' && Number.isFinite(Number(value)))
        return value.trim();
    return undefined;
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
export async function queryUsageBalance(input) {
    const fetchFn = input.fetchFn ?? proxiedFetch;
    const base = input.baseUrl.replace(/\/+$/, '').replace(/\/v1$/i, '');
    const url = `${base}/v1/usage`;
    let response;
    try {
        response = await fetchFn(url, {
            headers: { authorization: `Bearer ${input.apiKey}`, accept: 'application/json' },
            signal: AbortSignal.timeout(15_000),
        });
    }
    catch {
        return undefined;
    }
    if (!response.ok)
        return undefined;
    let payload;
    try {
        payload = await response.json();
    }
    catch {
        return undefined;
    }
    if (typeof payload !== 'object' || payload === null)
        return undefined;
    const record = payload;
    const total = num(record.remaining);
    if (total === undefined)
        return undefined;
    const currency = typeof record.unit === 'string' && record.unit.trim() !== '' ? record.unit.trim() : 'USD';
    return {
        currency,
        total,
        available: record.isValid !== false && record.is_active !== false,
    };
}
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
export async function queryCcSwitchBalance(input) {
    const fetchFn = input.fetchFn ?? proxiedFetch;
    // Resolve `<base>/user/balance` without doubling or dropping the path: a base that
    // already ends in `/v1` keeps it, since that is the path the OpenAI-compatible wire
    // uses and the balance endpoint lives beside it.
    const url = `${input.baseUrl.replace(/\/+$/, '')}/user/balance`;
    let response;
    try {
        response = await fetchFn(url, {
            headers: { authorization: `Bearer ${input.apiKey}`, accept: 'application/json' },
            signal: AbortSignal.timeout(15_000),
        });
    }
    catch {
        return undefined;
    }
    if (!response.ok)
        return undefined;
    let payload;
    try {
        payload = await response.json();
    }
    catch {
        return undefined;
    }
    if (typeof payload !== 'object' || payload === null)
        return undefined;
    const record = payload;
    const infos = record.balance_infos;
    if (!Array.isArray(infos))
        return undefined;
    for (const raw of infos) {
        if (typeof raw !== 'object' || raw === null)
            continue;
        const info = raw;
        const total = num(info.total_balance);
        if (total === undefined)
            continue;
        const currency = typeof info.currency === 'string' && info.currency.trim() !== '' ? info.currency.trim() : 'CNY';
        return {
            currency,
            total,
            available: record.is_available !== false,
        };
    }
    return undefined;
}
