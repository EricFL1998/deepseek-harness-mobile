package com.deepseek.dsh.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.AnchoredMenuOverlay
import com.deepseek.dsh.core.designsystem.AnchoredMenuPopup
import com.deepseek.dsh.core.designsystem.DshDialogButton
import com.deepseek.dsh.core.designsystem.DshIcons
import com.deepseek.dsh.core.designsystem.DshMenuBadge
import com.deepseek.dsh.core.designsystem.DshMenuCaption
import com.deepseek.dsh.core.designsystem.DshMenuCheck
import com.deepseek.dsh.core.designsystem.DshMenuDescription
import com.deepseek.dsh.core.designsystem.DshMenuIcon
import com.deepseek.dsh.core.designsystem.DshMenuLabel
import com.deepseek.dsh.core.designsystem.DshMenuRow
import com.deepseek.dsh.core.designsystem.DshMenuSectionLabel
import com.deepseek.dsh.core.designsystem.DshMenuValue
import com.deepseek.dsh.core.designsystem.DshMenuAlign
import com.deepseek.dsh.core.designsystem.DshDialog
import com.deepseek.dsh.core.designsystem.DshPanelDefaults
import com.deepseek.dsh.core.designsystem.DshSheetPanel
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.designsystem.dshPanelColors
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.MessageRole
import com.deepseek.dsh.core.model.ComposerStats
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.feature.chat.R

/** 推理等级档位（对齐 ui-model-selection 的 effort 目录）。 */
/** 官方客户端模型显示名：DeepSeek-V41-Flash -> DeepSeek-V4.1-Flash。 */
private fun displayModelName(name: String): String {
    val match = Regex("""^DeepSeek-V(\d{2,})([- ].*)?$""").find(name) ?: return name
    val digits = match.groupValues[1]
    val rest = match.groupValues[2]
    return "DeepSeek-V${digits.dropLast(1)}.${digits.last()}$rest"
}
data class ReasoningOption(val id: String, val label: String)

enum class ReasoningLevel(val labelResId: Int) {
    FAST(R.string.reasoning_fast),
    DEEP_THINK(R.string.reasoning_deep_think),
    MAX_REASONING(R.string.reasoning_max)
}

/**
 * 权限预设（对齐 ui-permission-presets 当前会话选择器）。
 *
 * 内置四项：仅可查看 / 工作区内修改 / 完全权限 / 自动审查（实验，带 EXP 标记）。
 * 完全权限与自动审查需要通过带确认勾选的风险弹窗。
 */
enum class PermissionPreset(
    val titleResId: Int,
    val descResId: Int,
    val badgeResId: Int? = null,
    val confirmTitleResId: Int? = null,
    val confirmDescResId: Int? = null,
    val confirmAckResId: Int? = null,
    val confirmEnableResId: Int? = null,
) {
    READ_ONLY(R.string.perm_read_only, R.string.perm_desc_read_only),
    WORKSPACE_WRITE(R.string.perm_workspace_write, R.string.perm_desc_workspace_write),
    FULL_ACCESS(
        R.string.perm_danger_full_access,
        R.string.perm_desc_danger_full_access,
        confirmTitleResId = R.string.perm_confirm_title,
        confirmDescResId = R.string.perm_confirm_desc,
        confirmAckResId = R.string.perm_confirm_acknowledge,
        confirmEnableResId = R.string.perm_confirm_enable,
    ),
    AUTO_REVIEW(
        R.string.perm_auto_review,
        R.string.perm_desc_auto_review,
        badgeResId = R.string.perm_auto_badge,
        confirmTitleResId = R.string.perm_auto_confirm_title,
        confirmDescResId = R.string.perm_auto_confirm_desc,
        confirmAckResId = R.string.perm_auto_confirm_acknowledge,
        confirmEnableResId = R.string.perm_auto_confirm_enable,
    ),
}

/** 加号菜单中的一条命令（对齐 ui-commands 的 label / description 与图标）。 */
private data class ComposerCommand(
    val labelResId: Int,
    val descResId: Int,
    val token: String,
    val iconResId: Int,
)

