package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
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
    /** 从 Host 重新拉提供商目录；页面进入与打开添加弹窗时各拉一次。 */
    onRefreshProviders: () -> Unit = {},
    /** 已注册（可路由）的 provider id：官方把它们的配置放在卡片里，不再进添加目录。 */
    routableProviders: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    /** 插件声明的 settings.models.provider-card 组件（componentId 列表）。 */
    /** bridge 门面；为 null 时插件组件不渲染。 */
    bridgeRegistry: com.deepseek.dsh.core.bridge.BridgeRegistry? = null,
    /** 插件请求刷新的组件 id 流（client/refresh 事件）。 */
    pluginComponentRefresh: kotlinx.coroutines.flow.SharedFlow<String>? = null,
) {
    val palette = LocalSettingsPalette.current
    LaunchedEffect(Unit) { onRefreshProviders() }
    val providers = remember(providersRaw, settingsRaw, credentialsRaw) {
        parseModelProviders(providersRaw, settingsRaw, credentialsRaw)
    }
    var editingId by remember { mutableStateOf<String?>(null) }
    var showAddProvider by remember { mutableStateOf(false) }
    // 自定义提供商的草稿：目录里没有它，但保存/编辑要和目录项走同一套编辑器。
    var customDraft by remember { mutableStateOf<ModelProviderUi?>(null) }
    val allProviders = remember(providers, customDraft) {
        if (customDraft == null) providers else providers + customDraft!!
    }
    // 已配置的提供商显示为卡片；官方 DeepSeek 始终保留一张卡片；正在编辑的提供商
    // （含刚从“添加”弹窗选中的未配置项）要渲染编辑器而不是卡片。
    val visibleProviders = allProviders.filter {
        it.configured || it.id == "deepseek" || it.id == editingId
    }
    // 未配置的目录项只出现在“添加模型提供商”弹窗里；DeepSeek 已常驻卡片，不再重复列出。
    // “添加模型提供商”只列官方内置目录：插件（订阅 OAuth 等）带来的 provider
    // 由它自己的卡片/订阅页配置，不混进这个列表。
    val addableProviders = allProviders.filter {
        !it.configured &&
            !it.pluginProvided &&
            it.id != "deepseek-official" &&
            it.id != "deepseek-account" &&
            it.id !in routableProviders
    }
    // 打开编辑器后滚动到该条目：从“添加”弹窗选中的提供商可能位于列表上方不可见处。
    // 固定前置条目共 3 项（标题、说明、账号卡片），其后才是 provider 条目。
    val listState = rememberLazyListState()
    LaunchedEffect(editingId) {
        val id = editingId ?: return@LaunchedEffect
        val index = visibleProviders.indexOfFirst { it.id == id }
        if (index >= 0) listState.animateScrollToItem(3 + index)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
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
        visibleProviders.forEach { provider ->
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
                onClick = {
                    // 目录随插件/订阅/凭据变化，打开弹窗前以 Host 为准重拉一次。
                    onRefreshProviders()
                    showAddProvider = true
                },
            )
        }
        // settings.models.provider-card：插件声明的 provider 卡片（订阅、用量、
        // 第三方登录等），就地渲染成原生控件，交互经 bridge 回传给插件。
    }
    if (showAddProvider) {
        AddModelProviderDialog(
            providers = addableProviders,
            // Host 目录还没拉到时（未启动 / 未登录），addableProviders 一定是空的；
            // 此时不能告诉用户「都配置好了」，那是两件完全不同的事。
            hostReady = !providersRaw.isNullOrBlank(),
            onDismiss = { showAddProvider = false },
            onSave = { provider, key, baseUrl, models ->
                if (providers.none { it.id == provider.id }) customDraft = provider
                showAddProvider = false
                onSaveProvider(provider, key, baseUrl, models)
            },
            onOpenConfigFile = onOpenConfigFile,
        )
    }
}

