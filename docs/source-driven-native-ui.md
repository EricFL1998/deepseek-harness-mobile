# 源码驱动的原生 UI 接入流程

> 本文给出固定流程：先用扫描器读插件自己的包，得到 UI 清单与缺口，再选择
> `PageContent` / `SlotContribution` / `OfficialSlot` / dsh-ui spec 实现。
> 完整示例是 Demo 插件：Web 半边 `samples/demo-js`，伴生 APK `samples/demo-native`。

## 1. 输入：插件自己的包与源码

SDK 自带扫描器直接读插件包。Demo 的接入命令：

```bash
python tools/plugin_ui_scanner.py \
  --plugin samples/demo-js \
  --companion samples/demo-native/src/main/kotlin \
  --check
```

需要留档时再加输出参数：

```bash
python tools/plugin_ui_scanner.py \
  --plugin /path/to/your-plugin \
  --companion /path/to/native-companion/src \
  --write docs/plugin-ui.md \
  --json docs/plugin-ui.json \
  --check
```

源码获取优先级：

1. `lib/*.js.map` 的 `sourcesContent`（原始 TSX，推荐）；
2. `src/**/*.tsx`（插件仓库源码）；
3. `lib/client.js`（编译产物，启发式）。

`--companion` 是可选的：传入后会扫描伴侣 APK 源码里的 `NativeSlot.X` /
`SlotRegistry.register` / `PageRegistry.register`，判断每个插件 slot 是否真的实现。
`--check` 在以下情况失败：

- 插件注册了 slot，但 App 没有 NativeSlot 映射；
- 映射了 NativeSlot，但 App 没有宿主容器；
- 传了 `--companion`，但伴侣没有注册对应贡献/页面。

## 2. 输出：可直接执行的清单

报告包含：

- **落点**：slot / entry / order / component / mode；
- **原生状态**：NativeSlot、宿主、伴侣是否实现；
- **数据**：RPC 端点、`useProjection`、useChat、inject；
- **UI**：JSX 组件树、可见文案、按钮动作；
- **localized 文案**：`locale/*.json`；
- **缺口**：未映射、无宿主、`--companion` 未实现。

Demo 插件（`samples/demo-js`）的扫描结果：

| slot | entry | order | component | 原生落点 | 宿主 | 原生实现 | 数据依赖 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `conversation.composer.dock` | `demo-dock` | 10 | `DemoDockChip` | `COMPOSER_DOCK` | 是 | 是 | `rpc:bump`, `rpc:status` |
| `settings.section` | `demo` | 10 | `DemoSettingsSection` | `SETTINGS_SHEET` | 是 | 是 | `rpc:bump`, `rpc:status` |

扫描摘要：包 `dsh-plugin-demo` 0.1.0，源文件 7，注册落点 2；`--check` 输出
`plugin UI coverage check OK`。报告里统计的 2 个 RPC 依赖是 Web 组件实际调用的
`demo.bump` / `demo.status`；Host 侧完整契约还有 `demo.catalog` / `demo.ping` /
`demo.reset` / `demo.echo`，见 [Demo README](../samples/demo-js/README.md)。

完整报告见 [插件 UI 清单：dsh-plugin-demo](plugin-ui-inventory-example.md)。Demo 的
两个落点都已映射、宿主就绪、伴生实现，可作为后续插件的最低覆盖线。

## 3. 选择实现方式

| 扫描结果 | 实现 |
| --- | --- |
| 设置页 / 整页入口 | `PageContent` + `PageRegistry.register`（Demo：`DemoSettingsPage`） |
| 输入栏、消息、dock、卡片扩展 | `SlotContribution`（Demo：`DemoDockContribution`） |
| 内置控件（tok/s、缓存、模型选择器等） | `OfficialSlot` 包装，保留默认 UI |
| 信息型卡片、列表、指标 | `SpecSlotContribution` + dsh-ui spec |
| 数据 | `PluginRpcHost.callRpc`，调用插件既有 RPC（Demo：`demo.status` / `demo.catalog`） |
| 需要切换模型/推理档位 | `PluginModelHost.selectModel` |
| 锚定浮层 / popover / 点击展开面板 | SDK `PluginPopover`（贴着锚点），不要用居中 `Dialog` |
| 暂时没有宿主 | 显式 Web 回退，不允许静默丢失 |

