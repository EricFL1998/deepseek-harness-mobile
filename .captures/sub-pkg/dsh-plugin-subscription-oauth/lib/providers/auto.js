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
import { EMPTY_RESPONSE_CODE, errorChain, LlmAdapter, LlmError, ReasoningEffortId, } from '@deepseek-ai/dsh-llm';
import { BAD_REQUEST, ProviderError } from '../errors.js';
/** Route key the `auto` provider registers under. */
export const AUTO_PROVIDER_ID = 'auto';
/** Display name for the picker group and the Settings card. */
export const AUTO_PROVIDER_NAME = 'Auto';
/** Context advertised before any candidate is known. */
export const AUTO_FALLBACK_CONTEXT_WINDOW = 262_144;
/** The key `auto` stores for one candidate, stable across restarts. */
export function autoKey(provider, model) {
    return `${provider}/${model}`;
}
/** Split one `provider/model` key back into its parts. */
export function parseAutoKey(key) {
    const at = key.indexOf('/');
    if (at <= 0 || at === key.length - 1)
        return undefined;
    return { provider: key.slice(0, at), model: key.slice(at + 1) };
}
/** One short reason for a log line. */
function reasonOf(error) {
    if (error instanceof LlmError)
        return `${error.failure.code}: ${error.message}`;
    return error instanceof Error ? error.message : String(error);
}
/** Whether a refusal means "this candidate cannot serve you now". */
function isCandidateFallbackWorthy(status) {
    // Gone or renamed model, exhausted allowance, rate limit, provider trouble.
    return status === 402 || status === 404 || status === 408 || status === 429 || status >= 500;
}
/**
 * The `auto` provider's wire adapter.
 *
 * Its own list is the single `auto` entry; every request is delegated to the
 * route that owns the chosen model.
 */
