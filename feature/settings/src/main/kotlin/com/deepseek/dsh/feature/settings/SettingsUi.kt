package com.deepseek.dsh.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DSH 设置界面设计令牌（对齐 dsh-upstream ui-theme / design-platform.css）。
 *
 * 手机上：设置主页对应设置弹窗的左侧导航，内容页对应右侧选项面板。
 * 这里只覆盖右侧面板使用的 alias 令牌，颜色值直接取 light / dark 两套调色板。
 */
@Immutable
internal data class SettingsPalette(
    val isDark: Boolean,
    /** --dsw-alias-label-primary：面板正文与标题主色。 */
    val labelPrimary: Color,
    /** --dsw-alias-label-secondary：说明文字与代码 chip 文案。 */
    val labelSecondary: Color,
    /** --dsw-alias-label-tertiary：弱化说明、分组标题。 */
    val labelTertiary: Color,
    /** --dsw-alias-settings-card-fill：设置卡片底色（= bg-layer-2）。 */
    val cardFill: Color,
    /** --dsw-alias-settings-card-stroke：0.5dp 卡片描边（= border-l4）。 */
    val cardStroke: Color,
    /** --dsw-alias-bg-module-platform：胶囊选择器 / 内嵌模块底色。 */
    val moduleFill: Color,
    /** --dsw-alias-border-l2：卡片内分隔线。 */
    val borderL2: Color,
    /** --dsw-alias-border-l3：描边按钮 / 虚线占位描边。 */
    val borderL3: Color,
    /** --dsw-alias-border-l4：卡片描边、代码 chip 描边。 */
    val borderL4: Color,
    /** --dsw-alias-button-primary-fill：主按钮底色。 */
    val buttonPrimaryFill: Color,
    /** --dsw-alias-label-primary-foreground：主按钮文字。 */
    val buttonPrimaryForeground: Color,
    /** --dsw-alias-button-primary-hover。 */
    val buttonPrimaryHover: Color,
    /** --dsw-alias-link：账号链接等业务蓝色。 */
    val link: Color,
    val success: Color,
    val error: Color,
    val warning: Color,
    /** --dsw-alias-bg-skeleton：头像占位底色。 */
    val skeletonFill: Color,
    /** 设置卡片选中态描边（static bluish-400）。 */
    val activeStroke: Color,
)

private val LightSettingsPalette = SettingsPalette(
    isDark = false,
    labelPrimary = Color(0xFF0F1115),
    labelSecondary = Color(0xFF61666B),
    labelTertiary = Color(0xFF81858C),
    cardFill = Color(0xFFFFFFFF),
    cardStroke = Color(0x29000000),
    moduleFill = Color(0xFFF5F6F7),
    borderL2 = Color(0x1A000000),
    borderL3 = Color(0x1F000000),
    borderL4 = Color(0x29000000),
    buttonPrimaryFill = Color(0xFF0F1115),
    buttonPrimaryForeground = Color(0xFFFFFFFF),
    buttonPrimaryHover = Color(0xFF43454A),
    link = Color(0xFF4176E6),
    success = Color(0xFF22C55E),
    error = Color(0xFFEC1313),
    warning = Color(0xFFDD8629),
    skeletonFill = Color(0x0A000000),
    activeStroke = Color(0xFFADB2B8),
)

private val DarkSettingsPalette = SettingsPalette(
    isDark = true,
    labelPrimary = Color(0xFFF9FAFB),
    labelSecondary = Color(0xFFCFD3D6),
    labelTertiary = Color(0xFFADB2B8),
    cardFill = Color(0xFF2C2C2E),
    cardStroke = Color(0x33FFFFFF),
    moduleFill = Color(0xFF353638),
    borderL2 = Color(0x1FFFFFFF),
    borderL3 = Color(0x29FFFFFF),
    borderL4 = Color(0x33FFFFFF),
    buttonPrimaryFill = Color(0xFFF9FAFB),
    buttonPrimaryForeground = Color(0xFF0F1115),
    buttonPrimaryHover = Color(0xFFEBEEF2),
    link = Color(0xFF7AAAFF),
    success = Color(0xFF22C55E),
    error = Color(0xFFF25A5A),
    warning = Color(0xFFF7AD31),
    skeletonFill = Color(0x14FFFFFF),
    activeStroke = Color(0xFFADB2B8),
)

