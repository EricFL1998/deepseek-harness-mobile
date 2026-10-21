package com.deepseek.dsh.runtime.host

/**
 * 进程内 HostRuntimeManager 引用，供前台服务在任务移除时停止 Host。
 *
 * 另外两个回调由 App 层注入，用于把「App 的数据层」和「Host 的运行控制」接上，
 * 而不让 `runtime:host` 依赖它们：
 *
 * - [runningBridgeVersionProvider]：从 bridge/v1 manifest 读**正在运行**的插件
 *   版本。这是最权威的「已装版本」来源 —— 落盘的 `package.json` 可能是新的、
 *   但 Host 还没重启，运行中的仍是旧代码。
 * - [restartHost]：装完插件后重启 Host，让新代码真正加载。
 */
object HostRuntimeHolder {
    @Volatile
    var manager: HostRuntimeManager? = null

    /** 读取 Host 当前运行的 bridge 插件版本；bridge 不可达或插件缺失时返回 null。 */
    @Volatile
    var runningBridgeVersionProvider: (suspend () -> String?)? = null

    /** 重启本地 Host；未处于可重启状态时返回可读的失败原因。 */
    @Volatile
    var restartHost: (suspend () -> Result<Unit>)? = null
}
