package sfg.wseminar.rover.algorithms

import kotlin.test.*
import sfg.wseminar.rover.algorithms.backend.AlgorithmRunner
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.GeneratedGraphCache
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.*
import sfg.wseminar.rover.ui.states.ExplorationMode

class LocalAlgorithmTest {
    private fun run(algorithm: RoverAlgorithm, graph: Graph, start: Int = 0, goal: Int = graph.nodes.lastIndex) =
        AlgorithmRunner.createTimeline(algorithm, graph, graph.nodes[start], graph.nodes[goal]).generateAll()

    @Test fun waypointStopsAtLocalMinimumAndCanBeRedirected() {
        val n = listOf(Graph.Node(0.0, 0.0, 0), Graph.Node(-1.0, 0.0, 1), Graph.Node(2.0, 0.0, 2))
        val graph = Graph(n, listOf(Graph.Edge(n[0], n[1], 1.0, 0), Graph.Edge(n[1], n[2], 3.0, 1)), 0, 2)
        val stopped = run(GoToWaypoint, graph).last()
        assertSame(n[0], stopped.roverNode)
        assertTrue(stopped.finalPathNodes.isEmpty())
        assertTrue(stopped.message.contains("intermediate waypoint"))
        assertEquals(listOf(n[0], n[1]), run(GoToWaypoint, graph, 0, 1).last().finalPathNodes)
        val model = VisualizerViewModel(GeneratedGraphCache { graph })
        model.onEvent(ChangeAlgorithm(GoToWaypoint))
        assertEquals(ExplorationMode.LOCAL, model.state.explorationMode)
        model.onEvent(GenerateGraph)
        model.onEvent(SelectNode(n[1]))
        model.onEvent(SendWaypoint)
        assertTrue(model.state.isPlaying)
        model.onEvent(SeekToStep(model.state.maxStep))
        assertSame(n[1], model.state.currentFrame!!.roverNode)
        val previous = model.state.frames
        val commandIndex = model.state.currentStep
        model.onEvent(SelectNode(n[2]))
        model.onEvent(SendWaypoint)
        assertEquals(previous, model.state.frames.take(previous.size))
        assertSame(n[1], model.state.currentFrame!!.startNode)
        assertSame(n[2], model.state.frames.last().roverNode)
        assertTrue(graph.edges[0] in model.state.currentFrame!!.knownEdges)
        model.onEvent(SeekToStep(commandIndex))
        model.onEvent(SelectNode(n[0]))
        model.onEvent(SendWaypoint)
        assertSame(n[0], model.state.frames.last().goalNode)
        assertTrue(model.state.frames.drop(commandIndex + 1).all { it.goalNode === n[0] })
        assertEquals(model.state.frames.indices.toList(), model.state.frames.map { it.stepIndex })
        model.onEvent(ResetSimulation)
        assertSame(graph, model.state.generatedGraph)
        assertEquals(1, model.state.frames.size)
    }
}
