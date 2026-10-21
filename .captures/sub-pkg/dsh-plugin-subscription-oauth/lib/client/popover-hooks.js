/**
 * The two popover behaviors the composer chip borrows from the shell.
 *
 * `useAnchoredPosition` and `useDismissOnOutsidePointer` are re-exported from
 * `@deepseek-ai/dsh-client-ui-primitives` so the import lives in exactly one
 * module. The package is listed in the bundle's externals (the shell seeds it
 * into the module table), but its published artifact pulls a full markdown and
 * icon toolchain as bare specifiers, which a bare Node test run cannot
 * resolve. Keeping the import in this leaf — and never in a module the test
 * suite evaluates — lets the pure helpers load without that toolchain while
 * the browser bundle inlines the hooks as usual.
 *
 * @module dsh-plugin-subscription-oauth/client/popover-hooks
 */
export { useAnchoredPosition, useDismissOnOutsidePointer } from '@deepseek-ai/dsh-client-ui-primitives';
