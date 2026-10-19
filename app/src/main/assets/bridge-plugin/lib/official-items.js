/**
 * The official plugins the Host's own Plugins page lists in its Official group.
 *
 * The page gets these from `plugins.item` slot registrations, which live in client
 * bundles. A phone client cannot run those bundles, and on a prebuilt web client
 * there is no composable entry graph to evaluate at all, so the bridge carries the
 * roster itself.
 *
 * Split by kind, deliberately:
 *
 * - **Text comes from the Host at runtime.** Each official plugin is a package
 *   (`@deepseek-ai/dsh-client-ui-settings-*`) whose `locale/<tag>.json` holds the
 *   title and description the page shows. The bridge reads those files, so a DSH
 *   release that renames a page changes what the phone shows, and no Chinese or
 *   English string is invented here.
 * - **Artwork is a snapshot.** The cards are React components
 *   (`@deepseek-ai/dsh-client-ui-primitives/plugin-artwork`) drawing fixed-palette
 *   SVG on a 36×36 viewBox. They are transcription, not data, so they live here and
 *   are the one thing that could drift with a DSH redesign.
 *
 * Nothing is reported for a package that is not installed: an item only appears
 * when the Host actually ships the plugin behind it.
 */
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
/**
 * The official roster in page order.
 *
 * These ids are the registration ids the official settings pages use; they are the
 * same keys the page's own `ITEM_ARTWORK` map switches on.
 */
export const OFFICIAL_PLUGIN_ITEMS = [
    { id: 'agent-loop', order: 30, packageName: '@deepseek-ai/dsh-client-ui-settings-agent-loop' },
    { id: 'shell', order: 40, packageName: '@deepseek-ai/dsh-client-ui-settings-shell' },
    { id: 'subagent', order: 50, packageName: '@deepseek-ai/dsh-client-ui-settings-subagent' },
    { id: 'web-search', order: 60, packageName: '@deepseek-ai/dsh-client-ui-settings-web-search' },
];
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
export { OFFICIAL_ARTWORK } from "./official-artwork.generated.js";
/**
 * Read one official plugin's title and description from the Host's own locale file.
 * @param installModules - absolute path of the Host installation's `node_modules`.
 * @param item - the roster row to read.
 * @param localeTag - preferred locale tag, such as `zh`.
 * @returns the text, or undefined when the package or its locale is absent.
 */
export async function readOfficialItemText(installModules, item, localeTag) {
    const localeDir = join(installModules, item.packageName, 'locale');
    // Preferred locale first, then English, then whatever single file exists: the
    // page has to render something rather than dropping an installed plugin.
    const candidates = [`${localeTag}.json`, 'en.json'];
    for (const file of candidates) {
        const text = await readLocaleMeta(join(localeDir, file));
        if (text !== undefined)
            return text;
    }
    return undefined;
}
/** Read `meta.title`/`meta.description` from one locale document. */
async function readLocaleMeta(path) {
    try {
        const parsed = JSON.parse(await readFile(path, 'utf8'));
        const title = typeof parsed.meta?.title === 'string' ? parsed.meta.title : undefined;
        if (title === undefined || title.length === 0)
            return undefined;
        const description = typeof parsed.meta?.description === 'string'
            ? parsed.meta.description
            : undefined;
        return { title, ...(description === undefined ? {} : { description }) };
    }
    catch {
        return undefined;
    }
}
