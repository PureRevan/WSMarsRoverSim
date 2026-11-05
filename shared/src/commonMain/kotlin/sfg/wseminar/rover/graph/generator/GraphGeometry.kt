package sfg.wseminar.rover.graph.generator

import sfg.wseminar.rover.graph.Graph
import kotlin.math.abs
import kotlin.math.hypot

internal object GraphGeometry {
    const val Epsilon = 1e-10

    fun distance(
        first: Graph.Node,
        second: Graph.Node
    ): Double =
        hypot(first.x - second.x, first.y - second.y)

    fun distance(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double
    ): Double =
        hypot(x1 - x2, y1 - y2)

    fun orderedPair(
        first: Int,
        second: Int
    ): Pair<Int, Int> =
        if (first < second) first to second else second to first

    fun segmentsProperlyIntersect(
        a: Graph.Node,
        b: Graph.Node,
        c: Graph.Node,
        d: Graph.Node
    ): Boolean {
        if (sharesEndpoint(a, b, c, d)) {
            return false
        }

        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)

        if (o1 * o2 < -Epsilon && o3 * o4 < -Epsilon) {
            return true
        }

        return collinearOverlap(a, b, c, d, o1, o2, o3, o4)
    }

    fun distancePointToSegment(
        point: Graph.Node,
        start: Graph.Node,
        end: Graph.Node
    ): Double {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared < Epsilon) {
            return distance(point, start)
        }

        val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared)
            .coerceIn(0.0, 1.0)

        return distance(
            point.x,
            point.y,
            start.x + t * dx,
            start.y + t * dy
        )
    }

    fun edgeClearsOtherNodes(
        fromIndex: Int,
        toIndex: Int,
        nodes: List<Graph.Node>,
        clearance: Double
    ): Boolean {
        val from = nodes[fromIndex]
        val to = nodes[toIndex]

        for (index in nodes.indices) {
            if (index == fromIndex || index == toIndex) continue

            if (distancePointToSegment(nodes[index], from, to) < clearance) {
                return false
            }
        }

        return true
    }

    private fun sharesEndpoint(
        a: Graph.Node,
        b: Graph.Node,
        c: Graph.Node,
        d: Graph.Node
    ): Boolean =
        a === c || a === d || b === c || b === d

    private fun orientation(
        p: Graph.Node,
        q: Graph.Node,
        r: Graph.Node
    ): Double =
        (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)

    private fun collinearOverlap(
        a: Graph.Node,
        b: Graph.Node,
        c: Graph.Node,
        d: Graph.Node,
        o1: Double,
        o2: Double,
        o3: Double,
        o4: Double
    ): Boolean {
        fun between(
            p: Graph.Node,
            q: Graph.Node,
            r: Graph.Node
        ): Boolean =
            q.x <= maxOf(p.x, r.x) + Epsilon &&
                    q.x >= minOf(p.x, r.x) - Epsilon &&
                    q.y <= maxOf(p.y, r.y) + Epsilon &&
                    q.y >= minOf(p.y, r.y) - Epsilon &&
                    distancePointToSegment(q, p, r) < Epsilon * 8.0

        if (abs(o1) <= Epsilon && between(a, c, b) && c !== a && c !== b) return true
        if (abs(o2) <= Epsilon && between(a, d, b) && d !== a && d !== b) return true
        if (abs(o3) <= Epsilon && between(c, a, d) && a !== c && a !== d) return true
        if (abs(o4) <= Epsilon && between(c, b, d) && b !== c && b !== d) return true

        return false
    }
}
