# Changelog

This file records changes to the DSH Mobile Android app. The bridge plugin and rootfs image have their own release lines; see [RELEASING.md](RELEASING.md) for the release order.

## 1.0.4 — 2026-10-23

### Fixed

- **The bash tool runs inside the Host again**: removed the `android_exec_shell` device-bridge branch; DSH's own `ctx.shell` now executes inside the Host proot. This restores `run_in_background`, sandbox policy, `shellEnv`, cancellation and jobs semantics instead of starting a second App-side proot per command.
- **proot flags aligned with DSHA**: Host startup now passes `-L`, `--kill-on-exit`, `-0`, and binds `/system`, `/apex`, `/proc/self/fd`, `/dev/urandom → /dev/random`; missing bind sources are skipped.
- **Reject rootfs images built for another DSH engine**: the manifest source ignores remote/cached manifests whose DSH segment differs from the APK's bundled manifest; Host startup refuses an installed incompatible rootfs; `decideRootfsUpdateKind` treats an engine mismatch as mandatory even when the installed rootfs segment is newer.
- `rootfs-v1.0.12` (DSH 0.2.1-rc.3) is marked as a prerelease, so `releases/latest` points back to the compatible `rootfs-v1.0.11` (DSH 0.2.1-rc.2).

### Verified

- Emulator: bash runs inside the Host (Debian 12 / Android 16 kernel / uid=0); background jobs and the fs-search device bridge work; the rc.3 runtime update is blocked by the guard.
- Added rootfs version-compatibility tests and proot command tests; fixed the stale `--link2symlink` assertion in the old test.

## 1.0.3 — 2026-10-23

### Security

- **Local authentication for the device bridge**: BridgeServer rotates a 256-bit token on every Host start and writes it to the guest's `/root/.dsh/.bridge_token`; every JSON-RPC request must carry `auth`, otherwise it gets `-32001` and the connection is closed (fail closed).
- Fixed BridgeServer binding every interface by default; it now binds `127.0.0.1` explicitly.
- bash / fs-search device-bridge requests carry the token (patch revision 38).

### Release

- APK tags now verify the bridge / rootfs releases. A missing bridge release can be dispatched automatically with `BRIDGE_RELEASE_TOKEN`, otherwise the build fails.
- Bundled rootfs manifest refreshed to `rootfs-v1.0.11` / bridge 2.0.7.
- Added [RELEASING.md](RELEASING.md) documenting the bridge → rootfs → APK release chain.

### Docs

- Added a DSHA comparison report (device control, workspace, bash, proot).

## 1.0.2 — 2026-10-23

### Fixed

- Repair a malformed profile `cordis.patch.yml` before Host startup, so the Host no longer fails on a Loader configuration error.

### Docs / site

- README product-page banner and links; product page header layout, language switch and CTA copy fixes.

## 1.0.1 — 2026-10-23

### Fixed

- Plugin-store detail page: duplicated back link, action-button placement, and English title wrapping.

### Build / release

- Release APKs are signed with the local keystore; CI builds and publishes a signed APK when signing secrets are present.

### Docs / site

- Added the product page and English page, deployed to GitHub Pages; README link and banner updates.

## 1.0.0 — 2026-10-23

### First stable release

- **Full DSH running locally**: shipped Debian + proot + Node inside the APK, booting a real DSH Host; sessions, agents, tools, approvals, schedules and Agent Team all run through the real Host.
- **Native Compose UI**: chat, sessions, files, approvals, settings, right sidebar and plugin store are rendered natively.
- **Plugin system**: Host plugins and the `bridge/v1` protocol are preserved; official client slots map to native UI, complex pages use the web island; the Native UI SDK and companion native plugin APKs are supported.
- **Local workspace**: defaults to the shared `Documents/DSH-Workspace`, visible to system file managers; includes rootfs install and update flows.
- **Multi-host and remote**: entries, settings and credential isolation for local and remote hosts (remote connectivity is still being completed).
