package sfg.wseminar.rover.ui

import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import sfg.wseminar.rover.algorithms.GoToWaypoint
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.GeneratedGraphCache
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.*
import sfg.wseminar.rover.ui.states.waypointCommandIssue

class WaypointInteractionTest {
    private fun graph(xs: List<Double>, directed: Boolean = false): Graph {
        val nodes = xs.mapIndexed { i, x -> Graph.Node(x, 0.0, i) }
        return Graph(nodes, nodes.zipWithNext().mapIndexed { i, (a, b) -> Graph.Edge(a, b, 1.0, i, !directed) }, 0, nodes.lastIndex)
    }
    private fun model(graph: Graph) = VisualizerViewModel(GeneratedGraphCache { graph }).also {
        it.onEvent(ChangeAlgorithm(GoToWaypoint)); it.onEvent(GenerateGraph)
    }

    @Test fun noControlStartsAnImplicitScenarioGoalCommand() {
        val model = model(graph(listOf(0.0, 1.0, 2.0)))
        val ready = model.state.frames
        assertNull(model.state.currentFrame!!.goalNode)
        for (event in listOf(RunAlgorithm, PlayPause, StepForward, Restart, SendWaypoint)) {
            model.onEvent(event)
            assertSame(ready, model.state.frames)
            assertFalse(model.state.hasRunAlgorithm)
            assertFalse(model.state.isPlaying)
            assertNull(model.state.runElapsedNanos)
        }
    }

    @Test fun rejectedCommandsDoNotAllocateFramesOrReplaceHistory() {
        val graph = graph(listOf(0.0, -1.0, 2.0))
        val model = model(graph)
        val ready = model.state.frames
        for (node in listOf(graph.nodes[0], graph.nodes[2], Graph.Node(2.0, 0.0, 2))) {
            model.onEvent(SelectNode(node))
            assertNotNull(model.state.waypointCommandIssue())
            model.onEvent(SendWaypoint)
            assertSame(ready, model.state.frames)
            assertEquals(0L, model.state.estimatedReplayBytes)
            assertNull(model.state.runElapsedNanos)
        }
        model.onEvent(SelectNode(graph.nodes[1]))
        assertNull(model.state.waypointCommandIssue())
        model.onEvent(SendWaypoint)
        assertSame(graph.nodes[1], model.state.frames.last().roverNode)
    }

    @Test fun distantCommandsWorkAndRedirectsStartAtTheDisplayedSensedPosition() {
        val graph = graph(listOf(0.0, 1.0, 2.0))
        val model = model(graph)
        assertFalse(graph.nodes[2] in model.state.currentFrame!!.knownNodes)
        model.onEvent(SelectNode(graph.nodes[2]))
        assertNull(model.state.waypointCommandIssue())
        model.onEvent(RunAlgorithm) // The command entry point also requires the explicit selection.
        assertEquals(graph.nodes, model.state.frames.last().finalPathNodes)
        val intermediate = model.state.frames.indexOfFirst { it.roverNode === graph.nodes[1] }
        model.onEvent(SeekToStep(intermediate))
        assertTrue(graph.edges[1] in model.state.currentFrame!!.knownEdges)
        val prefix = model.state.frames.take(intermediate + 1)
        model.onEvent(PlayPause)
        assertTrue(model.state.isPlaying)
        model.onEvent(SelectNode(graph.nodes[0]))
        assertFalse(model.state.isPlaying)
        model.onEvent(SendWaypoint)
        assertEquals(prefix, model.state.frames.take(prefix.size))
        assertSame(graph.nodes[1], model.state.currentFrame!!.startNode)
        assertSame(graph.nodes[0], model.state.frames.last().roverNode)
    }

    @Test fun laterLocalMinimumIsAnExplorationOutcomeNotARejectedCommand() {
        val graph = graph(listOf(0.0, 1.0, -1.0, 3.0))
        val model = model(graph)
        model.onEvent(SelectNode(graph.nodes.last()))
        assertNull(model.state.waypointCommandIssue())
        model.onEvent(SendWaypoint)
        val last = model.state.frames.last()
        assertSame(graph.nodes[1], last.roverNode)
        assertTrue(last.finalPathNodes.isEmpty())
        assertEquals(listOf(graph.nodes[0], graph.nodes[1]), last.currentPathNodes)
        assertTrue(last.message.contains("intermediate waypoint"))
        assertTrue(last.message.contains("does not mean that no route exists"))
        model.onEvent(SeekToStep(model.state.maxStep))
        val history = model.state.frames
        model.onEvent(SelectNode(graph.nodes.last()))
        assertNotNull(model.state.waypointCommandIssue())
        model.onEvent(SendWaypoint)
        assertSame(history, model.state.frames)
        model.onEvent(SelectNode(graph.nodes[2]))
        assertNull(model.state.waypointCommandIssue())
        model.onEvent(SendWaypoint)
        assertSame(graph.nodes[2], model.state.frames.last().roverNode)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun aSecondCommandCannotReplaceAnInFlightRecording() = runTest {
        val graph = graph((0..100).map(Int::toDouble))
        // Keep the recording in flight deterministically, independent of platform scheduling.
        val model = VisualizerViewModel(GeneratedGraphCache { graph }, this, StandardTestDispatcher(testScheduler),
            checkpoint = { kotlinx.coroutines.delay(1) })
        model.onEvent(ChangeAlgorithm(GoToWaypoint)); model.onEvent(GenerateGraph); advanceUntilIdle()
        model.onEvent(SelectNode(graph.nodes.last())); model.onEvent(SendWaypoint); runCurrent()
        assertTrue(model.state.isComputing)
        model.onEvent(SelectNode(graph.nodes[1]))
        assertNotNull(model.state.waypointCommandIssue())
        model.onEvent(SendWaypoint)
        advanceUntilIdle()
        assertSame(graph.nodes.last(), model.state.frames.last().goalNode)
        assertSame(graph.nodes.last(), model.state.frames.last().roverNode)
    }
}
