package com.deepseek.dsh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.deepseek.dsh.core.data.DshRepository
import com.deepseek.dsh.core.data.DefaultSettingsRepository
import com.deepseek.dsh.core.model.AppSettings
import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.PluginSettingsDocument
import com.deepseek.dsh.core.model.PluginSettingsPage
import com.deepseek.dsh.core.model.PluginSlotEntry
import com.deepseek.dsh.feature.settings.SettingsRootScreen
import com.deepseek.dsh.feature.settings.SettingsSectionTab
import com.deepseek.dsh.runtime.host.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * 「设置」页的宿主。
 *
 * 这段页面接线（以及模型 provider 的读写回调）从 [MainAppHost] 里搬出来：后者是
 * 一个体积接近 ART 校验上限的巨型 Composable，任何新逻辑都应落在独立文件里。
 */
@Composable
internal fun AppSettingsPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    initialSection: SettingsSectionTab?,
    repository: DshRepository,
    settingsRepository: DefaultSettingsRepository,
    activeHostId: String,
    scope: CoroutineScope,
    availableModels: List<String>,
    modelProvidersRaw: String?,
    onProvidersRaw: (String?) -> Unit,
    modelSettingsRaw: String?,
    onSettingsRaw: (String?) -> Unit,
    modelCredentialsRaw: String?,
    onCredentialsRaw: (String?) -> Unit,
    routableProviders: List<String>,
    onRoutableProviders: (List<String>) -> Unit,
    builtInPlugins: List<BuiltInPluginInfo>,
    pluginSlotEntries: List<PluginSlotEntry>,
    pluginSettingsPages: List<PluginSettingsPage>,
    runtimeStatus: RuntimeStatus?,
    hostPort: Int,
    hostToken: String?,
    hostPortProvider: () -> Int,
    onStartRuntime: () -> Unit,
    onStopRuntime: () -> Unit,
    onOpenSettingsDocument: () -> Unit,
    accountName: String,
    accountBalance: String,
    accountPhone: String,
    isSignedIn: Boolean,
    avatarUrl: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onManageAccount: () -> Unit,
    onCreateBackup: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onCreateAgentPreset: () -> Unit,
    onClose: () -> Unit,
    pluginComponentRefresh: SharedFlow<String>?,
    pluginRpc: (suspend (String, JsonObject) -> Result<String>)?,
    loadPluginSettings: suspend (String) -> PluginSettingsDocument?,
    savePluginSettings: suspend (String, JsonObject, Int) -> Boolean,
) {
    // 打开设置页时补一次拉取：用户不一定等得到定时轮询的下一拍。
    LaunchedEffect(Unit) {
        runCatching { repository.refreshPluginSettingsPages() }
    }
    val (languageTag, setLanguage) = rememberAppLanguage(repository, activeHostId, scope)
    SettingsRootScreen(
        settings = settings,
        initialSection = initialSection,
        languageTag = languageTag,
        onLanguageChange = setLanguage,
        onUpdateSettings = { updated ->
            onSettingsChange(updated)
            scope.launch {
                settingsRepository.updateSettings { updated }
            }
        },
        availableModels = availableModels,
        modelProvidersRaw = modelProvidersRaw,
        modelSettingsRaw = modelSettingsRaw,
        modelCredentialsRaw = modelCredentialsRaw,
        routableProviders = routableProviders,
        onRefreshModelProviders = {
            refreshModelProviderData(
                scope = scope,
                repository = repository,
                hostId = activeHostId,
                onProviderIds = onRoutableProviders,
                onSettingsRaw = onSettingsRaw,
                onProvidersRaw = onProvidersRaw,
                onCredentialsRaw = onCredentialsRaw,
            )
        },
        onCreateAgentPreset = onCreateAgentPreset,
        onSaveModelProvider = { provider, key, baseUrl, models ->
            saveModelProvider(
                scope = scope,
                repository = repository,
                hostId = activeHostId,
                provider = provider,
                key = key,
                baseUrl = baseUrl,
                models = models,
                onSettingsRaw = onSettingsRaw,
                onCredentialsRaw = onCredentialsRaw,
            )
        },
        builtInPlugins = builtInPlugins,
        bridgeRegistry = repository.bridge(),
        pluginSlotEntries = pluginSlotEntries,
        pluginSettingsPages = pluginSettingsPages,
        pluginComponentRefresh = pluginComponentRefresh,
        hostPort = hostPort,
        hostToken = hostToken,
        hostPortProvider = hostPortProvider,
        pluginRpc = pluginRpc,
        loadPluginSettings = loadPluginSettings,
        savePluginSettings = savePluginSettings,
        runtimeStatus = runtimeStatus,
        onStartRuntime = onStartRuntime,
        onStopRuntime = onStopRuntime,
        onOpenSettingsDocument = onOpenSettingsDocument,
        accountName = accountName,
        accountBalance = accountBalance,
        accountPhone = accountPhone,
        isSignedIn = isSignedIn,
        avatarUrl = avatarUrl,
        onSignIn = onSignIn,
        onSignOut = onSignOut,
        onManageAccount = onManageAccount,
        onCreateBackup = onCreateBackup,
        onExportDiagnostics = onExportDiagnostics,
        onClose = onClose,
    )
}
