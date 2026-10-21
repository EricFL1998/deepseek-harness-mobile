package com.example.dshgenui.nativeui

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.deepseek.dsh.plugin.nativesdk.DshPluginBinder

/** GenUI 伴侣插件的入口服务：App 绑定它拿到插件实例。 */
class GenuiPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(GenuiNativePlugin())

    companion object {
        /** 装载器契约：App 把本类加载进它的进程后，经这里拿插件实例并触发注册。 */
        @JvmStatic
        fun plugin(): com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin = GenuiNativePlugin()
    }
}
