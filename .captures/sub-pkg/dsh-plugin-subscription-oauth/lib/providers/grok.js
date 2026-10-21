/**
 * The `grok` provider route: xAI / X Premium (SuperGrok) subscription access.
 *
 * Login is an OIDC authorization-code flow with PKCE against `auth.x.ai`,
 * discovered from the issuer's well-known document and completed on a loopback
 * callback the CLI's registered client allows. Requests then go over xAI's
 * Responses endpoint, which the shared Responses translator already speaks.
 *
 * @module dsh-plugin-subscription-oauth/providers/grok
 */
import { errorChain, LlmAdapter, LlmError, ReasoningEffortId, } from '@deepseek-ai/dsh-llm';
import { REAUTH_REQUIRED, ProviderError } from '../errors.js';
import { attributedHeaders } from '../auth/headers.js';
import { LoopbackFlow } from '../auth/pkce-flow.js';
import { TokenStore, finiteInstant, nonEmpty } from '../auth/store.js';
import { TokenManager } from '../auth/tokens.js';
import { ProviderController } from '../auth/provider-controller.js';
import { proxiedFetch } from '../http.js';
import { composeCatalog, fallbackEntry, findEntry, liveEntry, liveModelFacts, resolveCatalog } from './catalog.js';
import { isMissingOrInvalidCredential } from './common.js';
import { streamProviderResponses, accountPoolHooks } from './responses-stream.js';
import { readGrokUsage } from './usage.js';
import { toResponsesInput, toResponsesTools } from '../translate/responses.js';
/** Route key this module registers. */
export const GROK_PROVIDER_ID = 'grok';
/** Display name for selectors and diagnostics. */
export const GROK_PROVIDER_NAME = 'Grok (X Premium)';
/** Where a user manages the underlying subscription. */
export const GROK_CODE_URL = 'https://x.ai/grok';
/** OIDC issuer whose well-known document names the endpoints. */
export const GROK_ISSUER = 'https://auth.x.ai';
/** Discovery document URL. */
export const GROK_DISCOVERY_URL = `${GROK_ISSUER}/.well-known/openid-configuration`;
/** The Grok CLI's registered client id. */
export const GROK_CLIENT_ID = 'b1a00492-073a-47ea-816f-4c329264a828';
/** Scopes the CLI requests; `grok-cli:access` is what admits subscription traffic. */
export const GROK_SCOPE = 'openid profile email offline_access grok-cli:access api:access';
/** The one loopback port the registered client redirects to. */
export const GROK_CALLBACK_PORT = 56121;
/** Path the client redirects to. */
export const GROK_CALLBACK_PATH = '/callback';
/** API base; `<base>/responses` streams and `<base>/models` lists. */
export const GROK_API_BASE = 'https://api.x.ai/v1';
/** Optional CLI catalog that discloses per-model context and reasoning levels. */
export const GROK_CLI_MODELS_URL = 'https://cli-chat-proxy.grok.com/v1/models';
/** Context window assumed when neither the CLI catalog nor a seed states one. */
export const GROK_CONTEXT_WINDOW = 256_000;
/** Default per-request output cap. */
export const GROK_DEFAULT_MAX_TOKENS = 32_000;
/** Refresh when the access token has less than this much life left. */
export const GROK_PREEMPT_MS = 2 * 60 * 1000;
/** Hosts an authorization server endpoint may live on. */
const TRUSTED_AUTH_HOSTS = new Set(['auth.x.ai', 'accounts.x.ai']);
/** The rung id the composer's quick-mode button selects. */
const FAST_RUNG = 'fast';
/** Display metadata for the reasoning ladder xAI documents. */
const EFFORT_LABELS = {
    none: { name: 'Off', description: 'Answer without spending thinking tokens' },
    low: { name: 'Low', description: 'Short chain of thought' },
    medium: { name: 'Medium', description: 'Balanced thinking budget' },
    high: { name: 'High', description: 'Deep chain of thought' },
    // Not a thinking budget: this rung is the model's fast variant, which the
    // composer's quick-mode button selects. It rides the effort channel so the row
    // keeps its own id — a swapped model id would leave the picker with no way back.
    // xAI's own word for it is "fast" (grok-4.7-build-fast), which names the rung.
    [FAST_RUNG]: { name: 'Fast', description: 'xAI fast variant of this model' },
};
/**
 * The cold-start seed: what this build knows about xAI's models before either of the
 * account's own listings has been read.
 *
 * A seed, never a roster. It decides which ids exist only until a listing has been read
 * — the cached one at startup, or a fresh one on "refresh models" — and afterwards it
 * describes only the fields those listings left silent. A fact the endpoint states,
 * a context window included, always wins over anything here, so a model released,
 * renamed or retired server-side reaches the picker without a plugin release.
 */
export const GROK_MODELS = [
    {
        id: 'grok-4.6',
        name: 'Grok 4.6',
        description: 'Current flagship, reasoning levels from the live catalog',
        contextWindow: GROK_CONTEXT_WINDOW,
        inputModalities: ['text', 'image'],
    },
    {
        id: 'grok-4.5',
        name: 'Grok 4.5',
        description: 'Previous flagship',
        contextWindow: GROK_CONTEXT_WINDOW,
        inputModalities: ['text', 'image'],
    },
    {
        id: 'grok-4.7',
        name: 'Grok 4.7',
        description: 'Latest flagship with extended context',
        contextWindow: 500_000,
        inputModalities: ['text', 'image'],
    },
];
/**
 * Coerce one decoded credential entry into a Grok session.
 * @param value - decoded JSON value for one account.
 * @returns the session, or undefined when required fields are missing.
 */
