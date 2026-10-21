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
import { chmod, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
/**
 * Provider whose earlier, flat preference shape is migrated on load.
 *
 * Before this plugin served more than one route the document was
 * `{ hidden: string[] }`; that list belonged to Kimi Code.
 */
export const LEGACY_FLAT_PROVIDER = 'kimi';
/** Read the `auto` section, keeping only usable keys. */
function readAuto(document) {
    if (!isRecord(document.auto))
        return { order: [], off: [], enabled: true };
    return {
        order: readIds(document.auto.order),
        off: readIds(document.auto.off),
        enabled: document.auto.enabled !== false,
    };
}
/** True for a plain object usable as a JSON record. */
function isRecord(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
/** Read one id list, keeping only usable ids and dropping repeats. */
function readIds(value) {
    if (!Array.isArray(value))
        return [];
    const result = [];
    const seen = new Set();
    for (const id of value) {
        if (typeof id !== 'string' || id.length === 0 || seen.has(id))
            continue;
        seen.add(id);
        result.push(id);
    }
    return result;
}
/** Coerce the document into a per-provider hidden map, migrating the flat shape. */
function readHidden(document) {
    const hidden = document.hidden;
    if (Array.isArray(hidden)) {
        const legacy = readIds(hidden);
        return legacy.length === 0 ? {} : { [LEGACY_FLAT_PROVIDER]: new Set(legacy) };
    }
    if (!isRecord(hidden))
        return {};
    const result = {};
    for (const [provider, ids] of Object.entries(hidden)) {
        const set = new Set(readIds(ids));
        if (set.size > 0)
            result[provider] = set;
    }
    return result;
}
/** Read the user-added ids, per provider. */
function readExtra(document) {
    if (!isRecord(document.extra))
        return {};
    const result = {};
    for (const [provider, ids] of Object.entries(document.extra)) {
        const list = readIds(ids);
        if (list.length > 0)
            result[provider] = list;
    }
    return result;
}
/** Coerce the document into a per-provider order map. */
function readOrder(document) {
    const order = document.order;
    if (!isRecord(order))
        return {};
    const result = {};
    for (const [provider, ids] of Object.entries(order)) {
        const list = readIds(ids);
        if (list.length > 0)
            result[provider] = list;
    }
    return result;
}
/**
 * Owns the model preference document.
 *
 * Reads are cached; every write updates the cache and the file together, so the
 * picker's next catalog query sees the change without a restart.
 */
export class ModelPreferences {
    file;
    hidden = {};
    order = {};
    auto = { order: [], off: [], enabled: true };
    extra = {};
    loaded = false;
    /** The in-flight first read, so concurrent callers share one. */
    loading;
    constructor(file) {
        this.file = file;
    }
    /**
     * Load the document once; `reload` forces a re-read.
     *
     * Callers read several sections at once (`hidden`, `order`, `extra`), so the load
     * is single-flight: marking the object loaded before the file had been read let a
     * concurrent first call return an empty snapshot, which silently dropped the
     * user's stored choices for whichever read lost the race.
     */
    async ensureLoaded(reload = false) {
        if (reload) {
            this.loaded = false;
            this.loading = undefined;
        }
        if (this.loaded)
            return;
        this.loading ??= this.read().finally(() => { this.loading = undefined; });
        await this.loading;
    }
    /** Read the file into memory and record that the state is loaded. */
    async read() {
        let raw;
        try {
            raw = await readFile(this.file, 'utf8');
        }
        catch {
            this.hidden = {};
            this.order = {};
            this.auto = { order: [], off: [], enabled: true };
            this.extra = {};
            this.loaded = true;
            return;
        }
        try {
            const parsed = JSON.parse(raw);
            const document = (isRecord(parsed) ? parsed : {});
            this.hidden = readHidden(document);
            this.order = readOrder(document);
            this.auto = readAuto(document);
            this.extra = readExtra(document);
        }
        catch {
            this.hidden = {};
            this.order = {};
            this.auto = { order: [], off: [], enabled: true };
            this.extra = {};
        }
        this.loaded = true;
    }
    /** Re-read the file, discarding in-memory state. */
    async reload() {
        await this.ensureLoaded(true);
    }
    /** The ids one provider's picker must not offer. */
    async hiddenIds(provider) {
        await this.ensureLoaded();
        return new Set(this.hidden[provider] ?? []);
    }
    /** The user's model rank for one provider, most preferred first. */
    async orderOf(provider) {
        await this.ensureLoaded();
        return [...this.order[provider] ?? []];
    }
    /** The ids the user added by hand for one provider. */
    async extraIds(provider) {
        await this.ensureLoaded();
        return [...this.extra[provider] ?? []];
    }
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
    async addExtra(provider, id) {
        await this.ensureLoaded();
        const current = this.extra[provider] ?? [];
        if (current.includes(id))
            return false;
        this.extra = { ...this.extra, [provider]: [...current, id] };
        await this.persist();
        return true;
    }
    /**
     * Forget one hand-added id.
     * @param provider - provider route key.
     * @param id - the model id to drop.
     * @returns whether the stored list actually changed.
     */
    async removeExtra(provider, id) {
        await this.ensureLoaded();
        const current = this.extra[provider] ?? [];
        if (!current.includes(id))
            return false;
        const next = current.filter((entry) => entry !== id);
        const copy = { ...this.extra };
        if (next.length === 0)
            delete copy[provider];
        else
            copy[provider] = next;
        this.extra = copy;
        await this.persist();
        return true;
    }
    /** The `auto` provider's arrangement. */
    async autoPreferences() {
        await this.ensureLoaded();
        return { order: [...this.auto.order], off: [...this.auto.off], enabled: this.auto.enabled };
    }
    /**
     * Record whether the `auto` provider is offered at all.
     * @param enabled - whether `auto` appears in the picker.
     * @returns whether the stored state actually changed.
     */
    async setAutoOn(enabled) {
        await this.ensureLoaded();
        if (this.auto.enabled === enabled)
            return false;
        this.auto = { ...this.auto, enabled };
        await this.persist();
        return true;
    }
    /**
     * Replace the `auto` provider's order.
     * @param keys - `provider/model` keys, most preferred first.
     * @returns whether the stored order actually changed.
     */
    async setAutoOrder(keys) {
        await this.ensureLoaded();
        const next = readIds([...keys]);
        const current = this.auto.order;
        if (current.length === next.length && current.every((key, index) => key === next[index]))
            return false;
        this.auto = { ...this.auto, order: next };
        await this.persist();
        return true;
    }
    /**
     * Record whether `auto` may use one model.
     * @param key - `provider/model` key.
     * @param enabled - whether `auto` may fall back to it.
     * @returns whether the stored state actually changed.
     */
    async setAutoEnabled(key, enabled) {
        await this.ensureLoaded();
        const current = new Set(this.auto.off);
        const shouldOff = !enabled;
        if (current.has(key) === shouldOff)
            return false;
        if (shouldOff)
            current.add(key);
        else
            current.delete(key);
        this.auto = { ...this.auto, off: [...current] };
        await this.persist();
        return true;
    }
    /**
     * Record one model's visibility.
     * @param provider - provider route key.
     * @param id - model id as the catalog reports it.
     * @param visible - whether the picker should offer it.
     * @returns whether the stored state actually changed.
     */
    async setVisible(provider, id, visible) {
        await this.ensureLoaded();
        const current = this.hidden[provider] ?? new Set();
        const shouldHide = !visible;
        if (current.has(id) === shouldHide)
            return false;
        if (shouldHide)
            current.add(id);
        else
            current.delete(id);
        if (current.size === 0)
            delete this.hidden[provider];
        else
            this.hidden[provider] = current;
        await this.persist();
        return true;
    }
    /**
     * Replace one provider's model rank.
     * @param provider - provider route key.
     * @param ids - the complete rank, most preferred first.
     * @returns whether the stored order actually changed.
     */
    async setOrder(provider, ids) {
        await this.ensureLoaded();
        const next = readIds([...ids]);
        const current = this.order[provider] ?? [];
        if (current.length === next.length && current.every((id, index) => id === next[index]))
            return false;
        if (next.length === 0)
            delete this.order[provider];
        else
            this.order[provider] = next;
        await this.persist();
        return true;
    }
    /** Write the document atomically with owner-only permissions. */
    async persist() {
        await mkdir(path.dirname(this.file), { recursive: true });
        const hidden = {};
        for (const [provider, ids] of Object.entries(this.hidden)) {
            if (ids.size > 0)
                hidden[provider] = [...ids].sort();
        }
        const order = {};
        for (const [provider, ids] of Object.entries(this.order)) {
            if (ids.length > 0)
                order[provider] = [...ids];
        }
        const document = {
            hidden,
            order,
            auto: { order: [...this.auto.order], off: [...this.auto.off] },
            extra: Object.fromEntries(Object.entries(this.extra).filter(([, ids]) => ids.length > 0)),
        };
        const temporary = `${this.file}.${process.pid}.tmp`;
        await writeFile(temporary, `${JSON.stringify(document, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 });
        try {
            await rename(temporary, this.file);
            await chmod(this.file, 0o600);
        }
        catch (error) {
            await rm(temporary, { force: true });
            throw error;
        }
    }
}
