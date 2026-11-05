package sfg.wseminar.rover.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*
import sfg.wseminar.rover.algorithms.*
import sfg.wseminar.rover.algorithms.backend.SimulationLimits
import sfg.wseminar.rover.graph.generator.*
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.*
import sfg.wseminar.rover.ui.states.*

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncSimulationTest {
    @Test fun firstBatchPublishesBeforeCompletionAndCancelPreservesHistory() = runTest {
        // Deliberately hold each batch at a virtual timer so this test can inspect/cancel mid-run.
        val model = VisualizerViewModel(scope = this, worker = StandardTestDispatcher(testScheduler),
            checkpoint = { kotlinx.coroutines.delay(1) })
        model.onEvent(ChangeGraphGeneration(GraphGenerationState(mode = SpaceMode.DISCRETE, advanced = true, gridRows = 20, gridColumns = 20)))
        model.onEvent(GenerateGraph)
        assertTrue(model.state.isComputing)
        advanceUntilIdle()
        model.onEvent(PlayPause)
        runCurrent()
        assertTrue(model.state.isComputing)
        assertTrue(model.state.isPlaying)
        assertTrue(model.state.frames.size in 1..32)
        val frames = model.state.frames
        model.onEvent(CancelWork)
        val elapsed = assertNotNull(model.state.runElapsedNanos)
        advanceUntilIdle()
        assertEquals(elapsed, model.state.runElapsedNanos)
        assertSame(frames, model.state.frames)
        assertFalse(model.state.isComputing)
        assertFalse(model.state.isPlaying)
        model.close()
    }

    @Test fun resetAndAlgorithmChangesCannotPublishOldWork() = runTest {
        val model = VisualizerViewModel(scope = this, worker = StandardTestDispatcher(testScheduler))
        model.onEvent(GenerateGraph)
        advanceUntilIdle()
        model.onEvent(RunAlgorithm)
        runCurrent()
        model.onEvent(ChangeAlgorithm(AStar))
        val reset = model.state.frames
        advanceUntilIdle()
        assertSame(reset, model.state.frames)
        assertEquals(AStar, model.state.algorithm)
        assertNull(model.state.runElapsedNanos)
        assertFalse(model.state.hasRunAlgorithm)
        model.onEvent(RunAlgorithm)
        runCurrent()
        model.onEvent(ResetSimulation)
        advanceUntilIdle()
        assertEquals(1, model.state.frames.size)
        assertFalse(model.state.isComputing)
    }

    @Test fun generationFailuresKeepThePreviousGraph() = runTest {
        val cache = GeneratedGraphCache { options ->
            require(options.seed != 99) { "Deliberate generation failure" }
            GraphGenerator.generate(options)
        }
        val model = VisualizerViewModel(cache, this, StandardTestDispatcher(testScheduler))
        model.onEvent(GenerateGraph)
        advanceUntilIdle()
        val graph = model.state.generatedGraph
        model.onEvent(ChangeGraphGeneration(model.state.graphGeneration.copy(seed = 99)))
        model.onEvent(GenerateGraph)
        advanceUntilIdle()
        assertSame(graph, model.state.generatedGraph)
        assertTrue(model.state.statusMessage.contains("Deliberate"))
        assertTrue(assertNotNull(model.state.noticeMessage).contains("Deliberate"))
        assertTrue(model.state.noticeId > 0)
        assertFalse(model.state.isComputing)
    }

    @Test fun finalCostIsRevealedOnlyAfterViewingTheEndAndPersistsWhileSeeking() {
        val model = VisualizerViewModel()
        model.onEvent(ChangeGraphGeneration(GraphGenerationState(mode = SpaceMode.DISCRETE, advanced = true, gridRows = 3, gridColumns = 3, seed = 42)))
        model.onEvent(GenerateGraph)
        model.onEvent(RunAlgorithm)
        assertNull(model.state.revealedFinalPathCost)
        val cost = model.state.frames.last().finalPathEdges.sumOf { it.weight }
        assertTrue(model.state.frames.last().finalPathNodes.isNotEmpty())
        model.onEvent(SeekToStep(model.state.maxStep))
        assertEquals(cost, model.state.revealedFinalPathCost)
        model.onEvent(Restart)
        assertEquals(cost, model.state.revealedFinalPathCost)
        model.onEvent(RunAlgorithm)
        assertNull(model.state.revealedFinalPathCost)
        model.onEvent(SeekToStep(model.state.maxStep - 1))
        model.onEvent(PlayPause)
        model.tickPlayback()
        assertEquals(cost, model.state.revealedFinalPathCost)
        model.onEvent(ResetSimulation)
        assertNull(model.state.revealedFinalPathCost)
    }

    @Test fun memoryBudgetStopsRecordingAndExposesAnAlertReason() = runTest {
        val model = VisualizerViewModel(scope = this, worker = StandardTestDispatcher(testScheduler),
            limits = SimulationLimits(maxEstimatedBytes = 1))
        model.onEvent(GenerateGraph)
        advanceUntilIdle()
        model.onEvent(RunAlgorithm)
        advanceUntilIdle()
        assertFalse(model.state.isComputing)
        assertTrue(assertNotNull(model.state.recordingStopReason).contains("memory budget"))
        assertTrue(model.state.estimatedReplayBytes >= 1)
        model.onEvent(ResetSimulation)
        assertNull(model.state.recordingStopReason)
    }

    @Test fun budgetsStopRecordingExplicitlyAndPreserveScenarioSeed() = runTest {
        val model = VisualizerViewModel(scope = this, worker = StandardTestDispatcher(testScheduler), limits = SimulationLimits(maxFrames = 5))
        model.onEvent(ChangeGraphGeneration(GraphGenerationState(seed = 42)))
        model.onEvent(GenerateGraph)
        advanceUntilIdle()
        model.onEvent(ChangeGraphGeneration(model.state.graphGeneration.copy(seed = 99)))
        model.onEvent(ChangeAlgorithm(Dijkstra))
        model.onEvent(RunAlgorithm)
        advanceUntilIdle()
        assertEquals(5, model.state.frames.size)
        val elapsed = assertNotNull(model.state.runElapsedNanos)
        model.onEvent(SeekToStep(0))
        model.onEvent(PlayPause)
        model.tickPlayback()
        assertEquals(elapsed, model.state.runElapsedNanos)
        assertNotNull(model.state.recordingStopReason)
        assertEquals(42, model.state.frames.first().runSeed)
        assertFalse(model.state.isComputing)
    }
}