export function parseGrokSession(value) {
    if (typeof value !== 'object' || value === null || Array.isArray(value))
        return undefined;
    const record = value;
    const accessToken = nonEmpty(record.accessToken);
    const tokenEndpoint = nonEmpty(record.tokenEndpoint);
    const expiresAt = finiteInstant(record.expiresAt);
    if (accessToken === undefined || tokenEndpoint === undefined || expiresAt === undefined)
        return undefined;
    const refreshToken = nonEmpty(record.refreshToken);
    const issuer = nonEmpty(record.issuer);
    const scopes = nonEmpty(record.scopes);
    const accountId = nonEmpty(record.accountId);
    const email = nonEmpty(record.email);
    return {
        accessToken,
        expiresAt,
        tokenEndpoint,
        issuer: issuer ?? GROK_ISSUER,
        ...refreshToken === undefined ? {} : { refreshToken },
        ...scopes === undefined ? {} : { scopes },
        ...accountId === undefined ? {} : { accountId },
        ...email === undefined ? {} : { email },
    };
}
/** One `reasoning.efforts` entry. */
function effortInfo(effort) {
    const known = EFFORT_LABELS[effort];
    const label = known ?? { name: effort[0].toUpperCase() + effort.slice(1), description: `${effort} variant of this model` };
    return { id: ReasoningEffortId(effort), name: label.name, description: label.description };
}
/**
 * The reasoning block for a catalog entry, or undefined when it supports none.
 *
 * @param entry - the catalog row.
 * @param fastWireId - the row's fast variant wire id, when it has one; the rung is the
 *   variant's own fast word (`grok-4.7-build-fast` → `fast`).
 */
