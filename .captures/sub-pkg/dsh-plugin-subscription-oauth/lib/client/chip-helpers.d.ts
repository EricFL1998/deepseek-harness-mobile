/**
 * The composer usage chip's pure helpers.
 *
 * Split from the component so the test suite can assert the reduction from a
 * route's usage to one line without evaluating the component's React and
 * popover imports (the primitives package's published artifact pulls a
 * markdown and icon toolchain a bare Node run cannot resolve). `UsageChip.tsx`
 * re-exports everything here, so the public surface is unchanged.
 *
 * @module dsh-plugin-subscription-oauth/client/chip-helpers
 */
import type { SubscriptionsKey } from './locales.js';
import type { SubscriptionsCall, AccountBalanceCall, CcSwitchBalanceCall } from './subscriptions-rpc.js';
import type { ProviderUsage, UsageWindow } from '../protocol.js';
/** Injected props the chip is registered with. */
export interface UsageChipInjected {
    /** The plugin's own RPC caller. */
    call: SubscriptionsCall;
    /** Copy for the active locale. */
    t: (key: SubscriptionsKey, values?: Record<string, string | number>) => string;
    /** Whether the provider is one this plugin serves. */
    isOwnProvider: (provider: string) => boolean;
    /**
     * The DeepSeek account's balance, when the session uses it.
     *
     * The host owns that account's grant and the platform endpoint, so the chip asks the
     * host rather than re-deriving either. It resolves to `undefined` when the account is
     * signed out or the read fails, in which case the chip stays silent.
     */
    accountBalance: AccountBalanceCall;
    /**
     * A non-subscription provider's balance, by cc-switch's query.
     *
     * A provider that is not a subscription this plugin serves has no usage windows to
     * meter, so the chip shows its balance instead. The caller names the provider; the
     * server maps it to the credential reference the provider's profile names (`apiKeyEnv`),
     * resolves that to the API key, and asks cc-switch's balance endpoint. It resolves to
     * `undefined` when the provider has no balance source or did not answer.
     */
    ccSwitchBalance: CcSwitchBalanceCall;
}
/** One model choice, as the host records it. */
export interface ModelSelection {
    /** Provider route key. */
    readonly provider: string;
    /** Model id. */
    readonly model: string;
    /** Reasoning effort, when one was chosen. */
    readonly reasoningEffort?: string;
}
/**
 * The session's model-selection projection, as the host wires it.
 *
 * `next` is a selection the user has made but not yet used; `lastUsed` is the one a
 * request actually ran with. Reading `next` first is what makes the chip follow the
 * picker immediately rather than only after the next message.
 */
export interface ModelSelectionProjection {
    /** The last selection a request ran with. */
    readonly lastUsed: ModelSelection | null;
    /** The selection in force now. */
    readonly next: ModelSelection | null;
}
/** The session's provider, from its model-selection projection. */
export declare function providerOf(projection: ModelSelectionProjection | null | undefined): string | undefined;
/** What one route's allowance reduces to for a single line. */
export interface ChipReading {
    /** Route the figures belong to. */
    provider: string;
    /** Name to show, as the route states it. */
    label: string;
    /** Every window that states a figure, in the order the route stated them. */
    windows: readonly UsageWindow[];
    /** The window worth showing in the compact line: the one closest to its limit. */
    window: UsageWindow;
    /** Plan name, when the provider stated one. */
    plan?: string;
}
/** One currency's balance, as the host's account controller reports it. */
export interface ChipBalance {
    /** The wallet's currency. */
    currency: string;
    /** Remaining balance, as a decimal string. */
    balance: string;
    /** Whether this is a bonus / promotional wallet rather than topped-up credit. */
    bonus: boolean;
}
/**
 * Reduce the node half's balance payload to the wallets the chip shows.
 *
 * Bonus wallets are shown after the topped-up ones, so a reader sees their own credit
 * first and any promotional credit as a secondary figure. A bonus wallet that holds
 * nothing is dropped rather than shown as `+¥0`: the platform reports one for every
 * currency whether or not there is a bonus, and an empty one is noise, not information.
 *
 * @param value - the node half's balance, or undefined when there is none.
 * @returns the wallets, topped-up first then bonus, or undefined when nothing is usable.
 */
export declare function balanceOf(value: {
    wallets?: readonly {
        currency: string;
        balance: string;
    }[];
    bonusWallets?: readonly {
        currency: string;
        balance: string;
    }[];
} | null | undefined): readonly ChipBalance[] | undefined;
/**
 * Format one wallet for the chip's compact line.
 *
 * A balance is money, not a percentage, so it renders as `¥6.89` rather than a meter;
 * the currency prefix keeps two currencies from being mistaken for one sum. The amount
 * is rounded to two decimal places (a balance reads to the cent, and a figure like
 * `1.1` for `1.10` would mislead) and always carries both places.
 *
 * @param wallet - the wallet to show.
 * @returns the short form, e.g. `¥6.89` or `+$2.00` for a bonus wallet.
 */
