package com.deepseek.dsh.runtime.host

/** 进程内 HostRuntimeManager 引用，供前台服务在任务移除时停止 Host。 */
object HostRuntimeHolder {
    @Volatile
    var manager: HostRuntimeManager? = null

    /**
     * 由 App 层注入的「在 Host 内安装插件」动作。
     *
     * bridge 插件不能靠 App 另起一个 proot 去装：Android 给应用进程的 seccomp
     * 策略会把 `getrandom` 一类系统调用直接返回 `ENOSYS`，于是 glibc 的
     * `mkdtemp` 失败；而同一条命令经由 Host 执行就正常 —— Host 已经是跑在 proot
     * 里的那个进程，而且它是用自己的身份启动的。
     *
     * 因此这里注入 Host 的官方 RPC（`pluginManager/installBundle`），走的是与
     * 插件页「添加插件」完全相同的那条路径。
     *
     * 放在 Holder 而不是把仓库类型搬进 runtime 模块，是为了不让底层模块依赖数据层。
     */
    @Volatile
    var pluginInstaller: (suspend (spec: String) -> Result<String>)? = null
}
