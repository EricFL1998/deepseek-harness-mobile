package com.deepseek.dsh.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 把 bridge 返回的 `<svg>` 文档画成原生矢量图形。
 *
 * 官方插件的图标由 Host 侧的 React stub 渲染成 SVG 后传下来，因此客户端不需要
 * WebView，也不需要把图形抄成 Kotlin 常量。这里解析 SVG 的几何形状并用 Compose
 * Canvas 绘制：`path` / `circle` / `ellipse` / `rect` / `line` / `polygon` /
 * `polyline` 都支持，`g` 与 `defs` 透明穿过。
 *
 * 渐变、滤镜、文本与外部引用不绘制 —— 它们需要桥接做不到的字体与资源解析；
 * 图形缺失时调用方应回落到 [DshIconFrame] 之类的占位，而不是显示空框。
 */
@Composable
fun DshSvgIcon(
    svg: String?,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(svg) { svg?.let { SvgShapes.parse(it) } }
    val shapes = parsed?.shapes.orEmpty()
    if (shapes.isEmpty()) return
    val viewBox = parsed?.viewBox ?: Rect(0f, 0f, 24f, 24f)

    Canvas(modifier = modifier.size(size)) {
        val scaleX = this.size.width / viewBox.width.coerceAtLeast(0.001f)
        val scaleY = this.size.height / viewBox.height.coerceAtLeast(0.001f)
        // 统一缩放，保持图形比例；translate 把 viewBox 原点移到画布左上。
        val scale = minOf(scaleX, scaleY)
        withTransform(
            translateX = -viewBox.left * scale,
            translateY = -viewBox.top * scale,
            scaleX = scale,
            scaleY = scale,
        ) {
            for (shape in shapes) {
                shape.draw(this, tint)
            }
        }
    }
}

/** 在已缩放的坐标系里执行一段绘制。 */
private inline fun DrawScope.withTransform(
    translateX: Float,
    translateY: Float,
    scaleX: Float,
    scaleY: Float,
    block: DrawScope.() -> Unit,
) {
    withTransform({
        translate(translateX, translateY)
        scale(scaleX, scaleY, pivot = Offset.Zero)
    }) {
        block()
    }
}

/** 一个可绘制的 SVG 形状。 */
internal sealed interface SvgShape {
    /** 用给定颜色绘制自己。 */
    fun draw(scope: DrawScope, tint: Color)

    /** 把自己画成遮罩：用白色按亮度做 alpha，供 `DstIn` 使用。 */
    fun drawAsMask(scope: DrawScope)
}

/**
 * 一个形状的填充或描边来源。
 *
 * 图标集本身是单色的，因此「没写 `fill`/`stroke`」必须继续使用调用方的 tint；
 * 官方 artwork 则自带固定配色，甚至带渐变。两者都要支持，所以显式区分三种情况。
 */
internal sealed interface SvgFill {
    /** `fill="none"` / `stroke="none"`：不绘制。 */
    data object None : SvgFill

    /** 没有声明：沿用调用方的 tint，保持单色图标集的原样。 */
    data object Tint : SvgFill

    /** 声明了具体颜色或渐变。 */
    data class Paint(val paint: SvgPaint) : SvgFill
}

/** 具体绘制用色。 */
internal sealed interface SvgPaint {
    /** `#RRGGBB` 一类固定色。 */
    data class Solid(val color: Color) : SvgPaint

    /** `url(#id)` 指向的线性渐变，坐标与 stops 来自 `<linearGradient>`。 */
    data class Linear(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val stops: List<Pair<Float, Color>>,
    ) : SvgPaint
}

/** 遮罩用的亮度：白=保留、黑=挖掉。 */
private fun luminanceOf(paint: SvgPaint): Float = when (paint) {
    is SvgPaint.Solid -> paint.color.luminance()
    is SvgPaint.Linear -> if (paint.stops.isEmpty()) 0f
    else paint.stops.map { (_, color) -> color.luminance() }.average().toFloat()
}