function reasoningInfo(entry, fastWireId) {
    const efforts = entry.reasoningEfforts;
    const rung = fastWireId === undefined ? undefined : fastWordOf(fastWireId);
    const ids = rung === undefined ? efforts : [...efforts ?? [], rung];
    if (ids === undefined || ids.length === 0)
        return undefined;
    return {
        efforts: ids.map(effortInfo),
        ...entry.defaultEffort === undefined ? {} : { defaultEffort: ReasoningEffortId(entry.defaultEffort) },
    };
}
/** Reject an endpoint that is not an xAI HTTPS URL. */
function assertTrustedEndpoint(raw, field) {
    let url;
    try {
        url = new URL(raw);
    }
    catch {
        throw new Error(`xAI discovery returned an unparseable ${field}`);
    }
    if (url.protocol !== 'https:' || url.username !== '' || url.password !== '' || url.port !== ''
        || !TRUSTED_AUTH_HOSTS.has(url.hostname.toLowerCase())) {
        throw new Error(`xAI discovery returned an untrusted ${field} (${url.hostname})`);
    }
    return url.toString();
}
/** Read one JWT payload, or undefined for an opaque token. */
function decodeJwt(token) {
    if (token === undefined)
        return undefined;
    const parts = token.split('.');
    if (parts.length < 2)
        return undefined;
    try {
        const parsed = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
        return typeof parsed === 'object' && parsed !== null ? parsed : undefined;
    }
    catch {
        return undefined;
    }
}
/** Account identity from the id token, falling back to the access token. */
export function identityFromGrokTokens(accessToken, idToken) {
    const payload = decodeJwt(idToken) ?? decodeJwt(accessToken);
    const accountId = nonEmpty(payload?.sub);
    const email = nonEmpty(payload?.email)?.toLowerCase();
    return {
        ...accountId === undefined ? {} : { accountId },
        ...email === undefined ? {} : { email },
    };
}
/** Turn a token-endpoint payload into tokens with an absolute expiry. */
export function parseGrokTokenPayload(payload, previousRefreshToken) {
    const record = (payload ?? {});
    const accessToken = nonEmpty(record.access_token);
    const lifetime = record.expires_in;
    const lifetimeOk = typeof lifetime === 'number' && Number.isFinite(lifetime) && lifetime >= 0;
    if (accessToken === undefined || !lifetimeOk) {
        throw new Error('xAI token response missing required fields');
    }
    const expiresAt = Date.now() + lifetime * 1000;
    if (!Number.isFinite(expiresAt))
        throw new Error('xAI token response missing required fields');
    const refreshToken = nonEmpty(record.refresh_token) ?? previousRefreshToken;
    const idToken = nonEmpty(record.id_token);
    return {
        accessToken,
        expiresAt,
        ...refreshToken === undefined ? {} : { refreshToken },
        ...idToken === undefined ? {} : { idToken },
    };
}
/** OIDC discovery, cached for the process. */
export function createGrokDiscovery(fetchFn, onWarn) {
    let cached;
    return async function discover() {
        if (cached !== undefined)
            return cached;
        const response = await fetchFn(GROK_DISCOVERY_URL, { headers: { accept: 'application/json' } });
        if (!response.ok) {
            throw new Error(`xAI OIDC discovery failed (HTTP ${response.status})`);
        }
        const payload = await response.json();
        if (typeof payload.authorization_endpoint !== 'string' || typeof payload.token_endpoint !== 'string') {
            throw new Error('xAI OIDC discovery response is missing authorization/token endpoints');
        }
        const endpoints = {
            authorizationEndpoint: assertTrustedEndpoint(payload.authorization_endpoint, 'authorization_endpoint'),
            tokenEndpoint: assertTrustedEndpoint(payload.token_endpoint, 'token_endpoint'),
        };
        onWarn?.(`xAI endpoints discovered (${new URL(endpoints.tokenEndpoint).host})`);
        cached = endpoints;
        return endpoints;
    };
}
/** Exchange an authorization code for tokens. */
export async function exchangeGrokCode(input) {
    const response = await input.fetchFn(input.tokenEndpoint, {
        method: 'POST',
        headers: { ...attributedHeaders(), 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
        body: new URLSearchParams({
            grant_type: 'authorization_code',
            client_id: GROK_CLIENT_ID,
            code: input.code,
            redirect_uri: input.redirectUri,
            code_verifier: input.pkce.verifier,
            code_challenge: input.pkce.challenge,
            code_challenge_method: 'S256',
        }).toString(),
        signal: input.signal,
    });
    const payload = await response.json().catch(() => undefined);
    if (!response.ok) {
        const detail = (payload ?? {});
        const described = nonEmpty(detail.error_description) ?? nonEmpty(detail.error);
        throw new Error(`xAI token exchange failed (HTTP ${response.status})${described === undefined ? '' : `: ${described}`}`);
    }
    return parseGrokTokenPayload(payload);
}
/** Redeem a refresh token. */
export async function refreshGrokToken(input) {
    const response = await input.fetchFn(input.tokenEndpoint, {
        method: 'POST',
        headers: { ...attributedHeaders(), 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
        body: new URLSearchParams({
            grant_type: 'refresh_token',
            client_id: GROK_CLIENT_ID,
            refresh_token: input.refreshToken,
        }).toString(),
    });
    const payload = await response.json().catch(() => undefined);
    if (!response.ok) {
        const detail = (payload ?? {});
        const described = nonEmpty(detail.error_description) ?? nonEmpty(detail.error);
        throw new ProviderError(`xAI token refresh failed (HTTP ${response.status})${described === undefined ? '' : `: ${described}`}`, REAUTH_REQUIRED);
    }
    return parseGrokTokenPayload(payload, input.refreshToken);
}
/** Description the picker shows for a model only the endpoint's listing describes. */
const GROK_LIVE_DESCRIPTION = 'Reported by the xAI model list';
/**
 * Split one cached or fresh payload into the two listings it holds.
 *
 * The payload is this route's own object, so its shape is validated rather than trusted:
 * a document written by an older build, or corrupted on disk, is a cache miss and never
 * a crash on the way to the picker.
 *
 * @param payload - the cached or freshly assembled `{ public, cli }` payload.
 * @returns the two row lists, or undefined when the payload is not this route's.
 */
function listingOf(payload) {
    if (typeof payload !== 'object' || payload === null || Array.isArray(payload))
        return undefined;
    const record = payload;
    const publicRows = record['public'];
    if (!Array.isArray(publicRows))
        return undefined;
    const cliRows = record['cli'];
    return { public: publicRows, cli: Array.isArray(cliRows) ? cliRows : [] };
}
/**
 * Read the capability metadata and the ids one CLI catalog answer named.
 *
 * The shape is xAI's own rather than the OpenAI-compatible one the shared row reader
 * handles: a context window under `context_window`, and a ladder under
 * `reasoning_efforts[].value` gated by `supports_reasoning_effort`. A row that states
 * nothing usable contributes only its id, because the id list is what folds a fast
 * variant onto its base row.
 *
 * @param rows - decoded rows of the CLI catalog answer.
 * @returns the metadata by model id, and every id the catalog named.
 */
function cliListingOf(rows) {
    const metadata = new Map();
    const ids = [];
    for (const row of rows) {
        if (typeof row !== 'object' || row === null || Array.isArray(row))
            continue;
        const entry = row;
        const rawId = entry['id'];
        const id = typeof rawId === 'string' && rawId.length > 0 ? rawId : undefined;
        if (id === undefined)
            continue;
        ids.push(id);
        const rawContext = entry['context_window'];
        const contextWindow = typeof rawContext === 'number' && Number.isFinite(rawContext) ? rawContext : undefined;
        const supportsEffort = entry['supports_reasoning_effort'] === true;
        const rawEfforts = entry['reasoning_efforts'];
        const efforts = supportsEffort && Array.isArray(rawEfforts)
            ? rawEfforts
                .map((effort) => (typeof effort === 'object' && effort !== null
                ? effort['value']
                : undefined))
                .filter((value) => typeof value === 'string' && value.length > 0)
            : [];
        const requested = entry['reasoning_effort'];
        const defaultEffort = supportsEffort && typeof requested === 'string' && efforts.includes(requested)
            ? requested
            : undefined;
        metadata.set(id, {
            ...contextWindow === undefined ? {} : { contextWindow },
            ...efforts.length === 0 ? {} : { reasoningEfforts: efforts },
            ...defaultEffort === undefined ? {} : { defaultEffort },
        });
    }
    return { metadata, ids };
}
/** Model ids that are clearly not chat models. */
const NON_CHAT_ID = /(?:^|[-_/])(?:image|video|imagine|embed|embedding|whisper|tts|vision-only)/i;
/** Model ids that are image-generation models, matched per provider's naming. */
const IMAGE_MODEL = /(?:^|[-_/])(?:image|imagine|draw|pic|photo)(?:[-_/]|$)/i;
/** True when a model id names an image-generation model. */
export function isImageModel(id) {
    return IMAGE_MODEL.test(id);
}
/**
 * Model ids that are the fast/lightspeed variant of a base model, matched per base.
 *
 * The vocabulary is the providers' own: xAI says `fast` (`grok-4.7-build-fast`), the
 * Claude/Codex world says `lightspeed` or `turbo`, and Kimi says `highspeed`
 * (`kimi-for-coding-highspeed`). A word has to stand on its own, so `highspeed` never
 * matches inside another word.
 */
const FAST_VARIANT = /(?:^|[-_@])(fast|lightspeed|turbo|quick|highspeed)(?=[-_@]|$)/i;
/** True when a model id names a fast/lightspeed variant of a base model. */
export function isFastVariant(id) {
    return FAST_VARIANT.test(id);
}
/** The fast word a variant's own id names (`grok-4.7-build-fast` → `fast`), lowercased. */
export function fastWordOf(id) {
    return FAST_VARIANT.exec(id)?.[1]?.toLowerCase() ?? 'fast';
}
/**
 * Every prefix of one model id, longest first, down to its first token.
 *
 * `grok-4.7-build-fast` → [`grok-4.7-build`, `grok-4.7`, `grok`]. A fast variant pairs
 * with the longest prefix that is itself a listed model id, which is how
 * `grok-4.7-build-fast` lands on `grok-4.7` (its own prefix `grok-4.7-build` is not a
 * model) instead of inventing a base that no catalog row serves.
 *
 * @param id - model id.
 * @returns the candidate bases, longest first.
 */
export function baseCandidatesOf(id) {
    const out = [];
    let at = id.length;
    while (at > 0) {
        at = id.lastIndexOf('-', at - 1);
        if (at <= 0)
            break;
        out.push(id.slice(0, at));
    }
    return out;
}
/**
 * The base model id a fast variant belongs to, derived only from its own name.
 *
 * The fast token is removed, then any trailing generation qualifier (`build`, `beta`,
 * `rc`, …) left behind by that removal, then any `@Nk` context suffix. What remains is
 * the base (`grok-4.7-build-fast@500k` → `grok-4.7`, `kimi-for-coding-highspeed` →
 * `kimi-for-coding`). Whether the base is a real catalog row is the index's job, not
 * this function's.
 */
export function baseModelOf(id) {
    return id
        .replace(FAST_VARIANT, '')
        .replace(/@\d+k$/i, '')
        .replace(/[-_]$/, '')
        .replace(/-(?:build|beta|alpha|rc|stable)$/i, '')
        .replace(/[-_]$/, '');
}
/**
 * The fast variant of one picker row, from either side of the pair.
 *
 * Shared by every route that discovers fast ids, so the composer's quick-mode button
 * behaves the same wherever a fast model is identifiable: it finds the pair when asked
 * about the base row, and when asked about the fast id itself — which is what the
 * selection becomes once the mode is on — so it can switch back off again.
 */
export class FastVariantIndex {
    byBase = new Map();
    rowOfVariant = new Map();
    knownIds = new Set();
    /** Forget every pair and every known id, before re-reading a catalog. */
    clear() {
        this.byBase.clear();
        this.rowOfVariant.clear();
        this.knownIds.clear();
    }
    /**
     * Record one id the catalog listed.
     *
     * Listed ids are what a variant may pair with: the pair is the longest listed prefix
     * of the variant's own name, so nothing is invented that no row actually serves.
     *
     * @param id - a wire id from a catalog.
     */
    noteListed(id) {
        this.knownIds.add(id);
    }
    /**
     * Pair one fast id with its base, purely from its own name.
     *
     * Every listed prefix of the id is a candidate base, longest first; the first one
     * that is itself a listed model wins. No curated alias or hardcoded map is needed:
     * a provider that names its variants after the base model pairs them automatically.
     *
     * @param id - a wire id the catalog named.
     * @returns the base id, or undefined when no listed prefix names one.
     */
    pair(id) {
        if (!isFastVariant(id))
            return undefined;
        for (const base of baseCandidatesOf(id)) {
            if (this.knownIds.has(base))
                return base;
        }
        return undefined;
    }
    /**
     * Record one id, if it names a fast variant.
     *
     * The first variant named for a row wins: a catalog that lists two spellings of the
     * same variant (`kimi-for-coding-highspeed` and `kimi-k2.7-code-highspeed`) offers
     * one button, and the earlier entry is the more specific one.
     *
     * @param id - a wire id from a catalog.
     * @param rowOf - maps a base id to the picker row that serves it, when the catalog
     *   spells the base differently from the variant's own prefix.
     */
    note(id, rowOf) {
        if (!isFastVariant(id))
            return;
        const base = baseModelOf(id);
        if (base.length === 0)
            return;
        const row = rowOf?.(base) ?? base;
        if (!this.byBase.has(row))
            this.byBase.set(row, id);
        if (!this.rowOfVariant.has(id))
            this.rowOfVariant.set(id, row);
    }
    /** The fast variant's wire id for one picker row, when the catalog named one. */
    variantOf(baseId) {
        return this.byBase.get(baseId);
    }
    /** The pair one picker id belongs to, from either side. */
    pairOf(id) {
        const direct = this.byBase.get(id);
        if (direct !== undefined)
            return { baseId: id, wireId: direct };
        const row = this.rowOfVariant.get(id);
        return row === undefined ? undefined : { baseId: row, wireId: id };
    }
}
/** A context-size suffix like `@500k` on a model id. */
const CONTEXT_SUFFIX = /@(\d+)k$/i;
/** Extract the context size from a model id's `@Nk` suffix, or undefined. */
export function contextOf(id) {
    const match = CONTEXT_SUFFIX.exec(id);
    return match ? parseInt(match[1], 10) : undefined;
}
/** Strip the `@Nk` context suffix from a model id. */
export function stripContext(id) {
    return id.replace(CONTEXT_SUFFIX, '');
}
/** Grok wire adapter: one instance serves the `grok` provider route. */
export class GrokAdapter extends LlmAdapter {
    options;
    /**
     * The catalog this route serves: entries derived from the endpoint's own two
     * listings, either the pair just read or the pair the cache kept from the last read.
     *
     * Undefined means neither source has produced a listing yet, so the cold-start seed
     * is all this build can honestly offer.
     */
    live;
    /** Whether the cached listing has been read; it is read at most once per session. */
    hydrated = false;
    /** Capability metadata from the CLI catalog, keyed by model id. */
    metadata = new Map();
    /** Fast-variant wire id per base model, for the picker's quick-mode button. */
    fastVariants = new FastVariantIndex();
    /** The context variant one base row dispatches to, so `@500k` is never typed by hand. */
    defaultContext = new Map();
    /** Image-generation model id, when the listing reported one. */
    imageModel;
    /** Ids the user added by hand, cached so the sync catalog can name them. */
    userAddedIds = [];
    constructor(options) {
        super();
        this.options = options;
    }
    /**
     * Adopt the two listings of one live read as this route's catalog.
     *
     * Everything the picker shows is derived here from the endpoint's own answers: the
     * public `/models` rows decide membership and state each model's display name, window
     * and modalities, and the account's CLI catalog states the reasoning ladder and names
     * the fast variants — the one thing the public list omits.
     *
     * A fast variant or an `@Nk` context variant rides with its base row instead of becoming
     * a row of its own: the variant is a wire id the composer dispatches, never a picker
     * row, so the user always has a way back to the standard model.
     *
     * @param payload - the cached or freshly assembled `{ public, cli }` payload.
     * @returns the entries to publish, in the endpoint's own order; undefined when the
     *   payload names no model at all, so the caller keeps whatever it already had.
     */
    adoptListing(payload) {
        const listing = listingOf(payload);
        if (listing === undefined)
            return undefined;
        const facts = listing.public
            .map((row) => liveModelFacts(row))
            .filter((fact) => fact !== undefined);
        const cli = cliListingOf(listing.cli);
        const chat = facts.filter((fact) => !NON_CHAT_ID.test(fact.id) && !isImageModel(fact.id));
        // Membership comes from the public list, so a payload that named none of it is a miss
        // rather than an empty picker.
        if (chat.length === 0)
            return undefined;
        // One payload is the whole truth, so every table derived from an earlier read is
        // replaced rather than merged: a model the endpoint has retired cannot survive in
        // state left behind by a read that no longer names it.
        this.metadata.clear();
        this.fastVariants.clear();
        this.defaultContext.clear();
        for (const [id, meta] of cli.metadata)
            this.metadata.set(id, meta);
        // Image models are never picker rows: an agent calls them, always on.
        this.imageModel = [...facts.map((fact) => fact.id), ...cli.ids].find((id) => isImageModel(id));
        // Every id either listing named is what a variant may pair with; the pairing itself is
        // the longest listed prefix of the variant's own name.
        const wireIds = [
            ...chat.map((fact) => fact.id),
            ...cli.ids.filter((id) => !NON_CHAT_ID.test(id) && !isImageModel(id)),
        ];
        for (const id of wireIds)
            this.fastVariants.noteListed(id);
        this.noteWireVariants(chat.map((fact) => fact.id));
        this.noteWireVariants(cli.ids);
        // Fold each variant onto its row, keeping the endpoint's order. A row's own facts are
        // its identity and win outright; a variant only fills what they omit, except a context
        // variant, whose window is the one the row actually dispatches to.
        const own = new Map();
        const folds = new Map();
        const windows = new Map();
        const order = [];
        for (const fact of chat) {
            const contextVariant = contextOf(fact.id) !== undefined;
            const rowId = isFastVariant(fact.id)
                ? this.fastVariants.pair(fact.id) ?? baseModelOf(fact.id)
                : contextVariant ? stripContext(fact.id) : fact.id;
            if (rowId === '')
                continue;
            if (!own.has(rowId) && !folds.has(rowId))
                order.push(rowId);
            if (contextVariant)
                windows.set(rowId, Math.max(windows.get(rowId) ?? 0, fact.contextWindow ?? 0));
            if (rowId === fact.id) {
                if (!own.has(rowId))
                    own.set(rowId, fact);
            }
            else {
                folds.set(rowId, [...folds.get(rowId) ?? [], fact]);
            }
        }
        return order.map((rowId) => {
            const stated = own.get(rowId);
            const variants = folds.get(rowId) ?? [];
            const fill = (pick) => {
                const ownValue = stated === undefined ? undefined : pick(stated);
                if (ownValue !== undefined)
                    return ownValue;
                for (const variant of variants) {
                    const value = pick(variant);
                    if (value !== undefined)
                        return value;
                }
                return undefined;
            };
            const widest = windows.get(rowId);
            const statedWindow = stated?.contextWindow;
            const contextWindow = widest === undefined || (statedWindow ?? 0) >= widest ? statedWindow : widest;
            const inputModalities = fill((fact) => fact.inputModalities);
            const reasoningEfforts = fill((fact) => fact.reasoningEfforts);
            const defaultEffort = fill((fact) => fact.defaultEffort);
            // The CLI catalog is a listing too, so its figures only fill what the public row
            // left silent: a window or a ladder the endpoint already stated is never overridden,
            // and the shipped seed never overrides either of them.
            const meta = [rowId, ...variants.map((variant) => variant.id)]
                .map((id) => this.metadata.get(id))
                .find((entry) => entry !== undefined);
            const context = contextWindow ?? meta?.contextWindow;
            const efforts = reasoningEfforts ?? meta?.reasoningEfforts;
            const metaDefault = meta?.defaultEffort;
            // A default the ladder does not offer would leave the picker with a rung it cannot
            // select, so it is stated only when the ladder that won actually contains it.
            const fallback = defaultEffort
                ?? (metaDefault === undefined || efforts !== undefined && !efforts.includes(metaDefault) ? undefined : metaDefault);
            const described = {
                ...stated ?? { id: rowId },
                ...context === undefined ? {} : { contextWindow: context },
                ...efforts === undefined ? {} : { reasoningEfforts: efforts },
                ...fallback === undefined ? {} : { defaultEffort: fallback },
                ...inputModalities === undefined ? {} : { inputModalities },
            };
            return liveEntry(described, GROK_MODELS, GROK_CONTEXT_WINDOW, GROK_LIVE_DESCRIPTION);
        });
    }
    /**
     * Read the cached listing, once, so the picker works without asking the endpoint.
     *
     * Called before the first catalog query, and fired at construction by the route's
     * factory because the Settings page reads the catalog synchronously. A read failure is
     * a cache miss, never a failure: the route simply has no listing until one is fetched.
     */
    async hydrate() {
        if (this.hydrated)
            return;
        this.hydrated = true;
        if (this.live !== undefined || this.options.cachedListing === undefined)
            return;
        try {
            const entries = this.adoptListing(await this.options.cachedListing());
            if (entries !== undefined)
                this.live = entries;
        }
        catch (error) {
            this.options.onWarn?.(`cached model catalog unavailable (${errorChain(error)})`);
        }
    }
    providerInfo(provider) {
        return { id: provider, name: GROK_PROVIDER_NAME };
    }
    providerRetryPolicy() {
        // xAI reports its own pacing through 429 `retry-after`.
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
        return resolveCatalog(imageless, [], hidden).entries.map((entry) => ({
            provider: GROK_PROVIDER_ID,
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
        catch (error) {
            this.options.onWarn?.(`model preferences unavailable (${errorChain(error)})`);
            return new Set();
        }
    }
    async resolveModel(_provider, model) {
        await this.hydrate();
        await this.syncUserModels();
        // A fast variant is not a catalog row of its own: it answers with its base model's
        // metadata — the same window, name and ladder the picker shows — while the id
        // stays the fast wire id the request must send.
        const pair = this.fastPairOf(model);
        const entry = findEntry(this.catalog(), model)
            ?? (pair === undefined ? undefined : findEntry(this.catalog(), pair.baseId));
        if (entry === undefined) {
            // An unknown id is still dispatchable: the endpoint accepts the id it reports, and a
            // model added after this build should not be refused. It gets the conservative
            // defaults, described by the cold-start seed rather than by a guess.
            const template = findEntry(GROK_MODELS, 'grok-4.6');
            const reasoning = template === undefined ? undefined : reasoningInfo(template);
            return {
                provider: GROK_PROVIDER_ID,
                id: model,
                name: model,
                context: { contextWindow: GROK_CONTEXT_WINDOW },
                defaultMaxTokens: GROK_DEFAULT_MAX_TOKENS,
                ...reasoning === undefined ? {} : { reasoning },
            };
        }
        // A row with a fast variant offers a rung named for the variant's own word; the
        // picker shows the row under its own name and the mode as the effort.
        const fastWireId = this.fastVariants.variantOf(entry.id);
        const reasoning = reasoningInfo(entry, fastWireId);
        return {
            provider: GROK_PROVIDER_ID,
            id: model,
            name: entry.name,
            ...entry.description === undefined ? {} : { description: entry.description },
            ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
            context: { contextWindow: entry.contextWindow },
            defaultMaxTokens: GROK_DEFAULT_MAX_TOKENS,
            ...reasoning === undefined ? {} : { reasoning },
        };
    }
    stream(options) {
        // Resolve @Nk context suffixes to the largest variant the endpoint offers.
        const base = this.resolveModelId(options.model);
        // Fast mode rides the reasoning channel: the rung is the variant's own fast word, not
        // a model id of its own, so the row the picker selected stays dispatchable and the user
        // can always select the standard model again. Only the wire id changes.
        const effort = String(options.reasoningEffort ?? '');
        const fastWireId = this.fastVariants.variantOf(base);
        const fast = fastWireId !== undefined && effort === fastWordOf(fastWireId);
        const model = fast ? fastWireId : base;
        const call = { ...options, model };
        // xAI has no fast-variant effort; the variant already carries the behaviour.
        if (fast)
            delete call.reasoningEffort;
        return streamProviderResponses(call, {
            label: 'xAI API',
            tokens: this.options.tokens,
            request: (call, model, accessToken, signal) => this.request(call, model, accessToken, signal),
            streamIdleTimeoutMs: this.options.streamIdleTimeoutMs,
            // A 402/429 means this account's allowance is spent: draw on the next account's.
            isAllowanceExhausted: (status) => status === 402 || status === 429,
            ...accountPoolHooks({ provider: GROK_PROVIDER_ID, tokens: this.options.tokens, readUsage: this.options.readUsage }),
            ...this.options.onCredentialRejected === undefined
                ? {}
                : { onCredentialRejected: this.options.onCredentialRejected },
            onFallback: (from, to, reason) => {
                this.options.onWarn?.(`grok: model ${from} unavailable (${reason}); falling back to ${to}`);
            },
            describeFailure: (error) => (error instanceof ProviderError && error.code === REAUTH_REQUIRED
                ? new LlmError(`Grok (X Premium) login expired; sign in again from Settings → Subscriptions (${GROK_CODE_URL})`, 'INVALID_CREDENTIAL', { status: 401 })
                : error),
        });
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
     * The catalog to present: the models both of the endpoint's listings named, in the
     * public list's own order, each with the metadata the listings stated.
     *
     * There is no shipped roster to fall back to. The listings are read once and cached
     * (see {@link hydrate} and {@link fetchCatalog}), and that cached table is what the
     * picker shows until the next read, so a model released, renamed or retired
     * server-side is reflected without a plugin update or a request per refresh.
     *
     * {@link GROK_MODELS} is only the cold-start seed: it decides membership while neither
     * listing has been read, and afterwards describes the fields the listings left silent.
     * A live fact, a context window included, always wins over it.
     */
    shippedCatalog() {
        return composeCatalog({
            curated: GROK_MODELS,
            ...this.live === undefined ? {} : { live: this.live },
            fallbackContextWindow: GROK_CONTEXT_WINDOW,
            liveDescription: GROK_LIVE_DESCRIPTION,
        });
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
     * The entries for ids the user added by hand.
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
            .map((id) => fallbackEntry(id, 'Added by hand in Settings', GROK_CONTEXT_WINDOW));
    }
    /**
     * Adopt the ids a caller reports as the served set, without a listing.
     *
     * {@link fetchCatalog} already derived the entries from the rows it read, and the
     * controller reports the union of every account's ids back here; the entries it
     * recorded are kept, and an id no entry describes gets a conservative row described by
     * the cold-start seed. An id the caller does not report is dropped: the reported set is
     * the endpoint's whole answer.
     *
     * @param ids - model ids the caller reports.
     */
    discover(ids) {
        const known = new Map((this.live ?? []).map((entry) => [entry.id, entry]));
        this.live = ids.map((id) => known.get(id)
            ?? liveEntry({ id }, GROK_MODELS, GROK_CONTEXT_WINDOW, GROK_LIVE_DESCRIPTION));
    }
    /** The fast/lightspeed variant wire id for a base model, when discovery reported one. */
    fastVariant(baseId) {
        return this.fastVariants.variantOf(baseId);
    }
    /** The fast pair one picker id belongs to, from either side. */
    fastPairOf(id) {
        return this.fastVariants.pairOf(id);
    }
    /** The image-generation model id, when discovery reported one. */
    imageModelId() {
        return this.imageModel;
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
    /** The curated catalog, for the Settings page's model list. */
    catalogEntries() {
        return this.catalog();
    }
    /** What this route itself serves, without the ids added by hand. */
    ownCatalogEntries() {
        return this.shippedCatalog();
    }
    /** Build and send one request for one model. */
    /** Resolve a model id to the wire id the endpoint accepts (largest context). */
    resolveModelId(model) {
        return this.defaultContext.get(model) ?? model;
    }
    /** Record one default-context mapping. */
    noteContextVariant(base, context, wireId) {
        const existing = this.defaultContext.get(base);
        if (existing !== undefined) {
            const existingCtx = contextOf(existing);
            if (existingCtx !== undefined && existingCtx >= context)
                return;
        }
        this.defaultContext.set(base, wireId);
    }
    async request(options, model, accessToken, signal) {
        const resolved = await toResponsesInput(options.messages, options.system, this.imageResolver(signal));
        const body = {
            model,
            ...resolved.instructions === undefined ? {} : { instructions: resolved.instructions },
            input: resolved.input,
            ...options.tools === undefined || options.tools.length === 0
                ? {}
                : { tools: toResponsesTools(options.tools), tool_choice: 'auto', parallel_tool_calls: true },
            ...options.maxTokens === undefined ? {} : { max_output_tokens: options.maxTokens },
            ...options.reasoningEffort === undefined
                ? {}
                : { reasoning: { effort: String(options.reasoningEffort) } },
            // Cache-affinity hint: xAI routes a repeated request back to the shard
            // holding its prompt cache only when the same key returns.
            ...options.sessionId === undefined ? {} : { prompt_cache_key: String(options.sessionId) },
            store: false,
            stream: true,
        };
        const fetchFn = this.options.fetchFn ?? proxiedFetch;
        return fetchFn(`${this.options.apiBase}/responses`, {
            method: 'POST',
            headers: attributedHeaders({
                'authorization': `Bearer ${accessToken}`,
                'accept': 'text/event-stream',
                'content-type': 'application/json',
            }),
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
                this.options.onWarn?.(`image attachment unavailable (${errorChain(error)})`);
                return undefined;
            }
        };
    }
    /**
     * Read both of the endpoint's listings and adopt them as this route's catalog.
     *
     * This is the one place a model table is produced, and it is the endpoint's own answer:
     * what the public list names is what the picker offers, and the account's CLI catalog
     * states the capabilities and names the fast variants that list omits. The rows are also
     * handed to {@link GrokAdapterOptions.saveListing}, so the next session starts from this
     * table instead of asking again — the Settings "refresh models" action is what re-reads
     * it, and a picker query never does.
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
            const response = await fetchFn(`${this.options.apiBase}/models`, {
                headers: attributedHeaders({ 'authorization': `Bearer ${accessToken}` }),
                ...signal === undefined ? {} : { signal },
            });
            if (!response.ok) {
                // A refused credential is reported by the card's account list; only a
                // genuine discovery failure is worth a warning.
                if (response.status !== 401 && response.status !== 403) {
                    this.options.onWarn?.(`xAI model discovery failed (HTTP ${response.status}); keeping the last catalog`);
                }
                return undefined;
            }
            const payload = await response.json();
            const publicRows = Array.isArray(payload.data) ? payload.data : [];
            const cliRows = await this.readCliCatalog(fetchFn, signal, accessToken);
            const listing = { public: publicRows, cli: cliRows };
            const entries = this.adoptListing(listing);
            if (entries === undefined) {
                this.options.onWarn?.('xAI model discovery returned no usable catalog; keeping the last catalog');
                return undefined;
            }
            this.live = entries;
            // The cached table is the endpoint's answer, kept so the next session does not ask
            // again. A cache failure never fails the read that produced it.
            if (this.options.saveListing !== undefined) {
                try {
                    await this.options.saveListing(listing);
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
            this.options.onWarn?.(`xAI model discovery failed; keeping the last catalog (${errorChain(error)})`);
            return undefined;
        }
    }
    /**
     * Fold the fast and context variants of one id list into their base models.
     *
     * @param ids - wire ids to classify.
     */
    noteWireVariants(ids) {
        for (const id of ids) {
            if (NON_CHAT_ID.test(id) || isImageModel(id))
                continue;
            if (isFastVariant(id)) {
                // Pair with the longest listed prefix, not a derived string: the prefix is a
                // model the account actually serves, so the button always targets a real row.
                const base = this.fastVariants.pair(id);
                this.fastVariants.note(id, () => base);
                continue;
            }
            const ctx = contextOf(id);
            if (ctx !== undefined)
                this.noteContextVariant(stripContext(id), ctx, id);
        }
    }
    /**
     * Read the account's CLI catalog; a failure is an empty listing, not an error.
     *
     * The catalog answers only for the signed-in account's own bearer — an anonymous
     * request is refused — and it, unlike the public `/models` list, states per-model
     * context windows and reasoning ladders and names the account's fast variant. The rows
     * are returned verbatim so both the fresh catalog and the cached payload are derived
     * from the same parse.
     *
     * @param fetchFn - fetch implementation.
     * @param signal - caller cancellation.
     * @param accessToken - the account's bearer.
     * @returns the decoded rows, or an empty list when the catalog is unavailable.
     */
    async readCliCatalog(fetchFn, signal, accessToken) {
        try {
            const response = await fetchFn(GROK_CLI_MODELS_URL, {
                headers: attributedHeaders({ 'authorization': `Bearer ${accessToken}` }),
                ...signal === undefined ? {} : { signal },
            });
            if (!response.ok)
                return [];
            const payload = await response.json();
            return Array.isArray(payload.data) ? payload.data : [];
        }
        catch {
            // Capability metadata is a bonus; the id list is what matters.
            return [];
        }
    }
}
/**
 * Build the `grok` route: its adapter and its login/lifecycle controller.
 *
 * @param options - resolved paths and shared services.
 * @returns the route, ready to register and to serve over RPC.
 */
export function createGrokProvider(options) {
    const fetchFn = options.fetchFn ?? proxiedFetch;
    const apiBase = options.apiBase ?? GROK_API_BASE;
    const store = new TokenStore(options.credentialFile, GROK_PROVIDER_ID, parseGrokSession);
    const discoverEndpoints = createGrokDiscovery(fetchFn, options.onWarn);
    /** Endpoints resolved by `prepare`, read synchronously while building URLs. */
    let endpointsSnapshot;
    const flow = new LoopbackFlow(GROK_PROVIDER_ID, {
        ports: [GROK_CALLBACK_PORT],
        callbackPath: GROK_CALLBACK_PATH,
        prepare: async () => { endpointsSnapshot = await discoverEndpoints(); },
        buildAuthorizeUrl: ({ redirectUri, state, pkce }) => {
            // `prepare` has already resolved and cached the endpoints.
            const endpoints = endpointsSnapshot;
            if (endpoints === undefined)
                throw new Error('xAI endpoints were not discovered');
            const url = new URL(endpoints.authorizationEndpoint);
            url.searchParams.set('response_type', 'code');
            url.searchParams.set('client_id', GROK_CLIENT_ID);
            url.searchParams.set('redirect_uri', redirectUri);
            url.searchParams.set('scope', GROK_SCOPE);
            url.searchParams.set('state', state);
            url.searchParams.set('code_challenge', pkce.challenge);
            url.searchParams.set('code_challenge_method', 'S256');
            return url.toString();
        },
        exchange: async ({ code, redirectUri, pkce, signal }) => {
            const endpoints = endpointsSnapshot ?? await discoverEndpoints();
            const tokens = await exchangeGrokCode({
                fetchFn,
                tokenEndpoint: endpoints.tokenEndpoint,
                code,
                redirectUri,
                pkce,
                signal,
            });
            return {
                accessToken: tokens.accessToken,
                ...tokens.refreshToken === undefined ? {} : { refreshToken: tokens.refreshToken },
                expiresAt: tokens.expiresAt,
                tokenEndpoint: endpoints.tokenEndpoint,
                issuer: GROK_ISSUER,
                scopes: GROK_SCOPE,
                ...identityFromGrokTokens(tokens.accessToken, tokens.idToken),
            };
        },
    });
    const tokens = new TokenManager({
        store,
        displayName: GROK_PROVIDER_NAME,
        preemptMs: GROK_PREEMPT_MS,
        refresh: async (session) => {
            const refreshToken = session.refreshToken;
            if (refreshToken === undefined) {
                throw new ProviderError(`${GROK_PROVIDER_NAME} account has no refresh token; sign in again`, REAUTH_REQUIRED);
            }
            const refreshed = await refreshGrokToken({
                fetchFn,
                tokenEndpoint: session.tokenEndpoint,
                refreshToken,
            });
            return {
                accessToken: refreshed.accessToken,
                expiresAt: refreshed.expiresAt,
                ...refreshed.refreshToken === undefined ? {} : { refreshToken: refreshed.refreshToken },
            };
        },
    });
    const adapter = new GrokAdapter({
        apiBase,
        discovery: options.discovery,
        streamIdleTimeoutMs: options.streamIdleTimeoutMs,
        tokens,
        ...options.onWarn === undefined ? {} : { onWarn: options.onWarn },
        ...options.resolveAttachments === undefined ? {} : { resolveAttachments: options.resolveAttachments },
        hiddenModels: () => options.preferences.hiddenIds(GROK_PROVIDER_ID),
        userModels: () => options.preferences.extraIds(GROK_PROVIDER_ID),
        modelOrder: () => options.preferences.orderOf(GROK_PROVIDER_ID),
        readUsage: async (account) => {
            const token = await tokens.resolve(account);
            const read = await readGrokUsage({ accessToken: token.accessToken, fetchFn: options.discoveryFetchFn ?? proxiedFetch });
            return read === undefined ? [] : read.windows;
        },
        ...options.cachedListing === undefined ? {} : { cachedListing: options.cachedListing },
        ...options.saveListing === undefined ? {} : { saveListing: options.saveListing },
        ...options.discoveryFetchFn === undefined ? {} : { fetchFn: options.discoveryFetchFn },
    });
    // Read the cached table now rather than at the first picker query: the Settings page
    // reads the catalog synchronously, so a request-free start must already have it.
    void adapter.hydrate().catch(() => undefined);
    const spec = {
        id: GROK_PROVIDER_ID,
        displayName: GROK_PROVIDER_NAME,
        codeUrl: GROK_CODE_URL,
        credentialFile: options.credentialFile,
        discovery: options.discovery,
        defaultModel: 'grok-4.6',
        catalog: () => adapter.catalogEntries(),
        catalogOwn: () => adapter.ownCatalogEntries(),
        fallbackContextWindow: GROK_CONTEXT_WINDOW,
        discover: (accessToken) => adapter.fetchCatalog(accessToken),
        usage: (accessToken) => readGrokUsage({ accessToken, fetchFn: options.discoveryFetchFn ?? fetchFn }),
        details: () => [
            { key: 'oauthHost', value: GROK_ISSUER },
            { key: 'apiBase', value: apiBase },
            { key: 'credentialFile', value: options.credentialFile },
        ],
        applyDiscovery: (ids) => { adapter.discover(ids); },
        fastVariant: (baseId) => adapter.fastVariant(baseId),
    };
    const controller = new ProviderController({
        spec,
        store,
        tokens,
        flow,
        preferences: options.preferences,
        ...options.onCatalogChanged === undefined ? {} : { onCatalogChanged: options.onCatalogChanged },
        ...options.onWarn === undefined ? {} : { onWarn: options.onWarn },
    });
    return {
        id: controller.id,
        displayName: controller.displayName,
        credentialFile: controller.credentialFile,
        adapter,
        status: () => controller.status(),
        login: (account) => controller.login(account),
        cancel: () => controller.cancel(),
        logout: (account) => controller.logout(account),
        setDefault: (account) => controller.setDefault(account),
        refresh: (account) => controller.refresh(account),
        discover: (force) => controller.discover(force),
        setModelVisible: (id, visible) => controller.setModelVisible(id, visible),
        setModelOrder: (ids) => controller.setModelOrder(ids),
        addModel: (id) => controller.addModel(id),
        removeModel: (id) => controller.removeModel(id),
        usage: (force) => controller.usage(force),
        reload: () => controller.reload(),
        modelVariants: async (id) => {
            // A variant is known only once the route has read its listing, whose rows name it.
            // The composer asks exactly once, so the read is awaited here: "no fast variant"
            // must mean the route really has none, not that its table was still loading.
            await adapter.hydrate();
            const pair = adapter.fastPairOf(id);
            const image = adapter.imageModelId();
            return {
                ...pair === undefined ? {} : { fast: pair.wireId, fastBase: pair.baseId, fastRung: fastWordOf(pair.wireId) },
                ...image === undefined ? {} : { image },
                // Nothing read yet: the answer above is provisional, and the caller is told so
                // rather than being left to read silence as "this model has no variant".
                ...adapter.hasListing() ? {} : { pending: true },
            };
        },
    };
}
