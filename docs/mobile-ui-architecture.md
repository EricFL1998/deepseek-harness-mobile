# 移动端 UI 架构选型：原生、混合与全 Web 套壳

> 状态：**提案（待评审）**。本文只做方案与路线决策，不包含删除代码的实施记录。
> 适用对象：Android 客户端 `com.dsh.mobile`（手机本地运行 DSH Host 的产品形态）。
> 关联文档：[插件 UI 原生转换](plugin-ui-transpile.md)、[移动 bridge 协议](mobile-bridge.md)、[原生插件 SDK 规范](native-plugin-sdk.md)、[原生插件宿主接口](native-plugin-host.md)。
> 记录时间：2026-10-20。

## 目录

- [0. 摘要](#0-摘要)
- [1. 背景](#1-背景)
- [2. 术语与边界](#2-术语与边界)
- [3. 方案定义](#3-方案定义)
- [4. 对比矩阵](#4-对比矩阵)
- [5. 逐方案分析](#5-逐方案分析)
- [6. 推荐路线](#6-推荐路线)
- [7. 壳层设计要点](#7-壳层设计要点)
- [8. Web 移动适配清单（上游优先）](#8-web-移动适配清单上游优先)
- [9. 版本与兼容](#9-版本与兼容)
- [10. 性能与容量](#10-性能与容量)
- [11. 迁移计划](#11-迁移计划)
- [12. 删除/保留清单（含实测规模）](#12-删除保留清单含实测规模)
- [13. 风险与对策](#13-风险与对策)
- [14. 验收清单（Web 套壳 E2E）](#14-验收清单web-套壳-e2e)
- [15. 未决问题](#15-未决问题)
- [16. 附录 A：本项目已踩过的坑（证据）](#16-附录-a本项目已踩过的坑证据)
- [17. 附录 B：关键文件索引](#17-附录-b关键文件索引)
- [18. 附录 C：决策记录（ADR 摘要）](#18-附录-c决策记录adr-摘要)

---

## 0. 摘要

产品 UI 正在快速演进（聊天、设置、插件、GenUI），而当前 Android 端同时维护**两套 UI 栈**：

1. 原生 Compose 页面（聊天、设置、插件面板）；
2. 真实 Web 客户端（“完整界面”），以及为了让它“看起来像原生”而写的 bridge 静态转译、keyed slot、原生 GenUI 渲染。

两套栈导致每个插件能力都要实现两次，且原生 UI 永远追不上 Web 版本。本文比较四种方案后建议：

- **短期实现 Web-first 薄壳**：产品 UI 全部用 Web，原生只保留 Host 引导、WebView 容器、系统集成与故障恢复；
- **长期收敛到全 Web 套壳**：当 Web 移动端质量与性能达标后，删除全部原生产品 UI 和 bridge 的 UI 半边。

关键数据（本仓库实测）：

- 原生 UI 相关约 **1.1 万行 Kotlin**（`feature/chat` 3,500、`feature/settings` 5,591、`plugin/mobile-ui` 1,436、`plugin/runtime-web` 597；最后一项将复用为容器，不删除）；
- bridge 的 UI 侧约 **5–6 千行 TypeScript**（`client-slots` 783、`slots/transpile` 3,097、`static-slots` 467、`render-tree` 374、`evaluate` 401 等，bridge 全仓 8,664 行）；
- WebView 容器有已知硬约束：Host 冷启动 60–120 秒、token 每次重启变化、`100vh` 在未定尺时算 0、键盘/刘海/返回需容器处理、Web Notification 在 WebView 不可靠、SAF 目录无法由网页直接访问。

**结论**：全 Web 套壳能消掉“产品 UI 重复实现”和“UI 版本跟随”两个根本问题，但**不能消掉原生壳**。省下的是产品 UI，不是容器与系统集成。

---

## 1. 背景

### 1.1 产品形态

App 启动后在手机内部用 proot 运行 Node 版 DSH Host（rootfs 随 App 分发），Host 起在本机 loopback 端口并打印带 token 的 URL：

```
http://127.0.0.1:<port>/?token=<token>
```

App 通过 REST + WebSocket 与 Host 通信；Host 侧另有一个 bridge 插件（`dsh-mobile-bridge-plugin`），把插件槽位、静态转译、设置命名空间、事件等投影给原生端。

### 1.2 现状架构（原生 + Web 双轨）

```
┌──────────────────────────── Android App ────────────────────────────┐
│  Compose 原生 UI                                                     │
│  ├─ 聊天（ChatScreen 2,442 行：流式、工具卡、审批、提问）             │
│  ├─ 设置（feature/settings 5,591 行：分组、插件面板、原生/Web 切换） │
│  └─ 插件 UI（PluginSlotHost + bridge 静态转译渲染）                  │
│                                                                      │
│  “完整界面”：WebView 加载真实 Web 客户端（重点补丁：视口/焦点/响应式）│
│                                                                      │
│  bridge 插件（Host 内）：client-slots / transpile / settings / events │
│  原生按 bridge 投影画插件；Web 按真实 React 渲染插件                  │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.3 痛点

1. **双份实现**：同一个插件设置页，Web 端由插件自己的 React 渲染，原生端由 bridge 扫描 bundle + 静态转译 + 数据 RPC 重画；插件越复杂，转译越难。
2. **追版本**：DSH Web/插件每版都在变；原生 UI 和 bridge 协议要跟着改，App 发版节奏被 UI 拖住。
3. **能力缺口**：图表、Mermaid、3D、交互组件、表单动作回传，Web 天然完整；原生只能做子集或退化提示。
4. **体验割裂**：原生页与 Web 页两套导航、两套样式、两套返回逻辑；“完整界面”和“原生界面”切换本身就是补丁。
5. **真实案例**：接入 `@changfenhuang/dsh-genui` 时，为了在原生端显示它的 `render_ui`，需要修 bridge 的静态扫描（反引号模板）、keyed 身份、重复挂载，再写一个原生 GenUI 渲染器（`Genui.kt`）。如果 UI 本来就是 Web，这些工作**一项都不需要**。

### 1.4 目标与非目标

目标：

- 产品 UI 只维护一份（Web）；
- UI 随 Host/Web 版本更新，不随 App 发版；
- 插件 UI 与官方 Web 完全一致（含图表/3D/交互）；
- 移动端体验可接受（键盘、刘海、返回、触控、深色、无障碍）；
- Host 故障/离线/低内存可恢复，不白屏。

非目标：

- 不追求“零原生进程/零原生代码”；
- 不改变“Host 跑在手机上、本地优先”的产品定位；
- 不在本文讨论远程 Host 形态（仅作为变体比较）。

---

## 2. 术语与边界

| 术语 | 含义 |
| --- | --- |
| 产品 UI | 用户日常操作界面：聊天、会话列表、设置、插件、GenUI 卡片等 |
| 壳层（Shell） | 不含产品 UI 的原生部分：Host 引导、WebView 容器、系统集成、错误恢复 |
| Host | 手机内 proot 中运行的 Node DSH 服务（含 Web 客户端静态资源） |
| Web UI | Host 提供的真实 Web 客户端（React SPA，无 URL 路由，靠界面状态导航） |
| bridge | Host 内插件，向原生端投影插件槽位/转译树/设置/事件 |
| 全 Web 套壳 | 产品 UI 全部由 WebView 承载；原生只剩壳层 |

**关键区分**：“Web-first 薄壳”和“全 Web 套壳”不是两个方向，而是同一路线的两个完成度：前者还保留少量原生控制页，后者连控制页也收进 Web（或只留隐藏调试入口）。真正的成本差异在壳层，而不在这几个页面。

---

## 3. 方案定义

### 方案 A：纯原生

所有界面都用 Compose 实现；Web 只作为可选的外部能力。

- 代表现状的极端化：删除“完整界面”，插件 UI 全部转译。
- 优点：性能、系统集成、体验上限最高。
- 缺点：每个 DSH 版本都要重写 UI；插件生态无法完整覆盖；长期维护成本随功能线性增长。

### 方案 B：原生 + Web 双轨（当前实现）

原生页面为主，WebView 作为“完整界面”补充；bridge 把插件 UI 投影到原生。

- 优点：离线/快速路径可用；插件至少能在 Web 里完整使用。
- 缺点：双份 UI + bridge 协议 + 原生/Web 模式切换，技术债最高；本次 genui 兼容工作即是成本示例。

### 方案 C：Web-first 薄壳

默认全屏加载 Web UI；原生保留：

- Host 启动/健康/重连/日志；
- WebView 容器（文件选择、下载、权限、键盘、insets、返回、外链）；
- 系统集成（通知、后台保活、更新、深链）；
- SAF 工作区选择；
- 错误/恢复页。

可保留一个“Host 控制台”原生页（运行状态、日志、重启、更新），但不再承载聊天/设置/插件。

### 方案 D：全 Web 套壳

方案 C 的终点：连 Host 控制台也尽量 Web 化或隐藏，原生仅剩启动器与容器。产品 UI 零原生。

### 方案 E：远程 Web 壳（变体，不作推荐）

Host 不在手机，App 只是打开远端 DSH Web 的 WebView/浏览器外壳。App 最轻，但失去本地优先、离线与隐私定位，不适用于当前产品。

---

## 4. 对比矩阵

| 维度 | A 纯原生 | B 双轨（现状） | C Web-first 薄壳 | D 全 Web 套壳 |
| --- | --- | --- | --- | --- |
| 产品 UI 开发量 | 高，每版重写 | 最高，两套并存 | 低，Web 一套 | 极低，Web 一套 |
| 插件 UI 兼容 | 差，需要转译 | 中，完整界面可用、补丁多 | 好，直接复用 Web | 好 |
| UI 随版本更新 | 不跟随 | 仅 Web 部分跟随 | 跟随 Host/Web | 跟随 Host/Web |
| 移动体验 | 最原生 | 割裂，两套风格 | 好，依赖移动适配 | 好 |
| 性能/内存 | 最省 | 最高（Node+WebView+Compose） | 中高 | 中高 |
| 系统集成 | 容易 | 容易 | 需壳层桥接 | 需壳层桥接 |
| 离线/故障 | 可读本地缓存 | 可读本地缓存 | 壳层错误页 + 缓存 | 同左 |
| 迁移成本 | 无 | 已承担 | 中（容器 + Web 适配） | 中（同左，原生更少） |
| 长期维护 | UI 债持续 | 技术债最高 | 低 | 最低 |
| 适用条件 | 功能固定、不跟上游 | 过渡期 | 当前推荐 | Web 达标后的目标 |

**读表要点**：

- C 与 D 的差距只在“几个原生控制页”，壳层成本相同；因此**先做 C，再自然收敛到 D**，不是二选一。
- B 的问题是结构性的：只要保留原生产品 UI，就必须保留 bridge 的 UI 半边，插件越丰富成本越高。
- A 只在“功能范围冻结、不跟随 DSH 演进”时成立，与产品方向冲突。

---

## 5. 逐方案分析

### 5.1 方案 A：纯原生

**架构**：Compose 页面 + REST/WS；插件通过 bridge 静态转译成 RenderTree。

**优点**

- 启动后不依赖 WebView，内存与输入延迟最低；
- 系统能力（通知、文件、SAF、相机）无需桥接；
- 无障碍与文本选择行为可控；
- Host 故障时可读 Room 缓存，体验降级平滑。

**代价**

- 插件 UI 覆盖永远落后：图表、Mermaid、3D、复杂表单、动作回传都要重写；
- 每版 DSH 的界面/插件协议变化都要跟；
- 设置项、文案、交互细节与 Web 不一致，用户看到两套产品。

**结论**：与“插件生态 + 快速版本”的方向冲突，不推荐作为主路线。

### 5.2 方案 B：原生 + Web 双轨（现状）

**架构**：原生页为主；插件页提供“原生/完整界面”切换；bridge 投影插件 UI。

**优点**

- 在 Web 移动适配不完善时，原生页保证可用；
- 插件至少有一个完整 Web 兜底；
- 离线缓存、通知等系统集成已经就绪。

**代价**

- 每次 UI/插件演进需要在原生与 Web 各实现一次；
- bridge 需要持续解析 bundle（引号/反引号、keyed slot、locale、依赖、重复挂载）；
- 两种模式的导航/返回/样式需要专门维护；
- 原生 GenUI 子集无法覆盖插件全部能力，只能退化提示。

**结论**：适合作为**过渡形态**，不适合作为长期架构。

### 5.3 方案 C：Web-first 薄壳

**架构**

```
┌────────────── Android App（壳层） ──────────────┐
│  Host 引导 / 健康检查 / 重连 / 日志             │
│  WebView 容器（权限/下载/键盘/insets/返回/外链）│
│  系统集成（通知/后台/更新/深链）                │
│  SAF 工作区桥                                   │
│  ┌───────────────────────────────────────────┐  │
│  │  真实 Web UI（聊天/设置/插件/GenUI）       │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

**优点**

- 产品 UI 只维护 Web 一份；插件 UI 天然完整；
- UI 随 Host/Web 更新，App 只在壳层变化时发版；
- 删除原生 UI 与 bridge UI 半边后，长期维护成本显著下降；
- 与官方 Web 体验一致，不再有“原生/完整界面”双模式。

**代价**

- 需要一次性把 WebView 容器做扎实（见第 7 节）；
- 需要推动 Web 自身支持移动端（dvh、safe-area、键盘、返回）；
- 内存与冷启动比原生页面高（Node + WebView 双运行时）；
- Host 不可用时只能显示壳层错误页/缓存，而不是原生聊天。

**适用条件**：Web 移动端适配达标、目标机型内存可接受。

### 5.4 方案 D：全 Web 套壳

与 C 相同，只是连“Host 控制台”等少量原生页面也不保留（或降级为隐藏调试入口）。**收益边际很小，风险与 C 相同**；建议作为 C 稳定后的自然结果，而不是单独立项。

### 5.5 方案 E：远程 Web 壳（不推荐）

Host 在桌面/服务器，App 打开远端 URL。

- 优点：App 极简，更新完全跟随服务端。
- 缺点：失去本地优先/离线/隐私；远程鉴权、断线、网络抖动都要处理；插件运行在远端，手机端能力（相册、文件）依赖上传。
- 适用：产品改为“远程控制台”时另议。

---

## 6. 推荐路线

**实现 C，目标 D。** 分四步走，每步有明确退出条件（见第 11 节）。

1. 壳 MVP（不删任何现有代码，用开关切换）；
2. WebView 容器补齐 + 移动适配；
3. 产品 UI 默认切 Web，原生页面降级为兜底；
4. 通过验收后删除原生 UI 与 bridge UI 半边。

### 6.1 壳层职责清单

| 职责 | 必须 | 说明 |
| --- | --- | --- |
| Host 首次解压/启动/健康检查 | ✅ | 冷启动 60–120 秒，必须有进度与失败重试 |
| token 获取与 WebView 加载 | ✅ | 每次 Host 重启 token 变化，需重新注入并 reload |
| WebView 生命周期/崩溃恢复 | ✅ | `onRenderProcessGone`、低内存重建、白屏检测 |
| 返回键/手势 | ✅ | Web 历史 → 覆盖层 → 退出确认 |
| 键盘/insets/safe-area | ✅ | edge-to-edge、`adjustResize`、dvh 配合 |
| 文件选择/上传/拍照/录音 | ✅ | `WebChromeClient` + 运行时权限 |
| 下载 | ✅ | `DownloadListener` → DownloadManager |
| 外链/OAuth 回跳/导航白名单 | ✅ | 只允许 Host origin 与授权回调 |
| 通知/后台保活 | ✅ | turn 完成/错误提醒；WebView 不承载通知 |
| SAF 工作区选择 | ✅ | 网页无法访问 Android 目录；需原生选择器 + Host 路径映射 |
| 更新检查/版本门禁 | ✅ | 壳与 Host/Web 的兼容矩阵 |
| Host 控制台（状态/日志/重启） | 建议 | 可先原生，稳定后 Web 化 |
| 屏幕常亮/深链/分享 | 可选 | 视产品需要 |

### 6.2 明确不保留的原生部分

- 原生聊天（流式、工具卡、审批、提问、附件、Markdown）；
- 原生设置页（分组、模型选择、插件列表、插件面板、原生/Web 切换）；
- 原生插件 UI 渲染（`PluginSlotHost`、静态转译消费、keyed 匹配）；
- 原生 GenUI 渲染器（`Genui.kt`）；
- bridge 的 `client-slots` / `transpile` / `settings` 等 UI 投影 providers。

---

## 7. 壳层设计要点

### 7.1 Host 引导与生命周期

状态机建议：

```
Idle → Preparing(rootfs) → Starting(Node) → WaitingForUrl → Ready
                                  ↘ Failed(可重试)     ↘ Crashed(重启)
```

关键点：

- **启动预算**：现有冷启动 60–120 秒，壳层必须有进度/阶段文案，不能白屏；
- **token**：从 Host stdout 解析 URL，或通过受控本地接口获取；token 不写日志；
- **健康检查**：端口可连 + 一次鉴权请求成功才算 Ready；
- **看门狗**：Host 进程退出/卡死检测，指数退避重启；
- **前台服务**：保证后台存活与通知通道；Android 12+ 注意前台服务类型；
- **日志**：滚动保留，支持导出（壳层页面或系统分享）；
- **恢复**：重启后 WebView 重新 load 新 token URL，恢复上次会话（Web 自己负责路由）。

### 7.2 WebView 容器

- **加载策略**：`loadUrl("http://127.0.0.1:<port>/?token=...")`；只允许该 origin 及 OAuth 回调；
- **Cookie**：`CookieManager` 持久化；Host 重启后旧 cookie 失效，需清理并重新走 token；
- **导航**：`shouldOverrideUrlLoading` 白名单；外部链接交给系统浏览器；
- **文件**：`onShowFileChooser` 支持相册/文件/拍照；权限被拒时给可读提示；
- **下载**：`setDownloadListener` → 系统下载；
- **权限**：相机/麦克风 `onPermissionRequest` 映射运行时权限；
- **渲染进程崩溃**：`onRenderProcessGone` 重建 WebView 并提示，避免整个 App 崩溃；
- **键盘**：`windowSoftInputMode=adjustResize` + edge-to-edge insets 分发；Web 侧用 `dvh`/`visualViewport`；
- **返回**：先 `canGoBack()`，再交给 Web 的覆盖层（抽屉/弹窗）逻辑，最后退出；
- **调试**：debug 构建开 `WebView.setWebContentsDebuggingEnabled(true)`，配合 CDP 排障；
- **安全**：关闭文件访问/混合内容；JS 接口最小化并校验调用来源；不向 Web 暴露任意原生能力。

### 7.3 系统集成

- **通知**：不能依赖 Web Notification（WebView 支持不可靠）。方案：Host 事件经本地 WS/HTTP 推给壳层前台服务，或壳层订阅 bridge `events`；通知点击深链回 App。
- **后台保活**：前台服务 + 电池优化白名单引导 + 任务结束自停；
- **更新**：App 更新检查；Host/rootfs 更新策略单独定义（见第 9 节）；
- **深链/分享**：`dsh://` scheme 或 App Links；分享入口进 Web 对应页面。

### 7.4 SAF 工作区

这是纯 Web 唯一真正缺失的能力：

1. 原生进入 SAF 选择器（`ACTION_OPEN_DOCUMENT_TREE`），持久化 URI 权限；
2. 把目录映射到 Host 的 `/workspace`（当前实现是绑定到 `Documents/DSH-Workspace`，可继续沿用）；
3. 通过受控接口通知 Host/Web 工作区变更并刷新；
4. 多工作区：列表、重命名、删除由 Web 展示，原生只负责目录授权与路径映射。

**协议建议**：定义一个小而稳定的“宿主工作区服务”契约（选择、列出、映射状态），由壳层实现、Web 调用；不要依赖任意文件系统路径。

### 7.5 错误与恢复

- Host 启动失败：阶段化错误 + 重试 + 日志导出；
- Web 加载失败：错误页 + 重试 + 检查 Host 状态；
- 渲染进程崩溃：自动重建一次，再失败进壳层错误页；
- 低内存：监听 `onTrimMemory`，释放 WebView 缓存/重启；
- 离线：本地 Host 不需要外网也能进 UI；模型请求失败由 Web 展示。

### 7.6 安全基线

- 只加载 loopback；禁止任意远程导航（白名单）；
- token 只在内存/URL 中使用，不写日志、不落盘明文；
- WebView 禁用文件访问、禁止混合内容；
- JS 桥仅暴露“工作区选择/通知/分享”等白名单方法，并校验来源；
- 导出日志前脱敏 token/路径。

---

## 8. Web 移动适配清单（上游优先）

| 项目 | 现状 | 目标 |
| --- | --- | --- |
| 视口高度 | WebView 未定尺时 `100vh` 算 0，需延迟加载/注入修复 | Web 用 `100dvh`/`visualViewport`，不依赖 App 注入 |
| 刘海/安全区 | App 注入响应式 CSS | `viewport-fit=cover` + `env(safe-area-inset-*)` |
| 键盘 | 需容器 `adjustResize`，页面可能被遮挡 | 输入区随 `visualViewport` 滚动，`dvh` 自适应 |
| 返回 | App 注入脚本/History 处理 | Web 统一拦截抽屉/弹窗，暴露可预测的 back 行为 |
| 触控 | 部分 hover 样式 | 无 hover 依赖、≥44px 触控目标、禁用双击缩放误触 |
| 深色模式 | 跟随系统 | `prefers-color-scheme` 与主题服务一致 |
| 字体缩放 | 未验证 | 支持系统字体缩放不破版 |
| 下载/上传 | App 容器能力 | Web 正常触发 input/download |
| 通知 | WebView 不可靠 | 由壳层通知 + Web 内提示 |
| 截图/分享 | 未定义 | Web Share API 或壳层桥 |

原则：**移动适配进上游**，App 侧的注入只作为过渡；否则“随版本更新”会重新变成“随版本打补丁”。

---

## 9. 版本与兼容

### 9.1 版本来源

- **App shell 版本**：APK 版本，含 Host 启动器、容器、系统集成；
- **Host/rootfs 版本**：决定 Host 能力；
- **Web UI 版本**：随 Host 分发（同一份 web 前端资源）。

理想状态：Web UI 与 Host 同步升级，App shell 不需要跟着改；实际要处理版本偏差。

### 9.2 兼容矩阵

| Shell | Host/Web | 行为 |
| --- | --- | --- |
| 新 | 新 | 正常 |
| 新 | 旧 | 允许，但按能力检测降级；提示可升级 Host |
| 旧 | 新 | **必须门禁**：Host 声明最低 shell/协议版本；不满足时提示升级 App，不进入 Web |
| 旧 | 旧 | 正常（保留旧 UI） |

实现建议：

- Host 启动接口返回 `{ webVersion, protocolVersion, minShellVersion }`；
- Shell 侧维护 `minWebVersion` 与 `minWebViewVersion`；
- 不满足时给明确升级页，而不是白屏/JS 报错；
- 保留回滚：Host 可切回上一版 rootfs/web 资源。

### 9.3 WebView 版本

- 声明最低 WebView/Chrome 版本（按 ECMAScript/API 需求）；
- 启动时能力检测（`dvh`、`visualViewport`、`ResizeObserver` 等），缺失时提示更新 WebView；
- 低端/老旧设备矩阵纳入测试。

---

## 10. 性能与容量

| 指标 | 现状/预期 | 目标/门槛 |
| --- | --- | --- |
| Host 冷启动 | 60–120 秒（实测） | 不劣化；壳层有进度与失败恢复 |
| Web 首屏 | WebView 加载 Host 页面 | Ready 后 ≤3 秒可见首屏 |
| 峰值内存 | Node(proot) + WebView + 壳 | 目标机型不触发 LMK；设上限并监控 |
| 后台存活 | 前台服务 | 10 分钟后回到前台可恢复 |
| WebView 崩溃恢复 | 未系统化 | 自动重建一次，成功率可观测 |
| 低端机 | 未验证 | 明确最低配置与降级策略 |

需要实测的指标：冷启动分段耗时、`dumpsys meminfo` 峰值、WebView 渲染进程重启次数、后台被杀率、键盘弹出后的布局正确率。

---

## 11. 迁移计划

### Phase 0：壳 MVP（开关控制，不删代码）

交付：

- 单 Activity + WebView + Host 引导；
- 启动/失败/重试页；
- token 注入与 Host 重启 reload；
- 返回键、键盘、insets 基础处理；
- Debug 入口保留原生页面。

退出条件：能连续 10 次冷启动进入 Web；Host kill 后能恢复；无白屏。

### Phase 1：容器完备

交付：文件选择/上传/拍照、下载、权限、外链/OAuth、`onRenderProcessGone`、前台服务、通知、日志导出、SAF 工作区桥。

退出条件：第 14 节验收清单通过 80% 以上。

### Phase 2：Web 移动适配

交付：上游 dvh/safe-area/键盘/返回/触控适配；App 注入降级为兜底。

退出条件：不依赖 App 注入也能正常使用；真机矩阵通过。

### Phase 3：产品 UI 切换默认

交付：默认全屏 Web；原生页面仅作为“Host 控制台/兜底”；灰度开关与回滚。

退出条件：崩溃率、启动成功率、用户反馈不低于现状；性能达标。

### Phase 4：删除原生 UI 与 bridge UI 半边

交付：删除第 12 节清单；bridge 收缩为 Host 控制/事件/通知桥；文档更新。

退出条件：连续两个版本无回滚需求。

---

## 12. 删除/保留清单（含实测规模）

### 12.1 可删除（约 1.1 万行 Kotlin + 5–6 千行 TS）

| 路径 | 行数 | 说明 |
| --- | --- | --- |
| `feature/chat/` | 3,500 | 原生聊天、工具卡、审批、提问、流式（含 `Genui.kt` 902 行） |
| `feature/settings/`（大部分） | 5,591 | 原生设置分组/插件面板/原生-Web 切换；Host 控制台少量保留 |
| `plugin/mobile-ui/` | 1,436 | `PluginSlotHost`、RenderTree 消费、keyed 匹配 |
| bridge `src/providers/client-slots.ts` | 783 | 插件槽位投影 |
| bridge `src/slots/transpile.ts` + `providers/transpile.ts` | 3,423 | 静态转译 |
| bridge `src/slots/static-slots.ts`、`render-tree.ts`、`evaluate.ts` | 1,242 | 扫描/渲染树/求值 |
| bridge `src/providers/settings.ts` | 104 | 设置命名空间投影 |
| bridge `src/providers/host-slots.ts` | 180 | Host 设置面投影 |

> 注：数字为当前仓库行数；删除时并非全部同时移除（`feature/settings` 中 Host 控制相关保留）。

### 12.2 保留/复用

| 路径 | 用途 |
| --- | --- |
| `app/` | 壳层入口、导航、权限、通知、更新 |
| `runtime/host/` | Host 启动/补丁/健康检查（核心资产） |
| `core/protocol/` | REST/WS 协议、会话/模型目录（壳层仍需要） |
| `core/data/` | Room 缓存（壳层错误页/会话恢复） |
| `plugin/runtime-web/` | 由“插件 Web 岛”升级为“主 WebView 容器” |
| bridge 的 `events`/`rpc`/`package-inventory` | 壳层通知/诊断按需保留，UI 投影删除 |

---

## 13. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| WebView 版本碎片 | 旧设备白屏/语法错误 | 最低版本门禁 + 能力检测 + 升级提示 |
| 内存（Node+WebView） | 低端机被杀/卡顿 | 实测矩阵 + 峰值上限 + 低内存降级 |
| 冷启动 60–120s | 用户等待 | 启动进度/阶段文案 + 失败重试 + 预热策略 |
| 键盘/insets 适配 | 输入被遮挡 | 上游 `dvh`/`visualViewport` + 容器 insets 分发 |
| 返回/导航不一致 | 误退出 | 统一 back 协议 + 覆盖层拦截 |
| SAF 映射 | 无法选择目录 | 原生选择器 + 稳定宿主协议 + 明确不支持任意路径 |
| 通知不可靠 | 错过完成提醒 | 壳层订阅 Host 事件，不依赖 Web Notification |
| OAuth/外链 | 登录失败 | 白名单 + Cookie/回跳测试 + 外部浏览器兜底 |
| 版本偏差 | 旧壳打不开新 Web | 兼容矩阵 + 版本门禁 + 回滚 |
| 上游改版 | 移动端破版 | 移动 E2E + 版本契约 + 灰度 |
| 安全 | token/导航风险 | origin 白名单、JS 桥最小化、日志脱敏 |
| 测试复杂度 | 回归成本 | 自动化 CDP/WebView 冒烟 + 真机矩阵 |

---

## 14. 验收清单（Web 套壳 E2E）

**启动与恢复**

- [ ] 冷启动（含首次解压）进入 Web，有进度与失败重试；
- [ ] Host kill / 崩溃后自动重启并 reload 新 token；
- [ ] WebView 渲染进程崩溃后恢复；
- [ ] 后台 10 分钟回前台可继续；
- [ ] 低内存/`onTrimMemory` 不闪退。

**输入与导航**

- [ ] 键盘弹出输入框可见、可滚动、可发送；
- [ ] 中文输入法组合输入/回车/粘贴图片；
- [ ] 返回键：Web 历史 → 抽屉/弹窗 → 退出确认；
- [ ] 旋转/分屏/折叠屏布局正常；
- [ ] 刘海/安全区/手势条不遮挡。

**文件与系统**

- [ ] 相册/文件上传、拍照、录音权限流程；
- [ ] 下载到系统下载目录并可打开；
- [ ] 外链走系统浏览器，OAuth 回跳成功；
- [ ] 通知（turn 完成/错误）可点进会话；
- [ ] SAF 选择工作区、重启后仍有效；
- [ ] 系统字体缩放/深色模式/无障碍（TalkBack）可用。

**插件与内容**

- [ ] 已接入原生 UI 的插件在纯 Web 下完整可用；
- [ ] GenUI 图表/Mermaid/3D/交互正常；
- [ ] 插件安装/卸载/启停后 Web 即时生效；
- [ ] 长会话滚动、图片、代码块、工具卡正常；
- [ ] 断网时 UI 可用，模型请求失败有可读提示。

**版本**

- [ ] 新 Shell + 旧 Host、旧 Shell + 新 Host 均有明确行为；
- [ ] 最低 WebView 版本不足时给出升级提示；
- [ ] 回滚到旧 Host/Web 可用。

---

## 15. 未决问题

1. **Host/rootfs 是否独立更新**？如果只随 APK，则“UI 随版本更新”仍受 APK 发版节奏约束，需要先定义 rootfs 更新通道。
2. **是否保留原生兜底聊天**？保留可降低 Web 故障风险，但等于继续维护第二套 UI；建议先用开关灰度，不长期保留。
3. **Workspace 映射策略**：继续固定 `Documents/DSH-Workspace`，还是支持任意 SAF 目录 + 复制/挂载？后者需要 Host 侧文件系统支持。
4. **通知通道**：壳层订阅 Host 事件，还是保留 bridge `events`？需要与 bridge 版本策略一起定。
5. **最低 WebView/Android 版本**与目标机型清单。
6. **上游移动适配的归属**：哪些进 DSH Web 官方，哪些允许 App 注入；需要和上游约定契约与时间表。
7. **全 Web 后 bridge 的去留**：仅保留 Host 控制/事件，还是完全去掉 bridge（改由 App 直接读 Host 本地接口）。
8. **远程 Host 变体**是否进入路线图。

---

## 16. 附录 A：本项目已踩过的坑（证据）

1. **WebView 0×0 启动**：未定尺时导航会让 Chromium 布局视口锁成 0 高，`window.innerHeight` 正常但 `100vh=0`，设置面板高度算成 0，只剩灰色蒙版。修复：等 `onSizeChanged` 非零再加载 + `100vh` 探针 + 尺寸微抖。
2. **响应式注入**：窄屏下设置面板需要改为顶部横向导航，App 注入 `#dsh-mobile-responsive` CSS 才能用；这类补丁应逐步上游化。
3. **Web 无 URL 路由**：插件设置页需要注入脚本驱动界面（预览提示 → 侧边栏 → 账号菜单 → 设置 → section），并用 React fiber 调 `openSection` 才稳定；说明“产品 UI 在 Web”能省掉这类原生驱动逻辑。
4. **token 轮换**：Host 每次重启端口/token 都变，WebView 必须重新 load；否则旧 cookie 失效后白屏。
5. **插件演进成本**：接 genui 时，原生侧需要修 bridge 反引号扫描、keyed 身份、重复挂载，并写 `Genui.kt`；Web 侧零改动即可完整渲染（含图表/3D）。
6. **Host 冷启动**：模拟器实测 60–120 秒，壳层必须有启动页与失败重试。
7. **SAF 工作区**：Web 无法访问 Android 目录，必须原生选择器 + 固定挂载点。
8. **通知**：WebView 内 Web Notification 不可靠，必须壳层实现。

## 17. 附录 B：关键文件索引

- Host 启动/补丁：`runtime/host/src/main/kotlin/com/deepseek/dsh/runtime/host/DefaultHostRuntimeManager.kt`
- WebView 容器现状：`plugin/runtime-web/src/main/kotlin/com/deepseek/dsh/plugin/runtimeweb/WebIslandContainer.kt`
- 插件 Web 岛：`feature/settings/src/main/kotlin/com/deepseek/dsh/feature/settings/PluginWebIsland.kt`
- 原生聊天：`feature/chat/src/main/kotlin/com/deepseek/dsh/feature/chat/ChatScreen.kt`
- 原生插件 UI：`plugin/mobile-ui/src/main/kotlin/com/deepseek/dsh/plugin/mobileui/PluginSlotHost.kt`
- bridge：`deepseek-harness-mobile-bridge-plugin/src/index.ts`、`src/slots/transpile.ts`、`src/providers/client-slots.ts`
- 本文关联设计：[plugin-ui-transpile.md](plugin-ui-transpile.md)、[mobile-bridge.md](mobile-bridge.md)

## 18. 附录 C：决策记录（ADR 摘要）

- **ADR-001**：移动端产品 UI 采用 Web 单一实现；原生只保留壳层。状态：提案。
- **ADR-002**：不追求零原生；壳层是长期资产，按第 6.1 节职责清单建设。状态：提案。
- **ADR-003**：Web-first 与全 Web 套壳为同一路线的前后阶段，不分别立项。状态：提案。
- **ADR-004**：移动适配以 DSH Web 上游为主，App 注入仅作过渡。状态：待与上游确认。
- **ADR-005**：删除原生 UI 前必须通过第 14 节验收清单与性能门槛。状态：提案。
- **ADR-006**：原生 UI 插件商店作为 Web-first 之外的增强层：第三方以伴侣 APK（Kotlin/Compose）为已有 JS 插件提供原生渲染前端，逻辑层留在 Host 零重写，通信复用 bridge `dispatch`/`client/refresh`；App 侧建设发现/验签/ABI 门禁/渲染宿主/能力桥/主题/商店/管理八大模块。Web 兜底永远在场，原生层未命中或未安装时一律降级 WebView。状态：提案。规范见 [native-plugin-sdk.md](native-plugin-sdk.md) 与 [native-plugin-host.md](native-plugin-host.md)。
- **ADR-007**：删除插件 UI 静态转译链路。状态：**已实施（2026-10-20）**。bridge `transpile` provider 与 App 的 `plugin/mobile-ui` 原生渲染器、`feature/chat/Genui.kt`、聊天区全部插件兜底槽位（tool.call.toolview、assistant-actions、composer.dock、input.right）一并移除，bridge 升至 2.0.0。插件界面页（设置→插件界面）统一走完整 Web 界面；插件管理详情页（PluginsScreen）保持原生不变。保留：bridge `client-slots` 的目录/图标/dispatch、Host 挂载补丁（`applyInstalledPluginRows`）、rootfs 运行时补丁。label 本地化由 `slots/bundle-locale.ts` 支撑。
- **ADR-008**：落地原生插件 SDK 与宿主（ADR-006 的实现）。状态：**已实施（2026-10-20）**。新增 `:plugin:native-sdk`（契约 `DshUiPlugin`/`ComponentRenderer`/`PluginDescriptor`、能力桥 `PluginHostApi`、主题 `PluginTheme`、spec 渲染 `SpecRenderer`（GenUI 重建为渲染能力）、落点 `SlotRegistry`（含 ENHANCE/OVERRIDE 覆盖仲裁）、工具钩子 `ToolRenderRegistry`、页面入口 `PageRegistry`、服务绑定 `DshPluginBinder`）与 `:plugin:native-host`（发现+验签+ABI 门禁 `NativePluginDiscovery`、落点容器 `NativeSlotHost`（错误围栏）、能力桥实现 `PluginHostApiImpl`、门面 `NativeHost` CompositionLocal）。9 个落点全部接入：composer.dock、input.right、tool.call、settings、message-action、sidebar、overlay、chat-stream、web-island。插件页新增「原生插件」tab（搜索+推荐列表+详情）。bridge 新增 `native-plugins` provider（原生伴侣清单/ABI/能力）与 GitHub tag 商店索引器（`dsh-mobile-plugin` tag）。开发者文档见 [plugin-sdk-guide.md](plugin-sdk-guide.md)。