/** 把 paint 解析成可绘制画刷；渐变点数不足时退回首个 stop 的纯色。 */
private fun SvgPaint.brush(): Brush = when (this) {
    is SvgPaint.Solid -> SolidColor(color)
    is SvgPaint.Linear -> {
        if (stops.isEmpty()) {
            SolidColor(Color.Transparent)
        } else if (stops.size == 1) {
            SolidColor(stops.first().second)
        } else {
            Brush.linearGradient(
                *stops.map { (offset, color) -> offset to color }.toTypedArray(),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
            )
        }
    }
}

/** 按来源选择画刷：沿用 tint 时用纯色，声明颜色时用声明值，None 时返回 null。 */
private fun SvgFill.brushOrNull(tint: Color): Brush? = when (this) {
    SvgFill.None -> null
    SvgFill.Tint -> SolidColor(tint)
    is SvgFill.Paint -> paint.brush()
}


/** 遮罩画刷：白色配亮度 alpha；纯黑返回 null，即完全不画。 */
private fun maskBrushOf(paint: SvgPaint): Brush? {
    val alpha = luminanceOf(paint)
    return if (alpha <= 0.004f) null else SolidColor(Color.White.copy(alpha = alpha))
}

/** 按遮罩语义画一条填充/描边。 */
private fun drawMasked(
    scope: DrawScope,
    fill: SvgFill,
    stroke: SvgFill,
    strokeWidth: Float,
    path: Path?,
    oval: Pair<Offset, Size>? = null,
) {
    ((fill as? SvgFill.Paint)?.paint)?.let(::maskBrushOf)?.let { brush ->
        if (path != null) scope.drawPath(path, brush, style = Fill)
        else if (oval != null) scope.drawOval(brush, oval.first, oval.second, style = Fill)
    }
    if (strokeWidth > 0f) {
        ((stroke as? SvgFill.Paint)?.paint)?.let(::maskBrushOf)?.let { brush ->
            if (path != null) scope.drawPath(path, brush, style = Stroke(width = strokeWidth))
            else if (oval != null) scope.drawOval(brush, oval.first, oval.second, style = Stroke(width = strokeWidth))
        }
    }
}

/** `path`：任意路径数据。 */
private class SvgPathShape(
    private val path: Path,
    private val fill: SvgFill,
    private val stroke: SvgFill,
    private val strokeWidth: Float,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        fill.brushOrNull(tint)?.let { scope.drawPath(path, it, style = Fill) }
        if (strokeWidth > 0f) {
            stroke.brushOrNull(tint)?.let {
                scope.drawPath(path, it, style = Stroke(width = strokeWidth))
            }
        }
    }

    override fun drawAsMask(scope: DrawScope) = drawMasked(scope, fill, stroke, strokeWidth, path)
}

/** `circle` / `ellipse`：由中心与半径描述。 */
private class SvgEllipseShape(
    private val center: Offset,
    private val radiusX: Float,
    private val radiusY: Float,
    private val fill: SvgFill,
    private val stroke: SvgFill,
    private val strokeWidth: Float,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        val topLeft = Offset(center.x - radiusX, center.y - radiusY)
        val size = Size(radiusX * 2, radiusY * 2)
        fill.brushOrNull(tint)?.let { scope.drawOval(it, topLeft, size, style = Fill) }
        if (strokeWidth > 0f) {
            stroke.brushOrNull(tint)?.let {
                scope.drawOval(it, topLeft, size, style = Stroke(width = strokeWidth))
            }
        }
    }

    override fun drawAsMask(scope: DrawScope) = drawMasked(
        scope, fill, stroke, strokeWidth,
        path = null,
        oval = Offset(center.x - radiusX, center.y - radiusY) to Size(radiusX * 2, radiusY * 2),
    )
}

/** `rect`：可带圆角。 */
private class SvgRectShape(
    private val path: Path,
    private val fill: SvgFill,
    private val stroke: SvgFill,
    private val strokeWidth: Float,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        fill.brushOrNull(tint)?.let { scope.drawPath(path, it, style = Fill) }
        if (strokeWidth > 0f) {
            stroke.brushOrNull(tint)?.let {
                scope.drawPath(path, it, style = Stroke(width = strokeWidth))
            }
        }
    }

    override fun drawAsMask(scope: DrawScope) = drawMasked(scope, fill, stroke, strokeWidth, path)
}

