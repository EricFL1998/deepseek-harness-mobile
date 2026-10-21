package com.example.subscription.nativeui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.host.PluginRpcHost
import com.deepseek.dsh.plugin.nativesdk.pages.PageContent
import com.deepseek.dsh.plugin.nativesdk.pages.PageEntry
import com.deepseek.dsh.plugin.nativesdk.pages.PageLifecycle
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Native owner of `dsh-plugin-subscription-oauth#subscriptions`. */
class SubscriptionSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "subscriptions",
        title = "订阅",
        subtitle = "使用你已付费的 AI 订阅，无需 API Key",
        placement = PageEntry.Placement.SETTINGS,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        SubscriptionSettingsContent(theme, host)
    }

    @Composable
    override fun Content(
        spec: JsonObject,
        theme: PluginTheme,
        host: PluginHostApi,
        refreshEvents: SharedFlow<String>?,
    ) {
        SubscriptionSettingsContent(theme, host, refreshEvents)
    }
}

@Composable
private fun SubscriptionSettingsContent(
    theme: PluginTheme,
    host: PluginHostApi,
    refreshEvents: SharedFlow<String>? = null,
) {
    val rpc = host as? PluginRpcHost
    val context = LocalContext.current
    val isActive = PageLifecycle.LocalIsActive.current
    var providers by remember { mutableStateOf<List<SubscriptionProvider>>(emptyList()) }
    var autoOn by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var refreshKey by remember { mutableStateOf(0) }
    var expandedProviders by remember { mutableStateOf(setOf<String>()) }

    fun refresh() {
        refreshKey += 1
    }

    LaunchedEffect(rpc, refreshKey) {
        if (rpc == null) {
            loading = false
            error = "Host RPC 未连接"
            return@LaunchedEffect
        }
        loading = providers.isEmpty()
        rpc.callRpc("subscription-oauth.status", JsonObject(emptyMap())) { result ->
            if (!isActive()) return@callRpc
            loading = false
            result.fold(
                onSuccess = { body ->
                    runCatching { SubscriptionRpc.value(body) }.fold(
                        onSuccess = { value ->
                            providers = SubscriptionParser.providers(value)
                            error = null
                        },
                        onFailure = { error = it.message ?: "无法解析订阅状态" },
                    )
                },
                onFailure = { error = it.message ?: "无法读取订阅状态" },
            )
        }
    }

    LaunchedEffect(rpc) {
        rpc?.callRpc("subscription-oauth.auto-state", JsonObject(emptyMap())) { result ->
            if (!isActive()) return@callRpc
            result.onSuccess { body ->
                autoOn = runCatching {
                    SubscriptionRpc.value(body).obj()?.bool("enabled") != false
                }.getOrDefault(autoOn)
            }
        }
    }

    LaunchedEffect(refreshEvents) {
        refreshEvents?.collect { componentId ->
            if (componentId == "dsh-plugin-subscription-oauth#subscriptions" ||
                componentId == "changfenhuang.subscription#subscriptions"
            ) refresh()
        }
    }

    val hasPendingLogin = providers.any { it.busy }
    LaunchedEffect(hasPendingLogin) {
        while (hasPendingLogin) {
            delay(1_500)
            refresh()
        }
    }

    fun call(method: String, payload: JsonObject = JsonObject(emptyMap())) {
        if (rpc == null || busy) return
        busy = true
        error = null
        val provider = payload["provider"]?.primitive?.contentOrNull
        if (provider != null) actionErrors = actionErrors - provider
        rpc.callRpc("subscription-oauth.$method", payload) { result ->
            if (!isActive()) return@callRpc
            busy = false
            result.fold(
                onSuccess = { body ->
                    runCatching { SubscriptionRpc.value(body) }.fold(
                        onSuccess = {
                            error = null
                            refresh()
                        },
                        onFailure = { failure ->
                            val message = failure.message ?: "操作失败"
                            if (provider != null) actionErrors = actionErrors + (provider to message)
                            else error = message
                        },
                    )
                },
                onFailure = { failure ->
                    val message = failure.message ?: "操作失败"
                    if (provider != null) actionErrors = actionErrors + (provider to message)
                    else error = message
                },
            )
        }
    }

    fun providerCall(method: String, provider: String) {
        call(method, buildJsonObject { put("provider", provider) })
    }

    fun accountCall(method: String, provider: String, account: String) {
        call(method, buildJsonObject {
            put("provider", provider)
            put("account", account)
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = theme.spacingUnit * 2, vertical = theme.spacingUnit),
        verticalArrangement = Arrangement.spacedBy(theme.spacingUnit),
    ) {
        Text(
            text = "使用你已付费的 AI 订阅，无需 API Key。在下方选择服务并展开卡片进行连接与管理。",
            style = theme.textBody,
            color = theme.colorOnSurfaceVariant,
        )
        error?.let {
            Text(text = it, style = theme.textBody, color = theme.colorError)
            TextButton(onClick = ::refresh) { Text("重试") }
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        providers.forEach { provider ->
            SubscriptionProviderCard(
                provider = provider,
                theme = theme,
                busy = busy,
                expanded = provider.id in expandedProviders,
                autoOn = autoOn,
                actionError = actionErrors[provider.id],
                onToggleExpanded = {
                    expandedProviders = if (provider.id in expandedProviders) {
                        expandedProviders - provider.id
                    } else {
                        expandedProviders + provider.id
                    }
                },
                onConnect = { providerCall("login", provider.id) },
                onCancel = { providerCall("cancel", provider.id) },
                onRefreshUsage = { providerCall("usage", provider.id) },
                onRefreshModels = {
                    call("discover", buildJsonObject {
                        put("provider", provider.id)
                        put("force", true)
                    })
                },
                onSetAutoOn = { enabled ->
                    call("setAutoOn", buildJsonObject { put("enabled", enabled) })
                    autoOn = enabled
                },
                onSetModelVisible = { modelId, visible ->
                    call("setModelVisible", buildJsonObject {
                        put("provider", provider.id)
                        put("id", modelId)
                        put("visible", visible)
                    })
                },
                onSetModelOrder = { ids ->
                    call("setModelOrder", buildJsonObject {
                        put("provider", provider.id)
                        put("ids", buildJsonArray { ids.forEach { add(it) } })
                    })
                },
                onAddModel = { modelId ->
                    call("addModel", buildJsonObject {
                        put("provider", provider.id)
                        put("id", modelId)
                    })
                },
                onRemoveModel = { modelId ->
                    call("removeModel", buildJsonObject {
                        put("provider", provider.id)
                        put("id", modelId)
                    })
                },
                onSetDefault = { account -> accountCall("setDefault", provider.id, account) },
                onRefreshToken = { account -> accountCall("refresh", provider.id, account) },
                onLogout = { account -> accountCall("logout", provider.id, account) },
                onReload = { call("reload") },
                onCheckin = { account -> accountCall("checkin", provider.id, account) },
                onTasks = { account ->
                    call("tasks", buildJsonObject {
                        put("provider", provider.id)
                        put("account", account)
                        put("claim", true)
                    })
                },
                onOpenAuthorization = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure { error = it.message ?: "无法打开授权页" }
                },
            )
        }
        if (!loading && providers.isEmpty() && error == null) {
            Text("暂无订阅服务。", style = theme.textBody, color = theme.colorOnSurfaceVariant)
        }
    }
}

@Composable
private fun SubscriptionProviderCard(
    provider: SubscriptionProvider,
    theme: PluginTheme,
    busy: Boolean,
    expanded: Boolean,
    autoOn: Boolean,
    actionError: String?,
    onToggleExpanded: () -> Unit,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onRefreshUsage: () -> Unit,
    onRefreshModels: () -> Unit,
    onSetAutoOn: (Boolean) -> Unit,
    onSetModelVisible: (String, Boolean) -> Unit,
    onSetModelOrder: (List<String>) -> Unit,
    onAddModel: (String) -> Unit,
    onRemoveModel: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onRefreshToken: (String) -> Unit,
    onLogout: (String) -> Unit,
    onReload: () -> Unit,
    onCheckin: (String) -> Unit,
    onTasks: (String) -> Unit,
    onOpenAuthorization: (String) -> Unit,
) {
    var manageOpen by remember(provider.id) { mutableStateOf(false) }
    var modelsOpen by remember(provider.id) { mutableStateOf(false) }
    var addingModel by remember(provider.id) { mutableStateOf(false) }
    var modelDraft by remember(provider.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.shapeCard,
        colors = CardDefaults.cardColors(containerColor = theme.colorSurfaceVariant),
    ) {
        val context = LocalContext.current
        Column(modifier = Modifier.padding(theme.spacingUnit * 2)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = theme.spacingUnit / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (provider.loggedIn) "●" else "○",
                    style = theme.textBody,
                    color = if (provider.loggedIn) theme.colorPrimary else theme.colorOnSurfaceVariant,
                )
                Text(
                    text = "  ${provider.name}",
                    style = theme.textTitle,
                    color = theme.colorOnSurface,
                )
                Text(
                    text = "  " + when {
                        provider.busy -> "等待授权"
                        provider.id == "auto" -> "${provider.models.count { it.visible }} 个已启用模型"
                        provider.loggedIn -> "已连接 ${provider.accounts.size} 个账号"
                        else -> "未登录"
                    },
                    style = theme.textCaption,
                    color = theme.colorOnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggleExpanded) {
                    Text(if (expanded) "收起" else "编辑")
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = theme.spacingUnit))
                Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                    if (provider.connectable && !provider.loggedIn && !provider.busy) {
                        Text(
                            "登录后即可在模型选择器中使用该服务的模型。",
                            style = theme.textBody,
                            color = theme.colorOnSurfaceVariant,
                        )
                    }
                    actionError?.let { message ->
                        Text(
                            text = "无法开始连接：${friendlySubscriptionError(message)}",
                            style = theme.textBody,
                            color = theme.colorError,
                        )
                        Text("请检查网络后重试。原始错误：$message", style = theme.textCaption, color = theme.colorOnSurfaceVariant)
                    }
                    provider.lastError?.let {
                        Text(it, style = theme.textCaption, color = theme.colorError)
                    }
                    if (provider.id == "auto") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "请求失败时自动切换",
                                style = theme.textBody,
                                color = theme.colorOnSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = autoOn, onCheckedChange = onSetAutoOn, enabled = !busy)
                        }
                    }

                    provider.accounts.forEach { account ->
                        AccountSection(
                            account = account,
                            usage = provider.usage.firstOrNull { it.account == account.id },
                            providerId = provider.id,
                            theme = theme,
                            busy = busy,
                            onSetDefault = { onSetDefault(account.id) },
                            onLogout = { onLogout(account.id) },
                            onRefreshUsage = onRefreshUsage,
                            onCheckin = { onCheckin(account.id) },
                            onTasks = { onTasks(account.id) },
                        )
                    }

                    if (provider.loggedIn || provider.id == "auto") {
                        ModelSection(
                            provider = provider,
                            theme = theme,
                            busy = busy,
                            open = modelsOpen,
                            adding = addingModel,
                            draft = modelDraft,
                            onToggle = { modelsOpen = !modelsOpen },
                            onAddingChange = { addingModel = it },
                            onDraftChange = { modelDraft = it },
                            onRefreshModels = onRefreshModels,
                            onAddModel = {
                                val id = modelDraft.trim()
                                if (id.isNotEmpty()) {
                                    onAddModel(id)
                                    modelDraft = ""
                                    addingModel = false
                                }
                            },
                            onSetModelVisible = onSetModelVisible,
                            onSetModelOrder = onSetModelOrder,
                            onRemoveModel = onRemoveModel,
                        )
                    }

                    if (provider.connectable) {
                        Row(horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                            if (provider.busy) {
                                OutlinedButton(onClick = onCancel, enabled = !busy) { Text("取消") }
                            } else {
                                Button(onClick = onConnect, enabled = !busy) {
                                    Text(
                                        when {
                                            actionError != null -> "重试连接"
                                            provider.accounts.isEmpty() -> "连接"
                                            else -> "添加账号"
                                        },
                                    )
                                }
                                OutlinedButton(
                                    onClick = { manageOpen = !manageOpen },
                                    enabled = !busy,
                                ) { Text(if (manageOpen) "收起管理" else "管理") }
                            }
                        }
                    }

                    if (provider.busy) {
                        provider.pendingUrl?.let { url ->
                            OutlinedButton(onClick = { onOpenAuthorization(url) }) { Text("打开授权页") }
                        }
                        provider.userCode?.let { code ->
                            Text("授权码：$code", style = theme.textTitle, color = theme.colorPrimary)
                            provider.pendingExpiresAt?.takeIf { it.isNotBlank() }?.let { expires ->
                                Text(
                                    "有效期：$expires",
                                    style = theme.textCaption,
                                    color = theme.colorOnSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(
                                        android.content.ClipboardManager::class.java
                                    )
                                    clipboard?.setPrimaryClip(
                                        android.content.ClipData.newPlainText("授权码", code)
                                    )
                                },
                            ) { Text("复制授权码") }
                        }
                    }

                    if (manageOpen) {
                        ManageSection(
                            provider = provider,
                            theme = theme,
                            busy = busy,
                            onSetDefault = onSetDefault,
                            onRefreshToken = onRefreshToken,
                            onLogout = onLogout,
                            onReload = onReload,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    account: SubscriptionAccount,
    usage: SubscriptionUsage?,
    providerId: String,
    theme: PluginTheme,
    busy: Boolean,
    onSetDefault: () -> Unit,
    onLogout: () -> Unit,
    onRefreshUsage: () -> Unit,
    onCheckin: () -> Unit,
    onTasks: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSetDefault, enabled = !busy && !account.isDefault) {
                Text(if (account.isDefault) "★" else "☆")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(account.label, style = theme.textBody, color = theme.colorOnSurface)
                Text(
                    buildString {
                        if (account.isDefault) append("默认")
                        if (account.needsLogin) append(" · 需要重新登录")
                        if (account.expired) append(" · 已过期")
                    }.ifBlank { "已连接" },
                    style = theme.textCaption,
                    color = theme.colorOnSurfaceVariant,
                )
            }
            TextButton(onClick = onLogout, enabled = !busy) { Text("退出登录") }
        }
        if (usage != null) {
            UsageSection(usage, theme)
        } else {
            TextButton(onClick = onRefreshUsage, enabled = !busy) { Text("读取额度") }
        }
        if (providerId == "workbuddy") {
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                TextButton(onClick = onCheckin, enabled = !busy) { Text("签到") }
                TextButton(onClick = onTasks, enabled = !busy) { Text("领取任务奖励") }
            }
        }
    }
}

