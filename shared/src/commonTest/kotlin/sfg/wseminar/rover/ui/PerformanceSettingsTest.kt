package sfg.wseminar.rover.ui

import kotlin.test.*
import sfg.wseminar.rover.algorithms.backend.SimulationLimits
import sfg.wseminar.rover.graph.generator.*
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.*
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.settings.PerformanceSettings

class PerformanceSettingsTest {
    @Test fun generationHonorsEachConfiguredBudget() {
        val grid = GraphGenerationOptions(gridColumns = 4, gridRows = 4)
        val work = assertFailsWith<IllegalArgumentException> {
            GraphGenerator.generate(grid.copy(budget = GenerationBudget(maxWorkOperations = 1)))
        }
        assertTrue(work.message.orEmpty().contains("work operations budget exceeded"))
        val edges = assertFailsWith<IllegalArgumentException> {
            GraphGenerator.generate(grid.copy(budget = GenerationBudget(maxEdges = 1)))
        }
        assertTrue(edges.message.orEmpty().contains("output edges budget exceeded"))
        val candidates = assertFailsWith<IllegalArgumentException> {
            GraphGenerator.generate(GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS,
                nodeCount = 10, planar = false, connectionRadius = 2.0, allowNodeOverlap = true,
                budget = GenerationBudget(maxCandidateEdges = 1)))
        }
        assertTrue(candidates.message.orEmpty().contains("candidate edges budget exceeded"))
        assertEquals(16, GraphGenerator.generate(grid).nodes.size)
    }

    @Test fun lowerBudgetsDoNotReuseCachedGraphOrDeleteExistingReplay() {
        val model = VisualizerViewModel()
        model.onEvent(GenerateGraph)
        val graph = model.state.generatedGraph
        val frames = model.state.frames
        model.onEvent(ChangePerformance(PerformanceSettings(generation = GenerationBudget(maxWorkOperations = 1))))
        assertSame(frames, model.state.frames)
        model.onEvent(GenerateGraph)
        assertSame(graph, model.state.generatedGraph)
        assertTrue(model.state.noticeMessage.orEmpty().contains("work operations budget exceeded"))
    }

    @Test fun nextRecordingUsesUpdatedFrameAndMemoryLimits() {
        val model = VisualizerViewModel()
        model.onEvent(GenerateGraph)
        model.onEvent(ChangePerformance(PerformanceSettings(replay = SimulationLimits(maxFrames = 2))))
        model.onEvent(RunAlgorithm)
        assertTrue(model.state.frames.size <= 2)
        assertNotNull(model.state.recordingStopReason)
        model.onEvent(ChangePerformance(PerformanceSettings(replay = SimulationLimits(maxEstimatedBytes = 1))))
        model.onEvent(RunAlgorithm)
        assertEquals(1L, model.state.replayBudgetBytes)
        assertNotNull(model.state.recordingStopReason)
    }
}
