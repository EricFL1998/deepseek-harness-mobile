# 原生插件宿主接口（App 侧开发规范）

> 状态：**提案（待评审）**。本文定义 App 为支持「原生 UI 插件商店」要开发的全部通用接口与模块。
> 适用对象：App（`com.dsh.mobile`）开发团队。
> 关联文档：[插件侧 SDK 规范](native-plugin-sdk.md)、[移动端 UI 架构选型](mobile-ui-architecture.md)、[移动 bridge 协议](mobile-bridge.md)。
> 记录时间：2026-10-20。

## 目录

- [0. 摘要](#0-摘要)
- [1. 总览：八大模块](#1-总览八大模块)
- [2. 模块①：插件发现 PluginDiscovery](#2-模块插件发现-plugindiscovery)
- [3. 模块②：信任校验 SignaturePinner](#3-模块信任校验-signaturepinner)
- [4. 模块③：ABI 门禁 AbiGate](#4-模块abi-门禁-abigate)
- [5. 模块④：渲染宿主 PluginHostContainer](#5-模块渲染宿主-pluginhostcontainer)
- [6. 模块⑤：宿主能力桥 PluginHostApi 实现](#6-模块宿主能力桥-pluginhostapi-实现)
- [7. 模块⑥：主题注入 PluginTheme](#7-模块主题注入-plugintheme)
- [8. 模块⑦：商店客户端 StoreClient](#8-模块商店客户端-storeclient)
- [9. 模块⑧：管理界面](#9-模块管理界面)
- [10. 与 bridge / 现有架构的集成](#10-与-bridge--现有架构的集成)
- [11. 安全基线](#11-安全基线)
- [12. 落地顺序与验收](#12-落地顺序与验收)
- [13. 风险与对策](#13-风险与对策)

---

## 0. 摘要

插件侧契约（manifest、SDK API、能力声明）由 [native-plugin-sdk.md](native-plugin-sdk.md) 定义；本文是硬币的另一面：**App 要让这些 APK「能发现、敢加载、兜得住、可管理」需要开发的通用接口**。

设计原则：

1. **增强层定位**：原生插件体系是 Web-first 路线（[mobile-ui-architecture.md](mobile-ui-architecture.md) ADR-006）之外的增强层，不是替代品；Web 兜底永远在场；
2. **铁律守门**：App 侧实现保证「逻辑在 Host、凭证不出 Host」——`PluginHostApi` 不提供网络/凭证/任意文件口子；
3. **崩溃不上溯**：任何插件渲染异常被围栏捕获，App 与其他插件不受影响；
4. **协议复用**：与 Host 的通信全部走现有 bridge（`dispatch` / `client/refresh` / `events`），不发明新协议。

---

## 1. 总览：八大模块

| # | 模块 | 职责 | 复用现有 | 工作量 |
| --- | --- | --- | --- | --- |
| ① | PluginDiscovery | 发现已装插件 APK，监听安装/卸载 | PackageManager | 中 |
| ② | SignaturePinner | 签名指纹钉死，防掉包 | `PackageInfo.signingInfo` | 中 |
| ③ | AbiGate | apiVersion/minAppVersion/Compose 版本门禁 | 新写 | 小 |
| ④ | PluginHostContainer | Compose 宿主容器：约束 + 错误围栏 + 降级 | Genui.kt 容器经验 | 大 |
| ⑤ | PluginHostApi 实现 | 能力桥：dispatchEvent/openUrl/storage/notify/share/clipboard/workspace | bridge dispatch 链路现成 | 大 |
| ⑥ | PluginTheme | App 主题 token → 插件只读快照 | core/designsystem | 小 |
| ⑦ | StoreClient | 商店索引、兼容标注、下载、拉起安装 | 现有 HTTP 栈 | 中 |
| ⑧ | 管理界面 | 插件列表、能力展示、总开关、卸载入口 | feature/settings 骨架 | 中 |

工作量集中在 ④⑤：前者决定稳定性，后者决定安全边界。

建议落点：新建 `plugin/native-host/` Gradle 模块（与 `plugin/mobile-ui/` 平级），`core/bridge` 复用。

---

## 2. 模块①：插件发现 PluginDiscovery

```kotlin
interface PluginDiscovery {
    /** 当前已装且通过初步校验的插件；状态流，UI 直接订阅 */
    val plugins: StateFlow<List<InstalledPlugin>>

    data class InstalledPlugin(
        val pluginId: String,          // dsh.plugin.id
        val packageName: String,       // APK 包名
        val version: Int,              // descriptor.version
        val versionCode: Int,          // APK versionCode
        val apiVersion: Int,
        val minAppVersion: Int,
        val signatureFingerprint: String,
        val status: Status,            // READY / ABI_TOO_NEW / SIGNATURE_MISMATCH / DISABLED
        val serviceComponent: ComponentName,
    )

    enum class Status { READY, ABI_TOO_NEW, SIGNATURE_MISMATCH, DISABLED }
}
```

实现要点：

- 发现：`PackageManager.queryIntentServices(Intent("com.deepseek.dsh.action.PLUGIN_UI"), MATCH_ALL)`，读取每个 service 的 meta-data 四项；
- 联动：注册广播监听 `PACKAGE_ADDED` / `PACKAGE_REMOVED` / `PACKAGE_REPLACED`（带自身 data scheme 过滤），即时增删条目；卸载必须**当帧摘除渲染位**，不留悬垂引用；
- 启动时全量重扫一次（覆盖 App 未运行时发生的安装/卸载）；
- 只读 meta-data，**不在此阶段 bind/加载任何代码**——加载决策在 ②③ 之后。

---

## 3. 模块②：信任校验 SignaturePinner

```kotlin
interface SignaturePinner {
    /** 通过：商店登记指纹与实装一致，且与首次钉死一致 */
    fun verify(plugin: InstalledPlugin): VerifyResult

    sealed interface VerifyResult {
        data object Trusted : VerifyResult
        data class FirstSeen(val fingerprint: String) : VerifyResult   // 钉死并继续
        data class Mismatch(val expected: String, val actual: String) : VerifyResult
        data object UnknownToStore : VerifyResult                       // 商店索引查无此 id
    }
}
```

规则：

- **首次钉死（TOFU）**：首次见到某 pluginId，其指纹须出现在商店索引（见模块⑦）中；一致则本地钉死并加载；
- **签名变更 = 掉包**：后续版本指纹变了 → `SIGNATURE_MISMATCH`，拒绝加载并向用户明示「身份不符」；不允许静默放行；
- 钉死数据落盘（Room/DataStore），随 App 数据清除而重置；
- 商店索引不可达（离线）：已钉死插件照常可用；未见过的新插件暂不可用并提示联网。

---

## 4. 模块③：ABI 门禁 AbiGate

```kotlin
interface AbiGate {
    fun check(plugin: InstalledPlugin): GateResult

    sealed interface GateResult {
        data object Pass : GateResult
        data class PluginTooNew(val requiredApi: Int, val appApi: Int) : GateResult
        data class AppTooOld(val minApp: Int, val app: Int) : GateResult
        data class ComposeMismatch(val pluginCompose: String, val appCompose: String) : GateResult
    }
}
```

- 三方比对：`apiVersion`（≤ App 支持的版本）、`minAppVersion`（≤ App 版本码）、插件 APK meta-data 里 SDK 记录的 Compose 版本（= App 内 Compose 版本）；
- 任一不匹配 → 状态置 `ABI_TOO_NEW` 等，UI 显示「插件需更新 / 请升级 App」，**绝不 try 加载**（Compose ABI 不兼容的加载结果就是 `IncompatibleClassChangeError`，必须在门外挡住）；
- App 支持的 apiVersion 列表随发版更新，写在 `BuildConfig`。

---

## 5. 模块④：渲染宿主 PluginHostContainer

每个插件 UI 都在此容器内运行；这是「兜得住」的核心。

```kotlin
@Composable
fun PluginHostContainer(
    plugin: InstalledPlugin,
    spec: JsonObject,
    fallback: @Composable () -> Unit,   // 降级内容（WebView 入口/转译树占位）
) {
    when {
        plugin.status != Status.READY -> fallback()
        else -> Box(Modifier.pluginConstraints()) {   // 最大尺寸/滚动边界/触控约束
            ResultCatching(onError = { PluginFallbackCard(plugin.name, it) }) {
                val renderer = remember(plugin) { PluginRendererHolder.get(plugin) }
                renderer.Render(spec, hostApiFor(plugin))
            }
        }
    }
}
```

职责清单：

- **尺寸约束**：插件内容限制在宿主分配的区域（max height、滚动裁剪），不许撑破布局；
- **错误围栏**：渲染器抛任何异常 → 捕获 → 占位块（插件名 + 「此插件界面出错」+ 重试）；**绝不上溯**崩聊天/设置页；
- **渲染器缓存**：`PluginRendererHolder` 按 pluginId 缓存 `bindService` 拿到的渲染器实例；卸载/停用即释放；
- **重组边界**：spec 更新只触发该容器内重组；插件之间无共享 CompositionLocal（主题快照除外，只读）；
- **耗时保护**：首帧渲染超时（如 2s）降级为占位 + 异步重试。

---

## 6. 模块⑤：宿主能力桥 PluginHostApi 实现

插件唯一摸得到的 App 表面；逐个能力实现，全部带**声明校验 + 白名单 + 审计**。

```kotlin
class PluginHostApiImpl(
    private val plugin: InstalledPlugin,
    private val bridge: BridgeRegistry,        // 现成
    private val audit: PluginAuditLog,
) : PluginHostApi {

    override fun dispatchEvent(componentId: String, event: String, payload: JsonObject) {
        require(componentId.startsWith(plugin.pluginId)) { "componentId 前缀不符" }
        bridge.dispatch(componentId, event, payload)     // → client-slots/dispatch，现成链路
        audit.record(plugin.pluginId, "dispatch:$event")
    }

    override fun openUrl(url: String) {
        if (!UrlAllowlist.check(url)) { /* 转系统浏览器并确认 */ }
        audit.record(plugin.pluginId, "openUrl")
    }

    override fun currentTheme(): PluginTheme = ThemeBridge.snapshot()   // 只读

    override fun requestCapability(capability: String): Boolean =
        capability in plugin.declaredCapabilities && CapabilitySwitches.isEnabled(capability)

    override fun storage(): PluginStorage =
        NamespacedStorage(plugin.pluginId)   // 每插件独立 namespace，禁读他人
}
```

能力实现矩阵（v1）：

| 能力 | 实现 | 白名单/限制 |
| --- | --- | --- |
| `openUrl` | Custom Tabs | 域白名单；其余系统浏览器 + 用户确认 |
| `storage` | DataStore 按 pluginId 分区 | 禁跨插件读写 |
| `notify` | 壳层前台服务通知通道（复用 Host 事件通知设施） | 用户全局开关；带深链回插件页 |
| `share` | `ACTION_SEND` | 系统面板 |
| `clipboard` | `ClipboardManager.setPrimaryClip` | 只写 |
| `workspace` | 经 SAF 映射的宿主工作区服务 | 路径前缀校验，仅授权目录内 |

审计：`PluginAuditLog` 滚动记录每次能力调用（pluginId、能力、时间），设置页可查看，导出前脱敏。

---

## 7. 模块⑥：主题注入 PluginTheme

```kotlin
/** 插件可见的主题只读快照；深色/字体缩放变化时更新并触发重组 */
data class PluginTheme(
    val colorPrimary: Color, val colorSurface: Color, val colorOnSurface: Color,
    val textTitle: TextStyle, val textBody: TextStyle, val textCaption: TextStyle,
    val shapeCard: Shape, val spacingUnit: Dp, val isDark: Boolean,
)

object ThemeBridge {
    fun snapshot(): PluginTheme          // 从 core/designsystem 取当前 token
    val flow: StateFlow<PluginTheme>     // 变化流
}
```

- 快照只读；插件拿不到 `Context`、`Resources`；
- 深色模式 / 系统字体缩放由 App 统一映射进快照，插件不写分支；
- 与 Web 移动适配同源：token 取值尽量与 DSH Web 主题对齐，减少两个前端的视觉漂移。

---

## 8. 模块⑦：商店客户端 StoreClient

v1 从简：商店索引是一个**签名 JSON**，随现有 Release 通道分发。

```kotlin
interface StoreClient {
    suspend fun fetchIndex(): StoreIndex?   // 离线返回 null，已钉死插件不受影响

    data class StoreIndex(
        val plugins: List<Entry>,
        val signature: String,               // 商店私钥签名，App 内置公钥验
    )
    data class Entry(
        val pluginId: String, val name: String, val version: Int,
        val apkUrl: String, val sha256: String,
        val signatureFingerprint: String,     // 登记指纹 → SignaturePinner
        val apiVersion: Int, val minAppVersion: Int,
        val capabilities: Set<String>,
        val screenshots: List<String>, val description: String,
    )
}
```

- **兼容标注**：拉取后与当前 App 版本比对，商店页标「兼容 ✓ / 需升级 App」；
- **下载安装**：HTTP 下载 → SHA-256 校验 → `PackageInstaller`/系统安装器 Intent 拉起；安装完成后由 PluginDiscovery 的广播接管；
- **更新检查**：索引 version > 已装 versionCode → 设置页/商店页提示；更新流程同安装；
- 索引验签失败或格式不符 → 整体丢弃，不部分采信。

---

## 9. 模块⑧：管理界面

设置页新增「原生插件」分组（复用 `feature/settings` 骨架）：

- **列表**：图标、名称、版本、状态（正常/待更新/身份不符/已停用）、能力声明、签名指纹缩写；
- **详情页**：能力开关（逐项）、审计记录、卸载入口（跳系统卸载页）；
- **总开关**：「禁用全部插件 UI」——一键停用全部原生渲染，统一降级 WebView；
- **商店入口**：打开商店页（WebView 内嵌或系统浏览器）。

---

## 10. 与 bridge / 现有架构的集成

```
┌─ App ────────────────────────────────────────────────┐
│  plugin/native-host/                                  │
│   PluginDiscovery → SignaturePinner → AbiGate         │
│        ↓ READY                                        │
│   PluginHostContainer ─→ PluginHostApiImpl            │
│        ↑ spec                ↓ dispatchEvent          │
│  core/bridge（现成）: client-slots/dispatch、events    │
└───────↕───────────────────────────────────────────────┘
   Host: bridge 插件 → JS 插件逻辑层（不动）
```

- **通信**：`dispatchEvent` → `client-slots/dispatch` op → `ui/<componentId>/<event>` 总线 → JS；状态回流走 `client/refresh` → App 重拉 spec。全部现成，零新协议；
- **spec 来源**：原生渲染器消费的 spec 与 bridge 的 RenderTree 数据模型同源（JSON 快照），由 Host 侧 `client/refresh` 的 patch 或重拉产生；
- **与 Web-first 的关系**：原生插件是增强层。聊天/设置/插件页主体仍是 Web（架构文档路线 C→D）；原生渲染位只出现在「插件声明了 supportedTypes 且命中」的局部（设置 section、工具卡等），未命中/未安装一律 WebView；
- **与旧转译链的关系**：bridge 的 transpile/client-slots UI 半边在原生插件覆盖的槽位上退居第三兜底（Web → 原生插件 → 转译树）。

---

## 11. 安全基线

1. 插件 APK 跑在 App 进程：商店审核 + 签名钉死是底线（见 SDK 文档 §11）；
2. `PluginHostApi` 无网络、无凭证、无任意文件、无反射口子；
3. 每次能力调用记审计；日志导出前脱敏；
4. 总开关与逐项开关并存；插件卸载即时摘除；
5. 商店索引签名验签；APK 下载 SHA-256 校验后才交安装器；
6. 插件服务绑定校验 `com.deepseek.dsh.permission.BIND_PLUGIN` 签名级权限，防止第三方 App 伪造插件端点。

---

## 12. 落地顺序与验收

### Phase 0：契约与自证（发现 + 门禁 + 宿主）

- 交付：①③④ + `dsh-plugin-sdk` 1.0；
- **关键自证**：把 `Genui.kt` 从 App 拆出，改造成第一个走完整链路的原生插件。拆不动 = 契约有问题，改契约再继续；
- 验收：样例插件安装/卸载/异常崩溃时 App 稳定，降级正确。

### Phase 1：能力桥与主题

- 交付：⑤⑥，`openUrl` + `storage` 两个能力先行，其余按 SDK 表补齐；
- 验收：Kimi 示例插件（SDK 文档 §13）端到端跑通 OAuth（dispatchEvent → Host → Custom Tab → 回跳 → 状态刷新）。

### Phase 2：信任与管理

- 交付：②⑧，签名钉死、总开关、设置页管理界面、审计查看；
- 验收：签名变更的更新包被拒；卸载联动即时；总开关联动全部降级。

### Phase 3：商店

- 交付：⑦，签名索引 JSON + 下载安装流 + 兼容标注；先第一方插件上架，再邀请制；
- 验收：从商店页到原生渲染可用的完整闭环；离线时已装插件不受影响。

---

## 13. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| Compose ABI 耦合 | 版本错位即崩 | AbiGate 门外拦截 + SDK 锁版本 + 降级路径 |
| 第三方代码进程内作恶 | 数据泄露/崩溃 | 签名钉死 + 商店审核 + PluginHostApi 窄口 + 错误围栏 |
| 商店索引被篡改 | 装到恶意插件 | 索引签名验签 + APK SHA-256 + 指纹钉死 |
| 插件卸载残留 | 悬垂引用崩溃 | PACKAGE_REMOVED 当帧摘除 + 启动全量重扫 |
| 双前端体验漂移 | 视觉/行为不一致 | PluginTheme 与 Web token 同源；规范要求降级可用 |
| 生态冷启动 | 无第三方插件 | Phase 0 自拆 Genui 自证；先第一方上架 |
| 与 Web-first 资源争抢 | 路线分裂 | 定位增强层写入 ADR-006；Web 兜底永远在 |
