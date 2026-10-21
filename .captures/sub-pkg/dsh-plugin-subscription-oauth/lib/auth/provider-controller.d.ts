/**
 * The per-provider login/lifecycle controller: the single place one route's
 * Settings state is derived from.
 *
 * A login is completed by the *browser*, so `login()` returns as soon as a
 * prompt exists and the attempt settles in the background; every later
 * `status()` call reports whether it is still pending, and a failure is recorded
 * so the card can show why instead of silently returning to "logged out".
 *
 * Everything provider-specific arrives through {@link ProviderSpec}, the token
 * manager, and the login flow, so the same controller serves a device-flow
 * provider and a loopback-redirect provider.
 *
 * @module dsh-plugin-subscription-oauth/auth/provider-controller
 */
import type { DetailRow, LoginPrompt, ProviderStatus, ProviderUsage, UsageWindow } from '../protocol.js';
import type { CatalogEntry } from '../providers/catalog.js';
import type { ModelPreferences } from '../settings.js';
import type { AuthFlow } from './flow.js';
import type { SessionBase, TokenStore } from './store.js';
import type { TokenManager } from './tokens.js';
/** A login that has just started. */
export interface ProviderLoginStart {
    prompt: LoginPrompt;
    status: ProviderStatus;
}
/** A completed token refresh. */
export interface ProviderRefreshResult {
    account: string;
    expiresAt: number;
    status: ProviderStatus;
}
/** A completed usage read, carrying this route's own status. */
export interface ProviderUsageResult {
    usage: readonly ProviderUsage[];
    error?: string;
    status: ProviderStatus;
}
/** The provider-owned half of one route. */
export interface ProviderSpec<S extends SessionBase> {
    /** Provider route key, e.g. `kimi`. */
    readonly id: string;
    readonly displayName: string;
    /** Where a user manages the underlying subscription. */
    readonly codeUrl?: string;
    /** Absolute path of the credential file, for the manage dialog. */
    readonly credentialFile: string;
    /** Whether the live model list is consulted. */
    readonly discovery: boolean;
    /**
     * What this route itself serves: its shipped and live-discovered models, without
     * the ids the user added by hand.
     *
     * The pair with {@link ProviderSpec.catalog} is what separates a synced model from
     * a manual entry once the adapter merges both into one list.
     */
    readonly catalogOwn?: () => readonly CatalogEntry[];
    /** Context advertised for a model id the user added by hand. */
    readonly fallbackContextWindow?: number;
    /** Model the picker preselects. */
    readonly defaultModel?: string;
    /** The curated catalog, live ids included once discovery has run. */
    catalog(): readonly CatalogEntry[];
    /** Live model ids, or undefined when unavailable. */
    discover(accessToken: string): Promise<readonly string[] | undefined>;
    /** Connection facts for the manage dialog. */
    details(): readonly DetailRow[];
    /** Called with the live ids once discovery succeeded, so the route re-announces. */
    applyDiscovery?(ids: readonly string[]): void;
    /** Read one account's plan and metered allowances, when the provider states them. */
    usage?(accessToken: string, account: string): Promise<ProviderUsageRead | undefined>;
    /** The fast/lightspeed variant wire id for a base model, when the provider reported one. */
    fastVariant?(baseId: string): string | undefined;
}
/** What a provider's usage read returns, before the controller stamps it. */
export interface ProviderUsageRead {
    plan?: string;
    renewsAt?: number;
    windows: readonly UsageWindow[];
    /** Daily check-in state, when the provider states one. */
    checkin?: {
        active: boolean;
        todayCheckedIn: boolean;
        streakDays: number;
        todayCredit: number;
    };
    /**
     * Why these figures are not the provider's primary accounting.
     *
     * A route that had to publish a substitute says so here, and the note travels
     * with the figures so the page never presents the substitute as the plan's own.
     */
    usageError?: string;
}
/** Constructor dependencies for {@link ProviderController}. */
export interface ProviderControllerOptions<S extends SessionBase> {
    readonly spec: ProviderSpec<S>;
    readonly store: TokenStore<S>;
    readonly tokens: TokenManager<S>;
    readonly flow: AuthFlow<S>;
    /** Model-visibility preferences, shared across providers. */
    readonly preferences: ModelPreferences;
    /** Invoked when the catalog or its visibility changed, so the picker re-queries. */
    readonly onCatalogChanged?: () => void;
    readonly onWarn?: (message: string) => void;
}
export declare class ProviderController<S extends SessionBase> {
    private readonly options;
    /** Live catalog ids, once discovery succeeded this session. */
    private discovered;
    /** Last background failure a user should see. */
    private lastError;
    /** Most recent eager-refresh failure, cleared once a refresh succeeds. */
    private lastRefreshError;
    /**
     * Usage figures recently read, keyed by account.
     *
     * The entry is a short-lived coalescing cache, not a stored reading: an
     * allowance moves as the account is used, so a figure kept for the whole
     * session would keep answering with the state of the first read and the card
     * would look like it never noticed an update.
     */
    private readonly usageByAccount;
    /** Why the last usage read failed, when it did. */
    private usageError;
    /** Accounts whose grant the provider refused, so only a new login helps. */
    private readonly refused;
    constructor(options: ProviderControllerOptions<S>);
    /** Provider route key. */
    get id(): string;
    /** Human name for selectors and diagnostics. */
    get displayName(): string;
    /** Absolute path of the credential file this route reads. */
    get credentialFile(): string;
    /** Current state of the route. */
    status(): Promise<ProviderStatus>;
    /**
     * Refresh the default account's token when it is at or near its margin.
     *
     * Best-effort by design: a transient failure leaves the stored session alone
     * and the next model call retries. A permanent failure is recorded so the card
     * can tell the user to sign in again instead of silently showing a dead token.
     */
    private refreshIfLapsing;
    /** The catalog as the page lists it, in the user's order. */
    private modelSummaries;
    /** This route's catalog after the user's order and visibility are applied. */
    private resolveOrdered;
    /**
     * Begin an interactive login, returning the prompt to display.
     *
     * The returned status is already `busy`, because the attempt is registered
     * before this resolves; the login itself settles in the background.
     *
     * @param account - existing account key to replace, for a re-login.
     * @returns the pending prompt plus a fresh status.
     */
    login(account?: string): Promise<ProviderLoginStart>;
    /** Persist the account once the browser approves, or record why it did not. */
    private completeLogin;
    /** Abandon the pending authorization, when one exists. */
    cancel(): Promise<ProviderStatus>;
    /** Forget one account (the default one when none is named). */
    logout(account?: string): Promise<ProviderStatus>;
    /** Promote one account to default. */
    setDefault(account: string): Promise<ProviderStatus>;
    /**
     * Refresh one account's access token now.
     *
     * @param account - account to refresh; the default one when omitted.
     * @returns the refreshed expiry and a fresh status.
     * @throws {ProviderError} with `REAUTH_REQUIRED` when the account must sign in again.
     */
    refresh(account?: string): Promise<ProviderRefreshResult>;
    /**
     * Re-read the endpoint's live model list.
     *
     * @param force - fetch again even when a list was already read this session.
     * @returns a fresh status.
     */
    /**
     * Re-read the endpoint's live model list.
     *
     * @param force - fetch again even when a list was already read this session.
     * @returns a fresh status.
     */
    discover(force?: boolean): Promise<ProviderStatus>;
    /**
     * Drop hand-added ids the route's own catalog now covers.
     *
     * Discovery is the authority on what the endpoint serves, so once it reports an
     * id the manual entry has done its job.
     *
     * @param ids - the ids discovery reported.
     */
    private forgetDiscovered;
    /**
     * Set whether one model appears in the session model picker.
     *
     * The route is re-announced so the picker re-queries the catalog; a model
     * hidden here stays resolvable, so a session that already selected it keeps
     * working.
     *
     * @param id - model id as the catalog reports it.
     * @param visible - whether the picker should offer it.
     * @returns a fresh status.
     * @throws {ProviderError} with `MODEL_NOT_FOUND` when the id is not in the catalog.
     */
    setModelVisible(id: string, visible: boolean): Promise<ProviderStatus>;
    /**
     * Replace this route's model order with the arrangement the user dropped into
     * place.
     *
     * The page sends the complete list it displays, so a drop is one atomic
     * statement of intent rather than a sequence of moves. Ids are validated
     * against the catalog first: a stale page could otherwise persist a list
     * naming a model this build no longer has.
     *
     * @param ids - every catalog model id, in the order the user arranged them.
     * @returns a fresh status.
     * @throws {ProviderError} when an id is not a rankable catalog model.
     */
    setModelOrder(ids: readonly string[]): Promise<ProviderStatus>;
    /**
     * Add one model id by hand, for a model this build's catalog does not know yet.
     *
     * The endpoint is the only authority on which ids it serves, so the id is
     * checked for shape rather than against a list: a model released after this
     * build must be usable without waiting for a plugin update.
     *
     * @param id - the model id to publish on this route.
     * @returns a fresh status.
     * @throws {ProviderError} when the id is unusable or already known.
     */
    addModel(id: string): Promise<ProviderStatus>;
    /**
     * Forget one hand-added model id.
     * @param id - the id to drop from this route.
     * @returns a fresh status.
     */
    removeModel(id: string): Promise<ProviderStatus>;
    /**
     * Read every account's plan and metered allowances.
     *
     * A provider that states no usage answers with an empty list rather than an
     * error, and one account's failure never hides the others' figures.
     *
     * @param force - read again even when figures were already read this session.
     * @returns the figures plus a fresh status.
     */
    usage(force?: boolean): Promise<ProviderUsageResult>;
    /** Re-read the credential file and the preferences, after an external change. */
    reload(): Promise<ProviderStatus>;
}
/** Re-exported so provider modules need one import for their catalog types. */
export type { CatalogEntry, UsageWindow };