/** `line` / `polyline` / `polygon`：折线。 */
private class SvgPolyShape(
    private val path: Path,
    private val closed: Boolean,
    private val fill: SvgFill,
    private val stroke: SvgFill,
    private val strokeWidth: Float,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        if (closed) fill.brushOrNull(tint)?.let { scope.drawPath(path, it, style = Fill) }
        val width = if (strokeWidth > 0f) strokeWidth else 1f
        stroke.brushOrNull(tint)?.let { scope.drawPath(path, it, style = Stroke(width = width)) }
    }

    override fun drawAsMask(scope: DrawScope) =
        drawMasked(scope, fill, stroke, if (strokeWidth > 0f) strokeWidth else 1f, path)
}

/**
 * 带 `mask="url(#id)"` 的形状：先画自己，再按遮罩亮度用 `DstIn` 挖掉不可见部分。
 *
 * Compose 没有声明式遮罩，用离屏图层还原同样的效果。
 */
private class SvgMaskedShape(
    private val child: SvgShape,
    private val mask: List<SvgShape>,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        val canvas = scope.drawContext.canvas
        val bounds = androidx.compose.ui.geometry.Rect(
            0f, 0f, scope.size.width, scope.size.height,
        )
        canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint())
        child.draw(scope, tint)
        for (shape in mask) shape.drawAsMask(scope)
        canvas.restore()
    }

    override fun drawAsMask(scope: DrawScope) = child.drawAsMask(scope)
}

/**
 * SVG 文档解析器。
 *
 * 只做形状提取：不建 DOM、不解析样式表、不解析渐变。属性按名字直接读取，
 * 因此任何顺序、任何引号风格都能读；未知属性被忽略。
 */
internal object SvgShapes {

    /** 解析结果：形状列表与坐标系。 */
    data class Parsed(val shapes: List<SvgShape>, val viewBox: Rect)

    /** 元素名与其属性文本。 */
    private val ELEMENT = Regex("""<([A-Za-z][A-Za-z0-9:]*)((?:[^>"']|"[^"]*"|'[^']*')*)/?>""")

    /** 开/闭合标签：解析容器层级需要知道 `</g>` 何时出现。 */
    private val TOKEN = Regex("""<(/?)([A-Za-z][A-Za-z0-9:]*)((?:[^>"']|"[^"]*"|'[^']*')*?)(/?)>""")

