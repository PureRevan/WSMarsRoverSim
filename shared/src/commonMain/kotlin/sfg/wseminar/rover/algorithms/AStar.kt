package sfg.wseminar.rover.algorithms

import sfg.wseminar.rover.algorithms.backend.AlgorithmContext
import sfg.wseminar.rover.algorithms.backend.AlgorithmStep
import sfg.wseminar.rover.graph.Graph
import kotlin.math.hypot

object AStar : RoverAlgorithm(), GlobalAlgorithm, DiscreteAlgorithm, ContinuousAlgorithm {
    override val name: String = "A*"

    override fun run(context: AlgorithmContext): Sequence<AlgorithmStep> = sequence {
        context.use {
            requireNonNegativeWeights()

            val g = nodeCosts
            val h = Costs("h")
            val f = Costs("f")
            val previous = predecessors
            val open = PriorityQueue<Double>()

            // The minimum cost per unit distance makes h consistent even when
            // weights differ from geometric lengths. A zero scale gives Dijkstra.
            val scale = graph.heuristicScale()
            for (node in graph.nodes) {
                h[node] = if (scale == 0.0) 0.0 else scale * distance(node, goalNode)
            }

            g[startNode] = 0.0
            f[startNode] = h[startNode]
            open[startNode] = f[startNode]
            snapshot("A* initialized at ${startNode.label}.")

            while (open.isNotEmpty()) {
                val current = open.removeMin()
                snapshot("Visiting ${current.label}: path cost ${g[current]}, estimated total ${f[current]}.")
                if (current === goalNode) break

                for (edge in graph.outgoingEdges(current)) {
                    val neighbor = edge.neighborOf(current)
                    val candidate = g[current] + edge.weight
                    if (candidate < g[neighbor]) {
                        g[neighbor] = candidate
                        previous[neighbor] = edge
                        f[neighbor] = candidate + h[neighbor]
                        open[neighbor] = f[neighbor]
                        snapshot("Relaxed ${edge.label}: path cost $candidate, estimated total ${f[neighbor]}.")
                    }
                }
            }

            finalPathTo(goalNode)
            snapshot(if (g[goalNode].isFinite()) {
                "Shortest path found with total cost ${g[goalNode]}."
            } else {
                "No path from ${startNode.label} to ${goalNode.label} was found."
            })
        }
    }

    private fun distance(from: Graph.Node, to: Graph.Node): Double =
        hypot(from.x - to.x, from.y - to.y)

    private fun Graph.heuristicScale(): Double {
        if (nodes.any { !it.x.isFinite() || !it.y.isFinite() }) return 0.0

        var scale = Double.POSITIVE_INFINITY

        for (edge in edges) {
            val length = distance(edge.from, edge.to)
            if (!length.isFinite()) return 0.0
            if (length > 0.0) scale = minOf(scale, edge.weight / length)
        }

        return if (scale.isFinite()) scale else 0.0
    }
}
