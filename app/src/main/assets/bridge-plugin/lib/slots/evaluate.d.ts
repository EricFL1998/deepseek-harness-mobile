/**
 * Evaluate official client bundles in an isolated Node VM and capture what they
 * register.
 *
 * A client bundle is a factory registration: it calls
 * `window.__ModuleLoader__.load({ id, factory })`, and materializing the factory
 * yields `{ name, inject, apply }`. Calling `apply` with a stub Cordis context
 * makes the bundle declare its slots, labels, and components as ordinary calls.
 * The bridge records those calls instead of rendering anything.
 *
 * The VM grants no `document`, no timers, no network, and no dynamic import.
 * A bundle that needs one of them fails with a reported error rather than
 * reaching the Host.
 */
import { isStubElement } from './react-stub.ts';
/** One captured slot registration, before the wire projection. */
export interface CapturedRegistration {
    readonly slot: string;
    readonly id?: string;
    readonly key?: string;
    readonly order?: number;
    readonly label?: string;
    readonly priority?: number;
    readonly locale?: string;
    readonly component: unknown;
    /** Component identity assigned at capture time. */
    readonly componentId: string;
    readonly kind: 'component' | 'factory';
}
/** A component the evaluator captured and can render. */
export interface CapturedComponent {
    readonly componentId: string;
    readonly component: unknown;
    readonly slot: string;
    readonly entryId: string;
}
/** Result of evaluating one bundle. */
export interface EvaluatedBundle {
    readonly entryId: string;
    readonly registrations: readonly CapturedRegistration[];
    /** Every slot name the bundle touched through `ctx.slots.inject`. */
    readonly injectedSlots: readonly string[];
    /**
     * Module specifiers the bundle requested that this VM could not supply.
     * A bundle with a non-empty list registered its slots but its components are
     * not renderable, so `render-tree` reports that instead of a fake tree.
     */
    readonly missingExternals: readonly string[];
    /** Non-fatal notes gathered while evaluating. */
    readonly warnings: readonly string[];
}
/** Failure raised when a bundle cannot be evaluated. */
export declare class BundleEvaluationError extends Error {
    /** The bundle that failed. */
    readonly entryId: string;
    /**
     * @param entryId - the bundle that failed.
     * @param message - what went wrong.
     */
    constructor(entryId: string, message: string);
}
/**
 * Evaluate one client bundle and capture its registrations.
 * @param entryId - the bundle's entry id (package name).
 * @param source - the bundle's JavaScript text.
 * @returns the captured registrations and slots.
 * @throws {BundleEvaluationError} when the bundle cannot register a factory or
 * when `apply` throws.
 */
export declare function evaluateBundleSource(entryId: string, source: string): EvaluatedBundle;
/**
 * Read and evaluate one client bundle file.
 * @param entryId - the bundle's entry id.
 * @param path - absolute bundle path.
 * @returns the captured registrations.
 * @throws {BundleEvaluationError} when the file is too large or unreadable.
 */
export declare function evaluateBundleFile(entryId: string, path: string): Promise<EvaluatedBundle>;
/** Digest of one bundle's source, recorded so a client can tell a redeploy from a rename. */
export declare function bundleDigest(source: string): string;
/** Re-export so callers can type-check a rendered value without importing the stub module. */
export { isStubElement };
