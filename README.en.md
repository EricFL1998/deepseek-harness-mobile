<p align="center">
  <a href="https://ericfl1998.github.io/deepseek-harness-mobile/en.html">
    <img src="https://ericfl1998.github.io/deepseek-harness-mobile/assets/banner.svg" alt="DSH Mobile — Full DeepSeek Harness" width="900">
  </a>
</p>

<h3 align="center">
  <a href="https://ericfl1998.github.io/deepseek-harness-mobile/en.html">🌐 Product page</a>
  &nbsp;·&nbsp;
  <a href="https://ericfl1998.github.io/deepseek-harness-mobile/">中文页面</a>
</h3>

# DSH Mobile — Full DeepSeek Harness on Android

DSH Mobile is not a thin remote client and not a chat-only shell. It puts the **full DeepSeek Harness** on an Android phone: the real DSH Host, agent loop, tool runtime and plugin system all run locally on the device. What the desktop app can do, this app can do too.

## Highlights

- **Full DSH Host runs on the phone**: a bundled Debian (glibc) userland with proot and a Node runtime starts the upstream DSH directly. Sessions, agents, tool calls, approvals, schedules, Agent Team and plugin loading are handled by the real Host — not a re-implemented “mobile protocol”.
- **Plugin ecosystem compatibility**: Host plugins, bundles, `client-slots` registrations and the `bridge/v1` protocol are preserved. DOM-heavy UI runs in a Web island; core UI is rendered natively with Compose, so plugins do not need a mobile fork.
- **Companion native UI**: APK-delivered companion plugins can build **real native screens** with the **DSH Native UI SDK** (Kotlin + Compose) and register into chat, settings, sidebar and composer slots.
- **Real workspace**: the default workspace root is `/storage/emulated/0/Documents/DSH-Workspace` in shared phone storage, mounted as `/workspace` inside the container. Files are visible in the system file manager; no export step.
- **Multi-host**: local Host and remote private gateway Hosts coexist and can be switched at any time.

## Features

| Area | What you get |
| --- | --- |
| Chat & agents | Full DSH agent sessions, streaming messages, collapsible reasoning, tool calls and approvals; errors shown verbatim |
| Work detail | Results only / key details / full trace, matching the desktop `transcriptView` semantics |
| Models & effort | Model catalog, provider credentials and reasoning levels all come from the Host `modelCatalog` (with a local cache) |
| Files & deliverables | Native file browser, document preview, deliverables, reading and writing the phone workspace directly |
| Right drawer | Official dock styling: files, terminal, browser, plan, deliverables; the guide capsules and plugin pages are extensible |
| Automation & teams | Scheduled tasks, Agent Team task board, terminal and browser entry points |
| Plugins | Install / enable / disable / uninstall, plugin details, Web island, and discovery/loading of native plugin APKs |
| Account & onboarding | Official welcome page (sign in or add an API key), first-run onboarding, model sign-in state and credential management |
| Multi-host | Local Host plus remote Host pairing, switching and per-host credentials |

## Plugins and companion native UI

Two layers of plugin support:

1. **Host plugins (the same ones the desktop runs)**  
   Plugins keep running inside the real DSH Host and register UI through official slots. DSH Mobile maps core slots to native Compose components; Web-heavy UI runs in a Web island, so plugin code needs no fork.

2. **Companion native UI plugins (Native UI SDK)**  
   Native screens shipped as APKs alongside a plugin, written with the **DSH Native UI SDK**:
   - Kotlin + Jetpack Compose with DSH design tokens and controls;
   - registers into `RIGHT_PANEL`, `RIGHT_PANEL_GUIDE_ENTRY`, `SETTINGS_SHEET`, `COMPOSER_DOCK` and other `NativeSlot` seats;
   - talks to the Host for RPC, theme and model data through the SDK host API.

   SDK source, integration guide and samples:  
   **https://github.com/EricFL1998/dsh-mobile-plugin-sdk**


## Plugin store (user view)

Open the drawer → **Plugins** to reach the store. It manages the whole plugin ecosystem much
like the desktop app:

- **Browse & install**: official and third-party plugins are listed with details; one tap
  installs through the local Host — no computer or terminal needed.
- **Enable / disable**: every plugin and component has a switch; changes apply immediately,
  and the few that need a Host restart say so explicitly.
- **Update & uninstall**: version, source and contained components are shown on the detail
  page; plugins can be removed independently.
- **Install only what you need**: core agent works without extra plugins; installed plugins
  appear automatically in settings, the sidebar and the composer where they belong.
- **Native plugins are plugins too**: companion APKs with native UI are discovered and loaded
  by the app like any other plugin.

The store handles discovery and installation; plugin code itself runs inside the on-device
Host. That is what separates DSH Mobile from a plain chat client.

## Bridge and rootfs (briefly)

- **Bridge**: a compatibility/device-capability plugin inside the Host. It maps official
  client slot registrations to native mobile UI and exposes device capabilities (files,
  notifications, clipboard, scheduling) to the Host. It ships with the image; no setup.
- **Rootfs**: a Debian + Node + DSH runtime image on the phone, prepared on first launch.
  Its version has three segments (`rootfs-dsh…-bridge…`): a DSH change requires reinstalling
  the image, while a rootfs-only change is an optional update in Settings.

## How the app works

```
Launch app
  └─ Check/install rootfs (Debian + Node + DSH)
       └─ Start the real DSH Host inside the proot userland (prefer the bundled Host entry)
            ├─ Host serves the loopback Web GUI and Remote RPC
            ├─ Bridge registers UI slots / exposes device capabilities
            └─ App reads sessions, models, plugins and files via Remote + bridge
                 ├─ native Compose renders chat, settings and the sidebar
                 ├─ plugin web UI runs in the Web island
                 └─ companion plugin APKs render native UI through the Native UI SDK
```

The data flow is therefore: **the real on-device Host runs agents and tools; the app only
presents and interacts**. Sessions, files, models and plugins stay on the device. Remote Host
mode simply routes the same chain to a gateway you own.

## Project layout

```
app/                 entry point, navigation, settings, right drawer and other native UI
core/                models, protocol, bridge, Room cache, design system
feature/             chat, sessions, files, approvals, settings, runtime
plugin/              Web island, Surface adapter, native plugin host (NativeSlot / Native UI SDK)
runtime/             local proot Host lifecycle and bridge device capabilities
tooling/             protocol codegen, contract replay, frontend patcher
```

## Build

```bash
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 21 and Android SDK 35. The Native UI SDK is consumed through a Gradle composite build (`includeBuild`) from the sibling SDK repo; override with `-PdshSdkRepo=<path>`.

```bash
./gradlew test
```

## Changelog

See [CHANGELOG.en.md](CHANGELOG.en.md) for version changes, and [RELEASING.md](RELEASING.md) for the release order and version constraints.

## Related repositories

- This app: https://github.com/EricFL1998/deepseek-harness-mobile
- Companion Native UI SDK: https://github.com/EricFL1998/dsh-mobile-plugin-sdk
- Upstream DeepSeek Harness: https://github.com/deepseek-ai/dsh
