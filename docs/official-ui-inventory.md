# 官方 UI / 落点清单（自动生成）

> 由 `tools/official_ui_inventory.py` 扫描 dsh-upstream/packages/client 生成。
> 不再靠截图找 UI：每个官方 slot 的注册条目、组件、数据依赖与原生覆盖状态都在这里。

## 概览

- 官方 slot：31
- 官方注册条目：145
- 已映射 NativeSlot：31
- 未映射官方 slot：0
- 已映射但无宿主：0
- composer.dock 注册条目未拆成原生落点：0

## 落点覆盖

| 官方 slot | 原生落点 | 宿主 | 注册条目 |
| --- | --- | --- | --- |
| `conversation.chat.assistant-actions` | `MESSAGE_ACTION` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `feedback` |
| `conversation.chat.flow` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `ChatFlow` |
| `conversation.chat.node` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `UserMessageNodeView`, `UserMessageNodeView`, `ContextMessageNodeView`, `TurnTriggerNodeView`, `SystemPromptNodeView`, `AssistantNodeView`, `CommandNodeView`, `ManualCompactionNodeView`, `CompactionNodeView`, `RetryNodeView`, `TurnErrorNodeView`, `TurnMaxTokensNodeView`, `TurnProcessNodeView`, `TurnTailNodeView`, `UnknownNodeView`, `GoalCommandInputView`, `ToolCallTree`, `WorkflowRunPanel` |
| `conversation.chat.reasoning.body` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `DefaultReasoningBody` |
| `conversation.chat.turnTail` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `schedule-created`, `PlanCards`, `@deepseek-ai/dsh-client-ui-deliverables` |
| `conversation.composer.bar` | `COMPOSER_BAR` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `false` |
| `conversation.composer.dock` | `COMPOSER_DOCK` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ComposerDockBar.kt<br>plugin\native-host\src\main\kotlin\com\deepseek\dsh\plugin\nativehost\NativeHost.kt | `activity`, `usage` |
| `conversation.input.attachments` | `COMPOSER_ATTACHMENTS` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `ComposerAttachments` |
| `conversation.input.dock` | `COMPOSER_DOCK` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ComposerDockBar.kt<br>plugin\native-host\src\main\kotlin\com\deepseek\dsh\plugin\nativehost\NativeHost.kt | `todo`, `goal`, `queue` |
| `conversation.input.left` | `COMPOSER_LEFT` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | — |
| `conversation.input.model` | `COMPOSER_MODEL` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `ModelSelect` |
| `conversation.input.overlay` | `COMPOSER_OVERLAY` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `slash-menu`, `command-popup`, `feedback-dialog` |
| `conversation.input.permission` | `COMPOSER_PERMISSION` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `PermissionSelect` |
| `conversation.input.plan` | `COMPOSER_PLAN` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `PlanChip` |
| `conversation.input.right` | `COMPOSER_RIGHT` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | — |
| `conversation.session.header.corner` | `SESSION_HEADER_CORNER` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt | `ExpandButton` |
| `conversation.user.text.node` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `PlainUserText`, `SessionReference`, `GoalCommandReference`, `FileReference`, `FolderReference`, `SecretText`, `SkillReference` |
| `conversation.view` | `CONVERSATION_VIEW` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `chat`, `trajectory` |
| `plugins.tab` | `SETTINGS_SHEET` | feature\settings\src\main\kotlin\com\deepseek\dsh\feature\settings\SettingsRootScreen.kt | `discover` |
| `settings.general.item` | `SETTINGS_SHEET` | feature\settings\src\main\kotlin\com\deepseek\dsh\feature\settings\SettingsRootScreen.kt | `permission`, `language`, `appearance`, `font-size`, `transcript-view`, `developer-tools`, `shortcuts`, `link-opening`, `composer-enter`, `performance-usage`, `UploadRow`, `current-version` |
| `settings.models.provider-card` | `SETTINGS_PROVIDER_CARD` | feature\settings\src\main\kotlin\com\deepseek\dsh\feature\settings\SettingsModelsPane.kt | — |
| `settings.section` | `SETTINGS_SHEET` | feature\settings\src\main\kotlin\com\deepseek\dsh\feature\settings\SettingsRootScreen.kt | `account`, `general`, `models`, `plugins`, `agent-presets` |
| `shell.overlay` | `PLUGIN_OVERLAY` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt | `TerminalCleanup`, `account.platform-page`, `chat.quota-notice`, `desktop-onboarding`, `host-release-change`, `plugin-manager.refresh-toast`, `schedule.delete-toast`, `session-log-upload-toast`, `shortcuts`, `workspace.row-toast`, `workspace.session-archive`, `workspace.session-rename` |
| `sidebar.panellist` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt | `PluginsPanelIcon`, `TaskManagerIcon` |
| `sidebar.right.pane.tab` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt | `PlanPreview`, `ScheduleTaskTab`, `BrowserBody`, `GuideBody`, `TerminalRecovery`, `SidebarChatTab` |
| `sidebar.right.pane.tab.title` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt | `PlanTitle`, `ScheduleTaskTabTitle`, `BrowserTitle` |
| `sidebar.workspaces` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt | `pin` |
| `sidebar.workspaces.session.menu.item` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt | `rename`, `fork`, `archive`, `session-id` |
| `sidebar.workspaces.session.row.action` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt | `archive`, `pin` |
| `tool.call.images` | `TOOL_VIEW_SHEET` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `MessageImages` |
| `tool.call.toolview` | `TOOL_VIEW_SHEET` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt | `abort.signal`, `AskQuestionRow`, `BashRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `DetailsRow`, `FileMutationRow`, `FileMutationRow`, `ReadImageRow`, `ReadRow`, `SearchRow`, `SearchRow`, `TodoRow`, `WebRow`, `WebRow` |

## 注册条目明细

| 官方 slot | entry | order | 组件 | 包 | 数据依赖 |
| --- | --- | --- | --- | --- | --- |
| `conversation.chat.assistant-actions` | `feedback` | 10 | `MessageFeedbackActions` | `packages/client` | — |
| `conversation.chat.flow` | `—` | — | `ChatFlow` | `packages/client` | `useChat`, `useSession` |
| `conversation.chat.node` | `—` | — | `UserMessageNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `UserMessageNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `ContextMessageNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `TurnTriggerNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `SystemPromptNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `AssistantNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `CommandNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `ManualCompactionNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `CompactionNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `RetryNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `TurnErrorNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `TurnMaxTokensNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `TurnProcessNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `TurnTailNodeView` | `packages/client` | `useChat`, `usePerformanceUsage` |
| `conversation.chat.node` | `—` | — | `UnknownNodeView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `GoalCommandInputView` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `ToolCallTree` | `packages/client` | — |
| `conversation.chat.node` | `—` | — | `WorkflowRunPanel` | `packages/client` | — |
| `conversation.chat.reasoning.body` | `—` | — | `DefaultReasoningBody` | `packages/client` | — |
| `conversation.chat.turnTail` | `schedule-created` | 20 | `ScheduleTurnCard` | `packages/client` | — |
| `conversation.chat.turnTail` | `—` | — | `PlanCards` | `packages/client` | — |
| `conversation.chat.turnTail` | `@deepseek-ai/dsh-client-ui-deliverables` | — | `DeliverablesTail` | `packages/client` | — |
| `conversation.composer.bar` | `—` | — | `false` | `packages/client` | — |
| `conversation.composer.dock` | `activity` | 0 | `ActivityPill` | `packages/client` | — |
| `conversation.composer.dock` | `usage` | 1 | `UsagePill` | `packages/client` | — |
| `conversation.input.attachments` | `—` | — | `ComposerAttachments` | `packages/client` | — |
| `conversation.input.dock` | `todo` | 0 | `TodoDock` | `packages/client` | — |
| `conversation.input.dock` | `goal` | 10 | `GoalDock` | `packages/client` | — |
| `conversation.input.dock` | `queue` | 20 | `QueueDock` | `packages/client` | `useProjection`, `useSession`, projection:inbox |
| `conversation.input.model` | `—` | — | `ModelSelect` | `packages/client` | — |
| `conversation.input.overlay` | `slash-menu` | 0 | `MenuView` | `packages/client` | — |
| `conversation.input.overlay` | `command-popup` | 1 | `PopupSelectView` | `packages/client` | — |
| `conversation.input.overlay` | `feedback-dialog` | 2 | `FeedbackDialog` | `packages/client` | — |
| `conversation.input.permission` | `—` | — | `PermissionSelect` | `packages/client` | `useProjection`, projection:permissions |
| `conversation.input.plan` | `—` | — | `PlanChip` | `packages/client` | — |
| `conversation.session.header.corner` | `—` | — | `ExpandButton` | `packages/client` | — |
| `conversation.user.text.node` | `—` | — | `PlainUserText` | `packages/client` | — |
| `conversation.user.text.node` | `—` | — | `SessionReference` | `packages/client` | — |
| `conversation.user.text.node` | `—` | — | `GoalCommandReference` | `packages/client` | — |
| `conversation.user.text.node` | `—` | — | `FileReference` | `packages/client` | — |
| `conversation.user.text.node` | `—` | — | `FolderReference` | `packages/client` | — |
| `conversation.user.text.node` | `—` | — | `SecretText` | `packages/client` | — |
| `conversation.user.text.node` | `—` | — | `SkillReference` | `packages/client` | — |
| `conversation.view` | `chat` | 0 | `ChatView` | `packages/client` | `useChat`, `useProjection`, `useSession`, projection:inbox, projection:turnOutline |
| `conversation.view` | `trajectory` | 10 | `TrajectoryView` | `packages/client` | `useSession` |
| `plugins.tab` | `discover` | — | `DiscoverSection` | `packages/client` | — |
| `settings.general.item` | `permission` | -20 | `PermissionRow` | `packages/client` | — |
| `settings.general.item` | `language` | 0 | `LanguageRow` | `packages/client` | — |
| `settings.general.item` | `appearance` | 10 | `AppearanceRow` | `packages/client` | — |
| `settings.general.item` | `font-size` | 11 | `FontSettingsGroup` | `packages/client` | — |
| `settings.general.item` | `transcript-view` | 12 | `TranscriptViewRow` | `packages/client` | — |
| `settings.general.item` | `developer-tools` | 15 | `DeveloperToolsRow` | `packages/client` | — |
| `settings.general.item` | `shortcuts` | 16 | `ShortcutsRow` | `packages/client` | — |
| `settings.general.item` | `link-opening` | 17 | `LinkOpeningRow` | `packages/client` | — |
| `settings.general.item` | `composer-enter` | 20 | `EnterBehaviorRow` | `packages/client` | — |
| `settings.general.item` | `performance-usage` | 30 | `PerformanceUsageRow` | `packages/client` | `usePerformanceUsage` |
| `settings.general.item` | `—` | 90 | `UploadRow` | `packages/client` | — |
| `settings.general.item` | `current-version` | 100 | `CurrentVersionRow` | `packages/client` | — |
| `settings.section` | `account` | -10 | `AccountSection` | `packages/client` | — |
| `settings.section` | `general` | 0 | `GeneralSection` | `packages/client` | — |
| `settings.section` | `models` | 10 | `ModelsSection` | `packages/client` | — |
| `settings.section` | `plugins` | 15 | `PluginsSettingsSection` | `packages/client` | — |
| `settings.section` | `agent-presets` | 20 | `AgentPresetSection` | `packages/client` | — |
| `shell.overlay` | `—` | — | `TerminalCleanup` | `packages/client` | — |
| `shell.overlay` | `account.platform-page` | — | `AccountPlatformHost` | `packages/client` | — |
| `shell.overlay` | `chat.quota-notice` | — | `QuotaNoticeHost` | `packages/client` | — |
| `shell.overlay` | `desktop-onboarding` | — | `DesktopOnboardingEntry` | `packages/client` | — |
| `shell.overlay` | `host-release-change` | — | `HostReleasePrompt` | `packages/client` | — |
| `shell.overlay` | `plugin-manager.refresh-toast` | — | `PluginRefreshToast` | `packages/client` | — |
| `shell.overlay` | `schedule.delete-toast` | — | `ScheduleDeleteToast` | `packages/client` | inject:dismiss: deleteToast.dismiss, inject:hooks: deleteToast.hooks |
| `shell.overlay` | `session-log-upload-toast` | — | `UploadToast` | `packages/client` | — |
| `shell.overlay` | `shortcuts` | — | `ShortcutReference` | `packages/client` | — |
| `shell.overlay` | `workspace.row-toast` | — | `RowActionToast` | `packages/client` | — |
| `shell.overlay` | `workspace.session-archive` | — | `SessionArchiveConfirmDialog` | `packages/client` | — |
| `shell.overlay` | `workspace.session-rename` | — | `SessionRenameDialog` | `packages/client` | — |
| `sidebar.panellist` | `—` | 0 | `PluginsPanelIcon` | `packages/client` | — |
| `sidebar.panellist` | `—` | 10 | `TaskManagerIcon` | `packages/client` | — |
| `sidebar.right.pane.tab` | `—` | — | `PlanPreview` | `packages/client` | — |
| `sidebar.right.pane.tab` | `—` | — | `ScheduleTaskTab` | `packages/client` | — |
| `sidebar.right.pane.tab` | `—` | — | `BrowserBody` | `packages/client` | — |
| `sidebar.right.pane.tab` | `—` | — | `GuideBody` | `packages/client` | — |
| `sidebar.right.pane.tab` | `—` | — | `TerminalRecovery` | `packages/client` | — |
| `sidebar.right.pane.tab` | `—` | — | `SidebarChatTab` | `packages/client` | — |
| `sidebar.right.pane.tab.title` | `—` | — | `PlanTitle` | `packages/client` | — |
| `sidebar.right.pane.tab.title` | `—` | — | `ScheduleTaskTabTitle` | `packages/client` | — |
| `sidebar.right.pane.tab.title` | `—` | — | `BrowserTitle` | `packages/client` | — |
| `sidebar.workspaces` | `pin` | 100 | `PinSessionMenuItem` | `packages/client` | — |
| `sidebar.workspaces.session.menu.item` | `rename` | 200 | `RenameSessionMenuItem` | `packages/client` | — |
| `sidebar.workspaces.session.menu.item` | `fork` | 300 | `ForkSessionMenuItem` | `packages/client` | — |
| `sidebar.workspaces.session.menu.item` | `archive` | 400 | `ArchiveSessionMenuItem` | `packages/client` | — |
| `sidebar.workspaces.session.menu.item` | `session-id` | 450 | `SessionIdMenuItem` | `packages/client` | — |
| `sidebar.workspaces.session.row.action` | `archive` | 100 | `ArchiveSessionRowButton` | `packages/client` | — |
| `sidebar.workspaces.session.row.action` | `pin` | 200 | `PinSessionRowButton` | `packages/client` | — |
| `tool.call.images` | `—` | — | `MessageImages` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `abort.signal` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `AskQuestionRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `BashRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `DetailsRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `FileMutationRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `FileMutationRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `ReadImageRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `ReadRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `SearchRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `SearchRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `TodoRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `WebRow` | `packages/client` | — |
| `tool.call.toolview` | `—` | — | `WebRow` | `packages/client` | — |

## 官方 children 关系

- `conversation.chat.assistant-actions <- dsh-upstream\packages\client\ui-chat\src\client\chat\register-node-renderers.ts`
- `conversation.chat.flow <- dsh-upstream\packages\client\ui-chat\src\client\apply.ts`
- `conversation.chat.node <- dsh-upstream\packages\client\ui-chat\src\client\apply.ts`
- `conversation.chat.reasoning.body <- dsh-upstream\packages\client\ui-chat\src\client\chat\register-node-renderers.ts`
- `conversation.chat.turnTail <- dsh-upstream\packages\client\ui-chat\src\client\chat\register-node-renderers.ts`
- `conversation.composer.bar <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.composer.bar <- dsh-upstream\packages\client\ui-conversation\src\client\contract\slots.ts`
- `conversation.composer.dock <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.input.attachments <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.input.dock <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.input.dock <- dsh-upstream\packages\client\ui-conversation\src\client\contract\slots.ts`
- `conversation.input.left <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.input.model <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.input.overlay <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.input.permission <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.input.plan <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.input.right <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.session.header.corner <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.user.text.node <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `conversation.user.text.node <- dsh-upstream\packages\client\ui-conversation\src\client\contract\slots.ts`
- `conversation.view <- dsh-upstream\packages\client\ui-conversation\src\client\apply.ts`
- `plugins.tab <- dsh-upstream\packages\client\ui-plugin-manager\src\client\index.ts`
- `settings.general.item <- dsh-upstream\packages\client\ui-settings-general\src\client\index.ts`
- `settings.models.provider-card <- dsh-upstream\packages\client\ui-settings-models\src\client\index.ts`
- `settings.section <- dsh-upstream\packages\client\ui-settings-general\src\client\index.ts`
- `shell.overlay <- dsh-upstream\packages\client\ui-layout\src\client\index.ts`
- `sidebar.panellist <- dsh-upstream\packages\client\ui-sidebar\src\client\index.ts`
- `sidebar.right.pane.tab <- dsh-upstream\packages\client\ui-sidebar-right\src\client\index.ts`
- `sidebar.right.pane.tab.title <- dsh-upstream\packages\client\ui-sidebar-right\src\client\index.ts`
- `sidebar.workspaces <- dsh-upstream\packages\client\ui-sidebar\src\client\index.ts`
- `sidebar.workspaces.session.menu.item <- dsh-upstream\packages\client\ui-workspace\src\client\index.ts`
- `tool.call.images <- dsh-upstream\packages\client\ui-tool\src\client\tool\toolviews\read-image-row.tsx`
- `tool.call.toolview <- dsh-upstream\packages\client\ui-tool\src\client\apply.ts`
