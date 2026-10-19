package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
 * 模型设置：数据全部来自 Host：
 * - settings/describe 的 provider profile 与 models；
 * - llm/listConfigurableProviders 的提供商名称/路径；
 * - credentials/describe 的密钥配置状态。
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SettingsPaneTitle(text = "模型", large = false, modifier = Modifier.weight(1f))
                SettingsOutlineAction(text = "打开配置文件", onClick = onOpenConfigFile)
            }
        }
        item(key = "models_intro") {
            SettingsIntro(text = "模型与提供商来自 Host 配置；这里只展示后端实际注册的模型。", large = true)
        }

        item(key = "models_account") {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DeepSeek 账号",
                                style = SettingsText.RowTitleMedium,
                                color = palette.labelPrimary,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            SettingsStatusDot(color = if (accountSignedIn) palette.success else palette.labelTertiary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (accountSignedIn) accountName else "未登录",
                                style = SettingsText.AccountBody,
                                color = if (accountSignedIn) palette.success else palette.labelTertiary,
                            )
                        }
                        if (accountModels.isEmpty()) {
                            Text(
                                text = if (accountSignedIn) "暂无可用模型" else "登录后显示账号可用模型",
                                style = SettingsText.RowDesc,
                                color = palette.labelTertiary,
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                accountModels.forEachIndexed { index, model ->
                                    if (index > 0) SettingsCardDivider()
                                    ModelRow(name = model)
                                }
                            }
                        }
                    }
                }
            }
        }

        providers.forEach { provider ->
            item(key = "provider_${provider.id}") {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                style = SettingsText.RowTitleMedium,
                                color = palette.labelPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            SettingsStatusDot(color = if (provider.keyConfigured) palette.success else palette.labelTertiary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (provider.keyConfigured) "已配置" else "缺少 API Key",
                                style = SettingsText.AccountBody,
                                color = if (provider.keyConfigured) palette.success else palette.labelTertiary,
                            )
                        }
                        if (provider.models.isEmpty()) {
                            Text("该提供商暂未配置模型。", style = SettingsText.RowDesc, color = palette.labelTertiary)
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                provider.models.forEachIndexed { index, model ->
                                    if (index > 0) SettingsCardDivider()
                                    ModelRow(name = model)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            SettingsOutlineAction(
                                text = if (provider.keyConfigured) "更新 API Key" else "配置 API Key",
                                onClick = { editingProvider = provider },
                            )
                        }
                    }
                }
            }
        }

        item(key = "models_add") {
            SettingsDashedButton(text = "添加模型提供商", onClick = onOpenConfigFile)
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** 提供商卡片内的模型行：名称 14/22 + 右侧启用对勾。 */
@Composable
private fun ModelRow(name: String) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = SettingsText.RowTitle,
            color = palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = palette.labelPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

