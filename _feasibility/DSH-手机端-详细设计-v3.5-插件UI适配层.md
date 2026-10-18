> **最终基线（v4.0）**：完整实现设计见 [DSH-手机端-最终设计-v4.0.md](DSH-手机端-最终设计-v4.0.md)。本文保留为专题设计/决策背景。

# v3.5 修订：插件 UI 的 Native 适配层（Surface Adapter + Web 岛 + SDUI）

| 项 | 内容 |
|---|---|
| 版本 | v3.5（客户端 UI 层的补充） |
| 日期 | 2026-10-17 |
| 问题 | 手机是原生 UI，插件的入口和界面该显示在哪？是否要做「现有插件 UI → native」的转译层？以后插件只声明 layout 够不够？ |
| 结论 | **方向对，但「转译层」不是把 React 自动翻译成原生——那等于重写浏览器。** 实际要做的是三层适配：**Surface Adapter（入口/位置映射）+ Web 岛（内容托管）+ SDUI（未来插件声明 layout 后原生渲染）**。现有插件不改代码，入口 native 化、内容由岛承载；新插件声明 layout 后可以完全原生。 |

---

## 0. 先说清楚「转译层」是什么

| 你可能设想的 | 现实 |
|---|---|
| 把插件 React 组件自动转成 Compose | ❌ 需要实现 DOM/CSS/布局引擎，等于浏览器 |
| 把插件 DOM 快照映射成原生控件 | ❌ 动态交互、CSS、Canvas/SVG 无法映射 |
| 把插件的「入口/页面」注册信息映射到原生位置 | ✅ 可行（Surface Adapter） |
| 把插件声明的 layout（JSON）渲染成原生 | ✅ 可行（SDUI，v3.0 §7） |
| 现有插件内容用 WebView 承载 | ✅ 可行（Web 岛） |

**所以「转译层」= 入口转译 + 内容托管 + 未来声明式渲染，不是 UI 代码翻译。**

---

## 1. 为什么现有插件 UI 不能自动转原生

1. **插件 UI 是任意 React 代码**：`client.js` 注册的是 React 组件，内部可以用任意 DOM、CSS、SVG、Canvas、第三方库；没有可解析的声明。
2. **依赖完整客户端运行时**：组件依赖 `ctx`、client services、slots、主题、locale、store；单独抽出来无法渲染。
3. **5,000+ 插件、持续更新**：没有稳定的中间表示，无法逐版本转译。
4. **桌面布局假设**：很多插件本身就是桌面悬浮/宽面板，转成手机原生也没有意义。

结论：现有插件只能「原样运行在 Web 环境」；native 端要做的是**给它们安排位置和容器**，而不是翻译它们的代码。

---

## 2. 插件 UI 在 Native 上的四种形态

| 形态 | 适用 | 实现 |
|---|---|---|
| **N1 原生 SDUI** | 插件声明了 `dsh.mobile.layout` | 原生渲染（v3.0 §7） |
| **N2 岛页面（全屏/Sheet）** | 现有插件的主界面、设置页、市场、面板 | Web 岛加载官方客户端，打开对应页面 |
| **N3 岛浮层（Sheet/抽屉）** | 轻量浮层、overlay、dock 装饰 | 作为 Sheet 打开；不做内联像素级还原 |
| **N4 桌面专属（隐藏/提示）** | 桌宠、桌面悬浮、hover 交互 | 手机端隐藏或标注「桌面专属」 |

**关键：Native 负责「入口 + 容器 + 导航」，Web 岛负责「内容」。**

---

## 3. Surface Adapter：入口/位置映射

### 3.1 插件会在哪些地方出现

DSH 客户端的扩展点（slot/服务）：

| 类型 | 典型 slot / 服务 | 插件示例 |
|---|---|---|
| 设置页 | `settings.page` / 插件设置注册 | dsh-context、dshmarket、dsh-purge |
| 侧栏入口 | `sidebar.*` / `sidebar.footer.action` | 插件市场、阅读器、终端 |
| 输入区装饰/动作 | `conversation.composer.dock` | 表情、快捷操作 |
| 全局浮层 | `shell.overlay` | 桌宠、挂件、音乐播放器 |
| 消息动作/渲染 | `conversation.message.*` / 工具卡 renderer | 消息反馈、自定义工具展示 |
| 主题/全局 CSS | theme 服务 | 主题类插件 |
| 命令/快捷键 | commands / shortcuts | 各类工具 |

### 3.2 Native 放置策略

| 插件 surface | Native 位置 | 打开方式 |
|---|---|---|
| 设置页 | 设置 → 插件 → 条目 | 岛 Sheet（打开对应设置页） |
| 侧栏入口 | 侧栏「插件」区 / 更多菜单 | 岛页面 |
| 输入区动作 | 输入框「+」菜单 | 岛 Sheet |
| 全局浮层 | 「插件浮层」入口 | 岛 Sheet（不内联） |
| 消息动作 | 消息长按菜单 | 岛 Sheet |
| 工具卡 renderer | 通用工具卡 + 「查看原始」 | 岛 Sheet |
| 主题/CSS | 仅作用于岛；原生不跟随 | 设置中说明 |
| 桌面专属 | 不显示，或标注「桌面专属」 | — |

**原则：native 只承诺「入口在、能打开、能用」；不承诺「像素级内联」。**

