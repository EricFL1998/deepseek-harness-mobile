/**
 * The transport-independent bridge handler.
 *
 * One function answers both carriers — the Host's `webServer` routes and the
 * standalone dev server — so the wire behavior has exactly one home and both
 * carriers are testable without the other.
 */
import type { IncomingMessage, ServerResponse } from 'node:http';
import { ProviderRegistry } from './protocol/provider.ts';
/** What the handler needs to answer. */
export interface BridgeHandlerOptions {
    /** The provider table. */
    readonly registry: ProviderRegistry;
    /** The bridge plugin package's own version. */
    readonly pluginVersion: () => string;
    /** The Host release label, or null when the Host publishes none. */
    readonly dshVersion: () => string | null;
    /** Notes collected at assembly time, reported on every manifest. */
    readonly assemblyWarnings: readonly string[];
    /**
     * Decide whether a request may proceed.
     * @param request - the raw Node request.
     * @returns the HTTP status to refuse with, or undefined to admit.
     */
    readonly admit?: (request: IncomingMessage) => number | undefined;
}
/** One response the handler decided on. */
export interface BridgeReply {
    readonly status: number;
    readonly body: unknown;
    /** Extra response headers beyond `content-type`. */
    readonly headers?: Readonly<Record<string, string>>;
}
/**
 * Build the request handler.
 * @param options - registry, version facts, and admission policy.
 * @returns the handler both carriers call.
 */
export declare function createBridgeHandler(options: BridgeHandlerOptions): (request: IncomingMessage) => Promise<BridgeReply>;
/**
 * Write one handler reply onto a Node response.
 * @param response - the Node response to own.
 * @param reply - the decided status, body, and headers.
 */
export declare function writeReply(response: ServerResponse, reply: BridgeReply): void;