## 4. 定位与样式：Popup 要贴着锚点，并使用宿主材质

定位与样式按以下规则确定，保证结构与位置一致。扫描器会从源码提取定位线索：
`useAnchoredPosition`、`createPortal`、`anchorRef`、`role="dialog"`、
`position: fixed/absolute`、`side: 'top'`、`align: 'end'` 等；如果插件使用锚定
浮层而伴侣源码里没有 `Popup` / `PopupPositionProvider`，`--check` 会给出警告。

### 4.1 定位规则

- Web 版用 anchored popover（`side: top` / `align: end`）时，原生必须贴锚点：
  用 SDK 的 `PluginPopover`，把它和触发元素放在同一个 `Box`；
- 只有真正的模态流程（确认框、全屏选择器）才用 `Dialog`；
- 浮层右对齐到触发元素的右边，上方留 6dp 间距，并夹在窗口内；
- 不要自己写 `PopupPositionProvider`；SDK 已提供统一实现。

Demo dock 的写法（`DemoDockContribution.kt`）：

```kotlin
val current = status
var open by remember { mutableStateOf(false) }

Box {
    Text(
        text = "Demo ${current.count}",
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { open = !open }, onLongPress = { open = true })
        },
    )
    if (open) {
        PluginPopover(theme = theme, onDismiss = { open = false }) {
            Text("Demo 插件", style = theme.textPopupTitle)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("计数", style = theme.textPopupBody)
                Text(current.count.toString(), style = theme.textPopupBody)
            }
        }
    }
}
```

### 4.2 样式规则

浮层必须使用 App 菜单系统（`DshMenuCard`）的材质，不能自己造一张 Material `Card`：

- 背景：`theme.colorPopupSurface`（App 把 `menuSurfaceFill` 合成到 `bgBase`）；
- 描边：`theme.colorPopupBorder`（深色 `borderL3` / 浅色 `borderL1`）；
- 圆角：`theme.cornerPopup` / `theme.shapePopup`（16dp）；
- 内边距：`theme.spacingUnit * 1.75f`；
- 文字：标题 `theme.textPopupTitle`，正文 `theme.textPopupBody`；
- 阴影由 `PluginPopover` 统一处理，插件不传 elevation。

`PluginTheme` 的 popup 字段由 App `rememberPluginTheme()` 从
`DshPanelColors` / `DshPanelDefaults` 映射，因此模型菜单、权限菜单、插件浮层
看起来是同一套设计。

### 4.3 检查清单

- [ ] 插件源码里出现 `useAnchoredPosition` / `createPortal` / `side: top`；
- [ ] 伴侣使用 `PluginPopover`，不是 `Dialog`（Demo：`DemoDockContribution`）；
- [ ] 浮层宽度随内容（`IntrinsicSize.Max`），最大 `320dp`，内容过长时换行/滚动；
- [ ] 使用 `theme` 的 popup 字段，无硬编码颜色/圆角/阴影；
- [ ] `--check` 没有“插件使用锚定浮层，而伴侣没有 Popup”警告；
- [ ] 真机点按后，浮层紧贴胶囊，不遮挡输入框的发送按钮。

## 5. 数据契约

- 原生端不建网络连接、不持有 token；所有数据经 RPC 从 Host 插件取（Demo：
  `demo.status` 状态、`demo.catalog` 接入清单）。
- slot spec 只放展示数据；当前 provider/model 等选择状态由 App 通过 spec 下发
  （具体字段随落点而定），原生端不在本地推断“大概是谁”。
