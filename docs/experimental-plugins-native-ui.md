# 实验性插件的原生 UI

这份表说明「实验性插件」里每个插件在 App 里的原生 UI 落在哪、对应官方客户端的哪个落点，
以及为什么有些插件没有原生 UI。判断依据是上游客户端源码（`dsh-upstream/packages/`），
不是猜的。

约定：**每个有客户端 UI 的插件一个 `.kt` 文件**，在 `PluginUis`（`app/.../PluginUi.kt`）
里登记；bundle 详情页把区块画在「包含的组件」下面，聊天侧的 UI 由 `feature/chat` 负责。

## 有原生 UI 的插件

| 插件 | 官方客户端落点 | 原生实现 |
| --- | --- | --- |
| 思考过程机器翻译 | `plugins.bundle.config`（偏好表单）+ `conversation.chat.reasoning.body`（思考内容译文/原文切换） | `CotTranslationPluginUi.kt`：翻译服务/目标语言面板（写 `cot-translation`）；`ReasoningCard` 的「查看译文/查看原文/重试」工具条（`cotTranslation/limits|translate`） |
| 录音输入 | `conversation.input.activity`（麦克风与录音条）+ `plugins.bundle.config`（模型准备卡片） | `VoiceInputPluginUi.kt`：识别服务/语言、下载源、准备卡片（进度/步骤/失败原因/下载并准备/重试取消），轮询 `speech/catalog`；`voice/VoiceRecorder.kt` + `ChatScreen`：麦克风按钮、录音条、`speech/transcribe`、结果插草稿 |
| 智能体团队 | `conversation.session.header.actions`（团队名单/共享任务浮层） | `AgentTeamPluginUi.kt`：头部团队动作 + 浮层（成员阶段、当前会话、任务状态/Owner/依赖/写入范围/展开收起），数据来自会话投影 `agentTeam` |
| 自定义插件市场 | `plugins.tab`（`id=discover`，插件页「发现」页签） | `PluginMarketTab.kt` + `core/data/PluginMarket.kt`：索引源管理、搜索、列表、详情、安装；数据来自 `pluginMarket/listIndexes|search|setIndexes` |
| Claude Code 子智能体 | `conversation.input.dock`（动态模组横幅） | `feature/chat/ClaudeCodeModsBand.kt`：`Text/Box/Button` 树渲染 + `claudeCodeMods/watchBand` 流式订阅 + `pressBand` |
| 开发者工具 | `shell.bottom`（Chrome DevTools 面板） | `InspectorDevToolsPluginUi.kt`：WebView 载入 Host 的 `/inspector/devtools/devtools_app.html`（同一份 DevTools 前端，同步会话 cookie） |
| 开发者工具（会话数据诊断） | `sidebar.right.pane.tab`（原始日志/聊天分组） | `SessionInspectorPluginUi.kt`：会话头部调试入口 + 全屏只读面板（原始帧的序号/类型/UTC 时间/数据、类型过滤、行内展开） |

流式通道：`RemoteClient` 接 Gateway 的 `/api/remote.mux` WebSocket（`open/item/end/error` 帧），
语音准备、Claude Code 模组这类 `@Remote({mode:'stream'})` 方法都走它。

## 没有原生 UI 的插件

以下插件在上游只有 Host 侧工具/行为，没有客户端 UI（没有 `dsh.client`、没有 `src/client/`、
不注册任何 slot）。它们的效果由现有的原生工具卡片、会话列表、设置页承接，不需要额外界面：

| 插件 | Host 侧提供 |
| --- | --- |
| Git 工作树 | 服务 `worktrees` + 工具 `create_worktree` |
| 持久终端 | `terminals` 注册表 + 6 个终端工具（用户终端侧栏属于基础产品，不属于这个 bundle） |
| 会话搜索 | sqlite `sessionQuery` + 5 个只读工具 |
| 随对话更新的标题 | 替换基础 `session-title` 的 LLM provider |
| DSH 徽章技能 | bundled skill provider `dsh-badge`（无模型工具） |
| Ralph 循环 | 工具 `ralph` + workflow 引擎 |
| Codex 子智能体 | provider `codex` + 工具 `subagent_codex` |
| Claude Code 子智能体（Host 半） | provider `claude-code` + 工具 `subagent_claude_code`（客户端半是上面的模组横幅） |
| 自动授权审查 | 权限模式工具调用钩子 |

## 验证状态与限制

已在模拟器上验证：插件页「发现」页签的索引/搜索/详情；录音输入的准备面板（选择语言写入 Host、
重试准备触发真实的准备失败原因）；录音条与 `speech/transcribe` 调用（模型未准备时回显失败原因）；
智能体团队入口与浮层开关；Claude Code 横幅启用后无多余 UI；会话数据诊断面板；NodeJS 诊断
WebView 载入真实 DevTools；思考过程翻译的偏好面板与 Host 接口。

受环境限制未能端到端验证（代码路径与 Host 接口已对齐）：真实团队会话下的成员/任务渲染、
语音转写成功路径（模型下载不通）、翻译成功路径（无真实思考消息且网络受限）、
模组按钮按下（无真实 Claude Code 模组会话）。
