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
 * 由 **dsh 段**与 **rootfs 段**共同决定，但两者的打扰级别不同：
 *
 * - dsh 段变化 → [MANDATORY]：引擎换了，旧镜像里预置的运行时补丁可能不再匹配，
 *   必须重装镜像。
 * - dsh 段相同、rootfs 段变化 → [REPACKAGED]：镜像重新打包过。它可能是为了把新
 *   插件打进镜像（此时插件已能就地升级），也可能是**只存在于镜像里的修复**
 *   （例如原生依赖/打包缺陷）。启动流程不拦人，只在设置页给一个可选入口，
 *   让用户能主动换上有修复的镜像。
 * - 两者都相同 → [NONE]。
 *
 * 早先的实现在这里完全忽略 rootfs 段，于是「镜像级修复」没有任何下发路径。
 * 现在改成：启动横幅仍只认 [MANDATORY]，重打包的镜像只出现在设置 → 更新里。
 */
enum class RootfsUpdateKind {
    /** 无需重装镜像。 */
    NONE,

    /** 引擎没变、但镜像重新打包过：设置页可选更新，不打断启动。 */
    REPACKAGED,

    /** 必须重装镜像：dsh 引擎换了。 */
    MANDATORY,
}

/**
 * 判断镜像是否需要重装。这是该判断的**唯一实现**，启动流程与设置页都走这里。
 *
 * 只看 dsh 段判断「是否强制」；dsh 相同而 rootfs 段自增时给 [REPACKAGED]，
 * 由设置页提供可选更新。任一侧无法解析时返回 [RootfsUpdateKind.MANDATORY]：
 * 宁可多问一次，也不能因为认不出格式就假定「已是最新」而让用户停在跑不起来的
 * 运行时上。
 * @param installed 已安装的版本串；null 表示未安装或读不到。
 * @param available 可用的版本串；null 表示拿不到清单。
 * @return 镜像更新的性质。
 */
fun decideRootfsUpdateKind(
    installed: String?,
    available: String?,
    installedPatched: Boolean = false,
    availablePatched: Boolean = false,
): RootfsUpdateKind {
    if (installed == null || available == null) return RootfsUpdateKind.NONE
    val local = RootfsVersion.parse(installed) ?: return RootfsUpdateKind.MANDATORY
    val remote = RootfsVersion.parse(available) ?: return RootfsUpdateKind.MANDATORY
    // 构建期已烧入 Android 补丁的镜像不再受 DSH 段约束：它内部是「最新
    // DSH + 补丁」，换引擎不需要换 APK 的整文件补丁。是否更新只看 rootfs 段。
    if (availablePatched) {
        val order = comparePluginVersions(remote.rootfs, local.rootfs)
        if (order != null && order <= 0) return RootfsUpdateKind.NONE
        return if (local.rootfs != remote.rootfs) RootfsUpdateKind.REPACKAGED else RootfsUpdateKind.NONE
    }
    // 已装的是烧过补丁的新镜像，而远端只有旧的未烧补丁镜像：不要退回旧模型。
    if (installedPatched) return RootfsUpdateKind.NONE
    // 引擎段不一致优先于“镜像段更旧”：APK 的补丁按引擎版本整文件替换，
    // 即使用户装了一个更新的镜像（例如先收到了 rc.3），只要引擎不匹配就必须
    // 换回与当前 APK 对应的镜像，不能因为 rootfs 段更大而保持不动。
    if (local.dsh != remote.dsh) return RootfsUpdateKind.MANDATORY
    // 离线兜底清单可能比已装镜像旧（内置 assets 里的那份不会随发版更新）：
    // 旧版本不能被当成「可更新」，否则会引导用户降级重下 300MB。
    val rootfsOrder = comparePluginVersions(remote.rootfs, local.rootfs)
    if (rootfsOrder != null && rootfsOrder < 0) return RootfsUpdateKind.NONE
    return when {
        local.rootfs != remote.rootfs -> RootfsUpdateKind.REPACKAGED
        else -> RootfsUpdateKind.NONE
    }
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
 * @param installedPlugin 实际在用的插件版本（优先取 Host 报出的运行中版本，
 *   其次读 rootfs 内落盘的版本）；都读不到时为 null。
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
     * 插件是否落后于最新发布。
     *
     * 只有「已知最新版本」且「已装版本确实更旧（或未知）」时才成立；已装版本
     * 比最新发布还新时（例如装了未发布的开发版）不算落后，也绝不允许降级。
     */
    val pluginOutdated: Boolean get() = installed != null &&
        isPluginBehind(installedPlugin, latestPlugin)

    /**
     * 「更新插件」按钮是否可用。
     *
     * 必须在已安装 rootfs、拿到最新发布版本、且已装版本确实落后时可用：
     * 版本相同、已装版本更新、或离线拿不到最新版本时都不该按下去（按下去只会
     * 装上错误的方向或直接失败）。
     */
    val canUpdatePlugin: Boolean get() = pluginOutdated
}

