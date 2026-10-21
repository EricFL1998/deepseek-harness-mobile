/**
 * Wire contract shared by the plugin's node half (the RPC routes) and its
 * browser half (the Settings page).
 *
 * Only this module crosses the boundary: the client bundle imports the types
 * and the channel name, and every call travels as one JSON envelope over a
 * `POST /api/<prefix><endpoint>` route. One status document describes every
 * provider route the plugin serves, so the page renders one card per entry.
 *
 * @module dsh-plugin-subscription-oauth/protocol
 */
/** Prefix of every RPC method this plugin serves. */
export declare const RPC_PREFIX = "subscription-oauth.";
/**
 * The settings namespace the Models page keys this plugin's provider cards by.
 *
 * The settings service projects a plugin's `Config` schema under the Cordis row
 * id that mounted it (`settings/src/index.ts`: `ns = entry.options.id`), and the
 * Models section dispatches `settings.models.provider-card` with that same
 * namespace as `entryKey`. This constant therefore names the row id declared in
 * `cordis.patch.yml`; the two must stay in step, or the Models page renders the
 * routes with no subscription block (the standalone Subscriptions page is
 * unaffected).
 */
export declare const SETTINGS_NS = "llm-subscription-oauth";
/** Endpoints the Settings page may call. */
export declare const RPC_ENDPOINTS: readonly ["status", "login", "cancel", "logout", "setDefault", "refresh", "discover", "setModelVisible", "setModelOrder", "addModel", "removeModel", "usage", "reload", "auto-state", "setAutoOn", "checkin", "tasks", "deepseek-balance", "ccswitch-balance", "model-variants"];
/** One endpoint name. */
export type RpcEndpoint = typeof RPC_ENDPOINTS[number];
/** Client → host envelope. */
export interface RpcRequest {
    type: 'client-request';
    rpcId: string;
    method: string;
    payload: unknown;
}
/** Host → client envelope, carrying either the value or the failure. */
export interface RpcResponse {
    type: 'server-response';
    rpcId: string;
    result: RpcResult;
}
/** Discriminated result inside {@link RpcResponse}. */
export type RpcResult = {
    ok: true;
    value: unknown;
} | {
    ok: false;
    error: RpcError;
};
/** Failure detail, with the provider-neutral code when one applies. */
export interface RpcError {
    code: string;
    message: string;
    /**
     * Carrier-owned structured detail. The Connection transport models a failure
     * as `{code, message, details}`, so an empty object travels here rather than
     * an absent field the client would have to repair.
     */
    details: Record<string, unknown>;
}
/**
 * One provider-owned connection fact the manage dialog shows.
 *
 * The key is a stable identifier (`oauthHost`, `apiBase`, …) that the browser
 * half localizes; the host never sends user-facing prose.
 */
export interface DetailRow {
    key: string;
    value: string;
}
/** How the browser should present one pending login. */
export type LoginMode = 
/** Open the URL in a new tab; the redirect completes the login. */
'open-url'
/** Show the code (and a link) while the host polls the token endpoint. */
 | 'device-code';
