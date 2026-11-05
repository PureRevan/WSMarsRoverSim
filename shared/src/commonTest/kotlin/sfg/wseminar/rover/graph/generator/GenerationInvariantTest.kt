package sfg.wseminar.rover.graph.generator

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import sfg.wseminar.rover.graph.Graph

class GenerationInvariantTest {
    @Test fun spatialClearanceAgreesWithBruteForce() {
        val graph = GraphGenerator.generate(GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS, nodeCount = 60))
        val index = NodeSpatialIndex(graph.nodes)
        for (a in graph.nodes.take(12)) for (b in graph.nodes.takeLast(12)) {
            val actual = immediate { index.clears(a, b, 0.015, GenerationWork {}) }
            assertEquals(GraphGeometry.edgeClearsOtherNodes(a.id, b.id, graph.nodes, 0.015), actual)
        }
    }
    @Test fun planarCandidatesStayPlanarAcrossSeedsAndDoNotLoseNodes() {
        repeat(6) { seed ->
            val graph = GraphGenerator.generate(GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS, nodeCount = 90, seed = seed, density = 1.0))
            assertEquals(90, graph.nodes.size)
            for (a in graph.edges) for (b in graph.edges) if (a.id < b.id) {
                assertFalse(GraphGeometry.segmentsProperlyIntersect(a.from, a.to, b.from, b.to))
            }
        }
    }
    @Test fun cooperativeSortAndWorkBudgetAreDeterministic() = runTest {
        val work = GenerationWork(GenerationBudget(maxWorkOperations = 50_000_000)) {}
        val values = listOf(8, 1, 9, 1, 2, 0, 5)
        assertEquals(values.sorted(), work.sorted(values, naturalOrder()))
        val failure = assertFailsWith<IllegalArgumentException> { work.add(50_000_001) }
        assertTrue(failure.message!!.contains("work operations budget exceeded"))
        assertTrue(failure.message!!.contains("limit 50,000,000"))
        val cancel = GenerationWork { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { cancel.pause() }
    }
    @Test fun constraintsFailExplicitlyInsteadOfReturningAShortOrDisconnectedGraph() {
        val options = GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS, nodeCount = 2,
            planar = false, connectionRadius = 0.08, seed = 42)
        assertTrue(GraphGenerator.generate(options.copy(allowDisconnected = true)).edges.isEmpty())
        assertFailsWith<IllegalArgumentException> { GraphGenerator.generate(options) }
    }
}
