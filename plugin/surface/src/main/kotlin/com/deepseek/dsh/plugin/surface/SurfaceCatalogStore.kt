package com.deepseek.dsh.plugin.surface

import com.deepseek.dsh.core.model.DshSlotRegistry
import com.deepseek.dsh.core.model.SurfaceCatalog
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.core.model.SurfaceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * v4.0 设计文档 §7.4 & §7.5:
 * Surface 目录中心与原生放置决策，对齐全部核心 Slot
 */
enum class PlacementDestination {
    SETTING_SHEET,    // 设置页 (Sheet)
    SIDEBAR_PANEL,    // 侧栏面板
    COMPOSER_DOCK,    // 输入框菜单 / 底部药丸栏
    COMPOSER_OVERLAY, // 输入框悬浮气泡
    PLUGIN_OVERLAY,   // 顶层插件浮层 (悬浮/收起/气泡)
    MESSAGE_ACTION,   // 消息长按动作
    TOOL_VIEW_SHEET,  // 工具卡片专属弹窗
    WEB_ISLAND_FULL   // 整岛回退
}

data class SurfacePlacement(
    val item: SurfaceItem,
    val destination: PlacementDestination,
    val isIsolatedSupported: Boolean
)

class SurfaceCatalogStore {
    private val _catalogFlow = MutableStateFlow(
        SurfaceCatalog(clientId = "local", dshVersion = "4.0.0")
    )
    val catalogFlow: StateFlow<SurfaceCatalog> = _catalogFlow.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun updateCatalogFromJson(rawJson: String) {
        try {
            val catalog = json.decodeFromString<SurfaceCatalog>(rawJson)
            _catalogFlow.value = catalog
        } catch (_: Exception) {
        }
    }

    fun addSurfaceItem(item: SurfaceItem) {
        val current = _catalogFlow.value
        val list = current.surfaces.toMutableList()
        list.removeAll { it.pluginId == item.pluginId && it.slot == item.slot }
        list.add(item)
        _catalogFlow.value = current.copy(surfaces = list)
    }

    /** 从目录中移除一个已安装插件（卸载），不存在时为空操作。 */
    fun removeSurfaceItem(pluginId: String, slot: String) {
        val current = _catalogFlow.value
        val list = current.surfaces.filterNot { it.pluginId == pluginId && it.slot == slot }
        if (list.size != current.surfaces.size) {
            _catalogFlow.value = current.copy(surfaces = list)
        }
    }

    /**
     * 将 DSH Slot 精确映射到手机端原生宿主承载位置
     */
    fun resolvePlacement(item: SurfaceItem): SurfacePlacement {
        val dest = when (item.slot) {
            DshSlotRegistry.CONVERSATION_COMPOSER_DOCK -> PlacementDestination.COMPOSER_DOCK
            DshSlotRegistry.CONVERSATION_INPUT_DOCK -> PlacementDestination.COMPOSER_DOCK
            DshSlotRegistry.CONVERSATION_INPUT_OVERLAY -> PlacementDestination.COMPOSER_OVERLAY
            DshSlotRegistry.CONVERSATION_CHAT_ASSISTANT_ACTIONS,
            DshSlotRegistry.CONVERSATION_CHAT_TURN_TAIL -> PlacementDestination.MESSAGE_ACTION
            DshSlotRegistry.TOOL_CALL_TOOLVIEW,
            DshSlotRegistry.TOOL_CALL_IMAGES -> PlacementDestination.TOOL_VIEW_SHEET
            DshSlotRegistry.SIDEBAR_WORKSPACES,
            DshSlotRegistry.SIDEBAR_PANELLIST,
            DshSlotRegistry.SIDEBAR_RIGHT_PANE_TAB,
            DshSlotRegistry.SIDEBAR_WORKSPACES_SESSION_MENU_ITEM -> PlacementDestination.SIDEBAR_PANEL
            DshSlotRegistry.SHELL_OVERLAY -> PlacementDestination.PLUGIN_OVERLAY
            DshSlotRegistry.SETTINGS_SECTION,
            DshSlotRegistry.SETTINGS_GENERAL_ITEM,
            DshSlotRegistry.PLUGINS_TAB -> PlacementDestination.SETTING_SHEET
            else -> {
                when (item.kind) {
                    SurfaceKind.PAGE -> PlacementDestination.SETTING_SHEET
                    SurfaceKind.SIDEBAR -> PlacementDestination.SIDEBAR_PANEL
                    SurfaceKind.DOCK -> PlacementDestination.COMPOSER_DOCK
                    SurfaceKind.OVERLAY -> PlacementDestination.PLUGIN_OVERLAY
                    SurfaceKind.MESSAGE_ACTION -> PlacementDestination.MESSAGE_ACTION
                    else -> PlacementDestination.WEB_ISLAND_FULL
                }
            }
        }
        return SurfacePlacement(
            item = item,
            destination = dest,
            isIsolatedSupported = item.kind != SurfaceKind.THEME
        )
    }
}
