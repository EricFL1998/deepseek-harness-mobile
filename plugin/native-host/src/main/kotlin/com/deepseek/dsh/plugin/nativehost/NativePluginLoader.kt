package com.deepseek.dsh.plugin.nativehost

import android.content.Context
import com.deepseek.dsh.plugin.nativesdk.contract.DshUiPlugin

/**
 * 伴侣渲染器装载器：把伴侣 APK 的插件类加载进 App 进程。
 *
 * 伴侣 APK 是独立进程；它 `init` 块里的 `SlotRegistry.register(...)` 只在它自己的
 * 进程生效。要在 App 的落点容器里渲染，必须把伴侣的类加载进 **App 进程**——用
 * `createPackageContext` 拿到伴侣的 ClassLoader，反射实例化它的 Service 持有的
 * 插件类，让它在 App 进程里跑一遍注册。
 *
 * 安全前提：只装载已通过 ABI 门禁 + 验签的 READY 插件。
 */
class NativePluginLoader(private val context: Context) {

    /**
     * 把一个 READY 状态的伴侣插件装载进 App 进程。
     *
     * 找到它的入口 Service 类名，用伴侣的 ClassLoader 加载并实例化其 [DshUiPlugin]，
     * 触发它的 `init` 块在 App 进程里注册落点贡献/工具渲染器/页面。
     *
     * @return 装载成功的插件实例；失败返回 null（该插件降级 Web 界面）。
     */
    /**
     * 同一个插件在进程内只加载一次。
     *
     * 每次回前台/重扫都会重新 `loader.load()`；DexClassLoader 会生成新的类加载器，
     * 而旧实例在 SlotRegistry 里的贡献不会消失。新旧两个类加载器各自有一套
     * `DockData` 等同名类，插件自己的 Render 里 `as DockData` 就会抛
     * ClassCastException（订阅插件的 dock 崩溃）。缓存实例后重扫直接复用，
     * 不重复注册贡献；插件升级后由 App 进程重启生效。
     */
    private companion object {
        /** 进程级缓存：MainAppHost 每次重扫都会 new NativePluginLoader，缓存不能放在实例上。 */
        val loadedPlugins = java.util.concurrent.ConcurrentHashMap<String, DshUiPlugin>()
    }

    fun load(plugin: InstalledNativePlugin): DshUiPlugin? {
        loadedPlugins[plugin.pluginId]?.let { return it }
        if (plugin.status != InstalledNativePlugin.Status.READY) return null
        return try {
            // The companion packages the SDK for compilation, but at runtime the host
            // must own the single copy of DshUiPlugin, PageRegistry and Compose. A
            // package context has a separate SDK class identity, so `instanceof` would
            // reject an otherwise valid companion. Parent-first loading shares the
            // gated host ABI while the child loader supplies only the companion's code.
            val pluginContext = context.createPackageContext(
                plugin.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
            // `createPackageContext` already builds a ClassLoader over the installed
            // APK. Prefer it: a separate DexClassLoader over sourceDir can fail with
            // "Failed to find entry 'classes.dex'" for multi-dex / v4-signed APKs,
            // which silently drops every slot contribution of the companion.
            val viaPackage = runCatching {
                loadViaService(pluginContext.classLoader, plugin)
            }.getOrNull()
            if (viaPackage != null) {
                loadedPlugins[plugin.pluginId] = viaPackage
                return viaPackage
            }
            val appInfo = pluginContext.applicationInfo
            val dexPath = (listOf(appInfo.sourceDir) + appInfo.splitSourceDirs.orEmpty())
                .joinToString(java.io.File.pathSeparator)
            val classLoader = dalvik.system.DexClassLoader(
                dexPath,
                null,
                appInfo.nativeLibraryDir,
                context.classLoader,
            )
            // 约定：插件入口类是 `<service 类全名去掉 Service 后缀>` 不一定可靠；
            // 直接读 service 类名，找它同包里实现 DshUiPlugin 的类太绕。改为约定：
            // 插件在 service 类里暴露一个静态 `plugin()` 工厂，或 service 名推 plugin 类名。
            // 这里采用「service 类名 + Plugin 后缀约定」之外的稳妥做法：反射 service 类，
            // 调它的伴生/静态方法。最稳妥：插件按契约提供一个入口类名 meta-data。
            val viaDex = loadViaService(classLoader, plugin)
            viaDex
        } catch (error: Throwable) {
            android.util.Log.w("NativePluginLoader", "failed to load ${plugin.packageName}", error)
            null
        }
    }

    /**
     * 经伴侣的 Service 类拿到插件实例。
     *
     * 约定（写进 SDK 文档）：伴侣的 Service 类提供一个 `@JvmStatic fun plugin(): DshUiPlugin`，
     * 或者 Service 类本身的名约定 `<X>PluginService` 对应插件类 `<X>Plugin`。两种都试。
     */
    private fun loadViaService(classLoader: ClassLoader, plugin: InstalledNativePlugin): DshUiPlugin? {
        val serviceClassName = plugin.serviceComponent.className
        // ① 先试 service 类的静态 plugin() 工厂。
        runCatching {
            val serviceClass = classLoader.loadClass(serviceClassName)
            val factory = serviceClass.getMethod("plugin").apply { isAccessible = true }
            val instance = factory.invoke(null)
            if (instance is DshUiPlugin) return instance
        }
        // ② 退回类名约定：…PluginService → …Plugin。
        val pluginClassName = serviceClassName.removeSuffix("Service")
        if (pluginClassName != serviceClassName) {
            runCatching {
                val pluginClass = classLoader.loadClass(pluginClassName)
                val ctor = pluginClass.getDeclaredConstructor().apply { isAccessible = true }
                val instance = ctor.newInstance()
                if (instance is DshUiPlugin) return instance
            }
        }
        return null
    }
}
