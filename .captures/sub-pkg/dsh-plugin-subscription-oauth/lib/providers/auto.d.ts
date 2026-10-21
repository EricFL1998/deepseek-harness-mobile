/**
 * The `auto` provider route: one model that stands in for the user's arrangement
 * of every enabled model, across every other route this plugin serves.
 *
 * `auto` is a provider rather than a model inside each route because its list is
 * not any one route's catalog: the user mixes Kimi, Grok and Antigravity models
 * in a single order, and switches individual ones out of the fallback without
 * hiding them from the picker. A request for `auto` is delegated, in that order,
 * to the route that owns each model — so `auto` holds no credentials of its own
 * and its card has nothing to sign into.
 *
 * @module dsh-plugin-subscription-oauth/providers/auto
 */
import { LlmAdapter } from '@deepseek-ai/dsh-llm';
import type { GenerateOptions, LlmModelInfo, LlmProviderInfo, LlmResolvedModelInfo, ModelModality, StreamChunk } from '@deepseek-ai/dsh-llm';
import type { ModelSummary } from '../protocol.js';
import type { ModelPreferences } from '../settings.js';
import type { ProviderRoute, SubscriptionRegistry } from '../auth/registry.js';
/** Route key the `auto` provider registers under. */
export declare const AUTO_PROVIDER_ID = "auto";
/** Display name for the picker group and the Settings card. */
export declare const AUTO_PROVIDER_NAME = "Auto";
/** Context advertised before any candidate is known. */
export declare const AUTO_FALLBACK_CONTEXT_WINDOW = 262144;
/** One candidate: a model of one route. */
export interface AutoCandidate {
    /** Owning route key. */
    provider: string;
    /** Model id within that route. */
    model: string;
    /** Display name, for the Settings card. */
    name: string;
    /** Context window the owning route advertises. */
    contextWindow: number;
    /** Reasoning efforts the owning route advertises, in display order. */
    reasoningEfforts?: readonly string[];
    /** Input modalities the owning route advertises. */
    inputModalities?: readonly ModelModality[];
}
/** The key `auto` stores for one candidate, stable across restarts. */
export declare function autoKey(provider: string, model: string): string;
/** Split one `provider/model` key back into its parts. */
export declare function parseAutoKey(key: string): {
    provider: string;
    model: string;
} | undefined;
/**
 * The harness's own provider roster.
 *
 * `auto` spans everything the session can pick, not only the routes this plugin
 * registered: the built-in DeepSeek providers and any provider the user added are
 * models Auto is expected to fall back to. The runtime offers exactly the three
 * facts that needs — which providers exist, what each one serves, and the call
 * entry point that selects the owning adapter — so it is injected as a port rather
 * than reached for directly.
 */
export interface AutoHost {
    /** Every provider the harness serves, in picker order. */
    providers(): readonly {
        id: string;
        name: string;
    }[];
    /** Models one provider currently offers. */
    models(provider: string): Promise<readonly {
        id: string;
        name?: string;
    }[]>;
    /** One model's context window, reasoning ladder and input kinds, when stated. */
    resolve(provider: string, model: string): Promise<{
        contextWindow?: number;
        reasoningEfforts?: readonly string[];
        inputModalities?: readonly ModelModality[];
    }>;
    /** Run one call, letting the harness select that provider's adapter. */
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
}
/** Everything the `auto` route needs. */
export interface AutoProviderOptions {
    /** Shared model preferences, where `auto`'s own order and switches live. */
    preferences: ModelPreferences;
    /** Every other route, read per call so registration order stays free. */
    registry: SubscriptionRegistry;
    /**
     * The harness's own roster, when the route is mounted in a running harness.
     *
     * Present, this is what `auto` enumerates — it already contains this plugin's
     * routes as well, so enumerating both would list every model twice. Absent (a
     * unit test with no runtime), the registry alone is used.
     */
    host?: AutoHost;
    onWarn?: (message: string) => void;
    /** Invoked when the list changed, so the picker re-queries. */
    onCatalogChanged?: () => void;
}
/**
 * The `auto` provider's wire adapter.
 *
 * Its own list is the single `auto` entry; every request is delegated to the
 * route that owns the chosen model.
 */
export declare class AutoAdapter extends LlmAdapter {
    private readonly options;
    /** The route this adapter serves, built alongside it. */
    route?: ProviderRoute;
    constructor(options: AutoProviderOptions);
    providerInfo(provider: string): LlmProviderInfo;
    providerRetryPolicy(): undefined;
    listModels(_provider: string): Promise<readonly LlmModelInfo[]>;
    resolveModel(_provider: string, model: string): Promise<LlmResolvedModelInfo>;
    stream(options: GenerateOptions): AsyncIterable<StreamChunk>;
    /**
     * The candidates `auto` will try, in order.
     *
     * Every model a route currently offers the picker is a candidate — that is what
     * "enabled" means here — minus the ones switched out of Auto, arranged by the
     * user's own order and then by each route's catalog order.
     */
    chain(): Promise<readonly AutoCandidate[]>;
    /**
     * Every candidate in the user's order, switched-out ones included.
     *
     * The card renders these rows and the drop sends back their ids, so the order
     * applied here is the one the page must show: reading the raw candidate list
     * instead would persist a reorder and then redraw the old arrangement.
     */
    private arranged;
    /**
     * Every model the picker currently offers, from every provider.
     *
     * In a running harness that means the whole roster, which is where the built-in
     * DeepSeek providers and any provider the user added come from. Without a host —
     * a unit test — this plugin's own routes are the roster.
     */
    private candidates;
    /** The same list built from this plugin's own routes, for a host-less build. */
    private registryCandidates;
    /** The Settings card's rows: every candidate, switched-out ones included. */
    summaries(): Promise<ModelSummary[]>;
}
/**
 * Build the `auto` route: its adapter and a Settings façade over shared
 * preferences.
 *
 * `auto` has no credentials, so the credential-shaped endpoints are refused here
 * rather than half-answered: the page hides their controls for a route that
 * reports itself as not connectable.
 *
 * @param options - shared preferences and the registry it stands in for.
 * @returns the route, ready to register and to serve over RPC.
 */
export declare function createAutoProvider(options: AutoProviderOptions): ProviderRoute;
