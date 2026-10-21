package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
) {
    var guide by remember { mutableStateOf<GuideDialog?>(null) }
    // 预设是否存在/名称由 Host 决定；说明文案是官方内置 i18n，仅按 id 匹配。
    val presets = presetGroups.map { group ->
        AgentPresets.firstOrNull { it.id == group.id }
            ?: AgentPreset(
                id = group.id,
                name = group.name,
                description = "",
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
            SettingsGroupHeading("内置")
        }
        if (presets.isEmpty()) {
            item(key = "agent_presets_empty") {
                SettingsIntro(text = "暂无 Agent 预设。")
            }
        }
        presets.forEach { preset ->
            item(key = preset.id) {
                val selected = preset.id == selectedPresetId
                AgentPresetCard(
                    preset = preset,
                    selected = selected,
                    onSelect = { onSelectPreset(preset.id) },
                    onExplain = {
                        guide = GuideDialog(
                            title = "模式说明 · ${preset.name}",
                            body = preset.explanation,
                        )
                    },
                    onUsage = {
                        guide = GuideDialog(
                            title = "如何使用 · ${preset.name}",
                            body = preset.usage,
                        )
                    },
                    onViewConfig = {
                        guide = GuideDialog(
                            title = "查看配置 · ${preset.name}",
                            body = listOf(
                                "id: ${preset.id}",
                                "名称: ${preset.name}",
                                "分组: 内置",
                                "新任务默认: ${if (selected) "是" else "否"}",
                            ).joinToString(separator = "\n"),
                        )
                    },
                )
            }
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
                        SettingsTag(text = "新任务默认", tone = SettingsTagTone.Solid)
                    } else {
                        SettingsTag(text = "内置", tone = SettingsTagTone.Outline)
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
            PresetFootButton(text = "模式说明", onClick = onExplain)
            PresetFootButton(text = "如何使用", onClick = onUsage)
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
                    contentDescription = "查看配置",
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
                label = "关闭",
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

/** 预设卡数据（中文文案对齐 ui-agent-preset 的 locales.ts / guide-locales.ts）。 */
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

/** guide-locales.ts 中文帮助原文（分段沿用空行分隔）。 */
private object PresetGuideRaw {
    val StandardExplanation: String = listOf(
        "### 工作方式",
        "Agent 直接调用工具来读写文件、检索资料和执行终端命令。包含 Skills、计划、目标、子 Agent、工作流和上下文压缩等能力。",
        "### 什么时候选",
        "日常编程、文件处理和资料整理可以从这里开始。标准模式也能编写脚本、批量处理文件；PTC 改变的是工具调用方式，批量任务并不必须使用 PTC。",
    ).joinToString(separator = "\n\n")

    val StandardUsage: String = listOf(
        "### 修复一个问题",
        "> 搜索表单连续提交两次后，结果会消失。请定位原因、修复问题并运行相关测试，最后说明原因和修改内容。",
        "预期产出：代码修改、相关测试结果，以及问题原因说明。",
        "### 整理项目资料",
        "> 阅读项目中的 Markdown 记录，整理已经达成的结论和仍待确认的问题，并附上对应文件链接。",
        "预期产出：一份带来源引用的总结，方便回到原文核对。",
    ).joinToString(separator = "\n\n")

    val PtcExplanation: String = listOf(
        "### 怎样调用工具",
        "PTC 是 Programmatic Tool Calling，即通过程序调用工具。当前内置预设让 Agent 通过 run_code 编写 TypeScript 程序，使用生成的工具 SDK 发起调用。程序可以组织循环、条件判断、错误处理，以及适合并发执行的调用。",
        "### 哪些结果交给模型",
        "工具返回的数据先交给程序，经过筛选、计算或合并，再通过输出或返回值交给模型；图片结果会另行附加。程序中的工具调用仍会被记录，也仍受工具权限约束。",
        "### 与标准模式的区别",
        "两种模式都能编程、批量处理文件。标准模式直接向模型提供各个工具；PTC 让模型用代码组织工具调用。当前 PTC 预设未启用 workflow 工具。速度和 token 用量取决于具体任务与结果处理方式。",
    ).joinToString(separator = "\n\n")

    val PtcUsage: String = listOf(
        "### 批量检查配置文件",
        "> 检查 configs/ 下所有 JSON 文件，按照 schema.json 找出缺失字段和不合法的值。每个问题写成 CSV 中的一行；读取失败的文件也记入报告，继续检查其余文件。保留原文件。",
        "预期产出：问题汇总和一份 CSV 报告。程序可以对多份文件执行相同检查，处理单个文件的失败，再汇总结果。",
        "### 汇总错误日志",
        "> 分析 logs/ 下的日志，按服务和错误类型统计次数，列出出现最多的十类错误，每类保留一条示例。完整统计另存为 CSV。",
        "预期产出：高频错误摘要和完整统计表。中间数据可以先在程序中聚合，再把汇总交给模型。",
    ).joinToString(separator = "\n\n")

    val MinimalExplanation: String = listOf(
        "### 保留哪些能力",
        "提供持久 Shell 和 read_image 工具，并使用固定系统提示词。读取图片需要支持图片输入的模型。上下文压力达到阈值时自动压缩较早历史；Agent 空闲时可输入 /compact 手动压缩。内置预设不加载 Skills、计划，也不注入标准运行时上下文。",
        "### 什么时候选",
        "适合作为实验和对照测试的基线。Agent 仍能通过终端命令读写文件、运行脚本，但缺少管理长任务的内置辅助能力。工具少，不代表对新手更容易。",
    ).joinToString(separator = "\n\n")

    val MinimalUsage: String = listOf(
        "### 对比基础修复表现",
        "> 运行这个项目的测试，找出失败原因，做最小修复，再运行相关测试并报告结果。",
        "分别用标准模式和极简模式，从相同的工作区状态执行这条任务，对比完成情况、工具调用和最终修改。极简模式会通过终端命令完成这些操作。",
    ).joinToString(separator = "\n\n")

    val CordisExplanation: String = listOf(
        "### 可以创造什么",
        "创造模式具备标准任务工具，并增加运行时检查、持久化插件管理，以及 Cordis 插件和 Agent 预设的开发指引。可以编写插件来添加功能或界面，也可以组合工具和提示词，创建适合特定任务的模式。",
        "### 插件与模式的关系",
        "插件为 DSH 增加能力，例如工具、服务连接或界面入口。模式是一份 Agent 预设，用来选择任务可用的工具，并约定 Agent 的工作方式。自定义模式中也可以使用自己开发的插件。",
        "### 怎样让成果生效",
        "可以要求 Agent 完成安装并验证实际效果。插件可能即时加载，也可能需要重启，取决于修改内容；新建的模式在创建新任务时选择。",
    ).joinToString(separator = "\n\n")

    val CordisUsage: String = listOf(
        "### 添加一个界面",
        "> 帮我写一个 DSH 插件，在侧栏增加「项目笔记」入口，列出当前工作区的 Markdown 文件，点击后能预览内容。完成安装并验证页面能打开。",
        "预期产出：带侧栏入口和预览页的插件，以及仍需完成的生效步骤。",
        "### 添加一个工具",
        "> 写一个插件，提供读取项目测试报告、汇总失败用例的工具。注册工具，并用一份示例报告验证调用结果。",
        "预期产出：可调用的新工具，以及一次示例调用的验证结果。",
        "### 创建自己的模式",
        "> 基于标准模式创建「代码审查」模式，优先检查潜在错误和测试缺口，指出文件与行号，修改文件前先询问我。保存成可选择的预设。",
        "预期产出：可在新任务中选择的自定义模式。审查要求用于指导 Agent，实际可执行的操作仍由权限设置决定。",
    ).joinToString(separator = "\n\n")
}

/** 本应用内置的四个预设；顺序与 roster 一致。 */
private val AgentPresets: List<AgentPreset> = listOf(
    AgentPreset(
        id = "standard",
        name = "标准模式",
        description = "处理代码、文件和资料，适合大多数任务。Agent 会按需使用检索、编辑和终端等工具。",
        explanation = plainGuide(PresetGuideRaw.StandardExplanation),
        usage = plainGuide(PresetGuideRaw.StandardUsage),
    ),
    AgentPreset(
        id = "ptc",
        name = "PTC 模式",
        description = "包含标准模式的所有能力，更适合批量调用工具，并对结果进行筛选、整理、去重、统计或汇总的任务。",
        explanation = plainGuide(PresetGuideRaw.PtcExplanation),
        usage = plainGuide(PresetGuideRaw.PtcUsage),
    ),
    AgentPreset(
        id = "minimal",
        name = "极简模式",
        description = "Agent 使用终端和图片读取工具，并支持上下文压缩，适合测试和对比其基础表现。",
        explanation = plainGuide(PresetGuideRaw.MinimalExplanation),
        usage = plainGuide(PresetGuideRaw.MinimalUsage),
    ),
    AgentPreset(
        id = "cordis",
        name = "创造模式",
        description = "用对话定制 DSH：让 Agent 编写插件，添加新功能或界面；也能组合工具和提示词，创建自己的模式。",
        explanation = plainGuide(PresetGuideRaw.CordisExplanation),
        usage = plainGuide(PresetGuideRaw.CordisUsage),
    ),
)

