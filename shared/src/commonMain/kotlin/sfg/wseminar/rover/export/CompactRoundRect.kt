package sfg.wseminar.rover.export

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Four cubic corners avoid SVGCanvas's verbose conic-to-quadratic tessellation. */
internal fun DrawScope.drawCompactRoundRect(color: Color, topLeft: Offset, size: Size, cornerRadius: CornerRadius) {
    val x = topLeft.x; val y = topLeft.y
    val r = (x + size.width); val b = (y + size.height)
    val rx = cornerRadius.x.coerceIn(0f, size.width / 2)
    val ry = cornerRadius.y.coerceIn(0f, size.height / 2)
    val k = 0.55228475f
    val path = Path().apply {
        moveTo(x + rx, y); lineTo(r - rx, y)
        cubicTo(r - rx + rx * k, y, r, y + ry - ry * k, r, y + ry)
        lineTo(r, b - ry)
        cubicTo(r, b - ry + ry * k, r - rx + rx * k, b, r - rx, b)
        lineTo(x + rx, b)
        cubicTo(x + rx - rx * k, b, x, b - ry + ry * k, x, b - ry)
        lineTo(x, y + ry)
        cubicTo(x, y + ry - ry * k, x + rx - rx * k, y, x + rx, y)
        close()
    }
    drawPath(path, color)
}