/** What the Settings page has to show to finish a login. */
export interface LoginPrompt {
    /** URL the user opens (or that was opened for them). */
    readonly url: string;
    /** Code the user confirms in the browser; device flows only. */
    readonly userCode?: string;
    /** Epoch milliseconds after which the attempt lapses. */
    readonly expiresAt: number;
    readonly mode: LoginMode;
}
/** One signed-in account as the page displays it. */
export interface AccountStatus {
    /** Account key the store is keyed by. */
    account: string;
    /** Display label (email, user id, or the key's digest). */
    label: string;
    /** Epoch milliseconds when the access token lapses. */
    expiresAt: number;
    /** Whether the access token is already past its expiry. */
    expired: boolean;
    /** Whether this account serves requests by default. */
    isDefault: boolean;
    /**
     * Whether the provider refused this account's grant outright.
     *
     * A refresh answered with `invalid_grant` means the authorization is gone — the
     * stored token can never be renewed — so the page asks for a new sign-in rather
     * than reporting the account as connected.
     */
    needsLogin?: boolean;
}
/** One selectable model, as the page lists it. */
export interface ModelSummary {
    id: string;
    name: string;
    description?: string;
    contextWindow: number;
    inputModalities?: readonly string[];
    defaultEffort?: string;
    /** Whether the session model picker offers this model. */
    visible: boolean;
    /**
     * Route that owns this model, when the listing spans routes.
     *
     * The uto provider mixes models from every route, so its rows name the
     * owning route; a single-route catalog leaves it out.
     */
    source?: string;
    /** Whether the user added this id by hand, so it can be removed again. */
    userAdded?: boolean;
    /**
     * Whether this entry is fixed at the head of the list.
     *
     * The synthetic `auto` entry follows the user's own order, so it cannot be
     * hidden or moved; the page renders it without those controls.
     */
    pinned?: boolean;
    /** Fast/lightspeed variant wire id, when the provider reported one for this base. */
    fastVariant?: string;
}
/** One metered allowance of a subscription plan. */
export interface UsageWindow {
    /** What the window meters, e.g. `weekly`, `5h`, `credits`. */
    label: string;
    /** How much of the allowance is spent, when the provider states it. */
    usedPercent?: number;
    /** Amount already spent, when the provider states an absolute figure. */
    used?: number;
    /** Total allowance, when the provider states one. */
    limit?: number;
    /** Epoch milliseconds when the window resets, when the provider states it. */
    resetsAt?: number;
}
/** One account's plan and metered allowances. */
export interface ProviderUsage {
    /** Account the figures belong to. */
    account: string;
    /** Plan name or tier, when the provider states one. */
    plan?: string;
    /** Whether the plan renews on a date the provider states. */
    renewsAt?: number;
    /** Metered allowances, most relevant first. */
    windows: readonly UsageWindow[];
    /** Daily check-in state, when the provider states one. */
    checkin?: {
        active: boolean;
        todayCheckedIn: boolean;
        streakDays: number;
        todayCredit: number;
    };
    /** Epoch milliseconds these figures were read. */
    fetchedAt: number;
    /**
     * Whether these figures are the route's substitute rather than its own accounting.
     *
     * A substitute is shown when the primary source cannot answer, and it is never
     * reused as if it were a reading: the next request asks again.
     */
    substitute?: boolean;
}
/** Result of reading one route's usage. */
export interface UsageResult {
    provider: string;
    /** Usage per account the endpoint could answer for. */
    usage: readonly ProviderUsage[];
    /** Why an account's usage is missing, when the read failed. */
    error?: string;
    status: SubscriptionStatus;
}
/** The full state of one provider route. */
export interface ProviderStatus {
    /** Provider route key, e.g. `kimi`. */
    provider: string;
    displayName: string;
    /** Where a user manages the underlying subscription. */
    codeUrl?: string;
    /** Whether at least one account is stored. */
    loggedIn: boolean;
    /**
     * Whether this route signs in at all.
     *
     * uto has no account of its own — it delegates to routes that do — so the
     * page hides the connect and manage controls for it.
     */
    connectable?: boolean;
    /** Whether a login attempt is being polled. */
    busy: boolean;
    /** The pending attempt, when `busy`. */
    pending?: LoginPrompt;
    accounts: AccountStatus[];
    /** Account key serving requests, when one does. */
    activeAccount?: string;
    /** Absolute path of the credential file, for support requests. */
    credentialFile: string;
    /** Whether the live model list is consulted. */
    discovery: boolean;
    /** Live catalog ids, when discovery has succeeded this session. */
    discovered?: readonly string[];
    models: readonly ModelSummary[];
    /** Model the picker preselects. */
    defaultModel?: string;
    /** Last usage figures read for this route, when they were requested. */
    usage?: readonly ProviderUsage[];
    /** Why the last usage read failed, when it did. */
    usageError?: string;
    /** Provider-owned connection facts shown in the manage dialog. */
    details: readonly DetailRow[];
    /** Last background failure (a login that could not complete). */
    lastError?: string;
}
/** Every provider route's state, in display order. */
export interface SubscriptionStatus {
    providers: ProviderStatus[];
}
/** Result of starting a login. */
export interface LoginStart {
    prompt: LoginPrompt;
    status: SubscriptionStatus;
}
/** Result of a token refresh. */
export interface RefreshResult {
    account: string;
    expiresAt: number;
    status: SubscriptionStatus;
}
/** Payload fields naming one provider route and optionally one account. */
export interface AccountRequest {
    provider: string;
    account?: string;
}
/** Payload of `setDefault`. */
export interface SetDefaultRequest {
    provider: string;
    account: string;
}
/** Payload of `discover`. */
export interface DiscoverRequest {
    provider: string;
    /** Ask the endpoint for its live model list even when one is cached. */
    force?: boolean;
}
/** Payload of `setModelVisible`. */
export interface SetModelVisibleRequest {
    provider: string;
    /** Model id as the catalog reports it. */
    id: string;
    /** Whether the session model picker should offer it. */
    visible: boolean;
}
/** Payload of `addModel` and `removeModel`. */
export interface ModelIdRequest {
    provider: string;
    /** Model id as the endpoint accepts it. */
    id: string;
}
/** Payload of `setModelOrder`. */
export interface SetModelOrderRequest {
    provider: string;
    /** Every catalog model id, in the order the user arranged them. */
    ids: readonly string[];
}
/** Payload naming only a provider route. */
export interface ProviderRequest {
    provider: string;
}
/** Map an endpoint to its payload and value types. */
export interface RpcMap {
    status: {
        payload: undefined;
        value: SubscriptionStatus;
    };
    login: {
        payload: AccountRequest;
        value: LoginStart;
    };
    cancel: {
        payload: ProviderRequest;
        value: SubscriptionStatus;
    };
    logout: {
        payload: AccountRequest;
        value: SubscriptionStatus;
    };
    setDefault: {
        payload: SetDefaultRequest;
        value: SubscriptionStatus;
    };
    refresh: {
        payload: AccountRequest;
        value: RefreshResult;
    };
    discover: {
        payload: DiscoverRequest;
        value: SubscriptionStatus;
    };
    setModelVisible: {
        payload: SetModelVisibleRequest;
        value: SubscriptionStatus;
    };
    setModelOrder: {
        payload: SetModelOrderRequest;
        value: SubscriptionStatus;
    };
    addModel: {
        payload: ModelIdRequest;
        value: SubscriptionStatus;
    };
    removeModel: {
        payload: ModelIdRequest;
        value: SubscriptionStatus;
    };
    usage: {
        payload: DiscoverRequest;
        value: UsageResult;
    };
    reload: {
        payload: undefined;
        value: SubscriptionStatus;
    };
    'auto-state': {
        payload: undefined;
        value: AutoStateResult;
    };
    setAutoOn: {
        payload: SetAutoOnRequest;
        value: AutoStateResult;
    };
    checkin: {
        payload: CheckinRequest;
        value: CheckinResult;
    };
    tasks: {
        payload: TasksRequest;
        value: TasksResult;
    };
    'deepseek-balance': {
        payload: DeepSeekBalanceRequest;
        value: DeepSeekBalanceResult;
    };
    'ccswitch-balance': {
        payload: CcSwitchBalanceRequest;
        value: CcSwitchBalanceResult;
    };
    'model-variants': {
        payload: ModelVariantsRequest;
        value: ModelVariantsResult;
    };
}
/** One currency's wallet balance, as the platform reports it. */
export interface WalletBalance {
    /** ISO currency the balance is denominated in. */
    currency: 'CNY' | 'USD' | string;
    /** Remaining balance, as a decimal string. */
    balance: string;
}
/** The DeepSeek account's wallet balances, normalised from the host's account service. */
export interface AccountBalance {
    /** Normal (topped-up) wallets, one per currency. */
    wallets: readonly WalletBalance[];
    /** Bonus / promotional wallets, one per currency. */
    bonusWallets: readonly WalletBalance[];
}
/** Result of reading the DeepSeek account's balance through the host. */
export type BalanceResult = 
/** The account answered with its balances. */
{
    ok: true;
    balance: AccountBalance;
}
/** The account is signed out, or the read failed. */
 | {
    ok: false;
};
/** One model's fast and image variants, as discovery reported them. */
export interface ModelVariants {
    /** Fast/lightspeed variant wire id, when the provider reported one. */
    fast?: string;
    /**
     * The base model the fast variant belongs to.
     *
     * Sent with `fast` so the picker button can switch back off when the selection is
     * already the fast variant, which is not itself a listed row.
     */
    fastBase?: string;
    /**
     * The reasoning rung that selects the fast variant.
     *
     * Each provider names its own variant (xAI `lightspeed`, Kimi `highspeed`), so the
     * rung is the provider's word — the picker reads it, and the stream maps it back to
     * the variant's wire id. Defaults to `fast`.
     */
    fastRung?: string;
    /** Image-generation model wire id, when the provider reported one. */
    image?: string;
    /**
     * The route has not read its model listing yet, so "no variant" is not yet an answer.
     *
     * A listing is read once per session and its variants are derived from it, so an early
     * read of this endpoint cannot tell "this model has no fast variant" from "the route has
     * not looked yet". Saying so lets the caller ask again later instead of hiding a control
     * that should be there — and, once a listing has been read, lets it stop asking.
     */
    pending?: boolean;
}
/** Result of `model-variants`: the variants for one model id. */
export type ModelVariantsResult = ModelVariants | undefined;
/** Payload of `model-variants`. */
export interface ModelVariantsRequest {
    provider: string;
    id: string;
}
/** Payload of `setAutoOn`: whether the `auto` provider is offered at all. */
export interface SetAutoOnRequest {
    enabled: boolean;
}
/** Result of `setAutoOn`: the `auto` provider's live enabled state. */
export interface AutoStateResult {
    enabled: boolean;
}
/** Payload of `checkin`: which account claims today's reward. */
export interface CheckinRequest {
    provider: string;
    account: string;
}
/** Result of `checkin`: the credit granted and the streak after claiming. */
export interface CheckinResult {
    credit: number;
    streakDays: number;
    already: boolean;
}
/** Payload of `tasks`: which account to read and auto-claim. */
export interface TasksRequest {
    provider: string;
    account: string;
    /** Claim every claimable reward; false only lists. */
    claim?: boolean;
}
/** One task's state for the card. */
export interface WorkBuddyTaskState {
    taskCode: string;
    title: string;
    credit: number;
    target: number;
    current: number;
    state: 'pending' | 'claimable' | 'claimed' | 'locked';
}
/** Result of `tasks`: the task list and what auto-claiming earned. */
export interface TasksResult {
    tasks: WorkBuddyTaskState[];
    claimed: number;
    claimable: number;
    creditEarned: number;
}
/** Payload of `deepseek-balance`. */
export interface DeepSeekBalanceRequest {
    /** Client metadata the host's account service expects. */
    version?: string;
    locale?: string;
    timezoneOffsetSeconds?: number;
}
/** One currency's wallet, as the host's account service reports it. */
export interface DeepSeekWallet {
    /** ISO currency the balance is denominated in. */
    currency: string;
    /** Remaining balance, as a decimal string. */
    balance: string;
}
/**
 * Result of `deepseek-balance`.
 *
 * `undefined` means the account is signed out or the read failed, so a chip stays
 * silent rather than showing a wrong figure.
 */
export type DeepSeekBalanceResult = {
    /** Normal (topped-up) wallets, one per currency. */
    wallets: readonly DeepSeekWallet[];
    /** Bonus / promotional wallets, one per currency. */
    bonusWallets: readonly DeepSeekWallet[];
} | undefined;
/** Payload of `ccswitch-balance`. */
export interface CcSwitchBalanceRequest {
    /** The provider whose balance to read; the server maps it to its credential reference. */
    provider: string;
}
/**
 * Result of `ccswitch-balance`.
 *
 * `undefined` means the provider did not answer with a balance — no key, a key it
 * rejects, or a body that is not the balance shape. A chip shows nothing rather than a
 * wrong figure, so this is deliberately not an error.
 */
export type CcSwitchBalanceResult = {
    /** The currency the balance is denominated in. */
    currency: string;
    /** The remaining balance, as a decimal string. */
    total: string;
    /** Whether the account reports itself as usable. */
    available: boolean;
} | undefined;
