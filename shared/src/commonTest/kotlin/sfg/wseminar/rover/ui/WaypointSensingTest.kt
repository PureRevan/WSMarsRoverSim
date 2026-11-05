package sfg.wseminar.rover.ui

import kotlin.test.*
import sfg.wseminar.rover.algorithms.GoToWaypoint
import sfg.wseminar.rover.algorithms.backend.AlgorithmRunner
import sfg.wseminar.rover.algorithms.backend.KnowledgeMode
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.GeneratedGraphCache
import sfg.wseminar.rover.simulation.InitialSimulationFrameFactory
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.*
import sfg.wseminar.rover.ui.states.ExplorationMode
import sfg.wseminar.rover.ui.states.waypointCommandIssue

class WaypointSensingTest {
    private fun chain(): Graph {
        val nodes = List(7) { Graph.Node(it.toDouble(), 0.0, it) }
        return Graph(nodes, nodes.zipWithNext().mapIndexed { i, (a, b) -> Graph.Edge(a, b, 1.0, i) }, 0, 6)
    }

    @Test fun globalCommandsRevealTheWholeGraphAndKeepTheSameController() {
        val graph = chain()
        val model = VisualizerViewModel(GeneratedGraphCache { graph })
        model.onEvent(ChangeAlgorithm(GoToWaypoint))
        assertEquals(ExplorationMode.LOCAL, model.state.explorationMode)
        model.onEvent(ChangeExplorationMode(ExplorationMode.GLOBAL))
        model.onEvent(GenerateGraph)
        assertEquals(7, model.state.currentFrame!!.knownNodes.size)
        assertEquals(6, model.state.currentFrame!!.knownEdges.size)
        model.onEvent(SelectNode(graph.nodes.last()))
        assertNull(model.state.waypointCommandIssue())
        model.onEvent(SendWaypoint)
        assertEquals(graph.nodes, model.state.frames.last().finalPathNodes)
        assertTrue(model.state.frames.all { it.knownNodes.size == 7 && it.knownEdges.size == 6 })
        model.onEvent(ChangeExplorationMode(ExplorationMode.LOCAL))
        assertSame(graph, model.state.generatedGraph)
        assertEquals(2, model.state.currentFrame!!.knownNodes.size)
    }

    @Test fun radiusAppliesInitiallyAndAfterEachMoveWithoutChangingMovement() {
        val graph = chain()
        val frames = AlgorithmRunner.createTimeline(GoToWaypoint, graph, graph.nodes[0], graph.nodes[3],
            knowledgeMode = KnowledgeMode.LOCAL, sensingRadius = 2).generateAll()
        val ready = InitialSimulationFrameFactory.create(graph, KnowledgeMode.LOCAL, 2)
        assertEquals(graph.nodes.filter { it in ready.knownNodes }, graph.nodes.filter { it in frames.first().knownNodes })
        assertEquals(graph.edges.filter { it in ready.knownEdges }, graph.edges.filter { it in frames.first().knownEdges })
        assertEquals(3, ready.knownNodes.size)
        assertEquals(2, ready.knownEdges.size)
        assertEquals(Double.POSITIVE_INFINITY, ready.edgeCosts[2])
        for (frame in frames) {
            val position = frame.roverNode!!.id
            assertEquals((0..minOf(6, position + 2)).toList(), graph.nodes.filter { it in frame.knownNodes }.map { it.id })
            assertEquals((0..position).toList(), graph.nodes.filter { it in frame.physicallyVisitedNodes }.map { it.id })
        }
        assertEquals(graph.nodes.take(4), frames.last().finalPathNodes)
        assertFalse(graph.nodes.last() in frames.last().knownNodes)
    }

    @Test fun sensingRespectsOutgoingHopsCyclesAndTheBoundary() {
        val nodes = List(8) { Graph.Node(it.toDouble(), 0.0, it) }
        val pairs = listOf(0 to 1, 1 to 2, 2 to 0, 0 to 4, 4 to 5, 6 to 0, 5 to 7)
        val graph = Graph(nodes, pairs.mapIndexed { id, (a, b) -> Graph.Edge(nodes[a], nodes[b], 1.0, id, false) }, 0, 7)
        val frame = InitialSimulationFrameFactory.create(graph, KnowledgeMode.LOCAL, 2)
        assertEquals(listOf(0, 1, 2, 4, 5), nodes.filter { it in frame.knownNodes }.map { it.id })
        assertEquals(listOf(0, 1, 3, 4), graph.edges.filter { it in frame.knownEdges }.map { it.id })
        assertEquals(Double.POSITIVE_INFINITY, frame.edgeCosts[5])
    }

    @Test fun changingRadiusRestartsExplorationWithoutRegeneratingTheGraph() {
        val graph = chain()
        val model = VisualizerViewModel(GeneratedGraphCache { graph })
        model.onEvent(ChangeAlgorithm(GoToWaypoint)); model.onEvent(GenerateGraph)
        model.onEvent(ChangeSensingRadius(3))
        assertEquals(4, model.state.currentFrame!!.knownNodes.size)
        model.onEvent(SelectNode(graph.nodes[2])); model.onEvent(SendWaypoint)
        model.onEvent(ChangeSensingRadius(1))
        assertSame(graph, model.state.generatedGraph)
        assertEquals(1, model.state.frames.size)
        assertFalse(model.state.hasRunAlgorithm)
        assertEquals(2, model.state.currentFrame!!.knownNodes.size)
        assertSame(graph.nodes[0], model.state.currentFrame!!.roverNode)
        assertNull(model.state.selectedNode)
    }
}
