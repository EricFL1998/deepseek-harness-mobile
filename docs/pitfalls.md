# 原生 UI 接入踩坑清单

> 这些是 DSH Mobile Native UI 接入过程中真实出现过的问题，按“现象 → 根因 → 修复 → 预防”
> 记录。新插件/新 slot 接入前先读一遍，能少走很多弯路。

---

## 1. Slot / 宿主

### 1.1 插件注册了 slot，但界面完全没出现

**现象**：插件 Web 版有额度胶囊/设置页，原生界面一片空白；扫描器显示 slot 已映射、宿主也存在。

**根因**：slot 贡献拿到的是基础 `PluginHostApi`，没有实现 `PluginRpcHost`；
插件代码第一行 `host as? PluginRpcHost ?: return` 直接退出。

**修复**：`SlotBridgeHost` 在基础 host 上补齐 `PluginRpcHost` / `PluginModelHost`，
`NativeSlotHost` 统一用它渲染 contributions；App 注入 `NativeHost.rpcHandler`。

**预防**：
- 需要 RPC 的贡献，扫描器报告里必须出现 `rpc:*`；
- 第一次接入先确认 `callRpc` 能拿到结果，再写 UI。

### 1.2 插件 APK 异步加载后，旧页面一直空白

**现象**：新会话里已出现 dock，切到旧会话 dock 消失；重启后有时正常。

**根因**：`SlotRegistry` 是普通列表，插件 APK 在聊天页组合之后才被扫描装载；
注册表变化没有触发已组合的 `NativeSlotHost` 重组。

**修复**：`SlotRegistry` 增加 Compose 可观察的 `revision`，register/unregister 时递增；
`resolve()` 读取 revision，已存在的落点自动重组。

**预防**：任何“注册表 + Compose”组合都要有可观察版本号，不要依赖调用方重建整页。

### 1.3 一个官方 slot 下有多条 entry，却只能整块替换

**现象**：composer dock 的 tok/s、缓存命中、上下文被写死在一行里，插件只能整条覆盖。

**修复**：按官方 `activity` / `usage` entry 拆成 `COMPOSER_DOCK_ACTIVITY`、
`COMPOSER_DOCK_USAGE`、`COMPOSER_DOCK_CONTEXT`，各自用 `OfficialSlot` 包装。

**预防**：扫描报告里出现同一 slot 多条 entry 时，先问“是否要拆成独立 NativeSlot”。

### 1.4 keyed slot 没有宿主

**现象**：插件在 `settings.models.provider-card` 注册了 ProviderCardExtras，
App 的设置-模型页没有任何订阅区块。

**根因**：只补了官方 `DshSlotRegistry`，没有补 NativeSlot、映射、宿主容器。

**修复**：新增 `SETTINGS_PROVIDER_CARD` + 映射 + `SettingsModelsPane` 宿主。

**预防**：扫描器 `--companion --check` 遇到“未映射/无宿主”必须失败，不能只记 TODO。

---

## 2. 伴侣 APK / 装载

### 2.0 包名缩写导致身份/升级混乱

**现象**：伴侣包名写成 `com.dsh.mobile.subscription`，后来功能扩展（或同包多个能力）
无法区分，用户安装后像另一个 APK。

**根因**：包名没有使用完整插件名，发布后不能再改。

**规范**：`com.dsh.mobile.<完整插件名>`，保留 `dsh-plugin-` 前缀并转小写，
非 `[a-z0-9]` 字符替换为 `_`：

```
dsh-plugin-subscription-oauth  -> com.dsh.mobile.dsh_plugin_subscription_oauth
dsh-plugin-my-plugin           -> com.dsh.mobile.dsh_plugin_my_plugin
```

同一 JS 包的附加能力加 `_<capability>` 后缀，例如
`com.dsh.mobile.dsh_plugin_subscription_oauth_kimi`。
`dsh.plugin.id` 与包名解耦，仍用插件自己的反向域名 id。

**预防**：包名在第一次发布前定稿；写进 CI/评审清单，禁止缩写。

### 2.1 `Failed to find entry 'classes.dex'`

**现象**：`createPackageContext` 后单独 `DexClassLoader` 加载伴侣，日志报
`Failed to find entry 'classes.dex': Entry not found`，插件类没加载，所有贡献消失。

**根因**：多 dex / 新签名 APK 下，自己按 `sourceDir` 重建 DexClassLoader 不可靠。

**修复**：优先使用 `pluginContext.classLoader`（`createPackageContext` 自带），
失败才回退 DexClassLoader；加载失败必须打日志，不能 `getOrNull()` 静默。

**预防**：伴侣装载失败时要有可见状态（插件详情显示“加载失败”），不要只表现为 UI 空白。

### 2.2 重复安装覆盖了用户已有的伴侣 APK