export declare function formatBalance(wallet: ChipBalance): string;
/**
 * The short form of a route's name.
 *
 * @param provider - the route id.
 * @param label - the route's own display name.
 * @returns an abbreviation that still identifies the route.
 */
export declare function shortLabel(provider: string, label: string): string;
/**
 * The short form of a window's name.
 *
 * @param label - the window's own name, e.g. `weekly`.
 * @returns an abbreviation, or the label itself when it is already short.
 */
export declare function shortWindow(label: string): string;
/**
 * The name a window reads as in the panel.
 *
 * A provider's window label is a wire id — `gemini_weekly` is not something to put in front
 * of a reader — so a label this build has copy for is named in the reader's language, and
 * anything else keeps the name its provider gave it.
 *
 * @param t - copy for the active locale.
 * @param label - the window's own label.
 * @returns the display name.
 */
export declare function windowName(t: UsageChipInjected['t'], label: string): string;
/**
 * How one window reads as a figure.
 *
 * A credit balance (WorkBuddy's Credits, a wallet) is money: it reads as the raw
 * `used`/`limit` count. A quota is a share of an allowance: it reads as the spent
 * percent. The two are told apart by what the window is called — a credit or wallet
 * names money, a time window names an allowance — because both state the same fields.
 *
 * @param window - the metered window.
 * @returns the display figure.
 */
export declare function windowAmount(window: Pick<UsageWindow, 'label' | 'used' | 'limit' | 'usedPercent'>): string;
/**
 * Pick the window a single line should show.
 *
 * A plan can meter several windows at once (a rolling five-hour one and a weekly one).
 * The most constrained is the one that will stop work soonest, so it earns the space;
 * ties fall to the earlier window, which is the order the providers state them in.
 *
 * @param windows - the route's metered windows.
 * @returns the window to show, or undefined when none states a figure.
 */
export declare function pickWindow(windows: readonly UsageWindow[]): UsageWindow | undefined;
/**
 * Reduce one route's usage to what the chip renders.
 *
 * @param provider - the route the figures came from.
 * @param label - the route's display name.
 * @param usage - the accounts the route answered for.
 * @returns the reading, or undefined when the route stated no figure.
 */
/** What one account's allowance reduces to for the chip. */
export interface AccountReading {
    /** Which account these figures belong to. */
    account: string;
    /** Plan name, when the provider stated one. */
    plan?: string;
    /** Every metered window, in the order the route stated them. */
    windows: readonly UsageWindow[];
}
/**
 * Reduce a route's per-account usage to one reading per account.
 *
 * A provider can hold several accounts (two Antigravity sign-ins, say), and the chip
 * shows each one's allowance on its own line, the way the settings cards do — a merged
 * figure would hide that one account is exhausted while the other is free.
 *
 * @param usage - every account the route answered for.
 * @returns one reading per account that states a figure, in the route's order.
 */
/**
 * Reduce a route's per-account usage to one reading per account.
 *
 * A provider can hold several accounts (two Antigravity sign-ins, say). Every account's
 * windows are kept as the route stated them — a percentage-metered quota and a credit
 * balance (`credits`) alike — so the per-account panel shows the real figures, and the
 * compact line's merge decides which to integrate.
 *
 * @param usage - every account the route answered for.
 * @returns one reading per account that states any window, in the route's order.
 */
export declare function accountReadingsOf(usage: readonly ProviderUsage[]): readonly AccountReading[];
/**
 * Merge every account's percentage-metered windows into one figure per label.
 *
 * The chip's compact line integrates the whole pool: two accounts at 50 percent read as
 * one window at 50 percent (the same allowance spent twice as wide), not two lines. A
 * credit balance (`credits`) is money, not a share of a shared allowance, so it is not
 * merged — it stays on the per-account panel, where it reads correctly. Only windows
 * that state a `usedPercent` (a share of an allowance) merge.
 *
 * @param readings - one per account, from {@link accountReadingsOf}.
 * @returns one merged window per percentage-metered label, most-spent first.
 */
export declare function mergeAccountWindows(readings: readonly AccountReading[]): readonly UsageWindow[];
/**
 * Reduce one route's usage to the single line the compact chip renders.
 *
 * Kept for the balance-free providers that still show one merged line.
 *
 * @param provider - the route id.
 * @param label - the route's display name.
 * @param usage - the accounts the route answered for.
 * @returns the first account's reading, or undefined when none states a figure.
 */
export declare function readingOf(provider: string, label: string, usage: readonly ProviderUsage[]): ChipReading | undefined;
/**
 * Format a window's reset the way the settings cards do.
 *
 * The rule is shared with the cards on purpose: a figure reading `6d20h` here and
 * something else there would be two answers to one question.
 *
 * @param at - epoch milliseconds, or undefined when the provider states none.
 * @param now - the instant to measure from.
 * @returns a short phrase, or undefined when there is no reset to show.
 */
export declare function formatReset(at: number | undefined, now: number): string | undefined;
