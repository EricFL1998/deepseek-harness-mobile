# v1.4 插件声明式 Mobile UI：插件写 UI 数据，客户端渲染

> **v2.0 已收敛**：完整实现基线见 [`DSH-Android客户端-详细设计-v2.0.md`](DSH-Android客户端-详细设计-v2.0.md)。本文保留 SDUI 契约的设计理由与取舍。

- 日期：2026-10-17
- 需求：插件自己声明一份 mobile UI，客户端负责渲染
- 结论：**可行，但有一个前提必须成立——mobile UI 必须是「数据」（JSON schema），不能是 React 代码。** 数据才能同时被原生渲染器和 Web 渲染器消费；React 代码只有 WebView 能跑，就又回到套壳。

---

## 1. 先分清三条路线

| 插件声明 mobile UI 的形式 | 谁能渲染 | 是否 native | 契约变更 |
|---|---|---|---|
| React 组件 / CSS 变体（`mobile.tsx`） | 只有 WebView | ❌ | 不用改契约，但客户端只能套壳 |
| **声明式 JSON UI（SDUI）** | 原生 Compose + Web | ✅ | 可选增量字段 `dsh.mobile` |
| 每个平台带 native 代码 | 要插件带二进制 | ✅ | 不可行（插件生态不会这么写） |

你要的「插件声明、客户端 native 渲染」= 第二种。

---

## 2. 契约：`dsh.mobile`（可选、向后兼容）

```jsonc
// package.json —— 只新增 dsh.mobile，dsh.client 原样不动
{
  "dsh": {
    "client": {
      "platform": "web",
      "inject": ["@deepseek-ai/dsh-client-ui-settings"]
    },
    "mobile": {
      "schema": 1,
      "entry": "./mobile/ui.json",          // 静态声明
      // 或："endpoint": "mypkg/mobile-render"  // 动态声明：host 返回 UI 树
      "actions": {
        "refresh": { "remote": "mypkg/status" },
        "openSettings": { "navigate": "settings.page" }
      }
    }
  }
}
```

```json
// mobile/ui.json —— 复用 GenUI 词表，纯数据
{
  "type": "page",
  "title": "上下文",
  "data": { "source": { "remote": "context/snapshot" } },
  "children": [
    { "type": "chart", "kind": "donut", "data": "{{breakdown}}" },
    { "type": "keyvalue", "pairs": "{{stats}}" },
    { "type": "button", "label": "刷新", "action": "refresh" }
  ]
}
```

规则：

- `{{path}}` 只做**路径取值**，不是 JS 表达式，不执行代码。
- 没声明 `dsh.mobile` 的插件完全不受影响（继续走 WebView）。
- `dsh.client` 一行不改；移动声明是**新增的可选字段**，旧插件零改动。

---

## 3. 数据与动作（这是能不能替代 WebView 的关键）

| 声明 | 含义 | 客户端行为 |
|---|---|---|
| `data.source.remote` | 调用插件 Host 半暴露的 Remote 方法 | 走官方 `/api`，结果 JSON 直接绑定 |
| `data.source.event` | 订阅插件/会话事件 | 走官方 `/api/remote.mux` 流 |
| `data.source.static` | 静态 props | 直接渲染 |
| `action.remote` | 调用 Remote 方法 | 走官方 `/api` |
| `action.navigate` | 客户端内部跳转 | 原生导航 |
| `action.openUrl` | 打开外链 | 白名单校验 |

**安全边界**：组件白名单、动作白名单、无 HTML/JS、URL 白名单、任何写操作要求用户可见。声明式 UI 不允许插件在客户端执行代码——这正是它能被原生渲染的前提。

---

## 4. 渲染与 fallback 决策树

```
插件有 dsh.mobile？
├─ 无 → 桌面 Web UI（WebView 岛）
└─ 有
   ├─ schema 版本支持 + 组件全支持 → 原生渲染
   ├─ 部分组件不支持 → 整屏 fallback WebView（不做半原生混排）
   └─ 数据源失败 → 原生错误态 + 重试（不崩溃）
```

原则：**宁可整屏回 WebView，也不要拼一半原生一半 Web**——半成品体验比纯 Web 更糟。

---

## 5. 两个渲染器

| 渲染器 | 实现 | 范围 |
|---|---|---|
| 原生（Compose） | 自己实现词表子集 | v1：page/section/text/list/table/keyvalue/button/input/chart/callout/tabs/progress |
| Web（岛内） | 复用 `@changfenhuang/dsh-genui`（MIT，omdsh-dev）的词表与渲染器 | 全词表 + 插件向 data/action 扩展 |

> GenUI 原本是「模型 → UI」；这里把同一套组件词表用于「插件 → UI」。好处：词表、样式、渲染器都能复用，插件作者也只需要学一套声明。

**一致性测试**：同一份 spec 分别在原生与 Web 渲染，截图 + 关键交互对比，纳入 CI。

---

## 6. 版本与兼容

- `schema` 用整数版本；客户端声明支持范围；插件可在不支持时提供降级声明。
- 未知组件 → 整屏 fallback，不猜测。
- 旧插件 → 零改动，走 WebView 岛。
- 原生客户端不支持某插件时，用户仍能通过岛完整使用——**不会出现「插件不可用」**。

---

## 7. 工作量（v1）

| 工作 | 时间 |
|---|---|
| schema 定义 + 校验器 + 文档 + 示例插件 | 2–3 周 |
| 原生渲染器 v1（约 10 个组件） | 4–6 周 |
| Web 渲染器（复用 GenUI）+ 插件向 data/action | 2–3 周 |
| 一致性测试 + fallback + 安全 | 2 周 |
| **合计 v1** | **约 2.5–3.5 人月** |
| 后续每加一个组件 | 2–5 天 |

注意：这不含「让现有插件补 mobile 声明」的工作——那属于插件作者侧，是可选加分项，不强制。

---

## 8. 风险

| 风险 | 对策 |
|---|---|
| 词表表达力不够，复杂插件仍要 WebView | 接受；复杂插件保留岛；词表按真实需求迭代 |
| 两个渲染器行为漂移 | 同一 spec 的 conformance 测试 + 截图对比 |
| 数据绑定做成表达式语言后失控 | 只做路径取值 + 简单模板，不做图灵完备 |
| 插件采用率低 | mobile 声明是可选加分项；旧插件不受影响 |
| 声明式 UI 的安全问题 | 白名单 + 无代码 + 动作审计；写操作需用户确认 |
| 插件作者想完全控制布局 | 保留 WebView 岛作为逃生通道 |

---

## 9. 建议

1. **词表直接对齐 GenUI**，不要自造一套（省 1–2 个月，插件作者只学一套）。
2. 先拿一个只读插件（如 `dsh-context` 的仪表盘）做端到端示例：声明 → 原生渲染 → 数据刷新 → fallback。
3. 把 `dsh.mobile` 定位为**可选增量契约**：旧插件永远可用；新插件想要原生体验才加。
4. 不要把「React mobile 变体」和「声明式 mobile UI」混在一起：前者只能 WebView 渲染，后者才能原生。
