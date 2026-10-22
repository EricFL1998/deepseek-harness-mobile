package com.deepseek.dsh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.core.bridge.BridgeNativeCompanion
import kotlinx.serialization.json.JsonObject

/** 「可安装原生伴侣」的底部提示卡。 */
@Composable
internal fun CompanionAvailableCard(
    companion: BridgeNativeCompanion,
    modifier: Modifier = Modifier,
    onGoInstall: () -> Unit,
    onIgnore: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.companion_available_title,
                        companion.name ?: companion.pluginId,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.companion_available_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onGoInstall) {
                Text(stringResource(R.string.companion_go_install))
            }
            TextButton(onClick = onIgnore) {
                Text(stringResource(R.string.companion_ignore))
            }
        }
    }
}

/** 插件整岛 Web UI 与 companion 原生浮层。 */
@Composable
internal fun PluginIslandOverlays() {
    if (com.deepseek.dsh.plugin.nativesdk.slots.SlotRegistry
            .resolve(com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.WEB_ISLAND_FULL)
            .isNotEmpty()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            com.deepseek.dsh.plugin.nativehost.NativeSlot(
                slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.WEB_ISLAND_FULL,
                spec = JsonObject(emptyMap()),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        com.deepseek.dsh.plugin.nativehost.NativeSlot(
            slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.PLUGIN_OVERLAY,
            spec = JsonObject(emptyMap()),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 登录 / API Key 两个对话框。 */
@Composable
internal fun SignInDialogs(
    showSignIn: Boolean,
    onDismissSignIn: () -> Unit,
    onAddKey: () -> Unit,
    onSignIn: () -> Unit,
    showApiKey: Boolean,
    apiKeyInput: String,
    onApiKeyChange: (String) -> Unit,
    onDismissApiKey: () -> Unit,
    onSubmitApiKey: () -> Unit,
) {
    if (showSignIn) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = onDismissSignIn,
            title = stringResource(R.string.onboarding_title),
            closeLabel = stringResource(R.string.action_close),
            description = stringResource(R.string.onboarding_desc),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.onboarding_add_key),
                    primary = false,
                    enabled = true,
                    onClick = onAddKey,
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.onboarding_login),
                    primary = true,
                    enabled = true,
                    onClick = onSignIn,
                )
            },
        )
    }
    if (showApiKey) {
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = onDismissApiKey,
            title = stringResource(R.string.onboarding_key_title),
            closeLabel = stringResource(R.string.action_close),
            description = stringResource(R.string.onboarding_key_desc),
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.action_cancel),
                    primary = false,
                    enabled = true,
                    onClick = onDismissApiKey,
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = stringResource(R.string.onboarding_login),
                    primary = true,
                    enabled = apiKeyInput.isNotBlank(),
                    onClick = onSubmitApiKey,
                )
            },
            content = {
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = apiKeyInput,
                    onValueChange = onApiKeyChange,
                    placeholder = stringResource(R.string.onboarding_key_placeholder),
                )
            },
        )
    }
}
