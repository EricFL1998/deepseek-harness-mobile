/**
 * The `glm` route: BigModel's coding plan over the OpenAI Responses wire.
 *
 * Sign-in is ZCode's own OAuth flow (see `auth/glm-flow.ts`). What the flow hands
 * back is a BigModel access token and the ZCode JWT the subscription is bound to;
 * requests and the model list then go to the coding-plan host, whose
 * `/responses` route answers the same wire the `kimi` and `grok` routes use.
 *
 * The model table this route publishes is that host's own `/models` answer, read once
 * and kept in this plugin's cache (see `hydrate` and `fetchCatalog`). Nothing in this
 * module decides which models exist, so one released, renamed or retired server-side
 * reaches the picker without a plugin update and without a request per refresh.
 *
 * @module dsh-plugin-subscription-oauth/providers/glm
 */
import { LlmAdapter, LlmError, ReasoningEffortId } from '@deepseek-ai/dsh-llm';
import { REAUTH_REQUIRED, ProviderError } from '../errors.js';
import { finiteInstant, nonEmpty } from '../auth/store.js';
import { proxiedFetch } from '../http.js';
import { composeCatalog, fallbackEntry, findEntry, liveEntry, liveModelFacts, resolveCatalog } from './catalog.js';
import { streamProviderResponses, accountPoolHooks } from './responses-stream.js';
import { toResponsesInput, toResponsesTools } from '../translate/responses.js';
import { baseModelOf, FastVariantIndex, isFastVariant, isImageModel } from './grok.js';
/** Model ids that are clearly not chat models. */
const NON_CHAT_ID = /(?:^|[-_/])(?:image|video|imagine|embed|embedding|whisper|tts|vision-only)/i;
/** Provider route this module serves. */
export const GLM_PROVIDER_ID = 'glm';
/** Display name for the picker group and the Settings card. */
export const GLM_PROVIDER_NAME = 'GLM (BigModel)';
/** Page a user opens to manage the coding plan. */
export const GLM_CODE_URL = 'https://bigmodel.cn/coding-plan/personal/overview';
/** Coding-plan host; the Responses endpoint is `<base>/responses`. */
export const GLM_API_BASE = 'https://open.bigmodel.cn/api/coding/paas/v4';
/** Origin the account-level endpoints hang off, without the coding path. */
export const GLM_ORIGIN = 'https://open.bigmodel.cn';
/** Path appended to the API base for a streaming request. */
export const GLM_RESPONSES_PATH = '/responses';
/** Path appended to the API base to list the models an account can reach. */
export const GLM_MODELS_PATH = '/models';
/**
 * Account allowance endpoint.
 *
 * It sits on the host root rather than under the coding prefix, and BigModel
 * authenticates it with the bare key — no `Bearer` scheme, which the host refuses.
 */
export const GLM_QUOTA_PATH = '/api/monitor/usage/quota/limit';
/** Model the picker preselects. */
export const GLM_DEFAULT_MODEL = 'glm-5.3';
/** Context capacity of the GLM 5.x family. */
export const GLM_CONTEXT_WINDOW = 1_048_576;
/** Output budget used when the caller sets no cap. */
export const GLM_DEFAULT_MAX_TOKENS = 131_072;
/**
 * Coerce one decoded credential entry into a GLM session.
 *
 * @param value - decoded JSON value for one account.
 * @returns the session, or undefined when required fields are missing.
 */
