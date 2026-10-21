/**
 * JSON helpers shared by every provider.
 *
 * The bridge serializes data it did not author (Host records, evaluated client
 * bundles, component props). These helpers make that data safe to put on the
 * wire: cycles, functions, symbols, and bigints become explicit omissions
 * instead of throwing during serialization.
 */
/** Outcome of projecting an arbitrary value into JSON. */
export interface JsonProjection {
    readonly value: unknown;
    /** Dotted paths whose value was removed, in visit order. */
    readonly dropped: readonly string[];
    /** Dotted paths whose value was rewritten, in visit order. */
    readonly coerced: readonly string[];
}
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
export declare function projectJson(input: unknown, label?: string): JsonProjection;
/**
 * Read a dotted path out of a JSON value without trusting its shape.
 * @param value - the container.
 * @param path - dot-separated field names.
 * @returns the value at the path, or undefined when any segment is missing.
 */
export declare function readPath(value: unknown, path: string): unknown;
/**
 * Copy the string-valued entries of a record, dropping everything else.
 * @param value - candidate record.
 * @returns a plain string map, empty when `value` is not a record.
 */
export declare function stringRecord(value: unknown): Record<string, string>;
/**
 * Read the first string-valued field of a record.
 * @param value - candidate record.
 * @param fields - field names in preference order.
 * @returns the first present string, or undefined.
 */
export declare function firstString(value: unknown, fields: readonly string[]): string | undefined;
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
export declare function localizedString(value: unknown, fields: readonly string[], tags: readonly string[]): string | undefined;
