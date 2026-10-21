package com.deepseek.dsh

/**
 * 「命令执行设置」页的字段表。
 *
 * 逐条对照官方 `@deepseek-ai/dsh-client-ui-settings-shell` 的 `ShellCard`：
 * 两个数字字段，绑定 `bash-sandbox` 命名空间的 `timeoutMs` / `maxOutputBytes`。
 * 文案取自该包 `lib/client.js` 里的 `zh` 字典（`title`/`description`/`timeoutMs`/
 * `timeoutMsHint`/`maxOutputBytes`/`maxOutputBytesHint`）。
 *
 * 官方页面的 `_NS` 还列出 `pwsh-sandbox`：Host 只有在该命名空间被服务时才渲染，
 * 本部署没有 `pwsh-sandbox` 行，所以这里也只列 bash 的两个字段。
 */
internal val SHELL_SETTINGS_PAGE: OfficialPageSpec = OfficialPageSpec(
    title = "命令执行设置",
    description = "限制每条命令最多能跑多久、最多输出多少内容。",
    fields = listOf(
        OfficialField(
            ns = "bash-sandbox",
            key = "timeoutMs",
            label = "命令超时（毫秒）",
            hint = "单条命令允许运行多久，超时即终止。",
            kind = OfficialFieldKind.NUMBER,
        ),
        OfficialField(
            ns = "bash-sandbox",
            key = "maxOutputBytes",
            label = "单流输出上限（字节）",
            hint = "超出部分会转存到临时文件，而不是被丢弃。",
            kind = OfficialFieldKind.NUMBER,
        ),
    ),
    namespaces = listOf("bash-sandbox"),
)
