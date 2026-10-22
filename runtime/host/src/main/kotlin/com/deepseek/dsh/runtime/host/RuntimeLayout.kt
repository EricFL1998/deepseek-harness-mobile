package com.deepseek.dsh.runtime.host

import java.io.File

/**
 * v4.0 设计文档 §4.1: 本地运行时目录结构规范
 *
 * files/
 * ├─ rootfs/                 # Debian slim（默认）/ Ubuntu minimal（备选）
 * │  ├─ opt/dsh/             # Node、pnpm、dsh 入口
 * │  └─ root/.dsh/           # DSH_HOME：配置/会话/插件/凭据
 * ├─ runtime/                # 版本清单、补丁、校验和
 * ├─ workspace-link/         # 工作区绑定信息
 * ├─ cache/                  # 依赖/构建缓存（/cache）
 * ├─ saf/                    # SAF 镜像根
 * ├─ logs/                   # 运行时日志
 * └─ backups/                # 用户数据快照
 */
data class RuntimeLayout(
    val baseDir: File,
    val nativeLibraryDir: File
) {
    val rootfsDir: File get() = File(baseDir, RootfsNaming.INSTALL_DIR)
    val optDshDir: File get() = File(rootfsDir, "opt/dsh")
    val dshHomeDir: File get() = File(rootfsDir, "root/.dsh")
    val runtimeMetaDir: File get() = File(baseDir, "runtime")
    val cacheDir: File get() = File(baseDir, "cache")
    val safDir: File get() = File(baseDir, "saf")
    val logsDir: File get() = File(baseDir, "logs")
    val backupsDir: File get() = File(baseDir, "backups")

    val prootBinary: File get() = File(nativeLibraryDir, "libproot.so")

    /** 覆盖 rootfs /etc/passwd 的绑定文件；Android uid 不在镜像的 passwd 里。 */
    val passwdOverlay: File get() = File(runtimeMetaDir, "etc/passwd")
    /** 覆盖 rootfs /etc/group 的绑定文件。 */
    val groupOverlay: File get() = File(runtimeMetaDir, "etc/group")

    /**
     * 写入 passwd/group 覆盖文件。
     *
     * proot 不带 -0 时 guest 的 uid 就是 Android uid（如 10227），而 rootfs 镜像
     * 的 /etc/passwd 只有 root 等系统账号。node-pty 起 shell 时 libuv 会
     * `uv_os_get_passwd`（getpwuid）——查不到就返回 ENOENT，官方终端因此
     * 报 `A system error occurred`。这里把当前 uid/gid 补进 passwd/group，
     * 再用 proot -b 绑定进去。
     */
    fun ensureIdentityOverlays() {
        runtimeMetaDir.mkdirs()
        passwdOverlay.parentFile?.mkdirs()
        val uid = android.os.Process.myUid()
        val gid = runCatching { android.system.Os.getgid() }.getOrDefault(uid)
        val passwd = """
            root:x:0:0:root:/root:/bin/sh
            dsh:x:$uid:$gid:dsh:/root:/bin/sh
        """.trimIndent() + "\n"
        val group = """
            root:x:0:
            dsh:x:$gid:
        """.trimIndent() + "\n"
        if (!passwdOverlay.isFile || passwdOverlay.readText() != passwd) {
            passwdOverlay.writeText(passwd)
        }
        if (!groupOverlay.isFile || groupOverlay.readText() != group) {
            groupOverlay.writeText(group)
        }
        runCatching {
            passwdOverlay.setReadable(true, false)
            groupOverlay.setReadable(true, false)
        }
    }

    fun ensureDirectories() {
        baseDir.mkdirs()
        rootfsDir.mkdirs()
        optDshDir.mkdirs()
        dshHomeDir.mkdirs()
        runtimeMetaDir.mkdirs()
        cacheDir.mkdirs()
        safDir.mkdirs()
        logsDir.mkdirs()
        backupsDir.mkdirs()
        ensureIdentityOverlays()
    }
}


