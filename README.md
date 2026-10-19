# DSH Android 手机端工程

> 基于 **《DSH 手机端 · 最终设计文档 v4.0》**（2026-10-17）落地实现的完整 DeepSeek Harness 手机端客户端。

---

## 核心设计决策 (v4.0 基线)

1. **Host 运行在手机本地 Linux 用户空间**：基于 Compact Debian slim (glibc) + proot/bxroot + Node 24 + 真实 DSH，插件与 Agent 无需任何改动。
2. **核心 UI 原生化（Compose + Material 3）**：会话、聊天消息流、审批、设置、文件均由纯原生 Compose 承载；WebView 仅用于运行插件与重型组件（Web 岛）。
3. **真实文件系统接入（工作区 = 手机目录）**：默认工作区直绑手机公有共享文件夹（`/storage/emulated/0/Documents/DSH-Workspace`），在 Linux 容器内挂载为 `/workspace`，系统自带文件管理器直接可见，产物无需导出。
4. **内置 Surface 适配层（无伴生插件）**：将 `native-surface.js` 补丁内建于前端，自动拦截并调度 `conversation.composer.dock`、`shell.overlay`、`settings.*` 等全部 26 个核心 Slot，保障第三方插件生态 100% 正常运行。
5. **SDUI 声明式渲染器**：支持未来插件以 JSON 声明移动端布局（`dsh.mobile`），手机端以零 WebView 的原生组件树进行高性能渲染。
6. **多主机支持**：支持本地 Linux 用户空间 Host 与远程私有网关 Host 混合管理与无缝切换。

---

## 模块拓扑与工程结构

```
├── app/                           # 主入口：MainActivity (Compose), DshApplication, 资产与资源
├── core/
│   ├── model/                     # 核心领域模型 (HostDescriptor, DshSlotRegistry, ChatMessage, SessionInfo)
│   ├── protocol/                  # RPC 协议层 (ConnectionStateMachine, HostAuthenticator, RemoteClient)
│   ├── network/                   # OkHttp 底座与拦截器
│   ├── data/                      # Room 2.7 本地只读缓存数据库 (6 张持久化表与 DAO)
│   ├── designsystem/              # Material 3 设计系统调色板 (DshTheme, DeepSeek Blue)
│   └── ui/                        # 原子可复用 Compose 状态组件
├── feature/
│   ├── chat/                      # 现代消息流、思考折叠面板、悬浮药丸输入框、ComposerDockBar Pills
│   ├── sessions/                  # 现代化侧滑抽屉 (ModernNavigationDrawerContent) 与会话管理
│   ├── files/                     # 真实工作区解析器 (WorkspaceManager) 与原生文件浏览器
│   ├── hosts/                     # 多主机管理 (HostsScreen) 与扫码配对器 (RemotePairingManager)
│   ├── approval/                  # 系统与高危操作审批中心 (ApprovalScreen, Pending->Approve 状态机)
│   ├── settings/                  # 偏好与系统设置 (SettingsScreen, 容器休眠、安全、备份导出)
│   └── runtime/                   # 本地 Linux 容器生命周期管控界面
├── plugin/
│   ├── runtime-web/               # 安全加固的 Web 岛容器 (WebIslandContainer, 独立进程沙箱)
│   ├── surface/                   # 内置 Surface 适配层 (SurfaceCatalogStore, SurfaceSheet, PluginOverlayContainer)
│   └── mobile-ui/                 # SDUI 声明式原生渲染器 (SduiPageRenderer, SduiValidator 预算门禁)
├── runtime/
│   ├── host/                      # 本地运行管理器 (DefaultHostRuntimeManager, ProotCommandBuilder, RootfsInstaller)
│   └── bridge/                    # 设备能力桥 (BridgeServer 127.0.0.1 JSON-RPC 2.0, NativeScheduler)
├── tooling/
│   ├── codegen/                   # Remote 描述符到 Kotlin 代码生成器
│   ├── contract/                  # 会话流量与契约回放器 (ContractReplayer)
│   └── frontend-patch/            # 前端补丁自动化注入器 (FrontendPatcher)
├── fixtures/                      # 脱敏契约回放夹具
└── docs/                          # 设计文档与 ADR
```

