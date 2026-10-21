/**
 * The Xiaomi MiMo wire adapter serving the `mimo` route.
 *
 * Model requests are chat completions at the MiMo server's `/route`, carrying the
 * session cookies the sign-on set — not a bearer token. The open platform's Token
 * Plan spends its own `tp-` key at a separate endpoint; that fallback lives beside
 * the account-level stream here.
 *
 * The model table this route publishes is MiMo's own listing, read once and kept in
 * this plugin's cache (see `hydrate` and `fetchCatalog`), so a model released, renamed
 * or retired server-side reaches the picker without a plugin update and without a
 * request per refresh. `MIMO_MODELS` is the cold-start seed used while no listing has
 * been read, the metadata overlay a live row is merged against, and the fallback a
 * listing that does not answer leaves the picker with — never the authority on which
 * models exist.
 *
 * Two bases are involved, and they are not the same surface:
 *   - the app surface, `<session.apiBase>/route/chat/completions`, which the stored
 *     session's cookies sign; and
 *   - the documented open-platform API at {@link MIMO_PUBLIC_API_BASE}, whose
 *     `/v1/models` route is documented to answer an OpenAI-style listing.
 * The listing is therefore best-effort: it may name the public ids (`mimo-v2.5-pro`, …)
 * rather than the app's (`mimo-pro`, …), and it may refuse the session credential
 * outright. Either way the roster the seed describes stays until a listing answers.
 * The endpoint is documented rather than probed from this machine — no MiMo account is
 * stored here — so the first real read is the verification.
 *
 * @module dsh-plugin-subscription-oauth/providers/mimo
 */
import { LlmAdapter, errorChain, } from '@deepseek-ai/dsh-llm';
import { httpLlmError, isMissingOrInvalidCredential, mapFetchFailure, idleWatchdog } from './common.js';
import { proxiedFetch } from '../http.js';
import { composeCatalog, fallbackEntry, findEntry, liveEntry, liveModelFacts, resolveCatalog } from './catalog.js';
import { readMimoPlanKey, refreshMimoSession } from '../auth/mimo-flow.js';
/** The `mimo` provider route key. */
export const MIMO_PROVIDER_ID = 'mimo';
/** Human name for the route and its cards. */
export const MIMO_PROVIDER_NAME = 'Xiaomi MiMo';
/** Where the Settings page sends someone to get an account. */
export const MIMO_CODE_URL = 'https://platform.xiaomimimo.com';
/** MiMo context window shared by Pro and Flash. */
export const MIMO_CONTEXT_WINDOW = 1_000_000;
/** The output ceiling MiMo advertises. */
export const MIMO_DEFAULT_MAX_TOKENS = 128_000;
/** The model the picker preselects. */
export const MIMO_DEFAULT_MODEL = 'mimo-pro';
/**
 * The documented open-platform API base.
 *
 * This is the host Xiaomi documents its List Models endpoint on, and it is *not* the
 * app surface a stored session chats on (`{session.apiBase}/route/chat/completions`,
 * per account and per region). The adapter's `apiBase` option overrides it, so a
 * listing that turns out to live on another host needs no code change.
 */
export const MIMO_PUBLIC_API_BASE = 'https://api.xiaomimimo.com';
/** Path appended to the base to list the models an account can reach. */
export const MIMO_MODELS_PATH = '/v1/models';
/**
 * Client identity the MiMo app sends, shared by the chat call and the listing read.
 *
 * The app gateway recognizes its own client; sending the same words on the listing
 * costs nothing and keeps the two requests from drifting apart.
 */