**现象**：为了验证新功能执行 `adb install -r sample.apk`，把用户正在用的、
包含更多功能的伴侣版本覆盖了。

**修复/预防**：
- 安装前确认 APK 来源与版本，优先从源码构建并保留原 APK；
- 调试安装用独立 package id，或先 `adb pull` 备份；
- 真实功能以源码为准，不靠覆盖设备上的二进制。

### 2.3 装了 APK 但插件没配对

**现象**：原生插件 tab 能看到条目，但插件详情“包含的组件”为 0。

**根因**：`dsh.plugin.jsPackage` 与 JS `package.json.name` 不一致，或 APK 安装后没触发重扫。

**修复/预防**：
- 核对 manifest 的 `jsPackage`；
- 安装/卸载后回前台触发重扫；
- 插件详情显示组件数量，0 个就是第一个排查点。

---

## 3. 数据 / RPC

### 3.1 启动首帧 RPC 返回 401

**现象**：dock/卡片第一次加载空白，过一会才有数据。

**根因**：Host 刚启动，`/api` 还没完成认证，首次 `callRpc` 拿 401。

**修复**：贡献内对首次加载做 3–6 次退避重试（800ms / 2s），成功即停；
失败不要伪造数据，保持空白/错误态。

**预防**：所有首屏 RPC 默认考虑 Host 启动时序，不要只写 `LaunchedEffect { callOnce() }`。

### 3.2 数据不刷新

**现象**：余额/用量只有打开时那一刻，之后一直不变。

**根因**：Web 版 UsageChip / ProviderCardExtras 是 60 秒轮询，原生只加载一次。

**修复**：`LaunchedEffect(key) { while(true){ delay(60_000); reload() } }`，
页面离开自动取消；不要自己维护业务状态。

### 3.3 切换 provider 后仍显示旧 provider 的额度

**现象**：从 DeepSeek 切到 Kimi，dock 还是 DeepSeek 余额。

**根因**：dock spec 里没有当前模型/provider，原生自己猜。

**修复**：App 把 `selectedModelId` 放进 `COMPOSER_DOCK` spec；
原生用 status 的 `models[].id` / `defaultModel` 反查 provider，再走对应 RPC。

**预防**：选择态必须由 App 显式下发，不能在原生端推断“大概是谁”。

### 3.4 fast mode 需要“写模型选择”，不是纯展示

**现象**：能读出 `model-variants`，但点击按钮无法真正切到 fast rung。

**根因**：SDK 只有 RPC 能力，没有“改会话模型/推理档位”的能力。

**修复**：新增 `PluginModelHost.selectModel(provider, model, reasoningEffort)`，
App `selectModel` 注入，`SlotBridgeHost` 代理。

**预防**：需要改变宿主状态时，先加**可选接口**（参考 PluginRpcHost），
不要在插件里绕 Host 内部协议。

---

## 4. UI 定位 / 样式

### 4.1 popup 居中弹出，而不是贴着 pill

**现象**：点 dock 额度胶囊，浮层出现在屏幕中央，盖住整个页面。

**根因**：用了 `Dialog`；Web 版是 `useAnchoredPosition({side:'top', align:'end'})` +
`createPortal` 的 anchored popover。

**修复**：SDK 提供 `PluginPopover`，放在触发元素同一个 `Box` 内，
`PopupPositionProvider` 贴上方 6dp、右对齐、窗口内夹取。

**预防**：扫描器提取 `useAnchoredPosition/createPortal/side/align` placement 线索；
插件是 anchored popover 而伴侣没有 `Popup`/`PluginPopover` 时告警。

### 4.2 浮层用了插件自己的 Card，和 App 菜单不一致

**现象**：popup 颜色、圆角、阴影、字号和模型菜单明显不是一套。

**根因**：插件拿 `PluginTheme` 的 surface 自己画 Material Card。

**修复**：`PluginTheme` 增加 `colorPopupSurface`、`colorPopupBorder`、`cornerPopup`、
`textPopupTitle/Body`；App 从 `DshPanelColors` / `DshMenuCard` 映射。
插件统一用 `PluginPopover`。

**预防**：浮层禁止硬编码颜色/圆角/阴影；主题字段缺什么就在 SDK 补，不要插件私有化。

### 4.3 popup 固定最小宽度，短内容出现大块空白

**现象**：`DeepSeek Account 额度 / CNY ¥19.64` 只有两行，浮层却横跨近半个屏幕。

**根因**：`widthIn(min = 220.dp)` 强制最小宽度，`fillMaxWidth` 的行又把宽度撑满。

**修复**：`PluginPopover` 改用 `width(IntrinsicSize.Max).widthIn(max = 320.dp)`；
内容多宽就多宽，超长才换行/滚动。

**预防**：浮层宽度先按内容 intrinsic 测量，再设上限；不要用固定 min 宽度。

