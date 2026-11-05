package sfg.wseminar.rover.algorithms.backend

import sfg.wseminar.rover.graph.Graph

/** Stores the actual incoming edge, including direction and parallel-edge identity. */
class TrackedPredecessors internal constructor(private val context: AlgorithmContext) {
    private val edges = arrayOfNulls<Graph.Edge>(context.graph.nodes.size)

    operator fun get(node: Graph.Node): Graph.Edge? = edges[node.id]

    operator fun set(node: Graph.Node, edge: Graph.Edge) {
        require(edge.to === node || (edge.bidirectional && edge.from === node)) {
            "The predecessor edge must lead to ${node.label}."
        }

        edges[node.id] = edge
        context.previousNode[node.id] = edge.neighborOf(node)
        context.edgeCosts[edge] = edge.weight
        context.currentPathTo(node)
    }

    fun clear(node: Graph.Node) {
        edges[node.id] = null
        context.previousNode[node.id] = null
        context.currentPathTo(node)
    }
}
