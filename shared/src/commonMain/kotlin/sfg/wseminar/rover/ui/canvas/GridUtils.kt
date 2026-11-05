package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import sfg.wseminar.rover.ui.CanvasConstants
import sfg.wseminar.rover.ui.states.ViewportState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun DrawScope.drawGrid(
    viewport: ViewportState,
    color: Color,
    axisColor: Color
) {
    val worldTopLeft = transformCanvasToWorld(
        canvas = Offset(0f, 0f),
        viewport = viewport,
        canvasWidth = size.width,
        canvasHeight = size.height
    )

    val worldBottomRight = transformCanvasToWorld(
        canvas = Offset(size.width, size.height),
        viewport = viewport,
        canvasWidth = size.width,
        canvasHeight = size.height
    )

    val minWorldX = minOf(worldTopLeft.x, worldBottomRight.x)
    val maxWorldX = maxOf(worldTopLeft.x, worldBottomRight.x)
    val minWorldY = minOf(worldTopLeft.y, worldBottomRight.y)
    val maxWorldY = maxOf(worldTopLeft.y, worldBottomRight.y)

    val visibleWorldSpan = maxOf(
        maxWorldX - minWorldX,
        maxWorldY - minWorldY
    )

    val grid = calculateGridLevels(
        visibleWorldSpan = visibleWorldSpan,
        canvasSpanPx = maxOf(size.width, size.height)
    )

    val axisWorldX = 0.5f
    val axisWorldY = 0.5f

    if (grid.minorAlpha > 0.01f) {
        drawGridLines(
            viewport = viewport,
            minWorldX = minWorldX,
            maxWorldX = maxWorldX,
            minWorldY = minWorldY,
            maxWorldY = maxWorldY,
            axisWorldX = axisWorldX,
            axisWorldY = axisWorldY,
            worldStep = grid.minorStep,
            color = color.copy(alpha = color.alpha * grid.minorAlpha),
            strokeWidth = 0.75f,
            skipAxis = true
        )
    }

    drawGridLines(
        viewport = viewport,
        minWorldX = minWorldX,
        maxWorldX = maxWorldX,
        minWorldY = minWorldY,
        maxWorldY = maxWorldY,
        axisWorldX = axisWorldX,
        axisWorldY = axisWorldY,
        worldStep = grid.majorStep,
        color = color,
        strokeWidth = 1f,
        skipAxis = true
    )

    drawAxisLines(
        viewport = viewport,
        minWorldX = minWorldX,
        maxWorldX = maxWorldX,
        minWorldY = minWorldY,
        maxWorldY = maxWorldY,
        axisWorldX = axisWorldX,
        axisWorldY = axisWorldY,
        axisColor = axisColor
    )
}

private data class GridLevels(
    val majorStep: Float,
    val minorStep: Float,
    val minorAlpha: Float
)

private fun calculateGridLevels(
    visibleWorldSpan: Float,
    canvasSpanPx: Float
): GridLevels {
    if (visibleWorldSpan <= 0f || !visibleWorldSpan.isFinite() || canvasSpanPx <= 0f) {
        return GridLevels(
            majorStep = 0.1f,
            minorStep = 0.02f,
            minorAlpha = 1f
        )
    }

    val targetWorldStep = visibleWorldSpan * CanvasConstants.TargetMajorGridSpacingPx / canvasSpanPx
    val majorStep = chooseNiceStep(targetWorldStep)
    val minorStep = majorStep / 5f

    val minorSpacingPx = minorStep / visibleWorldSpan * canvasSpanPx

    val minorAlpha = ((minorSpacingPx - CanvasConstants.MinimumMinorGridSpacingPx) / CanvasConstants.MinimumMinorGridSpacingPx)
        .coerceIn(0f, 1f)

    return GridLevels(
        majorStep = majorStep,
        minorStep = minorStep,
        minorAlpha = minorAlpha
    )
}

private fun chooseNiceStep(
    targetStep: Float
): Float {
    if (targetStep <= 0f || !targetStep.isFinite()) {
        return 0.1f
    }

    val exponent = floor(log10(targetStep))
    val base = 10f.pow(exponent)
    val normalized = targetStep / base

    val niceNormalized = when {
        normalized <= 1f -> 1f
        normalized <= 2f -> 2f
        normalized <= 5f -> 5f
        else -> 10f
    }

    return niceNormalized * base
}

private fun DrawScope.drawGridLines(
    viewport: ViewportState,
    minWorldX: Float,
    maxWorldX: Float,
    minWorldY: Float,
    maxWorldY: Float,
    axisWorldX: Float,
    axisWorldY: Float,
    worldStep: Float,
    color: Color,
    strokeWidth: Float,
    skipAxis: Boolean
) {
    val firstVerticalIndex = floor((minWorldX - axisWorldX) / worldStep).toInt() - 1
    val lastVerticalIndex = ceil((maxWorldX - axisWorldX) / worldStep).toInt() + 1

    for (index in firstVerticalIndex..lastVerticalIndex) {
        if (skipAxis && index == 0) continue

        val worldX = axisWorldX + index * worldStep

        val start = transformWorldToCanvas(
            world = Offset(worldX, minWorldY),
            viewport = viewport,
            canvasWidth = size.width,
            canvasHeight = size.height
        )

        val end = transformWorldToCanvas(
            world = Offset(worldX, maxWorldY),
            viewport = viewport,
            canvasWidth = size.width,
            canvasHeight = size.height
        )

        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = strokeWidth
        )
    }

    val firstHorizontalIndex = floor((minWorldY - axisWorldY) / worldStep).toInt() - 1
    val lastHorizontalIndex = ceil((maxWorldY - axisWorldY) / worldStep).toInt() + 1

    for (index in firstHorizontalIndex..lastHorizontalIndex) {
        if (skipAxis && index == 0) continue

        val worldY = axisWorldY + index * worldStep

        val start = transformWorldToCanvas(
            world = Offset(minWorldX, worldY),
            viewport = viewport,
            canvasWidth = size.width,
            canvasHeight = size.height
        )

        val end = transformWorldToCanvas(
            world = Offset(maxWorldX, worldY),
            viewport = viewport,
            canvasWidth = size.width,
            canvasHeight = size.height
        )

        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = strokeWidth
        )
    }
}

private fun DrawScope.drawAxisLines(
    viewport: ViewportState,
    minWorldX: Float,
    maxWorldX: Float,
    minWorldY: Float,
    maxWorldY: Float,
    axisWorldX: Float,
    axisWorldY: Float,
    axisColor: Color
) {
    val verticalStart = transformWorldToCanvas(
        world = Offset(axisWorldX, minWorldY),
        viewport = viewport,
        canvasWidth = size.width,
        canvasHeight = size.height
    )

    val verticalEnd = transformWorldToCanvas(
        world = Offset(axisWorldX, maxWorldY),
        viewport = viewport,
        canvasWidth = size.width,
        canvasHeight = size.height
    )

    drawLine(
        color = axisColor,
        start = verticalStart,
        end = verticalEnd,
        strokeWidth = 1.8f
    )

    val horizontalStart = transformWorldToCanvas(
        world = Offset(minWorldX, axisWorldY),
        viewport = viewport,
        canvasWidth = size.width,
        canvasHeight = size.height
    )

    val horizontalEnd = transformWorldToCanvas(
        world = Offset(maxWorldX, axisWorldY),
        viewport = viewport,
        canvasWidth = size.width,
        canvasHeight = size.height
    )

    drawLine(
        color = axisColor,
        start = horizontalStart,
        end = horizontalEnd,
        strokeWidth = 1.8f
    )
}