    /** 一个 `<mask>` 的属性与其内部文本。 */
    private val MASK_BLOCK = Regex(
        """<mask((?:[^>"']|"[^"]*"|'[^']*')*)>(.*?)</mask>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val ATTRIBUTE = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")

    /** 一个 `<linearGradient>` 的属性与其内部文本。 */
    private val GRADIENT = Regex(
        """<linearGradient((?:[^>"']|"[^"]*"|'[^']*')*)>(.*?)</linearGradient>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** 一个 `<stop>`。 */
    private val STOP = Regex("""<stop((?:[^>"']|"[^"]*"|'[^']*')*)/?>""")

    /** `url(#id)` 渐变引用。 */
    private val GRADIENT_REFERENCE = Regex("""url\(\s*#([^)\s]+)\s*\)""")

    /** 少数常见命名色；其余非十六进制颜色忽略，交由调用方 tint 兜底。 */
    private val NAMED_COLORS = mapOf(
        "black" to Color(0xFF000000),
        "white" to Color(0xFFFFFFFF),
        "red" to Color(0xFFFF0000),
        "green" to Color(0xFF008000),
        "blue" to Color(0xFF0000FF),
        "gray" to Color(0xFF808080),
        "grey" to Color(0xFF808080),
    )

    /** 不绘制、但需要穿过其子元素的容器。 */
    private val CONTAINERS = setOf("svg", "g", "defs", "clipPath", "mask", "symbol", "title", "desc")

    /**
     * 容器内部的形状属于「定义」，不是可见图形。
     *
     * `<mask>` 里通常是一块白色底加一个黑色挖孔：如果照常绘制，白底会盖住整个
     * 图标、黑孔变成正中的一个黑点——这正是遮罩图标此前退化成圆点的原因。
     */
    private val DEFINING = setOf("defs", "clipPath", "mask", "symbol", "title", "desc")

    /**
     * 解析一段 SVG 文本。
     *
     * @param svg 完整的 `<svg>…</svg>` 文档。
     * @return 形状与坐标系；无法解析时返回空形状列表，调用方据此回落占位。
     */
    fun parse(svg: String): Parsed {
        val shapes = mutableListOf<SvgShape>()
        var viewBox = Rect(0f, 0f, 24f, 24f)
        // 渐变与遮罩都要先收集：形状用 `url(#id)` 引用它们，引用可能出现在定义之前。
        val gradients = gradientsOf(svg)
        val masks = masksOf(svg, gradients)
        // 每个容器帧记住它生效的遮罩；形状归属当前帧，闭合时弹栈。
        val frames = ArrayDeque<String?>()
        // 定义型容器内部的形状只用于定义；解析时跳过，避免它们被当成图形绘制。
        var definingDepth = 0
        for (match in TOKEN.findAll(svg)) {
            val closing = match.groupValues[1] == "/"
            val tag = match.groupValues[2]
            val attributes = attributesOf(match.groupValues[3])
            val selfClosing = match.groupValues[4] == "/"
            if (closing) {
                if (tag in CONTAINERS && frames.isNotEmpty()) frames.removeLast()
                if (tag in DEFINING && definingDepth > 0) definingDepth -= 1
                continue
            }
            if (tag in DEFINING) {
                if (!selfClosing) definingDepth += 1
                continue
            }
            if (definingDepth > 0) continue
            if (tag !in CONTAINERS) {
                val shape = shapeOf(tag, attributes, gradients) ?: continue
                val maskId = frames.lastOrNull()
                val maskShapes = maskId?.let(masks::get)
                shapes += if (maskShapes == null) shape else SvgMaskedShape(shape, maskShapes)
                continue
            }
            if (tag == "svg") viewBox = viewBoxOf(attributes) ?: viewBox
            val maskId = attributes["mask"]?.let {
                GRADIENT_REFERENCE.find(it)?.groupValues?.get(1)
            }
            if (!selfClosing) frames.addLast(maskId ?: frames.lastOrNull())
        }
        return Parsed(shapes, viewBox)
    }

    /**
     * 收集文档里的 `<mask>`。
     *
     * 遮罩内容按亮度参与 `DstIn`，因此内部形状照常解析；`maskUnits` 等坐标细节
     * 只影响裁剪范围，这里按整幅 viewBox 使用。
     * @param svg 完整文档。
     * @param gradients 已解析的渐变表。
     * @returns 遮罩 id 到形状列表。
     */
    private fun masksOf(svg: String, gradients: Map<String, SvgPaint.Linear>): Map<String, List<SvgShape>> {
        val out = mutableMapOf<String, List<SvgShape>>()
        for (block in MASK_BLOCK.findAll(svg)) {
            val attributes = attributesOf(block.groupValues[1])
            val id = attributes["id"] ?: continue
            val shapes = mutableListOf<SvgShape>()
            for (element in ELEMENT.findAll(block.groupValues[2])) {
                val tag = element.groupValues[1]
                if (tag in CONTAINERS) continue
                shapeOf(tag, attributesOf(element.groupValues[2]), gradients)?.let(shapes::add)
            }
            if (shapes.isNotEmpty()) out[id] = shapes
        }
        return out
    }

    /**
     * 收集文档里的线性渐变。
     *
     * 只支持 `linearGradient`；径向/锥形渐变与滤镜不在绘制范围内，引用它们的
     * 形状会退回声明色或 tint 而不是画成空白。
     * @param svg 完整文档文本。
     * @returns 渐变 id → 绘制参数。
     */
    private fun gradientsOf(svg: String): Map<String, SvgPaint.Linear> {
        val out = mutableMapOf<String, SvgPaint.Linear>()
        for (block in GRADIENT.findAll(svg)) {
            val attributes = attributesOf(block.groupValues[1])
            val id = attributes["id"] ?: continue
            val stops = STOP.findAll(block.groupValues[2]).mapNotNull { stop ->
                val stopAttributes = attributesOf(stop.groupValues[1])
                val offset = stopAttributes["offset"]?.let { raw ->
                    raw.trim().removeSuffix("%").toFloatOrNull()
                        ?.let { value -> if (raw.contains("%")) value / 100f else value }
                } ?: 0f
                val color = stopAttributes["stop-color"]?.let(::colorOf)
                    ?: stopAttributes["stop_color"]?.let(::colorOf)
                    ?: return@mapNotNull null
                offset.coerceIn(0f, 1f) to color
            }.toList()
            if (stops.isEmpty()) continue
            out[id] = SvgPaint.Linear(
                x1 = attributes["x1"]?.toFloatOrNull() ?: 0f,
                y1 = attributes["y1"]?.toFloatOrNull() ?: 0f,
                x2 = attributes["x2"]?.toFloatOrNull() ?: 1f,
                y2 = attributes["y2"]?.toFloatOrNull() ?: 1f,
                stops = stops,
            )
        }
        return out
    }

    /** `#RGB` / `#RRGGBB` / `#AARRGGBB` 与少量命名色。 */
    private fun colorOf(raw: String): Color? {
        val text = raw.trim()
        if (text.isEmpty() || text == "none" || text == "transparent") return null
        NAMED_COLORS[text.lowercase()]?.let { return it }
        val hex = text.removePrefix("#")
        return runCatching {
            when (hex.length) {
                3 -> {
                    val r = hex[0].digit() * 17
                    val g = hex[1].digit() * 17
                    val b = hex[2].digit() * 17
                    Color(r, g, b)
                }
                6 -> Color(
                    hex.substring(0, 2).toInt(16),
                    hex.substring(2, 4).toInt(16),
                    hex.substring(4, 6).toInt(16),
                )
                8 -> Color(
                    hex.substring(2, 4).toInt(16),
                    hex.substring(4, 6).toInt(16),
                    hex.substring(6, 8).toInt(16),
                    hex.substring(0, 2).toInt(16),
                )
                else -> null
            }
        }.getOrNull()
    }

    /** 单个十六进制位展开成 0..255。 */
    private fun Char.digit(): Int = this.digitToInt(16)

    /** 读取一个元素的属性表（小写名字 → 值）。 */
    private fun attributesOf(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (attribute in ATTRIBUTE.findAll(text)) {
            val name = attribute.groupValues[1].lowercase()
            val value = attribute.groupValues[3].ifEmpty { attribute.groupValues[4] }
            out[name] = value
        }
        return out
    }

    /** `viewBox="minX minY width height"`。 */
    private fun viewBoxOf(attributes: Map<String, String>): Rect? {
        val parts = attributes["viewbox"]?.trim()?.split(Regex("""[\s,]+""")) ?: return null
        if (parts.size != 4) return null
        val numbers = parts.map { it.toFloatOrNull() ?: return null }
        if (numbers[2] <= 0f || numbers[3] <= 0f) return null
        return Rect(numbers[0], numbers[1], numbers[0] + numbers[2], numbers[1] + numbers[3])
    }

    /** 把一个元素变成形状；不认识的标签返回 null。 */
    private fun shapeOf(
        tag: String,
        attributes: Map<String, String>,
        gradients: Map<String, SvgPaint.Linear>,
    ): SvgShape? {
        val fill = fillOf(attributes, "fill", gradients)
        val stroke = fillOf(attributes, "stroke", gradients)
        val strokeWidth = attributes["stroke-width"]?.toFloatOrNull()
            ?: if (stroke is SvgFill.Paint || stroke == SvgFill.Tint) 1f else 0f
        return when (tag) {
            "path" -> {
                val data = attributes["d"] ?: return null
                val path = runCatching {
                    PathParser().parsePathString(data).toPath()
                }.getOrNull() ?: return null
                SvgPathShape(path, fill, stroke, strokeWidth)
            }
            "circle" -> {
                val centerX = attributes["cx"]?.toFloatOrNull() ?: 0f
                val centerY = attributes["cy"]?.toFloatOrNull() ?: 0f
                val radius = attributes["r"]?.toFloatOrNull() ?: return null
                SvgEllipseShape(Offset(centerX, centerY), radius, radius, fill, stroke, strokeWidth)
            }
            "ellipse" -> {
                val centerX = attributes["cx"]?.toFloatOrNull() ?: 0f
                val centerY = attributes["cy"]?.toFloatOrNull() ?: 0f
                val radiusX = attributes["rx"]?.toFloatOrNull() ?: return null
                val radiusY = attributes["ry"]?.toFloatOrNull() ?: return null
                SvgEllipseShape(Offset(centerX, centerY), radiusX, radiusY, fill, stroke, strokeWidth)
            }
            "rect" -> {
                val x = attributes["x"]?.toFloatOrNull() ?: 0f
                val y = attributes["y"]?.toFloatOrNull() ?: 0f
                val width = attributes["width"]?.toFloatOrNull() ?: return null
                val height = attributes["height"]?.toFloatOrNull() ?: return null
                val radius = attributes["rx"]?.toFloatOrNull() ?: attributes["ry"]?.toFloatOrNull() ?: 0f
                SvgRectShape(roundedRect(x, y, width, height, radius), fill, stroke, strokeWidth)
            }
            "line" -> {
                val x1 = attributes["x1"]?.toFloatOrNull() ?: 0f
                val y1 = attributes["y1"]?.toFloatOrNull() ?: 0f
                val x2 = attributes["x2"]?.toFloatOrNull() ?: 0f
                val y2 = attributes["y2"]?.toFloatOrNull() ?: 0f
                val path = Path().apply {
                    moveTo(x1, y1)
                    lineTo(x2, y2)
                }
                SvgPolyShape(
                    path,
                    closed = false,
                    fill = SvgFill.None,
                    stroke = if (stroke == SvgFill.None) SvgFill.Tint else stroke,
                    strokeWidth = if (strokeWidth > 0f) strokeWidth else 1f,
                )
            }
            "polyline", "polygon" -> {
                val points = attributes["points"]?.trim()?.split(Regex("""[\s,]+"""))
                    ?.mapNotNull { it.toFloatOrNull() }
                    ?: return null
                if (points.size < 4) return null
                val path = Path().apply {
                    moveTo(points[0], points[1])
                    var index = 2
                    while (index + 1 < points.size) {
                        lineTo(points[index], points[index + 1])
                        index += 2
                    }
                }
                val closed = tag == "polygon"
                if (closed) path.close()
                SvgPolyShape(path, closed, fill, stroke, strokeWidth)
            }
            else -> null
        }
    }

    /**
     * 解析一条绘制来源。
     *
     * 缺省填充为 tint（单色图标集依赖这一点）；`none`/`transparent` 表示不画；
     * 具体颜色与 `url(#id)` 渐变按声明绘制。
     * @param attributes 元素属性表。
     * @param name `fill` 或 `stroke`。
     * @param gradients 文档内已收集的渐变。
     * @returns 绘制来源。
     */
    private fun fillOf(
        attributes: Map<String, String>,
        name: String,
        gradients: Map<String, SvgPaint.Linear>,
    ): SvgFill {
        val raw = attributes[name]
        // 描边缺省是不画：只有显式声明 stroke 才描边。
        if (raw == null) return if (name == "fill") SvgFill.Tint else SvgFill.None
        val value = raw.trim()
        if (value == "none" || value == "transparent") return SvgFill.None
        val reference = GRADIENT_REFERENCE.find(value)
        if (reference != null) {
            val gradient = gradients[reference.groupValues[1]]
            if (gradient != null) return SvgFill.Paint(gradient)
            return SvgFill.Tint
        }
        val color = colorOf(value) ?: return SvgFill.Tint
        val alpha = attributes["$name-opacity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        return SvgFill.Paint(SvgPaint.Solid(color.copy(alpha = color.alpha * alpha)))
    }

    /** 构造一个可带圆角的矩形路径。 */
    private fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float): Path {
        val path = Path()
        path.fillType = PathFillType.NonZero
        val r = radius.coerceIn(0f, minOf(width, height) / 2f)
        if (r <= 0f) {
            path.addRect(Rect(x, y, x + width, y + height))
            return path
        }
        path.moveTo(x + r, y)
        path.lineTo(x + width - r, y)
        path.quadraticTo(x + width, y, x + width, y + r)
        path.lineTo(x + width, y + height - r)
        path.quadraticTo(x + width, y + height, x + width - r, y + height)
        path.lineTo(x + r, y + height)
        path.quadraticTo(x, y + height, x, y + height - r)
        path.lineTo(x, y + r)
        path.quadraticTo(x, y, x + r, y)
        path.close()
        return path
    }
}
