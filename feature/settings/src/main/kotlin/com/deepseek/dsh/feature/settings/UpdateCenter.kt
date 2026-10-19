package com.deepseek.dsh.feature.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal const val DSH_GITHUB_REPO = "EricFL1998/deepseek-harness-mobile"
internal const val DSH_RELEASES_PAGE = "https://github.com/$DSH_GITHUB_REPO/releases"
private const val DSH_RELEASES_API = "https://api.github.com/repos/$DSH_GITHUB_REPO/releases?per_page=20"

data class DshUpdateInfo(
    val tag: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
)

/**
 * 官方更新模型：启动后静默检查 GitHub Release；只有检测到新版本时 UI 才显示“新版本”。
 *
 * 这里只管 **APK 自身**的更新。bridge 插件与 rootfs 的更新由
 * [com.deepseek.dsh.runtime.host.HostRuntimeManager.assessUpdates] 判断：
 * 插件必需、镜像可选，两者都只在设置页呈现，绝不在启动时打断用户。
 */
object UpdateCenter {
    private val _update = MutableStateFlow<DshUpdateInfo?>(null)
    val update: StateFlow<DshUpdateInfo?> = _update.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile
    private var checked = false

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "1.0.0" }

    fun hasUpdate(context: Context): Boolean {
        val latest = _update.value ?: return false
        // 必须与带 -mobile 的完整版本区分，不能把官方版本当成移动端新版本。
        return latest.tag.isNotBlank() && latest.tag != currentVersion(context)
    }

    suspend fun check(context: Context) {
        if (_checking.value) return
        _checking.value = true
        _error.value = null
        try {
            val info = withContext(Dispatchers.IO) {
                val connection = (URL(DSH_RELEASES_API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "dsh-android")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        error("GitHub 返回 HTTP ${connection.responseCode}")
                    }
                    val root = org.json.JSONArray(connection.inputStream.bufferedReader().readText())
                    // 只把 Android 应用自身的 release 当作更新来源；rootfs-* 等资产版本忽略。
                    val release = (0 until root.length()).mapNotNull { index ->
                        root.optJSONObject(index)?.takeIf { item ->
                            val tag = item.optString("tag_name")
                            tag.startsWith("android-v") || tag.startsWith("app-v")
                        }
                    }.firstOrNull() ?: return@withContext null
                    val tag = release.optString("tag_name")
                        .removePrefix("android-v").removePrefix("app-v")
                    if (tag.isBlank()) error("Release 缺少版本号")
                    DshUpdateInfo(
                        tag = tag,
                        name = release.optString("name").ifBlank { release.optString("tag_name") },
                        body = release.optString("body"),
                        htmlUrl = release.optString("html_url", DSH_RELEASES_PAGE),
                    )
                } finally {
                    connection.disconnect()
                }
            }
            checked = true
            if (info != null) _update.value = info
        } catch (t: Throwable) {
            _error.value = t.message ?: "检查更新失败"
        } finally {
            _checking.value = false
        }
    }

    suspend fun ensureChecked(context: Context) {
        if (!checked) check(context)
    }
}




