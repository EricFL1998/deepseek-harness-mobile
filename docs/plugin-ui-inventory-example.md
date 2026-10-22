# 插件 UI / 功能清单：dsh-plugin-demo

> 由 `tools/plugin_ui_scanner.py` 直接扫描插件源码（含 `.js.map` 内嵌 TSX）生成。
> 不再需要用户截图：slot、entry、组件、RPC、文案与组件树都在这里。

- 包：`dsh-plugin-demo` 0.1.0
- 源文件：7
- 注册落点：2
- RPC 端点：2

## 落点与原生状态

| slot | entry | order | component | 原生落点 | 宿主 | 原生实现 | 数据依赖 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `conversation.composer.dock` | `demo-dock` | 10 | `DemoDockChip` | `COMPOSER_DOCK` | 是 | 是 | `rpc:bump`, `rpc:status` |
| `settings.section` | `demo` | 10 | `DemoSettingsSection` | `SETTINGS_SHEET` | 是 | 是 | `rpc:bump`, `rpc:status` |

## 组件与可见 UI

### `DemoDockChip`（conversation.composer.dock）


```json
[
  {
    "type": "any",
    "text": "",
    "children": []
  }
]
```

### `DemoSettingsSection`（settings.section）


```json
[
  {
    "type": "any",
    "text": "",
    "children": []
  }
]
```

## RPC / 数据

| 端点/方法 |
| --- |
| `bump` |
| `status` |
