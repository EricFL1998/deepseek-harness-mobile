package com.deepseek.dsh

import com.deepseek.dsh.core.data.DshRepository

/**
 * 让官方 workflow 工具在手机 profile 里可用。
 *
 * 手机镜像默认只带 `ptc-runtime`；`workflow-ptc`（引擎）和 `tool-workflow`
 * （模型可见的工具）是官方可选行，默认禁用——所以 DSH 里没有 workflow 工具。
 * 这里用官方 pluginManager API 把它们打开（等同官方插件页里点开关），只发
 * 两次幂等请求，已启用时 Host 侧就是 no-op。
 */
suspend fun ensureOfficialWorkflowEnabled(repository: DshRepository, hostId: String) {
    for (entryId in listOf("include:workflow-ptc", "include:tool-workflow")) {
        repository.setPluginEnabled(hostId, entryId, true)
    }
}
