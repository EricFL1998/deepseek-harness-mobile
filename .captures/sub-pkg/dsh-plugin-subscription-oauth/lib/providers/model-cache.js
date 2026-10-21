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
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
/**
 * On-disk shape version.
 *
 * Bumped when a route's parse of a cached payload changes meaning, so a document
 * written by an older understanding is discarded rather than misread.
 */
const CACHE_VERSION = 1;
/**
 * Keep each route's last live listing on disk.
 *
 * Reads are tolerant: a missing, unreadable, unparsable or stale-version file yields
 * no cache rather than an error, because a cache miss must only mean "ask the
 * endpoint", never a broken picker. Writes are atomic, so a crash mid-write cannot
 * leave a half-written document behind.
 */
export class ModelCatalogCache {
    file;
    /**
     * @param file - absolute path of the cache document.
     */
    constructor(file) {
        this.file = file;
    }
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
    async read(provider) {
        const document = await this.load();
        const cached = document?.providers[provider];
        if (cached === undefined || cached.payload === undefined || cached.payload === null)
            return undefined;
        if (Array.isArray(cached.payload) && cached.payload.length === 0)
            return undefined;
        return cached.payload;
    }
    /**
     * Record what the endpoint just reported for one route.
     *
     * @param provider - provider route key.
     * @param payload - what that route needs to rebuild its catalog.
     */
    async write(provider, payload) {
        if (payload === undefined || payload === null)
            return;
        if (Array.isArray(payload) && payload.length === 0)
            return;
        const document = await this.load() ?? { version: CACHE_VERSION, providers: {} };
        document.providers[provider] = { fetchedAt: Date.now(), payload };
        await mkdir(path.dirname(this.file), { recursive: true });
        // Write beside the target and rename, so a reader never sees a partial document.
        const temporary = `${this.file}.${process.pid}.tmp`;
        await writeFile(temporary, `${JSON.stringify(document, null, 2)}\n`, 'utf8');
        await rename(temporary, this.file);
    }
    /** Load the document, or undefined when it is absent or unusable. */
    async load() {
        try {
            const parsed = JSON.parse(await readFile(this.file, 'utf8'));
            if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed))
                return undefined;
            const record = parsed;
            if (record['version'] !== CACHE_VERSION)
                return undefined;
            const providers = record['providers'];
            if (typeof providers !== 'object' || providers === null || Array.isArray(providers))
                return undefined;
            return { version: CACHE_VERSION, providers: providers };
        }
        catch {
            // Absent, unreadable or corrupt: a miss, not a failure.
            return undefined;
        }
    }
}
