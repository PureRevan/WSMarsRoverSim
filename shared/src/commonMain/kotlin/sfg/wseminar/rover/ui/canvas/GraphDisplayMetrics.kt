package sfg.wseminar.rover.ui.canvas

import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.ui.states.GraphAppearance
import sfg.wseminar.rover.ui.states.ViewportState
import kotlin.math.sqrt

data class GraphDisplayMetrics(
    val nodeRadius: Float,
    val edgeWidth: Float,
    val showLabels: Boolean,
    val estimatedSpacing: Float
) {
    fun radius(important: Boolean): Float = if (important) nodeRadius.coerceAtLeast(3f) else nodeRadius
}

/** O(1) density estimate over cached graph bounds, shared by rendering and hit testing. */
fun graphDisplayMetrics(
    graph: Graph,
    viewport: ViewportState,
    canvasWidth: Float,
    canvasHeight: Float,
    appearance: GraphAppearance
): GraphDisplayMetrics {
    val options = appearance.constrained()
    val spacing = if (graph.nodes.size < 2) Float.POSITIVE_INFINITY else {
        val width = (graph.maxX - graph.minX).toFloat() * minOf(canvasWidth, canvasHeight) * viewport.zoom
        val height = (graph.maxY - graph.minY).toFloat() * minOf(canvasWidth, canvasHeight) * viewport.zoom
        if (width <= 0f || height <= 0f) maxOf(width, height) / (graph.nodes.size - 1)
        else sqrt(width * height / graph.nodes.size)
    }
    val adaptive = if (options.adaptiveSizing) (spacing / 42f).coerceIn(0.06f, 1f) else 1f
    // Keep overview behavior unchanged. Once there is ample space, let nodes grow
    // with screen spacing; cap growth so close-ups do not become oversized disks.
    val closeZoom = if (!options.enlargeNodesAtCloseZoom) 1f else
        (if (spacing.isFinite()) spacing / 144f else viewport.zoom).coerceIn(1f, 3f)
    return GraphDisplayMetrics(
        nodeRadius = (10.5f * options.nodeScale * adaptive * closeZoom).coerceAtLeast(0.6f),
        edgeWidth = (3f * options.edgeScale * adaptive * sqrt(closeZoom)).coerceAtLeast(0.35f),
        showLabels = !options.hideCrowdedLabels || spacing >= options.labelRevealSpacing * options.labelScale,
        estimatedSpacing = spacing
    )
}