/**
 * 「添加模型提供商」弹窗：列出 Host 目录中尚未配置的提供商（DeepSeek 常驻卡片，不在此列）。
 * 选中「添加」后关闭弹窗并把 editingId 指向该提供商，由既有 [ModelProviderEditor] 完成密钥录入。
 *
 * @param hostReady Host 的提供商目录是否已拿到；false 时说明「暂不可用」而不是「都已配置」。
 */
@Composable
private fun AddModelProviderDialog(
    providers: List<ModelProviderUi>,
    hostReady: Boolean,
    onDismiss: () -> Unit,
    onSave: (ModelProviderUi, String, String, List<String>) -> Unit,
    onOpenConfigFile: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    var mode by remember { mutableStateOf(0) }
    var selected by remember(providers) { mutableStateOf<ModelProviderUi?>(null) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var expanderOpen by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf("") }
    var modelsInput by remember { mutableStateOf("") }
    var customId by remember { mutableStateOf("") }
    var customBaseUrl by remember { mutableStateOf("") }
    var customKey by remember { mutableStateOf("") }
    val customValid = customId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$"))
    com.deepseek.dsh.core.designsystem.DshDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_pane_models_add_title),
        closeLabel = stringResource(R.string.action_close),
        description = stringResource(R.string.settings_pane_models_add_desc),
        content = {
            // 官方 ModelsSection：一个卡片 + 段选择（第三方目录 / 自定义），目录项
            // 用下拉折叠而不是铺开整张清单。
            SettingsCard(verticalPadding = 6.dp) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        stringResource(R.string.settings_pane_models_add_tab_catalog),
                        stringResource(R.string.settings_pane_models_add_tab_custom),
                    ).forEachIndexed { index, label ->
                        val active = mode == index
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = if (active) palette.labelPrimary else androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .clickable { mode = index },
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                                    color = if (active) androidx.compose.ui.graphics.Color.White else palette.labelSecondary,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (mode == 0) {
                if (providers.isEmpty()) {
                    SettingsEmptyState(
                        text = stringResource(
                            if (hostReady) {
                                R.string.settings_pane_models_add_empty
                            } else {
                                R.string.settings_pane_models_add_unavailable
                            }
                        )
                    )
                } else {
                    Text(
                        stringResource(R.string.settings_pane_models_provider_label),
                        style = SettingsText.RowTitle,
                        color = palette.labelPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // 目录有 50+ 项：选择器下方用悬浮弹层，材质沿用设置页的
                    // cardFill + borderL2 + 10dp 圆角，而不是 Material 默认菜单面。
                    var anchorWidth by remember { mutableStateOf(0) }
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    Box {
                        SettingsCard(verticalPadding = 10.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        anchorWidth = coordinates.size.width
                                    }
                                    .clickable { dropdownOpen = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selected?.name
                                        ?: stringResource(R.string.settings_pane_models_provider_select),
                                    fontSize = 14.sp,
                                    color = if (selected == null) palette.labelTertiary else palette.labelPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                SettingsChevronDown(expanded = dropdownOpen, size = 12.dp)
                            }
                        }
                        if (dropdownOpen) {
                            androidx.compose.ui.window.Popup(
                                alignment = Alignment.TopStart,
                                offset = androidx.compose.ui.unit.IntOffset(
                                    0,
                                    with(density) { 54.dp.roundToPx() },
                                ),
                                onDismissRequest = { dropdownOpen = false },
                                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                            ) {
                                androidx.compose.material3.Surface(
                                    color = palette.cardFill,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.borderL2),
                                    shadowElevation = 10.dp,
                                    modifier = Modifier.width(
                                        if (anchorWidth > 0) {
                                            with(density) { anchorWidth.toDp() }
                                        } else {
                                            280.dp
                                        },
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .heightIn(max = 320.dp)
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        providers.forEach { provider ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selected = provider
                                                        dropdownOpen = false
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        provider.name,
                                                        fontSize = 13.sp,
                                                        color = palette.labelPrimary,
                                                    )
                                                    Text(
                                                        provider.id,
                                                        fontSize = 11.sp,
                                                        color = palette.labelTertiary,
                                                    )
                                                }
                                                if (provider.id == selected?.id) {
                                                    com.deepseek.dsh.core.designsystem.DshMenuCheck()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.settings_pane_models_api_key),
                        style = SettingsText.RowTitle,
                        color = palette.labelPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    com.deepseek.dsh.core.designsystem.DshTextInput(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = stringResource(
                            R.string.settings_pane_models_key_placeholder,
                            selected?.keyRef ?: "",
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanderOpen = !expanderOpen },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsChevronDown(expanded = expanderOpen, size = 12.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.settings_pane_models_custom_settings),
                            style = SettingsText.RowTitle,
                            color = palette.labelSecondary,
                        )
                    }
                    if (expanderOpen) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.settings_pane_models_api_base_url),
                            style = SettingsText.RowTitle,
                            color = palette.labelPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        com.deepseek.dsh.core.designsystem.DshTextInput(
                            value = baseUrlInput,
                            onValueChange = { baseUrlInput = it },
                            placeholder = selected?.baseUrl.orEmpty(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.settings_pane_models_models_label),
                            style = SettingsText.RowTitle,
                            color = palette.labelPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        com.deepseek.dsh.core.designsystem.DshTextInput(
                            value = modelsInput,
                            onValueChange = { modelsInput = it },
                            placeholder = selected?.models.orEmpty().joinToString(", "),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_pane_models_models_hint),
                            fontSize = 12.sp,
                            color = palette.labelTertiary,
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.settings_pane_models_custom_id),
                    style = SettingsText.RowTitle,
                    color = palette.labelPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = customId,
                    onValueChange = { customId = it },
                    placeholder = stringResource(R.string.settings_pane_models_custom_id_placeholder),
                )
                if (customId.isNotEmpty() && !customValid) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_pane_models_custom_id_invalid),
                        fontSize = 12.sp,
                        color = palette.warning,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_pane_models_api_base_url),
                    style = SettingsText.RowTitle,
                    color = palette.labelPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = customBaseUrl,
                    onValueChange = { customBaseUrl = it },
                    placeholder = "https://api.example.com/v1",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_pane_models_api_key),
                    style = SettingsText.RowTitle,
                    color = palette.labelPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = customKey,
                    onValueChange = { customKey = it },
                    placeholder = stringResource(R.string.settings_pane_models_api_key),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_pane_models_models_label),
                    style = SettingsText.RowTitle,
                    color = palette.labelPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                com.deepseek.dsh.core.designsystem.DshTextInput(
                    value = modelsInput,
                    onValueChange = { modelsInput = it },
                    placeholder = "deepseek-chat, deepseek-reasoner",
                )
            }
        },
        footer = {
            com.deepseek.dsh.core.designsystem.DshDialogButton(
                text = stringResource(R.string.settings_pane_models_cancel),
                primary = false,
                enabled = true,
                onClick = onDismiss,
            )
            com.deepseek.dsh.core.designsystem.DshDialogButton(
                text = stringResource(R.string.settings_pane_models_save),
                primary = true,
                enabled = if (mode == 0) selected != null else customValid,
                onClick = {
                    if (mode == 0) {
                        val provider = selected ?: return@DshDialogButton
                        val typedModels = modelsInput.split(',', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onSave(
                            provider,
                            keyInput.trim(),
                            baseUrlInput.trim(),
                            if (typedModels.isEmpty()) provider.models else typedModels,
                        )
                    } else {
                        val id = customId.trim()
                        onSave(
                            ModelProviderUi(
                                id = id,
                                name = id,
                                models = emptyList(),
                                keyRef = id.uppercase().replace('-', '_')
                                    .replace('.', '_') + "_API_KEY",
                                keyConfigured = false,
                                settingsNs = "llm-pi-ai",
                                settingsPath = listOf("providers", id),
                                baseUrl = customBaseUrl.trim(),
                                declared = false,
                                configured = false,
                            ),
                            customKey.trim(),
                            customBaseUrl.trim(),
                            modelsInput.split(',', '\n')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() },
                        )
                    }
                },
            )
        },
    )
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




