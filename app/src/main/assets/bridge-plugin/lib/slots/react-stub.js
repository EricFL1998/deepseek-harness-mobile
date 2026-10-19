/**
 * The React-shaped stub every evaluated client bundle runs against.
 *
 * The bridge does not want a DOM, a scheduler, or a live component tree: it
 * wants the element tree a component *produces*. This stub therefore provides
 * exactly the surface a client component may call, with hooks that resolve once
 * and no effects, refs to real nodes, or scheduling of any kind. Anything that
 * would reach outside the VM (timers, network, `document`) is absent, so a
 * bundle that needs it fails loudly instead of doing something invisible.
 */
/** Whether a value is an element the stub produced. */
export function isStubElement(value) {
    return typeof value === 'object' && value !== null && value.$$dshElement === true;
}
/** Whether a value is the stub's fragment marker. */
export function isStubFragment(value) {
    return typeof value === 'object' && value !== null && value.$$dshFragment === true;
}
/**
 * Build one stub namespace.
 *
 * `useId` counts per namespace so a single render pass numbers ids
 * deterministically; every hook resolves its value once, because the bridge
 * never re-renders.
 * @returns the React namespace to hand to evaluated bundles.
 */
export function createReactStub() {
    let idCounter = 0;
    const flattenChildren = (children) => {
        const out = [];
        for (const child of children) {
            if (Array.isArray(child)) {
                out.push(...flattenChildren(child));
                continue;
            }
            if (child === null || child === undefined || typeof child === 'boolean')
                continue;
            out.push(child);
        }
        return out;
    };
    const createElement = (type, props, ...children) => {
        const source = props ?? {};
        const { key, ref, ...rest } = source;
        const merged = flattenChildren(children);
        const resolvedProps = { ...rest };
        if (merged.length > 0) {
            resolvedProps.children = merged.length === 1 ? merged[0] : merged;
        }
        return {
            $$dshElement: true,
            type,
            key: key === undefined || key === null ? null : String(key),
            ref: ref ?? null,
            props: resolvedProps,
        };
    };
    const fragment = { $$dshFragment: true };
    const stub = {
        createElement,
        cloneElement: (element, props, ...children) => createElement(element.type, { ...element.props, ...(props ?? {}) }, ...(children.length > 0 ? children : [element.props.children])),
        isValidElement: isStubElement,
        Fragment: fragment,
        StrictMode: fragment,
        useState: (initial) => {
            const value = typeof initial === 'function' ? initial() : initial;
            // A single render: the setter records nothing, because there is no second
            // pass for it to reach.
            return [value, () => { }];
        },
        useReducer: (reducer, initial) => {
            void reducer;
            return [initial, () => { }];
        },
        useMemo: (factory) => factory(),
        useCallback: (callback) => callback,
        useRef: (initial) => ({ current: initial }),
        useId: () => {
            idCounter += 1;
            return `dsh-stub-${idCounter}`;
        },
        useEffect: () => { },
        useLayoutEffect: () => { },
        useInsertionEffect: () => { },
        useContext: () => undefined,
        useSyncExternalStore: (_subscribe, getSnapshot) => getSnapshot(),
        useTransition: () => [false, (callback) => { callback(); }],
        useDeferredValue: (value) => value,
        memo: (component) => component,
        forwardRef: (render) => render,
        Children: {
            toArray: children => flattenChildren([children]),
            map: (children, fn) => flattenChildren([children]).map((child, index) => fn(child, index)),
            count: children => flattenChildren([children]).length,
            only: children => {
                const items = flattenChildren([children]);
                if (items.length !== 1) {
                    throw new Error(`React.Children.only expected exactly one child, received ${items.length}`);
                }
                return items[0];
            },
        },
    };
    return stub;
}
/**
 * The `react-dom` face: nothing renders, so nothing mounts.
 * @returns the namespace `require('react-dom')` receives.
 */
export function createReactDomStub() {
    return {
        createPortal: (children) => children,
        flushSync: (callback) => { callback(); },
        findDOMNode: () => null,
        render: () => undefined,
        unmountComponentAtNode: () => false,
        version: '0.0.0-dsh-bridge-stub',
    };
}
