package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.ui.states.ViewportState

/** Identical polylines are used for drawing, path highlights, labels and hit testing. */
data class EdgeGeometry(val start: Offset, val bend: Offset, val end: Offset) {
    fun onScreen(width: Float, height: Float, margin: Float): Boolean =
        maxOf(start.x, bend.x, end.x) >= -margin && minOf(start.x, bend.x, end.x) <= width + margin &&
        maxOf(start.y, bend.y, end.y) >= -margin && minOf(start.y, bend.y, end.y) <= height + margin
    fun distance(point: Offset): Float = minOf(distanceToSegment(point, start, bend), distanceToSegment(point, bend, end))
}

class CanvasGeometry(graph: Graph, viewport: ViewportState, size: IntSize) {
    val nodes: List<Offset> = graph.nodes.map {
        transformWorldToCanvas(Offset(it.x.toFloat(), it.y.toFloat()), viewport, size.width.toFloat(), size.height.toFloat())
    }
    val edges: List<EdgeGeometry> = graph.edges.map { edge ->
        val from = nodes[edge.from.id]
        val to = nodes[edge.to.id]
        val direction = (to - from) * if (edge.from.id <= edge.to.id) 1f else -1f
        val length = direction.getDistance().coerceAtLeast(1f)
        val perpendicular = Offset(-direction.y, direction.x) / length
        EdgeGeometry(from, (from + to) / 2f + perpendicular * (graph.edgeLane(edge).toFloat() * 18f), to)
    }
}

class CanvasGeometryCache {
    private var graph: Graph? = null
    private var viewport: ViewportState? = null
    private var size: IntSize? = null
    private var geometry: CanvasGeometry? = null
    fun get(graph: Graph, viewport: ViewportState, size: IntSize): CanvasGeometry {
        if (this.graph !== graph || this.viewport != viewport || this.size != size) {
            geometry = CanvasGeometry(graph, viewport, size)
            this.graph = graph; this.viewport = viewport; this.size = size
        }
        return checkNotNull(geometry)
    }
}