---

## 4. 如何发现现有插件的入口：Surface Catalog

现有插件的 `package.json` 只声明 `dsh.client`，**不声明它注册了哪些 slot**。三个来源组合：

### 4.1 运行时目录（首选）

App 内置一个**伴生 Client 插件** `dsh-native-surface-catalog`（我们自己的插件，不修改任何现有插件）：

1. 以 `immediately: true` 最先加载；
2. 包装/监听 `ctx.slots.register`（或读取 slot registry 的公开列表），记录 `(pluginId → slotName, order, props 摘要)`；
3. 通过 `postMessage` 把 catalog 发给 native；
4. 同时提供 `openSurface(surfaceId)`：用客户端服务打开对应设置页/侧栏面板（避免模拟点击）。

> 需要在 spike 中验证：slot registry 是否可枚举、插件加载顺序是否可控、打开页面的客户端服务是否可用。若不可行，退化到 4.3。

### 4.2 主机侧清单

Host 的 plugin inventory 能列出已安装插件与启用状态；native 用它生成「插件」列表，即使没有 surface 细节也能提供「打开插件中心」。

### 4.3 兜底

- 维护一个已知插件 → surface 的映射表（top 100 插件）；
- 未知插件统一进「插件」页 → 岛根页面，用户手动导航；
- 未来插件可用 `dsh.mobile.surfaces` 显式声明入口（可选字段）。

---

## 5. Web 岛导航桥

官方 SPA 没有 pathname 路由（v2.0 §5），不能直接 deep link 到插件页。桥的做法：

```text
Native → Island:  { type: 'openSurface', surfaceId, pluginId }
Island 伴生插件: 调用客户端服务（打开设置页 / 侧栏面板 / 命令）
Island → Native:  { type: 'surfaceOpened', surfaceId } / { type: 'error', reason }
```

- 打开失败：回退到岛根页面 + 提示「请在插件中心中打开」。
- 返回：岛内返回栈交给 WebView；native 返回键先问岛，再关闭 Sheet。
- 不允许 native 直接操作 DOM/点击；只用伴生插件暴露的语义 API。

---

## 6. 未来插件：只声明 layout 就够了吗？

不够，还需要声明**入口**（否则 native 不知道它出现在哪）。建议两个可选字段：

```jsonc
"dsh": {
  "mobile": {
    "schema": 1,
    "layout": { "entry": "./mobile/ui.json" },        // 页面内容（SDUI）
    "surfaces": [                                       // 入口位置（可选）
      { "slot": "settings.page", "title": "上下文", "icon": "chart" },
      { "slot": "sidebar.footer.action", "title": "上下文" }
    ],
    "fallback": { "web": true }
  }
}
```

- 有 `layout` + `surfaces` → 入口和内容都原生。
- 只有 `layout` → 入口走默认位置（插件页），内容原生。
- 都没有 → Web 岛（现有插件行为）。

**所以「以后插件只声明 layout」要补一句：最好同时声明 surface；不声明也能工作，只是入口位置由客户端决定。**

---

## 7. UX 细节

- 插件入口统一带「插件」标记，用户知道这是扩展功能。
- 岛打开的插件页顶部显示「插件 · 网页模式 · 返回原生」，避免误以为原生。
- 桌面专属插件在插件列表中标注「桌面专属，手机不可用」，不静默消失。
- 插件权限/能力提示：首次打开时展示它需要的能力（文件/网络/命令）。
- 通知/深链：`dsh://plugin/{id}` 打开对应入口。

---

## 8. 安全

| 风险 | 对策 |
|---|---|
| 岛内插件调用原生桥 | origin 白名单 + 方法白名单 + 参数校验 |
| 伴生插件被仿冒 | 固定包名/签名；仅内置 |
| 插件页面钓鱼 | 岛顶栏显示真实插件名；外链白名单 |
| SDUI 注入 | schema 校验、组件白名单、无代码 |
| 插件读文件 | 走 Host 权限；文件访问确认 |

---

## 9. 工期

| 工作 | 周期 |
|---|---|
| 伴生 Client 插件（catalog + 导航桥） | 3–4 周 |
| Surface Adapter（映射表、放置、回退） | 2–3 周 |
| 岛 Sheet/页面容器与返回栈 | 1–2 周 |
| SDUI surfaces 声明与原生入口 | 1–2 周 |
| 测试（插件矩阵、导航、桌面专属） | 2 周 |
| **合计** | **约 9–13 周（净增 4–6 周，可与 v3.0 §7 并行）** |

---

## 10. 开放问题

1. slot registry 能否在伴生插件中安全枚举（不依赖私有字段）。
2. 打开设置页/侧栏面板的客户端服务是否稳定、是否有公开 API。
3. 插件加载顺序：伴生插件能否保证最先加载并完成包装。
4. 是否推动上游把 `mobile.layout` / `mobile.surfaces` 作为官方可选字段。
5. 桌面专属插件（桌宠/悬浮）在手机端的处理：隐藏、折叠，还是允许作为 Sheet 打开。

---

> **一句话**：需要的不是「把插件 UI 翻译成原生」，而是「**把插件的入口翻译成原生，把插件的内容放进合适的容器，把未来的插件引导到声明式 layout**」。现有插件零改动、能用；新插件声明后可以完全原生。

