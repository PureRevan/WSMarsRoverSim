package sfg.wseminar.rover.algorithms

import kotlin.test.*
import kotlin.random.Random
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.graph.Graph

class AlgorithmBackendTest {
    private fun nodes(count: Int) = List(count) { Graph.Node(it * 100.0, 0.0, it) }

    private fun run(algorithm: RoverAlgorithm, graph: Graph, start: Int = 0, goal: Int = graph.nodes.lastIndex) =
        AlgorithmRunner.createTimeline(algorithm, graph, graph.nodes[start], graph.nodes[goal]).generateAll()

    @Test fun shortestPathsUseImprovedPrioritiesAndAdmissibleHeuristics() {
        val n = nodes(4)
        val graph = Graph(n, listOf(
            Graph.Edge(n[0], n[3], 10.0, 0, false),
            Graph.Edge(n[0], n[1], 1.0, 1, false),
            Graph.Edge(n[1], n[2], 1.0, 2, false),
            Graph.Edge(n[2], n[3], 1.0, 3, false)
        ))
        for (algorithm in listOf(Dijkstra, AStar)) {
            val frames = run(algorithm, graph)
            assertEquals(3.0, frames.last().nodeCosts[3])
            assertEquals(n, frames.last().finalPathNodes)
            assertEquals(Double.POSITIVE_INFINITY, frames.first().nodeCosts[3])
            assertTrue(n[0] in frames.first().frontierNodes)
            assertFalse(n[0] in frames.first().visitedNodes)
            assertTrue(n[0] in frames[1].visitedNodes)
        }
        val frames = run(AStar, graph)
        assertEquals(setOf("g", "h", "f"), frames.last().nodeCostFields.keys)
        assertEquals(Double.POSITIVE_INFINITY, frames.first().nodeCostFields.getValue("f")[3])
        assertEquals(3.0, frames.last().nodeCostFields.getValue("f")[3])
    }

    @Test fun disconnectedDirectedAndTrivialPaths() {
        val n = nodes(3)
        val graph = Graph(n, listOf(Graph.Edge(n[0], n[1], 0.0, 0, false)))
        for (algorithm in listOf(Dijkstra, AStar)) {
            assertTrue(run(algorithm, graph).last().finalPathNodes.isEmpty())
            assertTrue(run(algorithm, graph, 1, 0).last().finalPathNodes.isEmpty())
            assertEquals(listOf(n[0]), run(algorithm, graph, 0, 0).last().finalPathNodes)
            assertEquals(0.0, run(algorithm, graph, 0, 1).last().nodeCosts[1])
        }
    }

    @Test fun queueMatchesReferenceUnderMixedUpdatesAndRemovals() {
        val n = nodes(25)
        val context = AlgorithmContext(Dijkstra, Graph(n, emptyList()), n[0], n.last())
        val queue = context.PriorityQueue<Int>()
        val expected = mutableMapOf<Graph.Node, Int>()
        val random = Random(129)
        repeat(1000) {
            val node = n[random.nextInt(n.size)]
            when (random.nextInt(3)) {
                0 -> {
                    val priority = random.nextInt(10)
                    queue[node] = priority
                    expected[node] = priority
                    assertFalse(node in context.visitedNodes)
                }
                1 -> assertEquals(expected.remove(node) != null, queue.remove(node))
                else -> if (expected.isNotEmpty()) {
                    val first = expected.keys.minWith(compareBy<Graph.Node> { expected.getValue(it) }.thenBy { it.id })
                    assertEquals(first, queue.peek())
                    assertEquals(expected.getValue(first), queue.peekPriority())
                    assertEquals(first, queue.removeMin())
                    expected.remove(first)
                    assertTrue(first in context.visitedNodes)
                }
            }
            assertEquals(expected.size, queue.size)
            for (candidate in n) {
                assertEquals(candidate in expected, candidate in queue)
                assertEquals(candidate in expected, candidate in context.frontierNodes)
            }
        }
    }

    private object DualMode : RoverAlgorithm(), GlobalAlgorithm, LocalAlgorithm {
        override fun run(context: AlgorithmContext): Sequence<AlgorithmStep> = emptySequence()
    }

