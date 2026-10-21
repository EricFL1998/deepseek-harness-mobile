/**
 * Slot registrations read out of a client bundle without running it.
 *
 * Executing a bundle is the exact answer, but it is not a safe answer on a phone Host:
 * a client half's `apply` performs real mount work, and running it inside the bridge VM
 * has taken the Host process down. Reading the compiled source instead costs a file read
 * and yields the same registration metadata — the slot name, the entry id, its order —
 * because those values are object literals passed to the registry.
 *
 * A label is written one of two ways: a literal (`label: "Subscriptions"`) or a thunk
 * that reads the bundle's own copy table (`label: () => t("nav")`). The thunk's key is
 * captured here and resolved through the bundle's inline dictionaries by the caller,
 * which knows the language the request asked for.
 *
 * What static reading cannot recover is anything computed at runtime: a label built
 * from a variable, a component, or a registration produced by a loop. Those are
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
    /** Label text written as a literal in the registration spec. */
    readonly label?: string;
    /**
     * Dictionary key a label thunk resolves (`label: () => t("nav")` → `nav`).
     *
     * The key is resolved through the bundle's own inline dictionaries, with the
     * locale preference of the request, so the label arrives in the client's language.
     */
    readonly labelKey?: string;
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
/**
 * A display label for one registration, read from its package's own locale files.
 *
 * The browser resolves labels through a locale dictionary at render time; static
 * reading has no render, so the dictionaries themselves are the source. Candidates are
 * tried in the order the page would use: the registration's own id as a key, then the
 * package's `meta.title`. A registration with no dictionary entry keeps no label —
 * showing an id is honest, inventing a name is not.
 * @param locales parsed locale dictionaries, most preferred first.
 * @param id registration id, when it has one.
 * @returns the resolved label, or undefined.
 */
export declare function resolveStaticLabel(locales: readonly Readonly<Record<string, unknown>>[], id: string | undefined): string | undefined;
/**
 * Resolve one registration's display label for a locale preference.
 *
 * Three sources, in the order the browser would use them: the text the spec wrote
 * as a literal; the key its thunk resolves through the bundle's own inline copy
 * tables (the shape the real subscription plugin ships); then the package's
 * `locale/<tag>.json` files keyed by the registration id. A registration none of
 * them names keeps no label, so the caller shows the id rather than a name nobody
 * wrote.
 *
 * @param registration the extracted registration.
 * @param dictionaries the bundle's inline locale tables, when it carries any.
 * @param fileLocales parsed package locale dictionaries, most preferred first.
 * @param localeTags preferred language tags, most preferred first.
 * @returns the resolved label, or undefined.
 */
export declare function resolveStaticLabelFor(registration: StaticRegistration, dictionaries: ReadonlyMap<string, ReadonlyMap<string, string>> | undefined, fileLocales: readonly Readonly<Record<string, unknown>>[], localeTags: readonly string[]): string | undefined;
