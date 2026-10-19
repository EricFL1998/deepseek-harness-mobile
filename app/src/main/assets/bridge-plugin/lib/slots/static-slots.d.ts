/**
 * Slot registrations read out of a client bundle without running it.
 *
 * Executing a bundle is the exact answer, but it is not a safe answer on a phone Host:
 * a client half's `apply` performs real mount work, and running it inside the bridge VM
 * has taken the Host process down. Reading the compiled source instead costs a file read
 * and yields the same registration metadata — the slot name, the entry id, its order —
 * because those values are object literals passed to the registry.
 *
 * What static reading cannot recover is anything computed at runtime: a label resolved
 * through a locale table, a component, or a registration produced by a loop. Those are
 * reported as absent rather than guessed, and the caller decides what to show.
 */
import type { SlotRegistration } from '../protocol/types.ts';
/** One registrations call found in a bundle. */
export interface StaticRegistration {
    /** Slot name the contribution registers into. */
    readonly slot: string;
    /** Entry identity, when the call carries one. */
    readonly id?: string;
    /** Keyed-slot identity. */
    readonly key?: string;
    /** Display order. */
    readonly order?: number;
    /** Priority, when present. */
    readonly priority?: number;
    /** Locale namespace the label resolves through, when written as a literal. */
    readonly locale?: string;
}
/**
 * Extract every registration a compiled client bundle performs.
 *
 * A bundle from this repository registers through `…slots.register({ … }, Component)`,
 * so each `register(` call followed by an object literal is one registration. The walk
 * is deliberately literal: a registration built dynamically is invisible here, which is
 * reported by the caller's coverage rather than papered over.
 * @param source compiled bundle text.
 * @returns registrations in source order, deduplicated by slot and id.
 */
export declare function extractStaticRegistrations(source: string): StaticRegistration[];
/**
 * Project a static registration into the bridge's registration type.
 * @param entryId package the bundle belongs to.
 * @param registration the extracted registration.
 * @returns the registration as a client sees it.
 */
export declare function toSlotRegistration(entryId: string, registration: StaticRegistration): SlotRegistration;
