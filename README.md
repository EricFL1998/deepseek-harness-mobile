# DSH 手机端（完整版 DeepSeek Harness）

[English](README.en.md) · 简体中文

DSH 手机端不是“连到电脑的远程客户端”，也不是只保留聊天界面的精简壳。它把**完整版 DeepSeek Harness** 装进 Android 手机：真实的 DSH Host、Agent 循环、工具运行时与插件系统都在手机上本地运行，桌面端能做的事在这里同样能做。

## 核心

- **完整 DSH Host 在手机本地运行**：内置 Debian (glibc) + proot 用户空间与 Node 运行时，直接启动上游 DSH。会话、Agent、工具调用、审批、定时任务、Agent Team、插件加载全部由真实 Host 处理，不是重新实现的“手机版协议”。
- **插件生态原样兼容**：Host 侧插件、bundle、`client-slots` 注册与 `bridge/v1` 协议全部保留。需要 DOM 的重型界面跑在 Web 岛，核心界面由原生 Compose 渲染，插件不需要为手机重写业务逻辑。
- **伴生原生 UI**：APK 形式的伴随插件可以用 **DSH Native UI SDK** 以 Kotlin + Compose 编写真正的原生界面，并注册到聊天、设置、侧栏、输入栏等落点。
- **真实工作区**：默认绑定手机共享目录 `Documents/DSH-Workspace`，在容器内挂载为 `/workspace`；系统文件管理器直接可见，产物无需导出。
- **多 Host**：本地 Host 与远程私有网关 Host 并存、随时切换。

## 功能概览

| 能力 | 说明 |
| --- | --- |
| 对话与 Agent | 完整 DSH Agent 会话、流式消息、思考折叠、工具调用与审批；错误原样展示 |
| 工作过程展示 | 聚焦结果 / 关键细节 / 完整过程，与桌面端 `transcriptView` 语义一致 |
| 模型与推理等级 | 模型目录、provider 凭据、推理档位全部来自 Host `modelCatalog`（含本地缓存） |
| 文件与交付物 | 原生文件浏览、文档预览、交付物列表，直接读写手机工作区 |
| 右侧栏（Drawer） | 官方 dock 样式：文件 / 终端 / 浏览器 / 计划 / 交付，开始页胶囊与插件页面均可扩展 |
| 自动化与团队 | 定时任务、Agent Team 任务看板、终端、浏览器入口 |
| 插件 | 安装 / 启停 / 卸载、插件详情、Web 岛，以及原生插件 APK 的发现与加载 |
| 账号与引导 | 官方欢迎页（登录 / 添加 API Key）、首次引导、模型登录态与凭据管理 |
| 多主机 | 本地 Host + 远程 Host 配对、切换与独立凭据 |

## 插件与伴生原生 UI

DSH 手机端保留两层插件能力：

1. **Host 插件（与桌面同一套）**  
   插件仍运行在真实 DSH Host 里，通过官方 slot 注册界面。手机端把核心 slot 映射为原生 Compose 组件，需要 Web 的界面跑在 Web 岛；插件代码不需要分叉。

2. **伴生原生 UI 插件（Native UI SDK）**  
   以 APK 形式随插件分发的原生界面，使用 **DSH Native UI SDK** 编写：
   - Kotlin + Jetpack Compose，直接使用 DSH 设计令牌与控件；
   - 通过 `NativeSlot` 注册到 `RIGHT_PANEL`、`RIGHT_PANEL_GUIDE_ENTRY`、`SETTINGS_SHEET`、`COMPOSER_DOCK` 等落点；
   - 与 Host 的 RPC、主题、模型数据通过 SDK 提供的 Host API 交互。

   SDK 源码、接入方式与示例：  
   **https://github.com/EricFL1998/dsh-mobile-plugin-sdk**

## 工程结构

```
app/                 应用入口、导航、设置、右侧栏等原生界面
core/                模型、协议、bridge、Room 缓存、设计系统
feature/             聊天、会话、文件、审批、设置、运行时
plugin/              Web 岛、Surface 适配层、原生插件宿主（NativeSlot / Native UI SDK 接入）
runtime/             本地 proot Host 生命周期、bridge 设备能力
tooling/             协议代码生成、契约回放、前端补丁
```

## 构建

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 21、Android SDK 35。Native UI SDK 通过 Gradle 组合构建（`includeBuild`）引用同级目录的 SDK 仓库，可用 `-PdshSdkRepo=<path>` 覆盖路径。

```bash
./gradlew test
```

## 相关仓库

- 本仓库（Android App）：https://github.com/EricFL1998/deepseek-harness-mobile
- 伴生原生 UI SDK：https://github.com/EricFL1998/dsh-mobile-plugin-sdk
- 上游 DeepSeek Harness：https://github.com/deepseek-ai/dsh
