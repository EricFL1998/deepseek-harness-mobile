# 官方 slot 覆盖清单（自动生成）

> 由 `tools/slot_inventory.py` 生成。先跑扫描，再写 UI；
> `mapped` 是 `NativeSlotMapping` 的声明，`hosted` 是 App 源码里真实出现的
> `NativeSlotHost`/`NativeSlot.X` 引用。两者不一致时工具失败。

- 官方 slot 常量：30
- 已映射：30
- 明确不支持：0
- 未覆盖：0
- 声明了映射但未找到宿主：0

| 官方 slot | NativeSlot | 宿主文件 |
| --- | --- | --- |
| `conversation.chat.assistant-actions` | `MESSAGE_ACTION` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.chat.flow` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.chat.node` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.chat.reasoning.body` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.chat.turnTail` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.composer.bar` | `COMPOSER_BAR` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.composer.dock` | `COMPOSER_DOCK` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt<br>plugin\native-host\src\main\kotlin\com\deepseek\dsh\plugin\nativehost\NativeHost.kt |
| `conversation.input.attachments` | `COMPOSER_ATTACHMENTS` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.input.dock` | `COMPOSER_DOCK` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt<br>plugin\native-host\src\main\kotlin\com\deepseek\dsh\plugin\nativehost\NativeHost.kt |
| `conversation.input.left` | `COMPOSER_LEFT` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.input.model` | `COMPOSER_MODEL` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.input.overlay` | `COMPOSER_OVERLAY` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.input.permission` | `COMPOSER_PERMISSION` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.input.plan` | `COMPOSER_PLAN` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.input.right` | `COMPOSER_RIGHT` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.session.header.corner` | `SESSION_HEADER_CORNER` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt |
| `conversation.user.text.node` | `CHAT_STREAM` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `conversation.view` | `CONVERSATION_VIEW` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `plugins.tab` | `SETTINGS_SHEET` | feature\settings\src\main\kotlin\com\deepseek\dsh\feature\settings\SettingsRootScreen.kt |
| `settings.general.item` | `SETTINGS_SHEET` | feature\settings\src\main\kotlin\com\deepseek\dsh\feature\settings\SettingsRootScreen.kt |
| `settings.section` | `SETTINGS_SHEET` | feature\settings\src\main\kotlin\com\deepseek\dsh\feature\settings\SettingsRootScreen.kt |
| `shell.overlay` | `PLUGIN_OVERLAY` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt |
| `sidebar.panellist` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt |
| `sidebar.right.pane.tab` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt |
| `sidebar.right.pane.tab.title` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt |
| `sidebar.workspaces` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt |
| `sidebar.workspaces.session.menu.item` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt |
| `sidebar.workspaces.session.row.action` | `SIDEBAR_PANEL` | app\src\main\kotlin\com\deepseek\dsh\MainActivity.kt |
| `tool.call.images` | `TOOL_VIEW_SHEET` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
| `tool.call.toolview` | `TOOL_VIEW_SHEET` | feature\chat\src\main\kotlin\com\deepseek\dsh\feature\chat\ChatScreen.kt |
