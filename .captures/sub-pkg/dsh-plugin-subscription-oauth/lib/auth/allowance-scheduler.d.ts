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
/** One account's remembered allowance state. */
export interface AllowanceState {
    /** Epoch milliseconds at which the account's allowance resets, when known. */
    resetsAt?: number;
    /** Epoch milliseconds until which the account is cooling after a refused request. */
    cooldownUntil?: number;
    /** Epoch milliseconds until which the provider has gated the account behind verification. */
    gatedUntil?: number;
    /** Epoch milliseconds at which this state was last learned. */
    learnedAt: number;
}
/** Default cooldown after a refused request, so the provider's gate is not re-hit at once. */
export declare const DEFAULT_REFUSAL_COOLDOWN_MS: number;
/**
 * How long a verification gate is remembered.
 *
 * The gate clears in the user's browser, not here, so this is only long enough to keep
 * a gated account from being re-hit every turn; once it lapses the account is probed
 * again, which is how a freshly verified account returns to the pool by itself.
 */
export declare const DEFAULT_GATE_TTL_MS: number;
/** In-memory, per-provider memory of account reset times. */
export declare class AllowanceScheduler {
    private readonly byProvider;
    /** One provider's account states, creating the map on first use. */
    private provider;
    /**
     * Record that one account's allowance resets at a given moment.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @param resetsAt - epoch milliseconds of the allowance reset, when known.
     */
    noteReset(provider: string, account: string, resetsAt?: number): void;
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
    coolDown(provider: string, account: string, until?: number): void;
    /**
     * Whether one account is cooling after a refused request and should be left alone.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @returns whether the account is mid-cooldown.
     */
    isCooling(provider: string, account: string): boolean;
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
    markGated(provider: string, account: string, until?: number): void;
    /**
     * Whether one account is remembered as gated by the provider.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @returns whether the account is mid-gate.
     */
    isGated(provider: string, account: string): boolean;
    /**
     * Forget one account's remembered state, when the account is removed.
     *
     * A signed-out account's allowance state is no longer meaningful — there is no
     * account to schedule around — and a re-added account starts fresh.
     *
     * @param provider - the provider route id.
     * @param account - the account key to forget.
     */
    clear(provider: string, account: string): void;
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
    isSpentUntilReset(provider: string, account: string): boolean;
    /**
     * The moment one account's allowance is known to reset, when it was recorded.
     *
     * @param provider - the provider route id.
     * @param account - the account key.
     * @returns the reset instant, or undefined when unknown or already passed.
     */
    resetAt(provider: string, account: string): number | undefined;
}
/** The one shared scheduler every route reads and writes. */
export declare const allowanceScheduler: AllowanceScheduler;
