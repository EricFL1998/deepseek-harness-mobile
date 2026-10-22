package com.deepseek.dsh

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deepseek.dsh.core.designsystem.dshPanelColors

/**
 * 聊天页的右侧栏：官方窄屏（<768px）行为是整屏覆盖。
 *
 * 用独立 Dialog 窗口承载，而不是内容树里的覆盖层：聊天页的浮动输入工具是
 * Popup（独立窗口），普通覆盖层会被它压在下面；Dialog 能保证右侧栏真正在最上层，
 * 同时系统返回键走 onDismiss。
 */
@Composable
fun RightSideDrawer(
    open: Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!open) return
    val palette = dshPanelColors()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(initialOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = palette.bgBase,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }
}
