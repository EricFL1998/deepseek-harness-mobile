package com.deepseek.dsh.plugin.nativehost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
import kotlinx.serialization.json.JsonObject

/**
 * 右侧栏插件标签：App 侧新增的落点 API。
 *
 * 插件在 [NativeSlot.RIGHT_PANEL] 注册 [SlotContribution] 并设置 `tabTitle`，
 * App 的聊天右侧栏就会追加一个同名标签；标签内容是该贡献的 Render。内置标签
 * （Files / Document / Plan / Deliverables / Terminal / Schedule / Browser）
 * 永远排在最前面，插件标签按 [SlotContribution.order] 排序。
 *
 * 同一份描述同时供右侧栏和测试使用：纯函数 [rightPaneTabDescriptors] 只做
 * 过滤与排序，不依赖 Compose；[nativeRightPaneTabs] 再把描述包成可渲染项。
 */
data class NativeRightPaneTab(
    /** 稳定标识：插件类名 + order，避免同标题冲突。 */
    val key: String,
    val title: String,
    val render: @Composable () -> Unit,
)

/** 从注册表快照里挑出带标题的右侧栏贡献（按 order 排序）。 */
fun rightPaneTabDescriptors(
    contributions: List<SlotContribution>,
): List<Pair<String, String>> = contributions
    .filter { it.slot == NativeSlot.RIGHT_PANEL && it.tabTitle != null }
    .sortedBy { it.order }
    .map { contribution ->
        val title = contribution.tabTitle.orEmpty()
        "${contribution.javaClass.name}#${contribution.order}" to title
    }

/**
 * 当前已注册的右侧栏插件标签。
 *
 * 读取 [SlotRegistry.resolve] 会观察注册表版本，插件 APK 装载/卸载后自动重组。
 * 无 NativeHost / Theme（预览、测试或 App 未接 Host）时返回空列表。
 */
@Composable
fun nativeRightPaneTabs(): List<NativeRightPaneTab> {
    val host = NativeHost.LocalHost.current ?: return emptyList()
    val theme = NativeHost.LocalTheme.current ?: return emptyList()
    val contributions = SlotRegistry.resolve(NativeSlot.RIGHT_PANEL)
    val slotHost = remember(host) { SlotBridgeHost(host) }
    val descriptors = remember(contributions) { rightPaneTabDescriptors(contributions) }
    if (descriptors.isEmpty()) return emptyList()
    // contributions 与 descriptors 一一对应（同一过滤/排序）。
    val titled = remember(contributions) {
        contributions
            .filter { it.slot == NativeSlot.RIGHT_PANEL && it.tabTitle != null }
            .sortedBy { it.order }
    }
    return titled.mapIndexed { index, contribution ->
        val (key, title) = descriptors[index]
        NativeRightPaneTab(
            key = key,
            title = title,
            render = {
                NativeSlotGuard(
                    pluginLabel = contribution.javaClass.simpleName,
                    theme = theme,
                ) {
                    // 右侧栏标签没有槽位级 spec：插件通过 PluginRpcHost 自己取数据。
                    contribution.Render(JsonObject(emptyMap()), theme, slotHost)
                }
            },
        )
    }
}


/** 右侧栏开始页的一枚插件入口胶囊（官方 `sidebar.right.tab.guide.entry`）。 */
data class NativeGuideEntry(
    val key: String,
    val title: String,
    val description: String?,
    val render: @Composable () -> Unit,
)

/** 从注册表快照里挑出带 guideTitle 的入口（按 order 排序）。 */
fun guideEntryDescriptors(
    contributions: List<SlotContribution>,
): List<Triple<String, String, String?>> = contributions
    .filter { it.slot == NativeSlot.RIGHT_PANEL_GUIDE_ENTRY && it.guideTitle != null }
    .sortedBy { it.order }
    .map { contribution ->
        Triple(
            "${contribution.javaClass.name}#${contribution.order}",
            contribution.guideTitle.orEmpty(),
            contribution.guideDescription,
        )
    }

/** 当前注册的 guide 入口胶囊。 */
@Composable
fun nativeGuideEntries(): List<NativeGuideEntry> {
    val host = NativeHost.LocalHost.current ?: return emptyList()
    val theme = NativeHost.LocalTheme.current ?: return emptyList()
    val contributions = SlotRegistry.resolve(NativeSlot.RIGHT_PANEL_GUIDE_ENTRY)
    val slotHost = remember(host) { SlotBridgeHost(host) }
    val descriptors = remember(contributions) { guideEntryDescriptors(contributions) }
    if (descriptors.isEmpty()) return emptyList()
    val titled = remember(contributions) {
        contributions
            .filter { it.slot == NativeSlot.RIGHT_PANEL_GUIDE_ENTRY && it.guideTitle != null }
            .sortedBy { it.order }
    }
    return titled.mapIndexed { index, contribution ->
        val (key, title, description) = descriptors[index]
        NativeGuideEntry(
            key = key,
            title = title,
            description = description,
            render = {
                NativeSlotGuard(
                    pluginLabel = contribution.javaClass.simpleName,
                    theme = theme,
                ) {
                    contribution.Render(JsonObject(emptyMap()), theme, slotHost)
                }
            },
        )
    }
}
