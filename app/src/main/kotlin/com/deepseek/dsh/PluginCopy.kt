package com.deepseek.dsh

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

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