internal val LocalSettingsPalette = staticCompositionLocalOf { LightSettingsPalette }

/** 为设置内容面板提供调色板；跟随系统深浅色。 */
@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSettingsPalette provides if (com.deepseek.dsh.core.designsystem.LocalDshDarkTheme.current) {
            DarkSettingsPalette
        } else {
            LightSettingsPalette
        },
        content = content,
    )
}

/** 设置面板的排版度量（CSS px → sp/dp 1:1）。 */
internal object SettingsText {
    /** ModelsSection .title：16/24 500，手机端放大到 18/26。 */
    val SectionTitle = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
    /** Plugins / AgentPreset .title：18/24 600，手机端 20/26。 */
    val PaneTitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    /** .intro：13/20 或 14/22 三级色，手机端 14/22 与 15/23。 */
    val Intro = TextStyle(fontSize = 14.sp, lineHeight = 22.sp)
    val IntroLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp)
    /** 分组标题：12/600 字距 .06em，手机端 13/20。 */
    val GroupHead = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.78.sp)
    /** 行标题：14/22 400，手机端 15/22。 */
    val RowTitle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val RowTitleMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    /** 行说明：12/18 三级色，手机端 13/20。 */
    val RowDesc = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)
    /** 插件卡标题：14/20 500，手机端 15/21。 */
    val CardTitle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    /** 预设卡名称：15/600、描述 13/1.55，手机端 16/22 与 14/21。 */
    val PresetName = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val PresetDesc = TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
    /** Tag / 模型 rowTag：11sp，手机端 12/18。 */
    val Tag = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    /** 账号余额金额：18/28 500，手机端 20/30。 */
    val Amount = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
    /** 账号卡片正文基准字号 13/22，手机端 14/22。 */
    val AccountBody = TextStyle(fontSize = 14.sp, lineHeight = 22.sp)
    /** 按钮：14/22；紧凑按钮 12/18，手机端 15/22 与 13/20。 */
    val Button = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val SmallButton = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)
    /** 代码 chip / 预设 id：12/18 等宽；11/17 等宽，手机端 13/20 与 12/18。 */
    val Code = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)
    val CodeSmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace)
}

/** 设置卡片：R20 + 0.5dp 描边 + settings-card-fill。 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalSettingsPalette.current
    val shape = RoundedCornerShape(20.dp)
    val border = BorderStroke(0.5.dp, palette.cardStroke)
    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = palette.cardFill,
            border = border,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                content = content,
            )
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = palette.cardFill,
            border = border,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                content = content,
            )
        }
    }
}

/** 选中态卡片底色 + bluish-400 描边（预设默认项、外观选中项）。 */
@Composable
internal fun SettingsSelectedCard(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 14.dp,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalSettingsPalette.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = palette.moduleFill,
        border = BorderStroke(0.5.dp, palette.activeStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            content = content,
        )
    }
}

/** 卡片内分隔线：0.5dp border-l2。 */
@Composable
internal fun SettingsCardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = LocalSettingsPalette.current.borderL2,
    )
}

/** 面板标题（右侧内容面板自带标题，与左侧导航标题并存）。 */
@Composable
internal fun SettingsPaneTitle(
    text: String,
    modifier: Modifier = Modifier,
    large: Boolean = true,
) {
    Text(
        text = text,
        modifier = modifier,
        style = if (large) SettingsText.PaneTitle else SettingsText.SectionTitle,
        color = LocalSettingsPalette.current.labelPrimary,
    )
}

