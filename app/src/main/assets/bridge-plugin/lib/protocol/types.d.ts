/**
 * Wire types of the `bridge/v1` protocol.
 *
 * These types are the single source of truth for the JSON that native clients
 * consume. Everything a provider returns must be JSON-serializable: the bridge
 * never hands out class instances, functions, or cyclic values.
 */
/** Protocol version reported by `GET /manifest`. Clients refuse an unknown major. */
export declare const BRIDGE_VERSION = "1.0.0";
/** URL prefix every route lives under. */
export declare const BRIDGE_BASE_PATH = "/dsh-mobile/bridge/v1";
/** Route serving the provider catalog. */
export declare const MANIFEST_PATH = "/dsh-mobile/bridge/v1/manifest";
/** Route serving one provider query. */
export declare const QUERY_PATH = "/dsh-mobile/bridge/v1/query";
/** One provider's negotiated surface. */
export interface ProviderManifestEntry {
    /** Stable provider id; clients switch on it and ignore unknown ids. */
    readonly id: string;
    /** Provider schema version. A client refuses an id it knows at a different version. */
    readonly version: number;
    /** Operation names this provider answers. */
    readonly capabilities: readonly string[];
    /** True when the provider never mutates Host state. Omitted means read-write. */
    readonly readOnly?: true;
    /** Authorization model; `host-session` means the caller's existing Host session cookie. */
    readonly authorization?: 'host-session';
}
/** `GET /manifest` response body. */
export interface ManifestResponse {
    readonly bridgeVersion: string;
    /**
     * The bridge plugin package's own version. It moves independently of
     * `bridgeVersion`: the protocol can stay compatible while the plugin gains
     * providers, and a native client uses this to tell a stale install from a
     * current one.
     */
    readonly pluginVersion: string;
    /** The Host release the bridge runs inside; `null` when the Host publishes none. */
    readonly dshVersion: string | null;
    readonly providers: readonly ProviderManifestEntry[];
    /** Non-fatal assembly notes, such as a provider that could not bind its service. */
    readonly warnings?: readonly string[];
}
/** `POST /query` request body. */
export interface QueryRequest {
    readonly provider: string;
    readonly op: string;
    readonly args?: Readonly<Record<string, unknown>>;
}
/** `POST /query` success body. */
export interface QuerySuccess {
    readonly ok: true;
    readonly provider: string;
    readonly version: number;
    readonly data: unknown;
    readonly warnings: readonly string[];
}
/** Stable failure codes. A client maps these to copy; the message is diagnostic only. */
export type BridgeErrorCode = 'request/malformed' | 'request/unsupported-method' | 'provider/unknown' | 'provider/unavailable' | 'provider/version-mismatch' | 'op/unknown' | 'args/invalid' | 'budget/exceeded' | 'auth/required' | 'internal/error';
/** `POST /query` failure body. */
export interface QueryFailure {
    readonly ok: false;
    readonly error: {
        readonly code: BridgeErrorCode;
        readonly message: string;
        /** Optional structured detail; shape depends on the code. */
        readonly details?: unknown;
    };
}
/** Any `/query` response body. Errors never partially succeed. */
export type QueryResponse = QuerySuccess | QueryFailure;
/** One boot-manifest entry, as the served shell injects it. */
export interface BootEntry {
    readonly id: string;
    readonly url: string;
    readonly rev: string;
    readonly inject?: readonly string[];
    readonly immediately?: boolean;
    readonly external?: readonly string[];
}
/** One initial combo script descriptor. */
export interface BootBatch {
    readonly phase: string;
    readonly url: string;
    readonly rev: string;
    readonly entries: readonly string[];
}
/** `client-assets` / `boot-graph` data. */
export interface BootGraphData {
    readonly rev: string;
    readonly release: {
        readonly version: string;
        readonly commit?: string;
    } | null;
    readonly entries: readonly BootEntry[];
    readonly batches: readonly BootBatch[];
}
/** `client-assets` / `module-source` data. Bounded by the provider's byte budget. */
export interface ModuleSourceData {
    readonly id: string;
    /** Path on the Host that produced the bytes; clients treat it as diagnostic. */
    readonly path: string;
    readonly bytes: number;
    /** True when `bytes` is the whole file rather than the truncated head. */
    readonly complete: boolean;
    /** Lowercase hex SHA-256 over the returned bytes. */
    readonly sha256: string;
    readonly source: string;
}
/** `client-assets` / `locale` data for one package namespace. */
export interface LocaleData {
    readonly namespace: string;
    /** Requested locale tag, resolved or echoed back unchanged when unavailable. */
    readonly locale: string;
    /** Locale tags this namespace publishes. */
    readonly available: readonly string[];
    /** Flat key → text dictionary, empty when the namespace publishes none. */
    readonly dictionary: Readonly<Record<string, string>>;
}
/** One slot registration as data. */
export interface SlotRegistration {
    readonly slot: string;
    readonly id?: string;
    readonly key?: string;
    readonly order?: number;
    readonly label?: string;
    readonly priority?: number;
    /** Package that owns the contribution, when the evaluating bundle declared one. */
    readonly package?: string;
    /** Locale namespace the label resolves through, when declared. */
    readonly locale?: string;
    /** Component identity for `render-tree` / `icon-svg`. */
    readonly render?: {
        readonly kind: 'component' | 'factory';
        readonly componentId: string;
    };
}
/** `client-slots` / `list` data. */
export interface SlotListData {
    readonly slot: string | null;
    readonly registrations: readonly SlotRegistration[];
    /** Slots that exist in the evaluated bundles but contributed nothing. */
    readonly emptySlots: readonly string[];
}
/** One serialized element in a render tree. */
export interface ElementNode {
    readonly kind: 'element';
    readonly type: string;
    readonly key: string | null;
    readonly props: Readonly<Record<string, unknown>>;
    readonly children: readonly RenderNode[];
}
/** A text child of an element. */
export interface TextNode {
    readonly kind: 'text';
    readonly value: string;
}
/** A hole the stub could not resolve into JSON. */
export interface OpaqueNode {
    readonly kind: 'opaque';
    readonly reason: string;
}
/** Any node of a serialized render tree. */
export type RenderNode = ElementNode | TextNode | OpaqueNode;
/** `client-slots` / `render-tree` data. */
export interface RenderTreeData {
    readonly componentId: string;
    readonly tree: RenderNode | null;
}
/** `client-slots` / `icon-svg` data. */
export interface IconSvgData {
    readonly componentId: string;
    readonly size: number;
    /** Complete `<svg>` document text, or `null` when the component drew no path. */
    readonly svg: string | null;
}
/** `package-inventory` / `bundles` row, projected from the Host's own records. */
export interface InventoryBundle {
    readonly name: string;
    readonly official: boolean;
    readonly optional: boolean;
    readonly removable: boolean;
    readonly enabled: boolean;
    readonly installed: boolean;
    readonly availability: string;
    readonly version?: string;
    readonly source?: string;
    readonly title?: string;
    readonly description?: string;
    /** `meta.icon` passed through untouched: a data URL owned by the package. */
    readonly icon?: string;
    readonly rows: readonly InventoryBundleRow[];
    readonly errorCode?: string;
}
/** One row a bundle's patch declares. */
export interface InventoryBundleRow {
    readonly rowId: string;
    readonly moduleName: string;
    readonly title?: string;
    readonly description?: string;
    readonly icon?: string;
}
/** `package-inventory` / `rows` row, projected from the Loader inventory. */
export interface InventoryRow {
    readonly entryId: string;
    readonly moduleName: string;
    readonly enabled: boolean;
    readonly fiberPhase: string | null;
    readonly title?: string;
    readonly description?: string;
    readonly icon?: string;
}
/** `package-inventory` / `bundles` and `/rows` data. */
export interface InventoryData {
    readonly bundles?: readonly InventoryBundle[];
    readonly rows?: readonly InventoryRow[];
    /** Whether the Host manages a profile at all. */
    readonly managementAvailable: boolean;
}
/** `rpc` / `invoke` data. */
export interface RpcInvokeData {
    readonly namespace: string;
    readonly method: string;
    readonly result: unknown;
}
