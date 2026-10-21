/**
 * Cross-request memory of when each provider account's allowance resets.
 *
 * This is what lets one model id draw on the whole account pool without a wasted
 * request: when the default account's allowance runs out, the moment it will refill is
 * remembered, and until then the scheduler sends work straight to another account
 * instead of trying the spent one, failing, and only then moving on. When the reset
 * time passes the spent account is first choice again.
 *
 * The holder is shared by the route's reader (which learns reset times from usage
 * reads) and its streamer (which consults them when picking an account), so the
 * scheduler's view is exactly the last read's.
 *
 * @module dsh-plugin-subscription-oauth/auth/allowance-scheduler
 */
/** Default cooldown after a refused request, so the provider's gate is not re-hit at once. */
export const DEFAULT_REFUSAL_COOLDOWN_MS = 2 * 60 * 1000;
/**
 * How long a verification gate is remembered.
 *
 * The gate clears in the user's browser, not here, so this is only long enough to keep
 * a gated account from being re-hit every turn; once it lapses the account is probed
 * again, which is how a freshly verified account returns to the pool by itself.
 */
export const DEFAULT_GATE_TTL_MS = 30 * 60 * 1000;
/** In-memory, per-provider memory of account reset times. */
export class AllowanceScheduler {
    byProvider = new Map();
    /** One provider's account states, creating the map on first use. */
    provider(provider) {
        let map = this.byProvider.get(provider);
        if (map === undefined) {
            map = new Map();
            this.byProvider.set(provider, map);
        }
        return map;
    }
    /**
     * Record that one account's allowance resets at a given moment.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @param resetsAt - epoch milliseconds of the allowance reset, when known.
     */
    noteReset(provider, account, resetsAt) {
        const existing = this.byProvider.get(provider)?.get(account);
        this.provider(provider).set(account, {
            ...existing?.cooldownUntil === undefined ? {} : { cooldownUntil: existing.cooldownUntil },
            ...resetsAt === undefined ? {} : { resetsAt },
            learnedAt: Date.now(),
        });
    }
    /**
     * Cool one account after its request was refused, so the provider's gate is not
     * re-hit at once.
     *
     * A 403 "verify your account" is Google's safety gate, not a dead credential; hitting
     * it in a tight loop across every account is what turns a transient check into a
     * sustained block. Cooling the refused account for a short beat (and trying another)
     * is what keeps the pool reachable — the same discipline a careful client keeps.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @param until - epoch milliseconds to cool until; defaults to a short beat.
     */
    coolDown(provider, account, until = Date.now() + DEFAULT_REFUSAL_COOLDOWN_MS) {
        const existing = this.byProvider.get(provider)?.get(account);
        this.provider(provider).set(account, {
            ...existing?.resetsAt === undefined ? {} : { resetsAt: existing.resetsAt },
            cooldownUntil: until,
            learnedAt: Date.now(),
        });
    }
    /**
     * Whether one account is cooling after a refused request and should be left alone.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @returns whether the account is mid-cooldown.
     */
    isCooling(provider, account) {
        const state = this.byProvider.get(provider)?.get(account);
        return state?.cooldownUntil !== undefined && Date.now() < state.cooldownUntil;
    }
    /**
     * Remember one account as gated behind the provider's own verification wall.
     *
     * A gated account is neither a dead credential nor a spent allowance: the provider
     * wants its owner to verify it, so until that happens in the browser — or this memory
     * lapses — requests belong on another account instead of on another refusal. Recording
     * it is also what keeps a cooldown from being mistaken for the gate: a cooled account
     * is retried by design, a gated one is skipped.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @param until - epoch milliseconds to remember the gate until; defaults to a long beat.
     */
    markGated(provider, account, until = Date.now() + DEFAULT_GATE_TTL_MS) {
        const existing = this.byProvider.get(provider)?.get(account);
        this.provider(provider).set(account, {
            ...existing,
            gatedUntil: until,
            learnedAt: Date.now(),
        });
    }
    /**
     * Whether one account is remembered as gated by the provider.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @returns whether the account is mid-gate.
     */
    isGated(provider, account) {
        const state = this.byProvider.get(provider)?.get(account);
        return state?.gatedUntil !== undefined && Date.now() < state.gatedUntil;
    }
    /**
     * Forget one account's remembered state, when the account is removed.
     *
     * A signed-out account's allowance state is no longer meaningful — there is no
     * account to schedule around — and a re-added account starts fresh.
     *
     * @param provider - the provider route id.
     * @param account - the account key to forget.
     */
    clear(provider, account) {
        this.byProvider.get(provider)?.delete(account);
    }
    /**
     * Whether one account's allowance is known to be spent now.
     *
     * True when the account was recorded with a reset time that is still in the future:
     * its allowance is spent and will not refill until then, so a request should go to a
     * different account rather than fail against this one.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @returns whether the account's allowance is spent until its remembered reset.
     */
    isSpentUntilReset(provider, account) {
        const state = this.byProvider.get(provider)?.get(account);
        if (state === undefined)
            return false;
        if (state.cooldownUntil !== undefined && Date.now() < state.cooldownUntil)
            return true;
        if (state.resetsAt === undefined)
            return false;
        return Date.now() < state.resetsAt;
    }
    /**
     * The moment one account's allowance is known to reset, when it was recorded.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @returns the reset instant, or undefined when unknown or already passed.
     */
    resetAt(provider, account) {
        const state = this.byProvider.get(provider)?.get(account);
        if (state === undefined || state.resetsAt === undefined)
            return undefined;
        return Date.now() < state.resetsAt ? state.resetsAt : undefined;
    }
}
/** The one shared scheduler every route reads and writes. */
export const allowanceScheduler = new AllowanceScheduler();
