package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 模型设置（对齐官方 ModelsSection 的提供商卡片）：
 * - 「模型」标题 + 「填入各提供商的 API 密钥即可使用其模型。」
 * - DeepSeek 账号卡片：名称 + 登录状态绿点 + 已登录
 * - 各提供商卡片：名称 + 密钥状态点 + 编辑
 * - 虚线上添加提供商入口
 *
 * 数据来自 Host：llm/listConfigurableProviders、settings/describe、credentials/describe。
 */
@Composable
internal fun SettingsModelsPane(
    accountSignedIn: Boolean,
    accountName: String,
    accountModels: List<String>,
    providersRaw: String?,
    settingsRaw: String?,
    credentialsRaw: String?,
    onOpenConfigFile: () -> Unit,
    onSetApiKey: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingsPalette.current
    val providers = remember(providersRaw, settingsRaw, credentialsRaw) {
        parseModelProviders(providersRaw, settingsRaw, credentialsRaw)
    }
    var editingProvider by remember { mutableStateOf<ModelProviderUi?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "models_title") {
            SettingsPaneTitle(text = "模型", large = false)
        }
        item(key = "models_intro") {
            SettingsIntro(text = "填入各提供商的 API 密钥即可使用其模型。", large = true)
        }
        item(key = "models_account") {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "DeepSeek 账号",
                            style = SettingsText.RowTitleMedium,
                            color = palette.labelPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SettingsStatusDot(color = if (accountSignedIn) palette.success else palette.error)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (accountSignedIn) "已登录" else "未登录",
                            style = SettingsText.AccountBody,
                            color = if (accountSignedIn) palette.success else palette.error,
                        )
                    }
                }
            }
        }
        providers.forEach { provider ->
            item(key = "provider_${provider.id}") {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = provider.name,
                            style = SettingsText.RowTitleMedium,
                            color = palette.labelPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SettingsStatusDot(color = if (provider.keyConfigured) palette.success else palette.error)
                        Spacer(modifier = Modifier.weight(1f))
                        SettingsOutlineAction(
                            text = "编辑",
                            onClick = { editingProvider = provider },
                        )
                    }
                }
            }
        }
        item(key = "models_add") {
            SettingsDashedButton(
                text = "添加模型提供商",
                onClick = onOpenConfigFile,
            )
        }
    }

    val editing = editingProvider
    if (editing != null) {
        var keyInput by remember(editing.id) { mutableStateOf("") }
        com.deepseek.dsh.core.designsystem.DshDialog(
            onDismiss = { editingProvider = null },
            title = "${editing.name} API Key",
            closeLabel = "关闭",
            description = "密钥将保存到 Host 凭据库（${editing.keyRef}），不会回显。",
            footer = {
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "取消", primary = false, enabled = true,
                    onClick = { editingProvider = null },
                )
                Spacer(modifier = Modifier.width(8.dp))
                com.deepseek.dsh.core.designsystem.DshDialogButton(
                    text = "保存", primary = true, enabled = keyInput.isNotBlank(),
                    onClick = {
                        onSetApiKey(editing.keyRef, keyInput.trim())
                        editingProvider = null
                    },
                )
            },
            content = {
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = "sk-...",
                )
            },
        )
    }
}

@Composable
private fun SettingsOutlineAction(text: String, onClick: () -> Unit) {
    val palette = LocalSettingsPalette.current
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL3),
        onClick = onClick,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = palette.labelPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
