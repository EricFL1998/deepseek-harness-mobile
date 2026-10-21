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
    /**
     * Dictionary key the registration's label resolves through.
     *
     * A client bundle that localizes its label writes `label: () => t("nav")`; the
     * bridge resolves that key through the bundle's own copy tables and reports the
     * text as `label`, keeping the key here so a client can tell a resolved
     * translation from a literal. Absent when the registration declares no thunk
     * label, or on responses from older bridges.
     */
    readonly labelKey?: string;
    readonly priority?: number;
    /** Package that owns the contribution, when the evaluating bundle declared one. */
    readonly package?: string;
    /** Native area this registration belongs in; classified by the bridge. */
    readonly placement?: string;
    /** Locale namespace the label resolves through, when declared. */
    readonly locale?: string;
    /** Component identity for `render-tree` / `icon-svg` / `transpile`. */
    readonly render?: {
        readonly kind: 'component' | 'factory';
        readonly componentId: string;
    };
    /**
     * How this registration was read: `static` means the bundle's text was
     * analyzed without running it, `evaluated` means the VM captured the call.
     * Absent on responses from older plugins; clients treat it as unknown.
     */
    readonly origin?: 'static' | 'evaluated';
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
/** `transpile` / `component-tree` data. */
export interface ComponentTreeData {
    readonly componentId: string;
    /** The declared structure, or null when the component is unknown/unreadable. */
    readonly tree: RenderNode | null;
    readonly warnings: readonly string[];
    /** True when a budget stopped the walk; the tree is then a prefix. */
    readonly truncated: boolean;
}
/** `client-slots` / `dispatch` data. */
export interface DispatchData {
    readonly ok: true;
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
    /**
     * The Loader entry this row resolves to, when the Host reports one.
     *
     * Absent means the row is not live, so it cannot be toggled through the profile
     * patch; present means the entry exists even while it is disabled.
     */
    readonly entryId?: string;
    readonly title?: string;
    readonly description?: string;
    readonly icon?: string;
    /**
     * Whether this row is enabled, joined from the Loader inventory.
     *
     * Absent only when the composition has no inventory service; a row with no live
     * entry reports `false`.
     */
    readonly enabled?: boolean;
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
/**
 * One contribution the Host can enumerate server-side, named with the native
 * surface it belongs in.
 *
 * `slot` is the native placement, not a browser slot name: a phone app maps
 * `settings.page` to its own Settings list. Keeping the browser vocabulary here
 * would force every client to re-derive the placement.
 */
export interface SlotSurface {
    /** Native placement, such as `settings.page`. */
    readonly slot: string;
    /** Stable identity within that placement. */
    readonly id: string;
    readonly title: string;
    readonly description?: string;
    /** Module that contributes the surface, when the Host reports one. */
    readonly module?: string;
}
/** `host-slots` / `list` data. */
export interface SlotSurfaceData {
    readonly surfaces: readonly SlotSurface[];
}
/** Route plugins POST a transient message to. */
export declare const EVENTS_PATH = "/dsh-mobile/bridge/v1/events";
/**
 * A plugin's own native-layout binding for one component (`dsh.mobile.layout`).
 *
 * A static tree is the declared structure; a chip that shows live numbers cannot be
 * read that way, because those numbers are computed at runtime. A plugin that wants
 * a native look for such a slot names a native component and the data paths to bind,
 * and the client renders that instead of the raw tree.
 */
export interface MobileLayoutData {
    /** Native component the client knows how to draw (`value-chip` today). */
    readonly kind: string;
    /** Host method the bound values come from. */
    readonly data?: {
        readonly method: string;
        readonly payload?: Record<string, unknown>;
    };
    /** Prop name to path into the method's result object. */
    readonly bind?: Record<string, string>;
    /** Optional static label shown next to the values. */
    readonly label?: string;
}
export interface ComponentTreeData {
    /** Present when the plugin declares a native layout for this component. */
    readonly layout?: MobileLayoutData | null;
}
export interface ComponentTreeData {
    /** Set when a data source is fetched once per provider. */
    readonly data?: readonly {
        readonly state: string;
        readonly method: string;
        readonly payload?: Record<string, unknown>;
        readonly scope?: 'provider';
    }[];
}
export interface ComponentTreeData {
    /**
     * API prefix the package's own methods are served under (`<prefix>.<method>`).
     *
     * The browser `call("status")` resolves to `<prefix>.status`; reporting it keeps a
     * client from guessing the prefix from the package name.
     */
    readonly apiPrefix?: string;
}
export interface DispatchData {
    /** True when a plugin Host half actually ran the reported action. */
    readonly handled?: boolean;
}
export interface ComponentTreeData {
    /** Host methods the page's buttons call, offered when no native action is registered. */
    readonly actions?: readonly {
        readonly method: string;
        readonly payload?: Record<string, unknown>;
    }[];
}
