# 原生插件 SDK 规范（插件侧契约）

> 状态：**提案（待评审）**。本文定义「原生 UI 插件商店」体系里第三方开发者要遵守的全部契约。
> 适用对象：想为 DSH 手机 App 提供原生（Kotlin/Compose）UI 的插件开发者。
> 关联文档：[JS UI 转原生 UI 实战指南](plugin-conversion-guide.md)、[SDK 开发指南](plugin-sdk-guide.md)、[App 侧宿主接口](native-plugin-host.md)、[移动端 UI 架构选型](mobile-ui-architecture.md)、[移动 bridge 协议](mobile-bridge.md)。
> 更新：2026-10-21。本文定义目标契约；当前可编译 API 与模拟器验证步骤以开发指南为准。

## 目录

- [0. 摘要](#0-摘要)
- [1. 体系定位：原生 APK 是 Host 逻辑的第二张脸](#1-体系定位原生-apk-是-host-逻辑的第二张脸)
- [2. 铁律：逻辑永远在 Host，凭证永远不出 Host](#2-铁律逻辑永远在-host凭证永远不出-host)
- [3. 包形态：npm 包 + 伴侣 APK](#3-包形态npm-包--伴侣-apk)
- [4. APK 清单契约（AndroidManifest）](#4-apk-清单契约androidmanifest)
- [5. SDK API 契约](#5-sdk-api-契约)
- [6. 主题契约](#6-主题契约)
- [7. 能力白名单](#7-能力白名单)
- [8. 与 Host 侧 JS 的通信](#8-与-host-侧-js-的通信)
- [9. 降级契约](#9-降级契约)
- [10. 分发与商店提交](#10-分发与商店提交)
- [11. 审核清单](#11-审核清单)
- [12. 版本与兼容](#12-版本与兼容)
- [13. 完整示例：Kimi OAuth 原生插件](#13-完整示例kimi-oauth-原生插件)
- [14. 禁止事项速查](#14-禁止事项速查)

---

## 0. 摘要

DSH 手机 App 开放一套「原生 UI 插件」体系：第三方开发者可以为已有（或新的）JS 插件附带一个 **Kotlin/Compose 渲染器**，以独立 APK 的形式经商店分发，装入 App 后把插件 UI 从 WebView/转译树升级为原生体验。

核心承诺：

- **逻辑层零重写**：插件的 Host 侧逻辑（`lib/index.js`：OAuth、存储、模型 API、事件）原封不动继续跑；原生 APK 只写「前端逻辑」——画 spec、回传意图；
- **通信零新协议**：原生端与 Host 之间走现有 bridge 的 `dispatch` op 与 `client/refresh` 事件，一行新协议不发明；
- **降级永远可用**：用户不装原生 APK，插件走 WebView 完整可用；装了则增强。原生层是增强，不是必需。

---

## 1. 体系定位：原生 APK 是 Host 逻辑的第二张脸

DSH 插件天然分层：

| 层 | 产物 | 运行位置 | 谁写 |
| --- | --- | --- | --- |
| 逻辑层 | `lib/index.js` | Host（手机内 proot/Node） | 第三方，已完成 |
| Web 前端 | `lib/client.js` | 浏览器 / WebView | 第三方，已完成 |
| **原生前端** | **伴侣 APK** | **App 进程（Compose 宿主容器内）** | **第三方，新增（本规范）** |
| 通信协议 | bridge `dispatch` / `client/refresh` | Host 内 | DSH，现成 |
| 宿主接口 | `PluginHostApi` 实现 | App 内 | DSH，见 [native-plugin-host.md](native-plugin-host.md) |

```
┌─ Host（proot/Node）──────────────────────────┐
│  kimi-oauth/lib/index.js                     │
│  OAuth 状态机 · token · 用量查询 · 事件监听    │
│        ↕ client/refresh / dispatch           │
│  bridge 插件（现成）                           │
└───────↕────────────────↕──────────────────────┘
   前端 A：浏览器          前端 B：原生 APK
   lib/client.js (React)   KimiRenderer.kt (Compose)
   桌面 / Web 用户          手机用户
```

两个前端消费**同一份状态流**、回传**同一种事件**，互不感知。

---

## 2. 铁律：逻辑永远在 Host，凭证永远不出 Host

这是规范的第一条，也是商店审核的底线：

1. **原生 APK 不做业务逻辑**：不发起 OAuth 流程、不刷新 token、不直接调用模型 API。想做事，只能 `dispatchEvent` 让 Host 侧 JS 去做；
2. **凭证不出 Host**：token、OAuth secret、API key 只存在于 Host 进程（proot）内。`PluginHostApi` 不提供任何读取凭证的口子；spec 里只允许出现「已登录/剩余额度」这类纯展示数据；
3. **渲染器无状态**：原生渲染器只消费 spec（JSON 快照），不持有网络连接、不缓存凭证、不跨调用保存业务状态；
4. **审核兜底**：商店静态扫描拒绝 APK 内直连模型 API、读取 Host 目录、反射访问 App 内部类的行为。

这条边界同时是安全架构：原生插件 APK 是 App 进程内的第三方代码，物理上碰不到凭证，攻击面因此大幅收窄。

---

## 3. 包形态：npm 包 + 伴侣 APK

### 3.1 为什么原生层是 APK 而不是 dex

| 维度 | 裸 dex 下载 | 伴侣 APK（本规范选择） |
| --- | --- | --- |
| 签名校验 | 自己实现 | PackageManager 系统级完成 |
| 安装确认 | 无系统 UI | 系统安装器，用户可见 |
| 版本管理 | 自己实现 | versionCode/versionName 系统级 |
| 卸载联动 | 自己监听 | `PACKAGE_REMOVED` 广播，自动清理 |
| Play 政策 | **禁止**（下载可执行代码） | 合法（用户主动安装的应用） |

代价是用户多一次「安装另一个 App」的确认；商店 UI 把这个流程包装成「安装插件」。

### 3.2 两个包的关联

- npm 包（如 `@changfenhuang/dsh-kimi-oauth`）：不变，走现有插件安装通道进 Host；
- 伴侣 APK（如 `com.example.kimi.oauth.native`）：走商店分发，由系统安装器装入；
- 伴生身份是 **`pluginId`**（如 `changfenhuang.kimi-oauth`），用于签名、商店与注册归属；
- JS 配对键是 **`jsPackage`**（如 `dsh-plugin-kimi-oauth`），在 Service 的 `dsh.plugin.jsPackage` 声明。两者可以不同；
- 页面接管键是 **`<jsPackage>#<PageEntry.id>`**，与 bridge 的实际组件身份一致。App 据此把「JS 插件注册的槽位」和「原生页面」配对，不能仅凭 pluginId 猜包名。

### 3.3 插件可以只有一边

- 纯 JS 插件：不受影响，走 WebView/转译树；
- 纯原生插件：允许，但逻辑仍须在某个 Host 插件里（可是一个极薄的配套 JS 包）；**不允许 APK 自带业务逻辑**（见第 2 节）。

---

## 4. APK 清单契约（AndroidManifest）

插件 APK 必须声明以下 meta-data 与服务，缺一不可：

```xml
<manifest package="com.example.kimi.oauth.native">

    <!-- 入口服务：App 通过 action 发现插件，并读取 service 级 meta-data -->
    <service
        android:name=".KimiPluginService"
        android:exported="true"
        android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">
        <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
        <meta-data android:name="dsh.plugin.minAppVersion" android:value="12" />
        <meta-data android:name="dsh.plugin.id" android:value="changfenhuang.kimi-oauth" />
        <meta-data android:name="dsh.plugin.name" android:value="Kimi 登录" />
        <meta-data android:name="dsh.plugin.jsPackage" android:value="dsh-plugin-kimi-oauth" />
        <intent-filter>
            <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
        </intent-filter>
    </service>

    <!-- 可选：OAuth 等深链回跳。scheme 固定 dsh-plugin，host 必须是 pluginId -->
    <activity android:name=".OAuthReturnActivity" android:exported="true">
        <intent-filter>
            <data android:scheme="dsh-plugin" android:host="changfenhuang.kimi-oauth" />
        </intent-filter>
    </activity>
</manifest>
```

规则：

- `apiVersion` 是整数，SDK 破坏性变更时 +1；App 低于 `minAppVersion` 时**拒绝加载**（显示「待更新」，不崩溃）；
- `dsh.plugin.id` 一经发布**不可更改**——它是签名钉死和商店身份的锚；
- `android:permission="com.deepseek.dsh.permission.BIND_PLUGIN"` 必须原样写上，防止其他 App 绑定你的服务；
- 插件 APK **不申请任何系统权限**（无 `<uses-permission>`）；需要能力走第 7 节的能力桥声明。

---

## 5. SDK API 契约

依赖：`implementation("com.deepseek.dsh:plugin-sdk:1.x")`。SDK 锁定与 App 一致的 Kotlin/Compose 版本；插件以 `compileOnly` 方式使用 Compose runtime（运行时用 App 的，杜绝版本耦合）。

### 5.1 插件入口

```kotlin
interface DshUiPlugin {
    val descriptor: PluginDescriptor
    fun createRenderer(): ComponentRenderer
}

data class PluginDescriptor(
    val id: String,                  // 与清单 dsh.plugin.id 一致
    val name: String,
    val version: Int,                // 插件自身版本，随发布递增
    val capabilities: Set<String>,   // 见第 7 节，未声明的能力调用即拒
)
```

服务类照抄模板，只换插件实例。`onBind` 与静态 `plugin()` 都要保留：前者服务绑定，
后者让 App 在自己的进程实例化并注册页面。主 App 与伴生共享父优先加载的同一份
SDK/Compose 类型，禁止插件再用隔离 ClassLoader 或嵌套 dex 加载绕开 ABI：

```kotlin
class KimiPluginService : Service() {
    override fun onBind(intent: Intent): IBinder =
        DshPluginBinder(KimiOAuthPlugin())

    companion object {
        /** App 在自身进程加载插件时使用的必需工厂。 */
        @JvmStatic
        fun plugin(): DshUiPlugin = KimiOAuthPlugin()
    }
}
```

### 5.2 渲染器

```kotlin
interface ComponentRenderer {
    /** 声明本渲染器能画的 spec 类型；App 命中才走原生，否则降级 */
    val supportedTypes: Set<String>

    /**
     * 画一个 spec。运行在 App 提供的宿主容器内（尺寸约束/错误围栏），
     * 插件只画内容区。拿不到 Context、View、App 内部对象——只有 host。
     */
    @Composable
    fun Render(spec: JsonObject, host: PluginHostApi)
}
```

### 5.3 宿主 API（插件唯一摸得到的 App 表面）

```kotlin
interface PluginHostApi {
    /** 回传用户意图给 Host 侧 JS（走 bridge dispatch op，现成协议） */
    fun dispatchEvent(componentId: String, event: String, payload: JsonObject)

    /** 打开链接：域白名单内直接开，其余经系统浏览器确认 */
    fun openUrl(url: String)

    /** 主题 token 只读快照；深色模式切换时更新并触发重组 */
    fun currentTheme(): PluginTheme

    /** 申请能力（通知/存储等）；未声明或用户关闭返回 false */
    fun requestCapability(capability: String): Boolean

    /** 每插件独立命名空间的键值存储；禁读其他插件 */
    fun storage(): PluginStorage
}

interface PluginStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}
```

**明确没有的东西**（有即违规）：网络、`Context`、Host token、文件系统任意路径、其他插件的存储、反射入口。

---

## 6. 主题契约

- 颜色、字号、形状、间距**全部**取自 `host.currentTheme()` 的 token；
- 禁止硬编码色值/字号——商店静态扫描 + 人工抽查；
- 深色模式、系统字体缩放由 App 统一处理；插件不写 `isSystemInDarkTheme()` 分支，主题快照已含正确取值；
- 触控目标 ≥44dp、无 hover 依赖，与 Web 移动适配清单对齐（见 [mobile-ui-architecture.md](mobile-ui-architecture.md) §8）。

---

## 7. 能力白名单

能力版本化、声明制、用户可控。v1 开放六项：

| 能力 | 声明值 | 调用 | App 侧策略 |
| --- | --- | --- | --- |
| 打开链接 | `openUrl` | `host.openUrl(url)` | 域白名单；其余系统浏览器确认 |
| 独立存储 | `storage` | `host.storage().get/put` | 每插件独立 namespace |
| 通知 | `notify` | `requestCapability("notify")` 后 SDK 回调接口 | 前台服务通道；用户可全局关 |
| 分享 | `share` | 经 SDK 扩展函数 | 系统分享面板 |
| 剪贴板 | `clipboard` | 经 SDK 扩展函数 | 只写不读 |
| 工作区文件 | `workspace` | 经 SDK 扩展函数 | 仅 SAF 授权目录内，路径前缀校验 |

纪律：

- **只增不改**：已有能力语义不变；破坏性变更升 `apiVersion`；
- 相机/定位/联系人 **v1 不开放**，未来开放须同步升 `apiVersion` 并进商店页明示；
- 未在 `descriptor.capabilities` 声明的能力，调用返回 `false`/空操作，并记审计日志。

---

## 8. 与 Host 侧 JS 的通信

完全复用 bridge 现成链路（[mobile-bridge.md](mobile-bridge.md)）：

```
原生端 host.dispatchEvent(pluginId, "start-oauth", payload)
  → App bridge client 调 client-slots/dispatch
  → Host 事件总线 ui/<componentId>/<event>
  → JS 插件监听器处理（已有代码）
  → JS 调 ctx.emit('client/refresh', { componentId, patch })
  → App 收到刷新，重拉 spec
  → 原生渲染器 Render() 重组
```

对插件作者的要求：

1. **componentId 约定**：使用 Host 实际的 `<jsPackage>#<slot 内 id>`，与 JS 侧 `ctx.slots.register` 的包名和 id 对应；伴生 `pluginId` 可以不同；
2. **JS 侧推状态**：把原生端要画的数据放进 `client/refresh` 的 `patch`（或直接重新 register），保证 spec 是 JSON 安全的纯展示数据（无凭证、无函数、无循环引用）；
3. **已有 RPC**：完整页面可通过 `PluginRpcHost.callRpc(method, payload, callback)` 调用 Host 已有路由，payload 原样发送；先解 `{ result: { ok, value | error } }` 信封。不要虚构通用 `component.state` / `component.dispatch` 路由；
4. **页面生命周期**：带 `refreshEvents` 的 `PageContent.Content` 可响应 Host 刷新；异步回调先检查 `PageLifecycle.LocalIsActive`，避免迟到结果更新已离开的页面。

---

## 9. 降级契约

原生层永远是增强，不是必需：

| 场景 | 行为 |
| --- | --- |
| 用户未装伴侣 APK | 插件走 WebView（完整界面）或转译树，功能完整 |
| APK 已装但 ABI 不兼容 | 显示「插件需更新」，降级 WebView |
| 渲染器 `supportedTypes` 未命中 spec 类型 | 该组件降级，其余原生 |
| 渲染器抛异常 | 错误围栏捕获，显示占位块，不影响 App 与其他插件 |
| 用户卸载 APK | `PACKAGE_REMOVED` 触发即时摘除渲染位，回退 WebView |
| 用户打开「禁用全部插件 UI」总开关 | 全部原生插件 UI 停用，统一降级 |

JS 侧因此**不得假设**原生端在场：所有功能必须在纯 Web 下可用。

---

## 10. 分发与商店提交

1. **开发**：用 `dsh-plugin-sdk` 建 Android 工程，按第 4–7 节实现；
2. **自验**：SDK 自带 `plugin-lint`（清单完整性、能力声明一致性、禁止 API 扫描）+ 示例宿主 App；
3. **提交商店**：上传 APK + 源码仓库地址 + 截图 + 能力说明；
4. **商店登记**：记录 pluginId 与签名证书指纹（永久身份）；审核（第 11 节）通过进索引；
5. **用户安装**：商店页 → 下载 → 系统安装器 → App 自动发现（见 [native-plugin-host.md](native-plugin-host.md) §3）；
6. **更新**：APK versionCode 递增、签名不变即可；签名变更视为新插件（防掉包），商店拒绝同 pluginId 覆盖。

---

## 11. 审核清单

静态扫描（自动）：

- [ ] Service 级 `apiVersion`/`minAppVersion`/`id`/`name` 齐全，需要 JS 配对时声明 `jsPackage`；
- [ ] `pluginId` 与 descriptor 一致；`jsPackage#PageEntry.id` 与 Host 实际组件身份一致；
- [ ] Service 暴露 `@JvmStatic fun plugin(): DshUiPlugin`；
- [ ] 无 `<uses-permission>`；
- [ ] 禁止 API 零命中：反射访问 `com.deepseek.dsh.*` 内部类、`DexClassLoader`/`PathClassLoader` 再嵌套动态加载、读 `/data/data/com.deepseek.dsh.mobile` 私有目录、直连模型 API 域名；
- [ ] 无硬编码色值/字号（主题契约）；
- [ ] `capabilities` 声明与实际调用一致。

人工抽查：

- [ ] 截图与实装一致；能力说明无夸大；
- [ ] 无凭证收集 UI（密码框、token 输入框）；
- [ ] 降级路径可用（卸载 APK 后 JS 插件仍完整可用）。

---

## 12. 版本与兼容

| 版本 | 含义 | 变更规则 |
| --- | --- | --- |
| `apiVersion` | SDK/App 接口版本 | 破坏性变更 +1；App 按门禁拒绝过低版本 |
| `minAppVersion` | 插件要求的最低 App 版本码 | 插件发布时声明，App 低于此值拒载 |
| `descriptor.version` | 插件自身版本 | 每次发布递增，商店展示 |
| APK `versionCode` | 系统安装版本 | 每次发布递增，PackageManager 管理 |

兼容矩阵：

| 插件 apiVersion | App apiVersion | 行为 |
| --- | --- | --- |
| 相等 | — | 正常 |
| 插件 < App | — | 允许，按能力检测降级 |
| 插件 > App | — | 拒载，提示升级 App |

---

## 13. 完整示例：Kimi OAuth 原生插件

假设你是 `@changfenhuang/dsh-kimi-oauth` 的作者。该插件已有：

- `lib/index.js`：OAuth 流程、token 存储、用量查询、事件监听；
- `lib/client.js`：React 设置页（订阅卡片、登录按钮）。

现在要新增「原生手机端插件」。

### 13.1 工程结构

```
kimi-oauth/                        ← 现有 JS 插件（不动）
├── lib/client.js
└── lib/index.js

kimi-oauth-native/                 ← 新增 Android 工程
├── build.gradle                   ← implementation("com.deepseek.dsh:plugin-sdk:1.0.0")
└── src/main/
    ├── AndroidManifest.xml        ← 见第 4 节
    └── kotlin/com/example/kimi/oauth/native/
        ├── KimiPluginService.kt
        └── KimiOAuthPlugin.kt
```

### 13.2 原生端全部代码

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
        capabilities = setOf("openUrl", "storage"),
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
                host.openUrl(state.str("authorizeUrl"))   // Custom Tab，域白名单校验
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

注意这份代码里**没有**：网络请求、token、OAuth 状态机、存储凭证——全部在 Host 侧。

### 13.3 JS 侧需要的配合（通常已在写）

```js
// lib/index.js —— 监听原生端事件（与 Web 端共用同一监听器）
ctx.on('ui/changfenhuang.kimi-oauth#section/start-oauth', async () => {
  const url = await beginOAuth();   // 已有逻辑
  ctx.emit('client/refresh', {
    componentId: 'changfenhuang.kimi-oauth#section',
    patch: { state: { status: 'authorizing', authorizeUrl: url } },
  });
});

// 登录成功 / 用量变化时同样 emit client/refresh，spec 里只放展示数据：
// { state: { status: 'active', quotaRemaining: 1234 } } —— 永远不放 token。
```

### 13.4 用户视角的流程

1. 用户在 App 插件页安装 JS 插件（现有流程，不变）；
2. 商店页看到「Kimi 登录 · 原生增强」：截图、能力声明（打开链接、独立存储）、签名指纹、「兼容当前 App ✓」；
3. 一键安装 → 系统安装器 → App 自动发现、验签、ABI 校验通过；
4. 设置页「Kimi」条目、聊天工具卡升级为原生渲染；OAuth 走 Custom Tab + `dsh-plugin://changfenhuang.kimi-oauth` 回跳；
5. 用户卸载原生 APK → 插件自动回退 WebView，功能完整。

---

## 14. 禁止事项速查

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
