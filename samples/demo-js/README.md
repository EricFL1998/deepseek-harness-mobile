# Demo 插件（JS 半边）

SDK 的**接入案例**：一个真实的 npm 插件包，与 [`samples/demo-native`](../demo-native)
（Kotlin/Compose 伴生 APK）配对。它把「JS 插件 + 原生 UI」的完整链路缩小到可读的
几百行代码，演示：

- Host 侧 RPC：`demo.status` / `demo.catalog` / `demo.ping` / `demo.bump` /
  `demo.reset` / `demo.echo`；
- 原生事件回传：原生按钮 → `dispatchEvent` → JS 插件 `mobileUi` 动作 →
  bridge `client/refresh` → 原生页重读 RPC；
- 组件身份配对：`settings.section`、`conversation.composer.dock` 的 Web 注册
  与原生落点、`PageEntry.id`、`componentId` 一一对应；
- Web 回退：不装伴生 APK 时，设置页与 dock 的 React 组件仍可用，只是走 Web。

> 纯虚构演示：不访问网络、不读写账号、不持有任何凭据；状态只存在 Host 进程内，
> Host 重启后回到初始值（计数 42）。

## 目录

```
samples/demo-js/
├── package.json          # npm 身份 + dsh.client / dsh.native 声明
├── cordis.patch.yml      # bundle 自激活：挂载 lib/index.js
├── lib/
│   ├── index.js          # 插件入口：name / apply
│   ├── constants.js      # RPC 前缀、componentId、settings ns（三方约定）
│   ├── state.js          # 内存状态仓（计数 42、上次动作、更新时间）
│   ├── rpc.js            # /api/demo.* 路由 + 请求/响应信封
│   ├── actions.js        # mobileUi 动作：bump / reset
│   ├── catalog.js        # demo.catalog 返回的注册清单
│   └── client.js         # Web 半边：settings.section + composer.dock
├── locale/{zh,en}.json   # Web 回退界面文案
└── test/rpc.test.mjs     # node:test：信封、路由、动作、状态隔离
```

## 与伴生 APK 的身份配对

| 位置 | 值 |
| --- | --- |
| JS 包名 | `dsh-plugin-demo` |
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

## RPC 契约

App / 原生端通过 `PluginRpcHost.callRpc("demo.<endpoint>", payload)` 调用；Web 侧
通过 `connection.rpc.call("/api", "demo.<endpoint>", payload)`。

请求体（与真实插件一致，payload 原样传递、不包 `args`）：

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

响应体：

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

业务失败也是 HTTP 200，错误放在 `result.error`：

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

| 端点 | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | 更新后的 status 快照 |
| `demo.reset` | `{}` | 重置后的 status 快照 |
| `demo.echo` | 任意 JSON | `{ received: <原样回显> }` |

## 原生事件契约

原生控件不执行插件 JS，只上报「哪个组件、哪个动作、什么 payload」：

| componentId | actionId | payload | 效果 |
| --- | --- | --- | --- |
| `dsh-plugin-demo#demo` | `bump` | `{ actionId:"bump", by?:number }` | 计数 +n |
| `dsh-plugin-demo#demo` | `reset` | `{ actionId:"reset" }` | 重置为 42 |
| `dsh-plugin-demo#demo-dock` | `bump` | `{ actionId:"bump", by?:number }` | 计数 +n |
| `dsh-plugin-demo#demo-dock` | `reset` | `{ actionId:"reset" }` | 重置为 42 |

事件名由 bridge 限定为 `press` / `input` / `toggle` / `submit`；Demo 统一用
`submit`。bridge 命中 `mobileUi.registerAction` 后执行插件逻辑，再给手机排
`client/refresh`。

## 联调

```bash
# 1) 安装 JS 插件到 Host profile（本地路径）
dsh plugin --profile web add ./samples/demo-js

# 2) 构建并安装伴生 APK
./gradlew :samples:demo-native:assembleDebug
adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk

# 3) 打开 App → 设置 → 插件界面 → Demo 插件（应被原生页接管）
#    聊天页输入栏上方出现 "Demo 42" 胶囊，点按打开 popover。
```

不装 APK 时，第 3 步进入的是 Web 回退页面（同样能读 `demo.status`）。

## 自测

```bash
# RPC / 动作 / 状态隔离（无需 Host、Android、网络）
node --test samples/demo-js/test/rpc.test.mjs

# 扫描器：JS 注册的 2 个落点都有 NativeSlot 映射、宿主和伴生实现
python tools/plugin_ui_scanner.py \
  --plugin samples/demo-js \
  --companion samples/demo-native/src/main/kotlin \
  --check
```

## 在文档里的位置

- [SDK 开发者文档](../../docs/plugin-sdk-guide.md)：从装包到落点、RPC、生命周期；
- [源码驱动接入流程](../../docs/source-driven-native-ui.md)：扫描 → 缺口 → 实现；
- [文档站案例](../../docs/index.html#case)：Demo 插件的接入清单与验证输出。
