/**
 * Inline locale dictionaries carried inside a compiled client bundle.
 *
 * A client bundle ships its copy inside itself (`const en = { nav: 'Subscriptions' }`)
 * and reads it through a `t(key)` helper. The bridge reads those tables *statically* —
 * without executing the bundle — so a registration declared as `label: () => t('nav')`
 * can be labelled in the client's language on a phone Host.
 *
 * This module is the locale-reading subset the slot scanners (`static-slots.ts`) and
 * the `client-slots` provider need. It carries a small purpose-built tokenizer and a
 * forward-only cursor, but **none** of the JSX/component-tree machinery: that lives
 * with the (removed) transpiler, and locale reading never builds a render tree.
 *
 * History: extracted verbatim from the former `slots/transpile.ts` when the static
 * component-tree transpiler was retired; only the dictionary/escape/string-table
 * reading survives here, kept byte-for-byte so the scanner behaviour does not drift.
 */
const KEYWORDS = new Set([
    'await', 'break', 'case', 'catch', 'class', 'const', 'continue', 'debugger',
    'default', 'delete', 'do', 'else', 'export', 'extends', 'finally', 'for',
    'function', 'if', 'import', 'in', 'instanceof', 'new', 'return', 'switch',
    'this', 'throw', 'try', 'typeof', 'var', 'void', 'while', 'with', 'yield',
    'let', 'static', 'of', 'true', 'false', 'null', 'undefined', 'get', 'set',
    'async', 'from', 'as',
]);
/** Punctuation sorted longest-first so `...` wins over `.` and `===` over `=`. */
const PUNCTS = [
    '>>>=', '...', '===', '!==', '>>>', '<<=', '>>=', '**=', '&&=', '||=', '??=',
    '=>', '==', '!=', '<=', '>=', '&&', '||', '??', '?.', '++', '--', '+=', '-=',
    '*=', '/=', '%=', '&=', '|=', '^=', '<<', '>>', '**',
    '{', '}', '(', ')', '[', ']', ';', ',', '<', '>', '+', '-', '*', '/', '%',
    '&', '|', '^', '!', '~', '?', ':', '=', '.',
];
/**
 * Tokenize one bundle into an array of tokens.
 *
 * A scanner, not a parser: comments and strings are consumed atomically, regex
 * literals are distinguished from division by the previous meaningful token, and
 * everything else is an identifier, number, template string, or punctuation.
 */
