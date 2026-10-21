/**
 * The `workbuddy` provider route's wiring (WorkBuddy China build):
 * credential store, browser sign-in, token refresh, and its Settings card facts.
 *
 * @module dsh-plugin-subscription-oauth/providers/workbuddy-provider
 */
import type { ProviderRoute } from '../auth/registry.js';
import type { ModelPreferences } from '../settings.js';
import type { FetchFn } from '../http.js';
/** Everything the WorkBuddy route needs from the plugin configuration. */
export interface WorkBuddyProviderOptions {
    /** Absolute path of `auth.json`. */
    credentialFile: string;
    /** Abandon a stream after this much silence. */
    streamIdleTimeoutMs: number;
    /**
     * Whether WorkBuddy's own console listing is consulted for the catalog.
     *
     * The listing is the authority on which models an account serves; switching this off
     * leaves the seed table in place and reads nothing.
     */
    discovery?: boolean;
    /** Model the picker preselects. */
    defaultModel?: string;
    /**
     * WorkBuddy's last listing, from this plugin's cache.
     *
     * The cached table is what the picker shows before any request: the console's own answer
     * from the last read, so a release does not need a plugin update and a refresh does not
     * need a request.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
    /** Shared model-visibility preferences. */
    preferences: ModelPreferences;
    /** Invoked when the catalog or its visibility changed. */
    onCatalogChanged?: () => void;
    onWarn?: (message: string) => void;
    /** Fetch implementation; defaults to the proxy-aware fetch. */
    fetchFn?: FetchFn;
}
/**
 * Build the `workbuddy` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved paths and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export declare function createWorkBuddyProvider(options: WorkBuddyProviderOptions): ProviderRoute;
