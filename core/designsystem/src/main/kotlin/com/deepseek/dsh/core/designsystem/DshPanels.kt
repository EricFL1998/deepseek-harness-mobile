package com.deepseek.dsh.core.designsystem

import android.view.Gravity
import android.view.View
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Shared panel system that mirrors the DeepSeek Harness web client.
 *
 * Every dropdown menu, context menu, bottom sheet and dialog surface in the app
 * uses these primitives so popups share one material (translucent menu fill,
 * hairline elevation stroke, soft shadow), one radius scale and one row rhythm
 * (menu rows 34dp / 13sp, section labels 11sp, dialogs from the Modal).
 *
 * The menu card is a translucent material with a backdrop blur; Android
 * cannot blur content behind an anchored popup window, so the exact fill,
 * stroke, radius, shadow, row and typography metrics are reproduced without the
 * blur layer.
 */
object DshPanelDefaults {
    /** Menu card radius (`--dsw-radius-lg`). */
    val Shape: Shape = RoundedCornerShape(16.dp)

    /** Compact menu card radius (`--dsw-radius-md`). */
    val CompactShape: Shape = RoundedCornerShape(12.dp)

    /** Gap between the anchor and the menu card (`.list` top offset). */
    val AnchorGap = 4.dp

    /** Menu card padding (`.list` 4px). */
    val ContentPadding = 4.dp

    /** Menu row min height (`.item` 34px). */
    val RowMinHeight = 34.dp

    /** Menu row horizontal padding (`.item` 8px). */
    val RowHorizontalPadding = 8.dp

    /** Menu row vertical padding (`.item` 6px). */
    val RowVerticalPadding = 6.dp

    /** Menu row corner radius (`--dsw-radius-md`). */
    val RowShape: Shape = RoundedCornerShape(12.dp)

    /** Gap between a row icon and its label (6px). */
    val RowGap = 6.dp

    /** Menu list minimum width (`.list` 144px). */
    val MenuMinWidth = 144.dp

    /** Menu list maximum width (`.list` 360px). */
    val MenuMaxWidth = 360.dp

    /** Menu list default maximum height used on a portrait phone. */
    val MenuMaxHeight = 440.dp

    /** Menu popup keeps this air around the card for its soft shadow. */
    val PopupShadowPadding = 12.dp

    /** Dialog card radius (`--dsw-radius-panel`). */
    val DialogShape: Shape = RoundedCornerShape(28.dp)

    /** Dialog card width (`min(380px, 100%)`). */
    val DialogWidth = 380.dp

    /** Wide dialog width used by the risk confirmation (`min(440px, 100%)`). */
    val WideDialogWidth = 440.dp

    /** Dialog air around the card (root 24px). */
    val DialogInset = 24.dp

    /** Dialog close control size (28x28). */
    val DialogCloseSize = 28.dp

    /** Dialog action height (Button md 36px). */
    val DialogButtonHeight = 36.dp
}

/** Horizontal alignment of an anchored menu relative to its anchor. */
enum class DshMenuAlign { Start, End }

/**
 * Menu surface card: 16dp radius, 4dp padding, translucent menu fill,
 * hairline elevation stroke, soft prominent shadow.
 *
 * @param modifier external modifier (for example an exact menu width).
 * @param scrollable whether overflowing rows scroll inside the card.
 * @param compact smaller 12dp radius card (compact menus).
 * @param minWidth menu minimum width (`.list` 144px; model menu 240px).
 * @param maxWidth menu maximum width (`.list` 360px; model menu 420px).
 * @param maxHeight card maximum height when scrollable.
 * @param contentWidth size the card to its widest row, so full-width rows/press fills work.
 * @param content menu rows, labels and separators.
 */
