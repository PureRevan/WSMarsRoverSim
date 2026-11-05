package sfg.wseminar.rover.ui.settings

import kotlin.test.*
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.milliseconds
import sfg.wseminar.rover.algorithms.Dijkstra
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.*
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.*

class PerformanceControlsTest {
    @Test fun decimalMemoryUnitsPreserveExactByteBudgets() {
        for (bytes in listOf(1L, 1_000_000L, 536_870_912L, Long.MAX_VALUE)) {
            assertEquals(bytes, memoryBytes(memoryMegabytes(bytes)))
            val draft = PerformanceDraft(PerformanceSettings(replay = SimulationLimits(maxEstimatedBytes = bytes)))
            assertEquals(bytes, assertNotNull(draft.build()).replay.maxEstimatedBytes)
        }
        assertEquals(512_000_000L, memoryBytes("512"))
        assertEquals(1_250_000L, memoryBytes("1.25"))
        for (invalid in listOf("0", "-1", "NaN", "0.0000001", "9223372036854.775808")) {
            assertNull(memoryBytes(invalid))
        }
        assertEquals("1 MB", sfg.wseminar.rover.ui.infopanel.formatMemory(1_000_000))
        assertEquals("1 GB", sfg.wseminar.rover.ui.infopanel.formatMemory(1_000_000_000))
    }

    @Test fun sliderEndpointsMonotonicityAndLinearPrefix() {
        assertFalse(NodeSliderSettings().exponential)
        assertEquals(100, NodeSliderSettings().nodeCount(1f))
        assertEquals(100 * 10, NodeSliderSettings().nodeCount(1f, advanced = true))
        assertEquals(100, NodeSliderSettings(maximum = 25_000).nodeCount(1f))
        for (advanced in listOf(false, true)) {
            assertEquals(10_000, NodeSliderSettings(exponential = true).nodeCount(1f, advanced))
            assertEquals(25_000, NodeSliderSettings(exponential = true, maximum = 25_000).nodeCount(1f, advanced))
        }
        for (factor in listOf(0.1, 1.0, 10.0)) {
            val scale = NodeSliderSettings(exponential = true, growthFactor = factor)
            assertEquals(2, scale.nodeCount(0f))
            assertEquals(10_000, scale.nodeCount(1f))
            for (count in 2..100) assertEquals(count, scale.nodeCount(scale.position(count)))
            val counts = (0..1000).map { scale.nodeCount(it / 1000f) }
            assertTrue(counts.zipWithNext().all { (a, b) -> a <= b })
        }
        val scale = NodeSliderSettings(exponential = true)
        assertTrue(scale.copy(growthFactor = 2.0).nodeCount(0.6f) > scale.nodeCount(0.6f))
        assertEquals(50, scale.copy(maximum = 50).nodeCount(1f))
        assertEquals(2, scale.copy(maximum = 2).nodeCount(0.5f))
    }

    @Test fun inputHasNoOldCapsAndRejectsOverflowOrNonFiniteNumbers() {
        val draft = PerformanceDraft(PerformanceSettings())
        val large = draft.copy(values = draft.values + mapOf(
            PerformanceField.MEMORY to "100_000", PerformanceField.FRAMES to "2_000_000",
            PerformanceField.OPERATIONS to "2_000_000_000", PerformanceField.CANDIDATES to "3_000_000",
            PerformanceField.EDGES to "2_000_000", PerformanceField.NODE_MAX to "100_000"))
        assertNotNull(large.build())
        for ((field, value) in listOf(PerformanceField.MEMORY to Long.MAX_VALUE.toString(),
            PerformanceField.FRAMES to "2147483648", PerformanceField.SPEED_MAX to "NaN",
            PerformanceField.SPEED_MIN to "0", PerformanceField.SPEED_MAX to "0.1")) {
            assertNull(draft.copy(values = draft.values + (field to value)).build())
        }
        assertNotNull(draft.copy(values = draft.values + (PerformanceField.RUN_TIME to "")).build())
    }

    @Test fun workAndTimeLimitsAreIndependent() {
        for (workEnabled in listOf(false, true)) for (timeEnabled in listOf(false, true)) {
            val clock = TestTimeSource()
            val work = GenerationWork(GenerationBudget(maxWorkOperations = 1, workLimitEnabled = workEnabled,
                timeLimitEnabled = timeEnabled, maxDurationMillis = 10), clock) {}
            if (workEnabled) assertFailsWith<IllegalArgumentException> { work.add(2) } else work.add(2)
            clock += 10.milliseconds
            if (timeEnabled) {
                val failure = assertFailsWith<IllegalArgumentException> { work.checkTime() }
                assertTrue(failure.message.orEmpty().contains("time budget exceeded"))
            } else work.checkTime()
        }
        assertEquals(16, GraphGenerator.generate(GraphGenerationOptions(gridColumns = 4, gridRows = 4,
            budget = GenerationBudget(maxWorkOperations = 1, workLimitEnabled = false))).nodes.size)
    }

    @Test fun recordingTimeStopsAfterAnExpensiveStepAndKeepsHistory() {
        val nodes = listOf(Graph.Node(0.0, 0.0, 0), Graph.Node(1.0, 0.0, 1))
        for (timeEnabled in listOf(false, true)) {
            val clock = TestTimeSource()
            val timeline = CachedSimulationTimeline(AlgorithmContext(Dijkstra, Graph(nodes, emptyList()), nodes[0], nodes[1]),
                sequence {
                    yield(AlgorithmStep("First"))
                    clock += 10.milliseconds
                    yield(AlgorithmStep("Late"))
                }, SimulationLimits(maxFrames = 1, frameLimitEnabled = false, timeLimitEnabled = timeEnabled, maxDurationMillis = 10), clock)
            assertNotNull(timeline.generateNext())
            if (timeEnabled) {
                assertNull(timeline.generateNext())
                assertEquals(1, timeline.cachedFrames.size)
                assertTrue(timeline.stopReason.orEmpty().contains("time budget reached"))
            } else {
                assertNotNull(timeline.generateNext())
                assertNull(timeline.stopReason)
            }
        }
    }

    @Test fun frameSwitchAndPlaybackBoundsReachViewModel() {
        val model = VisualizerViewModel()
        model.onEvent(GenerateGraph)
        model.onEvent(ChangePerformance(PerformanceSettings(replay = SimulationLimits(maxFrames = 1, frameLimitEnabled = false),
            playback = PlaybackSettings(0.01f, 100f))))
        model.onEvent(RunAlgorithm)
        assertTrue(model.state.frames.size > 1)
        assertNull(model.state.recordingStopReason)
        model.onEvent(SetPlaybackSpeed(80f))
        assertEquals(80f, model.state.playbackSpeed)
        model.onEvent(SetPlaybackSpeed(Float.NaN))
        assertEquals(80f, model.state.playbackSpeed)
        model.onEvent(ChangePerformance(PerformanceSettings(playback = PlaybackSettings(0.1f, 2f))))
        assertEquals(2f, model.state.playbackSpeed)
    }
}
