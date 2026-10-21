/**
 * The `mimo` provider route's wiring: credential store, Xiaomi QR/password
 * login, model catalog (MiMo's own listing, read once and cached), and its
 * Settings card facts.
 *
 * @module dsh-plugin-subscription-oauth/providers/mimo-provider
 */
import type { ProviderRoute } from '../auth/registry.js';
import type { ModelPreferences } from '../settings.js';
import type { FetchFn } from '../http.js';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
/** Everything the MiMo route needs from the plugin configuration. */
export interface MimoProviderOptions {
    /** Absolute path of `auth.json`. */
    credentialFile: string;
    /** Consult MiMo's own live model listing. Defaults to on. */
    discovery?: boolean;
    /** Abandon a stream after this much silence. */
    streamIdleTimeoutMs: number;
    /** Model the picker preselects. */
    defaultModel?: string;
    /**
     * MiMo's last live listing, from this plugin's cache.
     *
     * The cached table is what the picker shows before any request: the endpoint's own
     * answer from the last read, so a release does not need a plugin update and a refresh
     * does not need a request. Absent, the built-in roster is what the picker shows.
     */
    cachedListing?: () => Promise<unknown>;
    /** Record what a live listing just produced, replacing the cached table. */
    saveListing?: (payload: unknown) => Promise<void>;
    /** Shared model-visibility preferences. */
    preferences: ModelPreferences;
    /** Resolve the attachment service per request. */
    resolveAttachments?: () => AttachmentStore | undefined;
    /** Invoked when the catalog or its visibility changed. */
    onCatalogChanged?: () => void;
    onWarn?: (message: string) => void;
    /** Fetch implementation; defaults to the proxy-aware fetch. */
    fetchFn?: FetchFn;
}
/**
 * Build the `mimo` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved endpoints, paths, and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export declare function createMimoProvider(options: MimoProviderOptions): ProviderRoute;