@Composable
fun DshMenuCard(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    compact: Boolean = false,
    minWidth: Dp = DshPanelDefaults.MenuMinWidth,
    maxWidth: Dp = DshPanelDefaults.MenuMaxWidth,
    maxHeight: Dp = DshPanelDefaults.MenuMaxHeight,
    contentWidth: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = dshPanelColors()
    val shape = if (compact) DshPanelDefaults.CompactShape else DshPanelDefaults.Shape
    Column(
        modifier = modifier
            .widthIn(min = minWidth, max = maxWidth)
            .then(if (contentWidth) Modifier.width(IntrinsicSize.Max) else Modifier)
            .shadow(
                elevation = 5.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.05f),
            )
            .clip(shape)
            // Android cannot blur content behind an anchored popup window; compositing the
            // translucent material over the base layer keeps the menu legible
            // while matching the menu's flattened fill.
            .background(colors.menuSurfaceFill.compositeOver(colors.bgBase))
            .border(0.5.dp, if (colors.isDark) colors.borderL3 else colors.borderL1, shape)
            .padding(DshPanelDefaults.ContentPadding),
    ) {
        CompositionLocalProvider(
            LocalDshPanelColors provides colors,
            LocalContentColor provides colors.labelPrimary,
            LocalTextStyle provides LocalTextStyle.current.copy(
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        ) {
            if (scrollable) {
                Column(
                    // 顺序必须是「先限高、再滚动」：heightIn 在外层把视口约束到
                    // maxHeight，verticalScroll 在内层才会得到 Infinity 的测量约束，
                    // 从而真正产生滚动范围。反过来写会把内容直接压到 maxHeight，
                    // 溢出部分被裁掉、列表滚不动（模型列表底部被裁的根因）。
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}

/**
 * One menu row: 34dp minimum height, 8dp/6dp padding, 12dp radius,
 * 6dp gap, 13sp/20 label rhythm and pressed fill instead of a ripple.
 *
 * @param onClick row activation.
 * @param modifier external modifier.
 * @param fillWidth whether the row fills the card width (false for content-width cards).
 * @param enabled whether the row can be activated.
 * @param danger destructive styling: error ink and danger pressed fill.
 * @param selectedFill fill-mode selection (used where the row keeps the hover fill).
 * @param content row content.
 */
@Composable
fun DshMenuRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    danger: Boolean = false,
    selectedFill: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = dshPanelColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (danger) colors.stateErrorPrimary else colors.labelPrimary
    val fill = when {
        selectedFill && enabled -> colors.interactiveHover
        pressed && enabled -> if (danger) colors.interactiveHoverDanger else colors.interactiveHover
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(DshPanelDefaults.RowShape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = DshPanelDefaults.RowMinHeight)
            .padding(
                horizontal = DshPanelDefaults.RowHorizontalPadding,
                vertical = DshPanelDefaults.RowVerticalPadding,
            )
            .then(if (enabled) Modifier else Modifier.alpha(0.4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DshPanelDefaults.RowGap),
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            content()
        }
    }
}

/** Menu row label: primary ink, 13sp/20, one ellipsized line. */
@Composable
fun DshMenuLabel(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(
        text = text,
        color = LocalDshPanelColors.current.labelPrimary,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Trailing menu value (for example a current model name): tertiary 13sp/20. */
@Composable
fun DshMenuValue(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalDshPanelColors.current.labelTertiary,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Keyboard shortcut hint inside a menu row: caption 11sp/16. */
@Composable
fun DshMenuShortcut(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalDshPanelColors.current.labelCaption,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Superscript badge used by permission rows (EXP). */
@Composable
fun DshMenuBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalDshPanelColors.current.labelTertiary,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        modifier = modifier,
    )
}

/** Secondary row caption (command alias, 12sp/18 tertiary). */
@Composable
fun DshMenuCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalDshPanelColors.current.labelTertiary,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Right-aligned row description (12sp/18 tertiary, one ellipsized line). */
@Composable
fun DshMenuDescription(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalDshPanelColors.current.labelTertiary,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        modifier = modifier,
    )
}

/** Menu row icon seat: 14dp glyph tinted with the menu icon token. */
@Composable
fun DshMenuIcon(resId: Int, modifier: Modifier = Modifier, size: Dp = 14.dp, tint: Color? = null) {
    Icon(
        painter = painterResource(id = resId),
        contentDescription = null,
        tint = tint ?: LocalDshPanelColors.current.menuIcon,
        modifier = modifier.size(size),
    )
}

/** Trailing selection check (14dp, primary ink). */
@Composable
fun DshMenuCheck(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(id = R.drawable.ic_check),
        contentDescription = null,
        tint = LocalDshPanelColors.current.labelPrimary,
        modifier = modifier.size(14.dp),
    )
}

/** Non-interactive group heading: tertiary 11sp/16, medium weight, 23dp seat. */
@Composable
fun DshMenuSectionLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 23.dp)
            .padding(
                start = DshPanelDefaults.RowHorizontalPadding,
                end = DshPanelDefaults.RowHorizontalPadding,
                top = 5.dp,
                bottom = 2.dp,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = LocalDshPanelColors.current.labelTertiary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Menu hairline separator (0.5dp border-l2 with 3dp/2dp margins). */
@Composable
fun DshMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .height(0.5.dp)
            .background(LocalDshPanelColors.current.borderL2),
    )
}

/**
 * Anchored dropdown: 4dp gap, viewport-safe placement, right/left
 * aligned to the anchor and a 12dp shadow gutter around the card.
 *
 * @param expanded whether the menu is open.
 * @param onDismiss dismiss callback.
 * @param menuWidth exact card width; omitted means content-derived with bounds.
 * @param align card alignment relative to the anchor edge.
 * @param maxHeight card maximum height (model selection uses the 360dp cap).
 * @param contentWidth size an unmeasured card to its widest row.
 * @param anchorGap gap between the card and its anchor; pass 0.dp for a menu that
 *        must sit flush against a bottom panel (for example the account menu).
 * @param content menu rows.
 */
@Composable
fun AnchoredMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    menuWidth: Dp? = null,
    align: DshMenuAlign = DshMenuAlign.End,
    maxHeight: Dp = DshPanelDefaults.MenuMaxHeight,
    contentWidth: Boolean = false,
    anchorGap: Dp = DshPanelDefaults.AnchorGap,
    /** 稳定展开方向：true 固定向上，false 固定向下，null 按空间自动选择。 */
    preferredAbove: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val gapPx = with(density) { anchorGap.roundToPx() }
    val shadowPx = with(density) { DshPanelDefaults.PopupShadowPadding.roundToPx() }
    val marginPx = with(density) { 12.dp.roundToPx() }
    val positionProvider = remember(gapPx, shadowPx, marginPx, align, preferredAbove) {
        DshMenuPositionProvider(gapPx, shadowPx, marginPx, align == DshMenuAlign.End, preferredAbove)
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(modifier = Modifier.padding(DshPanelDefaults.PopupShadowPadding)) {
            DshMenuCard(
                modifier = if (menuWidth != null) Modifier.width(menuWidth) else Modifier,
                maxHeight = maxHeight,
                contentWidth = contentWidth,
                content = content,
            )
        }
    }
}

