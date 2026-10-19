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
import type { Context } from '@deepseek-ai/cordis';
import type { IncomingMessage, ServerResponse } from 'node:http';
/** One route the Host's HTTP carrier accepts. */
export interface HostWebRoute {
    /** `exact` matches the pathname verbatim; `prefix` matches the path and everything under it. */
    readonly kind: 'exact' | 'prefix';
    /** Absolute pathname, no trailing slash. */
    readonly path: string;
    /** Owns the full response lifecycle. */
    readonly handler: (request: IncomingMessage, response: ServerResponse) => void | Promise<void>;
}
/** The Host's route-registration service. */
export interface HostWebServer {
    /**
     * Register one route.
     * @param route - the route to serve.
     * @returns the disposer that removes it.
     */
    register(route: HostWebRoute): () => void;
}
/** The Host's connection trust fence. */
export interface HostConnection {
    /** The peer a bridge-originated remote call speaks for. */
    readonly browserPeer?: unknown;
    /**
     * Apply the Host/Origin fence and require an authenticated caller.
     * @param request - the request facts the fence reads.
     * @returns the HTTP status to refuse with, or undefined to admit.
     */
    requestRejection?(request: {
        readonly headers: IncomingMessage['headers'];
    }): number | undefined;
}
/**
 * Read the Host's HTTP carrier.
 * @param ctx - the plugin context.
 * @returns the carrier, or undefined when the composition mounts no HTTP server.
 */
export declare function webServerOf(ctx: Context): HostWebServer | undefined;
