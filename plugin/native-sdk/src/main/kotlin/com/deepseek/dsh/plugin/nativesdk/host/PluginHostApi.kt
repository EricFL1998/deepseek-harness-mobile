package com.deepseek.dsh.plugin.nativesdk.host

import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject

/**
 * 宿主能力桥：插件唯一摸得到的 App 表面。
 *
 * 设计纪律（与铁律一致）：
 * - **逻辑永远在 Host**：插件想做事，只能 [dispatchEvent] 让 Host 侧 JS 去做；
 * - **凭证不出 Host**：本接口没有网络、token、任意文件路径的口子；
 * - **能力声明制**：每项能力调用前宿主校验 `descriptor.capabilities`，未声明即拒。
 *
 * 实现由 App 提供（`PluginHostApiImpl`），插件只面向这个接口编程。
 */
interface PluginHostApi {
    /**
     * 回传一次用户意图给 Host 侧 JS。
     *
     * 走 bridge 的 `dispatch` op → Host 事件总线 `ui/<componentId>/<event>` →
     * 插件的监听器。插件处理后经 `client/refresh` 推回新 spec，本渲染器随之重组。
     *
     * @param componentId `<pluginId>#<slot 内 id>`，前缀必须等于插件 id。
     * @param event 事件名（如 `"start-oauth"` / `"refresh"` / `"logout"`）。
     * @param payload JSON 安全的参数对象。
     */
    fun dispatchEvent(componentId: String, event: String, payload: JsonObject)

    /**
     * 打开一个链接。域白名单内走 Custom Tab，其余经系统浏览器并需用户确认。
     * 需要能力 [com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities.OPEN_URL]。
     */
    fun openUrl(url: String)

    /**
     * 当前主题只读快照。深色模式 / 系统字体缩放变化时快照更新并触发重组。
     * 插件不许硬编码颜色/字号，全部从这里取。
     */
    fun currentTheme(): PluginTheme

    /**
     * 申请一项能力。
     *
     * @return 该能力已声明且用户未关闭时 true；否则 false（插件应据此降级，
     *   而不是假定能力可用）。
     */
    fun requestCapability(capability: String): Boolean

    /**
     * 本插件独立命名空间的键值存储。
     * 需要能力 [com.deepseek.dsh.plugin.nativesdk.contract.PluginCapabilities.STORAGE]。
     * 拿不到其他插件的存储。
     */
    fun storage(): PluginStorage
}

/**
 * 每插件独立命名空间的键值存储。只存展示相关的轻量状态（UI 偏好、上次的
 * 非敏感展示缓存）；**不许存凭证**——token/secret 永远留在 Host。
 */
interface PluginStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}