---

## 构建与测试验证

### 编译调试安装包 (Debug APK)
```bash
./gradlew :app:assembleDebug
```
产物位置：`app/build/outputs/apk/debug/app-debug.apk`

### 运行全量单元测试与工具链测试
```bash
./gradlew test
```
覆盖模块：
- `:core:protocol`（连接状态机 Generation 丢弃、独立 CookieJar 隔离）
- `:runtime:host`（Proot 命令组装、输出流端口解析、SHA-256 镜像校验、脱敏备份导出）
- `:runtime:bridge`（本地 JSON-RPC 方法分发与未知方法拦截）
- `:plugin:surface`（26 个 Slot 到原生 Sheet / 浮层的放置决策）
- `:plugin:mobile-ui`（SDUI 深度、节点数与体积门禁校验）
- `:feature:files`（真实公有工作区目录创建与遍历）
- `:feature:hosts`（二维码扫码 Payload 解析与过期检测）
- `:tooling:contract`（契约代码生成、HTML 补丁自动化注入、夹具校验）

---

## 首次启动引导（Onboarding）

首次安装启动时展示与桌面端一致的 4 步引导，完成（或跳过）后写入
`dsh_onboarding` SharedPreferences，之后启动直接进入对话流：

1. **欢迎使用**：介绍文案 + 产品截图（浅色/深色、中/英四套资源），主按钮「开始设置」；
2. **准备可用额度**：按余额判断展示「继续 + 去充值」或「去充值 + 下一步」，
   跳过时弹出「暂时跳过充值？」确认；
3. **你希望 DeepSeek Harness 帮你做什么？**：办公与创作 / 代码与开发两张可选卡片
   （选中后替换为对应文件类型图标组并显示流光渐变描边），仅选办公与创作时直接完成；
4. **你希望看到多少工作过程？**：聚焦结果 / 关键细节 / 完整过程三张单选卡片，
   选中后「进入应用」完成引导。

文案、图标与流转逻辑逐字对齐 `ui-settings-account` 的 `DesktopOnboarding`，仅将
桌面横向布局适配为手机纵向布局（单列卡片、全宽按钮、安全区留白、内容可滚动并垂直居中）。
完成后的选择会写回设置：工作步骤展示（`transcriptView`）与「显示代码工作视图」。

---

## 弹层与菜单（面板系统）

所有下拉菜单、上下文菜单、底部面板与确认对话框统一使用 `core:designsystem` 的面板基元
（`DshPanels.kt` + `DshPanelTokens.kt`），数值直接对齐 Web 客户端的
`ui-primitives`（`Menu.module.css` / `MenuSurface.module.css` / `Modal.module.css`）与
`ui-theme` 设计令牌：

- **菜单卡片**：16dp 圆角（紧凑 12dp）、4dp 内边距、最小 144dp / 最大 360dp 宽、
  `menuSurfaceFill` 材质、0.5dp `border-l1` 描边、柔和阴影；
- **菜单行**：34dp 最小高度、8dp/6dp 内边距、12dp 圆角、6dp 图标间距、13sp/20 行文字、
  14dp 图标、14dp 尾部对勾、11sp/16 分组标题、0.5dp 分隔线；
- **确认对话框**：`Modal` 规格——28dp 圆角、`min(380dp, 屏宽-48dp)`、
  22/14/12/24 头部内边距、16sp/24 Medium 标题、28dp 关闭位 + 14dp 图标、
  14sp/22 描述、20dp 内容间距、右对齐 8dp 间距底部按钮、24dp 底部留白；
- **风险确认**：18dp 警告图标 + 16dp 方形勾选框，取消按钮最小 72dp、主按钮最小 136dp 且
  勾选后才可用；**权限菜单**的 EXP 标记为 8sp/10 Medium 上标，行内图标 14dp；
  **模型菜单**最小 240dp、导航行 13sp/20、12dp 右箭头；
