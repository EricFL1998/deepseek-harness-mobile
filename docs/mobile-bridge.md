# DSH Mobile Bridge (generic client-compatibility layer)

> **Status: implemented.** The Host plugin lives in its own repository,
> [`deepseek-harness-mobile-bridge-plugin`](https://github.com/EricFL1998/deepseek-harness-mobile-bridge-plugin)
> (`dsh-mobile-bridge-plugin`); this document is the design it implements, and
> [`core/bridge`](../core/bridge) is the Android consumer. Where the shipped
> code settles a question this document left open, the note is marked
> **As built**.
>
> 架构层面的取舍（原生 UI / Web-first 薄壳 / 全 Web 套壳）见
> [移动端 UI 架构选型](mobile-ui-architecture.md)。

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
    { "id": "client-assets", "version": 1, "capabilities": ["boot-graph", "module-source", "locale"], "readOnly": true },
    { "id": "client-slots", "version": 1, "capabilities": ["list", "describe", "render-tree", "icon-svg", "dispatch"], "readOnly": true },
    { "id": "transpile", "version": 1, "capabilities": ["component-tree"], "readOnly": true },
    { "id": "package-inventory", "version": 1, "capabilities": ["bundles", "rows", "meta-icon"], "readOnly": true },
    { "id": "rpc", "version": 1, "capabilities": ["invoke"], "authorization": "host-session" }
  ]
}
```

**As built:** the field is `version`, not `schemaVersion` — one name for one
thing. `dshVersion` is `null` when the Host publishes no release label, and a
`warnings` array lists every service the composition could not bind, so a client
degrades knowingly instead of discovering the hole at call time.

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

**As built:** `op/unknown` carries `details.capabilities` and an unknown
component id carries `details.known`, so a client corrects itself without a
second round trip. The full code table is in the plugin's `docs/protocol.md`.

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

- `list { slot?, locale? }` → registrations with id, label, order, package, locale keys,
  plus `emptySlots` (slots the evaluated bundles touched but filled with
  nothing). A contribution appears **once**: the provider deduplicates by
  `slot|id|package` because the same bundle is reachable through several roots
  (the installation's `node_modules`, the profile's `@deepseek-ai` symlink). The
  first (installation-first) row wins, so the authoritative copy and its label survive.
- `describe { slot, id, locale? }` → full contribution metadata.
- `render-tree { componentId, props? }` → a serialized element tree produced by
  the component function under the React stub. The client converts the tree to
  native widgets or SVG. This is how official artwork/icons are obtained without
  a WebView.
- `icon-svg { componentId, size }` → convenience conversion of an element tree
  to SVG for image slots.

Execution is deterministic and side-effect free: the stub provides
`createElement`, `useId`, `useMemo`, `useState` (single render), and rejects
`window`, `document`, network, timers, and dynamic imports.

**As built:** the stub also carries the rest of the hook surface
(`cloneElement`, `useRef`, `useCallback`, `Children`, `memo`, `forwardRef`,
`useSyncExternalStore`, `useTransition`), each resolving once, because a real
official bundle calls them. The VM additionally rejects `process` and any
module request other than `react` / `react-dom`; a bundle that needs one is
reported as a `warnings` entry and its registrations stay readable, so one
incompatible bundle does not blank the catalog. One evaluation is cached per
entry revision.

## Provider: `transpile` — **removed in bridge 2.0.0**

The static component-tree provider was retired on 2026-10-20. The phone renders
plugin UI through the plugin's **full Web UI** (the real Web client in the
WebView container), so the static projection into native controls was deleted
along with the App's `plugin/mobile-ui` renderer.

What survives, and where it now lives:

- The **inline-locale reading** the slot scanners still need — recovering a
  bundle's own copy tables (`const en = {…}`) and resolving a label thunk
  (`label: () => t('nav')`) — was extracted verbatim into
  `slots/bundle-locale.ts`. `client-slots/list` and `describe` keep resolving
  localized labels through it; nothing else of the transpiler remains.
- `render-tree` and `icon-svg` are untouched: they belong to `client-slots` and
  power the plugin catalogue's official artwork, not the retired component-tree.

**As built (v1.4.0 fix):** a profile's `node_modules` is added to the scan roots
*without* an `existsSync` gate. Inside proot a `stat` on a translated path can
fail where `readdir` plainly reads the directory — the plugin's own
`import.meta.url` proved the path was right while the check said no — and the
gate silently hid every third-party plugin's UI. The scanner already treats an
absent root as "no bundles", so the gate bought nothing and cost correctness.

**As built (2026-10-20):** `list` and `describe` accept an optional `locale`
(BCP-47, e.g. `zh-Hans-CN`). A registration's `label` is usually a thunk
(`label: () => t('nav')`) whose text lives in the bundle's own inline copy
tables (`const en = {…}` / `const zh = {…}`), and many packages ship no
`locale/` directory. The static reader resolves the thunk key through the
bundle's inline dictionaries, preferring `[locale, base(locale), …configured
localeTags]`, so the phone shows “订阅” rather than the registration id
`subscriptions` and follows the client language. Registration rows also carry
`labelKey` (additive; clients ignore unknown fields).

## Provider: `client-slots` additions

- Registrations carry `origin: "static" | "evaluated"` so a client can mark
  statically extracted entries as potentially incomplete.
- `dispatch { componentId, event, payload? }` delivers one native UI event to
  the plugin: the bridge re-broadcasts it on the Host event bus as
  `ui/<componentId>/<event>` and queues a `client/refresh` transient message
  (`events/pending`), so the phone re-pulls the tree once the plugin reacted.

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

**As built** (`core/bridge`):

| Piece | Home | Role |
| --- | --- | --- |
| `BridgeProtocol.kt` | `core:bridge` | Wire types mirroring `docs/protocol.md`; unknown fields ignored. |
| `BridgeClient` | `core:bridge` | Manifest negotiation, one `query`, `BridgeResult` success/failure. Refuses an unknown major version. |
| `BridgeRegistry` | `core:bridge` | Named methods per provider; a missing provider or unadvertised op fails locally without touching the wire. |
| `BridgePluginCatalog` | `core:bridge` | Normalizes `client-slots` + `package-inventory` into one catalog with an explicit `source`. |
| `DshSvgIcon` | `core:designsystem` | Parses the returned `<svg>` and draws it on a Compose Canvas — no WebView. |

The degradation order is the design's, made explicit as an enum:
`CLIENT_SLOTS` → `PACKAGE_INVENTORY` → `NONE`. `NONE` renders the empty state;
it never invents a category. An icon that fails to render keeps its category and
records why in `warnings`, so the page explains its own gaps.

`DshRepository.refreshBridgePluginCatalog()` reads the catalog on Host bind;
`DshApplication.bindLocalHost` shares one CookieJar between the Remote API and
the bridge, so the bridge never mints credentials.

## Roadmap

1. `client-assets` + `client-slots/list` → plugin categories and labels. **Done.**
2. `render-tree`/`icon-svg` → official plugin artwork. **Done.**
3. `package-inventory` → third-party bundles and their `meta.icon`. **Done.**
4. `rpc` → settings forms and any host remote the native shell needs. **Provider
   done; the allowlist ships empty**, so a profile opts methods in explicitly.
5. Reuse the same providers for settings sections, surfaces, and future plugin
   compatibility checks. **Not started** — the mechanism is generic; only the
   `plugins.item` slot is consumed so far.

## Installation

The bridge is a DSH bundle. It is **not published to npm** — the npm name
`dsh-mobile-bridge` belongs to an unrelated package — so the Host installs it
from GitHub:

```sh
dsh plugin --profile web add \
  https://github.com/EricFL1998/deepseek-harness-mobile-bridge-plugin#v1.1.0
