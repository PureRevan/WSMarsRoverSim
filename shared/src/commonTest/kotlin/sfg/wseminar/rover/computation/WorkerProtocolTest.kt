package sfg.wseminar.rover.computation

import kotlin.test.*
import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.algorithms.*
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.computation.wire.*
import sfg.wseminar.rover.graph.*
import sfg.wseminar.rover.graph.generator.*
import sfg.wseminar.rover.ui.models.*
import sfg.wseminar.rover.ui.settings.PerformanceSettings

class WorkerProtocolTest {
    @Test fun schedulingUsesCheapEstimatesAndExplicitOverrides() {
        val auto = WorkerSettings()
        assertFalse(auto.useWorker(estimateGenerationWork(GraphGenerationOptions())))
        assertTrue(auto.useWorker(estimateGenerationWork(GraphGenerationOptions(nodeCount = 10_000, mode = GraphGenerationMode.CONTINUOUS))))
        assertTrue(WorkerSettings(WorkerMode.ALWAYS).useWorker(0))
        assertFalse(WorkerSettings(WorkerMode.NEVER).useWorker(Long.MAX_VALUE))
        assertFalse(WorkerSettings(workThreshold = 100).useWorker(99))
        assertTrue(WorkerSettings(workThreshold = 100).useWorker(100))
    }
    @Test fun optionsAndTopologyRoundTripWithoutChangingSeedOrLabels() {
        val options = GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS, seed = 123, nodeCount = 35,
            terrain = sfg.wseminar.rover.terrain.TerrainSettings(beta = 3.5, mapWidthMetres = 60.0, samplesPerEdge = 8),
            density = 0.7, planar = false, bidirectional = false, endpointPlacement = EndpointPlacement.NEARBY,
            budget = GenerationBudget(enabled = false, maxWorkOperations = 1, timeLimitEnabled = true))
        val writer = PacketWriter().apply { write(options) }
        assertEquals(options, PacketReader(writer.bytes()).readGraphGenerationOptions())
        val original = GraphGenerator.generate(options)
        val (kind, input) = readPacket(WorkerEngine().handle(packet(GENERATE) { write(options) }))
        assertEquals(GRAPH, kind)
        val decoded = input.graph()
        assertEquals(original.seed, decoded.seed)
        assertEquals(original.startNodeId, decoded.startNodeId)
        assertEquals(original.goalNodeId, decoded.goalNodeId)
        assertEquals(original.nodes.map { Triple(it.x, it.y, it.label) }, decoded.nodes.map { Triple(it.x, it.y, it.label) })
        assertEquals(original.edges.map { listOf(it.from.id, it.to.id, it.weight, it.bidirectional, it.label) }, decoded.edges.map { listOf(it.from.id, it.to.id, it.weight, it.bidirectional, it.label) })
        decoded.edges.forEach { assertSame(decoded.nodes[it.from.id], it.from); assertSame(decoded.nodes[it.to.id], it.to) }
    }
    @Test fun everyAlgorithmProducesEquivalentFramesWithOriginalUiGraphReferences() {
        val graph = GraphGenerator.generate(GraphGenerationOptions(gridColumns = 5, gridRows = 5, seed = 44))
        for (algorithm in RoverAlgorithm.algorithms) {
            val mode = if (algorithm is GlobalAlgorithm) KnowledgeMode.GLOBAL else KnowledgeMode.LOCAL
            val start = graph.nodes[graph.startNodeId!!]; val goal = graph.nodes[graph.goalNodeId!!]
            val expected = AlgorithmRunner.createTimeline(algorithm, graph, start, goal, mode, seed = graph.seed).generateAll()
            val actual = remoteRun(graph, algorithm, start, goal, mode)
            assertEquals(expected.size, actual.size, algorithm.name)
            expected.zip(actual).forEach { (a, b) -> sameFrame(a, b) }
            if (algorithm === GoToWaypoint) {
                val initial = actual.last()
                val reverse = AlgorithmRunner.createTimeline(algorithm, graph, initial.roverNode!!, start, mode,
                    seed = graph.seed, initialKnowledge = initial).generateAll()
                val remoteReverse = remoteRun(graph, algorithm, initial.roverNode!!, start, mode, initial = initial)
                assertEquals(reverse.size, remoteReverse.size)
                reverse.zip(remoteReverse).forEach { (a, b) -> sameFrame(a, b) }
            }
        }
    }
    @Test fun pagesAreSharedAfterDecodingAndOnlyChangesAreTransferred() {
        val nodes = List(10_000) { Graph.Node(it.toDouble(), 0.0, it) }
        val graph = Graph(nodes, emptyList())
        val frame = AlgorithmRunner.createTimeline(Dijkstra, graph, nodes.first(), nodes.last()).generateNext()!!
        val encoder = FrameEncoder(); val decoder = FrameDecoder(graph)
        val first = PacketWriter().also { encoder.write(it, frame) }.bytes()
        val decoded = decoder.read(PacketReader(first))
        val next = PacketWriter().also { encoder.write(it, frame.copy(stepIndex = 1)) }.bytes()
        val nextFrame = decoder.read(PacketReader(next))
        assertTrue(next.size < first.size / 10)
        assertSame(decoded.nodeCosts.pages[0], nextFrame.nodeCosts.pages[0])
        assertSame((decoded.knownNodes as IDBitSet).pages[0], (nextFrame.knownNodes as IDBitSet).pages[0])
        assertEquals(1, nextFrame.stepIndex)
    }
    @Test fun sparseCostsAndPathPrefixesPreserveOldFramesAndAliases() {
        val nodes = List(300) { Graph.Node(it.toDouble(), 0.0, it) }
        val graph = Graph(nodes, emptyList())
        val initial = AlgorithmRunner.createTimeline(Dijkstra, graph, nodes.first(), nodes.last()).generateNext()!!
        val encoder = FrameEncoder(); val decoder = FrameDecoder(graph)
        fun roundTrip(frame: SimulationFrame): Pair<SimulationFrame, Int> {
            val bytes = PacketWriter().also { encoder.write(it, frame) }.bytes()
            return decoder.read(PacketReader(bytes)) to bytes.size
        }
        val (first, _) = roundTrip(initial.copy(currentPathNodes = nodes.take(260)))
        val costs = TrackedNodeCosts(nodes.size)
        costs[nodes[0]] = 0.0
        costs[nodes[129]] = -0.0
        costs[nodes[250]] = Double.NEGATIVE_INFINITY
        val values = costs.snapshot()
        val (second, bytes) = roundTrip(initial.copy(stepIndex = 1, nodeCosts = values,
            nodeCostFields = mapOf("g" to values), currentPathNodes = nodes.take(261)))
        assertTrue(bytes < 1024, "Sparse changes should not transmit full cost pages or paths: $bytes bytes")
        assertSame(second.nodeCosts, second.nodeCostFields["g"])
        assertEquals(Double.POSITIVE_INFINITY, first.nodeCosts[250])
        assertEquals(Double.NEGATIVE_INFINITY, second.nodeCosts[250])
        assertEquals((-0.0).toBits(), second.nodeCosts[129].toBits())
        assertEquals(nodes.take(260), first.currentPathNodes)
        assertEquals(nodes.take(261), second.currentPathNodes)
        val third = roundTrip(initial.copy(stepIndex = 2, currentPathNodes = nodes.take(3))).first
        assertEquals(nodes.take(3), third.currentPathNodes)
        val unchanged = roundTrip(initial.copy(stepIndex = 3, currentPathNodes = nodes.take(3))).first
        assertSame(third.nodeCosts, unchanged.nodeCosts)
        assertSame(third.knownNodes, unchanged.knownNodes)
        assertSame(third.currentPathNodes, unchanged.currentPathNodes)
    }

    @Test fun masterSwitchBypassesAndRestoresBudgets() {
        val model = VisualizerViewModel()
        val settings = PerformanceSettings(replay = SimulationLimits(maxFrames = 1, maxEstimatedBytes = 1),
            generation = GenerationBudget(maxWorkOperations = 1, maxCandidateEdges = 1, maxEdges = 1), limitsEnabled = false)
        model.onEvent(RoverVisualizerEvent.ChangePerformance(settings))
        model.onEvent(RoverVisualizerEvent.GenerateGraph)
        assertNotNull(model.state.generatedGraph)
        model.onEvent(RoverVisualizerEvent.RunAlgorithm)
        assertTrue(model.state.frames.size > 1)
        assertNull(model.state.recordingStopReason)
        model.onEvent(RoverVisualizerEvent.ChangePerformance(settings.copy(limitsEnabled = true)))
        model.onEvent(RoverVisualizerEvent.RunAlgorithm)
        assertNotNull(model.state.recordingStopReason)
        model.onEvent(RoverVisualizerEvent.GenerateGraph)
        assertTrue(model.state.noticeMessage.orEmpty().contains("budget exceeded"))
    }
    @Test fun workerEnforcesBudgetsAndReportsErrors() {
        val graph = GraphGenerator.generate()
        val frames = remoteRun(graph, Dijkstra, graph.nodes.first(), graph.nodes.last(), KnowledgeMode.GLOBAL,
            limits = SimulationLimits(maxFrames = 2))
        assertEquals(2, frames.size)
        assertTrue(remoteRun(graph, Dijkstra, graph.nodes.first(), graph.nodes.last(), KnowledgeMode.GLOBAL,
            limits = SimulationLimits(maxFrames = 1, maxEstimatedBytes = 1, maxDurationMillis = 1,
                timeLimitEnabled = true, enabled = false)).size > 2)
        val (kind, input) = readPacket(WorkerEngine().handle(packet(GENERATE) { write(GraphGenerationOptions(budget = GenerationBudget(maxWorkOperations = 1))) }))
        assertEquals(ERROR, kind)
        assertTrue(input.string().contains("work operations"))
        assertEquals(ERROR, readPacket(WorkerEngine().handle(packet(999))).first)
    }
    private fun remoteRun(graph: Graph, algorithm: RoverAlgorithm, start: Graph.Node, goal: Graph.Node,
                          mode: KnowledgeMode, initial: SimulationFrame? = null, limits: SimulationLimits = SimulationLimits()): List<SimulationFrame> {
        val engine = WorkerEngine(); val decoder = FrameDecoder(graph)
        var result = engine.handle(packet(RUN) {
            bool(true); graph(graph); int(RoverAlgorithm.algorithms.indexOf(algorithm)); int(start.id); int(goal.id); int(mode.ordinal)
            int(graph.seed); write(limits); bool(initial != null); initial?.let { FrameEncoder().write(this, it) }; int(1)
        })
        val frames = mutableListOf<SimulationFrame>()
        repeat(10_000) {
            val (kind, reader) = readPacket(result)
            if (kind == ERROR) fail(reader.string())
            assertEquals(BATCH, kind)
            val done = reader.bool(); reader.long(); assertTrue(reader.long() > 0); if (reader.bool()) reader.string()
            repeat(reader.count()) { frames += decoder.read(reader) }
            if (done) return frames
            result = engine.handle(packet(NEXT))
        }
        error("Worker did not finish")
    }
    private fun sameFrame(a: SimulationFrame, b: SimulationFrame) {
        assertSame(a.graph, b.graph); assertEquals(a.stepIndex, b.stepIndex)
        assertSame(a.roverNode, b.roverNode); assertSame(a.startNode, b.startNode); assertSame(a.goalNode, b.goalNode)
        assertEquals(a.nodeCosts, b.nodeCosts); assertEquals(a.edgeCosts, b.edgeCosts); assertEquals(a.nodeCostFields, b.nodeCostFields)
        assertEquals(a.currentPathNodes, b.currentPathNodes); assertEquals(a.finalPathNodes, b.finalPathNodes)
        assertEquals(a.currentPathEdges, b.currentPathEdges); assertEquals(a.finalPathEdges, b.finalPathEdges)
        assertEquals(a.message, b.message); assertEquals(a.costMeaning, b.costMeaning); assertEquals(a.runSeed, b.runSeed)
        a.graph.nodes.forEach { n ->
            assertEquals(n in a.knownNodes, n in b.knownNodes); assertEquals(n in a.visitedNodes, n in b.visitedNodes)
            assertEquals(n in a.frontierNodes, n in b.frontierNodes); assertEquals(n in a.physicallyVisitedNodes, n in b.physicallyVisitedNodes)
        }
        a.graph.edges.forEach { assertEquals(it in a.knownEdges, it in b.knownEdges) }
    }
}
