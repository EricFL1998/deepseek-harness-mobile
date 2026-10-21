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
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
/** Read the ledger, tolerating a missing or corrupt file. */
export async function readSpendLedger(file) {
    try {
        const raw = JSON.parse(await readFile(file, 'utf8'));
        if (typeof raw !== 'object' || raw === null || Array.isArray(raw))
            return {};
        const out = {};
        for (const [key, value] of Object.entries(raw)) {
            if (typeof value !== 'object' || value === null)
                continue;
            const record = value;
            const spent = typeof record.spent === 'number' && Number.isFinite(record.spent) ? record.spent : 0;
            const calls = typeof record.calls === 'number' && Number.isFinite(record.calls) ? record.calls : 0;
            out[key] = { spent: Math.max(0, spent), calls: Math.max(0, calls) };
        }
        return out;
    }
    catch {
        return {};
    }
}
/** Add one turn's billed credit to an account's spend, returning the new totals. */
export async function recordSpend(file, account, credit) {
    const ledger = await readSpendLedger(file);
    const previous = ledger[account] ?? { spent: 0, calls: 0 };
    const next = {
        spent: Math.round((previous.spent + Math.max(0, credit)) * 10000) / 10000,
        calls: previous.calls + 1,
    };
    ledger[account] = next;
    try {
        await mkdir(path.dirname(file), { recursive: true });
        await writeFile(file, JSON.stringify(ledger, null, 2), 'utf8');
    }
    catch {
        // A failed write keeps the in-memory figure; the next turn retries.
    }
    return next;
}
