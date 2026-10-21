package com.dsh.mobile.dsh_plugin_demo

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.deepseek.dsh.plugin.nativesdk.DshPluginBinder
import com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin

/** Demo 伴侣 APK 的入口服务（纯演示）。 */
class DemoPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(DemoNativePlugin())

    companion object {
        @JvmStatic
        fun plugin(): DshUiPlugin = DemoNativePlugin()
    }
}