export class AutoAdapter extends LlmAdapter {
    options;
    /** The route this adapter serves, built alongside it. */
    route;
    constructor(options) {
        super();
        this.options = options;
    }
    providerInfo(provider) {
        return { id: provider, name: AUTO_PROVIDER_NAME };
    }
    providerRetryPolicy() {
        // The routes `auto` walks own their pacing; retrying here would multiply
        // their own retry-after handling.
        return undefined;
    }
    async listModels(_provider) {
        // A switched-off `auto` is not offered at all, so the picker shows the routes directly.
        if (!(await this.options.preferences.autoPreferences()).enabled)
            return [];
        const chain = await this.chain();
        // An `auto` with nothing to try is not offered at all, rather than offered
        // and guaranteed to fail.
        if (chain.length === 0)
            return [];
        return [{
                provider: AUTO_PROVIDER_ID,
                id: AUTO_PROVIDER_ID,
                name: AUTO_PROVIDER_NAME,
                description: `Tries ${String(chain.length)} enabled model(s) in your order, falling back when one is unavailable`,
            }];
    }
    async resolveModel(_provider, model) {
        if (model !== AUTO_PROVIDER_ID) {
            throw new ProviderError(`auto serves only its own entry, not ${model}`, BAD_REQUEST);
        }
        const first = (await this.chain())[0];
        const efforts = first?.reasoningEfforts ?? [];
        return {
            provider: AUTO_PROVIDER_ID,
            id: AUTO_PROVIDER_ID,
            name: AUTO_PROVIDER_NAME,
            context: { contextWindow: first?.contextWindow ?? AUTO_FALLBACK_CONTEXT_WINDOW },
            ...efforts.length === 0 ? {} : {
                reasoning: {
                    efforts: efforts.map((effort) => ({
                        id: ReasoningEffortId(effort),
                        name: effort[0]?.toUpperCase() + effort.slice(1),
                    })),
                },
            },
        };
    }
    async *stream(options) {
        if (options.model !== AUTO_PROVIDER_ID) {
            throw new ProviderError(`auto serves only its own entry, not ${options.model}`, BAD_REQUEST);
        }
        const chain = await this.chain();
        if (chain.length === 0) {
            throw new LlmError('Auto has no enabled model to try; turn one on in Settings → Subscriptions', EMPTY_RESPONSE_CODE);
        }
        let lastError;
        for (const [index, candidate] of chain.entries()) {
            const route = this.options.registry.get(candidate.provider);
            const next = chain[index + 1];
            // A chain may advance only while nothing has been produced: once a chunk
            // has been yielded the turn belongs to that model, and a later failure is
            // reported rather than papered over by a second model's text.
            let produced = false;
            try {
                // A route this plugin owns streams through its own adapter. Anything else
                // — a built-in provider, a provider the user added — goes through the
                // harness, which is the only thing that knows which adapter serves it.
                const source = route !== undefined
                    ? route.adapter.stream({ ...options, provider: candidate.provider, model: candidate.model })
                    : this.options.host?.stream({ ...options, provider: candidate.provider, model: candidate.model });
                if (source === undefined) {
                    lastError = new LlmError(`Auto refers to the unknown provider ${candidate.provider}`, EMPTY_RESPONSE_CODE);
                    if (next === undefined)
                        break;
                    continue;
                }
                for await (const chunk of source) {
                    produced = true;
                    yield chunk;
                }
                return;
            }
            catch (error) {
                lastError = error;
                if (produced || next === undefined || options.signal?.aborted === true)
                    throw error;
                this.options.onWarn?.(`auto: ${candidate.provider}/${candidate.model} failed (${reasonOf(error)}); trying ${next.provider}/${next.model}`);
            }
        }
        throw lastError ?? new LlmError('Auto served none of its models', EMPTY_RESPONSE_CODE);
    }
    /**
     * The candidates `auto` will try, in order.
     *
     * Every model a route currently offers the picker is a candidate — that is what
     * "enabled" means here — minus the ones switched out of Auto, arranged by the
     * user's own order and then by each route's catalog order.
     */
    async chain() {
        const { off } = await this.options.preferences.autoPreferences();
        const skipped = new Set(off);
        return (await this.arranged()).filter((candidate) => !skipped.has(autoKey(candidate.provider, candidate.model)));
    }
    /**
     * Every candidate in the user's order, switched-out ones included.
     *
     * The card renders these rows and the drop sends back their ids, so the order
     * applied here is the one the page must show: reading the raw candidate list
     * instead would persist a reorder and then redraw the old arrangement.
     */
    async arranged() {
        const { order } = await this.options.preferences.autoPreferences();
        const candidates = await this.candidates();
        const byKey = new Map(candidates.map((candidate) => [autoKey(candidate.provider, candidate.model), candidate]));
        const out = [];
        const seen = new Set();
        for (const key of order) {
            const candidate = byKey.get(key);
            if (candidate === undefined || seen.has(key))
                continue;
            seen.add(key);
            out.push(candidate);
        }
        for (const candidate of candidates) {
            const key = autoKey(candidate.provider, candidate.model);
            if (seen.has(key))
                continue;
            seen.add(key);
            out.push(candidate);
        }
        return out;
    }
    /**
     * Every model the picker currently offers, from every provider.
     *
     * In a running harness that means the whole roster, which is where the built-in
     * DeepSeek providers and any provider the user added come from. Without a host —
     * a unit test — this plugin's own routes are the roster.
     */
    async candidates() {
        const host = this.options.host;
        if (host === undefined)
            return await this.registryCandidates();
        const out = [];
        for (const entry of host.providers()) {
            // Auto cannot chain to itself; that would be a loop, not a fallback.
            if (entry.id === AUTO_PROVIDER_ID)
                continue;
            // A route this plugin owns but has not signed in offers nothing, even though
            // the harness still answers with its catalog: the harness has no notion of an
            // account, so Auto is the layer that has to ask. Host providers are left
            // alone — Auto has no way to judge somebody else's readiness, and dropping
            // them would silently empty the chain.
            const owned = this.options.registry.get(entry.id);
            if (owned !== undefined) {
                try {
                    if (!(await owned.status()).loggedIn)
                        continue;
                }
                catch (error) {
                    this.options.onWarn?.(`auto could not read ${entry.id}'s account state (${errorChain(error)})`);
                    continue;
                }
            }
            try {
                for (const model of await host.models(entry.id)) {
                    const resolved = await host.resolve(entry.id, model.id);
                    out.push({
                        provider: entry.id,
                        model: model.id,
                        name: model.name ?? model.id,
                        contextWindow: resolved.contextWindow ?? AUTO_FALLBACK_CONTEXT_WINDOW,
                        ...resolved.reasoningEfforts === undefined || resolved.reasoningEfforts.length === 0
                            ? {}
                            : { reasoningEfforts: resolved.reasoningEfforts },
                        ...resolved.inputModalities === undefined ? {} : { inputModalities: resolved.inputModalities },
                    });
                }
            }
            catch (error) {
                // One unreachable provider must not empty Auto: the others still stand in.
                this.options.onWarn?.(`auto could not read ${entry.id}'s models (${errorChain(error)})`);
            }
        }
        return out;
    }
    /** The same list built from this plugin's own routes, for a host-less build. */
    async registryCandidates() {
        const out = [];
        for (const route of this.options.registry.all()) {
            if (route.id === AUTO_PROVIDER_ID)
                continue;
            try {
                for (const model of await route.adapter.listModels(route.id)) {
                    const resolved = await route.adapter.resolveModel(route.id, model.id);
                    out.push({
                        provider: route.id,
                        model: model.id,
                        name: model.name,
                        contextWindow: resolved.context?.contextWindow ?? AUTO_FALLBACK_CONTEXT_WINDOW,
                        ...resolved.reasoning === undefined
                            ? {}
                            : { reasoningEfforts: resolved.reasoning.efforts.map((effort) => String(effort.id)) },
                        ...resolved.inputModalities === undefined ? {} : { inputModalities: resolved.inputModalities },
                    });
                }
            }
            catch (error) {
                // One unreachable route must not empty Auto: the others still stand in.
                this.options.onWarn?.(`auto could not read ${route.id}'s models (${errorChain(error)})`);
            }
        }
        return out;
    }
    /** The Settings card's rows: every candidate, switched-out ones included. */
    async summaries() {
        const { off } = await this.options.preferences.autoPreferences();
        const skipped = new Set(off);
        const chain = await this.chain();
        const listed = new Set(chain.map((candidate) => autoKey(candidate.provider, candidate.model)));
        // Switched-out candidates are not in the chain, so they are read from the full
        // arrangement, which keeps the user's order for the rows the card renders.
        const all = await this.arranged();
        return all.map((candidate) => {
            const key = autoKey(candidate.provider, candidate.model);
            return {
                id: key,
                name: candidate.name,
                description: `${candidate.provider} · ${candidate.model}`,
                contextWindow: candidate.contextWindow,
                ...candidate.inputModalities === undefined ? {} : { inputModalities: candidate.inputModalities },
                visible: listed.has(key) && !skipped.has(key),
                source: candidate.provider,
            };
        });
    }
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
export function createAutoProvider(options) {
    const adapter = new AutoAdapter(options);
    /** The state the page renders. */
    const status = async () => ({
        provider: AUTO_PROVIDER_ID,
        displayName: AUTO_PROVIDER_NAME,
        loggedIn: true,
        connectable: false,
        busy: false,
        accounts: [],
        credentialFile: '—',
        discovery: true,
        models: await adapter.summaries(),
        details: [
            { key: 'autoChain', value: String((await adapter.chain()).length) },
        ],
    });
    /** Refuse a credential operation: `auto` owns no account. */
    const noAccount = (operation) => {
        throw new ProviderError(`auto has no account of its own; ${operation} one of its providers instead`, BAD_REQUEST);
    };
    const route = {
        id: AUTO_PROVIDER_ID,
        displayName: AUTO_PROVIDER_NAME,
        credentialFile: '—',
        adapter,
        status,
        login: async () => noAccount('connect'),
        cancel: async () => status(),
        logout: async () => noAccount('sign out'),
        setDefault: async () => noAccount('select a default for'),
        refresh: async () => noAccount('refresh'),
        discover: async () => status(),
        setModelVisible: async (id, visible) => {
            if (parseAutoKey(id) === undefined) {
                throw new ProviderError(`auto lists provider/model keys, not ${JSON.stringify(id)}`, BAD_REQUEST);
            }
            if (await options.preferences.setAutoEnabled(id, visible))
                options.onCatalogChanged?.();
            return status();
        },
        setModelOrder: async (ids) => {
            for (const id of ids) {
                if (parseAutoKey(id) === undefined) {
                    throw new ProviderError(`auto lists provider/model keys, not ${JSON.stringify(id)}`, BAD_REQUEST);
                }
            }
            if (await options.preferences.setAutoOrder(ids))
                options.onCatalogChanged?.();
            return status();
        },
        // `auto` holds no catalog of its own: its rows are the other routes' models,
        // and a hand-added id belongs to the route that serves it.
        addModel: async () => noAccount('add a model to'),
        removeModel: async () => noAccount('remove a model from'),
        // `auto` states no allowance of its own: each candidate's own route does.
        usage: async () => ({
            usage: [],
            status: await status(),
        }),
        reload: () => status(),
    };
    // The adapter needs no back-reference to the route, but exposing it keeps a
    // future delegation from having to rebuild the pair.
    adapter.route = route;
    return route;
}
