/**
 * Serialize a component's output into a JSON element tree, and turn that tree
 * into SVG for image slots.
 *
 * A client component renders against the React stub, so its output is a tree of
 * stub elements whose `type` is either a host tag name (`'div'`, `'path'`) or a
 * captured component function. Serialization resolves component types by
 * calling them, which is safe because the stub's hooks resolve once and never
 * schedule work. Depth and node budgets keep a recursive component from
 * exhausting the Host.
 */
import type { RenderNode } from '../protocol/types.ts';
/** Ceilings applied to one render. */
export interface RenderBudget {
    /** Maximum element depth, so a self-recursive component stops. */
    readonly maxDepth: number;
    /** Maximum serialized nodes. */
    readonly maxNodes: number;
    /** Maximum UTF-8 bytes of the serialized tree. */
    readonly maxBytes: number;
}
/** Default render ceilings: deep enough for real cards, small enough to answer promptly. */
export declare const DEFAULT_RENDER_BUDGET: RenderBudget;
/** Result of serializing one component. */
export interface SerializedRender {
    readonly tree: RenderNode | null;
    readonly warnings: readonly string[];
    /** True when a budget stopped the walk; the tree is then a prefix. */
    readonly truncated: boolean;
}
/**
 * Render one captured component into a serialized tree.
 * @param component - the component function, or a stub element already.
 * @param props - props to render it with.
 * @param budget - ceilings for this render.
 * @returns the serialized tree and any warnings.
 */
export declare function renderComponent(component: unknown, props: Readonly<Record<string, unknown>>, budget?: RenderBudget): SerializedRender;
/**
 * Convert a serialized tree into one standalone SVG document.
 *
 * Only shape-bearing tags contribute; layout wrappers are transparent, because a
 * native client places the artwork itself. Text is not converted: a slot icon
 * that draws letters would need font metrics the bridge does not have, so the
 * caller learns that through `warnings`.
 * @param tree - the serialized tree.
 * @param size - the requested square edge in pixels.
 * @param viewBox - the coordinate space the shapes were drawn in.
 * @returns the SVG document text, or null when the tree drew nothing.
 */
export declare function treeToSvg(tree: RenderNode | null, size: number, viewBox?: string): {
    svg: string | null;
    warnings: string[];
};
