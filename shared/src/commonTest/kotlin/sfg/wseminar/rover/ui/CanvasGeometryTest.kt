package sfg.wseminar.rover.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.*
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.ui.canvas.*
import sfg.wseminar.rover.ui.states.ViewportState

class CanvasGeometryTest {
    @Test fun browserWheelUnitsGiveComparableGentleZoomSteps() {
        val desktopStep = calculateWheelZoomFactor(1f)
        val pixelStep = calculateWheelZoomFactor(normalizeBrowserWheelDelta(100f, 0))
        val lineStep = calculateWheelZoomFactor(normalizeBrowserWheelDelta(3f, 1))
        val pageStep = calculateWheelZoomFactor(normalizeBrowserWheelDelta(1f, 2))
        assertEquals(desktopStep, pixelStep, 0.000001f)
        assertEquals(pixelStep, lineStep, 0.000001f)
        assertEquals(pixelStep, pageStep, 0.000001f)
        assertTrue(pixelStep in 0.94f..0.97f)
    }

    @Test fun wheelSpikesAreBoundedAndInvalidInputDoesNotCorruptViewport() {
        val out = calculateWheelZoomFactor(normalizeBrowserWheelDelta(10_000f, 0))
        val into = calculateWheelZoomFactor(normalizeBrowserWheelDelta(-10_000f, 0))
        assertTrue(out in 0.87f..1f)
        assertTrue(into in 1f..1.15f)
        assertEquals(1f, out * into, 0.000001f)
        for (delta in listOf(0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(1f, calculateWheelZoomFactor(delta))
        }
    }

    @Test fun trackpadFractionsAccumulateSmoothly() {
        val small = calculateWheelZoomFactor(normalizeBrowserWheelDelta(1f, 0))
        assertTrue(small > calculateWheelZoomFactor(normalizeBrowserWheelDelta(10f, 0)))
        var accumulated = 1f
        repeat(100) { accumulated *= small }
        assertEquals(calculateWheelZoomFactor(normalizeBrowserWheelDelta(100f, 0)), accumulated, 0.00001f)
    }

    @Test fun aspectRatioInverseAndAnchoredZoomRemainConsistent() {
        val viewport = ViewportState(zoom = 2f, panX = 30f, panY = -12f)
        val center = transformWorldToCanvas(Offset(0.5f, 0.5f), viewport, 900f, 400f)
        val x = transformWorldToCanvas(Offset(0.6f, 0.5f), viewport, 900f, 400f)
        val y = transformWorldToCanvas(Offset(0.5f, 0.6f), viewport, 900f, 400f)
        assertEquals((x - center).getDistance(), (y - center).getDistance(), 0.001f)
        val cursor = Offset(280f, 140f)
        val world = transformCanvasToWorld(cursor, viewport, 900f, 400f)
        assertTrue((transformWorldToCanvas(world, viewport, 900f, 400f) - cursor).getDistance() < 0.001f)
        val zoomed = calculateCursorAnchoredViewport(viewport, cursor, 1.4f, 900f, 400f)
        assertTrue((transformWorldToCanvas(world, zoomed, 900f, 400f) - cursor).getDistance() < 0.001f)
    }
    @Test fun cachedGeometryAndHitTestingDistinguishParallelEdges() {
        val n = listOf(Graph.Node(0.1, 0.5, 0), Graph.Node(0.9, 0.5, 1))
        val edges = listOf(Graph.Edge(n[0], n[1], 1.0, 0, false), Graph.Edge(n[1], n[0], 2.0, 1, false))
        val graph = Graph(n, edges)
        val cache = CanvasGeometryCache()
        val viewport = ViewportState()
        val size = IntSize(900, 400)
        val geometry = cache.get(graph, viewport, size)
        assertSame(geometry, cache.get(graph, viewport, size))
        assertNotEquals(geometry.edges[0].bend, geometry.edges[1].bend)
        for (edge in edges) assertEquals(GraphHit.EdgeHit(edge), GraphHit.find(graph, viewport, size, geometry.edges[edge.id].bend, geometry = geometry))
        assertNotSame(geometry, cache.get(graph, viewport.copy(panX = 1f), size))
    }
}
