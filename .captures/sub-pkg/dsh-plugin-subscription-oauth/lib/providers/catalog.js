/**
 * Provider model catalogs.
 *
 * A provider ships a curated catalog — the ids it serves, their display names,
 * context windows, reasoning ladder, and any aliases the endpoint also accepts.
 * Live discovery may widen it; it never narrows it, because an endpoint's own
 * listing omits variants it still serves.
 *
 * The `auto` provider is not part of any of these: it spans them, and lives in
 * its own module.
 *
 * @module dsh-plugin-subscription-oauth/providers/catalog
 */
/** Whether one catalog entry covers a model id. */
export function entryMatches(entry, id) {
    return entry.id === id || entry.aliases?.includes(id) === true;
}
/** The entry covering a model id, when the catalog has one. */
export function findEntry(entries, id) {
    return entries.find((entry) => entryMatches(entry, id));
}
/**
 * Build an entry for a model the curated catalog does not describe.
 *
 * Used for ids the live list reports that this build has not seen, and for ids
 * the plugin configuration adds: they are dispatchable, but their metadata is
 * the conservative default rather than a guess.
 *
 * @param id - model id.
 * @param description - why this entry exists, shown in the picker.
 * @param contextWindow - context to advertise.
 * @returns the entry.
 */
export function fallbackEntry(id, description, contextWindow) {
    return {
        id,
        name: id,
        description,
        contextWindow,
        inputModalities: ['text', 'image'],
        reasoningEfforts: ['low', 'medium', 'high', 'max'],
        defaultEffort: 'max',
    };
}
/** Read a non-empty string field. */
function stringField(value) {
    return typeof value === 'string' && value.trim() !== '' ? value.trim() : undefined;
}
/** Read a positive integer field. */
function numberField(value) {
    if (typeof value === 'number' && Number.isFinite(value) && value > 0)
        return Math.floor(value);
    if (typeof value === 'string') {
        const parsed = Number(value);
        if (Number.isFinite(parsed) && parsed > 0)
            return Math.floor(parsed);
    }
    return undefined;
}
/**
 * The input modalities a listing states, in the harness vocabulary.
 *
 * Listings name modalities the harness has no word for (`video`, `audio`); those are
 * dropped rather than smuggled through the type, so a capability is advertised only
 * where the harness can act on it.
 */
function modalitiesOf(row) {
    const stated = [];
    const modalities = row['modalities'];
    if (typeof modalities === 'object' && modalities !== null) {
        const input = modalities['input'];
        if (Array.isArray(input))
            stated.push(...input);
    }
    for (const [field, modality] of [['supports_image_in', 'image'], ['supports_image_input', 'image']]) {
        if (row[field] === true)
            stated.push(modality);
    }
    if (stated.length === 0)
        return undefined;
    const out = [];
    for (const value of stated) {
        if (value === 'text' && !out.includes('text'))
            out.push('text');
        if (value === 'image' && !out.includes('image'))
            out.push('image');
    }
    return out.length === 0 ? undefined : out;
}
/** The reasoning ladder a listing states, from either of the shapes these APIs use. */
function effortsOf(row) {
    const think = row['think_efforts'];
    if (typeof think === 'object' && think !== null) {
        const record = think;
        const valid = Array.isArray(record['valid_efforts'])
            ? record['valid_efforts'].filter((effort) => typeof effort === 'string' && effort !== '')
            : [];
        const fallback = stringField(record['default_effort']);
        return {
            ...valid.length === 0 ? {} : { efforts: valid },
            ...fallback === undefined ? {} : { defaultEffort: fallback },
        };
    }
    const reasoning = row['reasoning'];
    if (typeof reasoning === 'object' && reasoning !== null) {
        const effort = reasoning['effort'];
        if (typeof effort === 'object' && effort !== null) {
            const record = effort;
            const valid = Array.isArray(record['valid'])
                ? record['valid'].filter((value) => typeof value === 'string' && value !== '')
                : [];
            const fallback = stringField(record['default']);
            return {
                ...valid.length === 0 ? {} : { efforts: valid },
                ...fallback === undefined ? {} : { defaultEffort: fallback },
            };
        }
    }
    return {};
}
/**
 * Read the facts one live listing row states.
 *
 * The field names are the ones these providers actually use, including Kimi's
 * `display_name`/`context_length`/`think_efforts` and the OpenAI-compatible
 * `context_window`/`name` spellings; anything unrecognized is left absent rather than
 * guessed, so a curated entry can still describe the model.
 *
 * @param row - one decoded row of a `/models` payload.
 * @returns the facts, or undefined when the row names no model.
 */