/**
 * Menu overlay for edge-to-edge surfaces where a normal Popup window would be
 * inset/clamped by the system bars (composer triggers, drawer rows). The caller
 * measures the anchor in window coordinates; the overlay renders a full-screen
 * dialog window with no dim and places the menu card flush against that anchor.
 *
 * @param expanded whether the menu is open.
 * @param anchorBounds anchor rectangle in window coordinates, or null while unmeasured.
 * @param onDismiss outside-click/back dismissal.
 * @param menuWidth exact card width; omitted uses the menu's intrinsic bounds.
 * @param align card alignment relative to the anchor edge.
 * @param maxHeight card maximum height.
 * @param anchorGap gap between the card and the anchor (0 = flush).
 * @param content menu rows.
 */
@Composable
fun AnchoredMenuOverlay(
    expanded: Boolean,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    menuWidth: Dp? = null,
    align: DshMenuAlign = DshMenuAlign.End,
    maxHeight: Dp = DshPanelDefaults.MenuMaxHeight,
    anchorGap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded || anchorBounds == null) return
    val density = LocalDensity.current
    val gapPx = with(density) { anchorGap.roundToPx() }
    val marginPx = with(density) { 12.dp.roundToPx() }
    val maxHeightPx = with(density) { maxHeight.roundToPx() }
    val exactWidthPx = menuWidth?.let { with(density) { it.roundToPx() } }
    // The dialog window fits the system bars, while the measured anchor rect is in
    // decor coordinates; convert the anchor into the dialog's content coordinates.
    val resources = LocalView.current.context.resources
    val statusBarRes = resources.getIdentifier("status_bar_height", "dimen", "android")
    val statusBarPx = if (statusBarRes > 0) resources.getDimensionPixelSize(statusBarRes) else 0
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DisableDialogWindowDim(
            topInsetPx = statusBarPx,
            fullHeightPx = LocalView.current.resources.displayMetrics.heightPixels,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenW = constraints.maxWidth
            val screenH = constraints.maxHeight
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    // Transparent scrim: dismiss on outside tap without dimming.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            ),
                    )
                    // Consume taps on the card so they never reach the scrim.
                    Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }) {
                        DshMenuCard(
                            modifier = if (menuWidth != null) Modifier.width(menuWidth) else Modifier,
                            maxHeight = maxHeight,
                            content = content,
                        )
                    }
                },
            ) { measurables, _ ->
                val scrim = measurables[0].measure(
                    Constraints.fixed(width = screenW, height = screenH),
                )
                val card = measurables[1].measure(
                    Constraints(
                        maxWidth = exactWidthPx ?: (screenW - 2 * marginPx).coerceAtLeast(0),
                        maxHeight = maxHeightPx.coerceAtMost(screenH),
                    ),
                )
                layout(screenW, screenH) {
                    scrim.place(0, 0)
                    val cardW = card.width
                    val cardH = card.height
                    val anchorLeft = anchorBounds.left.toInt()
                    val anchorTop = anchorBounds.top.toInt() - statusBarPx
                    val anchorRight = anchorBounds.right.toInt()
                    val anchorBottom = anchorBounds.bottom.toInt() - statusBarPx
                    val desiredX = if (align == DshMenuAlign.End) anchorRight - cardW else anchorLeft
                    val maxX = (screenW - cardW - marginPx).coerceAtLeast(marginPx)
                    val cardX = desiredX.coerceIn(marginPx, maxX)
                    val below = anchorBottom + gapPx
                    val above = anchorTop - cardH - gapPx
                    val maxY = (screenH - cardH - marginPx).coerceAtLeast(marginPx)
                    val cardY = when {
                        below + cardH <= screenH - marginPx -> below
                        above >= marginPx -> above
                        else -> below
                    }.coerceIn(marginPx, maxY)
                    card.place(cardX, cardY)
                }
            }
        }
    }
}

