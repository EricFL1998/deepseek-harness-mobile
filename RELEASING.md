# 发版流程

本仓库的 APK 不是孤立产物：它引用 bridge 插件与 rootfs 镜像。发版顺序固定为：

```text
bridge 插件发版  ->  rootfs 镜像重建  ->  APK 版本提升、打 tag、发布
```

APK 的 tag 工作流会校验前两步真的完成了，避免发布一个设备无法更新、或内置清单指向失效下载地址的 APK。

## 三个版本源

| 文件 | 字段 | 含义 |
|---|---|---|
| `app/build.gradle.kts` | `versionName` / `versionCode` | APK 版本；tag 形如 `app-v1.0.3` |
| `app/src/main/assets/bridge-plugin.json` | `version` | 当前 rootfs 镜像里打包的 bridge 插件版本；必须与 rootfs 清单一致 |
| `app/src/main/assets/rootfs-manifest.json` | `runtimeVersion` / `releaseTag` / `bridgeVersion` | 内置兜底用的 rootfs 清单；正常运行时 App 会从 rootfs 仓库最新 release 重新拉取 |

## bridge 插件发版

bridge 插件在独立仓库：

```text
https://github.com/EricFL1998/deepseek-harness-mobile-bridge-plugin
```

1. 修改代码后提升 `package.json` 的 `version`（纯 `x.y.z`）。
2. 把 `package.json` 的 `release` 设为 `true`。
3. 提交并推送 `master`。

该仓库的 `Publish version marker` workflow 会：

- 校验版本并创建 `v<version>` release（无附件，tag 就是产物）；
- 通过 `ROOTFS_DISPATCH_TOKEN` 触发 rootfs 仓库的 `bridge-release` 重建；
- 把 `release` 改回 `false` 并推回，避免同一版本重复发布。

## rootfs 镜像

rootfs 仓库收到 `bridge-release` 或 DSH 版本变化后，会解析最新 bridge release、重建镜像并发布 `rootfs-vX.Y.Z`。发布成功后，把最新 release 的 `rootfs-manifest.json` 内容同步到本仓库的 `app/src/main/assets/rootfs-manifest.json`，并确认 `bridge-plugin.json` 的 `version` 与清单的 `bridgeVersion` 一致。

APK 运行时仍会从 `releases/latest/download/rootfs-manifest.json` 拉取最新清单；内置清单只是首次离线/网络失败时的兜底，但兜底也必须指向真实存在的 release。

## APK 发版

1. 确认 `bridge-plugin.json` / `rootfs-manifest.json` 版本一致，且对应 release 已发布。
2. 提升 `app/build.gradle.kts` 的 `versionName` 与 `versionCode`。
3. 提交后打 tag 并推送：

   ```sh
   git tag app-v1.0.3
   git push origin master app-v1.0.3
   ```

4. `.github/workflows/build-apk.yml` 在 tag 上会先运行发版守卫：

   - `bridge-plugin.json` 的 `version` 必须等于 `rootfs-manifest.json` 的 `bridgeVersion`；
   - bridge release `v<version>` 必须存在；缺失时若配置了 `BRIDGE_RELEASE_TOKEN`，
     会触发 bridge 仓库的 `release.yml` 并等待 release 出现；没有 token 直接失败；
   - rootfs 的 `releaseTag` 必须存在；
   - 如果 bridge 仓库已有更新的 release，只警告（运行时会提示用户更新插件）。

5. 守卫通过后构建、签名并发布 APK。未配置签名 secrets 时构建 debug APK；tag 发布需要签名 secrets。

## 跨仓 token

在 APK 仓库配置：

| Secret | 用途 | 权限 |
|---|---|---|
| `BRIDGE_RELEASE_TOKEN` | APK tag 时补发缺失的 bridge release | 对 bridge 仓库有 `contents: read` + `actions: write`（经典 PAT 通常需要 `repo`、`workflow`） |

没有该 token 时，APK 发版仍然可以继续——前提是 bridge release 已经手动发好。守卫不会伪造 release，也不会在版本不一致时放行。

## 发版顺序速查

- **只改 APK（native UI、bridge 鉴权等）**：bridge 版本可以不变；APK tag 会校验现有 bridge release 存在，然后发布。
- **改了 bridge 插件**：先发 bridge（提升版本 + `release: true`），等 rootfs 镜像重建并发布；再同步 rootfs 清单、提升 APK 版本、打 tag。
- **只改 rootfs 配方**：rootfs 仓库会因 `bundle/**` 变化强制重建；发布后同步内置清单，再决定是否发 APK。
- **DSH 引擎升级**：先 rebase 补丁并更新内置清单，再发 APK；中间窗口里把新 DSH 的 rootfs release 标为 prerelease。

## 为什么 APK tag 要等 bridge release

设备端更新插件读的是 bridge 仓库的 release tag；rootfs 首装读的是 rootfs release 的清单。两者任何一个不存在，都会变成“APK 发出去了，但用户更新/首装失败”。所以在 APK tag 上做守卫，而不是在发布之后补救。

## DSH 引擎跟随上游与 Android 补丁

Android 补丁（`app/src/main/assets/rootfs-patches/*`）是 DSH 包文件的补丁。为了让 rootfs 永远跟随上游，补丁现在以**统一 diff** 的形式放在 rootfs 仓库的 `bundle/patches/android/*.patch`，由 rootfs 构建阶段应用到构建时解析到的最新 DSH：

- 每个 patch 只改必要的行，不再整文件覆盖；上游移动到补丁区域时，`patch` 失败，镜像构建失败，不会发布混合版本。
- 构建出的镜像在清单里带 `"androidPatched": true`。App 对这类镜像：
  - 不再要求 DSH 段与内置补丁目标一致；
  - 启动时跳过整文件补丁覆盖，只保留 `/root/.dsh` 下的 Node preload；
  - rootfs 更新只比较 rootfs 段，引擎升级不强制换 APK。
- 没有 `androidPatched` 的旧镜像仍走原来的运行时整文件补丁与 DSH 版本守卫。

发版规则：

1. **不要发布未烧补丁的新 DSH 镜像**；如果补丁暂时打不上，让 rootfs 构建失败，而不是把 release 发出去。
2. 如果确实发布了未烧补丁的新 DSH 镜像（例如补丁还没 rebase），必须把该 rootfs release 标记为 prerelease，直到补丁适配完成：
   ```sh
   gh release edit rootfs-v1.0.12 --repo EricFL1998/deepseek-harness-mobile-rootfs --prerelease
   ```
3. 补丁失败时，用 `bundle/patches/android` 里的 patch 文件对最新 DSH 包手工 `patch -p1`，根据冲突更新 diff，再触发 rootfs 构建。
4. 旧镜像的 DSH 段不一致仍被视为强制更新，即使已装镜像的 rootfs 段更大——否则用户会卡在版本混杂的树里。
