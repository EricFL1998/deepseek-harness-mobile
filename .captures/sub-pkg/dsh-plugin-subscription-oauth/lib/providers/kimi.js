/**
 * The `kimi` provider route: Kimi Code model catalog, request assembly over
 * the OpenAI Responses wire, and streaming.
 *
 * @module dsh-plugin-subscription-oauth/providers/kimi
 */
import { EMPTY_RESPONSE_CODE, errorChain, LlmAdapter, LlmError, ReasoningEffortId, } from '@deepseek-ai/dsh-llm';
import { KIMI_CODE_URL, KIMI_CONTEXT_WINDOW, KIMI_DEFAULT_MAX_TOKENS, KIMI_DEFAULT_MODEL, KIMI_LONG_CONTEXT_WINDOW, KIMI_MODELS_PATH, KIMI_PROVIDER_ID, KIMI_PROVIDER_NAME, KIMI_RESPONSES_PATH, } from '../constants.js';
import { proxiedFetch } from '../http.js';
import { kimiRequestHeaders } from '../auth/headers.js';
import { REAUTH_REQUIRED, ProviderError } from '../errors.js';
import { finiteInstant, nonEmpty } from '../auth/store.js';
import { idleWatchdog, httpLlmError, isMissingOrInvalidCredential, mapFetchFailure } from './common.js';
import { streamProviderResponses, accountPoolHooks } from './responses-stream.js';
import { composeCatalog, fallbackEntry, findEntry, liveEntry, liveModelFacts, resolveCatalog } from './catalog.js';
import { streamResponses, toResponsesInput, toResponsesTools } from '../translate/responses.js';
import { prepareMoonshotTool } from '../translate/moonshot-schema.js';
import { isImageModel, isFastVariant, baseModelOf, fastWordOf, FastVariantIndex } from './grok.js';
/** Model ids that are clearly not chat models. */
const NON_CHAT_ID = /(?:^|[-_/])(?:image|video|imagine|embed|embedding|whisper|tts|vision-only)/i;
/**
 * Coerce one decoded credential entry into a Kimi session.
 * @param value - decoded JSON value for one account.
 * @returns the session, or undefined when required fields are missing.
 */
export function parseKimiSession(value) {
    if (typeof value !== 'object' || value === null || Array.isArray(value))
        return undefined;
    const record = value;
    const accessToken = nonEmpty(record.accessToken);
    const oauthHost = nonEmpty(record.oauthHost);
    const expiresAt = finiteInstant(record.expiresAt);
    if (accessToken === undefined || oauthHost === undefined || expiresAt === undefined)
        return undefined;
    const refreshToken = nonEmpty(record.refreshToken);
    const apiBase = nonEmpty(record.apiBase);
    const accountId = nonEmpty(record.accountId);
    const email = nonEmpty(record.email);
    return {
        accessToken,
        expiresAt,
        oauthHost,
        ...refreshToken === undefined ? {} : { refreshToken },
        ...apiBase === undefined ? {} : { apiBase },
        ...accountId === undefined ? {} : { accountId },
        ...email === undefined ? {} : { email },
    };
}
/** Harness effort ids normalised onto the ladder Kimi's endpoint accepts. */
const EFFORT_WIRE_MAP = {
    none: 'none',
    low: 'low',
    medium: 'high',
    high: 'high',
    xhigh: 'max',
    max: 'max',
};
/** The rung id the composer's quick-mode button selects. */
const FAST_RUNG = 'highspeed';
/** Display metadata for the reasoning ladder shared by every live coding model. */
const EFFORT_LABELS = {
    none: { name: 'Off', description: 'Answer without spending thinking tokens' },
    low: { name: 'Low', description: 'Short chain of thought' },
    medium: { name: 'Medium', description: 'Balanced thinking budget' },
    high: { name: 'High', description: 'Deep chain of thought' },
    xhigh: { name: 'Max', description: 'Highest thinking budget Kimi accepts' },
    max: { name: 'Max', description: 'Highest thinking budget Kimi accepts' },
    // Not a thinking budget: this rung is the model's fast variant, which the composer's
    // quick-mode button selects. It rides the effort channel so the row keeps its own id —
    // a swapped model id would leave the picker with no way back. Kimi's own word for it
    // is "highspeed", which is what the rung is named here.
    [FAST_RUNG]: { name: 'Highspeed', description: 'Kimi highspeed variant of this model' },
};
/**
 * Stable labels for the ids this route is known for.
 *
 * Labels, not a roster: nothing here decides which models exist or what they serve —
 * membership, windows, ladders and modalities are the endpoint's listing. What it fixes is
 * the one thing the listing cannot answer, because the id does not name it: `kimi-for-coding`
 * is the subscription alias the service re-points at each coding release, so its
 * `display_name` names the release of the moment (`K2.8 Preview` the week K2.8 ships) while
 * the id the user selected stays the same. Labelling that row by release would rename the
 * user's saved selection overnight, so the alias keeps its own name — and the endpoint's
 * release name is deliberately not shown, because the row is the alias, not the version.
 *
 * A model the endpoint adds that this map does not name is published exactly as the
 * endpoint describes it.
 */
