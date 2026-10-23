package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.deepseek.dsh.core.designsystem.DshDialogCloseButton
import com.deepseek.dsh.core.designsystem.DshHeadlessModal
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.model.AgentPresetGroupInfo

/**
 * Agent 预设（右侧面板 AgentPresetSection）。
 *
 * 结构：标题「Agent 预设」18/24 600 + 引导语 13/20 三级色 + 分组标题「内置」12/600
 * + 预设卡片列表（R20 + 0.5dp 描边，选中项为 module 底 + bluish-400 描边）。
 * 卡片正文 14/16/12：名称 15/21 600 + 状态 tag、右侧等宽预设 id、描述 13/20 二级色四行截断；
 * 卡脚为 0.5dp 上描边的整宽行（左侧「模式说明 / 如何使用」ghost 文字按钮 + 右侧 28dp 图标按钮），
 * 三个动作都打开同款对话框。创造模式入口依赖 Creator 流程，本应用不开放，故只有「内置」分组。
 *
 * 卡片的正文内边距是不对称的（14/16/12）且卡脚需要整宽描边，而共享的
 * [SettingsCard] / [SettingsSelectedCard] 只暴露对称内边距，因此这里传入 0 内边距，
 * 由卡片内部自己实现度量。
 */
@Composable
internal fun SettingsAgentPresetsPane(
    selectedPresetId: String,
    onSelectPreset: (String) -> Unit,
    presetGroups: List<AgentPresetGroupInfo> = emptyList(),
    onCreatePreset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var guide by remember { mutableStateOf<GuideDialog?>(null) }
    // 分组名 / 是 / 否 会在多个位置（分组标题、tag、配置弹窗）复用，这里统一取一次。
    val builtInGroup = stringResource(R.string.agent_update_group_builtin)
    val yesLabel = stringResource(R.string.agent_update_yes)
    val noLabel = stringResource(R.string.agent_update_no)
    val builtInPresets = builtInAgentPresets()
    // 预设是否存在/名称/说明都由 Host 决定；官方内置 i18n 只补 id 对应的
    // 详细文案（官方客户端同一套字符串）。
    val presets = presetGroups.map { group ->
        val builtIn = builtInPresets.firstOrNull { it.id == group.id }
        if (builtIn != null) builtIn else AgentPreset(
            id = group.id,
            name = group.name,
            description = group.description.orEmpty(),
            explanation = "",
            usage = "",
        )
    }
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
        item(key = "agent_presets_title") {
            SettingsPaneTitle(text = stringResource(R.string.settings_section_agent_presets), large = true)
        }
        item(key = "agent_presets_intro") {
            SettingsIntro(
                text = stringResource(R.string.settings_agent_presets_desc),
            )
        }
        item(key = "agent_presets_group") {
            SettingsGroupHeading(builtInGroup)
        }
        if (presets.isEmpty()) {
            item(key = "agent_presets_empty") {
                SettingsIntro(text = stringResource(R.string.agent_update_presets_empty))
            }
        }
        presets.forEach { preset ->
            item(key = preset.id) {
                val selected = preset.id == selectedPresetId
                // 弹窗在点击时打开（非 @Composable 回调），文案先在此读好再捕获。
                val explanationTitle = stringResource(
                    R.string.agent_update_dialog_title_explanation,
                    preset.name,
                )
                val usageTitle = stringResource(
                    R.string.agent_update_dialog_title_usage,
                    preset.name,
                )
                val configTitle = stringResource(
                    R.string.agent_update_dialog_title_config,
                    preset.name,
                )
                val configBody = listOf(
                    stringResource(R.string.agent_update_config_id, preset.id),
                    stringResource(R.string.agent_update_config_name, preset.name),
                    stringResource(R.string.agent_update_config_group, builtInGroup),
                    stringResource(
                        R.string.agent_update_config_default,
                        if (selected) yesLabel else noLabel,
                    ),
                ).joinToString(separator = "\n")
                AgentPresetCard(
                    preset = preset,
                    selected = selected,
                    onSelect = { onSelectPreset(preset.id) },
                    onExplain = {
                        guide = GuideDialog(title = explanationTitle, body = preset.explanation)
                    },
                    onUsage = {
                        guide = GuideDialog(title = usageTitle, body = preset.usage)
                    },
                    onViewConfig = {
                        guide = GuideDialog(title = configTitle, body = configBody)
                    },
                )
            }
        }
        item(key = "agent_presets_custom_group") {
            SettingsGroupHeading(stringResource(R.string.agent_presets_custom_group))
        }
        item(key = "agent_presets_create") {
            CreatePresetButton(onClick = onCreatePreset)
        }
    }
    val openGuide = guide
    if (openGuide != null) {
        PresetGuideDialog(
            title = openGuide.title,
            body = openGuide.body,
            onDismiss = { guide = null },
        )
    }
}

/** 预设卡：选中态用 module 底 + bluish-400 描边，卡脚提供说明 / 用法 / 配置三个动作。 */
/** 官方「让 Agent 帮我创建预设模式」：虚线描边的整宽按钮。 */
@Composable
private fun CreatePresetButton(onClick: () -> Unit) {
    val palette = com.deepseek.dsh.core.designsystem.dshPanelColors()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .drawBehind {
                val stroke = 1.dp.toPx()
                val dash = 4.dp.toPx()
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                0f, 0f, size.width, size.height,
                                androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                            ),
                        )
                    },
                    color = palette.borderL3,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(dash, dash),
                        ),
                    ),
                )
            }
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "+",
            fontSize = 15.sp,
            color = palette.labelPrimary,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.agent_presets_create),
            fontSize = 13.sp,
            color = palette.labelPrimary,
        )
    }
}