/**
 * 主聊天页：信息结构 + 手机端（ChatGPT Android）尺度的输入区。
 *
 * 输入卡片内自上而下是文本域与操作行；操作行自左向右为加号（命令菜单）、
 * 权限预设、弹性留白、模型与推理等级、发送/停止，与 `InputBar` 的
 * tools / trailing 两簇排布一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    isSending: Boolean = false,
    workspaceName: String = "默认工作区",
    onWorkspaceClick: () -> Unit = {},
    onAnswerUserQuestion: (callId: String, questionId: String, selected: String) -> Unit = { _, _, _ -> },
    onOpenAttachmentMenu: () -> Unit = {},
    selectedModel: String = "",
    availableModels: List<String> = emptyList(),
    modelGroups: List<com.deepseek.dsh.core.model.ModelProviderGroup> = emptyList(),
    onModelSelected: (String) -> Unit = {},
    selectedPermission: PermissionPreset = PermissionPreset.WORKSPACE_WRITE,
    agentPresetLabel: String = "标准模式",
    onPermissionSelected: (PermissionPreset) -> Unit = {},
    reasoningLevel: ReasoningLevel = ReasoningLevel.FAST,
    reasoningOptions: List<ReasoningOption> = emptyList(),
    selectedReasoningId: String = "",
    effortLabelOverride: String? = null,
    onReasoningLevelChange: (ReasoningLevel) -> Unit = {},
    onReasoningSelected: (ReasoningOption) -> Unit = {},
    dockPluginSurfaces: List<SurfaceItem> = emptyList(),
    onDockPluginClick: (SurfaceItem) -> Unit = {},
    stats: ComposerStats = ComposerStats(),
    isThinking: Boolean = false,
    onStopClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var currentPermission by remember(selectedPermission) { mutableStateOf(selectedPermission) }
    var isPlanMode by remember { mutableStateOf(false) }
    var commandSheetOpen by remember { mutableStateOf(false) }
    var toolDetail by remember { mutableStateOf<ToolDetailData?>(null) }
    var permissionMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<PermissionPreset?>(null) }

    val listState = rememberLazyListState()
    val collapsedTurns = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val chatRows = remember(messages, isThinking) { buildChatRows(messages, isThinking) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val composerBlock: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onWorkspaceClick)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_folder_open),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = workspaceName,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_agent_preset_official),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = agentPresetLabel,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        BottomInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    val text = inputText
                    inputText = ""
                    onSendMessage(text)
                }
            },
            onPlusClick = { commandSheetOpen = true },
            selectedModel = selectedModel,
            availableModels = availableModels,
            effortLabel = effortLabelOverride ?: stringResource(reasoningLevel.labelResId),
            reasoningOptions = reasoningOptions,
            selectedReasoningId = selectedReasoningId,
            onOpenModelMenu = {
                permissionMenuOpen = false
                modelMenuOpen = true
            },
            onModelMenuDismiss = { modelMenuOpen = false },
            modelMenuOpen = modelMenuOpen,
            onModelSelected = { model ->
                modelMenuOpen = false
                onModelSelected(model)
            },
            onEffortSelected = { level ->
                modelMenuOpen = false
                onReasoningLevelChange(level)
            },
            onReasoningSelected = { option ->
                modelMenuOpen = false
                onReasoningSelected(option)
            },
            currentPermission = currentPermission,
            onOpenPermissionMenu = {
                modelMenuOpen = false
                permissionMenuOpen = true
            },
            onPermissionMenuDismiss = { permissionMenuOpen = false },
            permissionMenuOpen = permissionMenuOpen,
            onPermissionSelect = { preset ->
                permissionMenuOpen = false
                if (preset.confirmTitleResId != null) {
                    pendingConfirm = preset
                } else {
                    currentPermission = preset
                    onPermissionSelected(preset)
                }
            },
            isPlanMode = isPlanMode,
            isGenerating = isThinking,
            onStop = onStopClick,
            dockPluginSurfaces = dockPluginSurfaces,
            onDockPluginClick = onDockPluginClick,
            stats = stats,
            showDockStats = messages.isNotEmpty() || isSending
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        if (messages.isEmpty() && !isSending) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyWelcomeView(
                        onChipClick = { prompt -> onSendMessage(prompt) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-96).dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    composerBlock()
                }
            }
        } else if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "正在创建会话…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = chatRows,
                    key = { row ->
                        when (row) {
                            is ChatRow.Single -> row.message.messageId
                            is ChatRow.Process -> "proc_${row.key}"
                            is ChatRow.TurnDone -> "done_${row.key}"
                        }
                    }
                ) { row ->
                    when (row) {
                        is ChatRow.Single -> ModernMessageItem(
                            message = row.message,
                            onRetry = { onSendMessage(row.message.content) },
                            onAnswerUserQuestion = onAnswerUserQuestion,
                            onOpenToolDetail = { toolDetail = it },
                        )
                        is ChatRow.TurnDone -> TurnCompletedRow(
                            durationMs = row.durationMs,
                            expanded = collapsedTurns[row.key] != true,
                            onClick = { collapsedTurns[row.key] = collapsedTurns[row.key] != true },
                        )
                        is ChatRow.Process -> ToolProcessGroup(
                            items = row.items,
                            expanded = collapsedTurns[row.key] != true,
                            onToggle = { collapsedTurns[row.key] = collapsedTurns[row.key] != true },
                            onOpenDetail = { toolDetail = it },
                        )
                    }
                }

                val hasRunningProcess = chatRows.any { row ->
                    row is ChatRow.Process && row.items.any { it.role == MessageRole.TOOL && toolStatus(it) == "running" }
                }
                if (isThinking && !hasRunningProcess) {
                    item {
                        LiveToolStatus(messages = messages)
                    }
                }
            }
        }

        toolDetail?.let { detail ->
            ToolDetailSheet(detail = detail, onDismiss = { toolDetail = null })
        }

        if (!(messages.isEmpty() && !isSending)) {
            composerBlock()
        }
    }

    // 加号：分组命令菜单（添加 / 指令）
    if (commandSheetOpen) {
        ComposerCommandSheet(
            onDismiss = { commandSheetOpen = false },
            onFile = {
                commandSheetOpen = false
                onOpenAttachmentMenu()
            },
            onPlan = {
                commandSheetOpen = false
                isPlanMode = !isPlanMode
            },
            onPermission = {
                commandSheetOpen = false
                permissionMenuOpen = true
            },
            onModel = {
                commandSheetOpen = false
                modelMenuOpen = true
            },
            onCommand = { token ->
                commandSheetOpen = false
                inputText = if (inputText.isBlank()) "$token " else "${inputText.trimEnd()} $token "
            }
        )
    }

    pendingConfirm?.let { preset ->
        PermissionRiskDialog(
            preset = preset,
            onDismiss = { pendingConfirm = null },
            onConfirm = {
                currentPermission = preset
                onPermissionSelected(preset)
                pendingConfirm = null
            },
        )
    }
}

/**
 * 风险确认弹窗（对齐 ui-primitives 的 RiskConfirmation + Modal）：
 * 警告图标 + 说明文案 + 「我已了解风险」勾选，勾选后主按钮才可用。
 */
