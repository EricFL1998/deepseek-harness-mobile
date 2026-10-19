# Rootfs Upgrade Procedure (AI Playbook)

This document is the canonical procedure for an AI agent to ship a new Android rootfs image
containing a newer `@deepseek-ai/dsh`. Follow every step; do not update DSH in place on a device.

---

## 0. Invariants

- Rootfs build and release is managed by dedicated repo: `https://github.com/EricFL1998/deepseek-harness-mobile-rootfs`
- GitHub Actions automatically builds multi-arch images (x86_64, arm64-v8a) and publishes GitHub Releases.
- Rootfs versioning restarts from `1.0.0`; it is independent of DSH version.
- Canonical installed dir: `files/rootfs`
- Canonical marker: `files/runtime/rootfs-installed.json`
- Canonical cache name: `dsh-rootfs-<runtimeVersion>-<abi>.tar.gz`
- Canonical release tag: `rootfs-v<version>`
- Canonical release assets: `rootfs-x86_64.bin`, `rootfs-arm64-v8a.bin`, `rootfs-manifest.json`
- Canonical release download base: `https://github.com/EricFL1998/deepseek-harness-mobile-rootfs/releases/download/`
- `runtimeVersion` format: `<rootfsVersion>-dsh<dshVersion>`
  - example: `1.0.0-dsh0.2.1-rc.2`
- Never lose user state: the app preserves `root/.dsh` during reinstall.
- Never commit built rootfs archives or `_patch-work/` to git.

---

## 1. Choose versions

```text
DSH_VERSION=0.2.1-rc.2          # published npm version (latest or next)
ROOTFS_VERSION=1.0.1            # increment rootfs version
RUNTIME_VERSION=${ROOTFS_VERSION}-dsh${DSH_VERSION}
RELEASE_TAG=rootfs-v${ROOTFS_VERSION}
```

Check the actual npm version first:

```bash
docker run --rm --platform linux/amd64 dsh-rootfs:web-amd64 \
  sh -lc 'pnpm view @deepseek-ai/dsh version dist-tags --json'
```

If the desired version is a dist-tag (`next`), resolve it to an exact version and pin it.

---

## 2. Build a temporary container per ABI

The existing images already contain node/pnpm/python/git and a web profile. Reinstall DSH on top.

```bash
docker rm -f dsh-rootfs-build-amd64
docker run -d --name dsh-rootfs-build-amd64 --platform linux/amd64 \
  dsh-rootfs:web-amd64 sleep infinity

docker exec dsh-rootfs-build-amd64 sh -lc '
  export PNPM_HOME=/usr/local
  export PATH=$PNPM_HOME/bin:$PATH
  pnpm add -g npm@11 --reporter=silent >/dev/null 2>&1
  /usr/local/bin/npm install -g --no-audit --no-fund \
    @deepseek-ai/dsh@'"$DSH_VERSION"' pnpm
  /opt/dsh/bin/dsh plugin --profile web install
  /usr/local/bin/npm cache clean --force
  rm -rf /root/.npm /usr/local/lib/node_modules/npm \
         /usr/local/lib/node_modules/corepack /usr/local/bin/npm /usr/local/bin/npx
  mkdir -p /opt/dsh/bin
  printf "#!/bin/sh\nexec /usr/local/bin/dsh \"\$@\"\n" > /opt/dsh/bin/dsh
  chmod +x /opt/dsh/bin/dsh
  node /usr/local/lib/node_modules/@deepseek-ai/dsh/lib/bin.js --version
'
```

The last line MUST print the chosen DSH version. If not, stop and investigate.

Repeat for arm64:

```bash
docker rm -f dsh-rootfs-build-arm64
docker run -d --name dsh-rootfs-build-arm64 --platform linux/arm64 \
  dsh-rootfs:web-arm64 sleep infinity
# same exec block; verify uname -m = aarch64 and version output
```

Commit the built images (optional but useful for rollback):

```bash
docker commit dsh-rootfs-build-amd64 dsh-rootfs:web-amd64-${ROOTFS_VERSION}
docker commit dsh-rootfs-build-arm64 dsh-rootfs:web-arm64-${ROOTFS_VERSION}
```

---

## 3. Export and compress

```bash
docker export -o _rootfs-server/rootfs-x86_64-new.tar dsh-rootfs-build-amd64
docker export -o _rootfs-server/rootfs-arm64-new.tar dsh-rootfs-build-arm64
```

Compress with deterministic gzip (Windows: use bundled Python; Linux: gzip -n -6):

```python
import gzip, shutil
src = r"_rootfs-server/rootfs-x86_64-new.tar"
dst = r"_rootfs-server/rootfs-x86_64.bin"
with open(src, "rb") as fi, gzip.GzipFile(dst, "wb", compresslevel=6, mtime=0) as fo:
    shutil.copyfileobj(fi, fo, 1024 * 1024)
```

Do the same for arm64 (`rootfs-arm64-v8a.bin`). Then compute:

```bash
sha256sum _rootfs-server/rootfs-x86_64.bin
sha256sum _rootfs-server/rootfs-arm64-v8a.bin
```

Record `archiveSizeBytes` = size of the x86_64 archive.

---

## 4. Rebase runtime patches (REQUIRED)

The app ships full-file replacements under `app/src/main/assets/rootfs-patches/`.
They fix Android/proot behavior. A new DSH version may change those files.

