package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshSwitch
import com.deepseek.dsh.core.designsystem.DshTextInput
import com.deepseek.dsh.core.designsystem.dshPanelColors
import com.deepseek.dsh.core.model.PluginSettingsDocument
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 插件设置命名空间：Host 的一个可配置文档。
 *
 * 来源是 bridge 的 `host-slots/list`：每个命名空间都是插件自己 `Config` schema
 * 的一份投影，客户端只负责画字段与回写，不重新定义字段。
 */
internal data class SettingsNamespace(
    /** Host 设置命名空间（插件 `Config` 的 id，例如 `subagent`）。 */
    val ns: String,
    /** Host 给的标题；没有时用命名空间自身。 */
    val title: String,
    val description: String,
    /** 提供这个命名空间的插件模块名。 */
    val module: String?,
)

/**
 * 「内置插件配置」里的一行。
 *
 * @property namespaces 这一行背后要渲染的 Host 设置文档；为空表示官方页面用了
 *   自定义界面、没有可对应的文档，打开时回落到「全部可配置项」。
 */
internal data class ConfigEntryRow(
    val id: String,
    val title: String,
    val description: String,
    val iconSvg: String?,
    val namespaces: List<String>,
    /** 是否官方 `plugins.item` 条目（决定图标来源）。 */
    val official: Boolean,
)

/**
 * 设置表单的一个节点。
 *
 * Host 把插件的 `Config` schema 投影成「节点表 + uid 引用」：根节点在
 * `refs[root.uid]`，子节点用数字 uid 指向同一张表。这里把引用解开成树。
 */
internal data class FormNode(
    val type: String,
    val meta: JsonObject,
    val dict: List<Pair<String, FormNode>>,
    val list: List<FormNode>,
    val inner: FormNode?,
    /** `const` 节点的取值。 */
    val constValue: JsonElement?,
)

