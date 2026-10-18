package com.deepseek.dsh.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 上游 (dsh-upstream/packages/client/ui-primitives/src/icons/index.tsx & FishLogo.tsx)
 * 100% 像素级对齐的 SVG 矢量图标集 (Compose ImageVector)
 */
object DshIcons {

    // 鱼形 Logo 路径数据
    const val FISH_LOGO_PATH =
        "M22.9168 1.43018C22.6713 1.31018 22.5658 1.53918 22.4223 1.65519C22.3733 1.69269 22.3318 1.74169 22.2903 1.78669" +
        "C21.9317 2.1697 21.5127 2.42121 20.9657 2.39121C20.1657 2.34621 19.4827 2.59771 18.8787 3.20973" +
        "C18.7502 2.45521 18.3236 2.0047 17.6746 1.71569C17.3351 1.56568 16.9916 1.41518 16.7536 1.08867" +
        "C16.5876 0.856163 16.5421 0.597155 16.4591 0.341647C16.4061 0.187643 16.3536 0.0301382 16.1761 0.00363739" +
        "C15.9836 -0.0263635 15.9081 0.135141 15.8326 0.270145C15.5306 0.822162 15.4136 1.43018 15.4251 2.0462" +
        "C15.4516 3.43174 16.0366 4.53527 17.1991 5.3203C17.3311 5.4103 17.3651 5.5003 17.3236 5.63181" +
        "C17.2441 5.90231 17.1501 6.16482 17.0671 6.43533C17.0141 6.60784 16.9351 6.64584 16.7501 6.57033" +
        "C16.1121 6.30383 15.5611 5.90931 15.074 5.4328C14.2475 4.63328 13.5 3.75075 12.568 3.05973" +
        "C12.349 2.89822 12.13 2.74822 11.9034 2.60522C10.9524 1.68169 12.028 0.923165 12.277 0.833162" +
        "C12.5375 0.739159 12.3675 0.41615 11.5259 0.42015C10.6844 0.42365 9.91439 0.705658 8.93286 1.08117" +
        "C8.78935 1.13767 8.63835 1.17867 8.48384 1.21267C7.59332 1.04367 6.66829 1.00617 5.70226 1.11517" +
        "C3.88321 1.31768 2.43016 2.1777 1.36213 3.64575C0.0790928 5.4103 -0.222916 7.41536 0.146595 9.50642" +
        "C0.535106 11.7105 1.66014 13.535 3.38869 14.9616C5.18125 16.4406 7.24581 17.1657 9.60138 17.0266" +
        "C11.0319 16.9441 12.6245 16.7526 14.421 15.2321C14.874 15.4576 15.3496 15.5476 16.1381 15.6151" +
        "C16.7456 15.6716 17.3306 15.5851 17.7836 15.4911C18.4931 15.3411 18.4441 14.6841 18.1876 14.5636" +
        "C16.1081 13.595 16.5646 13.9891 16.1496 13.67C17.2061 12.42 18.8202 10.1979 19.3182 7.17235" +
        "C19.3672 6.83834 19.4297 6.36783 19.4222 6.09732C19.4182 5.93231 19.4562 5.86831 19.6447 5.84931" +
        "C20.1657 5.78931 20.6712 5.64681 21.1357 5.3913C22.4833 4.65528 23.0268 3.44624 23.1548 1.9972" +
        "C23.1738 1.77569 23.1508 1.54668 22.9168 1.43018ZM11.1749 14.4736C9.15936 12.889 8.18184 12.3675 7.77832 12.39" +
        "C7.40081 12.4125 7.46881 12.8445 7.55182 13.126C7.63882 13.404 7.75182 13.5955 7.91033 13.8396" +
        "C8.01983 14.0011 8.09533 14.1706 8.09533 14.3761C8.09533 14.8871 7.68132 15.3011 7.1703 15.3011" +
        "C6.91729 15.3011 6.69078 15.1976 6.52728 15.0341C5.22874 13.7355 4.31422 12.183 3.8647 10.4579" +
        "C3.41519 8.73286 3.44569 6.89981 3.9537 5.18726C4.46172 3.47472 5.43125 1.93668 6.74579 0.757636" +
        "C6.91579 0.605131 7.1428 0.513628 7.39181 0.513628C7.90282 0.513628 8.31684 0.927641 8.31684 1.43866" +
        "C8.31684 1.66666 8.23433 1.87567 8.09783 2.03818C7.03929 3.23821 6.30927 4.70776 5.99226 6.27681" +
        "C5.67525 7.84586 5.78225 9.46241 6.30177 10.9395C6.54077 11.6215 6.90378 12.2475 7.3693 12.7845" +
        "C7.81831 12.7215 8.44183 12.879 9.38736 13.4886C10.0214 13.8966 10.6384 14.2251 11.1749 14.4736Z"

