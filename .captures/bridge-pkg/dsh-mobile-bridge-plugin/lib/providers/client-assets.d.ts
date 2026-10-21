/**
 * `client-assets`: the web boot graph, one entry's bundle source, and a
 * package's locale dictionary.
 *
 * Everything here comes from what the Host itself serves the official shell —
 * `clientModules.graph()` and the bundle files it resolved. The provider parses
 * no React and interprets no UI; it only reports facts a native client cannot
 * see for itself.
 */
import { type Provider } from '../protocol/provider.ts';
/** Boot graph row as the Host composes it. */
export interface HostBootEntry {
    readonly id: string;
    readonly url: string;
    readonly rev: string;
    readonly inject?: readonly string[];
    readonly immediately?: boolean;
    readonly external?: readonly string[];
}
/** Boot batch descriptor as the Host composes it. */
export interface HostBootBatch {
    readonly phase: string;
    readonly url: string;
    readonly rev: string;
    readonly entries: readonly string[];
}
/** The composed boot graph, structurally the Host's `WebBootGraph`. */
export interface HostBootGraph {
    readonly rev: string;
    readonly release?: {
        readonly version: string;
        readonly commit?: string;
    };
    readonly entries: readonly HostBootEntry[];
    readonly batches: readonly HostBootBatch[];
}
/**
 * The slice of the Host's `clientModules` service this provider reads.
 * Declared locally so the package compiles without the Host's types.
 */
export interface ClientModulesFace {
    /** Current composed graph, or undefined when the Host has no web carrier. */
    graph(): HostBootGraph | undefined;
    /** Absolute path of one entry's client bundle. */
    clientPath(id: string): string | undefined;
}
/** `client-assets` configuration. */
export interface ClientAssetsOptions {
    /** Bytes of module source one `module-source` call may return. */
    readonly maxModuleBytes: number;
    /** Locale tags the `locale` op may report. */
    readonly localeTags: readonly string[];
    /** Resolve the client-modules service, or undefined when absent. */
    readonly clientModules: () => ClientModulesFace | undefined;
    /** Loader row names mounted in this Host, for boot-graph diagnostics. */
    readonly loaderRows?: () => readonly string[];
}
/**
 * Build the `client-assets` provider.
 * @param options - service accessor and budgets.
 * @returns the provider to register.
 */
export declare function createClientAssetsProvider(options: ClientAssetsOptions): Provider;
