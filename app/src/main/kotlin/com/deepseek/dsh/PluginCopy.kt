package com.deepseek.dsh

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource

@Composable
private fun isChineseLocale(): Boolean =
    LocalConfiguration.current.locales[0]?.language?.startsWith("zh") == true

/**
 * 官方插件设置页的标题/字段/说明来自官方 zh 字典。英文 locale 下用同一份
 * 映射转英文；中文下保留官方原文，避免二次翻译。
 */
@Composable
internal fun officialCopy(text: String): String {
    if (isChineseLocale()) return text
    val key = text.trim()
    return when (key) {
        // Agent Loop
        "Agent 循环" -> "Agent Loop"
        "控制 Agent 派发工具调用的方式。" -> "Control how the Agent dispatches tool calls."
        "并行工具调用数" -> "Parallel tool calls"
        "同一步内最多同时运行多少个可并行的调用。" -> "Maximum number of parallel calls in one step."
        // Terminal
        "终端" -> "Terminal"
        "限制每条命令最多能跑多久、最多输出多少内容。" -> "Limit how long each command may run and how much it may output."
        "命令超时（毫秒）" -> "Command timeout (ms)"
        "单条命令允许运行多久，超时即终止。" -> "How long a single command may run before it is terminated."
        "单流输出上限（字节）" -> "Output limit per stream (bytes)"
        "超出部分会转存到临时文件，而不是被丢弃。" -> "Excess output is written to a temporary file instead of being dropped."
        // Subagents
        "子智能体" -> "Subagents"
        "设置子智能体的递归层级、数量和模型。" -> "Configure recursion depth, concurrency and models for subagents."
        "运行限制" -> "Limits"
        "最大递归深度" -> "Maximum recursion depth"
        "限制 Agent 创建子智能体的递归层级。" -> "Limit how deeply the Agent may create subagents."
        "子智能体并行数量上限" -> "Maximum concurrent subagents"
        "同一主 Agent 下，所有递归层级同时存活的子智能体总数，主 Agent 不计入。达到上限时，新的启动请求会被拒绝。" ->
            "Total live subagents across all recursion levels under one main Agent, excluding the main Agent. New starts are rejected at the limit."
        "模型选择" -> "Model selection"
        "允许 Agent 为子智能体选择模型" -> "Allow the Agent to choose models for subagents"
        "开启后，Agent 可以从下方授权模型中，为每个子智能体选择提供方、模型和推理强度。仅影响新会话。" ->
            "When enabled, the Agent may choose a provider, model and reasoning effort for each subagent from the models below. Affects new sessions only."
        // Web Search
        "网页搜索" -> "Web Search"
        "设置 DeepSeek 的搜索提供方。" -> "Configure DeepSeek search providers."
        "Messages API 基础地址" -> "Messages API base URL"
        "填写含 API 前缀的地址，例如 https://api.deepseek.com/anthropic/v1；程序追加 /messages，留空恢复默认。" ->
            "Enter a URL including the API prefix, e.g. https://api.deepseek.com/anthropic/v1; /messages is appended. Leave blank to restore the default."
        "单次请求最多搜索次数" -> "Maximum searches per request"
        "辅助模型在一次 Messages 请求内最多可执行的搜索次数，仅适用于 Messages API。" ->
            "Maximum searches the helper model may run in one Messages request. Applies to the Messages API only."
        "Search / Fetch API 基础地址" -> "Search / Fetch API base URL"
        "填写含 API 前缀的地址，例如 https://api.deepseek.com/v1/tools；程序追加 /search 或 /fetch，留空恢复默认。" ->
            "Enter a URL including the API prefix, e.g. https://api.deepseek.com/v1/tools; /search or /fetch is appended. Leave blank to restore the default."
        "每次搜索结果数" -> "Results per search"
        "每个查询返回的结果条数，须为 1–50 的整数，默认 10；不控制搜索调用次数。web_search 保留的来源数不超过其自身的结果上限。" ->
            "Number of results per query, an integer from 1 to 50 (default 10). Does not control search call count; web_search retains no more sources than its own limit."
        else -> text
    }
}

/**
 * 官方插件行的标题/说明来自 Host 数据，Host 只下发中文。
 * App 在英文 locale 下按稳定标题映射到官方英文文案；未知插件保留 Host 原文。
 */
@Composable
internal fun officialPluginTitle(title: String): String = when (title.trim()) {
    "子智能体" -> stringResource(R.string.official_subagents_title)
    "网页搜索" -> stringResource(R.string.official_web_search_title)
    "Agent 循环" -> stringResource(R.string.official_agent_loop_title)
    "终端" -> stringResource(R.string.official_terminal_title)
    "智能体团队" -> stringResource(R.string.official_agent_team_title)
    "语音输入" -> stringResource(R.string.official_voice_title)
    "自动授权审查" -> stringResource(R.string.official_auto_approval_title)
    "开发者工具" -> stringResource(R.string.official_devtools_title)
    else -> title
}

@Composable
internal fun officialPluginDescription(title: String, fallback: String): String = when (title.trim()) {
    "子智能体" -> stringResource(R.string.official_subagents_desc)
    "网页搜索" -> stringResource(R.string.official_web_search_desc)
    "Agent 循环" -> stringResource(R.string.official_agent_loop_desc)
    "终端" -> stringResource(R.string.official_terminal_desc)
    "智能体团队" -> stringResource(R.string.official_agent_team_desc)
    "语音输入" -> stringResource(R.string.official_voice_desc)
    "自动授权审查" -> stringResource(R.string.official_auto_approval_desc)
    "开发者工具" -> stringResource(R.string.official_devtools_desc)
    else -> fallback
}