@Composable
private fun UsageSection(usage: SubscriptionUsage, theme: PluginTheme) {
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2)) {
        usage.plan?.let {
            Text("套餐：$it", style = theme.textCaption, color = theme.colorOnSurfaceVariant)
        }
        usage.windows.forEach { window ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    window.label,
                    style = theme.textCaption,
                    color = theme.colorOnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                window.percent?.let {
                    Text("${it.toInt()}%", style = theme.textCaption, color = theme.colorOnSurfaceVariant)
                }
            }
            window.percent?.let { percent ->
                LinearProgressIndicator(
                    progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ModelSection(
    provider: SubscriptionProvider,
    theme: PluginTheme,
    busy: Boolean,
    open: Boolean,
    adding: Boolean,
    draft: String,
    onToggle: () -> Unit,
    onAddingChange: (Boolean) -> Unit,
    onDraftChange: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onAddModel: () -> Unit,
    onSetModelVisible: (String, Boolean) -> Unit,
    onSetModelOrder: (List<String>) -> Unit,
    onRemoveModel: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2)) {
        HorizontalDivider()
        val hiddenCount = provider.models.count { !it.visible }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggle) {
                Text("${if (open) "▾" else "▸"} 模型")
            }
            if (provider.id == "auto") {
                Text(
                    "自动链",
                    style = theme.textCaption,
                    color = theme.colorPrimary,
                )
            }
            Text(
                buildString {
                    append("${provider.models.size} 个 · ")
                    append(if (provider.liveCatalog) "实时目录" else "内置目录")
                    if (hiddenCount > 0) append(" · 隐藏 $hiddenCount")
                },
                style = theme.textCaption,
                color = theme.colorOnSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefreshModels, enabled = !busy) { Text("刷新") }
            if (provider.connectable) {
                TextButton(onClick = { onAddingChange(!adding) }, enabled = !busy) { Text("添加") }
            }
        }
        if (adding) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text("模型 ID") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                Button(onClick = onAddModel, enabled = !busy && draft.isNotBlank()) { Text("添加模型") }
                TextButton(onClick = { onAddingChange(false); onDraftChange("") }) { Text("取消") }
            }
        }
        if (open) {
            provider.models.forEachIndexed { index, model ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        TextButton(
                            onClick = {
                                if (index > 0) {
                                    val ids = provider.models.map { it.id }.toMutableList()
                                    ids.add(index - 1, ids.removeAt(index))
                                    onSetModelOrder(ids)
                                }
                            },
                            enabled = !busy && index > 0,
                        ) { Text("↑") }
                        TextButton(
                            onClick = {
                                if (index < provider.models.lastIndex) {
                                    val ids = provider.models.map { it.id }.toMutableList()
                                    ids.add(index + 1, ids.removeAt(index))
                                    onSetModelOrder(ids)
                                }
                            },
                            enabled = !busy && index < provider.models.lastIndex,
                        ) { Text("↓") }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.name, style = theme.textBody, color = theme.colorOnSurface)
                        Text(
                            buildString {
                                append(model.id)
                                model.source?.let { append(" · $it") }
                                model.contextWindow?.let { append(" · ${formatTokens(it)}") }
                            },
                            style = theme.textCaption,
                            color = theme.colorOnSurfaceVariant,
                        )
                    }
                    if (model.userAdded) {
                        TextButton(onClick = { onRemoveModel(model.id) }, enabled = !busy) { Text("×") }
                    }
                    Switch(
                        checked = model.visible,
                        onCheckedChange = { onSetModelVisible(model.id, it) },
                        enabled = !busy,
                    )
                }
            }
            if (provider.models.isEmpty()) {
                Text(
                    if (provider.id == "auto") "自动链暂无可用模型。" else "暂无可用模型。",
                    style = theme.textBody,
                    color = theme.colorOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ManageSection(
    provider: SubscriptionProvider,
    theme: PluginTheme,
    busy: Boolean,
    onSetDefault: (String) -> Unit,
    onRefreshToken: (String) -> Unit,
    onLogout: (String) -> Unit,
    onReload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacingUnit / 2)) {
        HorizontalDivider()
        Text("账号管理", style = theme.textTitle, color = theme.colorOnSurface)
        provider.accounts.forEach { account ->
            Text(account.label, style = theme.textBody, color = theme.colorOnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit)) {
                TextButton(onClick = { onRefreshToken(account.id) }, enabled = !busy) { Text("刷新令牌") }
                if (!account.isDefault) {
                    TextButton(onClick = { onSetDefault(account.id) }, enabled = !busy) { Text("设为默认") }
                }
                TextButton(onClick = { onLogout(account.id) }, enabled = !busy) { Text("退出登录") }
            }
        }
        if (provider.accounts.isEmpty()) {
            Text("未登录", style = theme.textBody, color = theme.colorOnSurfaceVariant)
        }
        if (provider.loggedIn) {
            TextButton(onClick = onReload, enabled = !busy) { Text("重新读取凭据") }
            provider.details.forEach { detail ->
                Text(
                    "${detail.key}: ${detail.value}",
                    style = theme.textCaption,
                    color = theme.colorOnSurfaceVariant,
                )
            }
        }
    }
}

