# 插件 UI 原生转换：设计文档

> 状态：**已废弃（转译层已删除）**。
>
> 2026-10-20 起，bridge 的 `transpile` provider 与 App 侧的 `plugin/mobile-ui`
> 原生渲染器（`RenderTree`/`PluginSlotHost`/`ComponentChips`/`PluginValueChip`/
> `PluginDataLoader`）一并移除，bridge 升至 2.0.0。插件 UI 在手机端统一走
> **完整 Web 界面**（WebView 加载真实 Web 客户端），不再做静态转译成原生控件。
>
> 保留的部分：bridge `client-slots` 的 `list`/`describe`/`icon-svg`（插件目录的
> 分类、本地化 label、图标）与 `dispatch`（原生端事件回传总线）仍在，由
> `slots/bundle-locale.ts`（原 transpile 的词典解析子集）支撑 label 本地化。
>
> 本文保留作为历史设计记录。架构层面的取舍见
> [移动端 UI 架构选型](mobile-ui-architecture.md)；现行 bridge 协议见
> [移动 bridge 协议](mobile-bridge.md)。
>
> ## 历史记录（删除前的实现）
>
> ## 2026-10-20 收尾修复（原生界面 + 完整 Web 界面）
>
> 1. **设置里的插件选项跟随语言**：插件注册的 `label: () => t('nav')` 是
>    thunk，文案在 bundle 内联词典里（没有 `locale/` 目录）。bridge 现在从
>    bundle 自身的 `const zh/en = {…}` 解析 label，并按请求里的 `locale`
>    （`client-slots/list`、`describe`、`transpile/component-tree` 的可选参数）
>    选词典；App 每次拉取时带 `Locale.getDefault().toLanguageTag()`。设置页
>    因此显示 bundle 里声明的文案，而不是注册 id，且切语言后随语言变化。
>    同一次修复还按 `slot|id|package` 去重（同一个 bundle 会经安装目录、
>    profile、`@deepseek-ai` 符号链接被扫到多次），列表不再重复。
> 2. **去掉双重返回按钮**：插件界面/插件设置页打开时，设置根页面不再画顶层
>    Header；子页面自己的返回条是唯一的返回按钮（硬件返回同样作用于子页面）。
> 3. **完整界面全覆盖**：`PluginComponentPane` 的两种模式互斥——原生模式是
>    可滚动转译树，完整界面模式是一块占满内容区的 WebView。此前后者被塞进
>    `verticalScroll` 的 Column，无界高度量成 0，转译树还留在上面。
> 4. **完整界面加载真实 Web UI**：三处根因一起修掉——
>    - `applyInstalledPluginRows()` 每次 Host 启动都会核对 web-app patch，
>      摘掉「行在、包不在」的残留插件行（卸载/换版后 Loader 仍创建该 entry，
>      一个 entry 激活失败就让整个 Web 客户端停在 `Failed to load plugins`
>      启动页）；`stripRowMountName` 也改成摘整块而不是只删 `name:` 留下一
>      个坏掉的 id 占位。
>    - WebView 在 0×0 尺寸下开始导航会把 Chromium 布局视口锁成 0 高：之后
>      `window.innerHeight` 正常但 `100vh` 恒为 0，设置面板高度算成 0，只剩
>      灰色蒙版。现在首帧导航等 `onSizeChanged` 出非零尺寸再发起，并保留一次
>      `100vh` 探针 + 尺寸微抖作为兜底。
>    - Web 客户端是 SPA、没有 URL 路由：`完整界面` 打开后由注入脚本驱动它
>      自己的界面（预览提示 → 侧边栏 → 账号菜单 → 设置 → 该插件的
>      `settings.section` 行），并补一条窄屏适配样式（设置面板改为顶部横向
>      导航 + 全宽内容），让同一份真实 Web 界面在手机上可读。
> 5. **完整界面直接落在插件设置页**（2026-10-20 二次验收）：
>    - 脚本改为**持续重试**而不是一次性导航：先调 Web 客户端设置面板 store 的
>      `openSection(sectionId)`（从 settings launcher 的 React fiber props 找到，
>      与客户端自己的命令入口同一个 API，不依赖侧边栏/账号菜单的展开状态），
>      DOM 校验 `aria-current` 并连续确认面板没被 onboarding 关掉后才算成功；
>      失败才退回点击路径。首启「预览版说明」在循环里自动处理。
>    - 之前的脚本只要设置面板出现就返回成功；若 onboarding 随后把面板关掉，
>      最终停在 Web 客户端首页（截图里的现象）。现在只有在目标分区稳定可见时
>      才结束，窗口 45s，页面重载会重新执行。
>    - 另修了 Host 冷启动竞态：首个 bridge 请求可能先于会话 cookie 就绪而失败，
>      而 `refreshPluginSettingsPages()` 只在 Host 状态变化时跑一次，结果设置里的
>      「插件界面」整组消失、再也回不来。现在两条来源分别降级，App 每 8s 重试
>      （空列表时）/32s 常规刷新，进入设置页时也补一次。
> 6. **插件界面默认打开完整 Web 界面**：`PluginComponentPane` 的
>    `initialWebMode` 默认改为 true——点插件入口直接进入真实 Web 客户端并自动
>    停在插件设置页；右上角「原生」可切回静态转译视图看声明结构。Host 端口
>    异步就绪时先显示原生视图占位，端口出现后自动切到 Web。
> 7. **工具型插件（`@changfenhuang/dsh-genui` 0.13.0）兼容**（2026-10-20）：
>    - **重复挂载**：该插件随包发布的 `cordis.patch.yml` 恰好是本类追加行
>      的同一种形状（`- insert:` + `id`/`name` 两行），启动清理把它当成本类
>      残留删掉了；随后 App 又按安装目录补了一行，于是 Loader 里同一插件
>      被 apply 两次，第二次注册同名 prompt section 抛错，插件被禁用。现在
>      清理只删「本类生成的」行：形似但内容出现在已安装插件的
>      `dsh.bundle.patch` 里的块一律保护；安装器 marker 块的任何引号写法也
>      能被 `rowMounted` 识别，不再重复挂载。
>    - **静态扫描读不到压缩包里的注册**：该插件用 rolldown 打包，注册写成
>      反引号模板字面量（`` name: `tool.call.toolview` ``）。bridge 的静态
>      抽取器原来只认单/双引号，导致 `tool.call.toolview`（keyed
>      `render_ui`）和 `conversation.input.dock` 完全消失，工具卡片拿不到
>      插件自己的视图。现在反引号（不含 `${}` 插值）按普通字符串读取，
>      并补了回归测试；keyed 注册的组件身份也改为
>      `<package>#<id ?? key ?? slot>`。
>    - **原生渲染工具产出**：新增 `feature/chat/Genui.kt`——把助手消息里的
>      ``` ```dsh-ui ``` 围栏和 `render_ui` 工具入参 `{spec:…}` 按白名单子集
>      画成原生 Compose（文本/标题、callout、badge、stat、hero、progress、
>      list、table、keyvalue、steps、timeline、代码、柱状图、card/row/col/
>      grid 布局）。mermaid/plot/3D 等引擎组件退化成「需在完整 Web 界面
>      查看」的提示，不再把整段 JSON 暴露在聊天里；未闭合围栏显示“正在
>      生成界面…”。工具卡片不再叠加静态转译的浏览器视图。
>    - 安装路径验证：App「添加插件 → 安装第三方插件」以
>      `@changfenhuang/dsh-genui` 安装成功，scoped 包与 katex /
>      mdast-util-from-markdown / react 依赖均落到 profile；Loader 行
>      `fiberPhase=active`，`package-inventory` 只有一行，插件列表只有一条。
>
> 实现位置：
> - bridge 插件（`deepseek-harness-mobile-bridge-plugin` 仓库，最新 v1.5.0）：
>   `src/slots/transpile.ts`（静态转译核心：tokenizer + rolldown IIFE 解析、
>   `_jsx`/`_jsxs`/`createElement`/minified `(0,n.jsx)(…)` → RenderNode、
>   组件内联、props 环境与 minified 位置兜底、**bundle 内联词典的 `t(key)`
>   解析**、预算）、`src/providers/transpile.ts`（`component-tree` + `bundles`
>   诊断 op）、`src/providers/client-slots.ts` 的 `dispatch` op
>   （→ `ui/<componentId>/<event>` 总线 + `client/refresh` 排队）；
> - App：`core/bridge`（`BridgeComponentTree`、`componentTree()`、
>   `dispatchUiEvent()`、`origin` 解码）、`plugin/mobile-ui/RenderTree.kt`
>   + `ComponentChips.kt`（RenderTree → Compose 渲染器与内嵌芯片）、
>   `feature/settings/PluginComponentPane.kt` + `SettingsRootScreen`
>   「插件界面」分组（`settings.section`）、`SettingsModelsPane`
>   （`settings.models.provider-card`）、`ChatScreen`/`BottomInputBar`
>   （`conversation.composer.dock` 与 `conversation.input.right`）；
>   事件驱动刷新经 `DshRepository.pluginComponentRefreshFlow` 到达每个
>   正在展示的组件。
>
> 真机踩到并修掉的两个缺陷（v1.4.0）：
> 1. 扫描根用 `existsSync` 做门槛——在 proot 里对翻译后路径的 `stat` 会失败，
>    而 `readdir` 读得到，结果第三方插件的 `node_modules` 永远进不了扫描根，
>    插件 UI 全空。现在无条件加入，扫描器自身对缺失目录容错。
> 2. 组件「早返回空状态」写法让转译器抓了空状态；现在取宽度最大的
>    top-level `return`（主渲染通常在最宽的那个分支）。

## 1. 现状与问题

App 端的插件页现在能列出并管理插件，但**插件自己声明的 UI 不会出现在
手机上**：

- 插件 client bundle 里调 `ctx.slots.register(...)` 注册的槽位
  （`settings.section`、`conversation.composer.dock`、
  `conversation.input.right`、`settings.models.provider-card` 等）只在
  浏览器壳里渲染；手机端没有任何渲染器。
- bridge 的 `client-slots`/`render-tree` 可以把一个组件序列化成 JSON 树，
  但这条路当前不可用：
  - `clientBundleScan=false`，所以 VM 求值被关掉；
  - 求值打开后，`evaluateBundleFile` 会收集 `missingExternals`，组件不可
    渲染时 `render-tree` 直接报错；
  - App 侧只有 `BridgeRenderTree` 数据模型，没有把它画出来的 Compose
    组件。

实测（当时的一个真实插件包）：

- 编译后的 `lib/client.js` 里没有 `createElement`/`jsx(`/`jsxs(` 字面量，
  它是 rolldown 输出的 IIFE，React 组件是函数调用返回的 stub element；
- 它确实注册了 4 个槽位（`settings.section` ×4，
  `conversation.composer.dock`、`conversation.input.right`、
  `provider-card` 各若干），但这些注册只在 Host 的客户端模块注册表里，
  手机端看不到；
- 目前手机上这个插件只显示为「已安装插件」里的一行，点进去的详情页只有
  标题/包名/状态，没有任何自绘内容。

## 2. 目标

- 给 bridge 增加一个 **`transpile` provider**：把插件 client bundle 的
  编译后 JS 静态转成 Compose 可渲染的 JSON 树，不需要 TSX 源码，也不需要
  在 Host 里真的跑插件代码；
- App 端实现 **RenderTree → Compose** 的渲染器：把 `BridgeRenderTree` 画
  成原生控件，并把用户交互（点击、输入、开关）回传给 Host；
- 让「第三方插件的 UI 都转换成原生」成为一条通用路径，而不是给某个插件
  写死一个页面。

非目标：

- 100% 还原浏览器视觉效果；CSS 布局、动画、复杂自定义组件只做到「可读、
  可用、结构对」。
- 在手机上跑 OAuth/网络/存储这类 Host 能力；这些仍然走 Host 的 rpc/事件
  通道，UI 只负责把入口和状态画出来。

## 3. 设计原则

1. **静态优先，求值兜底**：能静态提取的就不要在 Host 里执行插件代码；
   静态提取不出来的再考虑 VM 求值，而且仍然不把结果当成「真实 React 树」，
   只作为生成 JSON 的中间形态。
2. **只渲染「声明的结构」**：静态转译器只负责把组件输出成一个可序列化的
   树；运行时的状态、副作用、条件渲染都不在树里，交互由 App 回传给 Host，
   Host 再给出新的树或状态。
3. **失败可解释**：无法转换的组件、缺失的依赖、超预算的树都在响应里给出
   原因和坐标，客户端显示「暂不支持」而不是空白。
4. **安全边界**：转译/求值都在 Host 内完成，但限制 bundle 大小、解析/渲染
   预算、禁用网络/定时器/动态 import；手机端只做渲染和事件转发，不执行
   任何插件 JS。

## 4. bridge 侧：`transpile` provider

### 4.1 接口形态

新增一个只读 provider（或给 `client-slots` 加一个 op，倾向单独 provider，
避免破坏现有能力表）：

```text
provider: transpile
version: 1
capabilities: ["component-tree"]
ops:
  component-tree(args: { componentId: string, props?: object }):
    { componentId, tree: RenderNode|null, warnings: string[], truncated: boolean }
```

- `componentId` 与 `client-slots/describe`/`render-tree` 用同一套身份：
  `<package>#<id>`（静态提取和 VM 求值输出一致）。
- `props` 是 JSON 安全的对象；转译器不解析表达式，只把字面量、静态对象
  和已知的静态函数参数纳入树。
- 返回的 `tree` 与 `render-tree` 的数据模型一致（`BridgeRenderNode`），
  客户端渲染器不需要区分来源。

### 4.2 从编译后 JS 静态提取

不走 TypeScript 编译器 API（那需要 `typescript` 依赖，rootfs 没有，而且
输入是 TSX），改为解析**编译后的 JavaScript**：

1. 用宿主已经有的解析器（`node:module` 不提供 AST；可以引入
   `acorn`/`meriyah` 这类小型 parser，或者复用 DSH 已经有的 AST 工具）
   把 `lib/client.js` 解析成 AST；
2. 找到 rolldown 的输出结构：`window.__ModuleLoader__.load({ id, factory })`
   里的 factory，执行到 `ctx.slots.register` 调用；
3. 对每个 `register({ ... }, Component)`：
   - 记录 slot/name/id/order/label（静态字段）；
   - `Component` 是函数/箭头函数：解析它的函数体，把 `return` 的表达式
     转成 `ElementNode` 树；
   - 识别 rolldown 的 JSX 运行时形式：`_jsx(type, props)` /
     `_jsxs(type, props)` / `React.createElement(type, props, ...children)`
     → 统一成 `ElementNode(type, props, children)`；
   - 字面量文本 → `TextNode`；无法静态解析的表达式 → `OpaqueNode`，带上
     原始表达式文本和位置，供 UI 显示「此处由插件动态控制」。
4. 样式：把 `style={{ color: 'red' }}` 这类静态对象转成 JSON props；
   `className`/`css` 里的字符串原样保留，客户端忽略或映射到主题 token。

无法解析的情况（动态导入、条件组件、运行时拼接的 props）直接报告为
`OpaqueNode` 或 warning，不猜测。

### 4.3 与现有 evaluate/render-tree 的关系

- `evaluate.ts` 的 VM 求值仍然保留（`clientBundleScan=true` 时），但它的
  角色变成「验证/补全静态提取不到的注册」：它能捕获 `ctx.slots.inject`
  等副作用，也能确认一个组件在 VM 里能渲染。
- `render-tree` 的现有实现是「调用组件函数 + 序列化 stub element」；
  `transpile` 的输出是同一数据模型，所以 App 端渲染器可以无差别消费。
- `transpile` 的结果可以反向用来回答 `icon-svg`（树里有 `svg`/`path`
  时直接序列化），不需要再求值。

## 5. App 侧：RenderTree → Compose 渲染器

### 5.1 渲染器职责

输入 `BridgeRenderTree`，输出一个可交互的 Compose 树：

- `kind == "element"`：按 `type` 映射到 Compose 控件；
- `kind == "text"`：`Text(value)`；
- `kind == "opaque"`：占位块（虚线框 + 文案「插件控制」），点击透传事件
  给 Host；
- `children` 递归渲染；`props` 里的 `style`/`className`/`onClick` 等映射到
  Modifier/事件。

标签映射（第一版只覆盖能覆盖官方卡片和常见插件 UI 的子集）：

| JS type | Compose |
| --- | --- |
| `div` | `Column`（默认纵向）或 `Row`（props.layout=row）|
| `span` | `Text` / inline content |
| `button` | `Button` |
| `input[type=text]` | `TextField` |
| `input[type=checkbox]` | `Switch` |
| `svg`/`path`/`rect`/`circle`/`g`/`defs` | 走现有 `DshSvgIcon` 的 SVG 路径（或换成矢量解析器） |
| `a` | `Text` + `clickable { openUrl }`（通过 Host rpc 打开） |
| 其它 | `Box` + 占位文本 |

props 处理：

- `style` 里的 `color/backgroundColor/fontSize/fontWeight/margin/padding/
  borderRadius/flex/width/height` 映射到 Modifier/主题色；无法识别的键忽略；
- `onClick`、`onChange`、`onSubmit` 不真的执行 JS：序列化成事件名 +
  componentId + props 摘要，回传给 Host（见 5.3）；
- `className` 保留原值，客户端查表（例如 `card`、`muted`）或忽略。

### 5.2 状态与刷新

静态树本身没有 React state；插件的动态内容（用量、登录状态、模型列表）
需要 Host 给出。设计两种刷新方式：

1. **轮询**：App 定时调 `transpile/component-tree`（同一 componentId +
   当前 props），树变了就重组；
2. **事件驱动**：插件在 Host 侧 `ctx.emit('client/refresh', { componentId })`，
   bridge 的 `events` provider 已经能把消息推到手机（App 已有
   `pendingPluginEvents` 轮询），收到后触发对应组件的重新拉取。

事件比轮询更省，但需要插件配合；轮询是第一版的兜底。

### 5.3 交互回传

用户点击/输入后，App 需要让插件知道。新增一个 bridge op（放在 `rpc`
provider 或单独 `client-events` provider）：

```text
op: dispatch(args: {
  componentId: string,
  event: "press" | "input" | "toggle" | "submit",
  payload?: object
}): { ok: true }
```

Host 侧把事件转成插件能理解的形式：调用插件注册时存的 `onAction` 回调
（如果组件注册时给了），或发给插件的 `ctx.on('ui/<componentId>/<event>')`
监听器。回传的 payload 是 JSON 安全的（字符串/数字/布尔/对象），不携带
Compose 状态对象。

### 5.4 页面集成点

- `settings.section`：设置根页面按 bridge 的 `settings.page` 列出条目，点
  进去就是该插件的渲染树（而不是只有表单 schema 的页面）；
- `conversation.composer.dock`：聊天输入区上方的胶囊/卡片；
- `conversation.input.right`：输入框右侧按钮；
- `settings.models.provider-card`：模型设置里的 provider 卡片；
- 其它 `plugins.item`/`plugins.row.config`：沿用现有插件页的分组逻辑。

## 6. 数据与协议

复用现有 `BridgeRenderNode`（`kind/type/props/children/value/reason`），
不需要改 wire 格式；`transpile` 的输出和 `render-tree` 一致，客户端可以
写一套渲染器吃两边。

新增/调整：

- `client-slots/list` 的 registration 增加 `origin: "static" | "evaluated"`，
  方便 UI 标出「这个条目来自静态提取，可能不完整」；
- `transpile/component-tree` 的 `truncated: true` 表示预算被截断，UI 显示
  「内容过长，已截断」。

## 7. 实现步骤（建议顺序）

1. ✅ bridge：`transpile` provider 骨架 + `component-tree` op（能解析
   rolldown IIFE、识别 register 调用）；
2. ✅ bridge：`_jsx`/`_jsxs`/`createElement` 到 `ElementNode` 的转换 + 静态
   props/children；含 minified `(0, n.jsx)(…)` 调用形式与组件内联；
3. ✅ App：最小渲染器（div/span/button/input/switch/svg/a + text/opaque +
   点击透传），接到设置页「插件界面」分组；
4. ✅ bridge：`dispatch` op + Host 侧事件桥接（`ui/<componentId>/<event>`
   总线消息 + `client/refresh` 排队）；
5. ✅ App：四个槽位全部接入（settings.section / composer.dock /
   input.right / provider-card）；
6. ✅ 边界处理：事件驱动刷新（`client/refresh` → `pluginComponentRefreshFlow`
   → 每个组件自动重拉）、插件卸载/停用/Host 重启时 `refreshPluginSettingsPages`
   整体重建条目流使组件消失。缓存 + 差量刷新留待第二阶段。

## 8. 风险与开放问题

- **样式保真**：浏览器插件大量使用 CSS 布局/自定义组件，原生端只能做到
  「结构对、可读」，视觉差异要接受；
- **事件语义**：React 的受控组件模式（onChange + value）在静态树上没有
  对应物，第一版只做非受控输入 + 提交时回传；
- **Host 侧状态**：插件的登录状态、用量数据存在 Host 的存储里，App 端
  需要轮询或事件才能拿到，第一版可能显示旧值；
- **性能**：每次刷新都要重新转译/传输整棵树；需要缓存 + 差量刷新（第二
  阶段）；
- **安全**：转译器只解析文本，不执行代码；VM 求值仍然默认关闭，只有用户
  明确打开「预览插件界面」时才允许。

## 9. 验收标准

- 安装插件后，设置页出现它的设置条目，点进去
  显示插件声明的卡片布局（账号、用量、登录按钮占位）；
- 点击登录按钮 → App 把事件回传给 Host → 插件在 Host 侧开始 OAuth 流程，
  状态变化通过事件/轮询回到手机；
- 任意官方 bundle 的 `plugins.item` 组件能通过 `transpile` 拿到和
  `render-tree` 一致（或更接近）的树；
- 转译失败/预算截断时 UI 显示可读的原因，而不是空白或崩溃。
