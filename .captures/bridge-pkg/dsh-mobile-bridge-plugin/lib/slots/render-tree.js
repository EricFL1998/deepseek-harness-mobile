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
import { isStubElement, isStubFragment } from "./react-stub.js";
/** Default render ceilings: deep enough for real cards, small enough to answer promptly. */
export const DEFAULT_RENDER_BUDGET = { maxDepth: 24, maxNodes: 4_000, maxBytes: 512 * 1024 };
/** Host tags a native client can turn into widgets or SVG shapes. */
const KNOWN_HOST_TAGS = new Set([
    'a', 'circle', 'clipPath', 'defs', 'div', 'ellipse', 'g', 'image', 'line', 'linearGradient',
    'mask', 'path', 'polygon', 'polyline', 'radialGradient', 'rect', 'span', 'stop', 'svg',
    'text', 'tspan', 'use',
]);
/**
 * Render one captured component into a serialized tree.
 * @param component - the component function, or a stub element already.
 * @param props - props to render it with.
 * @param budget - ceilings for this render.
 * @returns the serialized tree and any warnings.
 */
export function renderComponent(component, props, budget = DEFAULT_RENDER_BUDGET) {
    const warnings = [];
    const counter = { nodes: 0, truncated: false };
    const callComponent = (fn, callProps, depth) => {
        if (typeof fn !== 'function')
            return null;
        if (depth > budget.maxDepth) {
            counter.truncated = true;
            warnings.push(`render stopped at depth ${budget.maxDepth}`);
            return { kind: 'opaque', reason: 'depth-budget' };
        }
        let output;
        try {
            output = fn(callProps);
        }
        catch (error) {
            warnings.push(`component threw: ${error.message}`);
            return { kind: 'opaque', reason: 'component-threw' };
        }
        return serialize(output, depth + 1);
    };
    const serialize = (value, depth) => {
        counter.nodes += 1;
        if (counter.nodes > budget.maxNodes) {
            counter.truncated = true;
            return { kind: 'opaque', reason: 'node-budget' };
        }
        if (value === null || value === undefined || typeof value === 'boolean')
            return null;
        if (typeof value === 'string')
            return { kind: 'text', value };
        if (typeof value === 'number')
            return { kind: 'text', value: String(value) };
        if (Array.isArray(value)) {
            // An array is several siblings; the caller flattens them into its own
            // children list, so a bare array at the root is reported as a fragment.
            const children = [];
            for (const item of value) {
                const child = serialize(item, depth);
                if (child !== null)
                    children.push(child);
            }
            return { kind: 'element', type: 'fragment', key: null, props: {}, children };
        }
        if (isStubFragment(value)) {
            return { kind: 'element', type: 'fragment', key: null, props: {}, children: [] };
        }
        if (isStubElement(value))
            return serializeElement(value, depth);
        if (typeof value === 'object') {
            // An arbitrary object is not renderable; reporting it as opaque keeps the
            // rest of the tree usable.
            return { kind: 'opaque', reason: 'object-child' };
        }
        return { kind: 'opaque', reason: typeof value };
    };
    const serializeElement = (element, depth) => {
        const type = element.type;
        const childrenOf = (props, nextDepth) => {
            const raw = props.children;
            if (raw === undefined)
                return [];
            const list = [];
            for (const item of Array.isArray(raw) ? raw : [raw]) {
                const child = serialize(item, nextDepth);
                if (child !== null)
                    list.push(child);
            }
            return list;
        };
        if (typeof type === 'string') {
            const props = projectProps(element.props, warnings);
            const known = KNOWN_HOST_TAGS.has(type);
            return {
                kind: 'element',
                type: known ? type : `unknown:${type}`,
                key: element.key,
                props,
                children: childrenOf(element.props, depth + 1),
            };
        }
        if (isStubFragment(type)) {
            return { kind: 'element', type: 'fragment', key: element.key, props: {}, children: childrenOf(element.props, depth + 1) };
        }
        if (typeof type === 'function') {
            const name = type.displayName
                ?? type.name
                ?? 'anonymous';
            const inner = callComponent(type, element.props, depth + 1);
            return {
                kind: 'element',
                type: `component:${name}`,
                key: element.key,
                props: projectProps(element.props, warnings),
                children: inner === null ? [] : [inner],
            };
        }
        if (typeof type === 'symbol') {
            // A React builtin (Fragment/StrictMode) reached through a stub namespace
            // the bundle resolved itself; treat it as a transparent group.
            return { kind: 'element', type: 'fragment', key: element.key, props: {}, children: childrenOf(element.props, depth + 1) };
        }
        return { kind: 'opaque', reason: 'element-type' };
    };
    // The root keeps the component's own identity: a client reading the tree can
    // tell which component drew it, and the rendered element becomes its child.
    const root = serialize(typeof component === 'function'
        ? {
            $$dshElement: true,
            type: component,
            key: null,
            ref: null,
            props: props,
        }
        : component, 0);
    if (root !== null) {
        const bytes = Buffer.byteLength(JSON.stringify(root), 'utf8');
        if (bytes > budget.maxBytes) {
            counter.truncated = true;
            warnings.push(`serialized tree is ${bytes} bytes, above the ${budget.maxBytes}-byte budget`);
        }
    }
    return { tree: root, warnings, truncated: counter.truncated };
}
/**
 * Copy element props into JSON, dropping functions, symbols, and cycles.
 * @param props - the element's props.
 * @param warnings - sink for dropped-member notes.
 * @returns a JSON-safe prop map.
 */
