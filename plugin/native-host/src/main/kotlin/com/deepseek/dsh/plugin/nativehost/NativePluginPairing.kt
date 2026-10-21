package com.deepseek.dsh.plugin.nativehost

/**
 * 「装 APK 联动装 JS 插件」的配对解析。
 *
 * 伴侣 APK 是补全：它的 UI 要画的内容由配对的 JS 插件（Host 侧）供给。装 APK 时
 * 必须连带装 JS 插件，否则 APK 没有逻辑也没有 spec 可画。
 *
 * 配对来源（优先级）：
 * 1. **甲**：APK manifest 的 `dsh.plugin.jsPackage`（作者直声明，最准）；
 * 2. **乙**：商店索引里该 pluginId 对应的 jsPackage（集中管理，可改）；
 *
 * 两者都没有时返回 null——调用方提示「这个原生插件没声明配对的 JS 插件」，不瞎猜。
 */
object NativePluginPairing {

    /**
     * 解析一个伴侣插件要连带安装的 JS 插件地址。
     *
     * @param plugin 已发现的伴侣 APK。
     * @param storeLookup 商店索引查询：给 pluginId，回 jsPackage（乙兜底）；无索引传 null。
     * @return JS 插件安装地址（npm 包名 / GitHub 地址）；两个来源都没有时 null。
     */
    fun resolveJsPackage(
        plugin: InstalledNativePlugin,
        storeLookup: ((pluginId: String) -> String?)? = null,
    ): String? {
        // 甲：manifest 直声明。
        plugin.jsPackage?.takeIf { it.isNotBlank() }?.let { return it }
        // 乙：商店索引兜底。
        return storeLookup?.invoke(plugin.pluginId)?.takeIf { it.isNotBlank() }
    }
}
