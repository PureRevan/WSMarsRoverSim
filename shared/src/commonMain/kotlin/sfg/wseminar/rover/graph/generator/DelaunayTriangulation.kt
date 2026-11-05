package sfg.wseminar.rover.graph.generator

import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.GraphGeometry.orderedPair
import kotlin.math.abs
import kotlin.math.max

internal object DelaunayTriangulation {
    suspend fun undirectedEdges(
        nodes: List<Graph.Node>, work: GenerationWork
    ): List<Pair<Int, Int>> {
        if (nodes.size < 2) return emptyList()
        if (nodes.size == 2) return listOf(orderedPair(0, 1))

        val triangles = bowyerWatson(nodes, work)
        val edges = linkedSetOf<Pair<Int, Int>>()

        triangles.forEach { triangle ->
            edges += orderedPair(triangle.a, triangle.b)
            edges += orderedPair(triangle.b, triangle.c)
            edges += orderedPair(triangle.c, triangle.a)
        }

        if (edges.isEmpty()) {
            return fallbackStar(nodes)
        }

        return edges.toList()
    }

    private fun fallbackStar(
        nodes: List<Graph.Node>
    ): List<Pair<Int, Int>> =
        nodes.sortedWith(compareBy<Graph.Node> { it.x }.thenBy { it.y }).zipWithNext { a, b -> orderedPair(a.id, b.id) }

    private data class Triangle(
        val a: Int,
        val b: Int,
        val c: Int
    )

    private suspend fun bowyerWatson(
        nodes: List<Graph.Node>, work: GenerationWork
    ): List<Triangle> {
        val minX = nodes.minOf { it.x }
        val minY = nodes.minOf { it.y }
        val maxX = nodes.maxOf { it.x }
        val maxY = nodes.maxOf { it.y }

        val span = max(maxX - minX, maxY - minY).coerceAtLeast(1.0)
        val midX = (minX + maxX) / 2.0
        val midY = (minY + maxY) / 2.0
        val size = span * 20.0

        val superA = Graph.Node(x = midX, y = midY - size, id = nodes.size)
        val superB = Graph.Node(x = midX - size, y = midY + size, id = nodes.size + 1)
        val superC = Graph.Node(x = midX + size, y = midY + size, id = nodes.size + 2)
        val vertices = nodes + superA + superB + superC

        val superIndices = setOf(superA.id, superB.id, superC.id)
        var triangles = mutableListOf(
            Triangle(superA.id, superB.id, superC.id)
        )

        nodes.forEach { node ->
            if (work.add(triangles.size)) work.pause()
            val bad = triangles.filter { triangle ->
                inCircumcircle(vertices[triangle.a], vertices[triangle.b], vertices[triangle.c], node)
            }

            val polygon = linkedSetOf<Pair<Int, Int>>()

            bad.forEach { triangle ->
                toggleEdge(polygon, triangle.a, triangle.b)
                toggleEdge(polygon, triangle.b, triangle.c)
                toggleEdge(polygon, triangle.c, triangle.a)
            }

            val badSet = bad.toHashSet()
            triangles = triangles.filterNot { it in badSet }.toMutableList()

            polygon.forEach { (from, to) ->
                triangles += Triangle(from, to, node.id)
            }
        }

        return triangles.filter { triangle ->
            triangle.a !in superIndices &&
                    triangle.b !in superIndices &&
                    triangle.c !in superIndices
        }
    }

    private fun toggleEdge(
        edges: MutableSet<Pair<Int, Int>>,
        from: Int,
        to: Int
    ) {
        val edge = orderedPair(from, to)

        if (!edges.add(edge)) {
            edges.remove(edge)
        }
    }

    private fun inCircumcircle(
        a: Graph.Node,
        b: Graph.Node,
        c: Graph.Node,
        d: Graph.Node
    ): Boolean {
        var ax = a.x
        var ay = a.y
        var bx = b.x
        var by = b.y
        var cx = c.x
        var cy = c.y

        val ccw = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

        if (abs(ccw) < GraphGeometry.Epsilon) {
            return false
        }

        if (ccw < 0.0) {
            val swapX = bx
            val swapY = by
            bx = cx
            by = cy
            cx = swapX
            cy = swapY
        }

        val dx = d.x
        val dy = d.y

        val adx = ax - dx
        val ady = ay - dy
        val bdx = bx - dx
        val bdy = by - dy
        val cdx = cx - dx
        val cdy = cy - dy

        val abdet = adx * bdy - bdx * ady
        val bcdet = bdx * cdy - cdx * bdy
        val cadet = cdx * ady - adx * cdy
        val alift = adx * adx + ady * ady
        val blift = bdx * bdx + bdy * bdy
        val clift = cdx * cdx + cdy * cdy

        return alift * bcdet + blift * cadet + clift * abdet > 0.0
    }
}
