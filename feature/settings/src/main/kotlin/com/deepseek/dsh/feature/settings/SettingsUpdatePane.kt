package com.deepseek.dsh.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.runtime.host.HostRuntimeHolder
import com.deepseek.dsh.runtime.host.RootfsVersion
import com.deepseek.dsh.runtime.host.UpdateAssessment
import com.deepseek.dsh.runtime.host.UpdateNeed
import com.deepseek.dsh.runtime.host.comparePluginVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新面板：APK、bridge 插件、rootfs 镜像三块，各自的更新性质不同。
 *
 * - **APK**：装了新版本才提示，走 GitHub Release 下载页。
 * - **bridge 插件**：必需更新。它是客户端的契约对端，Host 与 App 必须说同一个
 *   协议；但代价极小，所以在 rootfs 内就地安装，不重下镜像。
 * - **rootfs 镜像**：可选更新。镜像重建只为跟随 DSH 引擎；旧镜像能跑，
 *   重下几百 MB 不该被强加给用户。
 *
 * 三块都只在这里提示。启动流程不会因为任何一项而打断用户。
 */
@Composable
internal fun SettingsUpdatePane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by UpdateCenter.update.collectAsState()
    val checking by UpdateCenter.checking.collectAsState()
    val error by UpdateCenter.error.collectAsState()
    val apkVersion = UpdateCenter.currentVersion(context)
    val available = update?.takeIf { UpdateCenter.hasUpdate(context) }

    var assessment by remember { mutableStateOf(UpdateAssessment()) }
    var assessmentLoaded by remember { mutableStateOf(false) }
    var assessmentError by remember { mutableStateOf<String?>(null) }
    var pluginBusy by remember { mutableStateOf(false) }
    var pluginStatus by remember { mutableStateOf<String?>(null) }
    var pluginError by remember { mutableStateOf<String?>(null) }
    var rootfsBusy by remember { mutableStateOf(false) }
    var rootfsStatus by remember { mutableStateOf<String?>(null) }
    // bridge 插件更新弹窗：落后时主动提醒一次，用户可稍后。
    var showPluginUpdateDialog by remember { mutableStateOf(false) }

    // stringResource 只能在 @Composable 中调用，而下面这些文案还要在协程 / suspend 回调
    // 里更新状态时使用，因此在组合作用域内先读出来（带占位符的保留模板，用 format 填充）。
    val textUnknown = stringResource(R.string.agent_update_unknown)
    val textNotInstalled = stringResource(R.string.agent_update_not_installed)
    val textUnknownReason = stringResource(R.string.agent_update_unknown_reason)
    val errorRuntimeUnavailable = stringResource(R.string.agent_update_error_runtime_unavailable)
    val errorRuntimeNotInitialized =
        stringResource(R.string.agent_update_error_runtime_not_initialized)
    val errorReadVersionFailed = stringResource(R.string.agent_update_error_read_version_failed)
    val errorPluginLatestMissing = stringResource(R.string.agent_update_error_plugin_latest_missing)
    val errorPluginUpdateFailed = stringResource(R.string.agent_update_error_plugin_update_failed)
    val errorHostRestartFailedTpl =
        stringResource(R.string.agent_update_error_host_restart_failed)
    val errorPluginNotLoaded = stringResource(R.string.agent_update_error_plugin_not_loaded)
    val errorHostVersionMismatchTpl =
        stringResource(R.string.agent_update_error_host_version_mismatch)
    val errorCheckUpdateFailed = stringResource(R.string.agent_update_error_check_update_failed)
    val statusRestartingHost = stringResource(R.string.agent_update_status_restarting_host)
    val statusHostRestarted = stringResource(R.string.agent_update_status_host_restarted)
    val statusPluginPrepareUpdateTpl = stringResource(R.string.agent_update_plugin_prepare_update)
    val statusPluginInstalledRestartingTpl =
        stringResource(R.string.agent_update_plugin_installed_restarting)
    val statusPluginInstalledRestartNeededTpl =
        stringResource(R.string.agent_update_plugin_installed_restart_needed)
    val statusPluginWrittenTpl = stringResource(R.string.agent_update_plugin_written)
    val statusPluginUpdatedRestartedTpl =
        stringResource(R.string.agent_update_plugin_updated_restarted)
    val statusRootfsPrepareReinstall = stringResource(R.string.agent_update_rootfs_prepare_reinstall)
    val statusRootfsUpdatedStartHost =
        stringResource(R.string.agent_update_rootfs_updated_start_host)
    val statusRootfsUpdatedRestarted =
        stringResource(R.string.agent_update_rootfs_updated_restarted)
    val statusRootfsUpdatedHostFailedTpl =
        stringResource(R.string.agent_update_rootfs_updated_host_failed)
    val statusRootfsUpdateFailedTpl =
        stringResource(R.string.agent_update_error_rootfs_update_failed)

    /**
     * 重新读一次版本与更新结论。
     *
     * 任何一步失败都必须让界面离开「检查中」：查询涉及网络与 rootfs 文件系统，
     * 抛异常时把状态留在加载态会让整页永远转圈。
     */
    suspend fun refreshAssessment() {
        try {
            assessmentError = null
            val manager = HostRuntimeHolder.manager
            if (manager == null) {
                // 运行时尚未创建（例如首次启动还没走到初始化）：这不是错误，
                // 但必须说明，否则用户只看到一片「未知」。
                assessmentError = errorRuntimeNotInitialized
                return
            }
            assessment = manager.assessUpdates()
        } catch (error: Throwable) {
            assessmentError = error.message ?: errorReadVersionFailed
        } finally {
            assessmentLoaded = true
        }
    }

    LaunchedEffect(Unit) {
        // APK 更新在启动时已经查过一次；这里复用那次结果，避免每次打开面板都
        // 打一次 GitHub API（未授权接口每小时只有 60 次）。
        UpdateCenter.ensureChecked(context)
        refreshAssessment()
        // bridge 插件是协议对端，落后时用弹窗主动提醒；镜像更新不弹窗。
        if (assessment.pluginOutdated) showPluginUpdateDialog = true
    }

    /**
     * 等 App 重新绑定重启后的 Host，再读运行中的插件版本。
     *
     * 重启会换端口，客户端的 bridge 绑定由主界面异步重建；这里轮询到读得到为止，
     * 读不到就返回 null（由调用方报「Host 没加载插件」），绝不当成成功。
     */
    suspend fun awaitRunningPluginVersion(): String? {
        repeat(12) {
            val running = HostRuntimeHolder.runningBridgeVersionProvider
                ?.let { provider -> runCatching { provider() }.getOrNull() }
            if (running != null) return running
            delay(700)
        }
        return null
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    val installed = RootfsVersion.parse(assessment.installed)
    val packaged = RootfsVersion.parse(assessment.available)
    val pluginComparison = comparePluginVersions(assessment.installedPlugin, assessment.latestPlugin)
    // 镜像只在「新引擎」真的存在时提示：bridge 单独更新不需要重下几百 MB 的镜像。
    val engineUpdateAvailable = installed?.dsh != null &&
        packaged?.dsh != null &&
        installed.dsh != packaged.dsh

    /** 安装最新 bridge 插件并重启 Host；按钮与弹窗共用这一条路径。 */
    suspend fun updateBridgePlugin() {
        pluginBusy = true
        pluginError = null
        val manager = HostRuntimeHolder.manager
        val target = assessment.latestPlugin
        if (manager == null) {
            pluginError = errorRuntimeUnavailable
            pluginBusy = false
            return
        }
        if (target == null) {
            pluginError = errorPluginLatestMissing
            pluginBusy = false
            return
        }
        pluginStatus = statusPluginPrepareUpdateTpl.format(target)
        // 下载与解压都在 App 进程完成：Host 跑在 proot 里，解析不到 github.com。
        val result = manager.updateBridgePlugin(target) { _, step ->
            pluginStatus = step
        }
        result.fold(
            onSuccess = { version ->
                pluginStatus = statusPluginInstalledRestartingTpl.format(version)
                val restart = HostRuntimeHolder.restartHost
                val restartFailure = restart?.invoke()?.exceptionOrNull()
                if (restart == null) {
                    pluginStatus = statusPluginInstalledRestartNeededTpl.format(version)
                }
                // 装完必须以「运行中的插件版本」收尾：文件对了但 Host 没加载时，
                // 界面不能假装更新成功。
                delay(1_200)
                val running = awaitRunningPluginVersion()
                pluginStatus = statusPluginWrittenTpl.format(version)
                pluginError = when {
                    restartFailure != null ->
                        errorHostRestartFailedTpl.format(
                            restartFailure.message ?: textUnknownReason,
                        )
                    running == null -> errorPluginNotLoaded
                    running != version -> errorHostVersionMismatchTpl.format(running, version)
                    else -> {
                        pluginStatus = statusPluginUpdatedRestartedTpl.format(version)
                        null
                    }
                }
                refreshAssessment()
            },
            onFailure = { failure ->
                pluginError = failure.message ?: errorPluginUpdateFailed
            },
        )
        pluginBusy = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_update_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_update_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // ── 版本信息 ─────────────────────────────────────────────────────────
        item {
            UpdateCard {
                Text(
                    stringResource(R.string.agent_update_section_version_info),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                VersionItemRow(
                    stringResource(R.string.agent_update_label_apk_version),
                    "v$apkVersion",
                )
                VersionItemRow(
                    stringResource(R.string.agent_update_label_bridge_version),
                    assessment.installedPlugin?.let { "v$it" } ?: textUnknown,
                )
                VersionItemRow(
                    stringResource(R.string.agent_update_label_rootfs_version),
                    installed?.rootfs?.let { "v$it" }
                        ?: if (assessment.installed == null) textUnknown else textNotInstalled,
                )
                VersionItemRow(
                    stringResource(R.string.agent_update_label_engine_version),
                    installed?.dsh ?: textUnknown,
                )
                // 打包版本只在它与已装版本不同时才有信息量，避免噪声。
                packaged?.bridge?.takeIf { it != assessment.installedPlugin }?.let { packagedBridge ->
                    VersionItemRow(
                        stringResource(R.string.agent_update_label_packaged_plugin),
                        "v$packagedBridge",
                    )
                }
                assessmentError?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // ── bridge 插件：必需更新 ────────────────────────────────────────────
        item {
            UpdateCard {
                SectionTitle(
                    stringResource(R.string.agent_update_section_bridge_plugin),
                    badge = if (assessment.need == UpdateNeed.PLUGIN_REQUIRED) {
                        stringResource(R.string.agent_update_badge_required)
                    } else {
                        null
                    },
                    badgeUrgent = true,
                )
                when {
                    !assessmentLoaded -> LoadingRow(
                        stringResource(R.string.agent_update_loading_plugin_version),
                    )
                    assessment.need == UpdateNeed.PLUGIN_REQUIRED -> {
                        Text(
                            stringResource(R.string.agent_update_bridge_required_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(
                                R.string.agent_update_plugin_versions,
                                assessment.installedPlugin ?: textUnknown,
                                assessment.latestPlugin ?: textUnknown,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    assessment.latestPlugin == null -> Text(
                        stringResource(
                            R.string.agent_update_plugin_latest_unavailable,
                            assessment.installedPlugin ?: textUnknown,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    pluginComparison != null && pluginComparison > 0 -> Text(
                        stringResource(
                            R.string.agent_update_plugin_ahead,
                            assessment.installedPlugin ?: textUnknown,
                            assessment.latestPlugin ?: textUnknown,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        stringResource(
                            R.string.agent_update_plugin_up_to_date,
                            assessment.installedPlugin ?: textUnknown,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                pluginStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                pluginError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(12.dp))
                // 按钮排两行：三个按钮挤在一行时，屏幕窄的设备会把「重启 Host」
                // 压成一竖列（中文可逐字换行、英文可断词），看起来像坏了。
                // 第一行放两个等宽动作，第二行把重启 Host 整宽放开，文案永远单行。
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !pluginBusy && assessmentLoaded && assessment.canUpdatePlugin,
                        modifier = Modifier.weight(1f),
                        onClick = { scope.launch { showPluginUpdateDialog = false; updateBridgePlugin() } },
                    ) {
                        if (pluginBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(14.dp).height(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (pluginBusy) {
                                stringResource(R.string.agent_update_action_updating)
                            } else {
                                stringResource(R.string.agent_update_action_update_plugin)
                            },
                            maxLines = 1,
                        )
                    }
                        OutlinedButton(
                            enabled = !pluginBusy && !rootfsBusy,
                            modifier = Modifier.weight(1f),
                            onClick = { scope.launch { refreshAssessment() } },
                        ) {
                            Text(
                                stringResource(R.string.agent_update_action_recheck),
                                maxLines = 1,
                            )
                        }
                    }
                    OutlinedButton(
                        enabled = !pluginBusy && !rootfsBusy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                pluginBusy = true
                                pluginError = null
                                pluginStatus = statusRestartingHost
                                val restart = HostRuntimeHolder.restartHost
                                if (restart == null) {
                                    pluginError = errorRuntimeUnavailable
                                } else {
                                    val restarted = restart()
                                    if (restarted.isFailure) {
                                        pluginError = errorHostRestartFailedTpl.format(
                                            restarted.exceptionOrNull()?.message ?: textUnknownReason,
                                        )
                                    } else {
                                        pluginStatus = statusHostRestarted
                                    }
                                }
                                delay(1_200)
                                refreshAssessment()
                                pluginBusy = false
                            }
                        },
                    ) {
                        Text(
                            stringResource(R.string.agent_update_action_restart_host),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // ── rootfs 镜像：区块常驻；只有 DSH 引擎换代时才提示更新 ────────────────
        item {
            UpdateCard {
                SectionTitle(
                    stringResource(R.string.agent_update_section_rootfs),
                    badge = if (engineUpdateAvailable &&
                        assessment.need == UpdateNeed.ROOTFS_OPTIONAL
                    ) {
                        stringResource(R.string.agent_update_badge_optional)
                    } else {
                        null
                    },
                    badgeUrgent = false,
                )
                when {
                    !assessmentLoaded -> LoadingRow(
                        stringResource(R.string.agent_update_loading_rootfs_version),
                    )
                    engineUpdateAvailable && assessment.need == UpdateNeed.ROOTFS_OPTIONAL -> Text(
                        stringResource(R.string.agent_update_rootfs_optional_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        stringResource(R.string.agent_update_rootfs_up_to_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                assessment.available?.takeIf {
                    engineUpdateAvailable && assessment.need == UpdateNeed.ROOTFS_OPTIONAL
                }?.let { target ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(
                            R.string.agent_update_rootfs_versions,
                            assessment.installed ?: textNotInstalled,
                            target,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                rootfsStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (engineUpdateAvailable) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    enabled = !rootfsBusy && !pluginBusy && assessment.need == UpdateNeed.ROOTFS_OPTIONAL,
                    onClick = {
                        scope.launch {
                            rootfsBusy = true
                            rootfsStatus = statusRootfsPrepareReinstall
                            val manager = HostRuntimeHolder.manager
                            if (manager == null) {
                                rootfsStatus = errorRuntimeUnavailable
                                rootfsBusy = false
                                return@launch
                            }
                            // 镜像替换会重启 Host，因此先停掉再装。
                            withContext(Dispatchers.IO) { manager.stop() }
                            val result = manager.installRootfs { _, step -> rootfsStatus = step }
                            result.fold(
                                onSuccess = {
                                    // 新镜像内已经换了插件与引擎；装完把 Host 拉起来，
                                    // 否则整页会停在「已停止」，用户还得自己去运行页启动。
                                    val restart = HostRuntimeHolder.restartHost
                                    rootfsStatus = if (restart == null) {
                                        statusRootfsUpdatedStartHost
                                    } else {
                                        val restarted = restart()
                                        if (restarted.isSuccess) statusRootfsUpdatedRestarted
                                        else statusRootfsUpdatedHostFailedTpl.format(
                                            restarted.exceptionOrNull()?.message ?: textUnknownReason,
                                        )
                                    }
                                    rootfsBusy = false
                                    delay(1_200)
                                    refreshAssessment()
                                },
                                onFailure = { failure ->
                                    rootfsStatus = statusRootfsUpdateFailedTpl.format(failure.message)
                                },
                            )
                            rootfsBusy = false
                        }
                    },
                ) {
                    Text(
                        if (rootfsBusy) {
                            stringResource(R.string.agent_update_action_installing)
                        } else {
                            stringResource(R.string.agent_update_action_update_rootfs)
                        },
                    )
                }
                }
            }
        }

        // ── APK 自身 ─────────────────────────────────────────────────────────
        item {
            UpdateCard {
                SectionTitle(
                    stringResource(R.string.agent_update_section_apk),
                    badge = null,
                    badgeUrgent = false,
                )
                when {
                    checking -> LoadingRow(
                        stringResource(R.string.agent_update_loading_apk_update),
                    )
                    available != null -> {
                        Text(
                            stringResource(R.string.agent_update_apk_new_version, available.tag),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(available.name, style = MaterialTheme.typography.bodyMedium)
                        if (available.body.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                available.body.take(2000),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { openUrl(available.htmlUrl) }) {
                                Text(stringResource(R.string.agent_update_action_download))
                            }
                            OutlinedButton(onClick = { openUrl(DSH_RELEASES_PAGE) }) {
                                Text(stringResource(R.string.agent_update_action_all_versions))
                            }
                        }
                    }
                    error != null -> Text(
                        error ?: errorCheckUpdateFailed,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(
                        stringResource(R.string.agent_update_apk_up_to_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/$DSH_GITHUB_REPO") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.agent_update_link_client_project)) }
        }
        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/EricFL1998/deepseek-harness-mobile-rootfs") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.agent_update_link_rootfs_project)) }
        }
        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/EricFL1998/deepseek-harness-mobile-bridge-plugin") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.agent_update_link_bridge_project)) }
        }
        item {
            Text(
                stringResource(R.string.agent_update_source_footer, DSH_GITHUB_REPO),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    if (showPluginUpdateDialog) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { showPluginUpdateDialog = false },
            title = stringResource(R.string.agent_update_plugin_popup_title),
            closeLabel = stringResource(R.string.agent_update_plugin_popup_later),
            description = stringResource(R.string.agent_update_plugin_popup_desc),
            content = {
                Text(
                    stringResource(
                        R.string.agent_update_plugin_versions,
                        assessment.installedPlugin ?: textUnknown,
                        assessment.latestPlugin ?: textUnknown,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            },
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.agent_update_plugin_popup_later),
                    primary = false,
                    enabled = !pluginBusy,
                    onClick = { showPluginUpdateDialog = false },
                )
                Spacer(Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.agent_update_action_update_plugin),
                    primary = true,
                    enabled = !pluginBusy,
                    onClick = {
                        showPluginUpdateDialog = false
                        scope.launch { updateBridgePlugin() }
                    },
                )
            },
        )
    }
}

/** 统一的面板卡片外观。 */
@Composable
private fun UpdateCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/** 区块标题，可选一个右侧标记。 */
@Composable
private fun SectionTitle(title: String, badge: String?, badgeUrgent: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = if (badgeUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                fontWeight = if (badgeUrgent) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** 一行「检查中」。 */
@Composable
private fun LoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VersionItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
        )
    }
}