/**
 * Menu placement: below the anchor when it fits, otherwise above;
 * clamped inside the viewport with a 12dp margin.
 */
private class DshMenuPositionProvider(
    private val gapPx: Int,
    private val shadowPx: Int,
    private val marginPx: Int,
    private val alignEnd: Boolean,
    private val preferredAbove: Boolean?,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val cardWidth = (popupContentSize.width - 2 * shadowPx).coerceAtLeast(0)
        val cardHeight = (popupContentSize.height - 2 * shadowPx).coerceAtLeast(0)
        val desiredX = if (alignEnd) anchorBounds.right - cardWidth else anchorBounds.left
        val maxX = (windowSize.width - cardWidth - marginPx).coerceAtLeast(marginPx)
        val cardX = desiredX.coerceIn(marginPx, maxX)
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - cardHeight - gapPx
        // Do not clamp an above-anchor card to the window's bottom: edge-to-edge
        // activity anchors sit in decor coordinates while the popup window excludes
        // system bars, so a bottom clamp would push the card away from its trigger.
        val cardY = when (preferredAbove) {
            true -> above.coerceAtLeast(marginPx)
            false -> below.coerceAtLeast(marginPx)
            null -> when {
                below + cardHeight <= windowSize.height - marginPx -> below
                above >= marginPx -> above
                else -> below
            }.coerceAtLeast(marginPx)
        }
        return IntOffset(cardX - shadowPx, cardY - shadowPx)
    }
}