const MIMO_CLIENT_HEADERS = {
    'X-Mimo-Source': 'mimocode-cli-free',
    'User-Agent': 'miNative PC/Normal Windows_NT/10.0.26100 SDKV/1.0.0 DEVT/PC DEVS/Windows APP/miaccount_desktop APPV/0.1.0',
    'X-Client-Version': '26.929.292248',
};
/** Description shown for a model only the listing names. */
const MIMO_LISTING_DESCRIPTION = 'Reported by the MiMo model list';
/** Validate one stored value as a MiMo session, or undefined when it is not one. */
export function parseMimoSession(value) {
    if (typeof value !== 'object' || value === null)
        return undefined;
    const record = value;
    if (typeof record.accessToken !== 'string' || record.accessToken.length === 0)
        return undefined;
    if (typeof record.expiresAt !== 'number')
        return undefined;
    if (typeof record.apiBase !== 'string' || record.apiBase.length === 0)
        return undefined;
    if (typeof record.mimoCreds !== 'string')
        return undefined;
    if (typeof record.mimoCookies !== 'string')
        return undefined;
    return {
        accessToken: record.accessToken,
        expiresAt: record.expiresAt,
        apiBase: record.apiBase,
        mimoCreds: record.mimoCreds,
        mimoCookies: record.mimoCookies,
        ...typeof record.refreshToken === 'string' ? { refreshToken: record.refreshToken } : {},
        ...typeof record.accountId === 'string' ? { accountId: record.accountId } : {},
        ...typeof record.email === 'string' ? { email: record.email } : {},
        ...typeof record.region === 'string' ? { region: record.region } : {},
    };
}
/**
 * The MiMo *app* surface's models, as this build last knew them.
 *
 * A cold-start seed and a metadata overlay, not the catalog. It decides membership
 * only while no listing has been read, it supplies the name, window and description a
 * live row is silent about (`liveEntry` — a figure the listing states always wins),
 * and it is what a listing that does not answer falls back to, so a refused probe can
 * never empty the picker.
 *
 * Its ids (`mimo-pro`, …) belong to the app surface reached at `{session.apiBase}/route`.
 * The documented public API is a different surface and may name different ids
 * (`mimo-v2.5-pro`, …); a listing that returns those replaces this table rather than
 * adding to it, which is the whole point of reading the listing at all.
 */
