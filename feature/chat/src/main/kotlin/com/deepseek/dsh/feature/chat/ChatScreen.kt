package com.deepseek.dsh.feature.chat

import kotlinx.serialization.json.put

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.deepseek.dsh.core.designsystem.AnchoredMenuOverlay
import com.deepseek.dsh.core.designsystem.AnchoredMenuPopup
import com.deepseek.dsh.core.designsystem.DshDialogButton
import com.deepseek.dsh.core.designsystem.DshIcons
import com.deepseek.dsh.core.designsystem.DshMenuBadge
import com.deepseek.dsh.core.designsystem.DshMenuCaption
import com.deepseek.dsh.core.designsystem.DshMenuCheck
import com.deepseek.dsh.core.designsystem.DshMenuDescription
import com.deepseek.dsh.core.designsystem.DshMenuDivider
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

/** 文本类斜杠命令的本地化名称（输入框 chip 用）；非文本命令返回 null。 */
private fun commandLabelRes(token: String): Int? = when (token) {
    "goal" -> R.string.cmd_goal
    "feedback" -> R.string.cmd_feedback
    "compact" -> R.string.cmd_compact
    "export" -> R.string.cmd_export
    else -> null
}

/** 文本类斜杠命令的参数提示（选中命令后的 placeholder）。 */
private fun commandHintRes(token: String): Int? = when (token) {
    "goal" -> R.string.cmd_goal_hint
    "feedback" -> R.string.cmd_feedback_hint
    "compact" -> R.string.cmd_compact_hint
    "export" -> R.string.cmd_export_hint
    else -> null
}

