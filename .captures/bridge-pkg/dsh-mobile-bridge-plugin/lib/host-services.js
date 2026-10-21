/**
 * The Host services this plugin reads, declared structurally.
 *
 * The bridge is published separately from the Harness, so it must compile
 * against the published `@deepseek-ai/cordis` alone. Each interface here names
 * only the members the bridge actually calls; the Host's real services satisfy
 * them, and a composition that lacks one reports `provider/unavailable` instead
 * of failing to load.
 *
 * Services are read through `ctx.get(name)`, never `ctx.<name>`: a property read
 * would need a Context augmentation that only the Host's own packages can
 * legitimately declare.
 */
/**
 * Read the Host's HTTP carrier.
 * @param ctx - the plugin context.
 * @returns the carrier, or undefined when the composition mounts no HTTP server.
 */
export function webServerOf(ctx) {
    return ctx.get('webServer');
}