@Composable
private fun AgentPresetCard(
    preset: AgentPreset,
    selected: Boolean,
    onSelect: () -> Unit,
    onExplain: () -> Unit,
    onUsage: () -> Unit,
    onViewConfig: () -> Unit,
) {
    if (selected) {
        SettingsSelectedCard(
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
            onClick = onSelect,
        ) {
            AgentPresetCardBody(
                preset = preset,
                selected = selected,
                onExplain = onExplain,
                onUsage = onUsage,
                onViewConfig = onViewConfig,
            )
        }
    } else {
        SettingsCard(
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
            onClick = onSelect,
        ) {
            AgentPresetCardBody(
                preset = preset,
                selected = selected,
                onExplain = onExplain,
                onUsage = onUsage,
                onViewConfig = onViewConfig,
            )
        }
    }
}

/** 预设卡内容：正文 14/16/12 内边距 + 整宽卡脚（见 AgentPresetSection.module.css）。 */
@Composable
private fun AgentPresetCardBody(
    preset: AgentPreset,
    selected: Boolean,
    onExplain: () -> Unit,
    onUsage: () -> Unit,
    onViewConfig: () -> Unit,
) {
    val palette = LocalSettingsPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = preset.name,
                        style = SettingsText.PresetName,
                        color = palette.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (selected) {
                        SettingsTag(
                            text = stringResource(R.string.agent_update_tag_new_task_default),
                            tone = SettingsTagTone.Solid,
                        )
                    } else {
                        SettingsTag(
                            text = stringResource(R.string.agent_update_group_builtin),
                            tone = SettingsTagTone.Outline,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = preset.id,
                    style = SettingsText.CodeSmall,
                    color = palette.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = preset.description,
                style = SettingsText.PresetDesc,
                color = palette.labelSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // .cardFoot：整宽 0.5dp 上描边 + 10/6 内边距，左侧 ghost 帮助按钮，右侧图标动作。
        SettingsCardDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PresetFootButton(
                text = stringResource(R.string.agent_update_action_explanation),
                onClick = onExplain,
            )
            PresetFootButton(
                text = stringResource(R.string.agent_update_action_usage),
                onClick = onUsage,
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onViewConfig),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = stringResource(R.string.agent_update_action_view_config),
                    tint = palette.labelTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 卡脚 ghost 文字按钮：12/18 三级色，按下时只显示文字色变化。 */
@Composable
private fun PresetFootButton(text: String, onClick: () -> Unit) {
    val palette = LocalSettingsPalette.current
    Text(
        text = text,
        style = SettingsText.SmallButton,
        color = palette.labelTertiary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/** 预设引导弹窗：headless Modal，640 宽、20/28 600 标题、32dp 关闭位、无底部按钮。 */
@Composable
private fun PresetGuideDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    DshHeadlessModal(onDismiss = onDismiss, width = 640.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = colors.labelPrimary,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            DshDialogCloseButton(
                onClick = onDismiss,
                label = stringResource(R.string.agent_update_dialog_close),
                size = 32.dp,
                cornerRadius = 12.dp,
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = body,
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

/** 对话框状态：标题 + 已去掉段标题前缀的正文。 */
private data class GuideDialog(
    val title: String,
    val body: String,
)

/** 预设卡数据（文案见 res/values(-zh)/strings_settings_agent_*.xml）。 */
private data class AgentPreset(
    val id: String,
    val name: String,
    val description: String,
    val explanation: String,
    val usage: String,
)

/** 去掉帮助文本里每行的 "### " 段标题前缀，保留原有换行与引用行。 */
private fun plainGuide(raw: String): String =
    raw.lineSequence().joinToString(separator = "\n") { line -> line.removePrefix("### ") }

/** 本应用内置的四个预设；文案来自字符串资源，顺序与 roster 一致。 */
@Composable
private fun builtInAgentPresets(): List<AgentPreset> = listOf(
    AgentPreset(
        id = "standard",
        name = stringResource(R.string.agent_update_preset_standard_name),
        description = stringResource(R.string.agent_update_preset_standard_desc),
        explanation = plainGuide(stringResource(R.string.agent_update_preset_standard_explanation)),
        usage = plainGuide(stringResource(R.string.agent_update_preset_standard_usage)),
    ),
    AgentPreset(
        id = "ptc",
        name = stringResource(R.string.agent_update_preset_ptc_name),
        description = stringResource(R.string.agent_update_preset_ptc_desc),
        explanation = plainGuide(stringResource(R.string.agent_update_preset_ptc_explanation)),
        usage = plainGuide(stringResource(R.string.agent_update_preset_ptc_usage)),
    ),
    AgentPreset(
        id = "minimal",
        name = stringResource(R.string.agent_update_preset_minimal_name),
        description = stringResource(R.string.agent_update_preset_minimal_desc),
        explanation = plainGuide(stringResource(R.string.agent_update_preset_minimal_explanation)),
        usage = plainGuide(stringResource(R.string.agent_update_preset_minimal_usage)),
    ),
    AgentPreset(
        id = "cordis",
        name = stringResource(R.string.agent_update_preset_cordis_name),
        description = stringResource(R.string.agent_update_preset_cordis_desc),
        explanation = plainGuide(stringResource(R.string.agent_update_preset_cordis_explanation)),
        usage = plainGuide(stringResource(R.string.agent_update_preset_cordis_usage)),
    ),
)
