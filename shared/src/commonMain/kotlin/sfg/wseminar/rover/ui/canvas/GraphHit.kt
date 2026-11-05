package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.Graph.Edge
import sfg.wseminar.rover.graph.Graph.Node
import sfg.wseminar.rover.ui.states.ViewportState
import sfg.wseminar.rover.ui.states.GraphAppearance

sealed interface GraphHit {
    data class NodeHit(val node: Node) : GraphHit
    data class EdgeHit(val edge: Edge) : GraphHit
    data object Empty : GraphHit

    companion object {
        fun find(
            graph: Graph,
            viewport: ViewportState,
            canvasSize: IntSize,
            tapPosition: Offset,
            appearance: GraphAppearance = GraphAppearance(),
            highlightedNodes: Set<Node> = emptySet(),
            visibleNode: (Node) -> Boolean = { true },
            visibleEdge: (Edge) -> Boolean = { true },
            geometry: CanvasGeometry = CanvasGeometry(graph, viewport, canvasSize)
        ): GraphHit {
            val canvasWidth = canvasSize.width.toFloat()
            val canvasHeight = canvasSize.height.toFloat()

            val metrics = graphDisplayMetrics(graph, viewport, canvasWidth, canvasHeight, appearance)
            val edgeHitDistancePx = (metrics.edgeWidth * 2.5f).coerceAtLeast(3f)

            var nearestNode: Node? = null
            var nodeDistance = Float.POSITIVE_INFINITY
            for (node in graph.nodes) {
                if (!visibleNode(node)) continue
                val distance = (geometry.nodes[node.id] - tapPosition).getDistance()
                val radius = (metrics.radius(node in highlightedNodes) * 18f / 10.5f).coerceAtLeast(4f)
                if (distance <= radius && distance < nodeDistance) { nearestNode = node; nodeDistance = distance }
            }
            nearestNode?.let { return NodeHit(it) }
            var nearestEdge: Edge? = null
            var edgeDistance = Float.POSITIVE_INFINITY
            for (edge in graph.edges) {
                if (!visibleEdge(edge) || !visibleNode(edge.from) || !visibleNode(edge.to)) continue
                val distance = geometry.edges[edge.id].distance(tapPosition)
                if (distance <= edgeHitDistancePx && distance < edgeDistance) { nearestEdge = edge; edgeDistance = distance }
            }
            return nearestEdge?.let { EdgeHit(it) } ?: Empty
        }
    }
}