const STABLE_MODEL_LABELS = {
    'kimi-for-coding': {
        name: 'Kimi for Coding',
        description: 'Subscription alias — the endpoint re-points it at the current coding release',
    },
    'k3': {
        name: 'Kimi K3',
        description: 'Flagship reasoning model',
    },
    'k3-256k': {
        name: 'Kimi K3 (256K)',
        description: 'Flagship reasoning model pinned to the 256K-token window',
    },
};
/**
 * Spellings the endpoint has retired, and the model each one became.
 *
 * A repair table, not a roster: nothing here decides which models exist. Each entry
 * is consulted only for an id the live listing does not itself serve, so a spelling
 * the endpoint reintroduces is dispatched exactly as the endpoint names it. The
 * endpoint's own 401 message (`Please set model id as \`k3\``) is the general case
 * and needs no table at all; this only saves the first failed round trip for a
 * spelling already known to be gone.
 */
const RETIRED_MODEL_IDS = { 'k3[1m]': 'k3' };
/** One `reasoning.efforts` entry for a resolved model. */
function effortInfo(effort) {
    const label = EFFORT_LABELS[effort] ?? { name: effort, description: '' };
    return { id: ReasoningEffortId(effort), name: label.name, description: label.description };
}
/**
 * The reasoning block for a catalog entry, or undefined when it supports none.
 *
 * @param entry - the catalog row.
 * @param fastWireId - the row's fast variant wire id, when it has one; the rung is the
 *   variant's own fast word (`kimi-for-coding-highspeed` → `highspeed`).
 */
