package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 模型设置（对齐官方 ModelsSection / ProviderEditor）。
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
    var editingId by remember { mutableStateOf<String?>(null) }

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
                SettingsCard(verticalPadding = 10.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
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
                if (editingId == provider.id) {
                    ModelProviderEditor(
                        provider = provider,
                        onCancel = { editingId = null },
                        onSave = { key, baseUrl, models ->
                            onSetApiKey(provider.keyRef, key)
                            editingId = null
                        },
                    )
                } else {
                    SettingsCard(verticalPadding = 12.dp) {
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
                            SettingsSmallButton(text = "编辑", onClick = { editingId = provider.id })
                        }
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
}

@Composable
private fun ModelProviderEditor(
    provider: ModelProviderUi,
    onCancel: () -> Unit,
    onSave: (key: String, baseUrl: String, models: List<String>) -> Unit,
) {
    val palette = LocalSettingsPalette.current
    var keyInput by remember(provider.id) { mutableStateOf("") }
    var baseUrlInput by remember(provider.id) { mutableStateOf("") }
    var customOpen by remember(provider.id) { mutableStateOf(false) }
    var models by remember(provider.id) { mutableStateOf(provider.models) }
    var newModel by remember(provider.id) { mutableStateOf("") }

    SettingsCard(verticalPadding = 14.dp) {
        // 卡片头：名称 + 状态点
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(provider.name, style = SettingsText.RowTitleMedium, color = palette.labelPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            SettingsStatusDot(color = if (provider.keyConfigured) palette.success else palette.error)
            Spacer(modifier = Modifier.weight(1f))
            SettingsSmallButton(text = "编辑", onClick = {})
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(provider.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.labelPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(provider.id, fontSize = 12.sp, color = palette.labelTertiary)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text("API 密钥", style = SettingsText.RowTitle, color = palette.labelPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        com.deepseek.dsh.core.designsystem.DshTextInput(
            value = keyInput,
            onValueChange = { keyInput = it },
            placeholder = "留空使用环境认证 (${provider.keyRef})",
        )
        if (!provider.keyConfigured) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "未检测到环境变量。请填写 API 密钥，或设置环境变量 ${provider.keyRef}。",
                fontSize = 12.sp,
                color = palette.warning,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { customOpen = !customOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsChevronDown(expanded = customOpen, size = 12.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text("自定义设置", style = SettingsText.RowTitle, color = palette.labelSecondary)
        }
        if (customOpen) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("API 地址", style = SettingsText.RowTitle, color = palette.labelPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            com.deepseek.dsh.core.designsystem.DshTextInput(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                placeholder = "https://api.deepseek.com/anthropic（默认使用环境 ${provider.keyRef.replace("_API_KEY", "_BASE_URL")}）",
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "请填写兼容 Anthropic Messages 协议的 API 地址。",
                fontSize = 12.sp,
                color = palette.labelTertiary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("模型目录", style = SettingsText.RowTitle, color = palette.labelPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "恢复默认模型",
                    fontSize = 12.sp,
                    color = palette.labelSecondary,
                    modifier = Modifier.clickable { models = provider.models },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (models.isEmpty()) "模型选择器中暂不显示任何模型；目录外 ID 仍可直接发送。" else "自定义模型：${models.joinToString(", ")}",
                fontSize = 12.sp,
                color = palette.labelTertiary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            com.deepseek.dsh.core.designsystem.DshTextInput(
                value = newModel,
                onValueChange = { newModel = it },
                placeholder = "添加模型 ID",
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsDashedButton(text = "添加模型", onClick = {
                val value = newModel.trim()
                if (value.isNotEmpty()) {
                    models = (models + value).distinct()
                    newModel = ""
                }
            })
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SettingsSmallButton(text = "取消", onClick = onCancel)
            Spacer(modifier = Modifier.width(8.dp))
            SettingsSmallButton(text = "保存", primary = true, onClick = { onSave(keyInput.trim(), baseUrlInput.trim(), models) })
        }
    }
}

@Composable
private fun SettingsSmallButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val palette = LocalSettingsPalette.current
    val bg = if (primary) palette.labelPrimary else androidx.compose.ui.graphics.Color.Transparent
    val fg = if (primary) androidx.compose.ui.graphics.Color.White else palette.labelPrimary
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = bg,
        border = if (primary) null else androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL3),
        modifier = Modifier.height(28.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = fg)
        }
    }
}