```

then add `dsh-mobile-bridge-plugin` to the profile's `dsh.profile.bundles`. It
registers on the Host's existing `webServer` — no port of its own, no second
listener.

Two settings are worth reading before shipping a client:

- `slotEntries` — empty evaluates every composed entry. Naming entries bounds
  the work to the bundles a client actually reads.
- `rpcAllowlist` — empty means the `rpc` provider refuses everything.

Every request passes the Host's own connection fence (`requireHostSession`), so
the bridge never bypasses DSH remote access.

## Update model

Three things version themselves, and the two below the APK are updated **two
different ways**:

| Component | Update type | How it lands |
| --- | --- | --- |
| APK | notice only | GitHub Release download page |
| **bridge plugin** | **required** | in place inside the rootfs — no image download |
| **rootfs image** | **optional** | full image re-download |

The plugin is the client's **contract peer**: Host and App must speak the same
`bridge/v1`. A plugin that lags is missing features, so updating it is
*required* — but it is cheap (a source-tarball install), so it never forces a
300 MB image download. The image is only a carrier: an old one still runs, so a
rebuild is an *optional* update.

That is why `runtimeVersion` carries three segments:

```
1.1.0-dsh0.2.1-rc.2-bridge1.1.0
```

`RootfsVersion.parse` splits it and `assessUpdate` decides:

- the **running plugin** (bridge manifest `pluginVersion`, falling back to the
  profile's on-disk `package.json`) compares **older than** the plugin repo's
  latest release → `PLUGIN_REQUIRED`;
- only the dsh segment moved → `ROOTFS_OPTIONAL`;
- otherwise → `NONE`.

Versions compare by numeric segment, never as text: an installed development
build (`1.24.0`) is *newer* than a release `1.1.0`, so Settings says there is
nothing to do instead of offering a downgrade. An unreadable version is treated
as behind — updating is the safe way to converge — but a missing latest release
(offline) leaves the action disabled rather than failing on the wire.

**Both kinds surface only in Settings → Updates.** Opening the app never
interrupts with a prompt, and no update blocks startup.

### The client installs the plugin from the tag archive

The Host runs inside proot, where Android's DNS resolver is unavailable:
`getent hosts github.com` fails with `EAI_AGAIN`, so `dsh plugin add` (git +
pnpm) cannot reach GitHub, and the Host's pnpm rewrites the profile manifest
anyway. The App process uses the system resolver, so the **App** performs the
in-place install:

1. read the plugin repo's latest release tag (`BridgeReleaseChecker`);
2. download that tag's source archive through the App's HTTP stack
   (`codeload.github.com/.../zip/refs/tags/v<version>`);
3. validate the archive — single root, no path escape, package name/version
   match, `main` and `dsh.bundle.patch` present;
4. extract it over `root/.dsh/profiles/web/node_modules/dsh-mobile-bridge-plugin`
   (staged, then swapped, so a failure leaves the previous version in place);
5. restart the Host, which reloads the web-app patch row that mounts the plugin.

No pnpm, no git inside the rootfs, and no profile-manifest write: the plugin is
mounted by the web-app layer's patch row, which survives profile reconciliation.
The APK's `bridge-plugin.json` supplies only the repository URL; its `version`
field is a build-time baseline, never the update target.

### Releases are version markers

The plugin repository publishes asset-free releases: the tag `v<version>` *is*
the artifact. Publishing is explicit — bump `version`, set `"release": true`,
push; the workflow creates the tag and disarms the flag, so ordinary commits
never publish.

Two consumers read that tag:

- the Android client, via `BridgeReleaseChecker` and the tag archive it
  downloads for the in-place install;
- the rootfs build, to decide which plugin version to bundle and stamp as
  `-bridge<version>`.

Publishing a plugin release also **dispatches a rootfs rebuild**, so a fresh
install gets the newest plugin already bundled. The rootfs image is rebuilt for
exactly two causes — a new DSH release or a new plugin release — and never on a
timer; the build compares the newest published image's `dsh|bridge` pair and
stops when nothing moved.

So a plugin release needs no APK rebuild, and existing devices need no image
download: they take the plugin in place, while new installs get it bundled.

### The plugin is locked in the plugin page

`dsh-mobile-bridge-plugin` renders as `必需` in the Plugins page: no enable
switch, no uninstall, on both the list row and the detail page. Disabling it
costs the page its official categories and artwork and falls back to the Host's
native inventory; removing it breaks the compatibility layer outright. It can
only be upgraded, from Settings → Updates.
