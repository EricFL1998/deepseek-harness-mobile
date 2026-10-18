package com.deepseek.dsh.runtime.host

import java.io.File

/**
 * v4.0 设计文档 §4.3: 启动与就绪命令组装规范
 *
 * proot -0 -r <rootfs> -b /proc -b /dev -b /sys \
 *   -b /storage/emulated/0:/mnt/phone \
 *   -b <user-workspace>:/workspace \
 *   -b <appfiles>/cache:/cache \
 *   -b <appfiles>/saf:/saf \
 *   --link2symlink <rootfs>/usr/bin/env -i HOME=/root DSH_HOME=/root/.dsh ... \
 *   <rootfs>/opt/dsh/bin/dsh --profile web --no-open --port <随机>
 */
object ProotCommandBuilder {

    data class LaunchConfig(
        val layout: RuntimeLayout,
        val userWorkspacePath: String,
        val internalStoragePath: String = "/storage/emulated/0",
        val port: Int = 0, // 0 = 随机端口
        val customEnv: Map<String, String> = emptyMap()
    )

    fun build(config: LaunchConfig): List<String> {
        val rootfs = config.layout.rootfsDir.absolutePath
        val proot = config.layout.prootBinary.absolutePath
        val cache = config.layout.cacheDir.absolutePath
        val saf = config.layout.safDir.absolutePath

        val cmd = mutableListOf<String>()
        cmd.add(proot)
        cmd.add("-r")
        cmd.add(rootfs)

        // Linux 伪文件系统挂载
        cmd.add("-b")
        cmd.add("/proc")
        cmd.add("-b")
        cmd.add("/dev")
        cmd.add("-b")
        cmd.add("/sys")

        // 用户选中的工作区绑定为 /workspace (C4 硬约束)
        cmd.add("-b")
        cmd.add("${config.userWorkspacePath}:/workspace")

        // App 私有缓存与 SAF 镜像
        cmd.add("-b")
        cmd.add("$cache:/cache")

        // 避免 Android 权限与硬链接异常；显式指定 guest 工作目录。
        cmd.add("--link2symlink")
        cmd.add("-w")
        cmd.add("/")

        // 启动环境隔离
        cmd.add("/usr/bin/env")
        cmd.add("-i")
        cmd.add("HOME=/root")
        cmd.add("DSH_HOME=/root/.dsh")
        cmd.add("PATH=/opt/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        cmd.add("NODE_ENV=production")
        cmd.add("LANG=C.UTF-8")

        config.customEnv.forEach { (k, v) ->
            cmd.add("$k=$v")
        }

        // 直接用 node 执行 dsh 的 bin.js：绕过 /opt/dsh/bin/dsh 包装脚本，
        // 保证 import.meta.url 指向真实安装目录（否则 profile bundle 解析锚点会错）。
        cmd.add("/usr/local/bin/node")
        cmd.add("/usr/local/lib/node_modules/@deepseek-ai/dsh/lib/bin.js")
        cmd.add("--profile")
        cmd.add("web")
        cmd.add("--no-open")
        cmd.add("--port")
        cmd.add(config.port.toString())

        return cmd
    }
}
