package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.PluginSettingsDocument
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 一个插件设置页：把 Host 投影出来的表单画成原生控件，并把改动写回 Host。
 *
 * 字段完全来自插件的 `Config` schema，所以插件更新字段后这里不需要改代码；无法
 * 用当前控件表达的类型会显式列出，而不是悄悄丢掉。
 *
 * @param ns 设置命名空间。
 * @param title 页面标题（Host 的 row meta）。
 * @param description 页面说明。
 * @param loadDocument 读取表单与当前值。
 * @param saveDocument 回写字段，`expectedRevision` 用于冲突检测。
 * @param onBack 返回设置列表。
 */
@Composable
fun PluginSettingsPage(
    ns: String,
    title: String,
    description: String?,
    loadDocument: suspend (String) -> PluginSettingsDocument?,
    saveDocument: suspend (String, JsonObject, Int) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var document by remember(ns) { mutableStateOf<PluginSettingsDocument?>(null) }
    var loading by remember(ns) { mutableStateOf(true) }
    var failure by remember(ns) { mutableStateOf<String?>(null) }
    var saving by remember(ns) { mutableStateOf(false) }
    var saved by remember(ns) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ns) {
        loading = true
        failure = null
        document = runCatching { loadDocument(ns) }.getOrNull()
        loading = false
        if (document == null) failure = "暂时读不到这个插件的设置"
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回设置",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        when {
            loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("正在读取设置…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            document == null -> Text(
                text = failure ?: "暂时读不到这个插件的设置",
                modifier = Modifier.padding(20.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                val doc = document!!
                val fields = formFieldsOf(doc.schema)
                val edits = remember(doc.ns, doc.revision) { mutableStateMapOf<String, JsonElement>() }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (fields.isEmpty()) {
                        Text(
                            text = "这个插件没有可视化配置项。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    fields.forEach { field ->
                        val current = edits[field.name]
                            ?: doc.value[field.name]
                            ?: field.defaultValue
                        SettingField(
                            field = field,
                            value = current,
                            onChange = { edits[field.name] = it; saved = false },
                        )
                    }
                    if (fields.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(enabled = !saving && edits.isNotEmpty()) {
                                    scope.launch {
                                        saving = true
                                        saved = saveDocument(doc.ns, JsonObject(edits.toMap()), doc.revision)
                                        saving = false
                                        if (saved) {
                                            document = runCatching { loadDocument(ns) }.getOrNull() ?: doc
                                            edits.clear()
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    text = if (saving) "保存中…" else "保存",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                )
                            }
                            if (saved) {
                                Text("已保存", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            } else if (edits.isNotEmpty()) {
                                Text("有未保存的修改", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 一个可编辑字段的标题、类型与默认值。 */
private data class SettingField(
    val name: String,
    val kind: FieldKind,
    val description: String?,
    val defaultValue: JsonElement?,
    val options: List<String> = emptyList(),
)

/** 字段种类；只覆盖 Host 表单里实际出现的类型。 */
private enum class FieldKind { BOOLEAN, TEXT, NUMBER, ENUM, UNSUPPORTED }

/**
 * 从 Host 的表单 JSON 里取顶层字段。
 *
 * schemastery 的投影用 `dict` 表示对象成员；为兼容 `properties` 两种写法都接受。
 * @param schema 表单 JSON。
 * @returns 字段定义，顺序与声明一致。
 */
private fun formFieldsOf(schema: JsonObject): List<SettingField> {
    val members = (schema["dict"] ?: schema["properties"])?.jsonObjectOrNull() ?: return emptyList()
    return members.map { (name, node) ->
        val object1 = node.jsonObjectOrNull() ?: return@map SettingField(name, FieldKind.UNSUPPORTED, null, null)
        val type = object1["type"]?.jsonPrimitiveOrNull()?.contentOrNull
        val description = object1["description"]?.jsonPrimitiveOrNull()?.contentOrNull
        val default = object1["default"]
        when (type) {
            "boolean" -> SettingField(name, FieldKind.BOOLEAN, description, default)
            "string", "text" -> SettingField(name, FieldKind.TEXT, description, default)
            "number", "natural", "percent" -> SettingField(name, FieldKind.NUMBER, description, default)
            "union" -> {
                val options = (object1["list"] ?: object1["options"])?.jsonArrayOrNull()
                    ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull }
                    .orEmpty()
                if (options.isEmpty()) {
                    SettingField(name, FieldKind.UNSUPPORTED, description, default)
                } else {
                    SettingField(name, FieldKind.ENUM, description, default, options)
                }
            }
            else -> SettingField(name, FieldKind.UNSUPPORTED, description, default)
        }
    }
}

/** 一个字段的编辑器。 */
@Composable
private fun SettingField(
    field: SettingField,
    value: JsonElement?,
    onChange: (JsonElement) -> Unit,
) {
    when (field.kind) {
        FieldKind.BOOLEAN -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(field.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                field.description?.let {
                    Text(it, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(
                checked = value?.jsonPrimitiveOrNull()?.booleanOrNull ?: false,
                onCheckedChange = { onChange(JsonPrimitive(it)) },
            )
        }

        FieldKind.NUMBER, FieldKind.TEXT -> {
            val text = value?.jsonPrimitiveOrNull()?.contentOrNull
                ?.takeUnless { it == "null" }
                .orEmpty()
            OutlinedTextField(
                value = text,
                onValueChange = { next ->
                    if (field.kind == FieldKind.NUMBER) {
                        next.toDoubleOrNull()?.let { onChange(JsonPrimitive(it)) }
                    } else {
                        onChange(JsonPrimitive(next))
                    }
                },
                label = { Text(field.name, fontSize = 13.sp) },
                supportingText = field.description?.let { { Text(it, fontSize = 11.sp) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (field.kind == FieldKind.NUMBER) KeyboardType.Number else KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FieldKind.ENUM -> Column {
            Text(field.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            field.description?.let {
                Text(it, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { option ->
                    val selected = value?.jsonPrimitiveOrNull()?.contentOrNull == option
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onChange(JsonPrimitive(option)) },
                    ) {
                        Text(
                            text = option,
                            fontSize = 12.sp,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        FieldKind.UNSUPPORTED -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Text(
                text = "${field.name}：这类字段暂时还不能在这里编辑（${field.description ?: "复杂类型"}）",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Default,
            )
        }
    }
}

/** 宽松的对象转换：Host 的表单节点一定是对象，缺省时返回 null。 */
private fun JsonElement?.jsonObjectOrNull(): JsonObject? = (this as? JsonObject)

/** 宽松的原始值转换。 */
private fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }

/** 宽松的数组转换。 */
private fun JsonElement?.jsonArrayOrNull(): List<JsonElement>? =
    (this as? kotlinx.serialization.json.JsonArray)
