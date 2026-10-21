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
/** Keywords that describe the schema document rather than the values it accepts. */
const DOCUMENT_KEYS = new Set(['$schema', '$id', '$defs', 'definitions']);
/** Root keywords a flattened schema keeps. */
const ROOT_KEYS = ['type', 'description', 'required', 'additionalProperties', 'minProperties', 'maxProperties'];
/**
 * Largest expanded schema the endpoint is asked to take, in bytes.
 *
 * Measured rather than assumed: 80,081 B was accepted and 124,389 B was refused
 * with `At path 'root': schema exceeds maximum allowed size`. This sits below
 * both so a schema this plugin sends has room to spare.
 */
const SIZE_BUDGET = 64 * 1024;
/** True for a plain object usable as a schema node. */
function object(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
/** Follow one local JSON pointer, or undefined when it does not resolve. */
function pointed(root, ref) {
    if (!ref.startsWith('#/'))
        return undefined;
    let node = root;
    for (const segment of ref.slice(2).split('/')) {
        const key = segment.replaceAll('~1', '/').replaceAll('~0', '~');
        if (Array.isArray(node))
            node = node[Number(key)];
        else if (object(node) && Object.hasOwn(node, key))
            node = node[key];
        else
            return undefined;
    }
    return node;
}
/**
 * Replace every reference reachable from one node with what it names.
 *
 * @param node - the node to rewrite.
 * @param root - the tool's root schema, which local pointers resolve against.
 * @param refs - pointers already being resolved on the path to this node.
 * @returns the rewritten node.
 */
function inline(node, root, refs) {
    if (Array.isArray(node))
        return node.map((item) => inline(item, root, refs));
    if (!object(node))
        return node;
    const { $ref, ...siblings } = node;
    if (typeof $ref === 'string') {
        // A repeated pointer, or one that names nothing local, cannot be inlined:
        // the node's own keywords are all that is left.
        const target = refs.has($ref) ? undefined : pointed(root, $ref);
        return object(target)
            ? inline({ ...target, ...siblings }, root, new Set([...refs, $ref]))
            : inline(siblings, root, refs);
    }
    return Object.fromEntries(Object.entries(node)
        .filter(([key]) => !DOCUMENT_KEYS.has(key))
        .map(([key, value]) => [key, inline(value, root, refs)]));
}
/**
 * Keep only what a model needs to call the tool: the root's own keywords, and
 * each argument's name, type, value choices and — while the budget allows —
 * its description.
 *
 * @param schema - an already inlined schema.
 * @param prose - whether descriptions are worth their bytes.
 * @returns the flattened schema.
 */
function flatten(schema, prose) {
    const out = {};
    for (const key of ROOT_KEYS) {
        if (Object.hasOwn(schema, key) && (prose || key !== 'description'))
            out[key] = schema[key];
    }
    if (!object(schema.properties))
        return out;
    out.properties = Object.fromEntries(Object.entries(schema.properties).map(([name, property]) => {
        const node = object(property) ? property : {};
        const reduced = {};
        if (typeof node.type === 'string')
            reduced.type = node.type;
        if (Array.isArray(node.enum))
            reduced.enum = node.enum;
        if (prose && typeof node.description === 'string')
            reduced.description = node.description;
        return [name, reduced];
    }));
    return out;
}
/** Bytes the endpoint measures once it has expanded every reference. */
function expandedBytes(schema) {
    return Buffer.byteLength(JSON.stringify(schema));
}
/**
 * Prepare one tool's parameters for Moonshot.
 *
 * @param parameters - the tool's declared argument schema.
 * @returns the schema to send and what it had to give up to fit the endpoint.
 */
export function prepareMoonshotTool(parameters) {
    const inlined = inline(parameters, parameters, new Set());
    if (expandedBytes(inlined) <= SIZE_BUDGET)
        return { parameters: inlined, preparedAs: 'as-declared' };
    const documented = flatten(inlined, true);
    if (expandedBytes(documented) <= SIZE_BUDGET)
        return { parameters: documented, preparedAs: 'flattened' };
    const bare = flatten(inlined, false);
    if (expandedBytes(bare) <= SIZE_BUDGET)
        return { parameters: bare, preparedAs: 'flattened' };
    return { parameters: { type: 'object', additionalProperties: true }, preparedAs: 'permissive' };
}
