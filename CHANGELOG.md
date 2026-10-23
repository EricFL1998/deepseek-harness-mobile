# 更新日志

本文件记录 DSH 手机端（Android App）的版本变化。bridge 插件与 rootfs 镜像有各自的版本线，不在此文件里展开；发版顺序见 [RELEASING.md](RELEASING.md)。

## 1.0.4 — 2026-10-23

### 修复

- **bash 工具回到 Host 容器**：移除 `android_exec_shell` 设备桥分支，改由 DSH 自己的 `ctx.shell` 在 proot 内执行。恢复 `run_in_background`、sandbox policy、`shellEnv`、取消与 jobs 语义，不再让 App 侧再起一个 proot。
- **proot 参数对齐**：Host 启动补上 `-L`、`--kill-on-exit`、`-0`，并绑定 `/system`、`/apex`、`/proc/self/fd`、`/dev/urandom → /dev/random`；缺失的 bind 源自动跳过。
- **阻止不兼容的 DSH 引擎镜像**：rootfs 清单源忽略 DSH 段与 APK 内置清单不一致的远端/缓存清单；Host 启动拒绝已安装的不兼容 rootfs；`decideRootfsUpdateKind` 在引擎段不一致时强制更新（即使已装 rootfs 段更大）。
- `rootfs-v1.0.12`（DSH 0.2.1-rc.3）标记为 prerelease，`releases/latest` 回到兼容的 `rootfs-v1.0.11`（DSH 0.2.1-rc.2）。

### 验证

- 模拟器：bash 在 Host 内执行（Debian 12 / Android 16 内核 / uid=0），后台任务与 fs-search 设备桥正常；rc.3 的运行时更新被守卫拦截。
- 新增 rootfs 版本兼容测试与 proot 构建测试；修复测试里过时的 `--link2symlink` 断言。

## 1.0.3 — 2026-10-23

### 安全

- **设备桥本地鉴权**：BridgeServer 每次 Host 启动轮换 256 位 token，写入 guest 的 `/root/.dsh/.bridge_token`；每个 JSON-RPC 请求必须携带 `auth`，缺失或错误返回 `-32001` 并断开连接（fail closed）。
- 修复 BridgeServer 默认绑定所有网卡的问题，显式绑定 `127.0.0.1`。
- bash / fs-search 的设备桥请求携带 token（patch revision 38）。

### 发版

- APK tag 增加 bridge / rootfs release 守卫：bridge 版本未发布时可通过 `BRIDGE_RELEASE_TOKEN` 自动补发，否则失败。
- 内置 rootfs 清单更新到 `rootfs-v1.0.11` / bridge 2.0.7。
- 新增 [RELEASING.md](RELEASING.md)，记录 bridge → rootfs → APK 的发版链。

### 文档

- 新增 DSHA 对比报告（设备操控、工作区、bash、proot）。

## 1.0.2 — 2026-10-23

### 修复

- 启动前修复损坏的 profile `cordis.patch.yml`，避免 Host 因 Loader 配置错误起不来。

### 文档 / 站点

- README 产品页横幅与链接更新；产品页头部布局、语言切换和 CTA 文案修复。

## 1.0.1 — 2026-10-23

### 修复

- 插件商店详情页重复返回入口、操作按钮位置、英文标题换行问题。

### 构建 / 发布

- release APK 使用本地 keystore 签名；CI 在配置签名 secrets 时构建并发布签名 APK。

### 文档 / 站点

- 新增产品介绍页与英文页面，部署到 GitHub Pages；README 链接与横幅更新。

## 1.0.0 — 2026-10-23

### 首个正式版

- **完整 DSH 本地运行**：Android 内置 Debian + proot + Node，启动真实 DSH Host；会话、Agent、工具、审批、定时任务、Agent Team 都由真实 Host 处理。
- **原生 Compose 界面**：聊天、会话、文件、审批、设置、右侧栏、插件商店原生渲染。
- **插件体系**：Host 插件与 `bridge/v1` 协议保留；官方 client slot 映射到原生界面，复杂页面进 Web 岛；提供 Native UI SDK 与伴生原生插件 APK。
- **本地工作区**：默认共享存储 `Documents/DSH-Workspace`，文件管理器可见；rootfs 首次安装与更新。
- **多主机与远程**：本地 Host 与远程 Host 的入口、设置与凭据隔离（远程连接能力仍在持续完善）。
