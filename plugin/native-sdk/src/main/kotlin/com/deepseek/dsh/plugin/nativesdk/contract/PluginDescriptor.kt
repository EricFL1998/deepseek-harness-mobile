package com.deepseek.dsh.plugin.nativesdk.contract

/**
 * 插件身份与能力声明。
 *
 * 与插件 APK 的 `AndroidManifest` 四项 meta-data 一一对应；App 据此配对
 * JS 插件（`id` 须与 JS 侧 componentId 前缀一致）、做 ABI 门禁与能力白名单。
 *
 * @property id 插件唯一 id，发布后不可更改（签名钉死与商店身份的锚）。
 * @property name 展示名（设置页/商店页用）。
 * @property version 插件自身版本，每次发布递增。
 * @property capabilities 声明的能力集合（见 `PluginCapabilities`）；未声明的能力
 *   调用即被宿主拒绝。
 */
data class PluginDescriptor(
    val id: String,
    val name: String,
    val version: Int,
    val capabilities: Set<String>,
)

/** 能力白名单的常量表；`descriptor.capabilities` 只能从这里取。 */
object PluginCapabilities {
    /** 打开链接（域白名单校验后走 Custom Tab / 系统浏览器）。 */
    const val OPEN_URL = "openUrl"

    /** 每插件独立命名空间的键值存储。 */
    const val STORAGE = "storage"

    /** 经壳层前台服务发系统通知（turn 完成/错误提醒）。 */
    const val NOTIFY = "notify"

    /** 系统分享面板。 */
    const val SHARE = "share"

    /** 剪贴板（只写）。 */
    const val CLIPBOARD = "clipboard"

    /** SAF 授权的工作区目录内读文件（路径前缀校验）。 */
    const val WORKSPACE = "workspace"

    /** 注册并渲染 tool call 卡片/动作。 */
    const val TOOL_RENDER = "toolRender"

    /** 注册设置 section / 插件页详情入口。 */
    const val PAGE_ENTRY = "pageEntry"
}