export function parseGlmSession(value) {
    if (typeof value !== 'object' || value === null || Array.isArray(value))
        return undefined;
    const record = value;
    const accessToken = nonEmpty(record.accessToken);
    const zcodeJwt = nonEmpty(record.zcodeJwt);
    const expiresAt = finiteInstant(record.expiresAt);
    if (accessToken === undefined || zcodeJwt === undefined || expiresAt === undefined)
        return undefined;
    const refreshToken = nonEmpty(record.refreshToken);
    const apiBase = nonEmpty(record.apiBase);
    const accountId = nonEmpty(record.accountId);
    const email = nonEmpty(record.email);
    return {
        accessToken,
        zcodeJwt,
        expiresAt,
        ...refreshToken === undefined ? {} : { refreshToken },
        ...apiBase === undefined ? {} : { apiBase },
        ...accountId === undefined ? {} : { accountId },
        ...email === undefined ? {} : { email },
    };
}
/** A finite number from a wire field, or undefined. */
function finiteNumber(value) {
    if (typeof value === 'number' && Number.isFinite(value))
        return value;
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : undefined;
    }
    return undefined;
}
/** A reset instant from an RFC 3339 string or epoch milliseconds. */
function resetInstant(value) {
    if (typeof value === 'number' && Number.isFinite(value))
        return value;
    if (typeof value !== 'string' || value.trim() === '')
        return undefined;
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : undefined;
}
/**
 * Read the two windows a coding-plan allowance reports.
 *
 * The host lists one row per metered window and names it by a `unit`/`number`
 * pair: `(3, 5)` is the rolling five-hour window and `(6, 1)` the weekly one.
 * Rows of any other type — MCP tool usage, for instance — are not model quota and
 * are skipped rather than shown as an allowance.
 *
 * @param payload - the decoded `quota/limit` body.
 * @returns one window per metered allowance, rolling first.
 */
export function parseGlmQuota(payload) {
    if (typeof payload !== 'object' || payload === null)
        return [];
    const body = payload;
    const data = typeof body.data === 'object' && body.data !== null ? body.data : body;
    const limits = Array.isArray(data.limits) ? data.limits : [];
    const found = new Map();
    for (const row of limits) {
        if (typeof row !== 'object' || row === null)
            continue;
        const type = typeof row.type === 'string' ? row.type : '';
        if (type !== 'TOKENS_LIMIT' && type !== 'CREDIT_LIMIT')
            continue;
        let usedPercent = finiteNumber(row.percentage);
        if (usedPercent === undefined) {
            const used = finiteNumber(row.currentValue);
            const total = finiteNumber(row.usage);
            if (used !== undefined && total !== undefined && total > 0)
                usedPercent = (used / total) * 100;
        }
        if (usedPercent === undefined)
            continue;
        const unit = finiteNumber(row.unit);
        const number = finiteNumber(row.number);
        const label = unit === 3 && number === 5 ? '5h' : unit === 6 && number === 1 ? 'weekly' : undefined;
        if (label === undefined || found.has(label))
            continue;
        const resetsAt = resetInstant(row.nextResetTime);
        found.set(label, {
            label,
            usedPercent: Math.max(0, Math.min(100, usedPercent)),
            ...resetsAt === undefined ? {} : { resetsAt },
        });
    }
    return ['5h', 'weekly'].flatMap((label) => {
        const window = found.get(label);
        return window === undefined ? [] : [window];
    });
}
/**
 * Read an account's coding-plan allowance.
 *
 * @param input - host, bearer, and fetch.
 * @returns the windows the host reported; an empty list when it stated none.
 */
