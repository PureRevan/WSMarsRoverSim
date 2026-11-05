package sfg.wseminar.rover.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.*
import sfg.wseminar.rover.algorithms.backend.KnowledgeMode
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.simulation.InitialSimulationFrameFactory
import sfg.wseminar.rover.ui.canvas.*
import sfg.wseminar.rover.ui.states.VisualizerOptions
import sfg.wseminar.rover.ui.states.ViewportState

class GraphVisibilityTest {
    @Test fun hiddenUnknownNodesCannotBeHitButEndpointsRemainVisible() {
        val nodes = List(4) { Graph.Node(it / 3.0, 0.5, it) }
        val edge = Graph.Edge(nodes[1], nodes[2], 1.0, 0)
        val graph = Graph(nodes, listOf(edge), 0, 3)
        val frame = InitialSimulationFrameFactory.create(graph, KnowledgeMode.LOCAL)
        val options = VisualizerOptions(showUnknownArea = false)
        assertTrue(frame.isNodeVisible(nodes[0], options))
        assertTrue(frame.isNodeVisible(nodes[3], options))
        assertFalse(frame.isNodeVisible(nodes[1], options))
        assertFalse(frame.isEdgeVisible(edge, options))
        val hit = GraphHit.find(graph, ViewportState(), IntSize(600, 400), Offset(200f, 200f),
            visibleNode = { frame.isNodeVisible(it, options) },
            visibleEdge = { frame.isEdgeVisible(it, options) })
        assertEquals(GraphHit.Empty, hit)
        assertTrue(frame.isNodeVisible(nodes[1], options.copy(showUnknownArea = true)))
        assertTrue(frame.isEdgeVisible(edge, options.copy(showUnknownArea = true)))
    }

}
