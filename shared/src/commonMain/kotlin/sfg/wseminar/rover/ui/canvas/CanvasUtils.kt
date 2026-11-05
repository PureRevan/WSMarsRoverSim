package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.ui.CanvasConstants.MaxZoom
import sfg.wseminar.rover.ui.CanvasConstants.MaxWheelStepsPerEvent
import sfg.wseminar.rover.ui.CanvasConstants.MinZoom
import sfg.wseminar.rover.ui.CanvasConstants.PanSpeed
import sfg.wseminar.rover.ui.CanvasConstants.WheelZoomSensitivity
import sfg.wseminar.rover.ui.states.ViewportState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min


const val EPSILON = 1e-12
const val EPSILON_F = 1e-5f


fun transformWorldToCanvas(
    world: Offset,
    viewport: ViewportState,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val canvasCenter = Offset(
        x = canvasWidth / 2f,
        y = canvasHeight / 2f
    )

    val scale = minOf(canvasWidth, canvasHeight) * viewport.zoom
    return canvasCenter + (world - Offset(0.5f, 0.5f)) * scale + Offset(viewport.panX, viewport.panY)
}

fun transformCanvasToWorld(
    canvas: Offset,
    viewport: ViewportState,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val canvasCenter = Offset(
        x = canvasWidth / 2f,
        y = canvasHeight / 2f
    )

    val pan = Offset(
        x = viewport.panX,
        y = viewport.panY
    )

    val scale = (minOf(canvasWidth, canvasHeight) * viewport.zoom).coerceAtLeast(1e-12f)
    return Offset(0.5f, 0.5f) + (canvas - canvasCenter - pan) / scale
}

fun calculateWheelZoomFactor(
    scrollDeltaY: Float
): Float {
    if (!scrollDeltaY.isFinite()) return 1f
    val normalizedDelta = scrollDeltaY.coerceIn(-MaxWheelStepsPerEvent, MaxWheelStepsPerEvent)
    return exp(-normalizedDelta * WheelZoomSensitivity)
}

/** Convert DOM wheel units into approximate mouse-wheel steps, retaining trackpad fractions. */
internal fun normalizeBrowserWheelDelta(deltaY: Float, deltaMode: Int): Float = when (deltaMode) {
    1 -> deltaY / 3f // DOM_DELTA_LINE: commonly three lines per wheel step.
    2 -> deltaY // DOM_DELTA_PAGE: treat a page-scroll action as one step.
    else -> deltaY / 100f // DOM_DELTA_PIXEL: CSS pixels, independent of display density.
}

fun calculateCursorAnchoredViewport(
    oldViewport: ViewportState,
    cursorPosition: Offset,
    zoomFactor: Float,
    canvasWidth: Float,
    canvasHeight: Float
): ViewportState {
    val oldZoom = oldViewport.zoom
    val newZoom = (oldZoom * zoomFactor).coerceIn(MinZoom, MaxZoom)

    if (abs(oldZoom - newZoom) < oldZoom * EPSILON_F) {
        return oldViewport
    }

    val actualZoomFactor = newZoom / oldZoom

    val canvasCenter = Offset(
        x = canvasWidth / 2f,
        y = canvasHeight / 2f
    )

    val oldPan = Offset(
        x = oldViewport.panX,
        y = oldViewport.panY
    )

    val newPan = cursorPosition -
            canvasCenter -
            (cursorPosition - canvasCenter - oldPan) * actualZoomFactor

    return oldViewport.copy(
        zoom = newZoom,
        panX = newPan.x,
        panY = newPan.y
    )
}

fun calculateCentroidAnchoredViewport(
    oldViewport: ViewportState,
    centroid: Offset,
    pan: Offset,
    zoomFactor: Float,
    canvasWidth: Float,
    canvasHeight: Float
): ViewportState {
    val zoomedViewport = calculateCursorAnchoredViewport(
        oldViewport = oldViewport,
        cursorPosition = centroid,
        zoomFactor = zoomFactor,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight
    )

    return zoomedViewport.copy(
        panX = zoomedViewport.panX + pan.x * PanSpeed,
        panY = zoomedViewport.panY + pan.y * PanSpeed
    )
}

fun calculateFitViewport(
    graph: Graph,
    canvasSize: IntSize,
    paddingFraction: Float = 0.18f
): ViewportState {
    if (graph.nodes.isEmpty() || canvasSize == IntSize.Zero) {
        return ViewportState()
    }

    val effectivePadding = when {
        graph.nodes.size >= 2_000 -> 0.06f
        graph.nodes.size >= 800 -> 0.09f
        else -> paddingFraction
    }

    val minX = graph.minX.toFloat()
    val maxX = graph.maxX.toFloat()
    val minY = graph.minY.toFloat()
    val maxY = graph.maxY.toFloat()

    val graphWidth = max(maxX - minX, 0.001f)
    val graphHeight = max(maxY - minY, 0.001f)

    val availableWidth = 1f - effectivePadding * 2f
    val availableHeight = 1f - effectivePadding * 2f

    val baseScale = minOf(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(1f)
    val zoomX = availableWidth * canvasSize.width / (graphWidth * baseScale)
    val zoomY = availableHeight * canvasSize.height / (graphHeight * baseScale)

    val zoom = min(zoomX, zoomY).coerceIn(MinZoom, MaxZoom)

    val graphCenterWorld = Offset(
        x = (minX + maxX) / 2f,
        y = (minY + maxY) / 2f
    )

    val pan = (Offset(0.5f, 0.5f) - graphCenterWorld) * baseScale * zoom

    return ViewportState(
        zoom = zoom,
        panX = pan.x,
        panY = pan.y
    )
}

fun distanceToSegment(
    point: Offset,
    start: Offset,
    end: Offset
): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y

    if (abs(dx) < EPSILON_F && abs(dy) < EPSILON_F) {
        return (point - start).getDistance()
    }

    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy))
        .coerceIn(0f, 1f)

    val projection = Offset(
        x = start.x + t * dx,
        y = start.y + t * dy
    )

    return (point - projection).getDistance()
}
