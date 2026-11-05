package sfg.wseminar.rover.algorithms.backend

import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.algorithms.LocalAlgorithm
import sfg.wseminar.rover.simulation.SensingRadius
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.SimulationFrame


object AlgorithmRunner {
    fun createTimeline(
        algorithm: RoverAlgorithm,
        graph: Graph,
        startNode: Graph.Node,
        goalNode: Graph.Node,
        knowledgeMode: KnowledgeMode = if (algorithm is LocalAlgorithm) KnowledgeMode.LOCAL else KnowledgeMode.GLOBAL,
        seed: Int = 0,
        initialKnowledge: SimulationFrame? = null,
        limits: SimulationLimits = SimulationLimits(),
        retainHistory: Boolean = true,
        sensingRadius: Int = SensingRadius.DEFAULT
    ): CachedSimulationTimeline {
        require(graph.nodes.getOrNull(startNode.id) === startNode)
        require(graph.nodes.getOrNull(goalNode.id) === goalNode)
        val context = AlgorithmContext(
            algorithm = algorithm,
            graph = graph,
            startNode = startNode,
            goalNode = goalNode,
            knowledgeMode = knowledgeMode,
            sensingRadius = sensingRadius
        )

        context.runSeed = seed
        if (initialKnowledge != null) {
            require(initialKnowledge.graph === graph) { "Knowledge must belong to the same graph." }
            graph.nodes.filter { it in initialKnowledge.knownNodes }.forEach(context.knownNodes::add)
            graph.edges.filter { it in initialKnowledge.knownEdges }.forEach { context.knownEdges.add(it); context.edgeCosts[it] = it.weight }
            graph.nodes.filter { it in initialKnowledge.physicallyVisitedNodes }.forEach(context.physicallyVisitedNodes::add)
        }

        return CachedSimulationTimeline(
            context = context,
            steps = algorithm.run(context),
            limits = limits,
            retainHistory = retainHistory
        )
    }
}