/**
 * 比较两个插件版本（`1.24.0` 与 `1.1.0`）。
 *
 * 只接受 `x.y.z` 三段，按数字段比较；带 `-rc.1` 一类后缀的预发布版本小于同号
 * 正式版。任一侧为空或不是三段形态时返回 null —— 调用方据此退化为「不同即
 * 落后」的保守判断，而不是把无法识别的版本当成最新。
 * @param left 左侧版本。
 * @param right 右侧版本。
 * @return 负数表示 left 更旧，0 表示相同，正数表示 left 更新；不可比时为 null。
 */
fun comparePluginVersions(left: String?, right: String?): Int? {
    fun parse(raw: String?): Pair<List<Int>, Boolean>? {
        val text = raw?.trim()?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: return null
        val numeric = text.substringBefore('-').substringBefore('+')
        val segments = numeric.split('.')
        if (segments.size != 3) return null
        val numbers = segments.map { it.toIntOrNull() ?: return null }
        return numbers to text.contains('-')
    }
    val a = parse(left) ?: return null
    val b = parse(right) ?: return null
    for (index in 0..2) {
        val difference = a.first[index].compareTo(b.first[index])
        if (difference != 0) return difference
    }
    return when {
        a.second == b.second -> 0
        a.second -> -1
        else -> 1
    }
}

/**
 * 已装版本是否落后于最新发布。
 *
 * 版本可解析时按数字段比较（因此 `1.24.0` 不会因为字符串不等而误判成落后
 * 于 `1.1.0`）；不可解析时退回「不同即落后」：宁可提示一次，也不把无法识别
 * 的版本当成最新而让契约端一直停在旧协议上。
 * @param installed 已装版本；null 表示读不到。
 * @param latest 最新发布版本；null 表示未查询到。
 * @return 是否需要更新。
 */
fun isPluginBehind(installed: String?, latest: String?): Boolean {
    if (latest.isNullOrBlank()) return false
    if (installed.isNullOrBlank()) return true
    val comparison = comparePluginVersions(latest, installed)
    return if (comparison != null) comparison > 0 else latest.trim() != installed.trim()
}

/**
 * 比较各段版本，得出更新结论。
 *
 * 规则：
 * 1. 插件侧（`latestPlugin` vs 实际在用的插件版本）：**最新发布确实更新时 →
 *    PLUGIN_REQUIRED**。版本按数字段比较，已装版本比最新发布还新时不算落后。
 *    实际在用的版本以 Host 报出的运行中版本为准，读不到时用 profile 内落盘的
 *    版本；都不算「镜像里打包过」，那只是打包记录。
 * 2. 镜像侧（dsh 段变化或 rootfs 段变化）：**ROOTFS_OPTIONAL**，只在设置页
 *    提示、不打断启动。dsh 段变化意味着引擎换了；rootfs 段变化意味着镜像重新
 *    打包，可能带着只存在于镜像里的修复。
 *
 * 插件判断优先于镜像判断：插件是契约对端，镜像只是载体。
 *
 * @param installed 安装标记里的版本串；null 表示 rootfs 未安装。
 * @param available rootfs 清单里的版本串；null 表示拿不到清单。
 * @param latestPlugin 插件仓库最新 release 版本；null 表示未查询或查询失败。
 * @param installedPlugin 实际在用的插件版本；null 表示运行中和落盘都读不到。
 */
fun assessUpdate(
    installed: String?,
    available: String?,
    latestPlugin: String?,
    installedPlugin: String?,
    installedPatched: Boolean = false,
    availablePatched: Boolean = false,
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

    // 1. 插件：只认实际在用的版本（Host 报出的运行中版本，或 rootfs 内落盘的
    //    版本）。镜像里打包的 `-bridge` 段只是打包记录，不能当作「已安装」：
    //    插件的家是 profile，镜像段说打包过不等于此刻还在。
    if (latestPlugin != null) {
        if (isPluginBehind(installedPlugin, latestPlugin)) {
            return UpdateAssessment(
                need = UpdateNeed.PLUGIN_REQUIRED,
                installed = installed,
                available = available,
                latestPlugin = latestPlugin,
                installedPlugin = installedPlugin,
            )
        }
    }

    // 2. 镜像：dsh 引擎换了必须重下整包；引擎没变而镜像重新打包过，也提供
    //    可选更新——重新打包可能是把新插件打进镜像，也可能带着只存在于镜像里的
    //    修复（原生依赖/打包缺陷），但用户不该在启动时被打断。
    if (decideRootfsUpdateKind(installed, available, installedPatched, availablePatched) != RootfsUpdateKind.NONE) {
        return UpdateAssessment(
            need = UpdateNeed.ROOTFS_OPTIONAL,
            installed = installed,
            available = available,
            latestPlugin = latestPlugin,
            installedPlugin = installedPlugin,
        )
    }

    return UpdateAssessment(
        need = UpdateNeed.NONE,
        installed = installed,
        available = available,
        latestPlugin = latestPlugin,
        installedPlugin = installedPlugin,
    )
}
