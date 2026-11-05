package sfg.wseminar.rover.algorithms.backend

import sfg.wseminar.rover.SimulationFrame


object SimulationFrameBuilder {
    fun build(
        context: AlgorithmContext,
        stepIndex: Int,
        step: AlgorithmStep
    ): SimulationFrame =
        SimulationFrame(
            graph = context.graph,
            stepIndex = stepIndex,
            roverNode = context.roverNode,
            startNode = context.startNode,
            goalNode = context.goalNode,
            knownNodes = context.knownNodes.snapshot(),
            knownEdges = context.knownEdges.snapshot(),
            visitedNodes = context.visitedNodes.snapshot(),
            frontierNodes = context.frontierNodes.snapshot(),
            currentPathNodes = context.currentPathNodes,
            finalPathNodes = context.finalPathNodes,
            nodeCosts = context.nodeCosts.snapshot(),
            edgeCosts = context.edgeCosts.snapshot(),
            message = step.message,
            nodeCostFields = context.CostSnapshots(),
            currentPathEdges = context.currentPathEdges,
            finalPathEdges = context.finalPathEdges,
            physicallyVisitedNodes = context.physicallyVisitedNodes.snapshot(),
            costMeaning = context.algorithm.costMeaning,
            runSeed = context.runSeed
        )
}
