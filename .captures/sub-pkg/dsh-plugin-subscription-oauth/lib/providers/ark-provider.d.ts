/**
 * The `ark` provider route's wiring: ZCode-style SSO sign-in, the control plane that
 * mints the plan's API key, the model catalog, and the Settings card facts.
 *
 * Ark splits its API in two, and the route has to satisfy both:
 *
 * - the **data plane** (`ark.cn-beijing.volces.com`) serves the models and accepts
 *   only an ordinary **API key** as a plain bearer — it refuses STS credentials
 *   outright;
 * - the **control plane** (`ark.cn-beijing.volcengineapi.com`) is signed with the
 *   temporary **STS** credentials the SSO token response carries, and is where the
 *   plan's API key and its allowance come from.
 *
 * So a sign-in produces both: the key for requests, the STS set for quota.
 *
 * @module dsh-plugin-subscription-oauth/providers/ark-provider
 */
import type { ProviderRoute } from '../auth/registry.js';
import type { ModelPreferences } from '../settings.js';
import type { FetchFn } from '../http.js';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
/** Everything the Ark route needs from the plugin configuration. */
export interface ArkProviderOptions {
    credentialFile: string;
    apiBase?: string;
    discovery: boolean;
    streamIdleTimeoutMs: number;
    defaultModel?: string;
    extraModels?: readonly string[];
    /**
     * The endpoint's last live listing, from this plugin's cache.
     *
     * The cached table is what the picker shows before any request: the endpoint's own
     * answer from the last read, so a release does not need a plugin update and a refresh
     * does not need a request.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
    preferences: ModelPreferences;
    resolveAttachments?: () => AttachmentStore | undefined;
    onCatalogChanged?: () => void;
    onWarn?: (message: string) => void;
    fetchFn?: FetchFn;
}
/**
 * Build the `ark` route.
 *
 * @param options - resolved endpoints, paths, and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export declare function createArkProvider(options: ArkProviderOptions): ProviderRoute;
