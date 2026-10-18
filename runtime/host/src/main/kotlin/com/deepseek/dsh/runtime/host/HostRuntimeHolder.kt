package com.deepseek.dsh.runtime.host

/** 进程内 HostRuntimeManager 引用，供前台服务在任务移除时停止 Host。 */
object HostRuntimeHolder {
    @Volatile
    var manager: HostRuntimeManager? = null
}
