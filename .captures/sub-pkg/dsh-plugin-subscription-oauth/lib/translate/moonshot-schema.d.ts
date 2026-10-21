/**
 * Tool-schema preparation for the Moonshot wire (Kimi Code).
 *
 * Moonshot validates `tools[].function.parameters` with its own reader, and
 * that reader expands every `$ref` before it measures or checks the schema. The
 * harness forwards each tool's schema exactly as its owner declared it, and
 * every MCP tool REA registers holds its shapes in a `$defs` block that its
 * properties reference, so Kimi refused the whole request with
 * `tools.function.parameters is not a valid moonshot flavored json schema`.
 *
 * Live probes against `api.kimi.com` measured both edges of that reader:
 *
 * - a `$ref` whose target itself holds `$ref`s is refused as
 *   `detected infinite recursion without termination condition` — the failing
 *   tool reported the node `properties.actions` and, once that node was
 *   inlined so its reference stood alone, the deeper `properties.actions.items`.
 *   A one-level reference is accepted; a chain is not.
 * - the expanded schema has a size ceiling between 80,081 B (accepted) and
 *   124,389 B (refused). `compare_web_captures` declares 57,175 B of shared
 *   definitions but expands past 153,000 B, so it was refused even as declared.
 *
 * So this module sends no references at all — every local pointer is replaced
 * by the definition it names, and the document keywords that only existed to
 * hold them (`$defs`, `$schema`, `$id`, `definitions`) are dropped. A pointer
 * that re-enters itself cannot be inlined, and there the referencing node keeps
 * its own keywords instead of recursing. A tool whose expanded schema still
 * exceeds the ceiling is flattened in steps — the arguments with their prose,
 * then the arguments alone, then an unrestricted object — so that one oversized
 * tool costs its own detail rather than the whole request.
 *
 * @module dsh-plugin-subscription-oauth/translate/moonshot-schema
 */
/** How much of a declaration a schema too large to send had to give up. */
export type MoonshotPreparation = 
/** Sent as declared, with its references inlined. */
'as-declared'
/** Only the arguments: names, types, choices, `required`, and prose while it fits. */
 | 'flattened'
/** An unrestricted object, because even the arguments did not fit. */
 | 'permissive';
/** One prepared tool schema together with what had to be done to it. */
export interface MoonshotToolSchema {
    /** The schema to send. */
    parameters: Record<string, unknown>;
    /** What the declaration had to give up to fit. */
    preparedAs: MoonshotPreparation;
}
/**
 * Prepare one tool's parameters for Moonshot.
 *
 * @param parameters - the tool's declared argument schema.
 * @returns the schema to send and what it had to give up to fit the endpoint.
 */
export declare function prepareMoonshotTool(parameters: Record<string, unknown>): MoonshotToolSchema;
