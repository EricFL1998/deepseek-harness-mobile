# 复盘：第一次做 Native UI 为什么没有一次找全 slot

> 状态：复盘记录。触发问题：首版原生 UI 做完后，用户仍需指出「composer dock 上还有内容」才补上落点。
> 关联代码：`core/model/.../DshSlotRegistry.kt`、`plugin/native-sdk/.../slots/NativeSlot.kt`、
> `plugin/native-host/.../NativeSlotHost.kt`、`plugin/native-sdk/.../pages/PageEntry.kt`。
> 日期：2026-10-21。

## 1. 结论

不是「slot 清单不存在」，而是**我们一开始按“页面”而不是按“落点清单”实现**，并且没有把
Host 的权威 slot 注册表投影成 App 端的可执行清单。结果：

- `NativeSlot` 枚举按“手机上的承载位置”分组（9 个），文档曾声称覆盖官方 38 个槽位；
- 但真正把 `NativeSlotHost` 放进 Compose 界面的是**手写的一个个容器**，第一个版本只接了
  设置页、插件页和会话主区域，`COMPOSER_DOCK` 没有宿主容器；
- 没有宿主容器的 slot 不报错、不占位、也不触发降级日志，插件声明了也等于静默丢失；
- 所以问题不是“发现不了 dock”，而是**没有机制要求每个官方 slot 都被显式认领或显式降级**。

用户指出 dock 后，才在 `ChatScreen.kt` 的输入卡片上方补了 `NativeSlot.COMPOSER_DOCK` 容器。

## 2. 证据

### 2.1 官方 slot 清单其实已经在 App 里有常量

`DshSlotRegistry` 有 30 个官方槽位名常量，覆盖：

- conversation.*（input / composer / chat / reasoning / actions）
- tool.call.*
- sidebar.*
- shell.overlay
- settings.* / plugins.tab

这说明 slot 名称并不缺；缺的是「名称 → 手机落点 → 实际宿主容器」的可验证映射。

### 2.2 NativeSlot 只有 9 个落点，且映射关系只写在注释里

`NativeSlot.kt` 用 9 个枚举承载 30 个官方槽位，注释写明了大致归属，但代码中**没有一张
`slotName -> NativeSlot` 的表**。运行时没有任何地方校验：

- 每个 `DshSlotRegistry` 常量是否都有归属的 `NativeSlot`；
- 每个 `NativeSlot` 是否真的在界面某处放了 `NativeSlotHost`；
- 某个插件贡献的 slot 是否因为找不到宿主而没有渲染。

### 2.3 宿主容器是手写散落的，覆盖率不完整

首版实际放置的 `NativeSlotHost`：

| 落点 | 位置 |
| --- | --- |
| `SETTINGS_SHEET` | `SettingsRootScreen.kt` |
| `SIDEBAR_PANEL` | `MainActivity.kt`（侧栏） |
| `PLUGIN_OVERLAY` | `MainActivity.kt`（顶层浮层） |
| `CHAT_STREAM` | `ChatScreen.kt` |
| `TOOL_VIEW_SHEET` | `ChatScreen.kt` |
| `MESSAGE_ACTION` | `ChatScreen.kt` |
| `COMPOSER_DOCK` | `ChatScreen.kt`（用户指出后才补） |
| `COMPOSER_OVERLAY` | `ChatScreen.kt` |
| `WEB_ISLAND_FULL` | 未接宿主容器 |

也就是说 9 个落点里首版并不完整；`COMPOSER_DOCK` 恰好是最容易“看不见”的那种：
它不占主内容区，只在输入框上方一条，空着时没有任何痕迹。

### 2.4 页面契约先行，slot 契约后补

`PageEntry` 只支持 `SETTINGS` / `PLUGINS` 两种 placement，对应 `settings.*` 与
`plugins.tab`。第一个示例插件（subscription-native）用页面契约就能跑通，于是
“原生 UI = 原生设置页”成为事实上的第一版模型。composer、sidebar、tool、overlay
这些非页面落点没有进入第一版的验收路径。

## 3. 根因

1. **以页面为中心建模，而不是以槽位清单为中心。**
   先做了“插件能挂一个原生页面”，再按看到的界面逐个补容器；不是先枚举官方槽位，
   再逐个认领或降级。

