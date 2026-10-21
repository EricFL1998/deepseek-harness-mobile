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
/** One lexical token of the compiled bundle. */
interface Token {
    readonly kind: 'ident' | 'keyword' | 'string' | 'number' | 'template' | 'regex' | 'punct';
    readonly value: string;
    readonly start: number;
    readonly end: number;
    /** Source line (0-based) of `start`; diagnostics only. */
    readonly line: number;
}
/**
 * Tokenize one bundle into an array of tokens.
 *
 * A scanner, not a parser: comments and strings are consumed atomically, regex
 * literals are distinguished from division by the previous meaningful token, and
 * everything else is an identifier, number, template string, or punctuation.
 */
export declare function tokenize(source: string): Token[];
/** Decode the escape sequences of one string token body. */
export declare function decodeEscapes(text: string): string;
/** One inline locale dictionary a bundle carries. */
export interface BundleDictionary {
    /** Binding name the bundle gives the table (`en`, `zh`, …). */
    readonly name: string;
    /** Key → text of that language. */
    readonly table: ReadonlyMap<string, string>;
}
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
export declare function chooseBundleDictionary(dictionaries: ReadonlyMap<string, ReadonlyMap<string, string>>, localeTags: readonly string[]): BundleDictionary | undefined;
/**
 * Every inline locale dictionary one bundle carries, keyed by binding name.
 *
 * @param source compiled bundle text.
 * @returns binding name → string table; empty when the bundle carries none.
 */
export declare function bundleDictionaries(source: string): ReadonlyMap<string, ReadonlyMap<string, string>>;
/**
 * Resolve one dictionary key the way the bundle's own `t(key)` would.
 *
 * @param dictionaries inline tables, as {@link bundleDictionaries} returns them.
 * @param localeTags preferred language tags, most preferred first.
 * @param key dictionary key of the text (`nav`).
 * @returns the text, or undefined when no reachable table carries the key.
 */
export declare function resolveDictionaryKey(dictionaries: ReadonlyMap<string, ReadonlyMap<string, string>>, localeTags: readonly string[], key: string | undefined): string | undefined;
export {};
