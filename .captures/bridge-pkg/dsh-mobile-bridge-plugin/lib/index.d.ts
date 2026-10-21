/**
 * dsh-mobile-bridge: a Host plugin that exposes the official web client's own
 * runtime data to native clients as a versioned, capability-negotiated JSON
 * protocol.
 *
 * The plugin owns two routes on the composition's `webServer`
 * (`/dsh-mobile/bridge/v1/manifest` and `/dsh-mobile/bridge/v1/query`) and a
 * provider table behind them. It extracts data; clients render. Every feature is
 * a provider, so a new compatibility feature adds a provider and leaves the wire
 * format alone.
 */
import type { Context } from '@deepseek-ai/cordis';
import z from '@deepseek-ai/schemastery';
/** Cordis function-plugin name. */
export declare const name = "dsh-mobile-bridge-plugin";
/** The routes need the Host's HTTP carrier; every other service is optional. */
export declare const inject: string[];
/** One allowlisted remote method, as configured. */
export interface RpcAllowConfig {
    /** Remote namespace, such as `settings`. */
    readonly namespace: string;
    /** Exported method name within that namespace. */
    readonly method: string;
}
/** Bridge configuration; every deployment-varying choice lives here. */
export interface Config {
    /** Serve the bridge at all. Disabled leaves the routes unregistered. */
    readonly enabled: boolean;
    /**
     * Entry ids `client-slots` evaluates. Empty means every entry the Host
     * composes, which is the honest default: the client asks for the slots it
     * wants and the bridge reports what the installed bundles register.
     */
    readonly slotEntries: string[];
    /** Whether to evaluate the client halves on disk when the Host composes no graph. */
    readonly clientBundleScan: boolean;
    /** Bytes one `client-assets/module-source` call may return. */
    readonly maxModuleBytes: number;
    /** Largest `size` an `client-slots/icon-svg` call may request. */
    readonly maxIconSize: number;
    /** Coordinate space the client's icons are authored in. */
    readonly iconViewBox: string;
    /** Depth ceiling for one `render-tree` call. */
    readonly maxRenderDepth: number;
    /** Node ceiling for one `render-tree` call. */
    readonly maxRenderNodes: number;
    /** Byte ceiling for one serialized render tree. */
    readonly maxRenderBytes: number;
    /** Locale tags the `client-assets/locale` op reports. */
    readonly localeTags: string[];
    /** Remote methods a native client may invoke through the `rpc` provider. */
    readonly rpcAllowlist: RpcAllowConfig[];
    /** Byte ceiling for one `rpc/invoke` result. */
    readonly maxRpcResultBytes: number;
    /** Deadline for one `rpc/invoke` call. */
    readonly rpcTimeoutMs: number;
    /**
     * Require the Host's own connection trust fence for every bridge request.
     * On by default: the bridge never bypasses DSH remote access.
     */
    readonly requireHostSession: boolean;
}
/** Config schema; `schemastery` applies these defaults through the Loader. */
export declare const Config: z<Config>;
/**
 * Register the bridge routes.
 * @param ctx - Host plugin context.
 * @param config - resolved plugin config.
 */
export declare function apply(ctx: Context, config: Config): void;
/** Re-export the protocol surface for a standalone carrier and for tests. */
export * from './protocol/types.ts';
export { ProviderRegistry, BridgeFailure } from './protocol/provider.ts';
export { createBridgeHandler, writeReply } from './handler.ts';
export { evaluateBundleSource } from './slots/evaluate.ts';
export { createReactStub } from './slots/react-stub.ts';
export { renderComponent, treeToSvg } from './slots/render-tree.ts';
export { bundleDictionaries, chooseBundleDictionary, resolveDictionaryKey, decodeEscapes } from './slots/bundle-locale.ts';
export type { BundleDictionary } from './slots/bundle-locale.ts';
/** Read this package's own version, for a standalone carrier's manifest. */
export declare function packageVersion(): string;