export function tokenize(source) {
    const tokens = [];
    const length = source.length;
    let index = 0;
    let line = 0;
    const push = (kind, value, start, end) => {
        tokens.push({ kind, value, start, end, line });
    };
    /** Regexes may start after these punctuations/keywords (division elsewhere). */
    const regexAllowedAfter = (previous) => {
        if (previous === undefined)
            return true;
        if (previous.kind === 'keyword') {
            return !['this', 'super', 'true', 'false', 'null', 'undefined'].includes(previous.value);
        }
        if (previous.kind !== 'punct')
            return false;
        return ![')', ']', '}', '++', '--'].includes(previous.value);
    };
    while (index < length) {
        const char = source[index];
        if (char === '\n') {
            line += 1;
            index += 1;
            continue;
        }
        if (char === ' ' || char === '\t' || char === '\r' || char === '' || char === '') {
            index += 1;
            continue;
        }
        if (char === '/' && source[index + 1] === '/') {
            while (index < length && source[index] !== '\n')
                index += 1;
            continue;
        }
        if (char === '/' && source[index + 1] === '*') {
            const end = source.indexOf('*/', index + 2);
            const stop = end < 0 ? length : end + 2;
            for (let at = index; at < stop; at += 1)
                if (source[at] === '\n')
                    line += 1;
            index = stop;
            continue;
        }
        if (char === '"' || char === "'") {
            const start = index;
            index += 1;
            let value = '';
            while (index < length) {
                const c = source[index];
                if (c === '\\') {
                    value += source[index] + (source[index + 1] ?? '');
                    if (source[index + 1] === '\n')
                        line += 1;
                    index += 2;
                    continue;
                }
                if (c === char) {
                    index += 1;
                    break;
                }
                if (c === '\n') {
                    line += 1;
                }
                value += c;
                index += 1;
            }
            push('string', value, start, index);
            continue;
        }
        if (char === '`') {
            const start = index;
            index += 1;
            let value = '';
            let depth = 0;
            while (index < length) {
                const c = source[index];
                if (c === '\\') {
                    value += source[index] + (source[index + 1] ?? '');
                    index += 2;
                    continue;
                }
                if (c === '`' && depth === 0) {
                    index += 1;
                    break;
                }
                if (c === '$' && source[index + 1] === '{') {
                    depth += 1;
                    value += '${';
                    index += 2;
                    continue;
                }
                if (c === '}' && depth > 0) {
                    depth -= 1;
                    value += c;
                    index += 1;
                    continue;
                }
                if (c === '\n')
                    line += 1;
                value += c;
                index += 1;
            }
            push('template', value, start, index);
            continue;
        }
        if (char === '/' && regexAllowedAfter(tokens[tokens.length - 1])) {
            const start = index;
            index += 1;
            let inClass = false;
            while (index < length) {
                const c = source[index];
                if (c === '\\') {
                    index += 2;
                    continue;
                }
                if (c === '\n')
                    break;
                if (c === '[')
                    inClass = true;
                else if (c === ']')
                    inClass = false;
                else if (c === '/' && !inClass) {
                    index += 1;
                    break;
                }
                index += 1;
            }
            while (index < length && /[a-z]/i.test(source[index]))
                index += 1;
            push('regex', source.slice(start, index), start, index);
            continue;
        }
        if (/[0-9]/.test(char) || (char === '.' && /[0-9]/.test(source[index + 1] ?? ''))) {
            const start = index;
            const match = /^(?:0[xX][0-9a-fA-F_]+|0[oO][0-7_]+|0[bB][01_]+|(?:\d[\d_]*\.?[\d_]*|\.[\d_]+)(?:[eE][+-]?[\d_]+)?n?)/.exec(source.slice(index));
            index += match?.[0].length ?? 1;
            push('number', source.slice(start, index), start, index);
            continue;
        }
        if (/[A-Za-z_$]/.test(char)) {
            const start = index;
            while (index < length && /[\w$]/.test(source[index]))
                index += 1;
            const value = source.slice(start, index);
            push(KEYWORDS.has(value) ? 'keyword' : 'ident', value, start, index);
            continue;
        }
        let matched;
        for (const punct of PUNCTS) {
            if (source.startsWith(punct, index)) {
                matched = punct;
                break;
            }
        }
        if (matched === undefined) {
            index += 1;
            continue;
        }
        push('punct', matched, index, index + matched.length);
        index += matched.length;
    }
    return tokens;
}
// ---------------------------------------------------------------------------
// Token cursor (locale reading needs only delimiter matching + top-level splits)
// ---------------------------------------------------------------------------
/** A forward-only walk over one token list with a delimiter matcher. */
class Cursor {
    tokens;
    constructor(tokens) {
        this.tokens = tokens;
    }
    /** Index one past the punct-balanced region opened at `openIndex` (which holds `open`). */
    matchDelimiter(openIndex) {
        const openToken = this.tokens[openIndex];
        if (openToken === undefined || openToken.kind !== 'punct')
            return openIndex + 1;
        const close = { '(': ')', '{': '}', '[': ']' }[openToken.value];
        if (close === undefined)
            return openIndex + 1;
        let depth = 0;
        for (let at = openIndex; at < this.tokens.length; at += 1) {
            const token = this.tokens[at];
            if (token.kind !== 'punct')
                continue;
            if (token.value === openToken.value)
                depth += 1;
            else if (token.value === close) {
                depth -= 1;
                if (depth === 0)
                    return at + 1;
            }
        }
        return this.tokens.length;
    }
}
/** Split one token region at its top-level commas (commas inside brackets stay). */
function splitTopLevel(cursor, start, end) {
    const spans = [];
    let spanStart = start;
    let at = start;
    while (at < end) {
        const token = cursor.tokens[at];
        if (token.kind === 'punct') {
            if (token.value === '(' || token.value === '{' || token.value === '[') {
                at = cursor.matchDelimiter(at);
                continue;
            }
            if (token.value === ',') {
                spans.push([spanStart, at]);
                spanStart = at + 1;
            }
        }
        at += 1;
    }
    if (spanStart < end)
        spans.push([spanStart, end]);
    return spans.filter(([a, b]) => a < b);
}
// ---------------------------------------------------------------------------
// String escape decoding
// ---------------------------------------------------------------------------
/** Decode the escape sequences of one string token body. */
export function decodeEscapes(text) {
    return text
        .replace(/\\u\{([0-9a-fA-F]+)\}/g, (_, hex) => String.fromCodePoint(parseInt(hex, 16)))
        .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
        .replace(/\\x([0-9a-fA-F]{2})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
        .replace(/\\n/g, '\n')
        .replace(/\\r/g, '\r')
        .replace(/\\t/g, '\t')
        .replace(/\\(.)/g, '$1');
}
// ---------------------------------------------------------------------------
// Inline locale dictionaries
// ---------------------------------------------------------------------------
/**
 * Binding names a bundle is likely to give its locale tables. Recognition is
 * structural first; these names simply decide which candidates are checked first
 * when several string-objects exist.
 */
const DICTIONARY_NAMES = new Set(['en', 'zh', 'copy', 'messages', 'strings', 'locale', 'dict', 'translations']);
/**
 * Collect every top-level string-object literal that looks like a locale table.
 *
 * A locale table is recognised structurally: a `const|let|var` initializer whose
 * object literal holds at least a handful of properties and whose values are all
 * quoted strings. Anything else (a config object, a style map) has mixed values
 * and is skipped, so a bundle is never mistranslated as a dictionary.
 */
function collectDictionaries(cursor, into) {
    for (let at = 0; at < cursor.tokens.length - 3; at += 1) {
        const keyword = cursor.tokens[at];
        if (keyword.kind !== 'keyword' || (keyword.value !== 'const' && keyword.value !== 'let' && keyword.value !== 'var'))
            continue;
        const name = cursor.tokens[at + 1];
        const assign = cursor.tokens[at + 2];
        const open = cursor.tokens[at + 3];
        if (name?.kind !== 'ident' || assign?.value !== '=' || open?.kind !== 'punct' || open.value !== '{')
            continue;
        const close = cursor.matchDelimiter(at + 3) - 1;
        const table = readStringTable(cursor, at + 3, close);
        if (table === undefined)
            continue;
        // A style map and a nested config both pass the "string values" test when
        // shallow; a real locale table is wide, so width is the discriminator.
        const isNamed = DICTIONARY_NAMES.has(name.value);
        if (!isNamed && table.size < 24)
            continue;
        if (table.size < 6)
            continue;
        into.set(name.value, table);
    }
}
/**
 * Read one object literal as a flat string table.
 *
 * A real locale table is mostly `key: 'text'`; a nested group or computed value is
 * skipped rather than rejecting the whole table.
 */
function readStringTable(cursor, openAt, closeAt) {
    const table = new Map();
    for (const [a, b] of splitTopLevel(cursor, openAt + 1, closeAt)) {
        const key = cursor.tokens[a];
        if (key === undefined || (key.kind !== 'ident' && key.kind !== 'string'))
            continue;
        if (cursor.tokens[a + 1]?.kind !== 'punct' || cursor.tokens[a + 1]?.value !== ':')
            continue;
        const value = cursor.tokens[a + 2];
        if (b !== a + 3 || value === undefined || value.kind !== 'string')
            continue;
        table.set(key.value, decodeEscapes(value.value));
    }
    return table.size === 0 ? undefined : table;
}
/** Dictionary binding names a missing tag falls back to, in order. */
const DICTIONARY_FALLBACK_ORDER = ['en', 'zh', 'copy', 'strings', 'messages'];
/**
 * Choose the inline dictionary a browser would resolve `t(key)` through.
 *
 * The order is the one the page itself applies: an exact locale tag, then that
 * tag's base language, then the bundle's `en` table, then the widest table it
 * carries. A bundle with no locale tables has no dictionary.
 *
 * @param dictionaries inline tables, as {@link bundleDictionaries} returns them.
 * @param localeTags preferred language tags, most preferred first.
 * @returns the dictionary and the tag/binding it answered with, or undefined.
 */
export function chooseBundleDictionary(dictionaries, localeTags) {
    const pick = (name) => {
        const exact = dictionaries.get(name);
        if (exact !== undefined)
            return exact;
        const base = name.split('-')[0] ?? name;
        return dictionaries.get(base);
    };
    for (const tag of localeTags) {
        const found = pick(tag);
        if (found !== undefined)
            return { name: tag, table: found };
    }
    for (const name of DICTIONARY_FALLBACK_ORDER) {
        const found = dictionaries.get(name);
        if (found !== undefined)
            return { name, table: found };
    }
    let widest;
    let widestName;
    for (const [name, table] of dictionaries) {
        if (widest === undefined || table.size > widest.size) {
            widest = table;
            widestName = name;
        }
    }
    return widest === undefined || widestName === undefined
        ? undefined
        : { name: widestName, table: widest };
}
/**
 * Every inline locale dictionary one bundle carries, keyed by binding name.
 *
 * @param source compiled bundle text.
 * @returns binding name → string table; empty when the bundle carries none.
 */
export function bundleDictionaries(source) {
    const dictionaries = new Map();
    collectDictionaries(new Cursor(tokenize(source)), dictionaries);
    return dictionaries;
}
/**
 * Resolve one dictionary key the way the bundle's own `t(key)` would.
 *
 * @param dictionaries inline tables, as {@link bundleDictionaries} returns them.
 * @param localeTags preferred language tags, most preferred first.
 * @param key dictionary key of the text (`nav`).
 * @returns the text, or undefined when no reachable table carries the key.
 */
export function resolveDictionaryKey(dictionaries, localeTags, key) {
    if (key === undefined)
        return undefined;
    const text = chooseBundleDictionary(dictionaries, localeTags)?.table.get(key);
    return text === undefined || text.trim() === '' ? undefined : text;
}