/**
 * Bottom-sheet panel: translucent menu material, 16dp top radius,
 * 4dp content inset and the quiet drag handle.
 *
 * @param onDismiss sheet dismiss callback.
 * @param content sheet rows, typically menu groups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DshSheetPanel(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = dshPanelColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        // Android cannot blur the content behind a window, so composite the
        // translucent menu material over the base layer to keep the sheet legible.
        containerColor = colors.menuSurfaceFill.compositeOver(colors.bgBase),
        contentColor = colors.labelPrimary,
        scrimColor = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.borderL3),
                )
            }
        },
    ) {
        CompositionLocalProvider(
            LocalDshPanelColors provides colors,
            LocalContentColor provides colors.labelPrimary,
            LocalTextStyle provides LocalTextStyle.current.copy(
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DshPanelDefaults.ContentPadding)
                    .padding(bottom = 8.dp),
                content = content,
            )
        }
    }
}

/**
 * Modal dialog from the shared `Modal` primitive: 28dp radius, secondary
 * layer fill, prominent shadow, 22/14/12/24 header, 16sp/24 medium title, 28dp
 * close control, 24dp side padding, 20dp content gap and a right-aligned footer
 * with an 8dp gap.
 *
 * @param onDismiss dismiss callback (mask, close control and system back).
 * @param title dialog heading.
 * @param closeLabel localized accessible close-button label.
 * @param description optional supporting sentence under the title.
 * @param width card width cap (380dp, or 440dp for the risk confirmation).
 * @param content optional dialog body.
 * @param footer optional action row.
 */
@Composable
fun DshDialog(
    onDismiss: () -> Unit,
    title: String,
    closeLabel: String,
    description: String? = null,
    width: Dp = DshPanelDefaults.DialogWidth,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = dshPanelColors()
    val shape = DshPanelDefaults.DialogShape
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DisableDialogWindowDim()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgMask1)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val cardWidth = (maxWidth - DshPanelDefaults.DialogInset * 2).coerceAtMost(width)
            val cardMaxHeight = maxHeight - DshPanelDefaults.DialogInset * 2
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .heightIn(max = cardMaxHeight)
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .shadow(
                        elevation = 6.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.05f),
                        spotColor = Color.Black.copy(alpha = 0.04f),
                    )
                    .clip(shape)
                    .background(colors.dialogFill)
                    .border(0.5.dp, colors.borderL4, shape),
            ) {
                CompositionLocalProvider(
                    LocalDshPanelColors provides colors,
                    LocalContentColor provides colors.labelPrimary,
                ) {
                    Column {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, top = 22.dp, end = 14.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = title,
                                    color = colors.labelPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                DshDialogCloseButton(onClick = onDismiss, label = closeLabel)
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Column {
                                    if (description != null) {
                                        Text(
                                            text = description,
                                            color = colors.labelPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            fontWeight = FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 24.dp),
                                        )
                                    }
                                    if (content != null) {
                                        Column(
                                            modifier = Modifier
                                                .padding(horizontal = 24.dp)
                                                .padding(top = 20.dp),
                                            content = content,
                                        )
                                    }
                                }
                            }
                        }
                        if (footer != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                content = footer,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * Headless modal shell: the mask and 28dp card without default
 * header/footer chrome, for dialogs that own their layout (for example the
 * Agent preset guide with its 20sp title and no action footer).
 *
 * @param onDismiss dismiss callback (mask and system back).
 * @param width card width cap.
 * @param content card content; the caller supplies padding and scrolling.
 */
@Composable
fun DshHeadlessModal(
    onDismiss: () -> Unit,
    width: Dp = DshPanelDefaults.DialogWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = dshPanelColors()
    val shape = DshPanelDefaults.DialogShape
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DisableDialogWindowDim()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgMask1)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val cardWidth = (maxWidth - DshPanelDefaults.DialogInset * 2).coerceAtMost(width)
            val cardMaxHeight = maxHeight - DshPanelDefaults.DialogInset * 2
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .heightIn(max = cardMaxHeight)
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .shadow(
                        elevation = 6.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.05f),
                        spotColor = Color.Black.copy(alpha = 0.04f),
                    )
                    .clip(shape)
                    .background(colors.dialogFill)
                    .border(0.5.dp, colors.borderL4, shape),
            ) {
                CompositionLocalProvider(
                    LocalDshPanelColors provides colors,
                    LocalContentColor provides colors.labelPrimary,
                ) {
                    Column(content = content)
                }
            }
        }
    }
}