    // 新会话气泡+加号 IconNewChatOutline
    val NewChatOutline: ImageVector = ImageVector.Builder(
        name = "NewChatOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2.37091f, 11.2501f)
            curveTo(1.58745f, 9.89288f, 1.32067f, 8.29835f, 1.61969f, 6.76006f)
            curveTo(1.91872f, 5.22177f, 2.76342f, 3.8433f, 3.99826f, 2.87846f)
            curveTo(5.2331f, 1.91362f, 6.77494f, 1.42737f, 8.33988f, 1.50925f)
            curveTo(9.90482f, 1.59113f, 11.3875f, 2.23562f, 12.5149f, 3.32406f)
            curveTo(13.6425f, 4.41269f, 14.3387f, 5.87206f, 14.4754f, 7.4334f)
            curveTo(14.612f, 8.99474f, 14.18f, 10.5529f, 13.2587f, 11.8209f)
            curveTo(12.3375f, 13.0888f, 10.9891f, 13.9813f, 9.46194f, 14.3337f)
            curveTo(8.18691f, 14.628f, 6.85895f, 14.5294f, 5.64989f, 14.0605f)
            curveTo(5.1712f, 13.8748f, 4.76962f, 13.4932f, 4.26534f, 13.3967f)
            curveTo(3.67413f, 13.2835f, 2.95257f, 13.5598f, 2.03794f, 14.3337f)
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 5f)
            lineTo(8f, 11f)
            moveTo(5f, 8f)
            lineTo(11f, 8f)
        }
    }.build()

    // 面板切换侧栏 IconPanelLeftOutline
    val PanelLeftOutline: ImageVector = ImageVector.Builder(
        name = "PanelLeftOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13.5f, 1.5f)
            lineTo(2.5f, 1.5f)
            curveTo(1.94772f, 1.5f, 1.5f, 1.94772f, 1.5f, 2.5f)
            lineTo(1.5f, 13.5f)
            curveTo(1.5f, 14.0523f, 1.94772f, 14.5f, 2.5f, 14.5f)
            lineTo(13.5f, 14.5f)
            curveTo(14.0523f, 14.5f, 14.5f, 14.0523f, 14.5f, 13.5f)
            lineTo(14.5f, 2.5f)
            curveTo(14.5f, 1.94772f, 14.0523f, 1.5f, 13.5f, 1.5f)
            close()
            moveTo(5.5f, 1.5f)
            lineTo(5.5f, 14.5f)
        }
    }.build()

    // 输入框加号/指令触发器 IconPlusOutline
    val PlusOutline: ImageVector = ImageVector.Builder(
        name = "PlusOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 2f)
            lineTo(8f, 14f)
            moveTo(2f, 8f)
            lineTo(14f, 8f)
        }
    }.build()

    // 发送按钮 IconPaperPlaneOutline
    val PaperPlaneOutline: ImageVector = ImageVector.Builder(
        name = "PaperPlaneOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4.74024f, 9.11029f)
            lineTo(1.82882f, 7.79865f)
            curveTo(1.75022f, 7.76323f, 1.75026f, 7.65161f, 1.82889f, 7.61626f)
            lineTo(12.9665f, 2.60943f)
            curveTo(13.0354f, 2.57846f, 13.1125f, 2.63213f, 13.1073f, 2.70749f)
            lineTo(12.3914f, 13.1388f)
            curveTo(12.3864f, 13.2117f, 12.3073f, 13.2548f, 12.2433f, 13.2194f)
            lineTo(6.12677f, 9.83657f)
            close()
            moveTo(4.74024f, 9.11029f)
            lineTo(12.9665f, 2.60943f)
        }
    }.build()

    // 停止生成按钮 IconStopFill
    val StopFill: ImageVector = ImageVector.Builder(
        name = "StopFill",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            fill = SolidColor(Color.White)
        ) {
            moveTo(3.5f, 2.5f)
            lineTo(12.5f, 2.5f)
            curveTo(13.0523f, 2.5f, 13.5f, 2.94772f, 13.5f, 3.5f)
            lineTo(13.5f, 12.5f)
            curveTo(13.5f, 13.0523f, 13.0523f, 13.5f, 12.5f, 13.5f)
            lineTo(3.5f, 13.5f)
            curveTo(2.94772f, 13.5f, 2.5f, 13.0523f, 2.5f, 12.5f)
            lineTo(2.5f, 3.5f)
            curveTo(2.5f, 2.94772f, 2.94772f, 2.5f, 3.5f, 2.5f)
            close()
        }
    }.build()

    // 复制图标 IconCopyOutline
    val CopyOutline: ImageVector = ImageVector.Builder(
        name = "CopyOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4.07373f, 1.52075f)
            lineTo(12.4795f, 1.52075f)
            curveTo(13.5841f, 1.52075f, 14.4795f, 2.41618f, 14.4795f, 3.52075f)
            lineTo(14.4795f, 11.9265f)
            moveTo(1.52075f, 6.07373f)
            lineTo(1.52075f, 12.4669f)
            curveTo(1.52075f, 13.5715f, 2.41618f, 14.4669f, 3.52075f, 14.4669f)
            lineTo(9.91395f, 14.4669f)
            curveTo(11.0185f, 14.4669f, 11.9139f, 13.5715f, 11.9139f, 12.4669f)
            lineTo(11.9139f, 6.07373f)
            curveTo(11.9139f, 4.96916f, 11.0185f, 4.07373f, 9.91395f, 4.07373f)
            lineTo(3.52075f, 4.07373f)
            curveTo(2.41618f, 4.07373f, 1.52075f, 4.96916f, 1.52075f, 6.07373f)
            close()
        }
    }.build()

    // 重试/重新生成图标 IconRefreshOutline
    val RefreshOutline: ImageVector = ImageVector.Builder(
        name = "RefreshOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13.0001f, 3.6f)
            lineTo(14.5001f, 5.1f)
            moveTo(14.5001f, 8f)
            curveTo(14.5f, 9.28552f, 14.1188f, 10.5422f, 13.4045f, 11.611f)
            curveTo(12.6903f, 12.6799f, 11.6752f, 13.5129f, 10.4875f, 14.0049f)
            curveTo(9.29982f, 14.4968f, 7.99295f, 14.6255f, 6.73212f, 14.3747f)
            curveTo(5.4713f, 14.124f, 4.31314f, 13.505f, 3.4041f, 12.596f)
            curveTo(2.49514f, 11.687f, 1.87614f, 10.5288f, 1.62537f, 9.26798f)
            curveTo(1.37459f, 8.00716f, 1.50331f, 6.70028f, 1.99525f, 5.51261f)
            curveTo(2.48719f, 4.32494f, 3.32025f, 3.30981f, 4.3891f, 2.59557f)
            curveTo(5.45795f, 1.88134f, 6.71458f, 1.50008f, 8.0001f, 1.5f)
            curveTo(9.9001f, 1.5f, 11.7001f, 2.3f, 13.0001f, 3.6f)
        }
    }.build()

    // 好评/点赞图标 IconLikeOutline
    val LikeOutline: ImageVector = ImageVector.Builder(
        name = "LikeOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13.537f, 8.12098f)
            lineTo(12.3983f, 12.8455f)
            curveTo(12.1818f, 13.7438f, 11.378f, 14.3769f, 10.454f, 14.3769f)
            lineTo(7.43799f, 14.3769f)
            lineTo(5.16577f, 14.3769f)
            curveTo(3.50892f, 14.3769f, 2.16577f, 13.0337f, 2.16577f, 11.3769f)
            lineTo(2.16577f, 7.88668f)
            curveTo(2.16577f, 7.33439f, 2.61349f, 6.88668f, 3.16577f, 6.88668f)
            lineTo(4.02665f, 6.88668f)
            curveTo(5.84943f, 6.88668f, 7.38083f, 3.28711f, 7.67689f, 2.54578f)
            curveTo(7.77922f, 2.27824f, 8.08228f, 1.87578f, 8.59039f, 2.07775f)
            curveTo(10.3291f, 2.76886f, 9.23144f, 6.04071f, 8.96955f, 6.75058f)
            curveTo(8.94502f, 6.81707f, 8.99495f, 6.88668f, 9.06581f, 6.88668f)
            lineTo(12.5648f, 6.88668f)
            curveTo(13.2119f, 6.88668f, 13.6886f, 7.49192f, 13.537f, 8.12098f)
            close()
        }
    }.build()

    // 差评/点踩图标 IconDislikeOutline
    val DislikeOutline: ImageVector = ImageVector.Builder(
        name = "DislikeOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2.46302f, 8.06749f)
            lineTo(3.60171f, 3.34299f)
            curveTo(3.81822f, 2.44467f, 4.62196f, 1.81162f, 5.546f, 1.8116f)
            lineTo(8.56202f, 1.81158f)
            lineTo(10.8342f, 1.81158f)
            curveTo(12.4911f, 1.81158f, 13.8342f, 3.15473f, 13.8342f, 4.81158f)
            lineTo(13.8342f, 8.3018f)
            curveTo(13.8342f, 8.85408f, 13.3865f, 9.3018f, 12.8342f, 9.3018f)
            lineTo(11.9734f, 9.3018f)
            curveTo(10.1506f, 9.3018f, 8.61918f, 12.9014f, 8.32311f, 13.6427f)
            curveTo(8.22078f, 13.9102f, 7.91772f, 14.3127f, 7.40961f, 14.1107f)
            curveTo(5.67089f, 13.4196f, 6.76856f, 10.1478f, 7.03045f, 9.43789f)
            curveTo(7.05498f, 9.37141f, 7.00505f, 9.3018f, 6.93419f, 9.3018f)
            lineTo(3.43519f, 9.3018f)
            curveTo(2.78811f, 9.3018f, 2.31141f, 8.69656f, 2.46302f, 8.06749f)
            close()
        }
    }.build()

    // 分享图标 IconShareOutline
    val ShareOutline: ImageVector = ImageVector.Builder(
        name = "ShareOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.1256f, 7.58723f)
            lineTo(8.6646f, 1.89266f)
            curveTo(8.53993f, 1.76259f, 8.32031f, 1.85088f, 8.32031f, 2.03109f)
            lineTo(8.32035f, 5.03832f)
            curveTo(3.42001f, 5.07166f, 1.49273f, 9.87168f, 1.4757f, 13.2768f)
            curveTo(5.30067f, 9.7729f, 8.08306f, 10.148f, 8.30592f, 10.1849f)
            lineTo(8.32031f, 13.9692f)
            curveTo(8.32031f, 14.1494f, 8.53985f, 14.2377f, 8.6646f, 14.1077f)
            lineTo(14.1254f, 8.41799f)
            close()
        }
    }.build()

    // 思考与火花 IconThinkOutline
    val ThinkOutline: ImageVector = ImageVector.Builder(
        name = "ThinkOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(10.2854f, 5.71481f)
            curveTo(12.9673f, 8.39663f, 14.1182f, 11.5938f, 12.8562f, 12.8559f)
            curveTo(11.5942f, 14.1179f, 8.39706f, 12.9669f, 5.71518f, 10.2851f)
            curveTo(3.03333f, 7.60323f, 1.88236f, 4.40608f, 3.14441f, 3.14403f)
            curveTo(4.40644f, 1.882f, 7.6036f, 3.03297f, 10.2854f, 5.71481f)
            close()
        }
    }.build()

    // 设置齿轮 IconSettingsOutline
    val SettingsOutline: ImageVector = ImageVector.Builder(
        name = "SettingsOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8f, 9.75012f)
            curveTo(8.9665f, 9.75012f, 9.75f, 8.96662f, 9.75f, 8.00012f)
            curveTo(9.75f, 7.03362f, 8.9665f, 6.25012f, 8f, 6.25012f)
            curveTo(7.0335f, 6.25012f, 6.25f, 7.03362f, 6.25f, 8.00012f)
            curveTo(6.25f, 8.96662f, 7.0335f, 9.75012f, 8f, 9.75012f)
            close()
            moveTo(8f, 1.5f)
            lineTo(8f, 3.5f)
            moveTo(8f, 12.5f)
            lineTo(8f, 14.5f)
            moveTo(1.5f, 8f)
            lineTo(3.5f, 8f)
            moveTo(12.5f, 8f)
            lineTo(14.5f, 8f)
        }
    }.build()

    // 权限盾牌 IconShieldOutline
    val ShieldOutline: ImageVector = ImageVector.Builder(
        name = "ShieldOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6.80132f, 2.14853f)
            curveTo(7.70663f, 1.80917f, 8.70422f, 1.80919f, 9.60952f, 2.14859f)
            lineTo(14.1296f, 3.84317f)
            lineTo(14.1296f, 7.11961f)
            curveTo(14.1296f, 11.6089f, 10.7615f, 13.5975f, 8.20543f, 14.5779f)
            curveTo(5.64931f, 13.5975f, 2.28052f, 11.6089f, 2.28052f, 7.11961f)
            lineTo(2.28052f, 3.84317f)
            lineTo(6.80132f, 2.14853f)
            close()
        }
    }.build()

    /**
     * 主发送按钮几何 (InputBar.tsx primary button 上箭头，fill 实心)。
     * 对应截图：主题色圆形按钮内的白色向上箭头。
     */
    val SendUpArrow: ImageVector = ImageVector.Builder(
        name = "SendUpArrow",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(8.3125f, 0.980183f)
            curveTo(8.66767f, 1.0531f, 8.97902f, 1.20418f, 9.2627f, 1.43233f)
            curveTo(9.48724f, 1.61297f, 9.73029f, 1.85793f, 9.97949f, 2.10714f)
            lineTo(14.707f, 6.83468f)
            lineTo(13.293f, 8.24874f)
            lineTo(9f, 3.95577f)
            lineTo(9f, 15.0417f)
            lineTo(7f, 15.0417f)
            lineTo(7f, 3.95577f)
            lineTo(2.70703f, 8.24874f)
            lineTo(1.29297f, 6.83468f)
            lineTo(6.02051f, 2.10714f)
            curveTo(6.26971f, 1.85793f, 6.51277f, 1.61297f, 6.7373f, 1.43233f)
            curveTo(6.97662f, 1.23986f, 7.28445f, 1.04402f, 7.6875f, 0.980183f)
            curveTo(7.8973f, 0.947006f, 8.1031f, 0.95516f, 8.3125f, 0.980183f)
            close()
        }
    }.build()
}
