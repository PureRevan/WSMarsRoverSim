package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.sqrt

/** Fit the entire measured rectangle inside the circle, leaving room for its outline.
 * Scaling the cached layout avoids repeated font measurement at every node radius.
 */
internal fun DrawScope.drawFittedNodeCost(layout: TextLayoutResult, center: Offset, radius: Float) {
    val width = layout.size.width.toFloat()
    val height = layout.size.height.toFloat()
    val diagonal = sqrt(width * width + height * height)
    if (diagonal <= 0f || radius <= 0f) return
    val innerDiameter = 2f * (radius - maxOf(1.5f, radius * 0.18f)).coerceAtLeast(0f)
    val fit = (innerDiameter / diagonal).coerceAtMost(1f)
    if (fit <= 0f) return
    withTransform({
        translate(center.x, center.y)
        scale(fit, fit, Offset.Zero)
    }) {
        drawText(layout, topLeft = Offset(-width / 2f, -height / 2f))
    }
}
