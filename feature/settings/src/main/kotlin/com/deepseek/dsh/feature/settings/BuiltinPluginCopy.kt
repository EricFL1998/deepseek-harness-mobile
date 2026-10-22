package com.deepseek.dsh.feature.settings

import com.deepseek.dsh.core.model.BuiltInPluginInfo

/**
 * 内置插件的英文名与说明。
 *
 * Host 的插件 locale 文件里只有中文 `meta.title` / `meta.description`（英文侧通常只有
 * 包名），所以英文界面下会出现「Bash 命令工具」这类中文条目。这里按官方包 id 维护
 * 一份英文文案，中文界面继续使用 Host 自己的文案，未收录的 id 回退到 Host 文案。
 */
private data class PluginCopy(val title: String, val description: String)

private val ENGLISH_BUILTIN_PLUGINS: Map<String, PluginCopy> = mapOf(
    "persona" to PluginCopy(
        "Agent persona",
        "Gives the agent the role and behavior described by the current preset.",
    ),
    "tool-pwsh-persistent" to PluginCopy(
        "Persistent PowerShell",
        "Lets the agent keep a PowerShell session whose state survives calls.",
    ),
    "agent-instructions" to PluginCopy(
        "Agent instructions",
        "Loads AGENTS.md and CLAUDE.md to give the agent workspace rules.",
    ),
    "time-context" to PluginCopy(
        "Time context",
        "Tells the model the current time, browser time zone and time since the last message.",
    ),
    "tool-bash" to PluginCopy(
        "Bash tool",
        "Lets the agent run Bash commands with background jobs and approval policies.",
    ),
    "tool-pwsh" to PluginCopy(
        "PowerShell tool",
        "Lets the agent run PowerShell commands.",
    ),
    "tool-fs" to PluginCopy(
        "File tools",
        "Lets the agent read, create and edit workspace files.",
    ),
    "tool-fs-search" to PluginCopy(
        "File search",
        "Lets the agent find files by name or search their contents.",
    ),
    "tool-jobs" to PluginCopy(
        "Background jobs",
        "Lets the agent list background jobs, read output and stop them.",
    ),
    "tool-schedule" to PluginCopy(
        "Reminders",
        "Model tool for reminder management (schedule_create, schedule_list, …).",
    ),
    "skill-filesystem" to PluginCopy(
        "Local skills",
        "Discovers and loads skills from local directories.",
    ),
    "tool-skill" to PluginCopy(
        "Skill tool",
        "Lets the agent load skill instructions on demand.",
    ),
    "command-goal" to PluginCopy(
        "Goal command",
        "Lets the user set and manage the current session goal from the composer.",
    ),
    "tool-goal" to PluginCopy(
        "Goal tool",
        "Lets the agent set, inspect and update the current session goal.",
    ),
    "tool-ask-user" to PluginCopy(
        "Ask user",
        "Lets the agent ask the user when it needs more input or a choice.",
    ),
    "tool-todo" to PluginCopy(
        "To-do list",
        "Lets the agent create and update the session to-do list.",
    ),
    "tool-web" to PluginCopy(
        "Web search and fetch",
        "Lets the agent search the web and read page contents.",
    ),
    "present" to PluginCopy(
        "Deliverables",
        "Lets the agent mark workspace files as this turn's deliverables.",
    ),
    "tool-plugin-manager" to PluginCopy(
        "Plugin manager",
        "Composed per session by the agent preset.",
    ),
    "tool-plugin-market" to PluginCopy(
        "Plugin market",
        "Composed per session by the agent preset.",
    ),
    "plan-mode" to PluginCopy(
        "Plan mode",
        "Lets the agent propose a plan and switch to execution after review.",
    ),
    "compaction-basic" to PluginCopy(
        "Context compaction",
        "Summarizes history with the model once context reaches the threshold.",
    ),
    "command-compact" to PluginCopy(
        "Compact command",
        "Lets the user compress the current session context with a command.",
    ),
    "tool-result-pruner" to PluginCopy(
        "Tool result pruning",
        "Trims long tool output to head and tail under context pressure.",
    ),
    "tool-subagent-control" to PluginCopy(
        "Subagent control",
        "Lets the agent message a subagent or interrupt its run.",
    ),
    "tool-subagent-list-agents" to PluginCopy(
        "Subagent list",
        "Composed per session by the agent preset.",
    ),
    "tool-subagent" to PluginCopy(
        "Subagent",
        "Lets the agent delegate a task to a subagent and collect its result.",
    ),
    "workflow-ptc" to PluginCopy(
        "Workflow runtime",
        "Executes workflow orchestration code in the shared PTC runtime.",
    ),
    "tool-workflow" to PluginCopy(
        "Workflow tool",
        "Lets the agent write and run JavaScript that orchestrates tool calls.",
    ),
    "tool-presentation" to PluginCopy(
        "Tool calling mode",
        "Choose whether the agent calls tools natively or through PTC code.",
    ),
    "pty" to PluginCopy(
        "Persistent terminal service",
        "Manages interactive terminal sessions, their input, output and cleanup.",
    ),
    "terminal-bash" to PluginCopy(
        "Shell backend",
        "Provides a shell terminal whose state survives across calls.",
    ),
    "persistent-bash" to PluginCopy(
        "Persistent Bash",
        "Lets the agent run commands in a Bash session that keeps its state.",
    ),
)

/** 把 Host 的插件 id 归一成文案表里使用的短 id。 */
private fun pluginCopyKey(plugin: BuiltInPluginInfo): String {
    val candidates = listOfNotNull(plugin.entryId, plugin.presetId, plugin.packageName, plugin.title)
    for (candidate in candidates) {
        val short = candidate.substringAfterLast('/')
            .removePrefix("@deepseek-ai/dsh-")
            .substringAfter("dsh-")
            .ifBlank { candidate }
        if (short in ENGLISH_BUILTIN_PLUGINS) return short
        if (candidate in ENGLISH_BUILTIN_PLUGINS) return candidate
    }
    return ""
}

/** 界面语言下的内置插件文案：中文用 Host 原文，英文用本地表，未收录时回退 Host。 */
internal fun localizedBuiltinPlugin(
    languageTag: String,
    plugin: BuiltInPluginInfo,
): BuiltInPluginInfo {
    if (languageTag.startsWith("zh")) return plugin
    val key = pluginCopyKey(plugin)
    val copy = ENGLISH_BUILTIN_PLUGINS[key] ?: return plugin
    // 标题还是包名或中文时换成英文；已经给出英文的保留 Host 文案。
    val title = if (plugin.title.any { it.code > 0x2E80 } || plugin.title == plugin.packageName ||
        plugin.title.substringAfterLast('/') == plugin.title
    ) {
        copy.title
    } else {
        plugin.title
    }
    val description = if (plugin.description.any { it.code > 0x2E80 }) {
        copy.description
    } else {
        plugin.description
    }
    return plugin.copy(title = title, description = description)
}
