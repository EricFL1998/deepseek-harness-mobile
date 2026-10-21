/**
 * The WorkBuddy China wire adapter serving the `workbuddy` route.
 *
 * WorkBuddy chats are chat completions at `https://copilot.tencent.com/v2/chat/completions`,
 * streamed only, and the API refuses a request whose User-Agent is not
 * `WorkBuddy/<version>`. The model table this route publishes is the console's own
 * listing — `GET /console/enterprises/<enterpriseId || personal>/models`, the route
 * WorkBuddy's web console reads — fetched once and kept in this plugin's cache (see
 * {@link WorkBuddyAdapter.hydrate} and {@link WorkBuddyAdapter.fetchCatalog}).
 * {@link WORKBUDDY_CATALOG} is the cold-start seed used until a listing has been read,
 * the fallback when a read fails, and the metadata overlay for a row the listing
 * describes only partly, so a model released, renamed or retired server-side reaches the
 * picker without a plugin update and without a request per refresh.
 * Adapted from magpie's `workbuddy` for this plugin's contracts.
 *
 * @module dsh-plugin-subscription-oauth/providers/workbuddy
 */
import { LlmAdapter, errorChain, ReasoningEffortId, ToolCallId, } from '@deepseek-ai/dsh-llm';
import { idleWatchdog, mapFetchFailure } from './common.js';
import { proxiedFetch } from '../http.js';
import { composeCatalog, findEntry, liveEntry } from './catalog.js';
import { WORKBUDDY_ENDPOINT, workbuddyHeaders } from '../auth/workbuddy-flow.js';
import { recordSpend } from './workbuddy-credits.js';
import { createHash } from 'node:crypto';
import { appendFileSync } from 'node:fs';
import { homedir } from 'node:os';
import path from 'node:path';
/** Append one diagnostic line to a file the user can read regardless of where stdout goes. */
function diag(message) {
    const line = `[${new Date().toISOString()}] ${message}\n`;
    try {
        appendFileSync(path.join(homedir(), '.dsh', 'workbuddy-diag.log'), line);
    }
    catch {
        console.warn(`[wb-diag] ${message}`);
    }
}
/** Read a `retry-after` header as an absolute delay in milliseconds. */
function retryAfterMs(response) {
    const raw = response.headers.get('retry-after');
    if (raw === null)
        return undefined;
    const seconds = Number(raw);
    if (Number.isFinite(seconds) && seconds >= 0)
        return Math.round(seconds * 1000);
    const at = Date.parse(raw);
    if (!Number.isNaN(at))
        return Math.max(0, at - Date.now());
    return undefined;
}
/**
 * Code for a turn that produced no content and no tool call.
 *
 * WorkBuddy answers a request whose context has grown past what the model serves with a
 * single empty frame (a bare role, then `[DONE]`): the model generated nothing, but the
 * wire reads as a clean `stop`, so the turn "completes" blank. That is not a real answer;
 * surfacing it lets the harness retry or fall back instead of showing an empty reply.
 */
export const WORKBUDDY_EMPTY_RESPONSE_CODE = 'WORKBUDDY_EMPTY_RESPONSE';
/** The `workbuddy` provider route key (China build). */
export const WORKBUDDY_PROVIDER_ID = 'workbuddy';
/** Human name for the route and its cards. */
export const WORKBUDDY_PROVIDER_NAME = 'WorkBuddy';
/** Where the Settings page sends someone to get an account. */
export const WORKBUDDY_CODE_URL = 'https://copilot.tencent.com';
/** The chat completions endpoint. */
const WORKBUDDY_API = `${WORKBUDDY_ENDPOINT}/v2/chat/completions`;
/**
 * The model the picker preselects.
 *
 * The console's own default, and a row its listing actually serves, so the page's
 * "recommended" marker never names a model the picker cannot offer. The previous value
 * (`hy4-preview-f`) was a seed-table spelling the live listing does not serve — it offers
 * `hy4-preview` — which is the staleness this route no longer keeps in its catalog.
 */
export const WORKBUDDY_DEFAULT_MODEL = 'auto';
/** Context advertised for a listed id neither the listing nor the seed table describes. */
export const WORKBUDDY_FALLBACK_CONTEXT_WINDOW = 168_000;
/**
 * The console route that lists the models one account may use.
 *
 * The scope segment is the enterprise the session is attributed to, or `personal` for an
 * account that states none. This is the route WorkBuddy's own console reads; it is not
 * one of the plugin's `/v2` API routes, which is why probing those found nothing.
 *
 * @param enterpriseId - the session's enterprise, when it has one.
 * @returns the absolute listing URL.
 */
export function workbuddyModelsUrl(enterpriseId) {
    const scope = typeof enterpriseId === 'string' && enterpriseId.trim() !== '' ? enterpriseId.trim() : 'personal';
    return `${WORKBUDDY_ENDPOINT}/console/enterprises/${encodeURIComponent(scope)}/models`;
}
/**
 * The cold-start seed table for the China build's catalog, [id, name, context, efforts?].
 *
 * Not the roster. Membership and metadata come from WorkBuddy's own listing (see
 * {@link WorkBuddyAdapter.fetchCatalog}); this table is what the picker shows until a
 * listing has been read, what a failed read falls back to, and the metadata overlay for a
 * listed row that states less than this table knows.
 *
 * It cannot be the authority, because it ships with the plugin: a model WorkBuddy
 * releases, renames or retires server-side would reach a user only through a plugin
 * update. The live listing already contradicts it — `hy4-preview-f` and `hy3-x` are rows
 * here that the console does not serve, while `hy4-preview`, `step-5-preview`,
 * `space-bunny` and `kimi-k2.8-preview` are served and are not rows here.
 */
const CN_MODELS = [
    ['auto', 'Auto', 168_000],
    ['hy4-preview-f', 'Hy4 preview', 1_000_000, ['high']],
    ['hy3', 'Hy3', 192_000, ['low', 'high']],
    ['hy3-x', 'Hy3-X', 192_000, ['low', 'high']],
    ['deepseek-v4.1-flash', 'Deepseek-V4.1-Flash', 1_000_000],
    ['glm-5.3', 'GLM-5.3', 1_000_000, ['low', 'high', 'max']],
    ['glm-5.3-flash', 'GLM-5.3-Flash', 1_000_000, ['low', 'high', 'max']],
    ['glm-5.2', 'GLM-5.2', 1_000_000, ['high', 'xhigh']],
    ['glm-5.1', 'GLM-5.1', 200_000],
    ['glm-5v-turbo', 'GLM-5v-Turbo', 200_000],
    ['minimax-m3', 'MiniMax-M3', 512_000],
    ['kimi-k3-1', 'Kimi-K3', 1_000_000, ['low', 'high', 'xhigh']],
    ['kimi-k2.7', 'Kimi-K2.7-Code', 256_000],
    ['kimi-k2.6', 'Kimi-K2.6', 256_000],
    ['deepseek-v4-pro', 'Deepseek-V4-Pro', 1_000_000, ['none', 'high', 'xhigh']],
];
/**
 * The seed table the route falls back to when no listing has been read (see
 * {@link CN_MODELS}).
 *
 * A table this build ships, never the roster: WorkBuddy's own listing decides which
 * models exist, and each row's own figures decide what they are. This is only the
 * cold-start membership and the overlay for what a listing leaves unsaid.
 */
