package sfg.wseminar.rover.computation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.*
import sfg.wseminar.rover.ui.states.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds
import sfg.wseminar.rover.algorithms.Dijkstra
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.graph.generator.*

class BrowserWorkerIntegrationTest {
    @Test fun defaultContinuousTenThousandNodePipeline() = runTest(timeout = 120.seconds) {
        withContext(Dispatchers.Default) {
            var clock = TimeSource.Monotonic.markNow()
            val graph = GraphGenerator.generate(GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS, nodeCount = 10_000, seed = 42,
                budget = GenerationBudget(enabled = false)))
            println("PERF continuous 10000 generation (generation budget disabled): ${clock.elapsedNow()}; edges=${graph.edges.size}")
            val model = VisualizerViewModel(graphCache = GeneratedGraphCache { graph }, scope = this)
            try {
                model.onEvent(ChangePerformance(model.state.performance.copy(browserWorker = WorkerSettings(WorkerMode.NEVER))))
                model.onEvent(ChangeGraphGeneration(GraphGenerationState(mode = SpaceMode.CONTINUOUS, advanced = true, nodeCount = 10_000, seed = 42)))
                model.onEvent(GenerateGraph)
                while (model.state.isComputing) delay(1)
                assertSame(graph, model.state.generatedGraph)
                var count: Int? = null
                var path: List<sfg.wseminar.rover.graph.Graph.Edge>? = null
                for (mode in listOf(WorkerMode.NEVER, WorkerMode.ALWAYS, WorkerMode.ALWAYS)) {
                    model.onEvent(ChangePerformance(model.state.performance.copy(browserWorker = WorkerSettings(mode))))
                    clock = TimeSource.Monotonic.markNow()
                    model.onEvent(RunAlgorithm)
                    while (model.state.isComputing) delay(10)
                    assertNull(model.state.recordingStopReason)
                    assertTrue(model.state.hasCompletedRecording, model.state.statusMessage)
                    if (count == null) { count = model.state.frames.size; path = model.state.frames.last().finalPathEdges }
                    assertEquals(count, model.state.frames.size)
                    assertEquals(path, model.state.frames.last().finalPathEdges)
                    println("PERF continuous 10000 ViewModel $mode: ${clock.elapsedNow()}; frames=${model.state.frames.size}")
                }
            } finally { model.close() }
        }
    }

    @Test fun binaryBridgeRecordsTenThousandNodesAndReusesWorker() = runTest(timeout = 120.seconds) {
        // Real timers: runTest's virtual timeout must not expire while waiting for a browser event.
        withContext(Dispatchers.Default) {
            val options = GraphGenerationOptions(gridColumns = 100, gridRows = 100, seed = 42)
            val graph = GraphGenerator.generate(options)
            val start = graph.nodes[graph.startNodeId!!]; val goal = graph.nodes[graph.goalNodeId!!]
            var clock = TimeSource.Monotonic.markNow()
            val expected = AlgorithmRunner.createTimeline(Dijkstra, graph, start, goal).generateAll()
            println("PERF 10000-node default-weight grid local recording: ${clock.elapsedNow()}; frames=${expected.size}")
            clock = TimeSource.Monotonic.markNow()
            val client = WorkerClient.open()
            println("PERF worker startup: ${clock.elapsedNow()}")
            try {
                repeat(2) { run ->
                    clock = TimeSource.Monotonic.markNow()
                    var batch = client.start(Dijkstra, graph, start, goal, KnowledgeMode.GLOBAL, SimulationLimits(), null)
                    var count = 0
                    var batches = 0
                    var lastFrame: sfg.wseminar.rover.SimulationFrame? = null
                    while (true) {
                        batches++
                        batch.frames.forEach { frame ->
                            assertEquals(count, frame.stepIndex)
                            assertSame(graph, frame.graph)
                            // JS and Wasm format Double strings differently (e.g. 0 vs 0.0).
                            assertSame(expected[count].roverNode, frame.roverNode)
                            assertEquals(expected[count].frontierNodes.size, frame.frontierNodes.size)
                            frame.roverNode?.let { assertEquals(expected[count].nodeCosts[it.id], frame.nodeCosts[it.id]) }
                            lastFrame = frame
                            count++
                        }
                        if (batch.done) break
                        batch = client.next()
                    }
                    assertNull(batch.stopReason)
                    assertEquals(expected.size, count)
                    assertEquals(expected.last().finalPathEdges, assertNotNull(lastFrame).finalPathEdges)
                    println("PERF 10000-node worker recording run=$run: ${clock.elapsedNow()}; frames=$count batches=$batches")
                }
                // Exercise worker generation, then use its cached graph directly.
                val generated = client.generate(GraphGenerationOptions(gridColumns = 3, gridRows = 3, seed = 123))
                var batch = client.start(Dijkstra, generated, generated.nodes.first(), generated.nodes.last(), KnowledgeMode.GLOBAL, SimulationLimits(), null)
                while (!batch.done) batch = client.next()
                assertSame(generated, batch.frames.last().graph)
            } finally { client.close() }
        }
    }
}
