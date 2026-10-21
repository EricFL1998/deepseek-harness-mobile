/**
 * `rpc`: one generic proxy for whitelisted Host remotes.
 *
 * Authorization is the caller's existing Host session cookie: the carrier has
 * already admitted the request through the Host's own connection fence before a
 * provider runs, so this provider never mints or forwards credentials. What it
 * adds is an allowlist and size caps, so a native client can reach exactly the
 * methods its profile names and nothing else.
 */
import { type Provider } from '../protocol/provider.ts';
/** One allowlisted remote method. */
export interface RpcAllowEntry {
    /** Remote namespace, such as `settings`. */
    readonly namespace: string;
    /** Exported method name within that namespace. */
    readonly method: string;
}
/** The slice of the Host gateway this provider calls. */
export interface GatewayFace {
    invoke(request: {
        readonly namespace: string;
        readonly method: string;
        readonly args: Readonly<Record<string, unknown>>;
        readonly peer: unknown;
        readonly signal?: AbortSignal;
    }): Promise<unknown>;
}
/** `rpc` configuration. */
export interface RpcOptions {
    /** Resolve the Host gateway, or undefined when the composition has none. */
    readonly gateway: () => GatewayFace | undefined;
    /** Methods a client may call. An empty list disables the provider. */
    readonly allowlist: readonly RpcAllowEntry[];
    /** Largest serialized result, in bytes. */
    readonly maxResultBytes: number;
    /** Deadline for one invocation. */
    readonly timeoutMs: number;
    /** The peer a bridge-originated call speaks for. */
    readonly peer: unknown;
}
/**
 * Build the `rpc` provider.
 * @param options - gateway accessor, allowlist, and caps.
 * @returns the provider to register.
 */
export declare function createRpcProvider(options: RpcOptions): Provider;
