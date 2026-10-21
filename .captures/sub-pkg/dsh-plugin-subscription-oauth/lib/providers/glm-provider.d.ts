/**
 * The `glm` provider route's wiring: credential store, ZCode OAuth login, model
 * catalog, and its Settings card facts.
 *
 * @module dsh-plugin-subscription-oauth/providers/glm-provider
 */
import type { ProviderRoute } from '../auth/registry.js';
import type { ModelPreferences } from '../settings.js';
import type { FetchFn } from '../http.js';
import type { AttachmentStore } from '@deepseek-ai/dsh-attachment';
/** Everything the GLM route needs from the plugin configuration. */
export interface GlmProviderOptions {
    /** Absolute path of `auth.json`. */
    credentialFile: string;
    /** Request base; `<base>/responses` is the endpoint. */
    apiBase?: string;
    /** Consult the endpoint's live model list. */
    discovery: boolean;
    /** Abandon a stream after this much silence. */
    streamIdleTimeoutMs: number;
    /** Model the picker preselects. */
    defaultModel?: string;
    /** Extra model ids published without shipped metadata. */
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
 * Build the `glm` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved endpoints, paths, and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export declare function createGlmProvider(options: GlmProviderOptions): ProviderRoute;
