package com.deepseek.dsh

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import com.deepseek.dsh.core.designsystem.dshPanelColors

/**
 * 聊天页的右侧栏：官方窄屏（<768px）行为是整屏覆盖，因此这里铺满窗口。
 *
 * 没有独立标题栏：标签条就是顶部边缘，关闭控件挂在它末尾。
 */
@Composable
fun RightSideDrawer(
    open: Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = dshPanelColors()
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            // 侧滑返回：向右拖动面板，超过阈值（或快速滑动）即关闭。
            val density = LocalDensity.current
            val closeThresholdPx = with(density) { 72.dp.toPx() }
            var dragOffset by remember(open) { mutableFloatStateOf(0f) }
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .offset { IntOffset(dragOffset.coerceAtLeast(0f).roundToInt(), 0) }
                    .pointerInput(open) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount > 0f) {
                                    dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (dragOffset > closeThresholdPx) onClose()
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                        )
                    },
                color = palette.bgBase,
            ) {
                Box(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
