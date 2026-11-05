package sfg.wseminar.rover.computation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import sfg.wseminar.rover.algorithms.Dijkstra
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.computation.wire.*
import sfg.wseminar.rover.graph.generator.*

class WorkerClientTest {
    private class Loopback : WorkerTransport {
        val engine = WorkerEngine()
        val responses = Channel<ByteArray>(1)
        val sent = mutableListOf<Pair<Int, Int>>()
        var closed = false
        init { check(responses.trySend(packet(7)).isSuccess) }
        override fun send(bytes: ByteArray) {
            sent += readPacket(bytes).first to bytes.size
            check(responses.trySend(engine.handle(bytes)).isSuccess) { "More than one batch queued" }
        }
        override suspend fun receive() = responses.receive()
        override fun close() { closed = true; responses.cancel() }
    }
    @Test fun prefetchIsBoundedAndCompletedWorkerReusesItsGraph() = runTest {
        val transport = Loopback()
        val client = WorkerClient.open { transport }
        try {
            val graph = client.generate(GraphGenerationOptions(gridColumns = 45, gridRows = 45))
            val start = graph.nodes[graph.startNodeId!!]; val goal = graph.nodes[graph.goalNodeId!!]
            val expected = AlgorithmRunner.createTimeline(Dijkstra, graph, start, goal).generateAll()
            repeat(2) {
                var batch = client.start(Dijkstra, graph, start, goal, KnowledgeMode.GLOBAL, SimulationLimits(), null)
                var count = 0
                while (true) {
                    batch.frames.forEach { frame ->
                        assertEquals(count, frame.stepIndex)
                        assertSame(graph, frame.graph)
                        assertEquals(expected[count].message, frame.message)
                        assertEquals(expected[count].currentPathNodes, frame.currentPathNodes)
                        count++
                    }
                    if (batch.done) break
                    assertEquals(NEXT, transport.sent.last().first)
                    batch = client.next()
                }
                assertEquals(expected.size, count)
            }
            assertTrue(transport.sent.filter { it.first == RUN }.all { it.second < 100 }, "Generated graph must not be re-uploaded")
            // A different graph must replace the worker's cached topology.
            val other = GraphGenerator.generate(GraphGenerationOptions(gridColumns = 2, gridRows = 2))
            var batch = client.start(Dijkstra, other, other.nodes.first(), other.nodes.last(), KnowledgeMode.GLOBAL, SimulationLimits(), null)
            assertTrue(transport.sent.first { it.first == RUN && it.second > 100 }.second > 100)
            while (!batch.done) batch = client.next()
            assertSame(other, batch.frames.last().graph)
        } finally { client.close() }
        assertTrue(transport.closed)
    }
    @Test fun localWaypointRadiusMatchesTheUiEngine() = runTest {
        val nodes = List(9) { sfg.wseminar.rover.graph.Graph.Node(it.toDouble(), 0.0, it) }
        val graph = sfg.wseminar.rover.graph.Graph(nodes, nodes.zipWithNext().mapIndexed { i, (a, b) ->
            sfg.wseminar.rover.graph.Graph.Edge(a, b, 1.0, i)
        }, 0, 8)
        val client = WorkerClient.open { Loopback() }
        try {
            for (mode in KnowledgeMode.entries) {
                val expected = AlgorithmRunner.createTimeline(sfg.wseminar.rover.algorithms.GoToWaypoint,
                    graph, nodes[0], nodes[1], mode, sensingRadius = 3).generateAll()
                var batch = client.start(sfg.wseminar.rover.algorithms.GoToWaypoint, graph, nodes[0], nodes[1],
                    mode, SimulationLimits(), null, sensingRadius = 3)
                val actual = batch.frames.toMutableList()
                while (!batch.done) { batch = client.next(); actual += batch.frames }
                assertEquals(expected.map { frame -> graph.nodes.filter { it in frame.knownNodes } },
                    actual.map { frame -> graph.nodes.filter { it in frame.knownNodes } })
                assertEquals(expected.map { frame -> graph.edges.filter { it in frame.knownEdges } },
                    actual.map { frame -> graph.edges.filter { it in frame.knownEdges } })
                assertEquals(expected.map { it.edgeCosts }, actual.map { it.edgeCosts })
                assertEquals(expected.map { it.roverNode }, actual.map { it.roverNode })
                assertEquals(if (mode == KnowledgeMode.LOCAL) 4 else 9, actual.first().knownNodes.size)
            }
        } finally { client.close() }
    }

    @Test fun cancellationCanDiscardAPrefetchedResponse() = runTest {
        val transport = Loopback()
        val client = WorkerClient.open { transport }
        val graph = client.generate(GraphGenerationOptions(gridColumns = 60, gridRows = 60))
        val batch = client.start(Dijkstra, graph, graph.nodes.first(), graph.nodes.last(), KnowledgeMode.GLOBAL, SimulationLimits(), null)
        assertFalse(batch.done)
        client.close()
        assertTrue(transport.closed)
        assertTrue(transport.responses.receiveCatching().isClosed)
    }
}
