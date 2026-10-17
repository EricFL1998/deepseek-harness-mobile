# DSH Android 手机端工程

> 基于 **《DSH 手机端 · 最终设计文档 v4.0》**（2026-10-17）落地实现的完整 DeepSeek Harness 手机端客户端。

---

## 核心设计决策 (v4.0 基线)

1. **Host 运行在手机本地 Linux 用户空间**：基于 Compact Debian slim (glibc) + proot/bxroot + Node 24 + 真实 DSH，插件与 Agent 无需任何改动。
2. **核心 UI 原生化（Compose + Material 3）**：会话、聊天消息流、审批、设置、文件均由纯原生 Compose 承载；WebView 仅用于运行插件与重型组件（Web 岛）。
3. **真实文件系统接入（工作区 = 手机目录）**：默认工作区直绑手机公有共享文件夹（`/storage/emulated/0/Documents/DSH-Workspace`），在 Linux 容器内挂载为 `/workspace`，系统自带文件管理器直接可见，产物无需导出。
4. **内置 Surface 适配层（无伴生插件）**：将 `native-surface.js` 补丁内建于前端，自动拦截并调度 `conversation.composer.dock`、`shell.overlay`、`settings.*` 等官方全部 26 个核心 Slot，保障第三方插件生态 100% 正常运行。
5. **SDUI 声明式渲染器**：支持未来插件以 JSON 声明移动端布局（`dsh.mobile`），手机端以零 WebView 的原生组件树进行高性能渲染。
6. **多主机支持**：支持本地 Linux 用户空间 Host 与远程私有网关 Host 混合管理与无缝切换。

---

## 模块拓扑与工程结构

```
dsh-android/
├── app/                           # 主入口：MainActivity (Compose), DshApplication, 资产与资源
├── core/
│   ├── model/                     # 核心领域模型 (HostDescriptor, DshSlotRegistry, ChatMessage, SessionInfo)
│   ├── protocol/                  # 官方 RPC 协议层 (ConnectionStateMachine, HostAuthenticator, OfficialRemoteClient)
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
│   ├── contract/                  # 官方会话流量与契约回放器 (ContractReplayer)
│   └── frontend-patch/            # 前端补丁自动化注入器 (FrontendPatcher)
├── fixtures/                      # 脱敏官方契约回放夹具
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
- `:plugin:surface`（官方 26 个 Slot 到原生 Sheet / 浮层的放置决策）
- `:plugin:mobile-ui`（SDUI 深度、节点数与体积门禁校验）
- `:feature:files`（真实公有工作区目录创建与遍历）
- `:feature:hosts`（二维码扫码 Payload 解析与过期检测）
- `:tooling:contract`（契约代码生成、HTML 补丁自动化注入、夹具校验）

---

## 模拟器实机测试

已在 Android 模拟器（`Pixel_10_Pro`, API 35+）完成实机跑通验证：
1. **启动与主界面**：冷启动进入纯净对话流，左侧滑出式抽屉流畅拉起；
2. **输入与 Pills 交互**：药丸输入栏内部集成 `DeepSeek-V3` 模型选择与推理等级切换（快速/深度思考/超强推理），下方横向滚动呈现 `42.5 tok/s` 与 `上下文 12% · 缓存 85%`；
3. **真实文件工作区**：直连 `/storage/emulated/0/Documents/DSH-Workspace`，生成的文本产物系统直接可见；
4. **插件与悬浮窗**：点击「表情包插件」拉起原生 BottomSheet，点击「桌宠与系统状态」进入可拖拽与最小化圆形气泡；
5. **审批中心与设置**：高危工具拦截与二次确认流转顺畅，用户配置与诊断包导出无缝。
