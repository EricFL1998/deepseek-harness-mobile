package com.deepseek.dsh.plugin.nativesdk.pages

import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject

/**
 * 页面入口：插件在设置页 / 插件页挂一个原生页面的契约。
 *
 * 对应官方槽位 `settings.section`、`settings.general.item`、`plugins.tab`。
 * 插件声明一个带标题/图标的入口；用户点进后，App 用 [content] 画整页。
 */

/** 一个原生页面入口的元数据。 */
data class PageEntry(
    /** 入口 id（在该插件内唯一），与 JS 侧 settings.section 的注册 id 对应。 */
    val id: String,
    /** 入口标题（设置/插件页列表里显示）。 */
    val title: String,
    /** 副标题/说明。 */
    val subtitle: String? = null,
    /** 挂在哪个页面：设置页 还是 插件页。 */
    val placement: Placement,
    /** 排序（小的在前）。 */
    val order: Int = 0,
) {
    enum class Placement {
        /** 设置页「插件界面」分组（settings.section / settings.general.item）。 */
        SETTINGS,

        /** 插件页（plugins.tab）。 */
        PLUGINS,
    }
}

/** 一个原生页面的内容提供者。 */
interface PageContent {
    /** 该页面对应的入口元数据。 */
    val entry: PageEntry

    /** 画整页内容。spec 是该页的最新状态快照（Host 经 client/refresh 推来）。 */
    @Composable
    fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi)
}

/** 页面注册表：插件注册后，设置页/插件页的列表出现对应入口。 */
object PageRegistry {
    private val pages = mutableListOf<PageContent>()

    fun register(page: PageContent) {
        pages.removeAll { it.entry.placement == page.entry.placement && it.entry.id == page.entry.id }
        pages.add(page)
        pages.sortBy { it.entry.order }
    }

    /** 一个放置位置下的全部页面入口（已排序）。 */
    fun forPlacement(placement: PageEntry.Placement): List<PageContent> =
        pages.filter { it.entry.placement == placement }

    /** 按入口 id 找页面（点进详情时用）。 */
    fun find(placement: PageEntry.Placement, id: String): PageContent? =
        pages.firstOrNull { it.entry.placement == placement && it.entry.id == id }

    fun unregister(page: PageContent) {
        pages.remove(page)
    }
}
