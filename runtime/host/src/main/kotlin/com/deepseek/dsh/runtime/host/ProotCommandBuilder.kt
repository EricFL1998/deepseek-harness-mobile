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

    /**
     * 一条待执行命令：argv 加上 proot 自身需要的环境变量。
     *
     * 把环境一并交出来，是因为 `PROOT_LOADER` 必须由调用方的进程环境提供，
     * 而不是命令行参数；启动 Host 时同样如此。
     */
    data class ExecCommand(
        val command: List<String>,
        val environment: Map<String, String>,
    )

    /**
     * 在 rootfs 内执行一条任意命令（不启动 Host）。
     *
     * 绑定、`--link2symlink` 与环境隔离与启动 Host 时一致，因此插件安装看到的
     * 文件系统就是运行时的那一个。工作目录固定为 `/`：安装命令按 profile 解析
     * 路径，不依赖 cwd。
     * @param layout 运行时目录。
     * @param argv rootfs 内的命令，例如 `["/usr/local/bin/node", "…/bin.js", "plugin", …]`。
     * @return 可执行的 argv 与所需环境变量。
     */
    fun buildExec(layout: RuntimeLayout, argv: List<String>): ExecCommand {
        val cmd = mutableListOf<String>()
        cmd.add(layout.prootBinary.absolutePath)
        cmd.add("-r")
        cmd.add(layout.rootfsDir.absolutePath)
        cmd.add("-b")
        cmd.add("/proc")
        cmd.add("-b")
        cmd.add("/dev")
        cmd.add("-b")
        cmd.add("/sys")
        cmd.add("--link2symlink")
        cmd.add("-w")
        cmd.add("/")
        // 与启动 Host 时相同的缓存绑定：DSH 可能把中间产物写到 /cache。
        cmd.add("-b")
        cmd.add("${layout.cacheDir.absolutePath}:/cache")
        // passwd/group 覆盖：让 getpwuid(Android uid) 能解析，node-pty 才能起 shell。
        cmd.add("-b")
        cmd.add("${layout.passwdOverlay.absolutePath}:/etc/passwd")
        cmd.add("-b")
        cmd.add("${layout.groupOverlay.absolutePath}:/etc/group")
        cmd.add("/usr/bin/env")
        cmd.add("-i")
        cmd.add("HOME=/root")
        cmd.add("DSH_HOME=/root/.dsh")
        cmd.add("DSH_PERMISSION_MODE=danger-full-access")
        cmd.add("PATH=/opt/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        cmd.add("NODE_ENV=production")
        cmd.add("LANG=C.UTF-8")
        cmd.add("TMPDIR=/root/.dsh/tmp")
        cmd.addAll(argv)
        return ExecCommand(
            command = cmd,
            environment = mapOf(
                "PROOT_LOADER" to "${layout.nativeLibraryDir.absolutePath}/libproot-loader.so",
                "PROOT_TMP_DIR" to layout.cacheDir.absolutePath,
                "LD_LIBRARY_PATH" to layout.nativeLibraryDir.absolutePath,
            ),
        )
    }

    fun build(config: LaunchConfig): List<String> {
        val rootfs = config.layout.rootfsDir.absolutePath
        val proot = config.layout.prootBinary.absolutePath
        val cache = config.layout.cacheDir.absolutePath
        val saf = config.layout.safDir.absolutePath

        val cmd = mutableListOf<String>()
        cmd.add(proot)
        // 与 DSHA 的 Termux proot 参数对齐：
        // -L 修正 symlink 的 lstat 大小；--kill-on-exit 让 launcher 退出时回收 guest；
        // -0 给 guest 假 root（真实权限仍是应用 UID）。这三项配合下面的 bind，
        // 是 Host 内 subprocess 能按 Linux 语义 spawn bash 的前提。
        cmd.add("-L")
        cmd.add("--kill-on-exit")
        cmd.add("-0")
        cmd.add("-r")
        cmd.add(rootfs)

        fun bind(host: String, guest: String? = null) {
            if (!java.io.File(host).exists()) return
            cmd.add("-b")
            cmd.add(if (guest == null || guest == host) host else "$host:$guest")
        }

        // Linux 伪文件系统挂载。DSHA 另外暴露 /system、/apex、/dev/fd；
        // 这些是 Android 上 Node 原生模块与 bash 的常见依赖。
        bind("/proc")
        bind("/dev")
        bind("/sys")
        bind("/system")
        bind("/apex")
        bind("/proc/self/fd", "/dev/fd")
        // Android 的 /dev/random 可能缺省；bash/openssl 会读它。
        bind("/dev/urandom", "/dev/random")

        // 用户选中的工作区绑定为 /workspace (C4 硬约束)
        bind(config.userWorkspacePath, "/workspace")

        // App 私有缓存与 SAF 镜像
        bind(cache, "/cache")

        // passwd/group 覆盖（见 RuntimeLayout.ensureIdentityOverlays）：
        // proot 里的 uid 是 Android uid，镜像的 passwd 里没有它，node-pty 的
        // getpwuid 会失败，官方 terminal/create 直接报系统错误。
        bind(config.layout.passwdOverlay.absolutePath, "/etc/passwd")
        bind(config.layout.groupOverlay.absolutePath, "/etc/group")

        // 注意：Host 进程不能加 --link2symlink。该 flag 会让 proot 把 link() 模拟成
        // `.l2s.*` 中间文件并移走源文件，在 FUSE 工作区上导致官方 write 工具的
        // “临时文件 + 硬链接”原子发布报 ENOENT。这里改用 Node 预载补丁：link 在
        // FUSE 上拿到 EPERM 后退化成 copy+unlink（见 RuntimeLayout.ensureNodePatches）。
        // 插件安装等 buildExec 命令仍保留 --link2symlink，那是它们需要的语义。
        cmd.add("-w")
        cmd.add("/root")

        // 启动环境隔离
        cmd.add("/usr/bin/env")
        cmd.add("-i")
        cmd.add("HOME=/root")
        cmd.add("DSH_HOME=/root/.dsh")
        // Android/proot 没有可用的 bubblewrap/Landlock 后端；默认完全权限才能执行 shell 工具。
        cmd.add("DSH_PERMISSION_MODE=danger-full-access")
        cmd.add("PATH=/opt/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        cmd.add("NODE_ENV=production")
        // V8 编译缓存：首次启动写盘，之后模块编译直接命中，冷启动明显更快。
        cmd.add("NODE_COMPILE_CACHE=/root/.dsh/node-compile-cache")
        cmd.add("LANG=C.UTF-8")
        cmd.add("TMPDIR=/root/.dsh/tmp")

        config.customEnv.forEach { (k, v) ->
            cmd.add("$k=$v")
        }

        // 直接用 node 执行 dsh 的 bin.js：绕过 /opt/dsh/bin/dsh 包装脚本，
        // 保证 import.meta.url 指向真实安装目录（否则 profile bundle 解析锚点会错）。
        cmd.add("/usr/local/bin/node")
        // 启动剖面：定位 Host 冷启动热点（--cpu-prof 只在调试构建开启）。
        if (config.customEnv["DSH_CPU_PROFILE"] == "1") {
            cmd.add("--cpu-prof")
            cmd.add("--cpu-prof-dir=/root/.dsh/cpu-prof")
        }
        // 引擎的 Loader/app-boot 通过 node-addon-require-builtin 读 Node internal
        // 模块；该原生插件在部分 rootfs 镜像里缺平台二进制时会直接让 Host 起不来
        // （Cannot find module .../node-addon-require-builtin/...）。暴露 internals
        // 后引擎优先走 require('internal/...')，不再依赖那个原生插件。
        cmd.add("--expose-internals")
        // FUSE 工作区的硬链接兜底（write 工具的原子新建路径）。
        cmd.add("--require")
        cmd.add("/root/.dsh/patches/fs-link-fallback.cjs")
        cmd.add("--require")
        cmd.add("/root/.dsh/patches/startup-trace.cjs")
        // 镜像里若带了启动入口 bundle，优先用它：把 ~1,200 次模块文件打开
        // 降到一次（proot 下这是冷启动的主要成本）；没有则退回原 bin.js。
        val bundledEntry = java.io.File(
            config.layout.rootfsDir,
            "usr/local/lib/node_modules/@deepseek-ai/dsh/lib/host.bundle.mjs",
        )
        cmd.add(
            if (bundledEntry.isFile) {
                "/usr/local/lib/node_modules/@deepseek-ai/dsh/lib/host.bundle.mjs"
            } else {
                "/usr/local/lib/node_modules/@deepseek-ai/dsh/lib/bin.js"
            },
        )
        cmd.add("--profile")
        cmd.add("web")
        cmd.add("--no-open")
        cmd.add("--port")
        cmd.add(config.port.toString())

        return cmd
    }
}