### 4.4 dock 位置放错：不是输入框上方

**现象**：额度胶囊出现在输入卡上方，官方却在底部状态行（tok/s、缓存命中旁边）。

**修复**：`NativeSlotInline(COMPOSER_DOCK)` 放进 `ComposerDockBar`，
tok/s / 缓存 / 上下文拆成独立 slot，插件条目排在上下文之后。

**预防**：UI 位置以官方源码 slot 结构为准，不以截图感觉为准。

---

## 5. 扫描器 / 流程

### 5.1 扫描器只扫结构，漏了定位和样式

**现象**：slot、组件、RPC 都识别了，但 popup 位置/样式还是错。

**根因**：旧版扫描器只有 JSX 树、文案、RPC；没有几何、portal、anchored 信息。

**修复**：`plugin_ui_scanner.py` 增加 placement hints（useAnchoredPosition、createPortal、
anchorRef、role=dialog、position fixed/absolute、side/align），
并在 companion 检查里判断是否有 `Popup`/`PluginPopover`。

**预防**：扫描报告必须包含“定位/交互”一栏；结构对不等于 UI 对。

### 5.2 第三方没有 upstream，扫描器无法运行

**根因**：早期扫描器默认读 `dsh-upstream`。

**修复**：扫描插件自己的包：优先 `.js.map` 的 sourcesContent，其次 `src/*.tsx`，
最后 `lib/client.js`；官方清单另外支持 `--rootfs`。

**预防**：SDK 工具不能假设开发者有官方仓库。

### 5.3 只比较“有没有 slot”，没比较“伴侣是否实现”

**现象**：扫描器显示所有 slot 有映射/有宿主，但 FastModeButton、ProviderCardExtras 根本没写。

**修复**：`--companion` 扫描伴侣源码里的 `NativeSlot.X` / `PageRegistry`；
`--check` 对“插件有 UI、伴侣没实现”失败。

**预防**：扫描器必须能对比“插件 UI 清单”和“伴侣实现清单”，不能只看 App 映射。

---

## 6. 构建 / 环境（proot）

### 6.1 bash 工具每次 `invalid output`

**现象**：Proot 内 bash 返回 200，但工具结果被判 invalid：
`"value" must match exactly one oneOf branch (matched 0)`。

**根因**：Proot 下工具结果缺 schema 必填的 `cwd`，随后 presentationMeta 又读不存在的 cwd。

**修复**：Host runtime 补丁放宽 cwd 必填并保护 presentationMeta；保留 cwd 兜底。

**预防**：新增工具输出字段时，以真实运行结果为准；schema 校验失败先看完整 value。

### 6.2 Host 域名解析失败 / `fetch failed`

**根因**：Android 沙箱内 DNS 不可用；插件域名没有进入 hosts。

**修复**：Host 启动时扫描已装插件包里的 HTTPS 域名并写 hosts；不写具体插件域名。

### 6.3 插件数据目录 mkdir 失败（ENOENT/ENOSYS）

**根因**：Proot 下 mkdir 行为不稳定，Host 插件首次运行创建目录失败。

**修复**：App 从 RPC 错误中解析 `mkdir '<path>'`，在 rootfs 内补建后重试一次。

---

## 7. 验收清单（每次接入新 UI 必跑）

- [ ] `plugin_ui_scanner.py --plugin ... --companion ... --check` 通过；
- [ ] 报告里每个 slot 的 `原生实现` 为“是”；
- [ ] 官方 slot 映射/宿主：`slot_inventory.py` + `SlotCoverageTest` 通过；
- [ ] 首屏 RPC 有重试，60s 刷新生效；
- [ ] popup 使用 `PluginPopover`，宽度随内容，材质来自 `PluginTheme`；
- [ ] 切换 provider/model 后 dock 数据正确；
- [ ] 未安装伴侣 APK 时 Web UI 正常回退；
- [ ] 截图只做视觉验收，不用来发现 UI。

---

## 8. 这次实际踩坑的对应修复

| 坑 | 修复 |
| --- | --- |
| slot 有映射但空白 | `SlotBridgeHost` 补齐 `PluginRpcHost` |
| 旧会话不重组 | `SlotRegistry.revision` |
| classes.dex 加载失败 | 优先 `packageContext.classLoader` |
| FastModeButton 缺失 | `PluginModelHost` + `FastModeContribution` |
| provider-card 无宿主 | `SETTINGS_PROVIDER_CARD` + `SettingsModelsPane` |
| popup 居中/样式不对 | `PluginPopover` + `DshMenuCard` 材质映射 |
| popup 过宽 | `IntrinsicSize.Max` + max 320dp |
| 只扫结构漏定位 | scanner placement hints |
| 系统 bash invalid | Host cwd schema 补丁 |
| 首屏 401/数据不刷新 | 退避重试 + 60s 轮询 |