export async function readGlmUsage(input) {
    const response = await input.fetchFn(`${input.origin}${GLM_QUOTA_PATH}`, {
        headers: {
            // BigModel wants the key itself here, not a Bearer scheme.
            authorization: input.accessToken,
            accept: 'application/json',
        },
        ...input.signal === undefined ? {} : { signal: input.signal },
    });
    if (!response.ok)
        throw await httpFailure(response, 'GLM allowance');
    return { windows: parseGlmQuota(await response.json()) };
}
/** Map a refused allowance request onto a harness error. */
async function httpFailure(response, label) {
    const text = await response.text().catch(() => '');
    const detail = text.trim() === '' ? undefined : text.slice(0, 200);
    return new LlmError(detail === undefined
        ? `${label} request failed (HTTP ${response.status})`
        : `${label} request failed (HTTP ${response.status}): ${detail}`, response.status === 401 || response.status === 403 ? 'INVALID_CREDENTIAL' : 'PROVIDER_ERROR', { status: response.status });
}
/** The adapter behind the `glm` route. */
export class GlmAdapter extends LlmAdapter {
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
     * Fast-variant wire id per picker row.
     *
     * Derived from the listing rather than a curated alias map: a row whose name is a
     * listed model plus the provider's own fast word rides that row instead of becoming
     * one of its own. GLM's own `-flash` id is a distinct model, not a variant, and is
     * left as a row.
     */
    fastVariants = new FastVariantIndex();
    constructor(options) {
        super();
        this.options = options;
    }
    /**
     * Adopt the rows of one live listing as this route's catalog.
     *
     * Everything the picker shows is derived here from the endpoint's own answer: the
     * rows that become picker entries and the fast variant that pairs with each row.
     * Nothing about which models exist, or what each one is, is decided by this build.
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
        return [...byId.values()].map((fact) => liveEntry(fact, [], GLM_CONTEXT_WINDOW, 'Reported by the GLM model list'));
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
            const rows = listingRows(await this.options.cachedListing());
            if (rows.length > 0)
                this.live = this.adoptListing(rows);
        }
        catch (error) {
            this.options.onWarn?.(`cached model catalog unavailable (${error instanceof Error ? error.message : String(error)})`);
        }
    }
    providerInfo(provider) {
        return { id: provider, name: GLM_PROVIDER_NAME };
    }
    providerRetryPolicy() {
        // The coding host states its own pacing through 429; a local ladder would
        // fight the account-level limiter.
        return undefined;
    }
    async listModels(_provider) {
        // Nothing to offer without a signed-in account: a model that cannot be called
        // must not appear in the picker, and Auto must not try it.
        if (await this.options.tokens.session() === undefined)
            return [];
        await this.hydrate();
        const hidden = await this.hidden();
        // Image models are never picker rows: they are agent-called, always on.
        const imageless = this.catalog().filter((entry) => !isImageModel(entry.id));
        return resolveCatalog(imageless, [], hidden).entries.map((entry) => ({
            provider: GLM_PROVIDER_ID,
            id: entry.id,
            name: entry.name,
            ...entry.description === undefined ? {} : { description: entry.description },
            ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
        }));
    }
    async resolveModel(_provider, model) {
        await this.hydrate();
        const entry = findEntry(this.catalog(), model);
        if (entry === undefined) {
            // An id this build has never heard of is still dispatchable: the endpoint is
            // the authority on what it serves, and a newer model should not be refused.
            const template = findEntry(this.catalog(), this.defaultModel()) ?? this.catalog()[0];
            return {
                provider: GLM_PROVIDER_ID,
                id: model,
                name: model,
                context: { contextWindow: template?.contextWindow ?? GLM_CONTEXT_WINDOW },
                defaultMaxTokens: GLM_DEFAULT_MAX_TOKENS,
            };
        }
        const reasoning = reasoningInfo(entry);
        return {
            provider: GLM_PROVIDER_ID,
            id: entry.id,
            name: entry.name,
            ...entry.description === undefined ? {} : { description: entry.description },
            ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
            context: { contextWindow: entry.contextWindow },
            defaultMaxTokens: GLM_DEFAULT_MAX_TOKENS,
            ...reasoning === undefined ? {} : { reasoning },
        };
    }
    stream(options) {
        return streamProviderResponses(options, {
            label: 'GLM API',
            tokens: this.options.tokens,
            request: (call, model, accessToken, signal) => this.request(call, model, accessToken, signal),
            streamIdleTimeoutMs: this.options.streamIdleTimeoutMs,
            // A 402/429 means this account's allowance is spent: draw on the next account's.
            isAllowanceExhausted: (status) => status === 402 || status === 429,
            ...accountPoolHooks({ provider: GLM_PROVIDER_ID, tokens: this.options.tokens, readUsage: this.options.readUsage }),
            ...this.options.onCredentialRejected === undefined
                ? {}
                : { onCredentialRejected: this.options.onCredentialRejected },
            onFallback: (from, to, reason) => {
                this.options.onWarn?.(`glm: model ${from} unavailable (${reason}); falling back to ${to}`);
            },
            describeFailure: (error) => this.refreshHint(error),
        });
    }
    /** Translate an auth failure into the message a user can act on. */
    refreshHint(error) {
        if (error instanceof ProviderError && error.code === REAUTH_REQUIRED) {
            return new LlmError(`GLM sign-in expired; sign in again from Settings → Subscriptions (${GLM_CODE_URL})`, 'INVALID_CREDENTIAL', { status: 401 });
        }
        return error;
    }
    /** The catalog served: the route's own entries, then hand-added ids. */
    catalog() {
        return [...this.shippedCatalog(), ...this.addedEntries()];
    }
    /**
     * The catalog to present: the models the endpoint's listing named, in its own order,
     * each with the metadata that listing stated, then the configured extras.
     *
     * There is no shipped roster. A listing is read once and cached (see {@link hydrate}
     * and {@link fetchCatalog}), and that cached table is what the picker shows until the
     * next read, so a model released, renamed or retired server-side is reflected without
     * a plugin update. Only the configured default is always offered, because the picker
     * preselects it.
     */
    shippedCatalog() {
        return composeCatalog({
            curated: [],
            ...this.live === undefined ? {} : { live: this.live },
            ...this.options.extraModels === undefined ? {} : { extra: this.options.extraModels },
            defaultModel: this.defaultModel(),
            fallbackContextWindow: GLM_CONTEXT_WINDOW,
            liveDescription: 'Reported by the GLM model list',
            extraDescription: 'Extra model from the plugin configuration',
        });
    }
    /** The model the picker preselects, from configuration. */
    defaultModel() {
        return this.options.defaultModel ?? GLM_DEFAULT_MODEL;
    }
    /** Ids the user published by hand that this route does not already serve. */
    addedEntries() {
        const own = this.shippedCatalog();
        const added = this.options.userModels?.();
        if (added === undefined)
            return [];
        return (Array.isArray(added) ? added : [])
            .filter((id) => findEntry(own, id) === undefined)
            .map((id) => fallbackEntry(id, 'Added by hand in Settings', GLM_CONTEXT_WINDOW));
    }
    /** The image-generation model id, when discovery reported one. */
    imageModelId() {
        return this.imageModel;
    }
    /**
     * Whether this route has read a model listing yet.
     *
     * The image model is a row of the listing, so until one has been read "there is no image
     * model" and "the listing has not arrived" look identical from outside; a caller that asks
     * once needs to tell them apart.
     */
    hasListing() {
        return this.live !== undefined;
    }
    /** The ids the picker must currently omit. */
    async hidden() {
        if (this.options.hiddenModels === undefined)
            return new Set();
        try {
            return await this.options.hiddenModels();
        }
        catch {
            // A preferences read failure must not empty the picker.
            return new Set();
        }
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
            ?? liveEntry({ id }, [], GLM_CONTEXT_WINDOW, 'Reported by the GLM model list'));
    }
    /**
     * Read the endpoint's model listing and adopt it as this route's catalog.
     *
     * This is the one place a model table is produced, and it is the endpoint's own
     * answer: what the listing names is what the picker offers, with the display name,
     * context window, modalities and reasoning ladder the listing states. The rows are
     * also handed to {@link GlmAdapterOptions.saveListing}, so the next session starts
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
            const response = await fetchFn(`${this.options.apiBase}${GLM_MODELS_PATH}`, {
                headers: { authorization: `Bearer ${accessToken}`, accept: 'application/json' },
                ...signal === undefined ? {} : { signal },
            });
            if (!response.ok) {
                this.options.onWarn?.(`GLM model list unavailable (HTTP ${response.status}); keeping the last catalog`);
                return undefined;
            }
            const payload = await response.json();
            const rows = Array.isArray(payload.data) ? payload.data : [];
            const entries = this.adoptListing(rows);
            if (entries.length === 0) {
                this.options.onWarn?.('GLM model discovery returned an empty catalog; keeping the last catalog');
                return undefined;
            }
            this.live = entries;
            // The cached table is the endpoint's answer, kept so the next session does not ask
            // again. A cache failure never fails the read that produced it.
            await this.saveListing(rows);
            return entries.map((entry) => entry.id);
        }
        catch (error) {
            if (signal?.aborted === true)
                return undefined;
            this.options.onWarn?.(`GLM model list unavailable (${error instanceof Error ? error.message : String(error)})`);
            return undefined;
        }
    }
    /** Persist one live listing, reporting a cache failure without failing the read. */
    async saveListing(rows) {
        if (this.options.saveListing === undefined)
            return;
        try {
            await this.options.saveListing(rows);
        }
        catch (error) {
            this.options.onWarn?.(`model catalog cache write failed (${error instanceof Error ? error.message : String(error)})`);
        }
    }
    /** Send one streaming request over the Responses wire. */
    async request(options, model, accessToken, signal) {
        const resolveImage = this.imageResolver(signal);
        const resolved = await toResponsesInput(options.messages, options.system, resolveImage);
        const body = {
            model,
            ...resolved.instructions === undefined ? {} : { instructions: resolved.instructions },
            input: resolved.input,
            ...options.tools === undefined || options.tools.length === 0
                ? {}
                : { tools: toResponsesTools(options.tools), tool_choice: 'auto', parallel_tool_calls: true },
            ...options.maxTokens === undefined ? {} : { max_output_tokens: options.maxTokens },
            ...options.reasoningEffort === undefined ? {} : { reasoning: { effort: String(options.reasoningEffort) } },
            ...options.sessionId === undefined ? {} : { prompt_cache_key: String(options.sessionId) },
            include: ['reasoning.encrypted_content'],
            store: false,
            stream: true,
        };
        const fetchFn = this.options.fetchFn ?? proxiedFetch;
        return fetchFn(`${this.options.apiBase}${GLM_RESPONSES_PATH}`, {
            method: 'POST',
            headers: {
                'authorization': `Bearer ${accessToken}`,
                'accept': 'text/event-stream',
                'content-type': 'application/json',
            },
            body: JSON.stringify(body),
            signal,
        });
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
                this.options.onWarn?.(`image attachment unavailable (${error instanceof Error ? error.message : String(error)})`);
                return undefined;
            }
        };
    }
    /** The catalog the controller publishes without hand-added ids. */
    ownCatalogEntries() {
        return this.shippedCatalog();
    }
    /** The catalog the controller publishes. */
    catalogEntries() {
        return this.catalog();
    }
}
/**
 * The rows of one cached listing.
 *
 * The cache holds the rows this plugin stored; a payload written as the endpoint's own
 * envelope is read too, so a differently shaped cache cannot silently empty the picker.
 *
 * @param payload - whatever `cachedListing` returned.
 * @returns the rows to adopt, or an empty list when the payload names none.
 */
function listingRows(payload) {
    if (Array.isArray(payload))
        return payload;
    const data = typeof payload === 'object' && payload !== null
        ? payload.data
        : undefined;
    return Array.isArray(data) ? data : [];
}
/** The reasoning block for a catalog entry, or undefined when it supports none. */
function reasoningInfo(entry) {
    const efforts = entry.reasoningEfforts;
    if (efforts === undefined || efforts.length === 0)
        return undefined;
    return {
        efforts: efforts.map((effort) => ({ id: ReasoningEffortId(effort), name: String(effort), description: '' })),
        ...entry.defaultEffort === undefined ? {} : { defaultEffort: ReasoningEffortId(entry.defaultEffort) },
    };
}
