package com.deepseek.dsh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.deepseek.dsh.core.data.DshRepository
import com.deepseek.dsh.feature.settings.ModelProviderUi
import com.deepseek.dsh.feature.settings.parseModelProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一次模型 provider 数据刷新：provider 目录、设置文档、凭据状态。
 *
 * 放在 MainAppHost 之外，避免把这段非组合代码算进那个已经贴着 ART 校验上限的
 * 巨型 Composable。
 */
internal fun refreshModelProviderData(
    scope: CoroutineScope,
    repository: DshRepository,
    hostId: String,
    onProviderIds: (List<String>) -> Unit,
    onSettingsRaw: (String?) -> Unit,
    onProvidersRaw: (String?) -> Unit,
    onCredentialsRaw: (String?) -> Unit,
) {
    scope.launch {
        val registeredRaw = repository.rpcRaw(hostId, "llm/listProviders", "{}").getOrNull()
        onProviderIds(
            runCatching {
                val root = kotlinx.serialization.json.Json.parseToJsonElement(registeredRaw.orEmpty())
                val value = ((root as? kotlinx.serialization.json.JsonObject)
                    ?.get("result") as? kotlinx.serialization.json.JsonObject)?.get("value")
                (value as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { item ->
                        ((item as? kotlinx.serialization.json.JsonObject)
                            ?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
                    .orEmpty()
            }.getOrDefault(emptyList()),
        )
        val settingsRaw = repository.rpcRaw(hostId, "settings/describe", "{}").getOrNull()
        val providersRaw = repository.rpcRaw(hostId, "llm/listConfigurableProviders", "{}").getOrNull()
        onSettingsRaw(settingsRaw)
        onProvidersRaw(providersRaw)
        val providerRefs = parseModelProviders(
            providersRaw,
            settingsRaw,
            null,
            repository.officialSettingsNamespaces(),
        ).map { it.keyRef }.distinct()
        val refsJson = providerRefs.joinToString(prefix = "{\"refs\":[", postfix = "]}") { "\"$it\"" }
        onCredentialsRaw(repository.rpcRaw(hostId, "credentials/describe", refsJson).getOrNull())
    }
}

/** 保存一个模型 provider：凭据、设置字段（apiKeyEnv/api/baseURL/models）。 */
internal fun saveModelProvider(
    scope: CoroutineScope,
    repository: DshRepository,
    hostId: String,
    provider: ModelProviderUi,
    key: String,
    baseUrl: String,
    models: List<String>,
    onSettingsRaw: (String?) -> Unit,
    onCredentialsRaw: (String?) -> Unit,
) {
    scope.launch {
        if (key.isNotBlank()) {
            val credArgs = JSONObject().put("ref", provider.keyRef).put("value", key).toString()
            repository.rpcRaw(hostId, "credentials/set", credArgs)
        }
        if (provider.settingsNs.isNotBlank()) {
            val ops = JSONArray()
            if (key.isNotBlank()) {
                ops.put(
                    JSONObject()
                        .put("op", "set")
                        .put("path", JSONArray(provider.settingsPath + "apiKeyEnv"))
                        .put("value", provider.keyRef),
                )
            }
            // 非适配器自带的路由必须声明线协议，否则 Host 会以 settings/rejected
            // 拒绝整个 provider。
            if (!provider.declared) {
                ops.put(
                    JSONObject()
                        .put("op", "set")
                        .put("path", JSONArray(provider.settingsPath + "api"))
                        .put("value", wireProtocolFor(provider.id)),
                )
            }
            if (baseUrl.isNotBlank()) {
                ops.put(
                    JSONObject()
                        .put("op", "set")
                        .put("path", JSONArray(provider.settingsPath + "baseURL"))
                        .put("value", baseUrl),
                )
            }
            if (models.isNotEmpty()) {
                val modelArray = JSONArray()
                models.forEach { model ->
                    modelArray.put(JSONObject().put("id", model).put("name", model))
                }
                ops.put(
                    JSONObject()
                        .put("op", "set")
                        .put("path", JSONArray(provider.settingsPath + "models"))
                        .put("value", modelArray),
                )
            }
            val args = JSONObject()
                .put("ns", provider.settingsNs)
                .put("ops", ops)
                .put("expectedRevision", JSONObject.NULL)
                .toString()
            repository.rpcRaw(hostId, "settings/mutate", args)
        }
        onSettingsRaw(repository.rpcRaw(hostId, "settings/describe", "{}").getOrNull())
        val refsJson = "{\"refs\":[\"${provider.keyRef}\"]}"
        onCredentialsRaw(repository.rpcRaw(hostId, "credentials/describe", refsJson).getOrNull())
        repository.refreshModelCatalog(hostId)
    }
}

/**
 * 界面语言控制器：选择值 + 切换动作（写 per-app locale 并同步 Host）。
 * @param hostId 当前 Host；为空时只切本地界面语言。
 */
@Composable
internal fun rememberAppLanguage(
    repository: DshRepository,
    hostId: String,
    scope: CoroutineScope,
): Pair<String, (String) -> Unit> {
    val context = LocalContext.current
    var selection by remember { mutableStateOf(AppLanguage.selection(context)) }
    val change: (String) -> Unit = { next ->
        selection = next
        AppLanguage.apply(context, next)
        scope.launch {
            repository.syncHostLocale(hostId, AppLanguage.effectiveTag(context))
        }
    }
    return selection to change
}

/**
 * 「添加插件」的完整安装流程：Host 安装 → App 兜底安装 → 重启 Host → 刷新目录。
 *
 * 从 PluginsScreen 的 `onInstallPlugin` 回调里搬出来：落盘的每一步进度都通过
 * [onProgress] 回传，状态刷新通过回调交给调用方。
 */
internal suspend fun installPluginWithProgress(
    context: android.content.Context,
    repository: DshRepository,
    hostId: String,
    pkgName: String,
    installer: com.deepseek.dsh.runtime.host.PluginBundleInstaller,
    onProgress: (Float, String) -> Unit,
    onManagedPackages: (Set<String>) -> Unit,
    onNativeCompanion: (com.deepseek.dsh.core.bridge.BridgeNativeCompanion?) -> Unit,
): Result<String> {
    // 安装耗时以秒计：先报一个「已受理」，再让安装器把每一步透传给对话框。
    onProgress(0.02f, context.getString(R.string.plugins_install_preparing))
    val hostRes = kotlinx.coroutines.coroutineScope {
        val progressJob = launch {
            repository.installProgressFlow.collect { progress ->
                if (progress == null) return@collect
                val label = when (progress.phase) {
                    "installing" -> if (progress.total > 1) {
                        context.getString(
                            R.string.plugins_install_state_installing,
                            progress.registry.orEmpty(),
                            progress.index,
                            progress.total,
                        )
                    } else {
                        context.getString(R.string.plugins_install_progress_title)
                    }
                    "applying" -> context.getString(R.string.plugins_install_state_applying)
                    "cancelling" -> context.getString(R.string.plugins_install_state_cancelling)
                    else -> context.getString(R.string.plugins_install_progress_title)
                }
                val fraction = 0.1f + 0.45f * progress.index.toFloat() /
                    progress.total.coerceAtLeast(1)
                onProgress(fraction.coerceIn(0.05f, 0.6f), label)
            }
        }
        try {
            repository.installPlugin(hostId, pkgName)
        } finally {
            progressJob.cancel()
        }
    }
    // Host 内 pnpm 依赖 proot 的 rename/unlink/mkdtemp，这台设备上全部返回 ENOSYS。
    // 失败后改用 App 自己的文件与网络能力完成同一件事，再重启 Host 加载。
    val usedFallback = hostRes.isFailure
    val result = if (hostRes.isSuccess) {
        onProgress(0.6f, context.getString(R.string.plugins_install_progress_title))
        Result.success(pkgName)
    } else {
        onProgress(0.05f, context.getString(R.string.plugins_install_fallback))
        installer.install(pkgName) { fraction, step ->
            onProgress(0.05f + fraction * 0.75f, step)
        }
    }
    return result.fold(
        onSuccess = { installed ->
            var restartFailure: Throwable? = null
            if (usedFallback) {
                // 文件已就位，重启 Host 才会让新插件进入 Loader。重启失败不能当成
                // 安装成功：界面会显示「已装」，但 Host 里并没有加载它。
                onProgress(0.85f, context.getString(R.string.plugins_install_restarting))
                restartFailure = com.deepseek.dsh.runtime.host.HostRuntimeHolder.restartHost
                    ?.invoke()
                    ?.exceptionOrNull()
                kotlinx.coroutines.delay(1_200)
            }
            onManagedPackages(runCatching { installer.managedPackages() }.getOrDefault(emptySet()))
            repository.refreshPluginInventory(hostId)
            repository.refreshBridgePluginCatalog()
            repository.refreshPluginSettingsPages()
            // 反向联动：这个 JS 插件有原生伴侣 APK 的话，提示可装。
            runCatching {
                val pkg = installed.substringAfterLast('/').substringBefore('@').ifBlank { installed }
                val companion = repository.bridge()?.nativeCompanionFor(pkg)
                if (companion is com.deepseek.dsh.core.bridge.BridgeResult.Success &&
                    companion.value != null
                ) {
                    onNativeCompanion(companion.value)
                }
            }
            val failure = restartFailure
            if (failure != null) {
                Result.failure(
                    IllegalStateException(
                        context.getString(
                            R.string.plugins_install_restart_failed,
                            failure.message ?: "",
                        ),
                    ),
                )
            } else {
                onProgress(1f, context.getString(R.string.plugins_install_success, installed))
                Result.success(installed)
            }
        },
        onFailure = { error ->
            Result.failure(
                IllegalStateException(
                    error.message ?: context.getString(R.string.plugin_install_failed),
                ),
            )
        },
    )
}
