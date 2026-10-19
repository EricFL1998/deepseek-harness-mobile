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
export const OFFICIAL_ARTWORK = {
    shell: `<svg viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">`
        + `<path d="M10 11L16.606 17.606C16.6841 17.6841 16.6841 17.8107 16.606 17.8888L10 24.4948" stroke="#679EFE" stroke-width="3.5"/>`
        + `<path d="M20.1211 24.4946H26.8685" stroke="#679EFE" stroke-width="3.5"/></svg>`,
    'agent-loop': `<svg viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">`
        + `<path d="M18.0486 28.4901C12.0858 28.4901 7.25195 23.6562 7.25195 17.6934C13.2148 17.6934 18.0486 22.5272 18.0486 28.4901Z" fill="#A797FC"/>`
        + `<path d="M18.0486 6.89667C12.0858 6.89667 7.25195 11.7305 7.25195 17.6934C13.2148 17.6934 18.0486 12.8595 18.0486 6.89667Z" fill="url(#bridge-loop-a)"/>`
        + `<path d="M18.0485 28.4901C24.0114 28.4901 28.8452 23.6562 28.8452 17.6934C22.8824 17.6934 18.0485 22.5272 18.0485 28.4901Z" fill="#4561EE"/>`
        + `<path d="M18.0485 6.89667C24.0114 6.89667 28.8452 11.7305 28.8452 17.6934C22.8824 17.6934 18.0485 12.8595 18.0485 6.89667Z" fill="#658EFF"/>`
        + `<defs><linearGradient id="bridge-loop-a" x1="16.7911" y1="16.1655" x2="8.98192" y2="8.74289" gradientUnits="userSpaceOnUse">`
        + `<stop stop-color="#A23AE7"/><stop offset="1" stop-color="#E2E2E2"/></linearGradient></defs></svg>`,
    subagent: `<svg viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">`
        + `<rect x="14.9893" y="15.1877" width="12.2365" height="12.2365" rx="2" fill="url(#bridge-sub-a)" fill-opacity="0.8"/>`
        + `<rect x="8.87109" y="9.0697" width="12.2365" height="12.2365" rx="2" fill="url(#bridge-sub-b)" fill-opacity="0.8"/>`
        + `<defs><linearGradient id="bridge-sub-a" x1="21.1075" y1="15.1877" x2="21.1075" y2="27.4243" gradientUnits="userSpaceOnUse">`
        + `<stop stop-color="#45E7A4"/><stop offset="1" stop-color="#05909D"/></linearGradient>`
        + `<linearGradient id="bridge-sub-b" x1="14.9894" y1="9.0697" x2="14.9894" y2="21.3063" gradientUnits="userSpaceOnUse">`
        + `<stop stop-color="#69B9FF"/><stop offset="1" stop-color="#324DE2"/></linearGradient></defs></svg>`,
    // The official search ring paints its conic gradient through a `foreignObject`,
    // which standalone SVG consumers do not render. A stroked ring carries the same
    // silhouette with the artwork's two colors as a real SVG gradient.
    'web-search': `<svg viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">`
        + `<path d="M26.5362 26.9865L22.3813 22.8317" stroke="#658EFF" stroke-width="3"/>`
        + `<circle cx="15.7661" cy="16.2159" r="7.4" stroke="url(#bridge-search-ring)" stroke-width="2"/>`
        + `<defs><linearGradient id="bridge-search-ring" x1="8.3" y1="8.8" x2="23.2" y2="23.6" gradientUnits="userSpaceOnUse">`
        + `<stop stop-color="#41E1AC"/><stop offset="0.172" stop-color="#658EFF"/><stop offset="1" stop-color="#41E1AC"/>`
        + `</linearGradient></defs></svg>`,
};
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