function reasoningInfo(entry, fastWireId) {
    const efforts = entry.reasoningEfforts;
    const rung = fastWireId === undefined ? undefined : fastWordOf(fastWireId);
    const ids = rung === undefined ? efforts : [...efforts ?? [], rung];
    if (ids === undefined || ids.length === 0)
        return undefined;
    const defaultEffort = entry.defaultEffort ?? ids[ids.length - 1];
    return {
        efforts: ids.map(effortInfo),
        ...defaultEffort === undefined ? {} : { defaultEffort: ReasoningEffortId(defaultEffort) },
    };
}
/** Kimi Code adapter serving the `kimi` provider route. */
export class KimiAdapter extends LlmAdapter {
    options;
    /**
     * The catalog this route serves: entries derived from the endpoint's own listing,
     * either the one just read or the one the cache kept from the last read.
     *
     * Undefined means neither source has produced a listing yet, so only the configured
     * default model is offered until one does.
     */
    live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    hydrated = false;
    /** Image-generation model id, when the listing reported one. */
    imageModel;
    /**
     * Fast-variant wire id per picker row, for the composer's quick-mode button.
     *
     * Derived from the listing: `kimi-for-coding-highspeed` is a row of its own there,
     * and pairs with `kimi-for-coding` because its name is that of a listed model plus
     * the provider's own fast word.
     */
    fastVariants = new FastVariantIndex();
    /** Ids the user added by hand, cached so the sync catalog can name them. */
    userAddedIds = [];
    /**
     * Model ids the endpoint renamed during this session, and what each became.
     *
     * Learned from the endpoint's own refusal (`Please set model id as \`k3\``), so a
     * spelling retired after this build shipped is repaired without a plugin update. The
     * first turn pays one refused request; every later turn goes straight to the current id.
     */
    renamed = new Map();
    /** Tools already reported as flattened, so each is stated once. */
    flattenedTools = new Set();
    constructor(options) {
        super();
        this.options = options;
    }
    /**
     * Adopt the rows of one live listing as this route's catalog.
     *
     * Everything the picker shows is derived here from the endpoint's own answer: the
     * rows that become picker entries, the fast variant that pairs with each row, and
     * the image model that is called by an agent rather than chosen. Nothing about
     * which models exist, or what each one is, is decided by this build.
     *
     * Fast variants ride with their base row instead of becoming rows of their own: they
     * share the row's visibility switch and are reached by the composer's quick-mode
     * button. A variant is recognized by its own name — the provider's fast word appended
     * to a listed model's id — so no curated alias map is needed to pair them.
     *
     * @param rows - decoded rows of the endpoint's listing, in the endpoint's order.
     * @returns the entries to publish, in picker order.
     */
    adoptListing(rows) {
        const facts = rows
            .map((row) => liveModelFacts(row))
            .filter((fact) => fact !== undefined);
        // Image models are never picker rows: they are agent-called, always on.
        this.imageModel = facts.find((fact) => isImageModel(fact.id))?.id;
        this.fastVariants.clear();
        for (const fact of facts)
            this.fastVariants.noteListed(fact.id);
        for (const fact of facts) {
            const base = this.fastVariants.pair(fact.id);
            this.fastVariants.note(fact.id, () => base ?? baseModelOf(fact.id));
        }
        // Fold a variant onto its row and drop the ids that are not chat models, keeping the
        // endpoint's order. A row's own facts describe the row; a variant's describe the
        // variant, so a row that is also listed is never overwritten by its variant.
        const byId = new Map();
        for (const fact of facts) {
            if (NON_CHAT_ID.test(fact.id))
                continue;
            const id = isFastVariant(fact.id) ? this.fastVariants.pair(fact.id) ?? baseModelOf(fact.id) : fact.id;
            if (id === fact.id || !byId.has(id))
                byId.set(id, { ...fact, id });
        }
        return [...byId.values()].map((fact) => this.labelled(liveEntry(fact, [], KIMI_CONTEXT_WINDOW, 'Reported by the Kimi Code model list')));
    }
    /**
     * Put this route's stable label on one entry, when the id has one.
     *
     * The listing states what the model *is*; the label states what the *id* is, which only
     * this route knows (see {@link STABLE_MODEL_LABELS}).
     *
     * @param entry - the entry built from the listing.
     * @returns the entry, relabelled when its id has a stable label.
     */
    labelled(entry) {
        const stable = STABLE_MODEL_LABELS[entry.id];
        return stable === undefined ? entry : { ...entry, name: stable.name, description: stable.description };
    }
    /**
     * Read the cached listing, once, so the picker works without asking the endpoint.
     *
     * Called before the first catalog query. A read failure is a cache miss, never a
     * failure: the route simply has no listing until one is fetched.
     */
    async hydrate() {
        if (this.hydrated)
            return;
        this.hydrated = true;
        if (this.live !== undefined || this.options.cachedListing === undefined)
            return;
        try {
            const rows = await this.options.cachedListing();
            if (Array.isArray(rows) && rows.length > 0)
                this.live = this.adoptListing(rows);
        }
        catch (error) {
            this.options.onWarn?.(`cached model catalog unavailable (${errorChain(error)})`);
        }
    }
    /** The fast pair one picker id belongs to, from either side. */
    fastPairOf(id) {
        return this.fastVariants.pairOf(id);
    }
    /**
     * Whether this route has read a model listing yet.
     *
     * A variant is derived from the listing, so until one has been read "this model has no
     * variant" and "the listing has not arrived" look identical from outside; a caller that
     * asks once (the composer's quick-mode button) needs to tell them apart.
     */
    hasListing() {
        return this.live !== undefined;
    }
    /**
     * Tool schemas in the shape Moonshot accepts: no `$ref`, and under its size
     * ceiling. A declaration too large to send keeps its arguments, and that is
     * reported once per tool rather than on every turn.
     */
    moonshotTools(tools) {
        return tools.map((tool) => {
            const prepared = prepareMoonshotTool(tool.parameters);
            if (prepared.preparedAs !== 'as-declared' && !this.flattenedTools.has(tool.name)) {
                this.flattenedTools.add(tool.name);
                this.options.onWarn?.(prepared.preparedAs === 'flattened'
                    ? `kimi: ${tool.name} declares an argument schema larger than the endpoint accepts; `
                        + 'its arguments are described without their nested structure'
                    : `kimi: ${tool.name} declares an argument schema larger than the endpoint accepts; `
                        + 'it is offered as an unrestricted object');
            }
            return { ...tool, parameters: prepared.parameters };
        });
    }
    providerInfo(provider) {
        return { id: provider, name: KIMI_PROVIDER_NAME };
    }
    providerRetryPolicy() {
        // Kimi reports its own pacing through 429 `retry-after`; a local retry
        // ladder would fight the account-level rate limiter.
        return undefined;
    }
    async listModels(_provider) {
        // Nothing to offer without a signed-in account: a model that cannot be called
        // must not appear in the picker, and Auto must not try it.
        if (await this.options.tokens.session() === undefined)
            return [];
        await this.hydrate();
        await this.syncUserModels();
        const hidden = await this.hidden();
        // One row per model, always under the model's own id: the host validates every GUI
        // selection against this list, so publishing only the fast variant would leave the
        // picker with no way back to the standard model. Fast mode is the row's `fast`
        // reasoning rung instead (see `stream`).
        const imageless = this.catalog().filter((entry) => !isImageModel(entry.id));
        return resolveCatalog(imageless, [], hidden).entries.map((entry) => ({ provider: KIMI_PROVIDER_ID,
            id: entry.id,
            name: entry.name,
            ...entry.description === undefined ? {} : { description: entry.description },
            ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
        }));
    }
    /** The image-generation model id, when discovery reported one. */
    imageModelId() {
        return this.imageModel;
    }
    /** The fast/lightspeed variant wire id for a picker row. */
    fastVariant(baseId) {
        return this.fastVariants.variantOf(baseId);
    }
    /** The ids the picker must currently omit. */
    async hidden() {
        if (this.options.hiddenModels === undefined)
            return new Set();
        try {
            return await this.options.hiddenModels();
        }
        catch (error) {
            // A preferences read failure must not empty the picker.
            this.options.onWarn?.(`model preferences unavailable (${errorChain(error)})`);
            return new Set();
        }
    }
    async resolveModel(_provider, model) {
        await this.hydrate();
        await this.syncUserModels();
        // A retired spelling answers with the model that replaced it, so a session that
        // saved the old id shows the right name, window and ladder before it dispatches.
        const repaired = this.repairModelId(model);
        const entry = this.entryFor(repaired ?? model);
        if (entry === undefined) {
            // An unknown id is still dispatchable: the endpoint accepts the id it
            // reports, and a model added after this build should not be refused. It
            // inherits the standard window and the shared reasoning ladder.
            const template = this.entryFor(KIMI_DEFAULT_MODEL) ?? this.catalog()[0];
            const reasoning = template === undefined ? undefined : reasoningInfo(template);
            return {
                provider: KIMI_PROVIDER_ID,
                id: model,
                name: model,
                context: { contextWindow: KIMI_CONTEXT_WINDOW },
                defaultMaxTokens: KIMI_DEFAULT_MAX_TOKENS,
                ...reasoning === undefined ? {} : { reasoning },
            };
        }
        // A row with a fast variant offers a rung named for the variant's own word; the
        // picker shows the row under its own name and the mode as the effort.
        const fastWireId = this.fastVariants.variantOf(entry.id);
        const reasoning = reasoningInfo(entry, fastWireId);
        // The listing's own id is what resolves here — a renamed or retired spelling is
        // reported as the model the endpoint now calls it — which is what keeps a selection
        // dispatchable and the picker able to show it.
        return {
            provider: KIMI_PROVIDER_ID,
            id: entry.id,
            name: entry.name,
            ...entry.description === undefined ? {} : { description: entry.description },
            ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
            context: { contextWindow: entry.contextWindow },
            defaultMaxTokens: KIMI_DEFAULT_MAX_TOKENS,
            ...reasoning === undefined ? {} : { reasoning },
        };
    }
    stream(options) {
        // Fast mode rides the reasoning channel: the rung is the variant's own fast word, not
        // a model id of its own, so the row the picker selected stays dispatchable and the user
        // can always select the standard model again. Only the wire id changes.
        const effort = String(options.reasoningEffort ?? '');
        // A request that already names a fast variant keeps that wire id: it is a model id of
        // its own rather than a spelling of the row. Every other id is sent exactly as the
        // endpoint's listing names it, except a spelling the endpoint has retired, which goes
        // out as the model that replaced it — a session that saved `k3[1m]` repairs itself on
        // the next turn instead of failing with HTTP 401.
        const selected = isFastVariant(options.model)
            ? options.model
            : this.repairModelId(options.model) ?? options.model;
        const fastWireId = this.fastVariants.variantOf(selected);
        const fast = fastWireId !== undefined && effort === fastWordOf(fastWireId);
        const model = fast ? fastWireId : selected;
        const call = { ...options, model };
        // Kimi has no fast-variant effort; the variant already carries the behaviour.
        if (fast)
            delete call.reasoningEffort;
        return streamProviderResponses(call, {
            label: 'Kimi API',
            tokens: this.options.tokens,
            request: (call, model, accessToken, signal) => this.request(call, model, accessToken, signal),
            streamIdleTimeoutMs: this.options.streamIdleTimeoutMs,
            // A 402/429 means this account's allowance is spent: draw on the next account's
            // before giving up, so one model id uses the whole pool's quota.
            isAllowanceExhausted: (status) => status === 402 || status === 429,
            ...accountPoolHooks({ provider: KIMI_PROVIDER_ID, tokens: this.options.tokens, readUsage: this.options.readUsage }),
            onAccountSwitch: (from, to, reason) => {
                this.options.onWarn?.(`kimi: account ${from} allowance spent (${reason}); switching to ${to}`);
            },
            // The endpoint names the model id to use when a request asks for one it has
            // retired; remembering it repairs this session rather than failing the turn.
            onModelRenamed: (from, to) => {
                this.renamed.set(from, to);
                this.options.onWarn?.(`kimi: model ${from} is retired; using ${to}`);
            },
            ...this.options.onCredentialRejected === undefined
                ? {}
                : { onCredentialRejected: this.options.onCredentialRejected },
            onFallback: (from, to, reason) => {
                this.options.onWarn?.(`kimi: model ${from} unavailable (${reason}); falling back to ${to}`);
            },
            describeFailure: (error) => this.refreshHint(error),
        });
    }
    /** Translate an auth failure into the message a user can act on. */
    refreshHint(error) {
        if (error instanceof ProviderError && error.code === REAUTH_REQUIRED) {
            return new LlmError(`Kimi Code login expired; sign in again from Settings → Subscriptions (${KIMI_CODE_URL})`, 'INVALID_CREDENTIAL', { status: 401 });
        }
        return error;
    }
    /**
     * The catalog the route serves: its own entries, then the ids the user added
     * by hand. The two are built separately so the hand-added check can look at
     * the shipped list without recursing.
     */
    catalog() {
        return [...this.shippedCatalog(), ...this.addedEntries()];
    }
    /**
     * The catalog to present: the models the endpoint's listing named, in its own order,
     * each with the metadata that listing stated, then the configured extras.
     *
     * There is no shipped roster to fall back to. A listing is read once and cached (see
     * {@link hydrate} and {@link fetchCatalog}), and that cached table is what the picker
     * shows until the next read, so a model released, renamed or retired server-side is
     * reflected without a plugin update or a request per refresh.
     */
    shippedCatalog() {
        return composeCatalog({
            curated: [],
            ...this.live === undefined ? {} : { live: this.live },
            ...this.options.extraModels === undefined ? {} : { extra: this.options.extraModels },
            defaultModel: this.options.defaultModel ?? KIMI_DEFAULT_MODEL,
            fallbackContextWindow: KIMI_CONTEXT_WINDOW,
            extraDescription: 'Extra model from the plugin configuration',
        });
    }
    /** Resolve a requested model id onto the entry the endpoint's listing described. */
    entryFor(model) {
        return findEntry(this.live ?? [], model);
    }
    /**
     * The model one wire id names, when the endpoint has retired the spelling.
     *
     * Consulted only for an id the current listing does not serve, so an id the endpoint
     * itself reports is always dispatched exactly as reported.
     *
     * @param model - the requested model id.
     * @returns the current spelling, or undefined when the id is not a known retirement.
     */
    repairModelId(model) {
        if (this.entryFor(model) !== undefined)
            return undefined;
        // A fast variant is a wire id rather than a row: a session that saved one belongs on
        // the row that owns it, where the quick-mode rung is what switches the variant on.
        const pair = this.fastVariants.pairOf(model);
        if (pair !== undefined)
            return pair.baseId;
        // Prefer what this session was actually told over the table this build shipped: the
        // endpoint's own answer is newer than any release.
        return this.renamed.get(model) ?? RETIRED_MODEL_IDS[model];
    }
    /** Re-read the ids the user added by hand. */
    async syncUserModels() {
        if (this.options.userModels === undefined)
            return;
        try {
            this.userAddedIds = await this.options.userModels();
        }
        catch (error) {
            this.options.onWarn?.(`user-added models unavailable (${errorChain(error)})`);
            this.userAddedIds = [];
        }
    }
    /**
     * The entries for ids the user added by hand, in the order they added them.
     *
     * Published without shipped metadata: this build has never seen the model, so
     * the endpoint's own defaults are the honest description.
     */
    addedEntries() {
        if (this.userAddedIds.length === 0)
            return [];
        const known = new Set(this.shippedCatalog().map((entry) => entry.id));
        return this.userAddedIds
            .filter((id) => !known.has(id))
            .map((id) => fallbackEntry(id, 'Added by hand in Settings', KIMI_CONTEXT_WINDOW));
    }
    /**
     * Adopt the ids a caller reports as the served set, without a listing.
     *
     * {@link fetchCatalog} already derived the entries from the rows it read, and the
     * controller reports those same ids back here; the entries it recorded are kept. A
     * caller that only has ids (a test, another discovery path) gets conservative entries
     * for them.
     *
     * @param ids - model ids the caller reports.
     */
    discover(ids) {
        const known = new Map((this.live ?? []).map((entry) => [entry.id, entry]));
        this.live = ids.map((id) => known.get(id)
            ?? this.labelled(liveEntry({ id }, [], KIMI_CONTEXT_WINDOW, 'Reported by the Kimi Code model list')));
    }
    /** The catalog, for the Settings page's model list. */
    catalogEntries() {
        return this.catalog();
    }
    /** What this route itself serves, without the ids added by hand. */
    ownCatalogEntries() {
        return this.shippedCatalog();
    }
    /** Build and send one request. */
    async request(options, model, accessToken, signal) {
        const resolveImage = this.imageResolver(signal);
        const resolved = await toResponsesInput(options.messages, options.system, resolveImage);
        const body = {
            model,
            ...resolved.instructions === undefined ? {} : { instructions: resolved.instructions },
            input: resolved.input,
            ...options.tools === undefined || options.tools.length === 0
                ? {}
                : {
                    tools: toResponsesTools(this.moonshotTools(options.tools)),
                    tool_choice: 'auto',
                    parallel_tool_calls: true,
                },
            ...options.maxTokens === undefined ? {} : { max_output_tokens: options.maxTokens },
            ...options.reasoningEffort === undefined
                ? {}
                : { reasoning: { effort: EFFORT_WIRE_MAP[String(options.reasoningEffort)] ?? String(options.reasoningEffort) } },
            ...options.sessionId === undefined ? {} : { prompt_cache_key: String(options.sessionId) },
            // Ask for the encrypted reasoning payload so a later turn can replay the
            // provider's own item rather than reconstructed text.
            include: ['reasoning.encrypted_content'],
            store: false,
            stream: true,
        };
        // Kimi locks temperature and penalties on every subscription coding model,
        // so a caller-supplied value is dropped rather than rejected upstream.
        const fetchFn = this.options.fetchFn ?? proxiedFetch;
        return fetchFn(`${this.options.apiBase}${KIMI_RESPONSES_PATH}`, {
            method: 'POST',
            headers: kimiRequestHeaders(await this.identity(), {
                'authorization': `Bearer ${accessToken}`,
                'accept': 'text/event-stream',
                'content-type': 'application/json',
            }),
            body: JSON.stringify(body),
            signal,
        });
    }
    /** Client-identity headers for this machine, when configured. */
    async identity() {
        if (this.options.identityHeaders === undefined)
            return {};
        try {
            return await this.options.identityHeaders();
        }
        catch (error) {
            this.options.onWarn?.(`device identity unavailable (${errorChain(error)})`);
            return {};
        }
    }
    /** Resolve image blocks through the attachment service, when it is present. */
    imageResolver(signal) {
        const store = this.options.resolveAttachments?.();
        if (store === undefined || store === null)
            return undefined;
        return async (ref) => {
            try {
                const stored = await store.readImage(ref, signal);
                return { mediaType: ref.mediaType, base64: Buffer.from(stored.data).toString('base64') };
            }
            catch (error) {
                this.options.onWarn?.(`image attachment unavailable (${errorChain(error)})`);
                return undefined;
            }
        };
    }
    /**
     * Read the endpoint's model listing and adopt it as this route's catalog.
     *
     * This is the one place a model table is produced, and it is the endpoint's own
     * answer: what the listing names is what the picker offers, with the display name,
     * context window, modalities and reasoning ladder the listing states. The rows are
     * also handed to {@link KimiAdapterOptions.saveListing}, so the next session starts
     * from this table instead of asking again — the Settings "refresh models" action is
     * what re-reads it.
     *
     * @param accessToken - bearer for the account.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    async fetchCatalog(accessToken, signal) {
        if (!this.options.discovery)
            return undefined;
        const fetchFn = this.options.fetchFn ?? proxiedFetch;
        try {
            const response = await fetchFn(`${this.options.apiBase}${KIMI_MODELS_PATH}`, {
                headers: kimiRequestHeaders(await this.identity(), { 'authorization': `Bearer ${accessToken}` }),
                ...signal === undefined ? {} : { signal },
            });
            if (!response.ok) {
                if (!isMissingOrInvalidCredential(await httpLlmError(response, 'Kimi models'))) {
                    this.options.onWarn?.(`Kimi model discovery failed (HTTP ${response.status}); keeping the last catalog`);
                }
                return undefined;
            }
            const payload = await response.json();
            const rows = Array.isArray(payload.data) ? payload.data : [];
            const entries = this.adoptListing(rows);
            if (entries.length === 0) {
                this.options.onWarn?.('Kimi model discovery returned an empty catalog; keeping the last catalog');
                return undefined;
            }
            this.live = entries;
            // The cached table is the endpoint's answer, kept so the next session does not ask
            // again. A cache failure never fails the read that produced it.
            if (this.options.saveListing !== undefined) {
                try {
                    await this.options.saveListing(rows);
                }
                catch (error) {
                    this.options.onWarn?.(`model catalog cache write failed (${errorChain(error)})`);
                }
            }
            return entries.map((entry) => entry.id);
        }
        catch (error) {
            if (signal?.aborted === true)
                return undefined;
            this.options.onWarn?.(`Kimi model discovery failed; keeping the last catalog (${errorChain(error)})`);
            return undefined;
        }
    }
}