/** Modal close control (28dp seat, 14dp glyph, 8dp corner radius). */
@Composable
fun DshDialogCloseButton(
    onClick: () -> Unit,
    label: String,
    size: Dp = DshPanelDefaults.DialogCloseSize,
    cornerRadius: Dp = 8.dp,
    iconSize: Dp = 14.dp,
) {
    val colors = dshPanelColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (pressed) colors.interactiveHover else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_close),
            contentDescription = label,
            tint = colors.labelSecondary,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Button inside a dialog: 36dp height, 12dp radius, 14sp/22 label,
 * primary brand fill or a 0.5dp outlined control.
 *
 * @param text visible label.
 * @param primary primary fill versus outline.
 * @param enabled whether the button can be pressed.
 * @param onClick activation callback.
 * @param modifier external modifier (min-widths for risk actions).
 * @param height button height; dialogs use 36dp, the plugin install footer uses 40dp.
 */
@Composable
fun DshDialogButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = DshPanelDefaults.DialogButtonHeight,
) {
    val colors = dshPanelColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val fill = when {
        primary && pressed && enabled -> colors.buttonPrimaryHover
        primary -> colors.buttonPrimaryFill
        pressed && enabled -> colors.interactiveHover
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .then(if (enabled) Modifier else Modifier.alpha(0.4f))
            .background(fill)
            .then(if (primary) Modifier else Modifier.border(0.5.dp, colors.borderL3, shape))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) colors.labelPrimaryForeground else colors.labelPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * Official switch (ui-primitives Switch): 36x20 capsule track, 16dp thumb,
 * border-l3 off track / brand-primary on track, label-foreground thumb.
 *
 * @param checked current state.
 * @param onCheckedChange called with the next state.
 * @param modifier external modifier.
 * @param enabled whether the switch can be toggled.
 */
@Composable
fun DshSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = dshPanelColors()
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 0.dp,
        label = "dshSwitchThumb",
    )
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) colors.buttonPrimaryFill else colors.borderL3)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { onCheckedChange(!checked) }
            .then(if (enabled) Modifier else Modifier.alpha(0.5f))
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (checked) colors.labelPrimaryForeground else colors.switchThumb),
        )
    }
}

/**
 * Single-line input used inside dialogs and panels: 40dp height, 12dp
 * radius, 0.5dp border-l4, 13sp/20 text and caption placeholder.
 *
 * @param value current text.
 * @param onValueChange text change callback.
 * @param placeholder hint shown while empty.
 * @param modifier external modifier.
 */
@Composable
fun DshTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = dshPanelColors()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(colors.bgLayer2)
            .border(0.5.dp, colors.borderL4, shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.labelPrimary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(colors.labelPrimary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.labelCaption,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * Turn off the platform dialog window dim so the mask fill is the only scrim.
 * An optional top inset pins the dialog window below the status bar, keeping
 * the activity's own system-bar appearance untouched. Falls through silently
 * when the host is not a dialog window provider.
 */
@Composable
private fun DisableDialogWindowDim(topInsetPx: Int = 0, fullHeightPx: Int = 0) {
    val view = LocalView.current
    DisposableEffect(view, topInsetPx, fullHeightPx) {
        val apply = {
            val provider = view.parent as? DialogWindowProvider
            val window = provider?.window
            if (window != null) {
                window.setDimAmount(0f)
                if (topInsetPx > 0) {
                    val attrs = window.attributes
                    attrs.gravity = Gravity.TOP
                    attrs.y = topInsetPx
                    if (fullHeightPx > topInsetPx) {
                        attrs.height = fullHeightPx - topInsetPx
                    }
                    window.attributes = attrs
                }
            }
        }
        // The dialog window is attached after the first composition pass.
        view.post(apply)
        onDispose { }
    }
}
