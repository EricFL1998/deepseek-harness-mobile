package com.deepseek.dsh.runtime.host

import java.io.File
import java.nio.file.Files

/**
 * 给 rootfs 里的 `node-addon-require-builtin` 装一个纯 JS 兜底实现。
 *
 * 引擎的 Loader 与 app-boot 通过这个原生插件读取 Node internal 模块（ESM/CJS
 * loader 等）。该插件的平台二进制是 optionalDependency：镜像打包机与目标 ABI
 * 不一致、或安装时跳过了 optional 依赖，包里就只剩 JS 外壳，Host 启动时直接
 * 报 `Cannot find module .../node-addon-require-builtin/...` 退出。
 *
 * Node 以 `--expose-internals` 启动时 `require('internal/...')` 直接可用，因此
 * 我们可以用 `requireBuiltin(id) = require(id)` 的 JS 实现顶替原生插件。只替换
 * 「已经坏了」的安装（主文件缺失 / 平台包缺失），健康的镜像保持原样。
 *
 * 幂等：结果写入 `/root/.dsh/.addon-shim-revision`，同一 revision 不重复改动。
 */
internal object RuntimeAddonShim {

    /** 被替换的包名。 */
    const val PACKAGE = "node-addon-require-builtin"

    /** shim 自己的版本；改动 shim 内容时递增，旧 rootfs 会重新应用。 */
    private const val REVISION = "1"

    /**
     * 检查并（必要时）安装 shim。
     * @param layout 运行时目录。
     */
    fun ensure(layout: RuntimeLayout) {
        val globalModules = File(
            layout.rootfsDir,
            "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules",
        )
        if (!globalModules.isDirectory) return
        val marker = File(layout.rootfsDir, "root/.dsh/.addon-shim-revision")
        if (marker.isFile && marker.readText().trim() == REVISION) return

        val target = File(globalModules, PACKAGE)
        if (needsShim(target, globalModules, layout)) {
            install(target)
        }
        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText(REVISION)
        }
    }

    /**
     * 原生插件是否已经不可用。
     *
     * 判据：包/主文件缺失，或 `optionalDependencies` 里声明的平台包一个都不在。
     * 解析失败按「坏」处理——宁可换成纯 JS 实现，也不要让 Host 起不来。
     */
    private fun needsShim(
        base: File,
        globalModules: File,
        layout: RuntimeLayout,
    ): Boolean {
        val manifest = File(base, "package.json")
        if (!manifest.isFile) return true
        // 用文本匹配而不是 org.json：这里只需要两个字段，且单元测试运行在 JVM 上
        // （android.jar 的 org.json 是未实现的 stub）。
        val text = runCatching { manifest.readText() }.getOrNull() ?: return true
        val main = MAIN_PATTERN.find(text)?.groupValues?.get(1) ?: "index.js"
        if (!File(base, main).isFile) return true
        val platformPackages = PLATFORM_PATTERN.findAll(text)
            .map { it.groupValues[1] }
            .toSet()
        if (platformPackages.isEmpty()) return false
        // 平台包可能 hoisted 到 dsh/node_modules、放在包内，或落在 rootfs 全局。
        val roots = listOf(
            globalModules,
            File(base, "node_modules"),
            File(layout.rootfsDir, "usr/local/lib/node_modules"),
        )
        return platformPackages.none { name -> roots.any { root -> File(root, name).exists() } }
    }

    /** `package.json` 里的 `main` 字段。 */
    private val MAIN_PATTERN = Regex("\"main\"\\s*:\\s*\"([^\"]+)\"")

    /** `optionalDependencies` 里的平台子包名（`node-addon-require-builtin-<os>-<arch>-<libc>`）。 */
    private val PLATFORM_PATTERN = Regex("\"($PACKAGE-[a-z0-9-]+)\"")

    /** 用纯 JS 实现替换目标包（原目录改名留档，便于排查）。 */
    private fun install(target: File) {
        val parent = target.parentFile ?: return
        if (target.exists() || Files.isSymbolicLink(target.toPath())) {
            val backup = File(parent, "$PACKAGE.broken-${System.currentTimeMillis()}")
            runCatching { target.renameTo(backup) }
        }
        target.mkdirs()
        File(target, "package.json").writeText(
            """{"name":"$PACKAGE","version":"0.1.9-local-shim","main":"index.js","private":true}"""
        )
        File(target, "index.js").writeText(
            """
            'use strict';
            // 引擎只用到 requireBuiltin(id)：Node 以 --expose-internals 启动时
            // require('internal/...') 可用，因此不再依赖原生 .node 二进制。
            function requireBuiltin(id) {
                return require(id);
            }
            module.exports = { requireBuiltin };
            """.trimIndent() + "\n"
        )
    }
}
