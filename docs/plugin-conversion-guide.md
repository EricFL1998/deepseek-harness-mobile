# JS UI 转原生 UI：伴侣 APK 实战指南

> 状态：**经真实订阅插件与模拟器验证**。本文以 `dsh-plugin-subscription-oauth` 和
> `samples/subscription-native` 为例，说明如何把已有 React/Web 设置页迁移为
> Kotlin/Compose 原生页面，同时保留完整 Web 回退。
> 前置阅读：[SDK 总览与 API](plugin-sdk-guide.md)。
> 更新：2026-10-21（补充 ClassLoader、页面配对、Host RPC、复杂交互和卸载回退）。

## 目录

- [1. 先理解迁移边界](#1-先理解迁移边界)
- [2. 盘点 Web UI，而不是直接翻译 JSX](#2-盘点-web-ui而不是直接翻译-jsx)
- [3. 建 Android 工程](#3-建-android-工程)
- [4. 配准三种身份](#4-配准三种身份)
- [5. 写入口、Service 与页面](#5-写入口service-与页面)
- [6. 接通 Host 状态和操作](#6-接通-host-状态和操作)
- [7. 把复杂 Web 交互做完整](#7-把复杂-web-交互做完整)
- [8. 刷新、轮询和页面生命周期](#8-刷新轮询和页面生命周期)
- [9. WebView 接管与卸载回退](#9-webview-接管与卸载回退)
- [10. 完整联调清单](#10-完整联调清单)
- [11. 本次实测踩坑表](#11-本次实测踩坑表)

---

## 1. 先理解迁移边界

迁移的目标是：**Host 逻辑不动，新增一个原生前端；没有伴生 APK 时 Web UI 仍完整可用。**

| 层 | 是否修改 | 职责 |
| --- | --- | --- |
| Host JS（OAuth、存储、模型调用、RPC） | 原则上不动 | 业务状态、凭证、网络与副作用 |
| Web React UI | 不删 | 桌面端界面 + 手机端降级路径 |
| 原生伴生 APK | 新增 | Compose 展示、局部 UI 状态、调用 Host |

原生 APK 不是独立业务应用，也不应复制一套账号/模型/OAuth 逻辑。它通过 App 宿主调用
已有 Host 接口，令牌、API Key、Secret 不进入伴生页面。

**先验收功能，再验收外观。** “页面变原生”不等于迁移完成；按钮、展开区、登录、刷新、
模型开关和返回导航都要可用。

## 2. 盘点 Web UI，而不是直接翻译 JSX

先读 Web 注册代码和状态协议，整理四张表。

### 2.1 槽位与组件身份

例如订阅插件：

```js
ctx.slots.register({
  name: 'settings.section',
  id: 'subscriptions',
  label: () => t('nav'),
}, SubscriptionsSection);
```

bridge 实际给出的组件身份是：

```text
dsh-plugin-subscription-oauth#subscriptions
```

记录 `slot`、`id`、包名、页面标题。页面接管要求 JS 页面 ID 与原生 `PageEntry.id` 完全相同。

### 2.2 状态结构

从 Web 调用的 `status` RPC 和协议类型中提取页面真正需要的字段。订阅页需要：

- Provider：`provider`、`displayName`、`loggedIn`、`connectable`、`busy`、`pending`；
- Account：`account`、`label`、`isDefault`、`expired`、`needsLogin`；
- Model：`id`、`name`、`visible`、`source`、`userAdded`、`contextWindow`；
- Usage：`account`、`plan`、`windows[].label`、`usedPercent`；
- 管理信息：`details`、`lastError`。

**只传展示数据。** 不把 token、refreshToken、API Key、Authorization 头放进 JSON。

### 2.3 操作与状态变更

| Web 操作 | Host 方法 | 必要 payload |
| --- | --- | --- |
| 读取页面 | `subscription-oauth.status` | `{}` |
| 登录 / 取消 | `login` / `cancel` | `{ provider }` |
| 退出 / 默认账号 / 刷新令牌 | `logout` / `setDefault` / `refresh` | `{ provider, account }` |
| 模型显示 / 排序 / 增删 | `setModelVisible` / `setModelOrder` / `addModel` / `removeModel` | 按协议 |
| 刷新模型 / 额度 | `discover` / `usage` | `{ provider }` |
| 重读凭据 | `reload` | `{}` |

### 2.4 交互完整性

逐项列出，不允许只迁移卡片外壳：

- 卡片头是否可以展开/收起？展开区到底有什么？
- 登录中是否显示授权链接、设备码、取消按钮和轮询？
- 未登录、已登录、无账号、空模型、加载失败分别怎么显示？
- 管理按钮是否进入管理区，还是只是一个没有动作的按钮？
- 页面刷新后，展开状态是否保留？返回是否到上一级？

本次第一版原生页只画了 Provider 名称和登录状态，因此“看起来原生”，但所有 Provider
都不能展开。这类问题只有交互测试能发现，截图首页不能作为验收。

## 3. 建 Android 工程

伴侣是独立 **application** 模块：

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.yourplugin.nativeui" // 不要用 Java 关键字 native
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.yourplugin.nativeui"
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

### ClassLoader：为什么编译成功却永远无法接管

本次三个伴生 APK 都出现：`Service.plugin()` 能返回对象，但宿主 `instanceof DshUiPlugin`
为 false。原因是伴生和主 App 各自加载了一份 SDK，同全限定名的接口也有不同运行时身份。

当前宿主使用 **父优先 ClassLoader**：主 App 持有唯一的 `DshUiPlugin`、`PageRegistry` 和
Compose 类，伴生只提供自己的实现类。插件侧不要：

- 自行创建子优先/隔离 ClassLoader；
- 把另一份 `PageRegistry` 当成宿主注册表；
- 在伴生中再次动态加载 dex 绕过 ABI 门禁；
- 假设 `createPackageContext()` 返回的类与 App 类型天然兼容。

App 升级 SDK 后，若伴生 ABI 不兼容，应停在门禁并走 Web，而不是强制类型转换。

## 4. 配准三种身份

这是最容易写错、也最关键的一步。三者不是同一个概念：

| 身份 | 示例 | 用途 |
| --- | --- | --- |
| `pluginId` | `changfenhuang.subscription` | 伴生插件自身身份、页面注册归属 |
| `jsPackage` | `dsh-plugin-subscription-oauth` | 与 Host 的 JS 插件配对 |
| `PageEntry.id` | `subscriptions` | 接管 Web 的具体 `settings.section` |

### 4.1 Manifest

meta-data 必须放在 **Service 级**：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.subscription.nativeui">
    <application>
        <service
            android:name=".SubscriptionPluginService"
            android:exported="true"
            android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">
            <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
            <meta-data android:name="dsh.plugin.minAppVersion" android:value="12" />
            <meta-data android:name="dsh.plugin.id" android:value="changfenhuang.subscription" />
            <meta-data android:name="dsh.plugin.name" android:value="订阅" />
            <meta-data
                android:name="dsh.plugin.jsPackage"
                android:value="dsh-plugin-subscription-oauth" />
            <intent-filter>
                <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

`dsh.plugin.id` **不要求等于 JS 包名**。如果相等也可以，但不要靠这个巧合。实际路由使用
`jsPackage + "#" + PageEntry.id` 匹配 Web 组件，并以 `pluginId` 隔离不同伴生注册的页面。

### 4.2 作用域包名和版本

`@scope/pkg` 的 `/` 属于包名，不能截掉；`#` 才是组件分隔符。路由按最后一个 `#` 拆分。
版本化 `@scope/pkg@1.2.3` 应归一为 `@scope/pkg`。Git URL 不能靠字符串猜 npm 包名，应该
以 Host 实际组件身份或明确的配对元数据为准。

## 5. 写入口、Service 与页面

### 5.1 Service 必须提供静态工厂

`onBind` 服务于绑定场景，但 App 把页面加载进自身进程时使用的是静态 `plugin()` 工厂。
只有 `onBind` 没有工厂，是“服务存在但页面永远不注册”的常见原因。

```kotlin
class SubscriptionPluginService : Service() {
    override fun onBind(intent: Intent): IBinder =
        DshPluginBinder(SubscriptionNativePlugin())

    companion object {
        @JvmStatic
        fun plugin(): DshUiPlugin = SubscriptionNativePlugin()
    }
}
```

### 5.2 在入口 init 注册页面

```kotlin
class SubscriptionNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "changfenhuang.subscription",
        name = "订阅",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,
            PluginCapabilities.STORAGE,
            PluginCapabilities.PAGE_ENTRY,
        ),
    )

    override fun createRenderer(): ComponentRenderer = SubscriptionRenderer()

    init {
        PageRegistry.register(SubscriptionSettingsPage())
    }
}
```

### 5.3 `PageEntry.id` 与 Web 注册 ID 完全一致

```kotlin
class SubscriptionSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "subscriptions", // 对应 JS settings.section id，不是显示标题
        title = "订阅",
        subtitle = "使用你已付费的 AI 订阅，无需 API Key",
        placement = PageEntry.Placement.SETTINGS,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        SubscriptionSettingsContent(theme, host)
    }
}
```

只有 **伴生加载成功 + 找到对应页面** 才接管 Web。仅发现 APK、仅有 Service、仅有
`ComponentRenderer` 都不足以接管一个 `settings.section`。

## 6. 接通 Host 状态和操作

有两条通道，按场景选择。

### 6.1 通用事件：`dispatchEvent`

适合已有 `ui/<componentId>/<event>` 监听器：

```kotlin
host.dispatchEvent(
    "dsh-plugin-subscription-oauth#subscriptions",
    "refresh",
    JsonObject(emptyMap()),
)
```

链路是原生事件 → `client-slots/dispatch` → Host JS 监听器 → `client/refresh`。

### 6.2 页面直接读取/调用现有 RPC：`PluginRpcHost`

适合已有 REST 风格插件 RPC 的复杂页面。宿主完整页面提供可选 `PluginRpcHost`：

```kotlin
val rpc = host as? PluginRpcHost
rpc?.callRpc("subscription-oauth.status", JsonObject(emptyMap())) { result ->
    // 先在组合外解析/校验，再更新 Compose 状态
}
```

注意：

1. 方法名用插件**已经存在**的 Host 路由，不虚构 `component.state`、
   `component.dispatch` 等不存在的方法；
2. payload 原样发送，不额外包一层 `args`；
3. 宿主响应通常是 `{ result: { ok, value | error } }`，先检查 `ok`，不能直接把整个
   信封当业务状态；
4. 网络回调不是 `LaunchedEffect` 的挂起等待。用 `refreshKey` 触发下一次读取，不要在
   `LaunchedEffect` 中写完 `callRpc()` 就立刻读旧状态；
5. `busy` 防止重复提交，错误要显示，按钮在失败后可重试。

一个安全的刷新骨架：

```kotlin
var refreshKey by remember { mutableStateOf(0) }
var providers by remember { mutableStateOf<List<ProviderUi>>(emptyList()) }
var error by remember { mutableStateOf<String?>(null) }

fun refresh() { refreshKey += 1 }

LaunchedEffect(rpc, refreshKey) {
    rpc?.callRpc("your-plugin.status", JsonObject(emptyMap())) { result ->
        result.onSuccess { body ->
            val value = unwrapRpcValue(body) // 校验 result.ok 并取 value
            providers = parseProviders(value)
            error = null
        }.onFailure { error = it.message }
    }
}
```

## 7. 把复杂 Web 交互做完整

### 7.1 展开状态属于原生局部 UI 状态

```kotlin
var expandedProviders by remember { mutableStateOf(setOf<String>()) }

val expanded = provider.id in expandedProviders
TextButton(onClick = {
    expandedProviders = if (expanded) {
        expandedProviders - provider.id
    } else {
        expandedProviders + provider.id
    }
}) { Text(if (expanded) "收起" else "编辑") }
```

**展开区必须有实际内容**。订阅页按 Web 结构实现：

- Provider 头：状态、模型/账号摘要、编辑/收起；
- 未登录提示、连接/添加账号、取消授权；
- 账号、默认账号、退出、刷新令牌；
- 套餐和额度、签到/任务（仅支持的 Provider）；
- 模型目录、显示开关、排序、添加/删除；
- 管理区、重读凭据和诊断信息。

### 7.2 使用 Provider ID 做局部状态键

`remember(provider.id)` 可以隔离不同卡片的 `manageOpen`、`modelsOpen`、模型输入草稿。
整个页面的展开集合在状态刷新时保留，避免每次 RPC 返回后所有卡片自动合上。

### 7.3 手机端交互映射

| Web | 原生建议 |
| --- | --- |
| 卡片头点击 / 编辑按钮 | `clickable` + 明确的编辑/收起按钮 |
| 模型拖拽排序 | 上移/下移按钮（无需强行实现拖拽） |
| Checkbox / switch | `Switch`，变更调 Host，失败要可恢复 |
| Modal 管理框 | 展开管理区或 Compose Dialog，二选一保持一致 |
| 授权新 tab | 系统浏览器；设备码用可选择文本，后续可加复制能力 |
| 定时轮询 | `LaunchedEffect(hasPending)` 中循环，条件消失自动停止 |

不要为追求 DOM 一比一而复刻嵌套网页布局；应保留**功能与信息层级**，用手机可用的控件实现。

## 8. 刷新、轮询和页面生命周期

### 8.1 订阅 Host 刷新事件

需要接收 `client/refresh` 的页面可重载带 `refreshEvents` 的 `Content`：

```kotlin
@Composable
override fun Content(
    spec: JsonObject,
    theme: PluginTheme,
    host: PluginHostApi,
    refreshEvents: SharedFlow<String>?,
) {
    SubscriptionSettingsContent(theme, host, refreshEvents)
}
```

只响应本组件 ID；收到事件后重新调用权威 RPC，不要假设某个通用 `component.state` 存在。

### 8.2 授权轮询要有退出条件

```kotlin
val hasPendingLogin = providers.any { it.busy }
LaunchedEffect(hasPendingLogin) {
    while (hasPendingLogin) {
        delay(1_500)
        refresh()
    }
}
```

授权完成、取消、页面离开都会结束循环。不要写永久 `while (true)`。

### 8.3 迟到响应不得更新已离开的页面

使用 `PageLifecycle.LocalIsActive`：

```kotlin
val isActive = PageLifecycle.LocalIsActive.current

rpc.callRpc("subscription-oauth.status", JsonObject(emptyMap())) { result ->
    if (!isActive()) return@callRpc
    // 此时才更新 Compose 状态
}
```

页面组件、RPC 回调和卸载/返回是异步竞争的。仅靠 `remember` 不能阻止迟到回调。

### 8.4 返回栈

页面切换要以**页面身份**为 key。不要在监听路由注册的 `AnimatedContent` 中继续引用旧
`activePluginComponent`，否则旧内容可能再次执行 `onBack`，表现为一次返回直接退出设置。
验收时同时测：

- 原生页 → 设置主页；
- Web 回退页 → 设置主页；
- 原生页打开时装载/卸载伴生，页面不错误连退两级。

## 9. WebView 接管与卸载回退

### 9.1 正确接管条件

必须同时满足：

1. PackageManager 发现伴生；
2. ABI/签名/App 版本门禁通过；
3. 静态 `plugin()` 成功返回主 App 认可的 `DshUiPlugin`；
4. 伴生注册了 `PageContent`；
5. `jsPackage#PageEntry.id` 与当前 Web 组件一致。

满足后，原生分支**不创建 WebView**。不满足则继续使用原 `PluginComponentPane`，不能显示
空白原生页。

### 9.2 包变更和旧实例清理

宿主监听 `PACKAGE_ADDED`、`PACKAGE_REMOVED`、`PACKAGE_REPLACED` 并重扫。卸载或加载失败
时，要从页面注册表移除旧伴生实例，再发布新的路由快照。否则打开中的页面会继续持有已
卸载 APK 的 ClassLoader 和旧页面。

### 9.3 必须实测的回退矩阵

| 操作 | 预期 |
| --- | --- |
| 未装伴生，打开页面 | 原 Web UI，功能完整 |
| 安装伴生，回到 App | 原生页接管，无需改 Host |
| 伴生打开时卸载 APK | 当前页即时切回 Web UI |
| 再装伴生 | 再次恢复原生页 |
| ABI 不兼容 / 工厂失败 | Web 回退，不崩溃、不空白 |

## 10. 完整联调清单

### 构建与静态检查

```bash
./gradlew :samples:subscription-native:assembleDebug
./gradlew :app:assembleDebug
./gradlew :plugin:native-host:testDebugUnitTest
```

路由单测至少覆盖：真实包名映射、作用域包名、错误页面 ID、卸载清空、无匹配页面时回退。

### 设备检查

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r \
  samples/subscription-native/build/outputs/apk/debug/subscription-native-debug.apk
adb -s emulator-5554 shell am start -n \
  com.dsh.mobile/com.deepseek.dsh.MainActivity
```

依次验证：

- [ ] 原生页面出现，且不是只改了副标题；
- [ ] 状态来自真实 Host RPC（Auto、登录态、Provider 列表不是硬编码）；
- [ ] 原生页没有 WebView 初始化；无伴生时 WebView 正常加载；
- [ ] 每个 Provider 都能展开和收起；
- [ ] 展开区有账号/模型/管理内容，不是空卡片；
- [ ] GLM 等未登录服务显示连接、管理与登录提示；
- [ ] Auto 可展开模型列表，模型开关和排序调用正确 RPC；
- [ ] 管理区可设默认、退出、刷新令牌、重读凭据（用测试账号）；
- [ ] 授权页和设备码流程可用，取消后轮询停止；
- [ ] 返回只回上一级，不直接退出设置；
- [ ] 页面打开时卸载伴生，立即回退 Web；装回后恢复原生；
- [ ] 深色模式、长文本、空列表、Host 不可用都有可读表现。

## 11. 本次实测踩坑表

| 坑 | 实际现象 | 正确做法 |
| --- | --- | --- |
| `pluginId` 当作 JS 包名 | 原生页永远匹配不到 | 分开配置 `dsh.plugin.jsPackage` 与 `PageEntry.id` |
| 只有 `onBind`，没有静态工厂 | 服务被发现，页面不注册 | `@JvmStatic fun plugin(): DshUiPlugin` |
| 主 App/伴生各加载一份 SDK | `instanceof DshUiPlugin == false` | 父优先共享宿主 SDK/Compose 类型 |
| 只注册 `ComponentRenderer` | 设置页仍走 Web | 设置页必须注册匹配的 `PageContent` |
| 调用不存在的 RPC | 页面一直空或报错 | 使用 Host 已有方法与真实 payload |
| `callRpc()` 后立即读状态 | 总是旧数据 | 用回调 + `refreshKey` 驱动读取 |
| 只做卡片外壳 | 所有 Provider 无法展开 | 先盘点交互，再实现完整展开区和管理操作 |
| 路由更新沿用旧页面 | 一次返回直接退出设置 | 以页面身份为动画/导航 key |
| 只清路由，不清旧页面 | 卸载后仍显示旧原生页 | 包广播重扫 + 移除旧注册 + 发布新快照 |
| 迟到 RPC 回调 | 离开页面后状态被重写/崩溃 | 回调先检查 `PageLifecycle.LocalIsActive` |
| 在主线程扫描/安装 | 启动掉帧、页面卡顿 | PackageManager、反射和安装放 IO，主线程发布快照 |
| 拿编译通过当功能完成 | 原生页能开但按钮无效 | 真机逐项点击，并验证 RPC 与回退 |

完整参照实现：`samples/subscription-native`；路由与生命周期宿主：`plugin/native-host`。
