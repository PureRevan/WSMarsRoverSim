package sfg.wseminar.rover

import sfg.wseminar.rover.graph.Graph.Edge
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.Graph.Node
import sfg.wseminar.rover.graph.IDSet
import sfg.wseminar.rover.simulation.CostSnapshot


data class SimulationFrame(
    val graph: Graph,
    val stepIndex: Int,
    val roverNode: Node?,
    val startNode: Node?,
    val goalNode: Node?,
    val knownNodes: IDSet<Node>,
    val knownEdges: IDSet<Edge>,
    val visitedNodes: IDSet<Node>,
    val frontierNodes: IDSet<Node>,
    val currentPathNodes: List<Node>,
    val finalPathNodes: List<Node>,
    val nodeCosts: CostSnapshot,
    val edgeCosts: CostSnapshot,
    val message: String,
    val nodeCostFields: Map<String, CostSnapshot> = emptyMap(),
    val currentPathEdges: List<Edge> = emptyList(),
    val finalPathEdges: List<Edge> = emptyList(),
    val physicallyVisitedNodes: IDSet<Node> = visitedNodes,
    val costMeaning: String = "Cost from start",
    val runSeed: Int = graph.seed
) {
    fun safeCopy() = copy(
            knownNodes = knownNodes.copy(),
            knownEdges = knownEdges.copy(),
            visitedNodes = visitedNodes.copy(),
            frontierNodes = frontierNodes.copy(),
            currentPathNodes = currentPathNodes.toList(),
            finalPathNodes = finalPathNodes.toList(),
        )

}
