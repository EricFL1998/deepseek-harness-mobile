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
    val lastError: String? = null
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
     * 走 GitHub 源码安装：Host 内执行
     * `dsh plugin --profile web add <repo>#<tag>`，因此需要网络；失败时返回
     * 可读原因，绝不留半装状态（命令自身是原子的）。
     * @param onProgress 进度与步骤文案。
     * @return 成功后 rootfs 内读到的插件版本。
     */
    suspend fun updateBridgePlugin(onProgress: (Float, String) -> Unit): Result<String>

    /** 读取 rootfs 内已安装的 bridge 插件版本；未安装或读不到时为 null。 */
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

