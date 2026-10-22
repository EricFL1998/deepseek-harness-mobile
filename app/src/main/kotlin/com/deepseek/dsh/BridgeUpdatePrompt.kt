package com.deepseek.dsh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.feature.settings.R as SettingsR
import com.deepseek.dsh.runtime.host.HostRuntimeHolder
import com.deepseek.dsh.runtime.host.UpdateAssessment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 启动时的 bridge 插件更新弹窗。
 *
 * 只有 bridge 插件落后才弹：它是客户端与 Host 的协议对端，版本错位会直接
 * 影响所有 bridge 能力。rootfs 镜像的「可选」更新只出现在设置页，不在这里
 * 打扰用户。
 */
@Composable
internal fun BridgeUpdatePrompt() {
    val scope = rememberCoroutineScope()
    var assessment by remember { mutableStateOf<UpdateAssessment?>(null) }
    var dismissedVersion by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // 等运行时创建并读完版本；最多等 2 分钟，读不到就安静退出。
        repeat(24) {
            val manager = HostRuntimeHolder.manager
            if (manager != null) {
                val result = runCatching { manager.assessUpdates() }.getOrNull()
                if (result != null) {
                    assessment = result
                    return@LaunchedEffect
                }
            }
            delay(5_000)
        }
    }

    val current = assessment ?: return
    if (!current.pluginOutdated) return
    if (dismissedVersion == current.latestPlugin) return

    com.deepseek.dsh.core.designsystem.DshDialog(
        onDismiss = { dismissedVersion = current.latestPlugin },
        title = stringResource(SettingsR.string.agent_update_plugin_popup_title),
        closeLabel = stringResource(SettingsR.string.agent_update_plugin_popup_later),
        description = stringResource(SettingsR.string.agent_update_plugin_popup_desc),
        content = {
            Text(
                stringResource(
                    SettingsR.string.agent_update_plugin_versions,
                    current.installedPlugin ?: "?",
                    current.latestPlugin ?: "?",
                ),
            )
            status?.let {
                Spacer(androidx.compose.ui.Modifier.height(8.dp))
                Text(it)
            }
            error?.let {
                Spacer(androidx.compose.ui.Modifier.height(8.dp))
                Text(it)
            }
        },
        footer = {
            com.deepseek.dsh.core.designsystem.DshDialogButton(
                text = stringResource(SettingsR.string.agent_update_plugin_popup_later),
                primary = false,
                enabled = !busy,
                onClick = { dismissedVersion = current.latestPlugin },
            )
            Spacer(androidx.compose.ui.Modifier.width(8.dp))
            com.deepseek.dsh.core.designsystem.DshDialogButton(
                text = stringResource(SettingsR.string.agent_update_action_update_plugin),
                primary = true,
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        val manager = HostRuntimeHolder.manager
                        val target = current.latestPlugin
                        if (manager == null || target == null) {
                            error = "Host is not ready"
                            busy = false
                            return@launch
                        }
                        val result = manager.updateBridgePlugin(target) { _, step -> status = step }
                        result.fold(
                            onSuccess = { version ->
                                status = version
                                HostRuntimeHolder.restartHost?.invoke()
                                delay(1_500)
                                dismissedVersion = current.latestPlugin
                            },
                            onFailure = { failure ->
                                error = failure.message
                            },
                        )
                        busy = false
                    }
                },
            )
        },
    )
}
