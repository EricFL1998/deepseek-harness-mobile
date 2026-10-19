package com.deepseek.dsh.plugin.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deepseek.dsh.core.designsystem.DshPanelDefaults
import com.deepseek.dsh.core.designsystem.DshSheetPanel
import com.deepseek.dsh.core.designsystem.dshPanelColors
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.plugin.runtimeweb.WebIslandContainer
import kotlin.math.roundToInt

/**
 * v4.0 设计文档 §7.5: 插件内容容器 (Surface Sheet)
 *
 * 外壳使用 bottom sheet 材质（[DshSheetPanel] 自带 4dp 横向内缩与拖拽手柄），
 * 内部再补 12dp 横向 / 16dp 纵向留白，使正文距屏幕边缘共 16dp。
 */
@Composable
fun SurfaceSheet(
    item: SurfaceItem,
    hostPort: Int,
    token: String? = null,
    onDismissRequest: () -> Unit
) {
    val colors = dshPanelColors()
    DshSheetPanel(onDismiss = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title ?: item.pluginId,
                    color = colors.labelPrimary,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                PanelCloseButton(onClick = onDismissRequest, label = "Close")
            }

            Spacer(modifier = Modifier.height(12.dp))

            AndroidView(
                factory = { ctx ->
                    WebIslandContainer(
                        context = ctx,
                        allowedPort = hostPort
                    ).apply {
                        loadSurface(surfaceSlot = item.slot, pluginId = item.pluginId, isolated = true, token = token)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

/**
 * v4.0 设计文档 §7.7:
 * 插件浮层（可悬浮、拖动、收起为气泡；内存单例保护）
 *
 * 浮层外壳与下拉菜单同一材质：16dp 圆角、menu 半透明底、0.5dp 描边与柔和阴影；
 * 标题 13/20 主色，关闭位为 28dp 点击区 + 14dp 图标。拖动与收起状态逻辑保持不变。
 */
@Composable
fun PluginOverlayContainer(
    item: SurfaceItem,
    hostPort: Int,
    token: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(100f) }
    var offsetY by remember { mutableFloatStateOf(300f) }
    var isMinimized by remember { mutableStateOf(false) }

    val colors = dshPanelColors()
    val popupShape = RoundedCornerShape(16.dp)
    // 与设计系统菜单一致：半透明材质合成到基础层，避免背后内容透出。
    val menuFill = colors.menuSurfaceFill.compositeOver(colors.bgBase)

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        if (isMinimized) {
            // 收起状态：悬浮圆形小气泡（同样使用菜单材质）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation = 5.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.05f),
                    )
                    .clip(CircleShape)
                    .background(menuFill)
                    .border(0.5.dp, if (colors.isDark) colors.borderL3 else colors.borderL1, CircleShape)
                    .clickable(onClick = { isMinimized = false }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = "Expand Overlay",
                    tint = colors.labelPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            // 展开状态：微型悬浮窗
            Column(
                modifier = Modifier
                    .size(width = 280.dp, height = 360.dp)
                    .shadow(
                        elevation = 5.dp,
                        shape = popupShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.05f),
                    )
                    .clip(popupShape)
                    .background(menuFill)
                    .border(0.5.dp, if (colors.isDark) colors.borderL3 else colors.borderL1, popupShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title ?: item.pluginId,
                        color = colors.labelPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    PanelIconSeat(onClick = { isMinimized = true }) {
                        Icon(
                            imageVector = Icons.Default.Minimize,
                            contentDescription = "Minimize",
                            tint = colors.labelSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    PanelCloseButton(onClick = onClose, label = "Close")
                }

                AndroidView(
                    factory = { ctx ->
                        WebIslandContainer(
                            context = ctx,
                            allowedPort = hostPort
                        ).apply {
                            loadSurface(surfaceSlot = item.slot, pluginId = item.pluginId, isolated = true, token = token)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Modal 关闭位：28dp 方形点击区 + 14dp ic_close 图标，labelSecondary 着色。 */
@Composable
private fun PanelCloseButton(onClick: () -> Unit, label: String) {
    val colors = dshPanelColors()
    PanelIconSeat(onClick = onClick) {
        Icon(
            painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_close),
            contentDescription = label,
            tint = colors.labelSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * 28dp 图标点击区（close 位）：8dp 圆角，按下时用 hover 填充而非 Material 涟漪。
 */
@Composable
private fun PanelIconSeat(onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = dshPanelColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(DshPanelDefaults.DialogCloseSize)
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) colors.interactiveHover else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}
