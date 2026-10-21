package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.data.BandNode
import com.deepseek.dsh.core.data.ClaudeCodeBandSnapshot
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors

/**
 * 「Claude Code 子智能体」插件的原生界面：输入框上方的模组横幅。
 *
 * 官方 `client-ui-claude-code-mods` 在 `conversation.input.dock` 注册一条
 * 动态横幅，内容由 Host 序列化成 `Text`/`Box`/`Button` 节点树；按钮按下走
 * `claudeCodeMods.pressBand`。这里按同样的树与配色画原生版。
 */
object ClaudeCodeModsPluginUi {
    /** 启用后才会出现横幅的 bundle（Claude Code 子智能体）。 */
    const val BUNDLE = "@deepseek-ai/dsh-subagent-claude-code"

    /** 官方 `data-color` 的取值 → 主题色。 */
    private fun colorOf(name: String?): Color? = when (name) {
        "yellow" -> Color(0xFFB58900)
        "red" -> Color(0xFFDC322F)
        "green" -> Color(0xFF859900)
        "cyan" -> Color(0xFF2AA198)
        "blue" -> Color(0xFF268BD2)
        "magenta" -> Color(0xFFD33682)
        "gray", "grey" -> Color(0xFF93A1A1)
        "white" -> Color(0xFFFDF6E3)
        "black" -> Color(0xFF002B36)
        else -> null
    }

    /**
     * 画一条横幅。
     *
     * @param snapshot 当前快照；`root == null` 时什么都不画（官方同样）。
     * @param pressing 有按钮在处理中（禁用其余按钮并显示处理中）。
     * @param onPress 点按钮：传入 generation 与 actionId。
     */
    @Composable
    fun BandView(
        snapshot: ClaudeCodeBandSnapshot?,
        pressing: Boolean,
        onPress: (generation: Long, actionId: String) -> Unit,
    ) {
        val root = snapshot?.root ?: return
        val colors = LocalDshPanelColors.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.bgModulePlatform)
                .border(0.5.dp, colors.borderL2, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            NodeView(root, snapshot.generation, pressing, onPress)
        }
    }
}

/** 递归画一个节点。 */
@Composable
private fun NodeView(
    node: BandNode,
    generation: Long,
    pressing: Boolean,
    onPress: (Long, String) -> Unit,
) {
    val colors = LocalDshPanelColors.current
    when (node) {
        is BandNode.Text -> Text(
            text = node.text,
            color = colorOrLabel(node.color),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = if (node.bold) FontWeight.SemiBold else FontWeight.Normal,
            fontStyle = if (node.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (node.underline) TextDecoration.Underline else TextDecoration.None,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )

        is BandNode.Box -> {
            val gap = if (node.gap > 0) node.gap.dp else 6.dp
            if (node.row) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    node.children.forEach { child ->
                        NodeView(child, generation, pressing, onPress)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    node.children.forEach { child ->
                        NodeView(child, generation, pressing, onPress)
                    }
                }
            }
        }

        is BandNode.Button -> {
            val actionId = node.actionId
            val enabled = !node.disabled && !pressing && !actionId.isNullOrBlank()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (enabled) colors.buttonPrimaryFill else colors.bgModulePlatform,
                    )
                    .border(0.5.dp, colors.borderL2, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) {
                        if (actionId != null) onPress(generation, actionId)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = node.label,
                        color = if (enabled) colors.labelPrimaryForeground else colors.labelTertiary,
                        fontSize = 11.5.sp,
                    )
                    node.hotkey?.let { hotkey ->
                        Text(
                            text = "[$hotkey]",
                            color = if (enabled) colors.labelPrimaryForeground.copy(alpha = 0.8f) else colors.labelTertiary,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
        }
    }
}

/** 官方 `data-color` → 颜色；没有时用主文本色。 */
@Composable
private fun colorOrLabel(name: String?): Color =
    name?.let { candidate ->
        when (candidate) {
            "yellow" -> Color(0xFFB58900)
            "red" -> Color(0xFFDC322F)
            "green" -> Color(0xFF859900)
            "cyan" -> Color(0xFF2AA198)
            "blue" -> Color(0xFF268BD2)
            "magenta" -> Color(0xFFD33682)
            "gray", "grey" -> Color(0xFF93A1A1)
            "white" -> Color(0xFFFDF6E3)
            "black" -> Color(0xFF002B36)
            else -> null
        }
    } ?: LocalDshPanelColors.current.labelPrimary
