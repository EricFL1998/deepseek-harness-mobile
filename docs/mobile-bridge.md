# DSH Mobile Bridge (generic client-compatibility layer)

The bridge is a Host-side DSH plugin that exposes the **official web client’s own
runtime data** to native clients (Android, desktop shells, test harnesses) as a
stable, versioned, capability-negotiated JSON protocol.

It is deliberately **not** a plugin-catalog endpoint. The plugin page is the
first consumer of a general mechanism that will later serve settings forms,
surfaces, icons, locales, and remote RPCs.

## Principles

1. **No UI interpretation on the Host.** The bridge extracts data; clients render.
2. **Client assets are the source of truth.** Slot registrations, labels, icons,
   and package metadata come from the installed client packages and the boot
   manifest — never from a hand-written mobile table.
3. **Providers, not endpoints.** Every feature is a provider behind one generic
   query API. New compatibility features add a provider; the wire format stays.
4. **Read-only by default.** Mutations go through explicit providers with a
   capability flag and the Host's own authorization; the bridge never bypasses
   DSH remote access.
5. **Versioned.** `bridge/v1` plus per-provider `schemaVersion` and
   `capabilities`. Clients negotiate; unknown providers are ignored.

## Wire contract

Base: `http://127.0.0.1:<port>/dsh-mobile/bridge/v1`

### `GET /manifest`

```json
{
  "bridgeVersion": "1.0.0",
  "dshVersion": "0.2.1-rc.2",
  "providers": [
    { "id": "client-assets", "version": 1, "capabilities": ["boot-graph", "module-source", "locale"] },
    { "id": "client-slots", "version": 1, "capabilities": ["list", "render-tree"], "readOnly": true },
    { "id": "package-inventory", "version": 1, "capabilities": ["bundles", "rows", "meta-icon"] },
    { "id": "rpc", "version": 1, "capabilities": ["invoke"], "authorization": "host-session" }
  ]
}
```

### `POST /query`

```json
{ "provider": "client-slots", "op": "list", "args": { "slot": "plugins.item" } }
```

Response:

```json
{ "ok": true, "provider": "client-slots", "version": 1, "data": [ ... ], "warnings": [] }
```

Errors never partially succeed:

```json
{ "ok": false, "error": { "code": "provider/unavailable", "message": "..." } }
```

## Provider: `client-assets`

Reads the web boot manifest injected into the served shell
(`window.__DSH_BOOT__`), then the installed client entry URLs.

Ops:

- `boot-graph` → entries, batches, revisions, external modules.
- `module-source` → source text/digest of one entry (bounded, read-only).
- `locale` → locale dictionaries for a package namespace.

This provider is the foundation for every later extraction; it does not parse
React.

## Provider: `client-slots`

The generic compatibility primitive. It evaluates official client bundles in an
isolated Node VM with a **React-shaped stub** (no DOM), captures
`ctx.slots.register` calls, and returns registrations as data:

```json
{
  "slot": "plugins.item",
  "id": "agent-loop",
  "label": "Agent 循环",
  "order": 10,
  "package": "@deepseek-ai/dsh-client-ui-settings-agent-loop",
  "render": { "kind": "component", "componentId": "settings-agent-loop" }
}
```

Ops:

- `list { slot? }` → registrations with id, label, order, package, locale keys.
- `describe { slot, id }` → full contribution metadata.
- `render-tree { componentId, props? }` → a serialized element tree produced by
  the component function under the React stub. The client converts the tree to
  native widgets or SVG. This is how official artwork/icons are obtained without
  a WebView.
- `icon-svg { componentId, size }` → convenience conversion of an element tree
  to SVG for image slots.

Execution is deterministic and side-effect free: the stub provides
`createElement`, `useId`, `useMemo`, `useState` (single render), and rejects
`window`, `document`, network, timers, and dynamic imports.

## Provider: `package-inventory`

Reuses Host services, not filesystem guesses:

- `bundles` → `pluginManager/listBundles` (name, official, optional, enabled,
  version, error, rows, `meta.title/description/icon`).
- `rows` → `pluginManager/listPlugins`.
- `meta-icon` → the bundle/row `meta.icon` data URL, passed through.

Third-party plugin icons therefore remain fully backend-driven.

## Provider: `rpc`

A single generic proxy for whitelisted Host remotes:

```json
{ "provider": "rpc", "op": "invoke",
  "args": { "namespace": "settings", "method": "describe", "args": {} } }
```

Authorization is the caller’s existing Host session cookie; the bridge never
mints credentials. A method allowlist and size caps are configured per profile.

## Client side (Android)

- `BridgeClient` implements manifest negotiation and typed `query`.
- A `BridgeRegistry` maps provider ids to Kotlin renderers.
- The plugin page consumes `client-slots/list` for categories and
  `client-slots/icon-svg` for artwork; no metric is hardcoded.
- When a provider is missing, the UI degrades to Host-native inventory
  (`package-inventory`), never to fake data.

## Roadmap

1. `client-assets` + `client-slots/list` → plugin categories and labels.
2. `render-tree`/`icon-svg` → official plugin artwork.
3. `package-inventory` → third-party bundles and their `meta.icon`.
4. `rpc` → settings forms and any host remote the native shell needs.
5. Reuse the same providers for settings sections, surfaces, and future plugin
   compatibility checks.
