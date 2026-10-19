package com.deepseek.dsh.runtime.host

/**
 * rootfs 版本串的解析（单一事实来源）。
 *
 * 串的形态是三段，用 `-` 连接：
 *
 * ```
 * <rootfs>-dsh<dsh>-bridge<bridge>
 * 1.1.0-dsh0.2.1-rc.2-bridge1.1.0
 * ```
 *
 * 三段各有不同含义，因此判断「要不要更新」必须拆段比，不能整串比。
 */
data class RootfsVersion(
    /** rootfs 自身语义版本，例如 `1.1.0`。 */
    val rootfs: String,
    /** 打包进去的 DSH 引擎版本，例如 `0.2.1-rc.2`；旧串缺失时为 null。 */
    val dsh: String?,
    /** 打包进去的 bridge 插件版本，例如 `1.1.0`；旧串缺失时为 null。 */
    val bridge: String?,
) {
    /** 重新拼回线协议里的形态，供日志与展示使用。 */
    fun encode(): String = buildString {
        append(rootfs)
        dsh?.let { append("-dsh").append(it) }
        bridge?.let { append("-bridge").append(it) }
    }

    companion object {
        /** `-dsh<version>` 段，紧接 rootfs 段。 */
        private val DSH_SEGMENT = Regex("""-dsh(\d[^-]*(?:-[^-]+)*?)(?=-bridge|$)""")
        /** `-bridge<version>` 段始终在末尾。 */
        private val BRIDGE_SEGMENT = Regex("""-bridge(\S+)$""")

        /**
         * 解析一个版本串。任何不认识的形态都退化成「整串当 rootfs、其余未知」，
         * 这样比较时表现为「必需更新」，不会因为解析失败而误判为无需更新。
         * @param raw 清单或安装标记里的 `runtimeVersion`。
         * @return 解析结果；空串返回 null。
         */
        fun parse(raw: String?): RootfsVersion? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val bridgeMatch = BRIDGE_SEGMENT.find(text)
            val bridge = bridgeMatch?.groupValues?.get(1)
            val withoutBridge = if (bridgeMatch != null) text.removeRange(bridgeMatch.range) else text
            val dshMatch = DSH_SEGMENT.find(withoutBridge)
            val dsh = dshMatch?.groupValues?.get(1)
            val rootfs = if (dshMatch != null) withoutBridge.removeRange(dshMatch.range) else withoutBridge
            return RootfsVersion(
                rootfs = rootfs.ifBlank { withoutBridge },
                dsh = dsh?.takeIf { it.isNotBlank() },
                bridge = bridge?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/**
 * 一次**镜像**更新的性质，供启动流程判断「是否值得打断用户」。
 *
 * 只有 rootfs 段或 dsh 段变化才返回 [MANDATORY]：那时旧镜像可能跑不起新引擎。
 * 仅仅 bridge 段变化返回 [NONE] —— 插件落后由 [UpdateNeed.PLUGIN_REQUIRED] 在
 * 设置页处理，且可以就地升级，绝不该拦启动、也不该要求重下整包。
 */
enum class RootfsUpdateKind {
    /** 无需重装镜像。 */
    NONE,

    /** 必须重装镜像：rootfs 段或 dsh 段变了。 */
    MANDATORY,
}

/**
 * 用户在设置页该看到哪种更新。
 *
 * 与 [RootfsUpdateKind] 的区别：那个只回答「镜像要不要重装」，这个回答
 * 「用户该做什么」，并明确区分两条路径的优先级：
 *
 * - **插件是必需更新。** 它是客户端的契约对端：Host 与 App 必须说同一个协议，
 *   插件落后即功能缺失。但代价极小（几十 KB 的源码安装），所以「必需」指必须
 *   提示并引导更新，而不是强制重下整包。
 * - **镜像整包是可选的。** 旧镜像完全能跑；重下几百 MB 不该被强加给用户。
 *
 * 插件判断优先于镜像判断：先补齐契约，再考虑换载体。
 */
enum class UpdateNeed {
    /** 无需更新。 */
    NONE,

    /** 插件落后：必须更新，走「更新插件」（rootfs 内就地安装）。 */
    PLUGIN_REQUIRED,

    /** 镜像重建了：可选更新，整包重下。 */
    ROOTFS_OPTIONAL,
}

/**
 * 一次更新判断的完整结论。
 *
 * @param need 需要哪种更新。
 * @param installed 已安装的版本串。
 * @param available 可用的版本串（rootfs 清单里的 `runtimeVersion`）。
 * @param latestPlugin 插件仓库最新 release 的版本，未查询到时为 null。
 * @param installedPlugin rootfs 内实际装的插件版本，读不到时为 null。
 */
data class UpdateAssessment(
    val need: UpdateNeed = UpdateNeed.NONE,
    val installed: String? = null,
    val available: String? = null,
    val latestPlugin: String? = null,
    val installedPlugin: String? = null,
) {
    /** 是否应该提示用户。 */
    val hasUpdate: Boolean get() = need != UpdateNeed.NONE

    /**
     * 「更新插件」按钮是否可用。
     *
     * 已知最新版本、且打包版本或已装版本落后时可用；读不到任何一侧版本时也给
     * 出可用状态：更新一次是最安全的收敛动作。
     */
    val canUpdatePlugin: Boolean get() = need == UpdateNeed.PLUGIN_REQUIRED ||
        (latestPlugin != null && installedPlugin != null && installedPlugin != latestPlugin)
}

/**
 * 比较各段版本，得出更新结论。
 *
 * 规则：
 * 1. 插件侧（`latestPlugin` vs 实际已装插件）：**任一可用来源显示落后 → PLUGIN_REQUIRED**。
 *    以「rootfs 里实际安装的版本」为准，因为用户可能已经单独更新过插件。
 * 2. 镜像侧（rootfs 段或 dsh 段变化）：**ROOTFS_OPTIONAL**，只在设置页提示。
 *
 * 插件判断优先于镜像判断：插件是契约对端，镜像只是载体。
 *
 * @param installed 安装标记里的版本串；null 表示 rootfs 未安装。
 * @param available rootfs 清单里的版本串；null 表示拿不到清单。
 * @param latestPlugin 插件仓库最新 release 版本；null 表示未查询或查询失败。
 * @param installedPlugin rootfs 内实际安装的插件版本；null 表示读不到。
 */
fun assessUpdate(
    installed: String?,
    available: String?,
    latestPlugin: String?,
    installedPlugin: String?,
): UpdateAssessment {
    // 未安装没有「更新」可言：首启安装流程会装好镜像与其打包的插件。
    // 在这里报插件落后，只会让全新用户在装之前就看到一个无法执行的提示。
    if (installed == null) {
        return UpdateAssessment(
            need = UpdateNeed.NONE,
            installed = null,
            available = available,
            latestPlugin = latestPlugin,
            installedPlugin = installedPlugin,
        )
    }

    val localVersion = RootfsVersion.parse(installed)
    val remoteVersion = RootfsVersion.parse(available)

    // 1. 插件：实际已装版本最权威；读不到时退回镜像里打包的版本。
    if (latestPlugin != null) {
        val effectiveInstalled = installedPlugin ?: localVersion?.bridge
        val pluginBehind = effectiveInstalled == null || effectiveInstalled != latestPlugin
        if (pluginBehind) {
            return UpdateAssessment(
                need = UpdateNeed.PLUGIN_REQUIRED,
                installed = installed,
                available = available,
                latestPlugin = latestPlugin,
                installedPlugin = effectiveInstalled,
            )
        }
    }

    // 2. 镜像：只有 rootfs 段或 dsh 段变化才值得重下整包。
    if (localVersion != null && remoteVersion != null && installed != available) {
        val imageMoved = localVersion.rootfs != remoteVersion.rootfs || localVersion.dsh != remoteVersion.dsh
        if (imageMoved) {
            return UpdateAssessment(
                need = UpdateNeed.ROOTFS_OPTIONAL,
                installed = installed,
                available = available,
                latestPlugin = latestPlugin,
                installedPlugin = installedPlugin,
            )
        }
        // 只有打包的 bridge 段不同，但插件已经逐步更新到最新：无需动作。
    }

    return UpdateAssessment(
        need = UpdateNeed.NONE,
        installed = installed,
        available = available,
        latestPlugin = latestPlugin,
        installedPlugin = installedPlugin,
    )
}
