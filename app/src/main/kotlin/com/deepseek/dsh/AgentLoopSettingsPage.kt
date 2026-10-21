package com.deepseek.dsh

/**
 * 「智能体运行设置」页的字段表。
 *
 * 逐条对照官方 `@deepseek-ai/dsh-client-ui-settings-agent-loop` 的 `AgentLoopCard`：
 * 一个数字字段，绑定 `agent-loop` 命名空间的 `maxParallelToolCalls`。
 * 文案取自该包 `lib/client.js` 里的 `zh` 字典（`title`/`description`/`maxParallel`/
 * `maxParallelHint`）。
 */
internal val AGENT_LOOP_SETTINGS_PAGE: OfficialPageSpec = OfficialPageSpec(
    title = "智能体运行设置",
    description = "配置同时执行的工具调用数量上限。",
    fields = listOf(
        OfficialField(
            ns = "agent-loop",
            key = "maxParallelToolCalls",
            label = "并行工具调用数",
            hint = "同一步内最多同时运行多少个可并行的调用。",
            kind = OfficialFieldKind.NUMBER,
        ),
    ),
    namespaces = listOf("agent-loop"),
)
