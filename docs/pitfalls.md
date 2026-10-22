# 原生 UI 接入检查清单与最佳实践

> 本清单按接入模块组织：每节先给正确做法，再列可勾选的检查项。新插件或新落点
> 接入时按 1 → 6 顺序执行。完整参照实现是 Demo 插件：Web 半边 `samples/demo-js`，
> 伴生 APK `samples/demo-native`。

---

## 1. Slot / 宿主

### 1.1 为 slot 贡献补齐宿主能力

**正确做法**：`SlotBridgeHost` 在基础 `PluginHostApi` 上补齐 `PluginRpcHost` /
`PluginModelHost`，`NativeSlotHost` 统一用它渲染 contributions；App 注入
`NativeHost.rpcHandler`。需要 RPC 的贡献先确认 `callRpc` 能拿到结果，再写 UI。
Demo dock 读取 `demo.status` 依赖这条链路；`rpc == null` 时显示可读的
“Host RPC 未连接”，不要留白。

**检查项**

- [ ] 扫描报告里出现该落点的 `rpc:*` 依赖；
- [ ] 首次接入先验证 `callRpc` 返回 `{ result: { ok, value } }`；
- [ ] 落点对 `PluginRpcHost` 缺失有降级 UI。

### 1.2 让注册表变化可观察

**正确做法**：`SlotRegistry` 暴露 Compose 可观察的 `revision`，register/unregister
时递增；`resolve()` 读取 revision，让已组合的 `NativeSlotHost` 随注册表变化重组。

**检查项**

- [ ] 插件 APK 在页面组合之后装载时，已有落点自动出现；
- [ ] 切换会话、重进聊天页后 dock 仍在。

### 1.3 拆分内置 slot 的多条 entry

**正确做法**：composer dock 的 tok/s、缓存命中、上下文分别用
`COMPOSER_DOCK_ACTIVITY`、`COMPOSER_DOCK_USAGE`、`COMPOSER_DOCK_CONTEXT`，
各自用 `OfficialSlot` 包装，插件只覆盖自己需要的 entry。

**检查项**

- [ ] 同一 slot 有多条 entry 时，已按 activity/usage 拆分；
- [ ] 插件条目 order 不与内置条目冲突。

### 1.4 为 keyed slot 同时补宿主

**正确做法**：需要 `settings.models.provider-card` 扩展时，同时补 NativeSlot
映射与 `SettingsModelsPane` 宿主容器，再让伴侣注册贡献。

**检查项**

- [ ] `--companion --check` 无“未映射/无宿主”；
- [ ] 设置-模型页能看到插件区块。

---

## 2. 伴侣 APK / 装载

### 2.0 使用完整包名

**正确做法**：`com.dsh.mobile.<完整插件名>`，保留 `dsh-plugin-` 前缀并转小写，
非 `[a-z0-9]` 字符替换为 `_`：

```
dsh-plugin-demo        -> com.dsh.mobile.dsh_plugin_demo
dsh-plugin-my-plugin   -> com.dsh.mobile.dsh_plugin_my_plugin
```

同一 JS 包的附加能力加 `_<capability>` 后缀，例如
`com.dsh.mobile.dsh_plugin_my_plugin_extra`。`dsh.plugin.id` 与包名解耦，用插件
自己的反向域名 id（Demo：`demo.native-ui`）。

**检查项**

- [ ] `namespace`、`applicationId`、manifest `package` 一致；
- [ ] 包名只含小写字母、数字、下划线；
- [ ] 包名在首次发布前定稿。

### 2.1 用宿主提供的 ClassLoader 加载伴侣

**正确做法**：优先使用 `packageContext.classLoader`（`createPackageContext` 自带），
加载失败再回退 `DexClassLoader`；失败路径必须打日志，并在插件详情显示“加载失败”。

**检查项**

- [ ] 日志无 `Failed to find entry 'classes.dex'`；
- [ ] 伴侣装载失败在界面上可见，不是空白。

### 2.2 保护设备上已有的伴侣

**正确做法**：安装前确认 APK 来源与版本；优先从源码构建并保留原 APK；调试安装
使用独立 package id，或先 `adb pull` 备份，再 `adb install -r`。

**检查项**

- [ ] 安装命令注明来源与版本；
- [ ] 覆盖前已备份，或使用独立 package id。

### 2.3 核对 jsPackage 配对

**正确做法**：manifest 的 `dsh.plugin.jsPackage` 必须等于 JS 包 `package.json.name`
（Demo 都是 `dsh-plugin-demo`）；安装/卸载后回到前台触发重扫。

**检查项**

- [ ] 插件详情“包含的组件”数量正确（Demo：2）；
- [ ] 安装/卸载后落点即时更新。

---

## 3. 数据 / RPC

### 3.1 首屏 RPC 退避重试

**正确做法**：贡献内对首次加载做 3–6 次退避（800ms / 2s），成功即停；失败显示
错误态，不伪造数据。Demo dock 按 3 次重试读取 `demo.status`。

**检查项**

- [ ] 冷启动首帧能拿到数据，或显示可读错误；
- [ ] 401 恢复后自动重试成功；
- [ ] 失败态不显示假数据。

### 3.2 按周期或事件刷新

**正确做法**：按 30–60s 周期重读，或等 `client/refresh` 后重拉，或用户操作后立即
重读；用 `LaunchedEffect(key)` 承载循环，页面离开自动取消；权威数据留在 Host，
APK 内不维护业务状态。Demo dock 每 30s 重读一次 `demo.status`。

**检查项**

- [ ] 页面停留期间数据随 Host 变化；
- [ ] 页面离开后无残留轮询；
- [ ] 计数等数据不在 APK 内本地累加。

