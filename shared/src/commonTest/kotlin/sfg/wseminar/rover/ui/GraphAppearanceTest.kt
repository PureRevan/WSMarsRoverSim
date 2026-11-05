package sfg.wseminar.rover.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.*
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.*
import sfg.wseminar.rover.ui.canvas.*
import sfg.wseminar.rover.ui.models.*
import sfg.wseminar.rover.ui.states.*

class GraphAppearanceTest {
    @Test fun closeZoomEnlargesNodesWithoutInflatingOverviewAndHitTargetsMatch() {
        val graph = grid(32)
        fun metrics(zoom: Float, enabled: Boolean = true) = graphDisplayMetrics(graph,
            ViewportState(zoom = zoom), 800f, 800f, GraphAppearance(enlargeNodesAtCloseZoom = enabled))
        assertTrue(metrics(0.01f).nodeRadius < 1f)
        assertEquals(metrics(0.01f, false), metrics(0.01f))
        val normal = metrics(4f)
        val close = metrics(12f)
        val veryClose = metrics(24f)
        assertTrue(close.nodeRadius > normal.nodeRadius)
        assertTrue(veryClose.nodeRadius > close.nodeRadius)
        assertEquals(31.5f, veryClose.nodeRadius)
        assertEquals(veryClose.nodeRadius, metrics(100f).nodeRadius)
        assertTrue(close.edgeWidth > normal.edgeWidth)
        assertTrue(close.edgeWidth / normal.edgeWidth < close.nodeRadius / normal.nodeRadius)
        assertEquals(10.5f, metrics(24f, false).nodeRadius)
        val single = Graph(listOf(Graph.Node(0.5, 0.5, 0)), emptyList())
        val viewport = ViewportState(zoom = 3f)
        val center = transformWorldToCanvas(Offset(0.5f, 0.5f), viewport, 800f, 800f)
        val tap = center + Offset(25f, 0f)
        assertIs<GraphHit.NodeHit>(GraphHit.find(single, viewport, IntSize(800, 800), tap))
        assertEquals(GraphHit.Empty, GraphHit.find(single, viewport, IntSize(800, 800), tap,
            GraphAppearance(enlargeNodesAtCloseZoom = false)))
    }

    @Test fun labelThresholdCanRevealEarlierOrLaterAndBeDisabled() {
        val graph = grid(32)
        fun visible(threshold: Float, enabled: Boolean = true, scale: Float = 1f) =
            graphDisplayMetrics(graph, ViewportState(zoom = 1f), 800f, 800f,
                GraphAppearance(labelRevealSpacing = threshold, hideCrowdedLabels = enabled, labelScale = scale)).showLabels
        // The estimated spacing is 25 pixels at this viewport.
        assertTrue(visible(12f))
        assertTrue(visible(25f))
        assertFalse(visible(26f))
        assertFalse(visible(52f))
        assertTrue(visible(120f, enabled = false))
        assertFalse(visible(25f, scale = 1.5f))
        assertEquals(12f, GraphAppearance(labelRevealSpacing = 0f).constrained().labelRevealSpacing)
        assertEquals(120f, GraphAppearance(labelRevealSpacing = 200f).constrained().labelRevealSpacing)
    }

    private fun grid(side: Int) = Graph(List(side * side) {
        Graph.Node((it % side).toDouble() / (side - 1), (it / side).toDouble() / (side - 1), it)
    }, emptyList())

    @Test fun crowdedOverviewShrinksSymbolsAndZoomRestoresDetails() {
        val graph = grid(32)
        val overview = graphDisplayMetrics(graph, ViewportState(zoom = 0.8f), 800f, 800f, GraphAppearance())
        val close = graphDisplayMetrics(graph, ViewportState(zoom = 4f), 800f, 800f, GraphAppearance())
        assertTrue(overview.nodeRadius < 6f)
        assertTrue(overview.edgeWidth < 1.5f)
        assertFalse(overview.showLabels)
        assertEquals(10.5f, close.nodeRadius)
        assertTrue(close.showLabels)
        val small = graphDisplayMetrics(grid(6), ViewportState(zoom = 0.8f), 800f, 800f, GraphAppearance())
        assertEquals(10.5f, small.nodeRadius)
        assertTrue(small.showLabels)
    }

