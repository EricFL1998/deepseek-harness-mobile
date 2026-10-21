package com.deepseek.dsh.core.data

/**
 * 一次 bundle 启停的 Host 变更结果里界面要展示的部分。
 *
 * 对应 Host `pluginManager` 的 `ChangeResult`：`application` 可能是
 * `applied / restart-required / overridden / failed / cancelled`；
 * `restart-required` 时界面必须提示「请重启 DSH 使更改生效」，`warnings`
 * 是本次操作留下的提示（官方网页端也会展示）。
 */
data class BundleChangeOutcome(
    /** 应用结果：applied / restart-required / overridden / failed / cancelled。 */
    val application: String?,
    /** 为什么要重启：no-hmr / package-moved / host-module / unmanaged-plugin / module-comparison。 */
    val restartReason: String?,
    /** Host 返回的提示列表。 */
    val warnings: List<String>,
) {
    /** 是否要重启 DSH 才生效。部分 Host 版本只返回 restartReason。 */
    val restartRequired: Boolean
        get() = application == "restart-required" || !restartReason.isNullOrBlank()
}