Targets:

| asset | installed path |
|---|---|
| dsh-app-boot-index.js | .../dsh-app-boot/lib/index.js |
| dsh-spill-local-index.js | .../dsh-spill-local/lib/index.js |
| dsh-atomic-write-index.js | .../dsh-atomic-write/lib/index.js |
| dsh-plugin-package-inventory-index.js | .../dsh-plugin-package-inventory-deepseek/lib/index.js |
| dsh-subprocess-output.js | .../dsh-subprocess-local/lib/output.js |
| dsh-subprocess-index.js | .../dsh-subprocess-local/lib/index.js |
| dsh-subprocess-runner-launch.js | .../dsh-subprocess-local/lib/runner-launch-B2zsQ1Dz.js |
| dsh-fs-local-index.js | .../dsh-fs-local/lib/index.js |
| dsh-tool-bash-index.js | .../dsh-tool-bash/lib/index.js |
| dsh-tool-fs-search-index.js | .../dsh-tool-fs-search/lib/index.js |
| dsh-storage-json-index.js | .../dsh-storage-json/lib/index.js |

Procedure:

1. Extract the target file from both the **previous** image and the **new** image.
2. Compare SHA-256. If unchanged, keep the existing asset.
3. If changed, use a 3-way merge:
   `git merge-file -p <new-unpatched> <old-unpatched> <old-patched>`
   Resolve conflicts by keeping the new upstream structure and re-applying the patch intent:
   - app-boot: profile bundle fallback path.
   - atomic-write: direct-write fallback + no-op file lock.
   - inventory: tolerate unreadable packages (new upstream may already do this).
   - subprocess: `dshSafeMkdtemp` helper + `mkdirSync` import.
   - tool-bash: `bridgeExec()` + `DSH_ANDROID_BRIDGE_PORT` branch.
   - tool-fs-search: bridge glob/grep branches + `shq` / `androidBridgeShell`.
4. Syntax-check every generated file with Node:
   `node --check <file>.mjs`
5. Bump the patch revision constant in
   `runtime/host/src/main/kotlin/com/deepseek/dsh/runtime/host/DefaultHostRuntimeManager.kt`:
   `val patchRevision = "<increment>"`
6. Copy assets into `app/src/main/assets/rootfs-patches/`.

If a target file was deleted or renamed upstream, update the patch map in
`applyBundledRuntimePatches()` accordingly; never write to a non-existent path silently.

---

## 5. Update manifest

Edit `app/src/main/assets/rootfs-manifest.json`:

- `runtimeVersion`: `${ROOTFS_VERSION}-dsh${DSH_VERSION}`
- `releaseTag`: `rootfs-v${ROOTFS_VERSION}`
- `bundledPackages`: update `@deepseek-ai/dsh@<version>`
- `downloadUrls`: both ABIs point to the new release tag
- `downloadSha256`: new hashes
- `archiveSizeBytes`: new x86_64 size
- leave `downloadUrl` / `sha256` empty unless using a single mirror

---

## 6. Publish release

```bash
gh release create ${RELEASE_TAG} \
  --title "Rootfs ${ROOTFS_VERSION} (DSH ${DSH_VERSION})" \
  --notes "Debian 12 rootfs with @deepseek-ai/dsh ${DSH_VERSION}."

gh release upload ${RELEASE_TAG} \
  _rootfs-server/rootfs-x86_64.bin \
  _rootfs-server/rootfs-arm64-v8a.bin --clobber

gh release view ${RELEASE_TAG} --json assets --jq '.assets[] | {name,size}'
```

Verify both asset names and byte sizes. URLs in the manifest must resolve to these assets.

---

## 7. Validate

1. Build: `.\gradlew.bat :app:assembleDebug --console=plain`
2. Fresh install on emulator (this wipes app data, so login afterwards):
   `adb uninstall com.dsh.mobile`
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Launch and wait for rootfs download/extraction.
4. Check Host log for `dsh web: http://127.0.0.1:...`.
5. Verify DSH version inside rootfs via bridge `android_exec_proot` if available.
6. Login, open model picker:
   - provider groups appear (e.g. `DeepSeek 账号`)
   - model names are exactly backend `model.name`
   - reasoning levels match backend `reasoning.efforts`
7. Smoke test tools: bash, read/write, grep, glob, storage write.
8. Upgrade path: install over an existing logged-in install; confirm
   `root/.dsh` credentials/sessions survive and Host boots.
9. If upgrade fails with ENOSPC, clean old caches or uninstall/reinstall; record it.

---

## 8. Rollback

Keep previous release tag and previous docker image. To roll back:

1. Point `downloadUrls`/`downloadSha256`/`runtimeVersion` back to previous values.
2. Publish a new rootfs version (do NOT reuse the same tag with different bytes).
3. App will reinstall on next launch and preserve `root/.dsh`.

---

## 9. Commit checklist

```text
[ ] manifest updated (version/tag/urls/sha/size/bundledPackages)
[ ] patched assets rebased and syntax-checked
[ ] patchRevision bumped
[ ] APK builds
[ ] fresh install boots Host
[ ] model picker + reasoning efforts load from backend
[ ] tools smoke-tested
[ ] release assets verified
[ ] no rootfs binaries or _patch-work committed
```