/** 解析一份 Host 投影出来的表单 schema；不可解析时返回 null。 */
internal fun parseSettingsForm(schema: JsonObject): FormNode? {
    val refs = schema["refs"] as? JsonObject
    val rootUid = (schema["uid"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    if (refs != null && rootUid != null) {
        return resolveFormNode(rootUid, refs, 0)
    }
    // 没有 uid/refs 的文档本身就是节点（字段直接内联）。
    return inlineFormNode(schema, 0)
}

private fun resolveFormNode(uid: Int, refs: JsonObject, depth: Int): FormNode? {
    if (depth > 16) return null
    val raw = refs[uid.toString()] as? JsonObject ?: return null
    return inlineFormNode(raw, depth, refs)
}

private fun inlineFormNode(raw: JsonObject, depth: Int, refs: JsonObject? = null): FormNode? {
    if (depth > 16) return null
    fun child(element: JsonElement?): FormNode? {
        val obj = element as? JsonObject ?: return null
        return inlineFormNode(obj, depth + 1, refs)
    }
    fun referenced(element: JsonElement?): FormNode? {
        val uid = (element as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return null
        val table = refs ?: return null
        return resolveFormNode(uid, table, depth + 1)
    }
    val type = (raw["type"] as? JsonPrimitive)?.contentOrNull ?: "unknown"
    val meta = raw["meta"] as? JsonObject ?: JsonObject(emptyMap())
    val dict = (raw["dict"] as? JsonObject)?.mapNotNull { (name, element) ->
        (referenced(element) ?: child(element))?.let { name to it }
    }.orEmpty()
    val list = (raw["list"] as? JsonArray)?.mapNotNull { referenced(it) ?: child(it) }.orEmpty()
    val inner = referenced(raw["inner"]) ?: child(raw["inner"])
    return FormNode(
        type = type,
        meta = meta,
        dict = dict,
        list = list,
        inner = inner,
        constValue = raw["value"],
    )
}

/** 读取节点在文档里的当前值。 */
private fun valueAt(root: JsonElement?, path: List<String>): JsonElement? {
    var current: JsonElement? = root
    for (segment in path) {
        val obj = current as? JsonObject ?: return null
        current = obj[segment] ?: return null
    }
    return current
}

/** 把 [value] 按 [path] 写进一份新的 patch 对象（逐层建中间对象）。 */
private fun putAtPath(target: MutableMap<String, JsonElement>, path: List<String>, value: JsonElement) {
    if (path.isEmpty()) return
    val head = path.first()
    if (path.size == 1) {
        target[head] = value
        return
    }
    val nested = mutableMapOf<String, JsonElement>()
    putAtPath(nested, path.drop(1), value)
    target[head] = JsonObject(nested)
}

/**
 * 插件设置表单页。
 *
 * 一页可以承载多个命名空间（官方一个设置页面背后常常是好几份文档）。每份文档
 * 单独渲染、单独保存：保存走 bridge 的 `settings/update`，带上打开时的 revision，
 * Host 用它拒绝覆盖别人的修改。
 *
 * @param title 页面标题（顶栏已由 App 的 [PageBackBar] 提供，这里只是内容标题）。
 * @param namespaces 要渲染的 Host 设置命名空间。
 * @param load 读取一份文档。
 * @param save 回写字段补丁。
 * @param modifier 外部修饰。
 */
@Composable
internal fun PluginSettingsEditorPage(
    title: String,
    namespaces: List<String>,
    load: suspend (String) -> PluginSettingsDocument?,
    save: suspend (String, JsonObject, Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = dshPanelColors()
    var documents by remember(namespaces) { mutableStateOf<List<PluginSettingsDocument>?>(null) }
    var failed by remember(namespaces) { mutableStateOf(0) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(namespaces, reload) {
        documents = null
        failed = 0
        var missing = 0
        val loaded = mutableListOf<PluginSettingsDocument>()
        for (ns in namespaces) {
            val document = load(ns)
            if (document == null) missing += 1 else loaded += document
        }
        failed = missing
        documents = loaded
    }
    Column(modifier = modifier.fillMaxSize().background(colors.bgBase)) {
        Text(
            text = title,
            color = colors.labelPrimary,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = PluginsDimens.PageHorizontal,
                end = PluginsDimens.PageHorizontal,
                top = PluginsDimens.PageTop,
                bottom = 4.dp,
            ),
        )
        val loaded = documents
        if (loaded == null) {
            Text(
                text = stringResource(R.string.settings_loading),
                color = colors.labelTertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(PluginsDimens.PageHorizontal, 16.dp),
            )
            return@Column
        }
        if (loaded.isEmpty()) {
            Text(
                text = if (failed > 0) stringResource(R.string.settings_no_docs) else stringResource(R.string.settings_no_fields),
                color = colors.labelTertiary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(PluginsDimens.PageHorizontal, 16.dp),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PluginsDimens.PageHorizontal,
                end = PluginsDimens.PageHorizontal,
                top = 8.dp,
                bottom = PluginsDimens.PageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(loaded, key = { it.ns }) { document ->
                SettingsDocumentEditor(
                    document = document,
                    save = save,
                    onSaved = { reload += 1 },
                )
            }
        }
    }
}

/** 一份设置文档的编辑器：字段 + 保存。 */
@Composable
private fun SettingsDocumentEditor(
    document: PluginSettingsDocument,
    save: suspend (String, JsonObject, Int) -> Boolean,
    onSaved: () -> Unit,
) {
    val colors = dshPanelColors()
    val scope = rememberCoroutineScope()
    val form = remember(document.schema) { parseSettingsForm(document.schema) }
    // 编辑过的字段，键是路径（用 \u0000 连接）。值为 null 表示这一字段没被改。
    val edits = remember(document.ns, document.revision) { mutableStateMapOf<String, JsonElement>() }
    // 密钥字段：只回显「已配置」，新值由用户输入，留空表示保持不变。
    val secretPaths = remember(document.secretSlots) {
        document.secretSlots.filter { it.set }
            .map { it.path.joinToString(PATH_SEPARATOR) }
            .toSet()
    }
    val secretDrafts = remember(document.ns, document.revision) {
        mutableStateMapOf<String, String>()
    }
    var status by remember(document.ns, document.revision) { mutableStateOf<String?>(null) }
    var saving by remember(document.ns) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = document.ns,
            color = colors.labelSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (form == null || form.dict.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_no_projected_fields),
                color = colors.labelTertiary,
                fontSize = 12.sp,
            )
        } else {
            FormFields(
                node = form,
                path = emptyList(),
                documentValue = document.value,
                edits = edits,
                secretPaths = secretPaths,
                secretDrafts = secretDrafts,
            )
        }
        val label = when {
            saving -> stringResource(R.string.settings_saving)
            status != null -> status!!
            else -> stringResource(R.string.settings_save)
        }
        val noChangesLabel = stringResource(R.string.settings_no_changes)
        val savedLabel = stringResource(R.string.settings_saved_host)
        val saveFailedLabel = stringResource(R.string.settings_save_failed)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (saving) colors.bgModulePlatform else colors.buttonPrimaryFill)
                .clickable(enabled = !saving) {
                    if (edits.isEmpty()) {
                        status = noChangesLabel
                        return@clickable
                    }
                    val patch = editsPatch(edits)
                    if (patch.isEmpty()) {
                        status = noChangesLabel
                        return@clickable
                    }
                    saving = true
                    status = null
                    scope.launch {
                        val ok = runCatching { save(document.ns, patch, document.revision) }
                            .getOrDefault(false)
                        saving = false
                        status = if (ok) savedLabel else saveFailedLabel
                        if (ok) onSaved()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (saving) colors.labelSecondary else colors.labelPrimaryForeground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 把某份文档的编辑合成一个 patch 对象。 */
private fun editsPatch(edits: Map<String, JsonElement>): JsonObject {
    val patch = mutableMapOf<String, JsonElement>()
    for ((key, value) in edits) {
        val path = key.split(PATH_SEPARATOR).filter { it.isNotEmpty() }
        putAtPath(patch, path, value)
    }
    return JsonObject(patch)
}

private const val PATH_SEPARATOR = "\u0000"

/** 递归渲染对象字段。 */
@Composable
private fun FormFields(
    node: FormNode,
    path: List<String>,
    documentValue: JsonObject,
    edits: MutableMap<String, JsonElement>,
    secretPaths: Set<String>,
    secretDrafts: MutableMap<String, String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((name, child) in node.dict) {
            FormField(
                name = name,
                node = child,
                path = path + name,
                documentValue = documentValue,
                edits = edits,
                secretPaths = secretPaths,
                secretDrafts = secretDrafts,
            )
        }
    }
}

/** 一个字段：按 Host 的字段类型选原生控件。 */
@Composable
private fun FormField(
    name: String,
    node: FormNode,
    path: List<String>,
    documentValue: JsonObject,
    edits: MutableMap<String, JsonElement>,
    secretPaths: Set<String>,
    secretDrafts: MutableMap<String, String>,
) {
    val colors = dshPanelColors()
    val key = path.joinToString(PATH_SEPARATOR)
    val current = edits[key] ?: valueAt(documentValue, path) ?: node.meta["default"]
    val label = (node.meta["description"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: name
    when (node.type) {
        "object" -> {
            Text(
                text = label,
                color = colors.labelSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgModulePlatform)
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((childName, child) in node.dict) {
                        FormField(
                            name = childName,
                            node = child,
                            path = path + childName,
                            documentValue = documentValue,
                            edits = edits,
                            secretPaths = secretPaths,
                            secretDrafts = secretDrafts,
                        )
                    }
                }
            }
        }
        "boolean" -> {
            val checked = (current as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = label,
                    color = colors.labelPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                DshSwitch(
                    checked = checked,
                    onCheckedChange = { edits[key] = JsonPrimitive(it) },
                )
            }
        }
        "string" -> {
            val secret = (node.meta["role"] as? JsonPrimitive)?.contentOrNull == "secret"
            if (secret) {
                // 密钥：Host 已剥离真实值，这里只显示「已配置」掩码；输入新值即替换，
                // 留空表示保持不变（与官方 ui-secret-mask 的语义一致）。
                val configured = key in secretPaths
                val draft = secretDrafts[key].orEmpty()
                FieldLabel(label, colors.labelSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (configured) "••••••••" else "—",
                        color = colors.labelTertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.width(84.dp),
                    )
                    DshTextInput(
                        value = draft,
                        onValueChange = { value ->
                            secretDrafts[key] = value
                            if (value.isBlank()) {
                                edits.remove(key)
                            } else {
                                edits[key] = JsonPrimitive(value)
                            }
                        },
                        placeholder = stringResource(
                            if (configured) R.string.settings_secret_configured_placeholder
                            else R.string.settings_secret_unset_placeholder,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                val text = (current as? JsonPrimitive)?.contentOrNull.orEmpty()
                FieldLabel(label, colors.labelSecondary)
                DshTextInput(
                    value = text,
                    onValueChange = { edits[key] = JsonPrimitive(it) },
                    placeholder = "",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        "number" -> {
            val text = (current as? JsonPrimitive)?.contentOrNull.orEmpty()
            FieldLabel(label, colors.labelSecondary)
            DshTextInput(
                value = text,
                onValueChange = { raw ->
                    val parsed = raw.trim().toLongOrNull()?.let { JsonPrimitive(it) }
                        ?: raw.trim().toDoubleOrNull()?.let { JsonPrimitive(it) }
                    if (parsed != null) edits[key] = parsed
                },
                placeholder = "",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        "union" -> {
            val options = node.list.mapNotNull { option ->
                val value = option.constValue ?: return@mapNotNull null
                val primitive = value as? JsonPrimitive ?: return@mapNotNull null
                primitive.contentOrNull?.let { it to value }
            }
            val selected = (current as? JsonPrimitive)?.contentOrNull
            FieldLabel(label, colors.labelSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((optionLabel, optionValue) in options) {
                    val active = optionLabel == selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) colors.buttonPrimaryFill else colors.bgModulePlatform)
                            .border(
                                0.5.dp,
                                if (active) colors.buttonPrimaryFill else colors.borderL2,
                                RoundedCornerShape(9.dp),
                            )
                            .clickable { edits[key] = optionValue }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = optionLabel,
                            color = if (active) colors.labelPrimaryForeground else colors.labelSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        "const" -> {
            Text(
                text = "$label：${(node.constValue as? JsonPrimitive)?.contentOrNull.orEmpty()}",
                color = colors.labelTertiary,
                fontSize = 12.sp,
            )
        }
        else -> {
            // 数组与未识别的字段：显示当前值，标注不可编辑，避免把编辑做成静默丢弃。
            FieldLabel(label, colors.labelSecondary)
            Text(
                text = (current?.toString() ?: stringResource(R.string.settings_not_set)).take(240),
                color = colors.labelTertiary,
                fontSize = 11.5.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 字段说明行。 */
@Composable
private fun FieldLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