function projectProps(props, warnings) {
    const out = {};
    for (const [key, value] of Object.entries(props)) {
        // Children are serialized into their own field; keeping the raw value here
        // would duplicate the subtree in a form a client cannot read.
        if (key === 'children')
            continue;
        switch (typeof value) {
            case 'string':
            case 'number':
            case 'boolean':
                out[key] = value;
                break;
            case 'undefined':
                break;
            case 'function':
                // A callback is not renderable data; a client supplies its own handler.
                out[key] = `<function:${value.name ?? 'anonymous'}>`;
                break;
            case 'object':
                if (value === null) {
                    out[key] = null;
                    break;
                }
                if (isStubElement(value)) {
                    // Children are serialized separately; a nested element in a prop
                    // (a slot prop, an icon prop) keeps only its type name.
                    out[key] = `<element:${describeType(value.type)}>`;
                    break;
                }
                try {
                    out[key] = JSON.parse(JSON.stringify(value));
                }
                catch {
                    warnings.push(`prop ${key} is not JSON-serializable and was dropped`);
                }
                break;
            default:
                warnings.push(`prop ${key} of type ${typeof value} was dropped`);
        }
    }
    return out;
}
/** Readable name of one element type, for prop placeholders. */
function describeType(type) {
    if (typeof type === 'string')
        return type;
    if (typeof type === 'function') {
        return type.displayName
            ?? type.name
            ?? 'anonymous';
    }
    return typeof type;
}
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
export function treeToSvg(tree, size, viewBox = '0 0 24 24') {
    const warnings = [];
    const shapes = [];
    let sawSvgRoot = false;
    const walk = (node) => {
        if (node === null)
            return;
        if (node.kind === 'text')
            return;
        if (node.kind === 'opaque') {
            warnings.push(`dropped an opaque node (${node.reason})`);
            return;
        }
        if (node.type === 'svg') {
            sawSvgRoot = true;
            for (const child of node.children)
                walk(child);
            return;
        }
        if (node.type === 'text' || node.type === 'tspan') {
            warnings.push('text nodes are not converted to SVG paths');
            return;
        }
        if (node.type === 'fragment' || node.type === 'div' || node.type === 'span') {
            for (const child of node.children)
                walk(child);
            return;
        }
        if (node.type.startsWith('component:')) {
            for (const child of node.children)
                walk(child);
            return;
        }
        if (node.type.startsWith('unknown:')) {
            warnings.push(`dropped an unknown element type ${node.type.slice('unknown:'.length)}`);
            return;
        }
        if (SHAPE_TAGS.has(node.type)) {
            shapes.push({ tag: node.type, props: node.props });
            return;
        }
        warnings.push(`dropped ${node.type}, which draws no shape`);
    };
    walk(tree);
    if (shapes.length === 0) {
        return { svg: null, warnings: sawSvgRoot ? warnings : [...warnings, 'the component drew no SVG shapes'] };
    }
    const body = shapes.map(shape => `<${shape.tag}${attributesOf(shape.props)} />`).join('');
    return {
        svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="${viewBox}" fill="currentColor">${body}</svg>`,
        warnings,
    };
}
/** SVG tags that draw something on their own. */
const SHAPE_TAGS = new Set([
    'circle', 'ellipse', 'line', 'path', 'polygon', 'polyline', 'rect',
    'g', 'defs', 'linearGradient', 'radialGradient', 'mask', 'clipPath', 'use', 'stop',
]);
/** Tags whose props are element references, not shapes, and are dropped from output. */
const DROPPED_ATTRIBUTES = new Set(['children', 'key', 'ref', 'className', 'style', 'dangerouslySetInnerHTML']);
/**
 * Render one prop map as SVG attributes.
 * @param props - the element props.
 * @returns the attribute text, including a leading space per attribute.
 */
function attributesOf(props) {
    const parts = [];
    for (const [key, value] of Object.entries(props)) {
        if (DROPPED_ATTRIBUTES.has(key))
            continue;
        if (value === null || value === undefined)
            continue;
        if (typeof value === 'function' || typeof value === 'object')
            continue;
        const name = SVG_ATTRIBUTE_NAMES.get(key) ?? key;
        parts.push(` ${name}="${escapeAttribute(String(value))}"`);
    }
    return parts.join('');
}
/** React prop names that differ from their SVG attribute spelling. */
const SVG_ATTRIBUTE_NAMES = new Map([
    ['fillRule', 'fill-rule'],
    ['clipRule', 'clip-rule'],
    ['strokeWidth', 'stroke-width'],
    ['strokeLinecap', 'stroke-linecap'],
    ['strokeLinejoin', 'stroke-linejoin'],
    ['strokeDasharray', 'stroke-dasharray'],
    ['strokeDashoffset', 'stroke-dashoffset'],
    ['strokeMiterlimit', 'stroke-miterlimit'],
    ['strokeOpacity', 'stroke-opacity'],
    ['fillOpacity', 'fill-opacity'],
    ['stopColor', 'stop-color'],
    ['stopOpacity', 'stop-opacity'],
    ['clipPath', 'clip-path'],
    ['clipPathUnits', 'clipPathUnits'],
    ['gradientUnits', 'gradientUnits'],
    ['gradientTransform', 'gradientTransform'],
    ['patternUnits', 'patternUnits'],
    ['maskUnits', 'maskUnits'],
    ['markerEnd', 'marker-end'],
    ['markerStart', 'marker-start'],
    ['markerMid', 'marker-mid'],
    ['xlinkHref', 'href'],
]);
/** Escape one attribute value for XML. */
function escapeAttribute(value) {
    return value
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
}
