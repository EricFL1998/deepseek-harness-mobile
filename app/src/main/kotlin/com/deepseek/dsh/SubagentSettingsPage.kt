package com.deepseek.dsh

/**
 * 「子智能体设置」页的字段表。
 *
 * 逐条对照官方 `@deepseek-ai/dsh-client-ui-settings-subagent` 的 `SubagentCard`：
 *
 * - **运行限制**：`subagent` 命名空间的 `maxDepth`（最大递归深度）与
 *   `maxActiveSubagents`（子智能体并行数量上限），两个数字字段并排；
 * - **模型选择**：`subagent-model-selection-settings` 命名空间的 `enabled`
 *   （允许 Agent 为子智能体选择模型）开关；打开后官方页面还列出「Agent 可选择的
 *   模型」，那份列表来自 Host 的模型目录（bridge 无此接口），原生页只给说明。
 *
 * 文案取自该包 `lib/client.js` 里的 `zh` 字典（`subagentTitle`/`subagentDescription`/
 * `subagentLimitsTitle`/`subagentMaxDepth`/`subagentDepthHelp`/`subagentMaxActive`/
 * `subagentCapacityHelp`/`subagentModelSelectionTitle`/`subagentModelSelectionToggle`/
 * `subagentModelSelectionChoose`/`subagentModelSelectionOff`）。
 */
internal val SUBAGENT_SETTINGS_PAGE: OfficialPageSpec = OfficialPageSpec(
    title = "子智能体设置",
    description = "设置子智能体的递归层级、数量和模型。",
    fields = listOf(
        OfficialField(
            ns = "subagent",
            key = "maxDepth",
            label = "最大递归深度",
            hint = "限制 Agent 创建子智能体的递归层级。",
            kind = OfficialFieldKind.NUMBER,
            group = "运行限制",
        ),
        OfficialField(
            ns = "subagent",
            key = "maxActiveSubagents",
            label = "子智能体并行数量上限",
            hint = "同一主 Agent 下，所有递归层级同时存活的子智能体总数，主 Agent 不计入。达到上限时，新的启动请求会被拒绝。",
            kind = OfficialFieldKind.NUMBER,
            group = "运行限制",
        ),
        OfficialField(
            ns = "subagent-model-selection-settings",
            key = "enabled",
            label = "允许 Agent 为子智能体选择模型",
            hint = "开启后，Agent 可以从下方授权模型中，为每个子智能体选择提供方、模型和推理强度。仅影响新会话。",
            kind = OfficialFieldKind.TOGGLE,
            group = "模型选择",
        ),
    ),
    namespaces = listOf("subagent", "subagent-model-selection-settings"),
)