@Composable
private fun PermissionRiskDialog(
    preset: PermissionPreset,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var acknowledged by remember(preset) { mutableStateOf(false) }
    val titleRes = preset.confirmTitleResId ?: return
    val descRes = preset.confirmDescResId ?: return
    val ackRes = preset.confirmAckResId ?: return
    val enableRes = preset.confirmEnableResId ?: return
    val colors = LocalDshPanelColors.current

    DshDialog(
        onDismiss = onDismiss,
        title = stringResource(titleRes),
        closeLabel = stringResource(R.string.action_close),
        width = DshPanelDefaults.WideDialogWidth,
        content = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_warning_outline),
                    contentDescription = null,
                    tint = colors.stateErrorPrimary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(descRes),
                    color = colors.labelSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = acknowledged,
                        role = Role.Checkbox,
                        onValueChange = { acknowledged = it },
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                RiskCheckbox(checked = acknowledged)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(ackRes),
                    color = colors.labelPrimary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        footer = {
            DshDialogButton(
                text = stringResource(R.string.action_cancel),
                primary = false,
                enabled = true,
                onClick = onDismiss,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            DshDialogButton(
                text = stringResource(enableRes),
                primary = true,
                enabled = acknowledged,
                onClick = onConfirm,
                modifier = Modifier.widthIn(min = 136.dp),
            )
        },
    )
}