### 3.3 选择态由 App 通过 spec 下发

**正确做法**：App 把当前选择（如 `selectedModelId`、`provider`）放进对应落点的
spec；原生只展示 spec 明确下发的选择，并配合 Host 返回值，不自行推断。

**检查项**

- [ ] 切换模型/provider 后条目数据正确；
- [ ] spec 缺失时显示明确默认或空态，而不是猜测。

### 3.4 写宿主状态用可选接口

**正确做法**：需要改会话模型/推理档位时，新增 `PluginModelHost.selectModel`
（App 注入、`SlotBridgeHost` 代理）；纯 RPC 写路径（如 Demo 的 `demo.reset`）
仍走 `callRpc`，两条通道不混用。

**检查项**

- [ ] 写操作后宿主状态生效；
- [ ] 插件不直接使用 Host 内部协议。

---

## 4. UI 定位 / 样式

### 4.1 锚定浮层用 PluginPopover

**正确做法**：把触发元素与 `PluginPopover` 放在同一个 `Box` 内，由
`PopupPositionProvider` 贴上方 6dp、右对齐、窗口内夹取；只有真正的模态流程
（确认框、全屏选择器）用 `Dialog`。

**检查项**

- [ ] 浮层贴在触发元素旁，不居中；
- [ ] 不遮挡输入框发送按钮；
- [ ] 扫描器无“有锚定线索但伴侣没有 Popup”的警告。

### 4.2 浮层材质来自 PluginTheme

**正确做法**：使用 `colorPopupSurface`、`colorPopupBorder`、`cornerPopup`、
`textPopupTitle` / `textPopupBody`；不自己画 Material Card，不硬编码颜色、
圆角、阴影。这些字段由 App 从 `DshPanelColors` / `DshMenuCard` 映射。

**检查项**

- [ ] 浮层与模型菜单、权限菜单观感一致；
- [ ] 深色/浅色模式都正确；
- [ ] 代码中无浮层硬编码颜色。

### 4.3 浮层宽度随内容

**正确做法**：`PluginPopover` 使用 `width(IntrinsicSize.Max).widthIn(max = 320.dp)`；
内容多宽就多宽，超长才换行/滚动。

**检查项**

- [ ] 短内容（如 `计数 42`、`上次动作 boot`）不出现大块空白；
- [ ] 长内容不溢出、不撑破窗口。

### 4.4 dock 放在内置位置

**正确做法**：`NativeSlotInline(COMPOSER_DOCK)` 放进 `ComposerDockBar`；tok/s、
缓存、上下文各自独立 slot，插件条目按 order 排在上下文之后。

**检查项**

- [ ] dock 胶囊在底部状态行，而不是输入卡上方；
- [ ] 与内置条目无重叠、无错位。

---

## 5. 扫描器 / 流程

### 5.1 用 placement hints 覆盖定位与样式

**正确做法**：`plugin_ui_scanner.py` 提取 `useAnchoredPosition`、`createPortal`、
`anchorRef`、`role=dialog`、`position: fixed/absolute`、`side`/`align` 等线索，
并在 companion 检查里判断是否有 `Popup` / `PluginPopover`；报告保留“定位/交互”栏。

**检查项**

- [ ] 报告包含定位/交互信息；
- [ ] 锚定浮层落点在伴侣侧有 `PluginPopover`。

### 5.2 扫描插件自己的包

**正确做法**：`--plugin` 指向插件包本身：优先读 `.js.map` 的 sourcesContent，
其次 `src/*.tsx`，最后 `lib/client.js`；内置落点清单用 `--rootfs` 生成。

**检查项**

- [ ] 只依赖插件包即可运行，不要求额外仓库；
- [ ] 解析失败时能回退到 `lib/client.js`。

### 5.3 对比插件 UI 与伴侣实现

**正确做法**：`--companion` 扫描伴侣源码里的 `NativeSlot.X`、`SlotRegistry.register`、
`PageRegistry.register`；`--check` 要求“插件有 UI → App 有映射和宿主 → 伴侣有实现”，
任一侧缺失即失败。

**检查项**

- [ ] 报告每行“原生实现”为“是”；
- [ ] `--check` 退出码为 0。

---

## 6. 验收清单（每次接入新 UI 必跑）

- [ ] `python tools/plugin_ui_scanner.py --plugin samples/demo-js --companion samples/demo-native/src/main/kotlin --check` 通过；
- [ ] 报告里每个 slot 的 `原生实现` 为“是”（Demo：2/2）；
- [ ] 内置 slot 映射/宿主：`slot_inventory.py` + `SlotCoverageTest` 通过；
- [ ] 首屏 RPC 有重试（Demo dock：3 次，800ms/2s），周期刷新生效（Demo dock：30s）；
- [ ] popup 使用 `PluginPopover`，宽度随内容，材质来自 `PluginTheme`（Demo：`DemoDockContribution`）；
- [ ] 事件使用正确的 `componentId`（Demo：`dsh-plugin-demo#demo` / `dsh-plugin-demo#demo-dock`），动作已注册到 `mobileUi`；
- [ ] 选择态变化后数据正确（spec 由 App 下发，不由原生推断）；
- [ ] 未安装伴侣 APK 时 Web UI 正常回退（Demo：设置页 + dock 胶囊）；
- [ ] `node --test samples/demo-js/test/rpc.test.mjs` 7/7 通过；
- [ ] 截图只做视觉验收，不用来发现 UI。

Demo 的完整实现与验证命令见
[Demo 插件 README](../samples/demo-js/README.md)、[扫描清单](plugin-ui-inventory-example.md)
和 [文档站案例](index.html#case)。
