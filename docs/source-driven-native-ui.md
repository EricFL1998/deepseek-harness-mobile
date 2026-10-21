# 源码驱动的原生 UI 接入流程

> 本文是“不要等用户截图”的固定流程：先用扫描器读插件源码，得到 UI 清单与缺口，
> 再选择 `PageContent` / `SlotContribution` / `OfficialSlot` / dsh-ui spec 实现。

## 1. 输入：插件源码，不是官方仓库

第三方开发者没有 DSH upstream。SDK 自带扫描器直接读插件包：

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
`PageRegistry.register`，判断每个插件 slot 是否真的实现。`--check` 在以下情况失败：

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

订阅插件示例（`docs/plugin-ui-inventory-example.md`）：

```
conversation.composer.dock      UsageChip             COMPOSER_DOCK     已实现
conversation.input.right        FastModeButton        COMPOSER_RIGHT    补齐后实现
settings.models.provider-card   ProviderCardExtras    SETTINGS_PROVIDER_CARD  补齐后实现
settings.section                SubscriptionsSection  SETTINGS_SHEET    已实现
```

## 3. 选择实现方式

| 扫描结果 | 实现 |
| --- | --- |
| 设置页 / 整页入口 | `PageContent` + `PageRegistry.register` |
| 输入栏、消息、dock、卡片扩展 | `SlotContribution` |
| 官方控件（tok/s、缓存、模型选择器等） | `OfficialSlot` 包装，保留官方默认 UI |
| 信息型卡片、列表、指标 | `SpecSlotContribution` + dsh-ui spec |
| 数据 | `PluginRpcHost.callRpc`，调用插件既有 RPC |
| 需要切换模型/推理档位 | `PluginModelHost.selectModel` |
| 锚定浮层 / popover / 点击展开面板 | SDK `PluginPopover`（贴着锚点），不要用居中 `Dialog` |
| 暂时没有宿主 | 显式 Web 回退，不允许静默丢失 |

## 4. 定位与样式：Popup 要贴着锚点，并使用宿主材质

这一步是“结构扫对了，位置/样式不对”的常见来源。扫描器现在会从源码提取
定位线索：`useAnchoredPosition`、`createPortal`、`anchorRef`、`role="dialog"`、
`position: fixed/absolute`、`side: 'top'`、`align: 'end'` 等；如果插件使用锚定
浮层而伴侣源码里没有 `Popup` / `PopupPositionProvider`，`--check` 会给出警告。

### 4.1 定位规则

- Web 版用 anchored popover（`side: top` / `align: end`）时，原生必须贴锚点：
  用 SDK 的 `PluginPopover`，把它和触发元素放在同一个 `Box`；
- 只有真正的模态流程（确认框、全屏选择器）才用 `Dialog`；
- 浮层右对齐到触发元素的右边，上方留 6dp 间距，并夹在窗口内；
- 不要自己写 `PopupPositionProvider`；SDK 已提供统一实现。

```kotlin
Box {
    Text(
        text = "DeepSeek ¥24.80",
        modifier = Modifier.clickable { open = !open },
    )
    if (open) {
        PluginPopover(theme = theme, onDismiss = { open = false }) {
            Text("DeepSeek Account 额度", style = theme.textPopupTitle)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("CNY", style = theme.textPopupBody)
                Text("¥24.80", style = theme.textPopupBody)
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
- [ ] 伴侣使用 `PluginPopover`，不是 `Dialog`；
- [ ] 浮层宽度随内容（`IntrinsicSize.Max`），最大 `320dp`，内容过长时换行/滚动；
- [ ] 使用 `theme` 的 popup 字段，无硬编码颜色/圆角/阴影；
- [ ] `--check` 没有“插件使用锚定浮层，而伴侣没有 Popup”警告；
- [ ] 真机点按后，浮层紧贴 pill，不遮挡输入框的发送按钮。

## 5. 数据契约

- 原生端不建网络连接、不持有 token；所有数据经 RPC 从 Host 插件取。
- slot spec 只放展示数据；当前 provider/model 等选择状态由 App 通过 spec 下发
  （例如 `COMPOSER_DOCK` 的 `modelId`、`SETTINGS_PROVIDER_CARD` 的 `provider`）。
- 长耗时/轮询动作留在 Host；原生只按周期 `callRpc` 或等 refresh。
- 能力缺失时降级：`host as? PluginRpcHost ?: return` 是允许的，但要在扫描器里明确
  “需要哪个宿主能力”。

## 6. 新增一个 UI 的完整顺序

1. 跑扫描器，确认 slot/entry/component 与数据依赖；
2. 在 App 侧确认 `NativeSlot` 映射与宿主容器；没有就补（映射表 + `NativeSlotHost`/`OfficialSlot`）；
3. 在伴侣侧注册 `SlotContribution`（或 `PageContent`），数据用 RPC；
4. 需要新宿主能力时，加 SDK 可选接口（参考 `PluginRpcHost`、`PluginModelHost`），
   由 `SlotBridgeHost` 实现，App 注入 handler；
5. 重跑 `plugin_ui_scanner.py --check`，必须通过；
6. 跑 `SlotCoverageTest` / `slot_inventory.py`，官方落点不允许回退；
7. 截图只做视觉验收，不作为发现 UI 的来源。

## 7. 订阅插件的实际补齐记录

扫描报告最初标出 2 个完全未实现、1 个部分实现；补齐后：

- `settings.models.provider-card`：新增 `SETTINGS_PROVIDER_CARD` 宿主 +
  `ProviderCardExtrasContribution`（状态、账号数、用量、连接/取消、60s 刷新）；
- `conversation.input.right`：新增 `PluginModelHost` + `FastModeContribution`
  （`model-variants` → 切 reasoningEffort，不换模型 id）；
- `conversation.composer.dock`：补齐 bonus 钱包、窗口重置、60s 刷新；
- `settings.section`：补 auto 链标题/隐藏模型计数、设备码复制与有效期、
  built-in/live 目录标签。

这就是“扫描 → 缺口 → 实现 → 校验”的闭环；后续插件按同样流程接入，不再逐个截图改 UI。
