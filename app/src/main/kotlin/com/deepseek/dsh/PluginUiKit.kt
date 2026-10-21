package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.model.PluginSettingsDocument
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** 插件原生 UI 里一个字段的控件形态。 */
internal enum class PluginFieldKind {
    /** 单行文本。 */
    TEXT,

    /** 数字。 */
    NUMBER,

    /** 几个固定选项（官方页面里的下拉/单选）。 */
    CHOICE,
}

/**
 * 插件原生 UI 的一个字段。
 *
 * @param key 设置文档里的字段名（官方 `Config` schema 的键）。
 * @param label 中文标签，取自官方 locale。
 * @param hint 官方页面里的说明文字，可为空。
 * @param kind 控件形态。
 * @param choices CHOICE 的候选项：值 → 显示文案。
 * @param group 小节标题；只有同一小节的第一项需要写。
 */
internal data class PluginField(
    val key: String,
    val label: String,
    val hint: String? = null,
    val kind: PluginFieldKind = PluginFieldKind.TEXT,
    val choices: List<Pair<String, String>> = emptyList(),
    val group: String? = null,
)

/**
 * 一个设置命名空间的通用原生表单：官方页面里由 `SettingsForm` 承担的那部分。
 *
 * 读一次文档、按字段画控件、保存时把改动按命名空间回写（带 revision）。
 * 每个插件文件只描述自己的字段与文案，不再重复表单机制。
 *
 * @param namespace Host 设置命名空间。
 * @param hooks 读写 Host 的接口。
 * @param fields 字段表，顺序即页面顺序。
 * @param notices 表单下方的说明段落（官方页面的付费/隐私提示等）。
 * @param emptyHint 读不到文档时的说明。
 */