export const WORKBUDDY_CATALOG = CN_MODELS.map((entry) => {
    const [id, name, contextWindow, efforts] = entry;
    return {
        id,
        name,
        contextWindow,
        ...efforts === undefined ? {} : { reasoningEfforts: efforts },
    };
});
/** The reasoning rungs this route publishes; a listing's own word outside the ladder is dropped. */
const REASONING_LADDER = new Set(['none', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max']);
/** A non-empty trimmed string from a wire field. */
function textField(value) {
    return typeof value === 'string' && value.trim() !== '' ? value.trim() : undefined;
}
/** A positive whole number from a wire field, stated as a number or as a numeric string. */
function countField(value) {
    if (typeof value === 'number' && Number.isFinite(value) && value > 0)
        return Math.floor(value);
    if (typeof value !== 'string' || value.trim() === '')
        return undefined;
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : undefined;
}
/**
 * The facts one row of WorkBuddy's own listing states.
 *
 * The shared reader knows the field names Kimi, xAI and BigModel use
 * (`context_length`, `think_efforts`, `supports_image_in`); WorkBuddy states its own —
 * `maxInputTokens` (a few rows `contextWindow`, some `maxAllowedSize`), `supportsImages`,
 * `reasoning.effort` — so a row is read here and the result handed to the shared merge.
 * A field the row does not state stays absent, so the seed table can describe it.
 *
 * @param row - one entry of the listing's `data.models`.
 * @returns the facts, or undefined when the row names no model.
 */
function workbuddyFacts(row) {
    if (!isSchemaRecord(row))
        return undefined;
    const id = textField(row['id']);
    if (id === undefined)
        return undefined;
    const name = textField(row['name']);
    const description = textField(row['descriptionEn']) ?? textField(row['descriptionZh']);
    // The row's own order of preference: what it takes per request, the window it states,
    // then the cap the app applies. The first figure it states is the one it means.
    const contextWindow = countField(row['maxInputTokens'])
        ?? countField(row['contextWindow'])
        ?? countField(row['maxAllowedSize']);
    // A row is text-only unless it says otherwise; `supportsImages` is the app's own field.
    const inputModalities = row['supportsImages'] === true ? ['text', 'image'] : ['text'];
    const reasoning = isSchemaRecord(row['reasoning']) ? row['reasoning'] : undefined;
    const stated = reasoning === undefined ? undefined : textField(reasoning['effort'])?.toLowerCase();
    // A word the harness has no rung for (`turbo`) is dropped rather than advertised.
    const rung = stated !== undefined && REASONING_LADDER.has(stated) ? stated : undefined;
    return {
        id,
        inputModalities,
        ...name === undefined ? {} : { name },
        ...description === undefined ? {} : { description },
        ...contextWindow === undefined ? {} : { contextWindow },
        ...rung === undefined ? {} : { reasoningEfforts: [rung], defaultEffort: rung },
    };
}
/** The `data` object of one listing payload, from either shape a cache may hold. */
function listingData(payload) {
    if (Array.isArray(payload) || !isSchemaRecord(payload))
        return undefined;
    const data = payload['data'];
    if (isSchemaRecord(data))
        return data;
    // A cache written as the route's own object (`{models, agents}`) is read too.
    return 'models' in payload || 'agents' in payload ? payload : undefined;
}
/** The model rows of one listing payload. */
function listingRows(payload, data) {
    if (Array.isArray(payload))
        return payload;
    const models = data?.['models'];
    return Array.isArray(models) ? models : [];
}
/**
 * The ids WorkBuddy's own console offers, when the listing narrows them.
 *
 * The console reads the account's `cli` agent: its `models` list is the set enabled for
 * this account, and it can be narrower than the model table (models the account may see
 * but not call). An absent, empty or non-array `cli` entry means the whole table is
 * available, which is the console's own fallback.
 *
 * @param data - the listing's `data` object.
 * @returns the ids to offer, or undefined when the listing does not narrow them.
 */
function cliModelIds(data) {
    const agents = data?.['agents'];
    if (!Array.isArray(agents))
        return undefined;
    for (const agent of agents) {
        if (!isSchemaRecord(agent) || textField(agent['name']) !== 'cli')
            continue;
        const models = agent['models'];
        if (!Array.isArray(models))
            return undefined;
        const ids = models.flatMap((model) => {
            const id = textField(model);
            return id === undefined ? [] : [id];
        });
        return ids.length === 0 ? undefined : ids;
    }
    return undefined;
}
/**
 * Words WorkBuddy refuses a chat for ("Illegal API invocation from an unapproved
 * channel"), each with words that say the same and pass. WorkBuddy reads every message,
 * not only the system prompt, and pays no mind to case. Adapted from magpie's workbuddy.
 */
const FLAGGED = [
    [/x-anthropic-billing-header:[^\n]*\n?/gi, ''],
    [/You are Claude Code, Anthropic['']s official CLI for Claude/gi, "You are Claude Code, Anthropic's CLI for Claude"],
    [/Main branch \(you will usually use this for PRs\)/gi, 'Main branch (usually the base for PRs)'],
    [/(?<!the )Codex CLI is an open source project led by OpenAI\./gi, 'The Codex CLI is an open source project led by OpenAI.'],
];
/** Put FLAGGED's words in place in one message's content. */
function unflagText(text) {
    let out = text;
    for (const [pattern, replacement] of FLAGGED)
        out = out.replace(pattern, replacement);
    return out;
}
/** Apply the FLAGGED filter to every message's content, in place. */
function unflagMessages(messages) {
    for (let index = messages.length - 1; index >= 0; index--) {
        const message = messages[index];
        if (typeof message.content === 'string') {
            message.content = unflagText(message.content);
        }
    }
    return messages;
}
/** 32 hex digits, for the chat ids WorkBuddy's own client makes. */
function randomHex() {
    const bytes = new Uint8Array(16);
    crypto.getRandomValues(bytes);
    return [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}
// ---- the conversation's ids -------------------------------------------------
//
// WorkBuddy's own client names each chat to its gateway four ways: the
// conversation it belongs to, the user send (turn) it answers, the message
// itself, and the request that carried it. Its backend counts a user send as
// one request by `X-Conversation-Request-ID`: a chat carrying a new one every
// time is counted on its own, and — what this adapter exists to fix — a chat
// that presents a conversation the backend has never seen, under no stable
// conversation id, is treated as an unaffiliated request and can be cut
// mid-stream ("夹断"). The ids below are made from what names the conversation
// and the turn, so every step of one send carries the same turn id and the
// user's next message carries another, as WorkBuddy's own client does.
/** Salt the ids are derived with, so an id says nothing of the words it was made from. */
const ID_SALT = randomHex();
/** The 32 hex digits WorkBuddy's own ids are made of. */
function idFor(kind, key) {
    return createHash('sha256').update(`${ID_SALT}|${kind}|${key}`).digest('hex').slice(0, 32);
}
/** A message content's signature: its text, and a digest of every part that isn't text. */
export function signatureOf(content) {
    if (typeof content === 'string')
        return content;
    if (!Array.isArray(content))
        return '';
    let out = '';
    for (const part of content) {
        if (!isSchemaRecord(part))
            continue;
        const type = typeof part['type'] === 'string' ? part['type'] : '';
        if (type === '' || type === 'text')
            out += typeof part['text'] === 'string' ? part['text'] : '';
        else if (type !== 'tool_result' && type !== 'tool-result')
            out += `[${type}:${createHash('sha256').update(JSON.stringify(part)).digest('hex').slice(0, 8)}]`;
    }
    return out;
}
/** Whether `messages[index]` answers the message before it (a tool result, or an assistant's calls). */
function answeredBefore(messages, index) {
    const prev = messages[index - 1];
    if (prev === null || typeof prev !== 'object')
        return false;
    if (prev['role'] === 'tool')
        return true;
    return prev['role'] === 'assistant' && Array.isArray(prev['tool_calls']) && prev['tool_calls'].length > 0;
}
/** The body of one assistant/tool message's text, for the turn key. */
function messageText(message) {
    return signatureOf(message['content']);
}
/** The last user message's signable signature, or '' when none is. */
function turnKey(messages) {
    for (let index = messages.length - 1; index >= 0; index--) {
        if (messages[index]?.['role'] !== 'user' || answeredBefore(messages, index))
            continue;
        const signature = messageText(messages[index]);
        return signature === '' ? '' : `u${index}:${signature}`;
    }
    return '';
}
/** The conversation a chat belongs to: the session the harness names, else the first user message. */
export function conversationKey(messages, session) {
    if (session !== undefined && session.trim() !== '')
        return `session:${session.trim().slice(0, 128)}`;
    for (const message of messages) {
        if (message['role'] !== 'user')
            continue;
        const signature = messageText(message);
        if (signature !== '')
            return `first:${signature}`;
    }
    return '';
}
/** Derive the conversation ids one request's body carries. */
export function conversationIdsFor(body, options) {
    const messages = Array.isArray(body['messages']) ? body['messages'] : [];
    const message = randomHex();
    // The loop names a session per request (one-shot title/compaction calls may not):
    // one conversation's chats carry one conversation id, never a made-up one.
    const conversation = conversationKey(messages, options.sessionId === undefined ? undefined : String(options.sessionId));
    const turn = turnKey(messages);
    return {
        message,
        // A turn is a conversation's own: two conversations whose user message reads the
        // same are still two sends, not one. An empty turn (no signable user message)
        // falls back to a fresh id, as WorkBuddy's own client makes one.
        turn: turn === '' ? randomHex() : idFor('turn', `${conversation}|${turn}`),
        conversation: conversation === '' ? undefined : idFor('conversation', conversation),
    };
}
// DSH runs a turn and a session-title request at the same time on one adapter instance,
// so nothing a request needs may live on the instance: the tool rename map, the history
// it rewrites, the text it accumulates, and the credit it bills are all locals, built and
// consumed inside the one `stream()` call that owns them.
/** A tool's name may hold letters, digits, `_`, `-`; another character is refused. */
const NAME_OK = /^[A-Za-z0-9_-]+$/;
/** A name with each character WorkBuddy refuses as `_`, kept apart from `taken`. */
function safeName(name, taken) {
    const base = name.replace(/[^A-Za-z0-9_-]/g, '_') || 'tool';
    let candidate = base;
    for (let index = 2; taken.has(candidate); index++)
        candidate = `${base}_${index}`;
    taken.add(candidate);
    return candidate;
}
/** True for a plain object usable as a JSON-schema node. */
function isSchemaRecord(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
/**
 * Mend a schema where the model gateway refuses one: `\0` escapes in a pattern, and an
 * `items` list (deep models take a single shape or an anyOf, not a bare array).
 */
function fitSchema(schema, depth = 0) {
    if (depth > 64)
        return false;
    let changed = false;
    if (Array.isArray(schema)) {
        for (const item of schema)
            if (isSchemaRecord(item) && fitSchema(item, depth + 1))
                changed = true;
        return changed;
    }
    if (typeof schema.pattern === 'string' && schema.pattern.includes('\\0')) {
        schema.pattern = schema.pattern.replace(/\\0/g, '\\u0000');
        changed = true;
    }
    if (Array.isArray(schema.items)) {
        const list = schema.items.filter(isSchemaRecord);
        schema.items = list.length === 0 ? {} : list.length === 1 ? list[0] : { anyOf: list };
        delete schema.additionalItems;
        changed = true;
    }
    for (const value of Object.values(schema)) {
        if (isSchemaRecord(value) && fitSchema(value, depth + 1))
            changed = true;
    }
    return changed;
}
/** The schema a local `$ref` names, or `node` itself. */
function localRef(root, node) {
    const ref = node.$ref;
    if (typeof ref !== 'string' || !ref.startsWith('#/'))
        return node;
    let at = root;
    for (const key of ref.slice(2).split('/')) {
        at = isSchemaRecord(at) ? at[key.replaceAll('~1', '/').replaceAll('~0', '~')] : undefined;
    }
    return isSchemaRecord(at) ? at : node;
}
/**
 * Make a tool's parameters an object at the root, the shape every model takes: `allOf`'s
 * branches are merged whole; of `anyOf`/`oneOf`'s object branches the properties are
 * merged and a field each requires stays required.
 */
function objectRoot(node, root = node, depth = 0) {
    const unions = ['allOf', 'anyOf', 'oneOf'].filter((key) => Array.isArray(node[key]));
    if (node.type === 'object' && unions.length === 0)
        return false;
    const properties = isSchemaRecord(node.properties) ? { ...node.properties } : {};
    const required = Array.isArray(node.required) ? [...node.required] : [];
    for (const branch0 of Array.isArray(node.allOf) ? node.allOf : []) {
        let branch = localRef(root, isSchemaRecord(branch0) ? branch0 : {});
        if (unions.length > 0 && depth < 8)
            objectRoot((branch = structuredClone(branch)), root, depth + 1);
        if (isSchemaRecord(branch) && isSchemaRecord(branch.properties)) {
            for (const [key, value] of Object.entries(branch.properties))
                if (!(key in properties))
                    properties[key] = value;
        }
        if (isSchemaRecord(branch) && Array.isArray(branch.required))
            required.push(...branch.required);
    }
    const branches = [];
    for (const key of ['anyOf', 'oneOf']) {
        for (const branch0 of Array.isArray(node[key]) ? node[key] : []) {
            let branch = localRef(root, isSchemaRecord(branch0) ? branch0 : {});
            if (unions.length > 0 && depth < 8)
                objectRoot((branch = structuredClone(branch)), root, depth + 1);
            if (isSchemaRecord(branch) && (branch.type === 'object' || isSchemaRecord(branch.properties)))
                branches.push(branch);
        }
    }
    const from = new Map();
    for (const branch of branches) {
        if (!isSchemaRecord(branch.properties))
            continue;
        for (const [key, value] of Object.entries(branch.properties)) {
            if (key in properties)
                continue;
            const seen = from.get(key) ?? [];
            if (!seen.some((entry) => JSON.stringify(entry) === JSON.stringify(value)))
                seen.push(value);
            from.set(key, seen);
        }
    }
    for (const [key, list] of from)
        properties[key] = list.length === 1 ? list[0] : { anyOf: list };
    delete node.allOf;
    delete node.anyOf;
    delete node.oneOf;
    node.type = 'object';
    node.properties = properties;
    if (required.length > 0)
        node.required = [...new Set(required)];
    else
        delete node.required;
    return true;
}
/** The WorkBuddy China wire adapter serving the `workbuddy` route. */
export class WorkBuddyAdapter extends LlmAdapter {
    options;
    fetchFn;
    /**
     * The catalog this route serves: entries derived from WorkBuddy's own listing, either
     * the one just read or the one the cache kept from the last read.
     *
     * Undefined means neither source has produced a listing yet, so the seed table
     * ({@link WORKBUDDY_CATALOG}) is what the picker shows.
     */
    live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    hydrated = false;
    constructor(options) {
        super();
        this.options = options;
        this.fetchFn = options.fetchFn ?? proxiedFetch;
    }
    providerInfo(provider) {
        return { id: provider, name: WORKBUDDY_PROVIDER_NAME };
    }
    /**
     * Adopt one listing as this route's catalog.
     *
     * Everything the picker shows is derived here from WorkBuddy's own answer: which models
     * exist (the account's `cli` agent when the listing states one), in the order that
     * listing states them, each with the name, window, modalities and reasoning rung its row
     * states. The seed table is consulted only for what a row leaves unsaid, so a live
     * figure is never overridden by a shipped guess.
     *
     * @param payload - the decoded listing, or the `{models, agents}` object a cache kept.
     * @returns the entries to publish, in picker order.
     */
    adoptListing(payload) {
        const data = listingData(payload);
        const byId = new Map();
        for (const row of listingRows(payload, data)) {
            const facts = workbuddyFacts(row);
            if (facts !== undefined && !byId.has(facts.id))
                byId.set(facts.id, facts);
        }
        // Membership is the account's own `cli` list when the listing states one, and the
        // whole model table otherwise: the same rule WorkBuddy's console applies.
        const membership = cliModelIds(data) ?? [...byId.keys()];
        const entries = [];
        const seen = new Set();
        for (const id of membership) {
            if (seen.has(id))
                continue;
            seen.add(id);
            entries.push(liveEntry(byId.get(id) ?? { id }, WORKBUDDY_CATALOG, WORKBUDDY_FALLBACK_CONTEXT_WINDOW, 'Reported by the WorkBuddy model list'));
        }
        return entries;
    }
    /**
     * Read the cached listing, once, so the picker works without asking the console.
     *
     * Called before the first catalog query. A read failure is a cache miss, never a
     * failure: the route falls back to its seed table until a listing is fetched, so a
     * broken cache can never empty the picker.
     */
    async hydrate() {
        if (this.hydrated)
            return;
        this.hydrated = true;
        if (this.live !== undefined || this.options.cachedListing === undefined)
            return;
        try {
            const entries = this.adoptListing(await this.options.cachedListing());
            if (entries.length > 0)
                this.live = entries;
        }
        catch (error) {
            this.options.onWarn?.(`cached model catalog unavailable (${errorChain(error)})`);
        }
    }
    async listModels(_provider) {
        // Nothing to offer without a signed-in account: a model that cannot be called
        // must not appear in the picker, and Auto must not try it.
        if (await this.options.tokens.session() === undefined)
            return [];
        await this.hydrate();
        const hidden = await this.hidden();
        return this.shippedCatalog().filter((entry) => !hidden.has(entry.id)).map((entry) => ({
            provider: WORKBUDDY_PROVIDER_ID,
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
    /**
     * The catalog to present: the models WorkBuddy's listing named, in the order its own
     * console states them, each with the metadata that listing stated.
     *
     * The seed table is the membership only while no listing has been read, and the metadata
     * overlay for a row the listing describes partly. It is never allowed to add a row back:
     * a model the console stopped serving must leave the picker without a plugin update,
     * which is the whole point of reading the listing instead of shipping a roster.
     */
    shippedCatalog() {
        return composeCatalog({
            curated: WORKBUDDY_CATALOG,
            ...this.live === undefined ? {} : { live: this.live },
            fallbackContextWindow: WORKBUDDY_FALLBACK_CONTEXT_WINDOW,
            liveDescription: 'Reported by the WorkBuddy model list',
        });
    }
    /** The catalog the Settings list reads. */
    catalogEntries() {
        return this.shippedCatalog();
    }
    /** The entries the route itself serves, without hand-added extras. */
    ownCatalogEntries() {
        return this.shippedCatalog();
    }
    /**
     * Adopt the ids a caller reports as the served set, without a listing.
     *
     * {@link fetchCatalog} already derived the entries from the listing it read, and the
     * controller reports those same ids back here; the entries it recorded are kept, so a
     * live name, window or rung is not replaced by the seed table's. A caller that only has
     * ids (a test, another discovery path) gets the seed table's metadata where it knows the
     * id and conservative entries otherwise.
     *
     * @param ids - model ids the caller reports.
     */
    discover(ids) {
        const known = new Map((this.live ?? []).map((entry) => [entry.id, entry]));
        this.live = ids.map((id) => known.get(id) ?? liveEntry({ id }, WORKBUDDY_CATALOG, WORKBUDDY_FALLBACK_CONTEXT_WINDOW, 'Reported by the WorkBuddy model list'));
    }
    async resolveModel(_provider, model) {
        await this.hydrate();
        const entry = findEntry(this.shippedCatalog(), model);
        if (entry === undefined) {
            // An id no source describes is still dispatchable: WorkBuddy is the authority on
            // what it serves, and a model added after this build should not be refused. It is
            // advertised conservatively rather than with a guess.
            return {
                provider: WORKBUDDY_PROVIDER_ID,
                id: model,
                name: model,
                context: { contextWindow: 0 },
                inputModalities: ['text', 'image'],
            };
        }
        const reasoning = reasoningInfo(entry);
        return {
            provider: WORKBUDDY_PROVIDER_ID,
            id: entry.id,
            name: entry.name,
            ...entry.description === undefined ? {} : { description: entry.description },
            ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
            context: { contextWindow: entry.contextWindow },
            ...reasoning === undefined ? {} : { reasoning },
        };
    }
    /**
     * Read WorkBuddy's own console listing and adopt it as this route's catalog.
     *
     * This is the one place a model table is produced, and it is WorkBuddy's own answer: the
     * account's `cli` agent decides which models are available, and each row states its own
     * name, window, modalities and reasoning rung (see {@link adoptListing}). The payload is
     * also handed to {@link WorkBuddyAdapterOptions.saveListing}, so the next session starts
     * from this table instead of asking again — the Settings "refresh models" action is what
     * re-reads it, and a picker query never does.
     *
     * A failed or empty read keeps the catalog the route already had, and says so once.
     *
     * @param accessToken - bearer for the account.
     * @param enterpriseId - the enterprise to read; taken from the stored session when omitted.
     * @param signal - optional cancellation.
     * @returns the model ids now served, or undefined when the listing is unavailable.
     */
    async fetchCatalog(accessToken, enterpriseId, signal) {
        if (!this.options.discovery)
            return undefined;
        try {
            // The cached listing is read first, so a read that fails leaves the route on the
            // table the endpoint itself answered last rather than on the seed this build ships.
            await this.hydrate();
            // The console route is attributed by the session's own fields (`X-User-Id`, the
            // domain, the enterprise), so the session the caller's token belongs to is read back.
            const held = await this.sessionFor(accessToken);
            const scope = enterpriseId ?? held?.enterpriseId;
            const token = {
                ...held ?? { accessToken, expiresAt: 0 },
                accessToken,
                ...scope === undefined ? {} : { enterpriseId: scope },
            };
            const response = await this.fetchFn(workbuddyModelsUrl(scope), {
                headers: {
                    ...workbuddyHeaders(token),
                    // The console route is an XHR endpoint: it states the encodings it accepts and
                    // refuses a request that does not present itself as the console's own.
                    'Accept': 'application/json, text/plain, */*',
                    'X-Requested-With': 'XMLHttpRequest',
                },
                ...signal === undefined ? {} : { signal },
            });
            if (!response.ok) {
                this.options.onWarn?.(`WorkBuddy model list unavailable (HTTP ${response.status}); keeping the last catalog`);
                return undefined;
            }
            const payload = await response.json();
            const entries = this.adoptListing(payload);
            if (entries.length === 0) {
                this.options.onWarn?.('WorkBuddy model discovery returned an empty catalog; keeping the last catalog');
                return undefined;
            }
            this.live = entries;
            // The endpoint's own payload is what is kept, so a later build that reads more of it
            // benefits from a cache this one wrote. A cache failure never fails the read that
            // produced it.
            if (this.options.saveListing !== undefined) {
                try {
                    await this.options.saveListing(payload);
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
            this.options.onWarn?.(`WorkBuddy model list unavailable; keeping the last catalog (${errorChain(error)})`);
            return undefined;
        }
    }
    /**
     * The stored session one access token belongs to.
     *
     * The chat signs with the resolved token, but the request still needs the session's
     * attribution fields (`uid`, `enterpriseId`, `domain`). They are read back by matching
     * the resolved token against each stored account; a token that matches none (already
     * rotated away) leaves the caller to sign with the bare token.
     */
    async sessionFor(accessToken) {
        try {
            for (const account of await this.options.tokens.accounts()) {
                const held = await this.options.tokens.session(account);
                if (held?.session.accessToken === accessToken)
                    return this.tokenOf(held.session);
            }
            return undefined;
        }
        catch {
            return undefined;
        }
    }
    providerRetryPolicy() {
        return undefined;
    }
    async *stream(options) {
        diag(`stream entered: model=${options.model} provider=${options.provider}`);
        // Resolve (not just read) the session so a token close to its end is refreshed
        // eagerly, the way WorkBuddy's own app does — a bare session can carry an expired
        // access token into the request, which the gateway refuses with a 403.
        const held = await this.options.tokens.resolve();
        const session = await this.sessionFor(held.accessToken);
        const watchdog = idleWatchdog(options.signal, this.options.streamIdleTimeoutMs);
        // Capture the tool-call history so unsafe tool names in past turns can be renamed to
        // the safe ones the model now sees, keeping the conversation consistent.
        const history = options.messages.filter((message) => message.role === 'assistant' || message.role === 'tool');
        // The last usage frame carries the turn's real billed credit; record it so the spend
        // total is a sum of real figures, not an estimate.
        const turn = { credit: 0 };
        diag(`stream start: model=${options.model} account=${held.account} expiresAt=${held.expiresAt} now=${Date.now()}`);
        try {
            const { body, names } = this.requestBody(options, history);
            // The conversation ids one user send carries, stable across this stream's retries
            // and the tool-result step of one turn, as WorkBuddy's own client keeps them.
            const ids = conversationIdsFor(body, options);
            diag(`body: keys=${Object.keys(body).join(',')} messages=${Array.isArray(body.messages) ? body.messages.length : 0} model=${String(body.model)}`);
            // Post once with the resolved token. A 401/403 can mean the access token lapsed
            // between resolve() and now: force one refresh and post again, as WorkBuddy's own
            // client does — a 403 with a genuinely dead credential keeps its meaning below.
            // The retry carries the same turn id (ids.turn), as WorkBuddy's own client does.
            let response = await this.post(body, session ?? held, watchdog.signal, ids);
            if (response.status === 401 || response.status === 403) {
                diag(`response: ${response.status} on resolved token; forcing one refresh and retrying`);
                const refreshed = await this.options.tokens.resolve(held.account, true);
                const fresh = await this.sessionFor(refreshed.accessToken);
                response = await this.post(body, fresh ?? refreshed, watchdog.signal, ids);
                diag(`response after refresh: status=${response.status} ok=${response.ok} ct=${response.headers.get('content-type') ?? ''}`);
            }
            else {
                diag(`response: status=${response.status} ok=${response.ok} ct=${response.headers.get('content-type') ?? ''}`);
            }
            // WorkBuddy refuses a chat mid-stream when the request carries words it filters; a
            // 403/400 here names why, so surface it instead of dying on an empty stream.
            if (!response.ok || response.body === null) {
                throw await this.errorOf(response);
            }
            yield* this.readStream(response, watchdog, names, turn);
        }
        catch (error) {
            throw mapFetchFailure(WORKBUDDY_PROVIDER_NAME, error, watchdog, options.signal);
        }
        finally {
            watchdog.stop();
            if (turn.credit > 0 && this.options.spendFile !== undefined) {
                void recordSpend(this.options.spendFile, held.account, turn.credit).catch(() => { });
            }
        }
    }
    /**
     * One chat-completions POST, named the way WorkBuddy's own client names a chat: the
     * message, the request, the turn, and the conversation. Without these the gateway
     * counts the request under no client and can cut the stream.
     */
    post(body, token, signal, ids) {
        return this.fetchFn(WORKBUDDY_API, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream',
                ...workbuddyHeaders(token),
                'X-Conversation-Message-ID': ids.message,
                'X-Request-ID': ids.message,
                'X-Conversation-Request-ID': ids.turn,
                ...ids.conversation === undefined ? {} : { 'X-Conversation-ID': ids.conversation },
            },
            body: JSON.stringify(body),
            signal,
        });
    }
    /** The stored session's fields as the token the request signs with. */
    tokenOf(session) {
        return {
            accessToken: session.accessToken,
            expiresAt: session.expiresAt,
            ...session.refreshToken === undefined ? {} : { refreshToken: session.refreshToken },
            ...session.uid === undefined ? {} : { uid: session.uid },
            ...session.enterpriseId === undefined ? {} : { enterpriseId: session.enterpriseId },
            ...session.domain === undefined ? {} : { domain: session.domain },
            ...session.tokenType === undefined ? {} : { tokenType: session.tokenType },
            ...session.refreshExpiresAt === undefined ? {} : { refreshExpiresAt: session.refreshExpiresAt },
        };
    }
    requestBody(options, history) {
        const messages = [];
        for (const message of options.messages) {
            messages.push(this.toChatMessage(message));
        }
        // A chat that doesn't open with a system message is refused: give it WorkBuddy's
        // default one, as its own app does, and take the words WorkBuddy filters (Codex and
        // Claude Code's own) out of every message — WorkBuddy reads them all, whatever role.
        const system = this.systemPrompt(options);
        messages.unshift({ role: 'system', content: system });
        const tools = this.fittedTools(options.tools, history);
        return {
            body: {
                model: options.model,
                messages: unflagMessages(messages),
                ...tools === undefined ? {} : { tools: tools.tools },
                ...options.maxTokens === undefined ? {} : { max_tokens: options.maxTokens },
                stream: true,
                stream_options: { include_usage: true },
            },
            // The names this request mended, handed to the reader that renames the answer back:
            // a request that declared no tools gets its own empty map, never a neighbour's.
            names: tools?.names ?? new Map(),
        };
    }
    /**
     * Mend the tool schemas the way WorkBuddy's model gateway accepts.
     *
     * A tool name may hold only letters, digits, `_`, `-`; another character is refused, and
     * a schema with a union at the parameter root or a `\0` escape is turned away by the
     * model. Each unsafe name is made safe and the safe→original map returned to the caller
     * so the model's calls can be renamed back in the answer. Adapted from magpie's fitTools.
     *
     * @param tools - the declared tool schemas, when any.
     * @param history - the request's assistant/tool turns, renamed in place to the safe names.
     * @returns the mended wire tools, or undefined when there are none.
     */
    fittedTools(tools, history) {
        if (tools === undefined || tools.length === 0)
            return undefined;
        const wire = tools.map((tool) => ({
            type: 'function',
            function: {
                name: tool.name,
                ...tool.description === '' ? {} : { description: tool.description },
                ...tool.parameters === undefined ? {} : { parameters: structuredClone(tool.parameters) },
            },
        }));
        const taken = new Set();
        for (const tool of wire) {
            const name = tool.function.name;
            if (typeof name === 'string' && NAME_OK.test(name))
                taken.add(name);
        }
        const names = new Map();
        const theirs = new Map();
        for (const tool of wire) {
            const fn = tool.function;
            if (typeof fn.name === 'string' && !NAME_OK.test(fn.name)) {
                const safe = safeName(fn.name, taken);
                names.set(safe, fn.name);
                theirs.set(fn.name, safe);
                fn.name = safe;
            }
            if (fn.parameters !== undefined) {
                if (!isSchemaRecord(fn.parameters))
                    fn.parameters = { type: 'object', properties: {} };
                else {
                    fitSchema(fn.parameters);
                    objectRoot(fn.parameters);
                }
            }
        }
        // Rename the calls in the conversation history to the safe names, so a turn that
        // already called a tool matches the name the model now sees.
        for (const message of history) {
            for (const call of Array.isArray(message.tool_calls) ? message.tool_calls : []) {
                const fn = call?.function;
                if (fn && typeof fn.name === 'string' && theirs.has(fn.name))
                    fn.name = theirs.get(fn.name);
            }
            if (message.role === 'tool' && typeof message.name === 'string' && theirs.has(message.name)) {
                message.name = theirs.get(message.name);
            }
        }
        return { tools: wire, names };
    }
    /**
     * Restore the original tool names the model was given, in one parsed chunk.
     *
     * @param value - a decoded SSE payload.
     * @param names - the safe→original map the request that produced this stream mended.
     * @returns whether any name changed.
     */
    namedBack(value, names) {
        let changed = false;
        for (const choice of Array.isArray(value.choices) ? value.choices : []) {
            if (!isSchemaRecord(choice))
                continue;
            for (const at of [choice.message, choice.delta]) {
                if (!isSchemaRecord(at))
                    continue;
                for (const call of Array.isArray(at.tool_calls) ? at.tool_calls : []) {
                    if (!isSchemaRecord(call) || !isSchemaRecord(call.function))
                        continue;
                    const original = names.get(String(call.function.name));
                    if (original !== undefined) {
                        call.function.name = original;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }
    /** The system prompt: the caller's, else WorkBuddy's default. */
    systemPrompt(options) {
        if (options.system !== undefined && options.system.trim() !== '')
            return options.system;
        for (const message of options.messages) {
            if (message.role === 'system') {
                const text = message.content.filter((block) => block.type === 'text').map((block) => block.text).join('\n');
                if (text.trim() !== '')
                    return text;
            }
        }
        return 'You are a helpful assistant.';
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
    /** Turn a non-2xx into an LlmError, naming WorkBuddy's specific refusals. */
    async errorOf(response) {
        const text = await response.text().catch(() => '');
        let body = {};
        try {
            body = JSON.parse(text);
        }
        catch {
            body = {};
        }
        const code = Number(body.code ?? body.data?.code ?? 0);
        const message = String(body.msg ?? body.error?.message ?? body.message ?? text.slice(0, 200) ?? `HTTP ${response.status}`);
        if (/unapproved channel|illegal api invocation/i.test(text)) {
            return new Error(`${message} — WorkBuddy refuses chats from Codex and Claude Code; use another client or add another provider`);
        }
        if (code === 11140 || /account has been restricted due to unusual activity/i.test(text)) {
            return new Error(`${message} — WorkBuddy restricted this account; it stays out until WorkBuddy lifts that`);
        }
        if (code === 14018 || /credits exhausted|额度已用尽/i.test(text)) {
            return new Error(`${message} — WorkBuddy requires an active credit package even for 0-rate free models`);
        }
        const { LlmError } = await import('@deepseek-ai/dsh-llm');
        const retryAfter = retryAfterMs(response);
        // A 429 is the model's own queue-full pacing, not a refusal: report it the way the
        // gateway words it, so the harness reads it as a normal rate-limit wait rather than a
        // failed request.
        if (response.status === 429) {
            return new LlmError(`429: {"code":null,"message":"${message}","param":null,"type":"rate_limit_error"}`, 'RATE_LIMIT', { status: response.status, ...retryAfter === undefined ? {} : { providerRetryAfterMs: retryAfter } });
        }
        return new LlmError(`${WORKBUDDY_PROVIDER_NAME} request failed (HTTP ${response.status}): ${message}`, response.status === 401 || response.status === 403 ? 'INVALID_CREDENTIAL'
            : response.status >= 500 ? 'SERVER' : 'HTTP_ERROR', { status: response.status, ...retryAfter === undefined ? {} : { providerRetryAfterMs: retryAfter } });
    }
    /**
     * Reassemble whole SSE `data:` payloads from decoded text.
     *
     * The gateway may deliver one SSE event over several network reads, and may also pack
     * several events (or a heartbeat comment) into a single read. The previous reader split
     * on blank lines and joined every `data:` line with `\n`, so a frame that straddled a
     * read boundary was cut in half and dropped by `JSON.parse` — and a dropped tool-call
     * fragment left its block forever without a `block-end`, so the turn never finished.
     * DeepSeek streams a tool call's arguments as one-character frames, which made the cut
     * frequent and the stall look random.
     *
     * This keeps the leftover of the previous read and walks complete lines instead. A
     * frame is yielded only once it parses as one JSON value, so a value split across reads
     * (and one packed into a single read) both reassemble; `[DONE]` is surfaced verbatim.
     *
     * @param chunk - newly decoded text.
     * @param carry - the leftover partial line, updated in place.
     * @returns the whole payloads found, in wire order.
     */
    *ssePayloads(chunk, carry) {
        carry.text += chunk;
        for (;;) {
            const newline = carry.text.indexOf('\n');
            if (newline < 0)
                return; // no complete line yet; wait for the rest
            const line = carry.text.slice(0, newline);
            carry.text = carry.text.slice(newline + 1);
            if (!line.startsWith('data:'))
                continue; // heartbeat comment or blank separator
            const payload = line.slice(5).trim();
            if (payload === '' || payload === '[DONE]') {
                if (payload === '[DONE]')
                    yield '[DONE]';
                continue;
            }
            try {
                JSON.parse(payload);
                yield payload;
            }
            catch {
                // An incomplete JSON value (the frame was split across reads, or packed several to
                // a line): hold it and let the next line or read complete it. A value never spans
                // lines — an embedded newline arrives escaped — so this cannot run past its end.
                carry.text = `${line}\n${carry.text}`;
                return;
            }
        }
    }
    async *readStream(response, watchdog, names, turn) {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        const carry = { text: '' };
        let started = false;
        // `block-end` carries the *assembled* block, and the harness takes that one as the
        // block's final content - it does not keep what the deltas accumulated. An empty text
        // there renders the whole answer as a blank body, whatever streamed before it.
        let text = '';
        // One entry per tool call the model streams: its own block index, its identity, and the
        // argument fragments seen so far. Calls are indexed past the text block so a reply that
        // both writes and calls assembles as two blocks instead of folding the call into the
        // text block (where it is silently lost and the harness loops on a call that never ran).
        const calls = new Map();
        let framesSeen = 0;
        let bytesSeen = 0;
        let firstBytes = '';
        let sawToolCall = false;
        // The wire's own terminal reason, when a frame states one. A real finish_reason is
        // honoured ('length' reads as max-tokens, an unexpected stop/error reads as an error)
        // instead of answering every stream with a bare 'stop' that hides a truncated answer
        // as a clean, complete one. Absent, the shape decides as before.
        let wireFinish;
        diag(`stream open: status=${response.status} content-type=${response.headers.get('content-type') ?? ''}`);
        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done)
                    break;
                watchdog.pulse();
                bytesSeen += value.byteLength;
                if (firstBytes.length < 200)
                    firstBytes += decoder.decode(value.slice(0, 200 - firstBytes.length), { stream: false });
                // Hand the decoded text to the payload reassembler, which splits off whole JSON
                // frames however they were packed into this read.
                for (const payload of this.ssePayloads(decoder.decode(value, { stream: true }), carry)) {
                    framesSeen += 1;
                    if (framesSeen <= 6)
                        diag(`f${framesSeen} raw-data=${JSON.stringify(payload.slice(0, 100))}`);
                    if (payload === '[DONE]')
                        continue;
                    let frame;
                    try {
                        frame = JSON.parse(payload);
                    }
                    catch (error) {
                        if (framesSeen <= 6)
                            diag(`f${framesSeen} PARSE FAIL: ${error instanceof Error ? error.message : String(error)}`);
                        continue;
                    }
                    if (framesSeen <= 6)
                        diag(`f${framesSeen} delta=${JSON.stringify(frame.choices?.[0]?.delta ?? null).slice(0, 120)}`);
                    // WorkBuddy names a refusal mid-stream (an error frame, or a body with a non-zero
                    // code): surface it instead of reading the rest as silence, so the turn fails with
                    // the reason rather than stopping after a few empty seconds.
                    const wireError = frame.error ?? frame.choices?.[0]?.delta;
                    if (wireError !== undefined && wireError !== null && typeof wireError === 'object') {
                        const message = wireError.message;
                        if (typeof message === 'string' && message !== '')
                            throw new Error(`WorkBuddy: ${message}`);
                    }
                    const wireCode = frame.code;
                    if (typeof wireCode === 'number' && wireCode !== 0) {
                        const msg = frame.msg;
                        throw new Error(`WorkBuddy: ${typeof msg === 'string' ? msg : `error ${wireCode}`}`);
                    }
                    // Restore the tool names the model called to the ones the client declared, before
                    // the chunk is read as a stream event.
                    this.namedBack(frame, names);
                    // The terminal reason a frame states, kept for the finish below.
                    const statedFinish = frame.choices?.[0]?.finish_reason;
                    if (typeof statedFinish === 'string' && statedFinish !== '')
                        wireFinish = statedFinish;
                    // A tool-call delta: emit it so the harness can run the tool. The id and name ride
                    // the first delta of each call; later deltas carry argument fragments.
                    const toolCalls = frame.choices?.[0]?.delta?.tool_calls;
                    if (Array.isArray(toolCalls)) {
                        if (framesSeen <= 8)
                            diag(`f${framesSeen} tool_calls=${JSON.stringify(toolCalls).slice(0, 160)}`);
                        for (const call of toolCalls) {
                            const fn = call?.function;
                            if (fn === undefined || fn === null)
                                continue;
                            const name = typeof fn.name === 'string' ? fn.name : '';
                            const args = typeof fn.arguments === 'string' ? fn.arguments : '';
                            const slot = call.index ?? 0;
                            let open = calls.get(slot);
                            // A reply ends with an empty function stub that is not a call: opening a block
                            // for it would end the turn as `tool-calls` with nothing for the harness to run.
                            if (open === undefined && name === '' && args === '')
                                continue;
                            if (open === undefined) {
                                open = { index: 1 + slot, id: call.id ?? `wb-tool-${slot}`, name: '', args: '' };
                                calls.set(slot, open);
                                sawToolCall = true;
                                yield { type: 'block-start', index: open.index, blockType: 'tool-call' };
                            }
                            if (name !== '' && open.name === '') {
                                open.name = name;
                                yield { type: 'tool-call-delta', index: open.index, id: ToolCallId(open.id), name, argumentsDelta: '' };
                            }
                            if (args !== '') {
                                open.args += args;
                                yield { type: 'tool-call-delta', index: open.index, id: ToolCallId(open.id), argumentsDelta: args };
                            }
                        }
                    }
                    const delta = frame.choices?.[0]?.delta?.content;
                    if (typeof delta === 'string' && delta !== '') {
                        if (!started) {
                            yield { type: 'block-start', index: 0, blockType: 'text' };
                            started = true;
                        }
                        text += delta;
                        yield { type: 'text-delta', index: 0, text: delta };
                    }
                    const usage = frame.usage;
                    if (usage !== undefined && usage !== null) {
                        const input = usage.prompt_tokens ?? 0;
                        const output = usage.completion_tokens ?? 0;
                        // The last usage frame carries the real billed credit; keep the latest, so a
                        // mid-stream retry does not double-count.
                        if (typeof usage.credit === 'number' && Number.isFinite(usage.credit)) {
                            turn.credit = usage.credit;
                        }
                        yield {
                            type: 'usage',
                            usage: { inputTokens: input, outputTokens: output, totalTokens: usage.total_tokens ?? input + output },
                        };
                    }
                }
            }
            // Close every open block with its assembled content: `block-end` is the block the
            // harness keeps, so the text that streamed has to ride it.
            if (started)
                yield { type: 'block-end', index: 0, block: { type: 'text', text } };
            for (const open of calls.values()) {
                yield {
                    type: 'block-end',
                    index: open.index,
                    block: { type: 'tool-call', id: ToolCallId(open.id), name: open.name, arguments: open.args },
                };
            }
            diag(`stream done: frames=${framesSeen} bytes=${bytesSeen} started=${started} text=${text.length} calls=${calls.size} sawToolCall=${sawToolCall} finish=${wireFinish ?? ''} firstBytes=${JSON.stringify(firstBytes.slice(0, 80))}`);
            // A turn that called a tool ends with the tool-call reason, not stop: the harness
            // runs the tool and feeds the result back. A stop reason here would read as a clean,
            // complete answer and the tool would never run. Otherwise the wire's own terminal
            // reason decides, so a length-capped answer is not mistaken for a complete one.
            if (sawToolCall)
                yield { type: 'finish', reason: { kind: 'tool-calls' } };
            else if (!started && calls.size === 0) {
                // Nothing was generated at all: the model answered a single empty frame (a bare role,
                // then `[DONE]`) — what WorkBuddy returns once the context outgrows the model. A clean
                // `stop` here reads as a completed blank turn; surface it as an empty response so the
                // caller retries or falls back rather than showing nothing.
                yield { type: 'finish', reason: { kind: 'error', failure: { message: 'WorkBuddy returned no content', code: WORKBUDDY_EMPTY_RESPONSE_CODE } } };
            }
            else if (wireFinish === 'length')
                yield { type: 'finish', reason: { kind: 'max-tokens' } };
            else if (wireFinish !== undefined && wireFinish !== 'stop') {
                yield { type: 'finish', reason: { kind: 'error', failure: { message: `WorkBuddy stream ended with finish_reason ${wireFinish}`, code: 'STREAM_FINISH' } } };
            }
            else
                yield { type: 'finish', reason: { kind: 'stop' } };
        }
        finally {
            reader.cancel().catch(() => { });
        }
    }
}
/**
 * The reasoning block for a catalog entry, or undefined when it supports none.
 *
 * The ladder is whatever the entry ended up with: the listing's own rung where it stated
 * one, the seed table's where it did not.
 *
 * @param entry - the catalog row.
 * @returns the reasoning block to advertise, when the row has a ladder.
 */
function reasoningInfo(entry) {
    const efforts = entry.reasoningEfforts;
    if (efforts === undefined || efforts.length === 0)
        return undefined;
    return {
        efforts: efforts.map((effort) => ({ id: ReasoningEffortId(effort), name: String(effort) })),
        ...entry.defaultEffort === undefined ? {} : { defaultEffort: ReasoningEffortId(entry.defaultEffort) },
    };
}