- 长耗时/轮询动作留在 Host；原生只按周期 `callRpc` 或等 `client/refresh`（Demo
  dock 每 30s 重读 `demo.status`）。
- 能力缺失时降级：`host as? PluginRpcHost ?: return` 是允许的，但要在扫描器里明确
  “需要哪个宿主能力”；也可以像 Demo 设置页那样显示可读的“Host RPC 未连接”，不要
  只表现为空白。

## 6. 新增一个 UI 的完整顺序

1. 跑扫描器，确认 slot/entry/component 与数据依赖；
2. 在 App 侧确认 `NativeSlot` 映射与宿主容器（Demo：`SETTINGS_SHEET` /
   `COMPOSER_DOCK`）；没有就补（映射表 + `NativeSlotHost`/`OfficialSlot`）；
3. 在伴侣侧注册 `SlotContribution`（或 `PageContent`），数据用 RPC；
4. 需要新宿主能力时，加 SDK 可选接口（参考 `PluginRpcHost`、`PluginModelHost`），
   由 `SlotBridgeHost` 实现，App 注入 handler；
5. 重跑 `plugin_ui_scanner.py --check`，必须通过；
6. 跑 `SlotCoverageTest` / `slot_inventory.py`，内置落点不允许回退；
7. 截图只做视觉验收，不作为发现 UI 的来源。

## 7. Demo 插件的接入清单与验证命令

按以下命令验证 Demo 的 JS 注册、原生实现与 RPC / 事件契约：

```bash
# 扫描：两个落点都必须有映射、宿主和伴生实现
python tools/plugin_ui_scanner.py \
  --plugin samples/demo-js \
  --companion samples/demo-native/src/main/kotlin \
  --check

# RPC / 动作 / 状态隔离
node --test samples/demo-js/test/rpc.test.mjs
```

期望输出：

```text
plugin UI coverage check OK
conversation.composer.dock      DemoDockChip         impl=True
settings.section                DemoSettingsSection  impl=True

# tests 7 / pass 7 / fail 0
```

接入清单与实现方式：

- `settings.section`（id `demo`）→ `SETTINGS_SHEET`：Web 组件
  `DemoSettingsSection`，原生 `DemoSettingsPage`（`PageContent`，
  `PageEntry(id="demo")`）。读取 `demo.status` / `demo.catalog`；「服务端 +1」走
  `dispatchEvent("dsh-plugin-demo#demo", "submit", {actionId:"bump"})`，
  bridge 排 `client/refresh` 后由 `refreshEvents` 触发重拉；「重置」直接调
  `demo.reset`。
- `conversation.composer.dock`（id `demo-dock`）→ `COMPOSER_DOCK`：Web 组件
  `DemoDockChip`，原生 `DemoDockContribution`（`SlotContribution`）。胶囊显示
  Host 的 `count`，点按打开 `PluginPopover`，30s 重读一次 `demo.status`；同样提供
  事件写路径与 `demo.reset` 纯 RPC 写路径。
- 其余原生落点：`DemoNativePlugin` 用 `DemoSlotContribution` 覆盖
  `DemoSlots` 22 项中的 21 项；`WEB_ISLAND_FULL` 是整岛 Web 回退容器，Demo 不注入，
  避免整屏覆盖 App，只在 Demo 页说明。

验证步骤：

1. `dsh plugin --profile web add ./samples/demo-js` 装入 JS 插件；
2. `./gradlew :samples:demo-native:assembleDebug` 构建伴生 APK；
3. `adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk`；
4. 打开 App → 设置 → 插件界面 → Demo 插件，确认原生页与 `demo.status` 数据；
5. 卸载伴生 APK，确认设置页与 dock 回到 Web 组件。

验收清单与文档站案例分别见 [检查清单](pitfalls.md) 和
[文档站案例](index.html#case)。
