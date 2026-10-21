package com.deepseek.dsh.plugin.nativehost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 已装原生伴侣插件的快照。
 *
 * @property pluginId 插件 id（`dsh.plugin.id`），与 JS 插件的 componentId 前缀配对。
 * @property packageName 伴侣 APK 的包名。
 * @property version 插件自身版本（descriptor.version 声明）。
 * @property versionCode APK 的 versionCode。
 * @property apiVersion 该伴侣编译所针对的 native-SDK ABI 版本。
 * @property minAppVersion 伴侣要求的最低 App 版本码。
 * @property signatureFingerprint 签名证书指纹（SHA-256），用于掉包检测。
 * @property status 当前状态；只有 READY 的会被加载。
 * @property serviceComponent 插件 Service 的组件名（bindService 用）。
 */
data class InstalledNativePlugin(
    val pluginId: String,
    val packageName: String,
    val version: Int,
    val versionCode: Int,
    val apiVersion: Int,
    val minAppVersion: Int,
    val signatureFingerprint: String,
    val status: Status,
    val serviceComponent: ComponentName,
    /**
     * 配对的 JS 插件安装地址（`dsh.plugin.jsPackage`：npm 包名或 GitHub 地址）。
     * 装 APK 时 App 读它连带装 JS 插件；缺省时兜底用 [pluginId] 当 npm 包名试装。
     */
    val jsPackage: String? = null,
    /** APK 的安装/更新时间（毫秒），用于插件商店的更新提醒。 */
    val lastUpdateTime: Long = 0L,
) {
    enum class Status { READY, ABI_TOO_NEW, APP_TOO_OLD, SIGNATURE_MISMATCH, DISABLED }
}

/**
 * 插件发现：扫描系统里声明了 `com.deepseek.dsh.action.PLUGIN_UI` 服务的伴侣 APK，
 * 解析其 meta-data，产出已装插件清单。只读 meta-data，此阶段不 bind/加载任何代码。
 *
 * 调用方（App）在启动时全量重扫一次，并监听 `PACKAGE_ADDED`/`PACKAGE_REMOVED`/
 * `PACKAGE_REPLACED` 广播做增量增删——卸载必须当帧摘除渲染位，不留悬垂引用。
 */
class NativePluginDiscovery(private val context: Context) {

    /**
     * 全量扫描一次系统里已装的原生伴侣插件。
     *
     * @param appVersionCode 当前 App 的版本码（ABI 门禁用）。
     * @param supportedApiVersion 当前 App 内 native-SDK 的 ABI 版本。
     * @param pinnedFingerprints 已钉死的 pluginId → 指纹（首次见由调用方钉死）。
     */
    fun scan(
        appVersionCode: Int,
        supportedApiVersion: Int,
        pinnedFingerprints: Map<String, String>,
    ): List<InstalledNativePlugin> {
        val pm = context.packageManager
        val intent = Intent(ACTION_PLUGIN_UI)
        val services = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        return services.mapNotNull { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
            val meta = serviceInfo.metaData ?: return@mapNotNull null

            val pluginId = meta.getString(META_ID)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val apiVersion = meta.getInt(META_API_VERSION, -1).takeIf { it >= 0 } ?: return@mapNotNull null
            val minAppVersion = meta.getInt(META_MIN_APP_VERSION, 0)
            // 配对的 JS 插件安装地址（甲：manifest 直声明）。缺省时由调用方查商店索引兜底（乙）。
            val jsPackage = meta.getString(META_JS_PACKAGE)?.takeIf { it.isNotBlank() }
            val packageName = serviceInfo.packageName
            val fingerprint = signingFingerprint(pm, packageName) ?: return@mapNotNull null

            val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
            val versionCode = pkgInfo?.longVersionCode?.toInt() ?: 0
            val lastUpdateTime = pkgInfo?.lastUpdateTime ?: 0L

            // ABI 门禁：任一不匹配 → 标记状态，绝不进入加载流程。
            val status = when {
                apiVersion > supportedApiVersion -> InstalledNativePlugin.Status.ABI_TOO_NEW
                minAppVersion > appVersionCode -> InstalledNativePlugin.Status.APP_TOO_OLD
                pinnedFingerprints[pluginId]?.let { it != fingerprint } == true ->
                    InstalledNativePlugin.Status.SIGNATURE_MISMATCH
                else -> InstalledNativePlugin.Status.READY
            }

            InstalledNativePlugin(
                pluginId = pluginId,
                packageName = packageName,
                version = meta.getInt(META_VERSION, 1),
                versionCode = versionCode,
                apiVersion = apiVersion,
                minAppVersion = minAppVersion,
                signatureFingerprint = fingerprint,
                status = status,
                serviceComponent = ComponentName(packageName, serviceInfo.name),
                jsPackage = jsPackage,
                lastUpdateTime = lastUpdateTime,
            )
        }
    }

    /** 读一个包的签名证书 SHA-256 指纹（小写无分隔）。 */
    private fun signingFingerprint(pm: PackageManager, packageName: String): String? = runCatching {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = info.signingInfo?.apkContentsSigners ?: return null
        val cert = signatures.firstOrNull()?.toByteArray() ?: return null
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(cert)
        digest.joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private companion object {
        const val ACTION_PLUGIN_UI = "com.deepseek.dsh.action.PLUGIN_UI"
        const val META_ID = "dsh.plugin.id"
        const val META_API_VERSION = "dsh.plugin.apiVersion"
        const val META_MIN_APP_VERSION = "dsh.plugin.minAppVersion"
        const val META_VERSION = "dsh.plugin.version"
        /** 配对的 JS 插件安装地址（npm 包名 / GitHub 地址）。 */
        const val META_JS_PACKAGE = "dsh.plugin.jsPackage"
    }
}
