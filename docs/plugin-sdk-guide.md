# DSH 原生插件 SDK 开发者文档

> 状态：**已自证（样例编译打包通过）**。本文是原生插件（伴侣 APK）的完整开发指南。
> 适用对象：想为 DSH 手机 App 提供原生（Kotlin/Compose）UI 的插件开发者。
> 关联文档：[JS UI 转原生 UI 实战指南](plugin-conversion-guide.md)、[插件侧 SDK 规范](native-plugin-sdk.md)、[App 侧宿主接口](native-plugin-host.md)、[移动端 UI 架构选型](mobile-ui-architecture.md)。
> SDK 坐标：`com.deepseek.dsh:dsh-native-sdk` · ABI 版本 `1`。
> 更新：2026-10-21（页面接管、Host RPC 与生命周期经真实订阅插件验证）。
>
> **自证**：`samples/genui-native`、`samples/kimi-native` 与
> `samples/subscription-native` 均可编译打包。订阅伴生已在模拟器验证原生页面接管、
> Provider 展开与管理、Host RPC、返回导航及卸载即时回退；它是设置页迁移的完整参照。

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
- [14. 完整示例：Kimi OAuth](#14-完整示例kimi-oauth)
- [15. 组件目录（dsh-ui spec 参考）](#15-组件目录dsh-ui-spec-参考)

---

## 0. 这是什么

DSH 手机 App 开放一套**原生 UI 插件**体系：你为已有（或新的）JS 插件附带一个 **Kotlin/Compose 渲染器**，打包成独立 APK 经商店分发。装上后，插件 UI 从 WebView 升级为原生体验——更快的渲染、更跟手的交互、与系统一致的主题。

**核心承诺**：

- **逻辑层零重写**：你的 Host 侧逻辑（`lib/index.js`：OAuth、存储、模型 API、事件）原封不动继续跑。原生 APK 只写「前端逻辑」——画 spec、回传意图；
- **通信零新协议**：原生端与 Host 之间走现有 bridge 的 `dispatch` op 与 `client/refresh` 事件；
- **降级永远可用**：用户不装你的 APK，插件走 WebView 完整可用；装了则增强。

**一句话**：原生 APK 是 Host 逻辑的第二张脸。逻辑永远在 Host，凭证永远不出 Host。

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

最小可用插件见 [§14 完整示例](#14-完整示例kimi-oauth)。

---

## 2. 核心概念

| 概念 | 说明 |
| --- | --- |
| **插件（JS 半边）** | 你已有的 npm/GitHub 插件，`lib/index.js` 跑在 Host。逻辑、凭证、模型调用都在这。 |
| **伴侣 APK（原生半边）** | 一个独立 Android 应用，含你的 Compose 渲染器。只画 UI、回传意图。 |
| **pluginId** | 伴生 APK 自身身份与页面注册归属，如 `changfenhuang.subscription`；不必等于 JS 包名。 |
| **jsPackage** | 与 Host 中 JS 插件配对的包名，如 `dsh-plugin-subscription-oauth`；在 manifest 的 `dsh.plugin.jsPackage` 声明。 |
| **componentId** | bridge 的组件身份，通常为 `<jsPackage>#<settings.section id>`；决定原生页接管哪个 Web 页面。 |
| **spec** | Host 推给原生端的 JSON 状态快照（纯展示数据，无凭证）。你的渲染器消费它。 |
| **落点（NativeSlot）** | App 界面里插件能挂内容的位置（dock、输入框旁、工具卡片、设置页…）。 |
| **能力桥（PluginHostApi）** | 插件唯一摸得到的 App 表面：回传事件、开链接、读主题、存数据。 |

---

## 3. 包结构与 manifest 契约

### 3.1 工程结构

```
your-plugin/                    ← 现有 JS 插件（不变）
├── lib/index.js                ← Host 逻辑
└── lib/client.js               ← Web 前端

your-plugin-native/             ← 新增 Android 工程
├── build.gradle.kts            ← implementation("com.deepseek.dsh:dsh-native-sdk:1.0.0")
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/.../YourPlugin.kt
```

### 3.2 AndroidManifest.xml（基础契约 + JS 配对）

```xml
<manifest package="com.example.yourplugin.native">

    <!-- 入口服务：发现、ABI 校验、静态工厂加载及 JS 插件配对都在 service 级声明 -->
    <service
        android:name=".YourPluginService"
        android:exported="true"
        android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">
        <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
        <meta-data android:name="dsh.plugin.minAppVersion" android:value="12" />
        <meta-data android:name="dsh.plugin.id" android:value="yourname.your-plugin" />
        <meta-data android:name="dsh.plugin.name" android:value="你的插件" />
        <meta-data android:name="dsh.plugin.jsPackage" android:value="dsh-plugin-your-plugin" />
        <intent-filter>
            <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
        </intent-filter>
    </service>

    <!-- 可选：OAuth 等深链回跳。scheme 固定 dsh-plugin，host 必须是 pluginId -->
    <activity android:name=".OAuthReturnActivity" android:exported="true">
        <intent-filter>
            <data android:scheme="dsh-plugin" android:host="yourname.your-plugin" />
        </intent-filter>
    </activity>
</manifest>
```

**规则**：
- `dsh.plugin.id` 一经发布不可更改——它是签名钉死与商店身份的锚；它不必等于 JS 包名；
- `dsh.plugin.jsPackage` 才是与 Host 插件的配对键；`PageEntry.id` 决定接管哪个 `settings.section`；
- 所有插件 meta-data 都放在 **Service 级**，不是 `<application>` 级；
- 插件 APK **不申请任何系统权限**（无 `<uses-permission>`）；需要能力走能力桥声明。

---

## 4. 插件入口与渲染器

### 4.1 插件入口 `DshUiPlugin`

```kotlin
class YourPlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "yourname.your-plugin",          // 与 manifest 的 dsh.plugin.id 一致
        name = "你的插件",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,      // 按需声明；未声明的能力调用即被拒
            PluginCapabilities.STORAGE,
        ),
    )
    override fun createRenderer(): ComponentRenderer = YourRenderer()
}
```

### 4.2 Service（照抄模板）

```kotlin
class YourPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(YourPlugin())

    companion object {
        /** App 在自身进程加载页面时使用的入口；仅写 onBind 不足以注册原生页面。 */
        @JvmStatic
        fun plugin(): DshUiPlugin = YourPlugin()
    }
}
```

### 4.3 渲染器 `ComponentRenderer`

```kotlin
class YourRenderer : ComponentRenderer {
    /** 声明你能渲染的 spec 类型；命中才走原生，否则降级 Web 界面 */
    override val supportedTypes = setOf("yourplugin.status-card")

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

**典型场景**：GenUI 插件的 `render_ui` 工具——模型调它时入参里就是完整 spec，你的工具渲染器（见 §8）直接 `SpecRenderer.Render` 画出来。

---

## 7. 落点：把内容放进 App 的界面

落点（`NativeSlot`）是 App 界面里插件能挂内容的位置。**官方插件能改的每个位置，原生端都有对应落点**；没有对应位置的由 App 补容器。

### 7.1 落点清单

| 落点 | 位置 | 对应官方槽位 |
| --- | --- | --- |
| `SETTINGS_SHEET` | 设置页 | settings.section / settings.general.item / plugins.tab |
| `SIDEBAR_PANEL` | 侧栏/抽屉面板 | sidebar.workspaces / sidebar.panellist / sidebar.right.pane.* |
| `COMPOSER_DOCK` | 输入框顶栏 | conversation.composer.dock / conversation.input.dock |
| `COMPOSER_OVERLAY` | 输入框悬浮气泡 | conversation.input.overlay |
| `PLUGIN_OVERLAY` | 顶层浮层 | shell.overlay |
| `MESSAGE_ACTION` | 消息动作 | conversation.chat.assistant-actions / turnTail |
| `TOOL_VIEW_SHEET` | 工具卡片弹窗 | tool.call.toolview / tool.call.images |
| `CHAT_STREAM` | 聊天消息流 | conversation.chat.flow / chat.node / user.text.node / reasoning.body |
| `WEB_ISLAND_FULL` | 整岛回退 | 无原生位置时嵌入完整 Web 界面 |

### 7.2 注册一个落点贡献

```kotlin
class DockContribution : SlotContribution() {
    override val slot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes = setOf("yourplugin.usage-pill")
    override val order = 0

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // 在输入框上方画一个胶囊（比如剩余用量）
    }
}

// 在插件入口注册
class YourPlugin : DshUiPlugin {
    init { SlotRegistry.register(DockContribution()) }
    // ...
}
```

App 在每个落点放了一个通用容器（`NativeSlotHost`），它查询 `SlotRegistry` 里该落点的贡献并渲染。**显示什么完全由你决定**；没有插件注册时容器不占空间。

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

模型调用 `render_ui` 后，App 在工具卡片处查询 `ToolRenderRegistry`；你的渲染器命中，就把这次调用画成原生 UI。没插件接管时 App 用默认工具卡片样式。

---

## 9. 页面入口：设置页与插件页

在设置页/插件页挂一个原生页面。

```kotlin
class StatusPage : PageContent {
    override val entry = PageEntry(
        id = "status",
        title = "用量状态",
        subtitle = "查看当前额度",
        placement = PageEntry.Placement.SETTINGS,   // 或 PLUGINS
        order = 0,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // 画整页
    }
}

PageRegistry.register(StatusPage())
```

用户点设置页/插件页里你的入口，App 用 `Content` 画整页。

### 9.1 设置页接管规则

`PageEntry.id` 必须与 JS 侧 `settings.section` 的注册 `id` 完全相同。实际组件身份是
`<jsPackage>#<PageEntry.id>`；伴生的 `pluginId` 与 JS 包名可以不同。只有静态工厂加载
成功并注册该页面后，App 才跳过 WebView；APK 仅被安装但未注册页面时仍走 Web。

### 9.2 接收 Host 刷新与检查页面存活

有实时状态的页面可重载带 `refreshEvents` 的 `Content`，收到本组件事件后重新读取权威
RPC。异步回调更新页面前，先检查 `PageLifecycle.LocalIsActive.current`，避免用户已经
返回后仍写入旧页面状态。

---

## 10. 主题契约

- 颜色/字号/形状**全部**取自 `host.currentTheme()` 的 token；
- **禁止**硬编码色值/字号——商店审核红线，也是 Web 与原生视觉一致的前提；
- 深色模式、系统字体缩放由 App 统一处理；快照变化触发重组，你不写深浅色分支；
- 触控目标 ≥44dp、无 hover 依赖。

---

## 11. 与 Host 侧 JS 的通信

完全复用 bridge 现成链路，零新协议：

```
你的原生端 host.dispatchEvent(pluginId, "start-oauth", payload)
  → App bridge client 调 client-slots/dispatch
  → Host 事件总线 ui/<componentId>/<event>
  → 你的 JS 插件监听器处理（已有代码）
  → JS 调 ctx.emit('client/refresh', { componentId, patch })
  → App 收到刷新，重拉 spec
  → 你的渲染器 Render() 重组
```

你只需保证：

1. **componentId 约定**：使用 Host 实际的 `<jsPackage>#<slot 内 id>`；伴生自身 `pluginId` 不一定等于这个包名；
2. **JS 侧推状态**：把原生端要画的数据放进 `client/refresh` 的 `patch`，保证 spec 是 JSON 安全的纯展示数据（无凭证、无函数、无循环引用）。

### 11.1 已有插件 RPC 的页面：`PluginRpcHost`

完整原生页面还可把 `host` 安全地转为 `PluginRpcHost`，直接调用 Host 已有的插件路由：

```kotlin
val rpc = host as? PluginRpcHost
rpc?.callRpc("your-plugin.status", JsonObject(emptyMap())) { result ->
    // 校验 result.ok，取 value；payload 原样发送，不包 args。
}
```

`callRpc` 是回调式 API，不是 `suspend`。需要重新读取时递增 `refreshKey`，由
`LaunchedEffect(rpc, refreshKey)` 发起下一次调用。只调用 Host 已存在的方法，响应中的
`{ result: { ok, value | error } }` 先解信封再解析业务状态。凭证仍只留在 Host。

### 11.2 复杂页面迁移参照

Provider 展开/收起、账号管理、模型显示与排序、授权轮询、迟到响应防护等完整实现，见
[JS UI 转原生 UI 实战指南](plugin-conversion-guide.md) 与
`samples/subscription-native/src/main/kotlin/com/example/subscription/nativeui/SubscriptionSettingsPage.kt`。
不要只迁移卡片外壳：展开区、按钮动作、空态与错误态都属于迁移范围。

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

**因此**：你的 JS 侧功能必须在纯 Web 下完整可用——不得假设原生端在场。

---

## 13. 发布与商店

### 13.1 两种构建方式

- **自己编**：`./gradlew assembleRelease` 出 APK；
- **CI 代编**：你只提交 Kotlin 源码到 GitHub，商店 CI 用钉死的 SDK/Compose/Kotlin 版本统一编译、统一签名登记——你本地甚至不需要装 Android SDK。

### 13.2 商店收录（GitHub tag 模式）

商店的实际内容走 **GitHub tag 索引**：给你的插件仓库挂上 `dsh-mobile-plugin` tag，商店就会自动把它收入索引。

1. 你的插件仓库（含 JS 插件 + 原生伴侣源码）打 tag `dsh-mobile-plugin`；
2. 商店的索引器扫描带该 tag 的仓库，读取 `dsh.native` 声明（pluginId/能力/版本）；
3. 通过审核后进商店索引，用户在「原生插件」tab 的推荐列表看到它；
4. 用户一键安装 → 系统安装器确认 → App 自动发现、验签、ABI 校验 → 出现在列表。

### 13.3 JS 侧声明原生伴侣（`package.json`）

```json
{
  "dsh": {
    "native": {
      "pluginId": "yourname.your-plugin",
      "name": "你的插件",
      "apiVersion": 1,
      "minAppVersion": 12,
      "capabilities": ["openUrl", "storage"],
      "storeUrl": "https://github.com/yourname/your-plugin"
    }
  }
}
```

bridge 的 `native-plugins` provider 把这个声明暴露给 App，商店页据此标注「原生增强」与兼容性。

### 13.4 签名与更新

- 商店登记你的 pluginId + 签名证书指纹（永久身份）；
- 更新时 APK versionCode 递增、签名不变即可；**签名变更视为新插件**（防掉包），商店拒绝同 pluginId 覆盖。

---

## 14. 完整示例：Kimi OAuth

假设你是 Kimi OAuth 插件作者。已有 `lib/index.js`（OAuth、token、用量查询）和 `lib/client.js`（React 设置页）。现在加原生增强。

### 14.1 原生端全部代码

```kotlin
class KimiPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(KimiOAuthPlugin())

    companion object {
        @JvmStatic
        fun plugin(): DshUiPlugin = KimiOAuthPlugin()
    }
}

class KimiOAuthPlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "changfenhuang.kimi-oauth",
        name = "Kimi 登录",
        version = 1,
        capabilities = setOf(PluginCapabilities.OPEN_URL, PluginCapabilities.STORAGE),
    )
    override fun createRenderer(): ComponentRenderer = KimiRenderer()
}

class KimiRenderer : ComponentRenderer {
    override val supportedTypes = setOf("kimi.account-card", "kimi.usage-meter")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        val state = spec.obj("state")
        when (state.str("status")) {
            "logged_out" -> LoginCard(theme) {
                host.dispatchEvent("changfenhuang.kimi-oauth#section", "start-oauth", emptyJson)
            }
            "authorizing" -> AuthorizingCard(theme) {
                host.openUrl(state.str("authorizeUrl"))
            }
            "active" -> UsageCard(
                theme,
                quota = state.num("quotaRemaining"),
                onRefresh = { host.dispatchEvent("changfenhuang.kimi-oauth#section", "refresh", emptyJson) },
                onLogout = { host.dispatchEvent("changfenhuang.kimi-oauth#section", "logout", emptyJson) },
            )
        }
    }
}
```

注意：这里**没有**网络、token、OAuth 状态机——全在 Host 侧 JS 里继续跑。

### 14.2 JS 侧配合（通常已在写）

```js
ctx.on('ui/changfenhuang.kimi-oauth#section/start-oauth', async () => {
  const url = await beginOAuth();
  ctx.emit('client/refresh', {
    componentId: 'changfenhuang.kimi-oauth#section',
    patch: { state: { status: 'authorizing', authorizeUrl: url } },
  });
});
// 登录成功/用量变化时同样 emit client/refresh；spec 里只放展示数据，永远不放 token。
```

### 14.3 用户视角

1. 用户装你的 JS 插件（现有流程）；
2. 「原生插件」tab 看到「Kimi 登录 · 原生增强」；
3. 一键安装 → App 自动发现、验签、ABI 校验通过；
4. 设置页、聊天工具卡升级为原生；OAuth 走 Custom Tab + 深链回跳；
5. 卸载原生 APK → 自动回退 WebView，功能完整。

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
| APK 内写业务逻辑（OAuth、调模型 API） | 铁律：逻辑在 Host |
| 存储/传输 token、secret、API key | 凭证不出 Host |
| 申请系统权限（`<uses-permission>`） | 能力走白名单桥 |
| 硬编码颜色/字号 | 主题契约 |
| 反射访问 App 内部类 | 安全边界 |
| 再嵌套动态加载（dex 里再加载 dex） | 审核红线 |
| 假设原生端在场（JS 功能依赖 APK） | 降级契约 |
| 更改已发布的 `dsh.plugin.id` | 身份锚定 |
| 更换签名后同 id 上架 | 防掉包 |