export const MIMO_MODELS = [
    {
        id: 'mimo-pro',
        name: 'MiMo Pro',
        description: 'Flagship MiMo model',
        contextWindow: MIMO_CONTEXT_WINDOW,
        inputModalities: ['text', 'image'],
    },
    {
        id: 'mimo-flash',
        name: 'MiMo Flash',
        description: 'Fast MiMo model',
        contextWindow: MIMO_CONTEXT_WINDOW,
        inputModalities: ['text', 'image'],
    },
];
/** The MiMo wire adapter serving the `mimo` route. */
export class MimoAdapter extends LlmAdapter {
    options;
    fetchFn;
    /**
     * The catalog this route serves: entries derived from MiMo's own listing, either
     * the one just read or the one the cache kept from the last read.
     *
     * Undefined means no listing has been read (yet), so {@link MIMO_MODELS} decides
     * membership and the picker is never empty.
     */
    live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    hydrated = false;
    /** Ids the user added by hand, cached so the sync catalog can name them. */
    userAddedIds = [];
    /**
     * Listing failures already reported, so one reason is stated once per session.
     *
     * A listing that does not answer is expected — the documented public API and the
     * app surface are different hosts — and saying so on every refresh would drown out
     * the warnings a user can act on.
     */
    reportedListingFailures = new Set();
    constructor(options) {
        super();
        this.options = options;
        this.fetchFn = options.fetchFn ?? proxiedFetch;
    }
    /**
     * Adopt the rows of one live listing as this route's catalog.
     *
     * Everything the picker shows is derived here from MiMo's own answer: the ids it
     * names, in its own order, with the name, window, modalities and ladder it states.
     * {@link MIMO_MODELS} is consulted only as the metadata overlay for a figure the
     * row is silent about, so a live figure is never overridden by a shipped guess.
     *
     * @param rows - decoded rows of the listing, in the listing's order.
     * @returns the entries to publish, in picker order.
     */
    adoptListing(rows) {
        return rows
            .map((row) => liveModelFacts(row))
            .filter((facts) => facts !== undefined)
            .map((facts) => liveEntry(facts, MIMO_MODELS, MIMO_CONTEXT_WINDOW, MIMO_LISTING_DESCRIPTION));
    }
    /**
     * Read the cached listing, once, so the picker works without asking the endpoint.
     *
     * Called before the first catalog query. A read failure is a cache miss, never a
     * failure: the route simply has no listing until one is fetched, and the seed roster
     * is what the picker shows in the meantime.
     */
    async hydrate() {
        if (this.hydrated)
            return;
        this.hydrated = true;
        if (this.live !== undefined || this.options.cachedListing === undefined)
            return;
        try {
            const rows = listingRows(await this.options.cachedListing());
            // A live read that landed while the cache was being read is newer than the cache.
            if (rows.length > 0 && this.live === undefined)
                this.live = this.adoptListing(rows);
        }
        catch (error) {
            this.options.onWarn?.(`cached MiMo model catalog unavailable (${errorChain(error)})`);
        }
    }
    providerInfo(provider) {
        return { id: provider, name: MIMO_PROVIDER_NAME };
    }
    async listModels(_provider) {
        if (await this.options.tokens.session() === undefined)
            return [];
        await this.hydrate();
        await this.syncUserModels();
        const hidden = await this.hidden();
        return resolveCatalog(this.catalog(), [], hidden).entries.map((entry) => ({
            provider: MIMO_PROVIDER_ID,
            id: entry.id,
            name: entry.name,
            ...entry.description === undefined ? {} : { description: entry.description },
            ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
        }));
    }
    async hidden() {
        if (this.options.hiddenModels === undefined)
            return new Set();
        try {
            return await this.options.hiddenModels();
        }
        catch {
            return new Set();
        }
    }
    async resolveModel(_provider, model) {
        await this.hydrate();
        await this.syncUserModels();
        const entry = findEntry(this.catalog(), model);
        if (entry === undefined) {
            // An id this build has never heard of is still dispatchable: the endpoint is the
            // authority on what it serves, and a model released after this build should not
            // be refused. It inherits the advertised window and the app's modalities.
            return {
                provider: MIMO_PROVIDER_ID,
                id: model,
                name: model,
                context: { contextWindow: MIMO_CONTEXT_WINDOW },
                defaultMaxTokens: MIMO_DEFAULT_MAX_TOKENS,
                inputModalities: ['text', 'image'],
            };
        }
        return {
            provider: MIMO_PROVIDER_ID,
            id: entry.id,
            name: entry.name,
            ...entry.description === undefined ? {} : { description: entry.description },
            context: { contextWindow: entry.contextWindow },
            defaultMaxTokens: MIMO_DEFAULT_MAX_TOKENS,
            ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
        };
    }
    providerRetryPolicy() {
        return undefined;
    }
    /** The catalog the Settings list reads: the route's own entries plus hand-added ids. */
    catalogEntries() {
        return this.catalog();
    }
    /** The ids the route itself serves, without hand-added extras. */
    ownCatalogEntries() {
        return this.ownCatalog();
    }
    /**
     * The catalog served: the membership the listing named — or the seed roster while no
     * listing has been read — each merged with the metadata of the source that described
     * it, then the ids the user added by hand.
     */
    catalog() {
        return [...this.ownCatalog(), ...this.addedEntries()];
    }
    /**
     * What the route itself serves, without the ids added by hand.
     *
     * No model is injected here: the configured default is an app-surface id, and the
     * listing may come from the public API, where that id does not exist. What the
     * listing names is exactly what the picker offers; a configured default that the
     * listing does not name stays dispatchable through {@link resolveModel}.
     */
    ownCatalog() {
        return composeCatalog({
            curated: MIMO_MODELS,
            ...this.live === undefined ? {} : { live: this.live },
            fallbackContextWindow: MIMO_CONTEXT_WINDOW,
            liveDescription: MIMO_LISTING_DESCRIPTION,
        });
    }
    /** Entries for ids the user added by hand that the route does not already serve. */
    addedEntries() {
        if (this.userAddedIds.length === 0)
            return [];
        const known = new Set(this.ownCatalog().map((entry) => entry.id));
        return this.userAddedIds
            .filter((id) => !known.has(id))
            .map((id) => fallbackEntry(id, 'Added by hand in Settings', MIMO_CONTEXT_WINDOW));
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
     * Adopt the ids a caller reports as the served set, without a listing.
     *
     * {@link fetchCatalog} already derived the entries from the rows it read, and the
     * controller reports those same ids back here; the entries it recorded are kept, so
     * a reported id keeps the metadata the listing gave it. A caller that only has ids (a
     * test, another discovery path) gets a conservative entry overlaid with the seed.
     *
     * @param ids - model ids the caller reports.
     */
    discover(ids) {
        const known = new Map((this.live ?? []).map((entry) => [entry.id, entry]));
        this.live = ids.map((id) => known.get(id)
            ?? liveEntry({ id }, MIMO_MODELS, MIMO_CONTEXT_WINDOW, MIMO_LISTING_DESCRIPTION));
    }
    /**
     * Read MiMo's model listing and adopt it as this route's catalog.
     *
     * The documented endpoint is `GET <apiBase>/v1/models`, answering an OpenAI-style
     * `{ object: 'list', data: [{ id, object, owned_by }] }`. Xiaomi documents either an
     * `api-key` header or Bearer authentication; this sends the bearer for the account
     * the caller resolved, plus the client identity the chat call uses.
     *
     * The read is best-effort by design. The documented public API is not the app surface
     * the chat call goes to, so the answer may be a refusal, a body in another shape, or
     * rows naming ids the app does not serve; in every one of those cases the roster
     * already published stays exactly as it was, the failure is stated once through
     * {@link MimoAdapterOptions.onWarn}, and the picker is never emptied by a probe.
     *
     * @param accessToken - bearer for the account, as the controller resolved it.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    async fetchCatalog(accessToken, signal) {
        if (this.options.discovery === false)
            return undefined;
        try {
            const response = await this.fetchFn(`${this.listingBase()}${MIMO_MODELS_PATH}`, {
                headers: {
                    'authorization': `Bearer ${accessToken}`,
                    'accept': 'application/json',
                    ...MIMO_CLIENT_HEADERS,
                },
                ...signal === undefined ? {} : { signal },
            });
            if (!response.ok) {
                this.warnListingFailure(`Xiaomi MiMo model list unavailable (HTTP ${response.status}); keeping the built-in roster`);
                return undefined;
            }
            let payload;
            try {
                payload = await response.json();
            }
            catch (error) {
                this.warnListingFailure(`Xiaomi MiMo model list could not be read (${errorChain(error)}); keeping the built-in roster`);
                return undefined;
            }
            const rows = listingRows(payload);
            const entries = this.adoptListing(rows);
            if (entries.length === 0) {
                this.warnListingFailure('Xiaomi MiMo model list named no usable models; keeping the built-in roster');
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
            this.warnListingFailure(`Xiaomi MiMo model list unavailable; keeping the built-in roster (${errorChain(error)})`);
            return undefined;
        }
    }
    /** The base the listing is read from, without a trailing slash. */
    listingBase() {
        const configured = this.options.apiBase?.trim();
        return (configured === undefined || configured === '' ? MIMO_PUBLIC_API_BASE : configured).replace(/\/+$/, '');
    }
    /** State one listing failure, once per distinct reason. */
    warnListingFailure(message) {
        if (this.reportedListingFailures.has(message))
            return;
        this.reportedListingFailures.add(message);
        this.options.onWarn?.(message);
    }
    async *stream(options) {
        const held = await this.options.tokens.session();
        if (held === undefined)
            throw await httpLlmError(new Response(null, { status: 401 }), 'Xiaomi MiMo');
        const session = held.session;
        const watchdog = idleWatchdog(options.signal, this.options.streamIdleTimeoutMs);
        try {
            const cookies = this.cookieHeader(session);
            const body = this.requestBody(options);
            const response = await this.fetchFn(`${session.apiBase.replace(/\/+$/, '')}/route/chat/completions`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                    'Cookie': cookies,
                    ...MIMO_CLIENT_HEADERS,
                },
                body: JSON.stringify(body),
                signal: watchdog.signal,
            });
            // A 403 with code membership_required means the account has no app plan but a
            // Token Plan at the open platform: re-ask at its endpoint with its tp- key.
            if (response.status === 403) {
                const text = await response.text();
                let code;
                try {
                    code = JSON.parse(text).error !== undefined
                        ? JSON.parse(text).error.code
                        : JSON.parse(text).code;
                }
                catch {
                    code = undefined;
                }
                if (code === 'membership_required') {
                    const plan = await this.tokenPlanRequest(session, body, watchdog);
                    if (plan !== undefined) {
                        yield* this.readStream(plan, watchdog);
                        return;
                    }
                }
                throw new Error(`Xiaomi MiMo: ${text.slice(0, 200)}`);
            }
            if (!response.ok || response.body === null) {
                throw await httpLlmError(response, 'Xiaomi MiMo');
            }
            yield* this.readStream(response, watchdog);
        }
        catch (error) {
            throw mapFetchFailure('Xiaomi MiMo', error, watchdog, options.signal);
        }
        finally {
            watchdog.stop();
        }
    }
    /** The app's names for its models, as the Token Plan's endpoint names them. */
    static TP_MODELS = {
        'mimo-pro': 'mimo-v2.6-pro',
        'mimo-flash': 'mimo-v2.6-flash',
    };
    /**
     * Re-ask a membership_required chat at the Token Plan's endpoint.
     *
     * Reads the account's tp- API key from the open platform, then posts the chat to the
     * plan's OpenAI base with that key. Undefined when the account has no live plan or no
     * readable key.
     */
    async tokenPlanRequest(session, body, watchdog) {
        let creds;
        try {
            creds = JSON.parse(session.mimoCreds);
        }
        catch {
            return undefined;
        }
        try {
            const plan = await readMimoPlanKey(creds, this.fetchFn);
            if (plan === null)
                return undefined;
            const model = MimoAdapter.TP_MODELS[String(body.model)] ?? String(body.model);
            const path = new URL(session.apiBase.replace(/\/+$/, '') + '/route/chat/completions').pathname;
            const rest = path.replace(/^.*?\/route(?=\/|$)/, '');
            return await this.fetchFn(`${plan.base}${rest}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                    'Authorization': `Bearer ${plan.key}`,
                },
                body: JSON.stringify({ ...body, model }),
                signal: watchdog.signal,
            });
        }
        catch {
            return undefined;
        }
    }
    /**
     * The session cookies as one Cookie header, in a steady order.
     *
     * MiMo is a cookie session, not a bearer: the wire credential is the cookie
     * header built from the stored session's cookie record.
     */
    cookieHeader(session) {
        const first = ['serviceToken', 'userId', 'cUserId'];
        try {
            const cookies = JSON.parse(session.mimoCookies);
            const parts = first.filter((key) => key in cookies).map((key) => `${key}=${cookies[key]}`);
            for (const [key, value] of Object.entries(cookies)) {
                if (!first.includes(key))
                    parts.push(`${key}=${value}`);
            }
            return parts.join('; ');
        }
        catch {
            return '';
        }
    }
    requestBody(options) {
        const messages = [];
        if (options.system !== undefined && options.system.trim() !== '') {
            messages.push({ role: 'system', content: options.system });
        }
        for (const message of options.messages) {
            messages.push(this.toChatMessage(message));
        }
        return {
            model: options.model,
            messages,
            ...options.maxTokens === undefined ? {} : { max_tokens: options.maxTokens },
            stream: true,
        };
    }
    toChatMessage(message) {
        const role = message.role === 'assistant' ? 'assistant' : 'user';
        const textParts = [];
        for (const block of message.content) {
            if (block.type === 'text')
                textParts.push(block.text);
        }
        return { role, content: textParts.join('\n') };
    }
    async *readStream(response, watchdog) {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let started = false;
        // `block-end` carries the *assembled* block, and the harness takes that one as the
        // block's final content - it does not keep what the deltas accumulated. An empty text
        // there renders the whole answer as a blank body, whatever streamed before it.
        let text = '';
        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done)
                    break;
                watchdog.pulse();
                buffer += decoder.decode(value, { stream: true });
                const frames = buffer.split('\n\n');
                buffer = frames.pop() ?? '';
                for (const frame of frames) {
                    const data = frame
                        .split('\n')
                        .filter((line) => line.startsWith('data:'))
                        .map((line) => line.slice(5).trim())
                        .join('\n');
                    if (data === '' || data === '[DONE]')
                        continue;
                    let chunk;
                    try {
                        chunk = JSON.parse(data);
                    }
                    catch {
                        continue;
                    }
                    const delta = chunk.choices?.[0]?.delta?.content;
                    if (typeof delta === 'string' && delta !== '') {
                        if (!started) {
                            yield { type: 'block-start', index: 0, blockType: 'text' };
                            started = true;
                        }
                        text += delta;
                        yield { type: 'text-delta', index: 0, text: delta };
                    }
                    const usage = chunk.usage;
                    if (usage !== undefined && usage !== null) {
                        const input = usage.prompt_tokens ?? 0;
                        const output = usage.completion_tokens ?? 0;
                        yield {
                            type: 'usage',
                            usage: { inputTokens: input, outputTokens: output, totalTokens: usage.total_tokens ?? input + output },
                        };
                    }
                }
            }
            // Close the text block with what streamed into it: `block-end` is the block the
            // harness keeps, so an empty one would drop the whole answer.
            if (started)
                yield { type: 'block-end', index: 0, block: { type: 'text', text } };
            yield { type: 'finish', reason: { kind: 'stop' } };
        }
        finally {
            reader.cancel().catch(() => { });
        }
    }
}
/**
 * The rows of one listing payload.
 *
 * MiMo's documented answer is an OpenAI-style envelope (`{ object: 'list', data: [...] }`);
 * a bare array is read too, so a cache written in either shape — or a host that answers
 * the rows directly — cannot silently empty the picker.
 *
 * @param payload - a listing response body or a cached listing.
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
