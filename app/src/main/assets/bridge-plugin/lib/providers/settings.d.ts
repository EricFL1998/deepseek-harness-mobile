/**
 * `settings`: the Host's live plugin configuration documents.
 *
 * A plugin's Settings page is a generated form: the Host projects the plugin's
 * `Config` schema into a field tree and stores the user's overrides. This provider
 * exposes exactly that pair — the form and its values — plus the write the page
 * needs, so a native client renders the *same* form the browser does instead of
 * guessing at fields.
 */
import { type Provider } from '../protocol/provider.ts';
/** One field in a projected settings form. */
export interface SettingsFieldNode {
    /** Field kind: `string`, `number`, `boolean`, `object`, `array`, `union`, … */
    readonly type?: string;
    /** Human label, when the schema sets one. */
    readonly description?: string;
    /** Declared default. */
    readonly default?: unknown;
    /** Enum members, when constrained. */
    readonly options?: readonly unknown[];
    /** Child fields for object/record nodes. */
    readonly dict?: Readonly<Record<string, SettingsFieldNode>>;
    /** Element field for arrays. */
    readonly inner?: SettingsFieldNode;
    /** Whether the field may be edited. */
    readonly meta?: unknown;
}
/** One settings document, structurally the Host's descriptor. */
export interface SettingsDocument {
    readonly ns: string;
    readonly revision: number;
    readonly applies?: string;
    readonly schema?: SettingsFieldNode;
    readonly value?: unknown;
    readonly user?: unknown;
    readonly base?: unknown;
}
/** The slice of the Host `settings` service this provider reads and writes. */
export interface SettingsServiceFace {
    describe(options?: {
        redactSecrets?: boolean;
    }): readonly SettingsDocument[];
    update(ns: string, patch: object, expectedRevision?: number): Promise<void>;
}
/** `settings` configuration. */
export interface SettingsOptions {
    /** Resolve the settings service, or undefined when this composition has none. */
    readonly settings: () => SettingsServiceFace | undefined;
}
/**
 * Build the `settings` provider.
 * @param options - service accessor.
 * @returns the provider to register.
 */
export declare function createSettingsProvider(options: SettingsOptions): Provider;
