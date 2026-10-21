package com.deepseek.dsh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshSwitch
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.model.PluginSettingsDocument
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * 官方插件设置页的原生实现：字段表 + 通用页面框架。
 *
 * 官方页面（`@deepseek-ai/dsh-client-ui-settings-*`）的每个设置页都是 React 组件，
 * 组件里写死了字段、文案、分组和保存行为。这里把同样的结构翻译成原生版：
 *
 * - **每个插件一页一文件**（`AgentLoopSettingsPage.kt`、`ShellSettingsPage.kt`、
 *   `WebSearchSettingsPage.kt`、`SubagentSettingsPage.kt`），字段与文案逐条对照
 *   官方包 `lib/client.js` 里的 `zh` 字典；
 * - **值**来自 Host 的 `settings` 命名空间，保存按命名空间回写，Host 侧
 *   `applies: live` 的配置立即生效；
 * - **保存**沿用官方语义：一次保存覆盖页面上所有改过的字段，revision 冲突时
 *   提示而不覆盖（官方 `saveFailed`）。
 */

/** 一个设置控件的类型。 */
internal enum class OfficialFieldKind { NUMBER, TEXT, TOGGLE }

/**
 * 官方设置页上的一个字段：落到某个设置命名空间的一个键。
 *
 * @param ns 设置命名空间（Host 的 Loader 行 id）。
 * @param key 命名空间里的字段名。
 * @param label 控件标题（官方字典原文）。
 * @param hint 控件说明（官方字典原文），null 表示官方没有说明。
 * @param kind 控件类型。
 * @param group 分组标题（官方页面的小节标题），null 表示不分组。
 */
internal data class OfficialField(
    val ns: String,
    val key: String,
    val label: String,
    val hint: String?,
    val kind: OfficialFieldKind,
    val group: String? = null,
)

/**
 * 一个官方设置页：标题、说明、字段，以及页面涉及的命名空间。
 *
 * @param title 页面标题（官方条目 label）。
 * @param description 页面说明（官方字典 `description`）。
 * @param fields 字段表，顺序即页面顺序。
 * @param namespaces 页面读写的命名空间（含开关写入的那一个）。
 * @param searchApiToggle 是否有「启用新版 Web 搜索 API」开关。
 */
internal data class OfficialPageSpec(
    val title: String,
    val description: String,
    val fields: List<OfficialField>,
    val namespaces: List<String>,
    val searchApiToggle: Boolean = false,
)

/** 注册表：官方设置页 id → 字段表（每页在自己的文件里定义）。 */
private val OFFICIAL_PAGES: Map<String, OfficialPageSpec> = mapOf(
    "agent-loop" to AGENT_LOOP_SETTINGS_PAGE,
    "shell" to SHELL_SETTINGS_PAGE,
    "web-search" to WEB_SEARCH_SETTINGS_PAGE,
    "subagent" to SUBAGENT_SETTINGS_PAGE,
)

/**
 * 这个官方设置条目是否有逐字段复刻的原生页。
 * @param itemId 官方 `plugins.item` 注册 id。
 * @returns true 表示走 [OfficialPluginSettingsPage]，false 走通用表单。
 */
fun hasOfficialPluginSettingsPage(itemId: String): Boolean = OFFICIAL_PAGES.containsKey(itemId)

/** 一个命名空间的加载结果。 */
private sealed interface DocState {
    data object Loading : DocState
    data class Loaded(val document: PluginSettingsDocument) : DocState
    data class Failed(val reason: String) : DocState
}

/**
 * 官方设置页：按字段表画出控件，保存时按命名空间回写 Host。
 *
 * @param itemId 官方设置页的注册 id。
 * @param onBack 返回插件页。
 * @param loadDocument 读取命名空间表单。
 * @param saveDocument 回写命名空间。
 */