private data class SubscriptionProvider(
    val id: String,
    val name: String,
    val loggedIn: Boolean,
    val connectable: Boolean,
    val busy: Boolean,
    val pendingUrl: String?,
    val userCode: String?,
    /** 设备码/授权码过期时间（Host 原样返回的文本）。 */
    val pendingExpiresAt: String?,
    val accounts: List<SubscriptionAccount>,
    val models: List<SubscriptionModel>,
    val liveCatalog: Boolean,
    val usage: List<SubscriptionUsage>,
    val lastError: String?,
    val details: List<SubscriptionDetail>,
)

private data class SubscriptionAccount(
    val id: String,
    val label: String,
    val isDefault: Boolean,
    val needsLogin: Boolean,
    val expired: Boolean,
)

private data class SubscriptionModel(
    val id: String,
    val name: String,
    val visible: Boolean,
    val source: String?,
    val userAdded: Boolean,
    val contextWindow: Long?,
)

private data class SubscriptionUsage(
    val account: String?,
    val plan: String?,
    val windows: List<SubscriptionWindow>,
)

private data class SubscriptionWindow(val label: String, val percent: Double?)
private data class SubscriptionDetail(val key: String, val value: String)

private object SubscriptionParser {
    fun providers(value: JsonElement): List<SubscriptionProvider> =
        value.obj()?.get("providers")?.array().orEmpty().mapNotNull { element ->
            val item = element.obj() ?: return@mapNotNull null
            val pending = item["pending"].obj()
            SubscriptionProvider(
                id = item.str("provider") ?: return@mapNotNull null,
                name = item.str("displayName") ?: item.str("provider") ?: "订阅服务",
                loggedIn = item.bool("loggedIn") == true,
                connectable = item.bool("connectable") != false,
                busy = item.bool("busy") == true,
                pendingUrl = pending?.str("url"),
                userCode = pending?.str("userCode"),
                pendingExpiresAt = pending?.str("expiresAt"),
                accounts = item["accounts"].array().mapNotNull { accountElement ->
                    val account = accountElement.obj() ?: return@mapNotNull null
                    SubscriptionAccount(
                        id = account.str("account") ?: return@mapNotNull null,
                        label = account.str("label") ?: account.str("account") ?: "账号",
                        isDefault = account.bool("isDefault") == true,
                        needsLogin = account.bool("needsLogin") == true,
                        expired = account.bool("expired") == true,
                    )
                },
                models = item["models"].array().mapNotNull { modelElement ->
                    val model = modelElement.obj() ?: return@mapNotNull null
                    SubscriptionModel(
                        id = model.str("id") ?: return@mapNotNull null,
                        name = model.str("name") ?: model.str("id") ?: "模型",
                        visible = model.bool("visible") != false,
                        source = model.str("source"),
                        userAdded = model.bool("userAdded") == true,
                        contextWindow = model["contextWindow"]?.primitive?.longOrNull,
                    )
                },
                liveCatalog = !item["discovered"].array().isEmpty(),
                usage = item["usage"].array().mapNotNull { usageElement ->
                    val usage = usageElement.obj() ?: return@mapNotNull null
                    SubscriptionUsage(
                        account = usage.str("account"),
                        plan = usage.str("plan"),
                        windows = usage["windows"].array().mapNotNull { windowElement ->
                            val window = windowElement.obj() ?: return@mapNotNull null
                            SubscriptionWindow(
                                label = window.str("label") ?: "额度",
                                percent = window["usedPercent"]?.primitive?.doubleOrNull,
                            )
                        },
                    )
                },
                lastError = item.str("lastError"),
                details = item["details"].array().mapNotNull { detailElement ->
                    val detail = detailElement.obj() ?: return@mapNotNull null
                    SubscriptionDetail(
                        key = detail.str("key") ?: return@mapNotNull null,
                        value = detail.str("value") ?: "",
                    )
                },
            )
        }
}

private object SubscriptionRpc {
    fun value(body: JsonElement): JsonElement {
        val envelope = body.obj() ?: throw IllegalStateException("Host 返回了空响应")
        val result = envelope["result"] ?: envelope
        val obj = result.obj() ?: throw IllegalStateException("Host 响应格式无效")
        if (obj.bool("ok") == false) {
            val message = obj["error"].obj()?.str("message") ?: "Host RPC 失败"
            throw IllegalStateException(message)
        }
        return obj["value"] ?: throw IllegalStateException("Host 响应缺少 value")
    }
}

private fun friendlySubscriptionError(message: String): String = when {
    message.equals("fetch failed", ignoreCase = true) ||
        message.contains("network", ignoreCase = true) ||
        message.contains("timeout", ignoreCase = true) -> "Host 无法连接授权服务"
    else -> message
}

private fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M tokens"
    value >= 1_000 -> "${value / 1_000}K tokens"
    else -> "$value tokens"
}

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray).orEmpty()
private val JsonElement.primitive: JsonPrimitive? get() = this as? JsonPrimitive
private fun JsonObject.str(key: String): String? =
    this[key]?.primitive?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.bool(key: String): Boolean? = this[key]?.primitive?.booleanOrNull
