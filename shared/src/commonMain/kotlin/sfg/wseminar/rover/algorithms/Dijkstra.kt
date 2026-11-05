package sfg.wseminar.rover.algorithms

import sfg.wseminar.rover.algorithms.backend.AlgorithmContext
import sfg.wseminar.rover.algorithms.backend.AlgorithmStep

object Dijkstra : RoverAlgorithm(), GlobalAlgorithm, DiscreteAlgorithm, ContinuousAlgorithm {
    override fun run(context: AlgorithmContext): Sequence<AlgorithmStep> = sequence {
        context.use {
            requireNonNegativeWeights()

            val distance = nodeCosts
            val previous = predecessors
            val queue = PriorityQueue<Double>()

            distance[startNode] = 0.0
            queue[startNode] = 0.0
            snapshot("Dijkstra initialized at ${startNode.label}.")

            while (queue.isNotEmpty()) {
                val current = queue.removeMin()
                snapshot("Visiting ${current.label} with cost ${distance[current]}.")

                if (current === goalNode) break

                for (edge in graph.outgoingEdges(current)) {
                    val neighbor = edge.neighborOf(current)

                    val candidate = distance[current] + edge.weight

                    if (candidate < distance[neighbor]) {
                        distance[neighbor] = candidate
                        previous[neighbor] = edge
                        queue[neighbor] = candidate
                        snapshot("Relaxed ${edge.label}: ${neighbor.label} now costs $candidate.")
                    }
                }
            }

            finalPathTo(goalNode)
            snapshot(if (distance[goalNode].isFinite()) {
                "Shortest path found with total cost ${distance[goalNode]}."
            } else {
                "No path from ${startNode.label} to ${goalNode.label} was found."
            })
        }
    }
}