2. **权威数据源没有参与构建期校验。**
   Host/bridge 有 `client-slots` 注册信息，但 App 只把它当运行时目录数据展示，
   没有在启动/CI 时与 `DshSlotRegistry`、`NativeSlot`、宿主容器位置做三方对账。

3. **缺失是静默的。**
   没有宿主容器时，`SlotRegistry.resolve` 根本不会被调用；插件贡献不会报错，
   UI 也不会显示“该 slot 未接入”。JS/Web 回退又把问题遮住，用户只能靠肉眼发现。

4. **示例覆盖太窄。**
   首个示例只验证 settings 页面；没有“全落点示例插件”或“空 slot 冒烟测试”。
   验收标准是“我的插件页面能打开”，不是“每个官方 slot 都有确定行为”。

5. **文档先行但未落地成代码约束。**
   `NativeSlot` 注释曾写“38 个槽位全部映射”，但这是声明，不是测试；后续新增
   官方 slot 或落点时没有失败用例提醒。

## 4. 改进措施

### 4.1 立即生效

- 所有 9 个 `NativeSlot` 都应有宿主容器；`WEB_ISLAND_FULL` 需要明确由全屏 Web 容器认领
  或标记为“不使用”。
- 补一份显式映射表（见 `slot-mapping.md`），把 30 个官方槽位逐条映射到落点与容器。`2026-10-21` 后续已把原先未接入的 9 个 composer/header 落点全部补上宿主容器，当前 30/30 已映射、0 个显式不支持。

### 4.2 构建期 / 启动期校验

1. **启动时槽位对账**：从 bridge `client-slots` / 已装插件 package 读取官方 slot 清单，
   与 `DshSlotRegistry` + `NativeSlot` 映射表比较；缺失项写入诊断日志与开发者面板。
2. **单元测试**：遍历 `DshSlotRegistry` 常量，断言每个都有映射目标；再断言每个
   `NativeSlot` 在 App 代码中至少被一个 `NativeSlotHost` 引用（可用源码扫描或注入注册）。
3. **静默变可见**：`SlotRegistry` 对“有贡献但无宿主”的 slot 输出警告；开发构建在
   composer 上方显示未接入提示，而不是完全无声。

### 4.3 已落地：先扫全，再实现

这次复盘之后补了三条硬约束，保证“哪怕 UI 不对，覆盖是完整的”：

1. **显式映射表**：`NativeSlotMapping` 把 30 个官方 slot 逐个映射到 `NativeSlot`
   或 `explicitlyUnsupported`；不再只写在注释里。
2. **覆盖测试**：`SlotCoverageTest` 用反射读取 `DshSlotRegistry` 常量，断言
   “已映射 + 明确不支持 = 官方全集”，新增官方 slot 未处理会直接失败。
3. **扫描工具 + 生成清单**：`tools/slot_inventory.py` 同时扫描映射表和 App 源码中的
   `NativeSlot.X` 宿主引用，生成 `docs/slot-inventory.md`；只要某个落点“声明了映射但
   代码里没有宿主容器”，工具退出码为 1。

当前扫描结果：官方 slot 30 个，已映射 30 个，明确不支持 0 个，未覆盖 0 个，
声明映射但无宿主 0 个。工作流固定为：

```bash
python tools/slot_inventory.py --write docs/slot-inventory.md
./gradlew :app:testDebugUnitTest --tests com.deepseek.dsh.SlotCoverageTest
```

先让这两步通过，再开始写 UI；UI 可以迭代，slot 覆盖不允许回退。

### 4.4 流程

- 新做任何 native UI 功能，第一步先产出 slot inventory（官方槽位 × 落点 × 容器 × 验收方式），
  评审后再写 UI。
- 每个新增落点必须同时提交：容器、映射表条目、示例或冒烟测试。
- SDK 文档与示例包含完整落点表；新插件不应需要用户指出“这里还有内容”。

## 5. 以后如何避免同类问题

- **先清单，后界面**：没有 slot inventory 不动手。
- **默认显式降级**：每个官方 slot 要么原生认领，要么明确走 Web/隐藏，不允许“未定义”。
- **用测试锁住覆盖**：官方 slot 常量新增时，映射测试必须失败。
- **端到端示例**：维护一个注册全部落点的示例 APK，作为每次改动的冒烟基线。
