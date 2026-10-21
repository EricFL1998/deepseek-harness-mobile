/**
 * Provider registration contracts and the generic dispatch registry.
 *
 * A provider is one feature behind the single `/query` route: adding a
 * compatibility feature adds a provider and leaves the wire format alone.
 */
import type { BridgeErrorCode } from './types.ts';
/** Collects non-fatal notes a provider wants reported beside its data. */
export interface WarningSink {
    /**
     * Record one non-fatal note.
     * @param message - the note; it reaches the client's `warnings` array verbatim.
     */
    warn(message: string): void;
}
/** One provider operation handler. */
export type ProviderHandler = (args: Readonly<Record<string, unknown>>, warnings: WarningSink) => unknown | Promise<unknown>;
/** What a provider declares and answers. */
export interface Provider {
    /** Stable id clients switch on. */
    readonly id: string;
    /** Schema version; bump when a returned field changes meaning or is removed. */
    readonly version: number;
    /** Operation name → handler. The key set is the advertised capability list. */
    readonly ops: Readonly<Record<string, ProviderHandler>>;
    /** True when no handler mutates Host state. */
    readonly readOnly?: true;
    /** Authorization model; only the `rpc` provider omits this. */
    readonly authorization?: 'host-session';
}
/** A provider failure carrying a stable code and an HTTP status. */
export declare class BridgeFailure extends Error {
    /** Stable failure code reported on the wire. */
    readonly code: BridgeErrorCode;
    /** HTTP status the carrier answers with. */
    readonly status: number;
    /** Optional structured detail; shape depends on the code. */
    readonly details: unknown;
    /**
     * @param code - stable failure code reported on the wire.
     * @param message - diagnostic text; clients do not parse it.
     * @param status - HTTP status the carrier answers with.
     * @param details - optional structured detail.
     */
    constructor(code: BridgeErrorCode, message: string, status?: number, details?: unknown);
}
/**
 * Narrow a wire `args` object to one string field.
 * @param args - the request arguments.
 * @param field - required field name.
 * @returns the string value.
 * @throws {BridgeFailure} `args/invalid` when the field is missing or not a string.
 */
export declare function requiredString(args: Readonly<Record<string, unknown>>, field: string): string;
/**
 * Read an optional string field.
 * @param args - the request arguments.
 * @param field - optional field name.
 * @returns the string value, or undefined when absent.
 * @throws {BridgeFailure} `args/invalid` when present and not a string.
 */
export declare function optionalString(args: Readonly<Record<string, unknown>>, field: string): string | undefined;
/**
 * Read an optional BCP-47 locale tag.
 *
 * A locale rides beside the data it localizes rather than replacing the Host's
 * configured `localeTags`, so one client asking for `zh-Hans-CN` and another for
 * `en` receive their own language from the same Host. Underscores are accepted as
 * separators because native clients sometimes send POSIX-style tags.
 *
 * @param args - the request arguments.
 * @param field - optional field name.
 * @returns the normalized tag, or undefined when absent.
 * @throws {BridgeFailure} `args/invalid` when present and not a language tag.
 */
export declare function optionalLocale(args: Readonly<Record<string, unknown>>, field?: string): string | undefined;
/**
 * Language tags one request resolves text with, most preferred first.
 *
 * The order is the one a client's own language negotiation applies: the tag it
 * asked for, that tag's base language (`zh-Hans-CN` → `zh`), then the tags the
 * Host is configured with. Duplicates are dropped case-insensitively, so a
 * configured `zh` does not appear twice for a `zh-CN` request.
 *
 * @param requested - the request's own `locale`, when it sent one.
 * @param configured - the Host's configured tags, in preference order.
 * @returns the tags to try, most preferred first.
 */
export declare function preferredLocaleTags(requested: string | undefined, configured: readonly string[]): string[];
/**
 * Read an optional positive integer field, clamped to a ceiling.
 * @param args - the request arguments.
 * @param field - optional field name.
 * @param fallback - value used when the field is absent.
 * @param maximum - inclusive ceiling applied to an accepted value.
 * @returns the resolved integer.
 * @throws {BridgeFailure} `args/invalid` when present and not a positive integer.
 */
export declare function optionalPositiveInt(args: Readonly<Record<string, unknown>>, field: string, fallback: number, maximum: number): number;
/**
 * Read an optional plain-object field.
 * @param args - the request arguments.
 * @param field - optional field name.
 * @returns the object, or undefined when absent.
 * @throws {BridgeFailure} `args/invalid` when present and not a plain object.
 */
export declare function optionalObject(args: Readonly<Record<string, unknown>>, field: string): Readonly<Record<string, unknown>> | undefined;
/** A resolved provider query, before the carrier serializes it. */
export interface ProviderAnswer {
    readonly provider: string;
    readonly version: number;
    readonly data: unknown;
    readonly warnings: readonly string[];
}
/**
 * The provider table. Registration order is the manifest order, so clients see
 * a stable catalog for a stable composition.
 */
export declare class ProviderRegistry {
    private readonly providers;
    /**
     * Add one provider.
     * @param provider - the provider to add.
     * @throws {Error} when the id is already registered; two features must not
     * share an id, because clients switch on it.
     */
    add(provider: Provider): void;
    /** Registered providers in registration order. */
    list(): readonly Provider[];
    /**
     * Look one provider up by id.
     * @param id - provider id from the wire.
     * @returns the provider, or undefined when the composition does not include it.
     */
    get(id: string): Provider | undefined;
    /**
     * Run one provider operation.
     * @param providerId - provider id from the wire.
     * @param op - operation name from the wire.
     * @param args - validated arguments.
     * @returns the answer to serialize.
     * @throws {BridgeFailure} `provider/unknown`, `op/unknown`, or whatever the handler raises.
     */
    query(providerId: string, op: string, args: Readonly<Record<string, unknown>>): Promise<ProviderAnswer>;
}
