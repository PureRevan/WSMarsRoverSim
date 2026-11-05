package sfg.wseminar.rover.algorithms

import kotlin.test.*
import kotlin.random.Random
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.graph.*
import sfg.wseminar.rover.simulation.FrameHistory

class ReplayStorageTest {
    @Test fun costPagesAndMasksRemainImmutableAcrossMutations() {
        val n = List(300) { Graph.Node(it.toDouble(), 0.0, it) }
        val costs = TrackedNodeCosts(n.size)
        val masks = IDSet.create<Graph.Node>(n.size)
        val random = Random(7)
        val expected = DoubleArray(n.size) { Double.POSITIVE_INFINITY }
        val referenceMask = mutableSetOf<Int>()
        val history = mutableListOf<Triple<sfg.wseminar.rover.simulation.CostSnapshot, IDSet<Graph.Node>, Pair<DoubleArray, Set<Int>>>>()
        repeat(200) {
            val id = random.nextInt(n.size)
            costs[n[id]] = it.toDouble(); expected[id] = it.toDouble()
            if (it % 2 == 0) { masks.add(n[id]); referenceMask.add(id) }
            else { masks.remove(n[id]); referenceMask.remove(id) }
            history += Triple(costs.snapshot(), masks.snapshot(), expected.copyOf() to referenceMask.toSet())
        }
        for ((snapshot, mask, expectedState) in history) {
            assertContentEquals(expectedState.first, snapshot.toDoubleArray())
            for (node in n) assertEquals(node.id in expectedState.second, node in mask)
            assertFailsWith<IllegalStateException> { mask.add(n[0]) }
            val copy = mask.copy(); copy.add(n[0]); copy.remove(n[1])
            assertEquals(expectedState.second.size, mask.size)
        }
        assertSame(costs.snapshot(), costs.snapshot())
        assertSame(masks.snapshot(), masks.snapshot())
    }

    @Test fun namedGIsSharedAndParallelEdgeIdentitySurvivesReplay() {
        val n = List(3) { Graph.Node(it.toDouble(), 0.0, it) }
        val edges = listOf(Graph.Edge(n[0], n[1], 8.0, 0), Graph.Edge(n[0], n[1], 2.0, 1), Graph.Edge(n[1], n[2], 1.0, 2))
        val graph = Graph(n, edges)
        val timeline = AlgorithmRunner.createTimeline(AStar, graph, n[0], n[2])
        val frames = timeline.generateAll()
        assertSame(frames.first().nodeCosts, frames.first().nodeCostFields.getValue("g"))
        assertSame(frames.first().nodeCostFields["h"], frames.last().nodeCostFields["h"])
        assertSame(frames.first().knownEdges, frames.last().knownEdges)
        assertEquals(listOf(edges[1], edges[2]), frames.last().finalPathEdges)
        val history = FrameHistory().append(frames)
        val branch = history.prefix(2).append(frames.take(1))
        assertEquals(frames, history)
        assertEquals(3, branch.size)
        assertSame(frames[0], branch[2])
        assertSame(frames[0], timeline.frameAt(0))
    }

    @Test fun tinyBudgetRetainsRecordedFramesAndReportsWhyItStopped() {
        val n = List(3) { Graph.Node(it.toDouble(), 0.0, it) }
        val graph = Graph(n, listOf(Graph.Edge(n[0], n[1], 1.0, 0), Graph.Edge(n[1], n[2], 1.0, 1)))
        val timeline = AlgorithmRunner.createTimeline(Dijkstra, graph, n[0], n[2], limits = SimulationLimits(maxFrames = 2))
        assertEquals(2, timeline.generateAll().size)
        assertTrue(timeline.isExhausted)
        assertNotNull(timeline.stopReason)
        assertNotNull(timeline.frameAt(0))
        assertNull(timeline.frameAt(2))
    }
}
