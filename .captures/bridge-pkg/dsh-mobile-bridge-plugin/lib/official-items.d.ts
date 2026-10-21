/** One official plugin as the page lists it. */
export interface OfficialPluginItem {
    /** Registration id, which is also the artwork key. */
    readonly id: string;
    /** Position in the Official group; the page sorts by it. */
    readonly order: number;
    /** Package that registers the item and owns its locale files. */
    readonly packageName: string;
}
/**
 * The official roster in page order.
 *
 * These ids are the registration ids the official settings pages use; they are the
 * same keys the page's own `ITEM_ARTWORK` map switches on.
 */
export declare const OFFICIAL_PLUGIN_ITEMS: readonly OfficialPluginItem[];
/**
 * Fixed-palette artwork, one standalone SVG document per id.
 *
 * Transcribed from the official components: stroke widths, stops and viewBox are
 * theirs. Gradient ids are fixed rather than per-instance because a standalone
 * document is rendered once per card, never duplicated in one DOM.
 */
/**
 * Official plugin artwork, generated from `ui-primitives`' source.
 *
 * Re-exported rather than declared here: the drawings are produced by
 * `scripts/generate-artwork.mjs` from the component the web shell actually renders,
 * so this file carries no hand-copied path data.
 */
export { OFFICIAL_ARTWORK } from './official-artwork.generated.ts';
/** Text the page shows for one official plugin. */
export interface OfficialItemText {
    readonly title: string;
    readonly description?: string;
}
/**
 * Read one official plugin's title and description from the Host's own locale file.
 * @param installModules - absolute path of the Host installation's `node_modules`.
 * @param item - the roster row to read.
 * @param localeTag - preferred locale tag, such as `zh`.
 * @returns the text, or undefined when the package or its locale is absent.
 */
export declare function readOfficialItemText(installModules: string, item: OfficialPluginItem, localeTag: string): Promise<OfficialItemText | undefined>;
