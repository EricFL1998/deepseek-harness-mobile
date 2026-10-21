package com.example.kimi.nativeui

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.deepseek.dsh.plugin.nativesdk.DshPluginBinder

/** Kimi 伴侣插件的入口服务。 */
class KimiPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(KimiNativePlugin())

    companion object {
        /** 装载器契约：App 把本类加载进它的进程后，经这里拿插件实例并触发注册。 */
        @JvmStatic
        fun plugin(): com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin = KimiNativePlugin()
    }
}