/** 风险确认的原生勾选框：16dp 方形，选中使用品牌主色填充。 */
@Composable
private fun RiskCheckbox(checked: Boolean) {
    val colors = LocalDshPanelColors.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .padding(top = 3.dp)
            .size(16.dp)
            .clip(shape)
            .background(if (checked) colors.buttonPrimaryFill else Color.Transparent)
            .then(
                if (checked) {
                    Modifier
                } else {
                    Modifier.border(1.dp, colors.borderL3, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_check),
                contentDescription = null,
                tint = colors.labelPrimaryForeground,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
/**
 * 权限预设菜单：四项（含 EXP 标记的自动审查），每项使用权限图标，
 * 选中项右侧显示对勾；卡片宽度按行内容实测收敛。
 */
@Composable
private fun PermissionMenu(
    expanded: Boolean,
    anchorBounds: Rect?,
    current: PermissionPreset,
    onDismiss: () -> Unit,
    onSelect: (PermissionPreset) -> Unit,
) {
    val labels = PermissionPreset.entries.map { stringResource(it.titleResId) }
    val badges = PermissionPreset.entries.map { preset -> preset.badgeResId?.let { stringResource(it) } }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)
    val badgeStyle = TextStyle(fontSize = 8.sp, lineHeight = 10.sp, letterSpacing = 0.2.sp)
    val menuWidth = remember(labels, badges, density) {
        val contentWidth = labels.indices.maxOf { index ->
            val label = measurer.measure(labels[index], labelStyle).size.width
            val badge = badges[index]?.let {
                measurer.measure(it, badgeStyle).size.width + with(density) { 4.dp.roundToPx() }
            } ?: 0
            label + badge
        }
        with(density) {
            (
                contentWidth +
                    14.dp.roundToPx() +
                    DshPanelDefaults.RowGap.roundToPx() * 3 +
                    DshPanelDefaults.RowHorizontalPadding.roundToPx() * 2 +
                    14.dp.roundToPx()
                ).toDp()
        }.coerceIn(DshPanelDefaults.MenuMinWidth, DshPanelDefaults.MenuMaxWidth)
    }
    AnchoredMenuPopup(
        expanded = expanded,
        onDismiss = onDismiss,
        menuWidth = menuWidth,
        align = DshMenuAlign.Start,
        // 卡片底部与 trigger 顶部齐平，不能压住 Composer。
        anchorGap = 0.dp,
    ) {
        PermissionPreset.entries.forEach { preset ->
            val selected = preset == current
            DshMenuRow(onClick = { onSelect(preset) }) {
                DshMenuIcon(preset.iconResId(), size = 14.dp)
                DshMenuLabel(labels[preset.ordinal], Modifier.alignByBaseline())
                preset.badgeResId?.let {
                    DshMenuBadge(badges[preset.ordinal].orEmpty(), Modifier.alignByBaseline())
                }
                Spacer(Modifier.weight(1f))
                if (selected) DshMenuCheck()
            }
        }
    }
}

/** 权限图标资源（只读 / 工作区内修改 / 完全权限 / 自动审查）。 */
private fun PermissionPreset.iconResId(): Int = when (this) {
    PermissionPreset.READ_ONLY -> com.deepseek.dsh.core.designsystem.R.drawable.ic_perm_readonly
    PermissionPreset.WORKSPACE_WRITE -> com.deepseek.dsh.core.designsystem.R.drawable.ic_perm_workspace_write
    PermissionPreset.FULL_ACCESS -> com.deepseek.dsh.core.designsystem.R.drawable.ic_perm_full_access
    PermissionPreset.AUTO_REVIEW -> com.deepseek.dsh.core.designsystem.R.drawable.ic_perm_auto_review
}

/**
 * 模型与推理等级菜单：首层两个导航行，进入后分别是模型列表与推理等级列表。
 */
@Composable
private fun ModelEffortMenu(
    expanded: Boolean,
    anchorBounds: Rect?,
    selectedModel: String,
    availableModels: List<String>,
    modelGroups: List<com.deepseek.dsh.core.model.ModelProviderGroup> = emptyList(),
    effortLabel: String,
    reasoningOptions: List<ReasoningOption> = emptyList(),
    selectedReasoningId: String = "",
    onDismiss: () -> Unit,
    onModelSelected: (String) -> Unit,
    onEffortSelected: (ReasoningLevel) -> Unit,
    onReasoningSelected: (ReasoningOption) -> Unit = {},
) {
    var page by remember { mutableStateOf(0) }
    var modelQuery by remember { mutableStateOf("") }
    LaunchedEffect(expanded) {
        if (expanded) {
            page = 0
            modelQuery = ""
        }
    }

    // 模型菜单：首层宽度按「标签 + 数值 + 12dp 箭头」实测收敛，卡片最小 240dp。
    val labelStyle = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)
    val valueStyle = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)
    val measurer = rememberTextMeasurer()
    val modelLabel = stringResource(R.string.model_menu_model)
    val effortLabelText = stringResource(R.string.model_menu_effort)
    val density = LocalDensity.current
    val rowWidth = remember(selectedModel, effortLabel, modelLabel, effortLabelText, density) {
        val labelMax = listOf(modelLabel, effortLabelText)
            .maxOf { measurer.measure(it, labelStyle).size.width }
        val valueMax = listOf(selectedModel, effortLabel)
            .maxOf { measurer.measure(it, valueStyle).size.width }
        with(density) {
            (
                labelMax + valueMax +
                    12.dp.roundToPx() +
                    DshPanelDefaults.RowHorizontalPadding.roundToPx() * 2
                ).toDp()
        }.coerceIn(144.dp, 420.dp)
    }

    AnchoredMenuPopup(
        expanded = expanded,
        onDismiss = onDismiss,
        menuWidth = if (page == 0) rowWidth else 240.dp,
        align = DshMenuAlign.Start,
        maxHeight = 360.dp,
        // 卡片底部与 trigger 顶部齐平，不能压住 Composer。
        anchorGap = 0.dp,
    ) {
        when (page) {
            0 -> {
                if (availableModels.distinct().isEmpty()) {
                    // 官方空目录文案：暂无可用模型。
                    DshMenuSectionLabel("暂无可用模型")
                } else {
                    MenuNavRow(
                        label = stringResource(R.string.model_menu_model),
                        value = selectedModel,
                        onClick = { page = 1 },
                    )
                    MenuNavRow(
                        label = stringResource(R.string.model_menu_effort),
                        value = effortLabel,
                        onClick = { page = 2 },
                    )
                }
            }

            1 -> {
                val fallbackGroups = remember(availableModels) {
                    if (modelGroups.isNotEmpty()) emptyList()
                    else availableModels.distinct().groupBy { name ->
                        if (name.startsWith("DeepSeek", ignoreCase = true)) "DeepSeek 账号"
                        else name.substringBefore("-").substringBefore(" ").ifBlank { "模型" }
                    }.map { (provider, names) ->
                        com.deepseek.dsh.core.model.ModelProviderGroup(
                            id = provider,
                            name = provider,
                            models = names.map { com.deepseek.dsh.core.model.ModelItem(id = it, name = it) }
                        )
                    }
                }
                val groups = if (modelGroups.isNotEmpty()) {
                    modelGroups.map { group ->
                        group.copy(
                            name = if (group.id == "deepseek-account") "DeepSeek 账号" else group.name
                        )
                    }
                } else fallbackGroups
                val totalModels = groups.sumOf { it.models.size }
                val showSearch = totalModels > 4
                val query = modelQuery.trim().lowercase()
                if (showSearch) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = modelQuery,
                            onValueChange = { modelQuery = it },
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (modelQuery.isEmpty()) {
                                    Text("搜索模型...", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                inner()
                            }
                        )
                    }
                }
                groups.forEach { group ->
                    val models = group.models.filter {
                        query.isEmpty() || it.name.lowercase().contains(query)
                    }
                    if (models.isNotEmpty()) {
                        DshMenuSectionLabel(group.name)
                        models.forEach { model ->
                            val selected = model.name == selectedModel
                            DshMenuRow(onClick = { onModelSelected(model.name) }) {
                                DshMenuLabel(model.name, Modifier.weight(1f))
                                if (selected) DshMenuCheck()
                            }
                        }
                    }
                }
            }
            else -> {
                if (reasoningOptions.isNotEmpty()) {
                    reasoningOptions.forEach { option ->
                        val selected = option.id == selectedReasoningId
                        DshMenuRow(onClick = { onReasoningSelected(option) }) {
                            DshMenuLabel(option.label, Modifier.weight(1f))
                            if (selected) DshMenuCheck()
                        }
                    }
                } else {
                    ReasoningLevel.entries.forEach { level ->
                        val label = stringResource(level.labelResId)
                        val selected = label == effortLabel
                        DshMenuRow(onClick = { onEffortSelected(level) }) {
                            DshMenuLabel(label, Modifier.weight(1f))
                            if (selected) DshMenuCheck()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 菜单里的「标签 + 当前值 + ›」导航行（model menu `.cell` 规格）：
 * 标签 13/20 左对齐，数值 13/20 三级色贴右，12dp 右箭头。
 */
@Composable
private fun MenuNavRow(label: String, value: String, onClick: () -> Unit) {
    DshMenuRow(onClick = onClick) {
        DshMenuLabel(label)
        Spacer(Modifier.weight(1f))
        DshMenuValue(value)
        DshMenuIcon(
            resId = com.deepseek.dsh.core.designsystem.R.drawable.ic_chevron_right_official,
            size = 12.dp,
        )
    }
}
/**
 * 首页居中欢迎与快捷建议胶囊（对齐 hero.headline「探索未至之境」）。
 */
@Composable
fun EmptyWelcomeView(
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_dsh_fish_logo),
                contentDescription = "DeepSeek Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.chat_welcome_title),
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Text(
                    text = "预览版",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.chat_welcome_desc),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QuickChip(label: String, onClick: () -> Unit) {
    val colors = dshPanelColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) colors.interactiveHover else colors.bgModulePlatform)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = colors.labelSecondary,
            maxLines = 1,
        )
    }
}

/**
 * 思考链折叠面板（文案对齐 `chat.deepDivingFor`）。
 */
@Composable
fun ReasoningCard(
    isFinished: Boolean,
    elapsedSeconds: String,
    reasoningText: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val preview = remember(reasoningText) { reasoningText.lineSequence().firstOrNull().orEmpty() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = DshIcons.ThinkOutline,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.chat_thinking),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!expanded) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Text(
                    text = preview,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }

        AnimatedVisibility(visible = expanded) {
            Text(
                text = reasoningText,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp, fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

/** 回合结束：官方「已完成，用时 X」+ 分割线，点击折叠/展开整个过程块。 */
@Composable
private fun TurnCompletedRow(durationMs: Long, expanded: Boolean = true, onClick: () -> Unit = {}) {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    val label = when {
        durationMs < 0 -> ""
        seconds >= 60 -> "，用时 ${seconds / 60}分${seconds % 60}秒"
        else -> "，用时 ${seconds}秒"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.height(24.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已完成$label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
        androidx.compose.material3.HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
    }
}
/** 连续的 tool call 归入一个 process group（官方 stepProcess）：运行中显示当前动作，结束后折叠为摘要。 */
@Composable
private fun ToolProcessGroup(
    items: List<ChatMessage>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenDetail: (ToolDetailData) -> Unit,
) {
    val running = items.firstOrNull { it.role == MessageRole.TOOL && toolStatus(it) == "running" }
    val isRunning = running != null
    val summary = remember(items) { processSummary(items) }
    val activeName = running?.content?.split(2.toChar())?.getOrNull(3).orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRunning) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isRunning) processVerb(activeName, done = false) else summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 20.dp, top = 2.dp)) {
                items.filterNot { it.role == MessageRole.TOOL && toolStatus(it) == "running" }.forEach { item ->
                    if (item.role == MessageRole.ASSISTANT) {
                        ReasoningCard(
                            isFinished = true,
                            elapsedSeconds = "",
                            reasoningText = item.content.substringBefore('\u0001'),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        ToolMessageItem(
                            content = item.content,
                            onOpenDetail = onOpenDetail,
                        )
                    }
                }
            }
        }
    }
}
/** 工具调用/结果：官方披露行样式，点击展开参数或输出。 */
@Composable
private fun ToolMessageItem(
    content: String,
    onAnswerUserQuestion: (callId: String, questionId: String, selected: String) -> Unit = { _, _, _ -> },
    onOpenDetail: (ToolDetailData) -> Unit = {},
) {
    val parts = content.split(2.toChar())
    if (parts.size < 5) return
    val callId = if (parts.size >= 6) parts[2] else ""
    val name = if (parts.size >= 6) parts[3] else parts[2]
    val status = if (parts.size >= 6) parts[4] else parts[3]
    val body = if (parts.size >= 6) parts[5] else parts[4]
    val args = parts.getOrNull(6).orEmpty()
    val summary = remember(name, args, body, status) { toolSummary(name, args, body, status) }
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable {
                    onOpenDetail(
                        ToolDetailData(
                            callId = callId,
                            name = name,
                            status = status,
                            args = args,
                            body = body,
                        )
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = toolIcon(name),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = toolTitle(name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = if (status == "error") MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (status == "running" || status == "error") {
                Text(
                    text = if (status == "running") "运行中" else "失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == "error") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
        if (name.contains("ask_user_question") && status == "running") {
            val askQuestions = remember(body) { parseAskQuestions(body) }
            if (askQuestions.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 22.dp, top = 4.dp)) {
                    askQuestions.forEach { q ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp, MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (q.header.isNotBlank()) {
                                    Text(
                                        text = q.header,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(
                                    text = q.question,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                q.options.forEach { option ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (callId.isNotBlank()) {
                                                    onAnswerUserQuestion(callId, q.id, option.label)
                                                }
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else AnimatedVisibility(visible = expanded && body.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(start = 22.dp, top = 4.dp)
            ) {
                Text(
                    text = toolExpandedText(name, args, body, status),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

/** 运行中：显示当前正在执行的 tool call（官方 live process title）。 */
@Composable
private fun LiveToolStatus(messages: List<ChatMessage>) {
    val active = messages.lastOrNull { message ->
        if (message.role != MessageRole.TOOL) return@lastOrNull false
        message.content.split(2.toChar()).getOrNull(4) == "running"
    }
    val alpha = 1f
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp).padding(start = 22.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active != null) {
            val parts = active.content.split(2.toChar())
            val name = parts.getOrNull(3).orEmpty()
            val args = parts.getOrNull(6).orEmpty()
            val summary = remember(name, args) { toolSummary(name, args, "", "running") }
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = toolTitle(name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
            if (summary.isNotBlank()) {
                Box(modifier = Modifier.padding(horizontal = 8.dp).size(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = alpha)))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Icon(
                imageVector = DshIcons.ThinkOutline,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "思考中…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}
private sealed interface ChatRow {
    data class Single(val message: ChatMessage) : ChatRow
    data class Process(val key: String, val items: List<ChatMessage>) : ChatRow
    data class TurnDone(val key: String, val durationMs: Long) : ChatRow
}

private fun buildChatRows(messages: List<ChatMessage>, isThinking: Boolean): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var process = mutableListOf<ChatMessage>()
    var turnStart = 0L
    var lastCreated = 0L
    fun flush(closed: Boolean) {
        if (process.isEmpty()) return
        val key = process.first().messageId
        val duration = if (turnStart > 0 && lastCreated > 0) (lastCreated - turnStart).coerceAtLeast(0L) else -1L
        if (closed) rows.add(ChatRow.TurnDone(key, duration))
        rows.add(ChatRow.Process(key, process.toList()))
        process = mutableListOf()
    }
    for (message in messages) {
        lastCreated = maxOf(lastCreated, message.createdAt)
        val sep = message.content.indexOf('\u0001')
        when {
            message.role == MessageRole.USER -> {
                flush(closed = true)
                turnStart = message.createdAt
                rows.add(ChatRow.Single(message))
            }
            message.role == MessageRole.TOOL -> process.add(message)
            message.role == MessageRole.ASSISTANT && sep >= 0 -> {
                val reasoning = message.content.substring(0, sep)
                val answer = message.content.substring(sep + 1)
                if (reasoning.isNotBlank()) {
                    process.add(message.copy(messageId = message.messageId + "_r", content = reasoning + '\u0001'))
                }
                if (answer.isNotBlank()) {
                    flush(closed = true)
                    rows.add(ChatRow.Single(message.copy(messageId = message.messageId + "_a", content = answer)))
                }
            }
            else -> {
                flush(closed = true)
                rows.add(ChatRow.Single(message))
            }
        }
    }
    val running = process.any { it.role == MessageRole.TOOL && toolStatus(it) == "running" }
    flush(closed = !isThinking && !running)
    return rows
}
private fun toolStatus(message: ChatMessage): String =
    message.content.split(2.toChar()).getOrNull(4).orEmpty()

private fun processVerb(name: String, done: Boolean): String = when {
    name.contains("bash") || name.contains("shell") -> if (done) "执行了命令" else "运行命令"
    name.contains("image") -> if (done) "已读取图片" else "读取图片"
    name.contains("read") -> if (done) "已读取文件" else "读取文件"
    name.contains("write") || name.contains("edit") -> if (done) "已编辑文件" else "编辑文件"
    name.contains("grep") || name.contains("glob") -> if (done) "已搜索文件" else "搜索文件"
    name.contains("ask_user_question") -> if (done) "已询问" else "等待回答"
    name.contains("web") -> if (done) "已获取网页" else "获取网页"
    else -> if (done) "已调用工具" else "调用工具"
}

private fun processSummary(tools: List<ChatMessage>): String {
    val verbs = tools.filter { it.role == MessageRole.TOOL }.mapNotNull { message ->
        val name = message.content.split(2.toChar()).getOrNull(3) ?: return@mapNotNull null
        processVerb(name, done = true)
    }.distinct()
    return verbs.joinToString("并")
}
data class ToolDetailData(
    val callId: String,
    val name: String,
    val status: String,
    val args: String,
    val body: String,
)

/** 点击 Tool 行后的完整参数/输出详情。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolDetailSheet(detail: ToolDetailData, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = toolTitle(detail.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (detail.status) {
                        "running" -> "运行中"
                        "error" -> "失败"
                        else -> "已完成"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (detail.status == "error") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline
                )
            }
            if (detail.callId.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail.callId,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.outline
                )
            }
            val prettyArgs = remember(detail.args) {
                runCatching {
                    org.json.JSONObject(detail.args).toString(2)
                }.getOrElse { detail.args }
            }
            if (prettyArgs.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("参数", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = prettyArgs,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            if (detail.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (detail.status == "error") "错误" else "输出",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (detail.status == "error") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = detail.body,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
private fun toolExpandedText(
    name: String,
    args: String,
    body: String,
    status: String,
): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    val obj = runCatching { org.json.JSONObject(args) }.getOrNull()
    val editOld = obj?.optString("old_string").orEmpty()
    val editNew = obj?.optString("new_string").orEmpty()
    if (name.contains("edit") && (editOld.isNotBlank() || editNew.isNotBlank())) {
        if (editOld.isNotBlank()) {
            editOld.lineSequence().forEach { line ->
                withStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFE5484D))) {
                    append("- $line\n")
                }
            }
        }
        if (editNew.isNotBlank()) {
            editNew.lineSequence().forEach { line ->
                withStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF2F9E5F))) {
                    append("+ $line\n")
                }
            }
        }
        if (status != "running" && body.isNotBlank()) {
            append("\n")
            append(body.take(600))
        }
        return@buildAnnotatedString
    }
    if (status == "running") {
        val running = when {
            name.contains("bash") || name.contains("shell") -> obj?.optString("command").orEmpty()
            name.contains("write") -> obj?.optString("content").orEmpty()
            else -> obj?.toString().orEmpty()
        }
        append(running)
    } else {
        append(body)
    }
}

private fun toolIcon(name: String) = Icons.Default.Build
private fun toolTitle(name: String): String = when {
    name.contains("bash") || name.contains("shell") -> "运行命令"
    name.contains("read") -> "读取"
    name.contains("write") -> "写入"
    name.contains("edit") -> "编辑"
    name.contains("grep") -> "搜索文件内容"
    name.contains("glob") -> "查找文件"
    name.contains("ask_user_question") -> "询问"
    name.contains("create_goal") || name.contains("update_goal") -> "目标"
    name.contains("subagent") -> "子任务"
    name.contains("web_search") || name.contains("web_fetch") -> "网页"
    else -> "工具调用"
}

private fun toolSummary(name: String, args: String, body: String, status: String): String {
    val obj = runCatching { org.json.JSONObject(args) }.getOrNull()
    fun pick(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        obj?.optString(key)?.takeIf { it.isNotBlank() }
    }.orEmpty()
    val summary = when {
        name.contains("bash") || name.contains("shell") -> pick("description", "command")
        name.contains("read") || name.contains("write") || name.contains("edit") -> pick("file_path", "path")
        name.contains("grep") || name.contains("glob") -> pick("pattern", "query", "path")
        name.contains("ask_user_question") -> pick("question", "header")
        name.contains("goal") -> pick("objective", "title")
        name.contains("subagent") -> pick("description", "prompt")
        else -> pick("description", "command", "path", "pattern", "query")
    }
    if (summary.isNotBlank()) return summary
    if (status != "running" && body.isNotBlank()) return body.lineSequence().firstOrNull()?.take(120).orEmpty()
    return ""
}private data class AskOption(val label: String, val description: String)
private data class AskQuestion(val id: String, val question: String, val header: String, val options: List<AskOption>)

private fun parseAskQuestions(raw: String): List<AskQuestion> = runCatching {
    val root = org.json.JSONObject(raw)
    val arr = root.optJSONArray("questions") ?: return emptyList()
    (0 until arr.length()).mapNotNull { index ->
        val obj = arr.optJSONObject(index) ?: return@mapNotNull null
        val id = obj.optString("id")
        val question = obj.optString("question")
        if (id.isBlank() || question.isBlank()) return@mapNotNull null
        val optionArray = obj.optJSONArray("options")
        val options = if (optionArray == null) emptyList() else {
            (0 until optionArray.length()).mapNotNull { oi ->
                val oo = optionArray.optJSONObject(oi) ?: return@mapNotNull null
                AskOption(oo.optString("label"), oo.optString("description"))
            }
        }
        AskQuestion(id, question, obj.optString("header"), options)
    }
}.getOrDefault(emptyList())
@Composable
fun ModernMessageItem(
    message: ChatMessage,
    onRetry: () -> Unit = {},
    onAnswerUserQuestion: (callId: String, questionId: String, selected: String) -> Unit = { _, _, _ -> },
    onOpenToolDetail: (ToolDetailData) -> Unit = {},
) {
    val isUser = message.role == MessageRole.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(20.dp).copy(bottomEnd = androidx.compose.foundation.shape.CornerSize(4.dp)),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 48.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else if (message.role == MessageRole.TOOL) {
            ToolMessageItem(
                content = message.content,
                onAnswerUserQuestion = onAnswerUserQuestion,
                onOpenDetail = onOpenToolDetail,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {

                val contentParts = message.content.split('\u0001', limit = 2)
                if (contentParts.size == 2 && contentParts[0].isNotBlank()) {
                    ReasoningCard(
                        isFinished = true,
                        elapsedSeconds = "",
                        reasoningText = contentParts[0],
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = contentParts.last(),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(10.dp))

            }
        }
    }
}

/**
 * composer trigger 药丸（模型 / 权限选择器）：
 * 28dp 高、R8、左 8dp / 右 4dp 内边距、4dp 间距、13sp/20 二级色标签、
 * 14dp 菜单/箭头图标，按压用 hover 填充且无 Material 涟漪。
 */

/**
 * composer trigger 药丸（模型 / 权限选择器）：
 * 28dp 高、R8、左 8dp / 右 4dp 内边距、4dp 间距、13sp/20 二级色标签、
 * 14dp 菜单/箭头图标，按压用 hover 填充且无 Material 涟漪。
 */
@Composable
private fun ComposerTrigger(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIconResId: Int? = null,
    trailingValue: String? = null,
    labelWeight: FontWeight = FontWeight.Normal,
    compact: Boolean = false,
) {
    val colors = dshPanelColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) colors.interactiveHover else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(start = if (compact) 5.dp else 8.dp, end = if (compact) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
    ) {
        if (leadingIconResId != null) {
            Icon(
                painter = painterResource(id = leadingIconResId),
                contentDescription = null,
                tint = colors.labelSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            color = colors.labelSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = labelWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(
                max = if (trailingValue.isNullOrBlank()) 132.dp else 132.dp,
            )
        )
        if (!trailingValue.isNullOrBlank()) {
            Text(
                text = trailingValue,
                color = colors.labelCaption,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 60.dp)
            )
        }
        Icon(
            painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_chevron_down_official),
            contentDescription = null,
            tint = colors.labelCaption,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * 底部输入区：一个悬浮卡片，内部是文本域与操作行。
 *
 * 操作行自左向右：加号（命令菜单）→ 权限预设 → 弹性留白 → 模型与推理等级 → 发送/停止。
 */
@Composable
fun BottomInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPlusClick: () -> Unit,
    selectedModel: String,
    availableModels: List<String> = emptyList(),
    modelGroups: List<com.deepseek.dsh.core.model.ModelProviderGroup> = emptyList(),
    effortLabel: String,
    reasoningOptions: List<ReasoningOption> = emptyList(),
    selectedReasoningId: String = "",
    onOpenModelMenu: () -> Unit,
    modelMenuOpen: Boolean,
    onModelMenuDismiss: () -> Unit,
    onModelSelected: (String) -> Unit,
    onEffortSelected: (ReasoningLevel) -> Unit,
    onReasoningSelected: (ReasoningOption) -> Unit = {},
    currentPermission: PermissionPreset,
    onOpenPermissionMenu: () -> Unit,
    permissionMenuOpen: Boolean,
    onPermissionMenuDismiss: () -> Unit,
    onPermissionSelect: (PermissionPreset) -> Unit,
    isPlanMode: Boolean,
    isGenerating: Boolean,
    onStop: () -> Unit,
    dockPluginSurfaces: List<SurfaceItem> = emptyList(),
    onDockPluginClick: (SurfaceItem) -> Unit = {},
    stats: ComposerStats = ComposerStats(),
    showDockStats: Boolean = true
) {
    val panelColors = dshPanelColors()
    val plusInteraction = remember { MutableInteractionSource() }
    val tightComposer = selectedModel.length > 16
    val cardShape = RoundedCornerShape(28.dp)
    var permissionAnchor by remember { mutableStateOf<Rect?>(null) }
    var modelAnchor by remember { mutableStateOf<Rect?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = cardShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.05f),
                )
                .clip(cardShape)
                .background(panelColors.bgLayer2)
                .border(0.5.dp, panelColors.borderL2, cardShape)
        ) {
            Column(modifier = Modifier.padding(start = 0.dp, end = 4.dp, top = 8.dp)) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        color = panelColors.labelPrimary,
                    ),
                    cursorBrush = SolidColor(panelColors.labelPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp)
                        .heightIn(min = 44.dp)
                        .padding(top = 7.dp),
                    singleLine = false,
                    maxLines = 8,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = if (isPlanMode) stringResource(R.string.chat_input_placeholder_plan)
                                    else stringResource(R.string.chat_input_placeholder),
                                    fontSize = 14.sp,
                                    lineHeight = 24.sp,
                                    color = panelColors.labelCaption,
                                    modifier = Modifier.offset(x = (-2).dp, y = (-3.2).dp)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 5.dp, end = 5.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (tightComposer) 6.dp else 10.dp)
                ) {
                    // 加号：28dp 圆形 selector 按钮（无 Material 涟漪）
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(panelColors.bgModulePlatform)
                            .clickable(
                                interactionSource = plusInteraction,
                                indication = null,
                                onClick = onPlusClick,
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = DshIcons.PlusOutline,
                            contentDescription = stringResource(R.string.action_input_commands),
                            tint = panelColors.labelPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // 权限预设：trigger 28dp 高 / R8 / 13-20 二级色 / 14dp 图标与箭头
                    Box(modifier = Modifier.onGloballyPositioned { permissionAnchor = it.boundsInWindow() }) {
                        ComposerTrigger(
                            label = stringResource(currentPermission.titleResId),
                            leadingIconResId = currentPermission.iconResId(),
                            labelWeight = FontWeight.Medium,
                            onClick = onOpenPermissionMenu,
                            compact = tightComposer,
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                        PermissionMenu(
                            expanded = permissionMenuOpen,
                            anchorBounds = permissionAnchor,
                            current = currentPermission,
                            onDismiss = onPermissionMenuDismiss,
                            onSelect = onPermissionSelect,
                        )
                    }

                    // 模型与推理等级：trigger（当前模型 + 推理等级 + 箭头），菜单锚定在此
                    Box(modifier = Modifier.weight(1f).onGloballyPositioned { modelAnchor = it.boundsInWindow() }) {
                        ComposerTrigger(
                            label = selectedModel.ifBlank { "请选择模型" },
                            // 官方：未选模型时只显示「请选择模型」，不附带推理等级。
                            trailingValue = if (availableModels.isEmpty()) null else effortLabel,
                            onClick = onOpenModelMenu,
                            compact = tightComposer,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ModelEffortMenu(
                            expanded = modelMenuOpen,
                            anchorBounds = modelAnchor,
                            selectedModel = selectedModel,
                            availableModels = availableModels.distinct(),
                            modelGroups = modelGroups,
                            effortLabel = effortLabel,
                            reasoningOptions = reasoningOptions,
                            selectedReasoningId = selectedReasoningId,
                            onDismiss = onModelMenuDismiss,
                            onModelSelected = onModelSelected,
                            onEffortSelected = onEffortSelected,
                            onReasoningSelected = onReasoningSelected,
                        )
                    }

                    // 生成中：发送按钮切换为停止按钮，两者不同时出现。
                    if (isGenerating) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            onClick = onStop,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = DshIcons.StopFill,
                                    contentDescription = stringResource(R.string.action_stop),
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = onSend,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = DshIcons.SendUpArrow,
                                    contentDescription = stringResource(R.string.action_send),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        ComposerDockBar(
            stats = stats,
            showStats = showDockStats,
            dockPluginSurfaces = dockPluginSurfaces,
            onPluginClick = { pluginItem ->
                onDockPluginClick(pluginItem)
            },
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * 加号命令菜单：两组（添加 / 指令），行内左侧图标与标签、右侧描述。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerCommandSheet(
    onDismiss: () -> Unit,
    onFile: () -> Unit,
    onPlan: () -> Unit,
    onPermission: () -> Unit,
    onModel: () -> Unit,
    onCommand: (String) -> Unit
) {
    val addItems = listOf(
        ComposerCommand(
            R.string.cmd_file, R.string.cmd_file_desc, "file",
            com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_file
        ),
        ComposerCommand(
            R.string.cmd_goal, R.string.cmd_goal_desc, "goal",
            com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_goal
        ),
        ComposerCommand(
            R.string.cmd_plan, R.string.cmd_plan_desc, "plan",
            com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_plan
        ),
        ComposerCommand(
            R.string.cmd_feedback, R.string.cmd_feedback_desc, "feedback",
            com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_feedback
        ),
    )
    val commandItems = listOf(
        ComposerCommand(
            R.string.cmd_compact, R.string.cmd_compact_desc, "compact",
            com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_compact
        ),
        ComposerCommand(
            R.string.cmd_permission, R.string.cmd_permission_desc, "permission",
            com.deepseek.dsh.core.designsystem.R.drawable.ic_perm_full_access
        ),
        ComposerCommand(
            R.string.cmd_model, R.string.cmd_model_desc, "model",
            com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_model
        ),
        ComposerCommand(
            R.string.cmd_export, R.string.cmd_export_desc, "export",
            com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_export
        ),
    )

    DshSheetPanel(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DshMenuSectionLabel(stringResource(R.string.cmd_section_add))
            addItems.forEach { item ->
                ComposerCommandRow(
                    labelResId = item.labelResId,
                    descResId = item.descResId,
                    token = item.token,
                    iconResId = item.iconResId,
                    onClick = {
                        when (item.token) {
                            "file" -> onFile()
                            "plan" -> onPlan()
                            else -> onCommand("/${item.token}")
                        }
                    }
                )
            }

            Spacer(Modifier.height(3.dp))

            DshMenuSectionLabel(stringResource(R.string.cmd_section_commands))
            commandItems.forEach { item ->
                ComposerCommandRow(
                    labelResId = item.labelResId,
                    descResId = item.descResId,
                    token = item.token,
                    iconResId = item.iconResId,
                    onClick = {
                        when (item.token) {
                            "permission" -> onPermission()
                            "model" -> onModel()
                            else -> onCommand("/${item.token}")
                        }
                    }
                )
            }
        }
    }
}
@Composable
private fun ComposerCommandRow(
    labelResId: Int,
    descResId: Int,
    token: String,
    iconResId: Int,
    onClick: () -> Unit
) {
    DshMenuRow(onClick = onClick) {
        DshMenuIcon(iconResId, size = 14.dp)
        DshMenuLabel(stringResource(labelResId))
        DshMenuCaption(token)
        Spacer(Modifier.weight(1f))
        DshMenuDescription(stringResource(descResId))
    }
}






















































































