    @Test fun localDiscoveryIsExplicitAndPlanningDoesNotMoveRover() {
        val n = nodes(3)
        val graph = Graph(n, listOf(
            Graph.Edge(n[0], n[1], 1.0, 0, false),
            Graph.Edge(n[1], n[2], 1.0, 1, false)
        ))
        val local = AlgorithmContext(DualMode, graph, n[0], n[2], KnowledgeMode.LOCAL)
        assertEquals(2, local.knownNodes.size)
        assertEquals(1, local.knownEdges.size)
        assertFailsWith<IllegalArgumentException> { local.moveTo(n[2]) }
        assertEquals(listOf(graph.edges[0]), local.observe(n[0]))
        assertEquals(2, local.knownNodes.size)
        assertFalse(n[2] in local.knownNodes)
        val queue = local.PriorityQueue<Int>()
        queue[n[1]] = 0
        queue.removeMin()
        assertSame(n[0], local.roverNode)
        local.moveTo(n[1])
        local.observe(n[1])
        assertTrue(n[2] in local.knownNodes)
        assertFailsWith<IllegalArgumentException> { local.moveTo(n[0]) }
        assertEquals(3, AlgorithmContext(DualMode, graph, n[0], n[2], KnowledgeMode.GLOBAL).knownNodes.size)
        assertFailsWith<IllegalArgumentException> {
            AlgorithmContext(Dijkstra, graph, n[0], n[2], KnowledgeMode.LOCAL)
        }
    }

    @Test fun predecessorPathsRejectCyclesAndKeepParallelEdgeIdentity() {
        val n = nodes(3)
        val graph = Graph(n, listOf(
            Graph.Edge(n[0], n[1], 8.0, 0),
            Graph.Edge(n[0], n[1], 2.0, 1),
            Graph.Edge(n[1], n[2], 1.0, 2)
        ))
        val context = AlgorithmContext(Dijkstra, graph, n[0], n[2])
        context.nodeCosts[n[1]] = 2.0
        context.predecessors[n[1]] = graph.edges[1]
        assertSame(graph.edges[1], context.predecessors[n[1]])
        assertEquals(2.0, context.edgeCosts[graph.edges[1]])
        assertEquals(listOf(n[0], n[1]), context.currentPathNodes)
        context.currentPathTo(n[2])
        assertTrue(context.currentPathNodes.isEmpty())
        context.previousNode[1] = n[2]
        context.previousNode[2] = n[1]
        assertFailsWith<IllegalStateException> { context.currentPathTo(n[2]) }
    }

    @Test fun timelineIsLazyRepeatableAndCopiesAreDetached() {
        val n = nodes(2)
        val graph = Graph(n, listOf(Graph.Edge(n[0], n[1], 3.0, 0)))
        val timeline = AlgorithmRunner.createTimeline(AStar, graph, n[0], n[1])
        assertEquals(-1, timeline.lastCachedIndex)
        val first = assertNotNull(timeline.frameAt(0))
        val cached = timeline.cachedFrames
        val copy = first.safeCopy()
        copy.nodeCosts.toDoubleArray()[0] = 99.0
        copy.edgeCosts.toDoubleArray()[0] = 99.0
        copy.frontierNodes.remove(n[0])
        assertEquals(0.0, first.nodeCosts[0])
        assertEquals(3.0, first.edgeCosts[0])
        assertTrue(n[0] in first.frontierNodes)
        val all = timeline.generateAll()
        assertEquals(1, cached.size)
        assertSame(first, timeline.frameAt(0))
        assertTrue(timeline.isExhausted)
        assertNull(timeline.frameAt(all.size))
        assertEquals(all, timeline.generateAll())
        assertFailsWith<IllegalArgumentException> { timeline.generateUntil(-1) }
    }

    @Test fun invalidWeightsAndForeignNodesAreRejected() {
        val n = nodes(2)
        for (weight in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val graph = Graph(n, listOf(Graph.Edge(n[0], n[1], weight, 0)))
            for (algorithm in listOf(Dijkstra, AStar)) {
                assertFailsWith<IllegalArgumentException> { run(algorithm, graph) }
            }
        }
        val graph = Graph(n, emptyList())
        assertFailsWith<IllegalArgumentException> {
            AlgorithmContext(Dijkstra, graph, Graph.Node(0.0, 0.0, 0), n[1])
        }
    }
}