export function liveModelFacts(row) {
    if (typeof row !== 'object' || row === null || Array.isArray(row))
        return undefined;
    const record = row;
    const id = stringField(record['id']) ?? stringField(record['model']);
    if (id === undefined)
        return undefined;
    const name = stringField(record['display_name']) ?? stringField(record['name']);
    const description = stringField(record['description']);
    const limit = record['limit'];
    const contextWindow = numberField(record['context_length'])
        ?? numberField(record['context_window'])
        ?? numberField(record['max_context_length'])
        ?? numberField(typeof limit === 'object' && limit !== null
            ? limit['context']
            : undefined);
    const inputModalities = modalitiesOf(record);
    const { efforts, defaultEffort } = effortsOf(record);
    return {
        id,
        ...name === undefined ? {} : { name },
        ...description === undefined ? {} : { description },
        ...contextWindow === undefined ? {} : { contextWindow },
        ...inputModalities === undefined ? {} : { inputModalities },
        ...efforts === undefined ? {} : { reasoningEfforts: efforts },
        ...defaultEffort === undefined ? {} : { defaultEffort },
    };
}
/**
 * The entry to publish for one id the endpoint served.
 *
 * What the endpoint stated wins; the curated table fills only what the listing was
 * silent about, so a live figure — the window a model actually serves today — is
 * never overridden by a shipped guess, and a retired figure never survives a live
 * read that contradicts it. A route whose ids are stable aliases the service re-points
 * per release keeps its own labels instead (see the kimi route's alias labels).
 *
 * @param facts - what the listing stated for this id.
 * @param curated - the curated metadata table.
 * @param fallbackContextWindow - window to state when neither source names one.
 * @param description - picker description when neither source states one.
 * @returns the merged entry.
 */
export function liveEntry(facts, curated, fallbackContextWindow, description) {
    const known = findEntry(curated, facts.id);
    const contextWindow = facts.contextWindow ?? known?.contextWindow ?? fallbackContextWindow;
    const name = facts.name ?? known?.name ?? facts.id;
    const summary = facts.description ?? known?.description ?? description;
    const inputModalities = facts.inputModalities ?? known?.inputModalities;
    const reasoningEfforts = facts.reasoningEfforts ?? known?.reasoningEfforts;
    const defaultEffort = facts.defaultEffort ?? known?.defaultEffort;
    return {
        id: known?.id ?? facts.id,
        name,
        description: summary,
        contextWindow,
        ...inputModalities === undefined ? {} : { inputModalities },
        ...reasoningEfforts === undefined ? {} : { reasoningEfforts },
        ...defaultEffort === undefined ? {} : { defaultEffort },
    };
}
/**
 * Compose one provider's published catalog.
 *
 * The membership is the live entries in the endpoint's own order when a listing has
 * been read, and the curated order otherwise. The configured default comes first —
 * the picker preselects it, so it must be selectable — and configured extras last.
 * Metadata always comes from the source that described the id: a live row is never
 * shadowed by the curated table or by the default seed, so what the endpoint states
 * about a model is what the picker shows.
 *
 * @param plan - curated metadata, the live membership, and the fallbacks.
 * @param refine - widens an entry with provider-side metadata, when the provider has it.
 * @returns the entries to publish, in picker order.
 */
export function composeCatalog(plan, refine = (entry) => entry) {
    const membership = plan.live ?? plan.curated;
    const described = new Map(membership.map((entry) => [entry.id, entry]));
    const source = plan.live === undefined
        ? 'Built-in catalog'
        : plan.liveDescription ?? 'Reported by the provider model list';
    const wanted = [];
    if (plan.defaultModel !== undefined && plan.defaultModel !== '') {
        wanted.push({ id: plan.defaultModel, description: plan.defaultDescription ?? 'Default model for this route' });
    }
    for (const entry of membership)
        wanted.push({ id: entry.id, description: source });
    for (const id of plan.extra ?? []) {
        wanted.push({ id, description: plan.extraDescription ?? 'Extra model from the plugin configuration' });
    }
    const rows = [];
    const seen = new Set();
    for (const { id, description } of wanted) {
        const canonical = findEntry(plan.curated, id)?.id ?? id;
        if (seen.has(canonical))
            continue;
        seen.add(canonical);
        const entry = described.get(canonical)
            ?? findEntry(plan.curated, id)
            ?? fallbackEntry(id, description, plan.fallbackContextWindow);
        rows.push(refine({ ...entry, id: canonical }));
    }
    return rows;
}
/**
 * Apply the user's model order and visibility to one provider's catalog.
 *
 * Ranked ids come first in the user's order; ids the order does not mention keep
 * their curated position after them, so a model added by a plugin update or by
 * live discovery is listed without the user having to re-rank anything.
 *
 * @param entries - the provider's catalog, in curated order.
 * @param order - the user's stored rank, as model ids.
 * @param hidden - ids the picker must omit.
 * @returns the listed entries, the full list, and the enabled entries.
 */
export function resolveCatalog(entries, order, hidden) {
    const available = new Map(entries.map((entry) => [entry.id, entry]));
    const ranked = [];
    const seen = new Set();
    for (const id of order) {
        if (seen.has(id))
            continue;
        const entry = available.get(id);
        if (entry === undefined)
            continue;
        seen.add(id);
        ranked.push(entry);
    }
    for (const entry of entries) {
        if (seen.has(entry.id))
            continue;
        seen.add(entry.id);
        ranked.push(entry);
    }
    const enabled = ranked.filter((entry) => !hidden.has(entry.id));
    return { entries: enabled, ranked, enabled };
}