    @Test fun manualOverridesAndHitTestingMatchDisplayedSizes() {
        val graph = grid(32)
        val viewport = ViewportState(zoom = 1f)
        val options = GraphAppearance(nodeScale = 2f, edgeScale = 0.5f, adaptiveSizing = false, hideCrowdedLabels = false)
        val metrics = graphDisplayMetrics(graph, viewport, 800f, 800f, options)
        assertEquals(21f, metrics.nodeRadius)
        assertEquals(1.5f, metrics.edgeWidth)
        assertTrue(metrics.showLabels)
        val single = Graph(listOf(Graph.Node(0.5, 0.5, 0)), emptyList())
        val hit = GraphHit.find(single, viewport, IntSize(800, 800), Offset(430f, 400f), options)
        assertIs<GraphHit.NodeHit>(hit)
        assertEquals(GraphHit.Empty, GraphHit.find(single, viewport, IntSize(800, 800), Offset(430f, 400f)))
    }

    @Test fun appearanceEditsPreserveSimulationAndLayout() {
        val model = VisualizerViewModel()
        model.onEvent(RoverVisualizerEvent.GenerateGraph)
        model.onEvent(RoverVisualizerEvent.RunAlgorithm)
        val before = model.state
        model.onEvent(RoverVisualizerEvent.ChangeGraphAppearance(GraphAppearance(nodeScale = 0.2f)))
        assertSame(before.frames, model.state.frames)
        assertEquals(before.currentStep, model.state.currentStep)
        assertEquals(before.graphGeneration, model.state.graphGeneration)
        assertEquals(0.2f, model.state.options.appearance.nodeScale)
    }

    @Test fun advancedRespectsConfiguredLimitWithoutChangingSimpleMode() {
        val limit = GenerationLimits.MaxNodes
        val advanced = GraphGenerationState(advanced = true, nodeCount = limit)
        assertNull(advanced.validationMessage())
        assertEquals(limit, advanced.toOptions().nodeCount)
        assertNotNull(advanced.copy(nodeCount = limit + 1).validationMessage())
        val columns = minOf(limit / 2, GenerationLimits.MaxGridSide)
        assertNull(advanced.copy(mode = SpaceMode.DISCRETE, gridColumns = columns, gridRows = 2).validationMessage())
        assertNotNull(advanced.copy(mode = SpaceMode.DISCRETE, gridColumns = columns, gridRows = limit).validationMessage())
        val simple = advanced.copy(advanced = false, sizeFactor = 1.0)
        assertEquals(48, simple.toOptions().nodeCount)
        assertEquals(132L, simple.copy(mode = SpaceMode.DISCRETE).requestedNodeCount())
        assertFailsWith<IllegalArgumentException> {
            GraphGenerator.generate(GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS, nodeCount = limit + 1))
        }
    }

    @Test fun strongerSpacingWorksOnDenseMapsAndKeepsEveryRequestedNode() {
        val count = 1_000
        val loose = GraphLayoutMetrics.minNodeSeparation(count, 1.0, 1.0, spacing = 0.0)
        val even = GraphLayoutMetrics.minNodeSeparation(count, 1.0, 1.0, spacing = 1.0)
        assertTrue(even > loose * 2)
        val graph = GraphGenerator.generate(GraphGenerationOptions(mode = GraphGenerationMode.CONTINUOUS,
            nodeCount = count, nodeSpacing = 1.0, seed = 62))
        assertEquals(count, graph.nodes.size)
        for (i in graph.nodes.indices) for (j in i + 1 until graph.nodes.size) {
            assertTrue(GraphGeometry.distance(graph.nodes[i], graph.nodes[j]) >= even - 1e-10)
        }
    }

    @Test fun largeGridFitsAndCanStillBeZoomedAtSmallScales() {
        val graph = Graph(listOf(Graph.Node(0.0, 0.0, 0), Graph.Node(49_999.0, 1.0, 1)), emptyList())
        val fit = calculateFitViewport(graph, IntSize(800, 800))
        val last = transformWorldToCanvas(Offset(49_999f, 1f), fit, 800f, 800f)
        assertTrue(last.x in 0f..800f)
        val closer = calculateCursorAnchoredViewport(fit, Offset(400f, 400f), 1.1f, 800f, 800f)
        assertTrue(closer.zoom > fit.zoom)
    }
}
