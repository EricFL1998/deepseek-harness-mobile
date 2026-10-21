package com.deepseek.dsh.plugin.nativesdk

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin

/**
 * 插件 Service 的绑定基类：伴侣 APK 的 Service 在 `onBind` 里返回它，App 据此拿到
 * 插件实例。
 *
 * 用法（插件 APK 侧）：
 *
 * ```kotlin
 * class KimiPluginService : Service() {
 *     override fun onBind(intent: Intent): IBinder = DshPluginBinder(KimiOAuthPlugin())
 * }
 * ```
 *
 * 配套清单（AndroidManifest.xml）见开发者文档：固定 action
 * `com.deepseek.dsh.action.PLUGIN_UI` + 签名级权限 `com.deepseek.dsh.permission.BIND_PLUGIN`，
 * 防止其他应用绑定你的服务。
 */
class DshPluginBinder(
    /** 插件实例。 */
    val plugin: DshUiPlugin,
) : Binder()
