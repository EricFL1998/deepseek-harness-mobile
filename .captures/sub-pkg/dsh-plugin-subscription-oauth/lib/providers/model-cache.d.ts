/**
 * The last model listing each route read from its own endpoint, kept on disk.
 *
 * A live listing is the authority on which models a subscription serves and what
 * each one is: its display name, context window, input modalities and reasoning
 * ladder. Asking for it on every picker query would spend a request per refresh and
 * still show nothing while the endpoint is unreachable, so the answer is read once,
 * cached here, and reused as the route's catalog until the next discovery.
 *
 * That cache replaces the model table this build used to ship by hand: the same
 * curated list, except it is now the endpoint's own answer rather than a roster that
 * goes stale between releases. A hardcoded table cannot learn that a model was
 * released, renamed or retired; a cached live read can, and it is read from disk
 * rather than the network on the path that matters.
 *
 * The endpoint's own payload is stored verbatim rather than a pre-digested catalog, so
 * the route re-derives everything it publishes — picker rows, fast variants, context
 * variants — from the same parse it uses on a fresh read. A later build that reads more
 * of a payload therefore benefits from a cache written by an earlier one.
 *
 * @module dsh-plugin-subscription-oauth/providers/model-cache
 */
/**
 * Keep each route's last live listing on disk.
 *
 * Reads are tolerant: a missing, unreadable, unparsable or stale-version file yields
 * no cache rather than an error, because a cache miss must only mean "ask the
 * endpoint", never a broken picker. Writes are atomic, so a crash mid-write cannot
 * leave a half-written document behind.
 */
export declare class ModelCatalogCache {
    private readonly file;
    /**
     * @param file - absolute path of the cache document.
     */
    constructor(file: string);
    /**
     * Whatever one route last cached.
     *
     * The payload is opaque here: a route writes what it needs to rebuild its catalog
     * (Kimi stores the decoded rows of `/models`; a route that derives fast variants from
     * a second listing stores both). It re-validates on read.
     *
     * @param provider - provider route key.
     * @returns the cached payload, or undefined when there is no usable cache.
     */
    read(provider: string): Promise<unknown>;
    /**
     * Record what the endpoint just reported for one route.
     *
     * @param provider - provider route key.
     * @param payload - what that route needs to rebuild its catalog.
     */
    write(provider: string, payload: unknown): Promise<void>;
    /** Load the document, or undefined when it is absent or unusable. */
    private load;
}
