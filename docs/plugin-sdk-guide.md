# DSH 原生插件 SDK 开发者文档

> 状态：**已自证（Demo 样例测试、扫描与打包通过）**。本文是原生插件（伴侣 APK）的完整开发指南。
> 适用对象：想为 DSH 手机 App 提供原生（Kotlin/Compose）UI 的插件开发者。
> 关联文档：[JS UI 转原生 UI 实战指南](plugin-conversion-guide.md)、[插件侧 SDK 规范](native-plugin-sdk.md)、[App 侧宿主接口](native-plugin-host.md)、[移动端 UI 架构选型](mobile-ui-architecture.md)、[Demo 插件（JS 半边）](../samples/demo-js/README.md)、[插件 UI 清单示例](plugin-ui-inventory-example.md)、[文档站案例](index.html#case)。
> SDK 坐标：`com.deepseek.dsh:dsh-native-sdk` · ABI 版本 `1`。
> 更新：2026-10-22（全部示例切换为 Demo 插件：`samples/demo-js` + `samples/demo-native`）。
>
> **自证**：`samples/demo-js` 的 RPC / 动作测试 7/7 通过；`samples/demo-native` 可编译打包；
> `tools/plugin_ui_scanner.py --check` 对 Demo 插件输出 `plugin UI coverage check OK`
> （2 个注册落点均已映射 NativeSlot、宿主就绪、伴生已实现）。授权类插件的边界实现只作旁注
> （[samples/kimi-native](../samples/kimi-native)），本文以 Demo 插件为例。

## 目录

- [0. 这是什么](#0-这是什么)
- [1. 五分钟上手](#1-五分钟上手)
- [2. 核心概念](#2-核心概念)
- [3. 包结构与 manifest 契约](#3-包结构与-manifest-契约)
- [4. 插件入口与渲染器](#4-插件入口与渲染器)
- [5. 能力桥 PluginHostApi](#5-能力桥-pluginhostapi)
- [6. GenUI：渲染 dsh-ui spec](#6-genui渲染-dsh-ui-spec)
- [7. 落点：把内容放进 App 的界面](#7-落点把内容放进-app-的界面)
- [8. tool call 渲染钩子](#8-tool-call-渲染钩子)
- [9. 页面入口：设置页与插件页](#9-页面入口设置页与插件页)
- [10. 主题契约](#10-主题契约)
- [11. 与 Host 侧 JS 的通信](#11-与-host-侧-js-的通信)
- [12. 降级契约](#12-降级契约)
- [13. 发布与商店](#13-发布与商店)
- [14. 完整示例：Demo 插件（JS + 原生）](#14-完整示例demo-插件js--原生)
- [15. 组件目录（dsh-ui spec 参考）](#15-组件目录dsh-ui-spec-参考)

---

## 0. 这是什么

DSH 手机 App 开放一套**原生 UI 插件**体系：你为已有（或新的）JS 插件附带一个 **Kotlin/Compose 渲染器**，打包成独立 APK 经商店分发。装上后，插件 UI 从 WebView 升级为原生体验——更快的渲染、更跟手的交互、与系统一致的主题。

**核心承诺**：

- **逻辑层零重写**：你的 Host 侧逻辑（`lib/index.js`：RPC、状态、事件）原封不动继续跑。原生 APK 只写「前端逻辑」——画 spec、回传意图；
- **通信零新协议**：原生端与 Host 之间走现有 bridge 的 `dispatch` op 与 `client/refresh` 事件；
- **降级永远可用**：用户不装你的 APK，插件走 WebView 完整可用；装了则增强。

**一句话**：原生 APK 是 Host 逻辑的第二张脸。逻辑永远在 Host。

本文所有示例都来自仓库里的 **Demo 插件**：JS 半边是 npm 包 [`samples/demo-js`](../samples/demo-js)（`dsh-plugin-demo`），原生半边是伴侣 APK [`samples/demo-native`](../samples/demo-native)（`demo.native-ui` / `com.dsh.mobile.dsh_plugin_demo`）。它没有账号、网络与凭证，只演示 SDK 的接入形状；真实授权类插件不在本文展开。

---

## 1. 五分钟上手

```bash
# 1. 依赖 SDK
# 在你的 Android 工程 build.gradle.kts 里：
dependencies {
    implementation("com.deepseek.dsh:dsh-native-sdk:1.0.0")
}

# 2. 写一个渲染器（或直接用 SpecRenderer 画 dsh-ui spec）
# 3. 在 Service 填 manifest 契约与 jsPackage 配对
# 4. 打包成 APK，提交商店（或 CI 代编）
```

最小可用插件见 [§14 完整示例：Demo 插件（JS + 原生）](#14-完整示例demo-插件js--原生)。想先跑通仓库里的 Demo：

```bash
# RPC / 动作 / 状态隔离（无需 Host、Android、网络）
node --test samples/demo-js/test/rpc.test.mjs

# 扫描器：JS 注册的 2 个落点都有 NativeSlot 映射、宿主与伴生实现
python tools/plugin_ui_scanner.py \
  --plugin samples/demo-js \
  --companion samples/demo-native/src/main/kotlin \
  --check
```

---

## 2. 核心概念

| 概念 | 说明 |
| --- | --- |
| **插件（JS 半边）** | 你已有的 npm/GitHub 插件，`lib/index.js` 跑在 Host。逻辑、状态、RPC 都在这。 |
| **伴侣 APK（原生半边）** | 一个独立 Android 应用，含你的 Compose 渲染器。只画 UI、回传意图。 |
| **pluginId** | 伴生 APK 自身身份与页面注册归属，如 `demo.native-ui`；不必等于 JS 包名。 |
| **jsPackage** | 与 Host 中 JS 插件配对的包名，如 `dsh-plugin-demo`；在 manifest 的 `dsh.plugin.jsPackage` 声明。 |
| **componentId** | bridge 的组件身份，通常为 `<jsPackage>#<slot 内 id>`；决定原生页接管哪个 Web 页面，如 `dsh-plugin-demo#demo`。 |
| **spec** | Host 推给原生端的 JSON 状态快照（纯展示数据）。你的渲染器消费它；Demo 的页面则直接经 RPC 读取。 |
| **落点（NativeSlot）** | App 界面里插件能挂内容的位置（dock、输入框旁、工具卡片、设置页…）。 |
| **能力桥（PluginHostApi）** | 插件唯一摸得到的 App 表面：回传事件、开链接、读主题、存数据。 |

---

## 3. 包结构与 manifest 契约

### 3.1 工程结构

```
samples/demo-js/                 ← JS 插件（npm 包 dsh-plugin-demo，0.1.0）
├── package.json                 ← npm 身份 + dsh.client / dsh.native 声明
├── cordis.patch.yml             ← bundle 自激活：挂载 lib/index.js
├── lib/
│   ├── index.js                 ← 插件入口：name / apply（无顶层 inject）
│   ├── constants.js             ← RPC 前缀、componentId、settings ns（三方约定）
│   ├── state.js                 ← 内存状态仓（计数 42、上次动作、更新时间）
│   ├── rpc.js                   ← /api/demo.* 路由 + 请求/响应信封
│   ├── actions.js               ← mobileUi 动作：bump / reset
│   ├── catalog.js               ← demo.catalog 返回的注册清单
│   └── client.js                ← Web 回退：settings.section + composer.dock
└── test/rpc.test.mjs            ← node:test：信封、路由、动作、状态隔离

samples/demo-native/             ← 伴生 APK 工程
├── build.gradle.kts             ← implementation("com.deepseek.dsh:dsh-native-sdk:1.0.0")
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/com/dsh/mobile/dsh_plugin_demo/
        ├── DemoPluginService.kt     ← Service 绑定 + 静态工厂 plugin()
        ├── DemoNativePlugin.kt      ← DshUiPlugin：descriptor、渲染器、落点注册
        ├── DemoSettingsPage.kt      ← PageContent：设置页整页（RPC 驱动）
        ├── DemoDockContribution.kt  ← COMPOSER_DOCK 胶囊 + PluginPopover
        ├── DemoSlotContribution.kt  ← 其余 NativeSlot 的演示贡献
        └── DemoRpc.kt               ← 信封解析 + demoCall
```

### 3.2 AndroidManifest.xml（基础契约 + JS 配对）

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.dsh.mobile.dsh_plugin_demo">

    <application>
        <!-- 入口服务：发现、ABI 校验、静态工厂加载及 JS 插件配对都在 service 级声明 -->
        <service
            android:name=".DemoPluginService"
            android:exported="true"
            android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">
            <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
            <meta-data android:name="dsh.plugin.minAppVersion" android:value="12" />
            <meta-data android:name="dsh.plugin.version" android:value="1" />
            <meta-data android:name="dsh.plugin.id" android:value="demo.native-ui" />
            <meta-data android:name="dsh.plugin.name" android:value="Demo 插件" />
            <!-- 与 samples/demo-js 的 package.json name 一致；App 据此配对并连带安装 JS 插件。 -->
            <meta-data android:name="dsh.plugin.jsPackage" android:value="dsh-plugin-demo" />
            <intent-filter>
                <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

**规则**：
- `dsh.plugin.id` 一经发布不可更改——它是签名钉死与商店身份的锚；它不必等于 JS 包名（Demo 的 `demo.native-ui` ≠ `dsh-plugin-demo`）；
- `dsh.plugin.jsPackage` 才是与 Host 插件的配对键；`PageEntry.id` 决定接管哪个 `settings.section`；
- 所有插件 meta-data 都放在 **Service 级**，不是 `<application>` 级；
- 插件 APK **不申请任何系统权限**（无 `<uses-permission>`）；需要能力走能力桥声明；
- 仅当插件确实需要系统回跳（例如真实授权类插件走深链）才额外声明 Activity：scheme 固定 `dsh-plugin`、host 必须是 `pluginId`。Demo 不需要；这类边界可参考 [samples/kimi-native](../samples/kimi-native)。

---

## 4. 插件入口与渲染器

### 4.1 插件入口 `DshUiPlugin`

```kotlin
class DemoNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "demo.native-ui",                 // 与 manifest 的 dsh.plugin.id 一致
        name = "Demo 插件",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,       // 按需声明；未声明的能力调用即被拒
            PluginCapabilities.STORAGE,
            PluginCapabilities.PAGE_ENTRY,     // 注册 PageContent 需要
        ),
    )
    override fun createRenderer(): ComponentRenderer = DemoRenderer()
}
```

### 4.2 Service（照抄模板）

```kotlin
class DemoPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(DemoNativePlugin())

    companion object {
        /** App 在自身进程加载页面时使用的入口；仅写 onBind 不足以注册原生页面。 */
        @JvmStatic
        fun plugin(): DshUiPlugin = DemoNativePlugin()
    }
}
```

### 4.3 渲染器 `ComponentRenderer`

```kotlin
class DemoRenderer : ComponentRenderer {
    /** 声明你能渲染的 spec 类型；命中才走原生，否则降级 Web 界面 */
    override val supportedTypes = setOf("demo.card")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        // 画 spec。交互经 host.dispatchEvent 回传 Host。
    }
}
```

**纪律**：渲染器是纯前端——不持网络、不缓存凭证、不做业务逻辑。这些都留在 Host 侧 JS。

---

## 5. 能力桥 PluginHostApi

插件唯一摸得到的 App 表面。每项能力调用前，宿主校验你的 `capabilities` 声明；未声明即拒。

| 方法 | 能力 | 说明 |
| --- | --- | --- |
| `dispatchEvent(componentId, event, payload)` | —（核心通道） | 回传用户意图给 Host 侧 JS（走 bridge dispatch） |
| `openUrl(url)` | `openUrl` | 域白名单内走 Custom Tab，其余系统浏览器确认 |
| `currentTheme()` | — | 主题只读快照（深色/字体缩放联动） |
| `requestCapability(cap)` | — | 申请能力；未声明或用户关闭返回 false |
| `storage()` | `storage` | 本插件独立命名空间键值存储 |

**能力白名单（v1）**：`openUrl` · `storage` · `notify` · `share` · `clipboard` · `workspace` · `toolRender` · `pageEntry`（常量见 `PluginCapabilities`）。能力只增不改；破坏性变更升 ABI 版本。

Demo 里的一次回传长这样（`DemoSettingsPage.kt` 的「服务端 +1」）：

```kotlin
val payload = buildJsonObject {
    put("actionId", "bump")
    put("by", 1)
}
host.dispatchEvent("dsh-plugin-demo#demo", "submit", payload)
```

---

## 6. GenUI：渲染 dsh-ui spec

**GenUI 不是组件库，而是渲染能力**：模型运行时产出一份 dsh-ui spec（JSON），你用 `SpecRenderer` 把它画成原生 UI——不需要自己写 Compose 布局。

```kotlin
import com.deepseek.dsh.plugin.nativesdk.render.SpecRenderer
import com.deepseek.dsh.plugin.nativesdk.render.DshUiSpecParser

@Composable
fun MyContent(specJson: JsonObject, host: PluginHostApi) {
    val spec = DshUiSpecParser.fromJson(specJson)
    val theme = host.currentTheme()
    SpecRenderer.Render(spec = spec, theme = theme, host = host)
}
```

`SpecRenderer.Render` 会按 spec 里的 `items` 逐个渲染组件（卡片、列表、表格、图表、步骤条…），主题取自 `theme`，交互经 `host` 回传。不支持的引擎组件（mermaid/3D 等）自动降级为「请在完整 Web 界面查看」占位——绝不把原始 JSON 暴露出来，也绝不崩溃。

仓库里两条路子各有一个样例：`samples/genui-native` 用 `SpecRenderer` 画 `render_ui` 工具产出的 spec；`samples/demo-native` 的 `DemoRenderer` 则是最小 `ComponentRenderer`（不解析 spec，只演示挂载与主题契约）。

**典型场景**：GenUI 插件的 `render_ui` 工具——模型调它时入参里就是完整 spec，你的工具渲染器（见 §8）直接 `SpecRenderer.Render` 画出来。

---

## 7. 落点：把内容放进 App 的界面

落点（`NativeSlot`）是 App 界面里插件能挂内容的位置。Web 侧的每个可插入槽位，都由 App 内置的 `NativeSlotMapping` 声明其对应的 `NativeSlot`；插件只需对同一个落点注册原生贡献，其余位置由 App 提供容器。

### 7.1 落点清单

Demo 伴生覆盖全部 22 个 `NativeSlot` 条目（清单见 `DemoNativePlugin.kt` 的 `DemoSlots`）。其中 **21 个注入真实界面**；`WEB_ISLAND_FULL` 虽有宿主，但它是整岛 Web 回退容器，Demo 不注入（否则会整屏覆盖 App），只在 Demo 页展示说明：

| 落点 | 位置 | 对应 Web 槽位 | 宿主 |
| --- | --- | --- | --- |
| `SETTINGS_SHEET` | 设置页 | settings.section / plugins.tab | ✅ 就绪 |
| `SETTINGS_PROVIDER_CARD` | Provider 卡片 | settings.models.provider-card | ✅ 就绪 |
| `SIDEBAR_PANEL` | 侧栏面板 | sidebar.* | ✅ 就绪 |
| `COMPOSER_DOCK` | 输入栏 dock | conversation.composer.dock / conversation.input.dock | ✅ 就绪 |
| `COMPOSER_DOCK_ACTIVITY` | dock activity | tok/s 条目 | ✅ 就绪 |
| `COMPOSER_DOCK_USAGE` | dock usage | 缓存命中条目 | ✅ 就绪 |
| `COMPOSER_DOCK_CONTEXT` | dock context | 上下文计量 | ✅ 就绪 |
| `COMPOSER_LEFT` | 输入栏左 | conversation.input.left | ✅ 就绪 |
| `COMPOSER_RIGHT` | 输入栏右 | conversation.input.right | ✅ 就绪 |
| `COMPOSER_MODEL` | 模型选择 | conversation.input.model | ✅ 就绪 |
| `COMPOSER_PERMISSION` | 权限选择 | conversation.input.permission | ✅ 就绪 |
| `COMPOSER_PLAN` | Plan 模式 | conversation.input.plan | ✅ 就绪 |
| `COMPOSER_ATTACHMENTS` | 附件入口 | conversation.input.attachments | ✅ 就绪 |
| `COMPOSER_BAR` | 整个 composer | conversation.composer.bar | ✅ 就绪 |
| `CONVERSATION_VIEW` | 会话视图 | conversation.view | ✅ 就绪 |
| `SESSION_HEADER_CORNER` | 会话标题角 | session.header.corner | ✅ 就绪 |
| `COMPOSER_OVERLAY` | 输入框浮层 | conversation.input.overlay | ✅ 就绪 |
| `PLUGIN_OVERLAY` | 插件浮层 | shell.overlay | ✅ 就绪 |
| `MESSAGE_ACTION` | 消息动作 | assistant-actions | ✅ 就绪 |
| `TOOL_VIEW_SHEET` | 工具详情 | tool.call.toolview | ✅ 就绪 |
| `CHAT_STREAM` | 聊天流 | chat.flow / chat.node | ✅ 就绪 |
| `WEB_ISLAND_FULL` | Web 岛 | 完整 Web 回退 | ✅ 宿主已就绪 · Demo 不注入 |

### 7.2 注册一个落点贡献

```kotlin
class DemoDockContribution : SlotContribution() {
    override val slot: NativeSlot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes: Set<String> = setOf("demo.dock")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // 在输入栏上方画 "Demo 42" 胶囊；点按用 PluginPopover 展开详情。
    }
}

// 在插件入口注册
class DemoNativePlugin : DshUiPlugin {
    init {
        PageRegistry.register(DemoSettingsPage())
        DemoSlots.entries
            .filter { it.hostReady && it.slot != NativeSlot.COMPOSER_DOCK }
            .forEach { SlotRegistry.register(DemoSlotContribution(it)) }
        SlotRegistry.register(DemoDockContribution())   // dock 单独展示 popover
    }
    // ...
}
```

App 在每个落点放了一个通用容器（`NativeSlotHost`），它查询 `SlotRegistry` 里该落点的贡献并渲染。**显示什么完全由你决定**；没有插件注册时容器不占空间。

### 7.3 Demo 的两个 Web 注册落点

Demo 的 JS 侧只注册 2 个落点，它们同时是 App 感知组件身份的来源；扫描器对这两个落点的输出是「有 NativeSlot 映射、有宿主、伴生已实现」：

| JS 注册 | id | `componentId` | 原生落点 | 宿主 | 原生实现 |
| --- | --- | --- | --- | --- | --- |
| `settings.section` | `demo` | `dsh-plugin-demo#demo` | `SETTINGS_SHEET` | 是 | 是 |
| `conversation.composer.dock` | `demo-dock` | `dsh-plugin-demo#demo-dock` | `COMPOSER_DOCK` | 是 | 是 |

```bash
$ python tools/plugin_ui_scanner.py --plugin samples/demo-js \
    --companion samples/demo-native/src/main/kotlin --check
plugin UI coverage check OK
```

扫描结果也存档在 [插件 UI 清单示例](plugin-ui-inventory-example.md)。

---

## 8. tool call 渲染钩子

接管某个工具调用结果的原生渲染。

```kotlin
class RenderUiToolRenderer : ToolRenderer {
    /** 你接管的工具名（精确匹配） */
    override val toolNames = setOf("render_ui")

    @Composable
    override fun Render(call: ToolCallContext, theme: PluginTheme, host: PluginHostApi) {
        // render_ui 的入参就是完整 dsh-ui spec
        val spec = DshUiSpecParser.fromToolArgs(call.args)
        if (spec != null) {
            SpecRenderer.Render(spec = spec, theme = theme, host = host)
        }
    }
}

// 注册
ToolRenderRegistry.register(RenderUiToolRenderer())
```

模型调用 `render_ui` 后，App 在工具卡片处查询 `ToolRenderRegistry`；你的渲染器命中，就把该次调用画成原生 UI。没插件接管时 App 用默认工具卡片样式。可运行的完整实现见 [`samples/genui-native`](../samples/genui-native)。

---

## 9. 页面入口：设置页与插件页

在设置页/插件页挂一个原生页面。Demo 的设置页是 `PageContent`，`id = "demo"`：

```kotlin
class DemoSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "demo",
        title = "Demo 插件",
        subtitle = "JS 插件 + 原生伴生：RPC 与落点接入示例（虚构）",
        placement = PageEntry.Placement.SETTINGS,   // 或 PLUGINS
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        DemoSettingsContent(theme = theme, host = host, refreshEvents = null)
    }
}

PageRegistry.register(DemoSettingsPage())
```

用户点设置页/插件页里你的入口，App 用 `Content` 画整页。

### 9.1 设置页接管规则

`PageEntry.id` 必须与 JS 侧 `settings.section` 的注册 `id` 完全相同。实际组件身份是
`<jsPackage>#<PageEntry.id>`；伴生的 `pluginId` 与 JS 包名可以不同。只有静态工厂加载
成功并注册该页面后，App 才跳过 WebView；APK 仅被安装但未注册页面时仍走 Web。

Demo 的对应关系：`settings.section id = "demo"` + `PageEntry(id = "demo", placement = SETTINGS)`
→ `componentId = dsh-plugin-demo#demo`；伴生自己的 `pluginId` 是 `demo.native-ui`。

### 9.2 接收 Host 刷新与检查页面存活

有实时状态的页面可重载带 `refreshEvents` 的 `Content`，收到本组件事件后重新读取权威
RPC。异步回调更新页面前，先检查 `PageLifecycle.LocalIsActive.current`，避免用户已经
返回后仍写入旧页面状态。Demo 设置页的真实写法：

```kotlin
LaunchedEffect(refreshEvents) {
    refreshEvents?.collect { componentId ->
        if (componentId == DemoRpc.PAGE_COMPONENT_ID || componentId == DemoRpc.JS_PACKAGE) refresh()
    }
}
```

---

## 10. 主题契约

- 颜色/字号/形状**全部**取自 `host.currentTheme()` 的 token；
- **禁止**硬编码色值/字号——商店审核红线，也是 Web 与原生视觉一致的前提；
- 深色模式、系统字体缩放由 App 统一处理；快照变化触发重组，你不写深浅色分支；
- 触控目标 ≥44dp、无 hover 依赖。

---

## 11. 与 Host 侧 JS 的通信

完全复用 bridge 现成链路，零新协议。以 Demo 的「服务端 +1」为例：

```
你的原生端 host.dispatchEvent("dsh-plugin-demo#demo", "submit", { actionId: "bump" })
  → App bridge client 调 client-slots/dispatch
  → Host 事件总线 ui/dsh-plugin-demo#demo/submit
  → JS 插件 mobileUi 动作（demo-js/lib/actions.js）
  → 状态变化；bridge 排 client/refresh
  → App 收到刷新，重拉 spec 或由页面重读 RPC
  → 你的渲染器 Render() 重组
```

你只需保证：

1. **componentId 约定**：使用 Host 实际的 `<jsPackage>#<slot 内 id>`；伴生自身 `pluginId` 不一定等于这个包名。Demo 的两个身份是 `dsh-plugin-demo#demo`（设置页）与 `dsh-plugin-demo#demo-dock`（dock）；
2. **JS 侧推状态**：把原生端要画的数据放进 `client/refresh` 的 `patch`，保证 spec 是 JSON 安全的纯展示数据（无凭证、无函数、无循环引用）。

### 11.1 已有插件 RPC 的页面：`PluginRpcHost`

完整原生页面还可把 `host` 安全地转为 `PluginRpcHost`，直接调用 Host 已有的插件路由：

```kotlin
val rpc = host as? PluginRpcHost
rpc?.callRpc("demo.status", JsonObject(emptyMap())) { result ->
    // 校验 result.ok，取 value；payload 原样发送，不包 args。
}
```

`callRpc` 是回调式 API，不是 `suspend`。需要重新读取时递增 `refreshKey`，由
`LaunchedEffect(rpc, refreshKey)` 发起下一次调用。只调用 Host 已存在的方法，响应中的
`{ result: { ok, value | error } }` 先解信封再解析业务状态。Demo 用一个顶层扩展把回调包成
`suspend`（`DemoRpc.kt`），页面与落点贡献都能直接 `await`：

```kotlin
internal suspend fun PluginRpcHost.demoCall(
    method: String,
    payload: JsonObject = JsonObject(emptyMap()),
): JsonElement? = suspendCancellableCoroutine { continuation ->
    callRpc(method, payload) { result ->
        continuation.resume(result.getOrNull())
    }
}
```

Demo 对外提供 6 个端点（完整契约见 §14.3）：

| 端点 | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | 更新后的 status 快照 |
| `demo.reset` | `{}` | 重置后的 status 快照 |
| `demo.echo` | 任意 JSON | `{ received: <原样回显> }` |

### 11.2 复杂页面迁移参照

整页 RPC 读取、`dispatchEvent` 回传 + `refreshEvents` 重拉、`PluginPopover` 浮层、
全落点占位等完整实现，见：

- `samples/demo-native/src/main/kotlin/com/dsh/mobile/dsh_plugin_demo/DemoSettingsPage.kt`（设置页整页）；
- `samples/demo-native/src/main/kotlin/com/dsh/mobile/dsh_plugin_demo/DemoDockContribution.kt`（dock 胶囊 + popover）；
- `samples/demo-native/src/main/kotlin/com/dsh/mobile/dsh_plugin_demo/DemoSlotContribution.kt`（其余落点）。

不要只迁移卡片外壳：展开区、按钮动作、空态、错误态与降级路径都属于迁移范围。

---

## 12. 降级契约

原生层永远是增强，不是必需：

| 场景 | 行为 |
| --- | --- |
| 用户未装伴侣 APK | 插件走 WebView（完整界面），功能完整 |
| APK 已装但 ABI 不兼容 | 显示「插件需更新」，降级 WebView |
| `supportedTypes` 未命中 spec 类型 | 该组件降级，其余原生 |
| 渲染器抛异常 | 错误围栏捕获，显示占位块，不影响 App 与其他插件 |
| 用户卸载 APK | 即时摘除渲染位，回退 WebView |

**因此**：你的 JS 侧功能必须在纯 Web 下完整可用——不得假设原生端在场。Demo 的 Web 回退
就在 `samples/demo-js/lib/client.js`：它注册 `settings.section(id = "demo")` 与
`conversation.composer.dock(id = "demo-dock")`，用 `connection.rpc.call("/api", "demo.status")`
读同一份 Host 状态；不装 APK 时仍可用。

---

## 13. 发布与商店

### 13.1 两种构建方式

- **自己编**：`./gradlew assembleRelease` 出 APK；
- **CI 代编**：你只提交 Kotlin 源码到 GitHub，商店 CI 用钉死的 SDK/Compose/Kotlin 版本统一编译、统一签名登记——你本地甚至不需要装 Android SDK。

### 13.2 商店收录（GitHub tag 模式）

商店的实际内容走 **GitHub tag 索引**：给你的插件仓库挂上 `dsh-mobile-plugin` tag，商店就会自动把它收入索引。

1. 你的插件仓库（含 JS 插件 + 原生伴侣源码）打 tag `dsh-mobile-plugin`；
2. 商店的索引器扫描带该 tag 的仓库，读取 `dsh.native` 声明（pluginId/能力/版本）；
3. 通过审核后进商店索引，用户在「插件 → 插件商店」的推荐列表看到它；
4. 用户一键安装 → 系统安装器确认 → App 自动发现、验签、ABI 校验 → 出现在列表。

### 13.3 JS 侧声明原生伴侣（`package.json`）

Demo 的真实声明（`samples/demo-js/package.json`）：

```json
{
  "name": "dsh-plugin-demo",
  "version": "0.1.0",
  "dsh": {
    "native": {
      "pluginId": "demo.native-ui",
      "name": "Demo 插件",
      "apiVersion": 1,
      "minAppVersion": 12,
      "capabilities": ["openUrl", "storage"],
      "storeUrl": "https://github.com/EricFL1998/deepseek-harness-mobile"
    }
  }
}
```

bridge 的 `native-plugins` provider 把这个声明暴露给 App，商店页据此标注「原生增强」与兼容性。

### 13.4 签名与更新

- 商店登记你的 pluginId + 签名证书指纹（永久身份）；
- 更新时 APK versionCode 递增、签名不变即可；**签名变更视为新插件**（防掉包），商店拒绝同 pluginId 覆盖。

---

## 14. 完整示例：Demo 插件（JS + 原生）

一个真实的 npm 插件包 + 一个伴侣 APK，代码全部在仓库里，可以直接跑：

- JS 半边：[`samples/demo-js`](../samples/demo-js)（`dsh-plugin-demo` 0.1.0，入口 `lib/index.js`）；
- 原生半边：[`samples/demo-native`](../samples/demo-native)（`demo.native-ui`，APK 包名 `com.dsh.mobile.dsh_plugin_demo`）；
- 清单输出：[插件 UI 清单示例](plugin-ui-inventory-example.md)。

数据只存在 Host 进程内（计数初始 42），不访问网络、不读写账号、不持有任何凭证；Host 重启后回到初始值。

### 14.1 身份配对

| 位置 | 值 |
| --- | --- |
| JS 包名 | `dsh-plugin-demo`（0.1.0） |
| JS 入口 | `lib/index.js`（导出 `name` / `apply`） |
| 伴生 `pluginId`（manifest `dsh.plugin.id`） | `demo.native-ui` |
| 伴生 APK 包名 | `com.dsh.mobile.dsh_plugin_demo` |
| manifest `dsh.plugin.jsPackage` | `dsh-plugin-demo` |
| 设置页 Web 注册 id / 原生 `PageEntry.id` | `demo` |
| 设置页组件身份 `componentId` | `dsh-plugin-demo#demo` |
| dock Web 注册 id | `demo-dock` |
| dock 组件身份 `componentId` | `dsh-plugin-demo#demo-dock` |

设置页被原生接管的两个条件：

1. JS 侧注册 `settings.section` 且 `id = "demo"`；
2. 伴生 APK 注册了 `PageEntry(id = "demo", placement = SETTINGS)`。

两者组件身份都是 `dsh-plugin-demo#demo`，App 才会跳过 WebView。

### 14.2 JS 侧：入口、RPC 与动作

入口不声明顶层 `inject`，两个能力都用 `ctx.inject` 在服务出现时注册——这样 Host 组合里没有 web server 或桥插件时，Demo 仍能加载：

```js
// samples/demo-js/lib/index.js
export const name = 'dsh-plugin-demo';

export function apply(ctx) {
  const state = createDemoState();      // 每个插件实例一份状态
  registerDemoRpc(ctx, state);          // ctx.inject(['connection']) → /api/demo.*
  registerDemoActions(ctx, state);      // ctx.inject(['mobileUi']) → bump / reset
}
```

RPC 注册在 `connection` 服务上，`payload` 原样传递、不包 `args`：

```js
// samples/demo-js/lib/rpc.js（节选）
export function registerDemoRpc(ctx, state) {
  ctx.inject(['connection'], (connectionCtx) => {
    const connection = connectionCtx.get('connection');
    const register = connection?.fetch?.register;
    if (typeof register !== 'function') return;
    for (const endpoint of RPC_ENDPOINTS) {
      const method = `${RPC_PREFIX}${endpoint}`;      // demo.status …
      const route = rpcRoute(method, endpoint, state);
      connectionCtx.effect(
        () => register.call(connection.fetch, route),
        `dsh-plugin-demo: /api/${method}`,
      );
    }
  });
}
```

原生动作同样注册在组件身份上；bridge 命中后执行插件代码，再给手机排 `client/refresh`：

```js
// samples/demo-js/lib/actions.js（节选）
ctx.inject(['mobileUi'], (uiCtx) => {
  const ui = uiCtx.get('mobileUi');
  if (ui === undefined || typeof ui.registerAction !== 'function') return;
  for (const componentId of [PAGE_COMPONENT_ID, DOCK_COMPONENT_ID]) {
    for (const actionId of ['bump', 'reset']) {
      uiCtx.effect(
        () => ui.registerAction(componentId, actionId, (payload) => runAction(actionId, payload)),
        `dsh-plugin-demo: ${componentId}/${actionId}`,
      );
    }
  }
});
```

Web 回退（`samples/demo-js/lib/client.js`）注册同样两个落点，用
`connection.rpc.call("/api", "demo.status")` 读同一份状态：

```js
ctx.slots.inject("settings.section", () => ctx.slots.register({
  name: "settings.section", id: "demo", order: 10,
  label: () => t("nav"), inject: () => ({ call, t }),
}, DemoSettingsSection));

ctx.slots.inject("conversation.composer.dock", () => ctx.slots.register({
  name: "conversation.composer.dock", id: "demo-dock", order: 10,
  inject: () => ({ call }),
}, DemoDockChip));
```

### 14.3 RPC 契约

请求信封（`payload` 原样传递，不包 `args`）：

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

成功响应：

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

业务失败也是 HTTP 200，错误放在 `result.error`；传输层错误用 400（无法解析/信封不匹配）与
415（非 JSON）：

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

6 个端点：

| 端点 | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | 更新后的 status 快照 |
| `demo.reset` | `{}` | 重置后的 status 快照 |
| `demo.echo` | 任意 JSON | `{ received: <原样回显> }` |

错误码：`demo/bad-request`（400）、`demo/unsupported-media-type`（415）、
`demo/unknown-endpoint`（HTTP 200 + `result.error`）。原生端按码分支，而不是匹配文案。

### 14.4 原生端全部代码

入口（`DemoNativePlugin.kt`）：descriptor 与清单一致；注册设置页、就绪落点与 dock：

```kotlin
class DemoNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "demo.native-ui",
        name = "Demo 插件",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,
            PluginCapabilities.STORAGE,
            PluginCapabilities.PAGE_ENTRY,
        ),
    )

    override fun createRenderer(): ComponentRenderer = DemoRenderer()

    init {
        PageRegistry.register(DemoSettingsPage())
        DemoSlots.entries
            .filter { it.hostReady && it.slot != NativeSlot.COMPOSER_DOCK }
            .forEach { SlotRegistry.register(DemoSlotContribution(it)) }
        SlotRegistry.register(DemoDockContribution())
    }
}
```

设置页（`DemoSettingsPage.kt`）拿到空 spec，用 RPC 读权威状态，按钮回传意图：

```kotlin
class DemoSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "demo",
        title = "Demo 插件",
        subtitle = "JS 插件 + 原生伴生：RPC 与落点接入示例（虚构）",
        placement = PageEntry.Placement.SETTINGS,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        DemoSettingsContent(theme = theme, host = host, refreshEvents = null)
    }
}

// 页面内的数据流
LaunchedEffect(rpc, refreshKey) {
    val body = rpcHost.demoCall(DemoRpc.STATUS)     // demo.status
    if (!isActive()) return@LaunchedEffect
    DemoRpc.value(body)?.let { status = DemoRpc.statusOf(it) }
}

// 「服务端 +1」：不自己改状态，回传事件给 JS 插件
host.dispatchEvent(
    DemoRpc.PAGE_COMPONENT_ID,                      // dsh-plugin-demo#demo
    DemoRpc.EVENT_SUBMIT,                           // "submit"
    buildJsonObject { put("actionId", "bump"); put("by", 1) },
)

// 「重置」：纯 RPC 写路径
rpcHost.demoCall(DemoRpc.RESET)                     // demo.reset
```

dock（`DemoDockContribution.kt`）用同一套读/写方式，浮层用 `PluginPopover`（贴锚点 + App 菜单材质）：

```kotlin
class DemoDockContribution : SlotContribution() {
    override val slot: NativeSlot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes: Set<String> = setOf("demo.dock")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // PluginRpcHost.callRpc("demo.status") 读；PluginPopover 里按钮回传：
        // host.dispatchEvent(DemoRpc.DOCK_COMPONENT_ID, DemoRpc.EVENT_SUBMIT,
        //                    buildJsonObject { put("actionId", "bump"); put("by", 1) })
    }
}
```

RPC 常量与信封解析集中在 `DemoRpc.kt`：方法名、组件身份、`value()` / `errorMessage()`
与 `demoCall` 扩展都在这里，页面与落点贡献共用，不重复拼字符串。

### 14.5 验证与用户视角

```bash
$ node --test samples/demo-js/test/rpc.test.mjs
# tests 7 / pass 7：路由、信封、bump/reset、payload 透传、动作隔离、状态隔离

$ python tools/plugin_ui_scanner.py --plugin samples/demo-js \
    --companion samples/demo-native/src/main/kotlin --check
plugin UI coverage check OK
```

用户视角：

1. 安装 JS 插件（`dsh plugin --profile web add ./samples/demo-js`）；
2. 「插件 → 插件商店」看到「Demo 插件 · 原生增强」；
3. 构建并安装伴生 APK（`./gradlew :samples:demo-native:assembleDebug`，
   `adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk`）；
4. 打开 App → 设置 → 插件界面 → Demo 插件（被原生页接管）；聊天页输入栏上方出现
   「Demo 42」胶囊，点按打开 popover；
5. 卸载原生 APK → 自动回退 Web 页面（同样能读 `demo.status`）。

---

## 15. 组件目录（dsh-ui spec 参考）

`SpecRenderer` 支持的全部组件类型。每个组件的 spec 字段如下。

### 布局容器

| type | 字段 | 说明 |
| --- | --- | --- |
| `card` | `title?`, `items[]` | 带标题的卡片容器 |
| `col` | `items[]` | 纵向堆叠 |
| `row` | `items[]` | 横向均分 |
| `grid` | `columns`, `items[]` | 网格（按列分行） |

### 文本与展示

| type | 字段 | 说明 |
| --- | --- | --- |
| `text` | `value`/`text`, `size?`(title/caption) | 文本 |
| `callout` | `content`/`text`, `tone?`(info/success/warning/error), `title?` | 提示块 |
| `badge` | `label`/`text` | 胶囊标签 |
| `stat` | `label`, `value` | 指标（标签+大数字） |
| `hero` | `title`, `subtitle?`/`content?` | 封面块（一条回答最多一个） |
| `divider` | — | 分隔线 |
| `link` | `url`/`href`, `label?`/`text?` | 链接（点击经 `openUrl`） |
| `image` | `alt?` | 图片占位（原生端降级提示） |

### 集合与数据

| type | 字段 | 说明 |
| --- | --- | --- |
| `list` | `items[]`（文本或子组件） | 列表 |
| `table` | `columns[]`, `rows[][]` | 表格 |
| `keyvalue` | `pairs[]`（`{key, value}`） | 键值对 |
| `steps` | `steps[]`（`{title}` 或文本） | 步骤条 |
| `timeline` | `events[]`/`items[]`（`{time/label, title/text}`） | 时间线 |
| `progress` | `value`(0-100), `label?` | 进度条 |
| `code` | `code`/`value`/`content` | 代码块（等宽、可横滚） |
| `chart` | `kind:"bars"`, `data[]`（`{label, value}`） | 柱状图（原生端简易） |

### 降级组件（走 Web）

以下引擎组件在原生端渲染为可读占位「需在完整 Web 界面查看」：

`mermaid` · `diagram` · `plot` · `echart` · `scene3d` · `diff` · 其他未识别类型

> 这些需要 Web 渲染引擎，用户在插件的完整 Web 界面能看到完整形态。

---

## 附：禁止事项速查

| 禁止 | 原因 |
| --- | --- |
| APK 内写业务逻辑（授权流程、调模型 API） | 铁律：逻辑在 Host |
| 存储/传输 token、secret、API key | 凭证不出 Host |
| 申请系统权限（`<uses-permission>`） | 能力走白名单桥 |
| 硬编码颜色/字号 | 主题契约 |
| 反射访问 App 内部类 | 安全边界 |
| 再嵌套动态加载（dex 里再加载 dex） | 审核红线 |
| 假设原生端在场（JS 功能依赖 APK） | 降级契约 |
| 更改已发布的 `dsh.plugin.id` | 身份锚定 |
| 更换签名后同 id 上架 | 防掉包 |
