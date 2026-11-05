package sfg.wseminar.rover.algorithms

import sfg.wseminar.rover.algorithms.backend.AlgorithmContext
import sfg.wseminar.rover.algorithms.backend.AlgorithmStep
import sfg.wseminar.rover.graph.Graph
import kotlin.math.hypot

/** Reactive graph controller: deliberately does not plan around local minima. */
object GoToWaypoint : RoverAlgorithm(), LocalAlgorithm, GlobalAlgorithm, DiscreteAlgorithm, ContinuousAlgorithm {
    override val name = "Go to Waypoint"
    override val description = "Choose a destination, then Send waypoint. The rover follows locally closer neighbors; it may need an intermediate waypoint to get around a dead end."

    /** Shared by command validation and execution; callers supply only sensed outgoing edges. */
    fun nextEdge(current: Graph.Node, target: Graph.Node, outgoing: Iterable<Graph.Edge>): Graph.Edge? {
        fun distance(node: Graph.Node) = hypot(node.x - target.x, node.y - target.y)
        val currentDistance = distance(current)
        return outgoing.filter { distance(it.neighborOf(current)) < currentDistance || it.neighborOf(current) === target }
            .minWithOrNull(compareBy<Graph.Edge> { distance(it.neighborOf(current)) }
                .thenBy { it.weight }.thenBy { it.id })
    }

    override fun run(context: AlgorithmContext): Sequence<AlgorithmStep> = sequence {
        context.use {
            requireNonNegativeWeights()
            var current = startNode
            var cost = 0.0
            nodeCosts[current] = cost
            visitedNodes.add(current)
            physicallyVisitedNodes.add(current)
            if (current === goalNode) completeTravel()
            snapshot(if (current === goalNode) "Already at waypoint ${goalNode.label}." else
                "Waypoint accepted: ${current.label} → ${goalNode.label}. Following locally closer connections.")
            while (current !== goalNode) {
                val edge = nextEdge(current, goalNode, knownOutgoingEdges(current))
                if (edge == null) {
                    snapshot("Waiting at ${current.label}: no outgoing neighbor is closer to ${goalNode.label}. Select an intermediate waypoint; this does not mean that no route exists.")
                    break
                }
                current = edge.neighborOf(current)
                moveTo(current)
                // Every commandable frame includes the neighborhood within the selected sensing radius.
                observe(current)
                visitedNodes.add(current)
                cost += edge.weight
                nodeCosts[current] = cost
                edgeCosts[edge] = edge.weight
                appendTravel(edge, complete = current === goalNode)
                snapshot(if (current === goalNode)
                    "Waypoint ${goalNode.label} reached. Travel cost $cost. Select another destination to continue."
                else "Moved to ${current.label} toward ${goalNode.label}; sensed nearby connections. Travel cost $cost.")
            }
        }
    }
}
