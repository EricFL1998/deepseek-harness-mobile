package com.deepseek.dsh.plugin.nativesdk.contract

import androidx.compose.runtime.Composable
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import kotlinx.serialization.json.JsonObject

/**
 * 一个 UI 渲染器：把 Host 推来的 spec 画成 Compose。
 *
 * 渲染器是**纯前端**：不持有网络连接、不缓存凭证、不做业务逻辑——这些都留在
 * Host 侧 JS。它只消费 spec（JSON 快照，纯展示数据），把用户意图经
 * [PluginHostApi.dispatchEvent] 回传给 Host。
 *
 * 渲染器运行在 App 提供的宿主容器内（尺寸约束 + 错误围栏）：抛异常只会让本组件
 * 降级为占位块，不影响 App 与其他插件。
 */
interface ComponentRenderer {
    /**
     * 本渲染器能画的 spec 类型集合（如 `setOf("kimi.account-card")`）。
     * App 在命中时才走原生渲染，未命中一律降级到插件的完整 Web 界面。
     */
    val supportedTypes: Set<String>

    /**
     * 画一个 spec。
     *
     * @param spec Host 经 `client/refresh` 推来的 JSON 快照；只含展示数据，无凭证。
     * @param host 宿主能力桥——插件唯一摸得到的 App 表面。
     */
    @Composable
    fun Render(spec: JsonObject, host: PluginHostApi)
}

/**
 * 插件入口：App 经伴侣 APK 的 `Service` 拿到它。
 *
 * 实现要点：在 APK 的 Service `onBind` 里返回 SDK 的 `DshPluginBinder` 包住你的
 * 实现即可；清单声明见开发者文档。
 */
interface DshUiPlugin {
    /** 插件身份与能力声明。 */
    val descriptor: PluginDescriptor

    /** 创建该插件的渲染器。 */
    fun createRenderer(): ComponentRenderer
}