/** 面板引导语（.intro，三级色）。 */
@Composable
internal fun SettingsIntro(
    text: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = if (large) SettingsText.IntroLarge else SettingsText.Intro,
        color = LocalSettingsPalette.current.labelTertiary,
    )
}

/** 分组标题（agent preset 内置/自定义）。 */
@Composable
internal fun SettingsGroupHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = SettingsText.GroupHead,
        color = LocalSettingsPalette.current.labelTertiary,
    )
}

/** Tag 色调。 */
internal enum class SettingsTagTone { Outline, Solid, Neutral, Success, Info, Warning, Danger }

/** Tag 形状：预设/插件使用胶囊，模型 rowTag 使用 R4。 */
internal enum class SettingsTagShape { Capsule, Rounded }

/** Tag：11sp 小标签。 */
@Composable
internal fun SettingsTag(
    text: String,
    tone: SettingsTagTone = SettingsTagTone.Outline,
    shape: SettingsTagShape = SettingsTagShape.Capsule,
) {
    val palette = LocalSettingsPalette.current
    val background: Color
    val foreground: Color
    var border: BorderStroke? = null
    when (tone) {
        SettingsTagTone.Outline -> {
            background = Color.Transparent
            foreground = palette.labelTertiary
            border = BorderStroke(0.5.dp, palette.borderL4)
        }
        SettingsTagTone.Solid -> {
            // Tag solid：label-primary 底 + bg-layer-3 文字（深色下为 bluish-800）。
            background = palette.labelPrimary
            foreground = if (palette.isDark) Color(0xFF353638) else Color(0xFFFFFFFF)
        }
        SettingsTagTone.Neutral -> {
            background = palette.moduleFill
            foreground = palette.labelSecondary
        }
        SettingsTagTone.Success -> {
            background = palette.success.copy(alpha = 0.10f)
            foreground = palette.success
        }
        SettingsTagTone.Info -> {
            background = palette.link.copy(alpha = 0.10f)
            foreground = palette.link
        }
        SettingsTagTone.Warning -> {
            background = palette.warning.copy(alpha = 0.12f)
            foreground = palette.warning
        }
        SettingsTagTone.Danger -> {
            background = palette.error.copy(alpha = 0.10f)
            foreground = palette.error
        }
    }
    val tagShape = if (shape == SettingsTagShape.Capsule) CircleShape else RoundedCornerShape(4.dp)
    Surface(shape = tagShape, color = background, border = border) {
        Text(
            text = text,
            style = SettingsText.Tag,
            color = foreground,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = if (shape == SettingsTagShape.Capsule) 10.dp else 8.dp,
                vertical = 2.dp,
            ),
        )
    }
}

/** 按钮尺寸：md 36/R12、sm 28/R8。 */
internal enum class SettingsButtonSize { Medium, Small }

/** 按钮变体：primary / outline / ghost。 */
internal enum class SettingsButtonVariant { Primary, Outline, Ghost }

/** 按钮几何与配色（ui-primitives Button）。 */
@Composable
internal fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SettingsButtonVariant = SettingsButtonVariant.Outline,
    size: SettingsButtonSize = SettingsButtonSize.Medium,
    enabled: Boolean = true,
    contentColor: Color? = null,
) {
    val palette = LocalSettingsPalette.current
    val height = if (size == SettingsButtonSize.Medium) 40.dp else 32.dp
    val radius = if (size == SettingsButtonSize.Medium) 12.dp else 8.dp
    val horizontalPadding = if (size == SettingsButtonSize.Medium) 16.dp else 12.dp
    val background = when (variant) {
        SettingsButtonVariant.Primary -> palette.buttonPrimaryFill
        else -> Color.Transparent
    }
    val foreground = when (variant) {
        SettingsButtonVariant.Primary -> palette.buttonPrimaryForeground
        SettingsButtonVariant.Outline, SettingsButtonVariant.Ghost ->
            contentColor ?: palette.labelPrimary
    }
    val border = if (variant == SettingsButtonVariant.Outline) {
        BorderStroke(0.5.dp, palette.borderL3)
    } else {
        null
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(radius),
        color = background,
        contentColor = foreground,
        border = border,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = horizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = if (size == SettingsButtonSize.Medium) {
                    SettingsText.Button
                } else {
                    SettingsText.SmallButton
                },
                color = foreground,
            )
        }
    }
}

