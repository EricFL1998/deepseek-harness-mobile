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
import type { ModelModality } from '@deepseek-ai/dsh-llm';
/** One provider-owned model entry. */
export interface CatalogEntry {
    /** Model id sent on the wire. */
    id: string;
    name: string;
    description?: string;
    /** Other ids the endpoint accepts for this entry. */
    aliases?: readonly string[];
    /** Combined request and response context, in tokens. */
    contextWindow: number;
    inputModalities?: readonly ModelModality[];
    /** Selectable reasoning efforts, in display order. */
    reasoningEfforts?: readonly string[];
    /** Effort materialized when a caller picks none. */
    defaultEffort?: string;
}
/** Whether one catalog entry covers a model id. */
export declare function entryMatches(entry: CatalogEntry, id: string): boolean;
/** The entry covering a model id, when the catalog has one. */
export declare function findEntry(entries: readonly CatalogEntry[], id: string): CatalogEntry | undefined;
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
export declare function fallbackEntry(id: string, description: string, contextWindow: number): CatalogEntry;
/** A catalog after the user's order and visibility choices are applied. */
export interface ResolvedCatalog {
    /** Every entry the picker may offer, hidden ids omitted (the picker's list). */
    entries: CatalogEntry[];
    /** The enabled entries, in catalog order. */
    enabled: CatalogEntry[];
    /** Every entry in catalog order, hidden ids included (the Settings list). */
    ranked: CatalogEntry[];
}
/**
 * One model as a live listing stated it.
 *
 * Every field but the id is optional, and an absent field means the listing was
 * silent rather than that the model lacks the capability: a row states what it
 * states, and a curated entry fills the gaps.
 */
export interface LiveModelFacts {
    readonly id: string;
    readonly name?: string | undefined;
    readonly description?: string | undefined;
    readonly contextWindow?: number | undefined;
    readonly inputModalities?: readonly ModelModality[] | undefined;
    readonly reasoningEfforts?: readonly string[] | undefined;
    readonly defaultEffort?: string | undefined;
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
export declare function liveModelFacts(row: unknown): LiveModelFacts | undefined;
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
export declare function liveEntry(facts: LiveModelFacts, curated: readonly CatalogEntry[], fallbackContextWindow: number, description: string): CatalogEntry;
/**
 * How one provider's catalog is composed from its curated table and the entries the
 * endpoint itself reported.
 *
 * The endpoint decides membership. A curated table is metadata, not a roster: the ids
 * in it describe what the build knows about a model's name, window and ladder, and it
 * is the membership only while no live list has been read. That is what lets a model
 * released or retired server-side reach the picker without a plugin update, which a
 * roster can never do.
 */
export interface CatalogPlan {
    /** Curated entries, in curated order; metadata first, fallback membership second. */
    readonly curated: readonly CatalogEntry[];
    /**
     * Entries the endpoint reported, in its own order.
     *
     * Undefined means discovery has not run (or failed), so the curated table decides
     * membership and the picker is never empty.
     */
    readonly live?: readonly CatalogEntry[] | undefined;
    /** Ids the plugin configuration adds, on top of the membership. */
    readonly extra?: readonly string[] | undefined;
    /** The model the picker preselects, and therefore always offers. */
    readonly defaultModel?: string | undefined;
    /** Context advertised for an id no curated entry describes. */
    readonly fallbackContextWindow: number;
    /** Description shown for a live id no curated entry describes. */
    readonly liveDescription?: string | undefined;
    /** Description shown for a configured id no curated entry describes. */
    readonly extraDescription?: string | undefined;
    /** Description shown for the default model when no curated entry describes it. */
    readonly defaultDescription?: string | undefined;
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
export declare function composeCatalog(plan: CatalogPlan, refine?: (entry: CatalogEntry) => CatalogEntry): CatalogEntry[];
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
export declare function resolveCatalog(entries: readonly CatalogEntry[], order: readonly string[], hidden: ReadonlySet<string>): ResolvedCatalog;
