/**
 * Model-visibility and model-order preferences.
 *
 * Which models appear in the session model picker, and in which order, is a user
 * choice, so it lives on the node half beside the adapter that answers the
 * picker's catalog query — not in the browser. The file is a small JSON document
 * keyed by provider; a missing or corrupt section means "everything visible, in
 * the curated order", which is the shipped default.
 *
 * The visibility set is a HIDDEN list rather than a visible list: a model that a
 * later release adds to the catalog must appear without the user re-selecting it.
 * The order is a partial rank: ids it does not mention keep their catalog
 * position, so a new model lands without disturbing the user's arrangement.
 *
 * @module dsh-plugin-subscription-oauth/settings
 */
/**
 * Provider whose earlier, flat preference shape is migrated on load.
 *
 * Before this plugin served more than one route the document was
 * `{ hidden: string[] }`; that list belonged to Kimi Code.
 */
export declare const LEGACY_FLAT_PROVIDER = "kimi";
/**
 * The `auto` provider's arrangement.
 *
 * `auto` is a provider of its own, so its list is not a route's catalog: it is
 * the union of every enabled model, keyed by `provider/model` because two routes
 * may serve the same id.
 */
export interface AutoPreferences {
    /** `provider/model` keys, most preferred first. */
    order: readonly string[];
    /** Keys `auto` must not use, whatever their own route offers. */
    off: readonly string[];
    /** Whether the `auto` provider is offered at all. Defaults to true. */
    enabled: boolean;
}
/**
 * Owns the model preference document.
 *
 * Reads are cached; every write updates the cache and the file together, so the
 * picker's next catalog query sees the change without a restart.
 */
export declare class ModelPreferences {
    private readonly file;
    private hidden;
    private order;
    private auto;
    private extra;
    private loaded;
    /** The in-flight first read, so concurrent callers share one. */
    private loading;
    constructor(file: string);
    /**
     * Load the document once; `reload` forces a re-read.
     *
     * Callers read several sections at once (`hidden`, `order`, `extra`), so the load
     * is single-flight: marking the object loaded before the file had been read let a
     * concurrent first call return an empty snapshot, which silently dropped the
     * user's stored choices for whichever read lost the race.
     */
    private ensureLoaded;
    /** Read the file into memory and record that the state is loaded. */
    private read;
    /** Re-read the file, discarding in-memory state. */
    reload(): Promise<void>;
    /** The ids one provider's picker must not offer. */
    hiddenIds(provider: string): Promise<ReadonlySet<string>>;
    /** The user's model rank for one provider, most preferred first. */
    orderOf(provider: string): Promise<readonly string[]>;
    /** The ids the user added by hand for one provider. */
    extraIds(provider: string): Promise<readonly string[]>;
    /**
     * Add one model id by hand.
     *
     * The point of the button is a model the catalog does not know yet, so nothing
     * here validates the id against an endpoint — only that it is a plausible id.
     *
     * @param provider - provider route key.
     * @param id - the model id the endpoint accepts.
     * @returns whether the stored list actually changed.
     */
    addExtra(provider: string, id: string): Promise<boolean>;
    /**
     * Forget one hand-added id.
     * @param provider - provider route key.
     * @param id - the model id to drop.
     * @returns whether the stored list actually changed.
     */
    removeExtra(provider: string, id: string): Promise<boolean>;
    /** The `auto` provider's arrangement. */
    autoPreferences(): Promise<AutoPreferences>;
    /**
     * Record whether the `auto` provider is offered at all.
     * @param enabled - whether `auto` appears in the picker.
     * @returns whether the stored state actually changed.
     */
    setAutoOn(enabled: boolean): Promise<boolean>;
    /**
     * Replace the `auto` provider's order.
     * @param keys - `provider/model` keys, most preferred first.
     * @returns whether the stored order actually changed.
     */
    setAutoOrder(keys: readonly string[]): Promise<boolean>;
    /**
     * Record whether `auto` may use one model.
     * @param key - `provider/model` key.
     * @param enabled - whether `auto` may fall back to it.
     * @returns whether the stored state actually changed.
     */
    setAutoEnabled(key: string, enabled: boolean): Promise<boolean>;
    /**
     * Record one model's visibility.
     * @param provider - provider route key.
     * @param id - model id as the catalog reports it.
     * @param visible - whether the picker should offer it.
     * @returns whether the stored state actually changed.
     */
    setVisible(provider: string, id: string, visible: boolean): Promise<boolean>;
    /**
     * Replace one provider's model rank.
     * @param provider - provider route key.
     * @param ids - the complete rank, most preferred first.
     * @returns whether the stored order actually changed.
     */
    setOrder(provider: string, ids: readonly string[]): Promise<boolean>;
    /** Write the document atomically with owner-only permissions. */
    private persist;
}
