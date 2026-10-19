/**
 * Slot registrations read out of a client bundle without running it.
 *
 * Executing a bundle is the exact answer, but it is not a safe answer on a phone Host:
 * a client half's `apply` performs real mount work, and running it inside the bridge VM
 * has taken the Host process down. Reading the compiled source instead costs a file read
 * and yields the same registration metadata — the slot name, the entry id, its order —
 * because those values are object literals passed to the registry.
 *
 * What static reading cannot recover is anything computed at runtime: a label resolved
 * through a locale table, a component, or a registration produced by a loop. Those are
 * reported as absent rather than guessed, and the caller decides what to show.
 */
import { placementOf } from "../slot-placements.js";
/** How far past `register(` to look for the spec object. */
const WINDOW_CHARS = 480;
/** A slot name: dotted lower-case segments, as the Host declares them. */
const SLOT_NAME = /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+$/;
/** `key: value` where value is a quoted string or a signed integer. */
const PROPERTY = /([A-Za-z_$][\w$]*)\s*:\s*(?:"((?:[^"\\]|\\.)*)"|'((?:[^'\\]|\\.)*)'|(-?\d+))/g;
/**
 * Extract every registration a compiled client bundle performs.
 *
 * A bundle from this repository registers through `…slots.register({ … }, Component)`,
 * so each `register(` call followed by an object literal is one registration. The walk
 * is deliberately literal: a registration built dynamically is invisible here, which is
 * reported by the caller's coverage rather than papered over.
 * @param source compiled bundle text.
 * @returns registrations in source order, deduplicated by slot and id.
 */
export function extractStaticRegistrations(source) {
    const found = [];
    const seen = new Set();
    const call = /\.register\s*\(/g;
    for (const match of source.matchAll(call)) {
        const start = match.index ?? 0;
        const window = source.slice(start, start + WINDOW_CHARS);
        const objectStart = window.indexOf('{');
        if (objectStart < 0)
            continue;
        const spec = specOf(window.slice(objectStart));
        if (spec?.slot === undefined)
            continue;
        const identity = `${spec.slot}#${spec.id ?? spec.key ?? ''}`;
        if (seen.has(identity))
            continue;
        seen.add(identity);
        found.push(spec);
    }
    return found;
}
/**
 * Turn one `register({ … })` body into a registration.
 * @param text the object literal and whatever follows it.
 * @returns the registration, or undefined when this call names no slot.
 */
function specOf(text) {
    const depthLimit = braceBody(text);
    if (depthLimit === undefined)
        return undefined;
    const properties = new Map();
    PROPERTY.lastIndex = 0;
    for (const property of depthLimit.matchAll(PROPERTY)) {
        const [, name, doubleQuoted, singleQuoted, number] = property;
        if (name === undefined)
            continue;
        const value = doubleQuoted ?? singleQuoted ?? (number === undefined ? undefined : Number(number));
        if (value === undefined)
            continue;
        if (!properties.has(name))
            properties.set(name, value);
    }
    const slot = properties.get('name');
    if (typeof slot !== 'string' || !SLOT_NAME.test(slot))
        return undefined;
    const id = stringOf(properties.get('id'));
    const key = stringOf(properties.get('key'));
    const order = numberOrUndefined(properties.get('order'));
    const priority = numberOrUndefined(properties.get('priority'));
    const locale = stringOf(properties.get('locale'));
    return {
        slot,
        ...(id === undefined ? {} : { id }),
        ...(key === undefined ? {} : { key }),
        ...(order === undefined ? {} : { order }),
        ...(priority === undefined ? {} : { priority }),
        ...(locale === undefined ? {} : { locale }),
    };
}
/**
 * The first balanced `{…}` in a text.
 * @param text text starting at or before the opening brace.
 * @returns the balanced body, or undefined when it never closes.
 */
function braceBody(text) {
    const start = text.indexOf('{');
    if (start < 0)
        return undefined;
    let depth = 0;
    let quote;
    for (let index = start; index < text.length; index += 1) {
        const char = text[index];
        if (quote !== undefined) {
            if (char === '\\')
                index += 1;
            else if (char === quote)
                quote = undefined;
            continue;
        }
        if (char === '"' || char === "'" || char === '`') {
            quote = char;
            continue;
        }
        if (char === '{')
            depth += 1;
        else if (char === '}') {
            depth -= 1;
            if (depth === 0)
                return text.slice(start, index + 1);
        }
    }
    return undefined;
}
/** A property value as a string, when it is one. */
function stringOf(value) {
    return typeof value === 'string' ? value : undefined;
}
/** A property value as a number, when it is one. */
function numberOrUndefined(value) {
    return typeof value === 'number' ? value : undefined;
}
/**
 * Project a static registration into the bridge's registration type.
 * @param entryId package the bundle belongs to.
 * @param registration the extracted registration.
 * @returns the registration as a client sees it.
 */
export function toSlotRegistration(entryId, registration) {
    return {
        slot: registration.slot,
        ...(registration.id === undefined ? {} : { id: registration.id }),
        ...(registration.key === undefined ? {} : { key: registration.key }),
        ...(registration.order === undefined ? {} : { order: registration.order }),
        ...(registration.priority === undefined ? {} : { priority: registration.priority }),
        package: entryId,
        placement: placementOf(registration.slot).placement,
        ...(registration.locale === undefined ? {} : { locale: registration.locale }),
        // Nothing was rendered, so no component identity exists; the client shows the slot
        // and its id instead of pretending a renderable component was captured.
        render: { kind: 'component', componentId: `${entryId}#${registration.id ?? registration.slot}` },
    };
}
