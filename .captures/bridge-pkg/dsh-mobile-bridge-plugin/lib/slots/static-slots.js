/**
 * Slot registrations read out of a client bundle without running it.
 *
 * Executing a bundle is the exact answer, but it is not a safe answer on a phone Host:
 * a client half's `apply` performs real mount work, and running it inside the bridge VM
 * has taken the Host process down. Reading the compiled source instead costs a file read
 * and yields the same registration metadata — the slot name, the entry id, its order —
 * because those values are object literals passed to the registry.
 *
 * A label is written one of two ways: a literal (`label: "Subscriptions"`) or a thunk
 * that reads the bundle's own copy table (`label: () => t("nav")`). The thunk's key is
 * captured here and resolved through the bundle's inline dictionaries by the caller,
 * which knows the language the request asked for.
 *
 * What static reading cannot recover is anything computed at runtime: a label built
 * from a variable, a component, or a registration produced by a loop. Those are
 * reported as absent rather than guessed, and the caller decides what to show.
 */
import { placementOf } from "../slot-placements.js";
import { bundleDictionaries, decodeEscapes, resolveDictionaryKey } from "./bundle-locale.js";
/** How far past `register(` to look for the spec object. */
const WINDOW_CHARS = 8_000;
/** A slot name: dotted lower-case segments, as the Host declares them. */
const SLOT_NAME = /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+$/;
/** `key: value` where value is a quoted string (including backticks) or a signed integer. */
const PROPERTY = /([A-Za-z_$][\w$]*)\s*:\s*(?:"((?:[^"\\]|\\.)*)"|'((?:[^'\\]|\\.)*)'|`((?:[^`\\]|\\.)*)`|(-?\d+))/g;
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
        const [, name, doubleQuoted, singleQuoted, backtickQuoted, number] = property;
        if (name === undefined)
            continue;
        // A template literal with substitutions is not a static value.
        const backtick = backtickQuoted?.includes('${') === true ? undefined : backtickQuoted;
        const value = doubleQuoted ?? singleQuoted ?? backtick ?? (number === undefined ? undefined : Number(number));
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
    const { label, labelKey } = labelSpecOf(depthLimit);
    return {
        slot,
        ...(id === undefined ? {} : { id }),
        ...(key === undefined ? {} : { key }),
        ...(order === undefined ? {} : { order }),
        ...(priority === undefined ? {} : { priority }),
        ...(locale === undefined ? {} : { locale }),
        ...(label === undefined ? {} : { label }),
        ...(labelKey === undefined ? {} : { labelKey }),
    };
}
/**
 * The label one `register({…})` spec declares.
 *
 * Both compiled shapes are read: a literal (`label: "Subscriptions"`) and the
 * thunk a bundle uses for localized text (`label: () => t("nav")`, minified as
 * `label: () => (0, x.t)("nav")`). A thunk that returns its text instead of a key
 * (`label: () => "Sample Two"`) is read as a literal; anything genuinely computed
 * (`label: () => names[kind]`) yields nothing, so the caller shows the id rather
 * than inventing a name.
 *
 * @param body the balanced spec object literal.
 * @returns the literal label and/or the dictionary key the thunk resolves.
 */
function labelSpecOf(body) {
    const expression = topLevelValue(body, 'label');
    if (expression === undefined)
        return {};
    const text = expression.trim();
    if (text === '')
        return {};
    const literals = stringLiterals(text);
    const first = literals[0];
    if (first === undefined)
        return {};
    // `label: () => "Sample Two"`, `label: "Sample One"`: the text itself.
    if (first.end === text.length)
        return { label: first.value };
    // `t("nav")` / `(0, x.t)("nav")`: the string is a call argument, so it is the
    // dictionary key the bundle's own copy table answers.
    if (first.before === '(' || first.before === ',')
        return { labelKey: first.value };
    // A string the expression builds around (`cond ? "a" : "b"`): not statically
    // decidable, so no label is claimed.
    return {};
}
/**
 * The value expression of one top-level property of an object literal.
 *
 * Only the object's own members are considered: commas, braces, and strings
 * nested in a value's call arguments or object literal are skipped. This is what
 * keeps a `label` inside an `inject()` object from being mistaken for the
 * registration's own label.
 *
 * @param body one balanced object literal, starting with `{`.
 * @param name property name to find.
 * @returns the property's value text, or undefined when it has none.
 */
