# 订阅插件：原生 UI 缺口（自动扫描 + 人工核对）

> **更新（2026-10-21）：`ProviderCardExtras` 与 `FastModeButton` 已补齐。**
> - 新增 `SETTINGS_PROVIDER_CARD` 宿主与 `ProviderCardExtrasContribution`；
> - 新增 `PluginModelHost` + `SlotBridgeHost.selectModel`，`COMPOSER_RIGHT` 上的
>   `FastModeContribution` 已接 `model-variants`；
> - 扫描器带 `--companion` 已通过（4/4 落点实现）；下方原始报告保留作记录。
>
> 仍待补齐的细节见第 3 节（dock 面板细化、设备码复制、签到/任务成功反馈等）。


> 扫描对象：`dsh-plugin-subscription-oauth@0.17.17` 的插件源码（source map 内嵌 TSX）。
> 工具：`python tools/plugin_ui_scanner.py --plugin <pkg> --companion <native-src>`
>
> 结论：插件注册 4 个 UI 落点、20 个 RPC。原生伴侣覆盖 2 个落点；**2 个落点完全没实现**，
> 另有 2 处“已实现但只覆盖了一部分”。

## 1. 落点级缺口

| 插件 slot | entry | 组件 | NativeSlot | 原生实现 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `conversation.composer.dock` | `subscription-usage` | `UsageChip` | `COMPOSER_DOCK` | 部分 | dock 读数/面板已接，面板明细与刷新未对齐 |
| `conversation.input.right` | `subscription-fast-mode` | `FastModeButton` | `COMPOSER_RIGHT` | **未实现** | 原生伴侣没有注册这个贡献 |
| `settings.models.provider-card` | — | `ProviderCardExtras` | **未映射** | **未实现** | App 的设置-模型页没有这个 keyed slot 宿主 |
| `settings.section` | `subscriptions` | `SubscriptionsSection` | `SETTINGS_SHEET` | 大部分 | 页面与主要动作已实现，细节有缺口 |

扫描器对 `--check` 会因 `conversation.input.right` 未实现、`settings.models.provider-card`
未映射而失败。

## 2. 完全没写的功能

### 2.1 composer 快切模式按钮（FastModeButton）

- 位置：`conversation.input.right`，官方在模型选择器左侧。
- 逻辑：
  - 用 `model-variants { provider, id }` 查当前模型是否有 fast/lightspeed 变体；
  - 有变体时显示闪电按钮；点击通过 model directory 把 `reasoningEffort` 切到
    provider 自己的 rung（Kimi `highspeed`、xAI `lightspeed`），再点切回默认；
  - fast 变体不是模型选择器里的独立行，模型 id 不变。
- 原生状态：`COMPOSER_RIGHT` 宿主存在，但伴侣没有注册对应 `SlotContribution`，
  也没有“写 reasoningEffort”的宿主能力。**这是功能性缺口，不只是样式。**

### 2.2 模型页 provider 卡片内的订阅块（ProviderCardExtras）

- 位置：`settings.models.provider-card`（keyed slot，`entryKey = subscriptions`）。
- 逻辑：在 Models 设置页每个 provider 卡片里显示：
  - 连接状态（已连接/未登录/忙）+ 账号数；
  - 当前用量窗口（plan、已用百分比、重置时间、进度条）；
  - 连接/添加账号/取消按钮（`login`/`cancel`）。
- 原生状态：App 未映射这个 keyed slot，也没有宿主；伴侣未实现。
- 后果：用户在 Settings → Models 里看不到订阅插件的 Connect 区块。

## 3. 已实现但缺细节

### 3.1 dock 的 UsageChip 面板

原生已有 dock 读数和点击/长按面板，但只显示简化行，插件 Web 版面板还有：

- 每个账号的 plan 名、窗口重置时间（`resetsAt`）、数据获取时间（`fetchedAt`）；
- 用量进度条（原生只有文本百分比）；
- bonus wallet（`bonusWallets`，`+¥x`）和 cc-switch 余额的货币明细；
- 每 60 秒自动刷新（原生只加载一次/模型切换时重载）；
- 非订阅 provider 的 cc-switch 余额分支已实现，但面板未展示 provider 标题与明细。

### 3.2 Subscriptions 设置页

主要动作已经覆盖：连接/取消、账号默认/退出/刷新、额度、签到、任务奖励、模型刷新/
添加/删除/排序/显隐、auto 开关、discover、reload。

与 Web 版相比仍缺：

- **设备码登录**：Web 有复制授权码按钮和过期时间；原生只显示授权码文本；
- **签到/任务结果反馈**：Web 显示签到天数、任务领取成功文案；原生只发 RPC，
  成功反馈依赖下一次 status；
- **auto provider 摘要**：Web 显示 auto 链模型数量/`模型链` 文案；
- **模型列表元信息**：built-in vs live、隐藏数量、模型来源等标签；
- **拖拽排序**：原生用上下箭头替代，功能等价但交互不同（可接受）。

## 4. RPC 覆盖

插件扫描出 20 个端点：

```
addModel auto-state cancel ccswitch-balance checkin deepseek-balance discover
login logout model-variants refresh reload removeModel setAutoOn setDefault
setModelOrder setModelVisible status tasks usage
```

原生已使用其中 19 个；**唯一没接的是 `model-variants`**，对应 2.1 的快切模式按钮。
其余端点都有原生调用（设置页 helper 或 dock）。

## 5. 建议实现顺序

1. `settings.models.provider-card`：先补 NativeSlot/宿主，再实现 ProviderCardExtras；
2. `conversation.input.right` + `model-variants`：需要补“切换 reasoningEffort”的宿主能力；
3. UsageChip 面板明细与 60s 刷新；
4. 设备码复制/过期、签到/任务成功反馈、auto 摘要等文案级补齐。