- **输入栏触发器**：模型 / 权限药丸按 trigger——28dp 高、8dp 圆角、左 8dp 右 4dp 内距、
  4dp 间距、13sp/20 二级色（权限 500 / 模型 400）、推理等级 13sp/20 caption 色、
  14dp 图标与下箭头，按压仅用 hover 填充、无 Material 涟漪；
- **底部面板**：菜单材质 + 16dp 顶部圆角 + 4dp 内缩，内容可滚动；
- 深色模式使用暗色令牌（`#2C2C2E` 对话框层、`menuSurfaceFill`、`labelPrimary` 等）。

### 页面（插件 / 自动化任务 / 任务看板）

三个抽屉入口已接通并统一为页面规格（`app/src/main/kotlin/com/deepseek/dsh/`）：

- **插件页**（`PluginsScreen.kt`，对齐 `ui-plugin-manager`）：20/28 500 标题 +
  13/20 二级引导语、48dp/40dp 细线图标框卡片、14/20 500 卡片标题、13/18 三级说明、
  11.5/16 等宽 slot 说明、sm 按钮；与上游一致，仅呈现「已安装插件」分组，
  不提供不存在的「发现 / 插件市场」页签；
  - 页头为官方刷新按钮 + 拆分式「添加插件」主按钮（右侧 32dp 下拉）：下拉两项为
    「安装第三方插件」（打开官方安装 Modal）与「让 Agent 创建插件」；
  - 点击插件卡片进入官方详情页：面包屑「‹ 插件」、48dp 图标 + 卸载危险描边按钮 + 官方开关、
    20/28 标题 + 版本胶囊、等宽包名、一句话说明、「包含的组件」行
    （40dp 图标框 + 名称/说明/模块 + 等待依赖状态点 + 开关）、
    「来源信息」两行（代码来源 / 当前版本，44dp 行高 + `border-l2` 下划线）；
    卸载会从 SurfaceCatalogStore 移除插件并返回列表；
- **自动化任务页**（`AutomationTasksScreen.kt`，对齐 `ui-schedule` TaskManagerPage）：
  20/28 标题、「已启用 / 已结束」SegmentedTabs、40dp 搜索框、
  空态文案与 32dp「新建自动化任务」主按钮（Host 数据接入后可直接渲染任务行）；
- **任务看板页**（`TaskBoardScreen.kt`，对齐 Agent Team 只读任务看板）：
  20/28 标题、待处理/进行中/已完成分组（8dp 状态点 + 14/22 500 标题 + 12/18 计数）、
  0.5dp `border-l2` 任务卡、空态「暂无任务」与只读说明。

插件安装对话框同步使用 Modal + 40dp 输入框 + 整宽 40dp 主按钮。

### 对话主页（phone 适配后的细节对齐）

- **顶栏**：0.5dp `border-l3` 底部分隔线，标题 16sp/24 Medium（完整显示、尾部省略），
  左侧菜单键 36dp `bg-module-platform` 圆钮，右侧图标 18dp 二级色；
- **Composer 卡片**：28dp 圆角、`input-major`（浅色白 / 深色 `#2C2C2E`）底、
  0.5dp `border-l2` 描边 + soft elevation；输入 14sp/22 一级色，占位符 13sp/20 caption 色；
  行内 28dp 圆形加号（`bg-module-platform` + 14dp 图标）、28dp trigger 药丸、
  34dp 蓝色发送圆钮，间距 10–12dp、无 Material 涟漪；
- **Hero**：56dp 鱼标、标题 26sp/32 Medium、副标题 13sp/20 二级色、
  快捷建议胶囊 34dp 高 / R12 / `bg-module-platform` / 13sp-20 二级色。

Android 窗口无法对弹层背后的内容做 backdrop blur，因此菜单/面板把半透明材质
合成到基础层上（等效于材质的扁平化结果）以保证可读性；对话框使用
`bg-mask-1` 遮罩并关闭系统默认 dim。