function topLevelValue(body, name) {
    let at = body.startsWith('{') ? 1 : 0;
    while (at < body.length) {
        while (at < body.length && (/\s/.test(body[at]) || body[at] === ','))
            at += 1;
        if (at >= body.length || body[at] === '}')
            return undefined;
        const key = propertyKeyAt(body, at);
        if (key === undefined) {
            at = scanToBoundary(body, at);
            continue;
        }
        let colon = key.end;
        while (colon < body.length && /\s/.test(body[colon]))
            colon += 1;
        // A shorthand or method member has no `: value` to read.
        if (body[colon] !== ':') {
            at = scanToBoundary(body, key.end);
            continue;
        }
        const valueStart = colon + 1;
        const boundary = scanToBoundary(body, valueStart);
        if (key.name === name)
            return body.slice(valueStart, boundary).trim();
        at = boundary;
    }
    return undefined;
}
/**
 * Index of the first `,` or closing bracket at the current nesting level.
 * @param text text to scan.
 * @param from index to start at.
 * @returns the index of that boundary, or the text length.
 */
function scanToBoundary(text, from) {
    let depth = 0;
    let quote;
    for (let at = from; at < text.length; at += 1) {
        const char = text[at];
        if (quote !== undefined) {
            if (char === '\\')
                at += 1;
            else if (char === quote)
                quote = undefined;
            continue;
        }
        if (char === '"' || char === "'" || char === '`') {
            quote = char;
            continue;
        }
        if (char === '{' || char === '(' || char === '[') {
            depth += 1;
            continue;
        }
        if (char === '}' || char === ')' || char === ']') {
            if (depth === 0)
                return at;
            depth -= 1;
            continue;
        }
        if (char === ',' && depth === 0)
            return at;
    }
    return text.length;
}
/**
 * One property key starting at `at`.
 * @param text object literal body.
 * @param at index the key starts at.
 * @returns the (decoded) key name and the index one past it, or undefined.
 */
function propertyKeyAt(text, at) {
    const char = text[at];
    if (char === '"' || char === "'" || char === '`') {
        const quoted = readQuotedAt(text, at);
        return quoted === undefined ? undefined : { name: decodeEscapes(quoted.value), end: quoted.end };
    }
    const match = /^[A-Za-z_$][\w$]*/.exec(text.slice(at));
    return match === null ? undefined : { name: match[0], end: at + match[0].length };
}
/**
 * One quoted string literal starting at `at`.
 * @param text text to read.
 * @param at index of the opening quote.
 * @returns the raw (still escaped) body and the index one past the closing quote.
 */
function readQuotedAt(text, at) {
    const quote = text[at];
    if (quote !== '"' && quote !== "'" && quote !== '`')
        return undefined;
    let index = at + 1;
    let raw = '';
    while (index < text.length) {
        const char = text[index];
        if (char === '\\') {
            raw += char + (text[index + 1] ?? '');
            index += 2;
            continue;
        }
        if (char === quote) {
            // `…${expr}…` is computed text, not a static literal.
            return raw.includes('${') ? undefined : { value: raw, end: index + 1 };
        }
        raw += char;
        index += 1;
    }
    return undefined;
}
/**
 * Every string literal in one expression, with the non-space character before it.
 * @param text expression text.
 * @returns the decoded literals in source order.
 */
