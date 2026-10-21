/**
 * The provider registry: one erased façade over every route's controller.
 *
 * The RPC layer talks to this object only, so it never has to know how many
 * providers exist, what their routes are called, or which login shape each one
 * uses. Each route's controller is generic over its own session type; this
 * façade is the single place that erasure happens.
 *
 * @module dsh-plugin-subscription-oauth/auth/registry
 */
import type { ModelPreferences } from '../settings.js';
import type { LoginStart, ModelVariants, ProviderStatus, RefreshResult, SubscriptionStatus, UsageResult } from '../protocol.js';
import type { LlmAdapter } from '@deepseek-ai/dsh-llm';
import type { ProviderLoginStart, ProviderRefreshResult, ProviderUsageResult } from './provider-controller.js';
/** Everything the registry needs from one provider route. */
export interface ProviderRoute {
    readonly id: string;
    readonly displayName: string;
    readonly credentialFile: string;
    /** The wire adapter this route registered with the harness. */
    readonly adapter: LlmAdapter;
    status(): Promise<ProviderStatus>;
    login(account?: string): Promise<ProviderLoginStart>;
    cancel(): Promise<ProviderStatus>;
    logout(account?: string): Promise<ProviderStatus>;
    setDefault(account: string): Promise<ProviderStatus>;
    refresh(account?: string): Promise<ProviderRefreshResult>;
    discover(force?: boolean): Promise<ProviderStatus>;
    setModelVisible(id: string, visible: boolean): Promise<ProviderStatus>;
    setModelOrder(ids: readonly string[]): Promise<ProviderStatus>;
    addModel(id: string): Promise<ProviderStatus>;
    removeModel(id: string): Promise<ProviderStatus>;
    usage(force?: boolean): Promise<ProviderUsageResult>;
    reload(): Promise<ProviderStatus>;
    /** Claim one account's daily check-in reward, when the route has one. */
    checkin?(account: string): Promise<{
        credit: number;
        streakDays: number;
        already: boolean;
    }>;
    /** Read growth tasks and optionally auto-claim, when the route has them. */
    tasks?(account: string, claim: boolean): Promise<{
        tasks: {
            taskCode: string;
            title: string;
            credit: number;
            target: number;
            current: number;
            state: 'pending' | 'claimable' | 'claimed' | 'locked';
        }[];
        claimed: number;
        claimable: number;
        creditEarned: number;
    }>;
    /** Fast and image variants for one model, when the route's discovery reported them. */
    modelVariants?(id: string): Promise<ModelVariants | undefined> | ModelVariants | undefined;
}
/**
 * Routes every Settings call to the provider it names.
 *
 * A provider key the plugin does not serve is a request error, not an internal
 * failure: the message names the routes that do exist.
 */
export declare class SubscriptionRegistry {
    private readonly preferences;
    private readonly byId;
    constructor(routes: readonly ProviderRoute[], preferences: ModelPreferences);
    /** Route keys this registry serves, in display order. */
    ids(): readonly string[];
    /** Every route, in registration order. */
    all(): readonly ProviderRoute[];
    /** One route by key, without the request-error path. */
    get(id: string): ProviderRoute | undefined;
    /**
     * Add one route.
     *
     * `auto` is built last on purpose: it stands in for the others, so it is
     * registered once their catalogs exist, without a circular construction order.
     * That is a construction detail, not a display one — the page shows `auto`
     * first, so the reading order is stated here rather than inherited from the
     * order the routes happened to be built in.
     *
     * @param route - the route to serve from now on.
     * @throws {ProviderError} when the key is already served.
     */
    register(route: ProviderRoute): void;
    /** One route, or a request error naming the known ones. */
    private route;
    /** Every provider's state, in registration order. */
    status(): Promise<SubscriptionStatus>;
    /** Start a login on one route. */
    login(provider: string, account?: string): Promise<LoginStart>;
    /** Abandon one route's pending login. */
    cancel(provider: string): Promise<SubscriptionStatus>;
    /** Forget one account of one route. */
    logout(provider: string, account?: string): Promise<SubscriptionStatus>;
    /** Promote one account to default. */
    setDefault(provider: string, account: string): Promise<SubscriptionStatus>;
    /** Refresh one account's token now. */
    refresh(provider: string, account?: string): Promise<RefreshResult>;
    /** Re-read one route's live model list. */
    discover(provider: string, force?: boolean): Promise<SubscriptionStatus>;
    /** Set one model's picker visibility. */
    setModelVisible(provider: string, id: string, visible: boolean): Promise<SubscriptionStatus>;
    /** Replace one route's model order with one dropped arrangement. */
    setModelOrder(provider: string, ids: readonly string[]): Promise<SubscriptionStatus>;
    /** Add one model id by hand to one route. */
    addModel(provider: string, id: string): Promise<SubscriptionStatus>;
    /** Forget one hand-added model id. */
    removeModel(provider: string, id: string): Promise<SubscriptionStatus>;
    /** Read one route's plan and metered allowances. */
    usage(provider: string, force?: boolean): Promise<UsageResult>;
    /** Re-read every credential file and the shared preferences. */
    reload(): Promise<SubscriptionStatus>;
    /** The fast and image variants for one model, as the route's discovery reported them. */
    modelVariants(provider: string, id: string): Promise<ModelVariants | undefined>;
    /** Whether the `auto` provider is offered; flips it. */
    autoState(): Promise<{
        enabled: boolean;
    }>;
    setAutoOn(enabled: boolean): Promise<{
        enabled: boolean;
    }>;
    /** Claim one account's daily check-in reward, when the route supports it. */
    checkin(provider: string, account: string): Promise<{
        credit: number;
        streakDays: number;
        already: boolean;
    }>;
    /** Read one account's growth tasks and optionally auto-claim the claimable rewards. */
    tasks(provider: string, account: string, claim: boolean): Promise<{
        tasks: {
            taskCode: string;
            title: string;
            credit: number;
            target: number;
            current: number;
            state: 'pending' | 'claimable' | 'claimed' | 'locked';
        }[];
        claimed: number;
        claimable: number;
        creditEarned: number;
    }>;
}