@Composable
fun OfficialPluginSettingsPage(
    itemId: String,
    onBack: () -> Unit,
    loadDocument: suspend (String) -> PluginSettingsDocument?,
    saveDocument: suspend (String, JsonObject, Int) -> Boolean,
) {
    val spec = OFFICIAL_PAGES[itemId] ?: return
    OfficialSettingsScaffold(spec, onBack, loadDocument, saveDocument)
}

/**
 * 通用页面框架：标题、说明、字段、保存。
 * @param spec 页面字段表。
 * @param onBack 返回插件页（系统返回键同样走这里）。
 * @param loadDocument 读取命名空间表单。
 * @param saveDocument 回写命名空间。
 */
@Composable
private fun OfficialSettingsScaffold(
    spec: OfficialPageSpec,
    onBack: () -> Unit,
    loadDocument: suspend (String) -> PluginSettingsDocument?,
    saveDocument: suspend (String, JsonObject, Int) -> Boolean,
) {
    // 系统返回先关这一页，而不是退出插件页。
    BackHandler(onBack = onBack)
    val colors = LocalDshPanelColors.current
    var docs by remember(spec) { mutableStateOf<Map<String, DocState>>(emptyMap()) }
    var reloadKey by remember(spec) { mutableStateOf(0) }
    // 编辑中的值：键是 "ns\u0000key"，避免两个命名空间的同名字段互相覆盖。
    val edits = remember(spec) { mutableStateMapOf<String, String>() }
    var saving by remember(spec) { mutableStateOf(false) }
    var saveError by remember(spec) { mutableStateOf<String?>(null) }

    LaunchedEffect(spec, reloadKey) {
        val loaded = mutableMapOf<String, DocState>()
        spec.namespaces.forEach { ns ->
            val document = runCatching { loadDocument(ns) }.getOrNull()
            loaded[ns] = if (document == null) {
                DocState.Failed("这个插件当前没有加载，暂时无法配置。")
            } else {
                DocState.Loaded(document)
            }
        }
        docs = loaded
    }

    fun docOf(ns: String): PluginSettingsDocument? = (docs[ns] as? DocState.Loaded)?.document

    /** 搜索 API 开关的当前值：`web` 的两个 provider 都指向 Search/Fetch API 时算开启。 */
    fun searchApiOn(): Boolean {
        val web = docOf("web") ?: return false
        val search = (web.value["searchProvider"] as? JsonPrimitive)?.contentOrNull
        val fetch = (web.value["fetchProvider"] as? JsonPrimitive)?.contentOrNull
        return search == "deepseek-search-api" && fetch == "deepseek-fetch-api"
    }

    /** 字段当前显示的值：改过的优先，否则取 Host 的值，再否则取 schema 默认。 */
    fun valueOf(field: OfficialField): String {
        edits["${field.ns}\u0000${field.key}"]?.let { return it }
        val document = docOf(field.ns) ?: return ""
        return textOf(document.value[field.key] ?: defaultOf(document.schema, field.key))
    }

    /** 开关当前值：改过的优先，否则取 Host 的值。 */
    fun toggleOf(field: OfficialField): Boolean {
        edits["${field.ns}\u0000${field.key}"]?.let { return it == "true" }
        val document = docOf(field.ns) ?: return false
        return (document.value[field.key] as? JsonPrimitive)?.booleanOrNull
            ?: ((defaultOf(document.schema, field.key) as? JsonPrimitive)?.booleanOrNull ?: false)
    }

    fun edit(field: OfficialField, text: String) {
        edits["${field.ns}\u0000${field.key}"] = text
        saveError = null
    }

    // 保存：一个命名空间一次 update，revision 用读到的版本；遇到失败就停下并保留
    // 用户改动，让用户看到 Host 的拒绝原因（官方 `saveFailed`）。
    LaunchedEffect(saving) {
        if (!saving) return@LaunchedEffect
        saveError = null
        runCatching {
            val byNamespace = mutableMapOf<String, MutableMap<String, JsonElement>>()
            if (spec.searchApiToggle) {
                edits["web\u0000searchApi"]?.let { flag ->
                    val on = flag == "true"
                    byNamespace.getOrPut("web") { mutableMapOf() }["searchProvider"] =
                        JsonPrimitive(if (on) "deepseek-search-api" else "deepseek-official")
                    byNamespace.getOrPut("web") { mutableMapOf() }["fetchProvider"] =
                        JsonPrimitive(if (on) "deepseek-fetch-api" else "http")
                }
            }
            spec.fields.forEach { field ->
                val text = edits["${field.ns}\u0000${field.key}"] ?: return@forEach
                val value: JsonElement = when (field.kind) {
                    OfficialFieldKind.NUMBER -> {
                        val number = text.trim().toDoubleOrNull()
                            ?: throw IllegalStateException("请输入数字：${field.label}")
                        JsonPrimitive(
                            if (number == number.toLong().toDouble()) number.toLong() else number,
                        )
                    }
                    OfficialFieldKind.TEXT -> JsonPrimitive(text)
                    OfficialFieldKind.TOGGLE -> JsonPrimitive(text == "true")
                }
                byNamespace.getOrPut(field.ns) { mutableMapOf() }[field.key] = value
            }
            byNamespace.forEach { (ns, patch) ->
                val revision = docOf(ns)?.revision ?: 0
                if (!saveDocument(ns, JsonObject(patch), revision)) {
                    throw IllegalStateException("本部署没有接受这些值，已保留供你修改。")
                }
            }
        }.fold(
            onSuccess = {
                edits.clear()
                reloadKey += 1
            },
            onFailure = { error -> saveError = error.message ?: "保存失败" },
        )
        saving = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PluginsDimens.PageHorizontal, vertical = PluginsDimens.PageTop),
    ) {
        Text(spec.title, color = colors.labelPrimary, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        if (spec.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(spec.description, color = colors.labelSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(20.dp))

        val loading = spec.namespaces.any { docs[it] == null || docs[it] is DocState.Loading }
        val failed = spec.namespaces.firstNotNullOfOrNull { (docs[it] as? DocState.Failed)?.reason }
        when {
            loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("正在读取设置…", color = colors.labelTertiary, fontSize = 13.sp)
            }

            failed != null -> Text(failed, color = colors.labelTertiary, fontSize = 13.sp, lineHeight = 19.sp)

            else -> {
                // 网页搜索的开关决定显示哪一组字段：开＝Search/Fetch API，关＝Messages。
                val searchApiOn = edits["web\u0000searchApi"]?.let { it == "true" } ?: searchApiOn()
                if (spec.searchApiToggle) {
                    ToggleRow(
                        label = "启用新版 Web 搜索 API",
                        hint = "默认关闭，搜索通过 DeepSeek Messages 完成。",
                        checked = searchApiOn,
                        onChange = { next -> edit(webSearchToggleField(), if (next) "true" else "false") },
                    )
                }
                // 分组标题只在第一次出现时打印一次：官方页面同样按小节组织字段。
                var currentGroup: String? = null
                spec.fields
                    .filter { field ->
                        if (!spec.searchApiToggle) return@filter true
                        (field.ns == "web-deepseek-api") == searchApiOn
                    }
                    .forEach { field ->
                        if (field.group != currentGroup) {
                            currentGroup = field.group
                            if (field.group != null) {
                                Spacer(Modifier.height(20.dp))
                                Text(field.group, color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        when (field.kind) {
                            OfficialFieldKind.NUMBER, OfficialFieldKind.TEXT -> FieldEditor(
                                label = field.label,
                                hint = field.hint,
                                value = valueOf(field),
                                numeric = field.kind == OfficialFieldKind.NUMBER,
                                onChange = { text -> edit(field, text) },
                            )

                            OfficialFieldKind.TOGGLE -> ToggleRow(
                                label = field.label,
                                hint = field.hint,
                                checked = toggleOf(field),
                                onChange = { next -> edit(field, if (next) "true" else "false") },
                            )
                        }
                    }
                SubagentModelListNote(spec, docOf("subagent-model-selection-settings"), edits)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (saving) colors.bgModulePlatform else colors.buttonPrimaryFill)
                            .clickable(enabled = !saving && edits.isNotEmpty()) { saving = true },
                    ) {
                        Text(
                            text = if (saving) "保存中…" else "保存",
                            color = if (saving) colors.labelTertiary else colors.labelPrimaryForeground,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    val status = when {
                        saveError != null -> saveError
                        edits.isEmpty() -> "已保存"
                        else -> "有未保存的修改"
                    }
                    Text(
                        text = status ?: "",
                        color = if (saveError != null) colors.stateErrorPrimary else colors.labelTertiary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** 网页搜索开关在编辑表里的伪字段：它不是某个命名空间的键，而是 `web` 的两个选择。 */
private fun webSearchToggleField(): OfficialField = OfficialField(
    ns = "web",
    key = "searchApi",
    label = "启用新版 Web 搜索 API",
    hint = null,
    kind = OfficialFieldKind.TOGGLE,
)

/**
 * 子智能体页的模型列表说明：官方在开关打开时列出「Agent 可选择的模型」。
 * 模型目录只在 Host 的 `remote.models`，bridge 没有这个接口，所以如实说明。
 */
@Composable
private fun SubagentModelListNote(
    spec: OfficialPageSpec,
    modelSelectionDoc: PluginSettingsDocument?,
    edits: Map<String, String>,
) {
    if (spec.fields.none { it.ns == "subagent-model-selection-settings" }) return
    val colors = LocalDshPanelColors.current
    val on = edits["subagent-model-selection-settings\u0000enabled"]?.let { it == "true" }
        ?: ((modelSelectionDoc?.value?.get("enabled") as? JsonPrimitive)?.booleanOrNull ?: false)
    if (!on) return
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Agent 可选模型列表需要在网页版设置页里编辑；已保存的授权模型会保留。",
        color = colors.labelTertiary,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    )
}

/** 一个数字/文本字段：标题、说明与输入框，对应官方 `SettingsValueField`。 */
@Composable
private fun FieldEditor(
    label: String,
    hint: String?,
    value: String,
    numeric: Boolean,
    onChange: (String) -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = colors.labelPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(hint, color = colors.labelTertiary, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 一个开关行：标题 + 说明，右侧开关，对应官方 `Switch` 行。 */
@Composable
private fun ToggleRow(
    label: String,
    hint: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = colors.labelPrimary, fontSize = 13.sp)
                if (!hint.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(hint, color = colors.labelTertiary, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.size(0.dp))
        DshSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** schema 里某个字段声明的默认值（schemastery 的 `{uid, refs}` 形态）。 */
private fun defaultOf(schema: JsonObject, key: String): JsonElement? {
    val refs = schema["refs"] as? JsonObject ?: return null
    val rootUid = (schema["uid"] as? JsonPrimitive)?.contentOrNull ?: return null
    val root = refs[rootUid] as? JsonObject ?: return null
    val members = root["dict"] as? JsonObject ?: return null
    val entry = members[key] ?: return null
    val field = when (entry) {
        is JsonPrimitive -> refs[entry.contentOrNull ?: return null] as? JsonObject
        is JsonObject -> entry
        else -> null
    } ?: return null
    return (field["meta"] as? JsonObject)?.get("default")
}

/** 把 JSON 值抖成输入框文本：数字去掉多余的 `.0`，字符串原样，布尔转 true/false。 */
private fun textOf(value: JsonElement?): String = when (value) {
    null -> ""
    is JsonPrimitive -> value.contentOrNull.orEmpty().let { text ->
        if (value.isString) text else text.removeSuffix(".0")
    }

    else -> value.toString()
}