function stringLiterals(text) {
    const found = [];
    let at = 0;
    while (at < text.length) {
        const char = text[at];
        // A template literal may carry substitutions: its text is not a static label.
        if (char !== '"' && char !== "'" && char !== '`') {
            at += 1;
            continue;
        }
        const quoted = readQuotedAt(text, at);
        if (quoted === undefined) {
            // Skip the whole (unreadable) template instead of re-reading quotes inside it.
            const end = text.indexOf(char, at + 1);
            if (end < 0)
                return found;
            at = end + 1;
            continue;
        }
        let before = at - 1;
        while (before >= 0 && /\s/.test(text[before]))
            before -= 1;
        found.push({
            value: decodeEscapes(quoted.value),
            before: before < 0 ? undefined : text[before],
            end: quoted.end,
        });
        at = quoted.end;
    }
    return found;
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
        origin: 'static',
        ...(registration.locale === undefined ? {} : { locale: registration.locale }),
        ...(registration.label === undefined ? {} : { label: registration.label }),
        ...(registration.labelKey === undefined ? {} : { labelKey: registration.labelKey }),
        // Nothing was rendered, so no component identity exists; the client shows the slot
        // and its id instead of pretending a renderable component was captured.
        render: { kind: 'component', componentId: `${entryId}#${registration.id ?? registration.key ?? registration.slot}` },
    };
}
/**
 * A display label for one registration, read from its package's own locale files.
 *
 * The browser resolves labels through a locale dictionary at render time; static
 * reading has no render, so the dictionaries themselves are the source. Candidates are
 * tried in the order the page would use: the registration's own id as a key, then the
 * package's `meta.title`. A registration with no dictionary entry keeps no label —
 * showing an id is honest, inventing a name is not.
 * @param locales parsed locale dictionaries, most preferred first.
 * @param id registration id, when it has one.
 * @returns the resolved label, or undefined.
 */
export function resolveStaticLabel(locales, id) {
    if (id === undefined)
        return undefined;
    const keys = [`${id}.title`, id, `plugins.item.${id}.title`, `meta.${id}.title`];
    for (const dictionary of locales) {
        for (const key of keys) {
            const found = valueAtPath(dictionary, key.split('.'));
            if (typeof found === 'string' && found.trim() !== '')
                return found;
        }
        // A dictionary keyed by slug (`agent-loop` inside `items`) still answers.
        const nested = findByLeaf(dictionary, id, 'title');
        if (nested !== undefined)
            return nested;
        // Last resort: a package with one contribution names it in `meta.title`, which is
        // the same text its row shows elsewhere in the UI.
        const meta = valueAtPath(dictionary, ['meta', 'title']);
        if (typeof meta === 'string' && meta.trim() !== '')
            return meta;
    }
    return undefined;
}
/**
 * Resolve one registration's display label for a locale preference.
 *
 * Three sources, in the order the browser would use them: the text the spec wrote
 * as a literal; the key its thunk resolves through the bundle's own inline copy
 * tables (the shape the real subscription plugin ships); then the package's
 * `locale/<tag>.json` files keyed by the registration id. A registration none of
 * them names keeps no label, so the caller shows the id rather than a name nobody
 * wrote.
 *
 * @param registration the extracted registration.
 * @param dictionaries the bundle's inline locale tables, when it carries any.
 * @param fileLocales parsed package locale dictionaries, most preferred first.
 * @param localeTags preferred language tags, most preferred first.
 * @returns the resolved label, or undefined.
 */
export function resolveStaticLabelFor(registration, dictionaries, fileLocales, localeTags) {
    if (registration.label !== undefined)
        return registration.label;
    if (dictionaries !== undefined) {
        const inline = resolveDictionaryKey(dictionaries, localeTags, registration.labelKey);
        if (inline !== undefined)
            return inline;
    }
    return resolveStaticLabel(fileLocales, registration.id ?? registration.key);
}
/** Read a dotted path out of a parsed dictionary. */
function valueAtPath(dictionary, path) {
    let node = dictionary;
    for (const key of path) {
        if (typeof node !== 'object' || node === null)
            return undefined;
        node = node[key];
    }
    return node;
}
/** Find `…[slug].title` anywhere in a dictionary, for dictionaries keyed by slug. */
function findByLeaf(node, slug, leaf, depth = 0) {
    if (depth > 6 || typeof node !== 'object' || node === null)
        return undefined;
    const record = node;
    const own = record[slug];
    if (typeof own === 'object' && own !== null) {
        const title = own[leaf];
        if (typeof title === 'string' && title.trim() !== '')
            return title;
    }
    for (const value of Object.values(record)) {
        const found = findByLeaf(value, slug, leaf, depth + 1);
        if (found !== undefined)
            return found;
    }
    return undefined;
}