/**
 * 主聊天页：信息结构 + 手机端（ChatGPT Android）尺度的输入区。
 *
 * 输入卡片内自上而下是文本域与操作行；操作行自左向右为加号（命令菜单）、
 * 权限预设、弹性留白、模型与推理等级、发送/停止，与 `InputBar` 的
 * tools / trailing 两簇排布一致。
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModernChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    isSending: Boolean = false,
    /** 当前会话正在流式输出的助手文本（重连基线 + live chunk）；空串表示没有。 */
    streamingText: String = "",
    /** 当前会话正在流式输出的推理文本。 */
    streamingReasoning: String = "",
    /** 列表滚到顶部时请求更早的一页历史。 */
    onLoadOlder: () -> Unit = {},
    /** 官方 `commands/list` 的斜杠命令（Host 注册的，动态显示在加号菜单）。 */
    hostCommands: List<com.deepseek.dsh.core.model.HostCommandInfo> = emptyList(),
    /** 执行一条 Host 斜杠命令（commands/execute）。 */
    onHostCommand: (String) -> Unit = {},
    /** 官方 `skills/list`：当前会话可调用的技能。 */
    skills: List<com.deepseek.dsh.core.model.SkillInfo> = emptyList(),
    /** 打开加号菜单前刷新技能目录（冷读 skills/list）。 */
    onRefreshSkills: () -> Unit = {},
    /** 直接读取技能目录（菜单打开时用返回值渲染，避免状态管道丢失）。 */
    loadSkills: suspend () -> List<com.deepseek.dsh.core.model.SkillInfo> = { emptyList() },
    /** 官方 `todos` 投影（当前会话待办）。 */
    todos: List<com.deepseek.dsh.core.model.TodoEntryInfo> = emptyList(),
    /** 官方 `goal` 投影（当前会话目标）。 */
    goal: com.deepseek.dsh.core.model.GoalInfo? = null,
    /** 官方 `deliverables/presented` 的产物列表。 */
    deliverables: List<com.deepseek.dsh.core.protocol.PresentedFileInfo> = emptyList(),
    /** 当前会话最近一轮的改动汇总（workspace/changes）。 */
    changes: com.deepseek.dsh.core.protocol.ChangesSummaryInfo? = null,
    /** 官方 inbox 投影里的排队消息（运行中发送的消息）。 */
    queuedMessages: List<com.deepseek.dsh.core.model.QueuedMessageInfo> = emptyList(),
    /** 排队消息操作：kind = edit / remove / steer。 */
    onQueueAction: (id: String, kind: String, text: String?) -> Unit = { _, _, _ -> },
    /** 打开某个改动文件的 diff（下标是 summary.files 的原始下标）。 */
    onLoadChangeDiff: suspend (Int) -> com.deepseek.dsh.core.protocol.ChangesDiffInfo? = { null },
    /** 点开一份产物（切到文件页并定位到该目录）。 */
    onOpenDeliverable: (com.deepseek.dsh.core.protocol.PresentedFileInfo) -> Unit = {},
    workspaceName: String = "默认工作区",
    /** 系统文件选择器选回的附件；由宿主负责复制进工作区后传回这里。 */
    attachments: List<ChatAttachment> = emptyList(),
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
    /** 点击「文件」：打开系统文件选择器（图片/任意文件）。 */
    onPickAttachments: () -> Unit = {},
    /** 会话级计划模式（横幅状态）；本地 placeholder 与发送文案随之切换。 */
    planMode: Boolean = false,
    onPlanModeChange: (Boolean) -> Unit = {},
    /** 可切换的工作区名称，来自 Host 的工作区列表。 */
    workspaceNames: List<String> = emptyList(),
    onWorkspaceSelected: (String) -> Unit = {},
    onAddWorkspace: () -> Unit = {},
    onWorkspaceClick: () -> Unit = {},
    onAnswerUserQuestion: (callId: String, questionId: String, selected: String) -> Unit = { _, _, _ -> },
    selectedModel: String = "",
    /** 当前模型 id（如 deepseek-account/deepseek-flash、kimi-for-coding），传给 dock 解析 provider。 */
    selectedModelId: String = "",
    /** 原生落点要求切换模型/推理档位（如插件 fast-mode 按钮）。 */
    onNativeModelSelect: suspend (provider: String, model: String, reasoningEffort: String?) -> Result<Unit> =
        { _, _, _ -> Result.failure(IllegalStateException("Model switching is not supported")) },
    availableModels: List<String> = emptyList(),
    modelGroups: List<com.deepseek.dsh.core.model.ModelProviderGroup> = emptyList(),
    onModelSelected: (provider: String, modelId: String, name: String) -> Unit = { _, _, _ -> },
    selectedPermission: PermissionPreset = PermissionPreset.WORKSPACE_WRITE,
    agentPresetLabel: String = "标准模式",
    /** 当前选中的预设 id（standard/cordis），用于菜单打勾。 */
    agentPresetSelectedId: String = "standard",
    onAgentPresetSelected: (String) -> Unit = {},
    onPermissionSelected: (PermissionPreset) -> Unit = {},
    reasoningLevel: ReasoningLevel = ReasoningLevel.FAST,
    reasoningOptions: List<ReasoningOption> = emptyList(),
    selectedReasoningId: String = "",
    effortLabelOverride: String? = null,
    onReasoningLevelChange: (ReasoningLevel) -> Unit = {},
    onReasoningSelected: (ReasoningOption) -> Unit = {},
    stats: ComposerStats = ComposerStats(),
    // 录音输入（官方录音输入插件的 composer activity）：麦克风按钮与录音条。
    // Claude Code 模组横幅（官方 conversation.input.dock）。

    /** 思考内容翻译（官方 cot-translation 的 reasoning body）；null 表示未启用。 */


    translateReasoning: (suspend (String) -> Result<String>)? = null,


    bandSnapshot: com.deepseek.dsh.core.data.ClaudeCodeBandSnapshot? = null,

    bandPressing: Boolean = false,

    onBandPress: (Long, String) -> Unit = { _, _ -> },

    voiceEnabled: Boolean = false,
    voiceRecording: Boolean = false,
    voiceSeconds: Int = 0,
    voiceLevel: Float = 0f,
    voiceBusy: Boolean = false,
    voiceError: String? = null,
    voiceTranscript: String? = null,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    onVoiceTranscriptConsumed: () -> Unit = {},
    isThinking: Boolean = false,
    onStopClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    // 语音转写结果插进草稿：官方 insertText 是在录前记下的光标处插入；
    // 原生草稿是单一字符串，这里追加到末尾，同样可继续编辑后发送。
    LaunchedEffect(voiceTranscript) {
        val transcript = voiceTranscript?.trim()
        if (!transcript.isNullOrEmpty()) {
            val base = inputText.trimEnd()
            inputText = when {
                base.isBlank() -> transcript
                base.endsWith(transcript) -> base
                else -> "$base $transcript"
            }
        }
        if (voiceTranscript != null) onVoiceTranscriptConsumed()
    }
    var currentPermission by remember(selectedPermission) { mutableStateOf(selectedPermission) }
    var commandSheetOpen by remember { mutableStateOf(false) }
    var trajectoryOpen by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val localContext = LocalContext.current
    // 技能目录随菜单打开现拉：工作区的 .dsh/skills 可能刚被改动。
    val sheetSkills = remember { mutableStateOf(skills) }
    val sheetScope = rememberCoroutineScope()
    LaunchedEffect(skills) {
        if (skills.isNotEmpty()) sheetSkills.value = skills
    }
    // 从加号菜单选中的斜杠命令：输入框内显示本地化 chip（如 /目标），
    // placeholder 变成该命令的参数提示，发送时再还原成 Host 认识的 /goal。
    var activeCommandToken by remember { mutableStateOf<String?>(null) }
    var toolDetail by remember { mutableStateOf<ToolDetailData?>(null) }
    var changeDiffIndex by remember { mutableStateOf<Int?>(null) }
    var changeDiff by remember { mutableStateOf<com.deepseek.dsh.core.protocol.ChangesDiffInfo?>(null) }
    var changeDiffLoading by remember { mutableStateOf(false) }
    LaunchedEffect(changeDiffIndex) {
        val index = changeDiffIndex ?: return@LaunchedEffect
        changeDiff = null
        changeDiffLoading = true
        changeDiff = onLoadChangeDiff(index)
        changeDiffLoading = false
    }
    var workspaceMenuOpen by remember { mutableStateOf(false) }
    var workspaceAnchor by remember { mutableStateOf<Rect?>(null) }
    var permissionMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var agentPresetMenuOpen by remember { mutableStateOf(false) }
    var agentPresetAnchor by remember { mutableStateOf<Rect?>(null) }
    var pendingConfirm by remember { mutableStateOf<PermissionPreset?>(null) }

    // 消息反馈：宿主通过 MessageFeedbackBridge 注入拉取函数，ChatScreen 自己维护展示。
    LaunchedEffect(messages.size, messages.firstOrNull()?.sessionId) {
        val refresh = MessageFeedbackBridge.refresh
        if (refresh != null && messages.isNotEmpty()) {
            MessageFeedbackBridge.items = refresh()
        }
        MessageMediaBridge.items = MessageMediaBridge.provider?.invoke() ?: emptyMap()
        WorkflowRunsBridge.items = WorkflowRunsBridge.provider?.invoke() ?: emptyList()
    }
    val listState = rememberLazyListState()
    val collapsedTurns = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val chatRows = remember(messages, isThinking) { buildChatRows(messages, isThinking) }
    // slot 贡献（fast-mode 等）通过同一入口切换当前会话模型/推理档位。
    com.deepseek.dsh.plugin.nativehost.NativeHost.modelSelectHandler = onNativeModelSelect

    // chatRows 会把思考/工具过程也拆成行，索引与 messages 不一致；按实际行数滚到底。
    LaunchedEffect(chatRows.size, isThinking) {
        if (chatRows.isNotEmpty()) {
            listState.animateScrollToItem(chatRows.lastIndex)
        }
    }

    // showContext=false 时只渲染输入框：工作区/模式只属于新会话界面。
    val composerBlock: @Composable (Boolean) -> Unit = { showContext ->
        // 计划模式横幅：进入后只生成计划、等用户确认再执行；可随时退出。
        if (planMode) {
            PlanModeBanner(onExit = { onPlanModeChange(false) })
        }
        if (showContext) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.onGloballyPositioned { workspaceAnchor = it.boundsInWindow() }) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { workspaceMenuOpen = !workspaceMenuOpen }
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
            // 与官方页面一致：点工作区名展开工作区列表，当前项打勾，末尾是「添加工作区」。
            WorkspaceMenu(
                expanded = workspaceMenuOpen,
                anchorBounds = workspaceAnchor,
                current = workspaceName,
                names = workspaceNames,
                onDismiss = { workspaceMenuOpen = false },
                onSelect = { name -> workspaceMenuOpen = false; onWorkspaceSelected(name) },
                onAdd = { workspaceMenuOpen = false; onAddWorkspace() },
            )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.onGloballyPositioned { agentPresetAnchor = it.boundsInWindow() }) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { agentPresetMenuOpen = !agentPresetMenuOpen }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            // 与工作区/权限/模型同一套 popup：列出可选预设，当前项打勾。
            AgentPresetMenu(
                expanded = agentPresetMenuOpen,
                currentId = agentPresetSelectedId,
                onDismiss = { agentPresetMenuOpen = false },
                onSelect = { id ->
                    agentPresetMenuOpen = false
                    onAgentPresetSelected(id)
                },
            )
            }
        }
        }
        // 已选斜杠命令的本地化展示：/目标、/反馈、/压缩、/下载日志。
        val activeCommandLabel = activeCommandToken
            ?.let { token -> commandLabelRes(token)?.let { stringResource(it) } }
        val activeCommandHint = activeCommandToken
            ?.let { token -> commandHintRes(token)?.let { stringResource(it) } }
        BottomInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            activeCommandLabel = activeCommandLabel,
            activeCommandHint = activeCommandHint,
            onClearCommand = { activeCommandToken = null },
            onSend = {
                // 允许「只发附件」：有附件没文字也能发送，消息里只有 @路径。
                if (inputText.isNotBlank() || attachments.isNotEmpty() || activeCommandToken != null) {
                    val attachmentBlock = if (attachments.isEmpty()) {
                        ""
                    } else {
                        attachments.filterNot { it.isImageAttachment() }
                            .joinToString(" ") { it.asMessageReference() }
                            .let { block -> if (block.isBlank()) "" else block + "\n\n" }
                    }
                    // 输入框里显示的是本地化 chip（/目标）；发送时还原成 Host 命令 /goal。
                    val commandPrefix = activeCommandToken?.let { "/$it " } ?: ""
                    val text = attachmentBlock + commandPrefix + inputText.trim()
                    inputText = ""
                    activeCommandToken = null
                    onSendMessage(text)
                }
            },
            onPlusClick = {
                // 技能来自工作区 .dsh/skills，可能刚被创建/修改；打开菜单前冷读一次。
                onRefreshSkills()
                commandSheetOpen = true
                sheetScope.launch {
                    val loaded = runCatching { loadSkills() }.getOrNull().orEmpty()
                    if (loaded.isNotEmpty()) sheetSkills.value = loaded
                }
            },
            selectedModel = selectedModel,
            selectedModelId = selectedModelId,
            availableModels = availableModels,
            modelGroups = modelGroups,
            effortLabel = effortLabelOverride ?: stringResource(reasoningLevel.labelResId),
            reasoningOptions = reasoningOptions,
            selectedReasoningId = selectedReasoningId,
            onOpenModelMenu = {
                permissionMenuOpen = false
                modelMenuOpen = true
            },
            onModelMenuDismiss = { modelMenuOpen = false },
            modelMenuOpen = modelMenuOpen,
            onModelSelected = { provider, modelId, name ->
                modelMenuOpen = false
                onModelSelected(provider, modelId, name)
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
            isPlanMode = planMode,
            isGenerating = isThinking,
            onStop = onStopClick,
            attachments = attachments,
            onRemoveAttachment = onRemoveAttachment,
            stats = stats,

            voiceEnabled = voiceEnabled,

            voiceRecording = voiceRecording,

            voiceSeconds = voiceSeconds,

            voiceLevel = voiceLevel,

            voiceBusy = voiceBusy,

            voiceError = voiceError,

            onVoiceStart = onVoiceStart,

            onVoiceStop = onVoiceStop,

            onVoiceCancel = onVoiceCancel,
            bandSnapshot = bandSnapshot,
            bandPressing = bandPressing,
            onBandPress = onBandPress,
            showDockStats = messages.isNotEmpty() || isSending
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        // conversation.view：插件可以在会话视图整体位置增强（官方默认内容为空占位）。
        com.deepseek.dsh.plugin.nativehost.OfficialSlot(
            slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.CONVERSATION_VIEW,
            arrangement = com.deepseek.dsh.plugin.nativehost.SlotArrangement.BLOCK,
        ) {}
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
                    composerBlock(true)
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
                        text = stringResource(R.string.chat_creating_session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LaunchedEffect(listState.firstVisibleItemIndex, messages.size) {
                if (messages.isNotEmpty() && listState.firstVisibleItemIndex <= 0) {
                    onLoadOlder()
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Top
            ) {
                itemsIndexed(
                    items = chatRows,
                    key = { _, row ->
                        when (row) {
                            is ChatRow.Single -> row.message.messageId
                            is ChatRow.Process -> "proc_${row.key}"
                            is ChatRow.TurnDone -> "done_${row.key}"
                        }
                    }
                ) { index, row ->
                    val previous = chatRows.getOrNull(index - 1)
                    val topPadding = when {
                        row is ChatRow.TurnDone -> 8.dp
                        // 完成头下面紧跟 AI 正文：不再叠加 16dp 的全局行距。
                        previous is ChatRow.TurnDone -> 2.dp
                        else -> 12.dp
                    }
                    Column(modifier = Modifier.padding(top = topPadding)) {
                    when (row) {
                        is ChatRow.Single -> Column(
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { actionMessage = row.message },
                            ),
                        ) {
                            // 官方 user 气泡：图片缩略图在文本上方。
                            if (row.message.role == MessageRole.USER) {
                                val refs = MessageMediaBridge.items[row.message.messageId]
                                if (!refs.isNullOrEmpty()) {
                                    MessageImagesRow(
                                        images = refs,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                    )
                                }
                            }
                            ModernMessageItem(
                                message = row.message,
                                onRetry = { onSendMessage(row.message.content) },
                                onAnswerUserQuestion = onAnswerUserQuestion,
                                onOpenToolDetail = { toolDetail = it },
                                translateReasoning = translateReasoning,
                            )
                            // 官方 message-feedback：助手消息下方的赞/踩（可选备注）。
                            if (row.message.role == MessageRole.ASSISTANT &&
                                row.message.content.isNotBlank() &&
                                !row.message.content.trimStart().startsWith("⚠️")
                            ) {
                                MessageFeedbackActions(
                                    current = MessageFeedbackBridge.items[row.message.messageId],
                                    onRating = { rating ->
                                        val existing = MessageFeedbackBridge.items[row.message.messageId]
                                        MessageFeedbackBridge.submit?.invoke(
                                            row.message.messageId,
                                            if (existing?.rating == rating) null else rating,
                                            existing?.note,
                                        )
                                    },
                                    onNote = { note ->
                                        val existing = MessageFeedbackBridge.items[row.message.messageId]
                                        MessageFeedbackBridge.submit?.invoke(
                                            row.message.messageId,
                                            existing?.rating ?: "positive",
                                            note,
                                        )
                                    },
                                )
                            }
                        }
                        is ChatRow.TurnDone -> TurnCompletedRow(
                            durationMs = row.durationMs,
                            expanded = collapsedTurns[row.key] == true,
                            onClick = { collapsedTurns[row.key] = collapsedTurns[row.key] != true },
                            onLongClick = { trajectoryOpen = true },
                        )
                        is ChatRow.Process -> {
                            // 思考链默认折叠：只有用户点开本轮完成头才渲染过程内容。
                            if (collapsedTurns[row.key] == true) {
                                ToolProcessGroup(
                                    items = row.items,
                                    expanded = true,
                                    header = false,
                                    onToggle = {},
                                    onOpenDetail = { toolDetail = it },
                                    translateReasoning = translateReasoning,
                                )
                            }
                        }
                    }
                    } // 行间距容器
                }

                if (streamingReasoning.isNotBlank() || streamingText.isNotBlank()) {
                    item(key = "streaming_assistant") {
                        Column {
                            if (streamingReasoning.isNotBlank()) {
                                Text(
                                    text = streamingReasoning,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                )
                            }
                            if (streamingText.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top = if (streamingReasoning.isNotBlank()) 6.dp else 12.dp,
                                        ),
                                ) {
                                    MarkdownMessageText(
                                        text = streamingText,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        }
                    }
                }
                val hasOpenProcess = chatRows.any { row ->
                    row is ChatRow.Process && collapsedTurns[row.key] == true
                }
                if (isThinking && !hasOpenProcess) {
                    item {
                        LiveToolStatus(messages = messages)
                    }
                }
                // conversation.chat.flow / chat.node：原生插件在消息流末尾的落点
                // （会话摘要卡、跟进建议等）。
                item(key = "native_slot_chat_stream") {
                    com.deepseek.dsh.plugin.nativehost.NativeSlot(
                        slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.CHAT_STREAM,
                        spec = kotlinx.serialization.json.JsonObject(emptyMap()),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                // 官方 ui-deliverables：改动卡在产物卡之前（同一 turn 尾部）。
                if (changes != null && changes!!.files.isNotEmpty()) {
                    item(key = "changed_files") {
                        ChangedFilesCard(
                            summary = changes!!,
                            onOpenDiff = { index ->
                                changeDiffIndex = index
                            },
                        )
                    }
                }
                if (WorkflowRunsBridge.items.isNotEmpty()) {
                    item(key = "workflow_runs") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WorkflowRunsBridge.items.forEach { run ->
                                WorkflowRunCard(run = run)
                            }
                        }
                    }
                }
                if (deliverables.isNotEmpty()) {
                    item(key = "presented_files") {
                        PresentedFilesRow(
                            files = deliverables,
                            onOpen = onOpenDeliverable,
                        )
                    }
                }
            }
        }

        toolDetail?.let { detail ->
            ToolDetailSheet(detail = detail, onDismiss = { toolDetail = null })
        }

        if (trajectoryOpen) {
            TrajectoryDialog(onDismiss = { trajectoryOpen = false })
        }

        actionMessage?.let { message ->
            MessageActionDialog(
                message = message,
                onCopy = { text ->
                    val clipboard = localContext.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE,
                    ) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("message", text),
                    )
                    android.widget.Toast.makeText(
                        localContext,
                        localContext.getString(R.string.markdown_copied),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
                onFork = { seq -> MessageActionBridge.forkAt?.invoke(seq) },
                onDismiss = { actionMessage = null },
            )
        }

        changeDiffIndex?.let { index ->
            val file = changes?.files?.getOrNull(index)
            ChangeDiffSheet(
                title = file?.display ?: "",
                diff = if (changeDiffLoading) null else changeDiff,
                onDismiss = { changeDiffIndex = null },
            )
        }

        if (queuedMessages.isNotEmpty()) {
            QueuedMessagesDock(
                messages = queuedMessages,
                running = isThinking,
                onEdit = { message, text -> onQueueAction(message.id, "edit", text) },
                onRemove = { message -> onQueueAction(message.id, "remove", null) },
                onSteer = { message -> onQueueAction(message.id, "steer", null) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (goal != null || todos.isNotEmpty()) {
            SessionPlanDock(
                goal = goal,
                todos = todos,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (!(messages.isEmpty() && !isSending)) {
            composerBlock(false)
        }
    }

    // 加号：分组命令菜单（添加 / 指令）
    if (commandSheetOpen) {
        ComposerCommandSheet(
            onDismiss = { commandSheetOpen = false },
            hostCommands = hostCommands,
            skills = sheetSkills.value,
            onSkill = { name ->
                commandSheetOpen = false
                inputText = (if (inputText.isBlank()) "" else inputText.trimEnd() + " ") + "/$name "
            },
            onFile = {
                commandSheetOpen = false
                onPickAttachments()
            },
            onPlan = {
                commandSheetOpen = false
                onPlanModeChange(!planMode)
            },
            onPermission = {
                commandSheetOpen = false
                permissionMenuOpen = true
            },
            onModel = {
                commandSheetOpen = false
                modelMenuOpen = true
            },
            onCommand = { command ->
                commandSheetOpen = false
                val token = command.trim().removePrefix("/")
                // 「导出」是本地动作：直接下载官方 ZIP，不进入 prompt。
                if (token == "export") {
                    MessageActionBridge.exportSession?.invoke()
                    return@ComposerCommandSheet
                }
                // 文本类命令在输入框里显示本地化 chip，参数由用户继续输入；
                // 发送时再还原成 /goal 之类 Host 认识的命令。
                if (commandLabelRes(token) != null) {
                    activeCommandToken = token
                }
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


/**
 * 工作区切换菜单：当前工作区打勾，末尾一行是「添加工作区…」。
 *
 * 列表内容来自 Host 的工作区列表，客户端不自己记一份；空列表时只显示添加入口。
 */
@Composable
private fun WorkspaceMenu(
    expanded: Boolean,
    anchorBounds: Rect?,
    current: String,
    names: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    // 与权限 / 模型菜单同一套 popup：不指定固定宽度，让卡片按内容量到最小宽度，
    // 分隔线用设计系统的 DshMenuDivider，而不是自己画一条 Material 颜色。
    AnchoredMenuPopup(
        expanded = expanded,
        onDismiss = onDismiss,
        align = DshMenuAlign.Start,
    ) {
        names.forEach { name ->
            val selected = name == current
            DshMenuRow(onClick = { onSelect(name) }) {
                DshMenuIcon(
                    com.deepseek.dsh.core.designsystem.R.drawable.ic_folder_open,
                    size = 14.dp,
                )
                DshMenuLabel(name, Modifier.alignByBaseline())
                Spacer(Modifier.weight(1f))
                if (selected) DshMenuCheck()
            }
        }
        if (names.isNotEmpty()) DshMenuDivider()
        DshMenuRow(onClick = onAdd) {
            // 没有对应的 plus 图形资源时用文字加号，保持与其它行的左边距一致。
            Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "＋",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DshMenuLabel(stringResource(R.string.workspace_add), Modifier.alignByBaseline())
        }
    }
}

/**
 * 预设 id → 本地化显示名；会话顶栏和输入栏共用。
 * @param presetId Host 预设标识（standard/cordis/minimal/ptc）。
 */
@Composable
fun agentPresetLabel(presetId: String): String = stringResource(
    when (presetId) {
        "cordis" -> R.string.preset_creative_title
        "minimal" -> R.string.preset_minimal_title
        "ptc" -> R.string.preset_ptc_title
        else -> R.string.preset_standard_title
    },
)

/**
 * Agent 预设菜单：与官方新会话页一致，只列「标准模式 / 创造模式」两项，
 * 名称 + 描述两行，当前项打勾。PTC / 极简模式只在「开发者工具」开启后才
 * 出现在官方菜单里，这里不开放。
 */
@Composable
private fun AgentPresetMenu(
    expanded: Boolean,
    currentId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    // 与官方 AgentPresetSeat 的候选项一致：标准 + 创造；名称与描述来自官方 i18n。
    val entries = listOf(
        Triple("standard", R.string.preset_standard_title, R.string.preset_standard_desc),
        Triple("cordis", R.string.preset_creative_title, R.string.preset_creative_desc),
    )
    AnchoredMenuPopup(
        expanded = expanded,
        onDismiss = onDismiss,
        align = DshMenuAlign.Start,
        contentWidth = true,
    ) {
        entries.forEach { (id, titleRes, descRes) ->
            val selected = id == currentId
            val name = stringResource(titleRes)
            val description = stringResource(descRes)
            DshMenuRow(onClick = { onSelect(id) }) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = LocalDshPanelColors.current.labelPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                    Text(
                        text = description,
                        color = LocalDshPanelColors.current.labelTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
    onModelSelected: (provider: String, modelId: String, name: String) -> Unit,
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

    // 稳定展开方向与高度：展开期间不随内部页切换而翻转，也不超出可用空间。
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val anchorTopPx = anchorBounds?.top ?: screenHeightPx
    val anchorBottomPx = anchorBounds?.bottom ?: screenHeightPx
    // 有足够下方空间就固定向下（新会话里就是这种布局）；否则固定向上。
    // 方向在展开期间只算一次，切页不会翻转；高度也限制在该方向的可用空间内。
    val marginPx = with(density) { 12.dp.toPx() }
    val minNeededBelowPx = with(density) { 180.dp.toPx() }
    val preferAbove = remember(expanded) {
        screenHeightPx - anchorBottomPx < minNeededBelowPx
    }
    val menuMaxHeight = remember(expanded, anchorBounds) {
        val available = if (preferAbove) {
            anchorTopPx - marginPx * 2
        } else {
            screenHeightPx - anchorBottomPx - marginPx * 2
        }
        with(density) { available.coerceIn(160.dp.toPx(), 360.dp.toPx()).toDp() }
    }

    AnchoredMenuPopup(
        expanded = expanded,
        onDismiss = onDismiss,
        menuWidth = if (page == 0) rowWidth.coerceAtLeast(300.dp) else maxOf(320.dp, rowWidth),
        align = DshMenuAlign.Start,
        maxHeight = menuMaxHeight,
        // 卡片底部与 trigger 顶部齐平，不能压住 Composer。
        anchorGap = 0.dp,
        preferredAbove = preferAbove,
    ) {
        when (page) {
            0 -> {
                if (availableModels.distinct().isEmpty()) {
                    // 官方空目录文案：暂无可用模型。
                    DshMenuSectionLabel(stringResource(R.string.model_none_available))
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
                // 官方 orderModelProviders：账号 → 官方 → 其余，保持目录内相对顺序；
                // 账号组的显示名走本地化文案（后端给的是 DeepSeek Account）。
                val accountLabel = stringResource(R.string.model_provider_account)
                fun providerRank(id: String): Int = when (id) {
                    "deepseek-account" -> 0
                    "deepseek-official" -> 1
                    else -> 2
                }
                val groups = remember(modelGroups, accountLabel) {
                    modelGroups
                        .sortedWith(compareBy { providerRank(it.id) })
                        .map { group ->
                            if (group.id == "deepseek-account") {
                                group.copy(name = accountLabel)
                            } else {
                                group
                            }
                        }
                }
                val flatModels = if (groups.isEmpty()) availableModels.distinct() else emptyList()
                val totalModels = if (groups.isEmpty()) flatModels.size else groups.sumOf { it.models.size }
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
                                    Text(stringResource(R.string.model_search_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                inner()
                            }
                        )
                    }
                }
                // 分组与模型名一律用 Host `session/modelCatalog` 的投影：不在客户端按
                // 名字前缀造分组（那会把 Claude/Gemini/Kimi 拼成假分组）。
                if (groups.isEmpty()) {
                    flatModels
                        .filter { query.isEmpty() || it.lowercase().contains(query) }
                        .forEach { name ->
                            val selected = name == selectedModel
                            DshMenuRow(onClick = {
                                onModelSelected("", name, name)
                                onDismiss()
                            }) {
                                DshMenuLabel(name, Modifier.weight(1f))
                                if (selected) DshMenuCheck()
                            }
                        }
                } else {
                    groups.forEach { group ->
                        val models = group.models.filter {
                            query.isEmpty() || it.name.lowercase().contains(query)
                        }
                        if (models.isNotEmpty()) {
                            DshMenuSectionLabel(group.name)
                            models.forEach { model ->
                                val selected = model.name == selectedModel
                                DshMenuRow(
                                    onClick = {
                                        onModelSelected(group.id, model.id, model.name)
                                    },
                                ) {
                                    DshMenuLabel(model.name, Modifier.weight(1f))
                                    if (selected) DshMenuCheck()
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // 推理等级只列 Host modelCatalog 里该模型真实提供的档位；没有就
                // 明确说不可调，不再用客户端枚举的预设文案冒充。
                if (reasoningOptions.isEmpty()) {
                    DshMenuSectionLabel(stringResource(R.string.model_effort_unavailable))
                } else {
                    reasoningOptions.forEach { option ->
                        val selected = option.id == selectedReasoningId
                        DshMenuRow(onClick = { onReasoningSelected(option) }) {
                            DshMenuLabel(option.label, Modifier.weight(1f))
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
                    text = stringResource(R.string.chat_preview_badge),
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
 *
 * @param translateReasoning 思考内容翻译（官方 cot-translation 的
 *   `conversation.chat.reasoning.body` 部分）；null 表示插件未启用。
 */
@Composable
fun ReasoningCard(
    isFinished: Boolean,
    elapsedSeconds: String,
    reasoningText: String,
    modifier: Modifier = Modifier,
    translateReasoning: (suspend (String) -> Result<String>)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val preview = remember(reasoningText) { reasoningText.lineSequence().firstOrNull().orEmpty() }
    // 译文状态：官方在展开的思考内容上提供「查看译文/查看原文」与失败重试。
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var translated by remember(reasoningText) { mutableStateOf<String?>(null) }
    var showTranslation by remember(reasoningText) { mutableStateOf(false) }
    var translating by remember(reasoningText) { mutableStateOf(false) }
    var translateFailed by remember(reasoningText) { mutableStateOf(false) }
    fun toggleTranslation() {
        val translator = translateReasoning ?: return
        if (showTranslation) {
            showTranslation = false
            return
        }
        val cached = translated
        if (cached != null) {
            showTranslation = true
            return
        }
        scope.launch {
            translating = true
            translateFailed = false
            translator(reasoningText).fold(
                onSuccess = { text ->
                    translated = text
                    showTranslation = true
                },
                onFailure = { translateFailed = true },
            )
            translating = false
        }
    }

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
            Column {
                if (translateReasoning != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 22.dp, top = 4.dp),
                    ) {
                        if (translating) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.chat_translating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        } else {
                            Text(
                                text = if (showTranslation) {
                                    stringResource(R.string.chat_view_original)
                                } else {
                                    stringResource(R.string.chat_view_translation)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { toggleTranslation() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (translateFailed) {
                            Text(
                                text = stringResource(R.string.chat_translation_unavailable),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                text = stringResource(R.string.action_retry),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { toggleTranslation() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = if (showTranslation) translated ?: reasoningText else reasoningText,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp, fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** 回合结束：官方「已完成，用时 X」+ 分割线，点击折叠/展开整个过程块。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TurnCompletedRow(
    durationMs: Long,
    expanded: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    val duration = when {
        durationMs < 0 -> null
        seconds >= 60 -> stringResource(R.string.chat_duration_minutes, seconds / 60, seconds % 60)
        else -> stringResource(R.string.chat_duration_seconds, seconds)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .height(22.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (duration == null) {
                    stringResource(R.string.chat_completed)
                } else {
                    stringResource(R.string.chat_completed_with_duration, duration)
                },
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
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
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
    translateReasoning: (suspend (String) -> Result<String>)? = null,
    pluginActions: List<com.deepseek.dsh.core.model.PluginSlotEntry> = emptyList(),
    onPluginAction: (com.deepseek.dsh.core.model.PluginSlotEntry) -> Unit = {},
    /** false 时由外层整轮完成头负责折叠，本组件只渲染过程内容。 */
    header: Boolean = true,
) {
    val running = items.firstOrNull { it.role == MessageRole.TOOL && toolStatus(it) == "running" }
    val isRunning = running != null
    val summary = processSummary(items)
    val activeName = running?.content?.split(2.toChar())?.getOrNull(3).orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        if (header) {
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
        }
        AnimatedVisibility(visible = if (!header) true else expanded) {
            Column(modifier = Modifier.padding(start = if (header) 20.dp else 0.dp, top = 2.dp)) {
                items.filterNot { it.role == MessageRole.TOOL && toolStatus(it) == "running" }.forEach { item ->
                    if (item.role == MessageRole.ASSISTANT) {
                        val separator = item.content.indexOf('\u0001')
                        if (separator >= 0) {
                            ReasoningCard(
                                isFinished = true,
                                elapsedSeconds = "",
                                reasoningText = item.content.substring(0, separator),
                                modifier = Modifier.fillMaxWidth(),
                                translateReasoning = translateReasoning,
                            )
                            val answer = item.content.substring(separator + 1)
                            if (answer.isNotBlank()) {
                                Text(
                                    text = answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        } else if (item.content.isNotBlank()) {
                            // 中间回复：属于本轮过程，折叠在完成头内。
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    } else {
                        ToolMessageItem(
                            content = item.content,
                            onOpenDetail = onOpenDetail,
                            pluginActions = pluginActions,
                            onPluginAction = onPluginAction,
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
    /** 插件声明在工具卡片上的动作（落点 tool.card）。 */
    pluginActions: List<com.deepseek.dsh.core.model.PluginSlotEntry> = emptyList(),
    onPluginAction: (com.deepseek.dsh.core.model.PluginSlotEntry) -> Unit = {},
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
        // tool.call.toolview：原生插件接管工具结果渲染的落点。GenUI 插件的
        // render_ui 工具的入参就是一份 dsh-ui spec，经这里交给注册的渲染器画出来。
        com.deepseek.dsh.plugin.nativehost.NativeSlot(
            slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.TOOL_VIEW_SHEET,
            spec = kotlinx.serialization.json.buildJsonObject {
                put("name", kotlinx.serialization.json.JsonPrimitive(name))
                put("status", kotlinx.serialization.json.JsonPrimitive(status))
                put("args", kotlinx.serialization.json.JsonPrimitive(args))
            },
        )
        // 插件声明在工具卡片上的动作：只在展开时出现，避免每条工具行都加噪音。
        if (expanded && pluginActions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pluginActions.forEach { entry ->
                    androidx.compose.material3.Text(
                        text = entry.title ?: entry.id ?: entry.slot,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPluginAction(entry) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
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
                    text = stringResource(if (status == "running") R.string.tool_detail_running else R.string.tool_detail_error),
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
                text = stringResource(R.string.chat_thinking_active),
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

    /**
     * 一个 turn = 一条用户消息 + 其后的 AI/工具消息。
     *
     * 完成头（TurnDone）紧跟在用户消息后、AI 内容前，且每个 turn 只出现一次；
     * 不能在每条中间 AI 消息后插入，否则“已完成 + 分割线”会夹在回复中间。
     */
    fun appendTurn(user: ChatMessage?, turn: List<ChatMessage>, closed: Boolean) {
        if (user != null) rows.add(ChatRow.Single(user))
        if (user != null && closed) {
            val lastCreated = turn.maxOfOrNull { it.createdAt } ?: user.createdAt
            rows.add(
                ChatRow.TurnDone(
                    key = user.messageId,
                    durationMs = (lastCreated - user.createdAt).coerceAtLeast(0L),
                )
            )
        }
        val process = mutableListOf<ChatMessage>()
        var finalAnswer: ChatMessage? = null
        // 官方只把「最后一个有内容的 assistant 消息」当最终回答，其余思考/中间回复
        // 都属于本轮的 process，由完成头统一折叠。
        val finalIndex = turn.indexOfLast { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() }
        turn.forEachIndexed { index, message ->
            val sep = message.content.indexOf('\u0001')
            when {
                message.role == MessageRole.TOOL -> process.add(message)
                message.role == MessageRole.ASSISTANT && index == finalIndex -> {
                    if (sep >= 0) {
                        val reasoning = message.content.substring(0, sep)
                        val answer = message.content.substring(sep + 1)
                        if (reasoning.isNotBlank()) {
                            process.add(message.copy(messageId = message.messageId + "_r", content = reasoning + '\u0001'))
                        }
                        if (answer.isNotBlank()) {
                            finalAnswer = message.copy(messageId = message.messageId + "_a", content = answer)
                        }
                    } else {
                        finalAnswer = message
                    }
                }
                message.role == MessageRole.ASSISTANT -> process.add(message)
                else -> rows.add(ChatRow.Single(message))
            }
        }
        if (process.isNotEmpty()) {
            rows.add(ChatRow.Process(key = user?.messageId ?: process.first().messageId, items = process.toList()))
        }
        finalAnswer?.let { rows.add(ChatRow.Single(it)) }
    }

    var index = 0
    while (index < messages.size) {
        val user = messages[index].takeIf { it.role == MessageRole.USER }
        var end = index + 1
        while (end < messages.size && messages[end].role != MessageRole.USER) end++
        val turn = messages.subList(index + 1, end)
        val running = turn.any { it.role == MessageRole.TOOL && toolStatus(it) == "running" }
        // 已有最终回答的轮次即使 running 状态短暂滞后，也要显示完成头；否则用户会
        // 看到回复却永远等不到“已完成/用时”这一行。
        val answered = turn.any { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() }
        val closed = user != null && (end < messages.size || answered || (!isThinking && !running))
        appendTurn(user, turn, closed)
        index = end
    }
    return rows
}
private fun toolStatus(message: ChatMessage): String =
    message.content.split(2.toChar()).getOrNull(4).orEmpty()

@Composable
private fun processVerb(name: String, done: Boolean): String = when {
    name.contains("bash") || name.contains("shell") ->
        stringResource(if (done) R.string.tool_cmd_done else R.string.tool_cmd)
    name.contains("image") ->
        stringResource(if (done) R.string.tool_image_done else R.string.tool_image)
    name.contains("read") ->
        stringResource(if (done) R.string.tool_read_done else R.string.tool_read)
    name.contains("write") || name.contains("edit") ->
        stringResource(if (done) R.string.tool_edit_done else R.string.tool_edit)
    name.contains("grep") || name.contains("glob") ->
        stringResource(if (done) R.string.tool_search_done else R.string.tool_search)
    name.contains("ask_user_question") ->
        stringResource(if (done) R.string.tool_ask_done else R.string.tool_ask)
    name.contains("web") ->
        stringResource(if (done) R.string.tool_web_done else R.string.tool_web)
    else -> stringResource(if (done) R.string.tool_generic_done else R.string.tool_generic)
}

@Composable
private fun processSummary(tools: List<ChatMessage>): String {
    val verbs = tools.filter { it.role == MessageRole.TOOL }.mapNotNull { message ->
        val name = message.content.split(2.toChar()).getOrNull(3) ?: return@mapNotNull null
        processVerb(name, done = true)
    }.distinct()
    return verbs.joinToString(stringResource(R.string.tool_join))
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
                        "running" -> stringResource(R.string.tool_detail_running)
                        "error" -> stringResource(R.string.tool_detail_error)
                        else -> stringResource(R.string.tool_detail_done)
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
                Text(stringResource(R.string.tool_detail_args), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
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
                    text = stringResource(
                        if (detail.status == "error") R.string.tool_detail_error_output
                        else R.string.tool_detail_output
                    ),
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
@Composable
private fun toolTitle(name: String): String = when {
    name.contains("bash") || name.contains("shell") -> stringResource(R.string.tool_title_cmd)
    name.contains("read") -> stringResource(R.string.tool_title_read)
    name.contains("write") -> stringResource(R.string.tool_title_write)
    name.contains("edit") -> stringResource(R.string.tool_title_edit)
    name.contains("grep") -> stringResource(R.string.tool_title_grep)
    name.contains("glob") -> stringResource(R.string.tool_title_glob)
    name.contains("ask_user_question") -> stringResource(R.string.tool_title_ask)
    name.contains("create_goal") || name.contains("update_goal") ->
        stringResource(R.string.tool_title_goal)
    name.contains("subagent") -> stringResource(R.string.tool_title_subagent)
    name.contains("web_search") || name.contains("web_fetch") ->
        stringResource(R.string.tool_title_web)
    else -> stringResource(R.string.tool_title_generic)
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
    translateReasoning: (suspend (String) -> Result<String>)? = null,
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
                        translateReasoning = translateReasoning,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val answerText = contentParts.last()
                if (answerText.trimStart().startsWith("⚠️")) {
                    // 运行失败：错误原文（含 code）完整显示在错误气泡里，不截断。
                    ErrorMessageBubble(text = answerText.trimStart().removePrefix("⚠️").trim())
                } else {
                    // 正文里的 ```dsh-ui 围栏由 GenUI 插件产生；原生兜底已移除，
                    // 这类界面内容请在插件的完整 Web 界面查看。
                    MarkdownMessageText(
                        text = answerText,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // conversation.chat.assistant-actions：原生插件在助手消息下的动作落点。
                com.deepseek.dsh.plugin.nativehost.NativeSlot(
                    slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.MESSAGE_ACTION,
                    spec = kotlinx.serialization.json.buildJsonObject {
                        put("role", kotlinx.serialization.json.JsonPrimitive("assistant"))
                        put("content", kotlinx.serialization.json.JsonPrimitive(contentParts.last()))
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

            }
        }
    }
}

/** 运行时错误气泡：显示完整错误文本（含错误码），不折叠、不省略。 */
@Composable
private fun ErrorMessageBubble(text: String) {
    val colors = com.deepseek.dsh.core.designsystem.dshPanelColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = if (colors.isDark) 0.35f else 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "运行失败",
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

/**
 * 计划模式横幅：提示当前只生成计划，等用户确认后再执行。
 * 与官方 plan mode 一致：进入后 placeholder 与发送语义切换，可随时退出。
 */
@Composable
private fun PlanModeBanner(onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.plan_banner_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.plan_banner_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onExit, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.plan_banner_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 输入框里的斜杠命令 chip：显示本地化名称（/目标），发送时还原成 /goal。 */
@Composable
private fun ActiveCommandChip(label: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "/$label",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.cmd_chip_remove),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(12.dp)
                .clickable(onClick = onClear),
        )
    }
}

/** 输入卡片上方的已选附件行：名称 + 大小，右侧移除。 */
@Composable
private fun AttachmentRail(
    attachments: List<ChatAttachment>,
    onRemove: (ChatAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_cmd_file),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val sizeLabel = attachment.formattedSize()
                if (sizeLabel.isNotEmpty()) {
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.attachment_remove),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(13.dp)
                        .clickable { onRemove(attachment) },
                )
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
    activeCommandLabel: String? = null,
    activeCommandHint: String? = null,
    onClearCommand: () -> Unit = {},
    onSend: () -> Unit,
    onPlusClick: () -> Unit,
    selectedModel: String,
    selectedModelId: String = "",
    availableModels: List<String> = emptyList(),
    modelGroups: List<com.deepseek.dsh.core.model.ModelProviderGroup> = emptyList(),
    effortLabel: String,
    reasoningOptions: List<ReasoningOption> = emptyList(),
    selectedReasoningId: String = "",
    onOpenModelMenu: () -> Unit,
    modelMenuOpen: Boolean,
    onModelMenuDismiss: () -> Unit,
    onModelSelected: (provider: String, modelId: String, name: String) -> Unit,
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
    attachments: List<ChatAttachment> = emptyList(),
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
    dockPluginSurfaces: List<SurfaceItem> = emptyList(),
    onDockPluginClick: (SurfaceItem) -> Unit = {},
    stats: ComposerStats = ComposerStats(),
    showDockStats: Boolean = true,
    // Claude Code 模组横幅（官方 conversation.input.dock）。
    bandSnapshot: com.deepseek.dsh.core.data.ClaudeCodeBandSnapshot? = null,
    bandPressing: Boolean = false,
    onBandPress: (Long, String) -> Unit = { _, _ -> },
    voiceEnabled: Boolean = false,
    voiceRecording: Boolean = false,
    voiceSeconds: Int = 0,
    voiceLevel: Float = 0f,
    voiceBusy: Boolean = false,
    voiceError: String? = null,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
) {
    val panelColors = dshPanelColors()
    val plusInteraction = remember { MutableInteractionSource() }
    val tightComposer = selectedModel.length > 16
    val cardShape = RoundedCornerShape(28.dp)
    var permissionAnchor by remember { mutableStateOf<Rect?>(null) }
    var modelAnchor by remember { mutableStateOf<Rect?>(null) }
    var composerOverlayAnchor by remember { mutableStateOf<Rect?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // conversation.composer.dock 的官方位置是输入框底部状态行（tok/s、缓存命中、
        // 上下文旁），由 ComposerDockBar 以行内方式承载，不在输入框上方。
        // conversation.input.dock：Claude Code 模组横幅（Host 下发的动态树）。
        ClaudeCodeModsPluginUi.BandView(
            snapshot = bandSnapshot,
            pressing = bandPressing,
            onPress = onBandPress,
        )
        // 录音条：官方展开后的 composer 工具条（波形 + 计时 + 取消 / 停止并识别）。
        if (voiceRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(panelColors.bgModulePlatform)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 电平条：8 段，按当前 RMS 亮起。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    repeat(8) { index ->
                        val threshold = (index + 1) / 8f
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(if (voiceLevel >= threshold) 16.dp else 8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (voiceLevel >= threshold) MaterialTheme.colorScheme.primary
                                    else panelColors.labelCaption,
                                ),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_recording, voiceSeconds),
                        color = panelColors.labelSecondary,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = panelColors.labelSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onVoiceCancel)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !voiceBusy, onClick = onVoiceStop)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (voiceBusy) "识别中…" else "停止并识别",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (voiceEnabled && voiceError != null) {
            Text(
                text = voiceError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        com.deepseek.dsh.plugin.nativehost.OfficialSlot(
            slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.COMPOSER_BAR,
            arrangement = com.deepseek.dsh.plugin.nativehost.SlotArrangement.BLOCK,
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
                .onGloballyPositioned { composerOverlayAnchor = it.boundsInWindow() }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (activeCommandLabel != null) {
                                ActiveCommandChip(
                                    label = activeCommandLabel,
                                    onClear = onClearCommand,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = activeCommandHint
                                            ?: if (isPlanMode) stringResource(R.string.chat_input_placeholder_plan)
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
                    }
                )

                // 已选附件：发送时以 @<工作区路径> 附在消息开头，agent 直接读真实文件。
                if (attachments.isNotEmpty()) {
                    AttachmentRail(
                        attachments = attachments,
                        onRemove = onRemoveAttachment,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 5.dp, end = 5.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (tightComposer) 6.dp else 10.dp)
                ) {
                    // 控件区可横向滑动：插件注入多个按钮后，plus/附件/Workspace/模型
                    // 不会被挤出屏幕；发送按钮固定在右侧。
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (tightComposer) 6.dp else 10.dp),
                    ) {
                    // 加号：28dp 圆形 selector 按钮（无 Material 涟漪）
                    com.deepseek.dsh.plugin.nativehost.OfficialSlot(
                        slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.COMPOSER_LEFT,
                    ) {
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
                    }
                    com.deepseek.dsh.plugin.nativehost.OfficialSlot(
                        slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.COMPOSER_ATTACHMENTS,
                    ) {}

                    // 权限预设：trigger 28dp 高 / R8 / 13-20 二级色 / 14dp 图标与箭头
                    com.deepseek.dsh.plugin.nativehost.OfficialSlot(
                        slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.COMPOSER_PERMISSION,
                    ) {
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
                    }

                    // conversation.input.right：模型选择器左侧的插件控件
                    // （订阅插件的 fast-mode 按钮）。当前模型/推理档位随 spec 下发。
                    com.deepseek.dsh.plugin.nativehost.NativeSlotInline(
                        slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.COMPOSER_RIGHT,
                        spec = kotlinx.serialization.json.buildJsonObject {
                            put("modelId", selectedModelId)
                            put("reasoningEffort", selectedReasoningId)
                        },
                    )

                    // 模型与推理等级：贴近右侧发送按钮，菜单锚定在此。
                    com.deepseek.dsh.plugin.nativehost.OfficialSlot(
                        slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.COMPOSER_MODEL,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .onGloballyPositioned { modelAnchor = it.boundsInWindow() },
                        ) {
                            ComposerTrigger(
                                label = selectedModel.ifBlank { stringResource(R.string.chat_select_model) },
                                // 官方：未选模型时只显示「请选择模型」，不附带推理等级。
                                trailingValue = if (availableModels.isEmpty()) null else effortLabel,
                                onClick = onOpenModelMenu,
                                compact = tightComposer,
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
                    }
                    com.deepseek.dsh.plugin.nativehost.OfficialSlot(
                        slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.COMPOSER_PLAN,
                    ) {}

                    } // 控件区横向滚动结束；麦克风/发送固定在右侧

                    // 录音输入：麦克风动作（官方 conversation.input.activity）。
                    // 录音中由上方录音条承担停止/取消，这里只保留状态指示。
                    if (voiceEnabled && !voiceRecording) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(panelColors.bgModulePlatform)
                                .clickable(enabled = !voiceBusy, onClick = onVoiceStart),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.chat_mic_start),
                                tint = panelColors.labelPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    // 生成中：停止与发送并存（官方行为）——输入框里的内容要能继续
                    // 排到下一轮（inbox next-turn），所以发送按钮不能消失。
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
                    }
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
            // conversation.input.overlay：锚定 Popup，不占输入卡布局。
        }

        Spacer(modifier = Modifier.height(6.dp))

        ComposerDockBar(
            stats = stats,
            showStats = showDockStats,
            selectedModelId = selectedModelId,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        com.deepseek.dsh.plugin.nativehost.AnchoredSlotPopup(
            slot = com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot.COMPOSER_OVERLAY,
            spec = kotlinx.serialization.json.JsonObject(emptyMap()),
            anchorBounds = composerOverlayAnchor,
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
    hostCommands: List<com.deepseek.dsh.core.model.HostCommandInfo> = emptyList(),
    skills: List<com.deepseek.dsh.core.model.SkillInfo> = emptyList(),
    onSkill: (String) -> Unit = {},
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
            // Host 注册的斜杠命令：动态目录，点选后走 commands/execute。
            if (skills.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                DshMenuSectionLabel(stringResource(R.string.cmd_section_skills))
                skills.forEach { skill ->
                    DshMenuRow(onClick = { onSkill(skill.name) }) {
                        DshMenuLabel("/${skill.name}")
                        Spacer(Modifier.weight(1f))
                        if (skill.description.isNotBlank()) {
                            DshMenuCaption(skill.description)
                        }
                    }
                }
            }
            if (hostCommands.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                DshMenuSectionLabel(stringResource(R.string.cmd_section_host))
                hostCommands.forEach { command ->
                    DshMenuRow(onClick = { onCommand("/${command.name}") }) {
                        DshMenuLabel("/${command.name}")
                        Spacer(Modifier.weight(1f))
                        if (command.description.isNotBlank()) {
                            DshMenuCaption(command.description)
                        }
                    }
                }
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
/**
 * 目标 / 待办 dock（官方 `goal` + `todos` 投影）：输入框上方的紧凑卡片。
 */
@Composable
private fun SessionPlanDock(
    goal: com.deepseek.dsh.core.model.GoalInfo?,
    todos: List<com.deepseek.dsh.core.model.TodoEntryInfo>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        goal?.let { current ->
            Text(
                text = current.objective,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val meta = buildString {
                append(current.phase)
                if (current.roundsStarted > 0) {
                    append(" \u00b7 round ")
                    append(current.roundsStarted)
                    if (current.maxGoalRounds > 0) {
                        append("/")
                        append(current.maxGoalRounds)
                    }
                }
                current.blockedReason?.takeIf { it.isNotBlank() }?.let {
                    append(" \u00b7 ")
                    append(it)
                }
            }
            Text(
                text = meta,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (todos.isNotEmpty()) {
            val done = todos.count { it.status == "completed" }
            Text(
                text = "TODO " + done + "/" + todos.size,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
            todos.take(6).forEach { todo ->
                Text(
                    text = when (todo.status) {
                        "completed" -> "\u2713 " + todo.content
                        "in_progress" -> "\u25B6 " + todo.content
                        else -> "\u25CB " + todo.content
                    },
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    color = if (todo.status == "completed") {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}


/**
 * 助手消息下方的反馈按钮（官方 message-feedback）。
 *
 * 点击赞/踩即写入；再次点击同一档位删除；「备注」打开可选说明输入框。
 */
@Composable
private fun MessageFeedbackActions(
    current: com.deepseek.dsh.core.model.MessageFeedbackInfo?,
    onRating: (String) -> Unit,
    onNote: (String) -> Unit,
) {
    var noteDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedbackIcon(
            selected = current?.rating == "positive",
            icon = androidx.compose.material.icons.Icons.Outlined.ThumbUp,
            description = stringResource(R.string.feedback_helpful),
            onClick = { onRating("positive") },
        )
        FeedbackIcon(
            selected = current?.rating == "negative",
            icon = androidx.compose.material.icons.Icons.Outlined.ThumbDown,
            description = stringResource(R.string.feedback_not_helpful),
            onClick = { onRating("negative") },
        )
        if (current != null) {
            Text(
                text = if (current.note.isNullOrBlank()) {
                    stringResource(R.string.feedback_add_note)
                } else {
                    stringResource(R.string.feedback_edit_note)
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .clickable { noteDialog = true }
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
    if (noteDialog) {
        var draft by remember(current?.note) { mutableStateOf(current?.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text(stringResource(R.string.feedback_note_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.feedback_note_hint)) },
                    minLines = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    noteDialog = false
                    onNote(draft.trim())
                }) { Text(stringResource(R.string.queue_save)) }
            },
            dismissButton = {
                TextButton(onClick = { noteDialog = false }) {
                    Text(stringResource(R.string.queue_cancel))
                }
            },
        )
    }
}

@Composable
private fun FeedbackIcon(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .size(16.dp),
    )
}
