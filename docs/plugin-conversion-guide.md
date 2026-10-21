# 把 JS 插件转成原生伴侣插件：转换指南

> 状态：**已自证**。本指南由三个真实样例（`samples/genui-native`、`samples/kimi-native`、`samples/subscription-native`）的移植过程提炼，全部经模拟器真机验证。
> 适用对象：已有 DSH JS 插件、想给它加原生手机端 UI 的开发者。
> 前置：先读 [plugin-sdk-guide.md](plugin-sdk-guide.md) 了解 SDK 全貌；本文是「怎么转」的实操手册。
> 记录时间：2026-10-20。

## 目录

- [0. 一句话](#0-一句话)
- [1. 转换前的判断](#1-转换前的判断)
- [2. 转换流程总览](#2-转换流程总览)
- [3. 第 1 步：建 Android 工程](#3-第-1-步建-android-工程)
- [4. 第 2 步：写 manifest（四要素）](#4-第-2-步写-manifest四要素)
- [5. 第 3 步：写插件入口](#5-第-3-步写插件入口)
- [6. 第 4 步：选渲染方式](#6-第-4-步选渲染方式)
- [7. 第 5 步：JS 侧配合（推状态）](#7-第-5-步js-侧配合推状态)
- [8. 第 6 步：打包、装机、验证](#8-第-6-步打包装机验证)
- [9. 三种典型的转换形态](#9-三种典型的转换形态)
- [10. 移植踩坑清单](#10-移植踩坑清单)

---

## 0. 一句话

**逻辑层一行不动**——你的 `lib/index.js`（OAuth、存储、模型 API、事件）继续跑在 Host。你只新增一个 Kotlin/Compose 的「原生半边」，画 UI、回传意图。

## 1. 转换前的判断

先问自己：这个插件的手机端体验，是 WebView 做不到、必须原生的吗？

- 是 → 转（系统级交互、跟手的卡片、深链、原生性能）；
- 只是「想在手机上看」→ 不必转，Web 界面本来就完整可用。原生层是增强，不是必需。

## 2. 转换流程总览

```
已有 JS 插件（lib/index.js + lib/client.js）
        │
        ├─ 不动：逻辑层继续跑在 Host
        │
        └─ 新增：原生伴侣 Android 工程
             1. build.gradle.kts 依赖 dsh-native-sdk
             2. AndroidManifest 填四要素 + Service
             3. 插件入口 DshUiPlugin + 渲染器/落点/工具钩子
             4. JS 侧 emit client/refresh 推 spec
             5. assembleDebug 打包 → 装机 → 验证
```

## 3. 第 1 步：建 Android 工程

新建一个 Android **application** 工程（伴侣是独立 APK）：

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application)   // 注意：application，不是 library
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.yourplugin.nativeui"   // 见踩坑：不能用 native
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.yourplugin.nativeui"
        minSdk = 26
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("com.deepseek.dsh:dsh-native-sdk:1.0.0")
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
```

> 本仓库内的样例用 `project(":plugin:native-sdk")`；独立工程用 aar 坐标。

## 4. 第 2 步：写 manifest（四要素）

```xml
<manifest package="com.example.yourplugin.nativeui">
    <application>
        <service
            android:name=".YourPluginService"
            android:exported="true"
            android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">

            <!-- 四要素：都在 <service> 级，App 的 discovery 从 service 读 -->
            <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
            <meta-data android:name="dsh.plugin.minAppVersion" android:value="12" />
            <meta-data android:name="dsh.plugin.id" android:value="yourname.your-plugin" />
            <meta-data android:name="dsh.plugin.name" android:value="你的插件" />

            <intent-filter>
                <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

规则：
- `dsh.plugin.id` 必须等于 JS 侧注册的 componentId 前缀，发布后不可改；
- 不申请任何 `<uses-permission>`；
- meta-data 放 `<service>` 级（不是 `<application>` 级）。

## 5. 第 3 步：写插件入口

**Service（照抄模板 + 静态工厂）**：

```kotlin
class YourPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(YourPlugin())

    companion object {
        /** 装载器契约：App 把本类加载进它的进程后，经这里拿插件实例。 */
        @JvmStatic
        fun plugin(): DshUiPlugin = YourPlugin()
    }
}
```

**插件入口**：

```kotlin
class YourPlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "yourname.your-plugin",       // 与 manifest 一致
        name = "你的插件",
        version = 1,
        capabilities = setOf(PluginCapabilities.OPEN_URL),  // 按需声明
    )
    override fun createRenderer(): ComponentRenderer = YourRenderer()

    init {
        // 在这里注册落点贡献 / 工具渲染器 / 页面（见第 6 节）
    }
}
```

## 6. 第 4 步：选渲染方式

按你的 UI 出现在哪选一种或多种：

### 6.1 整页/卡片（ComponentRenderer）

设置页/插件页点进去的整页内容：

```kotlin
class YourRenderer : ComponentRenderer {
    override val supportedTypes = setOf("yourplugin.status-card")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        // 画 spec；交互经 host.dispatchEvent 回传
    }
}
```

### 6.2 落点贡献（SlotContribution）

在 App 的固定位置（dock、输入框旁、设置页顶部、侧栏…）挂内容：

```kotlin
class DockContribution : SlotContribution() {
    override val slot = NativeSlot.COMPOSER_DOCK   // 输入框上方
    override val supportedTypes = setOf("yourplugin.pill")

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // 画一个胶囊/卡片
    }
}
// init 里：SlotRegistry.register(DockContribution())
```

落点全清单见 [plugin-sdk-guide.md §7](plugin-sdk-guide.md)。

### 6.3 工具渲染钩子（ToolRenderer）——GenUI 的情形

模型调用某个工具时接管它的结果渲染。dsh-ui spec 用 `SpecRenderer` 直接画：

```kotlin
class RenderUiTool : ToolRenderer {
    override val toolNames = setOf("render_ui")

    @Composable
    override fun Render(call: ToolCallContext, theme: PluginTheme, host: PluginHostApi) {
        val spec = DshUiSpecParser.fromToolArgs(call.args) ?: return
        SpecRenderer.Render(spec = spec, theme = theme, host = host)
    }
}
// init 里：ToolRenderRegistry.register(RenderUiTool())
```

## 7. 第 5 步：JS 侧配合（推状态）

原生端画什么由 Host 推下来的 spec 决定。JS 侧在状态变化时 emit：

```js
// lib/index.js
ctx.on('ui/yourname.your-plugin#section/start-oauth', async () => {
  // ...你的已有逻辑...
  ctx.emit('client/refresh', {
    componentId: 'yourname.your-plugin#section',
    patch: { state: { status: 'active', quotaRemaining: 1234 } },
  });
});
```

**铁律**：spec 里只放展示数据，**永远不放 token/secret**。

## 8. 第 6 步：打包、装机、验证

```bash
./gradlew assembleDebug        # 出 APK
adb install your-plugin.apk    # 装机
# 打开 App → 插件页 → 原生插件 tab：应出现你的插件
# 点进详情：pluginId、版本、签名指纹、卸载都在
# 卸载它 → 离开 App 再回来：列表里摘除
```

## 9. 三种典型的转换形态

| 形态 | 代表 | 用什么 | 转换要点 |
| --- | --- | --- | --- |
| **工具产出渲染** | GenUI（render_ui） | ToolRenderer + SpecRenderer | 接管工具卡片；spec 直接画，不写布局 |
| **账号/状态卡 + OAuth** | Kimi OAuth | ComponentRenderer + openUrl + dispatchEvent | 卡片画状态；OAuth 走 Custom Tab + 深链 |
| **状态概览落点** | 订阅 | SlotContribution（SETTINGS_SHEET / COMPOSER_DOCK） | 在设置页/输入框上方挂概览卡 |

三个形态在本仓库 `samples/` 各有一个可编译、可装机的参照实现。

## 10. 移植踩坑清单

| 坑 | 现象 | 解法 |
| --- | --- | --- |
| 包名用 `native` | 编译报「not a valid Java package name」 | `native` 是 Java 关键字，用 `nativeui` 等 |
| meta-data 放 `<application>` 级 | discovery 扫不到插件 | 放 `<service>` 级 |
| 忘了静态 `plugin()` 工厂 | 落点内容不渲染（装载器拿不到实例） | Service 加 `@JvmStatic fun plugin()` |
| spec 里有凭证 | 审核被拒 + 安全风险 | spec 只放展示数据 |
| 硬编码颜色/字号 | 深色模式破版 | 全部走 `host.currentTheme()` |
| 假设原生端在场 | 未装 APK 的用户功能残缺 | JS 侧功能必须在纯 Web 下完整可用 |
| 装载后落点空 | 落点容器读的是 App 进程的注册表 | 装载器把插件类加载进 App 进程（已由 native-host 处理），你只需保证 `plugin()` 工厂存在 |

---

## 附：转换前后的对照

| 维度 | 转换前（纯 JS） | 转换后（+ 原生伴侣） |
| --- | --- | --- |
| 逻辑层 | lib/index.js（Host） | 不变 |
| Web 前端 | lib/client.js | 不变（降级路径） |
| 手机端 UI | WebView 加载 Web 界面 | 原生渲染（增强）+ WebView 兜底 |
| 手机端交互 | 网页内点击 | 原生控件 → dispatchEvent → 同一 JS 监听器 |
| 发布 | npm/GitHub | npm/GitHub（JS）+ 商店/GitHub tag（APK） |
