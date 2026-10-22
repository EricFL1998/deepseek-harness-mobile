# JS UI 转原生 UI：伴侣 APK 实战指南

> 状态：**以 Demo 插件为完整参照实现**。本文以 `dsh-plugin-demo`（Web 半边
> `samples/demo-js`）和 `samples/demo-native`（Kotlin/Compose 伴生 APK）为例，
> 说明如何把已有 React/Web 设置页与 dock 迁移为原生页面/落点，同时保留完整 Web
> 回退。Demo 是纯虚构演示：不访问网络、不读写账号、不持有任何凭据，状态只存在
> Host 进程内（初始计数 42）。
> 完整源码：[Demo 插件 JS 半边](../samples/demo-js/README.md) ·
> [扫描清单](plugin-ui-inventory-example.md) · [文档站案例](index.html#case)。
> 前置阅读：[SDK 总览与 API](plugin-sdk-guide.md)。
> 更新：2026-10-22。

## 目录

- [1. 先理解迁移边界](#1-先理解迁移边界)
- [2. 盘点 Web UI，而不是直接翻译 JSX](#2-盘点-web-ui而不是直接翻译-jsx)
- [3. 建 Android 工程](#3-建-android-工程)
- [4. 配准三种身份](#4-配准三种身份)
- [5. 写入口、Service 与页面](#5-写入口service-与页面)
- [6. 接通 Host 状态和操作](#6-接通-host-状态和操作)
- [7. 把 Web 交互做完整](#7-把-web-交互做完整)
- [8. 刷新、轮询和页面生命周期](#8-刷新轮询和页面生命周期)
- [9. WebView 接管与卸载回退](#9-webview-接管与卸载回退)
- [10. 完整联调清单](#10-完整联调清单)
- [11. 接入检查清单](#11-接入检查清单)

---

## 1. 先理解迁移边界

迁移的目标是：**Host 逻辑不动，新增一个原生前端；没有伴生 APK 时 Web UI 仍完整可用。**

| 层 | 是否修改 | 职责 |
| --- | --- | --- |
| Host JS（RPC、状态、动作） | 原则上不动 | 权威状态、副作用与业务逻辑 |
| Web React UI | 不删 | 桌面端界面 + 手机端降级路径 |
| 原生伴生 APK | 新增 | Compose 展示、局部 UI 状态、调用 Host |

原生 APK 不是独立业务应用，也不复制一套业务状态。它通过 App 宿主调用已有 Host
接口：Demo 原生设置页上的计数、上次动作、更新时间全部来自 `demo.status`，APK 内
没有第二份计数。若真实插件持有令牌、API Key 等凭据，同样只留在 Host，不进入伴生
页面。

**先验收功能，再验收外观。** “页面变原生”不等于迁移完成；按钮、展开区、刷新、
写操作、返回导航都要可用。

## 2. 盘点 Web UI，而不是直接翻译 JSX

先读 Web 注册代码和状态协议，整理四张表。

### 2.1 槽位与组件身份

Demo 的 Web 半边注册了两个落点（`samples/demo-js/lib/client.js`）：

```js
ctx.slots.inject("settings.section", () => ctx.slots.register({
  name: "settings.section",
  id: "demo",
  order: 10,
  label: () => t("nav"),
  inject: () => ({ call, t }),
}, DemoSettingsSection));

ctx.slots.inject("conversation.composer.dock", () => ctx.slots.register({
  name: "conversation.composer.dock",
  id: "demo-dock",
  order: 10,
  inject: () => ({ call }),
}, DemoDockChip));
```

bridge 实际给出的组件身份是：

```text
dsh-plugin-demo#demo
dsh-plugin-demo#demo-dock
```

记录 `slot`、`id`、包名、页面标题。页面接管要求 JS 页面 ID 与原生 `PageEntry.id`
完全相同（Demo 都是 `demo`）；dock 回传事件时 `componentId` 也必须是
`dsh-plugin-demo#demo-dock`。

### 2.2 状态结构

从 Web 调用的 RPC 和 `lib/catalog.js` 中提取页面真正需要的字段。Demo 只需要两组：

- `demo.status`：`greeting`、`count`、`lastAction`、`updatedAt`、`startedAt`、
  `uptimeMs`；
- `demo.catalog`：`plugin`、`companion.pluginId`、`companion.apkPackage`、
  `companion.sample`、`registrations[].slot`、`registrations[].id`、
  `registrations[].componentId`、`registrations[].nativeSlot`、
  `registrations[].purpose`、`endpoints[]`。

**只传展示数据。** Demo 没有凭据字段；真实插件也不应把 token、API Key、
Authorization 头放进 RPC 返回值或 slot spec。

### 2.3 操作与状态变更

| Web 操作 | Host 方法 | 必要 payload |
| --- | --- | --- |
| 读取页面状态 | `demo.status` | `{}` |
| 读取接入清单 | `demo.catalog` | `{}` |
| 探活 | `demo.ping` | `{}` |
| 计数 +n | `demo.bump` | `{ by?: number }` |
| 重置为 42 | `demo.reset` | `{}` |
| 回显校验 | `demo.echo` | 任意 JSON |

对应到原生侧的三条路径：

| Web 操作 | 原生做法 |
| --- | --- |
| 打开 / 刷新 | `PluginRpcHost.callRpc("demo.status", {})`，或等 `client/refresh` 后重拉 |
| 服务端 +1 | `dispatchEvent("dsh-plugin-demo#demo", "submit", { actionId:"bump", by:1 })` |
| 重置 | 直接 `callRpc("demo.reset", {})`，纯 RPC 写路径 |

### 2.4 交互完整性

逐项列出，不允许只迁移卡片外壳：

- 设置页显示了哪些字段？加载中、RPC 不可用、空数据分别怎么显示？
- dock 胶囊显示什么？点按是展开 popover、跳转页面，还是别的行为？
- 写操作是否有 busy 防重复？失败后能否重试？
- 原生页与 Web 回退页的信息是否一致？刷新后会不会丢状态？
- 返回是否只回上一级？

Demo 只有两个落点，也要逐项验收：标题、数据、失败态、动作和返回都可验证。

## 3. 建 Android 工程

伴侣是独立 **application** 模块：

```kotlin
// samples/demo-native/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dsh.mobile.dsh_plugin_demo" // 不要用 Java 关键字 native
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dsh.mobile.dsh_plugin_demo"
        minSdk = 26
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":plugin:native-sdk")) // 独立工程用已发布 SDK 坐标
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
```

### ClassLoader：按约定加载

宿主使用 **父优先 ClassLoader**：主 App 持有唯一的 `DshUiPlugin`、`PageRegistry`
和 Compose 类，伴生只提供自己的实现类。按以下约定加载，保证
`Service.plugin()` 返回的对象能被宿主识别为 `DshUiPlugin`：

- 由宿主共享 SDK 与 Compose 类型，伴生不打包第二份；
- 不自行创建子优先/隔离 ClassLoader；
- 不把另一份 `PageRegistry` 当成宿主注册表；
- 不在伴生中再次动态加载 dex 绕过 ABI 门禁；
- 不假设 `createPackageContext()` 返回的类与 App 类型天然兼容。

App 升级 SDK 后，若伴生 ABI 不兼容，应停在门禁并走 Web，而不是强制类型转换。

## 4. 配准三种身份

三者不是同一个概念，按下表分别配置：

| 身份 | Demo 的值 | 用途 |
| --- | --- | --- |
| `pluginId` | `demo.native-ui` | 伴生插件自身身份、页面注册归属 |
| `jsPackage` | `dsh-plugin-demo` | 与 Host 的 JS 插件配对 |
| `PageEntry.id` | `demo` | 接管 Web 的具体 `settings.section` |

设置页组件身份是 `dsh-plugin-demo#demo`，dock 是 `dsh-plugin-demo#demo-dock`；
它们由 `<jsPackage>#<slot 内 id>` 组成，不是 `pluginId` 拼出来的。

### 4.1 Manifest

meta-data 必须放在 **Service 级**：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.dsh.mobile.dsh_plugin_demo">
    <application>
        <service
            android:name=".DemoPluginService"
            android:exported="true"
            android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">
            <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
            <meta-data android:name="dsh.plugin.minAppVersion" android:value="12" />
            <meta-data android:name="dsh.plugin.version" android:value="1" />
            <meta-data android:name="dsh.plugin.id" android:value="demo.native-ui" />
            <meta-data android:name="dsh.plugin.name" android:value="Demo 插件" />
            <meta-data
                android:name="dsh.plugin.jsPackage"
                android:value="dsh-plugin-demo" />
            <intent-filter>
                <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

`dsh.plugin.id` **不要求等于 JS 包名**：Demo 里它是 `demo.native-ui`，JS 包名是
`dsh-plugin-demo`。实际路由使用 `jsPackage + "#" + slot 内 id` 匹配 Web 组件，并以
`pluginId` 隔离不同伴生注册的页面。

### 4.2 作用域包名和版本

`@scope/pkg` 的 `/` 属于包名，不能截掉；`#` 才是组件分隔符。路由按最后一个 `#` 拆分。
版本化 `@scope/pkg@1.2.3` 应归一为 `@scope/pkg`。Git URL 不能靠字符串猜 npm 包名，
应该以 Host 实际组件身份或明确的配对元数据为准。Demo 是无 scope 的包名，配对靠
manifest 的 `dsh.plugin.jsPackage`，不靠字符串推断。

## 5. 写入口、Service 与页面

### 5.1 Service 必须提供静态工厂

`onBind` 服务于绑定场景，页面加载进 App 进程时使用静态 `plugin()` 工厂。Service
同时实现 `onBind` 与静态工厂：

```kotlin
class DemoPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(DemoNativePlugin())

    companion object {
        @JvmStatic
        fun plugin(): DshUiPlugin = DemoNativePlugin()
    }
}
```

### 5.2 在入口 init 注册页面与落点

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

`DemoSlots` 列了 22 个原生落点：其中 21 个注入真实界面（dock 单独由
`DemoDockContribution` 负责），`WEB_ISLAND_FULL` 宿主已就绪但要留给整岛 Web 回退，
Demo 不注入它，只在 Demo 页列出原因。

### 5.3 `PageEntry.id` 与 Web 注册 ID 完全一致

```kotlin
class DemoSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "demo", // 对应 JS settings.section id，不是显示标题
        title = "Demo 插件",
        subtitle = "JS 插件 + 原生伴生：RPC 与落点接入示例（虚构）",
        placement = PageEntry.Placement.SETTINGS,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        DemoSettingsContent(theme = theme, host = host, refreshEvents = null)
    }
}
```

只有 **伴生加载成功 + 找到对应页面** 才接管 Web。仅发现 APK、仅有 Service、仅有
`ComponentRenderer` 都不足以接管一个 `settings.section`。

## 6. 接通 Host 状态和操作

有两条通道，按场景选择。

### 6.1 通用事件：`dispatchEvent`

适合已有 `mobileUi.registerAction` 监听器的组件。Demo 的「服务端 +1」按钮：

```kotlin
val payload = buildJsonObject {
    put("actionId", "bump")
    put("by", 1)
}
host.dispatchEvent("dsh-plugin-demo#demo", "submit", payload)
```

链路是原生事件 → `client-slots/dispatch` → JS 插件 `mobileUi` 动作（`lib/actions.js`
里的 `bump`）→ Host 状态变化 → bridge 排 `client/refresh` → 原生页 `refreshEvents`
收到通知后重拉 `demo.status`。事件名使用 bridge 允许的 `press` / `input` / `toggle` /
`submit`，Demo 统一用 `submit`。

### 6.2 页面直接读取/调用现有 RPC：`PluginRpcHost`

适合已有 REST 风格插件 RPC 的复杂页面。宿主完整页面提供可选 `PluginRpcHost`。
Demo 用 `DemoRpc.kt` 把回调式 API 包成 suspend，页面只关心成功/失败：

```kotlin
internal suspend fun PluginRpcHost.demoCall(
    method: String,
    payload: JsonObject = JsonObject(emptyMap()),
): JsonElement? = suspendCancellableCoroutine { continuation ->
    callRpc(method, payload) { result ->
        continuation.resume(result.getOrNull())
    }
}

// 页面里
val rpcHost = rpc ?: return@LaunchedEffect
val body = rpcHost.demoCall(DemoRpc.STATUS)
if (!isActive()) return@LaunchedEffect
val value = DemoRpc.value(body) // 解 result.ok 并取 value
if (value != null) status = DemoRpc.statusOf(value)
else error = DemoRpc.errorMessage(body, "无法读取 demo.status")
```

注意：

1. 方法名用插件**已经存在**的 Host 路由（Demo 是 `demo.status` / `demo.catalog` /
   `demo.ping` / `demo.bump` / `demo.reset` / `demo.echo`），不虚构
   `component.state`、`component.dispatch` 等不存在的方法；
2. payload 原样发送，不额外包一层 `args`；
3. 宿主响应通常是 `{ result: { ok, value | error } }`，先检查 `ok`，不能直接把整个
   信封当业务状态；业务失败也是 HTTP 200，错误码在 `result.error`；
4. 回调不是 `LaunchedEffect` 的挂起等待。用 suspend 包装或 `refreshKey` 触发下一次
   读取，不要在写完 `callRpc()` 后立刻读旧状态；
5. `busy` 防止重复提交，错误要显示，按钮在失败后可重试。

一个安全的刷新骨架：

```kotlin
var refreshKey by remember { mutableStateOf(0) }
var status by remember { mutableStateOf<DemoStatus?>(null) }
var error by remember { mutableStateOf<String?>(null) }

fun refresh() { refreshKey += 1 }

LaunchedEffect(rpc, refreshKey) {
    val rpcHost = rpc ?: return@LaunchedEffect
    val body = rpcHost.demoCall(DemoRpc.STATUS)
    if (!isActive()) return@LaunchedEffect
    val value = DemoRpc.value(body)
    if (value != null) {
        status = DemoRpc.statusOf(value)
        error = null
    } else {
        error = DemoRpc.errorMessage(body, "无法读取 demo.status")
    }
}
```

## 7. 把 Web 交互做完整

### 7.1 局部 UI 状态属于原生

popover 是否打开、按钮是否 busy、输入草稿，都应该是原生局部状态，不要回写 Host：

```kotlin
var open by remember { mutableStateOf(false) }
var busy by remember { mutableStateOf(false) }
val current = status

Text(
    text = "Demo ${current.count}",
    modifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { open = !open })
    },
)
if (open) {
    PluginPopover(theme = theme, onDismiss = { open = false }) {
        // 展示 Host 数据 + 动作
    }
}
```

popover 里的内容按“Host 数据 + 写路径 + 关闭方式”实现：Demo dock 展示状态、计数、
上次动作、更新时间、Uptime，并提供三条动作：

- 「服务端 +1 · 事件」：`dispatchEvent` → Host 动作 → `client/refresh`；
- 「刷新 · RPC」：直接重拉 `demo.status`；
- 「重置 · RPC」：直接调用 `demo.reset`。

设置页按“状态、接入清单、动作”实现；popover 至少包含一条写路径和关闭方式。

### 7.2 用稳定身份做局部状态键

`remember(componentId)` 或 `remember(entry.slot)` 可以隔离不同落点、不同卡片的
`open`、草稿和错误态。刷新或重组时保留这些局部状态，避免每次 RPC 返回后浮层自动
合上、输入被清空。

### 7.3 手机端交互映射

| Web | 原生建议 |
| --- | --- |
| 胶囊 / 按钮点按 | `clickable` 或 `pointerInput(detectTapGestures)`，切换本地状态 |
| hover 显示提示 | 点按 / 长按展开 `PluginPopover` |
| 输入框 / 表单 | Compose `TextField` + 本地草稿，提交时调 RPC |
| Checkbox / switch | `Switch`，变更调 Host，失败要可恢复 |
| Modal 管理框 | 展开管理区或 Compose Dialog，二选一保持一致 |
| 新 tab / 外链 | 系统浏览器（`OPEN_URL`） |
| 定时轮询 | `LaunchedEffect(key)` 中循环，页面离开或条件变化自动取消 |

Demo 只覆盖了点按、Popover 和 RPC 动作；其余映射按真实插件的交互选用。不要为追求
DOM 一比一而复刻嵌套网页布局；应保留**功能与信息层级**，用手机可用的控件实现。

## 8. 刷新、轮询和页面生命周期

### 8.1 接住 Host 刷新事件

需要接收 `client/refresh` 的页面可重载带 `refreshEvents` 的 `Content`：

```kotlin
@Composable
override fun Content(
    spec: JsonObject,
    theme: PluginTheme,
    host: PluginHostApi,
    refreshEvents: SharedFlow<String>?,
) {
    DemoSettingsContent(theme, host, refreshEvents)
}

// 只响应本组件 ID，然后重拉权威 RPC
LaunchedEffect(refreshEvents) {
    refreshEvents?.collect { componentId ->
        if (componentId == DemoRpc.PAGE_COMPONENT_ID || componentId == DemoRpc.JS_PACKAGE) {
            refresh()
        }
    }
}
```

不要假设某个通用 `component.state` 存在；收到事件后重新调用权威 RPC。

### 8.2 轮询要有退出条件

有等待中的异步任务时才轮询，条件消失、页面离开都会结束循环；不要写与页面生命周期
无关的永久 `while (true)`：

```kotlin
// 例如等待授权/上传完成；条件消失、页面离开都会结束循环
val hasPending = /* 由 Host 状态推导 */
LaunchedEffect(hasPending) {
    if (!hasPending) return@LaunchedEffect
    while (hasPending) {
        delay(1_500)
        refresh()
    }
}
```

周期刷新则挂在组合上，页面离开自动取消。Demo dock 采用 30s 重读：

```kotlin
LaunchedEffect(rpc, status != null) {
    if (rpc == null || status == null) return@LaunchedEffect
    while (true) {
        delay(30_000)
        loadState()?.let { status = it }
    }
}
```

### 8.3 迟到响应不得更新已离开的页面

使用 `PageLifecycle.LocalIsActive`：

```kotlin
val isActive = PageLifecycle.LocalIsActive.current

val body = rpcHost.demoCall(DemoRpc.STATUS)
if (!isActive()) return@LaunchedEffect
DemoRpc.value(body)?.let { status = DemoRpc.statusOf(it) }
```

页面组件、RPC 回调和卸载/返回是异步竞争的。仅靠 `remember` 不能阻止迟到回调。

### 8.4 返回栈

页面切换要以**页面身份**为 key；在监听路由注册的 `AnimatedContent` 中使用当前页面
的 `activePluginComponent`，保证一次返回只回上一级。验收时同时测：

- 原生页 → 设置主页；
- Web 回退页 → 设置主页；
- 原生页打开时装载/卸载伴生，页面不错误连退两级。

## 9. WebView 接管与卸载回退

### 9.1 正确接管条件

必须同时满足：

1. PackageManager 发现伴生；
2. ABI/签名/App 版本门禁通过；
3. 静态 `plugin()` 成功返回主 App 认可的 `DshUiPlugin`；
4. 伴生注册了 `PageContent`（Demo：`PageEntry(id = "demo", placement = SETTINGS)`）；
5. `jsPackage#PageEntry.id` 与当前 Web 组件一致（Demo：`dsh-plugin-demo#demo`）。

满足后，原生分支**不创建 WebView**。不满足则继续使用原 `PluginComponentPane`，不能
显示空白原生页。dock 走另一套：伴生在 `SlotRegistry` 里为 `COMPOSER_DOCK` 注册了
`SlotContribution`（Demo 是 `DemoDockContribution`）才会接管；没有贡献时 Web 的
`DemoDockChip` 继续显示。

### 9.2 包变更和旧实例清理

宿主监听 `PACKAGE_ADDED`、`PACKAGE_REMOVED`、`PACKAGE_REPLACED` 并重扫。卸载或加载
失败时，要从页面注册表移除旧伴生实例，再发布新的路由快照。否则打开中的页面会继续
持有已卸载 APK 的 ClassLoader 和旧页面。

### 9.3 必须验证的回退矩阵

| 操作 | 预期 |
| --- | --- |
| 未装伴生，打开设置页 | `DemoSettingsSection` Web UI，RPC 可用 |
| 未装伴生，看输入栏 | `DemoDockChip` 胶囊显示，点按可调 RPC |
| 安装伴生，回到 App | 原生设置页 / dock 接管，无需改 Host |
| 伴生打开时卸载 APK | 当前页即时切回 Web UI |
| 再装伴生 | 再次恢复原生页 |
| ABI 不兼容 / 工厂失败 | Web 回退，不崩溃、不空白 |

## 10. 完整联调清单

### 构建与静态检查

```bash
# 1) 把 JS 插件装进 Host profile（本地路径）
dsh plugin --profile web add ./samples/demo-js

# 2) 构建伴生 APK 与 App
./gradlew :samples:demo-native:assembleDebug
./gradlew :app:assembleDebug
./gradlew :plugin:native-host:testDebugUnitTest

# 3) RPC / 动作 / 状态隔离
node --test samples/demo-js/test/rpc.test.mjs

# 4) 扫描器：两个落点都必须映射、有宿主、伴生实现
python tools/plugin_ui_scanner.py \
  --plugin samples/demo-js \
  --companion samples/demo-native/src/main/kotlin \
  --check
```

路由单测至少覆盖：真实包名映射、作用域包名、错误页面 ID、卸载清空、无匹配页面时回退。

### 设备检查

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r \
  samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk
adb -s emulator-5554 shell am start -n \
  com.dsh.mobile/com.deepseek.dsh.MainActivity
```

依次验证：

- [ ] 原生设置页出现，标题为“Demo 插件”，不是只改了副标题；
- [ ] 计数初始 42、上次动作 `boot`，来自 `demo.status` 而非硬编码；
- [ ] 接入清单来自 `demo.catalog`（两个落点与端点列表）；
- [ ] 原生页没有 WebView 初始化；卸载伴生后设置页回到 `DemoSettingsSection`；
- [ ] 输入栏出现“Demo 42”胶囊，点按弹出贴锚点的 `PluginPopover`；
- [ ] popover 的「服务端 +1 · 事件」经 `dispatchEvent` 改 Host 状态，刷新后页面重拉；
- [ ] 「重置 · RPC」直接调 `demo.reset`，计数回到 42；
- [ ] 刷新按钮与 30s 轮询只读 Host，不在 APK 内累加；
- [ ] 返回只回上一级，不直接退出设置；
- [ ] 页面打开时卸载伴生，立即回退 Web；装回后恢复原生；
- [ ] 深色模式、长文本、空列表、Host RPC 不可用都有可读表现；
- [ ] `node --test samples/demo-js/test/rpc.test.mjs` 7/7，扫描器 `--check` 通过。

## 11. 接入检查清单

| 检查项 | 正确做法 |
| --- | --- |
| 身份值分别配置 | `pluginId` / `jsPackage` / `PageEntry.id` 分开核对（Demo：`demo.native-ui` / `dsh-plugin-demo` / `demo`） |
| Service 同时有 `onBind` 与静态工厂 | `@JvmStatic fun plugin(): DshUiPlugin` |
| 父优先共享宿主类型 | 伴生不打包第二份 SDK/Compose 类型 |
| 设置页注册了匹配的 `PageContent` | `PageEntry.id` 与 JS `settings.section` id 完全一致 |
| RPC 方法真实存在 | 使用 Host 已有方法与真实 payload（`demo.status` 等） |
| 读值走回调或 suspend 包装 | `demoCall` 或 `refreshKey` 驱动读取 |
| 落点有完整内容与动作 | 按第 2.4 节列出交互，再逐项实现 |
| `componentId` 与 JS 注册一致 | `dsh-plugin-demo#demo` / `dsh-plugin-demo#demo-dock` |
| 导航以页面身份为 key | 一次返回只回上一级 |
| 包变更时移除旧注册 | 重扫 + 移除旧注册 + 发布新快照 |
| 迟到回调先判页面是否活跃 | `PageLifecycle.LocalIsActive` |
| 扫描与安装放在 IO 线程 | 主线程只发布快照 |
| 功能与回退都验收 | 真机逐项操作，并验证 RPC 与 Web 回退 |

完整参照实现：`samples/demo-native`（Web 半边 `samples/demo-js`；JS 身份、RPC 与
事件契约见 [README](../samples/demo-js/README.md)）。路由与生命周期宿主：
`plugin/native-host`。扫描报告：[Demo 插件 UI 清单](plugin-ui-inventory-example.md)。
