package sfg.wseminar.rover.simulation

import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.Graph.Companion.StartGoalSelector
import sfg.wseminar.rover.graph.IDLabeled
import sfg.wseminar.rover.graph.IDSet
import sfg.wseminar.rover.algorithms.backend.KnowledgeMode

/** Creates fresh display state over existing topology; never generates a graph. */
object InitialSimulationFrameFactory {
    fun create(
        graph: Graph,
        knowledgeMode: KnowledgeMode,
        sensingRadius: Int = SensingRadius.DEFAULT
    ): SimulationFrame {
        require(sensingRadius in SensingRadius.range)
        val startNode = StartGoalSelector.selectStart(graph) ?: graph.nodes.first()
        val goalNode = StartGoalSelector.selectGoal(graph) ?: graph.nodes.last()
        val knownNodes = IDSet.create<Graph.Node>(graph.nodes.size)
        val knownEdges = IDSet.create<Graph.Edge>(graph.edges.size)
        val visitedNodes = IDSet.create<Graph.Node>(graph.nodes.size)

        if (knowledgeMode == KnowledgeMode.GLOBAL) {
            knownNodes.addAll(graph.nodes)
            knownEdges.addAll(graph.edges)
        } else {
            visitedNodes.add(startNode)
            NeighborhoodSensor(graph).reveal(startNode, sensingRadius,
                { knownNodes.add(it) }, { knownEdges.add(it) })
        }

        val nodeCosts = DoubleArray(graph.nodes.size) { Double.POSITIVE_INFINITY }
        val edgeCosts = DoubleArray(graph.edges.size) { Double.POSITIVE_INFINITY }

        nodeCosts[startNode.id] = 0.0

        graph.edges.filter { it in knownEdges }.forEach { edge ->
            edgeCosts[edge.id] = edge.weight
        }

        val initialCosts = CostSnapshot.of(nodeCosts)
        return SimulationFrame(
            graph = graph,
            stepIndex = 0,
            roverNode = startNode,
            startNode = startNode,
            goalNode = goalNode,
            knownNodes = knownNodes.snapshot(),
            knownEdges = knownEdges.snapshot(),
            visitedNodes = IDSet.create<Graph.Node>(graph.nodes.size).snapshot(),
            physicallyVisitedNodes = visitedNodes.snapshot(),
            frontierNodes = IDSet.create<Graph.Node>(graph.nodes.size).snapshot(),
            currentPathNodes = listOf(startNode),
            finalPathNodes = emptyList(),
            nodeCosts = initialCosts,
            nodeCostFields = mapOf("g" to initialCosts),
            edgeCosts = CostSnapshot.of(edgeCosts),
            message = when (knowledgeMode) {
                KnowledgeMode.GLOBAL -> "Ready with complete prior knowledge."
                KnowledgeMode.LOCAL -> "Ready with only the start neighborhood revealed."
            }
        )
    }

    private fun <T : IDLabeled> IDSet<T>.addAll(
        elements: Iterable<T>
    ) {
        elements.forEach(::add)
    }
}
