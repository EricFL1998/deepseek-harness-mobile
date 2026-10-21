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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.put

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
    onSaveProvider: (ModelProviderUi, String, String, List<String>) -> Unit,
    modifier: Modifier = Modifier,
    /** 插件声明的 settings.models.provider-card 组件（componentId 列表）。 */
    /** bridge 门面；为 null 时插件组件不渲染。 */
    bridgeRegistry: com.deepseek.dsh.core.bridge.BridgeRegistry? = null,
    /** 插件请求刷新的组件 id 流（client/refresh 事件）。 */
    pluginComponentRefresh: kotlinx.coroutines.flow.SharedFlow<String>? = null,
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
            SettingsPaneTitle(text = stringResource(R.string.settings_section_models), large = false)
        }
        item(key = "models_intro") {
            SettingsIntro(text = stringResource(R.string.settings_models_desc), large = true)
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
                            text = stringResource(R.string.settings_pane_models_account_title),
                            style = SettingsText.RowTitleMedium,
                            color = palette.labelPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SettingsStatusDot(color = if (accountSignedIn) palette.success else palette.error)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (accountSignedIn) {
                                stringResource(R.string.settings_pane_models_signed_in)
                            } else {
                                stringResource(R.string.settings_pane_models_signed_out)
                            },
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
                            onSaveProvider(provider, key, baseUrl, models)
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
                            SettingsSmallButton(text = stringResource(R.string.settings_pane_models_edit), onClick = { editingId = provider.id })
                        }
                    }
                }
                // settings.models.provider-card（keyed slot）：订阅/额度/第三方登录
                // 等插件扩展块，按 provider 卡片就地渲染。
                com.deepseek.dsh.plugin.nativehost.NativeSlot(
                    slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.SETTINGS_PROVIDER_CARD,
                    spec = kotlinx.serialization.json.buildJsonObject {
                        put("provider", provider.id)
                        put("displayName", provider.name)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item(key = "models_add") {
            SettingsDashedButton(
                text = stringResource(R.string.settings_pane_models_add_provider),
                onClick = onOpenConfigFile,
            )
        }
        // settings.models.provider-card：插件声明的 provider 卡片（订阅、用量、
        // 第三方登录等），就地渲染成原生控件，交互经 bridge 回传给插件。
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
    var baseUrlInput by remember(provider.id) { mutableStateOf(provider.baseUrl) }
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
            SettingsSmallButton(text = stringResource(R.string.settings_pane_models_edit), onClick = {})
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(provider.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.labelPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(provider.id, fontSize = 12.sp, color = palette.labelTertiary)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(stringResource(R.string.settings_pane_models_api_key), style = SettingsText.RowTitle, color = palette.labelPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        com.deepseek.dsh.core.designsystem.DshTextInput(
            value = keyInput,
            onValueChange = { keyInput = it },
            placeholder = stringResource(R.string.settings_pane_models_key_placeholder, provider.keyRef),
        )
        if (!provider.keyConfigured) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_pane_models_key_missing, provider.keyRef),
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
            Text(stringResource(R.string.settings_pane_models_custom_settings), style = SettingsText.RowTitle, color = palette.labelSecondary)
        }
        if (customOpen) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.settings_pane_models_api_base_url), style = SettingsText.RowTitle, color = palette.labelPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            com.deepseek.dsh.core.designsystem.DshTextInput(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                placeholder = stringResource(
                    R.string.settings_pane_models_base_url_placeholder,
                    provider.keyRef.replace("_API_KEY", "_BASE_URL"),
                ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_pane_models_base_url_hint),
                fontSize = 12.sp,
                color = palette.labelTertiary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_pane_models_catalog), style = SettingsText.RowTitle, color = palette.labelPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.settings_pane_models_reset_defaults),
                    fontSize = 12.sp,
                    color = palette.labelSecondary,
                    modifier = Modifier.clickable { models = provider.models },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (models.isEmpty()) {
                    stringResource(R.string.settings_pane_models_empty)
                } else {
                    stringResource(R.string.settings_pane_models_custom_list, models.joinToString(", "))
                },
                fontSize = 12.sp,
                color = palette.labelTertiary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            com.deepseek.dsh.core.designsystem.DshTextInput(
                value = newModel,
                onValueChange = { newModel = it },
                placeholder = stringResource(R.string.settings_pane_models_add_id_placeholder),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsDashedButton(text = stringResource(R.string.settings_pane_models_add_model), onClick = {
                val value = newModel.trim()
                if (value.isNotEmpty()) {
                    models = (models + value).distinct()
                    newModel = ""
                }
            })
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SettingsSmallButton(text = stringResource(R.string.settings_pane_models_cancel), onClick = onCancel)
            Spacer(modifier = Modifier.width(8.dp))
            SettingsSmallButton(
                text = stringResource(R.string.settings_pane_models_save),
                primary = true,
                onClick = { onSave(keyInput.trim(), baseUrlInput.trim(), models) },
            )
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




