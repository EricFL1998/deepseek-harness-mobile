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
/** One element the stub produced. */
export interface StubElement {
    readonly $$dshElement: true;
    readonly type: unknown;
    readonly key: string | null;
    readonly ref: unknown;
    readonly props: Record<string, unknown>;
}
/** A fragment marker, so a tree can hold several sibling children. */
export interface StubFragment {
    readonly $$dshFragment: true;
}
/** Whether a value is an element the stub produced. */
export declare function isStubElement(value: unknown): value is StubElement;
/** Whether a value is the stub's fragment marker. */
export declare function isStubFragment(value: unknown): value is StubFragment;
/** The stub's React namespace, as `require('react')` returns it. */
export interface ReactStub {
    createElement(type: unknown, props?: Record<string, unknown> | null, ...children: unknown[]): StubElement;
    cloneElement(element: StubElement, props?: Record<string, unknown> | null, ...children: unknown[]): StubElement;
    isValidElement(value: unknown): boolean;
    Fragment: StubFragment;
    StrictMode: StubFragment;
    useState<T>(initial: T | (() => T)): [T, (next: T | ((prev: T) => T)) => void];
    useReducer<T>(reducer: (state: T, action: unknown) => T, initial: T): [T, (action: unknown) => void];
    useMemo<T>(factory: () => T): T;
    useCallback<T>(callback: T): T;
    useRef<T>(initial: T): {
        current: T;
    };
    useId(): string;
    useEffect(): void;
    useLayoutEffect(): void;
    useInsertionEffect(): void;
    useContext(): undefined;
    useSyncExternalStore<T>(_subscribe: unknown, getSnapshot: () => T): T;
    useTransition(): [boolean, (callback: () => void) => void];
    useDeferredValue<T>(value: T): T;
    memo<T>(component: T): T;
    forwardRef<T>(render: T): T;
    Children: {
        toArray(children: unknown): unknown[];
        map(children: unknown, fn: (child: unknown, index: number) => unknown): unknown[];
        count(children: unknown): number;
        only(children: unknown): unknown;
    };
}
/**
 * Build one stub namespace.
 *
 * `useId` counts per namespace so a single render pass numbers ids
 * deterministically; every hook resolves its value once, because the bridge
 * never re-renders.
 * @returns the React namespace to hand to evaluated bundles.
 */
export declare function createReactStub(): ReactStub;
/**
 * The `react-dom` face: nothing renders, so nothing mounts.
 * @returns the namespace `require('react-dom')` receives.
 */
export declare function createReactDomStub(): Record<string, unknown>;
