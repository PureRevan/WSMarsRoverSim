package sfg.wseminar.rover.graph.generator

import sfg.wseminar.rover.graph.Graph
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.ceil

/** Fixed bucket array avoids allocating map keys for every spatial query cell. */
internal class NodeSpatialIndex(nodes: List<Graph.Node>) {
    private val minX = nodes.minOfOrNull { it.x } ?: 0.0
    private val minY = nodes.minOfOrNull { it.y } ?: 0.0
    private val span = maxOf((nodes.maxOfOrNull { it.x } ?: 0.0) - minX,
        (nodes.maxOfOrNull { it.y } ?: 0.0) - minY).coerceAtLeast(1e-9)
    private val side = ceil(sqrt(nodes.size.toDouble().coerceAtLeast(1.0))).toInt() + 1
    private val cell = span / (side - 1)
    private val buckets = Array(side * side) { mutableListOf<Graph.Node>() }
    init { for (node in nodes) buckets[y(node.y) * side + x(node.x)].add(node) }
    private fun x(value: Double) = floor((value - minX) / cell).toInt().coerceIn(0, side - 1)
    private fun y(value: Double) = floor((value - minY) / cell).toInt().coerceIn(0, side - 1)
    fun query(left: Double, top: Double, right: Double, bottom: Double): Sequence<Graph.Node> = sequence {
        for (by in y(top)..y(bottom)) for (bx in x(left)..x(right)) {
            for (node in buckets[by * side + bx]) yield(node)
        }
    }
    suspend fun clears(from: Graph.Node, to: Graph.Node, clearance: Double, work: GenerationWork): Boolean {
        if (clearance <= 0.0) return true
        for (node in query(minOf(from.x, to.x) - clearance, minOf(from.y, to.y) - clearance,
            maxOf(from.x, to.x) + clearance, maxOf(from.y, to.y) + clearance)) {
            if (work.add()) work.pause()
            if (node !== from && node !== to && GraphGeometry.distancePointToSegment(node, from, to) < clearance) return false
        }
        return true
    }
}
