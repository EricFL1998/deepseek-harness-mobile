package com.deepseek.dsh.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
}

/** `path`：任意路径数据。 */
private class SvgPathShape(
    private val path: Path,
    private val filled: Boolean,
    private val strokeWidth: Float,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        if (filled) {
            scope.drawPath(path, SolidColor(tint), style = Fill)
        }
        if (strokeWidth > 0f) {
            scope.drawPath(path, SolidColor(tint), style = Stroke(width = strokeWidth))
        }
    }
}

/** `circle` / `ellipse`：由中心与半径描述。 */
private class SvgEllipseShape(
    private val center: Offset,
    private val radiusX: Float,
    private val radiusY: Float,
    private val filled: Boolean,
    private val strokeWidth: Float,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        val topLeft = Offset(center.x - radiusX, center.y - radiusY)
        val size = Size(radiusX * 2, radiusY * 2)
        if (filled) scope.drawOval(SolidColor(tint), topLeft, size, style = Fill)
        if (strokeWidth > 0f) scope.drawOval(SolidColor(tint), topLeft, size, style = Stroke(width = strokeWidth))
    }
}

/** `rect`：可带圆角。 */
private class SvgRectShape(
    private val path: Path,
    private val filled: Boolean,
    private val strokeWidth: Float,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        if (filled) scope.drawPath(path, SolidColor(tint), style = Fill)
        if (strokeWidth > 0f) scope.drawPath(path, SolidColor(tint), style = Stroke(width = strokeWidth))
    }
}

/** `line` / `polyline` / `polygon`：折线。 */
private class SvgPolyShape(
    private val path: Path,
    private val closed: Boolean,
    private val filled: Boolean,
    private val strokeWidth: Float,
) : SvgShape {
    override fun draw(scope: DrawScope, tint: Color) {
        if (closed && filled) scope.drawPath(path, SolidColor(tint), style = Fill)
        val width = if (strokeWidth > 0f) strokeWidth else 1f
        scope.drawPath(path, SolidColor(tint), style = Stroke(width = width))
    }
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
    private val ATTRIBUTE = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")

    /** 不绘制、但需要穿过其子元素的容器。 */
    private val CONTAINERS = setOf("svg", "g", "defs", "clipPath", "mask", "symbol", "title", "desc")

    /**
     * 解析一段 SVG 文本。
     *
     * @param svg 完整的 `<svg>…</svg>` 文档。
     * @return 形状与坐标系；无法解析时返回空形状列表，调用方据此回落占位。
     */
    fun parse(svg: String): Parsed {
        val shapes = mutableListOf<SvgShape>()
        var viewBox = Rect(0f, 0f, 24f, 24f)
        for (match in ELEMENT.findAll(svg)) {
            val tag = match.groupValues[1]
            val attributes = attributesOf(match.groupValues[2])
            when {
                tag == "svg" -> viewBox = viewBoxOf(attributes) ?: viewBox
                tag in CONTAINERS -> Unit
                else -> shapeOf(tag, attributes)?.let(shapes::add)
            }
        }
        return Parsed(shapes, viewBox)
    }

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
    private fun shapeOf(tag: String, attributes: Map<String, String>): SvgShape? {
        val filled = fillOf(attributes)
        val strokeWidth = attributes["stroke-width"]?.toFloatOrNull()
            ?: if (attributes["stroke"] != null && attributes["stroke"] != "none") 1f else 0f
        return when (tag) {
            "path" -> {
                val data = attributes["d"] ?: return null
                val path = runCatching {
                    PathParser().parsePathString(data).toPath()
                }.getOrNull() ?: return null
                SvgPathShape(path, filled, strokeWidth)
            }
            "circle" -> {
                val centerX = attributes["cx"]?.toFloatOrNull() ?: 0f
                val centerY = attributes["cy"]?.toFloatOrNull() ?: 0f
                val radius = attributes["r"]?.toFloatOrNull() ?: return null
                SvgEllipseShape(Offset(centerX, centerY), radius, radius, filled, strokeWidth)
            }
            "ellipse" -> {
                val centerX = attributes["cx"]?.toFloatOrNull() ?: 0f
                val centerY = attributes["cy"]?.toFloatOrNull() ?: 0f
                val radiusX = attributes["rx"]?.toFloatOrNull() ?: return null
                val radiusY = attributes["ry"]?.toFloatOrNull() ?: return null
                SvgEllipseShape(Offset(centerX, centerY), radiusX, radiusY, filled, strokeWidth)
            }
            "rect" -> {
                val x = attributes["x"]?.toFloatOrNull() ?: 0f
                val y = attributes["y"]?.toFloatOrNull() ?: 0f
                val width = attributes["width"]?.toFloatOrNull() ?: return null
                val height = attributes["height"]?.toFloatOrNull() ?: return null
                val radius = attributes["rx"]?.toFloatOrNull() ?: attributes["ry"]?.toFloatOrNull() ?: 0f
                SvgRectShape(roundedRect(x, y, width, height, radius), filled, strokeWidth)
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
                SvgPolyShape(path, closed = false, filled = false, strokeWidth = if (strokeWidth > 0f) strokeWidth else 1f)
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
                SvgPolyShape(path, closed, filled, strokeWidth)
            }
            else -> null
        }
    }

    /** `fill` 缺省为黑（即填充），`fill="none"` 关闭填充。 */
    private fun fillOf(attributes: Map<String, String>): Boolean {
        val fill = attributes["fill"] ?: return true
        return fill != "none" && fill != "transparent"
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
