/**
 * JSON helpers shared by every provider.
 *
 * The bridge serializes data it did not author (Host records, evaluated client
 * bundles, component props). These helpers make that data safe to put on the
 * wire: cycles, functions, symbols, and bigints become explicit omissions
 * instead of throwing during serialization.
 */
const MAX_PROJECTED_DEPTH = 12;
const MAX_PROJECTED_NODES = 20_000;
/**
 * Project an arbitrary value into JSON-compatible data.
 *
 * Objects and arrays are copied; functions, symbols, and cycles are dropped with
 * a recorded path, and a bigint becomes its decimal text (JSON has no integer
 * wider than 2^53, so the text is the only lossless spelling). Depth and node
 * budgets bound a hostile or pathological value instead of letting it exhaust
 * the Host.
 * @param input - the value to project.
 * @param label - path prefix recorded in the notes, usually a field name.
 * @returns the projected value, the dropped paths, and the coerced paths.
 */
export function projectJson(input, label = '') {
    const dropped = [];
    const coerced = [];
    const seen = new Set();
    let nodes = 0;
    const visit = (value, path, depth) => {
        nodes += 1;
        if (nodes > MAX_PROJECTED_NODES) {
            dropped.push(path === '' ? '<budget>' : `${path} <budget>`);
            return undefined;
        }
        if (depth > MAX_PROJECTED_DEPTH) {
            dropped.push(path === '' ? '<depth>' : `${path} <depth>`);
            return undefined;
        }
        switch (typeof value) {
            case 'string':
            case 'boolean':
                return value;
            case 'number':
                // JSON has no NaN or Infinity; null is the only value that round-trips
                // as "not a finite number".
                return Number.isFinite(value) ? value : null;
            case 'undefined':
                return undefined;
            case 'bigint':
                coerced.push(path);
                return String(value);
            case 'function':
            case 'symbol':
                dropped.push(path);
                return undefined;
            case 'object':
                break;
            default:
                dropped.push(path);
                return undefined;
        }
        if (value === null)
            return null;
        const object = value;
        if (seen.has(object)) {
            dropped.push(`${path} <cycle>`);
            return undefined;
        }
        seen.add(object);
        if (Array.isArray(object)) {
            const items = [];
            for (let index = 0; index < object.length; index += 1) {
                const projected = visit(object[index], `${path}[${index}]`, depth + 1);
                items.push(projected === undefined ? null : projected);
            }
            seen.delete(object);
            return items;
        }
        const record = object;
        const projected = {};
        for (const [key, entry] of Object.entries(record)) {
            const child = visit(entry, path === '' ? key : `${path}.${key}`, depth + 1);
            if (child !== undefined)
                projected[key] = child;
        }
        seen.delete(object);
        return projected;
    };
    return { value: visit(input, label, 0), dropped, coerced };
}
/**
 * Read a dotted path out of a JSON value without trusting its shape.
 * @param value - the container.
 * @param path - dot-separated field names.
 * @returns the value at the path, or undefined when any segment is missing.
 */
export function readPath(value, path) {
    let current = value;
    for (const segment of path.split('.')) {
        if (typeof current !== 'object' || current === null)
            return undefined;
        current = current[segment];
    }
    return current;
}
/**
 * Copy the string-valued entries of a record, dropping everything else.
 * @param value - candidate record.
 * @returns a plain string map, empty when `value` is not a record.
 */
export function stringRecord(value) {
    if (typeof value !== 'object' || value === null || Array.isArray(value))
        return {};
    const out = {};
    for (const [key, entry] of Object.entries(value)) {
        if (typeof entry === 'string')
            out[key] = entry;
    }
    return out;
}
/**
 * Read the first string-valued field of a record.
 * @param value - candidate record.
 * @param fields - field names in preference order.
 * @returns the first present string, or undefined.
 */
export function firstString(value, fields) {
    if (typeof value !== 'object' || value === null)
        return undefined;
    const record = value;
    for (const field of fields) {
        const entry = record[field];
        if (typeof entry === 'string' && entry !== '')
            return entry;
    }
    return undefined;
}
/**
 * Read the first localized field of a record.
 *
 * The Host writes display text either as a plain string or as a locale map such as
 * `{ zh: "...", en: "..." }`. A native client shows one of those, so the map is
 * resolved to the first requested tag rather than dropped.
 * @param value - candidate record.
 * @param fields - field names in preference order.
 * @param tags - locale tags in preference order.
 * @returns the resolved text, or undefined.
 */
export function localizedString(value, fields, tags) {
    if (typeof value !== 'object' || value === null)
        return undefined;
    const record = value;
    for (const field of fields) {
        const entry = record[field];
        if (typeof entry === 'string' && entry !== '')
            return entry;
        if (typeof entry === 'object' && entry !== null) {
            const map = entry;
            for (const tag of tags) {
                const text = map[tag];
                if (typeof text === 'string' && text !== '')
                    return text;
            }
            for (const text of Object.values(map)) {
                if (typeof text === 'string' && text !== '')
                    return text;
            }
        }
    }
    return undefined;
}
