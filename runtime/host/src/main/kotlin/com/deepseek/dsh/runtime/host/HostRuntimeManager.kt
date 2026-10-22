package com.deepseek.dsh.runtime.host

import kotlinx.serialization.Serializable

@Serializable
enum class RuntimePhase {
    UNINSTALLED,
    INSTALLING,
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

@Serializable
data class RuntimeStatus(
    val phase: RuntimePhase,
    val port: Int? = null,
    val token: String? = null,
    val pid: Long? = null,
    val lastError: String? = null,
    /** 最近输出（已脱敏，只保留最后约 120 行）；失败时给设置页直接看。 */
    val logTail: List<String> = emptyList(),
    /** 子进程退出码；不是「进程退出」导致的失败时为 null。 */
    val exitCode: Int? = null,
    /** 最近一次状态更新时间（epoch millis）。 */
    val updatedAtMillis: Long = 0L,
)

interface HostRuntimeManager {
    suspend fun getStatus(): RuntimeStatus
    suspend fun isRootfsInstalled(): Boolean
    suspend fun installedRootfsVersion(): String?
    suspend fun availableRootfsVersion(): String?
    suspend fun isRootfsUpdateAvailable(): Boolean

    /**
     * 这次更新是否需要整包重装。
     *
     * `MANDATORY` 由启动流程拦截；`OPTIONAL` 只在设置页提示，因为旧 rootfs
     * 完全可用，插件可以单独升级。
     */
    suspend fun rootfsUpdateKind(): RootfsUpdateKind

    /** 安装或更新 rootfs（整包下载）。 */
    suspend fun installRootfs(onProgress: (Float, String) -> Unit): Result<Unit>

    /**
     * 在 rootfs 内单独安装/更新 bridge 插件，不重下 rootfs。
     *
     * 下载插件仓库对应 tag 的源码归档，校验后铺进 profile 的 `node_modules`；
     * 不走 Host 的 git/pnpm（proot 内解析不到 GitHub，且 pnpm 会重写 profile
     * 清单）。装完需要重启 Host 才会加载新代码。
     * @param version 目标版本，取插件仓库的最新 release。
     * @param onProgress 进度与步骤文案。
     * @return 成功后读到的插件版本。
     */
    suspend fun updateBridgePlugin(
        version: String,
        onProgress: (Float, String) -> Unit,
    ): Result<String>

    /** 读取实际在用的 bridge 插件版本（运行中优先，其次落盘）；都读不到时为 null。 */
    suspend fun installedBridgeVersion(): String?

    /**
     * 汇总一次更新评估：查插件仓库最新 release、读本地插件版本、比较镜像版本。
     *
     * 插件落后 → [UpdateNeed.PLUGIN_REQUIRED]（必须更新，走「更新插件」）；
     * 只有镜像段变化 → [UpdateNeed.ROOTFS_OPTIONAL]（可选，整包重下）。
     * @return 评估结论；任何一步查询失败都退化为「无法判断」，不误报。
     */
    suspend fun assessUpdates(): UpdateAssessment

    suspend fun start(workspacePath: String): Result<RuntimeStatus>
    suspend fun stop(): Result<Unit>
}

