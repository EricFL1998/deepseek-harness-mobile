package com.deepseek.dsh.plugin.surface

import com.deepseek.dsh.core.model.SurfaceCatalog
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.core.model.SurfaceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * v4.0 设计文档 §7.4 & §7.5:
 * Surface 目录中心与原生放置决策
 */
enum class PlacementDestination {
    SETTING_SHEET,    // 设置页 (Sheet)
    SIDEBAR_PANEL,    // 侧栏面板
    COMPOSER_DOCK,    // 输入框菜单
    PLUGIN_OVERLAY,   // 插件浮层 (悬浮/收起)
    MESSAGE_ACTION,   // 消息长按动作
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

    fun resolvePlacement(item: SurfaceItem): SurfacePlacement {
        val dest = when (item.kind) {
            SurfaceKind.PAGE -> PlacementDestination.SETTING_SHEET
            SurfaceKind.SIDEBAR -> PlacementDestination.SIDEBAR_PANEL
            SurfaceKind.DOCK -> PlacementDestination.COMPOSER_DOCK
            SurfaceKind.OVERLAY -> PlacementDestination.PLUGIN_OVERLAY
            SurfaceKind.MESSAGE_ACTION -> PlacementDestination.MESSAGE_ACTION
            else -> PlacementDestination.WEB_ISLAND_FULL
        }
        return SurfacePlacement(
            item = item,
            destination = dest,
            isIsolatedSupported = item.kind != SurfaceKind.THEME
        )
    }
}