---

## 模拟器实机测试

已在 Android 模拟器（`Pixel_10_Pro`, API 35+）完成实机跑通验证：
1. **启动与主界面**：冷启动进入纯净对话流，左侧滑出式抽屉流畅拉起；
2. **输入与 Pills 交互**：药丸输入栏内部集成 `DeepSeek-V3` 模型选择与推理等级切换（快速/深度思考/超强推理），下方横向滚动呈现 `42.5 tok/s` 与 `上下文 12% · 缓存 85%`；
3. **真实文件工作区**：直连 `/storage/emulated/0/Documents/DSH-Workspace`，生成的文本产物系统直接可见；
4. **插件与悬浮窗**：点击「表情包插件」拉起原生 BottomSheet，点击「桌宠与系统状态」进入可拖拽与最小化圆形气泡；
5. **审批中心与设置**：高危工具拦截与二次确认流转顺畅，用户配置与诊断包导出无缝。

---

## Rootfs 运行时接入

### 安装链路

`RootfsInstaller` 已实现真实流水线（不再只是打日志）：

1. 读取 `app/src/main/assets/rootfs-manifest.json`（发布流水线写入 `downloadUrl` / `sha256` / `archiveSizeBytes`）；
2. 优先使用 `files/cache/rootfs-local.tar.gz`（无网调试 / `adb push`），否则按 manifest 下载；
3. SHA-256 校验（有下载 / 有 checksum 时）→ tar.gz 安全解包（拒绝 `../` 路径穿越、支持符号链接与可执行位）；
4. 解包到 staging 后原子替换 `files/rootfs`，创建 `opt/dsh`、`root/.dsh`；
5. 写入 `files/runtime/rootfs-installed.json` 安装标记；运行控制页显示进度/错误。

### 运行控制页

抽屉 →「运行控制」：

- 「安装/重置 Rootfs」始终可用，失败原因会显示在页面（如校验失败、manifest 未配置）；
- 「启动 DSH 容器」会先检查 `files/rootfs` 与 `jniLibs/<abi>/libproot.so`，缺失时直接返回可读错误。

### 发布前需要补齐的产物

1. `rootfs-manifest.json` 写入真实下载地址与 SHA-256；
2. rootfs 归档需以根目录布局打包（包含 `etc/os-release`、`usr/bin/env`、`opt/dsh`）；
3. `libproot.so` 按 `arm64-v8a` / `x86_64` 放入 `app/src/main/jniLibs/<abi>/`（Android 10+ W^X 要求可执行文件必须来自 APK native 库目录）。

已在模拟器用本地测试归档验证：安装成功、标记落盘；启动按钮会正确提示缺少 `libproot.so`。

### 首次启动下载 rootfs（当前实现）

- Rootfs 现由独立的轻量仓库与 GitHub Actions 统一管理与发布：[`deepseek-harness-mobile-rootfs`](https://github.com/EricFL1998/deepseek-harness-mobile-rootfs)；
- APK 不再内置 rootfs（体积保持约 20MB）；`rootfs-manifest.json` 的 `downloadUrls` / `downloadSha256` 按 ABI 配置下载地址与校验值，直接拉取 `deepseek-harness-mobile-rootfs` 自动化发布的 Release 资产；
- 启动顺序：**rootfs 准备页 → onboarding → 主界面**，未下载/解压完成前不会进入 onboarding；失败可在准备页重试；
- `RootfsInstaller` 支持下载进度、SHA-256 校验、tar.gz 安全解包（含硬链接可执行位恢复）；
- proot 已替换为 `build-proot-android` 的 Android NDK 构建（含独立 loader），放在 `jniLibs/{x86_64,arm64-v8a}/libproot.so` + `libproot-loader.so`，通过 `PROOT_LOADER` 启动；已能拉起 Node；
- 当前剩余问题：DSH 启动时 profile bundle（`@deepseek-ai/dsh-base` / `dsh-web-app`）在 proot 内解析失败，容器内同一 rootfs 正常，需继续修该解析路径。