/** 虚线占位按钮（添加提供商 / 创建预设）：手机端 H48 R16 虚线 border-l3。 */
@Composable
internal fun SettingsDashedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = Icons.Filled.Add,
) {
    val palette = LocalSettingsPalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .drawBehind {
                val width = 1.dp.toPx()
                drawRoundRect(
                    color = palette.borderL3,
                    style = Stroke(
                        width = width,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                        ),
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = palette.labelPrimary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = SettingsText.Button,
            color = palette.labelPrimary,
        )
    }
}

/** 文本链接（更多账号信息 / 前往开放平台查看）。 */
@Composable
internal fun SettingsTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = SettingsText.AccountBody,
            color = palette.labelPrimary,
            maxLines = 1,
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(2.dp))
            trailing()
        }
    }
}

/** chevron：右箭头用于可进入行，下箭头用于可展开卡片。 */
@Composable
internal fun SettingsChevronRight(size: Dp = 18.dp) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = LocalSettingsPalette.current.labelTertiary,
        modifier = Modifier.size(size),
    )
}

/** 可展开卡片的 chevron：展开时旋转 180°。 */
@Composable
internal fun SettingsChevronDown(expanded: Boolean, size: Dp = 12.dp) {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = LocalSettingsPalette.current.labelTertiary,
        modifier = Modifier
            .size(size)
            .rotate(if (expanded) 180f else 0f),
    )
}

/** 8dp 状态圆点。 */
@Composable
internal fun SettingsStatusDot(color: Color, size: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape),
    )
}

/** 账号头像占位：32dp 圆形骨架底 + 16dp 用户图标。 */
@Composable
internal fun SettingsUserAvatar(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val palette = LocalSettingsPalette.current
    Box(
        modifier = modifier
            .size(size)
            .background(palette.skeletonFill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = palette.labelTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 代码 chip：等宽 12sp + R4 + module 底（插件包名 / 预设 id 标签）。 */
@Composable
internal fun SettingsCodeChip(text: String, modifier: Modifier = Modifier) {
    val palette = LocalSettingsPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = palette.moduleFill,
    ) {
        Text(
            text = text,
            style = SettingsText.Code,
            color = palette.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** 空态：13/20 三级色，居中；手机端上下留白 28。 */
@Composable
internal fun SettingsEmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        style = SettingsText.Intro,
        color = LocalSettingsPalette.current.labelTertiary,
        textAlign = TextAlign.Center,
    )
}

/** 内容面板的通用内边距与纵向节奏（右侧 options 区域 24dp 底距）。 */
internal object SettingsPaneDimens {
    val Horizontal = 18.dp
    val Top = 10.dp
    val Bottom = 28.dp
}

/** 设置行右侧的控件槽（开关 / 下拉胶囊 / 数值）。 */
@Composable
internal fun SettingsRowControlSlot(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 开关（ui-primitives Switch）：轨道 36×20、圆钮 16，开启轨道为 brand-primary，
 * 关闭为 border-l3；滑块在浅色下为白色、深色关闭态为 bluish-400。
 */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val palette = LocalSettingsPalette.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.labelPrimary else palette.borderL3,
        label = "switch_track",
    )
    val thumbColor = when {
        checked -> if (palette.isDark) Color(0xFF0F1115) else Color(0xFFFFFFFF)
        palette.isDark -> Color(0xFFADB2B8)
        else -> Color(0xFFFFFFFF)
    }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        label = "switch_thumb",
    )
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .background(thumbColor, CircleShape),
        )
    }
}
