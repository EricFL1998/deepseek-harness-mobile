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

    /**
     * 设备桥 token 文件。
     *
     * 与 guest 内的 `/root/.dsh/.bridge_token` 指向同一份字节；App 每次启动 Host
     * 时轮换，Host 侧补丁按请求携带。文件位于应用私有目录，其他 Android 应用读不到，
     * 因此无法连接宿主机的回环桥执行设备命令。
     */
    val bridgeTokenFile: File get() = File(dshHomeDir, BridgeTokenFileName)

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
    /** 运行前注入的 Node 预载补丁目录（当前只有 fs.link 兜底）。 */
    val patchDir: File get() = File(dshHomeDir, "patches")

    /** 预载补丁：把 FUSE 上必然 EPERM 的硬链接改成 copy+rename 语义。 */
    val linkFallbackPatch: File get() = File(patchDir, "fs-link-fallback.cjs")

    /** 启动耗时探针：记录 preload、慢模块与 dsh web 就绪时刻。 */
    val startupTracePatch: File get() = File(patchDir, "startup-trace.cjs")

    /**
     * 安装 Node 预载补丁。
     *
     * 官方 `write` 工具在“新建文件”时用 `link(temp, target)` 做原子发布；Android 的
     * 外置存储是 FUSE，硬链接直接返回 EPERM（proot 的 --link2symlink 也拦不住
     * Node 走 linkat 的路径），于是 Agent 的重叠写全失败、只能退回 shell。这里预载
     * 一个小补丁：link 遇到 EPERM/ENOTSUP/EXDEV 时退化成“复制 + 删除临时文件”，
     * 保持“目标不存在才成功”的语义。
     */
    fun ensureNodePatches() {
        patchDir.mkdirs()
        val source = """
            |'use strict'
            |const fs = require('node:fs')
            |const fsp = require('node:fs/promises')
            |const FALLBACK_CODES = new Set(['EPERM', 'ENOTSUP', 'EOPNOTSUPP', 'EXDEV', 'EACCES'])
            |function fallbackSync(existingPath, newPath) {
            |  // 只复制、不删除源文件：调用方在 link 成功后通常还会自己 unlink 源
            |  // （官方 attachment store / fs-local 的原子发布都如此），提前删掉会让
            |  // 那一步报 ENOENT 并被包装成写入失败。
            |  fs.copyFileSync(existingPath, newPath, fs.constants.COPYFILE_EXCL || 1)
            |}
            |const originalLinkSync = fs.linkSync
            |fs.linkSync = function (existingPath, newPath) {
            |  try { return originalLinkSync(existingPath, newPath) } catch (e) {
            |    if (!e || !FALLBACK_CODES.has(e.code)) throw e
            |    return fallbackSync(existingPath, newPath)
            |  }
            |}
            |const originalLink = fs.link
            |fs.link = function (existingPath, newPath, callback) {
            |  const done = typeof callback === 'function' ? callback : function () {}
            |  return originalLink.call(fs, existingPath, newPath, function (error) {
            |    if (!error || !FALLBACK_CODES.has(error.code)) return done(error)
            |    try { fallbackSync(existingPath, newPath); done(null) } catch (fallbackError) { done(fallbackError) }
            |  })
            |}
            |const originalLinkPromise = fsp.link
            |fsp.link = async function (existingPath, newPath) {
            |  try { return await originalLinkPromise(existingPath, newPath) } catch (e) {
            |    if (!e || !FALLBACK_CODES.has(e.code)) throw e
            |    fallbackSync(existingPath, newPath)
            |  }
            |}
            |""".trimMargin()
        if (!linkFallbackPatch.isFile || linkFallbackPatch.readText() != source) {
            linkFallbackPatch.writeText(source)
        }
        runCatching { linkFallbackPatch.setReadable(true, false) }

        val traceSource = """
            |'use strict'
            |const Module = require('node:module')
            |// 进程被 force-stop 时不会走 exit 钩子，编译缓存必须定期主动 flush，
            |// 否则 NODE_COMPILE_CACHE 目录永远是空的。这里保持静默，不输出时间日志。
            |const flush = () => { try { Module.flushCompileCache?.() } catch {} }
            |const flushTimer = setInterval(flush, 5000)
            |flushTimer.unref()
            |""".trimMargin()
        if (!startupTracePatch.isFile || startupTracePatch.readText() != traceSource) {
            startupTracePatch.writeText(traceSource)
        }
        runCatching { startupTracePatch.setReadable(true, false) }
    }

    /**
     * 修正 Host 里已经安装好的 subprocess provider 的空 control 通道缺陷。
     *
     * proot 下 `controlPipe()` 可能返回 null；provider 只有 `!== undefined` 判断，
     * 于是一边把 null 塞进 controlChannels，一边调用 `control.once(...)`，把
     * workflow 的 worker 启动炸成 `Cannot read properties of null (reading 'once')`。
     * 这里只把守卫放宽为 null/undefined 都跳过；幂等，随 App 版本生效。
     */
    fun ensureHostRuntimePatches() {
        val provider = java.io.File(
            rootfsDir,
            "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/" +
                "@deepseek-ai/dsh-subprocess-local/lib/index.js",
        )
        if (!provider.isFile) return
        runCatching {
            val text = provider.readText()
            val patched = text.replace(
                "if (control !== void 0) {",
                "if (control != null) {",
            )
            if (patched != text) provider.writeText(patched)
        }
    }

    /** profile 的用户 patch 文件（Host 的插件开关会往这里追加 `- id:` 行）。 */
    val profilePatchFile: File
        get() = File(rootfsDir, "root/.dsh/profiles/web/cordis.patch.yml")

    /**
     * 修复历史版本写坏的 profile patch。
     *
     * Host 的 setPluginEnabled 在空数组文件（内容是 `[]`）后面直接追加 `- id: ...`，
     * 得到 `[]` 紧跟列表项的非法 YAML，下次启动会直接崩在 loadOverlayPatches。
     * 只要文件里已经有列表项，就把单独成行的 `[]` 去掉；只有 `[]` 时保持原样。
     */
    fun repairProfilePatch() {
        val file = profilePatchFile
        if (!file.isFile) return
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val lines = text.lines()
        val hasListItems = lines.any { it.trimStart().startsWith("- ") }
        if (!hasListItems) return
        val fixed = lines.filterNot { it.trim() == "[]" }.joinToString("\n")
        if (fixed != text) {
            runCatching { file.writeText(fixed) }
        }
    }

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
        ensureNodePatches()
    }

    private companion object {
        /** 与 `BridgeAuth.TOKEN_FILE_NAME` 保持一致；host 模块不反向依赖 bridge 模块。 */
        const val BridgeTokenFileName = ".bridge_token"
    }
}


