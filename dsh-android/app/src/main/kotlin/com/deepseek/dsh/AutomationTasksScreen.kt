package com.deepseek.dsh

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshTextInput
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.designsystem.dshPanelColors

import com.deepseek.dsh.core.model.AutomationTaskItem

/**
 * 「自动化任务」页面使用的单条任务数据。
 */
typealias AutomationTaskUi = AutomationTaskItem

/** 已启用 / 已结束两个筛选页签。 */
private const val TAB_ACTIVE = 0
private const val TAB_INACTIVE = 1

private val AutomationFilterLabels = listOf("已启用", "已结束")

/**
 * 「自动化任务」页面（Automation tasks）。
 *
 * 对照 dsh-upstream `packages/client/ui-schedule/src/client/TaskManagerPage.tsx`
 * 与 `TaskManagerPage.module.css`：标题 20/28/500，引导语 13/20 二级色，分段页签使用
 * ui-primitives `SegmentedTabs` 的 4dp 容器 / 16dp 圆角 / 34dp 页签，空态使用
 * task-manager-locales 的中文 copy 与 `empty.action` 主按钮。
 *
 * 页面只提供内容区，返回与菜单由 MainActivity 的 PageBackBar 提供。
 * 数据源接入前固定传入空列表，因此始终呈现空态。
 *
 * @param onCreateTask 空态主按钮「新建自动化任务」的回调。
 * @param modifier 外部修饰符。
 */
@Composable
fun AutomationTasksScreen(
    tasks: List<AutomationTaskItem> = emptyList(),
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AutomationTasksPage(
        tasks = tasks,
        onCreateTask = onCreateTask,
        modifier = modifier,
    )
}

/**
 * 自动化任务页面的数据化实现：真实任务列表、页签与搜索状态都在这里，
 * 公共入口只决定当前没有数据。
 *
 * @param tasks 任务列表（空列表时展示空态）。
 * @param onCreateTask 新建任务回调。
 * @param modifier 外部修饰符。
 */
@Composable
internal fun AutomationTasksPage(
    tasks: List<AutomationTaskUi>,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = dshPanelColors()
    var selectedTab by rememberSaveable { mutableStateOf(TAB_ACTIVE) }
    var query by rememberSaveable { mutableStateOf("") }
    CompositionLocalProvider(LocalDshPanelColors provides colors) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgBase)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = "自动化任务",
                color = colors.labelPrimary,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "在会话中创建的自动化任务会显示在这里",
                color = colors.labelSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(24.dp))
            AutomationSegmentedTabs(
                labels = AutomationFilterLabels,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
            Spacer(Modifier.height(16.dp))
            DshTextInput(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索自动化任务",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            val normalizedQuery = query.trim()
            val visibleTasks = tasks
                .filter { task ->
                    if (selectedTab == TAB_INACTIVE) task.inactive else !task.inactive
                }
                .filter { task ->
                    normalizedQuery.isEmpty() ||
                        task.title.contains(normalizedQuery, ignoreCase = true) ||
                        task.summary.contains(normalizedQuery, ignoreCase = true)
                }
            if (visibleTasks.isEmpty()) {
                AutomationEmptyState(
                    // 与 TaskManagerPage 的 emptyTitle 判定一致：已结束页签的
                    // 无筛选空态是「没有已结束的自动化任务」，目录为空时是首次空态。
                    text = when {
                        selectedTab == TAB_INACTIVE && normalizedQuery.isEmpty() ->
                            "没有已结束的自动化任务"
                        tasks.isEmpty() -> "还没有自动化任务，在会话中创建的任务会显示在这里"
                        else -> "没有匹配的自动化任务"
                    },
                    onCreateTask = onCreateTask,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    visibleTasks.forEach { task -> AutomationTaskRow(task) }
                }
            }
        }
    }
}

/**
 * 分段页签（ui-primitives `SegmentedTabs`）：4dp 内边距、16dp 容器圆角、
 * module-platform 底、4dp 内缩的 12dp 指示块与 34dp 页签高度。
 *
 * @param labels 页签文案。
 * @param selectedIndex 选中的页签下标。
 * @param onSelect 页签切换回调。
 * @param modifier 外部修饰符。
 */
@Composable
private fun AutomationSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val colors = LocalDshPanelColors.current
    val containerShape = RoundedCornerShape(16.dp)
    val indicatorShape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(colors.bgModulePlatform)
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / labels.size.toFloat()
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex.toFloat(),
            label = "automation-tab-indicator",
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .height(34.dp)
                .clip(indicatorShape)
                .background(colors.bgLayer3)
                .border(0.5.dp, colors.borderL3, indicatorShape),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .selectableGroup(),
        ) {
            labels.forEachIndexed { index, label ->
                AutomationSegmentedTab(
                    label = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/** 单个页签：选中为 600 字重的主色，未选中为二级色。 */
@Composable
private fun RowScope.AutomationSegmentedTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.labelPrimary else colors.labelSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 空态：居中三级色说明 + 16dp 后的主按钮。 */
@Composable
private fun AutomationEmptyState(
    text: String,
    onCreateTask: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = LocalDshPanelColors.current.labelTertiary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        AutomationPrimaryAction(
            text = "新建自动化任务",
            onClick = onCreateTask,
        )
    }
}

/** 主按钮：32dp 高、12dp 圆角、0/12 内边距、13/20 标签。 */
@Composable
private fun AutomationPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) colors.buttonPrimaryHover else colors.buttonPrimaryFill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.labelPrimaryForeground,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            maxLines = 1,
        )
    }
}

/**
 * 任务行：`.row` 的 8dp 内缩排版，标题 500 主色、摘要 13/20 三级色；
 * 已结束任务整体降一级色阶（`.endedRow`）。
 */
@Composable
private fun AutomationTaskRow(task: AutomationTaskUi) {
    val colors = LocalDshPanelColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            text = task.title,
            color = if (task.inactive) colors.labelTertiary else colors.labelPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = task.summary,
            color = if (task.inactive) colors.labelCaption else colors.labelTertiary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