@Composable
internal fun PluginSettingsSection(
    namespace: String,
    hooks: PluginUiHooks,
    fields: List<PluginField>,
    notices: List<String> = emptyList(),
    emptyHint: String = "",
) {
    val colors = LocalDshPanelColors.current
    var document by remember(namespace) { mutableStateOf<PluginSettingsDocument?>(null) }
    var loaded by remember(namespace) { mutableStateOf(false) }
    val edits = remember(namespace) { mutableStateMapOf<String, String>() }
    var saving by remember(namespace) { mutableStateOf(false) }
    var status by remember(namespace) { mutableStateOf<String?>(null) }
    var reload by remember(namespace) { mutableStateOf(0) }
    val numberRequiredLabel = stringResource(R.string.settings_number_required, "")
    val savedLabel = stringResource(R.string.settings_saved)
    val rejectedLabel = stringResource(R.string.settings_deployment_rejected)

    LaunchedEffect(namespace, reload) {
        loaded = false
        // 插件刚启用时 Host 可能还没挂上它的设置命名空间（bridge 的 describe 会回
        // "no configurable settings document"）。读不到就退避重试，避免停在官方的
        //「暂不可用」文案上不再刷新。
        repeat(20) {
            document = runCatching { hooks.loadDocument(namespace) }.getOrNull()
            loaded = true
            if (document != null) return@LaunchedEffect
            kotlinx.coroutines.delay(700)
        }
    }

    fun valueOf(field: PluginField): String {
        edits[field.key]?.let { return it }
        val doc = document ?: return ""
        return primitiveText(doc.value[field.key])
    }

    fun edit(field: PluginField, text: String) {
        edits[field.key] = text
        status = null
    }

    val doc = document
    when {
        !loaded -> Text(stringResource(R.string.settings_loading), color = colors.labelTertiary, fontSize = 13.sp)
        doc == null -> Text(emptyHint, color = colors.labelTertiary, fontSize = 13.sp, lineHeight = 19.sp)
        else -> {
            var currentGroup: String? = null
            fields.forEach { field ->
                if (field.group != currentGroup) {
                    currentGroup = field.group
                    if (field.group != null) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = field.group,
                            color = colors.labelPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                when (field.kind) {
                    PluginFieldKind.CHOICE -> ChoiceField(
                        field = field,
                        selected = valueOf(field),
                        onSelect = { edit(field, it) },
                    )
                    PluginFieldKind.NUMBER, PluginFieldKind.TEXT -> TextField(
                        field = field,
                        value = valueOf(field),
                        numeric = field.kind == PluginFieldKind.NUMBER,
                        onChange = { edit(field, it) },
                    )
                }
            }
            notices.forEach { notice ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = notice,
                    color = colors.labelTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (saving) colors.bgModulePlatform else colors.buttonPrimaryFill)
                        .clickable(enabled = !saving && edits.isNotEmpty()) { saving = true },
                ) {
                    Text(
                        text = if (saving) stringResource(R.string.settings_saving) else stringResource(R.string.settings_save),
                        color = if (saving) colors.labelTertiary else colors.labelPrimaryForeground,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                Text(
                    text = status ?: if (edits.isEmpty()) savedLabel else stringResource(R.string.settings_unsaved_changes),
                    color = colors.labelTertiary,
                    fontSize = 12.sp,
                )
            }
            LaunchedEffect(saving) {
                if (!saving) return@LaunchedEffect
                val patch = mutableMapOf<String, JsonElement>()
                fields.forEach { field ->
                    val text = edits[field.key] ?: return@forEach
                    patch[field.key] = when (field.kind) {
                        PluginFieldKind.NUMBER -> {
                            val number = text.trim().toDoubleOrNull()
                            if (number == null) {
                                status = numberRequiredLabel + field.label
                                saving = false
                                return@LaunchedEffect
                            }
                            JsonPrimitive(if (number == number.toLong().toDouble()) number.toLong() else number)
                        }
                        PluginFieldKind.TEXT, PluginFieldKind.CHOICE -> JsonPrimitive(text)
                    }
                }
                val ok = runCatching {
                    hooks.saveDocument(namespace, JsonObject(patch), doc.revision)
                }.getOrDefault(false)
                saving = false
                status = if (ok) {
                    edits.clear()
                    reload += 1
                    savedLabel
                } else {
                    rejectedLabel
                }
            }
        }
    }
}

/**
 * 选项胶囊组：按可用宽度自动换行。
 *
 * 官方设置页的下拉/单选在手机上换成胶囊；像「DeepSeek Flash · 官方 API（付费）」
 * 这种长标签用单行 Row 会被压成竖排，所以这里用 FlowRow 让它们按整块宽度换行。
 *
 * @param options 值 → 显示文案。
 * @param selected 当前值。
 * @param enabled 是否可点。
 * @param onSelect 选中回调。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceChipFlow(
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    val colors = LocalDshPanelColors.current
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) colors.buttonPrimaryFill else colors.bgModulePlatform)
                    .border(
                        0.5.dp,
                        if (active) colors.buttonPrimaryFill else colors.borderL2,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable(enabled = enabled) { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    color = if (active) colors.labelPrimaryForeground else colors.labelSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 一个 CHOICE 字段：等宽选项条。 */
@Composable
private fun ChoiceField(field: PluginField, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(field.label, color = colors.labelPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (!field.hint.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(field.hint, color = colors.labelTertiary, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        ChoiceChipFlow(options = field.choices, selected = selected, onSelect = onSelect)
    }
}

/** 一个 TEXT/NUMBER 字段：标题、说明与输入框。 */
@Composable
private fun TextField(field: PluginField, value: String, numeric: Boolean, onChange: (String) -> Unit) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(field.label, color = colors.labelPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (!field.hint.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(field.hint, color = colors.labelTertiary, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 文档里某个值的展示文本：字符串原样，布尔转 true/false，数字去掉多余 `.0`。 */
private fun primitiveText(value: JsonElement?): String = when (value) {
    null -> ""
    is JsonPrimitive -> value.booleanOrNull?.toString() ?: value.contentOrNull.orEmpty()
    else -> value.toString()
}
