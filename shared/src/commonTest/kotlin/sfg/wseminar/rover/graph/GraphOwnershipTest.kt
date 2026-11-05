package sfg.wseminar.rover.graph

import kotlin.test.*

class GraphOwnershipTest {
    @Test fun sharedGeometryDoesNotShareAdjacency() {
        val n = List(2) { Graph.Node(it.toDouble(), 0.0, it) }
        val e = Graph.Edge(n[0], n[1], 1.0, 0, false)
        val first = Graph(n, listOf(e))
        val second = Graph(n, emptyList())
        assertEquals(listOf(e), first.outgoingEdges(n[0]))
        assertEquals(listOf(e), first.incomingEdges(n[1]))
        assertTrue(first.outgoingEdges(n[1]).isEmpty())
        assertTrue(second.outgoingEdges(n[0]).isEmpty())
        assertEquals(listOf(e), first.copy(startNodeId = 1).outgoingEdges(n[0]))
    }

    @Test fun invalidIdentityAndEndpointsFailBeforeIndexing() {
        val n = Graph.Node(0.0, 0.0, 0)
        assertFailsWith<IllegalArgumentException> { Graph(listOf(Graph.Node(0.0, 0.0, 2)), emptyList()) }
        assertFailsWith<IllegalArgumentException> { Graph(listOf(n), emptyList(), goalNodeId = 2) }
        assertFailsWith<IllegalArgumentException> { Graph(listOf(n), listOf(Graph.Edge(n, Graph.Node(1.0, 0.0, 0), 1.0, 0))) }
        assertFailsWith<IllegalArgumentException> { Graph(listOf(n), emptyList()).outgoingEdges(Graph.Node(0.0, 0.0, 0)) }
    }
}
